package dev.reflux.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.Native
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Canvas
import java.awt.Color
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener

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
        return withTimeoutOrNull(SURFACE_TIMEOUT_MS) { ready.await() } ?: run {
            hide()
            null
        }
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
 * [owner] moves and resizes. The window never takes keyboard focus, so keys keep going to [owner].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun VideoOverlay(owner: ComposeWindow, anchor: Component, content: @Composable () -> Unit) {
    val current by rememberUpdatedState(content)
    DisposableEffect(owner, anchor) {
        val overlay = ComposeDialog(owner).apply {
            isUndecorated = true
            isTransparent = true
            isResizable = false
            focusableWindowState = false
            setContent { current() }
        }
        fun align() {
            if (!anchor.isShowing) {
                overlay.isVisible = false
                return
            }
            val origin = anchor.locationOnScreen
            overlay.setBounds(origin.x, origin.y, anchor.width, anchor.height)
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
        align()
        onDispose {
            owner.removeComponentListener(follow)
            anchor.removeComponentListener(follow)
            overlay.dispose()
        }
    }
}
