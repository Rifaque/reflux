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
        // The view/create button toggles fullscreen everywhere, like F on a keyboard.
        if (button == GamepadButton.SELECT) return SemanticAction.FULLSCREEN
        return when (context) {
            InputContext.BROWSE -> when (button) {
                GamepadButton.DPAD_UP -> SemanticAction.NAVIGATE_UP
                GamepadButton.DPAD_DOWN -> SemanticAction.NAVIGATE_DOWN
                GamepadButton.DPAD_LEFT -> SemanticAction.NAVIGATE_LEFT
                GamepadButton.DPAD_RIGHT -> SemanticAction.NAVIGATE_RIGHT
                // Y goes to the navigation and back (F-P2); Start, like a remote's Menu, opens Sources.
                GamepadButton.FACE_NORTH -> SemanticAction.TOGGLE_NAVIGATION
                GamepadButton.START -> SemanticAction.MENU
                // X is the focused item's contextual action: Media Quick View (F-P3, the owner's decision of 2026-11-19).
                GamepadButton.FACE_WEST -> SemanticAction.CONTEXT
                // LT hides the hints and brings them back (B-055); RT is the mini-player's close (AppState, B-052).
                GamepadButton.TRIGGER_LEFT -> SemanticAction.TOGGLE_HINTS
                // The bumpers step through the global destinations, as on a console.
                GamepadButton.SHOULDER_LEFT -> SemanticAction.PREVIOUS_SECTION
                GamepadButton.SHOULDER_RIGHT -> SemanticAction.NEXT_SECTION
                else -> null
            }
            // The Reflux keyboard (B-023): the D-pad moves among its keys, confirm types one, and the other buttons
            // are the editing a console keyboard gives them: X erases, Y is a space, the bumpers move the cursor, the
            // triggers change case and page, and Start finishes. No destination, section, or Menu while typing.
            InputContext.TEXT_ENTRY -> when (button) {
                GamepadButton.DPAD_UP -> SemanticAction.NAVIGATE_UP
                GamepadButton.DPAD_DOWN -> SemanticAction.NAVIGATE_DOWN
                GamepadButton.DPAD_LEFT -> SemanticAction.NAVIGATE_LEFT
                GamepadButton.DPAD_RIGHT -> SemanticAction.NAVIGATE_RIGHT
                GamepadButton.FACE_WEST -> SemanticAction.TEXT_ERASE
                GamepadButton.FACE_NORTH -> SemanticAction.TEXT_SPACE
                GamepadButton.SHOULDER_LEFT -> SemanticAction.CURSOR_BACK
                GamepadButton.SHOULDER_RIGHT -> SemanticAction.CURSOR_FORWARD
                GamepadButton.TRIGGER_LEFT -> SemanticAction.TEXT_SHIFT
                GamepadButton.TRIGGER_RIGHT -> SemanticAction.TEXT_SYMBOLS
                GamepadButton.START -> SemanticAction.TEXT_DONE
                else -> null
            }
            InputContext.PLAYBACK -> when (button) {
                GamepadButton.DPAD_UP -> SemanticAction.NAVIGATE_UP
                GamepadButton.DPAD_DOWN -> SemanticAction.NAVIGATE_DOWN
                GamepadButton.DPAD_LEFT -> SemanticAction.SEEK_BACKWARD
                GamepadButton.DPAD_RIGHT -> SemanticAction.SEEK_FORWARD
                GamepadButton.SHOULDER_LEFT -> SemanticAction.PREVIOUS
                GamepadButton.SHOULDER_RIGHT -> SemanticAction.NEXT
                // LT shows and hides the hints here as everywhere (the owner's walk, 2026-11-20); speed is in Options, so
                // the triggers no longer change it.
                GamepadButton.TRIGGER_LEFT -> SemanticAction.TOGGLE_HINTS
                GamepadButton.START, GamepadButton.FACE_NORTH -> SemanticAction.MENU
                else -> null
            }
        }
    }

    fun map(key: Key, context: InputContext): SemanticAction? = if (context == InputContext.TEXT_ENTRY) typingOnKeyboard(key) else when (key) {
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
        // A keyboard without a Menu key still reaches the player's lists. Outside the player a letter means nothing.
        Key.O -> if (context == InputContext.PLAYBACK) SemanticAction.MENU else null
        // The player steps aside into its dock, and comes back from it; with nothing playing it means nothing.
        Key.I -> SemanticAction.MINI_PLAYER
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
        // Destinations are not reached from inside the player: it is left first.
        Key.SHORTCUT_1 -> SemanticAction.GO_HOME.takeIf { context == InputContext.BROWSE }
        Key.SHORTCUT_2 -> SemanticAction.GO_MOVIES.takeIf { context == InputContext.BROWSE }
        Key.SHORTCUT_3 -> SemanticAction.GO_SHOWS.takeIf { context == InputContext.BROWSE }
        Key.SHORTCUT_4 -> SemanticAction.GO_SEARCH.takeIf { context == InputContext.BROWSE }
        Key.SHORTCUT_5 -> SemanticAction.GO_DOWNLOADS.takeIf { context == InputContext.BROWSE }
        Key.SHORTCUT_6 -> SemanticAction.GO_SOURCES.takeIf { context == InputContext.BROWSE }
        Key.TAB, Key.OTHER -> null
    }

    /**
     * Keys while the Reflux keyboard is open: a remote's or a keyboard's arrows move among its keys, OK or Enter types
     * the chosen one, and Escape or Back puts it away. Every other key is the field's: a physical keyboard still types
     * into it (Backspace included), and nothing is a shortcut.
     */
    private fun typingOnKeyboard(key: Key): SemanticAction? = when (key) {
        Key.ARROW_UP -> SemanticAction.NAVIGATE_UP
        Key.ARROW_DOWN -> SemanticAction.NAVIGATE_DOWN
        Key.ARROW_LEFT -> SemanticAction.NAVIGATE_LEFT
        Key.ARROW_RIGHT -> SemanticAction.NAVIGATE_RIGHT
        Key.ENTER, Key.DPAD_CENTER -> SemanticAction.SELECT
        Key.ESCAPE, Key.BACK -> SemanticAction.BACK
        Key.MEDIA_PLAY_PAUSE, Key.MEDIA_PLAY, Key.MEDIA_PAUSE -> SemanticAction.PLAY_PAUSE
        Key.MEDIA_STOP -> SemanticAction.STOP
        Key.VOLUME_UP -> SemanticAction.VOLUME_UP
        Key.VOLUME_DOWN -> SemanticAction.VOLUME_DOWN
        Key.VOLUME_MUTE -> SemanticAction.MUTE
        else -> null
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
            GamepadButton.STICK_LEFT -> "L3"
            GamepadButton.STICK_RIGHT -> "R3"
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
            GamepadButton.STICK_LEFT -> "LS"
            GamepadButton.STICK_RIGHT -> "RS"
            else -> neutralGlyph(button)
        }
        // A remote's keys are named for what they do; it has no face buttons.
        ControllerFamily.TV_REMOTE -> when (button) {
            GamepadButton.FACE_SOUTH -> "OK"
            GamepadButton.FACE_EAST -> "Back"
            GamepadButton.START -> "Menu"
            else -> neutralGlyph(button)
        }
        ControllerFamily.XBOX, ControllerFamily.GENERIC -> when (button) {
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
            GamepadButton.STICK_LEFT -> "LS"
            GamepadButton.STICK_RIGHT -> "RS"
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
