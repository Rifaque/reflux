# Beta 3 integration audit (2026-11-26)

**What this is.** The record of bringing `cachyos-final-audit`'s Linux and current-release work into `beta-3`, the diagnosis of the Windows CI failure on `cachyos-final-audit` (run 45785524870), and what was tested afterwards. Done on the Windows 11 development machine (physical). Nothing was pushed; `main`, every remote branch and every tag are untouched. Issues are in [BETA-BUG-TRACKER.md](BETA-BUG-TRACKER.md); run results are also in [VERIFICATION.md](../VERIFICATION.md), "Beta 3 integration".

## Summary

- **The Linux work was already on `beta-3`.** `beta-3` was created from `cachyos-final-audit` at `5de9fae` (the closure, 2026-11-25), so every Linux commit (the integrated renderer, B-084, B-092, B-099, B-102, and the rest) was already in its history. Since then each branch gained **one documentation-only commit**: `814518b` on `beta-3` (the Windows audit before Ubuntu) and `76b526a` on `cachyos-final-audit` (the Ubuntu validation, B-104). The production tree (everything outside `docs/` and `CLAUDE.md`) is **identical** on `406382b`, `5de9fae`, `76b526a` and `814518b`.
- **Integration:** one normal merge of `76b526a` into `beta-3` (`8eaf79c`), with three documentation files resolved by hand, keeping both sides. No commit was duplicated, rebased or cherry-picked.
- **Windows CI failure:** a **test race**, not caused by the Linux work and not Windows-specific in principle: `LocalFolderSourceTest.watchesForNewMediaIncludingNewFolders` waited a fixed 300 ms for a file watcher that registers on another thread. Reproduced locally under CPU load (8 of 12 failed), mechanism confirmed, fixed in the test only without loosening it (12 of 12 under the same load), with a negative control. Tracked as **[B-105](BETA-BUG-TRACKER.md#b-105)**. Hosted CI has **not** run on the fix (nothing pushed).
- **Beta 3's own work** (F-P1 with B-091, B-085, B-086) is untouched: the merge changed no code.

## 1. Starting state

Fetched from `origin` (`https://github.com/Rifaque/reflux.git`) at the start; the working tree was clean on `beta-3`, no stash.

| Ref | Local | Remote (`origin/`) | Note |
| --- | --- | --- | --- |
| `main` | `1d65dcf` | `1d65dcf` | Beta 2 as released (plus docs); as expected |
| `beta-3` | `814518b` | `814518b` | as expected |
| `cachyos-final-audit` | `5de9fae` | **`76b526a`** | the local branch was one commit behind its remote; left as it was, `origin/cachyos-final-audit` used as the source |

**Recovery references** (local branches, not pushed):

| Name | Commit |
| --- | --- |
| `recovery/beta-3-pre-integration-20261126` | `814518b` (beta-3 before the integration) |
| `recovery/cachyos-final-audit-76b526a-20261126` | `76b526a` (the source tip) |

To return `beta-3` to where it started: `git switch beta-3 && git reset --hard recovery/beta-3-pre-integration-20261126` (local only; the remote never moved).

## 2. Merge bases and graph

| Pair | Merge base |
| --- | --- |
| `beta-3` / `cachyos-final-audit` | `5de9fae` (as reported) |
| `main` / `beta-3` | `1d65dcf` (= `main`) |
| `main` / `cachyos-final-audit` | `1d65dcf` (= `main`) |

```
*   (this integration) 8eaf79c Merge cachyos-final-audit (76b526a) into beta-3
|\
| * 76b526a docs: Ubuntu 26.04.1 validation; B-104          (cachyos-final-audit)
* | 814518b docs: Windows repository audit before Ubuntu      (beta-3 before)
|/
* 5de9fae docs: CachyOS closure; beta-3 branch              (shared)
* 406382b … 17aba72  (11 shared commits since main)
* 1d65dcf (main, open-source-gpl)
```

`main` is an ancestor of both branches; `main` has nothing they lack. `git log --left-right --cherry-pick beta-3...origin/cachyos-final-audit` lists exactly `< 814518b` and `> 76b526a`; `git diff --stat beta-3...origin/cachyos-final-audit` is the four files of `76b526a`.

## 3. Commit classification (`main..beta-3` after the merge)

| Commit | What | Class |
| --- | --- | --- |
| `17aba72` | CachyOS final audit: Quick View, Linux window identity, sidebar shrink, compact keyboard | **Mixed:** Beta 3 features (F-P1, B-085, B-086), cross-platform fixes (B-082, B-083, B-087), Linux window identity; UI, desktop, core, library, design; tests; docs |
| `2d65eb5` | Hosted CI results; B-090 | Documentation |
| `0377cab` | Linux player as one window (B-084); sidebar name toggles | **Mixed:** Linux renderer/window (B-084) and a Beta 3 correction (F-P1); tests; docs |
| `d3d60a7` | The picture drawn through libmpv's render API (B-084, CACHY-011) | Linux renderer and playback (`desktop/app`, `playback/mpv`); tests |
| `1da6aaf` | Docked video clipped to the dock's rounded corners | Linux renderer |
| `fec8a4a` | CachyOS retired; handover | Documentation |
| `4be4c45` | Start closes Quick View again (B-091); B-093, B-097 | Beta 3 fix (B-091, a regression of F-P1); test fixes |
| `5b39a27` | Tracker anchors; link-check retries (B-098); B-074 | Test infrastructure (website scripts); documentation |
| `3197225` | gnu.org not asked from CI (B-098) | Test infrastructure (website link check) |
| `99fff18` | B-092 fixed, B-099, B-102 | Linux renderer lifecycle (B-099), window synchronisation (B-092), Linux desktop integration (B-102); tests |
| `406382b` | `LinuxDesktopEntryTest` raw-path comparison (Windows) | Test fix (B-102's test) |
| `5de9fae` | Closure; `beta-3` created | Documentation |
| `814518b` | Windows audit before Ubuntu; transition procedure | Documentation (beta-3 only before the merge) |
| `76b526a` | Ubuntu 26.04.1 validation; B-104 | Documentation (brought in by the merge) |
| `8eaf79c` | This merge | Documentation (conflict resolution) |
| `cbd28ca` | B-105 test fix | Test fix; tracker |

Every production change was already shared; on `beta-3` all of them belong (Beta 3 is meant to carry both the Linux player and Beta 3's features). **Deferred, not integrated, nothing to integrate:** B-027, B-038, B-040 (V1.1), F-003 (no release). **Excluded:** nothing (no source change was left out). **Not done here, still the owner's:** the "separation" in the ledger's [Closure](CACHYOS-FINAL-AUDIT-LEDGER.md#closure-2026-11-25-branches-the-beta-3-separation-and-the-hand-over-to-ubuntu), which concerns taking Beta 3's features *out of `cachyos-final-audit`* for a current-release Linux build; it does not apply to `beta-3`, and `cachyos-final-audit` was not changed.

## 4. File-by-file integration map

Only four files differed between the branches (all from `76b526a`), plus two files edited for the integration and the B-105 fix.

| File or change | Source | Beta 3 current state | Integration action | Reason | Required tests |
| --- | --- | --- | --- | --- | --- |
| `docs/UBUNTU-VALIDATION-REPORT.md` | `76b526a` (new) | absent | Bring forward source version (verbatim) | the evidence record; not rewritten | link check |
| `docs/README.md` | `76b526a`: one row | `814518b`: one row (UBUNTU-TRANSITION.md) at the same place | Merge manually: both rows, transition then report | both documents exist on beta-3 | link check |
| `docs/VERIFICATION.md` | `76b526a`: "Ubuntu validation (2026-11-26)" | `814518b`: "Windows repository audit before Ubuntu (2026-11-25)" at the end | Merge manually: both sections, in date order | both are results; neither replaces the other | link check |
| `docs/planning/BETA-BUG-TRACKER.md`, header | Ubuntu (2026-11-26) | Windows audit (2026-11-25) | Merge manually: one line, newest first, both histories | | anchors |
| tracker B-077, B-092, B-101, B-102, B-103 | Ubuntu bullets | unchanged since `5de9fae` | Bring forward (auto-merged) | | |
| tracker B-084, B-089, B-099 | Ubuntu bullets | `814518b` bullets at the same place | Merge manually: beta-3's 2026-11-25 bullet, then the Ubuntu one | both are evidence | |
| tracker B-104 entry | `76b526a` (new) | "next free ID: B-104" | Bring forward; the next free ID becomes B-105 (then B-106 with B-105) | ID agreed on both branches, no collision | anchors |
| tracker Beta 3 backlog table | B-104 row; an older B-084 row | B-084 row (newer status), F-P1, B-085, B-086 rows | Merge manually: B-104 added; beta-3's B-084 row kept ("Ubuntu validation next" → validated 2026-11-26); source's stale B-084 row dropped | beta-3's row was written after the source's | |
| `docs/UBUNTU-TRANSITION.md` | — | "nothing in it has been run on Ubuntu yet" | Dated status line added, procedure unchanged | the statement became stale | link check |
| `sources/local/.../LocalFolderSourceTest.kt` | — (B-105) | fixed 300 ms wait | Test fix (handshake) | the Windows CI failure (section 6) | the test under load; negative control; `check` |
| Production code, Gradle, CI workflows, fixtures, CHANGELOG | identical | identical | Keep Beta 3 version (no difference) | | full `check` |

## 5. Linux work carried forward and Beta 3 work preserved

**Linux (already on `beta-3`, confirmed by `git diff --name-only 406382b HEAD`: no production difference):** the integrated OpenGL renderer through libmpv's render API (`MpvGlRender.kt`, `LinuxGlVideo.kt`, `SkikoGl.kt`, `GlibcTrim.kt`; B-084/CACHY-011) and its selection over the shaped-window fallback (`DesktopPlatform.kt`); the render-context lifecycle and **B-099**'s context replacement (with `IntegratedContextReplacementTest`); the docked video clipped to the dock's corners; **B-092**'s window synchronisation; the fallback video window and mini-player; **B-102** (Reflux keeps its own desktop entry; `LinuxDesktopEntryTest`, with `406382b`'s Windows path fix); the window class and icon identity. Open Linux items, kept separate: **B-100** (fallback mini-player square corners), **B-101** (a process once lingered; not reproduced, 8 of 8 exited on Ubuntu), **B-103** (a second launch opens a second Reflux; the owner's decision), **B-104** (section 8).

**Beta 3 (untouched by the merge, which changed no code):** F-P1, the sidebar shrink on Start and the name toggle (`SidebarCollapseTest`, `DirectionalInputTest`, `ShellSurfaceTest`, `SourcesInlineAndBrandHomeTest`, `HomeFocusTest`); B-091, Start closes Quick View (`QuickViewUiTest.aControllersStartClosesQuickView`); B-085, the compact keyboard with LS (Shift) and LT (&123) (`KeyboardRendersTest`, `RefluxKeyboard.kt`, `TextEntry.kt`); B-086, the smaller PIN pad (`InteractionContractTest.aPinIsEnteredWithAControllerOverTheQuestionOfWhoIsWatching`). All are in the full check below.

**Ubuntu evidence (from the report, not repeated here):** Ubuntu 26.04.1, GNOME 50.1 on Wayland (XWayland), Intel Arrow Lake and RTX 5060 Laptop, libmpv 0.41.0, tested at `5de9fae` (code `406382b`): libmpv 0.41 compatible; 1,639 unit tests, 0 failed, 49 skipped; both renderers with picture and sound; H.264, HEVC 8/10-bit, 4K HEVC, VP9, AV1; mini-player, fullscreen, pause, seek, volume, mute; B-099; eight lifecycle rounds; B-102's packaged entry. **Not tested there:** software decoding, an audio device change while playing, keyring sign-in, HDR, a network share or Jellyfin server; the rounded mini-player corners and the dock icon were not looked at. **Known differences:** Xvfb with GNOME's session variables differs from CI (`FirstRunTest`, and one unattributed playback stuck in `LOADING` per suite); on the real session `FirstRunTest` timed out once and `PlayerChromeResizeTest` assumes an odd height mutter rounds. Since the production code is unchanged, those results hold for this `beta-3`; no Linux test was repeated on physical hardware for this integration.

## 6. The Windows CI failure (run 45785524870, job 139791205394)

**What failed.** Check (windows-2025) on `cachyos-final-audit` at `76b526a57b14411b1913cf93d1f06c000cc7da61`, Windows Server 2025, Temurin 21.0.13, Gradle 9.7.1, step "Build and test" (`./gradlew check :tools:cli:installDist -Preflux.android=false --stacktrace`), 4 min 25 s. The **first and only** failed task is `:sources:local:test`: `LocalFolderSourceTest > watchesForNewMediaIncludingNewFolders FAILED`, `kotlinx.coroutines.TimeoutCancellationException: Timed out waiting for 10000 ms` at `LocalFolderSourceTest.kt:94` (6 tests, 1 failed), from the job log and the `test-reports-windows-2025` artifact's HTML report. Because CI does not pass `--continue`, Gradle stopped there: other tasks (`:ui` tests among them) never ran in that job, and the desktop window-test step was skipped. All other jobs passed (Linux check, Android, website, documentation links, secret scan, workflow lint, release tools).

**Not caused by the integration or the Linux work.** `76b526a` changes four Markdown files; its production tree is identical to `406382b`'s, on which every job passed (run 45585080925, Windows included). `sources/local` was last changed on 2026-10-18 (`c8b5820`). `5de9fae` had no CI (`[skip ci]`). The test did not fail in any reachable earlier run: the failed runs on `cachyos-final-audit` (17aba72 to 99fff18) failed on B-102's test, B-092's window tests, B-077 and the website/link checks, all tracked; the older failed runs on `main`, `open-source-gpl` and `feature/engine-foundation` since 2026-11-13 show other tests or none.

**Cause.** `LocalFolderSource.changes()` registers its `WatchService` inside `callbackFlow`, i.e. only once the collector launched on `Dispatchers.IO` has started. The test waited `delay(300)` and then created the first file. If starting the collector takes longer than 300 ms, the file exists before the folder is watched; no event is reported, and the first `receive` times out. The failure happened while `:ui:compileKotlinJvm` and other tasks were running in parallel on the hosted runner (the log's timestamps put the failure between those tasks' outputs). Every later wait in the test follows a signal, i.e. a registered watcher, which explains why only line 94 fails.

**Reproduction (Windows 11, physical, 20 logical processors).**

| Run | Result |
| --- | --- |
| The test alone, 15 times (`--rerun`) | 15 of 15 PASS |
| The test with every core busy (40 spinning shells), 12 times | **8 of 12 FAIL**: the same exception at line 94 |
| Experiment (not committed): the wait lengthened to 5 s, same load, 12 times | 12 of 12 PASS: the registration race confirmed |

**Classification:** intermittent **test race** (timing; exposed by load), pre-existing in the test since it was written, unrelated to the Linux audit; seen on Windows, but nothing in it is specific to Windows. Not a toolchain problem, and not a production bug in the watcher (once registered, it reported every event). One product question is recorded unverified in B-105 (the initial refresh and the watcher start independently).

**Fix ([B-105](BETA-BUG-TRACKER.md#b-105), `cbd28ca`, test only).** The fixed wait is replaced by a handshake: rewrite the existing `Movies/existing.mkv` until the watcher reports (200 ms per try, at most 10 s), then settle and drain, as the test already does between steps. No assertion removed or loosened, no timeout raised.

| Check after the fix | Result |
| --- | --- |
| `:sources:local:test` (the class, 6 tests) | PASS |
| The test with every core busy, 12 times | **12 of 12 PASS** |
| Negative control: new subfolders no longer registered (product change, temporary, reverted) | the test **FAILS** (timeout at line 111, the file in the new folder) |

**Status:** resolved locally; **hosted CI has not run on the fix** (nothing pushed). It is not a known intermittent to be re-run; it is fixed, pending hosted confirmation.

## 7. Tests after integration

All on `beta-3` at `cbd28ca` (the merge plus B-105's fix). Production code is that of `406382b`.

| Run | Environment | Result |
| --- | --- | --- |
| `./gradlew check --continue` (Android included; window tests not requested) | Windows 11, physical | **BUILD SUCCESSFUL** in 14 min 55 s: tests, Android lint and the rest of `check`. Nine test tasks were up to date from 2026-11-24/25 (identical inputs), so they were re-run with `--rerun` (next row) |
| The nine test tasks forced to run again (`core`, `design`, `library`, `metadata:trakt`, `sources:jellyfin`, `plex`, `smb`, `webdav`, Android unit tests) | Windows 11, physical | **BUILD SUCCESSFUL** |
| Together, every test task run in this session | Windows 11, physical | **1,695 tests, 0 failed, 37 skipped**: `core` 393, `design` 43, `library` 263 (1 skipped), `metadata:trakt` 23, `sources:jellyfin` 74, `plex` 25, `smb` 10 (2), `webdav` 20, `local` 6, `playback:mpv` 62 (1), `desktop:engine` 52 (6), `desktop:app` 101 (25: the window, physical and Linux-only tests), `ui` 567 (2), Android unit 56 |
| Beta 3 features: `SidebarCollapseTest` 5, `QuickViewUiTest` 7 (B-091's Start case), `KeyboardRendersTest` 6, `InteractionContractTest` 7 (B-086's PIN case), `DirectionalInputTest` 10, `SourcesInlineAndBrandHomeTest` 8, `ShellSurfaceTest` 7, `HomeFocusTest` 6 | Windows 11, physical (in the check) | **56 of 56 PASS**, none skipped |
| B-105: the test under full CPU load; negative control | Windows 11, physical | 12 of 12 PASS; the mutant FAILS (section 6) |
| `:sources:local:test :playback:mpv:test :desktop:engine:test :desktop:app:test` on a clean clone of `cbd28ca`, `REFLUX_REQUIRE_ENGINE_TESTS=true`, `FONTCONFIG_FILE=tools/ci/fonts.conf` | **WSL2 Ubuntu 24.04.4 (a VM)**, JDK 21.0.13.1, libmpv2 0.37.0 | **BUILD SUCCESSFUL**: `sources:local` 6 (0 skipped: B-105's fix on inotify), `playback:mpv` 62 (1 skipped), `desktop:engine` 52 (18 skipped), `desktop:app` 101 (23 skipped); `LinuxDesktopEntryTest` 6 of 6 run and PASS (B-102), `LinuxPlaybackSetupTest` 2, `LinuxGlVideoTest` 5 (Skiko version pin) |
| Documentation links and anchors (`python -I tools/docs/check_links.py`) | Windows 11 | **PASS**: 616 relative links, 0 broken, after this report and the VERIFICATION.md record were written |

**Not run, and why.**

- **Window tests** (`-Preflux.windowTests=true`), Windows or Linux: they open and drive real windows, and the owner was at the machine during this session (CLAUDE.md: no desktop automation while it is in use). Linux window tests (B-092's `MiniPlayerWindowTest`, `PlayerChromeResizeTest`) and B-099's `IntegratedContextReplacementTest` need a display and OpenGL: on Windows they skip; they last passed on hosted CI (`406382b`, Xvfb fallback) and on the Ubuntu laptop (real GNOME session) on the same production code.
- **Physical Linux** (Ubuntu or CachyOS): not available to the agent; nothing was repeated there. The Ubuntu report's results stand because the production code is unchanged.
- **Android instrumented tests and device walks**: no Android code changed; unit tests and lint ran.
- **Website checks**: no website file changed.
- **Hosted CI**: nothing pushed (the owner approves pushes); no CI result exists for `beta-3` after `814518b` (which was `[skip ci]`).

## 8. Remaining Beta 3 work

- **B-104** (Open, cause established, not implemented). The proposed fix, choosing libmpv's `vaapi-device` as the render node of the GPU Skiko draws with, is supported by evidence: `mpv --hwdec=vaapi-copy --vaapi-device=/dev/dri/renderD129` initialised Intel's iHD at once on the same laptop, and `vainfo` agrees. **Ready to implement as designed, with questions to settle in the implementation:** mapping Skiko's OpenGL vendor string to a PCI vendor ID (Intel `0x8086`, AMD `0x1002`); a machine with two GPUs of the same vendor (fall back to today's request); the fallback renderer (already zero-copy `vaapi` on Intel; untouched); whether the start-up probe's CUDA initialisation (`MpvDecodeProbe`) also wakes NVIDIA (could not be measured). The tests can be written on Windows (node choice from a fake `/sys/class/drm` tree), but **acceptance needs the owner's Ubuntu laptop** (a perf walk showing `vaapi-copy` and NVIDIA idle). Not implemented in this integration, as instructed.
- **B-105**: hosted CI confirmation on the next owner-approved push.
- **B-077** (`MpvEngineTest` log-list iteration race; fix specified), **B-089** (UI test font), **B-090**, **B-100**, **B-101**, **B-103** (the owner's decision), and the rest of the tracker's [Beta 3 backlog](BETA-BUG-TRACKER.md#beta-3-backlog-from-phase-3s-closure-2026-11-20).
- **The owner's open decisions** (ledger, "Closure"): the integrated Linux player's acceptance and release placement, the desktop entry's placement (CACHY-001, B-102), B-103, and whether `cachyos-final-audit` is separated or retired now that `beta-3` holds everything.
- **Ubuntu not tested:** software decoding, audio device change, keyring sign-in, HDR, network shares, Jellyfin; the two GNOME test-environment findings.
- **Before Beta 3 is release-ready:** hosted CI green on `beta-3` (all jobs, including the Windows and Linux window tests); the systematic Beta 2 findings entered and fixed; B-104 fixed and walked on the Ubuntu laptop (if the owner places it in Beta 3); the owner's acceptance of the Linux player; the Beta 3 gate and version per docs/BETA.md and VERSIONING.md, only when the owner asks.

## 9. Final state

- **Branch:** `beta-3`, local, ahead of `origin/beta-3` by the merge, `76b526a` (through the merge), and three commits of this session; **not pushed**.
- **Commits created (local):** `8eaf79c` (merge of `76b526a`, documentation), `cbd28ca` (B-105 test fix and its tracker entry), and the commit adding this report and the VERIFICATION.md record. No AI co-author or attribution trailer.
- **Working tree:** clean after the final commit (only `.gitignore`d build output).
- **Untouched:** `main` (`1d65dcf`), `open-source-gpl`, the local `cachyos-final-audit` (`5de9fae`), every `origin/` branch (`origin/beta-3` still `814518b`, `origin/cachyos-final-audit` `76b526a`), every tag. No force-push, rebase, reset, stash or clean. No CI workflow changed.
- **Recovery:** `recovery/beta-3-pre-integration-20261126` (`814518b`) and `recovery/cachyos-final-audit-76b526a-20261126` (`76b526a`), local only.

## 10. Assessment

- **Integration: complete.** `beta-3` holds every commit of `cachyos-final-audit` (Linux renderer, B-092, B-099, B-102, the Ubuntu validation and B-104) and all of Beta 3's own work, with both branches' documentation kept.
- **Ready for systematic Beta 2 bug fixing:** yes, with the caveat that hosted CI has not yet run on this `beta-3`; the first owner-approved push should be watched on every job, the Windows check included.
- **The Windows CI failure:** **resolved locally**: diagnosed, reproduced, fixed in the test without weakening it, verified on Windows under load and on Linux (WSL2); hosted confirmation pending. It was a test race, not a regression from the Linux work.
- **Before Beta 3 is release-ready:** section 8.
