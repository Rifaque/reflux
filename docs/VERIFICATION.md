# Reflux Verification Log

What was run, on what, and with what result. This is history, not plan: the product roadmap is [ROADMAP.md](history/roadmaps/ROADMAP-M0-M13.md), and what a release still needs is [RELEASE.md](RELEASE.md).

Rules for adding to it:

- One section per milestone or validation cycle, dated, on the tree it names.
- Say what ran (command, device, server, by hand or automated) and the result, including what was **not** verified and why.
- A result from an emulator, a container, or a fixture is never recorded as a physical-device, live-service, or real-hardware result.
- Do not edit an old section to make it current; add a new one. Test counts in old sections are counts at that time.
- Milestone labels are those of the day a section was written. Before 2026-11-02, "M2" means release engineering (now M6) and "M3" real-world validation (now M7); ROADMAP.md, "Earlier numbering", maps every one.

- Sections before Beta 2 (2026-10-08 to 2026-11-17: M0 to M11, Beta 1) are in [history/verification/VERIFICATION-ALPHA-TO-BETA-1.md](history/verification/VERIFICATION-ALPHA-TO-BETA-1.md), with their names unchanged.

## Phase 1 (Beta 2): reliability, security, playback, source integrity, observability (2026-11-18 to 19)

On `main` at `540147c` with the Phase 1 changes uncommitted (DECISIONS.md, 2026-11-18, "Beta 2, Phase 1"; plan: history/phases/PHASE-1-PLAN.md). Machines: Windows 11 Home 26300, 20 threads, 32 GB (this machine); Xiaomi Pad 7 (2410CRP4CI, Android 16) and Nothing Phone 2a (A142, Android 16), physical, over adb; Fire TV Stick 4K Plus (AFTMA08C15), physical, over adb on the network; the Google TV emulator (`Reflux_TV`, sdk_google_atv64, Android 16); WSL2 Ubuntu 24.04 (a VM, not a Linux desktop); Docker Desktop 29.9.3 containers (Ubuntu 22.04 and 24.04, Debian 12, Fedora 41). No physical Linux machine, no live Jellyfin server with two addresses. Android runs are debug builds (`dev.rsxdevelops.reflux.debug`) beside the store install, which was not touched.

**B-018, profile deletion.** Confirmed in the code first: `ProfilesScreen` → `AppState.deleteProfile` → `Library.deleteProfile` had no PIN check, and accounts and downloads went before the library was asked. `./gradlew :library:jvmTest --tests ProfilesTest --tests SubtitleMemoryTest`: 18 + 7 passed (6 new: no PIN, a PIN without a key, wrong, empty, another profile's PIN, a PIN just accepted for another profile, five wrong to delete then a wait that also blocks opening and survives reopening the library, waits from opening blocking deletion, only the target's rows go while Main's PIN, account, history, favourite, collection, and setting stay, accounts still first, the last profile kept). `:ui:jvmTest --tests ProfilesUiTest --tests ProfileBoundaryTest`: 13 + 3 passed (3 new: the screen asks for the PIN after Delete and a wrong one deletes nothing; a profile without a PIN as before; the app state reads the PIN fresh rather than trusting a stale page).

**B-001, Android frozen frame.** Cause found in `MainActivity.VideoLayer`: the engine was attached only in the `AndroidView` factory; `AppState.play` releases the old engine and makes the new one back to back, and with no frame between them the view was kept and the new engine never attached (sound and clock running, no picture; the surface still holding the old frame). New `SurfaceHandoverOnDeviceTest` (red and blue generated films, colour read off the surface with `PixelCopy`), five cases: from the mini-player to another film; close then play at once, and reopen; a film played to its end then another; stop then another; five changes in a row. **On the old `MainActivity` (Pad 7): 4 passed, the mini-player case failed** (the new film played, its picture never reached the surface). With the fix: Pad 7 5/5, Phone 2a 5/5, Fire TV Stick 4K Plus 4/4 + 1 skipped (no mini-player on a television), Google TV emulator 4/4 + 1 skipped. Unchanged and passing: `SurfaceContinuityOnDeviceTest` 2/2 (Pad 7, Phone 2a, Fire TV, emulator); `PlayerAccessibilityOnDeviceTest` 1/1 (Pad 7, Phone 2a); `ActivityRecreationOnDeviceTest` 1/1 (all four); `TelevisionOnDeviceTest` 2/2 (Fire TV, emulator); `PlaybackOnDeviceTest` 4 passed and 2 skipped by assumption (no Jellyfin URL given; the resource manager lists no decoder) on both handhelds. The Pad 7's HyperOS refused USB installs (`INSTALL_FAILED_USER_RESTRICTED`) until the owner accepted them on the device.

**B-031, Linux libmpv.** `mpv/client.h` (read 2026-11-18): "The LC_NUMERIC locale category must be set to "C"", and `mpv_create()` returns NULL otherwise. Probe (JNA, WSL2, libmpv 0.37.0-1ubuntu4): under `LANG=de_DE.UTF-8` the JVM has `LC_NUMERIC=de_DE.UTF-8` before `main`, libmpv prints "Non-C locale detected" and `mpv_create` is NULL; `setlocale(LC_NUMERIC, "C")` alone makes it work with `LC_CTYPE` still `de_DE.UTF-8`; an AWT window with input focus on Xvfb did not set it back (no real input method was available, so it is re-checked before every engine anyway). **Before and after, the same tests in WSL under `de_DE.UTF-8`:** HEAD (`git archive`) `:playback:mpv:test --tests MpvEngineTest`: 22 of 24 failed. The working tree: `MpvEngineTest` 24/24 and `NumericLocaleTest` 3 + 1 skipped (Windows only), with 50 engines made and destroyed and a real file played at speed 1.5 (a number the engine parses from text). Windows: `:playback:mpv:test` all passed (24 engine; 4 locale, 1 skipped as Linux-only; 25 capabilities; 3 decode probe; 1 fixture); `:desktop:engine:test --tests DiagnosticLogTest` 3/3 (a repeat written once and counted, causes told apart, repeats redacted). **Not verified:** a physical Linux desktop (owner check: the tarball with `LANG=de_DE.UTF-8`, a local file and a Jellyfin stream played).

**B-026, Windows process.** Packaged `Reflux.exe` (`createDistributable`), scratch `REFLUX_HOME`, closed by WM_CLOSE to the application's window. Found: a packaged Reflux is two processes, the launcher (5 threads) and the application (about 100 threads) as its child, which is what Task Manager shows as two Reflux entries while it runs. 19 rounds (5 + 7 of the working tree, 7 of HEAD): the application exited 0.70 to 0.82 s after close, the launcher within 10 ms of it with code 0, nothing left in any round. Not reproduced: a process left after closing. Not tested: closing during playback, a hung engine (the 5 s release patience). **Procedure for a tester who sees one after closing:** in PowerShell, `Get-CimInstance Win32_Process | ? ExecutablePath -like '*Reflux*' | select ProcessId,ParentProcessId,ThreadCount,CreationDate,CommandLine`, then a support report from a fresh start, both sent with whether something was playing when it was closed. My first script closed the launcher, which has no window; those rounds are not counted.

**B-003, several Jellyfin addresses.** `:core:jvmTest --tests EndpointSelectorTest` 4/4 (rank, order, sticky, fail-over, a minute's recheck, unknown addresses). `:sources:jellyfin:jvmTest` all passed: 63 existing and 7 new in `JellyfinEndpointsTest` (a second address added, not swapped, home first either way, one identity; another server or user never combined; an old connection reads back byte for byte; home, then Tailscale while home is away, streams included and requests not waiting for home, then home again a minute on; an HTTP error not failed over; one address removed, never the last; a kept token never ended at another address). `:desktop:engine:test --tests JellyfinEndpointsLibraryTest` 4/4 through the real library and connections (one source and one copy of each film with both addresses; the first session ended, one left; both addresses known; away and back across a restart; one address removed with the library untouched; another profile's account untouched). **Not verified:** a live server at two real addresses (owner check: add the server at its LAN address and again at its Tailscale address in one profile; Sources shows one card with both; with the LAN unreachable it plays; with the LAN back, the next check returns to it).

**B-033, unavailable favourites.** `:library:jvmTest --tests UnavailableFavoritesTest` 2/2 (the favourite stays, is unavailable, is removed without the source, does not return when the source does, another profile's untouched; one that is kept comes back playable). `:ui:jvmTest --tests ScreensTest` 23/23 (1 new: the page says it is a favourite and removes it).

**B-024, resolution and HDR.** `:core:jvmTest --tests VersionNamesTest` 3/3; `:ui:jvmTest --tests FormattingTest` 11/11 (1 new). `JellyfinSourceTest`'s expectation that "Heat 4K" and "Heat 1080p" are editions was changed to none: it asserted the defect.

**B-005, the scanning sign.** New `ActivityIndicatorLayoutTest` 4/4, the sign's bounds against every heading and text field: phone (412 x 915) Home, Movies with a very long source name, Search; tablet (1200 x 800), desktop (1440 x 900), and small window (960 x 640) with Home, Search, and a long name kept within 260 dp; television. Its first run found the dot over the phone's Search field (x 372 to 396 against the field's 68 to 388, the rows overlapping); it now steps aside there as the profile entry does.

**B-006, search and listings.** Fixture `SearchPerformanceTest` (`-Preflux.perf=true`): 1,000 films and 200 shows of 50 episodes from stub files, 11,000 playables, scanned for real, no metadata provider; JVM on this machine, warm, 25 runs each.

| Query | Before median / p95 | After median / p95 |
| --- | --- | --- |
| Title, a prefix ("silent") | 236 / 253 ms | 13 / 19 ms |
| Title, two words | 226 / 232 ms | 16 / 20 ms |
| Title, a miss | 157 / 163 ms | 1 / 2 ms |
| Title, an episode's show | 225 / 236 ms | 27 / 36 ms |
| Smart, structured ("movies from the 90s") | 170 / 177 ms | 99 / 107 ms |
| Smart, falls back to title | 232 / 239 ms | 17 / 23 ms |

Where the time went before: resolving metadata for every item (about 103 ms: 44 freshly compiled 500-parameter lookups, even with no metadata at all) and reading every row (about 47 ms), on every keystroke and every library revision; then availability for the whole library to show 50 hits. `SearchFreshnessTest` 3/3 (a new file, a description, a removed file, a removed source, a profile switch, a source added later: each in the very next search). Not measured: Trakt lookups (bounded by Trakt's 500 requests per 5 minutes, which is the metadata speed of a new library), artwork decoding, the fixture on an Android device.

**Statistics foundation.** `:core:jvmTest --tests "dev.reflux.core.telemetry.*"` 5 + 1 passed: off by default, nothing recorded or sent; the build's transport sends nothing even with a yes, and makes Settings offer nothing; a yes sends one batch and a no stops and forgets; a refused or offline batch is dropped once and the queue is bounded; buckets, and a version that cannot be free text; every field of every event is an enum or the build's context. Nothing in the app records or sends an event.

**Workstream 12, Linux libmpv.** mpv 0.42.0's `meson.build` read (2026-11-18): `x11` requires `gpl`; `x11_common.c` and `vo_x11.c` are GPL-only; `gl-x11`, `egl-x11`, `vdpau`, `vaapi-x11` require `x11`. No libmpv was bundled (DECISIONS.md). `:desktop:app:test --tests LinuxPlaybackSetupTest` 2/2. The packaged tarball, built in WSL from the working tree (92,000,114 bytes; not a release artifact), in clean containers under `de_DE.UTF-8`: with no libmpv it starts; with exactly the package the hint names, its engine starts (the hardware-decoding probe ran) and its log notes the numeric locale. Ubuntu 22.04 (glibc 2.35, `libmpv1`, client API 1.109), Ubuntu 24.04 (glibc 2.39, `libmpv2`), Debian 12 (glibc 2.36, `libmpv2`), Fedora 41 (glibc 2.40, `mpv-libs`). Playback through the developer CLI, with a JRE in the container (the tarball's runtime has no `java` launcher): on Ubuntu 22.04 and 24.04 the clip was probed (720p, H.264), played to its end (exit 0), and decoding and audio output reported, with no locale or initialisation problem in any log. Not run: a desktop session with a real display and sound; Arch; openSUSE.

**Baselines (WS11).** Desktop, packaged, empty library, 7 runs each, HEAD from a detached worktree (removed afterwards) against the working tree: start to window median 2.7 s and 2.6 s (5.0 to 5.2 s on each first, cold run; then 2.2 to 2.7 s); working set at 6 s median 492 MB and 500 MB; close to both processes gone 0.71 to 0.80 s and 0.71 to 0.82 s. Android release cold start (`am start -W -S` of the installed store build, whose app code is identical to `v0.9.0-beta.1` and HEAD; 7 runs): Phone 2a median 347 ms, p95 1,283 ms (one outlier; the others 313 to 412 ms); Pad 7 median 302 ms, p95 397 ms; Google TV emulator (an older `0.9.0-alpha.2` under the store ID) median 238 ms, p95 419 ms; Fire TV Stick 4K Plus (reconnected at its new address) median 480 ms, p95 517 ms (6 of 7 runs reported a time). **Not measured:** an Android release build of the Phase 1 code (none can be installed beside the store's without replacing it), Linux on hardware, scrolling frame times, Android memory and CPU, Macrobenchmark (recommended for M12, at its cost: a module, an AndroidX dependency, a benchmark build type).

**Full check:** `./gradlew check --continue` on this Windows machine, the working tree as described: BUILD SUCCESSFUL in 12 min 59 s. 1,496 tests from the result files: 0 failures, 0 errors, 24 skipped (the existing assumptions: live servers not given, Linux-only and Windows-only cases, the performance fixture without `-Preflux.perf`). Per module: android 56, core 377, design 43, desktop app 56 (13 skipped), desktop engine 51 (6), library 256 (1), Trakt 23, mpv 57 (1), Jellyfin 70, local 6, Plex 23, SMB 10 (2), WebDAV 20, ui 448 (1). Hosted CI has not run: nothing is pushed.

**Closeout (2026-11-19):** history/phases/PHASE-1-PLAN.md, "Stage F: closeout", reconciles each workstream with this evidence; Phase 1 is closed as implemented with the checks named there outstanding.

## Open-source transition: GPL-3.0-or-later (2026-11-19)

On the branch `open-source-gpl`, made from `main` at `540147c` with the Phase 1 work uncommitted in the tree (it came along unchanged). DECISIONS.md, 2026-11-19. Nothing committed, pushed, tagged, released, or entered in a store console.

- **License text:** `LICENSE` downloaded from https://www.gnu.org/licenses/gpl-3.0.txt with `curl` (the first attempt was reset by the network; the retry succeeded), 35,149 bytes, SHA-256 `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986`, the published digest of gnu.org's text; copied unmodified (LF, pinned by `.gitattributes`). `NOTICE` added.
- **Rights audit:** `git log` over 253 commits (2026-10-08 to now): authors Rifaque (two addresses) and Claude (22 commits); co-author trailers all Claude models; no merges, no pull requests. Files carrying another holder's notice: the Gradle wrapper (Apache-2.0), `design/brand/Manrope-OFL.txt`, the generated decode-probe samples (encoder strings only), `legal/` texts. `legal/components.txt` read line by line for GPL-3.0-or-later compatibility (DECISIONS.md, 2026-11-19). Not legal advice; the open questions are in RELEASE.md, "Legal review".
- **App (owner-authorised):** `:ui:jvmTest --tests AboutUiTest` 12/12 (rewritten: the notice is `NOTICE` then the GPL, the copyright line is Reflux's and never the Free Software Foundation's, "Free software · GPL-3.0-or-later", no "proprietary" on the page, the license page names GPL-3.0-or-later and shows the license's Preamble; and `everyNoticeHasItsTexts` compares the app's text with `NOTICE` + `LICENSE` word for word). `AboutWindowTest` (real window, `-Preflux.windowTests=true`, Windows): **fails on unmodified HEAD too** (2 of 2 runs, at the arrow to Reflux's terms row, which lands on Privacy, a stop added after the test was written); on this branch 1 of 2 runs passed the whole flow (About, the notice, Escape back to the row, the third-party notices) and 1 failed at the same early step. A pre-existing intermittent defect of the test, not of the change; not fixed here. `AboutOnDeviceTest` updated, not run on a device.
- **Website:** `npm test` 32/32; `npm run build`; `check-dist` 17 pages and 47 files, 0 problems; `check-layout --screenshots` 17 pages at 7 sizes, 0 problems, with the License page and Home looked at by eye at desktop and phone width; `check-links`: every internal link resolves, 31 external checked, 3 unreachable (trakt.tv twice and Plex's privacy page, both known to answer automated clients badly), the new GitHub, CONTRIBUTING, LICENSE, and gnu.org links resolve (the LICENSE link on `main` shows the old notice until the branch is merged). Found and fixed while looking: the site's font drew "7(c)" as "7©"; `NOTICE` now says "section 7, item c". The live site (reflux.rsxdevelops.dev) was not changed: it still says proprietary until a merge deploys this.
- **Wording search** (`proprietary`, `all rights reserved`, `not open source`, LGPL-only, the render API, redistribution): every remaining match reviewed by hand and kept on purpose: the `0.9.0-beta.1` changelog entry (true of that build), the superseded 2026-10-19 decision and older verification and planning sections (history), third parties' own notices (Skia, FreeType), the GPL's own text, and the test that asserts the word is absent.
- **Full check:** `./gradlew check --continue` on this branch (Windows): **BUILD FAILED** in 9 min 18 s on one test, `PointerTest.controlsLeaveWhilePlayingAndReturnWithThePointer`, the known intermittent in M12's carried backlog (ROADMAP.md); 1,496 tests, 1 failure, 0 errors, 24 skipped. `:ui:jvmTest --tests PointerTest --rerun`, three times: 18/18 each. Not caused by this change (it touches no pointer or player code); not fixed here.

## Website documentation portal, Download, and the Beta 2 register (2026-11-19)

On the branch `open-source-gpl`, uncommitted at the time of these runs (DECISIONS.md, 2026-11-19, "The website gets a documentation portal"). Nothing deployed: the live site was only read.

- **Download speed, investigated before redesigning.** The live site: pages served from Vercel's edge (`bom1`, cache `HIT`), a few kilobytes of HTML each. Desktop downloads: one redirect from github.com to GitHub's release-asset service, the transfer starting in 0.19 to 0.49 s and running at 8.3 to 11.7 MB/s from this connection (several runs, both archives). The site's own weight was its one script bundle: 396 KB (127 KB compressed) live, carrying the changelog and, on this branch, the GPL's 36 KB text. After moving the GPL to a static `/license.txt` and adding the documentation: 420,832 bytes, 130,152 compressed (`gzip -9`). The congestion seen on the old Download page was its layout (one long column of mixed text and buttons), not delivery; the new page is a card per platform.
- **Site checks**, after the last change: `npm test` 32/32; `npm run build`; `check-dist` 24 pages and 55 files, 0 problems (its first run found the new Download page missing the exact "Reflux 0.9.0-beta.1." wording and a `SHA256SUMS` link, both fixed); `check-layout --screenshots` 24 pages at 7 sizes, 0 problems (its first run found the cards' "Installation guide" links 24 px tall; now 32 px); `check-links` (with the script's own header-size option): every internal link and anchor resolves, 31 external links, 1 unreachable: Plex's privacy page, which already was before this work and answers automated clients with a redirect loop (to check in a browser). Looked at by eye: Download and the Linux guide at desktop width, the docs at phone width; found and fixed by eye: the copy button covering the end of long commands.
- **Linux guide claims, each against a run:** Ubuntu 24.04, Debian 12, Fedora 41, Ubuntu 22.04 (`libmpv1`) from Phase 1's containers (above). **Arch Linux** (`archlinux:latest` container, `pacman -S mpv`): the packaged app found libmpv and its engine started; the image has no locale data, so the non-C locale case was not exercised there. Linux Mint was not run; the guide says its commands come from its Ubuntu base and that Mint itself was not tested.
- **A defect found writing the Mint tab:** Reflux's own hint (`LinuxPlaybackSetup`) told Linux Mint 21, whose base is Ubuntu 22.04, to install `libmpv2`, which does not exist there. It now reads the base from `UBUNTU_CODENAME` (or Debian's `VERSION_CODENAME`). `:desktop:app:test --tests LinuxPlaybackSetupTest` 2/2 (Mint 21 and 22, Pop!_OS 22.04, Raspbian 11 added).
- **B-031's wording corrected:** the tester's locale was `en_US.UTF-8`, so libmpv refused to start on nearly every Linux desktop, not only comma-decimal languages; CHANGELOG, DECISIONS, the code comment, and the guide now say so. No behaviour changed.
- **The Beta 2 register** (history/phases/BETA-2-PHASES.md) was written from the owner's findings document and checked against it entry by entry for IDs and meanings; the statuses there cite only the runs in this file.

## Beta bug tracker and register reconciliation (2026-11-19)

On the branch `open-source-gpl` at `0a0396f` (the four local Beta 2 commits: `c1ca1d1` Phase 1, `c9c222f` GPL, `c9014e0` website, `0a0396f` Beta 2 plan), with the tracker work uncommitted at the time of these runs. Nothing pushed, merged, deployed, or entered in a store console.

- **Full check on the committed tree, forced:** `./gradlew check --continue --rerun-tasks` (Windows; an earlier plain `check` was all up to date and proved nothing): **BUILD FAILED** in 10 min 53 s on one test; 1,496 tests, 1 failure, 0 errors, 24 skipped. The failure: `AccessibilityTest.withoutAScreenReaderTheyStillGo`, a `ComposeTimeoutException` at `AccessibilityTest.kt:218` waiting for the player controls to leave, while every module compiled and tested at once. The test file is unchanged since `540147c` and none of the four commits touches it or the player controls. `:ui:jvmTest --tests "*AccessibilityTest*" --rerun` alone, three times: passed each time. Recorded as B-044 (tracker); not fixed, not weakened.
- **Website, on the committed tree:** `npm run build`; `npm test` 32/32; `check-dist` 0 problems.
- **Tracker consistency** (a throwaway script, not committed): every ID B-001 to B-045, F-001 to F-005 and the F-P1/F-P2 entry has exactly one anchor; no duplicate anchor; every `#anchor` link in the tracker and BETA-2.md resolves to an anchor or heading in its target file; every relative file link resolves; each status in the phase overview matches its entry's header; the summary's counts add up (42 + 5). Results in the tracker's change history.
- **New Linux QA VM:** `Reflux-QA-Ubuntu-26.04` created with VBoxManage 7.2.22 (4 vCPU, 8 GB, 60 GB VDI on SATA, EFI, VMSVGA with 3D and 128 MB, NAT, audio) from `ubuntu-26.04-desktop-amd64.iso`, and booted to the installer; the owner entered the account. No result from it yet; results from it are VM results, never physical.

## Phase 1 closeout walks: Linux desktop session (VM), Android devices, Windows (2026-11-19)

On `open-source-gpl` at `556887b`, with three new on-device test files uncommitted at the time of these runs (`LoopbackFileShare.kt`, `PhaseOneWalkOnDeviceTest.kt`, a sixth case in `SurfaceHandoverOnDeviceTest.kt`). The owner asked for Phase 1's remaining work to be carried out. Nothing pushed, merged, deployed, versioned, or entered in a store console; the store installs on the devices were not touched (the debug build sits beside them).

- **Linux desktop session, B-031 (VM, not physical).** The Ubuntu 26.04 QA VM (`Reflux-QA-Ubuntu-26.04`, VirtualBox 7.2.22: 4 vCPU, 8 GB, 60 GB, VMSVGA with 3D; GNOME on Wayland, Reflux's window through XWayland; the session's own locale `en_US.UTF-8`; the distribution's `libmpv2` 0.41.0-2ubuntu4). The tarball was built in WSL2 from `556887b` (`packageDesktopArchive`, 92,022,898 bytes; not a release artifact) and copied in; a 60 s 1080p H.264/AAC film was made with the VM's ffmpeg and added with the CLI (whose `info` probed it through libmpv under `en_US.UTF-8`). **Released `0.9.0-beta.1`** (downloaded from the GitHub release; SHA-256 `56310b3c…14e`, as in its SHA256SUMS), same session and locale, Play: stderr "Non-C locale detected. This is not supported. Call 'setlocale(LC_NUMERIC, "C");' in your code." twice, and its log "libmpv was found but could not be initialized" and "The playback engine is installed but could not be started": the tester's report, reproduced on a desktop. **The fixed build**, no workaround: under `en_US.UTF-8` the film played (screenshots: its burned-in clock at 00:00:06, then 00:00:21), the log said "The numeric locale was en_US.UTF-8; it is C for libmpv (language and formats are unchanged)" and "A playback changed what is known of the audio output"; restarted under `de_DE.UTF-8`: the same, with "was de_DE.UTF-8". The Details page's version line read "H.264 · 1920 × 1080 · AAC Mono", the measured size (B-024). Driven with VirtualBox's virtual keyboard (`keyboardputscancode`): xdotool's synthetic click made GNOME ask for remote-desktop permission, which was refused and its portal restarted; nothing was granted.
- **Linux baseline, B-041 (VM).** The same VM and build, an empty scratch library, `en_US.UTF-8`, 7 runs, run 1 after `vm.drop_caches=3`: start to window mapped (`wmctrl`) 6,451 ms cold, then 2,849 / 2,841 / 2,870 / 2,901 / 3,267 / 3,353 ms (median of 7: 2,901 ms); resident memory 6 s after start 346 MB cold (its window just up), 676–721 MB warm; close (WM_DELETE through the window manager) to the process gone 709 / 465 / 552 / 591 / 454 / 649 / 2,046 ms (median 591 ms). On Linux the packaged app is one process.
- **B-001, a server source (physical).** `SurfaceHandoverOnDeviceTest` with its new sixth case `fromAFolderToAServerAndBack`: a film from a folder, then one streamed over HTTP from a WebDAV share on the device's loopback (`LoopbackFileShare`: PROPFIND listings, GET and HEAD with byte ranges; only those methods seen), then the folder's again, the picture read off the surface each time. Phone 2a 6/6, Pad 7 6/6 (the five earlier cases unchanged and passing).
- **B-033, the reported case (physical).** `PhaseOneWalkOnDeviceTest.aFavouriteOfARemovedSourceIsKeptAndCanBeRemoved`: a favourite film; its source removed through `AppState.removeSource` (as Sources does); still a favourite; its page shows "This is no longer in your library" and "Remove from favorites"; pressed through the accessibility tree; the favourite gone. Phone 2a and Pad 7 passed; screenshots `b033-<model>-gone-page.png` and `-removed.png`, looked at.
- **B-005, the scanning sign (physical).** `PhaseOneWalkOnDeviceTest.theLibrarysSignNeverCoversALabelOrAField`: Home, Movies, and Search opened in turn, each while 400 new files are scanned; whenever the sign is on screen its bounds are checked against every other visible label and text field. First run: the tablet saw the sign on all three pages, clear; the phone only on Home (the later updates had nothing new to do). The test was changed to add new files for each page and re-run: **Phone 2a**: Home and Movies, sign seen and clear; Search, no sign (on a phone's Search the sign steps aside, as designed); **Pad 7**: all three seen and clear. Screenshots looked at: the phone's Movies heading with the dot beside the profile button, nothing covered; the tablet's Search with "Scanning Walk library" in its own bar above the field.
- **B-026, closing during playback (physical, Windows).** The packaged app (`createDistributable` of `556887b`), a scratch `REFLUX_HOME` with the film, five rounds: started; Play (Tab, Enter); a screenshot 6 s later with the film's clock running; `libmpv-2.dll` loaded in the application process; WM_CLOSE to the window. Before closing: the launcher (5 threads, 8 MB) and the application (160–168 threads, 595–646 MB). Each round, both processes gone 816 / 747 / 766 / 738 / 743 ms after the close; launcher exit code 0; nothing left. Not reproduced; still not explained.
- **B-041, Android (physical, store build).** The installed store build (`0.9.0-beta.1`, release, the owner's library as installed; read-only: performance counters and swipes), 3 runs each: memory after a cold start (`am start -W -S`, `dumpsys meminfo` TOTAL PSS at 6 s): Phone 2a 331,010 / 307,594 / 376,326 KB; Pad 7 333,773 / 279,895 / 289,038 KB. Frame pacing scrolling Home (`dumpsys gfxinfo`, 8 swipes down and 8 up): Phone 2a 2,824 / 2,796 / 2,780 frames, janky 0.04 / 0.29 / 0.43 %, p50 10 ms, p90 14–17 ms, p95 21 ms, p99 40–46 ms; Pad 7 1,344 / 1,260 / 1,352 frames, janky 0.22 / 0.16 / 0.22 %, p50 13 ms, p90 18–22 ms, p95 30 ms, p99 36–38 ms. Cold-start times during these runs: phone 641 / 566 / 485 ms, tablet 450 / 705 / 496 ms.
- **Google TV emulator** (`Reflux_TV`, sdk_google_atv64, Android 16; an emulator result, not physical): the same debug and test APKs; `PhaseOneWalkOnDeviceTest` and `SurfaceHandoverOnDeviceTest`, 8 run, OK (the mini-player case skipped by its assumption: no mini-player on a television). B-005: the sign seen and clear on Home, Movies, and Search (the Movies screenshot was taken just after the sign went; the bounds check is the evidence); B-033's walk and B-001's server case passed. The Fire TV was not connected: B-005's Fire TV walk is outstanding.
- **Full check**, forced, on this tree (`./gradlew check --continue --rerun-tasks`, Windows): **BUILD SUCCESSFUL** in 12 min; 1,496 tests, 0 failures, 0 errors, 24 skipped (the same assumptions as before). `AccessibilityTest.withoutAScreenReaderTheyStillGo` (B-044) passed this time. The on-device tests are not part of `check`; they ran above. Hosted CI has not run (nothing pushed).
- **Phase 1 against its exit criteria:** history/phases/PHASE-1-PLAN.md, "Stage G"; each issue: planning/BETA-BUG-TRACKER.md.

## Phase 2: controller, focus, interaction, input, haptics (2026-11-19)

On `open-source-gpl` from `7dbade6`, with Phase 2's changes uncommitted at the time of these runs (committed afterwards as `77b994c`, code and tests, and the documentation commit after it). The owner accepted Phase 1 and authorised Phase 2 the same day. Plan, audit and contract: history/phases/PHASE-2-PLAN.md; each issue: planning/BETA-BUG-TRACKER.md.

Nothing was pushed, merged, deployed, versioned, tagged, released or entered in a store console. The store installs on the devices were not touched (the debug build sits beside them).

**Automated (Windows 11, JVM, offscreen Compose).**
- `:ui:jvmTest :library:jvmTest :core:jvmTest`, run before the walks: ui 487 tests, 0 failures, 1 skipped; library 257, 0, 1; core 377, 0, 0. BUILD SUCCESSFUL.
- New test classes:
  - `InteractionContractTest` (8): Backspace in the deletion PIN, the unlock PIN, a profile name and Settings' rename; Escape first lets go of the field; a controller types through the Reflux keyboard; a PIN entered with a controller over the profile gate; a remote on a television; Search with a controller alone.
  - `KeyboardSessionTest` (9).
  - `HomeFocusTest` (5): B-008's routes, B-007's See all, B-013's hero.
  - `CardLayoutTest` (4): B-009, B-015, B-032, B-010.
  - `ProfileSecurityTest` (4): B-049, Forgot PIN.
  - `PullToRefreshTest` (4).
  - `HapticsTest` (7), with the source scan.
- New library test: `ProfilesTest.aPinIsChangedOrRemovedOnlyWithItself`.
- Tests changed to the new behaviour, each because the behaviour changed by design:
  - `KeyboardTest`: OK opens Reflux's keyboard, never the system's.
  - `TelevisionTest`: Up from a row's card is its See all first.
  - `DirectionalInputTest`: the hints name Y Sources and never X.
  - `ScreensTest` and `PointerTest`: no "Try"; the designed empty state.
  - `ProfilesUiTest`: Forgot PIN's new choices; `removePin` needs the PIN.
  - `WatchNextTest`: `removePin` needs the PIN.
- **Found by the tests while working:**
  - The B-008 race: a press while Home was still being read fell to the toolkit's search, which picked the rail. A `DEBUGFOCUS` trace showed `hasFocus=false`, `inChrome=false`.
  - `focusRestorer` on Home's rows broke the rail's way back in, so it was replaced by explicit row targets.
  - `setPin` inside a read-then-write transaction met `SQLITE_BUSY` under the app's concurrent writes, so it was made one conditional statement.
- Forced full check: below.

**Phone, Nothing Phone (2a), Android 16 (physical, USB).** The debug build, `PhaseTwoWalkOnDeviceTest`: **3/3 passed** (after two corrections to the test's own input: a finger's tool type on injected touches, and the query committed as an IME commits it rather than as hardware key presses). Screenshots looked at:
- **Search:** "Search your library" short and grey; chips with no "Try", each at least 48 dp tall.
- **Keyboard (B-036):** with the system keyboard up the bar stepped aside; put away with Back it came back while the field kept focus; the next Back went Home; the query "zzqx" was kept.
- **Empty state:** "No matches for “zzqx”", with Clear search and Browse movies.
- **Profiles:** no Profile button in its top row; Home and Settings have it.
- **Pull (B-048):** a pull at the top of Home showed the turning sign and found a film added on disk meanwhile. Beside the Profile button was the new library-activity arc (B-047) rather than a dot.

- **Forgot PIN with the device lock:** a scratch profile with a PIN, then Forgot PIN. "Use device lock" was offered (the phone has a screen lock) and the system's own prompt opened over Reflux; its screenshot is black, because the system keeps its prompt out of screen captures. Back cancelled it, Reflux said "Nothing was changed.", and the PIN was still there. The scratch profile was deleted with its PIN afterwards. 4/4 with this walk.

The phone was unplugged during part of the phase and plugged in again by the owner for these walks.

**Tablet: a tablet emulator (Emulator; `Reflux_Tablet`, created 2026-11-19, Android 36.1, 2560 × 1600 at 320 dpi, headless).**
- `PhaseTwoWalkOnDeviceTest` **3/3 passed**. At 1280 × 800 dp Reflux lays it out with the desktop sidebar (its rule: 1200 dp wide or more).
- **The physical Pad 7 was not walked.** Its debug build's Main profile asks for a PIN that was set during earlier testing and is not known to the agent. It was not guessed, cleared or removed. This is an owner check.

**Google TV (Emulator; `Reflux_TV`, headless).** The debug build driven with remote keys through adb:
- Left into the rail, Down three times to Search, OK. Then OK on the field opened the **Reflux keyboard** (the system keyboard never appeared).
- Right and OK twice typed "ww" (the preview showed it).
- Back put the keyboard away, with Search still shown and the query kept.
- The screenshots were looked at.

**Windows desktop (physical).**
- The packaged app (`createDistributable`) was built. The owner's machine has two monitors at different scaling (DPI 120 and 144).
- A B-002 reproduction script was run once and **stopped**: it found the owner using the machine (a game and a chat window), and its Tab/Enter keystrokes, meant to start playback, went to the foreground window. Its full-screen captures were deleted at once.
- Measured, and still valid: moving the Reflux window with `SetWindowPos`, 1280 × 800 became 1536 × 960 on the 150% monitor and 1067 × 667 back on the 125% one.
- No controls window was found, because playback had not started.
- No further desktop automation was run without the owner's go-ahead. B-002 stays Investigating; the script is ready for a session the owner allows.

**Not walked (gaps, by kind).**
- Fire TV (physical): not connected; network adb at an address the agent does not have.
- Physical controllers: no Xbox, PlayStation or other pad was used, so rumble (F-002) was not felt, and a disconnect during rumble was not tried.
- Interface haptics (F-001): not felt on a device.
- Linux VM: not run in Phase 2, because no Linux-specific code changed except the shared UI. Rumble is not offered there (no force feedback through the joystick interface).
- B-002: see above.
- The Pad 7: see above.

**Forced full check.** `./gradlew check --continue`: 1,537 tests, 0 failures, 24 skipped. **BUILD FAILED on Android lint**, with 4 errors, all real:
- `Vibrator.vibrate` and `cancel` need `VIBRATE`, and `BiometricManager.canAuthenticate` and `BiometricPrompt.authenticate` need `USE_BIOMETRIC`. Neither was declared.
- On a device, rumble and the system prompt would have quietly failed, each caught as "unavailable".
- Both were added to the manifest (normal permissions, no prompt; RELEASE.md says so). `:android:app:check` then passed (lint included).
- The Phone 2a walk was re-run with the fixed build: 4/4, Forgot PIN's system prompt included.

## Phase 2: final verification (2026-11-19)

On `open-source-gpl`, the owner's final Phase 2 verification brief and the follow-up brief. Each run's environment is named. Nothing was pushed, merged, deployed, versioned, tagged, released, or entered in a store console. No screenshot, address, serial or credential is in the repository. The Pad 7's profile PIN was given by the owner for the session and passed only on the command line.

**Windows 11 desktop (physical; two monitors at 125% and 150%).**
- **A guarded driver** replaced the B-002 script:
  - input is sent only when the foreground window is the Reflux test process, raised without sending keys anywhere (`AttachThreadInput`);
  - the pointer is moved only where Reflux's window is under it;
  - captures are of Reflux's own windows (PrintWindow);
  - the test library holds only test films.
  - It refused once when Reflux was not in front, and sent nothing.
- **B-002:**
  - **Reproduced:** on the 150% monitor the controls window was 1010 × 1044 over a 1514 × 1566 picture.
  - **Fixed** (Java2D's Direct3D pipeline off). Walked with title-bar drags each way: the controls window equalled the picture (1515 × 906 over 1514 × 904; 1264 × 755 over 1262 × 753). It held paused, in the mini-player, on expanding it, and maximised (2562 × 1494 over 2560 × 1494).
  - **Control run:** the pipeline forced on brought the fault back (1010 × 604 over 1514 × 904).
  - Captures looked at.
- **Physical controller: a GameSir G7 Pro on its 2.4 GHz receiver (XInput slot 0, both motors), used by the owner.** Its presses were logged through XInput, and Reflux's window was captured half a second after each.
  - **Round one:** moves and the left stick navigate; ticks felt on every move; X does nothing (reserved); LT/RT did nothing; LB/RB step destinations; the right stick does nothing (Trickplay, Phase 3).
    - Y opened Sources; the owner said it should toggle the sidebar (F-P2, done).
    - Two crashes (`0xc0000374`) at the moments the controller was switched off: B-050.
  - **B-050:** four probe processes, one per native path. The joystick API crashed on reconnect; XInput (with rumble) and raw input did not.
    - After the fix, the full reader and Reflux ran through three off/on cycles with no crash and no new Application Error.
  - **Round two:**
    - Y to the sidebar and back to the same poster: "works perfectly fine".
    - X erases, Y gives a space, B closes the keyboard.
    - Controller rumble off: nothing felt; on: back.
    - The keyboard's cursor jumped to the end after one letter (B-054).
  - **After the B-054 fix:** ABD, LB, C, E gave ABCED, the cursor shown in both places, holding X erases on: "All correct".
  - **Follow-up:**
    - Details lands on Play, and A again plays.
    - RT closes the mini-player, and so does holding B, with the ring filling.
    - The mouse keyboard: the button, keys clicked with none left lit, Erase, Space.
    - "All worked", except the screen's brief blank on closing the mini-player: B-056. It is the same with the B-002 change off, and the same by mouse.
  - **The hint bar (B-055):** three rounds with the owner, ending "Looks right".
- **Not walked:** a DualSense (non-XInput) hot-plug (B-050's remaining case).

**Xiaomi Pad 7 (physical, USB).** The debug build of this branch, unlocked with the owner's PIN passed as an instrumentation argument. `PhaseTwoWalkOnDeviceTest`: **5/5 passed**:
- Search with the system keyboard: the bar steps aside, Back puts the keyboard away, and the next Back goes Home;
- Profiles has no Profile button; Home does;
- a pull finds a film added meanwhile;
- Forgot PIN offers the device lock, and cancelling changes nothing;
- interface haptics: the Pad 7 has **no vibrator** (`cmd vibrator_manager list`: none), so Settings rightly offers no switch.

Screenshots looked at; on touch, Search shows no mouse-keyboard button.

**Fire TV Stick 4K Plus (physical, network adb at an address the owner gave for the session, not recorded).** The debug build of this branch. Remote key codes injected by the system's input service (`input keyevent`, as adb does); no physical remote press. `PhaseTwoWalkOnDeviceTest`, the two television walks: **2/2 passed**.
- OK on a card opens Details with the ring on Play, nothing playing, and OK again plays.
- In Search, OK opens the Reflux keyboard; the system keyboard stays hidden (Fire OS reports it hidden).
- "jun" is typed by the remote, with the caret after it in the field, and found.
- Back puts the keyboard away with the query kept.
- Screenshots fetched with `run-as` and looked at.
- On the way, the instrumentation's own key sending was seen not to reach the field as a remote does, so the walk uses the input service.

**The GameSir moved to the Fire TV (Bluetooth; the owner paired it).**
- A walk by hand was set up as an instrumentation (the test film, Reflux open, five minutes for the owner). It could not start Reflux within 45 s ("the main thread has not gone idle"), and at its end the system stopped Reflux ("Killing … due to finished inst"; at its start, "due to start instr").
- **The owner saw that as a crash; it was not one.** The crash buffer was empty. The squircle keys the owner saw were from a stale app build: only the test package had been reinstalled.
- Walks by hand are not run as instrumentations again. The controller on the television is driven by an automated walk with gamepad events from the system's input service (below).

**Not done:** the controller on the phone, the tablet and the Google TV emulator; the Phone 2a (not attached).

**Linux.** WSL2 Ubuntu 24.04 (**a VM**, no `/dev/input`, no controller). On its JDK 21, all passed:
- `:desktop:app:test` gamepad suites, including `LinuxRumbleTest` 5/5 (with the Linux-only check that libc's `open`/`ioctl`/`write` load and that a missing node is refused quietly), and `ToolkitPreparationTest` 3/3;
- `HapticsTest` 7/7 and `KeyboardSessionTest` 10/10;
- core 377/377.

**Not done:** physical Linux rumble (no Linux desktop with a controller), and the Ubuntu QA VM (not started this pass).

**Automated (Windows, JVM).** After the follow-up: core 377, library 257 (1 skipped), ui 499 (1 skipped), desktop 61 (13 skipped, the window and physical tests), 0 failures. New suites:
- `ToolkitPreparationTest`, `ControllerFeelTest`, `DetailsFocusTest`, `PointerKeyboardTest`, `HintsTest`, `LinuxRumbleTest`;
- new cases in `HomeFocusTest`, `MiniPlayerTest`, `GamepadTest`, `KeyboardSessionTest` and `InteractionContractTest`;
- `DirectionalInputTest` updated (hints; arrival on Play).

**Forced full check.** `./gradlew check --continue --rerun-tasks`, on `e5f595e`: **BUILD SUCCESSFUL** (exit 0), Android lint included.
- 1,565 tests, 0 failures, 25 skipped: the window and physical-device tests that run only on request, and the platform-specific ones.
- An earlier run, before the follow-ups, failed only on lint (`GestureBackNavigation`, fixed in `d4aa5ee`).

### Search redesign (B-057), 2026-11-19

- **Automated (Windows, JVM):** `SearchDesignTest` 6/6, plus the updated `CardLayoutTest`, `ScreensTest` and `PointerTest`. The whole suites after it: core 377, ui 506 (1 skipped), desktop 66 (14 skipped), 0 failures.
- **Rendered and looked at (offscreen Compose, the same renderer as the desktop app):**
  - a desktop window: idle, with Recent; Movies scope; no results;
  - a phone (412 × 900);
  - a tablet (1280 × 800);
  - a television (960 × 540 at 2×).
- **Xiaomi Pad 7 (physical):** `PhaseTwoWalkOnDeviceTest` all passed.
  - Search on touch: the switch on the field's line, the quick finds counted from the walk's library ("Not seen yet", "Under 2 hours", "The 2010s", 1 title each), each at least 48 dp tall.
  - The system keyboard for a finger, with no desk keyboard button.
  - Screenshot looked at.
- **Fire TV Stick 4K Plus (physical; the current app build):** 3/3. The remote walks, and a controller walk with gamepad events from the system's input service (`input gamepad keyevent`):
  - A on a card lands on Play, and B goes back;
  - X does nothing, and Y opens the sidebar on Home and returns;
  - RB and LB move between destinations;
  - LT hides the hints, leaving "LT Show hints", and shows them again;
  - in Search, A opens the Reflux keyboard, A types, X erases, and B closes it with the query kept.
  - Screenshots fetched with `run-as` and looked at. These are gamepad events from the system, not presses of the owner's GameSir.
- **Nothing Phone (2a) (physical, attached later in the pass):** `PhaseTwoWalkOnDeviceTest` all passed.
  - Search on a phone: the switch under the field; quick finds two across, each a finger's target.
  - The system keyboard and the bar stepping aside; Profiles; a pull; Forgot PIN's system prompt.
  - Interface haptics: switched on and kept, then put back.
  - Its screenshots were looked at and deleted, because a personal notification was over one of them.
- **Windows:** the packaged app was opened on the owner's PC with their own library, for the owner to look at Search; the agent did not drive or capture it.
- **Not done this pass:** the Google TV emulator; the Linux VM.

### Search, second design (B-057), 2026-11-19/20

- **Automated (Windows, JVM):** the whole ui suite, 506 tests, 0 failures, 1 skipped. `SearchDesignTest` 6/6; `TelevisionTest` updated (Up from a result reaches the tabs, then the field). Renders looked at: desktop idle with Recent, phone, television, results in sections, no results.
- **Xiaomi Pad 7 (physical):** `PhaseTwoWalkOnDeviceTest` all passed (9 reported; the television walks skip).
- **Fire TV Stick 4K Plus (physical):** the whole class, OK (9); the handheld walks now skip a television.
  - The controller walk failed once on this build. Fire OS leaves a focused card's node without words, so the walk pressed past the card. It now finds the card by its box; three runs since, three passes.
- **Nothing Phone (2a):** not driven this time; another app was open on it (the owner's).
- **Windows:** the packaged app reopened on the owner's PC with their own library for review; not driven or captured by the agent.

## Phase 2: closing walks and closure (2026-11-20)

**Owner acceptance (2026-11-20): Phase 2 ACCEPTED AND CLOSED BY OWNER**, with the limitations and exceptions below accepted as recorded (DECISIONS.md, 2026-11-20, "Phase 2 accepted and closed").

On the `open-source-gpl` working tree after `b9ddec6`, with this pass's fixes (B-058, B-059, B-060). Each result is labelled with where it ran. Nothing here is a release build: the Linux tarball and the Android packages are local test builds (`0.9.0-beta.1` in their names because the version is unchanged).

**Skipped by the owner's decision (not failures).**
- **B-056**, the blank on closing the mini-player: a minor nitpick, accepted as a known minor issue and deferred to a later beta if evidence comes up.
- **A DualSense hot-plug check:** not applicable; the owner has no DualSense. This is not a product failure. B-050's fix stays walked with the GameSir G7 Pro.

### Linux desktop, in a VM (not physical Linux)

**Environment.**
- **VM:** `Reflux-QA-Ubuntu-26.04` on VirtualBox 7.2 (4 vCPU, 8 GB, VMSVGA with 3D). Run headless and driven only through VirtualBox's own virtual mouse and keyboard (its API), never the Windows desktop.
- **System:** Ubuntu 26.04 LTS, kernel 7.0.0-43, GNOME on Wayland; Reflux through XWayland; libmpv2 0.41.0-2ubuntu4; `en_US.UTF-8`.
- **Build:** `packageDesktopArchive` from a clean export of `b9ddec6` plus this pass's changes, built in WSL2. The final tarball's SHA-256 starts `d53a165c`.
- **Library:** the test film and five short ffmpeg titles in the VM, and a two-episode show; no Trakt key, so titles come from file names and there are no genres.

| Check | Result |
| --- | --- |
| Start-up | Starts; "Reflux … started", the libmpv locale note, hardware decoding probed (software only in the VM) |
| Home, Movies, Shows by mouse | Pass: hover lights the sidebar; no focus ring for the pointer |
| Search | Pass: idle "Start with" with right counts (Not seen yet 5, Under 2 hours 5, the 2010s 2, the 2020s 3; no Genres without metadata); the desk-keyboard button shown; typing; the All, Movies and Shows tabs; Shows' scoped empty state with "Search everything" |
| Search, then Backspace after a tab click | **Failed, then fixed: B-059.** Retested "night" → Movies tab → Backspace → "nigh", still on Search |
| Details by mouse | Pass: version line "H.264 · 1920 × 1080 · AAC Mono", no ring for the pointer |
| Player | **Failed, then fixed: B-058** (the video over the controls on Wayland; also in the released beta, same VM). Retested under Wayland: mpv "Initializing GPU context 'x11egl'"; full controls over the picture; the pointer brings them back; playback 0:18 → 0:24 in 5 s; pause, +10 s, Esc to the mini-player (the picture follows), close |
| Settings | Pass: Controller rumble on by default, Media Atmosphere off |
| Final tarball (with B-060) | Installed and started ("Reflux 0.9.0-beta.1 started"). Its keyboard walk of Settings' panels was not captured: the VM's GNOME session had locked after five idle minutes, and its sign-in is the owner's. B-060 is ui code shared with the walked build, covered by tests and walked on the Google TV emulator |
| Controller in the VM | **Not done.** VirtualBox's USB passthrough of the GameSir G7 Pro failed ("busy with a previous request"), and the controller stayed "Captured". The owner replugged it, and Windows has it again (XInput slot 0 connected). Linux controller input remains covered by unit tests (`LinuxGamepads`, `LinuxRumbleTest`) only |

**Limits of a VM:**
- software decoding and VMSVGA graphics;
- no real controller;
- not a physical Linux desktop. The owner's physical procedure still applies.

### Google TV (emulator, not a physical Google TV)

**Environment:** AVD `Reflux_TV` (Android 16, 1920 × 1080), headless, with the debug build and its test package. Its library is the earlier Blender films, on a source that is not connected, plus the walks' own test film. Driven by `input gamepad keyevent` (a gamepad's buttons, as the system delivers them) and `input keyevent` (a remote).

| Walk | Result |
| --- | --- |
| `onATelevisionAControllerWalksEveryPageWhateverTheLibrary` (new) | **Pass, twice.** It covers:<br>- Home focus;<br>- RB to Movies, onto the test film;<br>- A to Details with Play focused, nothing playing;<br>- B back with focus on the same film;<br>- RB to Shows (focus there), RB to Search;<br>- A on the field opens the Reflux keyboard, with the system keyboard kept away;<br>- typing "jun" + "x", X erases, the film found, B closes it with the query kept;<br>- X does nothing while browsing, Y twice, LT hides and shows the hints;<br>- Start opens Sources, the first B goes to the navigation and the second leaves (NAVIGATION.md, 2);<br>- the controller kept the walk throughout (directional modality: no hover/focus conflict) |
| `onATelevisionTheRemoteTypesInSearchWithTheRefluxKeyboard` | Pass (after a settle before OK: the walk sent OK as the field reported ready, and the earlier run timed out; by hand OK opens the keyboard every time) |
| `profilesHasNoButtonToItselfAndHomeHasOne`, `interfaceHapticsAreOffUntilTurnedOnInSettings` | Pass |
| `onATelevisionOkOnACardOpensItsPageOnPlayAndOkAgainPlays`, `onATelevisionAControllerBrowsesChoosesTypesAndHidesItsHints` | **Not run to the end here: a test precondition.** They look for the test film on Home, and this emulator's larger library keeps it off screen. Their checks are covered by the walk above; both passed on the physical Fire TV (2026-11-19) |
| A confirmation (Settings, Clear watch history) | **Failed, then fixed: B-060.** Before, A showed the confirmation but focus fell to the sidebar, and B left Settings. After, by hand with gamepad keys: A puts focus on Keep; B closes it, stays on Settings, and focus is back on the option |
| A side effect, put right | While a walk tried to automate the Settings step, a misread of focus made A turn **Online metadata & artwork** on, twice. Each time it was turned back off by hand and checked off; no PIN was set. The walk no longer touches Settings |
| Details where the source is not connected | Correct as designed: Resume is disabled, and focus goes to the watched button (B-053's fallback) |

### Nothing Phone (2a): physical, then the phone emulator

| Walk | Where | Result |
| --- | --- | --- |
| `searchKeepsItsNavigationAndBackTellsTheKeyboardFromLeaving` | **Physical Phone (2a)**, wired adb | **Pass.** Search's layout, the short placeholder, the system keyboard up and the bottom bar stepping aside, the empty state with its actions, Back putting the keyboard away (the bar back, still on Search), the next Back to Home, the query kept |
| Further touch checks on the physical phone | Physical | **Stopped:** the owner began using the phone (another app in front), so nothing more was sent to it. The walk's screenshots and a UI dump left on it were deleted |
| `searchOnAPhoneFindsNarrowsEditsAndKeepsItsQuery` (new) | **Phone emulator** (`Reflux_Phone`, 1080 × 2400 at 420 dpi) | **Pass, three times.** It covers:<br>- a suggestion asks its question ("unwatched", "Unwatched · 1 result");<br>- Shows: the scoped empty state; "Search everything"; the Movies tab;<br>- the field again with the system keyboard and the query in it, then editing it;<br>- Back puts the keyboard away;<br>- a result opens its page, and Back returns with the query and the scope kept;<br>- Clear search returns to the index, with the opened search under Recent;<br>- the bottom bar back with the keyboard away |
| `searchKeepsItsNavigationAndBackTellsTheKeyboardFromLeaving` | Phone emulator | Pass on 4 runs; failed on 2 cold starts, once on "the short placeholder" and once on "the bar steps aside". A screenshot of each showed the right screen; these are the emulator's timing. Recorded as an intermittent of the emulator |

**Emulator housekeeping:**
- the phone emulator had a leftover display override (1200 × 1920 at 240 dpi, a tablet layout), reset to its own;
- old milestone test packages (`dev.reflux.m3`, `dev.rsxdevelops.reflux.m10` and their tests) were removed from it to free storage.

### Automated

- **New tests:**
  - `SearchDesignTest.aClickedTabLeavesTheTypingInTheField` (B-059);
  - `EmbeddedOptionsTest` (3) and `ListOptionTest` (3) (B-058);
  - `PanelFocusTest` (3) (B-060);
  - device walks `onATelevisionAControllerWalksEveryPageWhateverTheLibrary` and `searchOnAPhoneFindsNarrowsEditsAndKeepsItsQuery`.
- **Changed:** `InteractionContractTest` (B-020's Escape sequence: the panel closes before the page is left).
- **Full check:** `./gradlew check --continue` on the final tree: **BUILD SUCCESSFUL**, Android lint included: 1,575 tests, 0 failures, 25 skipped (the window and physical-device tests run only on request, and the platform-specific ones). That is the earlier 1,565 plus the 10 new tests.

## Phase 3 (2026-11-20)

On `open-source-gpl`, from `9903e86` (Phase 2 closed). Commits `dcef9fb` (M1), `b2af2b7` (M2–M4) and the closing commit. The owner's Phase 3 work order set the scope (DECISIONS.md, 2026-11-20; history/phases/BETA-2-PHASES.md, Phase 3's state); each issue's record is in planning/BETA-BUG-TRACKER.md. Nothing was pushed, merged, deployed, versioned, tagged, released, or changed in a store console.

**Environments:**
- this Windows 11 machine, physical;
- Xiaomi Pad 7 (2410CRP4CI, Android 16), physical, USB;
- Fire TV Stick 4K Plus (AFTMA08C15, Fire OS, API 30), physical, network adb (woken for the run, put back to sleep);
- the Google TV emulator (`Reflux_TV`, Android 16) and the phone emulator (`Reflux_Phone`), headless, **emulators**. The Phone 2a was not connected, so the phone form factor is emulator only.
- a Jellyfin Server 12.4.0 in Docker (`tools/ci/jellyfin-test-server.sh`, trickplay extraction turned on through its API and its task run; a **container**, removed afterwards).

**Not used:** the Linux QA VM (no Phase 3 Linux walk; Linux is covered by the shared code's tests, the XKB reader's keysym test, and a Linux-only XKB test that skipped here), a physical Linux desktop, a Plex server with generated previews, any physical controller (the owner's), the owner's mouse at the owner's desk.

**Full check:** `./gradlew check --continue` on this machine, the closing tree: BUILD SUCCESSFUL in 13 min 26 s; **1,637 tests, 0 failures, 26 skipped** (live servers not named, OS-specific cases, the performance fixture without `-Preflux.perf`), Android lint and `verifySharedSources` clean. Per module: android 56, core 393, design 43, desktop app 72, desktop engine 52, library 263, Trakt 23, mpv 60, Jellyfin 74, local 6, Plex 25, SMB 10, WebDAV 20, ui 540. Hosted CI has not run: nothing is pushed.

**Earlier full check (2026-11-20, before the last fixes):** 1,630 tests, 0 failures, 25 skipped, lint clean. `verifySharedSources` failed on one `@Synchronized` in shared code, removed and re-verified.

**Automated, new in Phase 3:**

| Area | Tests | Result |
| --- | --- | --- |
| B-016, B-017 | `ProfilesUiTest` (15, of which 3 new or replaced) | Passed |
| B-042 | `CardLayoutTest.aRestingPointerNeverLiftsACardWhileAControllerHasFocus` | Failed with the old rule (two lifted cards), passed with the fix |
| B-045 | `AboutWindowTest`, real window (`-Preflux.windowTests=true`), the owner idle for some fifty minutes | Passed (1/1) |
| B-030 | `TrickplayTest` (6), `JellyfinTrickplayTest` (4), `PlexTrickplayTest` (2), `TrickplayUiTest` (hover) | Passed |
| B-030, live | `JellyfinTrickplayLiveTest` against the Docker server | Passed: 240 pictures every 10 s, 10 × 10 sheets of 320 × 180, 3 sheets read, each a JPEG of the grid's width |
| B-029 | `StickStepperTest` (6), `TrickplayUiTest` (stick, held stick, no previews, browsing) | Passed |
| F-P3 | `QuickViewUiTest` (6) | Passed |
| B-014 | `FeaturedTest` (6) | Passed |
| B-028 | `VisualSystemTest` (perceptibility threshold raised to 0.08) | Passed |
| F-P4 | `KeyboardLayoutsTest` (8), `KeyboardModeArbiterTest` (4), `AdaptiveKeyboardTest` (5), `KeyboardSessionTest` (2 updated to the approved grid) | Passed |
| F-P4, D3 | `SystemKeyboardLayoutTest` (Windows: this machine's layout read as US 0409; keysym conversion) | Passed; the Linux XKB case skipped (not Linux) |
| B-062 | `HeroFocusTest` (2), `FeaturedTest.whatCanBePlayedNowComesFirst` | The away case failed before the fix, passed after |

**Renders looked at** (`-Preflux.screenshots`):
- the desk keyboard, AZERTY, the grid after the morph, completions;
- Quick View on desktop, phone and TV;
- the timeline preview with and without previews, and during the stick;
- Atmosphere off and on.

**Device walks (`PhaseThreeWalkOnDeviceTest`, debug build `dev.rsxdevelops.reflux.debug` beside the store build):**

| Device | Kind | Result |
| --- | --- | --- |
| Google TV `Reflux_TV` | Emulator | 3/3: Quick View from the remote's Menu (a work whose source was away: Favorite, Download, Details, no Play), Back closing it; Switch profile and Log out in Settings, Log out asking who is watching; the remote typing on the grid (OK typed w; Left wrapped to the action column's Erase; OK erased) |
| Xiaomi Pad 7 | **Physical** | 3/3 (the grid walk is a television's and skipped itself): Quick View from a long touch, closed by the system's Back after the fix below; Log out to "Who's watching?" and back into Main. Screenshots looked at |
| Fire TV Stick 4K Plus | **Physical** | 3/3: Quick View from the remote's Menu; Log out and back; the remote typing on the grid, with a word-start completion ("Jungle Walk" for "w"). Screenshots looked at |
| `Reflux_Phone` | Emulator | 3/3 |

**Found by the walks, and fixed:**
- **The system's Back did not close Quick View on Android** (the Pad 7): `backRoute` now keeps Back in the app while it is open.
- **B-062** (the Google TV emulator): a television started Home in the navigation when the featured work could not play. The build before Phase 3 (`9903e86`, built in a temporary worktree) behaved the same: it is not a regression.

**Performance (B-041, B-022; Windows, physical):**
- packaged app, empty library, 7 runs: start to window median 2,227 ms (first 5,091 ms);
- working set at 6 s: 446 to 490 MB;
- close: 682 to 714 ms;
- breakdown: environment about 0.78 s, first composition to window about 0.70 s, window to first frame about 0.31 s.

Phase 1 on the same machine and method: 2.6 to 2.7 s, 492 to 500 MB, 0.71 to 0.82 s. Details and the gaps: the tracker's B-041 and B-022.

**Statistics (F-004):** no production code records or sends; no analytics SDK in any build file or manifest. On the Google TV emulator over 30 s, the app's only sockets were its configured Jellyfin source (`10.0.2.2:8096`) and its loopback-only SMB stream server.

**Video haptics feasibility (F-003; a scratch test, not committed):** libmpv's `lavfi` `astats` on a 150 Hz low-pass, read through `af-metadata`, saw each 60 Hz pulse at −5 dB against −62 dB of quiet, about 400 ms ahead of `time-pos` with `ao=null`. Not shipped (the tracker's F-003).

**The owner's packaged build** (not Phase 3's) left a crash log in the repository's root: a Skia access violation during a window resize after 11 h 43 min. Registered as B-061; the log is left in place, uncommitted.

## Phase 3: final validation and closure (2026-11-20)

The owner's "Phase 3 Final Validation & Closure" work order, and the steers given during it: the walk's findings, Linux display scaling, and Steam artwork. Same branch; nothing was pushed, merged, deployed, versioned, tagged, released, or changed in a store console or in Steam.

**Environments:**
- **This Windows 11 machine, physical,** with the owner's **physical controller and mouse.** The owner walked it, on a scratch library (`REFLUX_HOME` in the session's scratch folder: films and the Docker Jellyfin). Only Reflux's own window was captured (PrintWindow, every 2 s, 206 frames looked at as contact sheets). No keystroke or controller input was sent by the agent.
- **The Ubuntu QA VM `Reflux-QA-Ubuntu-26.04`** (VirtualBox; GNOME on Wayland; Reflux through XWayland): a **VM**.
  - It was running in the owner's session for the first walk. For the scaling runs it was started headless and signed in by itself.
  - Its display was changed for the scaling runs and put back.
- **The Google TV emulator `Reflux_TV`,** headless: an **emulator**.
- **Fire TV Stick 4K Plus,** physical: **asleep**. It was not woken for the reruns, because that would switch on the television it is attached to.
- **Xiaomi Pad 7,** physical, attached; not walked again: its debug build asks for the owner's PIN, which was not given for this session.
- **Not used:** the Nothing Phone (2a) (not connected), a physical Linux desktop, the cousin's Surface Laptop 5 with CachyOS, Steam.

**The owner's physical walk (Windows, controller and mouse):**

| Part | The owner's result | Then |
| --- | --- | --- |
| Card focus and a resting mouse (B-042) | Correct: only the controller's card lifts; the hovered card stays flat; hover returns when the mouse moves. "I like this behaviour." | B-042 **Verified (physical)**; unchanged |
| Quick View (X, right-click, Start → Sources) | Works; too translucent, the blur behind too strong | B-069: more solid, darker, a lighter blur |
| The player: right-stick scenes, A/B, held, 8 s idle, timeline pictures by mouse, the time alone without previews | All worked; the "A Go here · B Stay" line redundant | B-069: the line removed |
| The player's hints | Wanted LT to hide them, kept across restarts, with no LT hint left | B-067 |
| B into the mini-player, and back | No obvious way back to the full player | B-068: pressing the right stick |
| The adaptive keyboard | The morph looks good; it did not work properly by mouse and controller; moving the mouse changed only the hints; an empty place where Tab is; Backspace and Space in large words | B-064, B-065: causes reproduced with real mouse events and fixed |
| Profiles | Work; Delete buttons for every profile listed below them all; the navigation's initial oversized | B-066: a Manage menu per profile; icon-sized initial |
| Atmosphere, the featured hero | No finding reported | none |

**The owner's re-walk** of the fixes (B-064 to B-069) on the physical controller is the remaining acceptance step. The fixes are automated (below) and were not driven on the owner's desktop by the agent.

**Linux, the QA VM (a VM, not physical Linux):**
- **The XKB layout reader:** read correctly for us, de and fr. With a US layout on Linux the desk keyboard shows an extra ISO `<` key (minor; noted).
- **Start-up trace:** first frame at 2,280 ms. Quick View by the keyboard's Menu key, playback, the held-seek time bubble, and the player's controls above the video on Wayland all checked.
- **B-063, display scale (2560 × 1600 virtual display).** Captures are of Reflux's own X11 window (`ffmpeg x11grab -window_id`) and compared at native pixels without resampling:

| Desktop scale | Xft.dpi | Before (`REFLUX_SCALE=1`) | After |
| --- | --- | --- | --- |
| 100% | 96 | – | nothing applied; unchanged |
| 200% | 192 | 1360 × 823 window, all half size | ×2, 2183 × 1345, crisp at native pixels |
| 125% (GNOME fractional, XWayland native) | 192 (XWayland screen 4096 × 2560) | – | ×2, matching GNOME's 2× drawing that it then reduces |

- **Not reachable in this VM:**
  - 150% ("not supported by mode" at 2560 × 1600);
  - KDE's two legacy-application modes, the Surface's panel, and a second monitor.
  - These are the owner's physical checks (the tracker's B-063).
- **The Java probe:** Java 21.0.13 under Xvfb (WSL) showed the toolkit ignoring Xft.dpi and rounding a fractional `uiScale` down.

**Android:**

| Run | Where | Result |
| --- | --- | --- |
| `PhaseThreeWalkOnDeviceTest` | Google TV emulator | **OK (3 tests)**, on the build with all of today's changes; the grid shows the new ⌫ and spacebar keys (screenshot looked at) |
| `PhaseTwoWalkOnDeviceTest`, the TV controller walk | Fire TV (before), then the emulator | **Stale since F-P3:** it said "X does nothing", and X opens Quick View; it also expected the film on Home without moving down. Both were updated, and the walk then passed X → Quick View → B, Y, the bumpers and LT on the emulator. The emulator exited twice during the long walk, leaving the rest unrun; the first exit left a `test://walk2` source behind, cleared with `pm clear` on the **emulator's** debug build. To rerun on the Fire TV when it is on |
| Quick View and the system's Back on a phone and a tablet | – | Not rerun after B-069 (appearance only). The Pad 7 passed it in Phase 3's walks |

**Steam artwork (B-070):**
- `tools/brand/make-steam-artwork.py` renders `design/brand/steam/` from the brand's master geometry.
- The files were checked for size, mode, the logo's alpha, clear space and the cover at 150 px. They were looked at as one collection, with the logo laid over the hero.
- **Not applied in Steam** on Windows or Linux.

**Website:**
- `npm test` 33/33, `npm run build`, `npm run check-dist` (24 pages, 55 files, 0 problems), and `check-layout --screenshots` (24 pages × 7 sizes, 0 problems; the Releases page looked at, desktop and phone).
- `check-links`: 25 pages and 31 external links, 1 unreachable: `https://www.plex.tv/about/privacy-legal/`, refusing the checker (not a broken link on the site).

**Automated, new or changed in the final validation:**

| Area | Tests | Result |
| --- | --- | --- |
| B-064, B-065 | `KeyboardWalkTest` (3, real mouse events) | Before the fixes: 2 failed (the first key after the morph, and a mouse move that never morphed). After: passed. `AdaptiveKeyboardTest`, `PointerKeyboardTest` and `KeyboardTest` pass unchanged |
| B-066 | `ProfilesUiTest` (16, 1 new); `InteractionContractTest` through the new menu | Passed |
| B-067 | `HintsTest` (hidden means none, kept); `MiniPlayerTest` | Passed |
| B-068 | `MiniPlayerTest.theRightStickBringsTheMiniPlayerBackToTheFullPlayer_andLtHidesThePlayersHintsToo` | Passed |
| B-063 | `LinuxDisplayScaleTest` (5) | Passed |

**Full check** (`./gradlew check --continue`, this machine, the final tree):
- **Result:** BUILD SUCCESSFUL in 15 min 19 s; **1,647 tests, 0 failures, 26 skipped**; Android lint and `verifySharedSources` clean.
- **Per module:** android 56, core 393, design 43, desktop app 77, desktop engine 52, library 263, Trakt 23, mpv 60, Jellyfin 74, local 6, Plex 25, SMB 10, WebDAV 20, ui 545.
- **Not run:** the real-window tests (`-Preflux.windowTests`), because they would drive the owner's desktop.

**After the closure: the owner's manual test of the final build (2026-11-20).**
- **Found:** B-071, B-072 and B-073, each reproduced in a test before its fix (the tracker).
- **UI and core suites:** 941 tests, 2 failures, both in `SourcesUiTest`: `aServerThatIsAwayOrStopsAnsweringIsRemovedAtOnce` and `aServerThatIsAwayStaysListedAndInTheLibraryAndCanBeSignedInAgain` (timed out waiting).
  - Rerun alone: 11/11 passed.
  - They had passed in the full check before these changes, and these changes touch no source code: timing under load, recorded as an intermittent.
- **The packaged app** was rebuilt and opened on the walk library for the owner's check.

## Phase 3: owner acceptance and formal closure (2026-11-20)

- **The owner's manual test** of the rebuilt application, on Windows (physical): B-071, B-072 and B-073 confirmed fixed ("all fixed"). Recorded as Verified and closed.
- **The latest automated result, unchanged at the closure:**
  - `:ui:jvmTest :core:jvmTest`: 941 tests, 2 failures (`SourcesUiTest`, timing under the full run; 11/11 alone; B-074).
  - The earlier full `./gradlew check` (1,647 tests, 0 failures, 26 skipped) stands as history; it was before B-071 to B-073.
  - No full check was run again for the closure, which changed documentation only.
- **Phase 3 is closed:** history/phases/BETA-2-PHASES.md, "Phase 3 closure".

## Phase 4: consolidation, store assets, website, release rehearsal (2026-11-21)

On `open-source-gpl` from `3aa5964` (Phase 3's closure) to the commits listed in planning/PHASE-4.md, "Closure". Machine: the Windows 11 development PC (physical), Docker Desktop 29.9.3 (containers), the Android emulators named below. No physical phone, tablet, Fire TV or Linux desktop was attached or used. Nothing was pushed, published, uploaded or deployed. The work and its decisions: planning/PHASE-4.md; DECISIONS.md, 2026-11-21.

**Automated tests.**
- Start: `./gradlew check --continue` (Windows): **exit 0**. Executed: `:ui:jvmTest` 548 tests, 0 failures, 1 skipped (`SourcesUiTest` passed: B-074 not reproduced); `:desktop:app:test` 77 (15 skipped, window tests off); `:android:app:testDebugUnitTest` 56; Android lint; `verifySharedSources`. Up to date from 2026-11-20 with unchanged inputs: the other modules' 969 results, 0 failures.
- End: `./gradlew check --rerun-tasks --continue` (every task executed, 160 of 160; 15 min): **1,650 tests, 2 failures, 26 skipped**; `:ui:jvmTest` 548, 0 failures (`SourcesUiTest` passed again: B-074 not reproduced in a second full run); lint and `verifySharedSources` clean. The two failures, both explained and both passing on rerun: (1) `BuildIdentityTest.theBuildRecordsTheCommitItWasMadeFrom`, the build's "modified" flag against `git status`: documents were being edited in the working tree while the run was in progress; rerun on the clean tree after committing, 1/1. (2) `JellyfinExecutionLiveTest.everyDeliveryIsPlannedNegotiatedPlayedAndConfirmed` against the owner's live server: the remux played (first frame 573 ms, seek 414 ms) but the server's session evidence came back UNKNOWN, twice in a row; after two minutes' rest, 4/4 with the remux confirmed (REMUX, video and audio preserved). The same late session report is on record from M7 (history/verification/VERIFICATION-ALPHA-TO-BETA-1.md). No product code changed in Phase 4.
- Release tools: `python -I -m unittest discover -s tools/release -p "test_*.py"`: 49 tests, OK (2 new for B-075).
- Docs: `python -I tools/docs/check_links.py .`: 477 relative links, 0 broken (341 before the consolidation, 0 broken).
- Website: `npm audit` 0 vulnerabilities; `npm test` 34/34; `npm run build`; `check-dist` 26 pages, 0 problems; `check-links` 31 external links, 0 broken; `check-layout --screenshots` 26 pages × 7 sizes, 0 problems (screenshots looked at); `check-roadmap` 0 problems. Workflows: actionlint 1.7.7 (container), clean. Secrets: gitleaks v8.24.3 over the whole history (294 commits, as CI runs it): no leaks.

**Store screenshots (LaunchFrame 2.0.0; emulators, not physical devices).** `Reflux_Phone` (1080 × 2400, 420 dpi, Android 16), `Reflux_Tablet7` (new: 1920 × 1200, 320 dpi), `Reflux_Tablet` (2560 × 1600, 320 dpi), `Reflux_TV` (Google TV image, 1920 × 1080). Debug build without a Trakt Client ID (checked in its resources); a throwaway Jellyfin 10.10.7 container on localhost with seven Blender open films, removed afterwards with its volume. 24 captures kept, 8 rejected with reasons. `validate_assets.py --final`: exit 0. Details: store-assets/release-0.9.0-beta.2/.

**Release rehearsal (dry-run key; nothing published).** `release.py build` at `85d6d26` and, with the Linux tarball built in the CI container at `1aa0ce8`, `release.py build --add` from a worktree at `1aa0ce8`: 5 artifacts, `verify` and `stage-public` pass, the public set without APK or AAB; `publish-record` into a scratch folder and a website build against it (`check-dist` passes). APK identity checked with `aapt2` and `apksigner`.

**Windows package (physical PC, no launch).** The zip extracted to a scratch folder, 276 files, SHA-256 equal to SHA256SUMS, `Reflux.exe` and `libmpv-2.dll` NotSigned (by decision). Not launched: it would open on the owner's screen.

**Linux package, clean containers (not a desktop).** Under Xvfb, `de_DE.UTF-8`, 25 s each: Ubuntu 22.04, Ubuntu 24.04, Debian 12, Fedora 41 started without libmpv and with the distribution's libmpv; with it, the engine started, set the C numeric locale for libmpv keeping de_DE, and probed decoders (software only, no GPU in a container). Arch started both ways once Mesa and fonts were installed (its base image has neither; no locale data). Not read: the in-app libmpv hint.

**Not verified in Phase 4:** a physical device of any kind; hosted CI (branch not pushed); the Play and Amazon consoles; the Windows app launching; Steam; a physical Linux desktop.

## Beta 2 release attempt (2026-11-21)

On `open-source-gpl`, the promotion commit `ecce561` (`release: 0.9.0-beta.2`) and the documentation after it. The owner's release mission of 2026-11-21. Nothing was tagged, published, uploaded or deployed.

- **Fire TV captures (physical):** Fire TV Stick 4K Plus `AFTMA08C15`, Fire OS 8.1.8.5, API 30, 1920 × 1080 at 320 dpi, adb over the network; a side-by-side capture build (`dev.rsxdevelops.reflux.shot.debug`), removed afterwards; the stick's store and debug installs untouched. Six captures, five used; `validate_assets.py --final` exit 0.
- **Version identity:** `./gradlew releaseInfo`: `0.9.0-beta.2`, `versionCode` 90032, Windows 0.9.32, Linux 0.9.0~beta.2, `dev.rsxdevelops.reflux`.
- **Local full check** on `ecce561` (Windows, every task executed): **1,650 tests, 0 failures, 26 skipped**, exit 0.
- **Hosted CI run 44912262295** on `ecce561`: Website, Android, Check (windows-2025), release tools and doc links, workflow lint, secret scan **green**; **Check (ubuntu-24.04) red on both attempts**: `DownloadsUiTest.aRemoteReachesEveryDownloadControlAndFocusComesBack` ("Timed out waiting for focus on its removal"), 548 UI tests, 1 failed. The push's Release rehearsal (run 44912262382) green.
- **B-076 investigation (Linux CI image, Docker Desktop; containers, not a Linux desktop):** reproduced 2 of 2; passes on Windows 6 of 6; bisected `044c8f0` (last green hosted Linux CI) to `da08741`: first failing commit `77b994c`; eight instrumented runs (BETA-BUG-TRACKER.md, B-076). No fix kept.
- **Not run:** the release workflow, any store upload, the website deployment.

## B-076 fix (2026-11-22)

The owner's scoped fix before Beta 2's release (BETA-BUG-TRACKER.md, B-076). On `open-source-gpl` above `9604cc5`; still `0.9.0-beta.2`.

- **Reproduced first** in the Linux CI image (Docker Desktop, `reflux-linux-check`; a container, not a Linux desktop): `77b994c` fails `DownloadsUiTest.aRemoteReaches…`.
- **Intra-commit bisect** (one change reversed per run, the table in B-076): every isolatable `RefluxApp.kt` and `AppState.kt` change still fails; reversing `components/Focus.kt`'s change alone passes.
- **Regression test** `FocusEntryTest.anItemThatBecameTheOneToComeBackToDoesNotTakeFocusBackWhenItRecomposes`: fails without the fix **on Windows** (physical development machine), passes with it.
- **With the fix, Linux CI image:** `DownloadsUiTest` 3 of 3 runs pass (all 7 of its tests, the TV-layout remote walk of every download control included); `FocusEntryTest` passes.
- **With the fix, Windows:** `FocusEntryTest`, `DownloadsUiTest`, `HomeFocusTest`, `DetailsFocusTest`, `NavigationTest`, `TelevisionTest` pass; then the **full `./gradlew check --rerun-tasks`**: 160 tasks executed, **1,651 tests, 0 failures, 26 skipped**, exit 0.
- **Not run:** the window tests (`-Preflux.windowTests=true`, they open windows on the owner's screen) and devices; the fix changes no Android-specific code. Hosted CI: below, once pushed.
- **Hosted CI run 44941967966 on `dc70958`:** attempt 1: Windows check, Android, release tools, workflow lint, secret scan **green**; Check (ubuntu-24.04) **cancelled at the job's 60-minute limit** while `:ui:jvmTest` was still running (no hang: Linux's UI tests total 48.6 min over 67 classes on the hosted runner, from run 44934158483's reports; the job was already at 59 min on `ecce561`); Website **red** on one external link, `https://www.gnu.org/licenses/gpl-3.0.html` unreachable (gnu.org timed out from this machine too, 2026-11-21 21:00 UTC: an outage on its side). Attempt 2 (failed jobs, unchanged): Linux **"Build and test" passed** (`BUILD SUCCESSFUL in 51m 32s`, `DownloadsUiTest` included: B-076 fixed on hosted CI); its "Desktop window tests" step, reached for the first time since Phase 2, failed 3 stale tests (B-078); Website red again on gnu.org.
- **B-078 (stale window tests), Linux CI image:** reproduced (3 failed); fixed in the tests; `linux-check.sh window 2`: 2 of 2 runs pass, the three cases executed.
- **B-077** (`MpvEngineTest`, a test race seen once on hosted Linux in run 44934158483) recorded for Beta 3; not fixed.
- **Hosted CI run 44953745884 on `3986850`** (B-078's fix): Windows check, Android, release tools, workflow lint, secret scan **green**. Check (ubuntu-24.04), attempts 1 and 2: **"Build and test" green** both times, then **cancelled at the 60-minute limit** during "Desktop window tests" (B-079). Website: red on attempts 1 and 2, the same unreachable gnu.org link (gnu.org timing out intermittently from this machine as well, 2026-11-21 21:00–23:00 UTC).
- **State:** every test passes on hosted Windows, and every test of "Build and test" on hosted Linux; the window tests pass in the Linux CI image (2 of 2) but have not completed on the hosted runner within the job's limit. **Beta 2 not tagged**: waiting on the owner's decision on B-079.
- **B-079, Linux CI performance (the owner's decision, 2026-11-22: measure and shorten first):** the UI tests run in two JVMs on a 4-CPU runner (`ui/build.gradle.kts`); no test, step or limit changed. Linux CI image, 4 CPUs, 14 GB (a container, not the hosted runner): the complete job (`linux-check.sh`) **twice, every task executed: exit 0, 25.8 and 26.4 min**; 549 UI tests, 0 failures, the same skips as hosted CI. Windows development machine (physical), `./gradlew check --rerun-tasks`: **32m10s before, 6m31s after**, 160 tasks, **1,651 tests, 0 failures, 26 skipped**. One earlier complete run failed `JellyfinExecutionLiveTest`'s remux case against the live server (B-080: intermittent, failed and passed alone, unrelated). Hosted before/after: below.
- **Hosted CI run 44991408564 on `2f59c37`** (UI tests in two JVMs): Website, Android, release tools, workflow lint, secret scan green (gnu.org reachable again). Check (ubuntu-24.04): "Build and test" **28.5 min, 2 UI tests failed** (`VisualSystemTest`, an uncaught exception left by an earlier test in its JVM; `SourcesUiTest`, a timeout). Check (windows-2025): **23.1 min, 1 failed** (`HomeFocusTest`, focus). None had failed with one JVM on hosted runners. **The change is reverted** (B-079): faster but less reliable. The Linux job stays at about 54 to over 64 minutes; a limit of 75 minutes is recommended to the owner, not applied.
- **The owner's decision (2026-11-22):** the Linux Check job's limit raised to 75 minutes (Windows stays at 60); the UI tests stay serial; no test or step changed. Hosted CI on it: below.
- **Hosted CI run 44994139313 on `ff98a0d`** (the 75-minute limit, the tests serial): **every job green**: Check (ubuntu-24.04) 53.8 min ("Build and test" and the window tests), Check (windows-2025) 38.3 min, Android 5.9 min, Website 4.4 min, release tools and documentation links, workflow lint, secret scan. The push's Release rehearsal, run 44994139320: green. **Beta 2's gate step 8 holds.**

## Beta 2 release (2026-11-22)

- **Tag and build:** `v0.9.0-beta.2` (annotated) on `ff98a0d`; release workflow run 44999770048 green (Windows libmpv from source, Linux archive, Android/Windows/provenance, draft, independent verification).
- **Integrity, checked on the development machine (Windows, physical):** `release.py verify` 5 artifacts (full) and 3 (public), exit 0; `sha256sum -c SHA256SUMS` OK; `apksigner verify --print-certs`: signer SHA-256 `288c3810d9509c9732fccc333ff17813db4a73a228ca2f6773b148537c3b1370` = `REFLUX_UPLOAD_CERT_SHA256`; `aapt2 dump badging`: `dev.rsxdevelops.reflux`, 90032, `0.9.0-beta.2`; `gh attestation verify` exit 0 for the Windows zip, Linux tarball, libmpv source, AAB and APK (signer workflow `release.yml`, source ref `refs/tags/v0.9.0-beta.2`).
- **Published:** GitHub pre-release, immutable, 2026-11-22T08:28:35Z; the repository has no "latest" (stable) release.
- **Website build with the record (local):** `npm test` 34/34, `npm run build` exit 0, `check-dist` 0 problems over 26 pages, `check-roadmap` 0; the Download, install, verify and testing pages name the Beta 2 files.
- **Amazon:** Live App Test 1, `0.9.0-beta.2` submitted 2026-11-22 (console: "Test submitted").
- **Google Play:** not uploaded (11.9 MB AAB over the agent's 10 MB upload limit; the owner's step, BETA.md).
- **CI on the record commit `b7d5ba2`** (run 45003843008): attempt 1, Linux's window tests failed `FirstRunTest` once (B-081, intermittent; identical code passed on `ff98a0d`); attempt 2 (the failed job, unchanged): **every job green**.
- **`main` fast-forwarded** `ff98a0d..b7d5ba2` (no merge commit, no force); Vercel deployed it. **Live site checked 2026-11-22:** `/`, `/download`, `/testing`, `/docs/install/windows`, `/docs/install/verify`, `/roadmap` answer 200 and name `0.9.0-beta.2`, with no Beta 1 download left; the Download page's links to `reflux-0.9.0-beta.2-windows-x64.zip`, `reflux-0.9.0-beta.2-linux-x64.tar.gz` and `SHA256SUMS` resolve on the GitHub release.

## Beta 2 testing activation (2026-11-22)

- **Artifacts preserved** to `Documents/Reflux-release-archives/0.9.0-beta.2/` (Windows development machine): 18 files, 462 MB. On the archived copies: `release.py verify` 5 artifacts; every `SHA256SUMS` (release, linux, libmpv) OK; all five manifest hashes match (APK and AAB included); `unzip -t` (Windows zip, AAB), `tar -t` (Linux tarball), `xz -t` (libmpv source) clean; the intermediate Linux tarball byte-identical to the release one; AAB signer (`keytool -printcert -jarfile`) and APK signer (`apksigner`) both `28:8C:38:…:3B:13:70` = `REFLUX_UPLOAD_CERT_SHA256`; APK `dev.rsxdevelops.reflux` 90032 `0.9.0-beta.2`; `gh attestation verify` exit 0 for all five (`release.yml`, `refs/tags/v0.9.0-beta.2`); `ARCHIVE-SHA256SUMS` re-read: 18 OK.
- **Google Play (console, read):** Closed testing - Alpha: 90031 (0.9.0-beta.1) "Available to selected testers", 178 countries; testers: e-mail list "Reflux Beta", 12 users; feedback support@rsxdevelops.dev; web opt-in `https://play.google.com/apps/testing/dev.rsxdevelops.reflux`. **Upload attempted and refused by the agent's tool** (10.4 MB per call; the AAB is 11.9 MB); the empty draft discarded; the track unchanged afterwards. No production release.
- **Amazon (console, read):** Live App Test 1: `0.9.0-beta.2`, FOS, started 2026-11-22, **Test in-progress**; Beta 1 no longer listed; tester group "Reflux" (1 tester). App list: current (public) version "Not Submitted": nothing in production.
- **Announcement:** not sent (no authorised channel connected to the agent); the text is in BETA.md, "Beta 2 testing".

## CachyOS final audit (2026-11-24)

The owner's brief for a last session on the CachyOS laptop; full record in [planning/CACHYOS-FINAL-AUDIT-LEDGER.md](planning/CACHYOS-FINAL-AUDIT-LEDGER.md). **Environment:** physical laptop, CachyOS (kernel 7.3.5), Hyprland 0.57.2 on Wayland, Reflux through XWayland, Intel Arrow Lake iGPU + NVIDIA RTX 5060 Max-Q, libmpv from mpv 0.42.0 (VA-API hardware decoding), Temurin 21.0.13 (user-local), a GameSir G7 Pro controller (read only). No Android device, emulator or SDK; no Windows machine. Every run used copies of the owner's data or temporary libraries; Reflux's windows were kept on workspace 9.

| What | Result |
| --- | --- |
| `:core:jvmTest` | PASS, 393 tests |
| `:design:jvmTest` | PASS, 43 tests |
| `:library:jvmTest` | PASS, 263 tests (1 skipped: the opt-in live-server test) |
| `:ui:jvmTest` (full) | **FAIL: 563 tests, 2 failed, 1 skipped.** The two (`DetailActionsLayoutTest.withAFingerAllFourActionsStillSitOnOneLine`, `EpisodeVersionsUiTest.theVersionsAreBeneathTheEpisodeOnAPhone`) fail identically on an untouched worktree of `1d65dcf` on this machine: font-dependent, pre-existing (B-089). Every other test passed, the new ones included (`QuickViewModelTest` 3, `SidebarCollapseTest` 4, `KeyboardRendersTest` 6) |
| `:desktop:app:test` + `:desktop:engine:test`, real-window tests on (`-Preflux.windowTests=true`), helpers stubbed as CI does, physical Hyprland | **FAIL: 83 tests, 6 failed, 10 skipped (app); engine 52, 0 failed, 18 skipped.** The same six fail with the same messages on an untouched `1d65dcf` worktree run the same way: Hyprland's tiling overrides the window sizes and moves these tests ask for (`PlayerChromeResizeTest`, `MiniPlayerWindowTest`, `CapabilitiesWindowTest`, `BrowsingWindowTest.theMouseBrowsesTheRealWindow`, `EmbeddedPlaybackTest`, `OfflineWindowTest`). Pass on both: `FirstRunTest` (onboarding), `BrowsingWindowTest.aControllerBrowsesTheRealWindow` (updated for Start shrinking the sidebar), `LinuxDesktopEntryTest` (3, new), `ToolkitPreparationTest`, the rest |
| `:desktop:app:packageDesktopArchive` | PASS (a development archive, not published): `bin/install-desktop-entry` included and executable; `--add-opens java.desktop/sun.awt.X11` in `Reflux.cfg`; launched from its desktop entry through GLib, its window is class `reflux` |
| `python -I tools/docs/check_links.py .` | PASS, 480 links, 0 broken |
| Quick View timing (opt-in harnesses on a copy of the owner's library) | Model 143–161 → 6–7 ms (171 episodes); shell cold 813 → 202 ms, warm 242 → 81–84 ms |
| Hyprland walk, physical and nested (ten player states) | Two toplevels confirmed; blur over the picture with the owner's look; a `reflux` window rule gives a sharp, opaque player in all ten states; the workspace move leaves the controls behind (known limitation) |
| Android build, unit and instrumented tests | **NOT RUN** (no SDK or device); left to CI and the owner's devices |
| Windows | **NOT RUN** (the owner's separate session) |
| Walks with a person: controller presses, the dock pin | **NOT RUN** (BLOCKED: need the owner) |
| **Hosted CI on the branch** (`cachyos-final-audit` at `17aba72`, run 45345526419) | Attempt 1: Android (unit tests, lint, platform API, minified release) PASS; secret scan, workflow lint, release tools and documentation links PASS; Windows check FAIL (`SourcesUiTest.aServerThatIsAwayOrStopsAnsweringIsRemovedAtOnce`, the known B-074 intermittent); Linux check: `check` PASS, desktop window tests FAIL (`EmbeddedPlaybackTest` at "Home again", B-090, which fails the same way on an untouched `1d65dcf` here); Website FAIL (`https://www.gnu.org/licenses/gpl-3.0.html` unreachable from the runner; 200 from the laptop; `main` passed it on 2026-11-23; `website/` unchanged). **Attempt 2 (failed jobs re-run): Windows check PASS, Linux check PASS (window tests included); Website FAIL again on the same external link** |
