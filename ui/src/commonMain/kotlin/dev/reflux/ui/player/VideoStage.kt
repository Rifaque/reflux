package dev.reflux.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reflux.design.Radius
import dev.reflux.design.Motion
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.VideoFrame
import dev.reflux.ui.theme.tween
import kotlin.math.roundToInt

/**
 * Where a shell's video surface is right now, on its way between the whole window and the mini-player's dock. One
 * per shell; made by [rememberVideoPlacement].
 */
@Stable
class VideoPlacement internal constructor() {
    /** 0 across the whole window, 1 in the dock. */
    internal val docking = Animatable(0f)

    /** Where the dock's picture is, kept after the dock has gone so the way back starts from there. */
    internal var dock: Rect? by mutableStateOf(null)

    internal var hidden by mutableStateOf(false)

    /**
     * How far a back gesture has gone toward stepping aside (0 to 1), read where the surface is laid out: while it is
     * in progress the picture shrinks in place by [PREVIEW_SCALE], and a cancelled gesture lets it go back by itself
     * because nothing but this changed (docs/planning/M10-PLAYER-SPEC.md, section 4.4).
     */
    internal var preview: () -> Float? = { null }

    /**
     * Whether the surface is the whole window, and at rest there. The full player's controls belong over it only
     * then; while it travels, and in the dock, the shell shows the mini-player's own surface instead.
     */
    val full: Boolean by derivedStateOf { docking.value == 0f && !hidden }

    /**
     * The corner radius the picture has at this moment: none across the whole window, [DOCK_PICTURE_RADIUS] in the dock, and
     * in between as far along as it has travelled. A surface a shell composes itself is clipped to this, because the toolkit
     * cannot round what a surface puts on screen: the dock's rounded box is only drawn around it.
     */
    val pictureRadius: Dp get() = DOCK_PICTURE_RADIUS * docking.value
}

/**
 * The radius of the dock's picture: one step below the dock's own (nested shapes step down one size, `Radius`), which is
 * the corner the picture's padding leaves concentric. The dock draws its picture box with the same size.
 */
val DOCK_PICTURE_RADIUS: Dp = Radius.LARGE.dp

/**
 * Follows [AppState.videoFrame] for a shell. A new playback always starts across the whole window.
 *
 * @param travels whether the surface is seen going between the whole window and the dock, or is put there at once.
 * A surface the toolkit composes itself can travel (and takes no time about it when the system asks for less motion).
 * A native window cannot: resizing one on every frame under an engine that is rendering into it comes out as a few
 * uneven jumps (measured on Windows: DECISIONS.md, 2026-11-12), so it moves once, and the dock arriving around it is
 * what carries the eye.
 */
@Composable
fun rememberVideoPlacement(app: AppState, travels: Boolean = true): VideoPlacement {
    val placement = remember(app) { VideoPlacement().also { it.preview = { app.backPreview } } }
    val frame = app.videoFrame
    val playing = app.playback != null
    LaunchedEffect(frame, playing) {
        if (frame is VideoFrame.Docked) placement.dock = frame.bounds
        placement.hidden = frame == VideoFrame.Hidden
        when {
            !playing -> placement.docking.snapTo(0f)
            // Out of sight there is nothing to watch travel: it is where it will be when it is seen again.
            frame == VideoFrame.Hidden -> placement.docking.snapTo(1f)
            !travels -> placement.docking.snapTo(if (frame == VideoFrame.Full) 0f else 1f)
            else -> placement.docking.animateTo(if (frame == VideoFrame.Full) 0f else 1f, Motion.playerDock.tween())
        }
    }
    return placement
}

/**
 * Lays a shell's video surface out where [placement] says: across everything this is given, in the dock, or out of
 * sight while something covers the dock. It fills the window, above the experience layer; only [surface] is drawn.
 *
 * A surface that is out of sight is moved outside the window, not removed or resized: an engine that renders into it
 * keeps doing so undisturbed, and the picture is there the moment it comes back.
 */
@Composable
fun VideoStage(placement: VideoPlacement, modifier: Modifier = Modifier, surface: @Composable () -> Unit) {
    Layout(surface, modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val dock = placement.dock
        val docking = placement.docking.value
        val whole = Rect(0f, 0f, width.toFloat(), height.toFloat())
        val bounds = if (dock == null || docking == 0f) whole else lerp(whole, dock, docking)
        // On its way to being put aside by a gesture: the whole window shrinks toward its middle.
        val previewed = placement.preview()?.takeIf { it > 0f && docking == 0f && !placement.hidden }
            ?.let { lerp(whole, shrunk(whole), it.coerceIn(0f, 1f)) } ?: bounds
        val placeables = measurables.map {
            it.measure(Constraints.fixed(previewed.width.roundToInt().coerceAtLeast(1), previewed.height.roundToInt().coerceAtLeast(1)))
        }
        layout(width, height) {
            val x = if (placement.hidden) -(previewed.width.roundToInt() + OUT_OF_SIGHT_PX) else previewed.left.roundToInt()
            placeables.forEach { it.place(x, previewed.top.roundToInt()) }
        }
    }
}

/** The window as it is drawn at the end of a back gesture's preview: centred, at [PREVIEW_SCALE]. */
internal fun shrunk(whole: Rect): Rect {
    val dx = whole.width * (1f - PREVIEW_SCALE) / 2f
    val dy = whole.height * (1f - PREVIEW_SCALE) / 2f
    return Rect(whole.left + dx, whole.top + dy, whole.right - dx, whole.bottom - dy)
}

/** How small the window gets at the end of a back gesture (Android's own back animations scale to about 90%). */
internal const val PREVIEW_SCALE = 0.9f

private fun lerp(from: Rect, to: Rect, fraction: Float): Rect = Rect(
    from.left + (to.left - from.left) * fraction,
    from.top + (to.top - from.top) * fraction,
    from.right + (to.right - from.right) * fraction,
    from.bottom + (to.bottom - from.bottom) * fraction,
)

/** How far past the window's leading edge a surface that is out of sight is put. */
private const val OUT_OF_SIGHT_PX = 64
