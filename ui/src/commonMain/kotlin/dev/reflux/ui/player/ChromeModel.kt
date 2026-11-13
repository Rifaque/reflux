package dev.reflux.ui.player

import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.state.PlayerPresentation

/**
 * What the full player draws over the picture, as one value (docs/planning/M10-PLAYER-SPEC.md, section 7).
 *
 * It is derived, never stored and never set by a component: the player's state and the viewer's last doing go in, and the
 * layers that may be drawn come out. A layer that a mode does not list is absent, so a loader beside a transport, or a
 * stale pause button over a wait, cannot be expressed.
 */
enum class ChromeMode {
    /** Not the full player's to draw: the mini-player, the system's window, or the window is arriving there. */
    NONE,

    /** The picture alone, but for an offer to skip and what is said to the viewer. */
    HIDDEN,

    /** The controls: title and the way out, the transport, the bar, an open list. */
    SHOWN,

    /** Opening, or waiting for data: the Reflux loader and the way out, and nothing that cannot act on a wait. */
    LOADING,

    /** The next episode's countdown, or "Still watching?". */
    ENDING,

    /** Playback stopped: what happened, and the way on. */
    FAILURE,
}

/** What the player's state says, as far as the chrome is concerned. */
data class ChromeFacts(
    val presentation: PlayerPresentation,
    val status: PlayerStatus,
    /** When buffering began (on [ChromeClock]), or null when it is not. */
    val bufferingSinceMs: Long?,
    val failed: Boolean,
    /** A countdown to the next episode, or the question of whether the viewer is still watching, is showing. */
    val postPlay: Boolean,
    /** Android is animating the window into picture-in-picture (API 35+): the controls go before it starts. */
    val systemTransition: Boolean = false,
)

/** Why the controls stay up whatever the clock says. */
enum class Pin(val hard: Boolean) {
    /** Playback is paused: a paused picture with no controls looks frozen. A touch on the picture may still put them away. */
    PAUSED(false),

    /** A list is open. */
    MENU(true),

    /** A screen reader is running: controls that left could never be found again by reading. */
    SCREEN_READER(true),

    /** Keyboard, controller, or remote focus is inside the controls. */
    FOCUS_IN_CONTROLS(false),

    /** A pointer rests over the controls. */
    POINTER_OVER_CONTROLS(false),

    /** The timeline is being dragged. */
    SCRUBBING(true),

    /** A seek key is held down, and the target is showing. */
    SEEK_HOLD(true),
}

/** What the viewer's doing says: the deadline their last action set, what pins the controls, and whether a touch put them away. */
data class ChromeHold(
    val revealedUntilMs: Long = 0,
    val pins: Set<Pin> = emptySet(),
    /** The viewer touched the picture to put the controls away: what pins softly (a pause, a resting pointer) no longer keeps them. */
    val dismissed: Boolean = false,
) {
    internal fun pinned(): Boolean = pins.any { it.hard || !dismissed }
}

/** The time the chrome's deadlines are on: milliseconds from any one origin. */
object ChromeClock {
    /** Buffering shorter than this changes nothing: a seek or ten seconds back is not a wait the viewer should see. Opening is never given the grace. */
    const val LOADING_GRACE_MS = 600L
}

/**
 * The one rule. In order, the first that applies:
 *
 * 1. Not the full player ([PlayerPresentation.FULL]), or entering the system's window: [ChromeMode.NONE].
 * 2. A failure: [ChromeMode.FAILURE].
 * 3. The next episode's prompt: [ChromeMode.ENDING].
 * 4. Opening: [ChromeMode.LOADING]. Buffering for longer than [ChromeClock.LOADING_GRACE_MS] is LOADING too, unless the
 *    viewer is dragging the timeline or holding a seek (they are waiting for their own seek, and see the target).
 * 5. Pinned, or within the time of the viewer's last action: [ChromeMode.SHOWN].
 * 6. [ChromeMode.HIDDEN].
 */
fun chrome(facts: ChromeFacts, hold: ChromeHold, nowMs: Long): ChromeMode = when {
    facts.presentation != PlayerPresentation.FULL -> ChromeMode.NONE
    facts.systemTransition -> ChromeMode.NONE
    facts.failed -> ChromeMode.FAILURE
    facts.postPlay -> ChromeMode.ENDING
    facts.status == PlayerStatus.LOADING -> ChromeMode.LOADING
    facts.status == PlayerStatus.BUFFERING && waitedPastGrace(facts, hold, nowMs) -> ChromeMode.LOADING
    hold.pinned() || nowMs < hold.revealedUntilMs -> ChromeMode.SHOWN
    else -> ChromeMode.HIDDEN
}

private fun waitedPastGrace(facts: ChromeFacts, hold: ChromeHold, nowMs: Long): Boolean {
    val since = facts.bufferingSinceMs ?: return false
    if (Pin.SCRUBBING in hold.pins || Pin.SEEK_HOLD in hold.pins) return false
    return nowMs - since >= ChromeClock.LOADING_GRACE_MS
}

/**
 * The one moment the chrome will change by itself, if nothing else does: the end of the buffering grace, or the end of
 * the controls' time. Null when only a new fact can change it. The player waits for this and for nothing else.
 */
fun nextChange(facts: ChromeFacts, hold: ChromeHold, nowMs: Long): Long? {
    if (facts.presentation != PlayerPresentation.FULL || facts.systemTransition || facts.failed || facts.postPlay) return null
    val deadlines = ArrayList<Long>(2)
    val since = facts.bufferingSinceMs
    if (facts.status == PlayerStatus.BUFFERING && since != null && Pin.SCRUBBING !in hold.pins && Pin.SEEK_HOLD !in hold.pins) {
        (since + ChromeClock.LOADING_GRACE_MS).takeIf { it > nowMs }?.let(deadlines::add)
    }
    if (!hold.pinned() && hold.revealedUntilMs > nowMs) deadlines += hold.revealedUntilMs
    return deadlines.minOrNull()
}

/** What each mode may draw, so that a layer outside its mode is absent by the model and a test can say so. */
data class ChromeLayers(
    /** The way out and what is playing, at the top. */
    val context: Boolean,
    /** Minimize beside it, where there is a mini-player and nothing is wrong. */
    val minimize: Boolean,
    /** Play, pause, and the ten-second seeks in the middle of the picture (touch layouts). */
    val centreTransport: Boolean,
    /** The bottom row: timeline, times, and the actions. */
    val bar: Boolean,
    /** An open list, and the tracks and speed that open it. */
    val menus: Boolean,
    val loader: Boolean,
    /** The offer to skip an intro or the credits. */
    val skipOffer: Boolean,
    val upNext: Boolean,
    val failure: Boolean,
)

fun ChromeMode.layers(): ChromeLayers = when (this) {
    ChromeMode.NONE -> ChromeLayers(false, false, false, false, false, false, false, false, false)
    ChromeMode.HIDDEN -> ChromeLayers(false, false, false, false, false, false, true, false, false)
    ChromeMode.SHOWN -> ChromeLayers(true, true, true, true, true, false, true, false, false)
    ChromeMode.LOADING -> ChromeLayers(true, true, false, false, false, true, false, false, false)
    ChromeMode.ENDING -> ChromeLayers(true, true, false, false, false, false, false, true, false)
    // A failure is answered where it is: it is not put aside into the mini-player.
    ChromeMode.FAILURE -> ChromeLayers(true, false, false, false, false, false, false, false, true)
}
