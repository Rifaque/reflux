# Reflux Verification Log

What was run, on what, and with what result. This is history, not plan: the product roadmap is [ROADMAP.md](ROADMAP.md), and what a release still needs is [RELEASE.md](RELEASE.md).

Rules for adding to it:

- One section per milestone or validation cycle, dated, on the tree it names.
- Say what ran (command, device, server, by hand or automated) and the result, including what was **not** verified and why.
- A result from an emulator, a container, or a fixture is never recorded as a physical-device, live-service, or real-hardware result.
- Do not edit an old section to make it current; add a new one. Test counts in old sections are counts at that time.
- Milestone labels are those of the day a section was written. Before 2026-11-02, "M2" means release engineering (now M6) and "M3" real-world validation (now M7); ROADMAP.md, "Earlier numbering", maps every one.

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

## Fire TV smoke test on physical hardware: what was verified

Verified on 2026-10-18 on the developer's Fire TV Stick over adb (Wi-Fi), with the debug build of the tree that closes this cycle, and on Windows 11. This is a smoke test of the existing Android application on Fire OS, not an Android TV or Google TV verification, and not a release-build verification.

| Level | What | State |
| --- | --- | --- |
| Device, from the device itself | `AFTMA08C15` (Amazon's Fire TV Stick 4K Plus, 2025), `karat`, MediaTek MT8696; Fire OS 8.1.8.5 (RS8185/3891), Android 11, API 30, `user` build; userland `armeabi-v7a` only (32-bit); 1.7 GB of memory, about 390 MB available while idle; display 3840×2160, the application laid out at 1920×1080 and 320 dpi (960×540 dp, as the Google TV emulator); HDR10 reported (`HdrCapabilities` type 2); features `android.software.leanback`, `leanback_only`, `picture_in_picture`, `amazon.hardware.fire_tv`; no Google Play services; Android's TV provider present (`com.android.providers.tv`) | Recorded |
| Installation | `adb install` of the debug APK: installed, `primaryCpuAbi=armeabi-v7a` (the one native library ships for it), `WRITE_EPG_DATA` granted at install | Passed |
| Launcher, physically on the device | Reflux appears in "Your apps and channels" and starts from there with the remote. The tile is a generic picture, not the banner (Fire OS 8 with a build from outside the Appstore; LAUNCH.md) | Passed; tile noted for M2 |
| Remote navigation, physically | First run, the system's storage question, Reflux's folder chooser down to a folder, Home, Movies, Shows, Search, Media sources, Add media, the Jellyfin form, details pages of a film and two shows, Back at every level (focus returns to where it was), the player's panel, fast-forward and rewind keys (±10 s), play/pause key | Passed after the fixes below |
| Playback, physically | 1080p30 H.264 with a sidecar SRT (listed and readable: Fire OS 8 reads sidecar files); 720p H.264 episodes, next episode; 4K60 HEVC Main10 in Matroska with two audio tracks on the hardware decoder (`OMX.MTK.VIDEO.DECODER.HEVC`) at 60.001 fps, 52 frames dropped of about 2,400; playing to the end returns to details, marked watched | Passed |
| Lifecycle and media session, physically | The media session: active while playing, media-button session, `USAGE_MEDIA`/`CONTENT_TYPE_MOVIE`, title and position, released when the player closes (no session, focus, or wake lock left). Home while playing: paused, the place kept, decoders and audio focus released (resource manager: 2 codecs while playing, 0 in the background), back on return, paused at the same frame. Back out of the player and out of the application | Passed after the fixes below |
| Instrumented suite on the Fire TV | `am instrument` of the whole debug test APK: engine on Android's SQLite, shared parsing, storage going and coming, continuity with a server, Media3 playback, the new standby test, a folder chosen by path on a television, the device's capabilities, Watch Next (added, updated, removed, artwork readable) | 21 tests: 18 passed, 3 skipped (they need a live Jellyfin server); none failed |
| Full check, Windows 11 | `./gradlew check --continue` | 621 tests, none failed, 8 skipped (environment-gated); Android lint and `checkDebugPlatformApi` passed |
| Found on the device and fixed (DECISIONS.md, 2026-10-18) | 1. `NoSuchMethodError` for `Stream.toList()` in artwork prefetch (`PlaybackOnDeviceTest` failed on the device); a scan of the package found `Path.of` and `URLDecoder.decode(String, Charset)` as well. Fixed, and `checkDebugPlatformApi` added: it failed with `Stream.toList()` put back, and passes now. 2. Glass panels showed the sharp page through them (no blur before Android 12): `GlassTest`, which fails without the fix. 3. Search began with a stray "a" from Fire TV's keyboard: `KeyboardTest`, which failed without the fix; checked on the device, a search for "mountain" finds Mountain Light. 4. Home while playing left a frozen picture-in-picture window over the Fire TV home screen, because Fire OS pauses the session on Home (diagnosed with a temporary log of the pause's origin): `TelevisionTest.leavingATelevisionPausesInsteadOfFloating`, and on the device no pinned window. 5. A hardware decoder and audio focus held while stopped in the background: `PlaybackOnDeviceTest.leavingTheAppLetsGoOfTheDecodersAndKeepsThePlace`, run on the Fire TV | Fixed |
| Watch Next on Fire OS | Programs are written and maintained in the TV provider (the instrumented test above); Fire TV's own "Continue watching" row did not show any after several Reflux playbacks | Recorded (DECISIONS.md, 2026-10-18) |
| Seen, not changed | With Reflux in the background, the remote's play key reaches its media session and resumes the sound with nothing on screen (true on every Android device; not new; since fixed, "Fire OS Watch Next and background Play" below); Back from the search field puts focus on "Media sources" rather than on Search; the focus glow's soft shadow shows banding rings on this device | Open, for later decisions |
| Not verified | The developer's Fire TV television (not reachable); a release build (R8, signing); Jellyfin, SMB, or WebDAV from the device; a USB drive; HDR or Dolby Vision output to a display; audio passthrough; sleep and wake; a controller on Fire TV; Alexa voice commands; Amazon's own test criteria; a long session's thermals | Open |

Lightweight performance observations (debug build, so start-up and Compose are slower than a release build will be; for the performance work, not a baseline):

| What | Observed |
| --- | --- |
| Cold start to first frame (`am start -W`, three runs) | 5.14–5.22 s |
| Memory after start | 96 MB PSS, 156 MB RSS (Java heap 11 MB, native 7 MB, graphics 11 MB); about 150 MB resident while browsing |
| CPU while browsing (`top`, 2 s samples, 100% = one of four cores) | About 0% at rest (no continuous animation); short peaks of up to 54% on switching sections |
| Library from 7 files | On screen 5 s after choosing the folder |
| 4K60 HEVC Main10 | Reflux 31% of a core steady (55–83% in the first seconds), `media.codec` 12%, `surfaceflinger` 11%; first frame 776 ms after the decoder started; 40 of the 52 dropped frames in the first 0.8 s, the longest run 18 |
| Rendering | No stutter seen while moving focus; artwork was the generated treatment only (no TMDB credential on this machine, no sidecar images in the test media), so artwork loading was not exercised |

## Identify: what was verified

Verified on 2026-10-18 on Windows 11, on the tree that closes this cycle.

| Level | What ran | State |
| --- | --- | --- |
| Engine | `IdentifyTest` (4): an ambiguous film (two "Dune") corrected with its two versions, watch position, favorite, and pinned version kept, surviving a rescan, nothing left under the old identity; undo restoring the identity its file name gives, with its history and favorite, and staying undone after a rescan; episodes without a show name as one show per folder and per source (two sources of the same name stay two shows), all weak; identifying one folder's show leaving another folder's alone, and undoing it. Removing the carrying of user state made the first fail (a favorite lost) | Passed |
| Experience layer, fixtures | `IdentifyUiTest` (6) on the offscreen desktop renderer with a fixture catalog: from a details page to results told apart (year, summary, current match), small result posters, choosing, the corrected page in place of the old one and Back past it; one catalog request for a repeated query; nothing found; the catalog out of reach and "Try again"; Cancel changing nothing; undo; a weak show's prompt and its identification as a real show with its episodes' titles; no Identify without a catalog; a television with remote keys only, from the search field down the results to a choice. `IdentifyQueryTest` (2). `TmdbProviderTest` for the result poster size. Renderings reviewed | Passed |
| Full check, Windows 11 | `./gradlew check --continue` | 633 tests, none failed, 8 skipped (environment-gated); Android lint and `checkDebugPlatformApi` passed |
| Fire TV, physically | With the debug build (no TMDB credential, so Identify is not offered): an episode at the top of the source and two in a `TV` folder appeared as two shows, "RefluxSmoke" and "TV", beside the named ones. The details page was not opened on the device: the device was taken over by someone using it (another application was in front) and was left alone | Partly done |
| Not verified | Identify against live TMDB (no credential on this machine, M3); Identify driven on a device; the Android phone and Google TV emulators (not run in this cycle) | Open |

## Fire OS Watch Next and background Play: what was verified

Verified on 2026-10-18 on Windows 11 and on the developer's Fire TV Stick (`AFTMA08C15`, Fire OS 8.1.8.5) with the debug build of the tree that closes this change.

| Level | What ran | State |
| --- | --- | --- |
| Unit | `BackgroundTest` (4): out of sight, every command of the system's controls that starts or moves playback (play, prepare, play-when-ready, seeks, next and previous) reaches neither the engine nor Reflux, while pause and stop do; in sight they all do; the same controls follow the app in and out of sight; Watch Next is written with the TV provider and not on Fire OS | Passed |
| Instrumented, on the Fire TV | `WatchNextOnDeviceTest.fireOsKeepsNoProgramsInTheTvProvider`: the publisher is off, a work in progress writes nothing to the provider, and the work is still in Reflux's watch state and Continue Watching (the three publishing tests skip themselves on Fire OS). `PlaybackOnDeviceTest.leavingTheAppLetsGoOfTheDecodersAndKeepsThePlace`, changed: after standby, Reflux's play and a media key (`AudioManager.dispatchMediaKeyEvent`, PLAY) leave no decoder held, the player paused, and the place unmoved; after wake, Play plays | 5 run: 2 passed, 3 skipped as intended; none failed |
| The real application, on the Fire TV | Opened on a film from its Watch Next link and playing (session state 3, audio focus held, a decoder held); Home: paused, 0 decoders, no audio focus; then the remote's PLAY, PLAY_PAUSE, and FAST_FORWARD keys (`input keyevent`), 3 s apart: still 0 decoders, no focus, session position unchanged (10,959 ms); back to Reflux: decoder back, paused at 10,959 ms; PLAY then resumed it | Passed |
| Not verified | A phone or tablet with a headset or Bluetooth play button (the same session code; not run on the phone emulator in this change); Google TV emulator runs of the changed tests | Open |

## An episode's versions: what was verified

Verified on 2026-10-18 on Windows 11, on the developer's Fire TV Stick (`AFTMA08C15`, Fire OS 8.1.8.5, physically, with the remote driven over adb), and on the Google TV emulator, with the debug build of the tree that closes this cycle.

| Level | What ran | State |
| --- | --- | --- |
| Engine | `EpisodeVersionsTest` (5): an episode's versions from two sources, ranked by the unchanged Best Version and in the same order every time; its pin, which is the episode's alone, honored by playback planning, kept across a rescan, and given back; a pinned version whose source is away set aside by availability and kept, and played again when the source returns; another cut of an episode (another running time and "Extended") on another timeline, ranked last while the ordinary cut is resumed and played when starting over; one version, nothing to choose. `OfflineTest.anEpisodesCopyOnTheDeviceIsTheVersionItWasMadeFrom`: a copy and its origin are one choice, pinning the copy pins the origin, and the pin outlives the copy. `IdentifyTest`: an episode's pin follows it when its show is identified and undone (fails without `movePinsWithTheirVersions`) | Passed |
| Experience layer | `EpisodeVersionsUiTest` (7) on the offscreen renderer: only an episode with several versions offers them, and a season without any keeps its rows; the episode's page (number and title, "All episodes", no Identify), choosing and giving back, Play from the show's page playing the pinned copy; beneath the row on a phone; a remote alone from the show's row to the versions, a gap of at least 10 dp between a focused choice and the row, pinning, focus following the chosen version (fails without keying the rows), and Back to the row's choice; Back leaving the pin unchanged; a pin that this device cannot play and one whose source is away, set aside, said, and kept; the launcher's episode opening its own page beneath the player. `VersionChoiceTest` and `OfflineUiTest` unchanged and passing. Renderings reviewed (desktop, phone, television) | Passed |
| Full check, Windows 11 | `./gradlew check --continue` (before the focus-keying fix and its assertion, which were run on their own afterwards with `EpisodeVersionsUiTest` and `VersionChoiceTest`) | 650 tests, none failed, 8 skipped (environment-gated); Android lint and `checkDebugPlatformApi` passed |
| Fire TV, physically | A 1080p HEVC copy of an episode and a twice-as-long "Extended" copy of another were added to the device's test library. With the remote: Shows, the show's page with "2 versions" beside those two rows only, the row and the choice focused separately and clear of each other, the episode's page (1080p HEVC recommended, 720p H.264 below), "Always play this" on the 720p copy (stored in `version_preference` for that episode only), Back to the row's choice; Play on the episode's page started the H.264 decoder (`OMX.MTK.VIDEO.DECODER.AVC`), the pinned copy, not the recommended HEVC one; "Let Reflux choose" and pinning again; the other episode's page saying "Another cut, 1m" on the copy that is not the recommended one's cut. Found on the device and fixed: after pinning, focus stayed in its place on the list, now another version's "Always play this" (a second press would have moved the pin); it now goes with the chosen version (rebuilt and checked again on the device). The pin was given back afterwards | Passed after the fix |
| Fire TV, performance glance (debug build, not a baseline) | 136 MB PSS on the episode's page, 144 MB while playing; 0.0 to 0.3% of a core at rest on the page; cold start 5.8 s with a 10-file library | Recorded for the performance work |
| Google TV emulator (API 36), not hardware | `WatchNextOnDeviceTest`: the three publishing tests passed (programs added, updated, removed, artwork readable), so Android TV and Google TV still publish; the Fire OS test skipped itself. `PlaybackOnDeviceTest.leavingTheAppLetsGoOfTheDecodersAndKeepsThePlace` skipped itself (the emulator's resource manager lists no decoders). The emulator process then crashed (exit 139, nothing in its log) twice in a row as the app opened a show's page, after a second copy of an episode had been added; the same page renders offscreen and runs on the Fire TV. Not resolved | Partly done; crash open |
| Not verified | Episode versions on a phone or tablet emulator, or with a pointer on a real desktop window (offscreen renderings only); a Jellyfin, SMB, or WebDAV episode in several copies on a device (covered by the engine test with a catalog source); an episode's copy kept offline on a device | Open |


## Google TV emulator crash on a show's page: triage

Done on 2026-10-18 on Windows 11 with the `Reflux_TV` AVD (Google TV, API 36, `BT2A.260319.001`, 2 GB guest RAM, `-gpu swiftshader_indirect`), Android Emulator 37.2.12.0, and the debug build installed in the previous cycle. Time-boxed.

| What ran | Result |
| --- | --- |
| The show's page (`Signal Fires`, two episodes in two copies) opened by remote keys right after boot | Rendered; no crash |
| A copy of an episode added to the show's folder (`adb shell cp`, media scan), then Back, 15 s, and the show's page again | The emulator process died with exit 139 (SIGSEGV) as the page opened, the third time this sequence has done it |
| The same sequence after a restart, with another new copy; and the show's page opened and closed eight more times, scrolled into the episodes each time | No crash; the new copies listed ("2 versions" on every episode) |
| Evidence | The host process segfaulted (`Segmentation fault ... EXIT 139` from the shell); the emulator's verbose log ends without an error; no crashpad report was written (`emu-crash-37.2.12.db/reports` empty). In the guest, the last log lines before the host died show nothing from Reflux beyond normal rendering: no `FATAL EXCEPTION`, no ANR, no native crash, no tombstone, no dropbox crash entry; the next boot's `SYSTEM_LAST_KMSG` is clean and gives "reboot" as the reason, i.e. the guest was stopped from outside |

Attribution: **the emulator, not Reflux.** The process that crashed is the host's emulator (qemu with the SwiftShader renderer), which a guest application cannot legitimately crash; the guest shows no fault of its own; it is intermittent (one of two attempts with the same trigger, none in ten plain openings); and the same page works on the physical Fire TV and renders offscreen. What the trigger has in common each time is the app scanning and probing a new file while it draws the page, which points at load on the emulator's software renderer rather than at anything the page does. No workaround was added. The emulator stays usable for this page; a crash there is to be retried before it is taken for a regression, and a physical Google TV device (M3) is the real check.

## Smart Offline complete (episodes, seasons, Downloads, desktop): what was verified

Verified on 2026-10-18 on Windows 11, in the `tools/ci` Linux container, on the developer's Fire TV Stick (`AFTMA08C15`, Fire OS 8.1.8.5, physically, driven with remote key events over adb), and on the Google TV emulator, with the debug build of the tree that closes this cycle.

| Level | What ran | State |
| --- | --- | --- |
| Engine | `OfflineTest` (20, 4 new): a season is every episode of it, watched ones included, and an episode kept on its own is not planned or queued again; a show of 30 episodes with 3 kept is 3 of 30, never complete; keeping a season again picks up a paused copy and two that failed with their source away, which the planner had counted as on their way; planning, making, pausing, failing, picking up, and removing copies of a season and a film leaves every watch state as it was and says nothing to the server. The existing 16 (copies as versions, playback with the source away, pins, cuts, resume of a broken transfer, restart, subtitles, removal) unchanged and passing. `OfflinePlannerTest` unchanged: a pinned version that cannot be had now still gives way, as Play does | Passed |
| Desktop wiring | `DesktopOfflineTest`: a season from a share kept in the real `DesktopEnvironment`'s data directory byte for byte, and at the next launch (the share's kind not even known to it) both episodes play from their copies inside that directory and nothing is planned again | Passed |
| Experience layer, offscreen | `DownloadsUiTest` (6): an episode from its row's mark and from its own page, with no "Next episode" label, the show then "1 of 6 episodes on this device" and not "Downloaded", the season "1 of 4 episodes"; a whole season ("Download 3", nothing downloaded twice), the show "4 of 6", then "All 6" and "Downloaded" after the other season, and "Remove all" taking only the copies; the Downloads page empty, then with a film under way, paused, an episode that did not finish and its retry, the film continued and finished, removal asked once, a line opening the episode's page and Back to the page, the navigation's button leaving it; a phone (season action beneath the tabs, marks at the rows' ends, the page's actions on screen); a television with remote keys only (the mark a step right of the row with a gap of at least 20 dp and within the row's height, the sheet with focus on its confirmation, focus back on the mark through "Downloading" to "is on this device", Up from the mark to the season's action and Down back, Back closing the sheet with nothing kept, the Downloads page starting on its first copy, removal starting on Keep, focus on the next copy after a removal); removing a copy from inside the sheet with a remote keeps focus in the sheet. `OfflineUiTest` (4) updated for the page. Renderings reviewed (desktop, phone, television) | Passed |
| Full check, Windows 11 | `./gradlew check --continue` | 663 tests, none failed, 10 skipped (the 8 environment-gated ones of before, and the two new gated ones: `OfflineWindowTest` and the SMB live test); Android lint and `checkDebugPlatformApi` passed |
| Desktop, real window, Windows 11 | `./gradlew :desktop:app:test -Preflux.windowTests=true --tests "*OfflineWindowTest*"`: in the real window, with the shell's own input path, Shows, the show, "Download season", "Download 2", "Season on this device" and the show "Downloaded"; the Downloads page from the navigation; the share made unreachable and seen as away; the first episode's line to its page and Play, libmpv playing it from the copy inside Reflux's data directory past 1.5 s; removing the second copy from the page. The workstation was locked during the run, so the screen captures showed the lock screen and were deleted: this run proves behaviour, not looks | Passed (11.6 s) |
| Desktop, real window, Linux (`tools/ci` container: Xvfb, openbox, xcompmgr, libmpv) | Every window test once with captures. Passed: browsing with a mouse and a controller, first run, the silent-source tests, the shell tests. Failed: `OfflineWindowTest` and `EmbeddedPlaybackTest`, both at the same step: a show's card that is listed on screen ("Harbour Lights") is never located for the click (`Timed out waiting for "Harbour Lights" to be shown`). `EmbeddedPlaybackTest` fails the same way on `a48cd67`, the commit before this cycle, so it is not Smart Offline's; the cause is not yet known. Smart Offline therefore has no Linux real-window run | Partly done; pre-existing Linux failure open |
| Fire TV, physically | `OfflineOnDeviceTest` with the installed debug build (3 run: the copy kept, played with Media3 without its source, and outliving a restart passed; the two server-gated tests skipped themselves). Then the real application with the remote, against a Samba share on this machine (`dperson/samba` in Docker, reached at `192.168.1.5:1445` over the LAN) with a 3-episode show and a film: the share added from the application; the Downloads button appearing in the navigation only then; the show's page with a mark beside each episode, focused a step right of its row (44 px clear) and under "Download season"; the episode's sheet ("Night Watch · S1 · E1", "720p · 9 MB · from Movies on 192.168.1.5", focus on Download, nothing called "Next episode"); focus back on the mark through "Downloading" to "is on this device"; the show "1 of 3 episodes on this device" with its action unlit; Up to "Download season", its sheet proposing the two others and listing the first as kept, then "Season on this device" and the show "Downloaded · All 3 episodes on this device"; the Downloads page from the navigation, starting on the first copy, removal a step right; the share stopped, the episode's page from its line, and Play: the session playing at 3.9 s on `OMX.MTK.VIDEO.DECODER.AVC` from the copy (`rw-------` in the application's own `files/downloads`); removal asked once with focus on Keep, and focus on the next copy afterwards (after the fix below); the film's download cut by stopping the share 2.9 MB into 36 MB (FAILED, INTERRUPTED, its part kept, "The connection was lost" under "Didn't finish"), retried by remote with the share back ("Downloading · 97% · 34 MB of 35 MB") and finished byte-identical (SHA-256 equal to the original). Everything added was removed afterwards (copies from the page, the share from Media sources) | Passed after the fixes below |
| Found on the Fire TV and fixed | After a removal on the Downloads page focus went up to the navigation's Back: it now moves to the neighbouring copy (rebuilt and checked again on the device; `aRemoteReachesEveryDownloadControlAndFocusComesBack` asserts it). A reachable SMB share read as away on every other 30 s check, because the connection kept between checks went stale while idle (here through Docker's port forwarding) and still said it was connected: the works showed "Not connected" and the planner refused with "Its source isn't connected". The share now reconnects once before it is called away (`fix(smb)`); after the fix the share stayed available on the device for the whole run. A gated live test (`SmbSourceLiveTest.aConnectionThatWentStaleIsReplacedNotTakenForTheShareBeingAway`, relay in front of Samba stalling the open connection) fails without the fix ("expected AVAILABLE but was UNAVAILABLE") and passes with it | Fixed |
| Found on the Fire TV, not fixed | **WebDAV cannot be added on Android**: Android's `HttpURLConnection` refuses `PROPFIND` ("Expected one of [OPTIONS, GET, …] but was PROPFIND"), so connecting a WebDAV share fails with "Nothing answered at that address" on every Android device; the desktop's JDK client allows it, which is why it passed there. Recorded in ROADMAP.md (M1) as needing its own decision. (Fixed 2026-10-19, with a second cause found behind it: "Web shares (WebDAV) on Android" below.) Also seen, unrelated to downloads: closing the Fire OS keyboard with Back moves focus one field up (and from the first field to the navigation); after Remove on a source's row the confirmation's buttons are not focused (focus goes to the navigation); right after a share is added, a show's page can say "Not connected" until it is opened again, because a source's availability changing does not reload the page | Open |
| Google TV emulator (API 36), not hardware | `OfflineOnDeviceTest`: 3 run, the copy test passed. The application on a library of local files only: the show's page offers no download mark, no "Download season", and no Downloads button (nothing there can be kept), and its episode versions are as before. The emulator did not crash this time | Passed |
| Not verified | A download from Jellyfin or SMB on a phone, tablet, or the Google TV emulator (the same code as on the Fire TV; only engine tests and local fixtures there); WebDAV downloads on Android (they cannot be connected, above); a download continued after the Android system kills the application (restart path covered by tests); pause on the physical device (covered offscreen and in the engine); large files, metered or slow networks; the desktop with a real Jellyfin, SMB, or WebDAV server (engine and window tests use a share of local files) | Open |
| Performance glance (not a baseline) | Transfers from the LAN share ran at about 9 MB/s on the Fire TV; progress is written to the database once per MB. While a download runs, only what shows downloads reloads (every 500 ms at most), and the progress ring and bars move only with progress | Recorded for M1.6 |

## Web shares (WebDAV) on Android: what was verified

Verified on 2026-10-18 and 2026-10-19 on Windows 11, on the developer's Fire TV Stick (`AFTMA08C15`, Fire OS 8.1.8.5, API 30, physically, driven with remote key events over adb), and on the `Reflux_Phone` emulator (API 36, Android 16), with the debug build of `847318f`. The live server was Apache 2.4.37 `mod_dav` with Basic authentication (`bytemark/webdav` in Docker, `tools/ci/share-test-servers.sh`), serving the generated film, a show in nested folders (`Shows/Signal Fires/Season 01/`), and `Amélie (2001)/Amélie (2001) #1 100%.mkv`; the Fire TV reached it over the LAN at `192.168.1.5:8098`, the emulator at `10.0.2.2:8098`.

| Level | What ran | State |
| --- | --- | --- |
| Root cause, confirmed | `WebDavOnDeviceTest.androidsOwnHttpConnectionRefusesTheListingMethod`: Android's `HttpURLConnection` throws `ProtocolException` for `PROPFIND`, for `http` and `https`. Then, with the transport in place, a scan of the live share still reported it unavailable: `aListingIsReadByAndroidsParserAndADoctypeIsRefused` (no network) failed on the Fire TV with `ParserConfigurationException: http://apache.org/xml/features/disallow-doctype-decl`. Two causes; neither seen before because WebDAV had only run live on the desktop | Both fixed (DECISIONS.md, 2026-10-18, "Web shares on Android") |
| Shared adapter, JVM | `WebDavListingTest` (13, new): the `PROPFIND` request (method, `Depth` 1 and 0, body, `Content-Type`, credentials); an empty share; nested folders; only media kept; names decoded once and encoded again (`%`, `#`, `+`, non-ASCII); a share at the server's root and an address with a slash; hrefs outside the share, `..`, `%2e%2e`, an encoded `/`, and another host neither listed nor asked (credentials never leave the share's host); a server naming itself differently (a reverse proxy) asked at the share's address; a wrong password; a broken answer at the root and below it; a document type refused, also found without the parser's help (UTF-16, lower case, no false match on an escaped name); a server error; an unreachable share. With `WebDavSourceTest` (5) and `WebDavConnectorTest` (2): 20, none failed. The two href tests fail on the code before this change | Passed |
| Android transport, JVM against real sockets | `WebDavTransportTest` (10, new): `PROPFIND` with its headers and body on the wire; the shared connector connecting, listing, and giving playback addresses through it; every connection problem told apart as on the desktop (wrong password, sign-in required, not a share, server error, forbidden, nothing listening); a broken listing; a redirect keeping method, body, and credentials on the same origin; credentials dropped on another origin; endless redirects ending after 5; write methods (`PUT`, `DELETE`, `MOVE`, `COPY`, `MKCOL`, `PROPPATCH`, `LOCK`, `POST`) refused before anything is sent; an answer over the limit refused | Passed |
| Full check, Windows 11 | `./gradlew check --continue` | 686 tests, none failed, 10 skipped (environment-gated, as before); Android lint (warnings only, none about this change) and `checkDebugPlatformApi` passed |
| Desktop regression, live | `SourcesLiveTest` against Samba and WebDAV in Docker, all three phases over one data directory: connect (the wrong-password, sign-in-required, and missing-share cases; both shares scanned into one film with two versions; libmpv opening the WebDAV version for its 40-minute running time; a copy kept), away (both servers stopped: sources kept, the copy plays), back (nothing duplicated) | Passed |
| Fire TV, physically, instrumented | `WebDavOnDeviceTest` (4, new) with the installed debug build: the root cause; Android's own XML parser reading an Apache-style listing (lower-case percent-encoding, `lp1:` property prefixes) and refusing a document type; a loopback share added through the application's own wiring, scanned, and removed; the live share connected, scanned, every film opened by the device's media framework over the share for its running time, and removed | 4 passed |
| Fire TV, physically, the application | Media sources → Add media → Network share, the address and sign-in typed with the remote; Connect: "192.168.1.5 · Network share · 2 movies · 1 show"; Movies lists Amélie and Night Drive, Shows lists Signal Fires; Amélie's page shows its running time (probed over the share) and offers Download; Play: Media3 playing (`PLAYING`, position advancing), the server's log showing signed-in range requests for `Am%C3%A9lie%20(2001)%20%231%20100%25.mkv`; the source's row shows the address and "Signed in as reflux", no password; Remove and confirm: the source and its works gone | Passed |
| Phone emulator (API 36), not hardware | `WebDavOnDeviceTest` with the live share at `10.0.2.2:8098`: 4 passed. The application by touch: the same form, Connect ("10.0.2.2 · Network share · 2 movies · 1 show"), the show's page, Play S1 · E1 from the nested season folder (`PLAYING`, picture on screen), Remove: the show gone | Passed |
| Credentials | No password or `Authorization` value in logcat or `reflux.log` on either device after these runs (searched for the password and the encoded header) | Passed |
| What the share was sent | Across every run the server's log has only `PROPFIND` and `GET` (and the two deliberate wrong-password attempts) | Read-only, as designed |
| Seen, not fixed (already recorded) | On the Fire TV, after Remove on a source's row, the confirmation's buttons are not focused (focus goes to the navigation's Back); closing the Fire OS keyboard with Back moves focus one field up | Open, as in the Smart Offline section |
| Not verified | `https` shares (no TLS server was set up; OkHttp's TLS and Android's network security policy are the platform's, and `https`-to-`http` redirects are refused by the transport's own code, covered only by reading it); Nextcloud or a NAS's own WebDAV; a share needing a sign-in other than Basic (Digest is not supported, as on the desktop); the Google TV emulator (its exit-139 crash is unrelated, and the Fire TV covers the television path); a download from a WebDAV share on Android; a share with thousands of files | Open |
| Performance glance (not a baseline) | The whole test share (6 folders) was listed within one second on the Fire TV, one `PROPFIND` per folder. Connecting sends a depth-0 `PROPFIND`, and a refresh sends another for availability before listing: two round trips that a later pass could fold into one. Probing each file sends several `GET`s (the system's retriever and Media3: a full-file request abandoned, then ranges) | For M1.6 |


## Removing a Jellyfin server signs its session out: what was verified

Verified on 2026-10-19 on Windows 11 (JDK 21 HTTP client), against the developer's own Jellyfin server named in `~/.reflux-live/jellyfin.env` (Jellyfin Server 12.2.0, the test account; its address and credentials are not recorded here). No emulator or physical device was used in this cycle.

| Level | What ran | State |
| --- | --- | --- |
| Server behaviour, read | Jellyfin's own source (`SessionController.ReportSessionEnded`, `SessionManager.Logout` and `GetAuthorizationToken`, master branch): `POST /Sessions/Logout` needs only the session's own authorization, deletes the one device record holding the token it was sent with, and reports live sessions with that device id as ended. Signing in again from the same device revokes that user's earlier token on that device first | Basis of DECISIONS.md, 2026-10-19 |
| Jellyfin adapter | `JellyfinConnectorTest` (9, 5 new): the request (`POST` to the server's own address plus `/Sessions/Logout`, nothing in the address, no body, the session's own token and device id in `Authorization`, no other token header); 204 is ended; 401 (expired, revoked, replaced) is ended; unreachable, 500, and a 200 with a page that is not Jellyfin's are not, and the error carries no token; a connection that stays in use with the same token is not signed out, one with another token is, with the replaced token; a configuration that is not Jellyfin's ends nothing | Passed |
| Engine | `SourceConnectionsTest` (13, 9 new): removal is done (library, registry, secure storage) before the server is asked; ended with the token it was kept with, only that one; an unreachable server: removed, `UNCONFIRMED`, reported without the token; a server that stops answering mid-request: removed at once, given up after exactly 5 s of the test clock; a source already away: removed; a folder: nothing to end; watch history and an episode's position kept through removal and re-adding, which is a new sign-in the ended session has no part in; signing in again ends the replaced token and not the new one; a failed sign-in ends nothing; removing one server leaves another | Passed |
| Redirects | `JdkHttpFetcherTest` (2, new): a redirect to another host does not carry `Authorization` there; one on the same host does. The JDK client already behaved so; the test keeps it so | Passed |
| Desktop wiring | `CredentialStorageTest.removingASourceRemovesItsEntry`, now through the real `DesktopEnvironment` and HTTP client with a server that does not exist: the credential store's entry and the source gone, the sign-out `UNCONFIRMED`, the token not in `reflux.log` | Passed |
| Experience layer, offscreen | `SourcesUiTest` (11, 2 new): the removal question says Reflux signs this device out of the server; removing a connected server: its row and films gone, the folder and Heat's watched state kept, the server asked to end its own token only; adding it again works with a new token; removing a server seen as away, and one that stops answering in the middle of being signed out of: removed at once, nothing confirmed, no failure shown. `PointerTest` (folder removal) unchanged | Passed |
| Live, real server | `JellyfinSignOutLiveTest` (new, gated like `JellyfinLiveTest`): signed in through a fresh `DesktopEnvironment` (Windows Credential Manager) and as another client of the same user with another device id; Reflux's token worked and the server listed Reflux's device among the user's sessions. Removed: gone from the library, the registry, and the credential store; the server confirmed (`ENDED`). Then asked directly: Reflux's old token got 401 on `Users/Me`, the server no longer listed Reflux's device, and the other client's token still worked and was still listed. Added again: a new token, which worked while the old one stayed refused. Removed again (`ENDED`, 401), the other client signed out at the end, no token in the log. Passed three times (on its own in 1.5 s, and in each full check, which runs the gated live tests when that file exists) | Passed: server-side end directly observed |
| Full check, Windows 11 | `./gradlew check --continue` plus `:android:app:compileDebugAndroidTestKotlin` | 705 tests, none failed, 10 skipped (the environment-gated ones, as before); Android lint and `checkDebugPlatformApi` passed. The first run failed to compile the two shells (their `removeSource` now returns the sign-out); fixed before this run |
| Not verified | Android: the same shared code signs out there, but no emulator or device run was made, so `AndroidFetcher`'s bodyless `POST` and OkHttp's redirect handling on Android are covered only by reading them; a server that is off (rather than refusing) was exercised with fakes and an unresolvable name, not with a real server switched off mid-request; two accounts of one server in one Reflux (the server ends the other account's live session too, by device id, DECISIONS.md "Known limit"); older Jellyfin versions | Open |
| Logs | The server's own log records the token it revokes (Jellyfin's `Logging out access token`); that is the server owner's log, not Reflux's | Recorded |


## Desktop capabilities observed, not declared: what was verified

Verified on 2026-10-19 on Windows 11 on the developer's laptop (an Intel integrated GPU and an NVIDIA RTX 5060 Laptop GPU; two screens: a 2560x1440 monitor with HDR supported and switched off, and the 2560x1600 built-in panel), and on Linux in the `tools/ci` container (Xvfb at 1600x1000, openbox, xcompmgr, libmpv, no GPU). No Android device or emulator: this is desktop-only.

| Level | What ran | State |
| --- | --- | --- |
| Core | `PlaybackAssessorTest` (1 new): a decoder not known to be hardware or software is no compromise; where only known software fits, it is; known hardware is offered before unknown, unknown before software. `VersionSelectorTest` (1 new): a display of unknown size caps nothing. All existing core, library, and Android tests unchanged and passing with `hardware: Boolean?` and a nullable display size | Passed |
| Desktop decode and display, JVM | `DesktopCapabilitiesTest` (19, new): nothing observed is unknown, never hardware, and everything stays playable in software; hardware seen counts up to the frame seen; a depth without hardware has none deeper; a codec the engine cannot decode in software has no software decoder. Best Version: hardware for H.264 and not HEVC decides (fewer compromises); software only leaves it to resolution; unknown prefers no codec for being on a list (the larger copy wins, as for any equal pair); a hardware copy wins when all else is equal; known hardware does not outrank unknown; an 8K frame beyond the 4K the hardware took is software; a codec without hardware still plays in software; a codec with no decoder is not offered; a 4K copy wins only on a 4K display; HDR is claimed only where the display shows it and the engine sends it (today it does not, so the SDR copy wins on an HDR display, with the HDR copy marked tone-mapped). A playback in hardware proves and widens; in software where hardware was believed it disproves; beyond the frame seen it changes nothing; the engine's reports are parsed (4:4:4 ignored, a missing format ignored); `decoder-list` read without hardware wrappers or FFmpeg's hardware-only AV1 decoder; the diagnostics line names codecs, not the machine | Passed |
| Desktop engine, JVM | `ObservedCapabilitiesTest` (6, new): the display is the one the window is on and follows it between two written-down monitors (1080p SDR, 4K HDR) and back, told only of what changed; an HDR display is not claimed while the engine sends SDR; a display that cannot be read is unknown, not a guess, and a reader that fails is unknown, not an error; probe and playback evidence reach Best Version; Windows colour information is HDR only when it is HDR (automatic colour management's wide colour on SDR is not); this machine's own primary monitor read through `user32` | Passed |
| Real engine | `MpvDecodeProbeTest` (3, new): 16 bundled samples present, each under 64 KB; every probed question answered; a sample that cannot be opened leaves its question unknown. On this machine, through `d3d11va-copy` (the default adapter, Intel): H.264 8-bit, HEVC, VP9, and AV1 at 8 and 10 bits in hardware up to 3840x2160, H.264 10-bit in software; 1.9 s. First asked through `auto-copy-safe`, the probe answered H.264 10-bit "hardware" through NVDEC on the RTX 5060, which Reflux's Direct3D 11 output cannot use: that is why the probe asks only through playback's own decoders | Passed |
| Display reading cost | `WindowsDisplays` on this machine: first read 28 ms (loading `user32`'s functions), then 0.05 ms per read (100 reads) | Recorded for M1.6 |
| Windows, real window | `CapabilitiesWindowTest` (new): the window's display read as 2560x1440, HDR off, and that is what Best Version is told; moved 40 px on the same screen: read again, nothing changed; moved to the second screen: read as 2560x1600 within the settle time, and back; the probe finished a few seconds after start; "Night Drive" with an H.264 10-bit copy (15 MB) and an HEVC 10-bit copy (5 MB): the page recommends HEVC ("Plays best on this device") and marks H.264 "Decoded without hardware acceleration", decided by fewer compromises; a contrary observation fed in while the page was open: the page followed without being reopened (H.264, the larger, recommended); the HEVC copy played in the real embedded window, and the engine reported hardware decoding, which put HEVC 10-bit back to hardware | Passed (9.6 to 11 s) |
| Windows, the other window tests | `./gradlew :desktop:app:test -Preflux.windowTests=true`: browsing (mouse and controller), first run, Smart Offline, silent sources, shell, gamepad: passed. `EmbeddedPlaybackTest` failed at clicking "Harbour Lights" ("Timed out waiting for "Harbour Lights" to be shown" with it listed on screen), the step that already failed on Linux; it fails the same way on `c7cd940`, the commit before this cycle (run in a separate worktree), so it is not this change's | Passed except the pre-existing failure |
| Linux, real window | The `tools/ci` container, window tests: `CapabilitiesWindowTest` passed: the display read as Xvfb's 1600x1000 (HDR not reported: the toolkit says nothing of it), one screen (moving between screens not tested there), the probe through `auto-copy-safe` answered software for every codec and depth (no GPU in the container: truthful), the page comparison skipped as the test states. First run: `BrowsingWindowTest`'s controller test failed because the probe finished during its 1.2 s stick hold and refreshed the page (`revision` 18 to 19); the test now waits for the probe before measuring that navigation changes nothing, and passed on the rerun. `EmbeddedPlaybackTest` and `OfflineWindowTest` failed at clicking a show's card, as before this cycle | Passed except the pre-existing failures |
| Full check, Windows 11 | `./gradlew check --continue` plus `:android:app:compileDebugAndroidTestKotlin` | 736 tests, 2 failed, 11 skipped (the 10 gated as before and the new window test). The 2 failures are the live Jellyfin tests: the server named in `~/.reflux-live/jellyfin.env` did not answer (`HTTP connect timed out`; `curl` to it got no answer either), and nothing in this change touches Jellyfin. Android lint and `checkDebugPlatformApi` passed |
| Not verified | HDR output (not built: no HDR display was available with HDR on, and Reflux's engine does not output HDR; DECISIONS.md); a resolution or HDR switch in the system's settings being seen on activation (the code path is a re-read on `windowActivated`, the same read as on a move, but no setting was changed); a real Linux desktop with a GPU (VA-API or NVDEC), X11 or Wayland; a screen being plugged or unplugged; machines where Windows chooses the discrete GPU as Direct3D's default adapter | Open |
| Performance (for M1.6) | Probe: about 1.9 s of work on a background thread, 3 s after start, once per launch (about 115 ms per sample; the 1080p sample is tried only when 4K was software). Display reads: 0.05 ms, on a thread of their own, only after a move settles (250 ms), a screen change, or activation. A capability change rebuilds one `DeviceCapabilities` and invalidates the open pages once; a playback adds a change only when it teaches something new | Recorded |

## Real-window browsing: the show card, and what the harness got wrong

Investigated on 2026-10-19 on Windows 11 (125% scaling, two screens) and in the `tools/ci` Linux container (Xvfb 1600x1000), from `ff8123e`. `EmbeddedPlaybackTest` failed at clicking "Harbour Lights" on both, and `OfflineWindowTest` at opening it on Linux; both failed the same way on earlier commits. The harness now says, when a wait times out, what nodes say the text, their visible and unclipped bounds, and whether they can be clicked, and saves a picture of the window where pictures were asked for (`-Preflux.screenshots`).

| Level | What was found or run | State |
| --- | --- | --- |
| `EmbeddedPlaybackTest`: root cause | After the first playback, Home has a "Continue Watching" row under the hero, and the "Recently Added" row with the show's card moves below the bottom of an 860 px window (688 dp at 125%, 860 dp at 100%); only its heading is in view (picture taken at the failure). The card was on the page, clipped: no node says "Harbour Lights" with any bounds in the window. The test clicked without scrolling, which a person would not. Windows began failing like Linux once the window measured 688 dp tall | Harness: fixed |
| `OfflineWindowTest`: root cause (Linux) | After "Shows", three nodes said "Harbour Lights": Home's hero title (not clickable), Home's card, and the Shows card. Home was still fading out (navigation keeps the leaving page composed for its 140 ms fade, longer on the container's software rendering), and the harness clicked the first match, the title, whose middle lies just right of the Shows card: nothing opened. On Windows the geometry happened to land the click on the card | Harness: fixed; product: see below |
| Production change | The page on its way out is removed from the semantics tree as soon as navigation starts (`RefluxApp`): assistive technology no longer finds two pages at once, and the harness sees only the page being opened. Nothing changes on screen, for focus, or for the pointer. `ScreensTest.aPageOnItsWayOutIsNoLongerThereForAssistiveTechnology` holds the clock part way through a change: Home's "Recently Added" is gone and "TV Shows" is there (fails without the change: 1 node instead of 0) | Fixed |
| Harness changes | `click`/`drag` bring their target into view with the mouse wheel over the page, as a person would, each turn waiting for the page to have moved (never a fixed sleep); a clickable node is preferred over text that only says the same words; synthetic keys go to the component with the keyboard's focus (see below) | Done |
| Found while checking keyboard activation | Enter on a focused show card did nothing in the real-window test, while the same card opens with Enter offscreen (`DirectionalInputTest.enterOnAFocusedCardOpensIt`, new). Cause: the harness handed keys to the window directly, and the toolkit retargets a key to the focused component only when it comes through its own queue; Reflux's own handling (arrows, Escape) saw them, so only keys Reflux leaves to the toolkit (Enter on a control) were lost. Keys now go to the focus owner. Key location was ruled out (it passes without it) | Harness: fixed; product works |
| Production UI check | The show card is rendered, takes focus by keyboard and controller, opens with the pointer (both tests), with Enter (`BrowsingWindowTest`, new step: Shows, arrow keys to the card, Enter, the show's page), and with a controller (`BrowsingWindowTest`'s controller test, unchanged), always to that show's page | Works |
| Second, separate test race | `OfflineWindowTest` failed once in 4 Windows runs at "every episode of the show is kept, so the show is": it read the show's "Downloaded" label at the moment the season's "Season on this device" appeared; the two come from separate status objects and the show's catches up a moment later. The test now waits for it; 5 of 5 passed after | Harness: fixed |
| Windows, real window | The whole window suite: all passed (browsing with the new keyboard step, capabilities, shell, `EmbeddedPlaybackTest` 23 s, first run, `OfflineWindowTest`, silent sources, gamepad). Before that, `EmbeddedPlaybackTest` and `OfflineWindowTest` together twice and `OfflineWindowTest` 5 more times after the waiting fix: passed | Passed |
| Linux, real window | The container, window tests, 9 rounds in all with the show-card fix (3 of them with the final harness, all 31 window tests passing in each). The show-card steps passed in 9 of 9 | Passed |
| Seen once, not fixed | `EmbeddedPlaybackTest`'s missing-file step failed in 1 of 9 Linux rounds: the film's file was deleted, Resume was clicked, and the engine reported neither playing nor a failure within the test's 20 s (no "Playback failed" in the diagnostics, unlike the other rounds), while the page underneath already said the film had left the library. That step never ran on Linux before (the tests stopped at the show card), so this is newly visible, not new. Playback's own limit for a load that never starts is 30 s; whether the player then explains itself was not observed. Recorded in ROADMAP.md (M2) | Open |
| Full check, Windows 11 | `./gradlew check --continue` plus `:android:app:compileDebugAndroidTestKotlin` | 738 tests, none failed, 11 skipped (environment-gated, as before). The live Jellyfin server answered again, and both live Jellyfin tests passed: their failures in the previous cycle were the server being off. Android lint and `checkDebugPlatformApi` passed |
| Not verified | A by-hand check with a person's mouse and keyboard (the window tests drive the real window through the toolkit's input path without moving the system pointer); a screen reader actually reading a page change | Open |
| For M1.6 | The leaving page's fade (140 ms) takes visibly longer under the container's software rendering; the show's "Downloaded" label trails the season's by a moment after a season finishes downloading | Recorded |

## About, legal, and attribution: what was verified

2026-10-19, on the Windows 11 machine, from the working tree on top of `8bd4bba`.

**Automated (deterministic, no live service):**

- Full `./gradlew check :android:app:assembleDebugAndroidTest --continue`: green, 756 tests, 0 failures, 11 skipped (the same environment-gated skips as before: window, controller, and live-share tests). It ran the live Jellyfin tests against the user's server, `checkDebugPlatformApi`, Android lint, and the new `checkThirdPartyNotices` for `desktop:app` and `android:app`.
- New: `AboutUiTest` (11: reached from the media sources and back, the version from `RefluxBuild` with the recorded commit and a modified tree, TMDB's notice word for word with its logo smaller than Reflux's name and in its own proportions, nothing credited or claimed without a provider, each build's own notices, a license shown as it is, libmpv said to be not included, a keyboard in and back to each page's control, a remote paragraph by paragraph through the Apache License with every stop on screen and back to the control each page was left by, headings and button roles, the leaving page absent from the semantics tree mid-transition, every listed text present, reflowing that changes no word), `BuildIdentityTest` (core, 4), `TmdbAttributionTest` (2: the notice, and the logo's path, box, and gradient equal to TMDB's published SVG, kept as a test resource), and `BuildIdentityTest` in `desktop:app` (the commit the build recorded equals `git rev-parse`, and the modified flag `git status`).
- `checkThirdPartyNotices` was seen to fail, naming `com.google.guava:*`, with Guava's line removed, and to pass with it back.
- The configuration cache is reused after an edit: the commit is read when a task needs it, not while configuring.
- Offscreen renderings reviewed (`-Preflux.screenshots`): About at 1440x900 and 412x915, the desktop notices, Bouncy Castle's license, and the television at 960x540 reading the Apache License.

**Real desktop window (Windows):** `./gradlew :desktop:app:test -Preflux.windowTests=true`. New `AboutWindowTest`: the media sources button and About Reflux with the mouse, "Version 0.9.0-alpha.1 (8bd4bba, modified)" from the build, arrows through the page, Enter into the notices, Skia's license opened with the mouse (scrolled to as a wheel would), and Escape back page by page, the first arrow landing on the control each page was left by: passed. In the full window run the browsing, capabilities, offline, and silent-source window tests passed; `FirstRunTest` failed once with an empty first library and passed on an untouched rerun (the machine was in use); `EmbeddedPlaybackTest` failed with "the desktop is locked or something covers the Reflux window", its own message for a screen it cannot see (the machine was in use), so it says nothing about this change. Without a TMDB credential on this machine the real window showed no credit and no TMDB claim, as intended.

**Not verified:**

- Linux container window rounds: started, then stopped by Claude Code's low-memory reaper about 45 seconds in, while still compiling (0.8 GB free; the user had a game open). Not rerun.
- Google TV emulator and `AboutOnDeviceTest` on a device: not run, for the same memory reason. The instrumented test package builds.
- Physical Fire TV: the stick answered ping but refused adb on 5555 all session (network debugging off, probably after a restart).
- TMDB against the live service (no credential here; M3). Only the attribution's content was checked, against TMDB's FAQ and published logo file, read 2026-10-19.

## Reflux's ownership notice on the About page: what was verified

2026-10-19 and 20, from the working tree on top of `e41c538` (builds show `0.9.0-alpha.1 (e41c538, modified)`). This also completes the About page runs the previous section left open.

**Automated:** `./gradlew check :android:app:assembleDebugAndroidTest --continue` on Windows 11: green, 758 tests, 0 failures, 12 skipped, all environment-gated (8 real-window and controller tests without `-Preflux.windowTests`, 3 live-share tests without a share server, and a permission test that cannot run on Windows). Android lint, `checkDebugPlatformApi`, and `checkThirdPartyNotices` for both shells passed. New in `AboutUiTest`: Reflux's notice contains no open-source grant, its copyright line is shown with the build, "Copyright and ownership" is a button before "Third-party software", the notice opens and says third-party licenses are unchanged, and Reflux is not among the third-party components. `AboutOnDeviceTest` now also checks that the package carries the notice.

| Where | What | Result |
| --- | --- | --- |
| Windows, real window | The whole window suite (`-Preflux.windowTests=true`), then `AboutWindowTest` again after the change below | Passed |
| Linux, real window (`tools/ci` container) | First round: `AboutWindowTest` failed at its first arrow, then, after a first attempt at a fix, at the arrow after a page opened by the mouse; every other window test passed. Cause, in the harness: an arrow pressed while a page is still arriving finds nothing to focus yet (`FocusEntry` restores focus only to the page that has arrived), and the container's software rendering makes that window long. The test now presses the first arrow again while nothing has focus, at most every half second; the first arrow that lands must still land on the stop the page was left by. Then 2 of 2 rounds, all 31 window tests passing | Passed (harness fixed) |
| Google TV emulator (`Reflux_TV`, API 36) | `AboutOnDeviceTest` (2) via `am instrument`; by remote: Media sources → About Reflux arrives on the build and copyright stop; down to "Copyright and ownership", in, three paragraphs, Back to that row; "Third-party software" (13 components), Kotlin, the Apache License paragraph by paragraph to section 4; Back to Kotlin, the list's row, About Reflux. No crash | Passed |
| Fire TV Stick 4K Plus (`AFTMA08C15`, Fire OS 8.1.8.5, API 30), now at a new address on the LAN; adb had to be authorized again | Installed over the existing app (library kept); `AboutOnDeviceTest` (2); the same remote walk, Menu opening the media sources. No crash | Passed |
| Xiaomi Pad 7 (`2410CRP4CI`, Android 16, API 36, 3200x2136 landscape at 440 dpi), first physical tablet run | Fresh install (HyperOS needs "Install via USB" in developer options); `AboutOnDeviceTest` (2); by touch: Media sources → About Reflux, the build and copyright line, "Copyright and ownership" (the notice fits the screen), Back, "Third-party software", Kotlin, the Apache License scrolled by swipes to its end, Back through each page to Media sources. Layout: one centred column, nothing clipped or overflowing; the text scrolls under the floating navigation as on every page. No crash | Passed |

**Not verified:** portrait on the tablet (not rotated, to leave the device's rotation setting alone; portrait phone and tablet layouts are rendered offscreen by the `ui` tests); a physical phone; TalkBack or Narrator reading the pages; TMDB's credit on a device (no credential; with none the page shows no credit, as seen on all three devices).

**Seen, not changed:** an arrow pressed during a page transition is dropped rather than kept for the arriving page; the next arrow works. It predates this change and is visible only where rendering is slow. For the accessibility and performance milestones.

## Fire TV: the device-specific controls and the accessibility baseline

2026-11-01, on the physical stick: `AFTMA08C15`, Fire OS 8.1.8.5, API 30, 32-bit, at `192.168.1.176`. The debug build was installed over alpha.1 without clearing data, and driven by remote keys over adb. Its library is generated test media. This closes the Fire TV item of the closing pass above.

| What | Result |
| --- | --- |
| Upgrade | alpha.1 to alpha.2 kept the library: 2 sources, 22 items, 12 versions, 13 tracks, identical before and after by id |
| Browsing | Home, Movies, Shows, Search, Media sources, Add media, a film's and a show's page, by remote alone; each page starts on its main action or first item |
| Player, per device | Timeline, Episodes, Speed and a Paused state; no Back, volume, fullscreen, or on-screen transport buttons, and no focus stops; video fills the screen |
| Play and seek | OK pauses and resumes; Left and Right, Rewind and Fast Forward seek by 10 s, read from the media session |
| Lists | Down opens them on what is in effect; Left and Right change list, Up and Down move, OK chooses: speed 1.5 in the session, and back to 1.0; an episode chosen from the list starts |
| Up Next | Countdown, Play now and Cancel over a player that stays; the next episode followed |
| Back | One press closes a list with the session still playing; the next closes the player, with focus back on the page's main action; from a film's page, back on its poster |
| Isolation | With the player or a list open the tree holds only them; with the folder picker open, only it, focus cannot leave it, and Back returns to the row that opened it |
| Semantics | Roles, selected and checked states read from the tree; the timeline, through the Android accessibility API on the device: "Pause", "Back 10 seconds", "Forward 10 seconds" are named actions, each performed (0:13 to 0:03 to 0:13), value "0:13 of 1:30", and not focusable |
| Stability | No crash or ANR in the log |

**Found and fixed:** pressing Remove on a source took away the button that had focus, and focus fell to the navigation. It starts on Keep now, as Downloads does, and Keep returns to Remove. This was older than the accessibility work. `check`: 811 tests, 0 failures, 12 skips. Not rerun for that fix: the real-window suites and the Linux container.

**Not verified:** Up Next answered by a specific key: OK was pressed about two seconds before the timer ended, so which of them started the episode was not told apart, and Cancel was not pressed. A playback error on this device.

**Capability:** Fire OS has no TalkBack here, and the system lists one accessibility service, `com.amazon.logan`, off. It was neither forced nor enabled, so no screen reader has been heard on a Fire TV: what it would be given is verified, what it says is not.

## Accessibility closing pass: what was verified

2026-11-01, on `feature/engine-foundation` after `273e5fe`, with the fixes this pass made. It closes what the section below left open, and records what stays open.

**Verified on a real device, with real TalkBack.** Xiaomi Pad 7 (Android 16), TalkBack 17.1. Swipes and double taps came from a `uinput` virtual touchscreen, so they went through the system's touch handling as a finger's do, which `adb input` does not; what TalkBack said was read from its own "Display speech output". A person's ear has still not heard it.

| What | Result |
| --- | --- |
| Reading order | Home, Movies, Search, a film's page, a show's page, the episode list, the player, its lists, Sources: sections, then Search, Downloads, Media sources, then the page; headings are said as headings; a tab says its place ("Movies. Tab. 2 of 3") and whether it is selected |
| Cards | One stop each, title once, with what is left to watch ("VisionQuest, S1 · E5 · Firewall, 39 min left") |
| Search | "Editing. Harbour. Edit box. Search your library"; the number of results can be read ("1 result") |
| Player | Opening says "Playing Quiet Harbour"; reading starts on the way back; the whole player can be walked, the timeline says "0:11 of 1:15. Timeline. Slider"; nothing of the page behind is reached |
| Lists | Named on opening; one swipe from their button goes in; "Selected. Normal. Radio button. 3 of 7"; the controls behind are skipped |
| Up Next | Announced by itself: "Up next: S1 · E2 · Episode 2" |
| Playback error | Announced by itself: "Playback stopped. This file isn't in a format Reflux can play."; the card then reads in order, with its action |
| Sheet and questions | Watch offline reads in order with the page out of reach; a question is announced when it appears ("Remove RefluxA11y?") |
| Back | A real double tap on the way back left the player; Back closed a list, then the player |

**Found by that walk and fixed, none of it visible to the semantics tests:** wide and placeholder cards said their title twice; the speed button said its value twice; an open list came before its button in reading order, so reaching it meant starting over; a question that appeared in place was silent.

**Verified on a real device, without a screen reader.** Pad 7, "Remove animations" and text at 200%, by eye: the spinner becomes the word "Loading…"; Home, Search, a film's page, the player, a list (it scrolls, and a choice is made from it) and a question: nothing cut off, no action lost. Two cosmetic marks remain: a generated placeholder can break a long word at 200% in the smallest tile, and "Loading…" over the brightest frames has little contrast.

**Verified through semantics and tests only.** `AccessibilityTest`, with new tests for all four fixes. On television: isolation, the timeline's actions, and focus, on `Reflux_TV` in the section below. Dragging a slider under TalkBack (its action is tested, the gesture was not driven). Notices other than those above being spoken.

**Automated, at this point.** `check`: 810 tests, 0 failures, 12 environment-gated skips, lint and API green. Windows real windows: everything passes, `OfflineWindowTest` included; `PhysicalControllerTest` skips with no controller connected. Linux container, on these changes: both real-window rounds pass, and the headless check passes (`DesktopOfflineTest` with room to spare) except for two focus waits, `AboutUiTest.aKeyboardGoesInAndComesBackToWhereItWas` every time and `IdentifyUiTest.aRemoteSearchesAndChoosesWithoutAPointer` in some runs. Neither is from this work: they fail the same way on `fc51bd4`, before any of it. They time out with nothing focused, only in that headless run on Linux; the real-window keyboard and controller tests pass there. Their cause is not yet known, and is for M2's Linux CI to settle.

The two offline tests of the section below were this machine's disk, as it said, now proved: the planner read 25.80 GB free, 51.09 GB reserved, 0 usable. Once the disk had 59 GB free both passed, unchanged. They still fail on any disk under its 5% reserve (M2: give them a fixed room).

**Not met: where reading resumes.** Closing the player, a sheet, or a list opened from elsewhere puts TalkBack at the top of the screen, not on what opened it. Reflux can move input focus, but on Android TalkBack does not follow it on a touch screen, including with controls made focusable: tried and removed, since it worked only in a test. A double tap keeps TalkBack on a button that stays on screen, so a list closes back to its button. Moving TalkBack needs an accessibility-focus request the toolkit does not offer publicly.

**Blocked by environment, then resolved.** The Fire TV's network was degraded for this pass, so it could not take the build. It did the same day: see "Fire TV: the device-specific controls and the accessibility baseline" above.

**Not yet verifiable here.** TalkBack on a television (the `Reflux_TV` image has none). VoiceView on Fire OS.

**Unsupported by the platform or toolkit.** What Reflux gives a desktop screen reader is the same semantics tree. Compose reaches none on Linux; on Windows it reaches the Java Access Bridge only, so Narrator reads nothing, and an NVDA or JAWS pass also needs `jdk.accessibility` in the packaged runtime (M2). Keyboard use, reduced motion, and text size work on the desktop without one. Reflux does not claim desktop screen-reader support.

## Accessibility baseline: what was verified

2026-10-31, on `feature/engine-foundation` after `fc51bd4`. Physical devices were not attached and the Fire TV did not answer; device work below is on emulators.

| What | How | Result |
| --- | --- | --- |
| Automated suite | `check`, plus Android debug and test APKs | 807 tests, 12 environment-gated skips; everything passes except `DesktopOfflineTest`, below; lint and API checks green |
| What assistive technology is given | `AccessibilityTest` (`ui`): isolation, roles, states, panes, live regions, headings, traversal groups, keys, reduced motion, text size at 2×, 48 dp targets, read from the semantics tree | Pass |
| Settings readers | `DesktopAccessibilityTest`; `reg query` and `gsettings` output parsed; a real read on Windows | Pass |
| Isolation on Android | `Reflux_Phone` (API 36), `uiautomator dump` while a film plays: controls away, no Reflux node at all. `Reflux_TV`: player and list open, only they are in the tree, page gone | Pass; the defect recorded under "Controls composed per device" is gone |
| TalkBack, for real | TalkBack on `Reflux_Phone`, in a real film: the app saw it (controls stayed up, no timer), and TalkBack's own log and focus box put it on "Back to library" first in the player, and on Media sources first on Home, ahead of the page | Pass |
| Text size | `font_scale` 2.0 on `Reflux_Phone`, by eye: Home, details, player: buttons grow, Search stays in the bar, nothing cut | Pass |
| Remote | `Reflux_TV`: focus starts on Resume; OK plays; Down opens the lists; Back closes a list, the session still playing (read from `dumpsys media_session`); Back again closes the player and focus is back on Resume; Back again leaves | Pass |
| Windows | `:desktop:app:test -Preflux.windowTests=true` on Windows, including keyboard and controller browsing | Pass except `OfflineWindowTest`, below |

**Failing here, from this machine's disk, not from this work:** `DesktopOfflineTest` and `OfflineWindowTest`. The disk has 25 GB free of 952 GB and `StoragePolicy` keeps 5% (51 GB) free, so nothing can be kept; `OfflineWindowTest` says so ("not enough room"), and `DesktopOfflineTest` plans nothing for the same reason. `ui`'s offline tests read the same disk until now, and have a fixed amount of room since this work (`RoomyStore`); these two exercise the real wiring and still need room. For M2: give them a fixed room as well.

**Not verified:** a person moving through TalkBack. `adb input` does not pass through TalkBack's touch handling, and `UiAutomation` (`uiautomator dump`, `events`) suspends it, so linear swiping, how things are said, double-tap on a slider, and live regions being spoken could not be driven: the order past the first stop is tested as traversal groups, not heard. Physical Fire TV, phone, and Pad 7 (none attached): the cross-platform controls and this work are both still owed the Fire TV. TalkBack on a television (the `Reflux_TV` image has none). Reduced motion on a device (tested in the tree; the emulator setting was not exercised). A desktop screen reader: Compose reaches none on Linux, and on Windows only through the Java Access Bridge, which Narrator does not use (`jdk.accessibility` in the packaged runtime is for M2). Linux for this work (the container was not run).

**Seen on the way:** a suspected double Back on Android 13+ turned out to be controls auto-hiding during a slow check; with and without a fix, `dumpsys media_session` showed one Back closing only the list, so no change was made.

## Controls composed per device: what was verified

2026-10-20, from the working tree on top of `4f4a26f` (`0.9.0-alpha.2`, builds marked modified).

**Audit:** every screen the `ui` tests render, at phone (412x915 and on its side), tablet, desktop, and television sizes (`-Preflux.screenshots`), before and after; findings and the policy are in DECISIONS.md, 2026-10-20.

**Automated:** `./gradlew check :android:app:assembleDebug :android:app:assembleDebugAndroidTest --continue` on Windows 11: green, 767 tests, 0 failures, 12 skipped (the same environment-gated ones). Android lint and `checkDebugPlatformApi` passed. New: `PlayerControlsTest` (7: the television's remote-only set, the phone's lists and More, fullscreen by `FullscreenMode` and orientation, a tablet as a touch device and docked with a mouse, a desktop's bar at any window size, Previous and Next only where an episode is, Episodes always a tab), a tablet player test (Pad 7 size: transport in the middle, no volume or fullscreen on its side), the phone player test extended (More opens the lists, which fit the screen; the transport steps aside under an open list), the landscape phone's centred navigation, a notice kept above the phone's offline sheet, no Back on a television's details page, and a pointer test of the hover label (shown with its key, gone on leaving and after a press).

| Where | What | Result |
| --- | --- | --- |
| Windows, real window | The window suite: all passed except `EmbeddedPlaybackTest`, which failed twice with its own message for a screen it cannot see; `LogonUI.exe` was running (the workstation was locked), a known limit of that test on Windows | Passed apart from the locked-screen limit |
| Linux, real window (`tools/ci` container, scratch entrypoint saving screenshots) | All window tests, `EmbeddedPlaybackTest` included. A film in the real window: ↺10, play, ↻10, volume, the lists, fullscreen and back; no Previous | Passed |
| Google TV emulator (`Reflux_TV`, API 36) | By remote: a details page with no Back arrow and room around the focused action; the player showing only title, timeline, Paused, and the named list; OK paused, Down opened Speed, Back closed it, Back left the player. No crash | Passed |
| Phone emulator (`Reflux_Phone`, 1080x2400 after resetting an old size override) | By touch, a film: transport in the middle with ↺10/↻10, bar with Audio, Subtitles, More, Fullscreen; no Previous, Next, or volume. Fullscreen turned the screen sideways and became Exit fullscreen; Exit turned it back. More opened the lists; with Speed open, the long list sat over the transport (found here: the offscreen list was short), fixed so the transport steps aside while a list is open, and checked again on the emulator. The emulator's process died once on the first rotation (adb lost the device; the retry passed): an emulator failure, as recorded before for this image | Passed after the fix |
| Nothing Phone (2a) (A142, Android 16), first physical phone run | Installed (no earlier copy), three generated clips pushed to `/sdcard/Movies/RefluxTest` and added through the system picker. Episode 1: Next episode, More, Fullscreen, no Previous; Next episode went to episode 2, which showed Previous and no Next. The test source was removed in Reflux and the folder deleted afterwards; the app stays installed with an empty library | Passed |
| Xiaomi Pad 7 (landscape) | Updated over the existing install; the same clips added as a second source beside the user's own server (left untouched). Episode 1: transport in the middle, bar with Next episode and Speed only; no volume, no fullscreen, no Previous. The test source removed and the folder deleted afterwards | Passed |
| Fire TV Stick | Not reachable over adb this time (moved to another address, then refusing port 5555: network debugging off on the stick, which was unavailable). Amazon has no Fire OS emulator; the Google TV emulator ran the same television branch of the policy | Not re-verified |

**Seen, not changed (for the accessibility milestone):** while the player is open on Android, the library page beneath it is still in the accessibility tree (its rows appear in a uiautomator dump), so a screen reader could reach them behind the video.

## Playback compatibility audit (M2P): what was verified

2026-11-01, on `feature/engine-foundation` at `743b407`. No Reflux code changed and no Reflux build ran: these are direct readings with scratch scripts, not product behavior. No tests were run, because only documentation changed. Findings and their meaning are in PLAYBACK.md.

| What | How | Result |
| --- | --- | --- |
| RS-X1 library facts | Jellyfin Server 12.2.0, dedicated non-administrator test account, `Items` with media sources | 967 versions summarized (PLAYBACK.md, section 2) |
| `PlaybackInfo` for four client profiles | Live, the reference file and its siblings | Direct Play for the native profile; HLS with reasons otherwise. The browser-like profile is an approximation of Chrome's, not Chrome. Reasons are in the URL and in `TranscodingInfo`, not in the JSON field checked |
| Real server sessions | A first HLS segment per case on a sibling file (HEVC Main 10, E-AC-3, Matroska), then `GET Sessions` and `ffprobe` of the segment | Audio-only: video direct, AAC 5.1. Remux: both direct. Video: H.264, audio direct. All in software on the server. Each encode stopped (204) |
| Reference file | The same | **Static stream 404, HLS segments 500**, `PlaybackInfo` still answers; the eight other sampled files 206. Cause unknown: the account cannot read the server's log. On the first run an error path skipped stopping the encode; a later check showed no `TranscodingInfo` left |
| Static stream without authentication | Range request with no `Authorization`, and with one lacking a token | 206 for both |
| Desktop decode | libmpv 0.41-dev (FFmpeg N-127242) with `vo=null`, `ao=null`, on the sibling's static stream, Windows | Hardware `d3d11va-copy` (`p010`), and software (`yuv420p10`); E-AC-3 decoded to 6 channels at 48 kHz; exact seek to 30:00 in 0.25 s. No window or sound, so frame timing and first-frame latency here are not those of a real playback. That sibling has no PGS, so PGS was **not** rendered |
| Header escaping | The same with mpv's escaped and with Reflux's unescaped `http-header-fields` | Both open, because of the row above; so this proves nothing about the header |
| Pad 7 decoders | `dumpsys media.player`, read only (SM7675, API 36) | Dolby AC-3, E-AC-3, JOC; DV decoder; hardware H.264, HEVC, VP9; software AV1; no DTS or TrueHD; display lists all four HDR types |
| Fire TV decoders | The same, `AFTMA08C15`, API 30, `armeabi-v7a` only | Dolby AC-3, E-AC-3, AC-4; no `eac3-joc`, DTS, or TrueHD; hardware H.264, HEVC, VP9, AV1, MPEG-2, MPEG-4; DV decoders; current display lists HDR10 |

**Not verified:** anything played through Reflux; the Iron Man file itself; Media3 with any of this; the Phone 2a (not attached) and the Google TV emulator (not started); `.pgssub` delivery; passthrough; HDR output; Linux hardware decoding. "Listed" in PLAYBACK.md means only what a device lists.

## Playback truth layer (M2P): what was verified

2026-11-01, on `feature/engine-foundation`, on top of `a4435d8`. Real playback of a server's stream through libmpv, headless, and a real server's sessions; Jellyfin Server 12.2.0 on RS-X1 with the dedicated test account.

| What | How | Result |
| --- | --- | --- |
| Full suite | `check`, plus Android debug and test APKs | 845 tests, 12 skipped, 2 failed. `Media3FailureTest`'s new case was mine and could not run off a device (`Format` needs Android classes), so it was removed; the device test below covers it. `DesktopOfflineTest` failed as the 2026-11-01 section above says it does on a disk under its 5% reserve: C: had 35 GB free of a 51 GB reserve. `OfflineWindowTest`, its partner, is a window test and did not run. The Android unit tests were rerun after the removal |
| Focused tests | `JellyfinPlaybackTest` (20 new, fixtures), `MpvEngineTest` (4 new, real libmpv), `PlaybackSessionTest` (5 new) | Pass |
| The token through the engine, before the fix | Loopback server recording `Authorization`, real libmpv | **Failed:** the server received `MediaBrowser Client="Reflux"`, for playback and for the probe. Token never sent |
| The same after the fix | The same | Pass: the value arrives whole on every request |
| The token on a real server | `JellyfinTruthLiveTest`: real libmpv on `Items/{id}/Download`, which refuses without a token (control: 401) | With the header: playing, 4 tracks. Without it: `ERROR UNREACHABLE` |
| Direct Play | Real libmpv on the static stream, position reported, then `GET Sessions` | `DIRECT_PLAY`, video and audio preserved. Session lists the item, `playMethod=DirectPlay`, `TranscodingInfo` none. `SupportsDirectPlay=true` |
| Audio-only conversion | HEVC Main 10, E-AC-3, Matroska, no AAC track; real first segment | `IsVideoDirect: true`, `IsAudioDirect: false`, hevc + aac, ts, `AudioCodecNotSupported`. `SupportsDirectStream: false` |
| Remux | The same file, Matroska refused by the profile | `IsVideoDirect: true`, `IsAudioDirect: true`, hevc + eac3, ts, `ContainerNotSupported`. `SupportsDirectStream: false` |
| Video transcode | The same file, HEVC refused by the profile | `IsVideoDirect: false`, `IsAudioDirect: true`, h264 + eac3, ts, `VideoCodecNotSupported`. `SupportsDirectStream: false` |
| A subtitle causing a conversion | HEVC, E-AC-3, PGS, audio the only problem; PGS selected on the server | `IsVideoDirect: false`, `IsAudioDirect: false`, h264 + aac, `AudioCodecNotSupported`, `SubtitleCodecNotSupported`. Reported as subtitle-triggered |
| The same with PGS declared External | The same file and track | `IsVideoDirect: true`, hevc + aac, `AudioCodecNotSupported` only. No burn-in |
| Silent audio, desktop | `MpvEngineTest`, a generated file whose audio codec is unknown, real libmpv | Plays, `aid` is no, `audioUnavailable` true; a file with working audio is false |
| Silent audio, Android | `PlaybackOnDeviceTest` on the Xiaomi Pad 7, real Media3, generated H.264 + DTS in Matroska; this device has no DTS decoder | Picture plays, `audioUnavailable` true, `unsupportedAudioCodecs` `audio/vnd.dts`. `theEnginePlaysSeeksAndEnds` still passes there |

All conversions ran in software on the server (`HardwareAccelerationType: none`), and each was stopped after its first segment.

**Not verified:** the notice on a real screen (the session test reads it; the engine tests stop at `audioUnavailable`); the whole session on a Jellyfin stream through the real app, with its log line; the Iron Man file, which still fails on the server, so the live cases used other files of its class; any `.pgssub` delivery being played; Media3 authenticated requests, which were not in question; the Fire TV, the phone, and Linux for this slice. The live test now starts real conversions on every `check` where a server is named: about 20 seconds.

## Shared capability model (M2P.2): what was verified

2026-11-01, on `feature/engine-foundation`, on top of `545f721`. Nothing here played media beyond the engine tests' generated clips: this is what clients report, not what they play.

| What | How | Result |
| --- | --- | --- |
| Full suite | `check`, plus the Android debug APK | **Not clean.** 897 tests, 12 skipped, 1 failed: `DesktopOfflineTest`, on a disk with 27 GB free of a 51 GB reserve, the failure recorded above. Not rerun on a roomier disk. Gradle reused results for modules already tested on this tree, and the counts are read from the latest result of each. `OfflineWindowTest` and the other window tests did not run |
| The shared model | `PlaybackCapabilitiesTest` (29 new) | Pass |
| Android's mapping | `AndroidCapabilitiesTest` (13 new, as listed by real devices) | Pass |
| Desktop | `DesktopCapabilitiesTest` (6 new), `MpvEngineTest` (2 new, real libmpv): profile and sample rate probed, channels reported once per file | Pass |
| Best Version | `VersionSelectorTest` (2 new); its existing tests unchanged | Pass |
| Persistence | `WatchHandoffTest`: a library one schema back keeps its version and reads unknown for the new facts; the two older migration tests now start from the right older schema | Pass |
| Android lint | `lintDebug` | Pass |
| Xiaomi Pad 7 | `CapabilityReportOnDeviceTest`, real platform and Media3 (Android 16) | Pass; logged in PLAYBACK.md, section 6 |
| Fire TV `AFTMA08C15` | The same (API 30, 32-bit, through its HDMI output) | Pass. Media3 reports AC-3, E-AC-3, and Atmos passthrough and 8 channels; no receiver or ear has confirmed it |

**Not verified:** the Nothing Phone 2a and the Google TV emulator (not read); `PlaybackOnDeviceTest` after these changes (the Pad 7 dropped off adb before the rerun; `audioThisDevice...` last passed on `545f721`'s tree); `AndroidCapabilitySource` reacting to a real output being plugged in (its reading is tested, the callbacks are not); desktop channels on real surround hardware (the engine test uses a null output); any claim in the model about PGS, VobSub, DVB, WebVTT, or TTML on Android, which stay unknown.

## Playback planner (M2P.3): what was verified

2026-11-01, on `feature/engine-foundation`, on top of `f66c5bd`. Fixtures only: nothing here played anything, because nothing executes a plan.

| What | How | Result |
| --- | --- | --- |
| Full suite | `check` | **Not clean.** 962 tests, 12 skipped, 2 failed. `DesktopOfflineTest` failed as recorded above: C: had 27 GB free of a 51 GB reserve; it was not rerun on a roomier disk. `PlaybackAssessorTest`'s new case asserted too much and was corrected; `core` was then rerun alone and passed, and the full suite was not rerun. Window tests did not run |
| The plan | `PlaybackPlannerTest`, 62 new tests of plans and reasons, by delivery, video, audio, subtitles, combinations, range, and source | Pass |
| One answer to "can this play" | In the same test: a client-only plan is `CANNOT_PLAY` exactly when `PlaybackAssessor` says unsupported, over 720 combinations of device, video, audio, and container | Pass |
| Best Version | `PlaybackAssessorTest` (2 new); `VersionSelectorTest` unchanged | Pass |
| Jellyfin's declaration | `JellyfinPlaybackTest` (1 new) | Pass |

**Not verified:** any plan against a real playback; the capability sets for local, SMB, and WebDAV in the planner's tests are copies of what those adapters declare, not read from them; automatic burn-in of forced subtitles was decided against without a real case.

## Plan execution (M2P.4): what was verified

2026-11-01, on `feature/engine-foundation`, on top of `1e22dc6`. Jellyfin Server 12.2.0 on RS-X1 with the test account. "Same" means the comparison found no mismatch between the plan and the server's own session. The live cases use HEVC Main 10 files with E-AC-3 or AC-3, because the Iron Man file still answers 404 and 500.

| What | How | Result |
| --- | --- | --- |
| Full suite | `check`, plus the Android debug APK | **Not clean.** 1024 tests, 12 skipped, 1 failed: `DesktopOfflineTest`, on a disk with 25 GB free of a 51 GB reserve, as recorded above; not rerun on a roomier disk. Window tests did not run |
| Live, desktop | `JellyfinExecutionLiveTest`: scan, Best Version, plan, negotiation, real libmpv with no window or sound, `TranscodingInfo` | Table below; 4 of 4 |
| Authentication | `JellyfinTruthLiveTest`, in `check`: real libmpv on an address that refuses without a token | Pass |

| Live case, desktop | Planned | Server's session | First frame | Seek | Result |
| --- | --- | --- | --- | --- | --- |
| Direct Play | Original | No conversion listed; item playing | 0.5 s | 0.4 s | Same |
| Audio conversion | Audio only | Video direct; HEVC + AAC | 1.3 s | 1.7 s | Same |
| Remux | Container only | Video and audio direct; HEVC + AC-3 | 1.4 s | 0.5 s | Same, after the fix below |
| Video transcode | Video only | Audio direct; H.264 + AC-3 | 2.7 s | 5.0 s | Same |
| `originalFailed = VIDEO` | Video transcode | Audio direct; H.264 + AC-3 | 4.6 s | 5.7 s | Same |
| Explicit second track | Audio only, that track | Video direct; HEVC + AAC, one track in the stream | 1.1 s | 2.1 s | Same |
| Audio conversion, PGS chosen | PGS beside the stream | Video direct; HEVC + AAC, subtitle selected | 1.3 s | 1.3 s | Same |
| PGS nothing can draw, chosen | Burn-in | Video converted, audio direct; `SubtitleCodecNotSupported` | 4.4 s | 8.2 s | Same |

Times come from a headless engine on a file the server had just read, and are not a player's. The PGS row ran with the track already extracted; on first request, the server took 48.9 s.

| Device | Plan for the real device | Second case | Result |
| --- | --- | --- | --- |
| Xiaomi Pad 7 | Direct Play, `c2.qti.hevc.decoder` hardware, local decode. First frame 2.1 s, seek 1.5 s | Audio conversion over HLS, same decoder. 1.6 s, 2.0 s | Same; 6 of 6 |
| Fire TV `AFTMA08C15` | Direct Play, `OMX.MTK.VIDEO.DECODER.HEVC` hardware, passthrough. 2.1 s, 2.5 s | Audio conversion over HLS. 3.1 s, 3.5 s | Same; 6 of 6 |
| Google TV emulator | Video transcode, because it has no 10-bit HEVC decoder. 3.1 s, 4.5 s | The same, with audio converted. 3.0 s, 5.5 s | Same; 6 of 6. Software decoders |

The 6 are: both cases above; an explicit track that cannot play stays silent with French present; automatic selection reaches the planned track by its place in the file; the capability report; and silent audio plus ordinary playback. On the Fire TV and the Pad 7 these ran on the final product code; the test's own assertion was changed afterwards, for the emulator, which alone reran.

**Found by live runs, not by fixtures:** `MaxAudioChannels` set to a stereo output made a server convert audio a remux was to keep; PGS extraction would have held the picture for 49 s.

**Not verified:** the Nothing Phone 2a (not attached); Linux; the Iron Man file; any of this through the real application's screens, as these tests drive the engines and the planner directly; the notice on a screen; a real `UNSUPPORTED` failure from an engine re-planning live (`originalFailed` was passed in, and the session's reaction is tested with a fake); PGS through Media3; passthrough heard through a receiver. The first Pad 7 run hung past 500 s with no test output. It did not recur in two later runs, and its cause is unknown.

## M2P.4 gaps, real application (2026-11-01)

Dedicated non-admin test account; the real app's screens, on a real server.

| Check | Device | Result |
| --- | --- | --- |
| PGS, embedded, 1920x818 film | Fire TV `AFTMA08C15` | Pass: both lines drawn whole, the second below the picture |
| Explicit language on a converted stream | Google TV emulator | Pass after a fix: Hindi plan `track 0`; place kept from 1:41, server reported 123 s |
| External subtitle by server extraction, converted stream | Google TV emulator | Pass, on screen |
| Planned burn-in, PGS | Google TV emulator | Pass: plan `BURN_IN`, server says a subtitle caused the conversion, text in the picture |
| Server stream stops | Fire TV | Pass: error with Try again after 30 s, no re-plan, no loop |
| Back and close, place kept | Fire TV | Pass |
| Stream unlike its playlist | Fire TV | Was reported as a moved file; now `UNKNOWN`. Unit-tested; not re-seen, as the film's metadata was then repaired |

**Found:** a track change on a converted stream restarted the film (fixed). A subtitle the server refuses was silent on desktop (fixed: the first load of the film below got 404 and the viewer was told).

**The server's files changed during this verification, on purpose.** A batch remux of the owner's, stripping Hindi audio from `/srv/media/Movies`, ran throughout (70 films by 13:20 UTC). Each file is replaced atomically, and renamed ones keep their old modification date, so Jellyfin's scans miss them. By the end, 33 of 69 multi-audio films listed an audio track their file no longer had. Results that name those tracks describe the files as they then were, and the stream indexes are stale until the remux finishes and the libraries are refreshed. Fallout's subtitle 404s follow from this: Jellyfin's log shows it extracting by a stale stream index that the rewritten file no longer has. The three `JellyfinExecutionLiveTest` failures at the end are attributed to it, not re-tested. Four films were re-probed one by one, with no `.nfo` written, and Marvel as a library. Re-probing waits for the remux.

**Desktop, real window (`PlannedPlaybackWindowTest`, a device without AC-3 standing in for one needing audio converted):**

| Check | Windows, `d3d11va` | Linux container, Xvfb, software decode, libmpv 0.37 |
| --- | --- | --- |
| Plan `AUDIO_CONVERSION`, server evidence agrees, no mismatch | Pass | Pass |
| Another language, from the place | Pass (304.6 to 309.7 s) | Pass (304.7 to 312.1 s) |
| Server-extracted PGS with the picture running | Pass, 9 s | Pass, 10 s; seen on screen in mpv |
| Close | Pass | Assertion, below |

The first Windows attempt sat in `LOADING` for 90 s and did not recur; its cause is not established.

**Linux container:** `check` 991 tests, 25 skipped, 4 failed; one window round, its 8 window tests passing. The container has no GPU, no sound device, and is not a real Linux desktop. Failures: `AboutUiTest` and `IdentifyUiTest` wait for focus, headless and unrelated to this work. `probeReadsWhatCapabilityQuestionsNeed`: libmpv 0.37 reports no `codec-profile`, so profile stays unknown, which degrades correctly but leaves the test failing there. `theEnginesLogCarriesNoCredentials` closed before the log arrived, so it proved nothing; it now waits, and passes on Linux with the header line present.

**Open, Linux:** after the planned window test had passed, the test process aborted in libmpv 0.37: `vo_x11_config_vo_window: Assertion x11->window failed` (exit 134). The embedded window went while the engine used it. Closing during HLS playback may abort the application on that libmpv; not reproduced outside this test.

**Suite:** the last Windows `check`: 7 failed, all in `desktop:engine`; 6 live tests whose film or server had changed under them, and `DesktopOfflineTest`, low disk: 36 GB free against a 51.1 GB reserve. An earlier run: 1034 tests, 12 skipped, with `DesktopOfflineTest` and 6 server timeouts failing, and all 10 live tests passing on rerun; no run was fully clean. Emulator on-device 5 of 5. Fire TV 4 of 4 execution tests after a reboot, which cleared an engine timeout on the stick.

**Not verified:** the Nothing Phone 2a (not attached); a real `OUTPUT` failure; Linux on real hardware; stream indexes accurate across the library, which needs the files to stop changing first.

## M2P.4 on a stable library (2026-11-01 to 02)

The owner's Hindi remux finished at 14:19 UTC. Nothing in the libraries changed in the 30 minutes before this began. Jellyfin was scanned; no media was modified. Devices ran a side-by-side debug build (`dev.reflux.m2p`, signed in only as `reflux-test`, uninstalled afterwards), because the Pad's and Phone's own Reflux hold the owner's real library and watch state.

**Index baseline.** Every file on disk was read with ffprobe and compared with Jellyfin by stream index: 1,003 of 1,003 versions match, no size differs, none is missing, and films with Hindi audio agree at 215 on both sides. Embedded cover art (`mjpeg`) is an image to Jellyfin and is excluded. The stale tracks are gone. Reflux itself lists 1,003 versions, all with stream indexes, on each device.

**Fallout's PGS.** All five tracks return 200 (first extraction 50.9 s, the rest under a second). Four returned 404 while the indexes were stale, so that was the cause.

**Explicit language, server evidence.** Mickey 17 has Spanish at `0:1` and English at `0:2`. Asking for the second, the server's own ffmpeg ran `-map 0:2`, `-codec:v:0 copy`. On the Phone, choosing Spanish at 0:55 of a converted stream ran `-map 0:1`, `-codec:v:0 copy`, `-ss 00:01:01`, and the timeline went on at 1:10. `anExplicitTrackIsTheOneTheServerConverts` had begun to skip itself, as no HEVC film has two audio tracks now; it takes any file with two languages, and runs.

| Check, real application | Nothing Phone 2a (A142, Android 16, Wi-Fi) | Xiaomi Pad 7 |
| --- | --- | --- |
| Direct Play | Pass, `c2.mtk.avc.decoder` hardware | Pass, `c2.qti.avc.decoder` hardware |
| HEVC original | Pass in the suite, `c2.mtk.hevc.decoder` | Pass, `c2.qti.hevc.decoder` |
| Audio conversion, video preserved | Pass: AC-3 and DTS, no decoder here | Pass: DTS |
| Same film, Mickey 17 (AC-3) | Conversion | Direct Play: this device decodes it |
| Explicit audio | Pass, server evidence above | Pass, in the file, no new stream |
| Subtitles | PGS on screen, whole; VOBSUB burned in by plan (`overlay`, `SubtitleCodecNotSupported`) | SRT on screen |
| Plan against actual | Agree | Agree |
| Seek, resume, close | Pass; close released the server's conversion | Pass |
| Suite | 5 of 5 | 5 of 5 |

**Phone capabilities, declared by this device:** hardware H.264 (10-bit too), HEVC Main and Main 10 to 4096x2176, VP9; software AV1 to 1920x1088, VP8; no Dolby Vision decoder. Audio AAC, ALAC, FLAC, MP3, Opus, PCM, Vorbis: no AC-3, E-AC-3, or DTS. Passthrough none, two output channels. Display 2412x1084, HDR10, HLG, HDR10+. A seen capability, a declared one, and UNKNOWN stay separate in the report: AC-3 is UNSUPPORTED because the list was read; VOBSUB rendering stays UNKNOWN.

**Not seen on the Phone:** the no-sound notice, as every film it cannot decode was converted by the server; that notice was last seen on the Google TV emulator.

**Failure, one re-plan (Google TV emulator, real application).** Dexter with its video facts forgotten: Media3 failed on HEVC 10-bit, "the original VIDEO did not play, so it is being planned again", then `VIDEO_TRANSCODE`, audio preserved, `VideoBitDepthNotSupported`, plan and actual agreeing. Second-failure and no-loop behaviour was seen earlier (above); that code is unchanged. Emulator suite 6 of 6.

**`OUTPUT`, real.** `anOutputThePlatformRefusesIsAnOutputFailure` on the Phone (`-e refluxOutput true`): the process holds every audio track the platform gives it (40), the engine's own is refused, Media3 raises `ERROR_CODE_AUDIO_TRACK_INIT_FAILED`, and the player reports `OUTPUT`. This proves the engine and classification on hardware. The session's hard failure is covered by `anOutputThatWillNotStartIsNotPlannedAround`; the error screen on a real output failure was not seen.

**A false mismatch, explained.** A force-stopped application leaves its session on the server: the next film's evidence read the dead conversion's `TranscodingInfo`, reporting a mismatch on a static stream, which a server cannot convert. With a normal close the same sequence agrees. It takes a crash, and it only misreports a diagnostic line, so it is noted, not changed.

**Linux teardown assertion.** `vo_x11_config_vo_window: Assertion x11->window failed` was reproduced: 1 of 3 runs with HLS, 1 of 3 with the original file, 0 of 3 waiting 6 s after closing, 0 of 3 closing the window mid-playback. So it is not specific to HLS. Cause, in Reflux: leaving waited for a player still open, and not for one the viewer had just closed, whose engine might still be letting go of the surface. `AppState.finishPlayback` waits for both, bounded so a stuck engine cannot hold the application; `leavingWaitsForAnEngineThatIsStillLettingGo` fails without it. After it, 0 of 12 runs aborted (6 HLS, 6 original file), against 2 of 6 before. The race is intermittent, so that is strong evidence and not proof, and it is in a container with libmpv 0.37.

**Suites on the final code.** Windows `check`: 1,038 tests, 14 skipped, 0 failed, the first fully clean run, including `DesktopOfflineTest` and every live test. Windows real-window tests: 38 run, 0 failed, 2 skipped (one is the teardown test, which runs on request; the other's name was not kept). Linux container `check`: 994 tests, 17 skipped, 4 failed; window round: all window tests pass, `PlannedPlaybackWindowTest` included, on a real server and without an abort.

| Linux failure | Category |
| --- | --- |
| `theEnginesLogCarriesNoCredentials` | Stale test, fixed: it demanded a line count it stopped waiting for. 5 of 5 after, with the header line present |
| `probeReadsWhatCapabilityQuestionsNeed` | Environment: libmpv 0.37 reports no `codec-profile`. Profile stays unknown, which is the right degradation; the test still fails there |
| `AboutUiTest` (2) | Environment: headless focus and timing, on Linux only; pass on Windows. Unrelated to M2P |

`JellyfinSignOutLiveTest` printed a stored credential when it failed, by comparing it by value; it no longer can. Its failure in the container was false: with no Secret Service, `ConfigSeal.None` hands back what it is given.

**Not physical evidence:** the Linux runs are a container with a virtual display, software decoding, and no sound device.

## M2P.4 physical Linux gate: waived (2026-11-02)

**WAIVED: physical Linux hardware unavailable; virtual Linux validation accepted for M2P.4.** The owner decided this on 2026-11-02, and M2P.4 is closed on it. The gate was not run and did not pass. RS-X1 is a live media server and will not be changed to run it.

What was accepted is the Linux evidence above: real window tests and real playback in a container with a virtual display, the teardown fix, and a `check` of 994 tests with 3 environment failures left (the table above). What the waiver leaves unproven on Linux: a hardware decoder, a real audio output, a real desktop session, and teardown outside the container. It covers M2P.4 only. It is not evidence for those, and it does not change M3's clean Linux install or the V1 acceptance criteria.

The gate stays in the repository as an optional tool for when such a machine exists. `tools/live/physical-linux-gate.sh`, on a real machine in a graphical session at that machine, runs `PhysicalLinuxGateTest` and the teardown cases.

| Requirement | Evidence the gate takes |
| --- | --- |
| 1. Launches | The real window shows Home |
| 2. Hardware decoder | `PlaybackComparison.decoder.hardware` true on the Direct Play film; the engine log's `Using hardware decoding` |
| 3. Real audio output | A card in `/proc/asound/cards`; the engine log names an `AO` that is not `null`; a person hears it |
| 4. Direct Play | Plan and evidence both `DIRECT_PLAY`, no mismatch |
| 5. Audio conversion | `AUDIO_CONVERSION`, no mismatch, with AC-3 withheld as a device needing conversion would lack it |
| 6. Subtitles | A PGS track the server extracts, selected, in saved frames a person looks at |
| 7. Seek and resume | Seek to 5:00, close, reopen near that place |
| 8. Teardown | 6 runs each with HLS and the original file, exiting at once: no abort |
| 9. Planned against actual | The comparisons in 2, 4, and 5 |
| 10. No leak | The log, and every result file, searched for the password and tokens; counts only |

Asked for elsewhere, the gate refuses rather than skips: it refused a container (not a virtual display, sound card, or render node), and fails on Windows. A gap on a real machine is reported as what is missing, not as UNSUPPORTED. Two things stay with a person: hearing sound, and seeing the subtitle in the frames.

**Hardware seen.** RS-X1 is bare metal: a ThinkPad X1 Yoga 1st gen, Intel HD Graphics 520, HDA Intel audio, an eDP panel, Ubuntu 26.04. It is the only physical Linux machine reachable, and it cannot run the gate as it is: `rifaque` is not in `video`, `render`, or `audio`; there is no display server, audio service, libmpv, or Java on the path; and these need `sudo` on the live media server. `mimikyu`, a Linux node on the tailnet, was offline and its hardware is unknown.

**To run it on any machine:** install libmpv and ffmpeg; join `video`, `render`, and `audio`; start a real desktop session with a working audio output; copy `~/.reflux-live/jellyfin.env` (mode 600) naming `reflux-test`; then run the script from a checkout at the same commit, at the machine.


## M2P.5 real media matrix, first walk (2026-11-02)

Jellyfin 12.2.0 on RS-X1, 1,003 versions after the remux, as `reflux-test`. No media changed. Results per target: PLAYBACK.md, section 15.

| What | How | Result |
| --- | --- | --- |
| Survey | `PlaybackSurveyTest`, 7 tests | Pass |
| Windows | `MediaMatrixLiveTest`, `REFLUX_MATRIX=1`: libmpv, no window or sound, a declared 1920x1080 display | 49 cases, 21 walked, 21 as planned, all Direct Play and hardware decoded |
| Fire TV `AFTMA08C15` | `MediaMatrixOnDeviceTest` through `tools/live/run-on-device.py`, with its real capabilities; a private library, the application's left alone | 48 cases, 24 walked, 23 as planned, 1 failed (seek) |
| Iron Man, the reference file | The same tests for that title: no subtitles, PGS, SRT | 3 of 3 on each. First frame 0.4 to 0.5 s on Windows, 1.6 to 2.6 s on the Fire TV. Static stream 206 |
| Matroska structure | Read-only on RS-X1: first 256 KB and last 4 MB of each of 992 files | 989 name Cues in the SeekHead; 3 do not, all Moon Knight |

"Walked" means started, seeked, and compared with the server's session, plus the chosen track selected and sound present. **The failure:** The Goldfish Problem did not reach a seek in both cases of a repeat run on the Fire TV, position 89 s after 90 s, with a target of 120 s. It had restarted (next section). "3 do not" below names Cues in the first SeekHead only: they do in a second.

**Not verified:** the Phone 2a, the Pad 7, and the Google TV emulator were not walked (not attached, or not started); Linux; the two other Moon Knight files; Media3's seekable answer; any picture, as subtitles count as selected and nobody looked; passthrough heard; the full `check`, as this added one pure class and two gated tests, and ran `PlaybackSurveyTest` and compiled the rest.

## Media3 and chained SeekHeads (2026-11-02)

Fire TV `AFTMA08C15`, Jellyfin 12.2.0 on RS-X1, as `reflux-test`. The file structure was read with a read-only walk of every top-level element. No media changed. E01 was copied to the device for one run and deleted. Saved places were set on the test account for this and put back to 0. The cause and tables: PLAYBACK.md, section 15.

| What | How | Result |
| --- | --- | --- |
| Media3's own answers | `SeekabilityOnDeviceTest`, `-e refluxSeekProbe true`: 6 episodes, a film, E01 as a local file, E01 by server remux | E01, E03, E04 false, on Jellyfin and as a file; all others true |
| Real application, unfixed | Deep link, remote fast-forward, close | Saved 300 s: opened at 2.8 s; fast-forward to 0. Past a minute: Reflux 81.6 s, server 0 |
| Real application, fixed | The same, with saved places | E01 81.6 s, E03 400.0 s, E04 500.0 s, each landing there; fast-forward moved on; closed at 142 s, 462 s, 559 s, and both sides agreed |
| Control, fixed | E02, saved 300 s | Direct Play, plan and server agreeing; fast-forward moved on |
| What the server did | Jellyfin's ffmpeg logs (read over ssh) | `FFmpeg.Remux`, `-codec:v:0 copy`, `-codec:a:0 copy`: nothing converted |
| Windows | `MediaMatrixLiveTest`, each file with and without ASS | 8 of 8: Direct Play, seek under 0.4 s |
| Regression, no hardware | `PlaybackSessionTest`, `PlaybackPlannerTest`, `PlaybackControllerTest`, `Media3FailureTest` | Pass |
| Regression, Media3 | `SeekabilityOnDeviceTest`: a generated clip rewritten to the same shape, and the plain clip | 2 of 2 |
| Full suite | `check` on Windows, with the live server | 1,064 tests, 16 skipped, 0 failed. Window tests and the Linux container were not run |
| Each guard removed in turn | Tests rerun, then restored and compared | Every one fails a test. Without `Media3Player`'s guard the generated clip goes from 1.6 s to 0.6 s |

Also found and fixed: the first load's evidence check outlived a re-plan and logged a false mismatch of a Direct Play plan against the remux. Forced-stopping the application between runs gave a control `UNKNOWN` evidence, as recorded above. Without it, Direct Play was confirmed.

**Not verified:** a server refusing the remux, live. Other devices, the notices, and the evidence read: next section.

## Chained SeekHeads across devices (2026-11-02)

The build of `79fc739`, as a side-by-side `dev.reflux.m2p` signed in only as `reflux-test`, so the devices' own libraries were not touched. The devices ran one at a time, as they share the account's watch state. Saved places were put back to 0.

| Check, real application | Google TV emulator | Nothing Phone 2a | Xiaomi Pad 7 |
| --- | --- | --- | --- |
| E01, E03, E04: fast-forward from about 13 s | Re-plan to remux, landed about 10 s on. 3 of 3 | The same. 3 of 3 | The same. 3 of 3 |
| E02, control: fast-forward | Direct Play, no re-plan, plan and server agree | The same | The same, and the server has no remux of E02 |
| E01 resume, saved 400 s | Landed at 400.0 s; closed at 464 s | Landed at about 400 s (first reading 403.0 s); closed at 458 s | Landed at 400.0 s; closed at 464 s |
| E03 resume, saved 500 s | First reading 503.8 s; closed at 566 s | First reading 502.9 s; closed at 566 s | First reading 502.8 s; closed at 567 s |
| E02, control: resume, saved 300 s | Direct Play, plan and server agree; closed at 366 s | The same; landed at 300.1 s, closed at 367 s | The same; landed at 300.2 s, closed at 365 s |
| Watch state | Reflux and the server agreed at every close | The same | The same |
| Re-plans | One for each chained playback | One | One |
| Audio | Plan track 1, English, not the default Hindi; server `-map 0:2` | The same | The same |
| False mismatch | None | None | None |
| Media3's own answer | E01, E03, E04 false; E02, E05, E06, the server's remux true | The same; Doctor Strange true | The same; Doctor Strange true |
| Regression, generated clip | 2 of 2 | 2 of 2 | 2 of 2 |

Each saved place was read back from the application before it was played: a cleared application, a place set on the server, a sync, and only then playback. Resume on E04 was not run on these three; it ran on the Fire TV. The stay-awake setting was on for the Phone and the Pad during their runs, and turned off afterwards.

An attempt at resume on the emulator and the Phone was thrown away: seek walks had left a newer local state of 0, which rightly outranked a place edited on the server, so no resume was tested. A second attempt on the Phone did not play, as it had locked. Both were redone from a cleared application.

**No remux, Fire TV, real application and real screen.** A copy of E01 in the device's own folder, a source with no remux, then removed.

| Check | Result |
| --- | --- |
| Seek | "You can't skip through this file on this device." at the top of the picture, within 2 s, twice. The position went on from 27 s to 55 s |
| Saved place | "This file can't start partway through on this device, so it's playing from the beginning." within 1.3 s, over the first frames at 0:00 |
| Retry | No re-plan, no loop, no restart, no mismatch logged |
| Watch state | 69.7 s kept through a run under a minute |

The language of this run was not read: the plan and the file allow only what the same tests show elsewhere, and no comparison with a source exists here. A first press during a cold start (position 3.8 s at 14 s) logged no refusal and restarted nothing. What it did is not established.

**Evidence after a re-plan: a true `UNKNOWN`.** Polling the server during a re-plan listed `TranscodingInfo` for about 6 s, and the stream played on after it had gone. Jellyfin's own log says why: a remux copies, and the last one wrote the remaining 33:50 in 3.46 s, at 587 times speed, then exited. Reflux asks 5 s into playing, so the job is usually over. `TranscodingInfo` exists only while a job runs, and `PlayMethod` is an echo, so nothing left can show a remux. Shown as `unconfirmed`, never as a mismatch. It was not changed. Server evidence is `FFmpeg.Remux`, `-map 0:0 -map 0:2`, `-codec:v:0 copy`, `-codec:a:0 copy`.

## M2P.5 real media matrix on Android (2026-11-02)

`MediaMatrixOnDeviceTest` as a side-by-side `dev.reflux.m2p`, a private library, one device at a time, against Jellyfin 12.2.0 on RS-X1 as `reflux-test`. The walk now also plays one version per video size, frame rate, profile, and level (`PlaybackSurvey.edges`), a viewer's own second language, and records Best Version for each work with two versions. Subtitles are judged by the cues Media3 hands over: there is no surface, so no picture.

| | Google TV emulator | Xiaomi Pad 7 | Nothing Phone 2a |
| --- | --- | --- | --- |
| Classes planned | 51 | 48 | 51 |
| Walked, as planned | 50 of 50 | 46 of 46 | 38 of 40, and 9 not reached |
| Versions by first plan | Direct Play 809, audio conversion 52, video transcode 142 | Direct Play 1,000, audio conversion 2, video transcode 1 | Direct Play 909, audio conversion 94 |
| Conversions walked | AC-3, E-AC-3, DTS: audio only. HEVC Main 10 and H.264 High 10: video, audio kept where it decodes | DTS: audio only. High 10: video, AC-3 kept | AC-3, E-AC-3, DTS: audio only. High 10: audio only, decoded in hardware |
| Subtitles, cues delivered | SRT, ASS text; PGS and VobSub bitmaps; extracted SRT and ASS over a conversion | The same; PGS (Doctor Strange, Hindi forced first): none in 45 s, expected | The same |
| Burn-in | PGS or VobSub chosen where audio converts | VobSub chosen where DTS converts | PGS or VobSub chosen where audio converts |
| Viewer's second language | Spanish, converted, kept | Spanish, in the file, kept | Not reached |
| Best Version, works with two versions | H.264 over HEVC 10-bit it cannot decode | HEVC 10-bit with E-AC-3 | Not reached |
| Failures | None | None | 3, all network: `ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT` and server unreachable mid-run, which ended the run 9 cases early. Ping was clean afterwards. None on the other devices |

Not one walked playback was a mismatch, a silent track, an unnecessary conversion, a remux, or a change of language. The chained Moon Knight file walked as `engine-unseekable`, as designed.

**Found:** on Android, external PGS is declared unsupported, because the attachment path types a file by its extension and has no bitmap type. So a PGS track chosen on a film whose audio this device cannot decode burns in, and the video is transcoded: 30 to 48 versions on the Phone and the emulator, 0 on the Pad. That is the one subtitle-forced video conversion in the matrix. Real files exist to test a change. Not changed here.

**Not verified:** any subtitle picture. A real-application pass on the Pad captured only black frames, with the tablet asleep and its session paused, and is not counted. The Phone's last 9 edge cases, its second language, and its Best Version. Whether the Phone's network failures were the phone's or the network's.

## M2P closure (2026-11-02)

Side-by-side `dev.reflux.m2p` builds on the devices, signed in only as `reflux-test`, removed afterwards; the devices' own libraries were not touched. No media changed. Generated media are test pictures and tones (PLAYBACK.md, section 16).

**PGS beside a converted stream, Android.** Jellyfin serves an extracted PGS track as a `.sup` (2 Fast 2 Furious: 31.8 MB, 2,422 presentations, `PG` framing throughout).

| Check | Device | Result |
| --- | --- | --- |
| `SupDisplaySetsTest`, the framing | JVM | 5 of 5 |
| `PgsAttachmentOnDeviceTest`: a generated `.sup` beside a clip, drawn at 2 s and cleared at 4 s, the picture not held | Fire TV, Google TV emulator, Pad 7 | Pass on each |
| H.264 + AC-3 + PGS, The Vow, English PGS tracks 0 and 1 | Google TV emulator | Planned and actual `AUDIO_CONVERSION`, video preserved, subtitles `EXTERNAL`; bitmap cues 703x60 and 586x123. First run: none within 45 s, the server extracting the track for the first time; the picture did not wait |
| The same, real application, the subtitle chosen from the player's menu | Google TV emulator, screenshots | Whole lines drawn on the picture |
| VobSub in the file (Casino Royale), ASS in the file over a seek's remux (Moon Knight) | Google TV emulator, real application, screenshots | Drawn |
| Every PGS class, plans | Google TV emulator | H.264 + AC-3 or E-AC-3 + PGS: audio conversion, video kept (before: video transcode). HEVC Main 10 + PGS: video transcode, as the emulator has no 10-bit HEVC decoder; PGS external, not burned in |

**Format fixtures.**

| Device | Result |
| --- | --- |
| Windows (`FormatFixtureTest`) | 7 of 7 read, planned Direct Play, played in hardware (`d3d11va-copy`); HDR10 and HLG read and planned tone-mapped |
| Fire TV | 7 of 7 as planned: AV1 to 4K, HEVC 4K, VP9 in hardware; HLG not shown on this display, said; TrueHD silent and reported. One run of three, the first AV1 clip after an install did not start within 45 s; the next two passed |
| Xiaomi Pad 7 | 7 of 7: AV1 1080p in software, 4K AV1 refused by its declared limit, HEVC 4K HDR10 and HLG in hardware, VP9 in hardware |
| Google TV emulator | 7 of 7: AV1 in software, HEVC Main 10 refused (no decoder), VP9 |
| Nothing Phone 2a | Not run: not attached |

**Found and decided:** Android's own extractor drops a TrueHD track from a local file's probe (the platform lists only the video); Media3 sees it and reports it at playback, and the no-sound notice says so. Fire OS 8 reports no VP9 profile. Neither is changed (DECISIONS.md, "M2P's last decisions").

**Info.** `PlaybackReportTest` (10), `ScreensTest.playbackInfoSaysHowItPlaysAndWhy` rendered and reviewed; the menu test walks to it. The report is also written to the diagnostics log beside "Planned against actual".

**Security.** The test account's password, the server's address, and its host are in no tracked file and nowhere in unpushed history; 42 scratch logs held no password or key. The one key-shaped value in the tree is a test's made-up one, there to show the report leaves keys out.

**Suites at closure.**

| Suite | Result |
| --- | --- |
| Windows `check`, with the live server | 1,082 tests, 16 skipped, 0 failed |
| Windows real-window tests (`-Preflux.windowTests=true`) | 41, 3 skipped, 0 failed. Skipped: the controller test (none connected), the physical Linux gate (refuses Windows), the teardown test (on request) |
| Android unit tests | 50 of 50 (in `check`) |
| On-device: fixtures, PGS attachment, seekability | Fire TV, Pad 7, Google TV emulator: 5 of 5 each |
| Linux container | Not rerun at closure: the Linux path is the desktop JVM's, which `check` covers; physical Linux is waived |

## M3: navigation and source libraries

Verified from 2026-11-02 to 2026-11-07, on the tree that closes M3 (`feature/engine-foundation`, unpushed). Specification: NAVIGATION.md. Devices ran a side-by-side debug build (`dev.reflux.m3`, built and reverted, never committed), so the library on each device's own Reflux was not touched; the side-by-side apps were removed afterwards, their server session signed out first.

**Phone-specific M3 navigation validation was performed on the physical Nothing Phone 2a** (the user made it available again; an Android phone emulator was booted for it first and set aside, unused for results). This is M3 navigation and source validation only: it says nothing new about the Phone 2a's decoders, audio, or performance, which M2P covered. Its Wi-Fi was off and was turned on for the walk and off again afterwards, as was its screen timeout.

**Physical and emulated devices, with the live Jellyfin server as `reflux-test` (three libraries: Marvel 38 films, Movies 150 films, Shows 38 series).**

| Device | What was walked | Result |
| --- | --- | --- |
| Nothing Phone 2a (A142, Android 16), touch | Upright: four icons along the bottom (Home, Movies, Shows, Search; names in the accessibility tree, no words drawn), Downloads and Sources at the top. On its side: a compact rail of six icons. Sources: library switches; the Movies library off left unified Movies at 38, the server off left it empty, on again 38 (the library's own switch kept), the library on 187. The Shows library off: its own page "Not in your library · 38 titles"; Dexter from it, Season 3, S3 E1 played; Back from the player on Season 3; Back to the Shows library page, then Sources. Search "casino" → Casino Royale → Back to Search with the query. Downloads and Back to Home | Passed |
| Xiaomi Pad 7 (HyperOS, Android 16), touch | A rail with names. The card's three libraries with switches; from a state with the server off and Marvel and Shows off: unified Movies and Shows empty; server on: Movies 150, Shows empty; Marvel on: 187 (150 + 38, one film in both libraries is one work); Shows on: 38. Marvel off and its page opened: tabs for every library, the out-of-library note, real artwork; Ant-Man played from it; Back to its details, then the Marvel page. The Boys and Dexter from Shows; Dexter Season 3, S3 E2 played; Back from the player on Season 3, scroll kept, "Continue S3 · E2". Search → result → Back with the query. Downloads, Back to Home | Passed |
| Fire TV Stick 4K Plus (Fire OS 8.1, by remote over Wi-Fi adb), local folder `RefluxSmoke` | First run with Reflux's own folder picker by remote. The rail: Left enters on the current destination, Up and Down one stop at a time, OK goes there with focus in the page. Shows → Test Show → S1 E2 played → Back to the same row → Back to the grid on Test Show → Back to the rail on Shows → Back to Home on Play. Sources: the folder's switch off; Down goes to Open; its page "Not in your library · 6 titles"; Big Buck Test played from it; Back to Play, to the page, to Sources on Open; unified Movies empty; switched back: 2 titles. Left from the switch to the rail on Sources, Right back to the switch | Passed after two fixes (below) |
| Google TV emulator (API 36), by D-pad | The same rail checks; the server's card walked switch → Marvel → Movies → Shows → Open and back; the Shows library off and its page; Dexter from it played; Back from the player to the row, to the page, to Sources on "Open Shows"; Left to the Shows switch, on again, unified Shows 38; Back on a destination to the rail, then Home. A Plex server added through the app's own form ("Connect to Plex", no account): "Plex · 3 movies · 1 show"; unified Movies 187 → 190; its card lists its three libraries | Passed |
| Google TV emulator | The instrumented suite (`am instrument`, side-by-side build) | 41 tests, 0 failed |

**Found on devices and fixed (each with a regression test):**

- Fire TV: on first run, after the folder was added, focus stayed on the rail's top item, which the toolkit had picked, instead of the new Home's Play. A page that arrives or gains its main action takes focus again unless the viewer is already in the page (`TelevisionTest.aFolderIsChosenWithTheRemoteAlone`, re-walked on the stick).
- Fire TV: Left from a source's switch on the centred Sources page went up to Add media, not to the rail; Right from the rail went to the page's main action, not back to the switch. Left that does not land strictly left goes to the rail, and Right returns to the control focus left from (`NavigationTest.leftFromAnywhereInACentredPageIsTheRailNotTheButtonAboveIt`, re-walked on the stick).
- Google TV emulator: a source's page ordered works by plain title ("The Boys" under T), unlike the unified grids; it uses the library's sort key now.
- In the test-only UI pass, before devices: the tablet rail's items were wider than the rail; a television's rail item changed from one control to another as the rail widened, which would have dropped focus.

**Plex, against a real Plex Media Server 1.43.5 in Docker** (`plexinc/pms-docker`, unclaimed, letting the local network in without a token; media generated with ffmpeg: a one-minute film with an English `.srt`, a six-minute film, a second film library, a two-episode series; libraries made through its API with no metadata agent). `REFLUX_PLEX_TEST=http://127.0.0.1:32400 ./gradlew :desktop:engine:test --tests '*PlexLiveTest*'`: connected without signing in; libraries Films, TV, Kids with their kinds; the films and the series' two episodes; MKV, H.264 640x360, AAC English, the `.srt` as an English SRT; the file served (206) and its subtitle read; planned Direct Play; the played mark adopted by a scan and cleared; a 3-minute stop told to the server and read back as 180,000 ms; a library out, the source out, still browsable, the library's switch kept. Passed, after two fixes it found: a title "Plex Kids Film (2019)" with the year in it (from a server without an agent) is now read as title and year, and a container's generated host name is not taken for the server's name. Plex keeps no position in a one-minute item; the live test uses the six-minute film for positions. The container and its image were removed afterwards.

Not verified for Plex: a plex.tv account sign-in or a shared server's token against the real service (fake-server tests only; no account was used), a claimed server, other Plex versions, and Plex from a physical device.

**Suites at closure.**

| Suite | Result |
| --- | --- |
| Windows `check` (with the live Jellyfin server; Android module included) | 1,127 tests, 17 skipped, 0 failed (the 11 window tests in `desktop:app` run on request, below; the gated Plex live test without a server named; physical controllers) |
| Windows real-window tests (`-Preflux.windowTests=true`) | 41, 3 skipped, 0 failed |
| Linux real-window tests, `tools/ci` container (`window 1`; virtual, as accepted for M2P) | 41, 5 skipped, 0 failed |
| UI tests (`:ui:jvmTest`, inside `check`) | 288 tests, 0 failed (`NavigationTest` 13, `SourcesUiTest`, `HandheldLayoutTest`, `AccessibilityTest`, `TelevisionTest`, and the rest updated to the six destinations) |
| Plex live test against the Docker server | Passed (above) |
| Google TV emulator instrumented suite | 41, 0 failed |

**Not verified in M3:** a screen reader walk (TalkBack on the Pad, or a desktop reader) over the new navigation, whose names, roles, and selected states are covered by the automated tests and were read from the devices' accessibility trees; physical Linux (waived as for M2P); a physical Google TV device; physical controllers (the bumper mapping is tested, none connected).

## M4: Visual System 2.0

Verified on 2026-11-07, on the tree that closes M4 (`feature/engine-foundation`, unpushed). Specification: DESIGN.md, "Visual System 2.0 (M4)". Devices ran a side-by-side debug build (`dev.reflux.m4`, built and reverted, never committed), so the library on each device's own Reflux was not touched; the side-by-side apps were removed afterwards, their server session signed out first (by removing the server in the app), and every temporary file on the devices deleted, the credentials file the live runner writes among them. Neither the Phone 2a nor the Pad had Reflux's own build installed during M4; the Fire TV's was not touched.

**Automated.**

| Suite | What it covers | Result |
| --- | --- | --- |
| `design` (`VisualSystemTest.kt`: `ColorRolesTest`, `MaterialTierTest`, `ScaleTest`, `MotionTest`) | Roles derived from the palette and the high-contrast roles brighter; the tier chosen from blur, reduced transparency, high contrast, low memory, and television; every material's scrim holds 4.5:1 for secondary text over white, grey, black, and saturated backdrops on every tier; the backdrop measured from pixels; type hierarchy, radius family, elevation order; the television motion profile does not travel and reduced motion is immediate | 36, 0 failed |
| `library` | Migration 5 (`setting`): a library from before it keeps everything and starts with the defaults | 190, 0 failed |
| `ui` (`VisualSystemTest`, 10 new) | The hero runs under the sidebar and rail on PREMIUM and stays beside an opaque one on BASIC; the hero's text clear of the rail; Media Atmosphere off by default and a wash when on, an ordinary switch, remembered; edge to edge (content under the bars, controls clear of them); the selected destination shown by a pill and a bar, not color; a television's rail quiet closed and material open; high contrast strengthening text, edges, and the focus ring; artwork read for its color once, not on every visit; reduced motion making every new transition take no time. Every earlier UI test unchanged | 298, 0 failed |

**Devices.**

| Device | Tier | What was looked at | Result |
| --- | --- | --- | --- |
| Xiaomi Pad 7 (HyperOS, Android 16, 144 Hz), live Jellyfin as `reflux-test` | PREMIUM | Edge to edge: the hero under the status bar, its icons over the protecting shade; the frosted rail over Casino Royale's hero with the title and actions clear of it; Movies grid scrolling under the protected status bar; a work's page. Accessibility tree: one node `selected=true` (the current destination), every destination named | Passed |
| Xiaomi Pad 7, performance (`dumpsys gfxinfo`) | PREMIUM | Home scrolled: 822 frames, 2.68% janky, p50 14 ms, p90 28, p95 46, p99 97. Movies grid: 1,676 frames, 0.36% janky, p95 40, p99 73. Memory (PSS) 364 MB | Within budget |
| Nothing Phone 2a (A142, Android 16, gesture navigation), by wireless adb, live Jellyfin as `reflux-test` | PREMIUM | Upright: Casino Royale's hero running under the status bar, its icons legible over the protecting shade; Downloads and Sources below the status bar; the floating bar of four icons clear of the gesture handle, Home a lit pill; Movies tapped: the pill moved, the grid scrolling under the bar. On its side: the compact rail of six, clear of the camera cutout, Movies selected; Home's hero running under the frosted rail with title and actions clear of it. Shows, then Back: Home selected again, still in Reflux. Accessibility tree, both orientations: exactly one `selected=true` node, the current destination's tab, every destination named | Passed |
| Nothing Phone 2a, performance (`dumpsys gfxinfo`) | PREMIUM | Movies grid (187 titles) scrolled twelve times: first pass, artwork loading, 1,154 frames, 7.28% janky, p50 15 ms, p95 73, p99 129; again, warm, 2,026 frames, 0.44% janky, p50 14, p90 36, p95 44, p99 69. Memory (PSS) 371 MB | Within budget |
| Fire TV Stick 4K Plus (Fire OS 8.1, by remote over Wi-Fi adb), a local folder chosen with Reflux's picker by remote | BASIC | Opaque picker and panels; Home's hero beside the rail with focus on Play; the rail opening as an opaque panel with the names, the current destination as pill, bar, and ring | Passed |
| Fire TV Stick, performance, the same D-pad journey on the M3 and M4 builds | BASIC | M4: 201 frames, 85% janky, p50 44 ms, p95 133, p99 200, 169 MB. M3: 167 frames, 97.6% janky, p50 36, p95 250, p99 500, 152 MB. Debug builds are slow on the stick in both; M4 is not worse | No regression |
| Google TV emulator (API 36, SwiftShader), a local folder chosen by D-pad | ENHANCED | Frosted folder picker; Home's hero running under the closed rail; the rail opening by Left into a frosted panel with rounded trailing corners and the names; pill, bar, and ring on Home. D-pad journey: 197 frames, 26% janky, p95 65 ms, 108 MB (software rendering: functional evidence, not performance) | Passed |
| Google TV emulator | ENHANCED | The instrumented suite (`am instrument`, side-by-side build) | 41 tests, 0 failed |

**Found and fixed during M4:**

- A pressed control shrank (a press scale), which changed the focused bounds mid-gesture, so the list that keeps focus in view scrolled out from under the finger and the tap was lost (`EpisodeVersionsUiTest.theVersionsAreBeneathTheEpisodeOnAPhone`). Pressed is now a dim that never changes size.
- The hero's scrim was darkest under the rail, which hid the artwork the rail was meant to sit over; it is lighter under the navigation, with the text's own scrim unchanged.
- Each image was sampled twice (once for its hue, once for its brightness); it is read once for both.

**Suites at closure.**

| Suite | Result |
| --- | --- |
| Windows `check` (with the live Jellyfin server; Android unit tests included) | 1,157 tests, 17 skipped, 0 failed (`design` 36, `library` 190, `ui` 298, `core` 324, `android:app` 50, and the rest; the skips as at M3: the 11 window tests in `desktop:app`, run below, the gated Plex live test, physical controllers) |
| Windows real-window tests (`-Preflux.windowTests=true`) | 41, 3 skipped, 0 failed |
| Linux real-window tests, `tools/ci` container (`window 1`; virtual, as accepted for M2P) | 41, 5 skipped, 0 failed |
| Google TV emulator instrumented suite (side-by-side build) | 41, 0 failed |

**Not verified in M4:** a screen reader walk over the new surfaces, whose names, roles, and selected states are covered by the automated tests and the devices' accessibility trees; physical Linux (waived as for M2P); a physical Google TV device; release-build performance (M6, M7).

## M5: Library depth

Verified on 2026-11-07 and 08, on the tree that closes M5 (`feature/engine-foundation`, unpushed). Specification: METADATA.md, "Library depth (M5)". Real media is the developer's Jellyfin server (Jellyfin 12.2, the `reflux-test` account): 187 films, 38 shows, 63 seasons, 812 episodes. Everything against it was read-only (asserted: every request a GET); nothing on the server or in its media folders was changed, and every session the tests opened was ended. Devices ran a side-by-side debug build (`dev.reflux.m5`, built and reverted, never committed); its server sessions were signed out by the application ("ENDED") before it was uninstalled, and no temporary file was left on a device.

**Automated (new in M5).**

| Suite | What it covers | Result |
| --- | --- | --- |
| `core` `ProvenanceTest` | Source first, provider filling gaps; names follow identity; the user's choice outranks every source; two servers resolve the same in either order; blank and zero are not values; artwork precedence; an identification drops the IDs it corrects | 7, 0 failed |
| `library` `LibraryDepthTest` | A server's description without a provider; provider names and fills; scan-order independence of metadata and artwork; a server away keeps what it said; Identify outranking the server and surviving rescans, refreshes, and unification; a refresh never searches; a server's provider ID replacing a weaker guess; a failed refresh changing nothing (watch state untouched); seasons and episodes described and pictured, a provider still filling a missing one, a season refresh reading one season; staleness a few at a time; online lookups off asking nothing; a removed server taking its descriptions; offline re-identification keeping them | 13, 0 failed |
| `library` `ProviderGateTest`, `ArtworkCacheTest` (4 new), `MetadataMigrationTest` | Coalescing, memory, backoff and its growth, spacing; one download per picture at a time, failures not retried at once, provider pictures not fetched with lookups off, accounting, nothing written beside media; migration 6 keeping watch state, sources, favorites, settings, and metadata, and marking the user's choices | 5, 4, 1, 0 failed |
| `sources:jellyfin` `JellyfinDescriptionTest`, `sources:plex` `PlexDescriptionTest`, TMDB | Descriptions, seasons, and (Plex) people read with the catalog; Jellyfin never asked for people; a server without seasons still lists episodes; TMDB end dates, networks, season dates, episode ratings, guest cast | 3, 2, extended, 0 failed |
| `ui` `LibraryDepthUiTest`, `FormattingTest` | A show's aired years and a season's own name and story; stills, and a show's backdrop for episodes without one; an episode's date and guest cast; Identify's "Refresh details"; the online switch, and none without a provider; a season's story at tablet (1164 by 777 dp) and phone sizes | 7, 1, 0 failed |

**Real library, desktop** (`LibraryDepthLiveTest`): scan completed; films 100% described by the server; shows 100%; seasons 100% with posters, 79% with overviews; episodes 100% with stills, 99% with overviews. Pictures needed by a page fetched once each, decoded, and served again with no request; with the server away, the descriptions and kept pictures stayed.

**Performance, against M4 on the same server** (M4 measured from commit `c757809` in a scratch worktree; same machine, same test):

| Measure | M4 | M5 first cut | M5 |
| --- | --- | --- | --- |
| Scan of the whole server | 2.7 to 3.2 s | 25 to 29 s | 2.9 to 3.5 s |
| Every film, then every show's page, five times | 16.6 s | 27 s | 6.4 to 6.9 s |
| The same 16 pictures from the server in the cache | not measured (originals, as the first cut) | 6.3 MB | 2.25 MB |

Found and fixed by measuring: asking Jellyfin for people costs about 20 ms an item (films 0.5 s to 4.0 s, episodes 1.5 s to 17 s for one page; every other description field nothing measurable), so people are not asked for; descriptions kept in each version's row made every availability read scan larger rows, so they moved beside it; the source order was read twice per page read, and a show's page read its seasons one at a time (each read opens its own connection: about a millisecond a query, the dominant cost in a sampled profile).

**Devices** (`LibraryDepthOnDeviceTest` after `prepareTheInstalledApplication`, side-by-side build):

| Device | What ran | Result |
| --- | --- | --- |
| Nothing Phone 2a (Android 16), wireless adb | The live library on a real Android runtime: 187 films described by the server; 63 seasons with posters, 50 with overviews; 812 episodes with stills, 811 with overviews; every show's page read in 1,550 ms; six pictures fetched through Android's HTTP stack in 485 ms, each decoded at 780 px | Passed. No visual walk: the phone was locked, and it was not unlocked |
| Fire TV Stick 4K Plus (Fire OS 8.1), Wi-Fi adb | The same: identical counts; every show's page in 3,519 ms (debug build); pictures 780 px. By remote: Home with the server's descriptions and an episode still in Continue Watching; Shows; The Boys: "2019–2026 · TV-MA · ★ 8.4 · Prime Video · 1 season", Season 1's own poster, "2019 · 8 episodes", and its overview; episode rows with stills, runtime, air date, and synopsis; focus from the season tab straight to the first episode, past the season's text | Passed |
| Xiaomi Pad 7 | Not run: installing a new package there needs an approval on its screen. Tablet layout is covered by the render at its size (above) | Not verified on the device |
| Google TV emulator | Not run in M5 (no M5 code is television-specific; the Fire TV covered the television layout with real media) | Not verified |

During the Fire TV walk, a remote press meant for the navigation reached Resume and played about a minute of a film on the `reflux-test` account before it was closed with Back; that account's position in that film moved by about a minute. Focus was checked before every later press.

**Not verified in M5:** TMDB against the live service (no credential; M7); Plex against a real server (fake-server tests); the Pad 7 and the Google TV emulator (above); a screen reader walk (the season's text is one merged node, covered by the automated semantics); Windows real-window tests (they put windows on the developer's screen and M5 changed nothing in the desktop shell; the Linux container's window round below ran instead); physical Linux (waived as for M2P).

**Suites at closure.**

| Suite | Result |
| --- | --- |
| Windows `check` (with the live Jellyfin server; Android unit tests included) | 1,202 tests, 17 skipped, 1 failed: `SourcesUiTest.aServerThatIsAwayOrStopsAnsweringIsRemovedAtOnce` timed out waiting for a fake server's films under the loaded run; it passed alone three times and with its class, and takes 31 s on M4 against 26 s on M5, so it is the load-sensitive class already in M6's intermittent item, not a regression. `core` 332, `library` 213, `ui` 306, `sources:jellyfin` 62, `sources:plex` 20 |
| Linux, `tools/ci` container (`all 1`) | Not completed: started after the Windows run with 7.4 GB free and capped at 6 GB, it was stopped by Claude Code's low-memory reaper during the headless check. The partial log showed UI timeouts under that pressure (`AboutUiTest`, which passed on Windows). Not rerun. M5 adds no Linux-specific code; the same JVM suites passed on Windows. The Linux window round of M4 is the latest |
| On devices, `LibraryDepthOnDeviceTest` | Phone 2a and Fire TV, passed (above) |

## M6: Release engineering, security, legal (2026-11-08)

All on the developer's Windows 11 machine and the `tools/ci` Linux container unless named. Nothing was pushed, tagged, published, or submitted to a store.

**Release dry run** (RELEASE.md, "Release system"): a fresh clone of `9a95fe8`, `tools/release/release.py build` with the throwaway dry-run key, the Linux archive built in the container from the same commit and `--add`ed.

| Artifact | Bytes | SHA-256 |
| --- | --- | --- |
| `reflux-0.9.0-alpha.2-android.apk` | 6,429,289 | `93acfaedbc1128caddbfe5c81cc551b958db7b052d08e0e01ff16637754cd4f3` |
| `reflux-0.9.0-alpha.2-android.aab` | 11,141,390 | `11a068bf9e00efa7abd942e9f5a229b254f888a7f5060f66494dd7eed4ab4c96` |
| `reflux-0.9.0-alpha.2-windows-x64.zip` | 127,683,779 | `97cc763b765e316da3309c6ec65309e0f0f1c0216d825d15f56e71c2501de3ba` |
| `reflux-0.9.0-alpha.2-linux-x64.tar.gz` | 91,103,684 | `9bb209b68b7251f7a56389b4de9387fd86e3170fd31e16aa5e888dfe6e87aba4` |

- `release.py verify` passes on Windows; `sha256sum -c SHA256SUMS` passes on Linux; the manifest and provenance name `9a95fe8` and an unmodified tree. One flipped byte in the APK makes `verify` fail.
- The APK: `dev.reflux`, `versionCode` 90002, `versionName` 0.9.0-alpha.2, `targetSdk` 36, not debuggable, signed by the dry-run certificate only.
- The dry runs found and fixed: `SHA256SUMS` written with CRLF (`sha256sum -c` failed on Linux; `dfb7056`), a Windows checkout's line endings marking the Linux build "modified" (`4da58cc`, `07cfa2f`), jlink needing `objcopy` in the container (`23cdd1e`), and the tar.gz losing the launcher's executable bit (`9a95fe8`).
- Website release routing with the dry run's manifest staged in `release/published/` (then removed): Windows and Linux buttons point at the release asset URLs with size, SHA-256, and commit; Android, Google TV, and Fire TV point at the stores; with nothing staged, each section says nothing is published.

**Android release build on a fresh Google TV emulator** (new AVD `Reflux_TV_Release`, API 36, nothing installed before): installed the exact dry-run APK; launch, a local folder, Jellyfin (the live test server), SMB and WebDAV shares, playback from local, server, and SMB, subtitles, a download and playing it offline, search, server metadata and artwork, watch state kept across a restart. The minified paths that depend on keep rules (smbj, MBassador, Bouncy Castle) were exercised by the SMB share. The session's full `logcat` contains no credential. The release has no TMDB credential built in (`aapt2 dump resources` of the APK has no `reflux_tmdb_token`, nor anything named TMDB), so it makes no lookups.
- Lint on `release`: 0 errors, 16 warnings, each reviewed: 7 `UseKtx`, and one each of `OldTargetApi` (37 exists; Play requires 36), `ObsoleteSdkInt`, `InlinedApi`, `DiscouragedApi`, `IconMissingDensityFolder`, `PictureInPictureIssue`, `SelectedPhotoAccess`, `ExportedContentProvider` (the read-only artwork provider, intended), and `DataExtractionRules`.
- The physical devices followed the same day: "M6: the release on physical devices", below. The emulator did not stand in for them.

**Windows zip:** extracted to a new folder and launched; first run created `%LOCALAPPDATA%\Reflux`; a clip played to the end through the bundled `libmpv-2.dll` (H.264, hardware decoding) and was marked watched; extracting again over the folder kept the library. `Get-AuthenticodeSignature`: NotSigned. Microsoft Defender scan of the extracted folder: no threats. With the download mark (`Zone.Identifier`, zone 3) on `Reflux.exe`, SmartScreen showed "Windows protected your PC" and the app did not start until chosen; the prompt was closed, nothing was changed in Windows' settings. Smart App Control is off on this machine; its behavior for unsigned unknown apps is from Microsoft's documentation.

**Linux tar.gz** (the container, Xvfb and openbox): `bin/Reflux` starts and is running after 30 s; its log reports the version and commit, the display, and libmpv's decoders; a clip played to the end through the archive's own jars and the distribution's libmpv and was marked watched; removing the app folder and the data folder leaves nothing.

**Security:**
- Secrets: gitleaks over all 169 commits and the tree finds only fake values in tests (listed in `.gitleaksignore` with reasons). The live test account's address, user, and password (from `~/.reflux-live/`, compared without printing) occur in no commit, no file in the tree, and none of the four artifacts. No keystore, certificate, or `.env` is tracked.
- Redaction: Plex's `X-Plex-Token` header was not covered; added with a test (`RedactionTest.aPlexTokenHeaderIsRedacted`).
- Android manifest reviewed (RELEASE.md, "Android release build"); the desktop app opens no listening port except the loopback SMB stream server with per-file tokens.
- Website: `npm audit` 0 vulnerabilities; headers as WEBSITE.md lists, checked on the local production build; no source maps; the CSP allows no third-party origin.
- GitHub private vulnerability reporting turned on.

**Licensing:** `checkThirdPartyNotices` passes for the Android release and desktop runtime classpaths; the libmpv DLL reviewed for the LGPL (THIRD-PARTY.md); About page tests updated for the new texts.

**Tests:** the full Windows `./gradlew check` on the M6 tree (`9a95fe8` plus the documentation): 1,203 tests, 17 skipped, 1 failed. Both `SourcesUiTest` tests that timed out under load before M6 passed. The failure is new: `LibraryDepthUiTest.identifyRefreshesWhatAWorkIsMatchedTo` timed out (30 s) waiting for "Details and artwork refreshed"; rerun alone three times it passes (1.2 s). Carried to M7 as an intermittent test (ROADMAP.md, M6). Android unit tests (50) and lint passed in the same run.

**Final check** (after the device validation, on `3ac4fba` plus documentation only): the full Windows `./gradlew check`, 8 min 15 s: 1,203 tests, 17 skipped, 0 failed, both `SourcesUiTest` tests and `LibraryDepthUiTest.identifyRefreshesWhatAWorkIsMatchedTo` included. Across the two loaded runs: `SourcesUiTest` passed both, `LibraryDepthUiTest` failed one; neither is called fixed by this.

## M6: the release on physical devices (2026-11-08)

The dry run's APK itself (`reflux-0.9.0-alpha.2-android.apk`, SHA-256 `93acfaed…4cd4f3`, commit `9a95fe8`, re-checked against `SHA256SUMS` before installing), over USB adb to the phone and tablet and Wi-Fi adb to the stick. Identity read from each device before installing:

| Device | adb serial | Model, OS | Before | After install |
| --- | --- | --- | --- | --- |
| Nothing Phone 2a | `0005534AN000190` | `A142`, Android 16 (API 36), arm64 | No `dev.reflux` | `dev.reflux` 90002, not debuggable, signer `41952984` |
| Xiaomi Pad 7 | `852ec620` | `2410CRP4CI`, HyperOS, Android 16 (API 36), arm64 | A leftover `dev.reflux.test` only (removed) | The same. HyperOS's "Install via USB" prompt ignores injected input: the owner tapped Install |
| Fire TV Stick 4K Plus | `192.168.1.176:5555` | `AFTMA08C15`, Fire OS 8 (API 30), 32-bit | The debug `dev.reflux` (`DEBUGGABLE`) and `dev.reflux.test`: both uninstalled, with the owner's approval | The same |

Walked on each, with fresh data (first run): choosing a folder of generated media (the system picker on the phone and tablet, Reflux's own picker by remote on the stick) → the library built; local playback of a film and an episode; the film's English sidecar subtitle drawn; search across every source; Jellyfin (the live server, the test account; values typed from a file pushed to the device by adb's standard input, never on a command line) → 187 films and 38 shows with their artwork and server descriptions, a server film played; an SMB share (the `tools/ci` share) added and listed; its film downloaded (16 MB, "SD"); the download played with the network off (phone: Wi-Fi and mobile data off; tablet: Wi-Fi off; stick: the share's server stopped, since its adb runs over Wi-Fi); force-stopped and reopened → Continue Watching, Recently Added, the download, and the sources all there; About shows `0.9.0-alpha.2 (9a95fe8)`; Sources has no "Online metadata & artwork" switch (TMDB disabled in release). Navigation by touch on the phone (both orientations) and tablet, by D-pad on the stick with focus read before every OK; every navigation and title control carries an accessible label (read through `uiautomator`).

Logs (`logcat -b all` from the first launch, scanned without printing values): no crash, ANR, `ClassNotFoundException`, `NoSuchMethodError`, or other R8 symptom from Reflux on any device. The server password, the server's address, and any token: 0 occurrences anywhere. The share password and user name occur once on the tablet, in adbd's record of the `input text` command used to type the share password, not from Reflux. The stick's two `ClassNotFoundException` lines are Amazon's `com.amazon.alexamediaplayer.runtime.ftv`.

**Release → release upgrade:** a second release from a clean clone of `3ac4fba`, by `release.py build` with the same dry-run key (`verify` passes; APK `6483b554…3797f`), installed over the first with `adb install -r` (the tablet by `pm install -r`; it did not prompt again). Same version and `versionCode` (the version is not changed for a dry run), same signer, first-install time kept. Afterwards on all three: About shows `(3ac4fba)`; Sources, Downloads, and Home read the same as before the update (compared screen by screen; the differences were scroll positions, checked directly); the favorite set on the phone and tablet is kept; the tablet resumed Harbour Lights at 0:50, where it was left; the download plays on each. Logs after the update: as above.

Seen, for M7 (not release regressions as far as known): on the phone, with English chosen mid-film, a seek to 0:12 inside the file's single 0:01–0:30 cue drew nothing at 0:24; seeking to the start drew it (tablet and stick drew it from the start too). On the stick, a subtitle chosen in one play was Off when the same film was played again.

Mistake made and corrected: the phone's `svc wifi enable` after the offline step did not take; Wi-Fi stayed off for about 35 minutes (the phone used mobile data) until it was noticed and turned back on.

The release stays installed on the three devices with the walk's test data (the RefluxM6 folders on the phone and tablet, the test account's Jellyfin session, the SMB share entry).

## M7: Compatibility, release proof, brand, website, beta readiness (2026-11-09)

On the developer's Windows 11 machine, the `tools/ci` Linux container, the Google TV emulator, and the physical Fire TV Stick 4K Plus and Nothing Phone 2a. Nothing was pushed, tagged, published, deployed, or submitted to a store; the version stays `0.9.0-alpha.2`.

**Release rehearsal from a clean clone** (gates 5 and 6): a fresh `git clone` of `4d6538e`, fresh libmpv source downloads, `tools/release/release.py build` with the throwaway dry-run key, the Linux archive built in the container from the same clone and `--add`ed.

| Artifact | Bytes | SHA-256 |
| --- | --- | --- |
| `reflux-0.9.0-alpha.2-android.apk` | 6,569,275 | `23c189034d68f56e352422b12ba09f93efa40efa46ec9bb6cfcd5172a94592cd` |
| `reflux-0.9.0-alpha.2-android.aab` | 11,298,803 | `ace1aeea437140cd81b51ab9203b0f8423307a912268c6b125fff034aba341cd` |
| `reflux-0.9.0-alpha.2-windows-x64.zip` | 105,941,776 | `bd71bb2f19b2383dfc55c1d8691442361c153c0b8f461872e8bcb62e6f8fa037` |
| `reflux-0.9.0-alpha.2-libmpv-source.tar.xz` | 62,568,712 | `53853ac9089204270fcd4db16ce6e3d5660af0f41dde765c3fb12a489d1c9bfa` |
| `reflux-0.9.0-alpha.2-linux-x64.tar.gz` | 91,362,627 | `35f57393aab8f98c56ae82174689c96ee553e674bdd6985cf67baa4ad9aef5f0` |

- `release.py verify` passes: every `SHA256SUMS` entry, the in-toto provenance's subjects and commit (`4d6538e`, unmodified tree), the APK's package (`dev.rsxdevelops.reflux`), `versionCode` 90002, not debuggable, 16 KB zip alignment, the libmpv DLL inside the zip equal to the provenance's digest and the pin, and the source bundle present. One flipped byte makes it fail. `sha256sum -c SHA256SUMS` passes on Linux. `tools/release/test_release.py`: 7 self-tests pass.
- The manifest records `tmdb: "no credential"` (none was supplied locally). With a fake `REFLUX_TMDB_TOKEN` the release APK carries it and the desktop build packs `provider.properties`; without one neither has it (`aapt2 dump resources`, the jar listing).
- An earlier rehearsal (`9a81c55`) produced the same libmpv DLL and source bundle bytes.
- Not run: the hosted workflows. `ci.yml`, `libmpv.yml`, and `release.yml` pass `actionlint` (every action pinned by SHA); their first GitHub run and the signed attestations (`actions/attest-build-provenance`, checked by `gh attestation verify --signer-workflow` in the verify job) need the push.

**Windows libmpv from source** (gate 7): `tools/libmpv` (Docker, Ubuntu by digest, MinGW-w64 posix GCC, every source pinned by SHA-256) builds mpv 0.42.0 with FFmpeg 9.0.2 and its libraries, LGPL only (the build fails on any GPL or nonfree option and on an import outside the allowlist). Four builds, one from a clean clone with fresh downloads, gave the same DLL (`e52f3f45…90a4`) and source bundle (`53853ac9…9bfa`). With it the 53 engine tests pass, the packaged app reports hardware decoding and played a clip to the end, and `EmbeddedPlaybackTest` (a real window) passes. Defender: no threats; `Get-AuthenticodeSignature`: NotSigned. The notices list every library in the DLL with its license text (`legal/components.txt`, `legal/licenses/`); `checkThirdPartyNotices` passes. SmartScreen was not re-run (unchanged from M6: the archive is unsigned, so its prompt is expected; Reflux does not tell anyone to turn it off).

**Package identity** (gate 1): the release APK and AAB are `dev.rsxdevelops.reflux` (aapt2); the CI job checks it; `release/stores.json` and the website's store check use it; the on-device runner targets `dev.rsxdevelops.reflux.test`. The Kotlin namespace stays `dev.reflux.*`.

**Deferred playback and stability** (gate 8):
- Phone 2a subtitle after a seek: reproduced by two new on-device tests (`SubtitleSeekOnDeviceTest`), both failing before the fix; Media3's extraction-time subtitle parsing dropped a cue that had already begun where the seek landed. With legacy decoding (`experimentalParseSubtitlesDuringExtraction(false)` and the text renderer's legacy mode) both pass, 2 of 2. The whole on-device suite: 44 OK on the Phone 2a, 44 OK on the Fire TV Stick.
- Fire TV subtitle Off on replay: classified, not a defect and not Fire TV's. Reflux keeps no per-work subtitle choice; in Auto mode with audio in the viewer's language the subtitle starts Off on every play. A product decision for M8 (remembering the choice per work).
- `LibraryDepthUiTest.identifyRefreshesWhatAWorkIsMatchedTo`: a layout race (Identify's results arriving moved "Refresh details" under the click). Reproduced 1 in 25 under load before the fix, 0 in 25 after, and 8 further loaded rounds clean. `SourcesUiTest`: 8 of 8 alone and 8 of 8 loaded rounds pass; no failure reproduced, kept watched.

**Support report** (gate 9): `SupportReportTest` (6) and `SupportReportUiTest` (2): addresses, server names, paths (Windows, UNC, Unix, with spaces), e-mail addresses, IPv4 and IPv6, host and port, cookies, long tokens, and the library's own titles and source names never appear; versions, capabilities, and stack frames do. On the Fire TV Stick at `4d6538e`: About → "Save a support report" wrote a file to `Download/Reflux` (Fire OS blocks `Android/data` even to adb), read back by adb: the last playback's facts and nothing private. Reflux sends nothing.

**Brand** (gate 2): six exploration rounds (`design/brand/exploration/`), the Reflux Loop chosen (`design/brand/README.md`); every platform's icon, the TV banner, the store art, and the website's icons generated from the one geometry by `tools/brand/`; seen on the emulator's launcher and TV home row, the Fire TV launcher, the Windows taskbar and window.

**Motion:** the first-run draw-in and the library loader seen on the Fire TV Stick and in the desktop app; with reduce motion, the still mark (desktop). The Android 12+ animated launch screen was not seen on the Google TV emulator (its PIN setup stood in front) and is the still mark on the Fire TV (Android 11) by design; it was seen on the Phone 2a and the Pad 7 in the final walk (below, "M7: the release on the Phone 2a and Pad 7").

**Fire TV Stick release walk** at `4d6538e` (the rehearsal's APK, after uninstalling the M6 `dev.reflux`, which a new package cannot update): cold start 1.25 s, the first-run mark, Reflux's folder picker by remote, the library built, a film played, About shows "Made by RSX Develops" and `(4d6538e)`, the support report as above.

**Website** (gates 3 and 4): `npm audit` 0 vulnerabilities; `npm run build`, `check-dist`, and `check-links` pass. Served by `scripts/serve.mjs` with `vercel.json`'s headers and checked in Chromium (Playwright) at desktop and phone widths: every route renders with no console error and no CSP violation, unknown paths answer 404, nothing overflows at 375 px, navigation crosses over with View Transitions, and with `prefers-reduced-motion` nothing is hidden or moving. Not deployed: the Vercel project exists with no deployment and no domain (owner actions, WEBSITE.md, "Deployment").

**Store and verification readiness** (gate 10): Google's and Amazon's current requirements, read 2026-11-08, are in RELEASE.md, "Store readiness (M7)" and "Android developer verification (M7)". The APK's manifest, permissions, exported components, backup rules, and network policy were re-checked for `dev.rsxdevelops.reflux`.

**Security:** gitleaks over the whole history and tree: one new finding, the support report test's fake `api_key`, reviewed and recorded in `.gitleaksignore`. The live test account's values occur in no commit, no file, and none of the 738 entries of the rehearsal's artifacts. The website ships no source map, environment variable, secret, or third-party origin.

**Tests:** the full Windows `./gradlew check` on `253f21f` (the last code change): 1,212 tests, 18 skipped, 0 failed; Android unit tests and lint pass. The Linux container (`tools/ci`, the CI job's steps) on a clean clone of `4d6538e`: `./gradlew check` 1,162 tests, 32 skipped, 0 failed; the real-window step 1 failed: `AboutWindowTest` found the About page's one identity stop announced "RSX Develops" before "Reflux" (the new wordmark's image description outranks the stop's texts in a merged node, for screen readers too). Fixed in `253f21f` (the maker row is the text "Made by RSX Develops" in its place; `AboutUiTest` asserts the stop leads with "Reflux"); `AboutWindowTest` and `AboutUiTest` pass on Windows. On a clean clone of `253f21f`: `check` 1,162 tests, 32 skipped, 0 failed, and the real-window step passes. The release rehearsal's artifacts are from `4d6538e`; `253f21f` changes only that row's semantics. Earlier Linux runs failed on three environment gaps present since M2P, fixed in M7: libmpv 0.37's missing `codec-profile` (the test is now version-aware), a headless focus wait in `AboutUiTest` (skipped on headless Linux CI, with the reason in the test), and `LICENSE` checked out with CRLF from a Windows clone (now pinned to LF).

**Live server:** `JellyfinExecutionLiveTest`'s remux evidence came back UNKNOWN once (the server's session reported late) and passed on both reruns.

**Not verified in M7**, carried to the M8 entry checklist: the hosted CI run and attestations (need the push); the live website and DNS; (the animated launch screen and the Phone 2a and Pad 7 release walk followed: below); TMDB against the live service (no credential here); a SmartScreen re-run; a physical Google TV, the Fire TV television, physical controllers.

## M7: the release on the Phone 2a and Pad 7 (2026-11-09)

The final M7 release walk on the two physical handhelds, with release APKs from clean clones signed by the dry-run key (`dev.rsxdevelops.reflux`, `versionCode` 90002, not debuggable). Identity read before installing: Nothing Phone 2a `0005534AN000190` (`A142`, Android 16, API 36) and Xiaomi Pad 7 `852ec620` (`2410CRP4CI`, HyperOS, Android 16, API 36). The M6 `dev.reflux` on each was left untouched beside it; the phone's debug `dev.rsxdevelops.reflux` and its test APK (from M7's on-device suite) were removed first, since a release signed by another key cannot update them. The walk found two defects, fixed and re-checked on the devices.

**Nothing Phone 2a**, the APK from `557a54b` (`28b07078…1c06`), fresh data:
- Launch: cold start 547 ms. The Android 12+ launch screen is visible: the mark's line draws itself and the system hands over to Reflux at about 0.55 s, before the line completes (the system decides; nothing waits for the animation). The first-run screen then draws the mark and settles into the solid mark.
- Every control on the first-run screen, Home, the navigation, Sources, a film's page, and About carries an accessible label (`uiautomator`).
- The system picker → `Movies/RefluxM6` → the "Building your library" loader → Home with both works.
- Local playback (PLAYING). English chosen mid-film, then a seek from 1:21 back to 0:12, inside the file's single 0:01-0:30 cue: the cue is drawn at 0:16. This is the case that failed in M6.
- Jellyfin (the test account, typed on the device from a file sent over adb's standard input, then deleted): 187 movies and 38 shows, server artwork and details. A server film played: this phone has no AC-3 decoder, so Reflux planned and got the server converting the sound and keeping the picture.
- The `tools/ci` SMB share added (`//192.168.1.5:1445/Movies`, 1 movie); search finds it beside Jellyfin episodes (11 results); its film downloaded (SD, 16 MB) and played with the share's server stopped.
- About shows `0.9.0-alpha.2 (557a54b)` and the RSX Develops wordmark, read as "Made by RSX Develops".
- "Save a support report" opens the share sheet (not shared). It holds no server address, user, password, title, or path, and records the server playback as planned against actual.
- With "Remove animations" on, the launch screen shows the complete still mark at once.
- **Found and fixed:** a cold start with the 225-work library left Home blank for about 1.1 s. `5a55a0a`: Home shows the loader while it first reads the library, after 400 ms like every loader. `HomeLoadingUiTest` fails without the fix and passes with it.

**Xiaomi Pad 7**, the APK from `5a55a0a` (`d27902c7…1758`), fresh data; HyperOS installed it without a prompt:
- Launch: cold start 1,055 ms. The launch screen plays in full: the line draws, closes the loop, settles into the solid mark, and hands over; then the first-run draw-in.
- The tablet rail and every label as on the phone. The system picker → `RefluxM6` → the library built.
- Local playback; English chosen, then a seek from 1:00 back to 0:12: the cue is drawn at 0:14.
- Jellyfin connected (the keyboard covered the password field; the keyboard's Next brought it into view). Movies with server artwork and the server's watched mark; a server film played (Direct Play, hardware decoding).
- Search found Harbour Lights with its progress ("1 min left").
- About shows `(5a55a0a)` and "Made by RSX Develops".
- A cold start with a populated library now shows the loader, then Home.
- **Found and fixed:** the support report said `Display: 3200x2136, <redacted>`. The Pad's list of HDR kinds (`SDR/HLG/HDR10/HDR10_PLUS/DOLBY_VISION`, 38 characters) was taken for a token. `11ec1af`: the ranges are spaced, and token redaction is unchanged. `SupportReportTest` renders a fully equipped device and finds nothing redacted.

**The final code, `11ec1af`** (`79e12450…922d`), installed over both with `adb install -r` (same signer; each library, connection, and progress kept):
- Pad: About shows `(11ec1af)`. The report reads `Display: 3200x2136, SDR / HLG / HDR10 / HDR10_PLUS / DOLBY_VISION` with nothing redacted.
- Pad with "Remove animations" on: the still mark at launch, then the still mark with "Loading your library…", then Home.
- The phone: installed. Its secure lock screen came on before the re-walk, so it was not walked on `11ec1af`. That build changes only Home's loading state and the report's display line, both seen on the Pad. The `557a54b` walk above stands for the rest.

**Logs** (`logcat -b all` from installation, scanned without printing values):
- Phone, 19,369 lines; Pad, 201,612 lines.
- No crash, ANR, `ClassNotFoundException`, `NoClassDefFoundError`, `NoSuchMethodError`, or other R8 symptom from Reflux. The phone's three class-loading lines are Samsung's `oneconnect`.
- The server's password, address, and host, the share's password, and anything token-shaped: 0 occurrences.

**Left as found:** "Remove animations" and the animation scales restored to 1.0 on both. Reflux `dev.rsxdevelops.reflux` stays installed with the walk's test data: `RefluxM6`, the test account's Jellyfin session, and on the phone the share entry and its download.

**Seen, not M7 regressions, for M8:**
- On the phone's Movies page, the transient "Fetching artwork" status runs under the Downloads button.
- A film's page moves its Play button down about two seconds after opening, as its versions resolve.
- After the keyboard is dismissed, the search field keeps focus and the navigation bar stays hidden until a second Back.

**The Phone 2a on the final code** (the APK from a clean clone of `cfb4865`, `3b011d29…e346`, installed over its release with `adb install -r`; the phone unlocked again). A focused check of what changed after its `557a54b` walk, nothing more:
- **Top corner (`cfb4865`).** The owner saw Home's Downloads and Sources buttons touching on the phone. They are now 14 dp apart in the same corner, at the same size and style. On the phone they measure 37 px apart (2.65 px per dp), and they read as two buttons over Casino Royale's hero art. `HandheldLayoutTest.thePhonesTopCornerButtonsStandApart` checks the gap, the 44 dp targets, and the corner on a 412 dp and a 320 dp phone (offscreen renders). A phone on its side uses the rail, which has no such pair.
- **Home loader (`5a55a0a`).** Cold start 849 ms: the launch screen drew the whole mark, then Home showed the flowing loader for the about 2 s the library took to read, then the library. It is no longer blank.
- **Support report (`11ec1af`).** The report reads `Display: 2412x1084, SDR / HLG / HDR10 / HDR10_PLUS`. It contains no server password, user, or host, no share address or password, no title, folder, or path, and nothing redacted that should not be.
- **Reduced motion.** With "Remove animations" on: the still mark at launch, the still mark with "Loading your library…" on Home, then the library. The setting was restored to 1.0.
- About shows `0.9.0-alpha.2 (cfb4865)` and "Made by RSX Develops". Every navigation control carries its label.
- Log since installing: 13,119 lines; no crash, ANR, or R8 symptom; the private values and anything token-shaped: 0.

**Final checks on `cfb4865`, the last code change:**
- **Windows:** the full `./gradlew check`: 1,216 tests, 18 skipped, 0 failed (8 min 6 s).
- **Linux:** the `tools/ci` container on a clean clone, with the Gradle build cache in its volume cleared first so that every test executed (none restored from cache). `./gradlew check`: 1,166 tests, 32 skipped, 0 failed (20 min 51 s). The real-window step passes: About, browsing, capabilities, the first run, offline, and embedded playback through libmpv in real windows; `PlannedPlaybackWindowTest`'s two tests are skipped as always without the live-server environment.
- **Linux memory:** an earlier run on `11ec1af` was killed when the host ran out of memory, because the container saw all 20 CPUs and Gradle ran that many test JVMs. The final run was limited as GitHub's runner is: 4 CPUs (which the JVM honours) and 8 GB. The container peaked at 4.6 GB; the host never had less than 4.0 GB free. Nothing was asserted less.
- **Release workflow:** `release.yml` changed at closure (`db968e6`, DECISIONS.md, "CI and release automation") so that every push is an attested release rehearsal; `actionlint` passes.

## M7: operational closure (2026-11-09)

The final source state is **`a905f27`**. The commit that records this section changes documentation only.

**What the first hosted runs found, and fixed** (each fix pushed and re-run on GitHub):

| Commit | CI | Found | Fix |
| --- | --- | --- | --- |
| `43b4725` | Failed: Check (windows-2025) | PowerShell, the Windows runner's default shell, split `-Preflux.android=false` into two arguments; this was the job's first hosted run | `a0ed9e5`: that step runs in bash |
| `a0ed9e5` | Failed: Check (ubuntu-24.04) | Three tests that move the clock past the player's controls hiding render every frame in software: 12 s here, 45 s in the 4-CPU container, over a minute on GitHub's runner. They hit the test library's one-minute budget for a test body | `38b76e0` passed the Compose harness a longer `testTimeout`. It did not apply (still "after waiting for 1m") |
| `38b76e0` | Failed: both Checks | The same three on Linux. `SourcesUiTest`, Windows: the "connected" notice lives 4.5 s of the test clock, which the runner spent in the preceding wait. `SourcesUiTest`, Linux: a re-added server's films did not arrive within 30 s; the only report was "Refreshing the library failed", its cause discarded | `a905f27`: `kotlinx.coroutines.test.default_timeout=5m` (proved to be the budget that applies: at 5 s a test stops at 5 s); the notice is watched from sign-in and must still be shown; the fake platform keeps each report's cause, and that wait prints them |
| **`a905f27`** | **Passed: all 7 jobs** | — | — |

Nothing was asserted less. The re-added-server case passed on `a905f27`, on GitHub and locally. It stays classified as the watched `SourcesUiTest` intermittent, now reproduced once on GitHub; a recurrence will say its cause. M8: "Stability: the M6 intermittents".

**Hosted runs of `a905f27`** (Rifaque/reflux, branch `feature/engine-foundation`):

- **CI 42622256663: success.** Check (ubuntu-24.04), with the real-window tests, 47 min; Check (windows-2025); Android (unit tests, lint, platform API, minified release, package identity); Website; Release tools; Workflow lint; Secret scan.
- **Release 42622256798: success** (the rehearsal mode, on push):
  - libmpv was restored from its cache in 12 s and checked against its pins.
  - Linux archive; Android and Windows signed by a throwaway key made in the runner; `release.py verify`.
  - GitHub build provenance for every artifact, each verified in the job with `gh attestation verify --signer-workflow .../release.yml --source-digest a905f27`.
  - No tag, release, secret, or uploaded app binary. The draft and its verification correctly skipped.
- **Windows libmpv:** passed on its first hosted run (`43b4725`): built from source and matched the pin. The hosted libmpv source bundle is byte-identical to the local builds (`53853ac9…9bfa`).

**Attestations of `a905f27`**, read independently from GitHub's attestation store by digest:

| Artifact | SHA-256 (hosted build) |
| --- | --- |
| `reflux-0.9.0-alpha.2-android.apk` | `0120be9e8c874d9aa7736055051068774c154aaf937fb00d349bd349e857107c` |
| `reflux-0.9.0-alpha.2-android.aab` | `5f8d46000388af94a69c69ebe2b336eb62d2a9f12df0d8f3015908c9cb1c6abd` |
| `reflux-0.9.0-alpha.2-windows-x64.zip` | `a99127df42cb6fbcad4b829557be34196432d7bebafd87de32b33f48c59e83d7` |
| `reflux-0.9.0-alpha.2-libmpv-source.tar.xz` | `53853ac9089204270fcd4db16ce6e3d5660af0f41dde765c3fb12a489d1c9bfa` |
| `reflux-0.9.0-alpha.2-linux-x64.tar.gz` | `33803e210ff831ba0b06d7d05846560aff729b14c58c86ddeb57ca1e12932312` |

- Each has a signed attestation: SLSA provenance v1, workflow `Rifaque/reflux/.github/workflows/release.yml@refs/heads/feature/engine-foundation`, source commit `a905f27`, run 42622256798.
- The libmpv source bundle also carries attestations from the earlier commits that built the same bytes.
- **Cryptographic verification on this machine:** the locally rehearsed `libmpv-source.tar.xz`, byte-identical to the hosted one, passes `gh attestation verify --repo Rifaque/reflux --signer-workflow Rifaque/reflux/.github/workflows/release.yml --source-digest a905f27…`. A copy with one byte changed has no attestation and fails.

**Final clean-clone release rehearsal of `a905f27`:** a fresh clone, unmodified; the Linux archive from the `tools/ci` container (4 CPUs, 8 GB); then `release.py build` with the dry-run key, adding the Linux archive.

| Artifact | Bytes | SHA-256 |
| --- | --- | --- |
| `reflux-0.9.0-alpha.2-android.apk` | 6,569,275 | `c29f52d52f853cb3a329fd50bd213b5bda1f130827b3292f51cff7df1e1cb61f` |
| `reflux-0.9.0-alpha.2-android.aab` | 11,298,590 | `b7c11e2d9afe657fb3bbb6094c2b2420f792de1a7121018beba4a22e1fd2ef57` |
| `reflux-0.9.0-alpha.2-windows-x64.zip` | 105,943,340 | `e84aebcfffe49e8677c75e8c6aa170c9b49260cb1576418e3c8b02f52969bc12` |
| `reflux-0.9.0-alpha.2-libmpv-source.tar.xz` | 62,568,712 | `53853ac9089204270fcd4db16ce6e3d5660af0f41dde765c3fb12a489d1c9bfa` |
| `reflux-0.9.0-alpha.2-linux-x64.tar.gz` | 91,364,527 | `c1248f1b26c1cdc658d5b63ef445af8c2280875f2c59d13328ba4f4b6b1cefef` |

- `release.py verify` passes, and `sha256sum -c SHA256SUMS` passes.
- The manifest and the in-toto provenance (SLSA v1) name `a905f2777a00…`; the provenance subjects equal `SHA256SUMS`.
- The APK is `dev.rsxdevelops.reflux`, `versionCode` 90002, `versionName` 0.9.0-alpha.2, not debuggable, with no TMDB credential (none supplied). It carries `a905f27` in `assets/build-identity.properties`, which About reads.
- The desktop jar's build identity is `commit=a905f27 | modified=false`.
- The `libmpv-2.dll` inside the Windows zip matches the pinned `dllSha256`, and the source bundle matches the pinned `sourceSha256`. The pinned build was reused, not rebuilt: it was reproduced four times, once from fresh downloads, and again on GitHub.
- One byte changed in a copy of any of the five artifacts makes `release.py verify` fail.
- The version stays `0.9.0-alpha.2`.

**Final Linux validation of `a905f27`:**
- The `tools/ci` container on a clean clone, with the Gradle build cache cleared, so nothing was restored from cache.
- Resources: 4 CPUs, 8 GB, and a watchdog that would stop it if the host's free memory fell under 2 GB. It did not trigger: the container peaked at 5.1 GB, and the host's free memory reached 2.3 GB at its lowest, from the owner's other applications.
- `./gradlew check`: 1,166 tests, 32 skipped, 0 failed (22 min 14 s).
- The real-window step passes (About, browsing, capabilities, embedded playback, offline; `PlannedPlaybackWindowTest`'s two skipped, as always without the live server).
- Two earlier runs were stopped for host memory: on `11ec1af`, a container that saw all 20 CPUs; on `a905f27`, beside other work. Neither was a test failure.

**Windows:** the full `./gradlew check` on `a905f27`'s tree: 1,216 tests, 18 skipped, 0 failed.

**Physical Android:** VERIFICATION.md, "M7: the release on the Phone 2a and Pad 7". The Phone 2a on `cfb4865` is the final code apart from the test-only commits after it. The Pad 7 and the Fire TV Stick were walked on `11ec1af` and `4d6538e`.

**Website:** built, checked in CI (17 pages and the 404 prerendered, `check-dist` 0 problems, 18 external links), and not deployed (owner actions, WEBSITE.md, "Deployment").

## M8: Viewer identity, profiles, navigation structure, website search (2026-11-11)

On the developer's Windows 11 machine, uncommitted on `feature/engine-foundation` from `17cd272`; a throwaway Jellyfin server in Docker. Targeted validation, as the M8+ test strategy asks (ROADMAP.md; AGENTS.md, "Milestone execution rules"): the modules M8 touched, locally; the full `check` is CI's on the next push.

**The account-isolation defect, reproduced and closed.** Before M8, a second Jellyfin user signed in after the first saw the first's history, and resuming it reported progress to the second's server account. `ProfilesTest.anotherViewerSignedInToTheSameServerSeesNothingOfTheFirst` walks exactly that with two accounts on one fake server: the second profile sees none of the first's titles, places, favorites, collections, pins, Continue Watching, or sources, and its server account is told nothing of the first's viewing (nor the first's of the second's).

| What | How | Result |
| --- | --- | --- |
| Every read model bound to the active profile | `ProfileIsolationTest.noReadGivesAProfileAnotherProfilesTitlesOrViewing`: two profiles, each with its own account on one server, and a shelf both share; movies, shows, recently added, Continue Watching, continuations, Watch Next, favorites, collections, franchises, title search (7 queries), smart search (5), diagnostics (4 lists), downloads, each source's own library, every show's detail and episode version choices, under each profile | Pass. Checked to fail: with visibility forced on for every profile, it and `ProfilesTest` fail (2 of 11), as they must |
| Every query in `Library.sq` bound to the profile | `ProfileIsolationTest.everyQueryIsBoundToTheActiveProfile`: every labeled query reading versions requires `visible` or `included`, every one reading viewer state names `profile_id`, or is on a listed installation-wide query with its reason | Pass |
| Every viewer-state table has `profile_id`; every table classified | `ProfileMigrationTest.everyViewerStateTableIsKeyedByProfile` | Pass |
| Migration 7 from a pre-profile library | `ProfileMigrationTest.aLibraryFromBeforeProfilesBecomesTheFirstProfileAndLosesNothing`: history, server baselines, favorites, collections, pins, and downloads become the first profile's; the server account the first profile's, the share everyone's; a second profile starts with nothing. The older migration tests (schemas 1 to 6) run through it | Pass |
| A playback stays with its profile | `ProfilesTest.aPlaybackWritesToTheProfileItStartedInWhateverIsActiveLater` (library) and `ProfilesUiTest.switchingProfileWhilePlayingStopsThePlaybackForTheProfileItWasFor` (the switch stops it; the stop is recorded for the profile it played for) | Pass |
| Clearing local history: local, and not brought back | `WatchReconcilerTest` (three new cases: unchanged record, first meeting after a clearing, undated record; news after it adopted; new viewing sent), `ProfilesTest.clearingLocalWatchHistoryIsLocalAndIsNotBroughtBack` (a real scan of an unchanged server adopts nothing and sends nothing; a later change on the server is adopted), `ProfilesUiTest.clearingWatchHistoryAsksFirstAndEmptiesContinueWatching` | Pass |
| Accounts | `ProfilesTest.anAccountIsOneProfilesAndCannotBeSignedInToFromAnother`, `aProfilesAccountsMustBeRemovedBeforeIt`, `deletingAProfileRemovesWhatWasItsOwnAndNothingElse` | Pass |
| PIN | `ProfilesTest.aPinIsCheckedSlowsGuessersDownAndIsNeverKept`: wrong entries slow down, the wait survives reopening the database, the PIN is in no byte of the database file; `ProfileRulesTest` (form, back-off, comparison); `ProfilesUiTest` (asked when chosen, a wrong one said, required at start, Back cannot get past it) | Pass |
| Launcher | `WatchNextTest.theRowIsTheActiveProfilesAndAProfileWithAPinShowsNothingThere`, `WatchNextLinkTest` (the profile-bound link, and links from before profiles), `ProfilesUiTest.aLauncherCardOpensInItsProfileWithoutAskingAndAPinStillAsks` | Pass (`WatchNextLinkTest` with the Android unit tests, below) |
| Per-account device identity | `ProfileRulesTest`, `JellyfinConnectorTest.eachAccountSignsInAsItsOwnDeviceNeverTheInstallations`, `PlexConnectorTest.twoAccountsNeverShareAClientIdentityAndAServerWithoutSignInHasItsOwn` (accounts, tokens by digest, the open server: each its own identity, none the installation's own) | Pass |
| Navigation structure | `NavigationTest`, `HandheldLayoutTest`, `AboutUiTest`, `SourcesUiTest`, `VisualSystemTest`, `LibraryDepthUiTest`, `DownloadsUiTest`: the 12 tests that held the old structure (About and the switches under Sources, two top-corner buttons, six rail stops) rewritten to assert the new one (the utility area at the rail's foot, one profile entry on a phone, Settings); `ProfilesUiTest` (7) | Pass |

**Live: two Jellyfin accounts on a real server** (`JellyfinTwoAccountsLiveTest`, Jellyfin 12.4.0 in Docker from `tools/ci/jellyfin-test-server.sh`; the administrator, and a second user made by the test and deleted after). Observed, not assumed:

- *One identity shared by both accounts, as before M8:* A's playback report was accepted. B signing in from the same identity left A's token working (`Users/Me` 200), and B's worked. The server then listed two sessions under the one identity, B's and A's, and A's lookup of "this device's session" (the server's sessions filtered by the identity, as `JellyfinPlayback` reads them) returned **B's session first, then A's**. B's lookup returned only B's (a non-administrator sees only their own sessions). So before M8 the playback truth layer, signed in as an administrator, could read the other account's session.
- *Per-account identities, M8:* both accounts were signed in at once; A's lookup by A's identity found only A's sessions; B's identity, read as A, found only B's; B's lookup found only B's. Neither identity ever resolved the other account's session. Signing out ended both tokens.

**Live: two Plex accounts, owner-pending.** `PlexTwoAccountsLiveTest` is written and runs when `REFLUX_PLEX_URL`, `REFLUX_PLEX_TOKEN_A`, and `REFLUX_PLEX_TOKEN_B` are given (the environment or `~/.reflux-live/plex.env`). It needs two plex.tv accounts that can reach a server, which only the owner can provide. Run 2026-11-11: skipped, "owner-pending". No live Plex result is claimed; per-account Plex identity is covered by `PlexConnectorTest` against the fake server.

**Website** (`website/`, built locally):

- `check-dist`: 17 pages and 47 files, 0 problems, with the new checks (titles `Page | Reflux`, home `Reflux | Your media, played well`; no em dash; description lengths; canonical address and `og:url`; JSON-LD that parses and claims no rating or review; one heading 1 and no skipped level; alt text; every page in the sitemap; the 404 `noindex`; `robots.txt` naming the sitemap). It found two descriptions out of range (home 183 characters, Terms 36); both were rewritten.
- `check-links`: 18 pages, 18 external links, 0 broken.
- `check-layout`: 17 pages at 7 sizes (2560, 1440, 1280, 820, 1180 on its side, a 390 phone, 844 on its side), 0 problems after fixes. It found the header's logo link 23 px tall, the RSX Develops credit 21 px, and the documentation navigation's links just under 24 px, below the 24 × 24 px target on every page; all three now have a minimum height. No page scrolls sideways at any size; layout shift and largest paint within budget. 119 screenshots for review (phone and tablet pages looked at).
- Sitemap `<lastmod>` from git, also in a shallow clone (the boundary commit ignored).
- Production is unchanged until this is pushed and Vercel deploys `main`: the live site still shows the old titles.

**Targeted runs, final tree:** `core` 346 tests, `library` 228, `sources:jellyfin` 63, `sources:plex` 21, `desktop:engine` 44 (6 skipped: live tests without their servers, the Plex two-account test among them), `desktop:app` 41 (11 skipped: window tests, which need `-Preflux.windowTests=true`), `ui` 319 (1 skipped), Android unit tests 51, the Android release Kotlin compile; 0 failed. `JellyfinSignOutLiveTest`, which runs against the owner's own server (`~/.reflux-live/jellyfin.env`, a dedicated non-administrator account), first failed because it looked for Reflux's session under the installation's bare identity; updated to the account's own, it passes there: the session is listed under the per-account identity, signing in again is the same device, and removal ends it.

**Not run in M8, by the strategy:** the full regression on this machine (CI's, on push); the playback format matrix (M12: the engines did not change); device walks on the Phone 2a, Pad 7, and Fire TV (the end-of-milestone walk needs the devices attached and the owner's go-ahead; the shared code is covered offscreen above); Linux (CI's container job).

## M8: architecture challenge (2026-11-11)

A review of the M8 working tree for flaws that would force a redesign in M9 or M10, with three probes written as tests (`ProfileBoundaryTest`). All three failed first, and pass after the corrections:

| Probe | Found | Correction |
| --- | --- | --- |
| Taps across the window while a profile's PIN is asked for | A tap opened a title's page behind the question: the overlay drew over the page without taking its input | The page and the navigation are not composed while Reflux asks who is watching, and the overlay takes all pointer input |
| The eight-destination rail on a television (960 x 540) and a phone on its side (915 x 412, 740 x 360) | The television fits. On the phone the last entry was squeezed to 18 px | A phone's rail shows the primary four and the utility area's one profile entry, as when it is upright (NAVIGATION.md, section 3) |
| The search text after a profile switch | The last viewer's search was still in the field | Search text, the last playback report, and the notice are cleared on a switch |

Also corrected: the support report's redaction list had been narrowed to the open profile's titles and sources, while the diagnostics log it carries is the installation's; it is the whole installation's again (`Library.privateWords`).

Found and **not** corrected, each recorded in ROADMAP.md, M8, "Open corrections": a forgotten PIN or a lost PIN key on a single-profile installation locks the viewer out; Plex connections made with a token share one source identity; the desktop has no guard against opening a newer library; smaller items listed there.

## M8: corrections (2026-11-12)

The three corrections the architecture challenge required, and the three smaller ones, on the same uncommitted tree.

| Correction | Tests | Result |
| --- | --- | --- |
| **Forgot PIN** | `ProfilesTest.aForgottenPinResetsOnlyThatProfileAndTouchesNoServer` (a wrong PIN refused; reset refused until the accounts are signed out; then PIN, history, favorites, collections, pins, and preferences gone, the profile and its name kept, the server's address kept, no server told anything, the other profile and its account exactly as before, a new PIN accepted, and a library opened again finds no PIN). `ProfilesUiTest.aForgottenPinOnTheOnlyProfileIsResetAfterConfirmingAndStaysResetAtTheNextLaunch` (one profile with a PIN, a wrong PIN, Forgot PIN, nothing erased until confirmed, "Keep it" takes it back, the confirmed reset, the media library unchanged, no PIN in anything reported, a new PIN set, the next launch opens with no question). `resettingOneProfilesForgottenPinLeavesAnotherProfileAlone` | Pass |
| **Plex token identity** | `PlexConnectorTest.twoTokensOnOneServerAreTwoSourcesAndNeverCollide` (two tokens and the open server are three sources; the same token again the same one; the identity survives storing and reading back; no token or name in an identifier; each its own device), `aTokenConnectionStoredBeforeKeepsTheIdentityItHas`, and the earlier test that asserted the collision, now asserting the opposite. `ProfileRulesTest.theDigestIsSha256` (FIPS 180-4's examples and a million-character input) | Pass |
| **Desktop downgrade guard** | `LibraryDowngradeTest`: the current schema opens and opens again; an older one is migrated with its history; a newer one is refused with the message, and the file's SHA-256 is the same before and after | Pass |
| Shared server's sync profile on deletion | `ProfilesTest.aSharedServersRecordsAreComparedWithNobodyOnceItsProfileIsGone` (and nobody inherits the server's record) | Pass |
| Watch Next dismissals per profile | `WatchNextLinkTest.aCardOneViewerRemovedIsNotRemovedForAnother` | Pass |
| A launcher link to what a profile cannot see | `ProfilesTest.onlyWhatAProfileCanSeeCountsAsSeen`, `ProfilesUiTest.aLinkToWhatAProfileCannotSeeOpensNothingOfIt` | Pass |

**Runs, final tree:** `core` 347, `library` 234 (the isolation sweep and the query check among them), `sources:jellyfin` 63, `sources:plex` 23, `desktop:engine` 44 (6 skipped: live tests without their servers), `desktop:app` 41 (11 skipped: window tests), `ui` 325 (1 skipped), Android unit tests 52, the Android release Kotlin compile; 0 failed. The first Android compile of this pass failed on a missing import in `WatchNextPublisher`, fixed.

**Live, Jellyfin, rerun:** `JellyfinTwoAccountsLiveTest` on Jellyfin 12.4.0 in Docker passes again with the same observations as before (shared identity: the administrator's lookup lists the other account's session; per-account identities: each finds only its own; sign-out ends both tokens). `JellyfinSignOutLiveTest` passes against the owner's server.

**Live, Plex: still owner-pending.** No `~/.reflux-live/plex.env` and no `REFLUX_PLEX_*` variables exist on this machine, so `PlexTwoAccountsLiveTest` was skipped ("owner-pending") and **no live Plex result is claimed**. The test now signs both tokens in through the application's own connections as two profiles on a real data directory (two sources, two profiles, one endpoint, two device identities, no token in an identifier, each profile seeing only its own account), so when it runs it exercises the corrected identity rather than the connector alone.


## M9: the desktop mini-player trial (2026-11-12)

The feasibility trial that opened M9 (ROADMAP.md, M9; DECISIONS.md, 2026-11-12, "The desktop mini-player"), on the M8 commit `212150a` with M9's uncommitted tree. It was run as a real-window test, `MiniPlayerWindowTest`, which stays as the regression test: the real desktop shell, real libmpv rendering into the native child window, generated media, input delivered through the application's own event queue.

**Verdict: viable with an architectural adjustment.**

| Question | How it was asked | Windows 11 | Linux, X11 |
| --- | --- | --- | --- |
| One session, moved to a dock and back, without recreating anything | The same `PlaybackSession` object and the same native surface component before and after; position advancing throughout | Pass | Pass |
| The browsing shell stays interactive | Pages opened and left by mouse and by Escape while the picture plays; the session untouched | Pass | Pass |
| A visible compact presentation | The screen itself read: 137 to 194 distinct colors inside the dock's picture box, the page's own beside it | Pass | Not read (screen capture is not dependable on X11; the surface's bounds are asserted instead) |
| Pointer routing | The system asked which window a point reaches (`WindowFromPoint`): the controls window over the picture, the main window over the page | Pass | Not applicable |
| Focus and stacking | The controls window is owned by the main window, not focusable, and on top only as far as its owner is; the active window stays the page's | Pass | Pass |
| The dock operates the same session | Pause and play from the dock; `I` to the player and back | Pass | Pass |
| What covers the page | The offline sheet opened: the surface leaves the window, its controls window hides, playback goes on, the same surface returns | Pass | Pass |
| Resize | The window made smaller and restored: the picture follows its box | Pass | Pass |
| Another display | Moved to the second display (scale 1.5) and back (scale 1.25): the picture stays in its dock and plays | Pass | One display there; not exercised |
| Fullscreen | From fullscreen, `I`: the window is restored and the same surface docked | Pass | Pass |
| No duplicate session | Another title played while one is docked: one surface, the first engine gone, its place recorded | Pass | Pass |
| Lifecycle | Stop from the dock: no surface, no controls window, the place recorded | Pass | Pass |

**Measured, Windows:** with the surface made to travel over 280 ms, the toolkit moved it 10 and 16 times in two runs with gaps of 183 and 104 ms between moves (a few uneven jumps, not motion). Placed at once it is two moves in under 3 ms. That is why a native surface is placed and not animated.

**Environments:** Windows 11 with two displays (2560x1440 at 125% and 2560x1600 at 150%), the development libmpv; Linux in the `tools/ci` container (Ubuntu 24.04, Xvfb at 1600x1000, openbox, xcompmgr, the distribution's libmpv). A Wayland compositor was not used: the toolkit runs through XWayland, so X11 is what a Wayland session exercises. Physical Linux hardware remains waived (M2P).

## M9: Navigation & Surface System 2.0 (2026-11-12)

Targeted validation, as the milestone rules ask: what M9 changed, offscreen first, then real windows. No device was attached, so no device walk was made; the full `check` on GitHub runs on the next push.

| What | Tests | Result |
| --- | --- | --- |
| One session, several presentations | `PresentationTest` (17): one engine and one load across collapse and expand; what is recorded and when; no mini-player without a dock or on a television; a failure is not put aside; keys are the page's while docked and media keys still reach the session; fullscreen left on collapse; another title replaces the session, the same one brings it back; the full player still blocks a second start; the video frame the shell is told | Pass |
| Profile ownership across presentations | `PresentationTest.aPlaybackInTheMiniPlayerStaysItsProfiles` (a profile switch ends the docked session, recorded for the profile that started it and for nobody else), `whatIsRecordedFromTheDockIsRecordedForWhoeverStartedIt`, `takingASourceAwayEndsWhatPlaysFromItFirst` | Pass |
| The mini-player on screen | `MiniPlayerTest` (10): into the dock and back with one engine; the dock's controls; three stops with roles, an action label, and focusability; a failure announced from a live region; no control offered without a dock; a television has none | Pass |
| Every window class | `MiniPlayerTest.everyWindowWithADockShowsItClearOfTheNavigation` at 412x915, 915x412, 640x800, 800x1280, 1100x800, 1280x720, 1920x1080: inside the window, clear of every destination, the picture 16:9 in its box, 48 dp targets for a finger | Pass |
| Text at 200%, reduced motion, what covers the page, the question of who is watching, notices clear of the dock | `MiniPlayerTest` | Pass |
| The travelling mark | `ShellSurfaceTest`: lit where the viewer is, between destinations only mid-journey, arrived and gone from where it was; at once with motion reduced; gone when the viewer is in none of the bar's four; what a screen reader hears is never mid-journey | Pass |
| The phone's bar | `ShellSurfaceTest.thePhonesBarIsAsWideAsItsDestinations` at 320, 360, 412, 480 dp | Pass |
| The shell by window class | `ShellSurfaceTest.theShellInEveryWindowClassIsAsItsBaselineSays` against `shell-baseline.txt`: ten window classes, a phone-sized window on a tablet among them, and a television | Pass |
| Brand and credits | `ShellSurfaceTest`: the lockup named once on a desktop and absent on a phone; the credits per build, Jellyfin and Plex said to be independent of Reflux, no image | Pass |
| The dock's geometry | `LayoutTest.everyWindowButATelevisionHasADockForWhatIsPlaying` (found, while it was written, that a 640 dp tablet window got a card over half its page; such windows now have the row) | Pass |
| M1 and M8 guarantees | The existing suites unchanged: `AccessibilityTest`, `NavigationTest`, `DirectionalInputTest` (which holds the focus-bounds replacement), `TelevisionTest`, `HandheldLayoutTest`, `VisualSystemTest`, `ProfilesUiTest`, `ProfileBoundaryTest`, `PlaybackSessionTest`, `AppStateTest` | Pass, none edited |

**Runs, Windows, final tree (the closure pass, 2026-11-12):** `core` 347 and `design` 37, all passing; `ui` 359 (1 skipped), all passing in this run; `desktop:app` with the window tests 42 (3 skipped), 41 passing and 1 failed in the full runs (the live test below, which passes when run alone); the Android debug Kotlin compile passes. `ui` renders were saved and read for each form factor (desktop, tablet, phone upright and on its side, television open and closed, the dock at each window class, the credits page).

**Runs, Linux (the `tools/ci` container, Ubuntu 24.04, headless for `ui`, Xvfb with openbox and a compositor for windows):** targeted only, on the final tree. `design` (all), and `ui`: `ShellSurfaceTest`, `MiniPlayerTest`, `PresentationTest`, `NavigationTest`, `DirectionalInputTest`, `TelevisionTest`, `ProfileBoundaryTest`, `HandheldLayoutTest`, `LayoutTest`: 73 tests, **one failed**: `ShellSurfaceTest.theShellInEveryWindowClassIsAsItsBaselineSays`, because the baseline recorded the last row's bottom in rails and sidebars, which is the machine's font (the container's was 2 dp from Windows'). That was a defect in the test's own claim of machine independence, not in the shell: the measure was removed from the baseline (it is asserted to be inside the window instead), the baseline rewritten, and the test rerun on Windows and in the container, where it passes. Windows: `MiniPlayerWindowTest` (the trial, passing, with the Linux readings above), `AboutWindowTest`, `DesktopShellTest` (6, 1 skipped): pass. **The full Linux `check` and window suite were not run to completion**: the first attempt was stopped by the machine's low-memory guard and was not repeated here; it belongs to hosted CI.

**Failures and classifications:**

- `PlannedPlaybackWindowTest.aPlanIsCarriedOutInTheRealWindow` (a live test: it plays real films from the owner's Jellyfin server in a real window) has an **environmental, intermittent** record on M9's tree and no implementation defect found. Failed once with the server unreachable (`UNREACHABLE (server unreachable)` while the host was reachable by the system). Rerun when the server answered: **passed** alone, twice (the second time with the final sidebar). Failed once more inside a full `desktop:app` window run, at its last step (`closes()`, the session was already gone when the test went to close it: playback ended by itself during a 14 s capture loop on a live stream); it was not diagnosed further. It does not use the mini-player and M9 changed nothing in planning, negotiation, or the engine (`git diff` over `core/playback`, `library`, `sources`, and `playback` is empty), so it is classed as a live-server test that is intermittent on this machine; it was **not run against the M8 commit**, so "pre-existing" is not claimed for it. It should be watched, with the owner's server healthy, in the M12 campaign.
- `AboutUiTest.aKeyboardGoesInAndComesBackToWhereItWas` is **intermittent and pre-existing**, not caused by M9 and not fixed in it. Checked on this machine: on the untouched M8 commit (`212150a`, M9 stashed) it failed when run alone, and passed in a later run of the whole class; on M9's tree it passed in one whole-class run, failed in another, failed alone, and passed in the final full run. The symptom (the first arrow focuses nothing in the offscreen renderer) matches the headless-Linux skip recorded in VERIFICATION.md, M7. The test was not edited, weakened, or deleted; its real-window counterpart passes. Carried to M10.
- `AboutWindowTest` failed on the M8 commit because it still looked for About under Sources, where M8 moved it to Settings. It now follows M8's structure (Settings, About, and back to Settings) and passes; nothing it asserts about About was changed. Passes on Windows and Linux.

**Not done, and said so:**

- No device walk (Phone 2a, Pad 7, Fire TV): none was attached. M9's Android-visible changes are the navigation's look (covered offscreen at each window class); Android has no mini-player yet.
- No screen reader was used. The semantics are held by the tests above.
- No Wayland compositor, no second display on Linux, no physical Linux hardware.
- The full Linux `check` in the container, and the hosted CI run: pending (the next push).
- The full `check` on GitHub CI: on the next push.

**Closure corrections, same day:** "Made by RSX Develops" was removed from the desktop sidebar at the owner's request (DECISIONS.md); the maker is credited in About, and the sidebar is the Reflux lockup, the destinations, and the utility area. After it, `ShellSurfaceTest`, `MiniPlayerTest`, `NavigationTest`, `DirectionalInputTest`, `TelevisionTest`, `HandheldLayoutTest`, `AccessibilityTest`, and `VisualSystemTest` pass on Windows and the geometry baseline needed no change (nothing below the destinations moved). The Linux container was not rerun after that one-line removal.

**Still pending, not claimed:** hosted CI and the full Linux `check` (the next push); Wayland; a second display on Linux; a screen reader walk; device walks. Windows two-display verification of the mini-player passed.

**M9: hosted CI (2026-11-12).** The first push carried both M8 and M9 (M8 had not been pushed), so these are the first hosted runs of either. Releases and tags: none made; the version is unchanged.

| Run | Commit | Result |
| --- | --- | --- |
| CI 43238252678 (and its re-run) | `89075d0` | **Failed twice, the same way.** Ubuntu check: `MiniPlayerWindowTest` (an unrelated modal "Error" dialog, `SQLITE_CANTOPEN`, was the active window). Website job: layout shift over the 0.05 budget on many pages. Windows, Android, workflow lint, secret scan, release-tools: pass |
| CI 43256057566 | `347818a` (test diagnostics only) | Same two failures; the dialog's text identified the cause |
| CI 43268926732 | `e7bc319` | Windows `AboutUiTest.aKeyboardGoesInAndComesBackToWhereItWas` failed once (the intermittent, pre-existing test; unchanged); every other job passed. Re-run of that job: **passed**. **Final result: all 7 jobs green** (Ubuntu check including the window tests and `MiniPlayerWindowTest`; Windows check; Android; website incl. the layout check; release tools; lint; secret scan). The Release workflow also succeeded |

What the two failures were, and the fixes (`e7bc319`):

- **Ubuntu, `MiniPlayerWindowTest`: not the mini-player.** `EmbeddedPlaybackTest` closes its window while playing; the saved place wakes the pages' library reads, which run on after the test deletes its data folder. A blocking database read cannot be cancelled, so it failed with `SQLITE_CANTOPEN` after its caller was cancelled, was reported as an unhandled error, and Compose showed a modal dialog that outlived that test and was found by the next one to ask which window is active. (Windows cannot delete the open database, so it never showed there.) Fix: `AppState.read` ends a failure that arrives after its caller was cancelled as the cancellation it is; `LibraryReadTest` fails without the change (checked) and passes with it. The test's own assertion was left strict.
- **Website: the web font.** Manrope loaded with `font-display: swap` and no preload, so pages painted in the runner's wider fallback face and re-flowed. It is now declared with `font-display: optional` and preloaded on every page (DECISIONS.md, 2026-11-12). The budget is unchanged and the measurement was correct.

**Left open, honestly:** a library read that fails while its page is still showing still reaches Compose's default exception handler, which shows a raw error dialog; that production robustness gap is not fixed and is noted for M10 or later. The intermittent `AboutUiTest` is still intermittent (it failed on hosted Windows once this run). Device walks, Wayland, a second display on Linux, and a screen reader walk remain not done.

## M10: Player & Playback Experience 2.0 (2026-11-13)

Targeted validation, as the milestone rules ask, on the working tree over `1dbbc11` (uncommitted). Devices: a Nothing Phone 2a (A142, Android 16, API 36), a Xiaomi Pad 7 (Android 16), a Fire TV Stick 4K Plus (`AFTMA08C15`, Fire OS 8, Android 11, API 30), and an Android 16 phone emulator (`Reflux_Phone`, driven at phone size and at a tablet size of 1200x1920 at 240 dpi). Every device ran a **scratch build** (`-Preflux.android.applicationId=dev.rsxdevelops.reflux.m10`), so no installed Reflux, its library, or its signature was touched; the scratch builds and files were removed from each device afterwards. Nothing was installed over an existing app, no setting was changed, and TalkBack was never enabled.

### Step 0: the probes

| Probe | Result |
| --- | --- |
| **P1, one `SurfaceView` between the whole window and a dock** (`SurfaceContinuityOnDeviceTest`; Phone 2a) | A view moved full, dock, full five times over 300 ms under a playing engine. Both variants: decoder started 0 more times, video disabled 0 times, 0 dropped frames, no surface created or destroyed, the subtitle track still selected, positions only forward. **Default buffer:** 182 frames, 3 janky (1.65%), **174 "legacy" janky (95.6%)**, p50 12 ms, p90 17 ms, p99 29 ms. **Fixed-size buffer (`holder.setFixedSize`):** 164 frames, 2 janky (1.22%), **0 legacy janky**, p50 14, p90 18, p99 23 ms. Decision: the surface travels, on a buffer of the picture's own size. The probe moved a plain `View` over the application's window, not the Compose `AndroidView`; the walk below is the same placement through the real shell |
| **P2, picture-in-picture closed or expanded** (the real app, `RefluxPip` log; Phone 2a) | Entering (Back at Home with a playing dock): `onPictureInPictureUiStateChanged(transitioning=true)`, `onPictureInPictureModeChanged(true)` with the activity `STARTED`, then `UiStateChanged(transitioning=false)`. **Expand** (the system's button): `mode changed(false)`, lifecycle `STARTED`, no `onStop`, same engine, position continuous (54 s). **Close (X):** `mode changed(false)`, lifecycle **`CREATED`** (already stopped): the session closed, `engines=0`. **Swipe to dismiss:** the same as the X. **Launching the app by intent while in the window:** `mode changed(false)`, lifecycle `STARTED`: expanded, playing on (139 s to 142 s). The window's controls (previous, pause, next) came from the activity's media session, with no custom actions. The emulator entered the window the same way. Rule built: expanded iff the activity is started. One device, one Android version |
| **P3, desktop media keys** (a JDK 22 AWT frame, Windows 11; synthetic `keybd_event`) | A focused window received each media key as `KEY_PRESSED`/`KEY_RELEASED` with `keyCode=0`, `extendedKeyCode=0x0`, no key char, and **`rawCode` = the Windows virtual-key** (179 play/pause, 176 next, 177 previous, 178 stop, 173 mute); an ordinary letter arrived normally; no `KEY_TYPED`. Mapped from the event's description (`DesktopKeysTest` uses the exact strings captured). **Linux was not probed** (the plan's rule: Linux only if delivery is shown), so Linux media keys are V1.x |

### Automated

| Area | Tests | Result |
| --- | --- | --- |
| Subtitle preference, matching and planning (`SubtitlePreferenceTest`, core) | 9 | passed |
| Held seek (`SeekHoldTest`, core) | 8 | passed |
| Subtitle memory per profile and work, planned through the library (`SubtitleMemoryTest`), and the broad isolation test (`ProfileIsolationTest`, unchanged) | 7 + 2 | passed |
| Chrome reducer (`ChromeModelTest`): every precedence edge, the grace, one deadline, and a property over every combination of facts that no loader ever shares a layer with controls | 12 | passed |
| Session (`PlayerM10Test`): `SYSTEM` as a third presentation (same engine, owner, one load; expand and close), the Back-route table and its agreement with `dispatch(BACK)` in nine states, a gesture's preview being only a preview, a failure in the dock and a failed next episode, whose a playback is across profile switches, subtitle choices kept for the owner, held seek (one seek on release, quiet-key commit, quick separate presses being separate seeks, never past the end), the television explanation, media keys in every presentation | 35 | passed |
| Mini-player UI (`MiniPlayerTest`): includes a back gesture in progress shrinking the picture to 90% with the session unchanged, restoring it on cancel, and a completed gesture docking | 12 | passed |
| `PlaybackSessionTest` | one expectation changed on purpose: a next episode that cannot be played is now a failure the session shows, not a silent close (same explanation text asserted) | passed |
| Desktop keys (`DesktopKeysTest`, `DesktopShellTest`): media keys from the captured descriptions, volume keys left to the system, Windows' and X11's repeat patterns as repeats and releases | 7 + 6 | passed |
| Picture-in-picture policy (`PictureInPictureTest`, Android unit) | 4 | passed |
| The real-window mini-player on Windows with real libmpv (`-Preflux.windowTests=true`, `MiniPlayerWindowTest`) | 1 | passed |
| Local regression, `:core:jvmTest :library:jvmTest :ui:jvmTest :android:app:testDebugUnitTest` | 408 ui tests plus the others | **3 failures on the first full run**, dispositioned below; each passed on rerun |

The three failures of the full run. **`HandheldLayoutTest.aPhonePlayerPutsTheTransportUnderTheThumbs` was a real defect of mine:** a first draft of held seek treated a second press within 400 ms as the held key repeating, so a double tap's second seek was swallowed (and an arrow tapped quickly would have lost seeks). It was removed (the shells say which presses are repeats), a test was added (`quickSeparatePressesAreSeparateSeeksNotAHold`), and the test passed unchanged. `PointerTest.firstRunAnswersToAClick` and `SourcesUiTest.aServersLibrariesAreSwitchedOutOfTheLibraryAndStayOnItsPage` failed once in that run, which shared the machine with a second Gradle build and a device walk, and passed alone and again in a later group run; neither was diagnosed, and neither is claimed fixed.

### On devices

Instrumented, `am instrument` (never Gradle's connected task, which uninstalls the app): `SurfaceContinuityOnDeviceTest` (2), `ChaptersOnDeviceTest` (6: Media3's `Chapter` mapping, generated MKV and MP4 files with three chapters each reaching `PlayerState`, and a film with none having none), `ActivityRecreationOnDeviceTest` (1), `PlayerAccessibilityOnDeviceTest` (1). **Phone 2a:** the first three passed. **Emulator, final build:** all four passed.

**The recreation regression test reproduces the old leak.** On the old `onDestroy` (`if (isFinishing) close()`) it fails at "the engine to be let go" (the engine stays open); on the fix it passes, and the place is recorded. The cause: a lifecycle scope is cancelled before `onDestroy` runs, so closing there launched the engine's release into a dead scope.

**Walks** (the harness `PlayerWalkOnDeviceTest -e walk true`, which seeds generated media into the scratch app, plays it, and logs the session):

- *Phone 2a:* the full player in portrait; a tap reveals the controls (the lighter transport, Minimize and Stop at the top, the timeline row with the actions wrapped beneath at that width); Back gives the dock above the navigation bar with the live picture and the system bars visible; Back at Home with it playing gives picture-in-picture with the live picture and the system's previous / pause / next; then the P2 cases.
- *Pad 7:* the full player (title above a one-row bar, timeline, 1x); Back gives the card dock in the corner above the gesture bar with the live picture.
- *Fire TV Stick 4K Plus:* full-screen only (route `APP`, no mini-player); the controls revealed by the remote (title above a bare timeline, "Speed" and "Info" named beneath, no panel, no sidecar row: Fire OS 8 reads sidecar files); Back closed the player (`engines=0`); a second Back followed the unchanged television rule of going to the navigation first. `input keyevent --longpress` produced one seek (+10 s), which says nothing about a held seek on a real remote.
- *Emulator, phone and tablet size:* the same flow; picture-in-picture entered with the same callbacks (the UI-state callbacks arrive before the mode change); the tablet size shows the dock as a card, as on the Pad.

**The accessibility tree, on a real framework** (`PlayerAccessibilityOnDeviceTest`, emulator; not TalkBack's speech): the full player's Minimize, Stop, Pause, and the ten-second seeks are each one named, actionable node of at least 48 dp (with the touch input modality; with the pointer's they are 40 dp, as designed); the dock is one named pane ("Mini player") with Pause and Stop of at least 48 dp; nothing of one is in the tree of the other; and activating the dock's stop through the framework's own action brings the player back. **It found a defect the desktop tests could not:** on Android the dock's *picture* (the way back, an M9 design) has no node in the framework's tree at all (also seen in a `uiautomator` dump on the Phone 2a, where "Pause" and "Stop playing" were present and the picture was not). The cause was not found: it is not the shell's tap-catcher layer (made semantics-free, no change) and not the surface's view holder (marked not important for accessibility, no change); the node is absent even with the picture out of sight. So that a screen reader's user is never left without a way back, where the platform says the picture is unreachable (`Platform.dockPictureIsAStop`, false on Android) the dock's title and status are that stop and the picture is decorative; the test passes on that. This is a workaround for an unexplained behavior and is recorded as such.

### What was not done

- **The predictive back gesture on hardware.** `input swipe`, `input motionevent`, a `uinput` virtual touchscreen (which never registered as an input device on the Phone 2a or the Pad), and the emulator console's raw touch events were all tried; none was taken as the system's back gesture (the system bars appeared instead). Its preview, cancel, and commit are held by `MiniPlayerTest` and `PlayerM10Test`, and the three-button Back path was walked on every device.
- **TalkBack.** Not driven on any device, for the same reason (no gesture could be injected). The accessibility tree test is not a substitute for TalkBack's speech, focus order, or announcements; focus restoration after a presentation change and the loading and failure announcements are covered by semantics tests only.
- **A held seek on a real remote**, and a **loading state on a device** (a real wait could not be made on demand): covered by tests only.
- **A failure in the dock on a device**; **Linux media keys** (not probed); the Windows media keys from a real keyboard (a synthetic event was used); a **second Android version** for the picture-in-picture close-versus-expand rule; **200% text** on a device; the **intermittent `AboutUiTest`** (not touched, not seen).
- **Hosted CI** was not run (nothing was pushed). The full `check` runs on the next push.

### Final qualification pass (2026-11-13)

**Implemented, as a tier of evidence:** the desktop corners and chrome tiers below, the dock failure's two-line status.

**Locally tested.** Final local results (2026-11-13, on the uncommitted tree over `1dbbc11`): **ui 414 tests, 1 skipped, 1 failed**; the one failure is `AboutUiTest.aKeyboardGoesInAndComesBackToWhereItWas` (classified below). Core 364 passed; Android unit 56 passed; design 43 passed; desktop 53 with `-Preflux.windowTests=true` (3 skipped, 0 failed; real libmpv and real windows on Windows); each rerun after the last code change (dropping tabular figures from the player's clocks, which had changed the clocks' width and failed `PointerTest.theScrubberSeeksWhereItIsDraggedTo`; the test was not touched). The skipped ui test is `WebsiteShowcaseTest`. New: `PictureRadiusTest` (2), `PlayerChromeTest` (6), `PlayerChromeLayoutTest` (3), `SurfaceCornersTest` (4), and the real-window assertions in `MiniPlayerWindowTest`. `EmbeddedPlaybackTest` was updated for Escape's new meaning (dock, not close). `AboutUiTest` passed five of five alone, and `PointerTest` plus `SourcesUiTest` three of three alone: **not reproduced, not diagnosed, not claimed fixed**. **`AboutUiTest` is a known intermittent test, carried since M8 (ROADMAP, M9); it passed five of five run alone and in the earlier full run, and failed in one full `ui` run ("Timed out waiting for focus on `Version 0.9.0-alpha.2`"): intermittent under full-suite load. It is About-page keyboard focus, not the player; no M10 regression has been established, and it is not described as an M10 defect.**

**Physical-device evidence (Phone 2a and Pad 7, both Android 16, scratch build `dev.rsxdevelops.reflux.m10`, removed afterwards):** the dock's picture rounded at the surface on both; 200% text on the Phone 2a (full player, mini-player, failure dock, loading); a configuration change that recreates the activity (`font_weight_adjustment`) during playback on both (the session ended with no engine left, and a new play ran one; a text-size change does not recreate); picture-in-picture on both (enter; expand by launch, `STARTED`; X, `CREATED`, `engines=0`). Earlier in the pass: TalkBack driven on both, a real stalled and reset source on the Phone 2a.

**Windows (real libmpv, 125%):** the picture's region is a rounded rectangle of 20 px in the dock (401 x 226 px), none in the whole window, and rounded again after fullscreen and the dock; the corners were seen in a capture of the window, with the AWT panel colour found in the cut corners and replaced.

**Not done, unavailable, or deferred:** the predictive back gesture on hardware (no injection reaches the system's edge recogniser); a second Android version for the close-versus-expand rule; a held seek on a physical remote; the desktop corners at 100, 150, and 200% on a display; the chrome tiers in a real fullscreen window; Linux media keys and corner rounding (V1.x); hosted CI (nothing was pushed).

### Live resize of the desktop chrome (2026-11-13)

Reported by hand: dragging the window did not change the controls' density. **Reproduced** on Windows with real libmpv and the real mouse (`PlayerChromeResizeTest`): with the button held, a window of 1304 x 785 dp (Standard) and then 1803 x 1005 dp (Large) kept the Compact controls (a 44 dp pause button), and still did after the button was released; a screenshot showed the video at the new size and the controls laid out for the old one. **Cause:** the controls' dialog was resized but its root pane, layered pane, Compose panel and Skia layer were left at the old size (traced by dumping the component tree: dialog 1684 x 964, content 1000 x 640); the operating system's sizing loop does not lay a window out again, and `setSize` (which does) had hidden it. **Fix:** `VideoOverlay.align()` calls `overlay.validate()` after `setBounds`. **After:** with the mouse button held, the pause button was 48 dp at 1300 x 784 (Standard), 68 dp at 1816 x 1000 (Large), 48 dp again at 1300 x 784, 44 dp at 1000 x 640 (Compact), 68 dp at 1720 x 964, and 68 dp after release; the same session throughout. Also asserted: discrete `setSize` jumps through all five steps, and a 60-step simulated drag both ways, each with playback still playing and `app.playback` the same object. The desktop task (54 tests, 3 skipped), design (43), `PlayerChromeLayoutTest`, `MiniPlayerTest`, and `PointerTest` pass. Windows at 125% only; a drag across two displays of different scale was not driven.

**Full `ui` suite after the live-resize fix (2026-11-13): 414 tests, 1 skipped, 1 failed.** The failure was `SourcesUiTest.aServerThatIsAwayKeepsItsLibrariesAndItsSwitches` ("Timed out waiting for connected": the fake server's films did not reach the library within 20 s), not `AboutUiTest`, which passed in this run. **Classified as the existing `SourcesUiTest` intermittent family**, not an M10 defect: the same step has timed out in `theRemoteSwitchesLibrariesToo` and the same family is recorded in ROADMAP (2026-11-08, "Intermittent tests: `SourcesUiTest` ... time out under a loaded full `check`") and in the M6 and M8 records. The M10 diff does not touch the connect, source, or pipeline path (it is playback planning, subtitle memory, the player, and the desktop shell). Rates, said as they were: the class alone on this working tree, 2 failures in 10 runs (and 1 in the full suites that included it twice); the class alone on a clean `1dbbc11` checkout, 0 in 16 runs. That difference is suggestive and is **not explained**; no code cause was found and none is claimed, and the test was not changed. It stays open as a carried intermittent for M12's qualification.

**Final regression on the committed M10 tree `43f8f5b` (2026-11-13):** design 43, core 364, library 241, Android unit 56, desktop 54 (3 skipped, real windows and libmpv): all passed. `ui`: 414 tests, 1 skipped, 1 failed, the failure being the known intermittent `AboutUiTest.aKeyboardGoesInAndComesBackToWhereItWas` (it passed in this suite's previous run and five of five alone); `SourcesUiTest` did not fail in this run. No new failure. The predictive back gesture was then driven by the owner on the Phone 2a (Android 16) and **passed**. Open and unqualified: a second Android version for the picture-in-picture rule (unavailable: both handhelds are API 36), and a held seek on a physical remote (unavailable).

### Details page actions (2026-11-13)

Found by the owner on the Phone 2a: with Resume, From the start, watched, and favorite, the favorite wrapped alone. Reproduced offscreen (`DetailActionsLayoutTest`: favorite on its own line at 412 dp, and at 360 dp with 2x text). Fixed by grouping the compact actions and tightening the gaps. Tests: 7 in `DetailActionsLayoutTest` (pointer and touch targets, Play and Resume states, 320, 360, 412 and a 900 dp tablet, normal and 2x text); the full `ui`, design, and Android unit suites are run after it (see the final report). On the Phone 2a with a half-watched Harbour Lights: Resume and From the start on the first line, the watched and favorite pair together beneath them, nothing alone.

### Windows mini-player edge artifact (2026-11-13)

Reported by the owner: thin light lines down the video's sides. Measured on the real libmpv mini-player at 125%: one physical-pixel column at x=2082, just right of the canvas (1681 to 2081), (237, 237, 237), then one black pixel, then the dock; nothing at the left, top, or bottom. Isolated by elimination (rim removed; region off; controls window off; canvas and holder recoloured magenta and yellow): unchanged each time, so none of them. Cause: the toolkit's cut-out is a pixel wider than the 401-pixel video and showed the content pane's panel colour. Fixed by colouring that pane as the dock's glass; afterwards the column is (15, 15, 17). A 4:3 video in the dock (letterboxed) is clean with rounded outer corners. `MiniPlayerWindowTest` gained a native-pixel assertion (dark columns at the picture's left, right, and right+1), which fails with 237 when the fix is removed and passes with it; the desktop suite, 54 tests (3 skipped), passes. Not driven: 100, 150, and 200% display scales; a drag-resized mini-player (the dock is not resizable). The rim was kept, unchanged: it draws corner arcs only and was not the cause.

**Details page actions, final (2026-11-13).** The grouped pair was not enough: on the Phone 2a Resume and From the start were on one line and the pair beneath. Parent constraints were measured and are not the cause (the column has the screen less 16 dp margins, 382 dp); the four actions needed 394 dp because From the start was a glass pill. It is now the quiet text button (`ButtonStyle.QUIET`), and on the Phone 2a (412 dp wide, 48 dp targets, the real font) **Resume, From the start, watched, and favorite share one line**, with Resume large and the icons 48 dp; tapping watched turns the page to the completed state (Play, a lit check, the heart, one line), the favorite toggles off and on, From the start began at 0:03, and Resume continued at 1:18. `DetailActionsLayoutTest`, 7 tests: pointer and touch targets, Play and Resume states, 320, 360, 412, and 900 dp, normal and 2x text; at 360 dp and at 2x text the line breaks with the compact pair together and never alone. The tablet width was covered offscreen (900 dp), not on a tablet.

## M11 planning pass (2026-11-13; documentation only)

No application code, build script, workflow, package ID, or version was changed, and no console, store, website, or device setting was touched. What was done: the repository's release system, workflow, tool, records, listing material, website release data, and documents were read; the rebaseline's M11 scope was critiqued against the owner's brief; the current Google Play, Android developer verification, Android TV, Amazon Appstore, Microsoft Windows signing, and GitHub release and attestation requirements were read from their published documentation or summaries of it; the plan is `docs/planning/M11-DISTRIBUTION-PLAN.md`.

**Checked in the repository and on a device (read-only):** `gradle.properties` declares `reflux.android.applicationId=dev.rsxdevelops.reflux` once; `android/app/build.gradle.kts` gives every variant that ID with no `applicationIdSuffix`; the debug build is signed with the machine's debug keystore; the Nothing Phone 2a (`0005534AN000190`) holds `dev.rsxdevelops.reflux` 0.9.0-alpha.2, `versionCode` 90002, flagged `DEBUGGABLE`; the Xiaomi Pad 7 held no Reflux at the time; the Fire TV Stick was not connected. `docs/DECISIONS.md` (2026-11-08) and `docs/RELEASE.md` say `dev.reflux` is the development identity: they disagree with the build. The release workflow's `draft` job attaches `*.apk` and `*.aab` to the draft release, while `docs/RELEASE.md` says Reflux offers no Android file outside the stores.

**Read from outside sources on 2026-11-13, not exercised, to be rechecked in each console when its step runs:** Play's target API 36 rule (from 2026-08-31) and Android TV's lower floor; the personal-account closed-testing rule (twelve testers opted in for fourteen days; organization accounts exempt); Play App Signing and the upload key; the Android developer verification timeline (enforcement from 2026-09-30 in Brazil, Indonesia, Singapore, and Thailand; global in 2027) and its package-name registration rules; Android TV quality criteria (including 64-bit and 16 KB page size for TV apps from 2026-08-01); Amazon's presubmission checklist, privacy questionnaire, Live App Testing, and Appstore Beta Hub (Fire OS 7 or later, or Vega OS); Microsoft's Artifact Signing eligibility, SmartScreen per-file reputation, and Smart App Control; GitHub immutable releases (public preview announced 2025-08-26; 2026 status not verified), `gh release verify`, and artifact attestations. **Not verified, so not relied on:** whether a drop below twelve testers resets the fourteen days; the exact Play TV screenshot dimensions; whether sideloaded debug installs count toward a package's "known installs"; Microsoft Store account, fee, and packaging requirements; the April 2026 reports of an Artifact Signing reputation regression.

**State:** planned, not started. M11's evidence section will separate implemented, tested locally, verified on a device or console, owner-pending, unavailable, and deferred, as M10's did.
