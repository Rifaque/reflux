# Reflux Verification Log

What was run, on what, and with what result. This is history, not plan: the product roadmap is [ROADMAP.md](ROADMAP.md), and what a release still needs is [RELEASE.md](RELEASE.md).

Rules for adding to it:

- One section per milestone or validation cycle, dated, on the tree it names.
- Say what ran (command, device, server, by hand or automated) and the result, including what was **not** verified and why.
- A result from an emulator, a container, or a fixture is never recorded as a physical-device, live-service, or real-hardware result.
- Do not edit an old section to make it current; add a new one. Test counts in old sections are counts at that time.

## Desktop Vertical Slice 0.2: what was verified

Verified on 2026-10-13, on the tree that closes the slice.

| Where | What ran | Result |
| --- | --- | --- |
| Windows 11 | `./gradlew check` | 358 tests, none failed; 7 skipped (the window tests, which run on request; the physical-controller check; the live SMB test) |
| Windows 11, real window | `./gradlew :desktop:app:test -Preflux.windowTests=true`: first run through the native folder dialog, browsing with a mouse and with a scripted controller, playback with its controls, seeking, resume, next episode, fullscreen, a deleted file, closing the window | 27 tests, none failed; 1 skipped (physical controller) |
| Ubuntu 24.04 in the `tools/ci` container (Xvfb, openbox, xcompmgr, software rendering) | the Linux job: `check`, then the real-window tests eight times in a row | 358 tests, none failed; eight rounds without a failure, and every window test ran in every round |

Not verified: the workflow on GitHub (this work has not been pushed); physical controllers (none connected); TMDB against the live service; the GNOME and KDE folder dialogs; X11 without a compositing manager; native Wayland. The window tests need the desktop to themselves: a locked desktop, the test window being moved, or another window taking focus while they run fails them.

## Watch state and version handoff: what was verified

Verified on 2026-10-13, on the tree that closes the milestone.

| Level | What | State |
| --- | --- | --- |
| Implemented | Canonical watch state, timelines and cuts, handoff, reconciliation with sources, Best Version order, schema migration | Done |
| Verified on Windows 11 | `./gradlew check`; the real-window tests | 415 tests, none failed; 27 real-window tests, none failed (physical-controller check skipped) |
| Verified on Linux (Ubuntu 24.04 in the `tools/ci` container) | The Linux job: `check`, then the real-window tests | 415 tests, none failed; two real-window rounds without a failure. With `REFLUX_REQUIRE_ENGINE_TESTS=true`, every engine, watch-state, migration, and source suite ran; only the Windows-only, physical-controller, and live-server tests were skipped |
| Verified against a live service | `JellyfinLiveTest` against Jellyfin Server 12.2.0 in Docker: sign-in, catalog, direct stream, and the watch-state exchange in both directions, including the stale-value case | Passed, repeatedly, against freshly created servers |
| Not verified | Jellyfin with episodes, multi-version items, a real library, or another server version; playing a server's stream in the engine; watch state with WebDAV and SMB sources beyond the shared tests (they keep no watch state of their own) | Open |
| Blocked by the environment | The workflow on GitHub (not pushed); TMDB against the live service (no credential) | Open |

## Android phone and tablet: what was verified

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

## Visual refinement: what was verified

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

## Android TV / Google TV: what was verified

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

## Smart Offline: what was verified

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

## Source onboarding and management: what was verified

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

## Desktop credential storage: what was verified

Verified on 2026-10-14. The real Jellyfin server integration this was planned alongside ran on 2026-10-15 (below).

| Level | What ran | State |
| --- | --- | --- |
| Implemented | Source connections in the system credential store on the desktop (Windows Credential Manager; the Secret Service on Linux where one runs), a reference in the database, migration of connections stored in the open, removal of a source's entry, the developer CLI connecting through the same path; redaction of quoted Jellyfin tokens and stream-server tokens | Done |
| Fixture-tested | `CredentialStorageTest` (migration at start, a store that refuses, removal, an entry the store lost, two libraries on one account, the Secret Service protocol never putting a secret in arguments), `SourceSectionsTest` (the per-source seal), `RedactionTest` | Part of the check below; none failed |
| Windows verified | `./gradlew check`, including a real round trip through Windows Credential Manager | 513 tests, none failed |
| Not verified | The Secret Service on a Linux desktop with a running keyring | Open |

## Live Jellyfin on a real server: what was verified

Verified on 2026-10-15 on Windows 11, against the developer's own Jellyfin Server 12.2.0 (RS-X1) on the local network, with a dedicated test account named only in `~/.reflux-live/jellyfin.env`. The engine was driven through the developer CLI and headless libmpv, each step a fresh process over test libraries of their own; no window was opened.

| Level | What ran | State |
| --- | --- | --- |
| Real server, automated | `JellyfinLiveTest`: sign-in, the catalog, the direct stream, and the whole watch-state exchange (existing state read, progress reaching the server, nothing re-sent, a stale value not winning, another client's progress and "played" adopted, watched and unwatched both ways) | Passed |
| Real server, by hand | Signing in through the application's connector: 1,453 versions read (246 films, 45 shows), 265 works with several versions. Best Version on the server's own duplicates: decided by size when everything above it tied, by the stable order for identical copies, and by resolution over a lower-resolution local copy. A server stream played in libmpv with its audio and subtitle tracks, stopped at 10:08, resumed there, and the server held the position | Done |
| Real server, by hand: handoff | A local copy of a server film with the same running time, one work with the server's copy. Local progress (20:03) kept and the server told nothing when the server was connected; progress after that (25:03) reached the server at the next scan; another client's 40:00 adopted and the local copy resumed there (same timeline); Reflux's newer 40:04 not undone by the server's 40:00, which was brought up to date; watched and unwatched sent both ways; the test account's record for the film left as found | Done |
| Real server, credential storage on Windows | The connection in Credential Manager under the test library's name; the database holding only `keychain:v1:<source id>`; no password, token, or server address in the database, the cached artwork, the test reports, or the CLI's output; every later process reconnecting without the password; the test libraries' sessions signed out and their entries removed afterwards | Done |
| Real server, out of reach | The server made unreachable for the engine's process alone (a dead HTTP proxy): the source checked as unavailable, a scan removing nothing, every work still listed and marked offline with its watch state, artwork still cached, Play answered with no plan instead of a failure; reachable again: available, nothing changed, playable, no sign-in | Done |
| Real server, by hand: episodes | A second run the same day, through a scratch library of its own (the server's library had changed meanwhile: 1,189 versions, the second copies of 264 works gone from the server itself). One show the test account had not started: another client's position on an episode adopted, Continue Watching and Next Up on that episode, playback resuming there; progress reaching the server; Reflux's newer position kept over the server's older one and delivered at the next scan; watched and unwatched both ways; another client's "played" adopted; Next Up following each change by the documented rule. The test account's records for the episodes left as found | Done |
| Real server, by hand: several copies on the server | A film with two copies on the server whose running times differ by five minutes (two timelines). Best Version by size from the start; continuity over a pinned copy when resuming, the pin when starting over; the other cut resumed at an adjusted position, in both directions (the same moment in the longer copy, five minutes earlier in the shorter); a position sent only to the server copy on its timeline, "watched" to both; another client's position on one copy adopted with that copy's timeline. Both copies' records left as found, the pin removed | Done |
| Real server, cut off mid-playback | The scratch library connected through a local forwarder that could reset or silently drop its connections, the server itself untouched. Reset during playback: playback went on from what the engine had already buffered (most of an episode, on a local network), the source checked as unavailable, the position reached was kept and delivered at the next scan once reachable. Reset, then a seek past the buffer: reported as the media being unreachable, the position kept (after the fix below). Connections dropped without a reset, then a seek past the buffer: the player stayed buffering for as long as it was watched (150 s), and stopping it was clean and kept the position; nothing reports this as a failure. Reachable again: available, playback resumed where it was, no sign-in | Done; the silent case is now built as decided (DECISIONS.md, 2026-10-16) and tested against the real engine with a connection held open (`SilentSourceTest`) |
| Real server, silent stream, 2026-10-16/17 | The shared player session and the real desktop engine (headless libmpv, no window, a small engine cache so the buffer ran out in seconds) through a relay that could hold the stream's connection open and silent while the server's API still answered, or silence everything. Played from the buffer, then waiting shown; the stated failure ("stopped sending", Try again, the way back) after 30.0 s of waiting each time; the source checked and still online; data returning on the held connection cleared the failure and playback went on; Try again played again in under a second from the position kept, in the same engine; Reflux's watch state never completed and no play counted. Found and fixed: the check after the failure rescanned the server, which ended the playback's session there and counted a second play on the server (now only availability is checked; rerun: the server's count stayed at one through the failure, the check, and Try again). Not completed because the server went off the network during the run: leaving a failed playback, the whole server silent, the near-end and first-minute Try again, and Android against this server; a longer run's second play on the server was traced (2026-10-17, the relay logging each session call) to the library's first refresh, which had read the server just before Play and delivered a position as a stop two seconds into the playback; positions brought up to date by a scan now leave an open playback's session alone (WATCH_STATE.md), and the same run repeated opened one session and counted one play (DECISIONS.md, 2026-10-16) | Partly done; open |
| Real server, near the end, 2026-10-17 | Played past 90%, back to thirty minutes, and left: the server had marked the film played from a progress report alone, and the next scan marked it watched in Reflux. Fixed: positions told to the server stay short of its own threshold until a playback finishes, which is reported as the end (DECISIONS.md, 2026-10-17). Rerun: the server held 85% while the playback was near the end and stayed unplayed; after leaving at thirty minutes, Reflux and the server both had thirty minutes, unwatched, through a rescan, in one session; a playback run to the end of the file was watched in Reflux and played on the server with one play | Fixed, rerun against the server |
| Stress testing, 2026-10-17 | Deterministic fuzzing and race tests across identification (30,000 generated names), scanning (3,000 films; repeated, partial, and interrupted scans), watch-state reconciliation (randomized sequences against a server modeled on the real one), the playback session (randomized engine and viewer events), and Best Version (40,000 random cases); the shared UI rendered offscreen with worst-case real titles at desktop, phone, tablet, and television sizes. Found and fixed: identity keys that dropped every non-Latin letter (unrelated works of the same year merged, 2026-10-17 in DECISIONS.md); a folder a scan could not list removed its works (DECISIONS.md, 2026-10-17); a source not checked yet ranked a playable copy behind an undecodable one (WATCH_STATE.md); a source removed during its scan left works behind; the engine's diagnostic log recorded a server's access token (now redacted, confirmed against the real server); long titles, episode names, and the player's failure card on a phone. On the phone emulator, the regression test for an unreadable folder and the rest of the instrumented tests pass | Done |
| Found by the live run and fixed | A film's only subtitle, marked default but not forced, stayed on although its English audio was understood: mpv published the tracks before saying which it had chosen, so the default track selection was decided against no selection (`MpvEngineTest` reproduces it with a generated file) | Fixed in `playback:mpv`, rerun against the server |
| Found by the live run and fixed | With the server cut off, the stream running out before the end of an episode was reported by mpv as the end of the file, and the episode was recorded as watched (completed, a play counted, the position cleared, "played" pending for the server). An end more than 15 s before the running time is now the media becoming unreachable, and the position reached stays (`MpvEngineTest` reproduces it with a cut-off file) | Fixed in `playback:mpv`, rerun against the server |
| Android, real server, cut off mid-playback | On the phone emulator (Android 16), the engine with Media3 and the application's own playback controller, connected to this server through the same forwarder, 2026-10-16. Sign-in, a scan, playback, resume at the same moment, and progress reaching the server; an episode played to its end counted as watched and reached the server as played. Connections closed cleanly or reset during playback, or before a seek past the buffer: Media3 reports the source as unreachable (after what it had buffered, when playing on), the position reached is kept and is not counted as watched (a stop past 90% of the running time still is, as everywhere), and playback resumes there once the server is back. A server that stops answering: Media3 played on from its buffer and then, through its own read timeout, reported the source as unreachable, about a minute after the silence began. A file cut off before its stated running time, served whole by a local web server: reported as unreachable, never as ended. No change was needed on Android | Done; Media3's own failure now reads as the source having stopped, with Try again, through the shared player (DECISIONS.md, 2026-10-16); not yet rerun on the emulator |
| Not verified | The desktop application's window against this server (the machine was in use: a game during the first run, other applications during the second); Android on a real phone or television against this server; the Secret Service on Linux (the protocol-level test is no substitute) | Open |

## Source parity and library controls: what was verified

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

## Visual reset: what was verified

Verified on 2026-10-14 on Windows 11, on the tree that closes the visual reset (`design`, the experience layer, and the shells' icons; no navigation, playback, source, or library change).

| State | What | Result |
| --- | --- | --- |
| Implemented | The full check: 516 tests, 0 failures, 9 skipped (the environment-gated live-server, real-window, and physical-controller tests), including new tests that hold every chrome token to no hue, the primary action and progress to their contrast, the artwork atmosphere to a readable dark tone, and versions to leading with what they are chosen by | Done |
| Implemented | Every screen rendered offscreen at desktop, phone, tablet, and television sizes (`-Preflux.screenshots`) and reviewed: Home with and without artwork, scrolled Home, details of a movie, a show, and a work kept offline, versions with the recommendation, the player and its lists on all form factors, first run, sources, search, the offline sheet, and controller and remote focus | Done |
| Not verified | The new look on a physical phone, tablet, or television, and in the real desktop window with real artwork (the renders use generated fixture artwork) | Open |

## Rebaseline: what was verified

Verified on 2026-10-17 on Windows 11, at `2a66250` before the rebaseline (the documentation and version changes that followed do not change behavior).

| Level | What ran | State |
| --- | --- | --- |
| Baseline | `./gradlew check` | 579 tests, none failed; 8 skipped (environment-gated: live servers, real windows, physical controllers) |
| Version single-sourced | `./gradlew printVersion`, `:android:app:assembleDebug`, the merged manifest; an invalid `reflux.version` | `0.9.0-alpha.1 (90001)`; the APK's `versionName` and `versionCode` match; an invalid version fails the build with the protocol's message |
| Not verified | Anything on a device, a live service, or GitHub | Unchanged from the sections above |

## First M1 slice ("From the start", version pinning): what was verified

Verified on 2026-10-17 on Windows 11, on the tree that closes the slice.

| Level | What ran | State |
| --- | --- | --- |
| Rules | `WatchRulesTest` (starting over keeps the place through the first minute, then moves it; a finished work stays finished), `HandoffTest` (the same-timeline case now keeps the place) | Passed; the new tests failed before the fix |
| Library, with a server modeled on the real Jellyfin one | `ServerPlayedThresholdTest`: "From the start" and leaving after 25 s keeps 30:00 in Reflux, in Continue Watching, and on the server, through the next scan; watching on past the first minute is the new place everywhere. `OfflineTest`: pinning the copy on the device pins its origin, and the choice outlives the copy | Passed; the server test failed before the fix |
| Experience layer | `VersionChoiceTest`: pin and unpin by pointer on a desktop window, beneath the version on a phone (412 dp), and with remote keys alone on a television; the renderings reviewed (cards aligned, choice within the screen, focus ring on the choice) | Passed |
| Full check | `./gradlew check --rerun-tasks`, three times | 587 tests, 8 skipped (environment-gated). Twice one failure, `SourcesUiTest.theRemoteChoosesLibrariesToo` timing out waiting for focus on the libraries page; once none. That test passes alone and in the `ui` suite alone (151 tests, none failed), and the pre-slice tree passed one full run; the page offers its first focus only once its libraries arrive from a background load, which the test races under load. The page is untouched by this slice; recorded as an intermittent test to fix (ROADMAP.md, M2, CI) |
| Not verified | Against a real Jellyfin server (the model stores a short stop as it is; a real server's handling of it is not exercised); on a device or emulator; by hand in the desktop window | Open |

## Watch Next and Fire TV evaluation (second M1 slice): what was verified

Verified on 2026-10-18 on Windows 11 and the `Reflux_TV` emulator (Google TV image, Android 16, API 36, x86_64), on the tree that closes the slice.

| Level | What ran | State |
| --- | --- | --- |
| Rules and planning, with a real library | `WatchNextTest` (24): films and episodes continuing from their place, the next episode under the show's name, a barely begun episode as the next one, finished works and shows leaving, the row in Continue Watching's order and limit, updates in place and nothing written when nothing changed, the next episode taking the finished one's place across seasons, a rewatch, marking watched, a stale launcher position replaced and never adopted, an away source keeping its work, a removed source removing it, one work across two sources as one program with one place, a removal on the launcher deleted and kept off until played again, a dismissal forgotten once finished, duplicates and unknown rows, artwork choice | Passed |
| Experience layer | `ContinueFromOutsideTest` (4): a film from the launcher plays from its place over its details page, the next episode from its start, what was playing stops first, a work no longer there says so | Passed |
| Android unit | `WatchNextLinkTest` (2) | Passed |
| Full check | `./gradlew check --rerun-tasks`, once | 617 tests, none failed, 8 skipped (environment-gated). `SourcesUiTest.theRemoteChoosesLibrariesToo` passed in this run |
| Android lint | `:android:app:lintDebug` | Passed. The TV provider builder's setters are reported as restricted (they are declared on base types the library marks as its own; the Watch Next guide calls them the same way); suppressed on the two functions with that reason |
| Google TV emulator, instrumented | `WatchNextOnDeviceTest` (3) through the real TV provider, run as the application: a film added with its position and running time, unchanged on a second run, updated in place (same row), removed when finished; a show's next episode as `NEXT` / `TYPE_TV_EPISODE` with its season and episode, removed when the show is finished; the program's intent opens `MainActivity` on the work; artwork offered only once cached and readable through the application's provider | 3 passed, none skipped |
| Google TV emulator, by hand in the installed application | A film played past the first minute and left with Home (position 1:41 recorded, no Watch Next error reported); the program's intent sent from the launcher with Reflux in the background: the film played from its place, one Back showed its details page, a second Home; the same intent with the process killed (cold start): played from its place; `ArtworkProvider` read from the shell's uid: a cache file served, `..%2Flibrary.db` and `device-id` not found | Done, with screenshots reviewed |
| Found by the emulator and fixed | Inserting programs is refused without `WRITE_EPG_DATA` (it had been removed with `READ_EPG_DATA` on the assumption that neither was needed); restored, and reading back Reflux's own programs works without `READ_EPG_DATA` | Fixed |
| Not verified | What Google TV's launcher draws: on this image the launcher shows a Google sign-in before its home (not done: no account was added), and Google TV shows only certified Watch Next integrations; the user removing a program on a real launcher (the planning is tested with the provider's hidden-row contract, not with a launcher); a physical Google TV or Android TV device; the shell's 2-second debounce and leave-time publish beyond the one manual run; Fire OS behavior of the TV provider | Open |
| Fire TV devices | `adb devices` on 2026-10-18 listed no device (neither Fire TV nor the phone), so the developer's Fire TV Stick 4K and Fire TV television were not inspected; nothing about their model, OS, or API level is assumed | Open |
| Amazon documentation read (2026-10-18) | Watch Activity (Fire TV Integration SDK, Content Personalization): requires catalog integration and catalog content IDs; SDK distributed as a jar. Fire OS versions: Fire OS 6 = API 25, 7 = 28, 8 = 30, 14 = 34, 16 = 36. Identification: `adb shell getprop` (`ro.product.model`), `adb shell pm list features` (`amazon.hardware.fire_tv`) | Recorded in DECISIONS.md and ROADMAP.md |
