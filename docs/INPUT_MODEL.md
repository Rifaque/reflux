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
- Mappings depend on context: in `PLAYBACK`, confirm is Play/Pause and left/right seek; in `BROWSE` they select and navigate. An open player menu is a browsing context.
- The view/create button (`SELECT`) toggles fullscreen in every context; start/options (or north) is Menu: the sources panel while browsing, the player's menus while playing.
- Keyboard: arrows, Enter, Space, Escape/Backspace, `F` fullscreen, `M` mute, `J`/`K`/`L` seek and pause, `,`/`.` speed. Media and remote keys map directly.
- Modality tracking: a controller button or a stick deflection of at least 50% switches to `DIRECTIONAL`; pointer input must travel 24 dp continuously to leave it; media keys never switch modality. The last controller family is remembered for glyphs.
- A television starts out `DIRECTIONAL` with the `TV_REMOTE` family, and its arrow keys are its remote's (they never make it a keyboard). A controller used on a television is still told apart from the remote, in both directions.
- A remote's keys are named for what they do (OK, Back, Menu), so prompts are only shown for controllers.
- Where loudness is the device's own (`Platform.systemVolume`: Android's volume keys, a television's remote), up and down in the player are not the volume: down opens the player's lists, and up takes what is offered on screen (skip intro, recap, credits).
- A television shows no Back arrow in its navigation: the remote's Back is the way back, and an arrow for it would only be one more stop on the way up.

## Television remote

A remote has a D-pad, OK, Back, and sometimes play/pause, rewind, and fast-forward. Everything is reachable with the first three.

- Browsing: the D-pad moves focus, OK activates, Back goes back one step and leaves the app from Home. Down from the navigation returns to where focus was in the page.
- Player: OK plays and pauses (or accepts the next episode, or answers "still watching?"); left and right seek; down opens the lists, where left and right change list, up and down move, OK chooses, and Back closes; up skips an offered intro; Back leaves. The episode before and after are in the first list, since a remote has no key for them. Media keys do what they say.
- Search: the field has focus on arrival and opens the system's television keyboard; Back puts the keyboard away, down reaches the results, and Back from the field goes back.
- Choosing a folder: OK goes into a folder or uses the open one, Back goes up one folder and out from the top.
- Connecting a server or a share: a page arrives on its first field. Up and down move between the fields and down from the last one goes to Connect; a field's keyboard opens only when OK is pressed on it (on release, so the keyboard never receives the press), and Back puts it away. The keyboard's own action key goes on to the next field, or connects from the last.
- The media sources: Menu opens them from anywhere while browsing. Down from an open source goes to its first action, never straight to Remove.

## Desktop shell

- Keys are taken once, at the application level, normalized to `Key`, and handed to the experience layer. This works whichever native window has focus, including the video surface.
- While browsing, navigation actions move toolkit focus and Back unwinds navigation; while playing, the same keys are playback actions. While a text field has focus, only Escape and Down are taken, so typing is never read as shortcuts.
- Keys with Ctrl, Alt, or Meta held are left to the system. `F11` is fullscreen alongside `F`.
- The pointer: hover and focus are one "active" state; rows page with arrows that appear under the pointer; the wheel scrolls pages and is the volume in the player; a click on the video pauses and a double click toggles fullscreen; on Windows the mouse's back button is Back. An icon the pointer rests on says what it is, and in the player which key does the same ("Pause · Space"); the label goes once the icon is pressed, and is never shown for touch, a remote, or a controller.
- Controllers are read by the shell and enter the same semantic path as keys (decision log, "Desktop controllers through platform APIs"):
  - Windows: XInput (Xbox family) and the joystick API (PlayStation, Switch Pro, generic HID pads).
  - Linux: the kernel joystick interface, with button meanings taken from each device's capabilities.
  - A press acts once; a held direction repeats; the left stick is a D-pad once clearly deflected.
  - Buttons act only while a Reflux window is the active window.
  - Only devices with buttons and a stick count as controllers, and whatever is held when one appears is not a press.
- With a controller in use the navigation bar and the player show what the buttons do, labelled for that controller's family (Cross/Circle, A/B, ...). The first directional press on a screen focuses its main action; coming back to a screen returns focus to the item that was opened from it; focus that scrolls a page stays clear of the navigation bar; the sources panel keeps focus until it is closed.
- Confirm on a controller activates the focused control through the experience layer, because there is no key for the toolkit to handle.
- Not yet read on desktop: media keys. Controller support is exercised from the reader interface upward and against the platform APIs; real hardware has so far only supplied devices that are *not* controllers (which found two defects). A session with a physical Xbox or PlayStation controller is still owed, and `PhysicalControllerTest` walks a person through it.

## Configuration

Users may eventually override mappings, but sensible controller mappings must work without configuration.

Controller support is a product capability, not a compatibility checkbox.
