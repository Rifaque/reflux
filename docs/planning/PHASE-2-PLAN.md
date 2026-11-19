# Phase 2 (Beta 2): Controller, Focus, Interaction, Input, Haptics & UX Reliability

Written 2026-11-19 on `open-source-gpl` at `7dbade6`. The tree was clean, and the screenshot left untracked at Phase 1's end was gone. Phase 1 had been accepted by the owner, and the owner's Phase 2 brief of the same day authorised this work (DECISIONS.md, 2026-11-19, "Phase 2 authorised"). Scope, order and exit criteria are in [BETA-2.md](BETA-2.md); each issue's record is in [BETA-BUG-TRACKER.md](BETA-BUG-TRACKER.md); runs are in VERIFICATION.md, "Phase 2". This file holds the plan, the audit behind it, and the interaction contract it implements.

**Hardware at the start.** Over USB adb: the Nothing Phone (2a) and the Xiaomi Pad 7 (both physical). Also this Windows 11 machine, the Google TV emulator (`Reflux_TV`), the Ubuntu 26.04 QA VM, and WSL2. The Fire TV was not connected (it uses network adb at a changing address). There is one monitor at a time (see B-002).

## Reconciling IDs with the brief

The brief named four new owner findings B-043 to B-046. Those numbers were already in use in the tracker, given to issues found during Phase 1 (B-043 Linux install hint, B-044 a test timeout, B-045 `AboutWindowTest`). The tracker's rule is that IDs are stable and never reused, so the new findings take the next free IDs:

| Brief's ID | Tracker ID | Finding |
| --- | --- | --- |
| B-043 | **B-046** | Redundant Profile button on the Profile destination |
| B-044 | **B-047** | Unexplained dot beside the Profile button |
| B-045 | **B-048** | Touchscreen pull-to-refresh on Home, Movies, Shows |
| B-046 | **B-049** | Allow removing a profile's PIN (authorised) |

The owner's decisions in the brief are recorded in DECISIONS.md, 2026-11-19:
- **Forgot PIN:** "Keep profile" and "Reset profile", plus an OS-authenticated reset where it is practical (B-018's open decision, now resolved).
- **F-004:** Aptabase is to be evaluated, not integrated.

B-042 stays unassigned. B-016, B-017, B-022 and F-005 stay where they were.

## Stage A: audit (what the code does now)

### The architecture that exists

Reflux already has a shared input architecture. Phase 2 extends it; it does not replace it.

| Layer | Where | What it does |
| --- | --- | --- |
| Semantic actions | `core/input`: `SemanticAction`, `InputMapping`, `InputModalityTracker` | Keys and buttons (by position) map to actions per context (`BROWSE`, `PLAYBACK`). Modality (pointer, touch, keyboard, directional) drives the layout morph and the button glyphs |
| One entry point | `AppState.onKey`, `onGamepadButton`, `dispatch`; `backRoute` | Every shell sends input here. `dispatch` is the single place actions are routed: profile gate, then system window, then player, then browse (focus moves, Back unwinding, Menu, destinations, sections) |
| Focus | `ui/components/Focus.kt`: `FocusEntry` (each page's start and return target), `rememberFocusReturn`, `focusPanel`, `initialFocus`; `RailFocus` and `FocusedControl` in `RefluxApp`/`Controls` | Where the first directional press lands; return to the item a page was left from; rail entry and exit; Select for controllers (they have no key for the toolkit to handle) |
| Toolkit | Compose focus search (`FocusManager.moveFocus`), `BringIntoViewSpec` (`FocusScrollInsets`) | Movement inside a page; scrolling the focused item into view clear of the chrome |

### Where it falls short (each confirmed in code; the reported bugs are the symptoms)

| Gap | Code | Issues it explains |
| --- | --- | --- |
| **"Typing is active" is opt-in per call site.** `AppState.textInputActive` keeps keys from being read as shortcuts. Only Search, Identify and the Sources forms set it; the profile name (Profiles and Settings), every PIN field (unlock, deletion, PIN setup) and Settings' rename do not. There, `Key.BACKSPACE` maps to `BACK` and runs `navigator.back()`, which from a section is Home | `InputMapping.map(Key.BACKSPACE) = BACK`; `TextInput` only reports through an `onFocus` callback that most callers leave empty | **B-019, B-020, B-021** (root cause) |
| **A text field cannot be typed into with a controller.** Confirm calls `FocusedControl.activate`, which only a `Pressable` registers; a field registers nothing, so A does nothing. On a desktop there is no on-screen keyboard to open. On a tablet with a controller, the system keyboard opens on focus and cannot be driven with a D-pad | `AppState.dispatch(SELECT)` → `activateFocused`; `TextInput`'s `showKeyboardOnFocus` is true off television | **B-011, B-023**, B-004 (desktop, tablet) |
| **On a television the field relies on the system keyboard's lifecycle.** `keyboardByRemote` hides the IME for 20 frames after focus and shows it on OK's release. If the IME dismisses itself (focus moving on the IME's own Next, the TV keyboard closing on Back, Fire TV's quirks), A depends on the field still having focus and the IME reacting to `show()` | `TextInput.keyboardByRemote` | **B-004** (TV) |
| **Focus scale grows the card around its centre.** At `focusScale` 1.08 (controller), a 193 × 290 dp poster grows about 11.6 dp on every side. The ring reaches another 6 dp (`FocusRingReach`) and the glow a further 12 dp. The title starts 8 dp below the unscaled poster, so the ring crosses it; upward, the lift reaches past the row's 14 dp padding toward the section title | `Pressable` (`graphicsLayer` scale, `focusRing`, `focusGlow`), `PosterCard` (`spacedBy(8.dp)`), `RowSection` (`contentPadding vertical = 14.dp`) | **B-009**, B-015 (to verify) |
| **Badges are placed in fixed corners without regard to each other.** "Not connected" is a text chip at the top start; watched is a 22 dp circle at the top end. At phone poster width (112 dp) the chip is truncated and runs under the check | `StateBadges` | **B-032** |
| **Hover and focus are one "active" state.** `Pressable`: `active = hovered \|\| ring`. A pointer resting on one card while a controller focuses another lifts both | `Pressable` | **B-042** (recorded only; unassigned) |
| **Search's chips, placeholder and empty state are first drafts.** The placeholder is the 26-sp title style, one long sentence; suggestions are full `Button`s led by "Try"; spacing is 8 dp, so the focus rings (6 dp reach) of neighbours meet; the row is padded inside the grid's margins, so the last chip is cut by the grid's edge, not scrolled into view; no results gives one line of text | `SearchScreen` | **B-034, B-035, B-037, B-010** |
| **On a phone, typing hides the bottom bar entirely, with no way back but the system gesture.** `CompactNavigation` hides the bar while `textInputActive`. When the IME is dismissed by the system's Back, the field keeps focus, so `textInputActive` stays true and the bar stays hidden. Search has no in-app Back because it is a section | `CompactNavigation` (`AnimatedVisibility(!app.textInputActive)`), `SearchScreen` | **B-036** |
| **The phone's top row always shows the Profile button on a section, including Profiles itself.** `ProfileButton` is drawn for every section except Search | `CompactNavigation` | **B-046** |
| **Two dots sit beside the Profile button.** The library-activity sign in compact form is a 6 dp dot (B-005's Phase 1 change). The downloading mark is a 7 dp white dot on the button. Which one the owner saw persistently is to be established | `ActivityIndicator(compact = true)`, `ProfileButton` (`busy`) | **B-047** |
| **Removing or changing a PIN needs no proof of the PIN.** Settings' "Remove PIN" calls `Library.removePin(id)`, and "Change PIN" `setPin`, with no check. Anyone at an unlocked device can strip a profile's protection in two taps | `SettingsScreen`, `AppState.removePin`/`setPin`, `Library.removePin` | **B-049** |
| **No pull gesture.** `AppState.refresh()` runs the whole pipeline (`LibraryPipeline.refresh`) and is what Sources' refresh uses | `AppState.refresh`; no pull-to-refresh anywhere; the project uses Compose Foundation only, no Material | **B-048** |
| **Mapping.** Y (north) and Start are `MENU`, which opens Sources while browsing; X (west) is unmapped; LB/RB step through destinations while browsing and are previous/next while playing; LT/RT are slower/faster while playing and unmapped while browsing | `InputMapping.map(GamepadButton…)` | **B-012, B-039** |
| **No haptics anywhere** (no vibrator, `performHapticFeedback`, rumble or `LocalHapticFeedback` use). Compose's text selection does use `LocalHapticFeedback` (long-press handles) through the platform's default, which on Android vibrates whatever Reflux's setting | — | **F-001, F-002** |

Home (B-007, B-008, B-013) and the multi-monitor report (B-002) are reproduced or ruled out with tests before anything changes. Their entries record what the tests find.

### The screen audit

What each surface relies on, and the risks. Each one is walked with a controller once the shared changes are in (VERIFICATION.md, "Phase 2"):

| Surface | Focus start / return | Text entry | Risks found |
| --- | --- | --- | --- |
| Home (hero, rows, See all) | Hero's Play (`initialFocus`); each poster (`rememberFocusReturn`) | — | B-007, B-008, B-013 to reproduce; B-009 ring |
| Movies, Shows, listings (`PosterGrid`) | First poster | — | B-009 |
| Search | The field on arrival | Search field (sets `textInputActive`) | B-010, B-011, B-034 to B-037 |
| Details | Play | — | B-009 on episode rows (to check) |
| Sources, Add, Connect | First field | `TextInput` ×3 (set `textInputActive`) | B-004 |
| Profiles | First profile | Name, deletion PIN (**not** set) | B-019, B-020, B-046 |
| Profile gate (unlock, forgot) | PIN field, Keep | PIN (**not** set) | B-019 |
| Settings | Name option | Rename, PIN setup (**not** set) | B-020, B-049 |
| Downloads, About, Notices, Credits, Identify | First control | Identify field (sets it) | none known |
| Player, mini-player | The player's own keys | — | B-002 |
| Panels: offline sheet, folder picker | `focusPanel`; return to opener | — | none known |

## Stage B: the interaction contract

What every screen follows from here on. It is written into INPUT_MODEL.md, where the code is held to it.

**1. Event priority.** An input goes to the first of these that wants it:

| # | Owner | Takes |
| --- | --- | --- |
| 1 | **The active text editor**: a field with focus, or the Reflux keyboard bound to one | Every key that types or edits: characters, Backspace, Delete, Enter, Space, Left/Right, Home/End. On the Reflux keyboard: the D-pad among its keys, confirm (types the key), and its buttons (X erases, Y types a space, LB/RB move the cursor, Start finishes) |
| 2 | **The active modal**: the profile gate, the folder picker, the offline sheet, a Confirm pane, the Reflux keyboard | Back closes it, or steps back within it. Destination shortcuts, section stepping and Menu do nothing while it is open |
| 3 | **The focused control** | Select (the toolkit's Enter and Space; `FocusedControl` for a controller's confirm) |
| 4 | **The destination** | Its own meaning for a direction or Back (a television's Back to the rail; Search's Down into the results) |
| 5 | **Global** | Destination shortcuts (Ctrl+1 to 6), sections (LB/RB), Menu, fullscreen, mini-player |

These keys have stated exceptions:
- **Escape** (or a controller's cancel) in a focused field is level 2's: it lets go of the field, and the next press is Back. Backspace in a field is never Back.
- **Up and Down** leave a one-line field; they are movement, not editing.
- **Ctrl+1 to 6** stay global even while typing, because no typing produces them (NAVIGATION.md).
- **Media keys** always reach what is playing.

**2. One way to say "typing".** A field reports its own focus. `TextInput` and Search's field register through the shared `LocalTextEditing`, so `AppState.textInputActive` holds while any field has focus; no screen sets it. A field that is not built this way is a defect.

**3. Text entry by input.**
- **Touch:** the system keyboard (it is the better keyboard for a finger).
- **Physical keyboard:** typing straight into the field.
- **Controller or remote:** the **Reflux keyboard**, opened by confirm or OK on a focused field. It is used on every form factor, television included, so a remote never depends on a system keyboard's lifecycle (B-004). Under directional input the system keyboard is never shown on focus.
- **Mouse:** a click focuses the field; typing is a keyboard's.

**4. Focus movement.** Compose's directional search inside a page. Custom rules exist only where the toolkit demonstrably fails, and each is named in code and tested:
- the rail's entry and exit (`RailFocus`);
- the start and return targets (`FocusEntry`);
- a television's row-at-a-time scrolling;
- what a stage of this phase adds and justifies with a failing test.

**5. Restoration.** Returning to a page puts focus on the item it was left from (`rememberFocusReturn`) and keeps its scroll (`rememberSaveableStateHolder`). Coming back from the rail goes to where focus was in the page; failing that, to the page's return item, then its start.

**6. Back.** Back has one derivation (`AppState.backRoute`). Its order is unchanged, with the Reflux keyboard and Confirm panes added at the front: keyboard, text field (desktop), panel, player, then the stack.

**7. Pointer, touch and controller together.** Modality decides what is shown (rings, prompts, sizes), never what works. Every input works at every moment, and the first press of another input morphs the layout rather than switching modes. Hover is not disabled to stabilise focus. Hover and focus being one state is B-042's question, recorded and not changed here.

**8. Hints.** Button prompts name an action only where it is available now, in the controller's own terms.

## Stage C: plan and order (the brief's, unchanged)

| # | Work | Plan in brief |
| --- | --- | --- |
| A | **B-025** | The contract above. `LocalTextEditing` (one way to report typing). Text fields register as the focused control, so confirm opens the Reflux keyboard. Regression tests on a vertical slice before widening: Home rows and cards; a nested page and its return; a dialog (Confirm); a text field; Search suggestions and results; controller → mouse → controller |
| B1 | **B-008** | Reproduce each round trip under test (Home → Movies/Shows/Search/Settings/Profiles → Home, by rail and by Back, repeated), then fix what fails |
| B2 | **B-007** | Reproduce See all and row-boundary movement under test, then fix with the least custom focus logic |
| B3 | **B-004** | Source forms with a controller and remote, through the Reflux keyboard; the system keyboard is no longer involved under directional input |
| B4 | **B-019 to B-021** | Falls out of `LocalTextEditing`; a test per field that Backspace edits and never navigates |
| B5 | **B-023** | `RefluxKeyboard`: layouts per `InputKind` (text, search, PIN, address, secret); D-pad, A, B, X, Y, bumpers, Start; Shift; symbols page; clear; done; cursor; a preview line (masked for secrets); physical typing passes through |
| B6 | **B-011** | Search end to end with a controller, on the same keyboard |
| B7 | **B-039, B-012** | Conflict matrix; mapping decision; hints |
| H1 | **F-002** | `Haptics` gateway (one service, explicit events, preferences, rate limits). Controller rumble: on by default, capability-detected; XInput on Windows, `InputDevice` vibrators on Android; Linux's joystick interface has no force feedback, so it is a no-op there, stated. Stops on disconnect |
| H2 | **F-001** | Interface haptics through the same gateway, off by default. Compose's `LocalHapticFeedback` is replaced with the gateway's, so not even text selection vibrates before opt-in. A source test fails if a vibrator or `performHapticFeedback` is reached outside the gateway |
| S | **B-036, B-034, B-035, B-037, B-010** | Search as one piece: insets and IME, Back (keyboard first), the bar's visibility, a short soft placeholder, small chips without "Try" on an edge-to-edge row, an empty state with a next action, chips spaced for their rings and scrolled into view |
| F | **B-009, B-032, B-002, B-015, B-013** | Card lift that never covers its title or the row above; badges that never collide (a compact status mark with an accessible name); the multi-monitor investigation; alignment; the hero on return |
| N | **B-046, B-047, B-048, B-049** and Forgot PIN | Header self-navigation; the dot's source; pull-to-refresh on the existing pipeline (a gesture built on nested scrolling, since the project has no Material); authorised PIN removal and change; Forgot PIN with Keep profile, Reset profile, and Android's system authentication |
| D | **F-004** | Aptabase evaluation written down; the groundwork kept; nothing sent |

Each stage lands with its tests and its tracker entry, and is committed locally. In the event: the code and tests in one commit (`77b994c`), because `AppState` and `RefluxApp` carry several issues each, and the documentation in the next. The full check runs at the end, and the device, emulator and VM walks are recorded by kind.

## Forgot PIN: system authentication by platform

The owner's model (DECISIONS.md, 2026-11-19): **Keep profile** changes nothing, and **Reset profile** erases the profile's protected data and its PIN after a warning and a second confirmation. Where the system can confirm the device's owner, a third choice removes the PIN and keeps everything. Reflux never collects or sees what is entered, the old PIN is never revealed, and a cancelled, failed or unavailable confirmation authorises nothing. Investigation, per platform:

| Platform | Route | Decision |
| --- | --- | --- |
| **Android phone and tablet** | The platform `android.hardware.biometrics.BiometricPrompt`. From Android 11 with `BIOMETRIC_STRONG \| DEVICE_CREDENTIAL` (a strong fingerprint or face, or the screen lock's PIN, pattern or password); on Android 10 with `setDeviceCredentialAllowed(true)`. Availability comes from `BiometricManager.canAuthenticate` (11+) or `KeyguardManager.isDeviceSecure` (10). The system draws and checks the prompt, and Reflux receives only success, cancellation or an error. AndroidX Biometric was not used: it needs a `FragmentActivity` and a new dependency for the same system prompt | **Implemented** (`SystemLock`). Android 8 and 9, and a device with no screen lock, are not offered it |
| **Android TV, Google TV, Fire TV** | No reliable screen lock on these devices, and a remote has no biometric | **Not offered** (the brief's default): Keep profile or Reset profile |
| **Windows** | Windows Hello's `UserConsentVerifier` is a WinRT API. A desktop (Win32) application must call `IUserConsentVerifierInterop::RequestVerificationForWindowAsync` with its window handle. From the JVM that means COM/WinRT activation (`RoGetActivationFactory`, `HSTRING`s) and an `IAsyncOperation` completion handler through JNA, with no projection to lean on. `CredUIPromptForWindowsCredentials` was rejected: it hands the entered credentials back to the application, which would then hold the user's password | **Not implemented in Phase 2**: feasible, but a native interop layer of its own, with its own testing on Hello-equipped and Hello-less machines. Recorded as a candidate (a small native helper, or a JNA WinRT shim). Keep profile or Reset profile meanwhile |
| **Linux** | polkit authorises *actions* defined in policy files installed system-wide (`/usr/share/polkit-1/actions`), which the portable tarball cannot install without root. Borrowing an existing action (`org.freedesktop.policykit.exec`) would ask for administrator rights to run something as root: the wrong question, and an unsafe privileged route. PAM would mean Reflux collecting the user's password itself, which is forbidden | **Not implemented**: no secure, maintainable route for a tarball today. A distribution package could ship a policy file. Keep profile or Reset profile |

## F-004: statistics provider (Aptabase) evaluation

Phase 1's groundwork (a closed schema, consent off by default, `TelemetryTransport.Disabled` in every build, no switch while nothing can send) is unchanged. The evaluation the brief asked for, against Reflux's requirements:

| Requirement | Aptabase |
| --- | --- |
| Platforms (Android phone, tablet, TV, Fire TV, Windows, Linux) | No Kotlin Multiplatform or JVM SDK. Its SDKs are for other stacks (Swift, Kotlin/Android, Flutter, .NET, Electron, Tauri and others). It does have a plain HTTP ingestion API, so **Reflux's own transport can call it from shared code** on every platform, with no SDK |
| Shared code, footprint | Through the HTTP API: no new dependency (Reflux already has an HTTP client). The Android SDK is not needed |
| Self-hosting | Open source (AGPL-3.0) and self-hostable; hosted in the EU or US |
| Privacy | No device identifiers and no cookies. The client sends a random, short-lived session ID. The server derives country and region from the IP address, and counts users by a hash of the IP and User-Agent with a salt rotated daily and deleted within 48 hours. The IP and User-Agent are processed in memory and not stored (Aptabase's privacy policy and About page, read 2026-11-19). Reflux's schema stays the whole contract: enums and buckets only, and the app version. **For the owner:** that daily, IP-derived count still processes the address, and PRIVACY.md would have to say so |
| Consent | Enforced in Reflux before the transport (`TelemetryRecorder`). Nothing in Aptabase's API can bypass it, because nothing is called without consent |
| Offline, failure | Batches are bounded and dropped once on failure (Phase 1's recorder). Nothing retries in the background |
| Maintenance | One endpoint and an app key (configuration, not a secret), behind `TelemetryTransport` |

**Conclusion:** Aptabase fits through its HTTP API behind Reflux's own transport, not its SDKs. It is still not integrated, as the brief asks. What is left for the owner, before any transmission:
- approve the provider and region (EU-hosted or self-hosted);
- approve the event contract;
- approve the retention.

Then PRIVACY.md, the website, Play's Data safety and Amazon's questionnaire are updated before anything is sent. Nothing is sent from any build.
