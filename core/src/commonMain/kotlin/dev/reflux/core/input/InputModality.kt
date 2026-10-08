package dev.reflux.core.input

/**
 * How the user is currently interacting. The UI adapts its density, focus visuals, and prompts to it.
 *
 * Switching modality is a smooth state transition, not a destructive "TV mode": every input method keeps working.
 */
enum class InputModality {
    TOUCH,
    POINTER,
    KEYBOARD,

    /** Controller or TV remote: focus-driven, larger targets, button-glyph prompts. */
    DIRECTIONAL,
}

/** Raw input evidence reported by the platform shell. */
sealed interface InputSignal {
    data object Touch : InputSignal
    data class PointerMove(val dx: Float, val dy: Float) : InputSignal
    data object PointerClick : InputSignal
    data class KeyPress(val key: Key) : InputSignal
    data class GamepadButtonPress(val button: GamepadButton, val family: ControllerFamily) : InputSignal

    /** Analog stick or trigger movement; [magnitude] is normalized to 0..1. */
    data class GamepadAxis(val magnitude: Float, val family: ControllerFamily) : InputSignal
}

data class InputState(
    val modality: InputModality,
    /** The last controller family used, for glyphs; kept when the modality changes away and back. */
    val controllerFamily: ControllerFamily? = null,
)

/**
 * Tracks the active input modality from raw signals.
 *
 * Only *meaningful* input switches modality: a stick resting off-center, a nudged mouse, or a media key
 * must not make the UI jump between layouts.
 */
class InputModalityTracker(initial: InputModality) {
    var state: InputState = InputState(initial)
        private set

    private var pointerTravel = 0f

    /** Returns the state after [signal]. */
    fun onSignal(signal: InputSignal): InputState {
        state = when (signal) {
            is InputSignal.Touch -> state.copy(modality = InputModality.TOUCH)
            is InputSignal.PointerClick -> state.copy(modality = InputModality.POINTER)
            is InputSignal.PointerMove -> pointerMoved(signal)
            is InputSignal.KeyPress -> keyPressed(signal.key)
            is InputSignal.GamepadButtonPress -> InputState(InputModality.DIRECTIONAL, signal.family)
            is InputSignal.GamepadAxis ->
                if (signal.magnitude >= STICK_THRESHOLD) InputState(InputModality.DIRECTIONAL, signal.family) else state
        }
        // Pointer travel must be continuous: any other input restarts the count.
        if (signal !is InputSignal.PointerMove) pointerTravel = 0f
        return state
    }

    private fun pointerMoved(move: InputSignal.PointerMove): InputState {
        if (state.modality == InputModality.POINTER) return state
        pointerTravel += kotlin.math.abs(move.dx) + kotlin.math.abs(move.dy)
        return if (pointerTravel >= POINTER_TRAVEL_THRESHOLD) {
            pointerTravel = 0f
            state.copy(modality = InputModality.POINTER)
        } else {
            state
        }
    }

    private fun keyPressed(key: Key): InputState = when (key) {
        Key.ARROW_UP, Key.ARROW_DOWN, Key.ARROW_LEFT, Key.ARROW_RIGHT, Key.TAB -> state.copy(modality = InputModality.KEYBOARD)
        // Remote keys arrive as key events on Android TV.
        Key.DPAD_CENTER, Key.BACK, Key.MENU -> state.copy(modality = InputModality.DIRECTIONAL)
        // Media, volume, and shortcut keys do not express a navigation preference.
        else -> state
    }

    companion object {
        /** Stick deflection needed to count as intent rather than drift. */
        const val STICK_THRESHOLD: Float = 0.5f

        /** Pointer travel (in density-independent pixels) needed to leave a non-pointer modality. */
        const val POINTER_TRAVEL_THRESHOLD: Float = 24f
    }
}
