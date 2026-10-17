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

## 2026-10-14 — Television: a form factor of the one experience, in the one Android app

**Not a separate application, and not a mode.** Android TV and Google TV run the same application as phones and tablets. The shell asks the system whether the device is a television and says so through `Platform.television`; `AdaptiveLayout` already had that form factor, and the experience layer lays its existing pages out for it. There is no TV screen in the Android shell and no leanback UI library: the alternative, a second front end, would have been a second product to keep in step. A phone with a controller still morphs as before; a television simply starts where that morph ends.

**The canvas is 960 by 540.** A television reports a density that already accounts for the distance (1080p and 4K are both 960 by 540), so the earlier assumption of a 1.5× type scale was wrong: it would have doubled the scaling. Type is one step up (1.1×), six titles fit across, and controls are sized to be read, since focus needs no finger-sized target.

**Something always has focus, and it is the right thing.** A television has no pointer, so a page takes focus when it appears: the item it was left from when coming back, its main action otherwise, and the same again when a library appears on an empty page or a panel closes. Offers of focus are tied to the page that makes them, because during a transition two pages exist at once and focus given to the one that is leaving is lost with it. Found on the way, and true on the desktop too: the toolkit's directional search does not find its way from the navigation bar down into a scrolling page, so a keyboard or controller that went up to the bar could not come back. Leaving the bar toward the page now goes to where focus was, or the main action.

**Rows, not pixels.** On a television Home places the row that has focus under the navigation, by row, instead of scrolling just far enough to show the focused card. What moves up under the bar fades out. Other pages keep the ordinary bring-into-view.

**The remote drives the player through the session, as controllers do.** Player lists were already session state rather than toolkit focus (2026-10-11), which is what a remote needs: no focusable buttons were added. Two things were missing for hands holding only a D-pad. First, a way into the lists without a menu button: where loudness is the device's own (`Platform.systemVolume`, true on Android), up and down are free, so down opens the lists and up accepts an on-screen offer to skip. On the desktop they remain the volume. Second, a way to the neighbouring episodes without shoulder buttons: an Episodes list, first among the lists, on every platform. Back during the next-episode countdown still leaves the player; OK is "play now".

**No prompts for a remote.** Its keys are labelled with what they do. Prompts stay for controllers, whose buttons are named by position.

## 2026-10-14 — Television: choosing a folder without a system picker

Google TV answers the system's "choose a folder" request with a stub: there is no document picker to hand over to (observed on the Google TV image for Android 16). First run must still be possible with a remote alone, so where a platform offers `Platform.folderBrowser`, the experience layer draws its own picker over it: storage, then one folder at a time, "Use this folder", Back to go up. It lists names and counts videos; it creates and changes nothing.

On Android that browser is `StorageFolders` over the mounted volumes, and it needs the system's permission to read video files (`READ_MEDIA_VIDEO`; `READ_EXTERNAL_STORAGE` before Android 13), asked when the user first goes to choose a folder. The permission is declared for the one application but only ever requested on a television; a phone or tablet keeps the system picker, which grants a single folder and needs no permission. The chosen folder is a source by path, the same `LocalFolderSource` the desktop uses, under the same rule that one folder is one source.

Considered and rejected: all-files access (far more than a player needs, and restricted by store policy), and asking the user to type a path (not a thing to do with a remote). A known cost of the narrower permission: on Android 11 and later the system shows an app only the media files in shared storage, so subtitle and artwork files lying next to a video are not visible to Reflux on a television. Embedded subtitles are unaffected.

## 2026-10-14 — Sound output is what the output says

Android claimed stereo and no passthrough everywhere. A television is usually wired to something that does more, and says so: the HDMI output's channel count becomes `maxOutputChannels`, and the compressed formats it accepts (AC-3, E-AC-3, DTS, DTS-HD, TrueHD) become `audioPassthrough`, which version selection and the playback assessment already understood. Nothing attached, or an output that reports nothing, is still stereo. As with video, nothing is inferred from the device being a television.

## 2026-10-14 — A download is a version of the work, in a source of Reflux's own

Keeping something for offline does not create a "downloaded movie". The copy is registered as one more version of the work its origin belongs to, located in `DownloadsSource`, and from then on nothing is special about it: Best Version ranks it, the work's watch state resumes on it, and reconciliation with a server happens as for any playback. The alternatives were a parallel list of downloaded things with its own playback path (a second library, and offline-only watch history sooner or later) or a folder Reflux scans like a user's (which would re-identify files by name and could split a work in two).

Copies are not identified. Each records the version it was made from and belongs wherever that version belongs, also after the origin is corrected or merged; scanning and re-identification end by bringing the copies in line. A copy describes the same file as its origin, so it is equal to it in everything a device can show and wins only as the closest copy, which is the existing local-first rule doing its job.

A pin on a version is honored by its copy. The pin says which version the user wants; the copy is that version, nearer.

## 2026-10-14 — What to keep is Best Version, in the room there is

Offline selection is not a second ranking. It is Best Version's order for this device with two differences that only make sense for something kept: among versions the device plays and shows equally, the smallest is taken (streaming prefers the larger), and when the first choice does not fit, the best version of the same cut that does is taken and the user is told. A pinned version is never substituted: it is downloaded, or the work is left out with the reason. A different cut is never a stand-in for the one being watched.

The device is never filled: the larger of 500 MB and 5% of the volume stays free, and what accepted downloads will still write counts as used. Fixed, not a setting.

## 2026-10-14 — Sources say whether their files can be kept

`DOWNLOAD` is a capability a source declares, with `DownloadableSource.downloadTarget` giving the file itself. The planner only considers versions on sources that declare it and are there to be asked. The transport that fetches is generic (HTTP with ranges, or a file), so no source-specific download code exists anywhere, and a source that cannot hand out a file whole (transcode only) simply does not declare the capability.

## 2026-10-14 — Android: copies in private storage, made while Reflux runs

Copies go to the application's internal storage: private by construction, no permission, gone with the application. Choosing removable storage is not offered; it would need the user to grant a location and a story for the card being pulled, for a first version nobody has asked for.

Copies are made while the application's process is alive, and continue from where they were at the next start. Continuing with the application closed would need a foreground service and a notification (and on current Android, the notification permission): the same step that was declined for background playback, and to be decided together with it. A long download on a phone that is put away is the case this leaves open, and it is named in the roadmap.

## 2026-10-14 — Servers and shares are added through connectors, not through adapters

A user adds a server or a share by typing an address, and the experience layer must set that up without knowing Jellyfin, WebDAV, or SMB. Each adapter that is reached over a network offers a `SourceConnector` (in `core/source`): what kind of thing it is in the user's terms (`ConnectionKind`: a server, a share), whether it needs a name and password, whether an address is of its form, how to connect (validate, contact, sign in), and how to describe a stored connection without its secrets. Every failure a user can cause is a `ConnectionProblem` (invalid address, unreachable, nothing of that kind there, sign-in required, wrong name or password, not allowed, a failing server), never an exception; the experience layer words it. `SourceConnections` (in `library`) routes an address to a connector, turns a connection into a source the library keeps and the registry runs, and handles signing in again and a changed address. A platform offers the connectors it has; one that is not offered is a kind of source that cannot be added there, and the interface does not mention it.

Shares are one choice for the user, not one per protocol: an address that starts with `https://` is a web share, `\\nas\Movies` or `nas/Movies` is a Windows or NAS share, and the connector that accepts the address is the one used. Nobody is asked whether their NAS speaks WebDAV or SMB.

**Alternatives.** A form per adapter in the experience layer would have been quicker and would have put adapter knowledge (fields, error codes, URL rules) in the UI, which is what the Media Core exists to prevent. A generic form described by each adapter (fields, labels, validation) would have moved text onto the screen from engines, which "Failures are states, worded in one place" rules out, for a flexibility no source needs yet: every source reached by address so far needs an address and optionally a name and password.

**Consequences.** The Jellyfin password is used for the one request that signs in, and only the server's access token is kept; WebDAV and SMB passwords are still kept with the source configuration, unencrypted, until platform secure storage (RELEASE.md). Diagnostics get a connector's own detail (an HTTP status, an SMB status name), never the request. Jellyfin credentials now also keep the user's name, to show who is signed in and to fill the name in when signing in again. Choosing which of a server's libraries to include came with the next milestone (below).

## 2026-10-14 — The media sources are a page, and adding media is a short flow

The sources were a small panel under the navigation. With servers and shares, each needs to say whether it is there, what is on it, and offer trying again, signing in again, and removing; a form for an address and a password does not belong in a panel that floats over a page, least of all on a phone with its keyboard up or on a television. The sources became a page (`Destination.Sources`), and adding media is two more (`AddSource`: a folder, a server, a network share; `Connect`: the form). They are pages like any other, so Back, the controller's Menu button, focus on a television, and the phone's way back all work as they do everywhere else, with no new focus or input model.

- A source is a quiet card: its name, what kind it is, and whether it is there, with how much is on it. Selecting it reveals where it is, who is signed in, when it was last updated, why it is not connected and what to do, and its actions. Nothing technical (types, capabilities, identifiers) is on the page.
- A source that cannot be reached stays on the list and in the library, and says so. "Try again" checks it now; it also reconnects by itself.
- A folder stays the fastest way in: first run still has one main action, "Choose a folder", and a quiet "Connect a server or share" beneath it.
- After a connection the flow is left, back to where it started, and the library fills in by itself.
- The same column is used on every form factor. A tablet gets no split view: there is never more than one thing to look at on these pages.

## 2026-10-14 — Amber is a signature, not the color of the interface

The earlier refinement made amber the primary action, the section the user is in, progress, and what is switched on. In use that was too much amber: a selected tab, a favorite, a watched mark, a downloaded copy, and a player's buttons all in the same warm color made screens orange-leaning and took the accent's meaning away. Decided:

- Amber is what is playing or about to: the Play and Resume button, progress on posters, on details pages, and on the player's timeline, the "Recommended" mark on a version, and the one call to action of first run.
- Everything else is neutral. The section the user is in is white on a soft pill. Switched-on toggles (watched, downloaded) are a lit surface; a favorite is a filled heart. The main action of a form, a sheet, or a player prompt is solid white (`ButtonStyle.SOLID`). Text never takes the accent, and the focus ring stays white.
- Empty states sit on a neutral light from above with only a trace of warmth in a corner.

## 2026-10-14 — A source can be made of parts the user chooses between

A media server is several libraries, and a household's server often has one the user does not want in Reflux (the children's, someone else's). A source that is made of parts says so (`SectionedSource`: its parts, and which are left out) and its connector can rewrite a stored configuration to leave parts out (`SourceConnector.withExcludedSections`). The experience layer offers "Libraries" for such a source, and nothing about Jellyfin reaches it. Jellyfin's parts are the account's libraries that can hold films or series; music, books, photos, playlists, and live television are not offered.

- **What is stored is what is left out**, by the server's id for the library. A library that appears later is included, which is the automatic default; a renamed library keeps its choice; one that is deleted simply stops mattering.
- **The connection stays.** Narrowing a source rescans it, and the scan, as always, makes the library match the source: what was left out leaves the library, its watch history stays with its works and comes back with them. Copies kept for offline stay, as when any origin goes away.
- **Leaving everything out is not a choice.** A source that is reachable and lists nothing, while the library knows media on it, is treated as storage that went away (2026-10-08): the library is kept and the source marked unavailable. Letting a user produce that state on purpose would look like a failure. The last included library cannot be unticked, and the page says that removing the source is how to include nothing.
- **Signing in again keeps the choice.**

## 2026-10-14 — SMB on Android, and connection secrets sealed there

SMB had been desktop-only. Nothing about it was desktop-specific: smbj runs on Android's runtime, and the loopback stream server uses plain sockets. It is now offered on Android through the same adapter, connector, and stream server; the Media Core and the domain model did not change, and no storage permission is involved (a share is a network connection, not a folder on the device).

Adding it would have put a share's password in the library database in the open, next to the web share passwords and server tokens already there. On Android, what a connection stores is now sealed with an AES-GCM key that lives in the Android keystore and never leaves it (`ConfigSeal` in `library`, `KeystoreSeal` in the shell), and whatever an earlier version stored in the open is sealed when the engine starts. Adapters only ever see the opened form, so no adapter changed. This is not the whole secure-storage work (RELEASE.md): the desktop still stores connections as they are, and the column holds the sealed secret rather than a reference to a keychain entry.

Found by running it on an Android device, and fixed in the shared adapter:

- **The stream server listened where nobody called.** Its URLs name `127.0.0.1`; it bound to the platform's "loopback address", which on Android is the IPv6 one. Every player was refused. It now binds to `127.0.0.1` by address.
- **One reader closed the share under another.** smbj hands every caller the same share object for a share name, and each use closed "its" share when it was done; a probe finishing closed the share the player was reading. The share now lives as long as the connection; only files are closed per use.
- **A failing stream ended the application.** smbj reports a broken share with an unchecked exception, and on Android an exception escaping a thread ends the process. Each stream connection now catches its own failure, and the player is told the source is unreachable.

## 2026-10-14 — The navigation says where the user is, or nothing

The sources pages belong to no section of the library, so no tab is lit while one of them is open; the media sources button is, on every form factor. The same button leaves them, back to where the user was before (`Navigator.highlighted`, `Destination.aboutSources`).

## 2026-10-14 — Desktop source connections live in the system's credential store

A desktop kept server tokens and share passwords in the library database as they were. They now go to the store the operating system keeps for the signed-in user, and the database keeps a reference (`keychain:v1:<source id>`), so a copy of the database or of a backup carries no secret.

- **One contract for every platform.** `ConfigSeal` (in `library`) became per source: it keeps a source's connection and returns what the database stores in its place, opens it again, and forgets it when the source is removed. Android's keystore seal and the desktop's `KeychainSeal` implement it; adapters and the Media Core see neither.
- **Windows: Credential Manager**, bound directly through JNA (already a dependency for libmpv); the four calls it needs did not justify the platform library. **Linux: the Secret Service** through `secret-tool`, which ships with libsecret on GNOME and KDE desktops, with the secret on its standard input and never in arguments. A D-Bus binding was the alternative: more code to own, for the same store. Where no Secret Service answers (a bare window manager, a container), connections stay in the database and the diagnostics log says so once at start, rather than refusing to connect anything.
- **Entries are named after the library as well as the source**, so a second Reflux library on the same account (a test run) never shares an entry with the real one.
- **Migration is at start and never destructive.** A connection stored in the open is moved into the store; if the store refuses, it is left working as it was and the problem logged. A new connection the store refuses is not kept at all, since writing it into the database in the open would be exactly what this is for.
- **The developer CLI** connected servers and shares by writing their configuration into the database itself, which would have bypassed all of this; it now uses the same connectors and the same seal as the application.
- **Logs.** An audit of everything source code writes down found two gaps in `Redaction`, both closed: a Jellyfin token quoted on its own (`Token="..."`, as an exception or a player may quote an authorization value), and the per-file tokens in the SMB stream server's loopback addresses, which let any local process read that file while Reflux runs.

## 2026-10-14 — Visual reset: a neutral-first cinematic interface; the amber identity is retired

Supersedes "One accent" (2026-10-13), the amber parts of "Visual refinement: calm on the surface" (2026-10-13), and "Amber is a signature, not the color of the interface" (2026-10-14).

Reflux uses a neutral-first cinematic interface with selective frosted glass. Artwork provides most of the visual color; the application chrome remains neutral. The old persistent amber identity is retired. Restraining amber to "what is playing" still left the interface with a hue of its own, and with every poster's colors competing with one fixed warm color. The principle now is: let the media be colorful, let the interface be calm.

- **No chrome token has a hue.** `Palette` is near-black, charcoal, graphite, greys, and white; a test holds every chrome token to near-zero saturation, so a color cannot creep back in unnoticed. Only `error` is red. The amber `accent` and `onAccent` tokens are gone.
- **Emphasis is brightness, not color.** The one main action of a screen is bright neutral (`ButtonStyle.PRIMARY`: near-white, near-black text), whether it plays, connects, or chooses a folder. The former amber `PRIMARY` and white `SOLID` styles were the same idea in two colors and are now one. Progress is white. The recommended version is a filled neutral badge on a lifted row.
- **Artwork may color the environment, never the controls.** `ArtworkAccent` became `ArtworkAtmosphere`: the dominant hue of the artwork as a deep, muted tone (primary text stays at 7:1 on it), or none for grey artwork. Home's hero and a details page wash the page behind them with it at about a fifth of its strength, and it recedes as that artwork scrolls away. Navigation, buttons, badges, and focus are the same on every page; Reflux does not become "purple Reflux" on a purple poster.
- **Focus is a white ring set slightly outside the control**, with a sliver of page between them, so it reads around a white button as well as around a poster; the controller and remote glow, lift, and scale stay. The controller morph is unchanged and uses the same language.
- **Glass is a layer above the media, not the identity.** The navigation bar is clear over a page that opens on artwork and frosts once the page moves under it; player bars, lists, panels, sheets, and notices are frosted; content is not.
- **Versions lead with the decision.** A version row says what it is chosen by (resolution, HDR, edition) first and how it is encoded last; the one Play will use is lifted out and says why; a pinned version says "Your choice". `VersionRow` carries `quality` and `technical` separately for this (UI layer only).
- **The player puts what is playing next to where it is.** The title and episode sit just above the timeline (at the top beside Back on a phone). No playback function changed.
- **The marks follow:** the desktop window icon, the Android launcher icon, and the TV banner are a white play mark on a graphite tile.

Not changed: layouts and navigation structure, the material roles, motion timings, the controller morph, and every playback, source, and library behavior. The change is confined to `design`, the experience layer (`ui`), and the shells' icons.

## 2026-10-15 — Real-world validation of the visual reset: the baseline holds, with targeted corrections

The 2026-10-14 reset was checked with real film artwork (Blender open movies and public-domain posters: dark, bright, saturated, high-contrast, text-heavy, letterboxed) on desktop real windows (Windows, and Linux under the CI container), the phone emulator in both orientations, a tablet-sized display, and the Google TV emulator driven by remote keys. The direction holds: chrome stays neutral, the atmosphere stays a wash, glass stays on what floats, and no accent came back. What it exposed was fixed where it stood:

- **Subtitles move above the player's controls while they show.** They were drawn under the frosted bar and read through it. `Player.setSubtitleClearance` (a default no-op, so an engine that cannot move its subtitles simply does not) carries the share of the window the controls cover: libmpv applies it as `sub-pos`, and the Android shell lifts its own subtitle text by it. The overlay reports it, and 0 when the controls leave.
- **A work with a poster but no backdrop takes its color from the poster**, for the page and for the backdrop generated behind it (graphite for a grey poster). It took its title's hue before, which set a yellow poster on a purple page. `ArtworkAtmosphere.dominantHue` is the hue both are made from.
- **Artwork scrims overlap the page by a pixel**, because at fractional densities (2.625x) the scrim's antialiased bottom row let a sliver of the artwork show; and a band of fixed height keeps the navigation legible over bright artwork at the top of Home and details (its icons measured 1.3:1 over a bright sky, now 3.5:1).
- **A details page opens on its title and Play on a short screen** (a phone on its side). Below 600 dp of height the header leaves out the poster, the genres, and the credit, and keeps the overview to two lines.
- **Lists that clip leave room for the focus ring** (`FocusRingReach`), which now sits outside the control and was cut off at a list's first and last rows.

Not changed: palette, type, materials, motion, layouts elsewhere, and every source and library behavior.

## 2026-10-15 — One artwork fallback rule for every frame

A work may have a poster, a wide image (a backdrop, or an episode's still), both, or neither, and a platform may be unable to read some of it. Which image pictures it is now one rule (`ArtworkChoice`), the same on every form factor: the image made for the frame's shape; failing that, or failing to load, the work's other real image cropped to fit; and a generated title treatment only when no real artwork is left.

- **A poster card falls back to the backdrop**, cropped to its middle, with the title beneath it like any poster. A backdrop-only work showed a generated card before.
- **A poster fills a wide frame no wider than 16:9**: a wide card (as before), the header of a phone held upright, a hero that is nearly poster-shaped. Wider than that, a crop of a poster is a band of its lettering (seen with real posters: credits, taglines, a title cut in half), so a wide hero or a phone on its side keeps the header generated in the poster's color.
- **A details header with room for a poster beside the title** shows the poster there, and the backdrop or a header generated in the poster's color behind it. A backdrop-only work shows no generated card beside its title over its real backdrop. A work with no artwork at all keeps the designed placeholder in its title's color; that is the only case that uses the title's hue.
- **An image that cannot be read now** (offline and not cached, or out of the platform's reach) gives way to the next choice instead of leaving a placeholder; and a wide card names the work over its image only when an image was actually drawn (with neither, its placeholder and the overlay both named it).

Television, local artwork: on Android 13 and later a folder chosen by its path is read through `READ_MEDIA_VIDEO`, and the system then lists only the videos in it; `poster.jpg`, `fanart.jpg`, subtitles, and `.nfo` files are not there to find, so no artwork is recorded for them (a scratch build granted `READ_MEDIA_IMAGES` found it). No less-privileged path reaches those files: Google TV has no folder picker for the Storage Access Framework, `MediaStore` hands out images only with image access, partial "selected photos" access asks the user to pick individual images, and all-files access was rejected before. `READ_MEDIA_IMAGES` would make every image in shared storage readable (photos and screenshots too, on every volume), for Reflux to read the sidecar images in the chosen folders; it changes the system's question to "photos and videos", and Google Play's photo and video permissions policy reviews it. Whether to ask for it is decided in the next entry: not in v1. Without it, a television's local works use what they can have: provider artwork when identified and online, the server's own for network sources (unaffected), and the generated treatment otherwise, through the same fallback rule. Options that read no further than the video itself, for later: cover art embedded in the file (Matroska attachments, MP4 `covr`), or a frame of the video from the system's video thumbnails.

## 2026-10-15 — Android TV does not request `READ_MEDIA_IMAGES` in v1

Decided by the product owner. Reflux on a television asks only for `READ_MEDIA_VIDEO` (`READ_EXTERNAL_STORAGE` up to Android 12), and never for `READ_MEDIA_IMAGES`.

Why it was rejected:

- **It is far broader than the need.** It makes every image in shared storage readable (photos, screenshots, downloads, on every volume), while Reflux needs only the artwork that sits beside the media in folders the user chose.
- **Google Play treats it as restricted broad access.** An app must declare that the system's picker alternatives are insufficient for its core functionality; artwork beside a video is not core to a player, and the declaration would not be an honest fit.
- **Reflux on a television is fully usable without it.** Network sources (Jellyfin, SMB, WebDAV) keep their own artwork, identified local works use provider artwork, and everything else gets the designed generated treatment.

What a television shows without it: the shared fallback rule (`ArtworkChoice`, previous entry) over whatever artwork the work has. On Android 13 and later that is never a `poster.jpg` or `fanart.jpg` beside a video in local storage, since the system lists only the videos there. So a local work shows provider artwork when it is identified and was fetched online (cached for offline after that), or the generated title treatment. Android 12 and earlier read the sidecar images under `READ_EXTERNAL_STORAGE` as before.

Possible future direction, not committed to: local artwork that reads no further than the video itself, needing no new permission. That could be cover art embedded in the file (Matroska attachments, MP4 `covr`) or a frame of the video. Either would be its own decision when it is taken up.

## 2026-10-16 — A source that goes silent during playback: thirty seconds of waiting, then a stated failure

Built and tested (implementation notes at the end of this entry). A source can stop sending data without closing its connection: a machine that lost power or its network, a server that hung, a link that dropped somewhere between. Nothing then reports a failure; the engine waits. Observed against a real Jellyfin server (ROADMAP.md, the live verification): the desktop player buffered for as long as it was left, while Media3 on Android reported the source unreachable through its own read timeout about two seconds after its buffer ran out. This entry decides what the viewer gets, the same on every platform. Ending normally, and a stream that fails or ends early, already behave as they should and are not changed.

The rule extends the one for a playback that never starts (2026-10-12, "The engine is never waited for on the UI thread"): silence has a limit, and the limit is the viewer's patience, not the network's.

- **Silence the viewer cannot see is nothing.** While the engine plays from what it has buffered, nothing is shown and nothing is decided. Most stalls on a home network end inside the buffer.
- **Waiting shows as waiting.** When playback wants to run and has no data (the `Player` contract's `BUFFERING`, which includes waiting for a seek to land), the player shows that it is waiting, as it does today. A paused player is not waiting, and time paused does not count.
- **After thirty seconds of waiting, the wait is a stated failure.** The player says that the source has stopped sending and offers Try again and the way back. Thirty seconds is the patience Reflux already gives a playback that does not start. It is longer than the ordinary causes of a long wait (a disk spinning up, a NAS waking from sleep, a seek far into a large file over a slow link), and short enough that a viewer is not left watching a spinner with no way to know whether anything is happening. It is not a setting.
- **If the data comes back, the message makes way.** While the message shows, the player stays where it was. If playback starts again by itself, the message goes and playback continues, as with a playback that starts late.
- **Try again is the viewer's.** It plans the playback afresh from the position kept, as pressing Play would: the source is checked, Best Version chooses again (another available copy may now be the one), and the handoff applies. Reflux runs no retry loop of its own and no backoff: an automatic retry is activity the viewer cannot see or stop, and the two cases it would serve are already covered, a source that comes back by itself (the message makes way) and one that does not (Try again, or another copy).
- **The playback failed; the source is checked, not assumed.** A stated failure asks for the source to be checked, as an unreachable file already does. The source's card and the library show it offline only if that check cannot reach it. A server that answers the check but stalls a stream stays online, which is the truth.
- **An engine that knows sooner may say so sooner.** Where an engine concludes by itself that the source cannot be reached (Media3's read timeout and retries do), that failure is shown when it comes, in the same words and with the same actions. Reflux does not hold back a real failure to match the slower engine. The thirty seconds are the limit, not a delay.
- **Watch state does not change.** A stall is never an ending: the position kept is the one the engine reports when playback is left, as for every other stop, and the 90% rule applies to it as everywhere (WATCH_STATE.md). Nothing about a stall marks a work watched, counts a play, or clears a position.

The same on every platform: the waiting, the thirty seconds, the message, Try again, the way back, and what is kept. Underneath, the engines may differ: how long each waits for a byte, whether it reconnects by itself, and how soon it reports a failure. Because the limit is measured on the `Player` contract's states, it lives in the shared playback session (beside the startup limit) and needs nothing from either engine.

Alternatives considered:

- **Leave it to the engines.** Rejected: one engine never gives up, the other gives up on its own terms, and the viewer would get a different product on each.
- **An engine timeout as the rule** (mpv's or Media3's network timeouts). Rejected as the rule: they measure the network, not the wait the viewer sees, they behave differently per engine and per stage of a stream, and an engine reconnecting in the background would hide the wait from them. An engine timeout may still be set where implementing this shows it is needed (for instance so that Try again is not held up by a hung connection), as an engine detail.
- **A shorter limit** (ten to fifteen seconds). Rejected: a disk or a NAS waking, or a long seek over a slow link, can take that long, and the spinner already says what is happening.
- **Automatic retries with backoff.** Rejected for v1, for the reasons under Try again. It can be revisited if real use shows stalls that recover only on a new connection and are common.
- **Marking the source offline when a stream stalls.** Rejected: a stall is a fact about one playback. Whether a source is there is decided by checking it.

What implementing it must show, deterministically (the shared session with a fake player, as its existing tests do, plus each engine where its behavior is the point):

- a stall shorter than the limit shows waiting and nothing else, and playback continues by itself;
- waiting for the limit becomes the stated failure, with Try again and the way back, exactly once;
- time paused, and time playing from the buffer, do not count toward the limit; a stall that ends restarts the count;
- data returning while the failure shows clears it and playback continues;
- Try again plans afresh from the position kept, and may choose another available copy;
- the stated failure asks for the source to be checked, and the source is shown offline only when the check fails;
- an engine's own earlier failure is shown at once, with the same message and actions;
- the position kept is the position at leaving, the 90% rule applies unchanged, and a stall never completes a work or counts a play;
- on the desktop, a silent connection held open (as the live run did, with a forwarder) reaches the stated failure, and Try again and leaving are not blocked by the hung engine.

How it is built:

- **One limit for every wait.** `PlaybackSession` counts the viewer's wait on the `Player` states, with the session's coroutine clock (a test scheduler's in tests): `LOADING` and `BUFFERING` are waiting; anything else ends the wait and the next one starts from nothing. The limit for a playback that never starts (2026-10-12) is the same thirty seconds and the same timer, so a wait that runs from loading into buffering is one wait. It is stated once per wait: "isn't starting" when this load never played, "stopped sending" when it had. Both offer Try again and ask for the source to be checked (`AppState.checkAvailability`: whether it answers, and nothing more); data returning (playing or paused) clears either. The check does not rescan the source: against a real Jellyfin server a rescan here delivered Reflux's newer position as a stop (newer because a progress report had not reached the server), which ended the playback's session on the server while the player still had it open, and the next report opened another, counted there as a second play. A scan that brings a position up to date during a playback no longer ends its session wherever the scan comes from (WATCH_STATE.md, "Sources that keep their own watch state").
- **An engine's own failure** that the source is unreachable, once playback had begun, is shown at once in the same words with Try again (Media3's read timeout; mpv's stream ending short). Unreachable at opening keeps its own words ("couldn't be opened") and also offers Try again. Other failures offer only the way back.
- **Try again stays in the player.** It plans the work again with `planPlayback` in the same session and engine, as moving to the next episode does: no second engine, no trip through the library, no change of fullscreen. Select or Play takes it; Back leaves.
- **What it records.** Try again records the position reached as progress, never as a stop, and plans as *continuing*: a position past 90% is where the viewer is, not a finished work to start over (`WatchRules.resumePosition`). A playback that never got going records nothing when it is stopped, so a failed start does not overwrite where the viewer was (WATCH_STATE.md). Positions under a minute are not resumed, as everywhere.
- **The hung engine.** Investigated with a real libmpv and a loopback server that holds its connection open and sends nothing (`SilentSourceTest`): mpv's own abort interrupts the blocked read, so `loadfile` (Try again) opens a new connection at once and the engine shuts down promptly when the player is left. No engine timeout was needed. Commands to the engine never wait (2026-10-12), and an engine that would not shut down is still left behind after five seconds.
- **Media3** is unchanged: its read timeout and retries stay as they are, and its failure arrives through the `Player` contract as the source being unreachable.
