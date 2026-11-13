package dev.reflux.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sun.jna.Native
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Canvas
import java.awt.Color
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent

/**
 * The native surface libmpv renders into.
 *
 * Video goes straight from the engine to a native child window (mpv's `wid`), which keeps hardware
 * decoding zero-copy and lets the engine drive the display directly (HDR output, frame timing). Because a
 * native surface always sits above toolkit-drawn content in the same window, the player's controls are
 * drawn in a transparent window layered over it ([VideoOverlay]). See docs/DECISIONS.md.
 */
internal class VideoSurface {
    /** The surface to show, or null while nothing is playing. A new one is made for every player. */
    var canvas: Canvas? by mutableStateOf(null)
        private set

    /** Shows a new surface and returns its native window ID once the toolkit has created it. */
    suspend fun show(): Long? {
        val ready = CompletableDeferred<Long>()
        canvas = object : Canvas() {
            override fun addNotify() {
                super.addNotify()
                ready.complete(Native.getComponentID(this))
            }
        }.apply {
            name = NAME
            background = Color.BLACK
            isFocusable = false
        }
        val id = withTimeoutOrNull(SURFACE_TIMEOUT_MS) { ready.await() } ?: run {
            hide()
            return null
        }
        // The engine reaches the surface through a connection of its own. The toolkit only queues the
        // request that creates the native window, so without this the engine can be handed the ID of a
        // window the display server has not heard of yet, and its video output then never starts.
        Toolkit.getDefaultToolkit().sync()
        return id
    }

    fun hide() {
        canvas = null
    }

    companion object {
        /** The component name of the surface, for telling it apart from the toolkit's own canvases. */
        const val NAME = "reflux-video"
        private const val SURFACE_TIMEOUT_MS = 5_000L
    }
}

/**
 * Hosts [content] in a transparent, undecorated window that exactly covers [anchor] and follows it as
 * [owner] moves and resizes, and as [anchor] itself is moved inside it (to the mini-player's dock and back). The
 * window never takes keyboard focus, so keys keep going to [owner]; it belongs to [owner], so it is above that window
 * and no other, and is never on top of other applications.
 *
 * @param shown false while the surface is out of sight (something covers the dock): the window goes with it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun VideoOverlay(owner: ComposeWindow, anchor: Component, shown: Boolean = true, content: @Composable () -> Unit) {
    val current by rememberUpdatedState(content)
    val visible by rememberUpdatedState(shown)
    var realign by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Out of sight and back again is not a move of the surface that a listener would hear of.
    SideEffect { realign?.invoke() }
    DisposableEffect(owner, anchor) {
        val overlay = ComposeDialog(owner).apply {
            isUndecorated = true
            isTransparent = true
            isResizable = false
            focusableWindowState = false
            setContent { current() }
        }
        fun align() {
            if (!anchor.isShowing || !visible) {
                overlay.isVisible = false
                return
            }
            val origin = anchor.locationOnScreen
            overlay.setBounds(origin.x, origin.y, anchor.width, anchor.height)
            // A window the viewer is dragging the edge of is resized inside the operating system's own sizing loop, and a window
            // resized there is not laid out again by the toolkit: its dialog had the new size while its root pane, and the Compose
            // layer in it, kept the old one (measured: 1684 x 964 around 1000 x 640), so the controls were composed for a
            // window that was no longer there. Laying the dialog out is what hands the content its size.
            overlay.validate()
            if (!overlay.isVisible) overlay.isVisible = true
        }
        val follow: ComponentListener = object : ComponentAdapter() {
            override fun componentMoved(event: ComponentEvent) = align()
            override fun componentResized(event: ComponentEvent) = align()
            override fun componentShown(event: ComponentEvent) = align()
            override fun componentHidden(event: ComponentEvent) = align()
        }
        owner.addComponentListener(follow)
        anchor.addComponentListener(follow)
        // The surface is the toolkit's child of a holder that Compose places: on the way to the dock it is the holder
        // that moves, and the surface hears of it as an ancestor's move.
        val ancestors = object : HierarchyBoundsAdapter() {
            override fun ancestorMoved(event: HierarchyEvent) = align()
            override fun ancestorResized(event: HierarchyEvent) = align()
        }
        anchor.addHierarchyBoundsListener(ancestors)
        realign = ::align
        align()
        onDispose {
            realign = null
            owner.removeComponentListener(follow)
            anchor.removeComponentListener(follow)
            anchor.removeHierarchyBoundsListener(ancestors)
            overlay.dispose()
        }
    }
}

/**
 * Rounds what the controls window draws over the picture to [radius] and draws the picture's rim: a 1.5 dp black arc at
 * each corner, centred on the edge of the surface's window region. A region's edge is hard; this antialiased rim is what the
 * eye sees (the black is the picture box's own colour). It draws arcs only, so the straight edges get no frame line.
 */
internal fun Modifier.pictureRim(radius: Dp): Modifier =
    if (radius.value <= 0f) this else clip(RoundedCornerShape(radius)).drawWithContent {
        drawContent()
        val stroke = 1.5.dp.toPx()
        val half = stroke / 2f
        val r = radius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
        val diameter = 2f * (r - half)
        val style = Stroke(width = stroke, cap = StrokeCap.Butt)
        val arc = Size(diameter, diameter)
        drawArc(androidx.compose.ui.graphics.Color.Black, 180f, 90f, false, Offset(half, half), arc, style = style)
        drawArc(androidx.compose.ui.graphics.Color.Black, 270f, 90f, false, Offset(size.width - 2f * r + half, half), arc, style = style)
        drawArc(androidx.compose.ui.graphics.Color.Black, 0f, 90f, false, Offset(size.width - 2f * r + half, size.height - 2f * r + half), arc, style = style)
        drawArc(androidx.compose.ui.graphics.Color.Black, 90f, 90f, false, Offset(half, size.height - 2f * r + half), arc, style = style)
    }
