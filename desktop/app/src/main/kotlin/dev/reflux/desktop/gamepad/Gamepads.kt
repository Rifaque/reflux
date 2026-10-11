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
internal class GamepadTranslator(private val press: (GamepadButton, ControllerFamily) -> Unit) {
    private class Held(var buttons: Set<GamepadButton> = emptySet(), val repeatAt: MutableMap<GamepadButton, Long> = mutableMapOf(), var stick: Set<GamepadButton> = emptySet())

    private val held = mutableMapOf<String, Held>()

    /**
     * @param active false while Reflux is not the application in use: controller APIs are system-wide, and
     * buttons meant for something else must not act here. State is still tracked, so coming back to Reflux
     * with a button held does not count as pressing it.
     */
    fun update(states: List<GamepadState>, nowMs: Long, active: Boolean = true) {
        held.keys.retainAll(states.mapTo(mutableSetOf()) { it.id })
        for (state in states) {
            val previous = held.getOrPut(state.id) { Held() }
            previous.stick = stickDirections(state, previous.stick)
            val down = state.buttons + previous.stick
            for (button in down) {
                val repeatAt = previous.repeatAt[button]
                when {
                    button !in previous.buttons -> {
                        if (active) press(button, state.family)
                        if (button in DIRECTIONS) previous.repeatAt[button] = nowMs + REPEAT_DELAY_MS
                    }
                    repeatAt != null && nowMs >= repeatAt -> {
                        if (active) press(button, state.family)
                        previous.repeatAt[button] = nowMs + REPEAT_INTERVAL_MS
                    }
                }
            }
            previous.repeatAt.keys.retainAll(down)
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
    press: (GamepadButton, ControllerFamily) -> Unit,
) : AutoCloseable {
    private val translator = GamepadTranslator(press)

    @Volatile private var running = true

    private val poller = thread(name = "reflux-gamepads", isDaemon = true) {
        while (running) {
            // A reader that fails once is not asked again in a tight loop; controllers are optional.
            val states = runCatching { reader.poll() }.getOrElse { emptyList() }
            translator.update(states, System.nanoTime() / 1_000_000, active())
            try {
                Thread.sleep(if (states.isEmpty()) IDLE_POLL_MS else POLL_MS)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    override fun close() {
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
