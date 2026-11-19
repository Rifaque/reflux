package dev.reflux.desktop.gamepad

import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputModalityTracker
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * One controller at one instant, as its platform reader normalized it: buttons by position (never by
 * label), and the left stick as −1..1 with right and down positive.
 *
 * @param id stable for as long as the controller stays connected.
 */
internal data class GamepadState(
    val id: String,
    val family: ControllerFamily,
    val buttons: Set<GamepadButton>,
    val stickX: Float = 0f,
    val stickY: Float = 0f,
)

/** Reads every connected controller. One implementation per platform API; all of them only ever read. */
internal interface GamepadReader : AutoCloseable {
    /** The controllers connected now. Called from one background thread, many times a second. */
    fun poll(): List<GamepadState>

    /** Whether controller [id] has motors this reader can drive (F-002). */
    fun canRumble(id: String): Boolean = false

    /** Runs controller [id]'s motors at [strength], 0 stopping them. False where it cannot. */
    fun rumble(id: String, strength: Float): Boolean = false

    override fun close() {}
}

/** Controller families by USB vendor, with the product name as a fallback for adapters and clones. */
internal object ControllerFamilies {
    const val SONY = 0x054C
    const val MICROSOFT = 0x045E
    const val NINTENDO = 0x057E

    fun of(vendorId: Int, name: String = ""): ControllerFamily = when {
        vendorId == SONY -> ControllerFamily.PLAYSTATION
        vendorId == MICROSOFT -> ControllerFamily.XBOX
        vendorId == NINTENDO -> ControllerFamily.NINTENDO
        NAMES_PLAYSTATION.containsMatchIn(name) -> ControllerFamily.PLAYSTATION
        NAMES_XBOX.containsMatchIn(name) -> ControllerFamily.XBOX
        NAMES_NINTENDO.containsMatchIn(name) -> ControllerFamily.NINTENDO
        else -> ControllerFamily.GENERIC
    }

    private val NAMES_PLAYSTATION = Regex("""dualsense|dualshock|playstation|\bps[345]\b|wireless controller""", RegexOption.IGNORE_CASE)
    private val NAMES_XBOX = Regex("""x-?box|xinput""", RegexOption.IGNORE_CASE)
    private val NAMES_NINTENDO = Regex("""nintendo|switch|joy-?con""", RegexOption.IGNORE_CASE)
}

/**
 * Button positions by button number for controllers that report plain numbered buttons (HID game
 * controllers without a vendor driver). PlayStation controllers and most generic pads number them west,
 * south, east, north; Nintendo's start at south.
 */
internal object NumberedButtons {
    private val shared = listOf(
        GamepadButton.SHOULDER_LEFT, GamepadButton.SHOULDER_RIGHT, GamepadButton.TRIGGER_LEFT, GamepadButton.TRIGGER_RIGHT,
        GamepadButton.SELECT, GamepadButton.START, GamepadButton.STICK_LEFT, GamepadButton.STICK_RIGHT, GamepadButton.HOME,
    )
    private val standard = listOf(GamepadButton.FACE_WEST, GamepadButton.FACE_SOUTH, GamepadButton.FACE_EAST, GamepadButton.FACE_NORTH) + shared
    private val nintendo = listOf(GamepadButton.FACE_SOUTH, GamepadButton.FACE_EAST, GamepadButton.FACE_WEST, GamepadButton.FACE_NORTH) + shared

    /** The position of button [index] (0-based) on a controller of [family], or null for buttons Reflux has no use for. */
    fun position(index: Int, family: ControllerFamily): GamepadButton? =
        (if (family == ControllerFamily.NINTENDO) nintendo else standard).getOrNull(index)

    /** The buttons set in a bit mask whose bit 0 is button 1. */
    fun fromMask(mask: Int, family: ControllerFamily): Set<GamepadButton> =
        (0 until Int.SIZE_BITS).filter { mask and (1 shl it) != 0 }.mapNotNullTo(mutableSetOf()) { position(it, family) }
}

/**
 * Turns controller snapshots into button presses for the semantic input path.
 *
 * A press is reported once when a button goes down. Held directions repeat, so holding the D-pad travels
 * through a row. The left stick acts as a D-pad once it is clearly deflected and until it is clearly
 * released, so a stick resting off-center does nothing.
 */
internal class GamepadTranslator(
    /** Whether a held button repeats beyond the directions, which always do: the Reflux keyboard's erase and cursor buttons. */
    private val repeats: (GamepadButton, ControllerFamily) -> Boolean = { _, _ -> false },
    /**
     * Whether a button just pressed waits for its release, because holding it means something else (B-052: Back with the
     * mini-player up). Such a button is a press when let go before [holdMs], and [hold] when held that long; never both.
     */
    private val waitsForRelease: (GamepadButton, ControllerFamily) -> Boolean = { _, _ -> false },
    private val hold: (GamepadButton, ControllerFamily) -> Unit = { _, _ -> },
    private val holdMs: Long = 700L,
    /** A button began waiting for its release (true), or stopped waiting: let go, or held long enough (false). */
    private val holding: (GamepadButton, ControllerFamily, Boolean) -> Unit = { _, _, _ -> },
    private val press: (GamepadButton, ControllerFamily) -> Unit,
) {
    /** The controller whose buttons were pressed last: the one in the viewer's hands, whose motors rumble. */
    @Volatile var lastUsed: String? = null
        private set

    private class Held(
        var buttons: Set<GamepadButton> = emptySet(),
        val repeatAt: MutableMap<GamepadButton, Long> = mutableMapOf(),
        var stick: Set<GamepadButton> = emptySet(),
        /** Buttons waiting for their release, and since when; a held one that has acted is no longer here. */
        val waiting: MutableMap<GamepadButton, Long> = mutableMapOf(),
    )

    private val held = mutableMapOf<String, Held>()

    /**
     * @param active false while Reflux is not the application in use: controller APIs are system-wide, and
     * buttons meant for something else must not act here. State is still tracked, so coming back to Reflux
     * with a button held does not count as pressing it.
     */
    fun update(states: List<GamepadState>, nowMs: Long, active: Boolean = true) {
        held.keys.retainAll(states.mapTo(mutableSetOf()) { it.id })
        if (lastUsed != null && states.none { it.id == lastUsed }) lastUsed = null
        for (state in states) {
            val previous = held[state.id] ?: Held().also { known ->
                // A controller is taken as it is found: whatever is already held or deflected when it
                // appears (a button down while plugging in, a worn stick) is not a press.
                known.stick = stickDirections(state, emptySet())
                known.buttons = state.buttons + known.stick
                held[state.id] = known
            }
            previous.stick = stickDirections(state, previous.stick)
            val down = state.buttons + previous.stick
            for (button in down) {
                val repeatAt = previous.repeatAt[button]
                when {
                    button !in previous.buttons -> {
                        if (active) {
                            lastUsed = state.id
                            if (waitsForRelease(button, state.family)) {
                                previous.waiting[button] = nowMs
                                holding(button, state.family, true)
                            } else {
                                press(button, state.family)
                            }
                        }
                        if (button in DIRECTIONS || repeats(button, state.family)) previous.repeatAt[button] = nowMs + REPEAT_DELAY_MS
                    }
                    previous.waiting[button]?.let { since -> nowMs - since >= holdMs } == true -> {
                        previous.waiting.remove(button)
                        holding(button, state.family, false)
                        if (active) hold(button, state.family)
                    }
                    repeatAt != null && nowMs >= repeatAt -> {
                        // A button that no longer repeats (the keyboard closed while it was held) stops here.
                        if (button !in DIRECTIONS && !repeats(button, state.family)) {
                            previous.repeatAt.remove(button)
                            continue
                        }
                        if (active) press(button, state.family)
                        previous.repeatAt[button] = nowMs + REPEAT_INTERVAL_MS
                    }
                }
            }
            previous.repeatAt.keys.retainAll(down)
            // Let go before it was held long enough: it was a press after all.
            for (button in previous.waiting.keys - down) {
                holding(button, state.family, false)
                if (active) press(button, state.family)
            }
            previous.waiting.keys.retainAll(down)
            previous.buttons = down
        }
    }

    private fun stickDirections(state: GamepadState, engaged: Set<GamepadButton>): Set<GamepadButton> {
        fun held(direction: GamepadButton, deflection: Float) = deflection >= if (direction in engaged) STICK_RELEASE else STICK_ENGAGE
        // One axis at a time: a diagonal push follows its stronger component instead of moving twice.
        val horizontal = abs(state.stickX) >= abs(state.stickY)
        return buildSet {
            if (horizontal && held(GamepadButton.DPAD_LEFT, -state.stickX)) add(GamepadButton.DPAD_LEFT)
            if (horizontal && held(GamepadButton.DPAD_RIGHT, state.stickX)) add(GamepadButton.DPAD_RIGHT)
            if (!horizontal && held(GamepadButton.DPAD_UP, -state.stickY)) add(GamepadButton.DPAD_UP)
            if (!horizontal && held(GamepadButton.DPAD_DOWN, state.stickY)) add(GamepadButton.DPAD_DOWN)
        }
    }

    companion object {
        const val REPEAT_DELAY_MS = 420L
        const val REPEAT_INTERVAL_MS = 110L
        const val STICK_ENGAGE = InputModalityTracker.STICK_THRESHOLD + 0.1f
        const val STICK_RELEASE = InputModalityTracker.STICK_THRESHOLD - 0.15f
        private val DIRECTIONS = setOf(GamepadButton.DPAD_UP, GamepadButton.DPAD_DOWN, GamepadButton.DPAD_LEFT, GamepadButton.DPAD_RIGHT)
    }
}

/**
 * Polls the platform's controllers on a background thread and reports button presses.
 *
 * @param active whether Reflux is the application in use right now.
 * @param press called on the polling thread; the shell hands it to the UI thread.
 */
internal class GamepadInput(
    private val reader: GamepadReader,
    private val active: () -> Boolean,
    repeats: (GamepadButton, ControllerFamily) -> Boolean = { _, _ -> false },
    waitsForRelease: (GamepadButton, ControllerFamily) -> Boolean = { _, _ -> false },
    hold: (GamepadButton, ControllerFamily) -> Unit = { _, _ -> },
    holding: (GamepadButton, ControllerFamily, Boolean) -> Unit = { _, _, _ -> },
    press: (GamepadButton, ControllerFamily) -> Unit,
) : AutoCloseable, dev.reflux.ui.state.ControllerRumble {
    private val translator = GamepadTranslator(repeats, waitsForRelease, hold, dev.reflux.ui.state.BACK_HOLD_MS, holding, press)

    @Volatile private var running = true

    /** The controller rumbling now, and when it stops (F-002): stopped by the polling thread, or at once on disconnect. */
    @Volatile private var rumbling: String? = null
    @Volatile private var rumbleUntil = 0L

    override fun available(): Boolean = translator.lastUsed?.let(reader::canRumble) == true

    override fun rumble(strength: Float, durationMs: Long) {
        val id = translator.lastUsed?.takeIf(reader::canRumble) ?: return
        if (runCatching { reader.rumble(id, strength) }.getOrDefault(false)) {
            rumbling = id
            rumbleUntil = System.nanoTime() / 1_000_000 + durationMs
        }
    }

    override fun stop() {
        val id = rumbling ?: return
        rumbling = null
        runCatching { reader.rumble(id, 0f) }
    }

    private val poller = thread(name = "reflux-gamepads", isDaemon = true) {
        while (running) {
            // A reader that fails once is not asked again in a tight loop; controllers are optional.
            val states = runCatching { reader.poll() }.getOrElse { emptyList() }
            val now = System.nanoTime() / 1_000_000
            val inUse = active()
            rumbling?.let { id ->
                // Gone: nothing is left to stop, and nothing resumes on another. Done, or Reflux no longer the application
                // in use: the motors stop.
                if (states.none { it.id == id }) rumbling = null else if (now >= rumbleUntil || !inUse) stop()
            }
            translator.update(states, now, inUse)
            try {
                Thread.sleep(if (states.isEmpty()) IDLE_POLL_MS else POLL_MS)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    override fun close() {
        stop()
        running = false
        poller.interrupt()
        reader.close()
    }

    companion object {
        private const val POLL_MS = 8L
        private const val IDLE_POLL_MS = 250L

        /** The controller reader for this operating system, or null where Reflux has none yet. */
        fun platformReader(os: String = System.getProperty("os.name")): GamepadReader? = when {
            os.startsWith("Windows") -> runCatching { WindowsGamepads() }.getOrNull()
            os.startsWith("Linux") -> LinuxGamepads()
            else -> null
        }
    }
}
