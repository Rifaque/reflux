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

    /**
     * The system's own window: Android's picture-in-picture. Reflux draws no page and no player in it, only the picture;
     * what the system draws over it reaches the session through its media session. Set only by a shell, only when the
     * platform has confirmed the window ([PlaybackSession.enterSystem]), and left the same way ([PlaybackSession.leaveSystem]).
     */
    SYSTEM,
}

/**
 * What Back does now, derived from the state ([AppState.backRoute]). A platform shell enables one handler to match, or none.
 */
enum class BackRoute {
    /** Reflux has nothing to do with Back: the platform's own runs (back-to-home, leaving the app). */
    SYSTEM,

    /** Reflux handles it as the semantic action `BACK`, with no gesture preview. */
    APP,

    /** The full player steps into the mini-player: a gesture's progress may preview it. */
    COLLAPSE,

    /** What plays in the dock goes on in the system's own window. */
    SYSTEM_PRESENTATION,
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
