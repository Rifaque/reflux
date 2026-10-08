package dev.reflux.core.input

/** What the user wants to do, independent of the physical input that expressed it. */
enum class SemanticAction {
    NAVIGATE_UP,
    NAVIGATE_DOWN,
    NAVIGATE_LEFT,
    NAVIGATE_RIGHT,
    SELECT,
    BACK,
    MENU,

    PLAY_PAUSE,
    SEEK_FORWARD,
    SEEK_BACKWARD,
    NEXT,
    PREVIOUS,
    STOP,
    SPEED_UP,
    SPEED_DOWN,

    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    FULLSCREEN,
}

/** The UI context interpreting input. The same button can mean different things while browsing or playing. */
enum class InputContext { BROWSE, PLAYBACK }

/** Controller families, used for button glyphs and family-specific conventions. */
enum class ControllerFamily { PLAYSTATION, XBOX, NINTENDO, GENERIC, TV_REMOTE }

/**
 * Gamepad buttons by *position*, following the standard gamepad layout.
 *
 * Positions are stable across brands; labels are not (Xbox "A", PlayStation "Cross", and Nintendo "B" are all
 * [FACE_SOUTH]). Product logic never sees brand labels.
 */
enum class GamepadButton {
    FACE_SOUTH, FACE_EAST, FACE_WEST, FACE_NORTH,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
    SHOULDER_LEFT, SHOULDER_RIGHT, TRIGGER_LEFT, TRIGGER_RIGHT,
    START, SELECT, STICK_LEFT, STICK_RIGHT, HOME,
}

/** Keys from keyboards, media keys, and TV remotes, normalized by the platform shell. */
enum class Key {
    ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT,
    ENTER, SPACE, ESCAPE, BACKSPACE, TAB,
    F, M, K, J, L, PERIOD, COMMA,
    PAGE_UP, PAGE_DOWN,
    DPAD_CENTER, BACK, MENU,
    MEDIA_PLAY_PAUSE, MEDIA_PLAY, MEDIA_PAUSE, MEDIA_STOP, MEDIA_NEXT, MEDIA_PREVIOUS,
    MEDIA_FAST_FORWARD, MEDIA_REWIND,
    VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE,
    OTHER,
}
