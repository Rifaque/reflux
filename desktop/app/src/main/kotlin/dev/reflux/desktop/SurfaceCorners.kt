package dev.reflux.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.awt.Canvas
import java.awt.EventQueue
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import kotlin.math.roundToInt

/**
 * The window region that rounds the corners of the picture in the mini-player's dock.
 *
 * libmpv draws into a window of its own inside the surface's native window, so nothing the toolkit clips reaches it; the
 * dock's rounded box is drawn around it and the video would show square over the box's corners. A window region on the
 * surface's window does clip what the engine presents (probed with libmpv 0.42 on Windows 11: a flip-model Direct3D 11
 * swapchain, cut symmetrically, 0.3 ms per call). It is cut in device pixels, so the radius is taken in device pixels from the
 * window's own DPI, and the toolkit puts its own rectangular region back whenever it reshapes the surface, so the region is made
 * again after each reshape. Regions have hard edges: the controls window over the surface draws the antialiased rim that
 * hides the steps (docs/DECISIONS.md, 2026-11-13).
 *
 * Only on Windows; elsewhere it does nothing and the picture stays square.
 */
internal class SurfaceCorners(private val canvas: Canvas, private val window: Window?) : AutoCloseable {
    /** The corner radius in dp: 0 across the whole window (no region), the dock picture's radius in the dock. */
    var radiusDp: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            apply()
        }

    private var applied: CornerRegion? = null
    private var closed = false

    private val reshaped = object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) = apply()
        override fun componentMoved(event: ComponentEvent) = apply()
        override fun componentShown(event: ComponentEvent) = apply()
    }
    private val ancestors = object : HierarchyBoundsAdapter() {
        override fun ancestorMoved(event: HierarchyEvent) = apply()
        override fun ancestorResized(event: HierarchyEvent) = apply()
    }
    private val hierarchy = HierarchyListener { event ->
        if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) apply()
    }
    private val monitor = java.beans.PropertyChangeListener { later() }

    init {
        if (WINDOWS) {
            canvas.addComponentListener(reshaped)
            canvas.addHierarchyBoundsListener(ancestors)
            canvas.addHierarchyListener(hierarchy)
            window?.addPropertyChangeListener("graphicsConfiguration", monitor)
        }
    }

    /** Again once the toolkit has finished with the change that is under way (another display's scale arrives before it reshapes). */
    private fun later() = EventQueue.invokeLater { apply() }

    /** Makes the surface's region what [radiusDp] and its size now say. Safe to call at any time and from any thread. */
    fun apply() {
        if (!WINDOWS || closed) return
        runCatching {
            if (!canvas.isDisplayable) return
            val handle = Native.getComponentPointer(canvas) ?: return
            val rect = Rect()
            if (!User32.INSTANCE.GetClientRect(handle, rect)) return
            val region = cornerRegion(rect.right - rect.left, rect.bottom - rect.top, User32.INSTANCE.GetDpiForWindow(handle), radiusDp)
            if (region == null) {
                if (applied != null) User32.INSTANCE.SetWindowRgn(handle, null, true)
                applied = null
                return
            }
            // The toolkit re-applies a plain rectangle on a reshape that leaves our record unchanged, so it is always checked.
            val box = Rect()
            val current = Gdi32.INSTANCE.CreateRectRgn(0, 0, 0, 0)
            val kind = User32.INSTANCE.GetWindowRgn(handle, current)
            val intact = kind == COMPLEXREGION && applied == region && Gdi32.INSTANCE.GetRgnBox(current, box) > 0 &&
                box.right - box.left == region.width && box.bottom - box.top == region.height
            Gdi32.INSTANCE.DeleteObject(current)
            if (intact) return
            // The curve's diameter is twice the radius; the region is one pixel larger because its right and bottom edges are exclusive.
            val made = Gdi32.INSTANCE.CreateRoundRectRgn(0, 0, region.width + 1, region.height + 1, region.radiusPx * 2, region.radiusPx * 2)
            // The system owns a region once it is set; one that was not accepted is ours to free.
            if (User32.INSTANCE.SetWindowRgn(handle, made, true) == 0) Gdi32.INSTANCE.DeleteObject(made) else applied = region
        }.onFailure { failure -> if (!reported) { reported = true; System.err.println("Rounding the video surface failed: $failure") } }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (WINDOWS) {
            canvas.removeComponentListener(reshaped)
            canvas.removeHierarchyBoundsListener(ancestors)
            canvas.removeHierarchyListener(hierarchy)
            window?.removePropertyChangeListener("graphicsConfiguration", monitor)
        }
    }

    private companion object {
        val WINDOWS = System.getProperty("os.name").startsWith("Windows")
        const val COMPLEXREGION = 3
        @Volatile var reported = false
    }
}

/** A region to cut: the surface's size and the corner radius, all in device pixels. */
internal data class CornerRegion(val width: Int, val height: Int, val radiusPx: Int)

/**
 * The region that rounds a surface of [clientWidth] x [clientHeight] device pixels on a display of [dpi] (96 is 100%) to a
 * radius of [radiusDp], or null where there is nothing to cut: no radius, or no surface. A radius never exceeds half the
 * shorter side.
 */
internal fun cornerRegion(clientWidth: Int, clientHeight: Int, dpi: Int, radiusDp: Float): CornerRegion? {
    if (clientWidth <= 0 || clientHeight <= 0 || radiusDp <= 0f) return null
    val radius = (radiusDp * dpi.coerceAtLeast(96) / 96f).roundToInt().coerceAtMost(minOf(clientWidth, clientHeight) / 2)
    return if (radius < 1) null else CornerRegion(clientWidth, clientHeight, radius)
}

@Structure.FieldOrder("left", "top", "right", "bottom")
internal class Rect : Structure() {
    @JvmField var left = 0
    @JvmField var top = 0
    @JvmField var right = 0
    @JvmField var bottom = 0
}

@Suppress("FunctionName")
internal interface User32 : Library {
    fun SetWindowRgn(window: Pointer, region: Pointer?, redraw: Boolean): Int
    fun GetWindowRgn(window: Pointer, region: Pointer): Int
    fun GetClientRect(window: Pointer, rect: Rect): Boolean
    fun GetDpiForWindow(window: Pointer): Int

    companion object {
        val INSTANCE: User32 by lazy { Native.load("user32", User32::class.java) }
    }
}

@Suppress("FunctionName")
internal interface Gdi32 : Library {
    fun CreateRoundRectRgn(left: Int, top: Int, right: Int, bottom: Int, ellipseWidth: Int, ellipseHeight: Int): Pointer
    fun CreateRectRgn(left: Int, top: Int, right: Int, bottom: Int): Pointer
    fun GetRgnBox(region: Pointer, rect: Rect): Int
    fun DeleteObject(handle: Pointer): Boolean

    companion object {
        val INSTANCE: Gdi32 by lazy { Native.load("gdi32", Gdi32::class.java) }
    }
}
