package dev.reflux.android

import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.state.PlayerPresentation

/**
 * What Android's picture-in-picture needs decided, as pure functions the activity only carries out (docs/planning/
 * M10-PLAYER-SPEC.md, section 6): when leaving the app may shrink it into a window, what shape that window is, and whether
 * the window the viewer has just left was expanded or closed.
 */
internal object PictureInPicture {
    /**
     * Whether leaving the app now should shrink it into the system's window: a picture is moving and nothing needs the
     * viewer. Not when it is paused (a paused picture left in a corner is not what leaving meant), not on a failure or on a
     * question, and not when it is already there.
     */
    fun eligible(presentation: PlayerPresentation?, status: PlayerStatus?, failed: Boolean, asked: Boolean): Boolean =
        presentation != null && presentation != PlayerPresentation.SYSTEM && !failed && !asked &&
            (status == PlayerStatus.PLAYING || status == PlayerStatus.BUFFERING)

    /**
     * The window's shape as the system takes it: width and height of a ratio between 1:2.39 and 2.39:1 (anything outside it
     * throws), or null while the picture's shape is not known.
     */
    fun shape(aspect: Float?): Pair<Int, Int>? {
        if (aspect == null || !aspect.isFinite() || aspect <= 0f) return null
        val clamped = aspect.coerceIn(MIN_ASPECT, MAX_ASPECT)
        return (clamped * 1000).toInt() to 1000
    }

    /**
     * Whether leaving the window was the viewer bringing the app back (expanded) rather than closing it, from whether the
     * activity is still started when the system says the window is gone. A closed window stops the activity; an expanded one
     * keeps it (VERIFICATION.md, M10 probe P2).
     */
    fun expanded(activityStarted: Boolean): Boolean = activityStarted

    const val MIN_ASPECT = 0.42f
    const val MAX_ASPECT = 2.39f
}
