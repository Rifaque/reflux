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

> Extended by "Fire TV is a V1 target, separate from Android TV" (2026-10-17).

Initial target scope is Android phone/tablet, Android TV / Google TV, Windows, and Linux. iOS/iPadOS is not a v1 commitment.

## 2026-10-08 — Visual direction

> Refined by "Visual reset: a neutral-first cinematic interface" (2026-10-14): glass is selective, for what floats, not the identity.

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

`LocalFolderSource` (desktop JVM) walks a folder read-only, follows symbolic links with loop protection, skips hidden and system folders, and tolerates unreadable entries (a folder it cannot list keeps what was known below it; see 2026-10-17). Android will need a separate Storage Access Framework adapter because user-chosen folders are `content:` trees there, not paths.

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

> Amended for televisions by "Leaving on a television pauses; picture-in-picture is for handhelds" and "Out of sight, the player gives back its decoder and audio focus" (2026-10-18).

**Picture-in-picture is how video goes on when the app is left.** While something plays, leaving the app turns the activity into a floating window of the picture's shape that shows the picture and nothing else; Reflux's controls come back with the full window. An activity that is stopped (closed, covered, or the screen off) pauses.

**No playback in the background.** Keeping a film's sound going with nothing on screen would need a foreground service and a permanent notification, for something a video player rarely wants. It is not built and not claimed as a capability. Audio-only content would change that.

**One engine, one session.** The engine is published as the device's media session, which is the platform's standard way in: headset and Bluetooth buttons, and the buttons of the floating window, act on that engine directly, so there is exactly one playback state however it is controlled, and it is the one the watch state is recorded from. Moving to the next or previous work is not the engine's to decide (it holds one item), so those two controls are routed to the same semantic actions the on-screen buttons use.

**Chapters are not there on Android.** The shared playback model has them and the player shows them wherever an engine reports them; Media3 does not report a file's chapters, and nothing is faked.

## 2026-10-13 — One accent

> **Superseded** by "Visual reset: a neutral-first cinematic interface; the amber identity is retired" (2026-10-14).

The interface's own color is a warm amber, and it is the same on every page. Details pages used to take the color of their primary action from the poster; that made the controls compete with the artwork they sat on, and left Reflux with no color of its own. Artwork still colors the page behind the controls. The extraction of an accent from artwork stays in `design` for ambient use.

## 2026-10-13 — Credentials never reach a log

What a shell writes down about a problem passes through `Redaction` (in `core`): addresses with user names and passwords, token and password parameters, and authorization headers are replaced before the text reaches a file, the system log, or the screen. It was a desktop function; with a second shell writing logs it is shared.

## 2026-10-13 — Visual refinement: calm on the surface

> **Superseded in its use of amber** by "Visual reset" (2026-10-14); the neutral base and the typographic hierarchy stand.

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

> **Superseded** by "Visual reset: a neutral-first cinematic interface; the amber identity is retired" (2026-10-14).

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

Built and tested (implementation notes at the end of this entry). A source can stop sending data without closing its connection: a machine that lost power or its network, a server that hung, a link that dropped somewhere between. Nothing then reports a failure; the engine waits. Observed against a real Jellyfin server (VERIFICATION.md, the live verification): the desktop player buffered for as long as it was left, while Media3 on Android reported the source unreachable through its own read timeout about two seconds after its buffer ran out. This entry decides what the viewer gets, the same on every platform. Ending normally, and a stream that fails or ends early, already behave as they should and are not changed.

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

## 2026-10-17 — Positions told to a source stay short of where it counts a work watched

Found by the live validation against a real Jellyfin server (VERIFICATION.md). The server applies its own rule to every position it hears: a progress report or a stop past its maximum resume percentage (90% unless an administrator changed it) marks the item played and clears its position. Reflux reported its positions as they were, so a viewer who played past 90% and went back made the server say "played" before any stop. Leaving at thirty minutes, Reflux's record was right (not watched, thirty minutes); the next scan found the server's record changed and dated after Reflux's own stop, took it for news, and marked the work watched in Reflux.

Decided:

- **Watched is said once, when a playback finishes.** Reflux's rule for watched is its own (90% at the stop) and is unchanged. A position is never what makes a source count a work watched: a source declares where it would (`WatchStateSyncSource.marksPlayedPast`), and every position Reflux tells it (progress, a stop, a scan's update) stays 5% of the running time short of that (`WatchRules.reportedPosition`). The margin covers the server measuring against its own running time of the work, which can differ from the copy's; the live run had two copies of one film five minutes apart in two hours.
- **A finished playback is reported as having reached the end** of the source's copy. The source then marks it played by its own rule, as it does for its own clients, and records the one play its session already counted. Marking it played separately would count a second.
- **Reflux's own position is exact.** Only what the source is told is held short. The reconciler compares and sends Reflux's position as the source is told it, so the source's record of it agrees with Reflux and is never adopted over it, during a playback past 90% as after it.
- **A "played" on the source stays news.** Once Reflux no longer causes one, a played record that changed is another client, or the server itself, and is reconciled by the same rules as before.

Alternatives considered:

- **Ignore a server's "played"**, or only when it seems to echo Reflux's reports. Rejected: watching on the server's own clients is real history, and guessing where a record came from is not deterministic.
- **Read the server's configured threshold.** Rejected for now: the setting is the administrator's and not readable by an ordinary account. A server set below 90% can still count a work watched early (RELEASE.md).
- **Stop reporting progress near the end.** Rejected: other clients should still see the work nearly finished, and the session must stay alive.

Verified live on 2026-10-17 against the same server: playing to 95%, back to thirty minutes and leaving left the server unplayed at thirty minutes (it held 85% while the playback was near the end) and Reflux unwatched at the same position after a rescan, in one session and one play; a playback that ran to the end of the file was watched in Reflux and played on the server, with one play counted for it.

## 2026-10-17 — Identity keys keep every script

Found by stress-testing identification. A title's comparison key (`TitleText.key`, from which identity keys and work IDs are made) kept only ASCII letters and digits, after folding common Latin accents. A title in another script had an empty key, so works that had nothing in common became one: four films of 1953 named in Japanese, Russian, Hebrew, and Greek were one work with four versions and one watch history, and two shows named in Cyrillic and Chinese shared their episodes. The same was true of files named with no words at all (`-.mkv`, `().mkv`).

Decided:

- A key keeps the letters and digits of every script. Case and the combining accents of decomposed names (macOS writes `Amélie`) are folded, as precomposed Latin accents already were; marks that are part of a script's letters are kept.
- A title with no letters or digits keys as written, and such a file is titled by its own name, so it is a work of its own rather than one shared by every such file.

Works whose key changes get a new identity on the next scan; a work's history moves with its only file, as for any re-identification. A file with an episode number and no show name anywhere in its path still has an unnamed show, which such files share: nothing in the path tells them apart. (Since 2026-10-18 such files are a show of their folder: "Identify".)

## 2026-10-17 — A folder a scan could not list is not a folder emptied

Found by stress-testing source transitions. Every file-listing adapter (local folder, SMB, WebDAV, Android's document tree) treated only an unreadable root as the source being away; a folder below it that could not be listed was skipped. The library then saw its files missing and removed their versions, so a share whose connection dropped partway through a scan, a symbolic link to a drive that was not mounted, or a USB drive removed mid-scan took everything below that point out of the library until a later scan could read it again. Watch history survived (it belongs to works), but the library's picture of the source did not, against the rule that a source that is away is never emptied.

Decided: an adapter reports a folder that is there but could not be listed (`SourceFile.folderNotRead`), and a scan keeps what it knew below such a folder, with its subtitles and artwork, while settling everything else as usual. A folder that was deleted is no longer in its parent's listing and its works are removed as before; one that vanished between being listed and being read (the local adapter can tell) counts as deleted. A link that cannot be followed is reported the same way, so a permanently broken link keeps what was found through it until it is removed.

## 2026-10-17 — Roadmap rebaseline

Decided by the product owner after a read-only audit of the repository at `2a66250`. The Phase 0–18 roadmap was ordered by architectural layer, mixed status, plan, and verification logs, and no longer matched the code: most of its phases were built, while what stands between the tree and a release (signing, packaging, physical validation, legal, a beta) had no place in it. It is replaced by outcome milestones M0–M7 with a parallel website track W0–W4 (ROADMAP.md). Verification history moved to VERIFICATION.md; release and beta checklists are in RELEASE.md; launch tracks in LAUNCH.md.

- **Beta is a real launch stage** before 1.0: `0.9.0-beta.N` through Google Play closed testing, Amazon's testing path, and desktop downloads, with about 15–20 testers chosen for coverage, then release candidates `0.9.0-rc.N`, then `1.0.0`.
- **The website (reflux.rsxdevelops.dev) is a launch track**, begun before the beta, beta-facing at M4, and Reflux's permanent home at 1.0. Its visual language is minimal and nearly blank; the product's media provides the color (LAUNCH.md).
- **A product demo video is a beta asset**, required for the beta website.
- **V1 is what Reflux fundamentally is**, listed in ROADMAP.md (M1) with acceptance criteria; it is neither shrunk to make future features nor grown into everything Reflux may become.

## 2026-10-17 — Versioning protocol

One product version for every platform, declared once (`reflux.version` in `gradle.properties`), SemVer with `alpha`, `beta`, and `rc` stages on a single `0.9.0` line before 1.0, and Android `versionCode` and desktop package versions computed from it (VERSIONING.md). Alternatives: per-platform versions (rejected: one product, one changelog, testers comparing builds across devices) and date-based versions (rejected: stores need monotonically increasing codes anyway, and SemVer says what a release changes). The 1.0 build is the final release candidate's commit with only the version changed, as the product plan names its release candidates `0.9.0-rc.N`. The tree's version became `0.9.0-alpha.1` (`versionCode` 90001); the Jellyfin client version is read from the same property through a generated `RefluxBuild.VERSION`.

## 2026-10-17 — Fire TV is a V1 target, separate from Android TV

Fire TV devices running **Fire OS**, distributed through the **Amazon Appstore**, are a V1 platform. They run the same Android application as phones and Google TV, but are a separate target: a different store and review, devices without Google services, different launcher integration, a different device matrix (Fire OS 7 is API 28, Fire OS 8 is API 30; Reflux's `minSdk` is 26, so Fire OS 6 devices are excluded), and their own validation. Documentation never equates Fire TV with Android TV. The developer's Fire TV Stick 4K and Fire TV television are inspected for model and OS before anything is assumed about them (LAUNCH.md, Fire TV plan).

**Vega OS** (Amazon's newer non-Android Fire TV platform, on some recent Fire TV sticks) is not part of V1. Reflux's Android application does not run on it, and a Vega client would be a new shell. It is a future compatibility track, promoted only by an evaluation and an explicit decision.

## 2026-10-17 — Watch Next is a V1 feature

> Carried out by "Watch Next is derived from watch state, never stored" and "Fire TV ships without a home-screen 'continue' row" (2026-10-18).

Supersedes the audit's recommendation that the launcher's Watch Next row wait for V1.x. Reflux's Continue Watching and Next Up appear on the platform's own home screen, because on a television that is where people continue:

- **Android TV / Google TV**: the launcher's Watch Next row (`WatchNextPrograms` through AndroidX TV Provider): the work in progress with its position, and a show's next episode, added, updated, and removed as Reflux's watch state changes; selecting one opens Reflux and plays it. Required for V1.
- **Fire TV**: Amazon's home screen does not take Android TV's Watch Next channel; it has its own integration for continuing to watch. Whether it is open to an app of Reflux's kind (local and personal media, not a catalog partner) is evaluated against Amazon's current documentation as part of this work. If it is, it is V1; if it is not, Fire TV ships without a home-screen row, and that is recorded here rather than worked around.
- Phones, tablets, and desktops keep Continue Watching inside the application; there is no system surface to fill.
- Nothing about a work leaves the device through this beyond what the platform's launcher shows locally.

## 2026-10-17 — "From the start" keeps the place until the new playback is under way

The defect: pressing "From the start" on a work in progress, then leaving within a minute (to check the opening, or by mistake), erased the position. The first progress report of the new playback is below the minimum resume position and was recorded as 0, so Continue Watching lost the work; on a Jellyfin server the stop at those seconds reached the server as well, and would have come back as news at the next scan.

Decided: starting over is a new playback of the same work, and the remembered position stays until that playback is past the first minute (`WatchRules.MIN_RESUME_MS`), exactly as the first minute of another cut already did. Leaving within it keeps where the viewer was, on Reflux and on a source that keeps watch state (it is told the kept position when the playback stops). Past the first minute, the new position is where the viewer is. A completed work stays completed when watched again. Alternatives rejected: clearing the position as soon as "From the start" is pressed (an accidental press costs the place), and keeping the old position until the new one passes it (someone starting over means to start over; Continue Watching would resume them at a point they chose to leave).

## 2026-10-17 — Version pinning is reached from the versions it applies to

The engine has had a pinned version per work (Best Version's "the version the user pinned") with no way to set it. Decided: each version row offers "Always play this" beside it (beneath it on a phone), and the pinned row offers "Let Reflux choose" in its place; the row itself still plays that version once, as before. A pinned version that something outranks this time (the cut being resumed, a source that is away) still says "Your choice". Pinning a copy kept on the device pins the version it was made from, so the choice outlives the copy. Built for films, whose details page lists versions; episodes followed ("An episode's versions", 2026-10-18). A pin never makes Reflux play something this device cannot play (WATCH_STATE.md), so the action is offered only on versions that play here. One version is pinned at most, per work; pinning another moves it.

## 2026-10-17 — Television sidecar subtitles in V1

On Android 13 and later a television reads local folders through `READ_MEDIA_VIDEO`, and the system lists only video files there: a subtitle file beside a video (`.srt`, `.ass`) is invisible, as sidecar artwork is. No less-privileged permission covers subtitle files (they are not media to the system; even `READ_MEDIA_IMAGES` would not show them), and all-files access was rejected earlier. Decided for V1: no new permission. On such a television, local works play their embedded subtitle tracks, and network sources (Jellyfin, SMB, WebDAV) keep their external subtitles, which are unaffected. Where a local work on such a device has no subtitle track, the subtitle list says why ("Subtitle files beside videos can't be read on this TV") instead of looking empty. Android 12 and earlier, including Fire OS 7 and 8 devices, read sidecar files as before. Later possibility, not committed: a folder granted through the Storage Access Framework on televisions that offer a picker for it.

## 2026-10-17 — Smart Offline on the desktop is in V1

The engine and the JVM implementations (`FileDownloadStore`, `HttpDownloadTransport`) already run on the desktop; only the wiring and the data directory are missing. A laptop is the most common reason to keep something offline on a computer, and a feature that exists on one platform and silently not on another is not the same experience. Decided: V1 wires Smart Offline on Windows and Linux with the same sheet and rules as Android, copies in Reflux's data directory, made while Reflux runs. Alternative: Android only in V1 (rejected for the reasons above; the cost is small).

Also V1 on every platform: choosing a single episode or a whole season to keep, and a Downloads entry point that lists every copy with its state and actions (OFFLINE.md). Not V1: downloads that continue with the application closed.

## 2026-10-17 — Collections are V1.x

The engine has automatic (franchise), manual, and smart collections, and no interface shows any of them. Decided: 1.0 has no collections interface; collections, their pages, and editing are V1.x. 1.0 is complete without them (Home, Movies, Shows, search, and details organize the library), and a half-built collection editor would not meet the default-experience bar. The engine and its tests stay.

## 2026-10-17 — Episodes without a show name are grouped by folder and offered to Identify

> Built by "Identify" (2026-10-18).

Episodes whose path has no show name anywhere (`S01E01.mkv` at the top of a source) are grouped today under one unnamed show, so unrelated files from different folders and sources collide. Decided for V1: such episodes are grouped by the folder they are in (the source itself when they are at its top), the show is titled after that folder (or the source's name), its identification is weak by definition, so it appears among weak identifications, and Identify can assign it to the right show; that correction is kept as an override like any other. They are never merged with another group by an empty name. Not built yet (ROADMAP.md, M1).

## 2026-10-17 — Casting is a V1 feasibility gate

Casting is investigated for V1 and must not move the beta. A time-boxed spike, before beta entry, answers: can an Android phone cast a local file, a share's file, and a Jellyfin stream to a Google TV or Chromecast with Reflux's watch state recorded, without a Reflux account, with acceptable dependencies (the Cast SDK needs Google Play services, so it cannot be on Fire TV or desktop, and local files need a stream server on the local network), and without licensing or store-review problems? If the answer is yes and the work fits before beta, casting is V1 for Android senders; otherwise it is V1.1, recorded here. DLNA rendering is considered in the same spike only as an alternative, not as a second feature.

## 2026-10-17 — Removing a Jellyfin server ends its session

> Built by "How a Jellyfin server is signed out of" (2026-10-19).

Removing a Jellyfin source today forgets its token locally but leaves the session open on the server, which keeps listing the device and accepting the token. Decided for V1: removal asks the server to end the session (best-effort, when it is reachable) and forgets the token either way; if the server could not be reached, the removal still completes and the server's own device list may still show Reflux until the server expires it. Signing in again to a server ends the session it replaces in the same way.

## 2026-10-19 — How a Jellyfin server is signed out of

Implements "Removing a Jellyfin server ends its session" (2026-10-17).

- **The operation:** `POST /Sessions/Logout`, no body, sent to the server's own address with the session's access token in the usual `Authorization: MediaBrowser ... Token="..."` header. The server looks up the one device record holding that token and deletes it, so that token stops working. It needs no administrator rights and touches none of the account's other tokens, so other Reflux installations and the user's other clients keep their sessions (read in the server's source, `SessionController.ReportSessionEnded` and `SessionManager.Logout`, and verified live, VERIFICATION.md). Endpoints that revoke a user's tokens or delete devices by id were not used: they act on more than this session, and the device ones need an administrator.
- **Local first, then the server.** Removing a source removes it from the library and the registry and forgets its connection (the credential store's entry, or the encrypted value) before the server is asked anything; the connection is opened just before, so the sign-out has the token. The removal never waits for the server: the sign-out runs on its own, once, and is given up after 5 seconds (`SourceConnections.SIGN_OUT_WITHIN_MS`). No retry, and nothing kept to retry with.
- **What counts as ended:** the server's empty success (204), or a 401 (the token is no longer accepted: it expired, an administrator revoked it, or a newer sign-in from this device replaced it). Anything else (unreachable, a timeout, a server error, a page that is not Jellyfin's) is unconfirmed, reported to the diagnostics log without the token, and not presented as ended. The server may then keep listing Reflux until it expires the session; the token is gone from Reflux either way.
- **Signing in again** ends the session it replaces in the same way, unless the server handed out the same token again (then nothing is ended). Current servers already revoke the previous token of the same user on the same device when it signs in again, so this matters for a changed account or address, and for servers that do not.
- **Where it lives:** `SourceConnector.signOut` is the adapter's part (a share has nothing to end and keeps the default); `SourceConnections.remove` is the one removal path both platforms use. The experience layer only says, in the removal question, that Reflux signs this device out of the server.
- **Known limit:** the server also reports every live session with the same device id as ended (by device id, not by user). Reflux has one device id per installation, so with two accounts of one server added to one Reflux, removing one ends the other's live session on the server; its token is untouched and its next request opens a new session. A device id per account would avoid that, and would change every existing installation's device on its servers; not worth it for V1.

## 2026-10-17 — Desktop capabilities are observed, not declared

> Built by "How the desktop observes what it can show and decode" (2026-10-19), which also records why HDR is not claimed yet.

The desktop capability profile (`DesktopCapabilities`) declares hardware decoding for H.264, HEVC, and VP9 on every machine and an SDR display; Best Version ranks by it. Decided for V1: claims about hardware decoding come from what the engine reports for this machine (libmpv's decoder in use, recorded per codec after a playback, and a conservative default before), and HDR output is claimed only where the platform reports an HDR display. Until then the declared profile stays, and the roadmap lists the gap (ROADMAP.md, M1).

## 2026-10-19 — How the desktop observes what it can show and decode

Implements "Desktop capabilities are observed, not declared" (2026-10-17).

- **Unknown is a value.** `VideoDecoderSupport.hardware` is true, false, or null (not known). Only a version that only known-software decoders fit gets the "decoded without hardware acceleration" compromise; an unknown decoder is neither counted against a version nor for it, so no codec is preferred for being on a list. `DisplayCapabilities` has a null size when it is not known which display playback goes to; nothing is then capped by it. Best Version's criteria and their order are unchanged; only their inputs are truthful.
- **Hardware decoding is asked of the engine, once per launch.** A few seconds after start, `MpvDecodeProbe` decodes the first frame of 16 bundled samples (a still grey picture: H.264, HEVC, VP9, and AV1, at 8 and 10 bits, at 3840x2160 and 1920x1080 if that was software; about 100 KB in all) with no video or audio output and reads `hwdec-current`. It asks only through the decoders playback's own output can use: on Windows the output is Direct3D 11, so `d3d11va-copy` (asked for any decoder, a laptop's discrete GPU answered through NVDEC, which that output cannot use); elsewhere `auto-copy-safe`. The engine's `decoder-list` says which codecs it decodes in software at all. What a sample could not answer (it could not be opened) stays unknown; legacy codecs (MPEG-2, VC-1, VP8, MPEG-4 Part 2) and 12-bit are not probed and stay unknown.
- **Real playback is the better witness.** At each file's first frame the player reports the decoder in use, the codec, the frame, and the decoded pixel format. Hardware proves the key (codec and depth) at least that large; software at a size believed hardware disproves it. Streams that are not 4:2:0 are not used: a hardware decoder may refuse 4:4:4 that it takes at 4:2:0. Nothing is kept across launches: the probe runs again, so a driver or graphics change is seen at the next start.
- **The display is the one the window is on.** On Windows, the monitor the window is mostly on (`MonitorFromWindow`), its current mode in pixels, and its HDR state from the display configuration API; elsewhere the toolkit's screen for the window and its mode. Read again when the window has moved (once it settles), when the toolkit puts it on another screen, and when it is activated (a resolution or HDR switch made in the system's settings), on a thread of its own. Pages read Best Version again when something observed changes.
- **HDR is not claimed on the desktop yet.** The display's HDR state is read (Windows) but Reflux's engine does not switch its output to the stream's colour space (`target-colorspace-hint`), so HDR is tone-mapped to SDR on every display, HDR or not; the profile says SDR with tone mapping, which is what plays (`DesktopCapabilities.ENGINE_OUTPUTS_HDR`). Turning HDR output on needs a run on an HDR display, which was not available (the only HDR-capable monitor here has HDR switched off in Windows, and switching a user's display mode is not a test's to do). Linux: the video is embedded in an X11 window, which has no HDR output; Wayland's colour management is not reachable from there.
- **Not built:** remembering probe results across launches, probing 12-bit or legacy codecs, an HDR output path, per-display refresh rates.

## 2026-10-18 — Watch Next is derived from watch state, never stored

Implements "Watch Next is a V1 feature" (2026-10-17) on Android TV and Google TV.

- **One rule for what can be continued.** The launcher's programs are computed from `Library.continuations()`, the same computation as Continue Watching (most recent first, one entry per show, works with no version left excluded), by `WatchNext` in `library` (platform-neutral, pure). A film or episode with a resume position is `CONTINUE` with its position and running time; a show's next episode, or one started over and barely begun, is `NEXT`. At most 10 programs, the most recently engaged.
- **The launcher is compared with, never believed.** Each run reads back only Reflux's own rows and compares a fingerprint stored with each row (`internal_provider_data`); it adds what is missing, updates what changed, deletes what is no longer to be continued, and leaves unchanged rows alone so the launcher does not reorder them. Nothing read from the launcher reaches watch state; updating it never marks anything watched.
- **A work whose source is away stays on the row**, as it stays in Continue Watching: a sleeping server or an unplugged drive is not the end of a film. Selecting it opens the work's details page, where Play says why it cannot play. A work whose last version is gone (its source removed) leaves the row.
- **What the user removes from the launcher is deleted and stays off until they play it again in Reflux.** The platform hides such a row and expects the application to delete it (`ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED`). Reflux deletes it at its next run and remembers, per work, when it was last played at that moment (application preferences, not the library). That is a preference about the launcher, not progress. No broadcast receiver: Reflux does nothing in the background.
- **When it runs:** while Reflux runs, two seconds after the user's state settles (`AppState.revision`) and when the user leaves the application (after the paused position is written). The platform asks for under five seconds.
- **Selecting a program** sends an explicit intent to `MainActivity` naming the work (`reflux://play/<media id>`, no intent filter, so no new public entry point): the details page opens and Play runs as it would there (`AppState.continueFromOutside`).
- **Artwork** comes from Reflux's own artwork cache through a read-only, exported `ArtworkProvider` that serves only cache files by their digest names; the launcher draws cards in its own process and cannot read private storage. Publishing never waits for the network: art cached later is written then.
- **Capability, not device name:** it runs where the system has the TV provider. Phones and tablets have none.
- **Permissions:** `WRITE_EPG_DATA` is required to insert programs (Google TV emulator, Android 16: refused without it). The TV provider library's `READ_EPG_DATA` is removed from the manifest: reading back an application's own programs does not need it.
- **Dependency:** `androidx.tvprovider:tvprovider` 1.1.0 (May 2025, the current stable), Apache 2.0, depending only on AndroidX core and annotation, already in the graph. It is the documented way to write Watch Next programs; the alternative, writing `TvContract` columns by hand, is the same contract with less checking.
- **Not verified:** Google TV shows an application's Watch Next programs only once its integration is certified for quality (Google TV developer guide, read 2026-10-18), and the emulator's launcher needs a Google account before it shows its home. What a launcher draws has therefore not been seen; that the programs are written, updated, removed, and open Reflux was verified on the device (VERIFICATION.md).

## 2026-10-18 — Fire TV ships without a home-screen "continue" row

Resolves the Fire TV half of "Watch Next is a V1 feature" (2026-10-17), from Amazon's current documentation (read 2026-10-18):

- Fire TV's Continue Watching row is fed by **Watch Activity**, part of Amazon's Fire TV Integration SDK (Content Personalization). It requires the application to "participate in the catalog integration process, so Fire TV recognizes the content IDs that your app is passing": every event names a content ID from the application's catalog submitted to Amazon (`cdf_id`), with a partner ID supplied by Amazon and catalog acceptance as a step. The SDK is a jar downloaded from Amazon, not a Maven artifact.
- Reflux plays the user's own media. It has no catalog to submit and no content IDs Amazon could recognize; a film on a USB drive or a home server is not an Amazon catalog entry. Watch Activity is not open to an application of Reflux's kind.
- No Amazon page read says Fire OS shows Android TV Watch Next programs. Reflux does not add Amazon's SDK, does not invent a catalog, and does not try to place programs another way.
- Fire TV therefore ships **without a home-screen continue row**. Continue Watching inside Reflux is the continue experience there, reached from Reflux's ordinary launcher entry. (Whether Amazon's home screen shows recently used applications for any installed app was not documented on the pages read and is not relied on.) V1 acceptance criterion 4 is met for Fire TV by this record.
- Reflux's Watch Next code runs only where the TV provider exists. Whether a Fire OS device has one, and what happens if it does, is checked on the developer's devices; until then it is unknown, and nothing depends on it.
- Revisited if Amazon opens a "continue" integration to applications without a catalog.

## 2026-10-18 — The shared engine may call only the Java that Android's oldest supported release has

Found on the first physical Fire TV (Fire OS 8, Android 11, API 30). The engine modules are plain JVM code compiled against JDK 21 and shared with the desktop (2026-10-13, "Android: one more shell over the same engine"), and nothing checked their calls against Android's API levels: Android lint covers the Android application's own sources, not the JVM libraries it packages. `ArtworkCache` called `Stream.toList()` and `Path.of(URI)` (Android 14), and the WebDAV adapter `URLDecoder.decode(String, Charset)` (Android 13). Every emulator in use ran Android 16, so none of this was seen. On Android 8 to 13, which includes every Fire OS device, artwork prefetch, in-source artwork, and WebDAV browsing failed with `NoSuchMethodError`, an `Error` that the `catch (e: Exception)` around artwork does not catch.

Decided:

- Shared code uses the API 26 forms (`Paths.get`, `listDirectoryEntries`, `URLDecoder.decode(String, "UTF-8")`). Where a newer Java method is wanted, it is wanted on the desktop only, and lives there.
- `checkDebugPlatformApi` (part of `check` where an Android SDK is installed) reads the built package after D8, which backports some such methods by itself, and fails on any `java.*` or `javax.*` method that Reflux's own classes call and Android added after `minSdk`, naming the caller. Android's own APIs stay with lint, because the shell guards them with `SDK_INT`. Alternatives: core library desugaring (a dependency for three calls, and it does not cover everything), a source rule (unenforceable), or class-file scanning before D8 (reports methods D8 backports anyway).
- The check does not report classes Android lacks entirely: `java.net.http` is used only by the desktop's `JdkHttpFetcher`, which Android never loads. A shared class that needs one belongs on the desktop.

## 2026-10-18 — Glass without blur is solid

Android before 12 ignores render effects, so the glass material's blur did nothing there: the sharp page beneath showed through the tint and competed with the material's own text (the folder chooser on Fire OS 8 read as two screens on top of each other). Where the platform cannot blur (`platformBlursBackdrop`: Android 12 and later, and the desktop, can), a material that floats over the page is drawn on an opaque base under its tint instead. The frosted look is unchanged where blur exists. The alternative, a higher tint alpha, still leaves sharp shapes visible behind text.

## 2026-10-18 — Leaving on a television pauses; picture-in-picture is for handhelds

Amends "Android: leaving the app, and the system's controls" (2026-10-13) for televisions. Fire OS offers picture-in-picture, and it also pauses the playing media session itself when Home is pressed (seen on Fire OS 8: a pause command through the session, from the key policy that handles Home). Reflux entered picture-in-picture as it left, so a frozen frame floated over Fire TV's home screen where the remote cannot reach it. On a television (the system's own UI mode or the leanback feature, not a device name), leaving while something plays now pauses and keeps the place, as the Google TV emulator (which has no picture-in-picture) already did. Phones and tablets keep picture-in-picture. A television's picture-in-picture would need a way for the remote to reach and close the window, and a reason to watch in a corner; neither is a V1 need.

## 2026-10-18 — Out of sight, the player gives back its decoder and audio focus

Seen on Fire OS 8: with Reflux stopped in the background and its playback paused, it still held a hardware video decoder (the system's resource manager listed it) and audio focus. Amazon's requirements for media applications ask that an application it no longer shows release them, because other applications may need the device's few decoders. When the activity stops, the Android player now stands by: it keeps the work, its position, and its tracks, and lets the engine release its decoders and focus (`Media3Player.standBy`, Media3's `stop()` without clearing the item). When the activity is seen again it prepares again, paused where it was. (Play from the background, the remote's play key reaching the session, prepared it too until "Out of sight, Play is not obeyed" below.) What the player reports is frozen meanwhile, so watch state and the interface see no change. A floating window on a handheld is not stopped and keeps its decoder.

## 2026-10-18 — Every text field takes a remote the same way

Seen on Fire OS 8: searching began with a stray "a". The fields of the source forms already kept the keyboard away while focus arrived and opened it when OK was let go of, because a keyboard that is up takes the remote and Fire TV's types the key it starts on; the search field did neither, so the system's keyboard already had the field when OK arrived. The rule is now one modifier (`keyboardByRemote`, with `showsKeyboardOnFocus` in the field's options) used by every text field a remote can reach.

## 2026-10-18 — Fire OS has the TV provider; Reflux's Watch Next programs are kept there and not shown

> Superseded by "Fire OS: no Watch Next programs in the TV provider" (below): the open question is decided.

Resolves what "Fire TV ships without a home-screen 'continue' row" left to the device. Fire OS 8 has Android's TV provider (`com.android.providers.tv`, authority `android.media.tv`), so the publisher's capability check passes and Reflux keeps its programs there exactly as on Google TV: `WatchNextOnDeviceTest` passes on the Fire TV Stick (added, updated in place, removed, artwork readable). Fire TV's home screen does not show them: after several Reflux playbacks its own "Continue watching" row held only an Amazon title. No code was added for Fire OS: the publisher is cheap (a few provider rows after the user's state settles), harmless, and right if Amazon ever reads the row. Open for a decision, not a defect: on Fire OS these rows serve no one, and they put the titles of personal media into a system provider that Amazon's own components could read; the Watch Next decision promised that nothing leaves the device beyond what the launcher shows. Turning the publisher off on Fire OS would need a Fire-specific check (`amazon.hardware.fire_tv`), since no capability says whether a launcher shows the row.

## 2026-10-18 — Identify

Builds "Identify / fix match" (ROADMAP.md, M1) on the engine's existing flow (`searchMetadata`, `identifyAs`, persistent overrides), and "Episodes without a show name are grouped by folder and offered to Identify" (2026-10-17).

- **Where it is reached.** Every movie and show page has "Identify", a quiet button beside the others; where Reflux's identification of the work is weak (any of its files below the low-confidence line), the page says "Reflux isn't sure what this is" and offers it plainly. An episode is identified through its show, which is what carries a show's metadata. A page, not a sheet: it holds a field, a list, and actions, and a remote moves through it like any page. Without a metadata provider in the build (no credential), it is not offered; nothing offers what cannot be done.
- **Searching.** The work's own name and year are searched once as the page opens; after that, what the user types, when they ask (Search, or the keyboard's action), never as they type, and never twice for an unchanged query: results stay for the page's life. A trailing year in the query narrows it ("Dune 1984"; a number later than next year is title text, as in file names). Up to 12 results. Searching, nothing found, and the catalog being out of reach are each said; the last offers "Try again" and states that the library is unchanged.
- **Results** show a poster at the catalog's result size (TMDB `w185`, decoded at the smallest size the artwork loader keeps), never the full-size artwork, with the year, the original title where it differs, two lines of what it is about, and "Current match" on the entry the work is matched to.
- **Choosing** writes an override for every file of the work and pins the chosen entry (`identifyAs`, run between scans under the pipeline's lock), fetches its metadata, and caches its artwork. The work's watch history moves with its files as before; its favorite, collection membership, and pinned version now move with it too (they were lost when its identity changed). The corrected work's page opens in place of the old page and the Identify page, so Back does not return to either.
- **Undo** ("Let Reflux identify it") removes the overrides of every file of the work and the pinned entry, and re-identifies them from their names (`undoIdentification`); the same state follows the work back.
- **Episodes without a show name**: after any override, an episode whose show title is still empty is given the folder it is in, above season folders (the source's own name at its top, "Episodes" with neither), and keyed by `show@<source>/<folder>` instead of its title, so two folders, or two sources of one name, never share a show. Its confidence stays weak. Season artwork beside such episodes is not attached to their show (the season's key is rebuilt from the title); rare, and left so.
- Not in this: correcting an episode's own season or episode number, identifying a work Reflux has not found at all, and a list of every weak identification (diagnostics).

## 2026-10-18 — Fire OS: no Watch Next programs in the TV provider

Decides what "Fire OS has the TV provider; Reflux's Watch Next programs are kept there and not shown" left open. On Fire OS Reflux writes **no** Watch Next programs: Fire TV's home screen does not show the Android TV row, Amazon's Watch Activity is closed to an application without a catalog ("Fire TV ships without a home-screen 'continue' row"), so the rows served no one and only put the titles of personal media into a system provider. Android TV and Google TV publish exactly as before.

- The publisher is skipped where the device declares `amazon.hardware.fire_tv` (`WatchNextPublisher.publishes`): Fire OS is the one platform whose launcher is known not to read the provider, and no capability says whether a launcher shows the row, so this is the one place a platform name decides. A provider without that feature still publishes.
- Reflux's own watch state is untouched: Continue Watching and Next Up inside Reflux are the same on Fire OS as anywhere, and nothing is deleted or changed in the library because publishing is off.
- Rows that development builds wrote on a Fire TV before this are not swept by the application (no release ever wrote them); uninstalling, or the instrumented test, removes them.
- Revisited with the Fire TV decision, if Amazon opens a "continue" integration to applications without a catalog.

## 2026-10-18 — Out of sight, Play is not obeyed

Reflux does not play in the background on Android ("No playback in the background", 2026-10-13). Seen on the Fire TV Stick: with Reflux in the background, the remote's play key still reached its media session, prepared the engine again (a decoder and audio focus), and resumed the sound with nothing of Reflux on screen; the same holds for a headset's or Bluetooth device's play button on any Android device.

Decided, the same on phones, tablets, Android TV, Google TV, and Fire OS: while the activity is stopped (out of sight, from `standBy` to `wake`), Play does nothing. The system's controls (`SystemControls`, what the media session acts on) may still pause and stop; play, prepare, seeking, and moving to another episode are ignored, so a background Play neither resumes the work nor takes a decoder or audio focus back. Reflux's own `play()` is ignored too, so nothing in Reflux can start playback unseen. In sight, including a handheld's picture-in-picture window (which is not stopped), the system's controls are the engine's as before. When the user returns, the player is paused where they left it and Play is theirs again.

Alternatives rejected: deactivating or releasing the media session in the background (Fire OS's Home pause and the system's pause commands come through it, and releasing it means rebuilding it on every return), and dropping the media-button route only (a connected controller, such as a Bluetooth headset's, would still reach the session).

## 2026-10-18 — An episode's versions

Completes "Version pinning is reached from the versions it applies to" (2026-10-17) for episodes. The engine already ranked, pinned, and planned per work (`selectVersion`, `pinVersion`, `version_preference` keyed by the episode); what was missing was a place to see an episode's versions.

- **Where.** An episode has its own details page, the same page as a film's: under its show's name, with the episode's number and title, its Play/Resume, "From the start", watched and favorite, and the same versions list (play once, "Always play this", "Let Reflux choose"). It has "All episodes" in place of Identify (an episode is identified through its show) and no Download button (choosing single episodes to keep is Smart Offline's work, ROADMAP.md). The launcher's Watch Next row, which names episodes, now opens this page; it used to open a details page that had nothing for an episode and said the work was gone.
- **Reached from the episode's row only when there is a choice.** A row on the show's page offers "N versions" beside it (beneath it on a phone) only when the episode has more than one version to choose between; a season where no episode does keeps its rows as before. On a television the choice is a step to the right of the row, with a wider gap so a focused control's ring stays clear of the row, and Back returns to it. Pinning reorders the list, and focus goes with the chosen version (to its "Let Reflux choose"), never staying in place on another version's "Always play this" (seen on the Fire TV); this holds for films too.
- **What counts as a choice.** A copy kept on the device is the version it was made from: pinning it pins the origin (as for films), so an episode in one copy and its download is one choice and offers nothing. A film in one copy and its download no longer offers "Always play this" for the same reason. Both copies are still listed and either can be played once.
- **Not alike when not the same.** A version on another timeline than the recommended one (a named cut, or a running time too far apart, `Timelines`) says "Another cut" with its running time, on films as on episodes.
- **A pin set aside is said, not rewritten.** When the pinned version is not what Play uses this time, the versions list says why in place of its general line: its source isn't connected, this device can't play it, or the cut being resumed comes first; the pin is kept and applies again when it can. Best Version's order is unchanged.
- **A pin follows its version.** Identifying a show re-keys its episodes; each episode's pin now moves with the version it names (`movePinsWithTheirVersions`), as watch history moves with files. Rescans keep pins, since an episode's identity does not change with a rescan.
- One query per show page counts every episode's choices (`episodeVersionChoices`); nothing else is fetched or decoded for the rows, and the episode page loads its versions only when opened.

## 2026-10-18 — Smart Offline: episodes, seasons, a Downloads page, and the desktop

Completes Smart Offline for V1 (2026-10-17, "Smart Offline on the desktop is in V1"). The planner, its rules, and copies as versions are unchanged; what is new is what a user can ask for and where they see the answer.

- **What can be asked for.** A film or an episode (its details page or its own page, and a mark beside each episode's row), a season ("Download season"), or a show (its next three episodes, as before). A season is every episode of it, watched or not: choosing a season is choosing all of it, and keeping only the unwatched ones would make "Season on this device" untrue. The alternative was the earlier rule (the next three unwatched of the season), which made a season the same thing as a show. Each episode is planned on its own, so Best Version, a pin, the same-cut rule, and the room check apply per episode, and what is already here or on its way is not planned again.
- **A show or a season is counted per episode** (`offlineCoverage`). It is "Downloaded" only when every episode is kept, and otherwise says how many are; a card has an outline mark for some. The earlier status called a show downloaded when one episode was.
- **Asking again picks up what stopped** (`Offline.resumeStopped`). A paused or failed copy counts as on its way, so the planner does not plan it twice; without this, choosing a season again would have left its failed episodes failed with nothing said.
- **A pin set aside is said in the sheet.** The planner keeps its rule that a pinned version that cannot be had right now gives way to the best one that can (as Play sets it aside); the sheet now says so instead of giving the general reason. The pin stays. Changing that rule (leaving the work out) was considered and not done: it is tested and consistent with playback.
- **Downloads is a page**, reached from a download button beside Search in the navigation (and from the media sources page), listing copies by what can be done about them (on their way, didn't finish, on this device), each with its work's name, the version, its size and source, and its actions beside it. It is not a library: a line opens the work's page. The general "prepare for offline" sheet now only proposes (it is opened from the page as "Keep what you're watching"); the copies are on the page. The alternative, a longer sheet listing everything, did not fit a remote: its text cannot be scrolled with one, and its actions crowded each other.
- **Removing** a finished copy asks once; with a remote, focus starts on Keep, so a press too many keeps it. A season's or show's copies can be removed together, asked once.
- **On a television** every download control is a step to the right of what it belongs to, with a gap that keeps its focus ring off the row's text (24 dp), and the season's action sits at the end of the tabs' line over the episodes' marks, so Up and Down move straight between them. A sheet returns focus to the control that opened it.
- **Progress does not reload the application.** A download's progress advances its own counter (`downloadRevision`), followed only by what shows downloads (`offlineState`); before, every megabyte reloaded every screen on view. A copy finishing or going changes the library and so reloads it as before. Progress is drawn as a bar or a ring that moves only when progress does.
- **The desktop** is wired as Android is: `DesktopEnvironment` keeps copies in `downloads` in Reflux's data directory, recreates the downloads source at the next launch, and makes copies in the application's scope while Reflux runs. No decision beyond 2026-10-17 was needed.

## 2026-10-18 — Web shares on Android: their own transport, on OkHttp

WebDAV could not be added on any Android device (found on the Fire TV). Two causes, the second hidden behind the first:

- **The method.** A share is listed with `PROPFIND`. Android's `HttpURLConnection` (`AndroidFetcher`) refuses every method that is not plain HTTP's (`ProtocolException`), so connecting failed as if nothing answered. The desktop uses the JDK's `java.net.http` client, which allows any method; Android has no such client.
- **The parser.** `WebDavSource` asked the XML parser to refuse a document type (`disallow-doctype-decl`, against XXE). Android's parser accepts no such setting and throws, so every listing would have failed to read on Android even once it arrived.

Decided:

- **Web shares on Android go through OkHttp** (`com.squareup.okhttp3:okhttp`, Apache 2.0), in `WebDavTransport` in the Android shell, behind the same `HttpFetcher` the shared adapter already takes. Only the WebDAV connector and WebDAV sources are given it; everything else on Android keeps `AndroidFetcher`. It sends only the methods that read a share (`PROPFIND`, `GET`, `HEAD`, `OPTIONS`) and refuses any other before it leaves the device, so the adapter stays read-only by construction. It follows redirects itself, as the desktop's client does: method and body kept, never from `https` to `http`, and the `Authorization` header dropped when the origin changes. An answer over 32 MB is refused instead of being read into memory. OkHttp honours Android's network security policy (cleartext stays the manifest's choice) and the system proxy.
- **The document-type refusal is the adapter's own**: `WebDavSource` refuses any listing that declares one (`declaresDoctype`), on every platform, and still asks the parser where the parser supports it.
- **Hrefs are paths on the share's own address.** Only the path of a listing's href is used, resolved on the share's scheme and host and normalized, and a name that decodes to `.`, `..`, or contains `/` is skipped; a playback path with `.` or `..` is refused. Before, an href naming another host sent the next listing there with the share's credentials, and `..` could reach outside the share. Servers behind a proxy that report their internal address in hrefs now work too.

Alternatives rejected: setting `HttpURLConnection`'s protected `method` field by reflection (on Android an `https` connection is a wrapper whose delegate is a non-SDK field, blocked by hidden-API restrictions); `X-HTTP-Method-Override` (WebDAV servers do not honour it); a hand-written HTTP/1.1 client on sockets (TLS hostname checking, proxies, the cleartext policy, chunked bodies, and pooling would all be Reflux's to get right); Cronet (Google Play services or a large native library, and not on Fire OS); moving all of Android's HTTP to OkHttp (no requirement asks for it, and it would widen this change to Jellyfin, artwork, metadata, and downloads). Nothing already in the dependency set can send `PROPFIND`: Media3's HTTP data source is also `HttpURLConnection`.

Consequences: OkHttp 5.5.0 resolves to `okhttp-android` plus Okio 3.18.1, about 1.3 MB of artifacts before shrinking (R8 and its keep rules are M2, RELEASE.md). Third-party notices (M2, About) must list OkHttp and Okio. The desktop's WebDAV is unchanged apart from the shared href and document-type rules.

## 2026-10-19 — About, legal, and attribution

> Its bullet on Reflux's own license is settled by "Reflux is proprietary; its source is visible, not licensed" (2026-10-19).

Reflux had no page that said which build it is, who it credits, or what software it is made of. Decided:

- **About is reached from the media sources page**, as its last row ("About Reflux"), and belongs to the same group of pages: no library tab is lit, the media sources button leads out of it. Reflux has no settings page, and a player-first application does not grow one for this. About is one short page: the name and which build, the metadata provider's credit, what Reflux keeps and sends, and a row to the third-party software; each component's licenses are a page further in, read a paragraph at a time.
- **The build says which commit it is.** The shells' builds write `commit=` and `modified=` into the package (a resource on the desktop, an asset on Android) from Git, read lazily (a `ValueSource`) so an edit does not invalidate the configuration cache; without a Git checkout the version stands alone. The version itself is still only `RefluxBuild.VERSION`. Untracked files count as modified: a build cannot know they do not matter. The diagnostics logs start each run with the same label, and the HTTP user agents, which said `Reflux/0.1`, now carry the product version too (VERSIONING.md, rule 9).
- **Third-party notices are a checked-in list, checked by the build.** `legal/components.txt` names each component, its license, the texts shown for it (verbatim in `legal/licenses`, taken from the artifacts themselves where they carry them), and which build has it; the `ui` build generates the page's data from it, and `checkThirdPartyNotices` (part of `check`) fails when a shell's release runtime classpath has a module no line covers, or a line covers nothing that shell has. Rejected: a license-report Gradle plugin (a new build dependency whose data comes from POMs, which AndroidX's module metadata often leaves out, and which cannot see what is native: Skia and ICU inside Skiko, libmpv) and a hand-written list in code (nothing would notice it going stale).
- **A metadata provider's credit belongs to its adapter.** `MetadataProvider.attribution` gives the name, the notice its terms require word for word, its site, and its approved logo as vector data; the About page shows whichever provider is in use, and nothing when there is none. TMDB's is its "Primary short (blue)" logo, drawn from the published SVG's own path and gradient and held to that file by a test, smaller than Reflux's name, with "This product uses the TMDB API but is not endorsed or certified by TMDB." (developer.themoviedb.org FAQ, read 2026-10-19). Its site is named, not linked: televisions have no browser to open it in.
- **libmpv is listed as what it is today**: the copy on the user's computer, which Reflux loads and does not include, under the LGPL or the GPL depending on how it was built. The LGPL text and a source offer come with the libmpv build Reflux will ship (M2).
- **Privacy is stated as facts, not as a policy.** The page says what the code does (no account, nothing sent to Reflux's makers, metadata lookups to TMDB, the user's servers see what is played from them), checked against every host the production code names. The privacy policy and a support contact are documents the website will carry (LAUNCH.md, W3); no link is shown until they exist.
- **Diagnostics are not exposed yet.** The log removes credentials but keeps file paths and the titles of the user's media, and the library diagnostics are lists of titles. An export needs path redaction and a way to hand the text over on each platform (a television has no clipboard to speak of); it comes with the feedback path (RELEASE.md, beta entry).
- **Reflux's own license is not chosen** (README, RELEASE.md). The page shows it, first, as soon as a `LICENSE` file is in the repository; until then it shows none rather than a guess.
- **Coming back finds the control on every page.** `FocusEntry` remembered one control for the whole application, so Back two pages deep put focus at the top; it now remembers one per page.

## 2026-10-19 — Reflux is proprietary; its source is visible, not licensed

Decided by the product owner. Reflux is not open-source software, and no license to its source code is granted:

- **All rights reserved.** Reflux's own code, documentation, and design belong to RSX Develops, the maker the website already names (LAUNCH.md). The repository's `LICENSE` is a plain ownership notice, `Copyright © 2026 RSX Develops. All rights reserved.`, with a few sentences saying what that means; it is not a license, and it is not a custom legal license drafted to look like one. No MIT, Apache, GPL, LGPL, BSD, or other open-source license applies to Reflux itself.
- **The source is public on purpose**, as a portfolio and for transparency. Seeing it gives no permission to redistribute it, use it commercially, publish modified versions, sublicense it, build it into another product, or distribute works derived from it.
- **Third-party software keeps its own licenses.** Every component Reflux is built with stays under the license its authors chose, listed with its full text on the About page (`legal/`, checked by `check`). Reflux's status restricts none of them, and the notice says so.
- **On the About page**, the copyright line stands with the build, and "Copyright and ownership" is a row of its own, before "Third-party software", leading to the notice. The two never share a row or a page, so neither is read as the other's terms. The page reads the notice from `LICENSE` at build time (the mechanism the About decision already had), so there is one text, not a second copy in code.
- **A consequence for libmpv:** a proprietary application can ship libmpv only as an LGPL build (mpv built without its GPL parts, and an FFmpeg without GPL components), loaded dynamically as today, with its texts and a source offer. A GPL build cannot be bundled. This narrows the M2 item that was already "LGPL build" (RELEASE.md).
- **Left for legal review, not drafted here** (RELEASE.md, "Legal review"): whether the store and desktop builds need end-user terms (an EULA) beyond the stores' own; whether "RSX Develops" is the right legal name for the holder; how the notice reads beside the hosting service's own terms for public repositories (GitHub's let its users view and fork them on GitHub); and an agreement for outside contributions, without which none can be accepted.

Alternatives rejected: an open-source license (the owner's decision is that Reflux is not open source); a source-available license written for the occasion (that is legal drafting; if one is wanted, it is written with legal advice); no notice at all (the default is the same in law, but a visitor to a public repository would have to guess).

## 2026-10-19 — Reflux is free at launch, supported by voluntary donations

Decided by the product owner. How Reflux pays for itself at launch:

- **Free, whole, and unlimited.** 1.0 has no price, no subscription, no Pro tier, and no feature gating, and nothing in the core media-player experience is limited to make a paid version look better.
- **Voluntary donations** are the way to support Reflux at first. Their place is a calm, unobtrusive "Support Reflux" section on the website (LAUNCH.md), not the application: this milestone adds no donation or payment system to the app. The donation destination does not exist yet; choosing a provider and setting it up is a launch-track task. No provider, address, or account is named before it exists.
- **Reflux Pro is a possibility for later, not a plan.** If it comes, it would draw on advanced capabilities after V1 (the V1.x and Future candidates in ROADMAP.md). Which ones, if any, is not decided now; that is decided explicitly when those capabilities are understood. V1 functionality is never removed or held back to make room for it.
- **Nothing is built for it now**: no in-app billing, subscriptions, entitlements, license keys, or payment code. Store listings declare no in-app purchases.

## 2026-10-20 — Controls are composed for each device, not one set resized

An audit of every screen at each form factor (offscreen at phone, tablet, desktop, and television sizes, then on the devices) found the player in particular carrying one bar onto every screen and hiding parts of it by width: a tablet got the desktop's bar, its volume slider, and a fullscreen button that did nothing (on Android fullscreen only turns the picture sideways, and a tablet on its side already shows nothing else); every device showed Previous, which on a film or a first episode only went back to the start; the ten-second seeks were the same circle as Refresh, with no number; a phone's bar held seven equal icons. Decided:

- **One policy decides the player's controls**, `PlayerControls.of` (pure, tested), from the form factor, the input in use, and what the platform says about itself, rather than `if` statements spread through the overlay:
  - The transport (play and pause, the seeks) is in the middle of the picture on a touch screen, phone or tablet; in the bar for a pointer or keyboard; nowhere on a television, whose remote does all of it.
  - Previous and Next are shown only when there is an episode that way. The timeline is the way to a film's start.
  - Volume is shown only where `Platform.systemVolume` is false (desktops), and not on a phone-sized window.
  - Fullscreen follows the new `Platform.fullscreenMode`: `WINDOW` (desktop) always; `LANDSCAPE` (Android phones and tablets) only while the screen is upright or fullscreen is on, so there is a way back; `NONE` (televisions) never.
  - A phone keeps Audio and Subtitles as buttons and puts the other lists behind one "More options" button; the lists' own tabs lead to all of them. Tablets and desktops have room for each. Episodes are a tab, never a button, everywhere.
- **The seeks say what they do**: ↺10 and ↻10, the number never mirrored.
- **A television has no on-screen Back in its navigation**; the remote's Back is the way back.
- **With a pointer, an icon says what it is** after the pointer rests on it, and in the player the key that does the same. The label is not in the accessibility tree (the control's own description is), and touch, remotes, and controllers never see it.
- Smaller fixes from the same audit: a landscape phone's navigation is a centred pill rather than a bar stretched across the screen (a modifier order made its maximum width do nothing); a notice no longer covers the phone's offline sheet (it is said at the top while the sheet is open); a television's details actions stand further apart, so a focused action's ring and growth do not crowd its neighbour.

Kept as they were, deliberately: the phone's own navigation along the bottom and the larger screens' bar along the top; the player's way back on touch screens even though Android has a system Back (the system bars are hidden while playing, and the gesture is not discoverable there); the television's named lists beneath the timeline, which say what Down opens.

Alternatives rejected: a per-platform player composable for each form factor (four copies of the same timeline, lists, and failure handling, drifting apart); deciding by screen width alone (a large tablet is wider than a small desktop window, and a tablet with a mouse attached is driven like a desktop: the input in use and the platform's own capabilities decide, width only says how much fits).

## 2026-10-31 — The accessibility baseline is shared rules, not descriptions on every screen

An audit of every screen and the player (VERIFICATION.md) found the labels largely in place and the structure not: everything beneath the player and the panels stayed in the accessibility tree, nothing that happened by itself was ever said, the player's controls left and could not be found again, and fixed heights cut large text.

- **What the system asks for is one value**, `Platform.accessibility` (screen reader, reduce motion, text scale), observed from the system and never a setting of Reflux's own. Android listens to `AccessibilityManager` and `ANIMATOR_DURATION_SCALE`; a desktop reads `SystemParametersInfo` and the registry on Windows and `gsettings` on Linux, at start and when the window comes back to the front. Text scale exists because only Android applies it for itself.
- **Isolation is decided once, in `RefluxApp`**: while the player or a panel is open, the page and the navigation are `inert`. That takes them out of the semantics tree and does not touch focus, on purpose: what had focus keeps it behind the video, which is how a remote comes back to where it was. Taking focus away too would have changed behavior verified on the Fire TV for no gain a screen reader sees.
- **Controls stay up for a screen reader.** A screen reader finds things by reading them; controls that fade away on a timer could never be found again, because what brings them back is a movement it does not make. They are pinned while one runs. The system's own "time to take action" also lengthens the timer for everyone else.
- **A television's player gets actions, not buttons.** There are still no buttons on a screen nobody points at, and the D-pad policy is untouched. Play, pause, and the seeks are custom actions on the timeline, and the names of the lists can be activated by a screen reader without being focus stops, because TalkBack takes the remote's keys for itself.
- **The desktop player stays key-driven.** Its overlay window never takes focus, deliberately (DECISIONS: keys must keep working over the native video surface), so its buttons cannot be Tab stops. Every button has a key instead, including a new `O` for the lists, since many keyboards have no Menu key; Tab is swallowed while playing.
- **Announcements come from a node that was already there.** Android's live regions speak when a known node changes and never when a new one appears, so `announces` sits on something always composed, and stands for what is drawn inside it, so it is met once. What arrives as a new part of the screen (a failure, Up Next) uses a pane title instead, with fixed words: a countdown would otherwise be read every second.
- **Reduced motion goes through the one gate that exists.** Nearly every animation already calls `MotionSpec.tween()`, so that is where it is applied: duration 0. It reads a small global, `MotionPolicy`, because transition specifications are not composable and cannot read a composition local; threading a value through some thirty call sites would have been more code and easy to miss. Android's "Remove animations" already zeroes Compose's animations; the setting is still read, for the spinner and the scrolls that do not go through `tween()`.
- **Only the navigation limits text size**, at 1.5×: its width is fixed by what has to fit in it, and past that Search would leave the phone's bar. Everything else takes any size and scrolls. The full names stay in the tree either way.

Alternatives rejected: a description on each call site (what the roadmap's line asked for, and what would have left every structural gap in place); a global "accessibility mode" setting (the system already knows, and AGENTS.md prefers a default to a setting); making the desktop overlay window focusable for Tab (it would take keys from the video surface, which is why it does not); `invisibleToUser` or a separate hidden announcer (a second stop for the same words, or none at all).

Known limits, for the record: Compose Desktop reaches screen readers through the Java Access Bridge on Windows, which Narrator does not use, and has no AT-SPI bridge on Linux; so the labels are there, but a desktop screen reader pass depends on the toolkit (M3). TalkBack restores its own focus when a panel closes, and Reflux cannot move it: in touch mode a control cannot take input focus. Scrolling that focus starts is not reduced on a desktop (the toolkit's own animation, short, and what the user asked for).
