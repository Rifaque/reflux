# CachyOS final audit ledger (2026-11-24)

**What this is.** The record of the owner's one-shot "CachyOS final audit" session on Reflux Beta 2 (`0.9.0-beta.2`), run on the owner's CachyOS + Hyprland laptop before it is retired for Ubuntu. Issues here are `CACHY-nnn`. Where an issue is a general product bug it also has an entry in the main tracker ([BETA-BUG-TRACKER.md](BETA-BUG-TRACKER.md), B-082 onward), which stays the authority for product issues; this file does not replace it. What was run is also summarised in [VERIFICATION.md](../VERIFICATION.md), "CachyOS final audit".

**Baseline.** `main` = `origin/main` = `1d65dcf` at the start (no newer commits). The portable Beta 2 build (`reflux-0.9.0-beta.2-linux-x64`, built from `ff98a0d`) was used unmodified as the reproduction artifact; product code at `1d65dcf` is identical to it (only documentation changed after `ff98a0d`), so a development build of the baseline is the portable build's code. The owner's own data directory was never used: every run used a copy (`REFLUX_HOME`) or a fresh temporary library.

**Verification kinds are kept apart**, as in the tracker: *automated* · *physical Linux desktop (this machine, Hyprland)* · *emulator* · *physical Android/TV device* · *Windows*. This session had **no Android device, no emulator, no Android SDK and no Windows machine** (`adb devices` empty; no `ANDROID_HOME`); anything needing them is marked **Blocked (no device)**, never verified. A **GameSir G7 Pro** controller (Xbox layout, USB) was attached: Reflux's Linux reader was checked against it read-only, but its buttons need a person to press them, and no synthetic input was injected.

## What kind of change each is

Beta 2's fix scope was frozen on 2026-11-20 (anything new to the Beta 3 backlog unless the owner authorises a critical Beta 2 blocker). This session's changes were made on the owner's brief of 2026-11-24, but most of them are **not Beta 2 bug fixes**: they are features and redesigns of the kind the freeze sends to Beta 3. They are kept apart here so the owner can place each in a release (none is in a released build; `reflux.version` is unchanged at `0.9.0-beta.2`, nothing was cut or tagged).

| Kind | Items |
| --- | --- |
| **Bug fixes** (product defects, fixed) | CACHY-003 Quick View slow on long shows (B-082) · CACHY-001 the Linux window class (B-083) · CACHY-008 page hints wrong while typing (B-087) |
| **Feature additions** (Beta 3-class) | CACHY-004 the sidebar shrinks to its icons, and Start's meaning changed for it (F-P1, a registered proposal never scheduled; it reverses decisions of 2026-11-11 and 2026-11-20) · CACHY-001's `bin/install-desktop-entry` (a new packaging feature) |
| **UX redesigns** (Beta 3-class) | CACHY-005 the compact keyboard, its field preview only when needed, and its remapped shortcuts (LS Shift, LT &123; RT no longer symbols) · CACHY-006 the PIN pad's own size |
| **Environmental workarounds** | CACHY-002 a documented Hyprland window rule for `reflux`; nothing in Reflux changed for it beyond CACHY-001's class |
| **Incomplete or blocked** | CACHY-010 a Back lost after the dock's Stop (pre-existing) · CACHY-002's workspace move (a known limitation; needs one-window rendering) · CACHY-007 Android's small box (no device) · CACHY-009 two font-dependent UI tests (pre-existing) · the dock pin check, every controller press, every Android, TV and Windows walk (no device or needs a person) |

## Environment fingerprint

| | |
| --- | --- |
| OS | CachyOS (rolling), kernel `7.3.5-2-cachyos` |
| Compositor | Hyprland 0.57.2 (Lua configuration), Wayland session; Reflux runs through **XWayland** (`xorg-xwayland` 24.1.15) |
| Shell / desktop | Noctalia 5.6.0 (bar, launcher, window switcher) |
| Displays | eDP-1 2560 × 1600 @ 165 Hz, scale 1.6; HDMI-A-1 2560 × 1440 @ 144 Hz, scale 1.25. `xwayland:force_zero_scaling = true` |
| GPU | Intel Arrow Lake-S iGPU (Mesa 26.3.1, `intel-media-driver` 26.3.5) + NVIDIA RTX 5060 Max-Q (open module 620.73.04, `libva-nvidia-driver` 0.0.18) |
| Decoding | libmpv from mpv 0.42.0; Reflux's own probe: H.264/HEVC/AV1/VP9 8- and 10-bit in hardware up to 3840 × 2160; actual playback decoder `vaapi` (hardware) |
| Audio | PipeWire 1.6.11 with WirePlumber 0.5.19 (PulseAudio protocol); default sink a Bluetooth headset |
| Compositor rules that matter | `decoration.active_opacity = 0.95`, `inactive_opacity = 0.85`, `fullscreen_opacity = 1`; blur size 5, 4 passes; rounding 10; window slide animations; `opacity 1.0 override` only for `mpv`, `haruna`, `plex`, `vlc`, `gwenview`, browsers and terminals; `suppress_event = maximize` for every window; `_JAVA_AWT_WM_NONREPARENTING=1` in the session |
| Java (development) | Eclipse Temurin 21.0.13 installed for this session under `~/.local/share/jdks` (no system JDK; removable) |
| Launch paths available | Portable `bin/Reflux` (from a terminal, or `hyprctl … exec`); no `.desktop` entry existed for Reflux anywhere (`~/.local/share/applications`, `/usr/share/applications`) |
| Input | A GameSir G7 Pro on USB (`/dev/input/js0`), readable by the session (ACL); the kernel names it "Generic X-Box pad" |

**How the desktop was driven without touching the owner's session.** The owner was using the machine. Every Reflux window was opened on workspace 9 with a *runtime-only* Hyprland rule (`hyprctl eval hl.window_rule … workspace = "9 silent"`; nothing written to the configuration; gone at the next reload). Keys went only to Reflux's window (`hl.dsp.send_shortcut` with a `class:` selector); window operations only by Reflux's PID or class; captures only of Reflux's own toplevels (`grim -T <stable id>`). The real-window harness (`desktop/app/src/test/.../WindowHarness.kt`) drives Reflux through its own event queue and semantics, never the system pointer. **One slip, recorded honestly:** the first walk's rule matched the wrong window class, so for about 30 seconds the test windows opened on the owner's active workspace 4 before closing; the rule was corrected before any further run (see "Session notes").

## Issues

Severity: Critical · High · Medium · Low. Priority: P1 (fix now) · P2 · P3.

### CACHY-001: On Linux the window had no stable identity, so docks could not show Reflux's icon (BUG D) · B-083

- **Severity / priority / confidence:** Medium · P1 · High
- **Platform:** Linux, every desktop (found on Hyprland + XWayland) · **Component:** `desktop:app` (window class), Linux packaging
- **Reproduction:** run the portable `bin/Reflux`; `hyprctl clients -j`.
- **Expected:** a window class a desktop entry can name (`StartupWMClass`), and an entry with Reflux's icon, so a dock shows the logo and groups the window with its launcher.
- **Actual (portable Beta 2):** `class: dev-reflux-desktop-MainKt`, `xwayland: true` (the toolkit's default, from the Java main class; under the test harness the same window was `kotlin-concurrent-ThreadsKt$…`). The archive shipped no `.desktop` file; the icon (`lib/Reflux.png`) was in it but nothing pointed to it.
- **Root cause (High, verified):** Reflux set no `WM_CLASS`, and the Linux artifact had no desktop entry.
- **Fix:** `LinuxWindowClass.kt` names every Reflux window `reflux` before the first window (`XToolkit.awtAppClassName`, opened with `--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED` for the packaged launcher, `run` and tests; without it Reflux runs as before and logs that the class could not be set). `bin/install-desktop-entry` (new, in the Linux archive) writes `${XDG_DATA_HOME:-~/.local/share}/applications/reflux.desktop` with `StartupWMClass=reflux`, the archive's icon, `Categories=AudioVideo;Video;Player;`, the Exec path quoted per the Desktop Entry Specification; `--remove` deletes it; a folder path containing `%` is refused with a reason (GLib's launchers look for the program before expanding `%%`, so such an entry would never start).
- **Files:** `desktop/app/src/main/kotlin/dev/reflux/desktop/LinuxWindowClass.kt` (new), `Main.kt`, `desktop/app/build.gradle.kts`, `desktop/app/packaging/linux/install-desktop-entry` (new).
- **Regression test:** `LinuxDesktopEntryTest` (3, new): the entry names the class and this folder with the specification's quoting, `--remove` removes it, a `%` path is refused, and the class is set only on Linux.
- **Before / after:** portable `dev-reflux-desktop-MainKt` → development build `reflux` for both of its windows (`hyprctl clients`, `HyprlandWalkTest` run after the fix). The entry checked with `desktop-file-validate` (valid) and opened and launched through GLib (`Gio.DesktopAppInfo`, the API GNOME-style launchers use): `StartupWMClass` read as `reflux`, and the launch ran exactly `bin/Reflux` from folders whose paths had spaces, `$`, `"`, backquotes and backslashes.
- **Packaged build (physical Linux):** `./gradlew :desktop:app:packageDesktopArchive` on this machine (a development archive at `1d65dcf`+changes, never published): `bin/install-desktop-entry` is in it and executable; `lib/app/Reflux.cfg` carries `--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED`; the entry it wrote (to a scratch `XDG_DATA_HOME`) validated, and launched through GLib it started the packaged Reflux, whose window was class `reflux`, rendering Home normally on a copy of the owner's library; its log had no "could not be set" line.
- **Status:** **Fixed.** Pinning the entry in a dock and relaunching from it is **Blocked (the owner's dock)**: it needs operating Noctalia's dock and looking at it, which is the owner's screen. Procedure: run `bin/install-desktop-entry` from the extracted folder, start Reflux from the launcher, pin it, close it, start it from the pin; the icon is Reflux's and one dock item holds the window.

### CACHY-002: On Hyprland the player is two windows: blur and translucency over the picture, controls detachable and left behind on a workspace move (BUG C) · B-084

- **Severity / priority / confidence:** High (tiling compositors that blur or dim) · P1 · High for the mechanism
- **Platform:** Linux, Hyprland 0.57.2 + XWayland, the owner's decoration rules · **Component:** `desktop:app` (`VideoSurface.kt`, `VideoOverlay`), compositor configuration
- **Reproduction:** play any film on this machine; `HyprlandWalkTest` plays a 1080p test card and records ten states (home; playing tiled; floating; floating resized; paused; seeked; fullscreen; windowed again; moved to another workspace; mini-player).
- **Expected:** one opaque player; the picture sharp; the controls attached to it in every window state.
- **Actual:** the picture is libmpv's X11 child window inside the main window; the controls are a second, transparent toplevel (a `ComposeDialog` owned by the main window, empty title), which Hyprland manages as a separate **floating** window:
  - the owner's global `active_opacity 0.95` / `inactive_opacity 0.85` and blur (size 5, 4 passes) apply to each window separately. The blur behind the transparent controls window lies over the whole picture: **the picture is heavily blurred** while the controls stay sharp (composite captured in a nested Hyprland with the owner's values), and the desktop shows through the main window;
  - the controls window can be dragged or resized on its own, revealing the sharp picture under it: "a translucent layer over another media surface";
  - **moving the main window to another workspace left the controls window behind** (step 09: main on workspace 8, controls on 9), drawn alone over the empty workspace;
  - in fullscreen the main window was `fullscreen: 2` while the controls stayed an ordinary floating window above it (it displayed correctly).
  - Positions and sizes followed each other in every other state (floating, resized, fullscreen, back to windowed, the mini-player), so Reflux's own window following works.
- **Evidence:** `hyprctl clients -j` per step and `grim -T` of each toplevel (physical Hyprland, workspace 9); the composite of every step in a nested Hyprland (`Hyprland --config` with the owner's opacity, blur, rounding and border values, run as one window on workspace 9) with and without the rule below. Stored: [cachyos-audit/hyprland-player-owner-look.png](cachyos-audit/hyprland-player-owner-look.png), [hyprland-player-with-rule.png](cachyos-audit/hyprland-player-with-rule.png), [hyprland-controls-left-behind.png](cachyos-audit/hyprland-controls-left-behind.png).
- **Root cause (High):** the desktop player's design (video in a native child window; controls in a transparent window above it: docs/DECISIONS.md) meets a compositor that applies opacity, blur and window management per toplevel. The opacity and blur are the owner's configuration (environmental); the two toplevels and the lack of a stable class to write a rule for (CACHY-001) are Reflux's.
- **Experiments:** (1) making the controls window an X11 override-redirect popup (`Window.Type.POPUP`, unmanaged by the compositor), behind a temporary switch, in the nested Hyprland: the nested compositor stopped producing frames during the run, and an unmanaged window would also sit above every other application and stay on screen across workspaces; **rejected, reverted** (no diff left). (2) A window rule for class `reflux`: `opacity = "1.0 override", no_blur = true`, the same kind the owner keeps for mpv and VLC: **the picture sharp and opaque in all ten states**.
- **Fix:** CACHY-001's class makes the rule possible; the rule is documented for testers (BETA-NOTES.md, with Beta 2's class too). Nothing was written to the owner's configuration.
- **Not fixed (known limitation):** the controls do not follow a workspace move. The fix is one window for video and controls (libmpv's render API into Reflux's own surface), architecture work for a later version (DECISIONS.md, 2026-11-24).
- **Status:** **Fixed in part** (mitigated, verified in the nested Hyprland; the workspace move open as a known limitation).

### CACHY-003: Quick View waited for every episode of a long show (BUG B) · B-082

- **Severity / priority / confidence:** Medium · P1 · High
- **Platform:** all (shared UI); measured on Linux · **Component:** `ui` (`QuickView.kt`, `Models.kt`), `library` (`Library.showOutline`)
- **Reproduction:** open Quick View (X, right-click, long touch) on *The Vampire Diaries* (8 seasons, 171 episodes) or *The Originals* (5 seasons, 92 episodes).
- **Expected:** the summary at once.
- **Actual:** Quick View drew nothing until it had built the **whole details page model**: a `LibraryEntry` (artwork, watch state, metadata, availability) for the show, every season and every episode, a card for every episode, the cast, and version choices. It shows none of that per-episode detail. The cost grows about 1 ms per episode on this desktop, and every library change during the read restarts it.
- **Measurement (method):** (1) `QuickViewTimingTest` (opt-in, `REFLUX_TIMING_LIBRARY` = a checkpointed copy of the owner's library), 5 runs per show, same JVM; (2) `QuickViewWalkTest` (opt-in, real shell on a copy of the owner's data directory, its startup sync included), from `openQuickView` until the pane's favourite button is in the semantics tree, polled every 80 ms, cold then two warm rounds. The first shell runs showed exactly 3,000 ms: an artifact of the walk's window sitting on a hidden workspace, which gets no frame callbacks; all figures below are with `-Dskiko.vsync.enabled=false`, before and after alike.

| Show (episodes) | Model before | Model after | Shell before: cold / warm / warm | Shell after: cold / warm / warm |
| --- | --- | --- | --- | --- |
| The Vampire Diaries (171) | 143–161 ms | 6–7 ms | 813 / 242 / 243 ms | 202 / 81 / 84 ms |
| The Originals (92) | 77–84 ms | 4–5 ms | 169 / 167 / 165 ms | 109 / 81 / 81 ms |
| Suits (42) | 39–41 ms | 3 ms | 81 / 82 / 81 ms | 82 / 86 / 85 ms |

  Shell figures of about 80 ms are the poll interval: the pane appeared within one poll. These are a fast desktop CPU (Arrow Lake); the owner's 5–6 s was on slower hardware (tablet, Fire TV), where the same per-episode work is many times slower. No Android or TV device was attached, so the device figure is **not measured** here.
- **Fix:** `Library.showOutline` reads a show's seasons and episodes as plain items with Next Up, building an entry only for the show; `quickViewModel` (Models.kt) uses it for shows and keeps `detailsModel` for films and episodes (one item, already cheap). The show's facts and main-button label are one function (`showSummary`) used by both the details page and Quick View, so they cannot drift apart. `showDetail` and `showOutline` share one season/episode ordering.
- **Files:** `library/.../Library.kt`, `library/.../Models.kt`, `ui/.../state/Models.kt`, `ui/.../screens/QuickView.kt`; tests `ui/src/jvmTest/.../state/QuickViewModelTest.kt` (new), `QuickViewTimingTest.kt` (opt-in measurement), `desktop/app/src/test/.../QuickViewWalkTest.kt` (opt-in measurement).
- **Tests:** `QuickViewModelTest` 3/3 PASS (Quick View says exactly what the details page says of every film, show and episode in the fixture library, a started show included); `QuickViewUiTest` 6/6 PASS; `DetailsFocusTest` 3/3 PASS. On the owner's library copy the new model equals the details model's summary for all three shows (`same=true`, 15 of 15).
- **Status:** **Fixed** (automated + physical Linux measurement). Android tablet and Fire TV timing: **Blocked (no device)**.

### CACHY-004: A controller's ≡ should shrink the sidebar to its icons, not open Sources (BUG A) · F-P1

- **Severity / priority / confidence:** Medium (requested behaviour) · P1 · High
- **Platform:** every form factor with the sidebar · **Component:** `core` (`SemanticAction`, `InputMapping`), `design` (`LayoutProfile`), `ui` (`AppState`, `RefluxApp`)
- **Reproduction (before):** browse with a controller; press Start (≡): Sources opened (DECISIONS.md, 2026-11-20). There was no collapsed sidebar (F-P1 registered, Not Started; "not in 1.0", 2026-11-11).
- **Expected (the owner's brief):** ≡ toggles the sidebar between icons with names and icons alone; it must not move focus; Y still goes to the sidebar; phone unchanged; tablet decided on evidence.
- **Platform evidence:** the layout chooses `SIDEBAR` for windows 1200 dp wide or more (desktop; the 1280-dp tablet emulator), `RAIL` (names beneath small icons) under that: the Pad 7 is about 1164 dp in landscape (`SourcesInlineAndBrandHomeTest`), so it always has the rail, which is already compact; a television's `TV_RAIL` is icons until it has focus; a phone has the bottom bar or the compact rail. **Decision:** only `SIDEBAR` shrinks; elsewhere ≡ keeps opening Sources (nothing is lost where there is nothing to shrink).
- **Fix:** `COLLAPSE_NAVIGATION` for Start while browsing; `AppState.navigationCollapsed`, saved as a setting (`navigation_collapsed`) and read at start; `LayoutProfile.navigationWidthDp` (216 → 76 dp), which the page insets, the page bar, notices and the grid's column count follow; the shrunk sidebar shows the mark, centred icons with the lit place, every destination keeping its name for a screen reader; a pointer's or finger's button at its foot ("Shrink sidebar" / "Expand sidebar"; not a focus stop, so the destinations' Up/Down order is unchanged); hints "Menu Shrink sidebar" / "Expand sidebar"; a light tick on the controller as Y gives.
- **Files:** `SemanticAction.kt`, `InputMapping.kt`, `Layout.kt`, `AppState.kt`, `RefluxApp.kt`; tests `SidebarCollapseTest.kt` (new), `DirectionalInputTest.kt`.
- **Regression tests:** `SidebarCollapseTest` (4): Start shrinks and expands without moving focus and opens nothing, the page moves into the 140 dp given back, Y and Down still work in the shrunk sidebar, the choice survives a restart, a tablet-sized window keeps Start on Sources, the pointer's button. `DirectionalInputTest.theSourcesPageIsAButtonAwayAndBackIsTheWayOut` reached Sources with Start; on a desktop window it now goes through the sidebar (Y, Down to Sources, A, Right into the page) and checks the same page behaviour.
- **Before / after:** [sidebar-collapsed-controller.png](cachyos-audit/sidebar-collapsed-controller.png) (render, 1440 × 900).
- **Physical controller:** the GameSir G7 Pro is read by Reflux's Linux reader as `XBOX`, with `7=START`, `3=FACE_NORTH`, `9=STICK_LEFT` (`PhysicalControllerProbeTest`, read only). Pressing them: **Blocked (needs a person)**. Android TV, Fire TV, tablet and Windows walks: **Blocked (no device)**.
- **Status:** **Fixed** (automated, renders, controller read on physical Linux).

### CACHY-005: The Reflux keyboard was too large, repeated the field and covered the results; Xbox-style shortcuts; dropdown or docked (BUGs F and G) · B-085

- **Severity / priority / confidence:** Medium · P1 · High
- **Platform:** all (controller, remote, desk) · **Component:** `ui` (`RefluxKeyboard.kt`, `RefluxApp.kt`, `TextEntry.kt`, `state/Keyboard.kt`), `core` (`InputMapping`)
- **Reproduction:** Search with a controller, A on the field, type "he" (`KeyboardRendersTest`, renders at a desk 1440 × 900, a television 960 × 540, a phone 412 × 915).
- **Actual (before):** a panel up to 980 dp wide (the keys about 845 dp of it, blank glass both sides), a copy of the field ("Search / he") under Search's own field, about 430 dp tall at a desk; on a television a full-width panel (about 936 × 400 dp) over the tabs and every result. The page's hints stayed on top naming browsing's buttons (CACHY-008).
- **The Xbox screenshot:** not available in this environment (it was not in the repository or the session's files); the design follows the brief's written requirements and the project's own controller conventions.
- **Dropdown or docked (BUG G), decided on evidence:** the keyboard serves every field (search, address, sign-in, profile name, PIN). On Search the results start right under the field, so a keyboard dropped from it would cover the first results; a lower field (sign-in) leaves it no room; console and television keyboards (Xbox, PlayStation, Android TV) dock at the foot. **Kept docked, made compact** (DECISIONS.md, 2026-11-24).
- **Fix:** the panel is as wide as its keys; key unit 74 → 56 dp at a desk and 66 → 50 on a television, height 46 → 40 (36 on a television), gap 6 → 4, padding 14 → 10; the field reports where it is (`textEntry` → `KeyboardRequest.bounds`) and the keyboard repeats the text only when its panel would cover the field (still said to a screen reader either way); suggestions kept, one row; hints wrap to the panel's width. Shortcuts: **left stick pressed = Shift**, **LT = &123** (was LT Shift, RT symbols; RT now free), named in the keyboard's hints with the rest (B Close, X Erase, Y Space, Menu Done).
- **After:** about 660 × 315 dp at a desk and 595 × 300 on a television, the search field, tabs and first results in sight; on a phone it fits the width with its hints in two rows. [keyboard-desk-before.png](cachyos-audit/keyboard-desk-before.png) → [keyboard-desk-after.png](cachyos-audit/keyboard-desk-after.png); [keyboard-tv-before.png](cachyos-audit/keyboard-tv-before.png) → [keyboard-tv-after.png](cachyos-audit/keyboard-tv-after.png).
- **Suggestions:** library titles only (no network: `AppState.completionsFor`, decided D2), recomputed 140 ms after typing pauses; three under a controller, five under a pointer; one row that never covers the keys.
- **Mouse:** the desk keyboard (pointer presentation, F-P4) is unchanged in layout; only the panel's width now follows its keys and the gap is 4 dp. It does not rearrange itself further.
- **Tests:** `KeyboardRendersTest` (6, renders looked at); the keyboard, interaction and adaptive-keyboard suites in the full UI run (Testing, below).
- **Status:** **Fixed** (automated, renders). Walks with a controller on a television, a tablet and a desk: **Blocked (no device / needs a person)**.

### CACHY-006: The PIN pad was a small pad in a wide panel (BUG H) · B-086

- **Severity / priority / confidence:** Low · P2 · High
- **Platform:** all · **Component:** `ui` (`RefluxKeyboard.kt`)
- **Actual (before):** a 420 dp panel with a 216 dp pad centred in it and a full-width field above ([pin-pad-before.png](cachyos-audit/pin-pad-before.png)).
- **Fix:** sized by its own design: three keys across, 68 dp at a desk, 60 on a television, 76 on a phone (a finger), 44 dp tall (38 on a television); the panel as wide as the keys (about 224 dp at a desk); its hints in two rows inside that width; no copy of the profile gate's own PIN field, which is in sight above it.
- **After:** [pin-pad-after.png](cachyos-audit/pin-pad-after.png) (same state as before); the real profile gate with a controller: [pin-gate-after.png](cachyos-audit/pin-gate-after.png).
- **Tests:** `KeyboardRendersTest` (`pin-pad-desk`, `pin-pad-tv`, `pin-gate-desk`); `InteractionContractTest.aPinIsEnteredWithAControllerOverTheQuestionOfWhoIsWatching` (a PIN entered with the controller still opens the profile).
- **Status:** **Fixed** (automated, renders).

### CACHY-007: Android: a film sometimes plays in a small box with black on all four sides (BUG E) · B-088

- **Severity / priority / confidence:** Medium · P2 · Low (not reproduced)
- **Platform:** Android phones (reported) · **Component:** `android/app` (`MainActivity.VideoLayer`, `Media3Player`)
- **Reproduction:** **Blocked (no device)**: no Android device, emulator or SDK on this machine.
- **Code analysis:** the picture is laid out at its own shape (`width × pixelWidthHeightRatio / height`), centred, with the buffer fixed at the video's size (`setFixedSize`). A correctly flagged film is therefore boxed on two sides at most (bars top and bottom on a phone held upright). Black on all four sides means the picture is smaller than its box both ways: either the file has bars of its own (a windowboxed encode, then correctly pillarboxed again) or the surface's fixed size disagrees with what the decoder renders (rotation or crop metadata). The code cannot tell which; the device can.
- **Not done blind:** a pinch or zoom control over the `SurfaceView` under the shared player overlay changes touch routing that cannot be tested here.
- **Procedure for the device:** a screenshot; `adb shell dumpsys SurfaceFlinger` (the video layer's buffer and display size); `ffmpeg -i <file> -vf cropdetect -frames:v 300 -f null -` on the same film. Bars in the file: a zoom-to-fill control. A size mismatch: a surface fix with its own test.
- **Status:** **Blocked (no device).**

### CACHY-008: With the Reflux keyboard open, the page's hints still named browsing's buttons · B-087

- **Severity / priority / confidence:** Low · P2 · High
- **Found:** this session's keyboard renders: "Y Sidebar" (and, briefly, this session's own "Menu Shrink sidebar") at the top of the page while Y typed a space and Start meant Done.
- **Fix:** both of the page's hint bars hide while the keyboard is open (`browsePrompts` and their visibility); the keyboard names its own buttons.
- **After:** the keyboard renders above show no page hints. **Status:** **Fixed** (renders).

### CACHY-009: Two phone-layout UI tests fail on this machine, as they do on the untouched baseline · B-089

- **Severity / priority / confidence:** Low (test reliability) · P3 · Medium (cause)
- **Found:** the full UI run (563 tests, 2 failed): `DetailActionsLayoutTest.withAFingerAllFourActionsStillSitOnOneLine` (the four detail actions wrapped to two lines at 412 dp: the two buttons 16–292 dp, the icons on a second line) and `EpisodeVersionsUiTest.theVersionsAreBeneathTheEpisodeOnAPhone` ("Timed out waiting for the choice to be kept").
- **Baseline:** both fail alone, and identically on a detached worktree of `1d65dcf` (removed afterwards): **pre-existing, not caused by this session**. Hosted CI passed both on `ff98a0d`.
- **Likely cause:** Reflux ships no desktop font; Skia uses the system's default sans, Noto Sans here (`fc-match`), wider than the CI image's. Environment-dependent tests, not a phone defect (phones use Android's own font).
- **Not weakened.** **Status:** **Environmental** (test reliability), carried to Beta 3.

### CACHY-010: After the dock's Stop, the details page's Back did not return Home (window test) · B-090

- **Severity / priority / confidence:** Medium · P2 · Medium (pre-existing: High)
- **Found:** hosted CI on this branch: the Linux "Desktop window tests" failed only `EmbeddedPlaybackTest` at "Timed out waiting for Home again" (line 209). On CachyOS the tiled runs failed earlier (CACHY-009's companions), so a nested Hyprland that floats Reflux's windows was used to reach the step.
- **Baseline:** in that nested Hyprland the test fails at the same step with the same screen **on an untouched `1d65dcf` worktree and on this branch**: not caused by this session. Hosted CI passed it on `ff98a0d`.
- **Status:** **Open** (pre-existing; timing- or window-manager-dependent; possibly a real lost Back right after Stop, to be told apart in Beta 3).

## Environment findings

| Area | Finding | Classification |
| --- | --- | --- |
| Hyprland | Global `active_opacity 0.95` / `inactive_opacity 0.85` and blur apply to every window without an override; media players get `opacity 1.0 override` in the owner's rules, Reflux does not (its class was not stable enough to name; CACHY-001) | Environment, made worse by CACHY-001 |
| Hyprland | A window on a hidden workspace gets no frame callbacks, so Compose (Skiko) waits for its frame timeout (about 3 s) before drawing: anything timed on a hidden workspace is wrong unless vsync waiting is off | Environment (test method) |
| Hyprland | `hl.dsp.send_shortcut` delivers key presses to an XWayland window but not their releases, so a Compose button (activated on release) cannot be pressed this way; arrows work | Environment (test method) |
| Desktop entry | No Reflux `.desktop` file anywhere; the portable archive ships none | Packaging (CACHY-001) |
| Session | `_JAVA_AWT_WM_NONREPARENTING=1` is set for the session (needed by Java on non-reparenting window managers) | Environment, correct |
| Hyprland | A nested Hyprland (`Hyprland --config <file>`, a window of the outer one) gives a compositor whose whole picture can be captured without touching the owner's screen; it stalled once (no frames) while an experimental unmanaged window was up | Test method |
| Noctalia | The dock is enabled; its IPC (`noctalia msg`) can show, hide and reload it but cannot report which entry a window matched, so pinning is a look at the owner's screen | Environment (test limit) |
| Controller | GameSir G7 Pro, read by the kernel as "Generic X-Box pad" (`xpad`), session read access by ACL; triggers as axes | Environment, correct |
| Android | No SDK, no device, no emulator on this machine | Environment (blocks every Android check) |
| Portable install | `~/Applications/reflux-0.9.0-beta.2-linux-x64`, unmodified; a copy of the owner's data directory in the session's scratch space was used for every run | Environment |
| Logs | The portable run's `reflux.log` (on the copy): no error or warning; "Display for playback" repeats as the window crosses monitors (expected); the hardware-decoding probe takes about 1.8 s each launch, off the start-up path | Expected |

## The ten audit areas

What was done in each, and what could not be. "Physical Linux" is this laptop under Hyprland; renders are the UI tests' own screenshots (Compose desktop, not a device).

1. **First impressions.** Walked as a new viewer would on physical Linux (the portable build on a copy of the owner's library: Home, Search, Quick View, the player) and in renders. The three things most likely to annoy, all fixed or mitigated: the keyboard that took the screen and repeated the field (CACHY-005, before/after images); a blurred, see-through picture on this desktop (CACHY-002, mitigated); a multi-second wait for Quick View on long shows (CACHY-003, measured before/after). Also found and fixed: hints naming the wrong buttons while typing (CACHY-008).
2. **Onboarding.** The first-run flow (choose a folder, the library builds, the next launch remembers it) is `FirstRunTest`, a real-window test, run in the desktop window-test pass on physical Linux (Testing, below). A clean profile was used (a temporary data directory); the owner's data was never touched. Android onboarding (the system's folder picker, a television's own picker): **Blocked (no device)**.
3. **Core flow, correct and incorrect use.** Point Reflux at a folder, find a film, play it, pause, seek, fullscreen, mini-player, close and reopen: `EmbeddedPlaybackTest`, `MiniPlayerWindowTest` and `HyprlandWalkTest` on physical Linux; misuse in the same tests (a missing file explained, closing the window mid-playback saves the position, the window resized while playing). Double presses and rapid input on the keyboard and sidebar: `SidebarCollapseTest` toggles repeatedly; the keyboard suites. Network interruption against a server: **not run** (only the owner's server exists, and its watch state is the owner's).
4. **Button sweep.** Every control on Home, Search (with its keyboard), Quick View, the PIN gate, the sidebar (full and shrunk) and the player was exercised by the suites and seen in renders; no dead control was found. The pointer's shrink/expand button is new and tested. A physical controller sweep: **Blocked (needs a person)**.
5. **Permissions denied.** Inventory: desktop asks for none (folders through the system's chooser, secrets through the Secret Service); Android declares `INTERNET`, `VIBRATE`, `USE_BIOMETRIC`, `READ_MEDIA_VIDEO` (and `READ_EXTERNAL_STORAGE` up to API 32), and the TV provider's EPG write for Watch Next. The one runtime question is storage, asked only by a television's own folder browser; refused, Reflux says "Reflux needs your permission to read this device's storage before it can find your media." (`Problems.STORAGE_NOT_ALLOWED`) and asks again next time. **Finding (code, not run):** once Android stops showing the question (refused twice), Reflux repeats the message with no way to the app's settings; recorded for a device check. Denial walks: **Blocked (no device)**.
6. **Rotation, resizing, keyboard.** Desktop on physical Hyprland: tiled, floating, resized, fullscreen and back, another workspace, the mini-player (CACHY-002's walk): Reflux's own windows follow in every state but the workspace move. The Reflux keyboard at desk, television and phone sizes in renders. Android rotation and the system keyboard: **Blocked (no device)**.
7. **Deep links.** The only one is Android's Watch Next card (`WatchNextLink`, `MainActivity.continueFromLauncher`); the manifest declares no other intent filter, and the desktop takes no arguments. Its tests are Android unit tests: **Blocked (no Android SDK)**; CI runs them on every push.
8. **Network and runtime logs.** Reflux's own log on the portable run: clean (Environment findings). Quick View makes no network request (a library read only); keyboard suggestions are library-only by design. Requests to the owner's Jellyfin were not counted: there is no proxy here, and the server's state is the owner's. Hyprland's own log: nothing about Reflux beyond window events.
9. **Profiling.** The busiest operation measured: a long show's details model (`detailsModel`, 143–161 ms for 171 episodes on this desktop), most of it every episode's entry; Quick View no longer pays it (6–7 ms). Method: the same JVM, five runs each, on a copy of the owner's library (CACHY-003). Compose's own render timings were not profiled (no device to profile on; on this desktop frames were not the bottleneck).
10. **Slowest screen.** Quick View on a long show (cold 813 ms, warm 242 ms in the shell on this desktop; 5–6 s reported on the owner's slower devices) → 202 / 81 ms. The details page of the same show still builds the full model, because it shows every episode: on a television it is the next to measure (Windows and Android list, below).

## Regression matrix

| Area | Covered by | Result |
| --- | --- | --- |
| Launch, navigation, sidebar (full and shrunk), Y | `SidebarCollapseTest`, `HomeFocusTest`, `RailEntryTest`, `NavigationTest`, `DirectionalInputTest`; window tests | PASS (full UI run; `aControllerBrowsesTheRealWindow` on physical Hyprland) |
| Controller mapping | `core` `InputTest`, `AppStateTest`, `InteractionContractTest`; the physical controller read | PASS; presses BLOCKED (needs a person) |
| Keyboard, PIN, search, suggestions | `KeyboardTest`, `KeyboardWalkTest`, `KeyboardSessionTest`, `AdaptiveKeyboardTest`, `PointerKeyboardTest`, `SearchDesignTest`, `ProfilesUiTest`, `ProfileSecurityTest`, `KeyboardRendersTest` | PASS (full UI run) |
| Quick View, films, shows, seasons, episodes | `QuickViewModelTest`, `QuickViewUiTest`, `DetailsFocusTest`, `EpisodeVersionsUiTest`, `LibraryDepthUiTest` | PASS, except `EpisodeVersionsUiTest`'s phone case (pre-existing, B-089) |
| Playback, fullscreen, resizing, mini-player | window tests on physical Linux; `HyprlandWalkTest` | Six window tests fail identically on the baseline (Hyprland's tiling); the walk covers the states by hand |
| Linux integration, dock identity | `LinuxDesktopEntryTest`; `hyprctl` class check; the packaged archive | PASS; the dock pin BLOCKED (the owner's dock) |
| Android player sizing | not changed | Blocked (no device) |

## Windows preparation (for the Windows session)

Nothing here was run on Windows. To check there:

- **The sidebar shrink (CACHY-004)** with an Xbox controller over XInput: Start, Y, the hint bar, the pointer's button, the choice kept after a restart.
- **The compact keyboard (CACHY-005)**: the left stick's press (XInput `0x0040`) as Shift and LT as &123; the panel's width at 100% and 150% scale, on a monitor whose scale differs from the primary's (B-002's pipeline note: the keyboard is not a separate window, so it should not be affected).
- **The PIN pad (CACHY-006)** at the profile gate with a controller and a mouse.
- **Quick View (CACHY-003)** on the owner's long shows: the time to open, by eye or the harness.
- **`--add-opens java.desktop/sun.awt.X11`** is added only when building on Linux (the JVM would warn about a missing package elsewhere); the Windows package's `Reflux.cfg` should not have it, and `Reflux.exe` should start as before.
- **The details page of a long show**: the next slowest screen; worth a timing on Windows and the Fire TV.
- **B-061** (Skia after a Direct3D failure) and **B-056** (blank on closing the mini-player) remain the Windows items carried to Beta 3.

## Session notes

- **The 30-second slip (2026-11-24, first `HyprlandWalkTest` run).** The runtime rule meant to keep test windows on workspace 9 matched `worker-org-gradle.*`, but AWT named the test JVM's windows `kotlin-concurrent-ThreadsKt$…`. The walk's two windows opened tiled on workspace 4 next to the owner's terminal, played a test card, and closed by themselves. No input went to any other application. The rule was widened before the next run, and every later run was checked to be on workspace 9.
- **Test tones.** The harness's sample video carries two sine tones (330 and 550 Hz). The first three playback walks (two `HyprlandWalkTest` runs and the one on workspace 4) played that tone through the owner's default output, a Bluetooth headset, for about 20 to 30 seconds each. Found when checking the sample media; from then on every playback run was started with no audio server reachable (`PULSE_SERVER` and `PIPEWIRE_REMOTE` pointed at nothing), so libmpv had no sound device.
- **A folder dialog on the owner's workspace.** The first run of the desktop window tests was started with Gradle directly rather than through `tools/ci/desktop-window-tests.sh`, which stubs `zenity` and `kdialog`. `FirstRunTest` therefore opened a real "Choose a folder with movies or shows" dialog (zenity, through the GTK portal), which appeared on the owner's workspace 5 and stayed after the run. Found by listing every window after the run; closed by ending that `zenity` process. Every later window run used stubbed helpers and a monitor for any new window outside workspace 9 and any `zenity`/`kdialog` process.

## Testing

Every result, with its environment, is in [VERIFICATION.md](../VERIFICATION.md), "CachyOS final audit". In short: `core` 393, `design` 43, `library` 263 (1 skipped): PASS. `ui` 563: 2 FAIL, both pre-existing and font-dependent (CACHY-009; identical on the untouched baseline). Desktop with real windows on physical Hyprland: 83, 6 FAIL, identical with the same messages on the untouched baseline (Hyprland's tiling overrides the sizes and moves those tests ask for); every new or changed test PASS. Packaging PASS. Documentation links PASS. Android and Windows: NOT RUN on this machine (no SDK, device or Windows here). **Hosted CI on the branch** (run 45345526419): Android unit tests, lint and release build PASS; the Windows and Linux checks PASS on re-run (the first attempt's failures were B-074 on Windows and B-090 on Linux, both pre-existing intermittents); the Website job FAILS on one external link (gnu.org refusing the runner; unchanged site; passed on `main` the day before). Physical controller presses and the dock pin: NOT RUN (need the owner).

## CachyOS retirement decision

Everything this machine alone could show has been captured: the Hyprland player's two-window behaviour (recorded, explained, mitigated and stored as images), the window identity and its fix verified on the packaged build, the physical controller's layout as Reflux reads it, and Quick View measured on the owner's real library. What remains open does not need CachyOS: Android's small box (a phone), controller presses and the dock pin (the owner's hands, on any Linux desktop with a dock, Ubuntu included), and the one-window player (later architecture work).

**READY TO RETIRE CACHYOS AND INSTALL UBUNTU**

