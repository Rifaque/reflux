package dev.reflux.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What happened, for feedback that is felt (F-001, F-002). A short, closed list: feature code names an event, never a
 * pattern, a strength, or a vibrator.
 */
enum class HapticEvent {
    /** Something chosen took effect: a profile opened, a pull that will refresh. */
    CONFIRM,

    /** A switch changed. */
    TOGGLE,

    /** A direction had nowhere to go: the end of a row, the edge of the keyboard. */
    BOUNDARY,

    /** Focus moved to the next item with a controller: the lightest tick, and never on every repeat of a held direction. */
    NAVIGATE,

    /** Something was refused: a wrong PIN. */
    ERROR,
}

/** The device's own vibration, for interface haptics on a phone or a tablet (F-001). Implemented by the Android shell only. */
interface DeviceHaptics {
    /** Whether this device can vibrate at all. */
    val available: Boolean

    /** One effect for [event]; the shell chooses the platform's own effect for it, which follows the system's settings. */
    fun perform(event: HapticEvent)
}

/** The motors of the controller in use (F-002). Implemented where the platform can drive them (Windows XInput, Linux force feedback, Android). */
interface ControllerRumble {
    /** Whether the controller in use now has motors Reflux can drive. */
    fun available(): Boolean

    /** Runs the motors at [strength] (0 to 1) for [durationMs], then stops them. */
    fun rumble(strength: Float, durationMs: Long)

    /** Stops the motors now. */
    fun stop()
}

/**
 * The one way anything in Reflux makes a device vibrate or a controller rumble (F-001, F-002; docs/history/phases/PHASE-2-PLAN.md,
 * "Haptics"). Every request passes through here, and here alone the viewer's choices are enforced:
 *
 * - **Interface haptics** are **off** until the viewer turns them on. Off, nothing reaches the device's vibrator, by any
 *   path (the toolkit's own haptics, text selection included, are routed here too). They answer touch only: a controller
 *   in the hand never makes the device vibrate.
 * - **Controller rumble** is **on** unless the viewer turns it off, and only for a controller that has motors.
 * - Feedback is sparse: each event has a minimum interval, so held keys, repeats, and animations never become a buzz.
 *
 * Unsupported hardware (no vibrator, a controller without motors, a platform with no way to drive them) is a quiet no-op.
 * Video-synchronised haptics are Phase 3's (F-003) and are not here.
 */
class Haptics(
    private val device: () -> DeviceHaptics?,
    private val controller: () -> ControllerRumble?,
    private val now: () -> Long,
) {
    /** Interface haptics (F-001). Off by default: nothing vibrates before the viewer opts in. */
    var interfaceOn by mutableStateOf(false)
        internal set

    /** Controller rumble (F-002). On by default, and the viewer can turn it off. */
    var rumbleOn by mutableStateOf(true)
        internal set

    /** Whether this device can give interface haptics at all, for Settings to offer the choice only where it means something. */
    val interfaceAvailable: Boolean get() = device()?.available == true

    private val lastDevice = HashMap<HapticEvent, Long>()
    private val lastController = HashMap<HapticEvent, Long>()

    /** Feedback for [event] on a touchscreen: the device vibrates only if the viewer turned interface haptics on. */
    fun touch(event: HapticEvent) {
        if (!interfaceOn) return
        val target = device()?.takeIf { it.available } ?: return
        if (!due(lastDevice, event)) return
        runCatching { target.perform(event) }
    }

    /** Feedback for [event] from a controller in the hand: its motors, if rumble is on and it has them. */
    fun controller(event: HapticEvent) {
        if (!rumbleOn) return
        val target = controller()?.takeIf { runCatching { it.available() }.getOrDefault(false) } ?: return
        if (!due(lastController, event)) return
        val (strength, duration) = PATTERNS.getValue(event)
        runCatching { target.rumble(strength, duration) }
    }

    /** A controller went away, or rumble was turned off: whatever is running stops at once. */
    fun stopRumble() {
        runCatching { controller()?.stop() }
    }

    private fun due(last: HashMap<HapticEvent, Long>, event: HapticEvent): Boolean {
        val time = now()
        val previous = last[event]
        if (previous != null && time - previous < MIN_INTERVAL_MS.getValue(event)) return false
        last[event] = time
        return true
    }

    companion object {
        /** Strength (0 to 1) and length of each event's rumble: short and light; an error a little longer. */
        val PATTERNS = mapOf(
            HapticEvent.CONFIRM to (0.30f to 40L),
            HapticEvent.TOGGLE to (0.25f to 30L),
            HapticEvent.BOUNDARY to (0.35f to 45L),
            HapticEvent.NAVIGATE to (0.18f to 22L),
            HapticEvent.ERROR to (0.55f to 140L),
        )

        /** How often each event may be felt at most. A boundary held against repeats is felt once in a while, not on every repeat. */
        val MIN_INTERVAL_MS = mapOf(
            HapticEvent.CONFIRM to 150L,
            HapticEvent.TOGGLE to 120L,
            HapticEvent.BOUNDARY to 450L,
            // A held direction repeats about every 110 ms: it is felt about once in three repeats, a light patter, not a buzz.
            HapticEvent.NAVIGATE to 300L,
            HapticEvent.ERROR to 400L,
        )

        /** The device settings the choices are kept in. */
        const val INTERFACE_HAPTICS = "interface_haptics"
        const val CONTROLLER_RUMBLE = "controller_rumble"
    }
}
