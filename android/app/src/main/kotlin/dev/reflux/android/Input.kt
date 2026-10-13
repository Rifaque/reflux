package dev.reflux.android

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.Key
import kotlin.math.abs

/**
 * Normalizes Android's input events into Reflux's vocabulary: keys from keyboards, media keys, and remotes
 * become [Key]s, controller buttons become [GamepadButton]s named by position. What they mean is decided
 * by the experience layer, the same as on every other platform.
 */
internal object Input {
    /** The controller button this event is a press of, or null when it does not come from a controller. */
    fun gamepadButton(event: KeyEvent): GamepadButton? {
        val fromController = event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> GamepadButton.FACE_SOUTH
            KeyEvent.KEYCODE_BUTTON_B -> GamepadButton.FACE_EAST
            KeyEvent.KEYCODE_BUTTON_X -> GamepadButton.FACE_WEST
            KeyEvent.KEYCODE_BUTTON_Y -> GamepadButton.FACE_NORTH
            KeyEvent.KEYCODE_BUTTON_L1 -> GamepadButton.SHOULDER_LEFT
            KeyEvent.KEYCODE_BUTTON_R1 -> GamepadButton.SHOULDER_RIGHT
            KeyEvent.KEYCODE_BUTTON_L2 -> GamepadButton.TRIGGER_LEFT
            KeyEvent.KEYCODE_BUTTON_R2 -> GamepadButton.TRIGGER_RIGHT
            KeyEvent.KEYCODE_BUTTON_START -> GamepadButton.START
            KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadButton.SELECT
            KeyEvent.KEYCODE_BUTTON_THUMBL -> GamepadButton.STICK_LEFT
            KeyEvent.KEYCODE_BUTTON_THUMBR -> GamepadButton.STICK_RIGHT
            KeyEvent.KEYCODE_BUTTON_MODE -> GamepadButton.HOME
            // A directional pad exists on keyboards and remotes too; it is a controller's only when one sent it.
            KeyEvent.KEYCODE_DPAD_UP -> GamepadButton.DPAD_UP.takeIf { fromController }
            KeyEvent.KEYCODE_DPAD_DOWN -> GamepadButton.DPAD_DOWN.takeIf { fromController }
            KeyEvent.KEYCODE_DPAD_LEFT -> GamepadButton.DPAD_LEFT.takeIf { fromController }
            KeyEvent.KEYCODE_DPAD_RIGHT -> GamepadButton.DPAD_RIGHT.takeIf { fromController }
            else -> null
        }
    }

    /**
     * The key this event is a press of, or null for keys Reflux has no meaning for. Presses with Ctrl, Alt,
     * or Meta held are left to the system, and so are the volume keys and Back, which Android routes itself.
     */
    fun key(event: KeyEvent): Key? {
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return null
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> Key.ARROW_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Key.ARROW_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> Key.ARROW_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Key.ARROW_RIGHT
            KeyEvent.KEYCODE_DPAD_CENTER -> Key.DPAD_CENTER
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> Key.ENTER
            KeyEvent.KEYCODE_SPACE -> Key.SPACE
            KeyEvent.KEYCODE_ESCAPE -> Key.ESCAPE
            KeyEvent.KEYCODE_DEL -> Key.BACKSPACE
            KeyEvent.KEYCODE_TAB -> Key.TAB
            KeyEvent.KEYCODE_F -> Key.F
            KeyEvent.KEYCODE_M -> Key.M
            KeyEvent.KEYCODE_K -> Key.K
            KeyEvent.KEYCODE_J -> Key.J
            KeyEvent.KEYCODE_L -> Key.L
            KeyEvent.KEYCODE_PERIOD -> Key.PERIOD
            KeyEvent.KEYCODE_COMMA -> Key.COMMA
            KeyEvent.KEYCODE_PAGE_UP -> Key.PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> Key.PAGE_DOWN
            KeyEvent.KEYCODE_MENU -> Key.MENU
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> Key.MEDIA_PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY -> Key.MEDIA_PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE -> Key.MEDIA_PAUSE
            KeyEvent.KEYCODE_MEDIA_STOP -> Key.MEDIA_STOP
            KeyEvent.KEYCODE_MEDIA_NEXT -> Key.MEDIA_NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> Key.MEDIA_PREVIOUS
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> Key.MEDIA_FAST_FORWARD
            KeyEvent.KEYCODE_MEDIA_REWIND -> Key.MEDIA_REWIND
            else -> null
        }
    }

    /** Which family of controller a device is, for showing its own button names. Unknown ones are generic. */
    fun family(device: InputDevice?): ControllerFamily = when (device?.vendorId) {
        0x054C -> ControllerFamily.PLAYSTATION
        0x045E -> ControllerFamily.XBOX
        0x057E -> ControllerFamily.NINTENDO
        else -> ControllerFamily.GENERIC
    }
}

/**
 * Turns a controller's stick and hat into directional presses: one press when a direction is pushed, none
 * while it is held, another after it was let go. Android reports them as positions, not as buttons.
 */
internal class StickDirections {
    private var horizontal = 0
    private var vertical = 0

    /** The directions newly pushed by [event], which must be a joystick motion. */
    fun presses(event: MotionEvent): List<GamepadButton> {
        val x = direction(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_X))
        val y = direction(event.getAxisValue(MotionEvent.AXIS_HAT_Y), event.getAxisValue(MotionEvent.AXIS_Y))
        return changes(x, y)
    }

    internal fun changes(x: Int, y: Int): List<GamepadButton> = buildList {
        if (x != horizontal) {
            horizontal = x
            if (x < 0) add(GamepadButton.DPAD_LEFT) else if (x > 0) add(GamepadButton.DPAD_RIGHT)
        }
        if (y != vertical) {
            vertical = y
            if (y < 0) add(GamepadButton.DPAD_UP) else if (y > 0) add(GamepadButton.DPAD_DOWN)
        }
    }

    private fun direction(hat: Float, stick: Float): Int = when {
        abs(hat) > 0.5f -> if (hat < 0) -1 else 1
        abs(stick) > STICK_THRESHOLD -> if (stick < 0) -1 else 1
        else -> 0
    }

    private companion object {
        const val STICK_THRESHOLD = 0.6f
    }
}
