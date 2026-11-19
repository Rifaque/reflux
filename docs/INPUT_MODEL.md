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
- The view/create button (`SELECT`) toggles fullscreen in every context; start/options (or north) is Menu: Sources while browsing, the player's menus while playing. While browsing, the bumpers step through the global destinations (`PREVIOUS_SECTION`, `NEXT_SECTION`; docs/NAVIGATION.md). The whole mapping, with its conflict matrix, is under "Controller mapping" below.
- Keyboard: arrows, Enter, Space, Escape/Backspace, `F` fullscreen, `M` mute, `J`/`K`/`L` seek and pause, `,`/`.` speed, `O` the player's lists (a keyboard need not have a Menu key; outside the player it is only a letter), `I` the mini-player and back (`MINI_PLAYER`; with nothing playing, or on a device without a mini-player, it does nothing). Media and remote keys map directly. Ctrl with `1` to `6` goes to Home, Movies, Shows, Search, Downloads, and Sources (`Key.SHORTCUT_n` → `GO_*`), also from a text field; never from inside the player.
- Modality tracking: a controller button or a stick deflection of at least 50% switches to `DIRECTIONAL`; pointer input must travel 24 dp continuously to leave it; media keys never switch modality. The last controller family is remembered for glyphs.
- A television starts out `DIRECTIONAL` with the `TV_REMOTE` family, and its arrow keys are its remote's (they never make it a keyboard). A controller used on a television is still told apart from the remote, in both directions.
- A remote's keys are named for what they do (OK, Back, Menu), so prompts are only shown for controllers.
- Where loudness is the device's own (`Platform.systemVolume`: Android's volume keys, a television's remote), up and down in the player are not the volume: down opens the player's lists, and up takes what is offered on screen (skip intro, recap, credits).
- A television shows no Back arrow: the remote's Back is the way back, and an arrow for it would only be one more stop.

## Event priority (Beta 2 Phase 2, B-025)

An input goes to the first of these that wants it (the interaction contract; planning/PHASE-2-PLAN.md, Stage B):

1. **The active text editor.** This is a field with focus, or the Reflux keyboard bound to one.
   - It takes every key that types or edits: characters, Backspace, Delete, Enter, Space, Left/Right, Home/End.
   - Every field reports its own focus through `Modifier.textEntry`, and no screen sets `textInputActive` (B-019 to B-021).
2. **The active modal**: the Reflux keyboard, the profile gate, the folder picker, the offline sheet, a confirmation. Back closes it, or steps back inside it, and no destination, section or Menu acts while it is open.
3. **The focused control.** Select activates it.
4. **The destination.** This covers its own meaning for a direction or Back.
5. **Global.** Destination shortcuts, sections, Menu, fullscreen and the mini-player.

These keys have stated exceptions:
- **Escape** (or a controller's cancel) in a field lets go of the field. The next press is Back.
- On a **touchscreen**, the system keyboard takes the first Back. The next Back navigates, rather than letting go of a field the viewer cannot see has focus (B-036).
- **Up and Down** leave a one-line field.
- **Ctrl+1 to 6** stay global while typing.
- **Media keys** always reach what plays.

## Text entry by input (B-023)

- **Touch:** the system keyboard.
- **Physical keyboard:** typing into the field.
- **Controller, or any remote (television included):** the **Reflux keyboard**. Confirm on a focused field opens it; a remote opens it with OK, on release. The system keyboard is kept away under directional input and on a television, so a remote never depends on its lifecycle (B-004).

The Reflux keyboard's layout and controls:

| | |
| --- | --- |
| Layouts | General text and search; a name (its first letter capitalised); an address (`:` `/` `.` `-` and `http://` on its first page); a password (masked); a PIN (a number pad) |
| Bottom row | Shift, symbols, cursor back, space (`http://` for an address), cursor forward, erase, clear, done. A remote, which has no buttons for these, reaches them there |
| Controller | D-pad: choose a key. A: type it. B: close (the text stays). X: erase. Y: space. LB/RB: move the cursor. LT: Shift (once, then locked, then off). RT: symbols. Start: done, which is the field's own action |
| Above the keys | The text with its cursor (masked for a secret). A physical keyboard still types into the field underneath |

## Controller mapping (B-039, B-012)

What each button means in each context. A button gets a meaning only where one is useful and conflicts with nothing. Hints name only what is available now: while browsing, Select, Back (where there is somewhere to go back to), and Y Sources.

| Button | Browsing | Player | Reflux keyboard | Profile gate, panels |
| --- | --- | --- | --- | --- |
| A (south; east on Nintendo) | Select | Play/pause | Type the chosen key | Select |
| B (east; south on Nintendo) | Back | Back (to the mini-player where there is one) | Close the keyboard | Back / close |
| **X (west)** | **Nothing (reserved)**: Media Quick View on a focused movie or show, Phase 3 (DECISIONS.md, 2026-11-19) | Nothing | Erase | Nothing |
| Y (north) | Sources (Menu) | The player's lists (Menu) | Space | Nothing |
| Start / Options / Menu | Sources (Menu) | The player's lists | Done | Nothing |
| View / Create / Select | Fullscreen | Fullscreen | Nothing | Nothing |
| LB / RB | Previous / next destination | Previous / next item | Cursor back / forward | Nothing |
| LT / RT | Nothing (no meaning without a conflict) | Slower / faster | Shift / symbols | Nothing |
| D-pad, left stick | Move focus | Up/down: lists; left/right: seek | Choose a key | Move focus |
| Right stick | Nothing (**reserved** for Trickplay, B-029, Phase 3) | Nothing (reserved, B-029) | Nothing | Nothing |

**Conflicts checked:**
- **Text input:** no browsing or player meaning reaches a focused field; the keyboard is its own context.
- **Playback:** the player's meanings are unchanged.
- **Back:** B is Back everywhere, and closes the innermost thing first.
- **Remotes:** a remote has no X, Y, bumpers or triggers, so everything above is also reachable with the D-pad, OK and Back (the keyboard's bottom row; Sources from the navigation).
- **Platform conventions:** confirm and cancel follow each family's own.
- **F-P2:** the owner's undecided proposal that Y toggle the sidebar would take Y from Sources. Phase 2 leaves Y as it was.
- **Phase 3:**
  - X is held for Quick View, and the right stick for Trickplay.
  - Quick View's Menu/Options trigger on a focused item is to be reconciled with Menu opening Sources, in Phase 3.
  - Long press of OK/Select, right-click and touch long-press on a media card are kept free for Quick View.

## Haptics (F-001, F-002)

Everything felt goes through one gateway (`Haptics`). Its events are `CONFIRM`, `TOGGLE`, `BOUNDARY` and `ERROR`, and each has a minimum interval.

- **Interface haptics:**
  - **Off by default.** They act only on a touch, and only once the viewer has turned them on in Settings.
  - The toolkit's own haptics (text selection) go through the gateway as well.
  - A controller never makes the device vibrate.
- **Controller rumble:**
  - **On by default**, and the viewer can turn it off in Settings.
  - Only on a controller with motors: XInput on Windows, an `InputDevice` vibrator on Android. Linux's joystick interface has no force feedback, so nothing is offered there.
  - Its events: a boundary (a direction with nowhere to go, at most every 450 ms), and an error (a wrong PIN). Moving between items is never felt.
  - A controller that disconnects stops at once.
- A test fails if anything reaches a vibrator, a controller's motors or the toolkit's haptics outside the gateway's platform halves.

## Television remote

A remote has a D-pad, OK, Back, and sometimes play/pause, rewind, and fast-forward. Everything is reachable with the first three.

- Browsing: the D-pad moves focus, OK activates, Back goes back one step. The navigation is a rail on the left: Left from the page's first column enters it on the current destination, Up and Down move one destination at a time with no other stops, OK goes there, and Right returns to where focus was in the page. Back on a destination's own page goes to the rail first, then to Home, and from Home leaves the app.
- Player: OK plays and pauses (or accepts the next episode, or answers "still watching?"); left and right seek; down opens the lists, where left and right change list, up and down move, OK chooses, and Back closes; up skips an offered intro; Back leaves. The episode before and after are in the first list, since a remote has no key for them. Media keys do what they say.
- Search: the field has focus on arrival and OK opens the Reflux keyboard (since Beta 2 Phase 2; before, the system's television keyboard); Back puts the keyboard away, down reaches the results, and Back from the field goes back.
- Choosing a folder: OK goes into a folder or uses the open one, Back goes up one folder and out from the top.
- Connecting a server or a share: a page arrives on its first field. Up and down move between the fields and down from the last one goes to Connect; a field's keyboard (the Reflux keyboard, since Beta 2 Phase 2) opens only when OK is pressed on it (on release, so the keyboard never receives the press), and Back puts it away. Done goes on to the next field, or connects from the last.
- Sources: Menu opens them from anywhere while browsing. Down from an open source goes to its first control (its switch), never straight to Remove.

## Desktop shell

- Keys are taken once, at the application level, normalized to `Key`, and handed to the experience layer. This works whichever native window has focus, including the video surface.
- While browsing, navigation actions move toolkit focus and Back unwinds navigation; while playing, the same keys are playback actions. With what is playing in the mini-player the viewer is browsing: the keys are the page's, and only what a page has no use for (media keys, mute, the volume keys, `I`) still reaches the session. While a text field has focus, only Escape and Down are taken, so typing is never read as shortcuts.
- The player is operated by its keys: on a desktop its controls are in a window that never takes keyboard focus (that is what keeps keys working over the video surface), so its buttons are not Tab stops, and every one of them has a key. Tab does nothing while playing, or it would walk the page behind the video. An open list takes the arrows, Enter, and Escape.
- Tab walks a page in the order it is built, the page before the sidebar drawn beside it; an open panel holds Tab until it closes, and hands focus back to what opened it at once.
- Keys with Ctrl, Alt, or Meta held are left to the system, but for Reflux's destination shortcuts (Ctrl or Command with `1` to `6`). `F11` is fullscreen alongside `F`.
- The pointer: hover and focus are one "active" state; rows page with arrows that appear under the pointer; the wheel scrolls pages and is the volume in the player; a click on the video pauses and a double click toggles fullscreen; on Windows the mouse's back button is Back. An icon the pointer rests on says what it is, and in the player which key does the same ("Pause · Space"); the label goes once the icon is pressed, and is never shown for touch, a remote, or a controller.
- Controllers are read by the shell and enter the same semantic path as keys (decision log, "Desktop controllers through platform APIs"):
  - Windows: XInput (Xbox family) and the joystick API (PlayStation, Switch Pro, generic HID pads).
  - Linux: the kernel joystick interface, with button meanings taken from each device's capabilities.
  - A press acts once; a held direction repeats; the left stick is a D-pad once clearly deflected.
  - Buttons act only while a Reflux window is the active window.
  - Only devices with buttons and a stick count as controllers, and whatever is held when one appears is not a press.
- With a controller in use the navigation bar and the player show what the buttons do, labelled for that controller's family (Cross/Circle, A/B, ...). The first directional press on a screen focuses its main action; coming back to a screen returns focus to the item that was opened from it; focus that scrolls a page stays clear of the navigation bar; the sources panel keeps focus until it is closed.
- Confirm on a controller activates the focused control through the experience layer, because there is no key for the toolkit to handle.
- Media keys (play or pause, next, previous, stop) are read on Windows while a Reflux window has focus: the toolkit gives them no key code and the system's virtual-key in an event's `rawCode`, which the shell normalizes to `Key.MEDIA_*` (probe P3, VERIFICATION.md); `InputMapping` already says what they mean, and they reach what plays in the mini-player as before. The volume keys are the system's and are not read. Media keys while Reflux is not the focused window (the system's transport controls) and on Linux are V1.x. Controller support is exercised from the reader interface upward and against the platform APIs; real hardware has so far only supplied devices that are *not* controllers (which found two defects). A session with a physical Xbox or PlayStation controller is still owed, and `PhysicalControllerTest` walks a person through it.

## Back (M10)

Back is one semantic action with one derivation (`AppState.backRoute`; docs/PLAYBACK.md, section 18). On Android the shell enables one handler to match it and none at the root, so the system's own Back runs there; a controller's cancel button at the root is given to the system's Back as a remote's is; Escape, Backspace, a mouse's back button and a controller's cancel are the same action on a desktop.

## Held keys (M10)

A held seek key (a remote's, a keyboard's) is one seek: down is the ten-second seek at once; repeats move a shown target faster the longer the key is held (10, 30, then 60 media seconds per second); one seek is made on release. The shells say which presses are repeats and when a key is let go (`AppState.onKey(key, repeat)`, `onKeyReleased`); the semantic actions are unchanged.

## Configuration

Users may eventually override mappings, but sensible controller mappings must work without configuration.

Controller support is a product capability, not a compatibility checkbox.

**Note (2026-11-13).** Back in the player on a device with a mini-player is "step aside", not "stop"; held seek on a physical remote is still unqualified (none was available).
