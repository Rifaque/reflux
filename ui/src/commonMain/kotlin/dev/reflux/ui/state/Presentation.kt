package dev.reflux.ui.state

import androidx.compose.ui.geometry.Rect

/**
 * How one [PlaybackSession] is shown. A presentation is never a playback of its own: the session, its engine, its
 * plan, its watch reporting, and the profile it belongs to are the same in every one of them, and moving between
 * them touches none of that (docs/PLAYBACK.md, "One session, several presentations").
 */
enum class PlayerPresentation {
    /** The player has the screen: its controls over the picture, everything behind it inert. */
    FULL,

    /** The picture and a few controls in the navigation's dock, while the viewer browses. */
    MINI,
}

/**
 * Where a shell puts its video surface, as the experience layer asks: all a shell needs to know to present one
 * session in any presentation. The experience layer never knows what the surface is (a native child window, a
 * `SurfaceView`); the shell never decides where it goes.
 */
sealed interface VideoFrame {
    /** The whole window. */
    data object Full : VideoFrame

    /** Inside the mini-player's dock: [bounds] in the window's pixels, from the top left of the experience layer. */
    data class Docked(val bounds: Rect) : VideoFrame

    /**
     * Playing, and not shown: something covers the page the dock is on (a sheet, a question that takes the whole
     * window). A platform's video surface may sit above everything the experience layer draws, so it is not left
     * showing through what covers it; the sound goes on.
     */
    data object Hidden : VideoFrame
}
