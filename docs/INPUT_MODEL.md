# Reflux Input Model

## Goal

Reflux must understand **semantic actions**, not individual controller brands.

Physical input is normalized into actions before reaching product UI.

## Semantic actions

Core navigation:

- NavigateUp
- NavigateDown
- NavigateLeft
- NavigateRight
- Select
- Back
- Menu

Playback:

- PlayPause
- SeekForward
- SeekBackward
- Next
- Previous
- Stop
- SpeedUp
- SpeedDown

System/media:

- VolumeUp
- VolumeDown
- Mute
- Fullscreen

Additional actions may be introduced as features require them.

## Controller families

First-class targets include:

- PlayStation: DualShock 4, DualSense, DualSense Edge
- Xbox: Xbox One / Series controllers
- Nintendo: Switch Pro and other supported devices where practical
- Generic Bluetooth/USB HID controllers
- Android TV remotes
- Keyboard and media keys
- Handheld controls

## Button terminology

Never hard-code Xbox-only button names into the product model.

The same semantic action may be displayed as:

- Xbox A/B/X/Y,
- PlayStation Cross/Circle/Square/Triangle,
- Nintendo A/B/X/Y,
- keyboard keys,
- TV remote symbols.

When the platform can identify the controller family, user-facing prompts should use the appropriate glyphs.

## Adaptive UI

The first meaningful controller input should be able to shift the active interaction mode into a controller-optimized state.

This should be a smooth state transition rather than a destructive mode switch.

Touch/pointer/keyboard interaction should still remain possible.

## Implemented defaults (`core/input`)

- Gamepad buttons are modeled by **position** (`FACE_SOUTH`, `FACE_EAST`, ...). Confirm is south on PlayStation/Xbox and east on Nintendo; cancel is the other one.
- Mappings depend on context: in `PLAYBACK`, confirm is Play/Pause and left/right seek; in `BROWSE` they select and navigate.
- Keyboard: arrows, Enter, Space, Escape/Backspace, `F` fullscreen, `M` mute, `J`/`K`/`L` seek and pause, `,`/`.` speed. Media and remote keys map directly.
- Modality tracking: a controller button or a stick deflection of at least 50% switches to `DIRECTIONAL`; pointer input must travel 24 dp continuously to leave it; media keys never switch modality. The last controller family is remembered for glyphs.

## Desktop shell

- Keys are taken once, at the application level, normalized to `Key`, and handed to the experience layer. This works whichever native window has focus, including the video surface.
- While browsing, navigation actions move toolkit focus and Back unwinds navigation; while playing, the same keys are playback actions. While a text field has focus, only Escape and Down are taken, so typing is never read as shortcuts.
- Keys with Ctrl, Alt, or Meta held are left to the system. `F11` is fullscreen alongside `F`.
- Not yet read on desktop: controllers and media keys. The semantic path, the modality tracker, and the layout morph are in place and covered by tests; a gamepad reader for Windows (XInput and DualSense) and Linux (evdev) is the missing piece.

## Configuration

Users may eventually override mappings, but sensible controller mappings must work without configuration.

Controller support is a product capability, not a compatibility checkbox.
