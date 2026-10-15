# Reflux Roadmap

## Status

| Area | State |
| --- | --- |
| Media Core contracts (entities, versions, locations, availability, watch state, artwork) | Implemented in `core` |
| Universal watch state and version handoff: one watch state per work, positions carried between copies by timeline (cut and running time), different cuts never resumed blindly, reconciliation with sources that keep their own watch state | Implemented and tested with source-independent doubles ([WATCH_STATE.md](WATCH_STATE.md)); verified on Windows and Linux, and the exchange verified against a real Jellyfin server (see the verification table below) |
| Source abstraction and capabilities | Contracts implemented |
| Playback capability model and Best Version (capability-driven, local-first among equivalent versions, continuity of the cut in progress) | Implemented and tested |
| Input model (semantic actions, mappings, modality tracking) | Implemented and tested |
| Filename/folder identification, sidecars, confidence | Implemented and tested |
| Local persistence / offline model (SQLite, offline and unmount safety) | Implemented and tested |
| Local folder source (desktop) and scan → identify → library | Implemented and tested |
| Player contract, default track selection, playback controller, chapters, skip intro/recap/credits, next-episode autoplay with "still watching?" | Implemented and tested |
| Desktop engine: libmpv playback and probing (verified headlessly against real media) | Implemented and tested |
| Metadata engine: provider contract, deterministic matching, TMDB provider, episode metadata, Identify flow, artwork cache | Implemented and tested |
| Background pipeline: change watching, debounced rescans, reconnect detection, scan → probe → metadata → artwork | Implemented and tested |
| Smart search (deterministic query understanding over metadata, credits, stream facts, watch state) | Implemented and tested |
| Smart library: default Home feed, franchise/manual/smart collections, diagnostics (weak identification, unmatched works, multiple versions, missing episodes and artwork, offline sources) | Implemented and tested |
| Catalog-source architecture and Jellyfin adapter (sign-in, catalog, multi-version items, direct streams, two-way watch state through the source-independent reconciliation) | Implemented and tested against API fixtures. Sign-in, the catalog, the direct stream, and the watch-state exchange are verified against Jellyfin Server 12.2.0, first in Docker and then on a real home server with a full library (1,453 versions, including works with several), where a server stream also played in the desktop engine (see the live Jellyfin verification below); the watch state of episodes and other server versions are not |
| WebDAV network source (read-only) | Implemented; tested against fixtures and a live WsgiDAV server |
| SMB network source (read-only, streamed through a loopback server) | Implemented; tested against a live SMB2 server (impacket), including probing and playback |
| Cross-source unification: works sharing a provider ID merge into one work (Best Version across sources) | Implemented and tested |
| Design system foundations (tokens, materials, motion, adaptive layout, artwork atmosphere) | Implemented and tested; rendered by the experience layer |
| Developer CLI (scan, search, info, play, doctor) over the real engine | Implemented; verified end to end |
| Continuous integration (Linux with libmpv, Windows) | The workflow runs the whole check on both and the desktop window tests on Linux under a virtual display, from scripts in `tools/ci` that a container image runs identically. On Linux, where the job installs the engine and a display, a test that needs them fails rather than skips itself. Those steps pass locally (Windows 11, and Ubuntu 24.04 in the container); the workflow itself has not run on GitHub since the desktop shell was added |
| Experience layer (`ui`): first-run, Home, Movies/Shows, smart search, movie and show details, sources, player controls and menus, input-adaptive layout, failure states | Desktop Vertical Slice 0.2: implemented and tested as state logic, as rendered screens, and under pointer, keyboard, and controller input |
| Desktop shell (`desktop:app`): window, embedded libmpv video with Reflux controls above it, fullscreen, keyboard, mouse, and controller input, native folder picker, diagnostics log | Desktop Vertical Slice 0.2: verified end to end in the real window on Windows 11 and on Linux (X11 with a compositing manager): first run through the folder dialog, browsing, and real playback operated by a mouse |
| Desktop controllers (XInput and HID joysticks on Windows, the kernel joystick interface on Linux) | Implemented and tested from the reader upward, and hardened against the non-controller devices real machines report. No physical controller has been connected during development, so Xbox, PlayStation, and Switch Pro hardware is unverified; `PhysicalControllerTest` is the guided check for it |
| Platform boundary | Shared modules are free of platform APIs and `check` enforces it; what a new shell supplies is listed in the architecture document |
| Android phone and tablet client (`android:app`): the shared experience layer with phone and tablet layouts, folders through the Storage Access Framework, Media3 playback, the canonical watch state | First vertical slice implemented and hardened, verified on an emulator: first run, choosing a folder, storage that goes away and comes back, the library, details, playback, resume, picture-in-picture, the system's media controls, controller input, and continuity with a real Jellyfin server (see the verification table below) |
| Android TV / Google TV: the same application and experience layer as a television form factor (10-foot layout, focus that is always somewhere, a remote-driven player, Reflux's own folder picker where the system has none, HDMI audio capabilities) | First milestone implemented, verified on the Google TV emulator: first run, choosing a folder, Home, Movies and Shows, details, search with the system keyboard, playback and its lists, resume, Continue Watching, and continuity with a real Jellyfin server, all with remote keys only (see the verification table below). Not verified on a real television |
| Smart Offline: a copy of a version kept on the device as one more version of the same work, chosen by Best Version for this device in the room there is, played and resumed like any version ([OFFLINE.md](OFFLINE.md)) | Engine implemented and tested in `core` and `library` for every platform; in the experience layer a Download action, its status, and the "Watch offline" sheet; wired on Android (phone, tablet, television). Verified on emulators, including a real download from a Jellyfin server and playback with the server stopped (see the verification table below) |
| Source onboarding and management: adding a folder, a Jellyfin server, or a network share (SMB or WebDAV, told apart by the address) from the application; a sources page with each source's state, contents, and actions (refresh or try again, sign in again or change, remove); sources that are away stay listed and in the library; Smart Offline reached from a details page through a sheet that shows and explains what would be kept before anything is downloaded; a neutral, restrained pass over the interface's use of amber | Implemented and tested (see the verification table below). Connectors are platform-free contracts in `core`, wired on the desktop and on Android alike (Jellyfin, SMB, WebDAV) |
| Source parity and library controls: SMB on Android; choosing a Jellyfin server's libraries; connection secrets sealed with the Android keystore; adapters closed when replaced; the navigation lit only for the section the user is in | Implemented and tested (see the verification table below); exercised against live SMB, WebDAV, and Jellyfin servers on the desktop engine and in the installed Android application |
| Visual reset: neutral-first cinematic interface (no interface hue, bright-neutral primary action, white progress and focus, artwork atmosphere behind heroes, selective frosted glass, versions led by the decision, the player title by the timeline, neutral app marks); the amber identity retired (DECISIONS.md, 2026-10-14) | Implemented across desktop, phone, tablet, and television (one experience layer); verified as rendered screens on every form factor and by the full check (see the verification table below) |
| Smart Offline gaps | Downloads stop when the application is closed and continue at its next start. No choosing of single episodes or whole seasons. Not wired on the desktop. Removable storage is not offered. Verified on emulators only |
| Artwork completeness: one fallback rule for every frame (poster, backdrop, the other real image, then generated), including a work whose artwork cannot be read on a platform (DECISIONS.md, 2026-10-15) | Implemented and tested; a television does not request image access in v1 (decided, DECISIONS.md, 2026-10-15) |
| Television gaps | Subtitle and artwork files next to a video are not visible to Reflux on a television on Android 13 and later (the system only shows an app the media files; `READ_MEDIA_IMAGES` is not requested in v1, by decision; artwork embedded in or taken from the video is a possible later direction); nothing on the launcher beyond the app itself (no Watch Next row); a held seek key repeats at a fixed step; a folder on a drive is only reachable while the drive is mounted where it was; picture-in-picture, 4K, HDR, Dolby Vision, passthrough, and removable drives could not be exercised on an emulator |
| Android gaps | No playback in the background (by decision, for now); chapters are not reported by the engine; a folder's changes are picked up at the next refresh rather than as they happen; the Identify and collections flows have no UI on any platform yet; release builds (signing, shrinking) are not set up; verified on an emulator only, not on physical phones or tablets |
| Known gaps | Watch state is not synchronized between devices except through a source that keeps it; cuts are told apart by edition labels and running times only; stacked multi-part files play as separate versions; disc structures (BDMV/VIDEO_TS) are recognized but not playable; credentials are kept in the open on Linux desktops without a Secret Service |
| Not yet in the experience layer | Identify flow, library diagnostics, and user collections have engine support but no UI; no settings screen yet; browsing one source's media on its own |
| Desktop shell gaps | libmpv is not bundled and there is no installer; media keys are not captured; no scrollbars; the GNOME and KDE folder dialogs are used when their helper is installed but are only unit-tested (the Linux end-to-end run uses the toolkit's chooser); on X11 without a compositing manager the player's controls window is opaque and hides the video; Wayland has only been reached through X11 |
| Not verified here | TMDB against the live service (no credential on the development machine; the provider is covered by fixtures and the app falls back to file names); a session with physical controllers |
| Release readiness | Tracked in [RELEASE.md](RELEASE.md): libmpv distribution, credential storage, provider attribution, multi-part files, disc structures |

### Desktop Vertical Slice 0.2: what was verified

Verified on 2026-10-13, on the tree that closes the slice.

| Where | What ran | Result |
| --- | --- | --- |
| Windows 11 | `./gradlew check` | 358 tests, none failed; 7 skipped (the window tests, which run on request; the physical-controller check; the live SMB test) |
| Windows 11, real window | `./gradlew :desktop:app:test -Preflux.windowTests=true`: first run through the native folder dialog, browsing with a mouse and with a scripted controller, playback with its controls, seeking, resume, next episode, fullscreen, a deleted file, closing the window | 27 tests, none failed; 1 skipped (physical controller) |
| Ubuntu 24.04 in the `tools/ci` container (Xvfb, openbox, xcompmgr, software rendering) | the Linux job: `check`, then the real-window tests eight times in a row | 358 tests, none failed; eight rounds without a failure, and every window test ran in every round |

Not verified: the workflow on GitHub (this work has not been pushed); physical controllers (none connected); TMDB against the live service; the GNOME and KDE folder dialogs; X11 without a compositing manager; native Wayland. The window tests need the desktop to themselves: a locked desktop, the test window being moved, or another window taking focus while they run fails them.

### Watch state and version handoff: what was verified

Verified on 2026-10-13, on the tree that closes the milestone.

| Level | What | State |
| --- | --- | --- |
| Implemented | Canonical watch state, timelines and cuts, handoff, reconciliation with sources, Best Version order, schema migration | Done |
| Verified on Windows 11 | `./gradlew check`; the real-window tests | 415 tests, none failed; 27 real-window tests, none failed (physical-controller check skipped) |
| Verified on Linux (Ubuntu 24.04 in the `tools/ci` container) | The Linux job: `check`, then the real-window tests | 415 tests, none failed; two real-window rounds without a failure. With `REFLUX_REQUIRE_ENGINE_TESTS=true`, every engine, watch-state, migration, and source suite ran; only the Windows-only, physical-controller, and live-server tests were skipped |
| Verified against a live service | `JellyfinLiveTest` against Jellyfin Server 12.2.0 in Docker: sign-in, catalog, direct stream, and the watch-state exchange in both directions, including the stale-value case | Passed, repeatedly, against freshly created servers |
| Not verified | Jellyfin with episodes, multi-version items, a real library, or another server version; playing a server's stream in the engine; watch state with WebDAV and SMB sources beyond the shared tests (they keep no watch state of their own) | Open |
| Blocked by the environment | The workflow on GitHub (not pushed); TMDB against the live service (no credential) | Open |

### Android phone and tablet: what was verified

Verified on 2026-10-13, on the tree that closes the Android hardening milestone.

| Level | What | State |
| --- | --- | --- |
| Implemented | The Android shell over the shared experience layer and engine: folders through the system picker, Media3 playback, picture-in-picture, the media session, phone and tablet layouts, the amber accent | Done |
| Windows verified | `./gradlew check` (Android lint and unit tests included); the desktop real-window tests | 428 tests, none failed; 27 real-window tests, none failed |
| Linux verified | The Linux job in its container, which has no Android SDK: `check`, then the real-window tests | 421 tests, none failed (the Android unit tests are not part of a build without an SDK); the real-window round without a failure |
| Layouts, offscreen | Phone portrait and landscape and tablet, rendered and operated by touch (`HandheldLayoutTest`); every rendering reviewed | 4 tests, none failed |
| Android emulator verified (Android 16, API 36.1, x86_64 phone) | `connectedDebugAndroidTest`: the shared parsing on Android's runtime; a folder becoming a library on Android's SQLite with the platform prober; storage that goes away, comes back empty, and comes back whole; cancelling the picker; continuity between a local folder and a server in both directions, a different cut, and a stale server record; real Media3 playback (seek, speed, end, a missing file); watching, then resuming | 11 tests, none failed; the live-server test is skipped unless a server is named |
| Android emulator, by hand | First run through the system folder picker; cancelling it; the grant surviving a restart; the folder moved away (the library stays, titles marked offline) and back; details, playback, the timeline, Resume; leaving with Home into picture-in-picture, the system's play/pause key acting on it, and returning; rotation during playback; controller key events (navigation, select, back, play/pause, the layout morph and its prompts); system text at 130% | Done, with screenshots reviewed |
| Live Jellyfin verified (Jellyfin Server 12.2.0 in Docker) | From the emulator: a film watched from the device's storage to 00:30:12 resumed at 00:30:12 from the server's stream, and back to the local file; the server heard of the progress. From the desktop JVM: the whole watch-state exchange (`JellyfinLiveTest`) | Passed on this tree, both |
| Physical-device verified | Phones, tablets, and controllers in hand | **Not done.** No Android device was used (the developer's phone was attached and deliberately left alone), and no physical controller was connected; controller input was exercised as key events on the emulator |
| Not verifiable on an emulator | Hardware decoding, HDR output, high-resolution playback, removable storage, real Bluetooth media buttons | Open |
| Environment-blocked | The workflow on GitHub (not pushed); TMDB against the live service (no credential) | Open |

### Visual refinement: what was verified

Verified on 2026-10-13, all of it on the tree that closes the visual refinement (`2b7a21f`: colors, type, and accent use in the shared experience layer, and the details page's progress and recommendation; no navigation or engine change).

| Level | What ran | State |
| --- | --- | --- |
| Implemented | The refined look across desktop, phone, and tablet: neutral dark base, one amber accent, sentence-case type, quiet chips, progress on details pages | Done |
| Windows verified | `./gradlew check`, with every screen rendered and all 36 renderings reviewed (desktop, phone, tablet, player, controller focus, empty, search, and error states) | 428 tests, none failed |
| Windows verified, real window | `./gradlew :desktop:app:test -Preflux.windowTests=true`, playback included | 27 tests, none failed. One run in between failed in two steps while the machine was in use; the run after it passed |
| Linux verified (Ubuntu 24.04 container, engine tests required) | The Linux job: the headless `check`, then one round with the real-window tests (first run, browsing by mouse and by scripted controller, playback in the window) | 421 tests, none failed, in both |
| Android emulator verified (Android 16, API 36.1) | `connectedDebugAndroidTest` | 11 tests, none failed; the live-server test ran separately, below |
| Android emulator, by hand | First run through the system folder picker, Home, details, playback, the timeline, leaving at 00:30, Resume and the progress on details, Continue Watching on Home, resuming from there, and leaving into picture-in-picture | Done, with screenshots reviewed |
| Live Jellyfin verified (Jellyfin Server 12.2.0 in Docker) | From the emulator: a film watched from the device's storage to 00:30:12 resumed there from the server's stream, and back to the local file. From the desktop JVM: `JellyfinLiveTest` | Passed, both |
| Physical-device verified | A phone or tablet in hand | **Not done.** The developer's phone was attached and deliberately left alone |
| Physical-controller verified | A controller in hand | **Not done.** None was connected; controller input was scripted on the desktop and sent as key events on the emulator |
| Environment-blocked | The workflow on GitHub (not pushed); TMDB against the live service (no credential), so artwork in every run was generated placeholders | Open |

### Android TV / Google TV: what was verified

Verified on 2026-10-14, on the tree that closes the first television milestone, unless a row says otherwise.

| Level | What ran | State |
| --- | --- | --- |
| Implemented | Television as a form factor of `ui` (layout for a 960 by 540 canvas, automatic and returning focus, focus glow, row-at-a-time Home, remote-driven player with an Episodes list, the folder picker), and in the Android shell: television detection, the TV launcher entry and banner, the storage browser and its permission, path-based folder sources, HDMI audio capabilities | Done |
| Windows verified | `./gradlew check`, with every screen rendered: 51 renderings, of which the 15 television ones and a sample of the desktop and phone ones were reviewed | 439 tests, none failed. `TelevisionTest` drives the real screens with remote keys: starting focus, rows, details and back, the navigation, Movies, search, the player and its lists, and choosing a folder |
| Windows verified, real window | `./gradlew :desktop:app:test -Preflux.windowTests=true` | 27 tests, none failed (physical-controller check skipped) |
| Linux verified (Ubuntu 24.04 container, engine tests required) | The Linux job: the headless `check`, then one real-window round | 430 tests, none failed, in both |
| Android TV emulator verified (Google TV, Android 16, 1080p) | `connectedDebugAndroidTest`: everything the handheld suite covers, on the television image, and `TelevisionOnDeviceTest`: a folder chosen by path becoming a library, one folder being one source, Android's file watching, real Media3 playback from a path, the source surviving a restart and its storage going away and coming back; and what the device reports it can show and send out | 13 tests, none failed. The emulator reports a 1080p SDR display, stereo output, and no passthrough, and that is what Reflux claimed |
| Android TV emulator, with remote keys only | First run: the system's permission question, the folder picker down to a folder, the library appearing. Home, a row, details, playback, seeking with the D-pad and with the fast-forward key, the lists, Back; Resume and the progress on details; Continue Watching on Home and resuming from it; where focus is after each step; leaving with Home (playback pauses; this image has no picture-in-picture). Search: the television keyboard, typing, results, Back | Done, with screenshots reviewed. Search and the storage flow were walked on the tree before the last focus fixes; focus and playback on the final one |
| Android phone emulator verified (Android 16) | `connectedDebugAndroidTest`; first run still opens the system folder picker and the storage permission stays ungranted | 13 tests, none failed |
| Live Jellyfin verified (Jellyfin Server 12.2.0 in Docker) | From the TV emulator: a film watched from the device's storage to 00:30:12 resumed there from the server's stream, watched on to 00:33:00, and continued from the local file | Passed, on the tree before the last focus fixes (which are in `ui`; the engine and playback code did not change after). The desktop `JellyfinLiveTest` was not rerun for this milestone |
| Watch-state continuity | The same canonical watch state as every platform; the continuity tests (local to server and back, a different cut, a stale server record) ran on the television image | Passed, as part of the 13 |
| Physical-device verified | A television or streaming box, and its remote | **Not done.** None was available |
| Physical-controller verified | A controller in hand, on a television or anywhere | **Not done.** Controller input on a television is covered by the shared tests only |
| Not verifiable on an emulator | Hardware decoding, 4K, HDR and Dolby Vision output, an HDMI receiver's channels and passthrough formats, drives plugged into the device, picture-in-picture on a television, a real remote's media keys | Open |
| Environment-blocked | The workflow on GitHub (not pushed); TMDB against the live service (no credential), so artwork in every run was generated placeholders | Open |

The TV emulator stopped by itself twice during this work (the emulator process, not the app) and was restarted; no test result above comes from an interrupted run.

### Smart Offline: what was verified

Verified on 2026-10-14, on the tree that closes the Smart Offline milestone, unless a row says otherwise.

| Level | What ran | State |
| --- | --- | --- |
| Implemented | The download capability on sources; the download model, storage policy, and planner in `core`; records, queue, copying, and registration of copies as versions in `library`, with a schema migration; the Download action, status, and "Watch offline" sheet in `ui`; Android wiring over private storage | Done |
| Planner and engine, with source-independent doubles | `OfflinePlannerTest` (the version this device plays best, the smallest among equals, fitting the room, a pinned version never substituted, cuts, the reserve, estimates, subtitles) and `OfflineTest` (a copy is one more version of the same work; playback and resume with the source away; reconciliation when it is back, including a stale server record; pins; a different cut; proposals; insufficient room at planning and at start; interrupted and resumed transfers with and without ranges; a source that is away, gone media, a source that hands nothing out; pause, cancel, retry; an application stopped mid-download; subtitles; removal of a copy and of its source; corrected identity; storage away and a missing file), and both migrations | Part of the checks below; none failed |
| Windows verified | `./gradlew check`, with every screen rendered: 61 renderings, of which the 10 offline ones and the phone details page were reviewed | 468 tests, none failed. `OfflineUiTest` drives the real screens: the action and its states, the sheet, removal, a phone, and a television by remote |
| Windows verified, real window | `./gradlew :desktop:app:test -Preflux.windowTests=true` | 27 tests, none failed (physical-controller check skipped) |
| Linux verified (Ubuntu 24.04 container, engine tests required) | The Linux job: the headless `check`, then one real-window round | 459 tests, none failed, in both |
| Android phone emulator verified (Android 16; a clean virtual device, since the earlier one had no room left to download into) | `connectedDebugAndroidTest`, with `OfflineOnDeviceTest`: a copy fetched into the application's storage against the device's real free space, registered in Android's SQLite, played in Media3 from where the viewer was with its source away, still there after a restart, and removed | 16 tests, none failed; 3 skipped (the two live-server tests and the connect helper, which ran separately) |
| Android TV emulator verified (Google TV, Android 16) | The same suite | 16 tests, none failed; the same 3 skipped |
| Live Jellyfin verified (Jellyfin Server 12.2.0 in Docker), automated | On both emulators: a film watched from the server to 00:30:12, downloaded from the server over HTTP (the file's size as the server states it), played from the device at 00:30:12 with the server out of reach and on to 00:33:00, and the server holding that position once it was reachable again. The earlier continuity test alongside | Passed on both, on the tree before the last two interface fixes (wrapping actions on a phone, focus after a panel closes), which are in `ui` only |
| Live Jellyfin, by hand in the installed application (phone emulator) | With the server connected through the test helper: Download on the film's details, progress, "On this device", the copy listed as the recommended version; the server's container stopped, the source shown as not connected, the library and artwork intact, Resume playing from the device at about 00:19 where it was left, watched on to 00:30; the container started again, and the server's own record read back as 00:30:15 | Done, with screenshots reviewed |
| By hand on the TV emulator, remote keys only | Download from the details page, the sheet, Back, and playing the copy with the server stopped | Done |
| Physical-device verified | A phone, tablet, or television in hand | **Not done** |
| Not verified | Large files and slow or metered networks; a download interrupted by the system killing the application (the restart path is covered by tests, not by a real kill); WebDAV and SMB as download sources beyond the shared tests (SMB does not exist on Android); a server whose reported size differs from the file's | Open |
| Environment-blocked | The workflow on GitHub (not pushed); TMDB against the live service (no credential) | Open |

### Source onboarding and management: what was verified

Verified on 2026-10-14, on the tree that closes the milestone.

| Level | What ran | State |
| --- | --- | --- |
| Implemented | Connectors for Jellyfin, WebDAV, and SMB behind `SourceConnector`; `SourceConnections` in `library`; the Media sources, Add media, and connect pages; the offline sheet's review step with the planner's own reason; download marks on cards; the amber restraint pass | Done |
| Engine, with fixtures | `JellyfinConnectorTest`, `WebDavConnectorTest`, `SmbConnectorTest` (addresses as people type them, every failure told apart, nothing secret kept or logged), `SourceConnectionsTest` (routing, persistence, signing in again, a changed address, nothing added on failure), `OfflinePlannerTest` (why each version was chosen) | Part of the checks below; none failed |
| Windows verified | `./gradlew check`, with every screen rendered; the source, connect, offline review, phone, tablet, and television renderings reviewed. `SourcesUiTest` drives the real screens: first run with a folder first and a server one step aside, a wrong password and then the right one, a share that is not there, a server that goes away (still listed, still in the library), trying again, signing in again, a phone, a tablet, and the whole flow with remote keys | 488 tests, none failed |
| Windows verified, real window | `./gradlew :desktop:app:test -Preflux.windowTests=true`: first run, the media sources page, Add media, the share form, Back out of it, the controller's Menu button, playback | 27 tests, none failed (physical-controller check skipped) |
| Linux verified (Ubuntu 24.04 container, engine tests required) | The Linux job: the headless `check`, then a real-window round | Passed, both. The first window round failed in the test itself (a click meant for the navigation's "Media sources" landed on the page title of the same name); the test was corrected and the round rerun |
| Live Jellyfin, phone emulator (Android 16, Jellyfin Server 12.2.0 in Docker), by hand in the installed application | First run, Connect a server or share, Jellyfin, the address typed with the phone keyboard and Next between fields, a wrong password ("That user name or password wasn't accepted.") and the right one; the library with the server's artwork; details, Download, the review sheet (`SD · 16 MB · from …`, room available), Download, "On this device", the copy as the recommended version; the server stopped, the source "Not connected" with its explanation, its address and account, Try again, Sign in again; the film played from the device | Done, with screenshots reviewed |
| Live Jellyfin, television emulator (Google TV, Android 16), remote keys only | The same from first run: the form reached and filled with the D-pad and the television keyboard, connecting with OK, Home, details, Download, the sheet with focus on its confirmation, focus back on the action afterwards, the server stopped and the film played from the device; Menu opens the media sources | Done, with screenshots reviewed |
| Found by the device runs and fixed | On a phone the bottom navigation sat on top of the keyboard and covered Connect (it now steps aside while typing); a field's label did not focus its field; on a television the keyboard opened as soon as a field had focus and took over the remote, and OK's release typed a letter into it (it now opens on OK, when OK is let go); a server in a container was named by its generated host name | Fixed and rerun |
| Not verified | SMB and WebDAV connections against live servers from the application (their connectors are covered by fixtures and a closed port, and the adapters by the earlier live tests); physical devices; a real television's keyboard | Open |
| Environment-blocked | The workflow on GitHub (not pushed); TMDB against the live service (no credential) | Open |

### Desktop credential storage: what was verified

Verified on 2026-10-14. The real Jellyfin server integration this was planned alongside ran on 2026-10-15 (below).

| Level | What ran | State |
| --- | --- | --- |
| Implemented | Source connections in the system credential store on the desktop (Windows Credential Manager; the Secret Service on Linux where one runs), a reference in the database, migration of connections stored in the open, removal of a source's entry, the developer CLI connecting through the same path; redaction of quoted Jellyfin tokens and stream-server tokens | Done |
| Fixture-tested | `CredentialStorageTest` (migration at start, a store that refuses, removal, an entry the store lost, two libraries on one account, the Secret Service protocol never putting a secret in arguments), `SourceSectionsTest` (the per-source seal), `RedactionTest` | Part of the check below; none failed |
| Windows verified | `./gradlew check`, including a real round trip through Windows Credential Manager | 513 tests, none failed |
| Not verified | The Secret Service on a Linux desktop with a running keyring | Open |

### Live Jellyfin on a real server: what was verified

Verified on 2026-10-15 on Windows 11, against the developer's own Jellyfin Server 12.2.0 (RS-X1) on the local network, with a dedicated test account named only in `~/.reflux-live/jellyfin.env`. The engine was driven through the developer CLI and headless libmpv, each step a fresh process over test libraries of their own; no window was opened.

| Level | What ran | State |
| --- | --- | --- |
| Real server, automated | `JellyfinLiveTest`: sign-in, the catalog, the direct stream, and the whole watch-state exchange (existing state read, progress reaching the server, nothing re-sent, a stale value not winning, another client's progress and "played" adopted, watched and unwatched both ways) | Passed |
| Real server, by hand | Signing in through the application's connector: 1,453 versions read (246 films, 45 shows), 265 works with several versions. Best Version on the server's own duplicates: decided by size when everything above it tied, by the stable order for identical copies, and by resolution over a lower-resolution local copy. A server stream played in libmpv with its audio and subtitle tracks, stopped at 10:08, resumed there, and the server held the position | Done |
| Real server, by hand: handoff | A local copy of a server film with the same running time, one work with the server's copy. Local progress (20:03) kept and the server told nothing when the server was connected; progress after that (25:03) reached the server at the next scan; another client's 40:00 adopted and the local copy resumed there (same timeline); Reflux's newer 40:04 not undone by the server's 40:00, which was brought up to date; watched and unwatched sent both ways; the test account's record for the film left as found | Done |
| Real server, credential storage on Windows | The connection in Credential Manager under the test library's name; the database holding only `keychain:v1:<source id>`; no password, token, or server address in the database, the cached artwork, the test reports, or the CLI's output; every later process reconnecting without the password; the test libraries' sessions signed out and their entries removed afterwards | Done |
| Real server, out of reach | The server made unreachable for the engine's process alone (a dead HTTP proxy): the source checked as unavailable, a scan removing nothing, every work still listed and marked offline with its watch state, artwork still cached, Play answered with no plan instead of a failure; reachable again: available, nothing changed, playable, no sign-in | Done |
| Found by the live run and fixed | A film's only subtitle, marked default but not forced, stayed on although its English audio was understood: mpv published the tracks before saying which it had chosen, so the default track selection was decided against no selection (`MpvEngineTest` reproduces it with a generated file) | Fixed in `playback:mpv`, rerun against the server |
| Not verified | The desktop application's window against this server (a game was running on the machine); the server going away in the middle of playback; the watch state of episodes and of a work with several copies on the server; the Secret Service on Linux (the protocol-level test is no substitute) | Open |

### Source parity and library controls: what was verified

Verified on 2026-10-14, on the tree that closes the milestone.

| Level | What ran | State |
| --- | --- | --- |
| Gaps found before changing anything | SMB not offered on Android; a server's libraries could not be chosen; adapters not closed when replaced (a share's connection left open); adding SMB on Android would have stored its password in the open; Home lit on the sources pages; WebDAV and SMB never exercised through the application | All closed below |
| Engine, with fixtures | `JellyfinLibrariesTest` (which libraries are offered, a library left out is never read, an item in two libraries is read once, an older server's address, the choice kept with the connection), `SourceSectionsTest` (leaving a library out and taking it back with its watch history, the connection kept, everything cannot be left out, signing in again keeps the choice, sealed storage and sealing what was stored before, adapters closed), `LocalStreamServerTest` (listens on 127.0.0.1) | Part of the checks below; none failed |
| Experience layer | `SourcesUiTest`: choosing libraries by pointer and by remote, the last one kept, a server that is away cannot be asked, the card's summary, one film across a folder and a server with all its versions, no tab lit on the sources pages | Part of the checks below; none failed |
| Windows verified | `./gradlew check`, then the real-window tests; every screen rendered and the source, library-choice, phone, tablet, and television renderings reviewed | 503 tests, none failed; the real-window tests ran and passed (physical-controller check skipped) |
| Linux verified (Ubuntu 24.04 container) | The headless `check`, then a real-window round | Passed, both |
| Android emulators (phone and Google TV, Android 16) | `connectedDebugAndroidTest` on each, with SMB and the keystore seal in the application | 13 passed on each; the 3 tests that need a named live server skipped themselves |
| Live, desktop engine (`SourcesLiveTest` against Samba and WebDAV in Docker, `tools/ci/share-test-servers.sh`) | A wrong password, a share that does not exist, a share that needs a sign-in, each told apart; both shares connected and scanned into one film with a version on each; libmpv opening each version (running time read through SMB's loopback stream and over WebDAV); a copy kept from the SMB version; both servers stopped: sources kept and not connected, every version still known, the copy playing; both started: available, nothing duplicated, the same share connected again being the same source | Passed, all three phases, after the SMB fixes below |
| Live, Android phone emulator, by hand in the installed application | Upgrading over an installation whose server token was stored in the open: sealed at start, still read. A share added as `//10.0.2.2:1445/Movies` with its name and password, scanned, probed, played; the copy removed and kept again from the share; the share stopped: "Not connected" with its explanation, and the film resuming from the device; started again: reconnected by itself. A Jellyfin server with two libraries (`REFLUX_JELLYFIN_KIDS=1`): both listed, Kids left out, its film gone from the library, "1 library left out" on the card | Done, with screenshots reviewed |
| Found by the device run and fixed | The SMB stream server bound to IPv6 loopback on Android; a probe closed the share under the player; a failing stream ended the application | Fixed in `sources:smb`, rerun on the device and on the desktop engine |
| Not verified | SMB and the library choice on the television emulator (the remote path is covered by `SourcesUiTest`); physical devices; NAS products other than Samba | Open |
| Environment-blocked | The workflow on GitHub (not pushed); TMDB against the live service (no credential), so artwork in these runs was the server's own or generated | Open |

### Visual reset: what was verified

Verified on 2026-10-14 on Windows 11, on the tree that closes the visual reset (`design`, the experience layer, and the shells' icons; no navigation, playback, source, or library change).

| State | What | Result |
| --- | --- | --- |
| Implemented | The full check: 516 tests, 0 failures, 9 skipped (the environment-gated live-server, real-window, and physical-controller tests), including new tests that hold every chrome token to no hue, the primary action and progress to their contrast, the artwork atmosphere to a readable dark tone, and versions to leading with what they are chosen by | Done |
| Implemented | Every screen rendered offscreen at desktop, phone, tablet, and television sizes (`-Preflux.screenshots`) and reviewed: Home with and without artwork, scrolled Home, details of a movie, a show, and a work kept offline, versions with the recommendation, the player and its lists on all form factors, first run, sources, search, the offline sheet, and controller and remote focus | Done |
| Not verified | The new look on a physical phone, tablet, or television, and in the real desktop window with real artwork (the renders use generated fixture artwork) | Open |

## Phase 0 — Product and architecture foundation

- Define universal media entities.
- Define source and capability abstraction.
- Define library, metadata, artwork, playback, availability, version, watch-state, download, and search contracts.
- Define normalized input actions.
- Define adaptive UI states.
- Establish default-experience requirements.

## Phase 1 — Visual language and interaction prototype

- Build the liquid-glass + cinematic design language.
- Establish typography, spacing, poster/backdrop treatment, materials, focus states, motion, navigation, player surfaces, and empty/loading states.
- Prototype controller morph transitions before large-scale UI implementation.
- Use a Jellyfin-connected prototype only as a design laboratory if useful; do not make Jellyfin the architecture.

## Phase 2 — Scanner and identification engine

- Filesystem scanning.
- Incremental scanning and file watching.
- Large-library behavior.
- Disconnected-source behavior.
- Filename parsing.
- Movie/show/season/episode identification.
- Confidence scoring.
- Manual "Identify this movie/show" correction.
- Persistent per-item association overrides.

## Phase 3 — Metadata and artwork engine

- Posters, backdrops, logos, titles, synopsis, cast, genres, runtime, release data.
- Seasons and episodes.
- Local caching.
- Offline browsing.
- Provider abstraction.

## Phase 4 — Local media MVP

Target loop:

`folder -> scan -> identify -> artwork -> virtual library -> play`

Initial target:

- movies,
- TV shows,
- seasons,
- episodes,
- search,
- continue watching,
- resume,
- favorites,
- collections,
- cached local metadata.

## Phase 5 — Smart library

- Automatic collections.
- User collections.
- Rule-based smart collections.
- Diagnostics for duplicates, unidentified files, missing episodes, bad metadata, multiple versions, missing artwork, and disconnected storage.
- Opt-in library intelligence.

## Phase 6 — Smart search

Start deterministic and rule based. The first version is implemented (`SmartQueryParser`, `Library.smartSearch`): kinds, watch state, resolution and HDR, genres and people from the user's own library, years and decades, and runtimes; leftover words match titles, and the interpretation is returned for display.

Examples:

- `4k movies I haven't watched`
- `Nolan movies`
- `science fiction under 2 hours`
- `movies with Ryan Gosling`

Natural-language intelligence can be layered above this later.

## Phase 7 — Jellyfin integration

- Server connection and authentication.
- Libraries.
- Remote metadata.
- Playback.
- Resume/watch-state sync.
- Collections and playlists where practical.
- Local cache of server library information.

## Phase 8 — Universal source architecture

Candidate adapters:

- Local filesystem
- Jellyfin
- SMB
- WebDAV
- Plex
- Emby
- DLNA

Priority is based on product value and platform capability, not feature-count for its own sake.

## Phase 9 — Best Version

- Resolve equivalent media across sources.
- Prefer local when quality/compatibility are otherwise equivalent.
- Consider resolution, HDR, audio, compatibility, reliability, and availability.
- Allow user overrides.
- Continue on another version from where the user stopped (implemented: [WATCH_STATE.md](WATCH_STATE.md)).

## Phase 10 — Native Android

First vertical slice done and hardened (see the status and verification tables above); servers and shares (SMB and WebDAV) can be added in the application. Next on Android: the Identify flow, physical-device verification, and release builds.

- Android phone.
- Android tablet.
- Touch-first adaptive layout.
- Native playback integration.
- Strong controller support.

## Phase 11 — Serious playback

- Hardware decoding.
- Practical H.264 / HEVC / AV1 support by platform.
- HDR where supported.
- Audio track controls.
- Subtitle engine and styling.
- Chapters.
- Resume.
- Playback speed.
- Picture controls.
- Passthrough where supported.
- PiP/background behavior where supported.

## Phase 12 — Controller and adaptive UI

First-class support for:

- PlayStation controllers,
- Xbox controllers,
- Nintendo controllers where practical,
- generic Bluetooth/USB controllers,
- Android TV remotes,
- keyboard/media keys,
- handheld devices.

The first meaningful controller input should trigger a smooth transition into controller-optimized interaction.

## Phase 13 — Smart Offline

First milestone done (see the status and verification tables above, and [OFFLINE.md](OFFLINE.md)); reachable from the application since servers and shares can be added there. Next: continuing with the application closed, if that product decision is made; episode and season selection; the desktop.

- Download media.
- "Prepare for offline".
- Consider storage, quality, audio/subtitles, unwatched content, and next episodes.
- Future trip mode.

## Phase 14 — Android TV / Google TV

First milestone done (see the status and verification tables above): the television is a form factor of the shared experience layer inside the one Android application. Next for television: a real device, the Identify flow, and the launcher's Watch Next row. Servers and shares are added with the remote and the television's keyboard.

- D-pad-first interaction.
- 10-foot layouts.
- TV-oriented focus system.
- HDR/4K/audio support within platform capabilities.

## Phase 15 — Desktop

- Windows.
- Linux.
- Mouse/keyboard/controller.
- Large-library performance.
- Hardware acceleration.
- Fullscreen and multi-monitor considerations.

## Phase 16 — Ecosystem features

- Optional device handoff.
- No mandatory Reflux account.
- Local-network continuity where practical.
- Casting.

## Phase 17 — Advanced intelligence

- Natural-language search.
- Recommendations.
- Context-aware discovery.
- Higher-level library intelligence.

The deterministic local engine remains the source of truth.

## Phase 18 — v1.0 target

Target platforms:

- Android phone/tablet,
- Android TV / Google TV,
- Windows,
- Linux.

Target foundations:

- local media,
- Jellyfin,
- at least one network source,
- universal source architecture,
- automatic identification and artwork,
- virtual organization,
- smart collections,
- search,
- continue watching,
- version detection,
- serious playback,
- controller support,
- adaptive liquid-glass/cinematic UI,
- excellent defaults,
- no mandatory account,
- no filesystem mutation.

Explicitly outside the initial v1 target:

- iOS/iPadOS commitment,
- social features,
- Watch Together,
- own streaming service,
- mandatory cloud backend,
- AI everywhere,
- unnecessary server administration complexity,
- own transcoding stack unless justified by product requirements.
