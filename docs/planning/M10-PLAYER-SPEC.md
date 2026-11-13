# M10 — Player & Playback Experience 2.0: implementation specification

**Status: authoritative for M10.** Written 2026-11-13 in a planning session (Opus 5.5) on `feature/engine-foundation` at `1dbbc11`, after M8 (`212150a`) and M9 (`89075d0`, hosted CI green through `1dbbc11`). No code was changed to write it.

This is the one master prompt for M10. It replaces the M10 notes in `M8-M13-REBASELINE.md` section 8, which now point here. The durable decisions behind it are in `DECISIONS.md`, 2026-11-13. The roadmap entry is `ROADMAP.md`, "M10". Where this document and older wording disagree, this document wins for M10.

**Implemented 2026-11-13** (uncommitted). Where the build differs from this text, it is recorded here, in DECISIONS.md (2026-11-13, the second entry), and in VERIFICATION.md, "M10":

- Probe results: P1 passed (the surface travels; fixed-size buffer); P2 passed (the activity's lifecycle state at `onPictureInPictureModeChanged(false)` tells close from expand); P3 passed on Windows (media keys arrive with the system's key number in `rawCode`), and Linux was not probed, so Linux media keys are V1.x.
- A held seek's pace is time-based (media seconds per second held), where section 10.3 said "per repeat"; the shells say which presses are repeats (a first draft folded close presses into a hold by timing, and swallowed a double tap's seeks: removed), and the quiet-key commit is the session's own 400 ms timer.
- The player's top-left button is "Minimize player" and its top-right "Stop playing" (section 4.3 named them "Minimize" and "Close"); "Close" is the failure dock's word.
- The activity owns the app's coroutine scope and cancels it when the playback has been let go (section 6.7 said "closes the session on any destroy"; a lifecycle scope would have been cancelled first), and the manifest handles `fontScale|locale|layoutDirection` so a text-size change no longer recreates it.
- Not built or not done: the back gesture's hardware walk, TalkBack, and the Fire TV walk (section 13), and the `AboutUiTest` fix (scope item 9).

**Read before starting:** AGENTS.md; ROADMAP.md (M9, M10); DECISIONS.md 2026-11-11 to 2026-11-13; PLAYBACK.md sections 7 and 18; NAVIGATION.md sections 3 and 8; DESIGN.md; INPUT_MODEL.md; WATCH_STATE.md "Profiles". Code: `PlaybackSession`, `Presentation.kt`, `AppState` (playback, `dispatch`, `videoFrame`), `VideoStage`, `MiniPlayer`, `PlayerOverlay`, `PlayerControls`, `MainActivity`, `Media3Player`, `AndroidPlatform`, `Platform.kt`, `InputMapping`, desktop `Main.kt` and `Keys.kt`, `PresentationTest`, `MiniPlayerTest`, `PlaybackSessionTest`, `MiniPlayerWindowTest`.

---

## 0. What the code says today (verified 2026-11-13)

These facts shape the design. Each was read in the code, not inferred from the roadmap.

| # | Fact | Consequence for M10 |
| --- | --- | --- |
| F1 | `MainActivity` installs one root `OnBackPressedDispatcher` callback that is **always enabled** and calls `moveTaskToBack(true)` when `app.dispatch(BACK)` returns false | On Android 15 and 16 with target 36, an always-enabled callback suppresses the system's back-to-home animation (developer.android.com, predictive back). This callback is replaced (section 4) |
| F2 | `targetSdk = 36`, `compileSdk = 37`, `minSdk = 26`; no `enableOnBackInvokedCallback` in the manifest, so on Android 16 it defaults to true; `KEYCODE_BACK` is not mapped in `Input.kt` | Back already arrives only through the dispatcher, which is correct on API 36. Nothing relies on `onBackPressed` or `KEYCODE_BACK` |
| F3 | `androidx.activity` 1.13.0 (stable) and Media3 1.11.1 are already dependencies | `PredictiveBackHandler` / `BackHandler` and Media3's chapter metadata need **no new dependency** |
| F4 | The `VideoLayer` composable is drawn after `RefluxApp`, fills the window, and paints black; it holds the `SurfaceView` in an `AndroidView` inside an aspect-ratio box. `AndroidPlatform.docksVideo` is false | In MINI on Android the layer would cover the page. M10 places it through the shared `VideoStage`, as the desktop shell does |
| F5 | System bars are hidden while `app.playback != null` (`DisposableEffect(session != null)`) | In MINI the viewer browses with no status or navigation bar, and the dock could sit where the gesture bar is. The key becomes "the full player has the screen" |
| F6 | `Media3Player` builds an **activity-process `MediaSession`** (no service) through `SystemControls`, which refuses to start playback while out of sight. `onStop` pauses and calls `standBy()` | System and PiP controls already reach the one engine. No `MediaSessionService` is needed (section 6.5) |
| F7 | PiP exists: `setPictureInPictureParams` with auto-enter only while PLAYING/BUFFERING, `setSeamlessResizeEnabled(true)`, aspect clamped to 0.42–2.39, pre-31 entry in `onUserLeaveHint`, and `inPictureInPicture` hides the overlay. There is **no `sourceRectHint`**, no `onPictureInPictureUiStateChanged`, and no distinction between a closed PiP window and an expanded one | PiP becomes a presentation of the session, with those gaps closed (section 6) |
| F8 | `onDestroy` closes playback only when `isFinishing`. A recreated activity (a configuration change not in `configChanges`, such as a locale change) makes a new `AppState` and `AndroidPlatform`, and the old engine is never closed | A defect. M10 closes the session on any destroy (section 6.7) |
| F9 | A `SyncingWatchReporter` is built on every `load()` and captures `library.activeProfile()` then. A profile switch ends the session first, so this is correct today, but the owner is never held by the session itself | M10 captures the owner once per session and passes it to every reporter and every preference write (section 9) |
| F10 | `PlayerOverlay` decides visibility with one boolean, a `LaunchedEffect` timer, and `pinned`. The loader (`Spinner`) and the centre transport have separate conditions. `Controls` draws the bottom panel in FLOATING glass | The scattered rules are replaced by one derived chrome mode (section 7) |
| F11 | `PlaybackSession.handle(BACK)` calls `close()` everywhere. The player's top-left button is "Back to library" (close), with "Mini player" beside it where docking exists | Back becomes "collapse" where a mini-player exists (section 4) |
| F12 | A failure in the dock is said there and announced (M9: "Nothing takes the screen from a viewer who is browsing"), and `collapse()` refuses while a failure shows | Kept, and made explicit (section 8) |
| F13 | Android `PlayerState.chapters` is never filled. Media3 1.11.1 **does** parse chapters: the extractor jar has `androidx.media3.extractor.metadata.Chapter` (start, end, hidden, title), filled by `MatroskaExtractor$Track.maybeAddChaptersMetadata` (per track, honoring the chapter's track UID) and by `Mp4Extractor` (Nero `chpl` through `BoxParser.parseChpl`, and QuickTime chapter tracks), and attached with `Format.Builder.setMetadata` | Android chapters are read from `currentTracks` formats' `metadata`. No parser is written (section 10.1) |
| F14 | Desktop `Keys.kt` maps no media key; `InputMapping` already maps `Key.MEDIA_*` to semantic actions; JNA 5.19.1 is already a desktop dependency | Desktop media keys are a shell-normalization job (section 10.2) |
| F15 | The television sidecar-subtitle explanation decided on 2026-10-17 ("Subtitle files beside videos can't be read on this TV") is **not implemented** | Built in M10 (section 10.4) |
| F16 | `profile_setting (profile_id, key, value)` exists since M8 "for what M10 adds", is deleted with a profile's reset, and is covered by `ProfileIsolationTest` | The per-work subtitle preference uses it. **No migration** (section 9) |
| F17 | Seeks are fixed 10 s steps (`PlaybackController.SEEK_STEP_MS`); every key repeat is a separate seek | Held seek needs a pure accumulator and one committed seek (section 10.3) |

---

## 1. Scope

**In scope**

1. Back that follows UI state on every platform, with Android predictive back driving the full player's shrink toward the mini-player (section 4).
2. The handheld Android mini-player: the one `SurfaceView` placed by `VideoFrame` through `VideoStage` (section 5).
3. System picture-in-picture as the session's third presentation, `SYSTEM` (section 6).
4. One deterministic chrome model; loading is exclusive (section 7).
5. Failure behavior in every presentation (section 8).
6. The player's visual redesign (section 11).
7. A profile-scoped subtitle preference for each work (section 9).
8. Playback parity: Android chapters, desktop media keys, held seek acceleration, and the television sidecar-subtitle explanation (section 10).
9. The intermittent `AboutUiTest.aKeyboardGoesInAndComesBackToWhereItWas` carried from M9: fixed with its cause, or recorded with evidence. It may not be weakened.
10. The engine-leak defect F8 and the system-bar defect F5.

**Explicit exclusions** (not built in M10, and no partial seam is added for them)

- Background playback or audio while no Reflux activity is visible. Also `MediaSessionService`, a media notification, foreground-service permissions, and lock-screen controls.
- Casting, device transfer, sync, handoff, Nexus, the Reflux Integration API, Google Assistant / App Actions, Alexa catalog / VSK, recommendations, a backend or cloud account, new source adapters, and library-architecture changes.
- A mini-player or PiP on television or Fire TV. Desktop PiP (an always-on-top window).
- Rewriting desktop video output (the mpv render API, render-to-texture), rounded corners on a native surface, or a travelling native surface.
- Global (unfocused) desktop media keys: Windows SMTC and Linux MPRIS/D-Bus (V1.x; section 10.2).
- Server-provided chapters (for example from Jellyfin's API) for converted streams (V1.x; section 10.1).
- A navigation redesign. The dock geometry in `LayoutProfile.dock` changes only where section 5.4 requires it.
- New design language, new materials, or refraction. Any new dependency. Any change to versions, tags, or releases.

---

## 2. Architecture answers (the thirteen questions)

1. **Is `PlayerPresentation(FULL | MINI | SYSTEM)` right?** Yes, extended by one value. Presentation answers "how is this one session shown in Reflux's window", and the system's PiP window is a third answer: the window shows only the picture, Reflux draws no page and no player chrome, and input arrives only through the media session. The shell, the chrome model, the dock, and input routing all branch on that. A separate boolean (today's `inPictureInPicture`) can contradict the session (a MINI dock drawn inside a PiP window), which is the impossible state to prevent. `SYSTEM` is set only by the Android shell, only from the platform's confirmed callback, and only where `Platform.systemPresentation` is true.
2. **Should the PiP lifecycle be separate?** Only its transient part, and only in the shell. `ENTERING` (from `onPictureInPictureUiStateChanged`, `isTransitioningToPip`, API 35+) is an input to the chrome model (`systemTransition`), so the controls leave before the animation. It never changes the session. `IN_PIP` *is* `SYSTEM`. `EXITING` is not modeled: the platform says to restore UI in `onPictureInPictureModeChanged(false)`, which is when the session leaves `SYSTEM`. So there is no four-state enum to keep in sync, and nothing below the shell knows PiP exists beyond `SYSTEM`.
3. **One `SurfaceView` across full and mini.** One `AndroidView(SurfaceView)` per session, composed in a single fixed slot of the shell's tree, inside `VideoStage`. Only its layout bounds change: never its parent, its composition position, or its visibility. "Hidden" is placement outside the window, as on the desktop. Prefer a fixed-size buffer (`holder.setFixedSize` to the video's size), so that moving and resizing the view is compositor scaling, not buffer reallocation. Step 0 (section 3) proves it on the Phone 2a before anything depends on it.
4. **Predictive back and orientation.** The gesture drives a **preview** (the full player shrinks in place to about 90% and its chrome fades). That preview is UI state, not session state. The presentation changes only when the gesture commits, and a cancel restores everything because nothing was changed. On commit the surface travels from the preview to the dock when the orientation is not about to change. If collapsing releases a landscape lock (`platform.fullscreen` differs from what the viewer had), the surface is put out of sight at commit, the configuration settles, and the dock's picture appears in place with no travel. An animation into a dock rectangle that a rotation is about to move is never attempted.
5. **Root Back and the system animation.** No callback is enabled at the root. The experience layer derives one `BackRoute` from its state, and the Android shell enables exactly one handler matching it, or none. At the root with nothing to intercept, Android's own Back runs: back-to-home with its predictive animation, and on API 31+ the task moved back, not finished. Two exceptions are each tied to a state: playback in the dock that is actively playing, where Back enters PiP (the app intends to intercept, so the animation is rightly replaced by PiP's), and API < 31 (no predictive animation exists there; the old `moveTaskToBack` fallback is kept so Fire OS 7/8 and older handhelds behave as today).
6. **`MediaSessionService`?** Not needed, proven in section 6.5. Everything M10 does happens while a Reflux activity is visible, either full-screen or in PiP. The activity-owned `MediaSession` already gives the PiP window its play, pause, next, and previous controls ("If an app has an active media session, then play, pause, next, and previous controls will appear"). Android 17's background-audio hardening exempts apps that play "while it is showing an activity which is visible to the user, including using Picture in Picture (PiP) mode". A service is required only for background playback, which is excluded.
7. **Jetpack PiP library?** No. `androidx.core:core-pip` is `1.0.0-alpha0x` and too young for a 1.0 product. What it adds over Reflux's ~60 lines (source-rect tracking through Views, version shims) is small, and the Compose path (`onGloballyPositioned` to `setPictureInPictureParams`) is the one Google's own Compose guide shows. Revisit when it is stable (V1.x).
8. **The chrome reducer.** It is a pure function `chrome(facts, hold, now)` with a fixed precedence: not full → NONE; entering PiP → NONE; failure → FAILURE; post-play → ENDING; loading → LOADING; held open or recently touched → SHOWN; otherwise HIDDEN. Each mode lists the layers it may draw. Every other layer is absent, so a loader beside a transport cannot be expressed (section 7).
9. **Mini-player failure.** It stays in the dock and becomes a failure dock: the dead picture is taken out of sight, the dock says "Playback stopped", and it offers Try again where a retry exists, plus Close. It is announced, and activating it opens the full player with the failure. It is not escalated automatically to FULL, which would take the screen and focus from someone typing a search or reading a page (M9's rule, kept). This answers the concern behind the prompt's suggested FULL escalation, a tiny dead picture left silent over the library, without taking the screen. PiP failure: see 6.4.
10. **Back at Home (root) with playback in the dock** (handheld Android):

    | Playback in the dock | Back at root |
    | --- | --- |
    | Playing or buffering, PiP available | Enters PiP (`SYSTEM`); playback continues |
    | Playing or buffering, no PiP (feature absent, low-RAM device) | System Back: Reflux goes to the background; `onStop` pauses and stands by; the dock is there, paused, on return |
    | Paused, ended (post-play prompt), or loading | System Back, as above; the session is kept paused |
    | Failed | System Back; the failure dock is there on return |

    The desktop has no "leave" on Back: at the root Escape does nothing. A television has no dock.
11. **When PiP exits.** If it was expanded (the activity is not stopped), the session goes to `FULL`, whatever it was before PiP: the viewer asked for the picture. If it was closed (the activity is stopped when `onPictureInPictureModeChanged(false)` arrives, so `lifecycle.currentState` is `CREATED`), the session is closed, its place recorded and its stream released. A closed PiP window that came back as a paused dock would surprise the viewer and hold a server's transcode open.
12. **Subtitle preference.** One `profile_setting` row per (profile, work), where the work is the movie, or the show for an episode. The value is a track description, not an index: off, or on with language, forced, and hearing-impaired. The planner resolves it against each version's tracks through a new `SubtitleChoice.Preferred`, and falls back to Automatic when nothing matches. No migration (section 9).
13. **Physical versus automated.** Section 13 has the full list. On hardware: surface continuity and dropped frames, the predictive back gesture (progress, cancel, commit), PiP enter, exit, and close with real system UI, insets and the gesture bar, TalkBack, held seek on a real remote, and media keys on real keyboards. Everything else is automated: presentation, Back routes, chrome, preference resolution, chapter mapping, the seek accelerator, and ownership.

---

## 3. Step 0: three probes (first, time-boxed, results recorded before dependent work)

Each probe is at most half a day. Each result goes into VERIFICATION.md, "M10: Step 0", before the work that depends on it starts. Probe code that proves something is kept as a test; scratch code is not merged.

| Probe | Question | Method | Pass, and what it decides |
| --- | --- | --- | --- |
| P1 Surface continuity | Can one `SurfaceView` move between the full window and a dock rectangle, every frame, under a playing Media3 engine, with no decoder re-initialization and acceptable frames? | An instrumented test on the Phone 2a (and the API 36 emulator for CI), with a generated H.264 MKV that has a text subtitle and chapters. Animate the view's bounds full→dock→full over 300 ms, five times, with (a) the default buffer and (b) `holder.setFixedSize(videoWidth, videoHeight)`. Count `AnalyticsListener.onVideoDecoderInitialized`, `onVideoDisabled`, `onRenderedFirstFrame`, `onDroppedVideoFrames`; record `SurfaceHolder.Callback` calls and the view's identity; read `dumpsys gfxinfo` janky frames | **Pass:** decoder initialized once, never disabled, no `surfaceDestroyed`, the same view, the position monotonic, the subtitle track still selected. Choose (b) unless it is worse. **If both show re-initialization or visible stalls:** the surface does not travel. It moves once, as on the desktop (`travels = false`), and predictive back keeps its in-place preview by scaling the chrome layer only. Record which |
| P2 PiP close vs expand | Which signals tell a closed PiP window from an expanded one on the Phone 2a and Pad 7, and how does Reflux close its own PiP window when the session ends inside it? | Log `onPictureInPictureModeChanged`, `lifecycle.currentState`, `onStop`, and `onPictureInPictureUiStateChanged` for: expand, the close button, swipe-to-dismiss, and launching from a launcher shortcut while in PiP. Try `moveTaskToBack(true)` with the session ended in PiP | **Pass:** a rule that classifies all four correctly (expected: stopped means closed), and one call that removes the PiP window without finishing the activity. Record OS and API levels |
| P3 Desktop media keys | What reaches Reflux when a keyboard's media keys are pressed with Reflux focused? | Windows 11: log AWT `KEY_PRESSED` `keyCode`/`extendedKeyCode`/`rawCode` where reachable; then a JNA subclass of the main frame's window procedure logging `WM_APPCOMMAND`. Linux (the X11 container, and a GNOME session if one is available): log AWT key events for `XF86AudioPlay`/`Next`/`Prev` | **Decides** the layer each OS uses (section 10.2). If nothing reaches a focused Reflux on Linux without D-Bus, Linux media keys are recorded as V1.x |

---

## 4. Back

### 4.1 One route, derived

The experience layer exposes one derived value, the same function every input uses:

```kotlin
enum class BackRoute {
    SYSTEM,            // Reflux does not want Back here: the platform's own Back runs (back-to-home, leave)
    APP,               // Reflux handles it (dispatch(BACK)); no gesture preview
    COLLAPSE,          // the full player steps into the mini-player; previewable by a predictive gesture
    SYSTEM_PRESENTATION, // playback in the dock continues in the system's PiP window
}
val AppState.backRoute: BackRoute
```

**Invariant (a property test):** for every reachable state, `backRoute != SYSTEM` exactly when `dispatch(BACK)` would return true, and `COLLAPSE` exactly when it would collapse. `dispatch(BACK)` and `backRoute` share one decision function, `backDecision()`, so they cannot drift.

### 4.2 Precedence (first that applies)

| # | State | Route | Back does |
| --- | --- | --- | --- |
| 1 | Asking who is watching (`profileGate`) | APP, or SYSTEM when the gate is required at start (Back leaves, as today) | Gate-specific (unchanged) |
| 2 | The PiP window (`SYSTEM` presentation) | SYSTEM | Never reached: a PiP window has no Back |
| 3 | Full player, a menu open | APP | Closes the menu |
| 4 | Full player, a failure showing | APP | Closes the player (a failure is not put aside) |
| 5 | Full player, mini-player available (`miniPlayerAvailable`) | COLLAPSE | `collapse()`: loading, playing, paused, or in the post-play prompt alike |
| 6 | Full player, no mini-player (television, any platform without `docksVideo`) | APP | Closes the player (today's behavior) |
| 7 | Browsing: a text field focused with a keyboard (not a television) | APP | Lets go of the field |
| 8 | Browsing: the folder picker, then the offline sheet | APP | Closes it |
| 9 | Television, a section page with focus off the rail | APP | Focus to the rail |
| 10 | Browsing, the navigator can go back | APP | `navigator.back()` |
| 11 | Root, playback in the dock actively playing (PLAYING or BUFFERING, no error, no post-play prompt), and the platform has a system presentation | SYSTEM_PRESENTATION | Enters PiP |
| 12 | Root, otherwise | SYSTEM | The platform's Back |

The playback in the dock never changes rows 7 to 10: browsing is browsing (M9). Back never ends a docked session.

### 4.3 Platforms

- **Android shell.** Delete the always-enabled root callback. Compose, unconditionally, inside `Shell()`:
  - `PredictiveBackHandler(enabled = route == COLLAPSE)`. Progress goes to `app.backPreview` (0..1, UI-only, never session state). On completion: `backPreview = null` and `app.dispatch(BACK)`. On `CancellationException`: `backPreview = null`, then rethrow. A 3-button Back, a keyboard, or a remote triggers it with no progress, which is a plain collapse.
  - `BackHandler(enabled = route == APP) { app.dispatch(BACK) }`.
  - `BackHandler(enabled = route == SYSTEM_PRESENTATION) { enterPictureInPictureMode(params) }`. If entry is refused, `app.dispatch(BACK)` falls through to row 12 behavior (`moveTaskToBack(true)`).
  - API < 31 only: `BackHandler(enabled = route == SYSTEM) { moveTaskToBack(true) }`. This keeps the pre-Android-12 behavior (the root activity is not finished) where no back-to-home animation exists to protect.
  - The routes are exclusive by construction, so composition order never decides precedence.
- **Controller Back on Android.** `dispatchKeyEvent` currently swallows every gamepad button. A controller's cancel button that Reflux does not handle (route SYSTEM) is instead given to `onBackPressedDispatcher.onBackPressed()`, so B or Circle at the root leaves Reflux as a remote's Back does. It must never act twice.
- **TV remote Back.** On Android 16 with target 36 the remote's Back arrives through the dispatcher like any Back (F2). Precedence rows 3, 4, 6, 9, 10, 12. Fire OS 8 (API 30) keeps `moveTaskToBack` at the root.
- **Desktop.** Escape, Backspace, the mouse's back button, and a controller's cancel all call `dispatch(BACK)`. Row 5 now collapses (the desktop has a dock); row 12 does nothing.
- **Leaving the player explicitly.** On platforms with a mini-player, the full player's top-left control becomes **Minimize** (`Icon.MINI_PLAYER` or a chevron-down; the shortcut stays `I`, and Esc does the same). A **Close** control ("Stop playing", which records the place) moves to the top right, so ending playback is always one explicit action. The "Back to library" close button is removed there. On a television nothing changes.

### 4.4 Predictive back preview (Android 14+ gesture, handheld)

- `backPreview = p` (0..1) is read by two things only:
  1. `VideoStage`: the surface bounds are `lerp(whole, preview(whole, edge), p)`, where `preview` is the window scaled to 90% about its centre and shifted up to 8 dp away from the swipe edge (`BackEventCompat.swipeEdge`).
  2. The chrome alpha: `1 - min(1, p / 0.4)`. The chrome fades early, and the picture keeps shrinking.
- **Reduced motion:** `backPreview` is ignored. The gesture commits or cancels with no preview and the presentation changes at once.
- **Commit:** `collapse()`. If `collapse()` releases a landscape lock (`platform.fullscreen != wasFullscreen` before the call), the surface is placed out of sight for the transition, and the dock's picture appears when the dock has been laid out in the new configuration (`dockSlot` set). Otherwise the surface travels from the preview rectangle to the dock with `Motion.playerDock`.
- **Cancel:** the preview animates back to the whole window (`Motion.controlsShow`) and the chrome returns to the mode it was in. Nothing else happened, so nothing else is restored.
- **Never:** a gesture that both previews and changes the session, a preview on a television, or a preview that travels a native desktop surface.

---

## 5. The handheld mini-player (Android)

### 5.1 The surface

- `AndroidPlatform.docksVideo = !television && P1 passed (either path)`.
- The shell renders `VideoStage(placement) { VideoLayer(player, presentation) }` in the slot where `VideoLayer` is now, with `rememberVideoPlacement(app, travels = P1 travel result)`. `VideoLayer`'s black letterbox fills only its frame, never the window.
- One `AndroidView(SurfaceView)` per session. Its factory runs once per session; `onRelease` detaches it. It is never wrapped in a condition that changes between presentations. `importantForAccessibility = NO` on the `SurfaceView`.
- The surface draws over nothing: a `SurfaceView` punches its hole at its z-position, so content composed *after* it (the full player's chrome, a mini surface layer, the PiP loader) is drawn above the picture. The page and the dock's own picture box are composed before it.
- Over the docked picture the shell composes the shared `MiniPlayerSurface` (tap expands; the loader while waiting; no semantics), as the desktop does.
- **Subtitles:** the track stays selected in every presentation. Cues are **not drawn** in MINI (unreadable at 96 dp). They are drawn in FULL and in SYSTEM; in SYSTEM the text size comes from the PiP window, with a floor of 10 sp instead of 14.
- **System bars:** hidden only while the full player has the screen (`app.watching != null`), not whenever a session exists (F5). Keep-screen-on stays tied to playing in any presentation.
- **Orientation:** unchanged from M9. `collapse()` restores the viewer's window, which on a handheld releases `SENSOR_LANDSCAPE`. On sw600dp displays Android 16 ignores the lock (planning record, 3.1), and nothing depends on it there.

### 5.2 Continuity contract (what must be proven, automated plus P1)

Across FULL → MINI → FULL → SYSTEM → FULL:

- the same `Player` instance and the same ExoPlayer, with no `load()`, no new plan, and no `controller` replaced;
- the same `SurfaceView` instance, with no `surfaceDestroyed`, video decoder initialized once, and `onVideoDisabled` never called;
- position never decreases by more than one tick and never resets to the start;
- the same selected audio and subtitle track IDs;
- the same owner profile and the same `SerialWatchReporter` (nothing reported twice, nothing stopped);
- the stream is not released (no `NegotiatingSource.release`).

### 5.3 Insets and collisions

- The dock is part of `RefluxApp`'s chrome (M9), so it is inside the navigation's and the IME's padding: above the phone bar and the gesture bar, and above the keyboard while one is open. `dockSlot` is in root coordinates, as are `VideoStage`'s, so the surface follows the dock under any padding.
- The dock never overlaps the system gesture area (`WindowInsets.systemGestures` bottom) or the navigation bar. The insets test asserts this by window class.
- The accessibility button and the floating menu are system overlays and are not Reflux's to avoid. TalkBack's focus rectangle on the dock is the dock's bounds.

### 5.4 Geometry (M9 kept; refined only where M10 needs it)

| Window | Dock |
| --- | --- |
| Phone upright | M9's row above the bar (`DockStyle.BAR`, 96 dp picture) |
| Phone on its side | The row in the page's bottom corner (M9) |
| Tablet | M9's card in the bottom corner (272–288 dp picture), or the row in a narrow split window |
| Desktop | M9's dock, unchanged |
| Television | None |

The only refinement allowed: the dock's bottom inset honors `systemGestures` where the bar does not already clear it. `shell-baseline.txt` is rewritten only if that changes a number, and the diff is reviewed.

---

## 6. System picture-in-picture (`SYSTEM`)

### 6.1 Model

```kotlin
enum class PlayerPresentation { FULL, MINI, SYSTEM }
```

- `PlaybackSession.enterSystem()` and `leaveSystem(expanded: Boolean)` are called only by a shell, only from the platform's confirmed callbacks. `collapse()` and `expand()` refuse while in `SYSTEM`.
- `Platform.systemPresentation: Boolean` is true on handheld Android with `FEATURE_PICTURE_IN_PICTURE` (today's `leavesIntoWindow`), and false on televisions, the desktop, and tests unless set.
- `AppState.videoFrame` is `Full` in `SYSTEM`. `watching` and `docked` are both null in `SYSTEM`, and the page, the dock, notices, and the chrome are not composed: the window is the picture, plus at most the loader or the failure glyph (6.4).
- Input in `SYSTEM` comes only through the media session (`SystemControls`: play, pause, next, and previous through `onSkip`). Keys, if any arrive, are ignored.

### 6.2 Entering

- **Auto-enter** (API 31+): `setAutoEnterEnabled(eligible)`, where `eligible` = a session exists, presentation FULL or MINI, status PLAYING or BUFFERING, no error, no post-play prompt. Paused is not eligible (developer.android.com, "you probably don't want to enter PiP if the current playback is in a paused state"). Pre-31: `onUserLeaveHint` when eligible (unchanged).
- **Explicit entry:** Back at the root with an eligible docked session (4.2, row 11). There is no PiP button in the player: leaving the app is the gesture.
- **Params**, refreshed whenever any input changes:
  - `setAspectRatio` from `Media3Player.videoAspect`, clamped inside 1:2.39 to 2.39:1 (a value outside the platform's range throws) and omitted while unknown;
  - `setSourceRectHint` = the **picture's** bounds in the window (the aspect-fitted box inside the frame, not the letterbox), from `onGloballyPositioned` on that box in FULL and in MINI, so PiP animates out of the dock too;
  - `setSeamlessResizeEnabled(true)` (video);
  - `setAutoEnterEnabled(eligible)`.
- **API 35+:** `onPictureInPictureUiStateChanged(state)` with `isTransitioningToPip()` sets the shell's `systemTransition = true`. The chrome model then shows NONE and the dock's words fade, so the entering animation shows only the picture. It is cleared on `onPictureInPictureModeChanged`.
- `onPictureInPictureModeChanged(true)` → `session.enterSystem()`.

### 6.3 Leaving

- `onPictureInPictureModeChanged(false)`:
  - activity not stopped (expanded, or brought forward by an intent) → `leaveSystem(expanded = true)` → `FULL`, and focus to the player (12.3);
  - activity stopped (closed by the viewer) → `leaveSystem(expanded = false)` → `close()`: place recorded, stream released, engine freed. P2 confirms the rule on both devices.
- A launcher Continue Watching card while in PiP: the intent expands the activity (→ FULL), then `continueFromOutside` closes that session and starts the card's work, as today.
- `isStashed`: nothing changes and playback continues (the viewer stashed it to look at something).

### 6.4 Inside PiP

| Event | Behavior |
| --- | --- |
| Paused (from the PiP controls) | Stays in PiP, paused |
| Loading or buffering past the grace (7.3) | The Reflux loader, 28 dp, centred over the picture |
| Engine failure, or the source going away | Playback stops. The window shows the failure glyph and "Playback stopped" over black. `eligible` turns false. Expanding shows FULL with the failure and its actions |
| Ended, next episode with a countdown | The countdown runs, and the next episode plays in the same session and the same engine (the aspect updates the params) |
| Ended, "Still watching?" | Paused on the last frame, with "Still watching?" as text in the window. The PiP controls start nothing (`SystemControls` refuses play at the end). Expanding answers it |
| Ended, nothing next | The session closes, and the shell removes its PiP window with the call P2 found |
| A profile switch, source removal | Impossible from PiP (no UI). After returning, M8's and M9's rules apply: the session ends first |

### 6.5 No `MediaSessionService`: the proof

1. Every M10 behavior happens while a Reflux activity is visible: full-screen, or in PiP, where the activity is paused, not stopped. `onStop` still pauses and stands by (F6).
2. The PiP window's media controls come from an *active media session*, which `Media3Player` already creates in-process (F6; PiP guide: "If an app has an active media session, then play, pause, next, and previous controls will appear").
3. Media3: a service is required "when playing media in the background". Android 17's audio hardening exempts playback "while it is showing an activity which is visible to the user, including using Picture in Picture (PiP) mode".
4. A service would add `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, a Play Console foreground-service declaration, a notification, and a second lifecycle that owns the engine. That is the background-playback feature M10 excludes.

**Therefore:** no service. The session stays activity-owned. Any future external control (an integration API, casting) attaches to the existing `PlaybackSession` command surface (`handle(SemanticAction)`). Nothing is added for it now.

### 6.6 What PiP does not do

There is no custom `RemoteAction` (the media session supplies controls); no PiP on television, Fire TV, or the desktop; and no pausing on stash.

### 6.7 Activity recreation and process death

- `onDestroy` closes the session whether or not the activity is finishing, recording the place (F8). A recreated activity starts with nothing playing; its Continue Watching has the place.
- Process death: nothing resumes. No playback state is saved in `savedInstanceState`.

---

## 7. The chrome model

### 7.1 Modes and layers

```kotlin
enum class ChromeMode { NONE, HIDDEN, SHOWN, LOADING, ENDING, FAILURE }
```

| Mode | May draw | Never draws |
| --- | --- | --- |
| NONE | Nothing of the full player (MINI and SYSTEM draw their own) | Everything below |
| HIDDEN | The skip offer (intro/recap/credits) and notices | Transport, timeline, actions, title, menus |
| SHOWN | Top context (Minimize or Back, title, Close), centre transport (touch layouts), the bottom row, an open menu, the skip offer, notices | The loader, the up-next card, the failure panel |
| LOADING | The Reflux loader (centre), top context, notices | Centre transport, ±10, timeline, actions, menus, speed, tracks, the skip offer |
| ENDING | The up-next card or "Still watching?", top context, notices | Transport, timeline, actions, menus, the loader |
| FAILURE | The failure panel and its actions, top context, notices | Everything else |

"Top context" in LOADING, ENDING, and FAILURE is the way out (Minimize or Back) and the title. In FAILURE the way out is Back/Close only: no Minimize, since a failure is not put aside.

### 7.2 The reducer (pure, in `ui/player`, fully unit-tested)

```kotlin
data class ChromeFacts(
    val presentation: PlayerPresentation,
    val status: PlayerStatus,
    val bufferingSinceMs: Long?,   // set when BUFFERING began, null otherwise
    val failed: Boolean,           // session.error != null
    val postPlay: Boolean,         // session.upNext != null
    val systemTransition: Boolean, // entering PiP (API 35+)
)
data class ChromeHold(
    val revealedUntilMs: Long,     // the inactivity deadline
    val pins: Set<Pin>,
)
enum class Pin { PAUSED, MENU, SCREEN_READER, FOCUS_IN_CONTROLS, POINTER_OVER_CONTROLS, SCRUBBING, SEEK_HOLD }

fun chrome(f: ChromeFacts, h: ChromeHold, nowMs: Long): ChromeMode = when {
    f.presentation != PlayerPresentation.FULL -> NONE
    f.systemTransition -> NONE
    f.failed -> FAILURE
    f.postPlay -> ENDING
    f.status == PlayerStatus.LOADING -> LOADING
    f.status == PlayerStatus.BUFFERING && f.bufferingSinceMs != null &&
        nowMs - f.bufferingSinceMs >= LOADING_GRACE_MS &&
        Pin.SCRUBBING !in h.pins && Pin.SEEK_HOLD !in h.pins -> LOADING
    h.pins.isNotEmpty() || nowMs < h.revealedUntilMs -> SHOWN
    else -> HIDDEN
}
fun nextChange(f: ChromeFacts, h: ChromeHold, nowMs: Long): Long? // the one deadline the UI waits for
```

- `LOADING_GRACE_MS = 600`. Buffering after a seek or a ±10 shorter than that changes nothing, so rapid ±10 presses are never cut off by a flash of the loader. Opening (`LOADING`) is always exclusive.
- **One timer:** the overlay waits for `nextChange(...)` (the deadline, or the end of the grace) with one `LaunchedEffect`. No other visibility timers exist. `Motion.CONTROLS_AUTO_HIDE_MS` stays the base, scaled by `calculateRecommendedTimeoutMillis` as today.
- **Menus and loading:** the session closes an open menu when buffering crosses the grace (a deterministic effect in `PlaybackSession`, so no menu is ever "open but hidden"). Choosing a track already closes the menu.
- **Mode transitions as effects:** LOADING → SHOWN at the end of an *opening* (`revealedUntil = now + patience`), so the viewer sees the controls once. The end of a rebuffer restores what the hold says.

### 7.3 What reveals, what pins

| Input in FULL | Effect on the hold |
| --- | --- |
| Any semantic action (keyboard, controller, remote, media keys) | `revealedUntil = now + patience` |
| Pointer move (throttled, as today), hover | Reveal |
| Touch tap on the picture | Toggle: SHOWN → HIDDEN (`revealedUntil = 0`, unless pinned), HIDDEN → SHOWN |
| Double-tap on a side (touch) | ±10, and reveal |
| Volume keys where the volume is the system's | Nothing |
| Status PAUSED | Pin PAUSED |
| A menu open | Pin MENU |
| A screen reader running | Pin SCREEN_READER (controls never leave: M1) |
| Keyboard, controller, or remote focus inside the controls | Pin FOCUS_IN_CONTROLS |
| A desktop pointer resting over the bottom row or top bar | Pin POINTER_OVER_CONTROLS |
| Dragging the timeline | Pin SCRUBBING |
| A held seek (10.3) | Pin SEEK_HOLD |

**Reduced motion:** modes switch with no fade (as `Motion` already does for reduced motion). **The back preview** fades the chrome by alpha (4.4) without changing the mode.

### 7.4 MINI and SYSTEM

MINI keeps M9's dock statuses (`miniStatus`, `miniAction`), with the loader over the docked picture (`MiniPlayerSurface`) only after the same grace. SYSTEM is 6.4.

---

## 8. Failure

| Where | What the viewer gets |
| --- | --- |
| FULL | The FAILURE mode: the reason, Try again where the failure can pass, and Back/Close. Back closes |
| MINI | **The failure dock** (no escalation): the surface is placed out of sight (the dead picture is never left showing); the dock's picture box shows the failure glyph; the status reads "Playback stopped"; the button reads "Try again" (if retryable) or is absent; the dock keeps Close. Announced once. Activating the dock's picture opens FULL + FAILURE |
| SYSTEM | 6.4: the glyph in the window; expanding shows FULL + FAILURE |
| Next-episode autoplay fails (no plan, or the replan throws) | The session does **not** close silently. It becomes a failure ("The next episode couldn't be played", no retry; the finished episode's place is kept), shown in whichever presentation holds it, by the rows above. Today it notifies and closes; that changes |
| The source disappears | An engine failure as today (`checkSource` and the availability check run), by the rows above. Removing the source on purpose still ends the session first (M9) |
| A subtitle cannot be converted or delivered | Never a failure: the picture goes on, and the existing notice ("subtitle unavailable") is said; in MINI the dock announces it; in SYSTEM nothing is shown until expanded. The planner's `SUBTITLE_UNAVAILABLE` is unchanged |
| The waiting limit (30 s) | As today; it is a failure shown by the rows above |

---

## 9. Per-work subtitle preference (profile-scoped)

- **Key:** `profile_setting(profile_id, key = "subtitles.work:<workId>", value)`. `workId` is the movie's `MediaId`, or for an episode its show's `MediaId`, so a choice made in one episode carries through the show, which is what a viewer means by turning subtitles on.
- **Value** (a small stable text format, parsed and written in one place in `library`): `off`, or `on;lang=<iso639|_>;forced=<0|1>;sdh=<0|1>`.
- **Written** only when the viewer explicitly chooses in the Subtitles menu (`selectSubtitle`, `chooseInFile` with Off or Explicit), with the chosen track's language and flags, for the **session's owner profile** (F9). It is never written for automatic selection.
- **Read** at every plan of the work: `play`, next or previous episode, retry, a planned-again failure, and `continueFromOutside`. An explicit in-session choice for the current work wins over it.
- **Resolution, pure, in `core/playback`:** a new `SubtitleChoice.Preferred(language, forced, hearingImpaired)`. The planner chooses, among the version's subtitle tracks (embedded, then external), the first that matches language, then forced, then SDH; `language = null` matches only tracks with no language. No match means `Automatic`, and the stored preference is left unchanged. `off` means `SubtitleChoice.Off`. Burn-in rules are unchanged: a `Preferred` match counts as the viewer's choice, as `Explicit` does today.
- **Isolation:** `ProfileIsolationTest` already holds `profile_setting`. Add a test that two profiles' preferences for the same work are independent, and that a profile reset (`deleteProfileSettings`) removes them. Clearing watch history does not remove them (they are preferences, not history).
- **No migration and no new table.** A stream index is never stored.

---

## 10. Playback parity

### 10.1 Android chapters (Media3 only)

- `Media3Player.publish()` reads `Chapter` entries (`androidx.media3.extractor.metadata.Chapter`) from `player.currentTracks`: each selected group's track `Format.metadata`, video first, then any other. It drops hidden ones, maps them to `core` `Chapter(title = label text or null, startMs)`, sorts and de-duplicates by start, and sets `PlayerState.chapters`. The existing menu, timeline marks, and `SkipSegments` then work unchanged (MKV chapters named "Intro" now offer a skip on Android).
- **Exposed:** Matroska/WebM chapters, and MP4 Nero `chpl` and QuickTime chapter tracks, all on direct play. **Not exposed (documented in PLAYBACK.md):** HLS (every converted Jellyfin stream), MPEG-TS, AVI, and any chapter format Media3 1.11.1 does not parse. Server-provided chapters for converted streams are V1.x.
- **Tests:** a pure mapping test over `Metadata` built in a unit test, and an instrumented test that plays generated MKV and MP4 files with chapters (the fixture generator already makes media) and reads `PlayerState.chapters`.

### 10.2 Desktop media keys (focused window only)

- The shell normalizes to the existing `Key.MEDIA_*`; `InputMapping` and the session are unchanged. No playback code sees a platform key code.
- **Windows:** the layer P3 proves. Either AWT delivers them (`extendedKeyCode` / raw code mapped in `Keys.kt`), or a JNA subclass of the main window's procedure turns `WM_APPCOMMAND` (`MEDIA_PLAY_PAUSE`, `MEDIA_PLAY`, `MEDIA_PAUSE`, `MEDIA_STOP`, `MEDIA_NEXTTRACK`, `MEDIA_PREVIOUSTRACK`, `MEDIA_FAST_FORWARD`, `MEDIA_REWIND`) into `app.onKey`. JNA is already a dependency.
- **Linux:** only if P3 shows the keys reach a focused AWT window (X11 `XF86Audio*` keysyms). Otherwise they are recorded as V1.x: on desktops whose session grabs media keys, they are delivered over MPRIS, which needs D-Bus and is excluded.
- While the dock plays, media keys still reach the session (M9). They never change input modality (INPUT_MODEL.md).
- **Tests:** mapping unit tests for each source of key events, and one real-window test on Windows where P3 shows a synthetic path (`SendInput` with `VK_MEDIA_PLAY_PAUSE`), otherwise a recorded manual check.

### 10.3 Held seek acceleration (every directional input; required on television)

- A pure `SeekHold` in `core/playback`: `start(direction, positionMs, durationMs)`, `repeat(nowMs)`, `release()` → one target. Steps per repeat: 10 s for the first second held, 30 s until 3 s, then 60 s. A reversal of direction restarts the hold. The target is clamped to `[0, duration]`.
- **One seek:** while held, the timeline shows the target (Pin SEEK_HOLD, and the remote's timeline preview). The seek is issued once, on release, or 400 ms after the last repeat where a platform sends no release. A single press (no repeat) is today's 10 s seek, immediate.
- **Input:** shells report whether a key is a repeat (Android `KeyEvent.repeatCount > 0`; AWT repeats a `KEY_PRESSED` with no `KEY_RELEASED` between; desktop gamepads already repeat directions) and the release of the seek keys. `AppState.onKey(key, repeat)` and `onKeyReleased(key)` carry it. Semantic actions stay `SEEK_FORWARD` and `SEEK_BACKWARD`.
- **Tests:** `SeekHold` unit tests (step schedule, clamps, reversal, one commit); an `AppState` test where twenty repeats produce exactly one `seekTo`; the Fire TV walk with a real remote.

### 10.4 The television sidecar-subtitle explanation (the 2026-10-17 decision)

- Capability: `DeviceCapabilities.readsSidecarSubtitles`, false on a television on Android 13+ (API 33+) whose local folders are read through `READ_MEDIA_VIDEO`; true elsewhere (Fire OS 7/8 and phones read them).
- When the playing version is from a **local** source, the device cannot read sidecars, and the version has no subtitle track, the Subtitles menu is offered with one non-actionable row: "Subtitle files beside videos can't be read on this TV". It is focusable, read by assistive technology, and chooses nothing. Network sources are unaffected.
- **Tests:** a unit test of the condition; a UI test of the menu row on a television layout. The Fire TV (Android 11) shows nothing new, and that is asserted.

---

## 11. Visual redesign (the movie is the interface; the controls are temporary)

Reuse DESIGN.md's tokens, motion, and glass tiers. No new material, no new type scale. Measured targets:

| Element | Today | M10 |
| --- | --- | --- |
| Centre play/pause (touch layouts) | 72–84 dp glass disc | The glyph alone: 40 dp phone, 48 dp tablet, in a 72/84 dp touch target, with a soft radial shadow (black, about 35%) for legibility; no disc |
| ±10 (touch) | 54–64 dp glass discs | 28/32 dp glyphs at `contentSecondary`, in 56/64 dp targets; lit to `contentPrimary` for 160 ms on press (no lift on reduced motion) |
| Bottom area | A FLOATING glass panel with the timeline over a row of actions; content up to about 148 dp above the bottom | **One row, no glass panel**, on a scrim. Wide windows (≥ 600 dp): `[play/pause on pointer layouts] elapsed · timeline · −remaining · actions`. Under 600 dp: the timeline row with the actions at its end, wrapping to a second line only when actions would truncate. Bottom block ≤ 72 dp on a phone on its side, ≤ 96 dp (with the title) on tablet and desktop |
| Title | Top-left on phone, above the bar elsewhere | Unchanged in place; one line plus a secondary line; it fades with the chrome |
| Timeline | Slider | A thin track; chapter marks kept. A handheld's and a television's: 4 dp with a thumb only on hover, focus, or drag. A desktop's (final pass, DESIGN.md "Player chrome scales with the desktop window"): a thumb at rest, a stronger inactive track with a soft edge, and a target larger by tier |
| Scrims | Top 24% at 0.66; bottom 46% up to 0.84 | Top 18% at 0.55; bottom 32% up to 0.72. With reduced transparency or high contrast: the bottom row on a solid `surface` plate, scrims at 0.85 |
| Loader | `RefluxLoader` 52 dp, drawn independently | The same loader, only in LOADING, on its material disc as DESIGN.md defines it; nothing else in the centre |
| Menus | Glass panel | Unchanged (glass is for the functional layer) |
| Television | Remote transport and list hints on a glass panel | The same structure, the lighter scrims and row, LOADING exclusive, held seek, focus visible on the timeline |

**Acceptance:** with the controls shown on a 16:9 film in a 16:9 window, the chrome covers no more than 30% of the picture's area by its opaque-equivalent coverage (a pixel probe on the scrims). In HIDDEN nothing of the chrome is drawn. The structural baseline approach (DECISIONS.md, 2026-11-12) is extended to the player: the layers present per mode, and the row's height per window class. No committed pictures.

---

## 12. Platform behavior and accessibility

### 12.1 By platform

| | Phone | Tablet | Desktop | Television / Fire TV |
| --- | --- | --- | --- | --- |
| Full player | Any orientation; the fullscreen button turns it to landscape (M9) | As the phone; Android 16 may ignore the lock on sw600dp | Window or fullscreen | Whole screen |
| Back in FULL | Collapse, predictive preview | Collapse, predictive preview | Collapse (Esc) | Close |
| Mini-player | Row above the bar | Card, or the row when narrow | M9 dock | None |
| PiP | Auto on leave while playing; Back at root while playing in the dock | Same | None | None |
| Chrome | Section 7, touch transport | Section 7, touch transport | Section 7, bar transport, hover pins | Section 7, remote transport |
| Media keys | System (session) | System (session) | Focused window (10.2) | Remote's keys (existing) |
| Held seek | Keyboard/controller if attached | Same | Keys and controllers | Remote |

### 12.2 Accessibility (M1 holds)

- **The dock** stays one pane, "Mini player", with M9's three stops. The failure dock keeps three stops (what is playing / the failure, Try again or none, Close).
- **Announcements:** collapse says "Mini player, <title>" (a pane change); expand says the player's pane title; a failure in the dock and in the player, and LOADING ("Loading <title>") are said once per change, never repeated per tick.
- **Focus restoration:**
  - FULL → MINI: focus returns to where it was on the page before the player opened (the control that started playback), or to the dock's picture when that element is gone.
  - MINI → FULL: focus goes to the player's play/pause, or to Try again in FAILURE.
  - PiP expanded → FULL: as MINI → FULL.
  - PiP closed: the next time Reflux is shown, focus is on the page as it was.
- **Controls never leave under a screen reader** (Pin SCREEN_READER), or under keyboard/controller focus inside them. LOADING still replaces the transport for everyone, because no transport action can do anything while loading; Back and the title stay reachable.
- **200% text:** the one-row bar wraps (11), never truncates an action's name; the dock row keeps M9's behavior.
- **Reduced motion:** no back preview, no travel, no fades; tested.
- **Keyboard, controller, remote:** every action reachable; Back routes as section 4; `I` toggles the dock; the AboutUiTest carry (scope item 9).

---

## 13. Tests

**Automated (all run in CI; targeted locally):**

| Area | Tests (new unless noted) |
| --- | --- |
| Presentation | `PresentationTest` extended: FULL/MINI/SYSTEM transitions; `collapse`/`expand` refused in SYSTEM; `leaveSystem(expanded)` → FULL; `leaveSystem(closed)` → closed with the place recorded; no load, plan, engine, or reporter change across all of them; owner profile constant |
| Back routes | `BackRouteTest`: the precedence table row by row on each platform profile (phone, tablet, desktop, television, API < 31); the invariant `backRoute != SYSTEM ⇔ dispatch(BACK)`; the docked session never ended by Back |
| Predictive back | `BackPreviewTest`: progress → surface bounds and chrome alpha; cancel restores both with no session change; commit collapses; reduced motion ignores progress; an orientation-releasing commit places the surface out of sight, then in the dock |
| Chrome | `ChromeTest`: every precedence edge; LOADING exclusive (no layer of SHOWN in LOADING, asserted on the composed tree too); the grace (buffering under 600 ms changes nothing; over it is LOADING unless scrubbing or holding a seek); one deadline; the reveal/pin table; menu closed at the grace; a screen reader never hides |
| Failure | Dock failure: surface out of sight, statuses, actions, announcement, no escalation; next-episode failure becomes a stated failure in each presentation; SYSTEM failure then expand → FULL + FAILURE |
| PiP (shell logic) | Pure functions extracted from `MainActivity`: eligibility, params (aspect clamp, source rect = picture box), close-vs-expand classification (P2's rule); unit-tested on the JVM |
| Android surface | Instrumented `SurfaceContinuityTest` (from P1): section 5.2's contract on the API 36 emulator in CI and the Phone 2a by hand |
| Ownership | The owner is captured once; every reporter and preference write uses it; a profile switch still ends the session first in each presentation |
| Subtitle preference | Planner resolution of `Preferred` (match, partial match, none → Automatic, Off, burn-in rule); persistence round trip; two profiles independent; reset removes; next episode inherits through the show key |
| Chapters | Metadata → `PlayerState.chapters` mapping (hidden dropped, sorted, titles); instrumented MKV and MP4 |
| Media keys | Key normalization for Windows (and Linux if P3 passed); media keys reach the docked session |
| Held seek | `SeekHold` schedule and commit; twenty repeats = one `seekTo` |
| TV subtitles | The condition, and the menu row on television |
| Accessibility semantics | Dock and failure-dock stops; announcements once; focus restoration in each transition (offscreen); 200% text bar wrap; reduced motion |
| System bars and insets | Bars shown in MINI; the dock clear of the gesture area by window class |

**Physical (once each, at the milestone's end, results in VERIFICATION.md "M10"):**

| Device | Walk |
| --- | --- |
| Nothing Phone 2a | P1 and P2; predictive back: slow drag with cancel ×3, commit ×3 (portrait and landscape-locked), 3-button Back; FULL → MINI → FULL with subtitles on; PiP by home gesture and by Back at root; expand, close, and launcher-in-PiP; insets and the gesture bar; TalkBack: dock, failure dock, collapse/expand focus, PiP exit focus, loading announced (per the safety rules in the TalkBack memory: test items only) |
| Xiaomi Pad 7 | The card dock; PiP; predictive back; split-window narrow dock; TalkBack walk as above; 200% text |
| Fire TV Stick 4K Plus | Lighter chrome, LOADING exclusive, held seek with the real remote (one committed seek per hold), Back closes the player, root Back leaves as before, the sidecar row absent (Fire OS 8 reads sidecars) |
| Windows 11 (real window) | Esc collapses; P3 and media keys; the redesigned bar with hover pins; `MiniPlayerWindowTest` still green |
| Linux container (X11) | `MiniPlayerWindowTest` and the player window tests green; media keys per P3 |

The Google TV path (Android 16 Back on a TV launcher, and the sidecar row on API 33+) is checked on the Google TV emulator. No physical Google TV is required. No physical device walk is added for anything M10 does not change.

---

## 14. Documentation and evidence

- **PLAYBACK.md section 18:** SYSTEM as a presentation; the Android shell; the chrome model's reference; Android chapter support and its limits; held seek; the subtitle preference's resolution rule.
- **NAVIGATION.md section 8:** Back's precedence (4.2), the handheld mini-player, the failure dock, PiP.
- **INPUT_MODEL.md:** Back routes; controller Back at the root on Android; repeats and releases; media keys by platform.
- **DESIGN.md:** the player's layers and the measured targets in 11.
- **WATCH_STATE.md "Profiles":** the subtitle preference as viewer state.
- **ARCHITECTURE.md:** one line that the Android shell places its surface by `VideoFrame` and reports PiP as `SYSTEM`.
- **DECISIONS.md:** only what M10 decides beyond the 2026-11-13 entry (for example P1's travel result, or Linux media keys to V1.x).
- **ROADMAP.md:** M10 items and states; the version is unchanged.
- **CHANGELOG.md `Unreleased`:** the user-visible changes.
- **VERIFICATION.md "M10":** Step 0 results, test counts, device walks with OS and API levels, and what was not done.

---

## 15. Completion criteria

M10 is complete when all of these hold:

1. Step 0's three results are recorded, and the work follows them.
2. No root Back callback is enabled at the root on API 31+; the back-to-home animation runs from Reflux's Home on the Phone 2a; Back in the full player collapses on handhelds and the desktop, with a gesture preview that cancels cleanly.
3. The handheld mini-player works on the Phone 2a and the Pad 7 with one surface, and section 5.2's contract is proven by the instrumented test and on the phone.
4. PiP is the `SYSTEM` presentation: entered by leaving while playing and by Back at root; expanded → FULL; closed → session closed; failures and endings behave as 6.4; no service was added.
5. The chrome is the reducer of section 7; LOADING is exclusive, and no other visibility timer exists in the player.
6. The failure rules of section 8 hold in every presentation, including next-episode failure.
7. The redesign meets section 11's targets, with reduced transparency and high contrast handled.
8. The subtitle preference is profile-scoped, resolved by description, and needs no migration.
9. Android chapters, desktop media keys (Windows; Linux per P3), held seek, and the television explanation are built and tested, or a P3 limit is recorded as V1.x.
10. F5 and F8 are fixed with tests; `AboutUiTest` is fixed or its cause recorded with evidence and not weakened.
11. The automated tests in section 13 pass locally (targeted) and the full `check` passes on hosted CI. The physical walks in section 13 are recorded.
12. The documents in section 14 are updated; the version is unchanged; no tag is created; one commit (or a small series) with a clear message.

---

## Sources (read 2026-11-13)

- Android 16 behavior changes (predictive back defaults, `onBackPressed` and `KEYCODE_BACK`): https://developer.android.com/about/versions/16/behavior-changes-16
- Predictive back gesture; custom back navigation (callbacks tied to UI state; enabled callbacks suppress the system animations): https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture, https://developer.android.com/guide/navigation/navigation-custom-back
- `PredictiveBackHandler`: https://developer.android.com/reference/kotlin/androidx/activity/compose/PredictiveBackHandler.composable; Activity releases (1.12 NavigationEvent, 1.13.0 stable): https://developer.android.com/jetpack/androidx/releases/activity; Navigation Event: https://developer.android.com/guide/navigation/navigation-event
- Picture-in-picture (auto-enter, `sourceRectHint`, seamless resize, media-session controls, onStop/onStart): https://developer.android.com/develop/ui/views/picture-in-picture; Compose PiP: https://developer.android.com/develop/ui/compose/system/pip-enter, https://developer.android.com/develop/ui/compose/system/pip-setup; `PictureInPictureUiState` (`isTransitioningToPip`, API 35): https://developer.android.com/reference/android/app/PictureInPictureUiState; Jetpack PiP library (alpha): https://developer.android.com/develop/ui/views/pip-jetpack
- Media3 sessions: https://developer.android.com/media/media3/session/control-playback, https://developer.android.com/media/media3/session/background-playback; Android 17 background audio hardening: https://developer.android.com/about/versions/17/changes/bg-audio
- Media3 chapters: read from the resolved `media3-extractor-1.11.1.aar` (`Chapter`, `MatroskaExtractor$Track.maybeAddChaptersMetadata`, `Mp4Extractor` QuickTime chapters, `BoxParser.parseChpl`); androidx/media issues #2803 (MP4 chapters) and #3438 (Matroska `ChapterAtom` in 1.11.0)

## Final qualification record (2026-11-13)

Section 6.7 stands as specified and was confirmed on the Phone 2a and the Pad 7: **a recreated activity ends its playback by design**, so a configuration change that still recreates it (such as `font_weight_adjustment`) leaves no engine, audio, or session, and records the place; it is not a leak (the leak was the old code, `ActivityRecreationOnDeviceTest`). Implemented and tested, physically unqualified, known intermittent, and deferred items are classified in ROADMAP.md, "M10", and VERIFICATION.md, "Final qualification pass". M10 is not sealed.
