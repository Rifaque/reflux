# Reflux Decision Log

## 2026-10-08 — Product identity

Reflux is treated as a universal media player rather than a Jellyfin-specific client.

## 2026-10-08 — Jellyfin architecture

Jellyfin is a source adapter, not the application foundation.

## 2026-10-08 — Local-first

Local media and locally cached library state are first-class. Core functionality must not require a Reflux account.

## 2026-10-08 — Virtual organization

Reflux may organize media virtually, but must not silently rename, move, delete, or otherwise modify user media files.

## 2026-10-08 — Automatic metadata

Automatic identification, metadata, and artwork are core product behavior. Users should not need sidecar files such as poster.jpg or .nfo files for the default experience.

## 2026-10-08 — Default experience

Out-of-the-box quality is a hard requirement. Customization is additive.

## 2026-10-08 — Search foundation

Smart search begins with deterministic, rule-based parsing. Natural-language intelligence can be layered on top later.

## 2026-10-08 — Controller architecture

Controller support is normalized around semantic actions, with first-class PlayStation and Xbox support and adaptive UI transitions.

## 2026-10-08 — Version selection

When otherwise equivalent, local media should be preferred. Best Version logic may also consider resolution, compatibility, HDR, audio, availability, and reliability.

See the 2026-10-13 entry on local-first version selection for what "otherwise equivalent" means.

## 2026-10-08 — Platform scope

Initial target scope is Android phone/tablet, Android TV / Google TV, Windows, and Linux. iOS/iPadOS is not a v1 commitment.

## 2026-10-08 — Visual direction

The design language combines restrained premium ergonomics, cinematic media presentation, and liquid glass without copying any single reference product.

## 2026-10-08 — Implementation stack: Kotlin Multiplatform

**Decision.** Reflux is implemented in Kotlin. The Media Core is Kotlin Multiplatform `commonMain` code with no platform dependencies. Platform shells are native Android (Jetpack Compose, including Compose for TV) and Compose Multiplatform on the JVM for Windows and Linux.

**Alternatives evaluated.**

| Option | Platform coverage | Playback | Fit with Media Core | Main problem |
| --- | --- | --- | --- | --- |
| Kotlin Multiplatform + Compose | Android, Android TV, Windows, Linux (iOS possible later) | Media3 natively on Android; libmpv on desktop | Core shared as plain Kotlin; platform code behind interfaces | Desktop video embedding needs native-surface work |
| Flutter + media_kit | All targets | libmpv everywhere | Core in Dart | Android TV integration (display modes, tunneling, passthrough) goes through plugins; media_kit maintenance risk |
| Rust core + native shells | All targets | Any | Strong | Two languages plus FFI bindings; no mature Rust desktop UI, so desktop would still need another UI stack |
| Qt/QML + C++ | Desktop strong, Android weaker | libmpv | Strong | Weak Android TV ergonomics; C++ cost; Qt licensing constraints |
| Web shell (Electron/Tauri) | Desktop; Android TV poor | Browser codecs only | — | Cannot meet the serious-playback requirement |

**Why.** Android and Android TV are the first targets and their serious-playback features (HDR display modes, Dolby Vision, frame-rate matching, tunneled playback, audio passthrough) are only reached reliably from native Android code. Kotlin is native there, runs on the desktop JVM, and lets the Media Core be shared without an FFI boundary. One language keeps the system small.

**Consequences.** The core must not use JVM-only APIs; platform facilities (filesystem walking, SQLite drivers, HTTP, decoders) sit behind interfaces. Builds use Gradle with a version catalog. The Android SDK is not required to build or test the core (still so with the Android client in the tree: see 2026-10-13).

## 2026-10-08 — Playback engines

Playback is a platform capability behind a common contract, not a single cross-platform engine.

- **Android / Android TV:** AndroidX Media3 (ExoPlayer, Apache-2.0) is the primary engine: MediaCodec hardware decoding, HDR/Dolby Vision output, tunneling, passthrough, and frame-rate matching are platform features it integrates with. libmpv remains a candidate fallback engine for formats Media3 handles poorly.
- **Windows / Linux:** libmpv (built LGPL, without GPL-only components) provides hardware decoding (D3D11VA, NVDEC, VAAPI), libplacebo tone mapping, broad codec and container coverage, and libass subtitle rendering.

Each engine reports `DeviceCapabilities`; product logic (Best Version, player UI) only consults those capabilities.

## 2026-10-08 — Identity, versions, and locations are separate

A work (`MediaItem`: movie, show, season, episode) is separate from its `MediaVersion`s, and each version has one `MediaLocation` (source + source-relative path). IDs are derived deterministically from stable keys (normalized title/year/episode for works; source + path for versions) so rebuilding the index yields the same IDs. Watch state belongs to works, never to files.

## 2026-10-08 — Unknown technical information never blocks playback

Compatibility checks (`PlaybackAssessor`) treat unknown stream properties as "no evidence of a problem". Reflux ranks and explains versions but does not refuse to try a file it could not inspect; the platform player remains the final authority.

## 2026-10-08 — Library persistence: SQLite through SQLDelight

The library index, metadata cache references, watch state, favorites, identity overrides, and version preferences live in one SQLite database owned by Reflux, accessed through SQLDelight (Apache-2.0, Kotlin Multiplatform, compile-time-checked SQL, reactive queries). SQLite is available on every target and is the right tool for an embedded, offline, single-user index.

- SQL stays within the SQLite 3.18 dialect so the platform SQLite of older Android TV devices works; upserts are written as insert-or-ignore plus update.
- Desktop uses the JDBC driver (sqlite-jdbc) with WAL; Android will use the platform driver.
- Everything except user state (watch state, favorites, overrides, preferences) can be rebuilt by rescanning.

## 2026-10-08 — Offline and unmount safety

A scan only changes the cached library for a source that is reachable. If a source is unreachable, or reachable but contains no media although Reflux knows media on it (the typical unmounted drive or share leaving an empty mount point), the cached library is kept and the source is marked unavailable. Only a reachable, non-empty source can remove versions. Removing a work's last file hides the work but keeps its watch history; an identity correction carries the history to the corrected work.

## 2026-10-08 — Local folder adapter

`LocalFolderSource` (desktop JVM) walks a folder read-only, follows symbolic links with loop protection, skips hidden and system folders, and tolerates unreadable entries. Android will need a separate Storage Access Framework adapter because user-chosen folders are `content:` trees there, not paths.

## 2026-10-08 — libmpv integration

libmpv is bound through JNA (Apache-2.0/LGPL-2.1) using only the stable string-based client API, so any libmpv 2.x works. Reflux does not load the user's `mpv.conf` or scripts (`config=no`): the default experience must be identical everywhere, and advanced options will be exposed by Reflux itself. External subtitles are attached with the per-load `sub-files` list so that tracks are complete when the file is loaded, before default track selection runs. libmpv is a runtime dependency of desktop builds and is not bundled in the repository.

## 2026-10-08 — Default track selection

Track choice is product logic, identical across engines (`TrackSelector`): audio in the user's first preferred language, else the file's default track; subtitles automatically when the audio is in a language the user does not prefer, forced subtitles otherwise, and non-SDH tracks first. The preferred languages come from the device locale, so this works with no configuration.

## 2026-10-08 — Media probing

File-name hints are a first guess. Real stream data comes from a `MediaProber` (libmpv on desktop, MediaExtractor on Android), run in the background for new or changed files. Probed facts replace hints, except that a Dolby Vision release tag upgrades an HDR10 probe result because probes without DV support only see the base layer.

## 2026-10-08 — Metadata providers

Metadata comes from providers behind `MetadataProvider`; TMDB is the first. Matching is deterministic (`MetadataMatcher`) and conservative: an uncertain match is left for the Identify flow rather than guessed, except that decisive popularity resolves exact-title ties when no year is known. Provider metadata changes display fields only (titles, years, overviews, artwork), never identity. The provider credential is an application credential injected at build time; users never configure providers. HTTP is reached only through the core `HttpFetcher` so providers stay platform-free (the JDK client on desktop).

## 2026-10-08 — Change detection

Sources emit coarse "something changed" signals (`ChangeNotifyingSource`); the pipeline debounces them and runs a full, deterministic rescan of that source. Rescans re-parse paths (cheap) but only re-probe changed files, so correctness never depends on interpreting individual file events. Availability is re-checked periodically so reconnected drives and shares are rescanned automatically. Path-level incremental scans can be added later as an optimization without changing these semantics.

## 2026-10-08 — Smart search vocabulary comes from the library

Smart search recognizes genres and people only if they exist in the user's own library metadata, so a word is never interpreted as a filter that could match nothing by construction, and surnames only count when unambiguous and in context ("Nolan movies", "with Gosling"). A bare year stays title text unless the query is otherwise structured, because titles like "2012" and "1917" exist. Resolution classes use the nominal height (a 3840×1600 scope film is 4K).

## 2026-10-08 — Default Home and collections

Home is composed by fixed rules, not configuration: Continue Watching, Recently Added, Favorites, unwatched movies, 4K HDR, provider franchises with at least two present movies, TV shows, the library's four strongest genres, and all movies; empty or too-small rows are omitted. Rows carry an optional smart query for "see all". User collections are either manual lists or saved smart searches, which stay current without maintenance.

## 2026-10-08 — Catalog sources share the identity pipeline

Media servers are catalog sources whose items are converted into the same identity description a file-name parse produces, then identified by the same deterministic rules. This keeps one notion of identity: a movie on a Jellyfin server and the same movie in a local folder become one work with two versions. User corrections apply to server items too; to make corrections work offline, each server version stores the server's own description. Server watch state is adopted only when newer than Reflux's (replaced on 2026-10-13 by reconciliation against what the server last said); server favorites are added but never remove local ones.

## 2026-10-08 — Jellyfin adapter

Jellyfin is integrated as a catalog source, not as the application model. Reflux reads the user's catalog (movies, episodes, every media source of an item as a version), plays Jellyfin's direct static streams in its own player, and reports progress and played state back best-effort; Reflux's local state is always updated first. Server-side transcoding is intentionally not used in v1: Best Version and the device capability model decide what to play. Passwords are used only to sign in; the access token is kept with the source configuration.

## 2026-10-08 — Identity aliases

Identity stays key-based and deterministic, and provider IDs refine it: works sharing a provider identifier are unified by recording an alias from one identity key to the other, applied during identification. This merges differently named copies across sources without letting providers define identity on their own, and without fragile renaming of stored works.

## 2026-10-08 — Playback must degrade, not fail

libmpv's defaults are tuned for hardware but fall back instead of failing: video outputs fall back from `gpu-next` to plain outputs when no usable GPU driver exists, and audio falls back to a null output when no device is available. Found by running real playback in a GPU-less, audio-less environment.

## 2026-10-08 — Developer CLI

`tools/cli` is a long-lived diagnostic tool, not a product shell: it wires the engine exactly as a desktop shell does and makes identification, metadata, and playback behavior observable on real libraries. Tests run against file-backed databases, because the in-memory driver hid a connection-threading bug that the CLI exposed.

## 2026-10-08 — First network file source: WebDAV

WebDAV is the first network file source because it is plain HTTP(S): it reuses the `HttpFetcher` abstraction, streams directly into the player with standard headers, works through Nextcloud and most NAS devices, and needs no protocol library. SMB remains a candidate; on desktop, OS-mounted SMB shares already work as local folders. The adapter is read-only by construction (`PROPFIND`/`GET` only).

## 2026-10-08 — SMB through a loopback stream server

SMB shares are read with smbj (Apache-2.0, works on desktop JVMs and Android). Because Media3 cannot open `smb://` and typical libmpv builds lack libsmbclient, share files are served to players by a minimal loopback-only HTTP server with byte-range support and unguessable per-file tokens. The same URLs serve probing and artwork, so SMB needs no special cases elsewhere. Verified against a live SMB2 server, including probing and playback.

## 2026-10-09 — One experience layer, thin shells

The screens, navigation, playback session logic, and the Compose rendering of the design system live in one platform-free module (`ui`, Compose Multiplatform `commonMain`). Shells implement a small `Platform` interface and feed normalized input in. The desktop shell is the first consumer; the Android shells are meant to reuse the same module rather than re-implement the experience.

- The UI is built on Compose foundation with Reflux's own components. No Material library: the design language is Reflux's, and a component kit would have to be fought rather than used.
- Library access from the UI is asynchronous and keyed on a revision counter, so screens stay current without each one subscribing to queries.
- Rendered screens are tested offscreen against a real library over a temporary folder, with a fake provider and a fake engine.

## 2026-10-09 — Desktop video: native surface, controls in an overlay window

**Decision.** On desktop, libmpv renders into a native child window (`wid`) of the main window. The player's controls are a transparent, undecorated, never-focused window owned by the main window and kept exactly over the video surface.

**Alternatives evaluated.**

| Option | Result |
| --- | --- |
| Native surface inside the Compose window, Compose drawn over it (`compose.interop.blending`) | Tested: the video disappears. The toolkit clips a native child wherever another native child overlaps it, and Compose's own layer is one. |
| libmpv render API into a texture or bitmap drawn by Compose | One window and real blur over video, but every frame is read back and re-uploaded: no zero-copy hardware decoding, no HDR output, no engine-driven frame timing. It gives up what libmpv was chosen for. |
| Native surface with controls in a layered overlay window | Chosen. Tested on Windows with real playback: translucent controls compose over live video, follow moves and resizes, receive pointer input, and leave keyboard focus with the main window. |

**Consequences.** The engine keeps its direct path to the display. Player glass cannot blur the video behind it, so over video the materials use a denser tint; Android's `SurfaceView` has the same property, so the player design does not depend on blurring video anywhere. A fully transparent pixel lets pointer input fall through to the surface, so the overlay is filled with an imperceptible tint. All keys are taken at the application level and routed through the semantic input path, so input does not depend on which native window has focus.

## 2026-10-09 — Desktop fullscreen keeps the window

Fullscreen must not recreate the main window, because the playing engine renders into a child of it. On Windows the toolkit's fullscreen leaves the title bar on a decorated window, so the shell removes the frame styles and stretches the same window over its monitor (borderless fullscreen) and restores them afterwards. Elsewhere the toolkit's fullscreen is used; X11 window managers put the window back in a corner afterwards, so the shell restores its place once the manager has settled. Leaving the player restores the window to how it was when playback started.

## 2026-10-11 — Failures are states, worded in one place

Nothing the user asks for may fail silently or end the application.

- **Contracts name the failure.** A playback engine reports *why* it stopped as a `PlaybackFailure` (unreachable, unsupported, output, unknown) next to its own wording; offering a folder yields a `FolderAddition` (added, already covered, not a folder, unreadable); the pipeline exposes whether the metadata provider could be reached.
- **The experience layer words it.** `Problems` holds what the user is told for every failure, in their terms and with what they can do about it. Engines and shells never put text on screen.
- **Detail goes to diagnostics.** `Platform.report` receives the cause; on desktop it is appended to `reflux.log` in the data directory. User actions run through one guard that reports and explains, and the shell has a last-resort handler, so an exception cannot take the window down.
- **The library catches up.** A file that cannot be opened triggers a rescan of its source, and a details page whose work has no files left says so instead of offering a dead Play button.

## 2026-10-11 — One folder, one source

A folder that is already covered by a folder source is not added again, and a folder that contains existing folder sources takes them over. The same file is therefore never two versions of a work. Watch history survives the takeover because it belongs to works, not to files or sources. The rule lives in `desktop:engine`, so the shell and the CLI behave the same.

## 2026-10-11 — Desktop controllers through platform APIs

**Decision.** The desktop shell reads controllers itself, with no input library:

- **Windows:** XInput for Xbox controllers and everything presenting as one; the multimedia joystick API for other HID game controllers (DualShock 4, DualSense, Switch Pro, generic pads). XInput devices also appear as joysticks and are recognized by the `IG_` marker in their raw-input device path, so each controller is read once.
- **Linux:** the kernel joystick interface (`/dev/input/js*`). Button and axis numbers are resolved through the device's capabilities in sysfs, so the mapping follows event codes (`BTN_SOUTH`, ...) rather than per-driver tables. It needs only read access and no native code.

**Alternatives.** SDL would cover more devices and report families directly, but adds a bundled native library to every distribution for what is a few hundred lines here. JInput is unmaintained. Raw Input with HID parsing, and evdev with ioctls, are more complete but need a message loop or native calls for no benefit to navigation and transport control.

**Consequences.** Readers produce snapshots of buttons *by position*; one translator turns them into presses (with hold-to-repeat and the left stick as a D-pad) for the same semantic path keys use. Controller APIs are system-wide, so input is ignored unless a Reflux window is the active one. Gyro, touchpads, rumble, and per-button remapping are out of scope until a feature needs them. Numbered-button layouts for controllers without a vendor driver follow the common convention and may be wrong for unusual pads.

## 2026-10-11 — The player's menus are session state

Chapters, audio, subtitles, and speed are lists owned by `PlaybackSession`, with a highlighted option moved by navigation actions and chosen by Select. The video's controls live in a window that never takes focus, so toolkit focus cannot drive them; state driven by semantic actions works identically for a pointer, a keyboard, and a controller. While a menu is open the input context is browsing, so directions navigate instead of seeking.

## 2026-10-11 — Real-window tests act through the application's own event queue

End-to-end tests run the real shell in its real window. They find controls through the window's semantics, deliver pointer and key events to the toolkit's event queue, operate the native folder dialog through window messages, and ask the system which window the pointer would hit. They never move the system pointer, so they can run on a machine in use and on a virtual display in CI. What only a physical device can show (a controller in someone's hands) is covered up to the reader, which is injectable.

## 2026-10-12 — Linux: the toolkit's X11 error handler is guarded while libmpv is loaded

Xlib has one error handler per process. The toolkit's tolerates errors, because harmless ones are routine. libmpv installs its own for every video output it tries and resets the handler to Xlib's default when an output goes away; the default ends the process on any error. Found by running real playback in a real X11 window: Reflux quit about every other time the player was closed. Tracing the handler showed the resets come in two short phases only: at engine start, once per output tried before one works (several under software rendering, none with a working GPU driver), and at engine teardown.

The shell remembers the toolkit's handler before any engine exists and puts it back while an engine is alive and for a while after (`X11ErrorGuard`): without pause during those two phases, every few milliseconds otherwise. This cannot make a reset impossible, only brief (well under a microsecond), which is why the teardown order below matters as well. On X11 the engine also keeps its video output until it is destroyed (`force-window`), so there is one teardown per playback instead of one per stop. Putting the handler back can only narrow the moment in which an error is fatal, so the order of teardown removes the errors from that moment: the controls window lives exactly as long as the video surface, and the surface goes away only after the engine has. The toolkit's routine "that window is gone" errors therefore arrive after the handler is back. Reusing one engine for the life of the application would avoid teardown resets but not the ones at start, and would give one platform its own surface lifecycle; a libmpv build that leaves the handler alone is the real fix and belongs with packaging (see RELEASE.md).

The player's controls are a translucent window above the video, which on X11 needs a compositing manager. Every current desktop has one; without it the controls window is opaque.

## 2026-10-12 — Shared sources are checked for platform APIs

Every shared module compiles only for the JVM today, so `commonMain` code can use JVM and desktop APIs without the compiler objecting; the first Android build would be where that surfaces. `verifySharedSources` runs with `check` and fails on platform-only imports and calls in shared source sets. It is a text check, not a compiler: it holds the boundary until a second target exists, at which point the compiler takes over and the task can go.

Since the Android client (2026-10-13) the compiler does take over for `ui`, which is built for both platforms. The engine modules are still compiled once, for the JVM, and used by Android as they are, so the check stays for them.

## 2026-10-12 — One playback engine at a time

A playback session's engine is released asynchronously, so that leaving the player is instant. Starting playback waits for the previous engine to be gone. Two engines would share one video surface, and the first one's teardown hides the surface the second is using; on Android the same rule keeps two players from competing for a decoder.

## 2026-10-12 — Controllers are taken as they are found

Found with real hardware: Windows lists non-controller HID devices as joysticks with no buttons and no axes, and their idle hat reading is indistinguishable from "up", which made Reflux navigate upward forever. A controller's motion sensors appear on Linux as a second joystick with axes only, which would have turned tilting into navigation.

- A device is a controller only if it has face buttons and a stick (Windows) or buttons Reflux can map (Linux).
- A hat is only read on devices that report having one.
- Whatever is held or deflected when a controller first appears is not a press; input counts from the first change.

## 2026-10-12 — CI and local Linux verification share their scripts

The Linux job's package list and its window-test script live in `tools/ci` and are used by the workflow and by a container image (`tools/ci/Dockerfile`) alike. The Linux defects of the desktop shell were all found by running the real window on Linux, which must stay possible from a Windows or macOS machine.

Tests that need the engine, FFmpeg, or a display skip themselves where those are missing, so that a developer machine without them still builds. Where the environment installs them, that leniency would let a broken installation pass as a green run with nothing tested, so the Linux job sets `REFLUX_REQUIRE_ENGINE_TESTS=true` and a missing engine, tool, or display fails the tests that need it.

## 2026-10-12 — The engine is never waited for on the UI thread

`MpvPlayer` sends everything to libmpv through its asynchronous command call. The `Player` contract always said that commands return immediately and show their effect in the state; the binding used the synchronous calls, which return when the engine has run the command. Normally that is a millisecond. When the engine's core is busy or stuck (found on Linux: a video output that never started) the caller waited with it, and the caller is the UI thread.

An engine that never gets anywhere reports nothing, so silence has a limit: a playback that is still loading after thirty seconds becomes a stated failure with the way back (and makes way if playback starts after all), and a closed engine that will not shut down is left behind after five seconds rather than blocking the next playback.

Related, and the cause of that stuck video output: a video surface is handed to the engine only after the toolkit has synchronized with the display server. The engine reaches the surface through its own connection, and the toolkit merely queues the request that creates the native window.

## 2026-10-13 — Playback progress follows the work

**Playback progress follows the media work, not the physical file or source.** A work has one watch state, owned by Reflux. Whichever version plays, progress is recorded for the work; whichever version plays next, it continues from there. A user who starts a film from a local file and continues it from a server does not choose a source and does not seek. The full contract is in [WATCH_STATE.md](WATCH_STATE.md).

The state already belonged to works. What this adds is what makes that safe to rely on across copies:

- **A position has a timeline.** The state records the running time and the cut of the copy the position was measured on, and which version it was. A position without that is only meaningful on the file it came from.
- **Timelines are compared, not assumed.** Another encode or source of the same cut is the same timeline. Another cut, or a running time that differs by more than 1% (at least 15 seconds), is not. A cut is named only by edition labels that mean a different edit; a label on the transfer or the packaging, and a server's free-text name for a file, are not cuts.
- **A position is never carried blindly onto a different timeline.** It is moved to the latest point that cannot be past what the user has seen (the same position in a longer copy, earlier by the difference in a shorter one), and the player says so. With nothing to measure the difference by, playback starts at the beginning and the remembered position is kept.

**Alternatives.** Resetting progress when the cut changes is safe and loses the user's place, which is the thing the invariant exists to keep. Carrying the timestamp over unchanged is what most players do and silently skips or repeats minutes between cuts. Scaling the position by the ratio of running times is right only when the difference is spread evenly, and errs in the direction of skipping. Matching content itself (audio or frame fingerprints) would be exact and is far beyond what this needs; the comparison sits behind one function so that it can be replaced.

**Consequences.** Running times matter: a copy that was never probed can only be `UNVERIFIED`, and is trusted rather than held against the user (unknown is not failure). Best Version prefers a copy on the timeline in progress over a better picture of another cut: below being playable at all, above everything else. The database schema gained its first migration; watch state is user state and is carried forward, not rebuilt.

## 2026-10-13 — Sources are reconciled with Reflux's watch state, not obeyed

Reflux's watch state is the canonical one. A source that keeps its own (a media server) is a witness of what the user did, translated by its adapter into a source-independent record. For each of a source's items Reflux remembers what the source last said and when the two last had nothing left to settle, and from that:

- A record that changed while Reflux's state did not is news and is adopted. The exception is a record that has become empty and undated: it cannot be told from a server that was reset, and Reflux's history is not erased on the strength of it.
- A record that did not change never overwrites anything, however its date compares.
- When both changed, the later one is where the user is; an undated record cannot show that it is later.
- What changed in Reflux since the two were last in step is sent to the source, retried while it cannot be delivered, and not repeated once it was accepted.
- A first comparison adopts what only the source knows and otherwise changes nothing on the source.

**Why not compare timestamps alone.** That was the earlier rule (adopt the source's state when its last-played date is newer). It depends on two clocks agreeing, it cannot see a change that carries no date (marking something unwatched), and it let a server's old "played" return after the user un-marked a work while the server was away.

A position is sent only to a source whose copy is on the timeline it was measured on; that a work was watched is sent whatever the cut. Reflux's own state is always written first and never waits for a source.

## 2026-10-13 — Local-first version selection

**When versions are effectively equivalent in quality and compatibility, the local one is preferred.** Local is a strong tie-breaker, not an override, and "highest resolution wins" is not a rule.

"Equivalent" is judged for the device in front of the user, in this order: whether it can play the version at all, how many compromises that takes (software decoding, tone mapping), and then resolution, dynamic range, and audio *only as far as this display and these outputs reach*. Versions that are the same by all of that are equivalent, and the closest one plays: on the device, then on the local network, then remote. A local 1080p H.264 file beats the same thing streamed. A local 4K HEVC 10-bit file loses to a 1080p H.264 stream on a device that decodes HEVC in software or not at all, and wins on one that decodes it in hardware on a 4K display.

Codec, encoder, and bit depth are not quality in themselves and are never ranked directly: H.264 and HEVC are codecs, x264 and x265 are encoders, 8-bit and 10-bit are bit depths, and each counts only through what the device does with it.

The whole order is: what can be played here (its source is reachable, the device can decode it); the cut the viewer is part-way through; the version the user pinned; quality as this device delivers it (compromises first, then resolution, dynamic range, audio); and the closest copy. Continuity sits above a pin because resuming continues what was being watched, and a pin is a standing preference for starting a work. A pin sits above "plays better" because that is Reflux's judgment and the pin is the user overruling it; it sits below "can play at all" because no preference makes a device decode what it cannot.

## 2026-10-13 — Server behavior is observed, not assumed

The synchronization rules lean on how a server treats its own records, and fixtures can only repeat what their author believed. `JellyfinLiveTest` runs the whole exchange against a real server when one is named in the environment, and `tools/ci/jellyfin-test-server.sh` starts a throwaway one in Docker, so that the beliefs can be checked whenever a server version changes. The automated checks never need a server.

Observed on Jellyfin Server 12.2.0: a stop reported outside any playback session sets the position; marking played clears the position; marking not played clears position, play count, and date. The last one is why an empty, undated record is never adopted: it is what un-marking leaves behind, and equally what a reset server shows.

Found on the way: reports to a server were sent concurrently, so a stop could be overtaken by the progress report before it and leave the server on the earlier position. They now go one at a time, in order.

## 2026-10-13 — Android: one more shell over the same engine

The Android phone and tablet client is the shared experience layer and the shared engine in an Android shell. Nothing was reimplemented for it, and the Media Core did not change shape.

**How the shared code is built for Android.** The engine modules stay what they were, Kotlin compiled to JVM bytecode, and the Android application uses those same artifacts. Only `ui` gained an Android variant, because Compose is a different library there. The alternatives were an Android target on every shared module (several times the build configuration, for code that contains no platform API by rule) or an Android-only copy of the UI (a second product). The Android variant and the `android` modules exist only where an Android SDK is installed, so building and testing the engine and the desktop still needs none.

**Playback: Media3, into a surface.** ExoPlayer decodes with MediaCodec, in hardware where the device has it, straight to a `SurfaceView` beneath Reflux's own controls; libmpv was not brought to Android. The engine is driven through the same `Player` contract and the same `PlaybackController` as libmpv, so resume, next episode, track defaults, and watch reporting are the same code. What the device can play is read from the device (its codec list and display), never assumed from its Android version. Subtitles are drawn by the shell from the engine's cues. Not there on Android, and therefore not claimed: chapters (the engine does not report them), playback in the background, and a tone-mapping guarantee for HDR on SDR panels. Picture-in-picture came with the next entry.

**Storage: folders the user grants.** Media is reached through the system folder picker (Storage Access Framework), with a read grant that is kept across restarts and given back when the source is removed. Such a folder is a tree of documents, not a path: `DocumentTreeSource` lists it by display name so that its paths identify like any other source's, and finds documents again by walking those names. A revoked grant or an ejected card is the source being *unavailable*, which keeps the library, and never an empty listing, which would read as "everything was deleted". The rule that one folder is one source holds on Android through document IDs. No storage permission is requested and no file path is assumed.

**Phone and tablet.** A phone is not a small desktop: navigation is at the bottom, the player's transport is under the thumbs, touch shows and hides controls instead of pausing. A tablet keeps the desktop arrangement at its size. These are layouts of the one experience layer, chosen by `AdaptiveLayout`; there is no Android screen in the Android shell.

**Plain HTTP is allowed.** Media servers on a home network are usually `http://`, and which ones to connect to is the user's decision, so the app does not refuse cleartext traffic wholesale. Tracked with the other release questions.

**What the first Android run found in shared code.**

- Android's regular expressions (ICU) reject an unescaped literal `}`, which the JDK accepts: parsing failed on the first scan. The patterns are fixed, and the engine now has tests that run on a device.
- A real Jellyfin server names an item it has no metadata for after its folder, year included ("Night Drive (2021)"), which kept the server's copy from being recognized as the same work as a local file. The adapter takes a trailing year that agrees with the server's off the title.
- The artwork cache assumed its directory could not disappear while Reflux runs. A device short of space clears directories behind a running app.

## 2026-10-13 — Android: leaving the app, and the system's controls

**Picture-in-picture is how video goes on when the app is left.** While something plays, leaving the app turns the activity into a floating window of the picture's shape that shows the picture and nothing else; Reflux's controls come back with the full window. An activity that is stopped (closed, covered, or the screen off) pauses.

**No playback in the background.** Keeping a film's sound going with nothing on screen would need a foreground service and a permanent notification, for something a video player rarely wants. It is not built and not claimed as a capability. Audio-only content would change that.

**One engine, one session.** The engine is published as the device's media session, which is the platform's standard way in: headset and Bluetooth buttons, and the buttons of the floating window, act on that engine directly, so there is exactly one playback state however it is controlled, and it is the one the watch state is recorded from. Moving to the next or previous work is not the engine's to decide (it holds one item), so those two controls are routed to the same semantic actions the on-screen buttons use.

**Chapters are not there on Android.** The shared playback model has them and the player shows them wherever an engine reports them; Media3 does not report a file's chapters, and nothing is faked.

## 2026-10-13 — One accent

The interface's own color is a warm amber, and it is the same on every page. Details pages used to take the color of their primary action from the poster; that made the controls compete with the artwork they sat on, and left Reflux with no color of its own. Artwork still colors the page behind the controls. The extraction of an accent from artwork stays in `design` for ambient use.

## 2026-10-13 — Credentials never reach a log

What a shell writes down about a problem passes through `Redaction` (in `core`): addresses with user names and passwords, token and password parameters, and authorization headers are replaced before the text reaches a file, the system log, or the screen. It was a desktop function; with a second shell writing logs it is shared.

## 2026-10-13 — Visual refinement: calm on the surface

A pass over the whole interface against one test: does this element earn the attention it takes from the artwork? Decided, for every platform at once since the experience layer is shared:

- The base is darker and neutral. The earlier base had a blue cast, which tinted every page before any artwork did.
- Amber means something each time it appears: the primary action, where the user is, how far they are, what is selected, and the rare thing worth pointing out. Progress used to be white, the active section a white pill, and technical badges white chips; all three competed with artwork for no reason.
- Hierarchy is typographic. Uppercase tracked labels are gone (the eyebrow over Home's title, menu and notice titles); a featured title stands alone, and "Continue watching" is said only when it is true.
- An active primary button is its own color lit up, not white.
- The player's timeline is the one amber line on the screen; volume is a neutral setting next to it.

Not changed: layouts, navigation structure, the material roles, and motion timings, which the earlier slices had already settled and tested; and the focus ring, which stays white so that it shows on the accent.
