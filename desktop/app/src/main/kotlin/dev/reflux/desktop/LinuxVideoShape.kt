package dev.reflux.desktop

import androidx.compose.ui.geometry.Rect
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.awt.Canvas
import java.awt.EventQueue
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The video surface's shape on Linux (B-084, CACHY-002): the player is one window, its controls drawn by the toolkit beneath
 * the surface and seen through holes cut in it.
 *
 * libmpv draws into a native X11 window inside the surface, which is always above what the toolkit draws in the same window.
 * Reflux used to put the controls in a second, transparent window above the picture. On an XWayland whose windows reach the
 * compositor without alpha (the owner's CachyOS laptop, a hybrid Intel/NVIDIA machine: even a plain Java window with a clear
 * background came out black), that window was a black panel over the picture, and as a separate toplevel a tiling compositor
 * blurred it, floated it, and left it behind on a workspace move. A shape needs no alpha: the X SHAPE extension takes the
 * controls' rectangles out of the surface's **bounding** region (the picture is not drawn there, so the controls beneath
 * show), and empties its **input** region (the pointer always reaches the toolkit, which handles hover, clicks and the wheel
 * as it did over the controls window). Both clip libmpv's own window inside the surface, as a parent's regions do.
 *
 * The toolkit can put its own rectangular shape back when it lays the surface out, so the shape is made again after every
 * reshape, as [SurfaceCorners] does on Windows. Only on Linux, and only where libX11 and libXext load; otherwise it does nothing.
 */
internal class LinuxVideoShape(private val canvas: Canvas) : AutoCloseable {
    private var holes: List<Rect> = emptyList()
    private var surface: Rect? = null
    private var display: Pointer? = null
    private var lastCut: List<IntBox>? = null
    private var closed = false

    private val reshaped = object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) = later()
        override fun componentMoved(event: ComponentEvent) = later()
        override fun componentShown(event: ComponentEvent) = later()
    }
    private val ancestors = object : HierarchyBoundsAdapter() {
        override fun ancestorMoved(event: HierarchyEvent) = later()
        override fun ancestorResized(event: HierarchyEvent) = later()
    }
    private val hierarchy = HierarchyListener { event ->
        if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) later()
    }

    init {
        canvas.addComponentListener(reshaped)
        canvas.addHierarchyBoundsListener(ancestors)
        canvas.addHierarchyListener(hierarchy)
    }

    /**
     * What is drawn over the picture ([cutouts]) and where the surface is ([surfaceBounds]), both in the window's pixels as the
     * toolkit lays them out. Called on the UI thread whenever either changes.
     */
    fun update(cutouts: List<Rect>, surfaceBounds: Rect?) {
        holes = cutouts
        surface = surfaceBounds
        apply()
    }

    /** Again once the toolkit has finished the reshape that is under way. */
    private fun later() = EventQueue.invokeLater { apply() }

    @Synchronized
    private fun apply() {
        if (closed) return
        runCatching {
            if (!canvas.isDisplayable || !canvas.isShowing) return
            val bounds = surface ?: return
            val window = Native.getComponentID(canvas)
            if (window == 0L) return
            val x11 = libraries ?: return
            val connection = display ?: x11.x.XOpenDisplay(null)?.also { display = it } ?: return
            // The surface's size in device pixels, which X counts in; the toolkit may scale its own (Java's uiScale).
            val scale = canvas.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
            val width = (canvas.width * scale).toInt()
            val height = (canvas.height * scale).toInt()
            val cut = videoHoles(width, height, bounds, holes)
            val whole = XRectangle.array(listOf(IntBox(0, 0, width, height)))
            x11.ext.XShapeCombineRectangles(connection, window, SHAPE_BOUNDING, 0, 0, whole, 1, SHAPE_SET, UNSORTED)
            if (cut.isNotEmpty()) x11.ext.XShapeCombineRectangles(connection, window, SHAPE_BOUNDING, 0, 0, XRectangle.array(cut), cut.size, SHAPE_SUBTRACT, UNSORTED)
            // No input anywhere on the surface: the pointer goes to the toolkit's content beneath it.
            x11.ext.XShapeCombineRectangles(connection, window, SHAPE_INPUT, 0, 0, null, 0, SHAPE_SET, UNSORTED)
            x11.x.XFlush(connection)
            // What a hole uncovers was clipped from the window's last frame by the surface above it (X clips a window's drawing by
            // its children): the window draws again, so the controls appear in it at once.
            if (cut != lastCut) {
                lastCut = cut
                javax.swing.SwingUtilities.getWindowAncestor(canvas)?.repaint()
            }
        }.onFailure { failure -> if (!reported) { reported = true; System.err.println("Shaping the video surface failed: $failure") } }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        canvas.removeComponentListener(reshaped)
        canvas.removeHierarchyBoundsListener(ancestors)
        canvas.removeHierarchyListener(hierarchy)
        display?.let { connection -> runCatching { libraries?.x?.XCloseDisplay(connection) } }
        display = null
    }

    @Suppress("FunctionName")
    private interface X11 : Library {
        fun XOpenDisplay(name: String?): Pointer?
        fun XFlush(display: Pointer): Int
        fun XCloseDisplay(display: Pointer): Int
    }

    @Suppress("FunctionName")
    private interface Xext : Library {
        fun XShapeCombineRectangles(display: Pointer, window: Long, kind: Int, x: Int, y: Int, rectangles: Pointer?, count: Int, operation: Int, ordering: Int)
    }

    private class Libraries(val x: X11, val ext: Xext)

    private companion object {
        val libraries: Libraries? by lazy {
            runCatching { Libraries(Native.load("X11", X11::class.java), Native.load("Xext", Xext::class.java)) }.getOrNull()
        }
        const val SHAPE_BOUNDING = 0
        const val SHAPE_INPUT = 2
        const val SHAPE_SET = 0
        const val SHAPE_SUBTRACT = 3
        const val UNSORTED = 0
        @Volatile var reported = false
    }
}

/** A rectangle in whole device pixels: [x], [y] and its size. */
internal data class IntBox(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * The holes to cut in a surface of [width] x [height] device pixels that the window lays out at [surface] (window pixels),
 * for the [cutouts] drawn over it (window pixels): each moved into the surface's own pixels, scaled to its device size, grown to
 * whole pixels so nothing drawn is left under the picture's edge, and kept to the surface. What falls wholly outside it, or has
 * no area, is not a hole.
 */
internal fun videoHoles(width: Int, height: Int, surface: Rect, cutouts: List<Rect>): List<IntBox> {
    if (width <= 0 || height <= 0 || surface.width <= 0f || surface.height <= 0f) return emptyList()
    val sx = width / surface.width
    val sy = height / surface.height
    return cutouts.mapNotNull { cutout ->
        val left = floor((cutout.left - surface.left) * sx).toInt().coerceIn(0, width)
        val top = floor((cutout.top - surface.top) * sy).toInt().coerceIn(0, height)
        val right = ceil((cutout.right - surface.left) * sx).toInt().coerceIn(0, width)
        val bottom = ceil((cutout.bottom - surface.top) * sy).toInt().coerceIn(0, height)
        if (right > left && bottom > top) IntBox(left, top, right - left, bottom - top) else null
    }
}

/** Xlib's `XRectangle`: shorts for the place, unsigned shorts for the size. */
@Structure.FieldOrder("x", "y", "width", "height")
internal class XRectangle : Structure() {
    @JvmField var x: Short = 0
    @JvmField var y: Short = 0
    @JvmField var width: Short = 0
    @JvmField var height: Short = 0

    companion object {
        /** [boxes] laid out one after another in native memory, as `XShapeCombineRectangles` reads them. */
        fun array(boxes: List<IntBox>): Pointer {
            @Suppress("UNCHECKED_CAST")
            val items = XRectangle().toArray(boxes.size) as Array<XRectangle>
            boxes.forEachIndexed { i, box ->
                items[i].x = box.x.toShort()
                items[i].y = box.y.toShort()
                items[i].width = box.width.coerceAtMost(65_535).toShort()
                items[i].height = box.height.coerceAtMost(65_535).toShort()
                items[i].write()
            }
            return items[0].pointer
        }
    }
}
