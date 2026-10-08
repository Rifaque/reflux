package dev.reflux.core.input

/**
 * Default physical-to-semantic mappings. They must work well with no configuration (docs/INPUT_MODEL.md).
 *
 * Confirm/cancel follow each family's convention: Nintendo confirms with the east button (A),
 * PlayStation and Xbox with the south button (Cross / A).
 */
object InputMapping {
    fun confirmButton(family: ControllerFamily): GamepadButton =
        if (family == ControllerFamily.NINTENDO) GamepadButton.FACE_EAST else GamepadButton.FACE_SOUTH

    fun cancelButton(family: ControllerFamily): GamepadButton =
        if (family == ControllerFamily.NINTENDO) GamepadButton.FACE_SOUTH else GamepadButton.FACE_EAST

    fun map(button: GamepadButton, family: ControllerFamily, context: InputContext): SemanticAction? {
        if (button == confirmButton(family)) {
            return if (context == InputContext.PLAYBACK) SemanticAction.PLAY_PAUSE else SemanticAction.SELECT
        }
        if (button == cancelButton(family)) return SemanticAction.BACK
        return when (context) {
            InputContext.BROWSE -> when (button) {
                GamepadButton.DPAD_UP -> SemanticAction.NAVIGATE_UP
                GamepadButton.DPAD_DOWN -> SemanticAction.NAVIGATE_DOWN
                GamepadButton.DPAD_LEFT -> SemanticAction.NAVIGATE_LEFT
                GamepadButton.DPAD_RIGHT -> SemanticAction.NAVIGATE_RIGHT
                GamepadButton.START, GamepadButton.FACE_NORTH -> SemanticAction.MENU
                else -> null
            }
            InputContext.PLAYBACK -> when (button) {
                GamepadButton.DPAD_UP -> SemanticAction.NAVIGATE_UP
                GamepadButton.DPAD_DOWN -> SemanticAction.NAVIGATE_DOWN
                GamepadButton.DPAD_LEFT -> SemanticAction.SEEK_BACKWARD
                GamepadButton.DPAD_RIGHT -> SemanticAction.SEEK_FORWARD
                GamepadButton.SHOULDER_LEFT -> SemanticAction.PREVIOUS
                GamepadButton.SHOULDER_RIGHT -> SemanticAction.NEXT
                GamepadButton.TRIGGER_LEFT -> SemanticAction.SPEED_DOWN
                GamepadButton.TRIGGER_RIGHT -> SemanticAction.SPEED_UP
                GamepadButton.START, GamepadButton.FACE_NORTH -> SemanticAction.MENU
                else -> null
            }
        }
    }

    fun map(key: Key, context: InputContext): SemanticAction? = when (key) {
        Key.ARROW_UP -> SemanticAction.NAVIGATE_UP
        Key.ARROW_DOWN -> SemanticAction.NAVIGATE_DOWN
        Key.ARROW_LEFT -> if (context == InputContext.PLAYBACK) SemanticAction.SEEK_BACKWARD else SemanticAction.NAVIGATE_LEFT
        Key.ARROW_RIGHT -> if (context == InputContext.PLAYBACK) SemanticAction.SEEK_FORWARD else SemanticAction.NAVIGATE_RIGHT
        Key.ENTER, Key.DPAD_CENTER ->
            if (context == InputContext.PLAYBACK) SemanticAction.PLAY_PAUSE else SemanticAction.SELECT
        Key.SPACE, Key.K -> if (context == InputContext.PLAYBACK) SemanticAction.PLAY_PAUSE else SemanticAction.SELECT
        Key.ESCAPE, Key.BACKSPACE, Key.BACK -> SemanticAction.BACK
        Key.MENU -> SemanticAction.MENU
        Key.F -> SemanticAction.FULLSCREEN
        Key.M, Key.VOLUME_MUTE -> SemanticAction.MUTE
        Key.J -> if (context == InputContext.PLAYBACK) SemanticAction.SEEK_BACKWARD else null
        Key.L -> if (context == InputContext.PLAYBACK) SemanticAction.SEEK_FORWARD else null
        Key.PERIOD -> if (context == InputContext.PLAYBACK) SemanticAction.SPEED_UP else null
        Key.COMMA -> if (context == InputContext.PLAYBACK) SemanticAction.SPEED_DOWN else null
        Key.PAGE_UP -> if (context == InputContext.PLAYBACK) SemanticAction.PREVIOUS else SemanticAction.NAVIGATE_UP
        Key.PAGE_DOWN -> if (context == InputContext.PLAYBACK) SemanticAction.NEXT else SemanticAction.NAVIGATE_DOWN
        Key.MEDIA_PLAY_PAUSE, Key.MEDIA_PLAY, Key.MEDIA_PAUSE -> SemanticAction.PLAY_PAUSE
        Key.MEDIA_STOP -> SemanticAction.STOP
        Key.MEDIA_NEXT -> SemanticAction.NEXT
        Key.MEDIA_PREVIOUS -> SemanticAction.PREVIOUS
        Key.MEDIA_FAST_FORWARD -> SemanticAction.SEEK_FORWARD
        Key.MEDIA_REWIND -> SemanticAction.SEEK_BACKWARD
        Key.VOLUME_UP -> SemanticAction.VOLUME_UP
        Key.VOLUME_DOWN -> SemanticAction.VOLUME_DOWN
        Key.TAB, Key.OTHER -> null
    }

    /** The label shown in prompts for a button on a given controller family. */
    fun glyph(button: GamepadButton, family: ControllerFamily): String = when (family) {
        ControllerFamily.PLAYSTATION -> when (button) {
            GamepadButton.FACE_SOUTH -> "Cross"
            GamepadButton.FACE_EAST -> "Circle"
            GamepadButton.FACE_WEST -> "Square"
            GamepadButton.FACE_NORTH -> "Triangle"
            GamepadButton.SHOULDER_LEFT -> "L1"
            GamepadButton.SHOULDER_RIGHT -> "R1"
            GamepadButton.TRIGGER_LEFT -> "L2"
            GamepadButton.TRIGGER_RIGHT -> "R2"
            GamepadButton.START -> "Options"
            GamepadButton.SELECT -> "Create"
            GamepadButton.HOME -> "PS"
            else -> neutralGlyph(button)
        }
        ControllerFamily.NINTENDO -> when (button) {
            GamepadButton.FACE_SOUTH -> "B"
            GamepadButton.FACE_EAST -> "A"
            GamepadButton.FACE_WEST -> "Y"
            GamepadButton.FACE_NORTH -> "X"
            GamepadButton.SHOULDER_LEFT -> "L"
            GamepadButton.SHOULDER_RIGHT -> "R"
            GamepadButton.TRIGGER_LEFT -> "ZL"
            GamepadButton.TRIGGER_RIGHT -> "ZR"
            GamepadButton.START -> "+"
            GamepadButton.SELECT -> "−"
            else -> neutralGlyph(button)
        }
        ControllerFamily.XBOX, ControllerFamily.GENERIC, ControllerFamily.TV_REMOTE -> when (button) {
            GamepadButton.FACE_SOUTH -> "A"
            GamepadButton.FACE_EAST -> "B"
            GamepadButton.FACE_WEST -> "X"
            GamepadButton.FACE_NORTH -> "Y"
            GamepadButton.SHOULDER_LEFT -> "LB"
            GamepadButton.SHOULDER_RIGHT -> "RB"
            GamepadButton.TRIGGER_LEFT -> "LT"
            GamepadButton.TRIGGER_RIGHT -> "RT"
            GamepadButton.START -> "Menu"
            GamepadButton.SELECT -> "View"
            else -> neutralGlyph(button)
        }
    }

    private fun neutralGlyph(button: GamepadButton): String = when (button) {
        GamepadButton.DPAD_UP -> "D-pad Up"
        GamepadButton.DPAD_DOWN -> "D-pad Down"
        GamepadButton.DPAD_LEFT -> "D-pad Left"
        GamepadButton.DPAD_RIGHT -> "D-pad Right"
        GamepadButton.STICK_LEFT -> "Left Stick"
        GamepadButton.STICK_RIGHT -> "Right Stick"
        GamepadButton.HOME -> "Home"
        else -> button.name
    }
}
