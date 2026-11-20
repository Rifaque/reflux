# Beta bug tracker: every finding, its status, and its evidence

**Purpose.** The permanent, detailed record of every bug, UX issue, product improvement, feature request, investigation, performance concern, and verification task found in Beta testing and during Beta 2 development: what was reported, what is known, what was done, what was tested and where, and what is left. A future developer or agent should be able to pick up any issue from its entry alone.

**Scope.** B-001 to B-042 and F-001 to F-005 (the owner's original findings), two registered sidebar proposals (F-P1, F-P2), and every issue found since (B-043 onward). Release gates, phase scope, phase order, and exit criteria are **not** here: they are in [BETA-2.md](BETA-2.md), which links back to this file. What each test run was, in full, is in [VERIFICATION.md](../VERIFICATION.md); decisions and their reasons are in [DECISIONS.md](../DECISIONS.md).

**Last updated:** 2026-11-20, Phase 3 implementation (branch `open-source-gpl`; Phase 3's commits from `dcef9fb`). Phase 3's summary and walks: VERIFICATION.md, "Phase 3".

## Contents

1. [Status definitions and how this tracker is kept](#status-definitions-and-how-this-tracker-is-kept)
2. [Executive status summary](#executive-status-summary)
3. [Phase overview](#phase-overview)
4. [Issues: bugs and product issues (B-001 to B-042)](#issues-bugs-and-product-issues-b-001-to-b-042)
5. [Issues: original feature findings (F-001 to F-005)](#issues-original-feature-findings-f-001-to-f-005)
6. [Unassigned issues and unresolved owner decisions](#unassigned-issues-and-unresolved-owner-decisions)
7. [Newly discovered issues (B-043 onward)](#newly-discovered-issues-b-043-onward)
8. [Beta 3 backlog](#beta-3-backlog-from-phase-3s-closure-2026-11-20)
9. [Regression tracking](#regression-tracking)
10. [Verification and evidence summary](#verification-and-evidence-summary)
11. [Change history](#change-history)

## Status definitions and how this tracker is kept

| Status | Meaning |
| --- | --- |
| **Not Started** | No investigation or implementation work has begun. |
| **Investigating** | Active investigation, reproduction, diagnosis, or root-cause work. |
| **In Progress** | Implementation or remediation underway. |
| **Fixed** | A change has been implemented; full verification may still be outstanding. |
| **Verified** | The acceptance criteria are satisfied and the verification the [matrix](BETA-2.md#verification-matrix-what-each-item-must-be-verified-on) requires exists. |
| **Blocked** | Work cannot proceed because of an identified dependency or blocker. |
| **Deferred** | Explicitly moved out of current scope by an authorised (owner) decision. |
| **Not Reproduced** | Investigation has failed to reproduce the issue. This does **not** mean it is resolved. |

**Verification is reported by kind, never merged.** Each entry keeps these apart: *automated tests passed* · *emulator verification* · *physical-device verification* · *desktop/OS-specific verification* (a VM or container is named as such) · *regression testing* · *owner acceptance*. An issue is never Verified because a patch compiles or a unit test passes when its acceptance needs a device, a platform, an integration, or a manual walk. Owner acceptance is pending for every issue until the owner accepts the phase that holds it.

**Rules for keeping it** (also in [CLAUDE.md](../../CLAUDE.md)):

- Read an issue's entry before working on it; update the entry **as the work happens** (investigation started, reproduction found, cause confirmed or rejected, fix landed, tests added, runs on each kind of environment, platform differences, regressions, blockers, acceptance), not at the end of a phase. Code changed without its tracker entry updated is incomplete work.
- IDs are stable and never reused or deleted; a fixed issue stays, with its history.
- A new issue takes the next free ID (B-043, B-044, …; a new feature F-006 onward, once the owner has numbered F-P1 and F-P2), keeps its description as found, says how it was found, and is allocated only by existing scope or an owner decision.
- A phase change or a deferral needs the owner's decision, recorded in DECISIONS.md and in the [change history](#change-history).
- Nothing is invented to fill a field: unknown is written as unknown or pending.

**Sources of each entry.** *Original description* is the owner's master tracker prompt of 2026-11-19 (authoritative for the findings and the phase plan). *Findings document* is the owner's "Reflux Beta Testing Findings" (build 0.9.0-beta.1, 18 November 2026), whose text was first registered in BETA-2.md and is kept here word for word where it adds detail (reproduction notes, screenshots, corrections). Where the two disagree, both are kept and the difference is listed under [owner decisions](#unassigned-issues-and-unresolved-owner-decisions).

## Executive status summary

**At Phase 3's formal closure (2026-11-20; the owner accepted and closed Phase 3).** Reconciled entry by entry against the code, the commits, VERIFICATION.md, and the owner's confirmations. All 82 entries are B-001 to B-074, F-001 to F-005, F-P1 to F-P4 (F-P1 and F-P2 share one entry, counted once):

| Status | Count | Entries |
| --- | --- | --- |
| Verified | 11 | B-001, B-033 (Phase 1); B-016, B-017, B-029, B-030, B-042, F-P3 (Phase 3, physical); B-071, B-072, B-073 (the owner's manual test, closed) |
| Fixed | 56 | Phases 1 and 2 as below; Phase 3: B-014, B-028, B-041, B-045, B-062, F-P4, B-063 to B-070. Each entry names what was and was not checked |
| Investigating | 5 | B-006, B-022 (carried); B-044, B-074 (test reliability, Beta 3); B-061 (crash, Beta 3 candidate) |
| In Progress | 1 | F-004: foundation and validation done, nothing can be sent; a provider is the owner's later choice |
| Deferred | 2 | F-003 (future version, no release assigned); B-056 (a later beta) |
| Not Started | 5 | B-027, B-038, B-040 (V1.1 candidates, researched); F-005, F-P1 (unassigned) |
| Not Reproduced | 2 | B-015, B-026 |

From here on, new findings go to the [Beta 3 backlog](#beta-3-backlog-from-phase-3s-closure-2026-11-20).

**Earlier summary (as of 2026-11-19, kept as history).** Counts were reconciled against the code, the commits, and VERIFICATION.md.

| | Count |
| --- | --- |
| Original bugs and product issues (B-001 to B-042) | **42** |
| Original feature findings (F-001 to F-005) | **5** |
| Registered proposals outside the original findings (F-P1, F-P2) | 2 |
| Newly discovered issues (B-043 to B-045) | 3 |
| Findings added by the owner's Phase 2 brief (B-046 to B-049; the brief's B-043 to B-046, renumbered) | 4 |
| Registered proposals for Phase 3 (F-P3 Media Quick View, F-P4 Adaptive Virtual Keyboard) | 2 |
| Found in Phase 2's verification and closing walks (B-050 to B-060; B-051 to B-053 and B-055 from the owner's follow-up, B-057 from the owner's steer) | 11 |

Status of the 47 original findings:

| Status | Count | Issues |
| --- | --- | --- |
| Not Started | 12 | B-014, B-016, B-017, B-027 to B-030, B-038, B-040, B-042; F-003, F-005 |
| Investigating | 3 | B-006, B-022, B-041 |
| In Progress | 1 | F-004 (Phase 1 foundation implemented; Phase 2 provider evaluation; Phase 3 validation to come) |
| Fixed | 27 | Phase 1: B-003, B-005, B-018, B-024, B-031. Phase 2: B-002, B-004, B-007 to B-013, B-019 to B-021, B-023, B-025, B-032, B-034 to B-037, B-039; F-001, F-002 (each lacks a required check: see its entry) |
| Verified | 2 | B-001, B-033 (owner acceptance still pending, as for every issue) |
| Blocked | 0 | |
| Deferred | 0 | |
| Not Reproduced | 2 | B-015, B-026 |

Newly discovered: B-043 Fixed; B-044 Investigating; B-045 Not Started. Added by the owner's Phase 2 brief: B-046, B-047, B-048, B-049 Fixed. Found in Phase 2's verification: B-050 (controller reconnect crash), B-054 (Reflux keyboard cursor) Fixed. The owner's Phase 2 follow-up: B-051, B-052, B-053 Fixed; B-055 (hints) Fixed; B-056 (blank on closing the mini-player) accepted as a known minor issue by the owner's decision (2026-11-20). The owner's Search steer: B-057 Fixed. Found in Phase 2's closing walks (2026-11-20): B-058 (Linux Wayland video over the controls; older than Phase 2), B-059 (Search tab and Backspace), B-060 (in-place panels and focus) Fixed. F-P2 (Y toggles the sidebar): Fixed, placed in Phase 2 by the owner on the Windows walk. F-P1, F-P3: Not Started. F-P4 (Adaptive Virtual Keyboard, Phase 3): Not Started, researched and planned (2026-11-20).

| Phase | Items | Fixed | Investigating / In Progress | Not Reproduced | Not Started | Verified | Owner acceptance |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **1** | 11 (9 bugs, F-004 foundation, B-041 baseline) | 5 | 3 (B-006, B-041, F-004) | 1 (B-026) | 0 | 2 | **Pending**: exit criteria met (BETA-2.md); the owner's own checks and acceptance remain |
| **2** | 23 issues in 19 steps, and B-046 to B-049 | 25 (incl. B-046 to B-049; B-002 aside) | 1 (B-002) | 1 (B-015) | 0 | 0 | **Pending**: implemented and tested; device, controller and owner checks named in each entry |
| **3** | 8 issues + 2 checkpoints (B-041 final, F-004 final) | 0 | 0 | 0 | 8 | 0 | Not started |
| Unassigned / pending | B-016, B-017, B-022, B-042, F-005 (and F-P1, F-P2) | 0 | 1 (B-022, measured under B-041) | 0 | 6 | 0 | Owner decisions needed |

**Phase 3 (2026-11-20): accepted and formally closed by the owner** (BETA-2.md, "Phase 3 closure"); the paragraph that follows is as it stood before the closure. Fixed in Phase 3: B-014, B-016, B-017, B-028, B-029, B-030, B-042, B-045, B-062 (new), F-P3, F-P4. Investigating: B-022 (measured), F-003 (feasibility shown, not shipped), B-061 (new, a crash in the owner's build). B-041 and F-004 have their second checkpoints recorded. B-027, B-038 and B-040 are V1.1 candidates, researched and not implemented. The counts below are as of 2026-11-19 and predate Phase 3.

**Phase 1 was accepted by the owner (2026-11-19). Phase 2 was accepted and closed by the owner (2026-11-20)** (PHASE-2-PLAN.md, "Closure"; VERIFICATION.md, "Phase 2: closing walks and closure"; DECISIONS.md, 2026-11-20). Its accepted limitations: B-056 deferred as a known minor issue; DualSense not applicable; Linux verified in a VM; the Linux controller covered by automated tests; the Phone 2a Search walk completed on the emulator. Phase 3 has not started. Beta 2 is not cut.

## Phase overview

Scope, order, dependencies, exit criteria, and the gate: [BETA-2.md](BETA-2.md#the-three-phases-the-owners-order). Status here.

**Phase 1: reliability, security, data integrity, foundational investigations**

| # | Issue | Status |
| --- | --- | --- |
| 1 | [B-018](#b-018) Profile deletion authorization | Fixed |
| 2 | [B-031](#b-031) Linux libmpv locale/init | Fixed (VM desktop session and containers; physical Linux pending) |
| 3 | [B-001](#b-001) Playback lifecycle/frozen frame | Verified (physical devices, server source included) |
| 4 | [B-026](#b-026) Windows process shutdown | Not Reproduced (including closing during playback) |
| 5 | [B-003](#b-003) Multiple Jellyfin endpoints | Fixed (live server pending) |
| 6 | [B-033](#b-033) Orphaned favourites | Verified (phone and tablet walks) |
| 7 | [B-024](#b-024) Resolution/HDR metadata | Fixed (Windows walk pending) |
| 8 | [B-005](#b-005) Scan status UI | Fixed (phone, tablet walked; TV on the emulator; Fire TV pending) |
| 9 | [B-006](#b-006) Metadata/search latency | Investigating (local search part fixed) |
| 10 | [F-004](#f-004) Analytics architecture, schema, privacy, consent | In Progress (foundation implemented; nothing transmitted) |
| 11 | [B-041](#b-041) Performance baseline | Investigating (baseline recorded on all six platforms; gaps named) |

**Phase 2: controller, focus, interaction, input, and haptics**

| # | Issue | Status |
| --- | --- | --- |
| 1 | [B-025](#b-025) Shared whole-app controller/focus architecture | Fixed |
| 2 | [B-008](#b-008) Focus recovery after Home | Fixed |
| 3 | [B-007](#b-007) See All and row navigation | Fixed |
| 4 | [B-004](#b-004) Source configuration input | Fixed (TV emulator walked; Fire TV pending) |
| 5 | [B-019](#b-019), [B-020](#b-020), [B-021](#b-021) Backspace and shortcut handling | Fixed |
| 6 | [B-023](#b-023) Reflux on-screen keyboard | Fixed (TV emulator walked) |
| 7 | [B-011](#b-011) Controller-only search | Fixed |
| 8 | [B-039](#b-039), [B-012](#b-012) Controller mapping review | Fixed (X reserved for Phase 3's Quick View) |
| 9 | [F-002](#f-002) Controller rumble | Fixed (physical controller pending) |
| 10 | [F-001](#f-001) Interface haptics | Fixed (felt on a device pending) |
| 11 | [B-036](#b-036) Mobile search/keyboard navigation | Fixed (Phone 2a walked; tablet emulator) |
| 12 | [B-034](#b-034) Mobile search placeholder | Fixed (Phone 2a walked) |
| 13 | [B-035](#b-035) Mobile search chips | Fixed (Phone 2a walked) |
| 14 | [B-037](#b-037) Search empty state | Fixed (Phone 2a and TV emulator) |
| 15 | [B-010](#b-010) Search suggestion focus/clipping | Fixed |
| 16 | [B-009](#b-009), [B-032](#b-032) Focus outlines and poster indicators | Fixed |
| 17 | [B-002](#b-002) Multi-monitor playback controls | Fixed (reproduced and walked on the owner's two monitors) |
| 18 | [B-015](#b-015) Poster alignment | Not Reproduced |
| 19 | [B-013](#b-013) Home hero restoration | Fixed |
| + | [B-046](#b-046) Redundant Profile button on Profiles | Fixed (Phone 2a walked) |
| + | [B-047](#b-047) Unexplained dot beside the Profile button | Fixed (marks made meaningful; owner to confirm which dot) |
| + | [B-048](#b-048) Pull-to-refresh | Fixed (Phone 2a walked; tablet emulator) |
| + | [B-049](#b-049) Authorised PIN removal | Fixed |
| + | Forgot PIN (owner's decision) | Implemented (Android system authentication; Keep/Reset elsewhere) |
| + | [B-050](#b-050) Controller reconnect crash (Windows) | Fixed (walked; a DualSense hot-plug not applicable: the owner has no DualSense) |
| + | [B-054](#b-054) Reflux keyboard cursor and held erase | Fixed (walked) |
| + | [F-P2](#f-p1-and-f-p2) Y toggles the sidebar (owner's decision) | Fixed (walked) |
| + | [B-051](#b-051) Mouse keyboard in Search (W, L) | Fixed (Windows walked; Linux VM pending) |
| + | [B-052](#b-052) Close the mini-player: RT or hold B | Fixed (Windows walked) |
| + | [B-053](#b-053) Details opens on Play | Fixed (Windows and Fire TV walked) |
| + | [B-055](#b-055) Controller hints: LT, shapes, motion | Fixed (Windows walked) |
| + | [B-056](#b-056) Blank on closing the mini-player | Deferred: accepted as a known minor issue (owner's decision; a later beta) |
| + | [B-057](#b-057) Search redesign | Fixed (automated; Phone 2a physical, phone and Google TV emulators, Linux VM) |
| + | [B-058](#b-058) Linux on Wayland: the video covered the player's controls | Fixed (Linux VM walked; physical Linux desktop pending, the owner's) |
| + | [B-059](#b-059) A clicked Search tab took the typing from the field | Fixed (Linux VM walked; automated) |
| + | [B-060](#b-060) Panels opened in place lost focus, and Back skipped them | Fixed (Google TV emulator walked by hand; automated) |
| — | [B-042](#b-042) Controller focus and mouse hover conflict | Moved to Phase 3 by the owner's work order (2026-11-20) and Fixed there |

**Phase 3: media experience, discovery, Trickplay, and final performance** (the owner's Phase 3 work order, 2026-11-20, which set this scope)

| # | Issue | Status |
| --- | --- | --- |
| 1 | [B-016](#b-016) Open the last-used profile | Verified (Pad 7 and Fire TV physical; emulators) |
| 2 | [B-017](#b-017) Switch profile and Log out | Verified (Pad 7 physical; the owner's Windows walk) |
| 3 | Bug sweep, with [B-042](#b-042) | B-042 **Verified** (the owner's physical walk); B-045 Fixed; B-062 found and Fixed; B-061 registered (Investigating, Beta 3 candidate); B-044 cause found (carried) |
| 4 | [B-030](#b-030) Trickplay previews | Verified (the owner's Windows walk; Jellyfin live; Plex against the format only) |
| 5 | [B-029](#b-029) Right-stick Trickplay navigation | Verified (the owner's physical controller on Windows) |
| 6 | [F-P3](#f-p3) Media Quick View | Verified (Pad 7, Fire TV, the owner's Windows walk) |
| 7 | [B-014](#b-014) Featured hero | Fixed (automated; rendered) |
| 8 | [B-028](#b-028) Media atmosphere | Fixed (automated with a perceptibility threshold; rendered) |
| 9 | [F-P4](#f-p4) Adaptive Virtual Keyboard | Fixed (automated; Fire TV physical and Google TV emulator grid walks; desktop renders) |
| 10 | [F-003](#f-003) Video-synchronised haptics | **Deferred** (owner, 2026-11-20): a future experimental feature, no release assigned; feasibility findings kept |
| 11 | [B-041](#b-041) Final performance comparison | Fixed: both checkpoints recorded, no regression where comparable; gaps named |
| 12 | [B-022](#b-022) Windows start-up | Investigating (measured with a breakdown; no optimisation justified yet) |
| 13 | [F-004](#f-004) Final analytics validation | Checkpoint 2 validated (nothing can be sent; network checked) |
| — | [B-027](#b-027), [B-038](#b-038), [B-040](#b-040) | **V1.1 candidates** (the work order): researched ([V1.1-LIBRARY-RESEARCH.md](V1.1-LIBRARY-RESEARCH.md)), not implemented |
| + | Final validation: [B-063](#b-063) Linux display scale | Fixed (Ubuntu VM only; physical Linux and the Surface not tested: accepted limitation) |
| + | [B-064](#b-064), [B-065](#b-065) The keyboard following the mouse; Tab, ⌫, spacebar | Fixed (in the accepted build) |
| + | [B-066](#b-066) Profile management | Fixed (in the accepted build) |
| + | [B-067](#b-067), [B-068](#b-068) LT hides every hint; RS back to the full player | Fixed (in the accepted build) |
| + | [B-069](#b-069) Quick View look; the Trickplay hint removed | Fixed (in the accepted build) |
| + | [B-070](#b-070) Steam artwork | Fixed (files only; never applied in Steam: accepted limitation) |
| + | [B-071](#b-071), [B-072](#b-072), [B-073](#b-073) The empty hint bar; Right from the sidebar into Home; row arrows | **Verified by the owner** (manual test of the rebuilt app) and closed |
| ✓ | **Phase 3** | **Accepted and formally closed by the owner, 2026-11-20** ([BETA-2.md, "Phase 3 closure"](BETA-2.md#phase-3-closure-2026-11-20-accepted-and-formally-closed-by-the-owner)) |

**Unassigned or pending:** [F-005](#f-005), [F-P1 and F-P2](#f-p1-and-f-p2) (F-P2 done in Phase 2). After the closure: the [Beta 3 backlog](#beta-3-backlog-from-phase-3s-closure-2026-11-20) (B-061, B-074, B-044, B-056, F-003). See [owner decisions](#unassigned-issues-and-unresolved-owner-decisions).

## Issues: bugs and product issues (B-001 to B-042)

Each entry: the header facts, the original report (the master tracker prompt's text, then the findings document's where it adds detail), reproduction, expected and actual behaviour, acceptance criteria, and the work record. Platforms: **P** Android phone, **T** Android tablet, **TV** Android TV / Google TV, **F** Fire TV, **W** Windows, **L** Linux. Components named as "likely" are a reading of the code's layout, not a confirmed location.

<a id="b-001"></a>
### B-001: Video frame stuck while audio continues

- **Category:** Bug (playback) · **Priority:** High, provisional · **Platforms:** P T TV F (Android)
- **Components:** `android:app` (`MainActivity.VideoLayer`, the `SurfaceView` host), `ui` (`AppState.play` / `finishPlayback` / `createPlayer`), the Media3 engine adapter
- **Phase:** 1, step 3 · **Status:** Verified (physical devices; Google TV on the emulator; owner acceptance pending) · **Last updated:** 2026-11-19
- **Related:** the M10 surface-continuity contract (one surface per playback across full player, dock, PiP)

**Original description.** Opening another movie or episode can leave the previous media's final frame on screen while the new media's audio and playback time continue. *Investigate:* player lifecycle, media transitions, surface attachment/detachment, playback state reset, asynchronous races, and rendering state.

**Findings document.** On two occasions, opening another movie or episode left the previous video's final frame visible while new audio and playback time continued. Investigate: player reuse/release lifecycle, decoder/surface attachment, asynchronous transitions and race conditions.

**Steps to reproduce.** Reported: play a film, then open another (twice, intermittently). Confirmed by test: play film A, leave it to the mini-player, then start film B; B's audio and clock run over A's last frame (`SurfaceHandoverOnDeviceTest`, the mini-player case, on the old code).

**Expected behaviour.** When new media starts, its video surface shows the new content; the previous media's frame never remains frozen as the new playback's picture.

**Actual behaviour (before the fix).** The new engine was never attached to the surface when it was created in the same frame as the old one's release; the surface kept the old buffer.

**Acceptance criteria.** The new item is never shown over the previous frame; transitions (close then play, natural end, stop, mini-player, rapid changes) are correct; the new item's first frame renders on hardware; M10 continuity still holds; different source types.

**Work record**

- **Investigation findings:** `VideoLayer` attached the engine only in `AndroidView.factory`, which runs once per composition node. `AppState.play` → `finishPlayback()` → `createPlayer()` ran back to back; with no frame composed between them the node survived and engine B was never attached. Timing-dependent, hence intermittent.
- **Root cause:** established (the new test fails on the old code in the mini-player case and passes with the fix).
- **Implementation:** the surface is keyed on the engine (`key(player)`: a new engine gets a new `SurfaceView`, with nothing of A's buffer or fixed size), and attachment is a `DisposableEffect(player, view)`, not the factory alone. One engine still keeps one surface across full player, dock and PiP. Commit `c1ca1d1`; DECISIONS.md, 2026-11-18.
- **Tests added or modified:** new `SurfaceHandoverOnDeviceTest` (red and blue generated films, colour read off the surface with `PixelCopy`; five cases: mini-player to another film, close then play and reopen, end then another, stop then another, five changes in a row). Closeout (2026-11-19): a sixth case, `fromAFolderToAServerAndBack`: a folder film, then a film streamed over HTTP from a WebDAV share on the device's loopback (`LoopbackFileShare`, byte ranges, the media framework's own HTTP stack), then the folder film again; the share sees only reading methods.
- **Automated verification:** the instrumented tests below; full `./gradlew check` on Windows for the JVM modules.
- **Emulator verification:** Google TV emulator (`Reflux_TV`): 4/4 + 1 skipped (no mini-player on TV). Closeout: 5 + 1 skipped, the server-source case included.
- **Physical/device verification:** old code on the Pad 7: 4 passed, mini-player case **failed** (the reproduction). Fixed code: Pad 7 5/5, Phone 2a 5/5, Fire TV Stick 4K Plus 4/4 + 1 skipped. Closeout run (2026-11-19, commit `556887b` plus the new tests): the whole class with the server case, Phone 2a 6/6 and Pad 7 6/6.
- **Regression testing:** unchanged and passing: `SurfaceContinuityOnDeviceTest` 2/2 (Pad 7, Phone 2a, Fire TV, emulator); `PlayerAccessibilityOnDeviceTest` 1/1 (Pad 7, Phone 2a); `ActivityRecreationOnDeviceTest` 1/1 (all four); `TelevisionOnDeviceTest` 2/2 (Fire TV, emulator); `PlaybackOnDeviceTest` 4 + 2 skipped (both handhelds).
- **Evidence:** VERIFICATION.md, "Phase 1", B-001. Debug build `dev.rsxdevelops.reflux.debug`, installed beside the store build.
- **Outstanding work:** owner acceptance. The server-source case ran on the two handhelds, not on the Fire TV or the TV emulator (the reported scenarios did). A Jellyfin or Plex server source specifically was not tried (none is reachable); the share exercises the same streamed path through the media framework. *(Status history: "Verified on physical device" in BETA-2.md; Fixed when this tracker was made, for want of the source-type case; Verified at Phase 1 closeout once that case passed.)*

<a id="b-002"></a>
### B-002: Playback controls disappear or misrender after moving windows between monitors

- **Category:** Bug (desktop UI/playback) · **Priority:** Medium · **Platforms:** W
- **Components (likely):** `desktop:app` window handling; `ui` (`PlayerControls`, `PlayerOverlay`, `MiniPlayer`, `VideoStage`); the libmpv video surface (`playback:mpv`)
- **Phase:** 2, step 17 · **Status:** Fixed (reproduced, fixed and walked on the owner's two monitors, Windows, physical) · **Last updated:** 2026-11-19
- **Related:** B-025 (shared layout and focus); B-041 (window sizes and densities)

**Original description.** Playback controls become mispositioned, disappear, or render incorrectly when the application window is moved between monitors. Mini-player expansion can reproduce or contribute to the issue. *Investigate:* monitor DPI, display scaling, Compose layout sizing, window changes, player surface dimensions, overlays, and mini-player transitions. *Expected:* playback controls and player UI remain correctly positioned and functional across monitor changes and display scaling configurations.

**Findings document.** Moving the window between monitors causes controls to disappear or render incorrectly. Expanding the mini-player can reproduce it. Investigate: per-monitor DPI, Compose sizing and surface recreation.

**Steps to reproduce.** Reported: play on Windows and move the window to another monitor (whether the monitors' scaling differs is not stated), or expand the mini-player. Not yet reproduced by Reflux.

**Expected behaviour.** Controls and player UI positioned and working across monitor changes and scaling.

**Actual behaviour.** Controls mispositioned, missing, or misrendered (reported).

**Acceptance criteria.** Controls correct after moving between monitors of equal and different scaling, while playing, paused, in the mini-player, and on expanding it; a Windows walk on two monitors.

**Work record**

- **Environment found:** the owner's Windows machine has two monitors at different scaling (125% and 150%: `GetDpiForWindow` 120 and 144).
- **Code:** the player's controls are a separate transparent `ComposeDialog` owned by the main window (`VideoOverlay`), placed over the video's `Canvas` by `setBounds` from the anchor's `locationOnScreen` on every move, resize, show and hide of the window or its ancestors.
- **Reproduction attempt (2026-11-19):** a script played a film in the packaged app and moved the window between the monitors with `SetWindowPos`.
  - **Stopped** after one run: it found the owner was using the machine, and its keystrokes to start playback went to the foreground window instead.
  - Its window measurements stand: the main window went 1280 × 800 → 1536 × 960 on the 150% monitor → 1067 × 667 back on the 125% one. The window does not come back to its size, so per-monitor DPI handling is in play.
  - The controls window was not visible in any reading, because playback had not started.
  - The screenshots were deleted (they showed the owner's screens).
- **Hypotheses, not yet confirmed:**
  - the owned dialog keeps the old monitor's `GraphicsConfiguration` and scale after the owner changes screens, so its user-space bounds map to the wrong device pixels;
  - its Compose layer keeps the old density.
- **Reproduction (2026-11-19, the owner's go-ahead, the machine free).** A new driver replaced the first script:
  - it sends input only after checking that the foreground window belongs to the Reflux test process, raising it without sending keys to anything else, and aborts otherwise (it refused once, correctly);
  - it moves the pointer only where Reflux's own window is under it;
  - it captures only Reflux's own windows (PrintWindow), never the screen;
  - the test library holds only test films.
  - **Found:** on the 150% monitor the controls window was 1010 × 1044 device pixels over a 1514 × 1566 picture: its size had not been scaled. The title, transport and close button fell outside it; only the minimise chevron showed. This held after a fresh start of playback on that monitor and after a real title-bar drag.
- **Cause (narrowed, not fully root-caused inside the JDK).**
  - Reflux's own record (and the toolkit's) of both windows was right (traced in place); only the native size was wrong.
  - A plain Swing owned dialog on JDK 21 was sized right; Compose's own transparent `ComposeDialog` reproduced the fault outside Reflux, and an opaque one did not.
  - Compose makes a transparent window on Windows with Direct3D a toolkit-translucent window, and with Java2D's own Direct3D pipeline off (`-Dsun.java2d.d3d=false`) it was sized right.
  - Rejected on the way: reshaping the dialog after a screen change (cured plain Swing, not Compose), and snapping its native bounds to the surface (undone again about 150 ms later).
- **Fix.** `prepareToolkit()` turns Java2D's Direct3D pipeline off on Windows before the toolkit starts (an explicit `-Dsun.java2d.d3d` is kept). `HiddenCursor` is made lazily, because making it started the toolkit before `main`. Compose draws through Skia's own Direct3D, so nothing Reflux shows is drawn differently.
- **Tests:** `ToolkitPreparationTest` (3).
- **Walk (Windows, physical, two monitors, 125% and 150%):**
  - playing, then title-bar drags each way and back: controls window = picture each time (1515 × 906 over 1514 × 904 at 150%; 1264 × 755 over 1262 × 753 at 125%);
  - programmatic moves: 1515 × 1566 over 1514 × 1566;
  - paused; the mini-player on each monitor (its controls window over the docked picture); expanding it on the 150% monitor; maximised there (2562 × 1494 over 2560 × 1494);
  - captures looked at: the full controls on each.
  - **Control run:** the same build with `-Dsun.java2d.d3d=true` brought the fault back on a real drag (1010 × 604 over 1514 × 904).
- **Noted, not a bug:** the main window's height grew on the *programmatic* moves (an artefact of `SetWindowPos` with a fixed size); on real drags it kept its size.
- **Outstanding:** owner acceptance.
<a id="b-003"></a>
### B-003: Multiple Jellyfin addresses overwrite each other

- **Category:** Bug (source connectivity/data configuration) · **Priority:** Medium · **Platforms:** reported P T; the code path is shared by every platform
- **Components:** `sources:jellyfin` (config `alt=`, `JellyfinApi`); `core` (`EndpointSelector`); `desktop:engine` / `library` (`SourceConnections`, `SourceConnector.combine`); `ui` (Sources address list)
- **Phase:** 1, step 5 · **Status:** Fixed · **Last updated:** 2026-11-19
- **Related:** B-027 (one server's several *libraries*; a different problem from one server's several *addresses*)

**Original description.** Adding a Tailscale address to a Jellyfin server can overwrite the previously configured LAN address. A logical Jellyfin server may have more than one reachable endpoint. *Expected:* support multiple connection endpoints for the same logical server; prefer the LAN endpoint when appropriate, fall back to other available endpoints, and avoid treating each address as a separate server unnecessarily. *Investigate:* server identity, endpoint storage, endpoint selection, availability checks, connection fallback, and source configuration.

**Findings document.** Adding a Tailscale endpoint overwrites the existing LAN endpoint. Expected: one logical server with multiple endpoints, LAN preferred and Tailscale fallback. Investigate: server identity, endpoint storage, deduplication and failover.

**Steps to reproduce.** Add a Jellyfin server at its LAN address; add the same server again at its Tailscale address in the same profile. Before the fix the second address replaced the first and signed the LAN session out.

**Expected behaviour.** One source with every address; LAN preferred; fallback when an address does not answer; back to the LAN when it returns.

**Actual behaviour (before the fix).** Same server ID + user ID ⇒ same `SourceId` ⇒ the `existing` branch replaced the config (one `serverUrl`), re-pointed `endpoint_id`, and signed the LAN session out.

**Acceptance criteria.** One source, several addresses; LAN preferred; fallback; recovery; survives restart; one address removable (never the last); other profiles unaffected; an old single-address connection reads unchanged.

**Work record**

- **Investigation findings:** identity and library survived; only the address was lost. Version locators carry no host, so the base URL can change; Jellyfin tokens are not bound to the Host header.
- **Root cause:** established in code (above).
- **Implementation:** the ordered addresses live in the sealed connection (Jellyfin `alt=`; no SQL migration; an old connection reads as one address). `EndpointSelector` (core, pure): home network first, then overlay networks (Tailscale), then the rest, in the order added; sticky to the address that last answered; fails over only when an address does not answer at all (never on an HTTP status); re-tries preferred addresses at most once a minute. A second sign-in to an account already in the profile is combined (`SourceConnector.combine`): the new token kept, the old session ended, every address kept. Commit `c1ca1d1`; DECISIONS.md, 2026-11-18.
- **Tests added or modified:** `EndpointSelectorTest` (4, new); `JellyfinEndpointsTest` (7, new); `JellyfinEndpointsLibraryTest` (4, new).
- **Automated verification:** `:core:jvmTest --tests EndpointSelectorTest` 4/4; `:sources:jellyfin:jvmTest` all passed (63 existing + 7 new); `:desktop:engine:test --tests JellyfinEndpointsLibraryTest` 4/4 (real library and connections, fake servers). Windows.
- **Physical/device verification:** none. No live Jellyfin server at two addresses was available.
- **Regression testing:** full `./gradlew check` (Windows) passed with these tests included.
- **Evidence:** VERIFICATION.md, "Phase 1", B-003 (with the owner's procedure).
- **Outstanding work:** a live server at a real LAN and Tailscale address (owner procedure in VERIFICATION.md); a device walk of Sources' address list; owner acceptance.

<a id="b-004"></a>
### B-004: Source configuration/controller input is unreliable

- **Category:** Bug (controller/input UX) · **Priority:** Medium · **Platforms:** TV (controller)
- **Components (likely):** `ui` (`SourcesScreen`, `TextInput`, `Focus`); `android:app` (input method)
- **Phase:** 2, step 4 · **Status:** Fixed (automated; TV emulator walk below) · **Last updated:** 2026-11-19
- **Related:** B-023, B-025, B-011, B-019 to B-021

**Original description.** Address entry and source configuration using a controller is unreliable. The address field can lose its keyboard, and pressing A may not reliably reopen it. Navigation behaviour is inconsistent. *Expected:* all source configuration actions must be accessible and predictable with a controller, including focusing fields, opening text input, entering/editing an address, and completing configuration. *Investigate:* focus management, text input lifecycle, controller action dispatch, and keyboard interactions.

**Findings document.** Address field opens the keyboard and then loses it. Pressing A/select again does not reliably reopen it. Source setup navigation is inconsistent. Investigate: focus management, keyboard activation and input-method lifecycle.

**Steps to reproduce.** Reported: on Android TV with a controller, add a source, focus the address field, press A (the keyboard opens, then closes); press A again (it does not reliably reopen). Not yet reproduced by Reflux.

**Expected behaviour.** Every source-setup action reachable and predictable with a controller; the keyboard opens on A and reopens reliably; text kept.

**Actual behaviour.** Keyboard lost; A does not reliably reopen it; inconsistent navigation (reported).

**Acceptance criteria.** Source setup completed end to end with a controller only on Android TV (emulator), Fire TV (physical), and Windows with a controller; text never lost on dismissing and reopening.

**Work record**

- **Cause:**
  - On a television, `keyboardByRemote` hid the system keyboard while focus arrived and asked for it on OK's release. A keyboard that dismissed itself (its own Next, Back, Fire TV's quirks) left OK depending on the field still having focus and the IME answering `show()`.
  - On a desktop or a tablet with a controller, confirm on a field did nothing: a field was not a focused control.
- **Fix:**
  - With a controller or a remote, every field uses the Reflux keyboard (B-023) on every form factor, television included, and the system keyboard is kept away under directional input.
  - OK (on release) or confirm opens it; B or Back closes it with the text kept; Done goes on to the next field, or connects from the last (`SourcesScreen`'s `onDone`).
- **Tests:** `KeyboardTest.aRemoteOpensTheRefluxKeyboardWhenOkIsLetGoAndNeverTheSystems`; `InteractionContractTest.aRemoteOpensTheRefluxKeyboardOnATelevisionAndBackPutsItAway`.
- **Outstanding:** Fire TV (physical; not connected); owner acceptance.
<a id="b-005"></a>
### B-005: Scan status toast overlaps interface

- **Category:** Bug (UX) · **Priority:** Low to Medium · **Platforms:** reported P T; layouts also checked at desktop and TV sizes
- **Components:** `ui` (`CompactNavigation`, the activity indicator, `PageBar`)
- **Phase:** 1, step 8 · **Status:** Fixed · **Last updated:** 2026-11-19
- **Related:** B-032 (another indicator collision)

**Original description.** The "Finding titles and details" scan status/toast overlaps existing interface elements such as the search field, posters, and headers. *Expected:* scan status should be presented without obstructing important content or controls, with an appropriate placement and adaptive treatment across screen sizes.

**Findings document.** "Finding titles and details" overlaps search, poster rows and headers. Evidence: multiple screenshots supplied. Investigate: placement, duration, responsive behaviour and component lifecycle.

**Steps to reproduce.** On a phone, start a library scan: the indicator's words grow leftwards over the section heading, and on Search over the field.

**Expected behaviour.** Never covers search, headers, navigation, rows, or player controls, on any form factor.

**Actual behaviour (before the fix).** On phones (`BOTTOM_BAR`) the floating row's text grew over the heading and Search's field; on TV (`TV_RAIL`) within the page bar.

**Acceptance criteria.** No overlap at phone, tablet, desktop, small-window and TV sizes, with Search open, long source names, and a docked player; device walks on phone, tablet and TV.

**Work record**

- **Investigation findings:** desktop and tablet pages reserve a 64 dp bar and did not collide; phones and TVs did.
- **Root cause:** established in code (above).
- **Implementation:** on phones and TVs the sign is a dot in a fixed place (its words for screen readers, and in full on Sources); on Search's row it steps aside as the profile entry does; beside a rail or sidebar the words stay in their own bar, cut short. Commit `c1ca1d1`; DECISIONS.md, 2026-11-18.
- **Tests added or modified:** new `ActivityIndicatorLayoutTest` (the sign's bounds against every heading and text field); at closeout, on-device `PhaseOneWalkOnDeviceTest.theLibrarysSignNeverCoversALabelOrAField`.
- **Automated verification:** 4/4 at phone (412×915: Home, Movies with a very long source name, Search), tablet (1200×800), desktop (1440×900), small window (960×640) and television sizes. Its first run found a second overlap (the dot over the phone's Search field: x 372–396 against the field's 68–388), since fixed.
- **Physical/device verification (Phase 1 closeout, 2026-11-19):** Home, Movies and Search opened in turn while 400 new files were scanned for each; whenever the sign was on screen, its bounds were checked against every other visible label and text field through the accessibility tree. **Phone 2a:** Home and Movies, sign seen and clear; Search, no sign (on a phone's Search it steps aside, by design). **Pad 7:** all three seen and clear. Screenshots looked at (phone Movies: the dot beside the profile button, the heading clear; tablet Search: "Scanning Walk library" in its own bar above the field).
- **Emulator verification:** Google TV emulator: all three pages, sign seen and clear (its screenshot was taken just after the sign went, so the bounds check is the evidence there).
- **Regression testing:** full `./gradlew check` passed with it.
- **Evidence:** VERIFICATION.md, "Phase 1", B-005, and "Phase 1 closeout walks".
- **Outstanding work:** a Fire TV walk (the matrix asks for it; the Fire TV was not connected); owner acceptance.

<a id="b-006"></a>
### B-006: Metadata and search latency

- **Category:** Performance (investigation) · **Priority:** Low to Medium · **Platforms:** reported P and W
- **Components:** `library` (`Library.search`, `smartSearch`, listings, availability); `metadata:trakt` (rate limit); `ui` (artwork loading)
- **Phase:** 1, step 9 · **Status:** Investigating (the local search part: Fixed) · **Last updated:** 2026-11-19
- **Related:** B-041 (where the measurements continue); B-022

**Original description.** Metadata loading and search feel slower than expected. The precise provider, operation, and source of latency are not yet established. *Investigate:* network requests, provider response time, caching, artwork loading, database queries, main-thread work, repeated requests, rendering, and loading-state behaviour. *Expected:* identify measured bottlenecks before optimizing; avoid speculative changes that do not address the actual cause.

**Findings document.** Search and movie/show metadata loading feel slow. The exact provider referred to in the original report needs clarification. Investigate: network requests, cache behaviour, artwork loading, database access and main-thread work.

**Steps to reproduce.** None given beyond "feels slow"; measured on a fixture instead (below).

**Expected behaviour.** Bottlenecks measured, then removed where justified; not only a change to perceived loading.

**Actual behaviour.** Local search rebuilt its documents on every keystroke and every library revision (measured; fixed). Metadata for a new library is bounded by Trakt's 500 requests per 5 minutes (a hard ceiling; not measured end to end).

**Acceptance criteria.** Each slow operation in the report identified and measured; justified fixes with before/after numbers; the reported provider identified.

**Work record**

- **Investigation findings:** before, each keystroke and each library revision resolved metadata for every item (about 103 ms: 44 freshly compiled 500-parameter lookups, even with no metadata) and read every row (about 47 ms), then read availability for the whole library to show 50 hits.
- **Root cause:** established for local search and listings; not established for metadata, artwork, or the reported provider.
- **Implementation:** search keeps its documents until the library changes (invalidated by the database's change notifications on every table it is built from); a listing reads availability only for what it shows. Commit `c1ca1d1`; DECISIONS.md, 2026-11-18.
- **Tests added or modified:** `SearchPerformanceTest` (fixture; runs with `-Preflux.perf=true`); `SearchFreshnessTest` (3, new).
- **Automated verification:** fixture of 1,000 films and 200 shows × 50 episodes (11,000 playables), JVM, warm, 25 runs each: title prefix median 236 → 13 ms; two words 226 → 16; a miss 157 → 1; an episode's show 225 → 27; smart structured 170 → 99; smart falling back to title 232 → 17. `SearchFreshnessTest` 3/3.
- **Physical/device verification:** none (the fixture was not run on Android).
- **Regression testing:** full `./gradlew check` passed.
- **Evidence:** VERIFICATION.md, "Phase 1", B-006 (table).
- **Outstanding work:** identify the reported provider with the owner and tester; measure Trakt lookups, artwork fetch and decode, episode enrichment, offline behaviour, and the fixture on Android (with B-041).

<a id="b-007"></a>
### B-007: Home D-pad skips or mishandles "See All"

- **Category:** Bug (controller navigation) · **Priority:** Medium · **Platforms:** W (controller); TV relevance to assess
- **Components (likely):** `ui` (`HomeScreen`, `Focus`)
- **Phase:** 2, step 3 · **Status:** Fixed (automated) · **Last updated:** 2026-11-19
- **Related:** B-025, B-008, B-013

**Original description.** Home navigation can skip or mishandle "See All" controls. Focus and row-boundary navigation can be inconsistent. *Expected:* predictable focus order across cards, rows, section actions, and adjacent content using D-pad navigation.

**Findings document.** Vertical navigation sometimes skips See All. Moving down from See All can focus the next row's See All instead of its corresponding content. Investigate: focus graph, row boundaries and lazy-list behaviour.

**Steps to reproduce.** Reported: Home on Windows with a controller; move down through the rows; See All is sometimes skipped; down from See All lands on the next row's See All. Not yet reproduced by Reflux.

**Expected behaviour.** Predictable D-pad order across cards, rows, section actions and adjacent content.

**Actual behaviour.** See All skipped; down from See All goes to the next See All (reported).

**Acceptance criteria.** A defined focus order for Home, documented and covered by UI tests; walked with a controller on Windows and on TV.

**Work record**

- **Cause:** See all sat at the right of each row's title, and Compose's beam search governs vertical movement. Moving down card to card never stops on it ("skipped"). Down from See all finds the next row's See all, straight below and nearer than any card.
- **Decision (documented in INPUT_MODEL.md):** See all belongs to its row.
  - Up from any of a row's cards is its See all.
  - Down from See all is back into that row, to the card focus was last on, or the first in view (decided when Down is pressed).
  - Moving down card by card is unchanged.
  - Rows without See all are unchanged.
- **Implementation:** `HomeScreen.RowSection` (`focusProperties` on each card's `up` and on See all's `down`; per-card requesters; no `focusRestorer`, which broke the rail's way back in).
- **Tests:** `HomeFocusTest.seeAllBelongsToItsRow`; `TelevisionTest.theRemoteMovesARowAtATimeAndComesBackToWhereItWas`, updated to the new order (Up from a row's card stops at its See all first).
- **Outstanding:** controller walks on Windows and TV; owner acceptance.
<a id="b-008"></a>
### B-008: Returning Home leaves controller navigation stuck

- **Category:** Bug (controller navigation) · **Priority:** Medium · **Platforms:** W (controller)
- **Components (likely):** `ui` (`HomeScreen`, `Navigator`, `Focus`, lazy-list state)
- **Phase:** 2, step 2 · **Status:** Fixed (automated; root causes reproduced) · **Last updated:** 2026-11-19
- **Related:** B-025, B-007, B-013

**Original description.** Navigating Home → Movies → Home can leave focus, navigation, or scroll restoration in an unusable state. *Expected:* returning Home restores a valid, usable focus target and appropriate scroll state; navigation must not become stuck or lose focus.

**Findings document.** After Home → Movies → Home, right D-pad/joystick navigation sometimes fails to enter the main content. Focus restoration and scroll position are inconsistent. Investigate: focus restoration and lazy-list state.

**Steps to reproduce.** Reported: Windows with a controller; Home → Movies → Home; press right; focus sometimes does not enter the content. Not yet reproduced by Reflux.

**Expected behaviour.** A valid focus target and scroll state on return; never stuck.

**Actual behaviour.** Right does not enter the content; restoration inconsistent (reported).

**Acceptance criteria.** Round trips between every sidebar destination and Home restore focus and scroll (UI tests); controller walks on Windows and TV.

**Work record**

- **Reproduced (`HomeFocusTest`):** two causes, both found by the tests before the fix.
  1. Home → Movies → Home by the rail, then Right, landed on the hero's Play rather than the card that was left. The page remembered only an item that had been *opened* (`rememberFocusReturn`), and `RailFocus.enterPage` held a requester of the page that had gone.
  2. After a quick round trip by the bumpers, Right arrived before Home had been read again and had anything to focus. Nothing had focus, so the toolkit's search took the nearest control, the rail's Home. This is the reported "sometimes" (`DEBUGFOCUS` trace: `hasFocus=false`, `inChrome=false`).
- **Fix:**
  1. The item with focus is remembered as the one to come back to, whatever way the page is left, and is not pulled into focus while focus is on the navigation.
  2. A press while nothing has focus and the page has nothing yet waits up to 30 frames for the page's start, then falls back to the toolkit.
- **Tests:** `HomeFocusTest.homeToMoviesToHomeByTheRailLeavesRightIntoTheContent`, `everyRoundTripFromHomeComesBackUsable` (Movies, Shows, Search, Settings, Profiles, repeated; four bumper round trips), `aDetailsPageAndBackReturnsToTheCardItWasOpenedFrom`.
- **Outstanding:** controller walks on Windows and TV; owner acceptance.
<a id="b-009"></a>
### B-009: Focus outline obscures titles

- **Category:** Bug (UI/accessibility) · **Priority:** Medium · **Platforms:** W, F, TV (findings: more severe on Windows, smaller on Fire TV)
- **Components (likely):** `ui` (`Artwork` cards, `Focus`); `design` (scale)
- **Phase:** 2, step 16 (with B-032) · **Status:** Fixed (automated) · **Last updated:** 2026-11-19
- **Related:** B-032, B-042, B-015

**Original description.** Controller focus outlines can obscure poster/title text or other content. *Expected:* focus indicators must remain visually clear without obstructing important information, appropriate across layouts, screen densities, and TV distances.

**Findings document.** White focus border extends over movie titles. More severe on Windows, smaller on Fire TV. Evidence: Windows and mobile screenshots supplied. Investigate: card bounds, clipping, elevation and scaling.

**Steps to reproduce.** Reported: focus a poster card with a controller; the white outline covers the title below it. The screenshots are the owner's.

**Expected behaviour.** Clear focus that never covers titles or other content.

**Actual behaviour.** The outline covers titles (reported, with screenshots).

**Acceptance criteria.** A focused card's outline never overlaps title text at any density or size (a layout test like `ActivityIndicatorLayoutTest`); looked at on Windows, Fire TV, and TV.

**Work record**

- **Cause (measured from the code):** a controller's poster is 193 × 290 dp and lifts by 8%, about 11.6 dp on every side. Its ring reaches 6 dp more and its glow 12 dp more. The title started 8 dp below the unscaled poster, and the row's top padding was 14 dp under the section's title.
- **Fix:**
  - While a poster is lifted its words step down with it, by exactly the lift's reach plus the ring's (`liftReach`, animated with the lift), so they never pass under the ring.
  - A row leaves room above its cards for the same reach.
  - Nothing else in the row moves.
- **Tests:** `CardLayoutTest.aFocusedCardsRingNeverCrossesItsTitleOrTheRowsTitle` (1440 × 900, 1024 × 768, 960 × 540 television: three focused cards each, the ring against the card's own words and the row's title).
- **Outstanding:** looking at it on Windows, Fire TV and TV with the owner's library; owner acceptance.
<a id="b-010"></a>
### B-010: Search suggestion focus spacing/clipping

- **Category:** Bug (controller UI) · **Priority:** Medium · **Platforms:** W (controller)
- **Components (likely):** `ui` (Search's suggestion chips)
- **Phase:** 2, step 15 · **Status:** Fixed (automated) · **Last updated:** 2026-11-19
- **Related:** B-011, B-035, B-025

**Original description.** Search suggestion focus behaviour and spacing are inconsistent; suggestions can be clipped or poorly positioned. *Expected:* search suggestions should be visible, correctly spaced, unclipped, and navigable through controller focus.

**Findings document.** Suggestion chips are too close together, focus outlines overlap neighbours, and the final suggestion is clipped. Investigate: responsive layout, horizontal scrolling and padding.

**Steps to reproduce.** Reported: open Search on Windows with a controller and move through the suggestion chips; outlines overlap neighbours; the last chip is clipped.

**Expected behaviour.** Spaced, unclipped, navigable suggestions.

**Actual behaviour.** Overlapping outlines; the last chip clipped (reported).

**Acceptance criteria.** A focused chip's outline never overlaps a neighbour, and the last chip scrolls fully into view (layout test); a controller walk on Windows.

**Work record**

- **Cause:**
  - Chips were 8 dp apart while a ring reaches 6 dp beyond its control, so a focused chip's ring met its neighbour.
  - The chips were inside the grid's padded header, so the last one ended at the page's margin, cut off.
- **Fix:** 12 dp apart, no lift, and a `LazyRow` with focus scrolling inside the window's edges (`FocusScrollInsets`) and room for the ring above and below.
- **Tests:** `CardLayoutTest.suggestionChipsKeepTheirRingsApartAndScrollWholeIntoView` (1440 and 412 wide: every chip focused in turn is whole on screen, and every pair is at least 12 dp apart).
- **Outstanding:** a controller walk on Windows; owner acceptance.
<a id="b-011"></a>
### B-011: Controller-only search lacks usable keyboard

- **Category:** Bug (input/UX) · **Priority:** Medium · **Platforms:** W (controller), TV relevance
- **Components (likely):** `ui` (Search, `TextInput`, `Focus`)
- **Phase:** 2, step 7 · **Status:** Fixed (automated) · **Last updated:** 2026-11-19
- **Related:** B-023, B-025, B-004, B-010

**Original description.** Search is not reliably usable when operated exclusively by a controller. *Expected:* a controller user must be able to open search, focus the field, enter text, navigate suggestions/results, and finish the interaction without requiring a mouse or touch.

**Findings document.** A user cannot reliably enter a search query using only a controller.

**Steps to reproduce.** Reported: on Windows with only a controller, open Search and try to enter a query. Not yet reproduced by Reflux.

**Expected behaviour.** Open, type, browse suggestions and results, and finish with a controller alone.

**Actual behaviour.** A query cannot reliably be entered (reported).

**Acceptance criteria.** The whole search flow completed controller-only on Windows, Android TV (emulator) and Fire TV (physical); UI test of the flow.

**Work record**

- **Cause:** as B-023. On Windows with a controller alone there was no way to type: confirm on the field did nothing, and no keyboard was offered.
- **Fix:** the Reflux keyboard. Done (Start) goes on to the results (Search's `onDone` moves focus down). The query lives in `AppState`, so it survives opening a result and coming back.
- **Tests:** `InteractionContractTest.searchIsUsableWithAControllerAlone`: Search reached by the bumpers, the field, the keyboard, "heat" typed by moving to each key, Start into the results, a result opened, Back to Search, the query kept.
- **Outstanding:** walks on Windows (physical controller), the Google TV emulator, Fire TV; owner acceptance.
<a id="b-012"></a>
### B-012: Controller shortcuts incomplete or unclear

- **Category:** Product decision / UX investigation · **Priority:** Low · **Platforms:** W (controller)
- **Components (likely):** `ui` (input handling, `Controls`); docs/INPUT_MODEL.md
- **Phase:** 2, step 8 (with B-039) · **Status:** Fixed (with B-039) · **Last updated:** 2026-11-19
- **Related:** B-039 (the mapping design), F-005 (customisation), F-P2 (Y and the sidebar)

**Original description.** Existing controller shortcuts are incomplete or not clearly assigned. Y currently opens Sources, X has no useful action, and other buttons/triggers appear unused. *Expected:* establish a consistent, intentional controller mapping; document it and expose useful hints where appropriate; do not assign controls without checking for conflicts with existing behaviour.

**Findings document.** Y opens Sources, X does nothing, and triggers appear unused. Improvement: useful documented and configurable mappings.

**Steps to reproduce.** Press X, LB/RB, LT/RT on Windows with a controller: no useful action (reported).

**Expected behaviour.** A deliberate, documented, conflict-checked mapping with hints.

**Actual behaviour.** As described above.

**Acceptance criteria.** Delivered with B-039: a mapping decision in DECISIONS.md, INPUT_MODEL.md updated, hints shown, conflict analysis recorded, tests of each mapped action.

**Work record**

- **Resolved with B-039:**
  - Y opens Sources and is now named in the hints.
  - X is deliberately reserved (Phase 3's Quick View), not forgotten.
  - The triggers have no browsing meaning, by decision.
  - The mapping is documented in INPUT_MODEL.md.
- **Outstanding:** as B-039.
<a id="b-013"></a>
### B-013: Home hero position is not restored after scrolling

- **Category:** Bug (navigation UX) · **Priority:** Medium · **Platforms:** W (controller)
- **Components (likely):** `ui` (`HomeScreen` hero and scroll state)
- **Phase:** 2, step 19 · **Status:** Fixed (automated) · **Last updated:** 2026-11-19
- **Related:** B-008, B-007, B-014

**Original description.** Home hero content is not restored to its expected full position after navigating or scrolling. *Expected:* hero and Home layout restoration should be consistent and predictable.

**Findings document.** Returning to the featured area leaves only Play/Details visible rather than restoring the full hero artwork and content.

**Steps to reproduce.** Reported: on Windows with a controller, scroll down Home, then move back up to the featured area: only Play/Details are visible. Not yet reproduced by Reflux.

**Expected behaviour.** Moving back to the hero restores its full artwork and content.

**Actual behaviour.** Only Play/Details visible (reported).

**Acceptance criteria.** Returning focus to the hero scrolls to its top (UI test); walked on Windows and TV.

**Work record**

- **Cause:** on a desktop, a phone and a tablet, focus moving back up to the hero brought only its focused button into view (`BringIntoViewSpec`), leaving the page scrolled to just above it: only Play and Details showed. A television already moved a row at a time to the hero's top.
- **Fix:** when focus comes back into the hero, Home scrolls to its very top (`moveToItem(0, 0)`, at once with motion reduced).
- **Tests:** `HomeFocusTest.comingBackUpToTheHeroShowsAllOfIt` (scrolled five rows down; back up, the hero's heading is where it was at the start).
- **Outstanding:** walks on Windows and TV; owner acceptance.
<a id="b-014"></a>
### B-014: Featured hero appears alphabetically selected

- **Category:** Product decision / UX investigation · **Priority:** Low (design question) · **Platforms:** all (Home)
- **Components:** `library` (`Home.kt`, `Library.home`, the `featured` selection); `ui` (`HomeScreen`)
- **Phase:** 3, step 8 · **Status:** Fixed (automated; rendered; no finding in the owner's walk) · **Last updated:** 2026-11-20
- **Related:** B-040 (discovery variety), B-013

**Original description.** The featured Home hero appears to be selected alphabetically, for example "2 Fast 2 Furious". It is unclear whether this is intended. *Investigate:* hero selection logic, rotation, randomization, recommendations, and sorting. *Expected:* decide on an intentional, sensible featured-content strategy; do not assume alphabetical selection is desired.

**Findings document.** Hero displays 2 Fast 2 Furious. It may be selecting the first alphabetical title. Investigate: intended selection logic and whether multiple featured titles should rotate.

**Steps to reproduce.** Reported: open Home; the hero shows "2 Fast 2 Furious".

**Expected behaviour.** An intentional, explainable featured-content strategy decided by the owner.

**Actual behaviour.** The same alphabetically early title (reported).

**Acceptance criteria.** A strategy decided and recorded in DECISIONS.md; implemented with tests; never alphabetical by default merely because it happens now.

**Work record**

- **Root cause (confirmed in the code):** with nothing in Continue Watching, the hero was the first of Recently Added with a backdrop. After a first scan every work has the same `added_at`, and `recentlyAdded` breaks ties by `sort_key`, so the alphabetically first work with a backdrop ("2 Fast 2 Furious") was featured.
- **Strategy (DECISIONS.md, 2026-11-20, "Phase 3: Trickplay …; the featured hero …"):** what the viewer is in the middle of; otherwise a pick for the day and the profile among the twelve best-rated unwatched films and shows with a backdrop (ties by identity, never title); with nothing unwatched, among everything with a backdrop; nothing without a backdrop. Deterministic, stable through a day, different between profiles, no new query (`Featured.choose` in `library/Home.kt` over what Home already loads).
- **Tests:** `FeaturedTest` (5): continuing first; stable within a day and changing across days; listing order irrelevant; the alphabetical first no more likely than any other; profiles differ on most days; watched works step aside; no backdrop, no hero. `SmartLibraryTest` 6/6 unchanged.
- **Automated verification:** library JVM tests, Windows, 2026-11-20. Rendered (`quick-view-controller`): Home's hero is "Mad Max: Fury Road", not "Alien", the alphabetical first of the test library.
- **Outstanding:** walks per the matrix; owner acceptance. Not a recommendation engine: B-040 (V1.1) would give the hero a "because you watched" pick.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-015"></a>
### B-015: Poster alignment/clipping vertically

- **Category:** Bug (UI) · **Priority:** Low · **Platforms:** W (desktop)
- **Components (likely):** `ui` (`Artwork` cards, rows)
- **Phase:** 2, step 18 · **Status:** Not Reproduced (layout test green; a likely cause recorded) · **Last updated:** 2026-11-19
- **Related:** B-009, B-042

**Original description.** Posters can appear vertically misaligned or clipped. *Expected:* poster cards and artwork should align consistently within their rows and containers.

**Findings document.** Some posters appear vertically offset or partially hidden. Evidence: screenshots supplied.

**Steps to reproduce.** Reported on the Windows desktop with screenshots (the owner's). Not yet reproduced by Reflux.

**Expected behaviour.** Consistent alignment within rows and containers.

**Actual behaviour.** Offset or partly hidden posters (reported).

**Acceptance criteria.** Cards in a row share top and bottom edges at every size (layout test); looked at on Windows.

**Work record**

- **Investigation:** `CardLayoutTest.cardsInARowShareTheirTopAndBottom`: on Home, every row's cards share their top and bottom to within a pixel, with and without artwork, under a pointer and a controller.
  - Grids already use equal columns (`EqualColumns`, introduced so that captions line up).
  - What can make one poster look offset is the lift of a card that is hovered or focused: under a pointer, hover lifts a card by 4% about its centre. That is B-042's shared hover/focus state, unassigned.
- **Not reproduced** without the tester's screenshots, which are not in the repository.
- **Outstanding:** the owner's screenshots, or a walk with the owner on Windows.
- **2026-11-20:** the likely cause above (a card lifted by a resting pointer) is fixed with B-042: under a controller nothing but the focused card is lifted. Still Not Reproduced without the screenshots.
<a id="b-016"></a>
### B-016: App opens last-used profile instead of profile selection

- **Category:** Product decision / UX · **Priority:** Medium · **Platforms:** W L P T (desktop and Android)
- **Components (likely):** `ui` (`ProfilesScreen`, start-up), `library` (profiles); docs/WATCH_STATE.md ("Profiles")
- **Phase:** **3** (the owner's Phase 3 work order, 2026-11-20) · **Status:** Verified (physical: the Pad 7 and Fire TV walks, VERIFICATION.md, "Phase 3") · **Last updated:** 2026-11-20
- **Related:** B-017, B-018

**Original description.** The application opens directly into the last-used profile instead of presenting an obvious profile-selection experience. *Expected:* provide a startup profile chooser with the last-used profile prominently available, rather than making profile selection invisible or implicit. Phase allocation is unresolved; do not assign without recording the owner decision.

**Findings document.** After creating and using a Guest profile, restarting the application automatically opens the last-used profile. Expected: a startup profile chooser, with the most recently used profile first.

**Steps to reproduce.** Create and use a Guest profile; restart: the app opens Guest without asking.

**Expected behaviour.** A start-up chooser with the last-used profile first.

**Actual behaviour.** Opens the last-used profile. **This is the current design** (WATCH_STATE.md, "Profiles": a phone or a computer opens the profile it had; a TV asks), so changing it is a design decision as well as a fix.

**Acceptance criteria.** To be set with the owner's decision.

**Work record**

- **Investigation findings:** the behaviour matched the documented design (above). The active profile is persisted as the library's active row; `ensureProfiles` repairs none or several active, and `deleteProfile` makes another active when the open one goes, so a missing, deleted or invalid last profile cannot be opened.
- **Owner's decision (2026-11-20, the Phase 3 work order):** "Implement the behaviour so that the app opens using the last-used profile instead of forcing profile selection on every launch", with profile selection still reachable. This reverses the tester's expectation above; the work order is the later instruction (DECISIONS.md, 2026-11-20, "Phase 3: every device opens the last-used profile").
- **Implementation:** `AppState.askWhoIsWatchingAtStart` no longer asks on a television; every device opens the profile it had, and a profile with a PIN still opens locked. Selection stays a press away (B-017).
- **Tests:** `ProfilesUiTest.aTelevisionOpensTheProfileItHadAndAsksWhoIsWatchingOnlyWhenAsked` (replaces the test that held the old television rule); `aProfileWithAPinOpensLockedAtStart` unchanged and passing.
- **Automated verification:** `ProfilesUiTest` 15/15, `ProfileSecurityTest` 4/4, `ProfileBoundaryTest` 3/3 (Windows JVM, 2026-11-20).
- **Device verification:** see VERIFICATION.md, "Phase 3".
- **Outstanding:** owner acceptance.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-017"></a>
### B-017: No obvious logout/profile switch

- **Category:** Product decision / UX · **Priority:** Medium · **Platforms:** all
- **Components (likely):** `ui` (`ProfilesScreen`, navigation chrome)
- **Phase:** **3** (the owner's Phase 3 work order, 2026-11-20) · **Status:** Verified (physical: the Pad 7 walk; the owner's Windows walk, 2026-11-20) · **Last updated:** 2026-11-20
- **Related:** B-016, B-018

**Original description.** There is no obvious way to log out, switch profiles, or return to "Who's watching?". *Expected:* profile switching/logout must be discoverable and predictable without damaging profile data. Phase allocation is unresolved; do not assign without recording the owner decision.

**Findings document.** Profile switching is not discoverable. Expected: a clear "Who's watching?" or profile-switching entry point.

**Steps to reproduce.** Look for a way to switch profile or log out (reported as not obvious).

**Expected behaviour.** A discoverable, predictable switch that never damages profile data.

**Actual behaviour.** Not discoverable (reported).

**Acceptance criteria.** To be set with the owner's decision.

**Work record**

- **Investigation findings:** switching existed only as the profile list on Profiles (a utility entry); there was no log out, and no switch where the profile is shown (Settings).
- **Implementation:** with several profiles, Settings' profile section offers **Switch profile** (the "Who's watching?" question, which Back steps out of) and **Log out**; Profiles has **Log out of <name>** beside the list. `AppState.logOut` stops playback (recorded for its profile), closes the keyboard, clears the session's search, last playback report and notice, locks a PIN profile again, shows Home beneath, and asks who is watching as a required question (Back leaves Reflux). Nothing is deleted or signed out of: logging out is not resetting or deleting. With one profile neither entry is shown (one profile is never mentioned). Every action is an ordinary control, so it works by touch, pointer, keyboard, controller and remote.
- **Tests:** `ProfilesUiTest.logOutAsksWhoIsWatchingKeepsEverythingAndBackDoesNotGetPastIt` (history and PIN kept, PIN asked again, session cleared, Home beneath, Back does not pass); `switchProfileIsOfferedInSettingsAndOnProfilesOnlyOnceThereAreSeveral`; the existing `nothingOfOneViewersSessionIsLeftForTheNext` and `switchingProfileWhilePlayingStopsThePlaybackForTheProfileItWasFor`.
- **Automated verification:** as B-016 (2026-11-20).
- **Outstanding:** owner acceptance.
- **Verified, physical (2026-11-20, the owner on Windows):** add a profile, Switch profile, Log out to "Who's watching?" and back, delete: "The profile functionality works." Its management was then redesigned (B-066).
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-018"></a>
### B-018: Profile deletion without proving ownership

- **Category:** Security · **Priority:** Critical · **Platforms:** all
- **Components:** `library` (`Library.deleteProfile`, `verifyPin`); `ui` (`ProfilesScreen`, `AppState.deleteProfile`)
- **Phase:** 1, step 1 · **Status:** Fixed · **Last updated:** 2026-11-19
- **Related:** B-016, B-017; the open "Forgot PIN" decision

**Original description.** Guest was able to delete Main without entering a PIN or proving authorization. *Expected:* protected profile deletion must enforce authorization at the underlying operation level, not merely hide or disable a UI control; verify bypass attempts and other deletion paths. *Investigate/fix:* authorization enforcement, profile ownership, protected profile rules, PIN validation, deletion entry points, and regression tests.

**Findings document.** While signed into Guest, the user could delete Main without entering Main's PIN. Expected: authorization enforced at the underlying deletion operation, not merely through UI visibility or confirmation. Investigate: profile ownership and authorization boundaries.

**Steps to reproduce.** In Guest: Profiles → "Delete Main…" → Confirm. Before the fix Main (PIN-protected) was deleted.

**Expected behaviour.** A protected profile is deleted only with its own PIN, checked where the deleting happens.

**Actual behaviour (before the fix).** `ProfilesScreen` → `AppState.deleteProfile` signed accounts out and deleted downloads, then `Library.deleteProfile`, with no PIN check anywhere; a confirmation was the only gate.

**Acceptance criteria.** Deletion of a protected profile only with its own PIN, enforced in the operation; the lockout counts wrong entries; nothing signed out or deleted on a wrong PIN; isolation of other profiles; the last profile kept; accounts first.

**Work record**

- **Investigation findings:** confirmed in code (above). `resetProfile` ("Forgot PIN") erases a protected profile's private data without the PIN, by design.
- **Root cause:** established.
- **Implementation:** `Library.deleteProfile(id, pin, key)` verifies the target's PIN inside the deleting transaction with the same counting and waits as opening it (one rule, `verifyPin`); `AppState.deleteProfile(profile, pin)` checks first, so nothing is signed out or deleted on a wrong PIN; the screen asks for the PIN with the existing pad. No reusable token. Commit `c1ca1d1`; DECISIONS.md, 2026-11-18.
- **Tests added or modified:** `ProfilesTest` (6 new: no PIN, a PIN without a key, wrong, empty, another profile's PIN, a PIN just accepted for another profile; five wrong then a wait that also blocks opening and survives reopening; only the target's rows go; accounts first; last profile kept); `ProfilesUiTest` / `ProfileBoundaryTest` (3 new).
- **Automated verification:** `:library:jvmTest --tests ProfilesTest --tests SubtitleMemoryTest` 18 + 7 passed; `:ui:jvmTest --tests ProfilesUiTest --tests ProfileBoundaryTest` 13 + 3 passed. Windows.
- **Physical/device verification:** none (a Windows walk of the reported Guest/Main case is outstanding).
- **Regression testing:** full `./gradlew check` passed.
- **Evidence:** VERIFICATION.md, "Phase 1", B-018.
- **Forgot PIN (decided 2026-11-19, implemented in Phase 2):** Keep profile, or Reset profile (after a warning and a second confirmation; the reset now needs that confirmation step in the state itself). On Android phones and tablets the system's own prompt can remove the PIN and keep everything. `ProfileSecurityTest`, `ProfilesUiTest`; PHASE-2-PLAN.md, "Forgot PIN".
- **Outstanding work:** a walk of the reported case; confirming with a fingerprint on a handheld (cancelling was walked on the Phone 2a); owner acceptance.

<a id="b-019"></a>
### B-019: Backspace in PIN input triggers Home

- **Category:** Bug (input/navigation) · **Priority:** Medium (findings document) · **Platforms:** controller and keyboard inputs (W L)
- **Components (likely):** `ui` (PIN pad, global shortcut handling in `RefluxApp` / `Controls`)
- **Phase:** 2, step 5 (with B-020, B-021) · **Status:** Fixed (automated; root cause confirmed) · **Last updated:** 2026-11-19
- **Related:** B-020, B-021, B-023, B-025

**Original description.** Pressing Backspace while entering a PIN can trigger Home navigation instead of deleting the input character. *Expected:* input editing must take precedence while a text/PIN field is actively receiving input; Backspace must edit the field instead of propagating to global navigation.

**Findings document.** Backspace navigates Home instead of deleting a digit.

**Steps to reproduce.** Open a PIN prompt; type digits; press Backspace: Home opens (reported).

**Expected behaviour.** Backspace deletes a digit.

**Actual behaviour.** Home navigation (reported).

**Acceptance criteria.** Backspace edits every active PIN field and never navigates (UI test); one input/shortcut priority model (B-021).

**Work record**

- **Root cause (confirmed in code and reproduced):** `InputMapping.map(Key.BACKSPACE) = BACK`. Keys go to Reflux before the toolkit, and Reflux leaves them alone only while `AppState.textInputActive`. Only Search, Identify and the Sources forms set that. No PIN field did (unlock, deletion, PIN setup), so Backspace was Back: from a section, Home; on the gate, the gate's Back.
- **Fix:** B-025's `Modifier.textEntry`, through which every field reports its own focus.
- **Tests:** `InteractionContractTest.backspaceInADeletionPinErasesADigitAndNeverGoesHome`, `backspaceInTheUnlockPinErasesAndTheGateStays`.
- **Outstanding:** a physical keyboard walk on Windows and Linux; owner acceptance.
<a id="b-020"></a>
### B-020: Backspace in profile-name input triggers Home

- **Category:** Bug (input/navigation) · **Priority:** Medium (findings document) · **Platforms:** controller and keyboard inputs (W L)
- **Components (likely):** `ui` (profile name field, global shortcut handling)
- **Phase:** 2, step 5 (with B-019, B-021) · **Status:** Fixed (automated; root cause confirmed) · **Last updated:** 2026-11-19
- **Related:** B-019, B-021, B-023

**Original description.** Backspace in profile-name input can trigger Home navigation. *Expected:* Backspace correctly edits text without triggering unrelated navigation.

**Findings document.** Backspace navigates Home instead of deleting a character.

**Steps to reproduce.** Create or rename a profile; type a name; press Backspace: Home opens (reported).

**Expected behaviour.** Backspace deletes a character.

**Actual behaviour.** Home navigation (reported).

**Acceptance criteria.** As B-019, for the profile name field.

**Work record**

- **Root cause:** as B-019. Neither the profile-name field (Profiles' Add) nor Settings' rename set `textInputActive`.
- **Fix:** as B-019.
- **Tests:** `InteractionContractTest.backspaceInAProfileNameErasesAndEscapeLetsGoFirst` (Profiles' Add and Settings' rename; Escape lets go of the field first, then the next Escape is Back).
- **Outstanding:** as B-019.
<a id="b-021"></a>
### B-021: Backspace behaviour inconsistent across inputs

- **Category:** Bug (input/navigation) · **Priority:** Medium (findings document) · **Platforms:** cross-platform input (reported W L)
- **Components (likely):** `ui` (`TextInput`, global shortcut propagation)
- **Phase:** 2, step 5 (with B-019, B-020) · **Status:** Fixed (automated) · **Last updated:** 2026-11-19
- **Related:** B-019, B-020, B-023, B-025

**Original description.** Backspace handling is inconsistent across text inputs and can leak into global shortcut handling. *Expected:* establish a consistent input event/shortcut priority model; active text inputs must handle editing keys before global navigation shortcuts.

**Findings document.** PIN/name fields are affected, while Search and some source fields behave differently. Investigate: global shortcut propagation and input focus.

**Steps to reproduce.** Compare Backspace in the PIN, profile-name, Search and source fields (reported: PIN and name navigate; Search and some source fields do not).

**Expected behaviour.** One priority model: an active input handles editing keys first.

**Actual behaviour.** Inconsistent; leaks into global shortcuts (reported).

**Acceptance criteria.** A documented priority model (INPUT_MODEL.md); a test per text input that editing keys never navigate.

**Work record**

- **Cause:** the fields that worked (Search, some Sources fields) set `textInputActive` by hand; the others did not. That is the inconsistency reported.
- **Fix:** one priority model, written in INPUT_MODEL.md, "Event priority": the active text editor, the active modal, the focused control, the destination, then global shortcuts, with stated exceptions (Escape, Up/Down, Ctrl+1–6, media keys, a touchscreen's Back). One mechanism carries it (`Modifier.textEntry`), and no screen sets the flag.
- **Tests:** the B-019 and B-020 tests; `KeyboardSessionTest.whileTypingTheButtonsAreTheKeyboardsAndNothingIsAShortcut`; `NavigationTest` (Ctrl+number still works from inside a field).
- **Outstanding:** as B-019.
<a id="b-022"></a>
### B-022: Windows startup slower than Android

- **Category:** Performance (investigation) · **Priority:** Low to Medium · **Platforms:** W
- **Components (likely):** `desktop:app` (start-up, JVM and Compose initialisation), `library` (database open), `playback:mpv` (engine initialisation)
- **Phase:** **3** (the owner's Phase 3 work order, 2026-11-20, with B-041) · **Status:** Investigating (carried: measured with a breakdown in Phase 3; no optimisation justified yet) · **Last updated:** 2026-11-20
- **Related:** B-041 (its baseline holds the measurements), B-006

**Original description.** Startup feels slower than Android; approximately 3–4 seconds was observed. *Investigate:* measure cold startup, warm startup, initial usable UI, source initialization, database work, media engine initialization, rendering, and blocking operations. *Expected:* establish actual measurements and optimize justified bottlenecks.

**Findings document.** Windows startup takes approximately 3–4 seconds, while the tested Android device starts faster. Investigate: cold/warm startup, JVM/Compose initialisation, database and source loading.

**Steps to reproduce.** Start the packaged Windows app; time to a usable window (reported 3–4 s).

**Expected behaviour.** Measured, with justified bottlenecks optimised.

**Actual behaviour.** Measured (below): within the reported range warm, slower on a first cold run.

**Acceptance criteria.** Cold, warm and first-usable-UI times measured on Windows with a breakdown of where the time goes; optimisations only where the breakdown justifies them; before/after numbers.

**Work record**

- **Investigation findings:** Phase 1 baseline (one Windows machine, empty library, packaged, 7 runs): start to window median 2.7 s (HEAD) and 2.6 s (Phase 1 tree); 5.0–5.2 s on each first, cold run, then 2.2–2.7 s. Android release cold start medians 238–480 ms on four devices. Not a like-for-like comparison (a JVM desktop window against an Android activity). No breakdown of where the desktop time goes yet.
- **Root cause:** not established.
- **Implementation / tests:** none.
- **Physical/device verification:** the measurement above is on the physical Windows development machine.
- **Evidence:** VERIFICATION.md, "Phase 1", Baselines (WS11).
- **Phase 3 measurement (2026-11-20, the same Windows machine, method and workload as Phase 1: the packaged app from `createDistributable` at Phase 3's tree, an empty library through `REFLUX_HOME`, 7 runs, start to the window's appearance by its process, the window closed with `WM_CLOSE`):**
  - Start to window: 5,091 ms on the first (cold) run, then 2,162 to 2,240 ms; **median of 7: 2,227 ms**. Phase 1: 2.6 to 2.7 s, cold 5.0 to 5.2 s.
  - Working set at 6 s: 446 to 490 MB (cold run 359 MB). Phase 1: 492 to 500 MB.
  - Close to the processes gone: 682 to 714 ms. Phase 1: 0.71 to 0.82 s.
  - No regression; the difference from Phase 1 is within what the machine's state moves.
- **The breakdown (new, `StartupTrace`, opt-in with `REFLUX_STARTUP_TRACE=1` or `REFLUX_STARTUP_TRACE_FILE`; milliseconds after the JVM's start, warm runs):**
  - main 0;
  - **environment (the database, sources and registry) about 780 ms;**
  - composition about 940 ms;
  - **window about 1,660 ms;**
  - first frame about 1,970 ms;
  - the launcher and the JVM's own start before `main` make up the rest of the 2.2 s seen from outside.
- **Where the time goes:** creating `DesktopEnvironment` (about 0.78 s), and from the first composition to the window (about 0.7 s: `AppState`, the window, the toolkit's first layout). Android starts in 0.24 to 0.48 s, but that is not a like-for-like comparison: an activity in a running runtime against a JVM and a desktop toolkit started from nothing.
- **Root cause / optimisation:** none established. The next step is a profile of `DesktopEnvironment`'s construction (database open and migration check, the artwork cache's directory, source records, the engine's library lookup) on a library of the tester's size. An optimisation without that evidence would be guesswork, which B-041 rules out.
- **Outstanding:** that profile; a non-empty, large library run; the owner's acceptance of the measurement.

<a id="b-023"></a>
### B-023: Reflux-designed controller keyboard

- **Category:** Feature / UX improvement · **Priority:** High (UX requirement) · **Platforms:** W, T; TV relevance to assess
- **Components (likely):** `ui` (new keyboard component, `TextInput`, `Focus`); `design`
- **Phase:** 2, step 6 · **Status:** Fixed (automated; device and TV emulator walks below) · **Last updated:** 2026-11-19
- **Related:** B-004, B-011, B-019, B-020, B-021, B-025

**Original description.** Provide a Reflux-designed on-screen keyboard suitable for controller and touch input. *Expected capabilities:* letters; numbers; symbols; space; backspace; confirm/submit; clear and predictable focus behaviour; reliable insertion and dismissal; ability to reopen; suitable layouts for search, profile names, PINs, and source addresses; appropriate touch and controller operation. Investigate whether platform-native keyboards or custom keyboard layouts are more suitable per platform, but keep cross-platform UX consistent.

**Findings document.** Platforms: Windows desktop and Android tablets; TV applicability to be assessed. Native OS keyboard alone is not the desired experience. Reflux should provide an integrated, polished keyboard navigable with D-pad/joystick. Expected: letters, numbers, symbols, space, backspace, confirm, clear focus states, reliable insertion, dismissal/reopening without losing text and responsive layouts. Fields: search, profile creation, PIN entry and source configuration.

**Steps to reproduce.** Not applicable (a feature).

**Expected behaviour.** As the capabilities above.

**Actual behaviour.** No Reflux keyboard; the platform's keyboard (or none, on Windows with a controller).

**Acceptance criteria.** Each capability above works with controller and touch in each field type (search, profile name, PIN, source address); dismiss and reopen never lose text; a per-platform decision (native or custom) recorded in DECISIONS.md; UI tests; walks on Windows, tablet, TV.

**Work record**

- **Decision (per platform; DECISIONS.md and INPUT_MODEL.md):**
  - Touch keeps the system keyboard.
  - A physical keyboard types directly.
  - A controller or any remote uses the Reflux keyboard on every form factor: Windows and Linux have no controller-friendly system keyboard, a tablet's cannot be driven with a D-pad, and a television's lifecycle caused B-004.
- **Implementation:**
  - `KeyboardSession` (`ui/state/Keyboard.kt`): pure state.
    - Layouts per `InputKind`: text, search, name (first letter capitalised), address (`:` `/` `.` `-` and `http://` on its first page), secret, and a PIN number pad.
    - Keys are chosen by where they are (nearest above or below).
    - Typing goes at the cursor, and the field's own filter is read back.
    - Shift is once, locked or off; there is a symbols page; erase, clear, done, and the cursor.
  - `RefluxKeyboard`: the panel, at the foot of the window and above everything, the profile gate included. It shows a preview with the cursor (masked for secrets), the chosen key lit, touch and pointer presses, and controller hints.
  - A `TEXT_ENTRY` input context: A types, B closes, X erases, Y types a space, LB/RB move the cursor, LT is Shift, RT is symbols, Start is Done. Nothing global acts while it is open. A physical keyboard still types into the field underneath.
- **Tests:** `KeyboardSessionTest` (9), `InteractionContractTest` (typing with a controller; a PIN over the gate; a remote on a television), `KeyboardTest`.
- **Outstanding:** walks with a physical controller on Windows and the tablet; Fire TV; owner acceptance.
<a id="b-024"></a>
### B-024: Conflicting resolution values in media descriptions

- **Category:** Bug (metadata) · **Priority:** Medium, provisional · **Platforms:** reported W; the formatting is shared
- **Components:** `core` (`VersionNames`, release-name lexicon); `sources:jellyfin` (version names); `ui` (`Formatting`, the version's technical line)
- **Phase:** 1, step 7 · **Status:** Fixed · **Last updated:** 2026-11-19
- **Related:** none

**Original description.** Media descriptions display conflicting or duplicated resolution/format values, such as "1080p · 1080p" or "1440p · Dolby Vision · 2160p HDR". *Expected:* use the authoritative video stream resolution; separate resolution, HDR format, codecs, audio format, and other metadata correctly; avoid duplicating or mixing incompatible values. *Investigate:* metadata parsing, stream selection, probe data, display formatting, and codec/format classification.

**Findings document.** Version list shows `1080p · 1080p` for one copy and `1440p · Dolby Vision · 2160p HDR` for another. Clarification: display resolution does not determine source resolution. The second version appears to contain two conflicting source-resolution values. Expected: one authoritative source resolution, with HDR/Dolby Vision compatibility, codec and audio presented separately. Investigate: media probing, metadata precedence and release-title parsing.

**Steps to reproduce.** A Jellyfin item with two versions named from their file names ("2160p HDR", "1080p"): the version list showed the probed class plus the name as an "edition".

**Expected behaviour.** One authoritative resolution; range, codec and audio separate.

**Actual behaviour (before the fix).** `versionQuality` = resolution class · range · edition, and Jellyfin's edition was its MediaSource name (taken from the file name) whenever an item had two versions.

**Acceptance criteria.** Measured size authoritative; inferred values not shown as fact; unknowns omitted; HDR10/HDR10+/DV/HLG handled; two versions told apart.

**Work record**

- **Investigation findings:** two authorities (the probe and the file name) shown as one.
- **Root cause:** established in code.
- **Implementation:** a server's version name is an edition only when what remains after removing quality words (resolution, range, codec, source, audio) is non-empty ("Director's Cut" kept; "Heat 4K", "2160p HDR" not); the exact picture size is shown on the version's technical line only when a probe or the source measured it. Commit `c1ca1d1`; DECISIONS.md, 2026-11-18.
- **Tests added or modified:** `VersionNamesTest` (3, new); `FormattingTest` (1 new); `JellyfinSourceTest`'s expectation that "Heat 4K" and "Heat 1080p" are editions changed to none (it asserted the defect).
- **Automated verification:** `:core:jvmTest --tests VersionNamesTest` 3/3; `:ui:jvmTest --tests FormattingTest` 11/11. Windows.
- **Physical/device verification:** none.
- **Regression testing:** full `./gradlew check` passed.
- **Evidence:** VERIFICATION.md, "Phase 1", B-024.
- **Outstanding work:** a Windows walk of the reported version list (real library); owner acceptance.

<a id="b-025"></a>
### B-025: Robust whole-app controller support

- **Category:** Product requirement (cross-platform UX) · **Priority:** High · **Platforms:** W, TV (Android TV, Google TV), F, and controller-capable P/T
- **Components (likely):** `ui` (`Focus`, `Navigator`, `Controls`, every screen); `android:app` and `desktop:app` input; docs/INPUT_MODEL.md, docs/NAVIGATION.md
- **Phase:** 2, step 1 · **Status:** Fixed (shared architecture and contract implemented and tested; device and controller walks partly done: see below) · **Last updated:** 2026-11-19
- **Related:** B-007 to B-013, B-004, B-019 to B-021, B-023, B-039, B-042, F-002, F-P2

**Original description.** Controller support needs to be robust throughout the entire application, not just Home or playback. *Expected coverage:* D-pad navigation; analog sticks; face buttons; bumpers; triggers; Start/Select; platform remotes where relevant; focus and focus restoration; scroll navigation; dialogs and overlays; search and text entry; profile management; source management; playback controls; mini-player; settings; downloads and other screens. *Expected:* no trapped focus, inaccessible controls, unexpected focus loss, mouse-only actions, or navigation dead ends. Establish a shared controller/focus architecture and regression test strategy.

**Findings document.** Platforms: Windows, Android TV, Google TV, Fire TV and Android devices with controllers. Controller support should be consistent and complete, not merely basic button handling. Inputs: D-pad, analogue sticks, face buttons, bumpers, triggers, Start/Select and platform-specific remotes. Areas to validate: navigation, focus restoration, scrolling, dialogs, text entry, profile management, source setup, playback controls, mini-player, settings, downloads and all primary actions. Expected: predictable focus movement, no trapped focus, no accidental navigation, clear focus indication, coherent back/cancel behaviour and complete controller-only operation.

**Steps to reproduce.** Not a single defect: an audit of every screen with a controller only.

**Expected behaviour.** As above.

**Actual behaviour.** Not audited yet; the reported controller defects (B-007 to B-013, B-004) are its symptoms.

**Acceptance criteria.** A shared focus/input architecture recorded in DECISIONS.md and INPUT_MODEL.md; an audit table of every screen and dialog with controller-only results; a regression test strategy (UI tests per screen); walks per BETA-2.md's matrix (tablet, Android TV, Fire TV, Windows), each for focus movement, Back, sidebar entry and exit, focus restoration, mouse and controller together, hover and focus states, screen sizes, mapping, and unsupported controllers.

**Work record**

- **Investigation (audit):** PHASE-2-PLAN.md, Stage A. Reflux already had a shared input architecture: `core/input`'s semantic actions and modality; `AppState.dispatch` as the one router; `FocusEntry`, `RailFocus`, `FocusedControl`. The defects were gaps in it, not its absence:
  - "typing is active" was opt-in per screen;
  - a text field could not be activated by a controller;
  - a television relied on the system keyboard's lifecycle;
  - the focus lift crossed titles;
  - a page left by anything but opening an item forgot where focus was;
  - a press that arrived before a page had anything to focus fell to the toolkit's search.

  Every screen and dialog is listed in the audit table.
- **Decision:** extend the architecture rather than rewrite it. The interaction contract (event priority, one way to report typing, text entry by input, focus movement, restoration, Back, pointer/touch/controller together, hints) is PHASE-2-PLAN.md Stage B and INPUT_MODEL.md, "Event priority".
- **Implementation:**
  - `Modifier.textEntry` and `TextEditing` (`LocalTextEntry`): every field reports its own focus, is the focused control for a controller's confirm, and opens the Reflux keyboard. The per-screen `textInputActive` wiring (Search, Identify, Sources) was removed.
  - A `TEXT_ENTRY` input context with its own actions.
  - The Reflux keyboard (B-023).
  - `rememberFocusReturn` remembers the last focused item, not only the one opened (B-008).
  - A press before the page can take focus waits for its start (B-008).
  - See all belongs to its row (B-007).
  - The card lift is clear of titles and of the row above (B-009).
  - The hero is restored whole (B-013).
- **Validation slice (Home rows and cards; a nested page and its return; a dialog; a text field; Search suggestions and results; controller → pointer → controller):** `InteractionContractTest` (8), `HomeFocusTest` (5), `KeyboardSessionTest` (9), `CardLayoutTest` (4); with the existing `DirectionalInputTest`, `PointerTest`, `TelevisionTest`, `NavigationTest`, `FocusEntryTest` and `AccessibilityTest` re-run green.
- **B-042 (unassigned, not changed):** `Pressable` treats hover and focus as one active state (`active = hovered || ring`), so a pointer resting on one card lifts it while a controller focuses another. Recorded for the owner's decision.
- **Outstanding:** a physical controller walk on Windows (owner's PC; see B-002's note), Fire TV (not connected), owner acceptance.
<a id="b-026"></a>
### B-026: Windows process persists after close

- **Category:** Investigation (lifecycle) · **Priority:** Medium, provisional · **Platforms:** W
- **Components:** `desktop:app` (`Main`, `exitApplication`, the packaged launcher); `playback:mpv` (release); `ui` (`finishPlayback`)
- **Phase:** 1, step 4 · **Status:** Not Reproduced · **Last updated:** 2026-11-19
- **Related:** B-041 (shutdown is one of its measurements)

**Original description.** Reported by a tester on their PC. Reflux appeared to remain in Task Manager under Background Processes after closing, with two entries around 22 MB each. The owner could not reproduce this. *Expected:* Reflux should fully exit after closing unless an intentional, user-visible background activity is in progress. *Investigate:* window shutdown, coroutines, native media player processes, background workers, JVM processes, child processes, disposal, tray/background modes, and lifecycle cleanup. Do not label this explained or resolved without evidence; record attempts and actual observations.

**Findings document.** Correction: this was observed on the friend's PC, not the user's own PC. Friend opened Reflux, closed it, and later found Reflux listed under Task Manager's Background processes. The screenshot showed two Reflux entries, each using approximately 22 MB. User could not reproduce the behaviour on their own PC. Important: this is not a report that Reflux is classified as a background process while its window is open; the reported issue is that it appears to remain after closing. Investigate: executable paths, PIDs and parent/child relationships; whether the two entries are instances, JVM processes, or helpers; whether the window closes or hides; non-daemon threads; native libmpv resources; a tray component; particular operations or Windows configurations. Do not assume Task Manager classification alone proves a defect; establish actual process lifecycle first.

**Steps to reproduce.** Reported: open Reflux on the tester's PC, close it, later look in Task Manager. Attempted (below) on the owner's machine without success.

**Expected behaviour.** Closing the last window ends Reflux unless a designed, visible background operation is active.

**Actual behaviour.** On the tester's PC: two ~22 MB "Reflux" entries after closing (reported). On the owner's machine: none (19 attempts).

**Acceptance criteria.** The report explained by evidence (a process listing from an affected machine, or a reproduction), and fixed if it is a defect; closing during playback and with a hung engine tested.

**Work record**

- **Investigation findings:** a packaged Reflux is two processes, the launcher (5 threads) and the application (~100 threads, ~500 MB working set on an empty library) as its child; this is what Task Manager shows as two entries *while running*. Code: every Reflux thread is a daemon; children are `reg query` (2 s bound) and the folder picker; `Main` waits for `finishPlayback()` (5 s patience) then `exitApplication()`. 19 rounds (packaged, scratch `REFLUX_HOME`, WM_CLOSE to the window; 5 + 7 Phase 1 tree, 7 HEAD): the application exited 0.70–0.82 s after close, the launcher within 10 ms with code 0, nothing left. The report's two ~22 MB entries match neither a running application nor this pair as measured.
- **Root cause:** not established. **Not explained** and not resolved.
- **Implementation:** none (no evidence to justify a change).
- **Tests:** none added; measurement script (first rounds closed the launcher, which has no window; not counted).
- **Physical/device verification:** the 19 rounds, and the 5 closes during playback, ran on the owner's physical Windows machine.
- **Evidence:** VERIFICATION.md, "Phase 1", B-026 (with the tester procedure: `Get-CimInstance Win32_Process | ? ExecutablePath -like '*Reflux*' | select ProcessId,ParentProcessId,ThreadCount,CreationDate,CommandLine`, plus a support report, and whether something was playing).
- **Closing during playback (Phase 1 closeout, 2026-11-19):** the packaged app (`createDistributable` of `556887b`), a scratch library with one local film, five rounds: started, the film played (Tab, Enter; screenshot of each round with the clock running; `libmpv-2.dll` loaded in the application process), then WM_CLOSE to the window as the title bar's X sends. Before closing: the launcher (5 threads, 8 MB) and the application (160–168 threads, 595–646 MB). Every round: both processes gone 738–816 ms after the close, launcher exit code 0, nothing left. Still not reproduced; the report is still not explained.
- **Outstanding work:** a hung engine (the 5 s release patience); the tester's process listing if it recurs (procedure in VERIFICATION.md); an attempt on a second Windows machine.

<a id="b-027"></a>
### B-027: Separate Jellyfin libraries and aggregate views

- **Category:** Feature / product requirement · **Priority:** V1.1 product requirement in the findings document; moved into Beta 2 by the owner's phase plan (confirmation requested, below) · **Platforms:** all, with platform-specific navigation
- **Components (likely):** `sources:jellyfin` (user views, library IDs); `library` (library identity, aggregate queries); `ui` (`BrowseScreen`, `SourceLibraryScreen`, sidebar, mobile navigation)
- **Phase:** **V1.1 candidate** (the owner's Phase 3 work order, 2026-11-20: researched in Phase 3, not implemented) · **Status:** Not Started (researched: [V1.1-LIBRARY-RESEARCH.md](V1.1-LIBRARY-RESEARCH.md)) · **Last updated:** 2026-11-20
- **Related:** B-003 (a different problem), B-038 (filter by library), F-P1 (sidebar)

**Original description.** Support viewing separate Jellyfin libraries individually as well as aggregated Movies and Shows. For example, users may have Marvel, DC, Anime, and other libraries but still want aggregate Movies and TV views. *Expected:* aggregate Movies view; aggregate Shows view; access to each original Jellyfin library separately; appropriate library navigation and separation; desktop/TV sidebar navigation; appropriate mobile navigation; preserve original source/library identity. This was originally identified as a V1.1 candidate. Later Beta 2 scope notes include it in Phase 3. Treat this discrepancy as an owner scope decision to reconcile, not an invitation to silently change scope.

**Findings document.** Correction: this is not about genres or show taxonomy. A friend has multiple separate Jellyfin libraries, such as a Marvel library containing only Marvel movies and TV shows, alongside other libraries. Desired library model: Movies and TV Shows remain the two primary aggregated sections; media from all relevant libraries also appears in the appropriate aggregated section; each original source library remains separately accessible as its own view (Marvel movies in Movies and Marvel; Marvel shows in TV Shows and Marvel); other Jellyfin libraries are preserved similarly. Desktop/TV direction: sidebar can contain primary Movies and TV Shows plus individual source-library entries. Mobile direction: do not simply copy the desktop sidebar; reconsider the phone navigation and collection discovery experience to expose separate libraries without overcrowding the bottom navigation. Investigate: Jellyfin user views/library IDs, library content types, membership, item deduplication across aggregate and individual views, source-specific permissions, refresh and offline behaviour. Target: V1.1 candidate, not a V1.0 acceptance requirement.

**Steps to reproduce.** Not applicable (a feature).

**Expected behaviour.** As above.

**Actual behaviour.** Movies and Shows aggregate every library; each server library already has its own page under Sources (M3), but not as first-class navigation.

**Acceptance criteria.** As the expected list above, per form factor; deduplication across aggregate and library views; permissions, refresh and offline behaviour tested; walks per the matrix.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Outstanding work:** the owner's confirmation of the scope change (see [owner decisions](#unassigned-issues-and-unresolved-owner-decisions)) **before implementation**; then everything.

<a id="b-028"></a>
### B-028: Media atmosphere changes have little/no visible effect

- **Category:** Bug (UX/feature) · **Priority:** Medium, provisional · **Platforms:** all
- **Components:** `design` (`ArtworkAtmosphere.kt`); `ui` (`Artwork`, `Theme`, `DetailsScreen`, `HomeScreen`, Settings)
- **Phase:** 3, step 6 · **Status:** Fixed (automated with a perceptibility threshold; rendered; no finding in the owner's walk) · **Last updated:** 2026-11-20
- **Related:** none

**Original description.** Media atmosphere/ambience functionality appears to have little or no visible effect. *Investigate:* colour extraction, active state, animation, theme integration, rendering, cache updates, intensity, and whether changes are perceptible. *Expected:* if advertised, the feature should visibly work; effects must be intentional and tasteful, not so subtle that users cannot tell it is active; document limitations if any platform cannot support it.

**Findings document.** Tester did not notice meaningful changes when media atmosphere was expected to respond to selected media. Investigate: whether active; colour extraction and application; subtlety; updates on change; caching or rendering; platform/theme differences. Expected: if enabled and advertised, it should produce a perceptible, consistent result; otherwise communicate its purpose and limitations honestly.

**Steps to reproduce.** Reported: change the selected media with atmosphere enabled; no noticeable change.

**Expected behaviour.** A perceptible, consistent, tasteful effect, or honest communication of its limits.

**Actual behaviour.** Little or no visible effect (reported).

**Acceptance criteria.** Effect measurable (colour difference between two items' backgrounds above a set threshold) and visible on each platform; limitations documented.

**Work record**

- **Investigation:** the atmosphere was working, and invisible: the page tone was the artwork's dominant hue at lightness 0.2 and saturation 0.45, washed over the near-black page at **22 %**, which moves each channel by a few levels (a red work: about (24, 14, 14) against (10, 10, 10)). The UI test held it to a colour distance of 0.03, which passed while testers saw nothing.
- **Fix:** the wash is **60 %** (`ATMOSPHERE_STRENGTH`): a red work's page is about (49, 21, 21). The tone's lightness is unchanged, so primary text stays above 7:1 on it (`ArtworkAtmosphereTest.atmosphereIsAlwaysABackground`, `VisualSystemTest`). Still off by default, still only Home's hero and a title's page, as Settings says; the transition is the page's own colour animation, instant under reduced motion (`MotionPolicy`).
- **Tests:** `VisualSystemTest.mediaAtmosphereIsOffByDefaultAndAWashWhenOn` now requires a colour distance above 0.08 (perceptible) besides saturation and the 7:1 contrast; it passes. Renders `m4-atmosphere-off` / `-on` looked at.
- **Performance:** one sample of each artwork's pixels, cached (`ArtworkSamples`, held to one read per image by its test); the wash is a gradient drawn behind the page, not recomposed while scrolling. Nothing added.
- **Outstanding:** walks (TV, phone, tablet) per the matrix; owner acceptance.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-029"></a>
### B-029: Right analogue stick Trickplay navigation

- **Category:** Feature requirement · **Priority:** Medium (UX requirement) · **Platforms:** controller-capable (W, TV, F, controller P/T)
- **Components (likely):** `ui` (`PlayerControls`, input handling), Trickplay from B-030
- **Phase:** 3, step 4 (after B-030) · **Status:** Verified (physical: the owner's controller walk on Windows, 2026-11-20) · **Last updated:** 2026-11-20
- **Related:** B-030 (dependency), B-039 (mapping), F-003

**Original description.** Use the right analog stick for scene-based Trickplay navigation. *Expected:* the stick should navigate scene previews, not blindly seek by arbitrary time increments; integrate with preview selection and confirmation.

**Findings document.** Left joystick and D-pad both perform directional navigation. Right joystick currently does nothing. Desired: use the right analogue stick during playback for scene-based seeking through Trickplay previews.

**Steps to reproduce.** Move the right stick during playback: nothing happens.

**Expected behaviour.** Scene previews navigated; A confirms the seek; B cancels without seeking.

**Actual behaviour.** No action.

**Acceptance criteria.** BETA-2.md, "Acceptance criteria … Trickplay": right-stick navigation through preview timestamps; A confirms; B cancels without seeking; graceful when no previews exist.

**Work record**

- **Implementation:** docs/PLAYBACK.md, "Trickplay". `core/input/StickStepper` (dead zone 0.5 engage / 0.3 release, one step at once, repeats 260→70 ms by deflection, ×3 and ×8 steps after 2 and 4 s held); `PlaybackSession.rightStick` with `SceneScrub(origin, target)`: a scene is a preview picture, 10 s where there are none; playback does not move until **A** (`commitScrub`), **B** stays (`cancelScrub`), D-pad left/right fine-tune, 8 s untouched or the controller lost (`stickLost`) ends it without seeking. Only in the full player; nothing while browsing.
- **Input:** Windows reads XInput's right thumb X and the joystick API's Z axis (DualShock and other HID pads); Linux `ABS_RX` (or `ABS_Z` where the triggers are not there); Android `AXIS_Z` where it is a stick, with device removal ending the scrub. The desktop poller reports the latest value to the UI thread at most once per pass of its queue.
- **Tests:** `StickStepperTest` (6); `TrickplayUiTest`: a flick is one scene; A seeks there without pausing; B seeks nowhere and stays in the player; a lost controller and the 8 s idle end it without seeking; held, it repeats and speeds up; without previews it steps 10 s; nothing while browsing.
- **Outstanding:** walks with physical controllers (Windows: the owner's Xbox controller; Fire TV and the Pad 7 with a controller where one pairs); the Google TV emulator has no analogue stick; owner acceptance.
- **Verified, physical (2026-11-20, the owner on Windows with a controller):** a right-stick flick showed the scene preview with its time; B stayed; A went there; holding it sped up; 8 s idle cleared it; a work without previews showed the time alone. "All worked." (The "A Go here, B Stay" line was removed afterwards: B-069.)
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-030"></a>
### B-030: Trickplay previews missing

- **Category:** Feature requirement (playback UX) · **Priority:** High · **Platforms:** W L TV F P T
- **Components (likely):** `sources:jellyfin`, `sources:plex` (preview data); `ui` (`PlayerControls` timeline); `library` (cache); `playback:mpv` / Media3 (local generation, if pursued)
- **Phase:** 3, step 3 · **Status:** Verified (physical: the owner's mouse walk on Windows, 2026-11-20; Jellyfin live; Plex against the recorded format only) · **Last updated:** 2026-11-20
- **Related:** B-029, B-041

**Original description.** Playback needs scene thumbnail previews, both for mouse timeline hover and controller-driven scene navigation. *Mouse expectations:* hover over timeline positions; show the scene preview image and timestamp; update as the pointer moves; clicking/selecting the position seeks appropriately. *Controller expectations:* right stick navigates scene previews; preview selection is visible and updated; A confirms/seeks; B cancels without accidentally seeking. *Investigate:* thumbnail generation, indexing, caching, player timeline integration, sync, performance, media source capabilities, and local/remote sources. Support Jellyfin and Plex where usable preview data exists. Investigate local-file support separately. Gracefully fall back if previews are unavailable; do not break normal seeking.

**Findings document.** Moving the mouse over the playback progress/seek bar does not show preview images of scenes at corresponding timestamps. Expected mouse behaviour: hover over the seek bar; a small preview window with the scene thumbnail and the timestamp, updating as the pointer moves; seek to the chosen timestamp when selected. Expected controller behaviour: during playback, the right analogue stick moves backward and forward through scene previews shown with their timestamp in a floating window; A/select seeks there; Back/cancel exits without seeking. Source support: work with local media and supported remote sources where preview generation is possible. Fallback: if a source or media item has no Trickplay thumbnails, provide a graceful fallback rather than pretending previews are available. Note: this is a playback capability, not merely a cosmetic tooltip.

**Steps to reproduce.** Hover the seek bar during playback: no preview.

**Expected behaviour.** As above.

**Actual behaviour.** No previews.

**Acceptance criteria.** BETA-2.md, "Acceptance criteria … Trickplay": mouse hover previews with timestamp; right-stick navigation; A confirms; B cancels; thumbnails cached, never the same tile downloaded twice; Jellyfin and Plex where their data exists; sources without Trickplay handled clearly; local files investigated separately, not promised.

**Work record**

- **Research:** Jellyfin (10.9+) keeps trickplay tiles per media source and width when its administrator turns on extraction (`BaseItemDto.Trickplay`, `Videos/{item}/Trickplay/{width}/{index}.jpg`); Plex keeps a BIF index per part when its owner turns on video preview thumbnails (`/library/parts/{id}/indexes/sd`). Neither is generated by Reflux: local generation would compete with playback for the decoder and is a later investigation. Recorded in docs/PLAYBACK.md, "Trickplay".
- **Live check (Jellyfin 12.4.0 in Docker, `tools/ci/jellyfin-test-server.sh`, extraction turned on through its API):** the 40-minute test film's `Trickplay` answered `{"<source>": {"320": {"Width":320,"Height":180,"TileWidth":10,"TileHeight":10,"ThumbnailCount":240,"Interval":10000}}}`; sheets 0 and 2 fetched (810,762 and 369,415 bytes, `image/jpeg`), sheet 3 404, and without the token 401. Sheet 2 looked at: pictures 200 to 239 on a 10 × 10 grid, as the mapping expects.
- **Implementation:** `core/playback/Trickplay.kt` (`TrickplayManifest`, the frame mapping, `TrickplaySource`, `BifIndex`); `JellyfinSource` and `PlexSource` implement it; `ui/state/Trickplay.kt` (`TrickplayPreviews`: only the newest place loaded, older loads cancelled, a 60 ms settle before a new sheet, sheets kept encoded across playbacks in `TrickplaySheets` (32 MB) so none is downloaded twice while it fits, three decoded at most, background dispatcher); the timeline's floating preview (`TimelinePreview`) for the pointer, a dragged thumb, held seek keys and the right stick, the time alone where there are no previews.
- **Tests:** `TrickplayTest` (6: mapping, clamping, malformed manifests, BIF reading, a multiplier of zero, malformed BIFs); `JellyfinTrickplayTest` (4: the width nearest 320, the account's token, one request per version, an unreachable server asked again, malformed answers); `PlexTrickplayTest` (2); `TrickplayUiTest` (hover shows the scene, sheets fetched once, looking is not seeking, no previews shows the time alone).
- **Outstanding:** Plex against a server with generated previews (none available); walks on the matrix's platforms; owner acceptance.
- **Verified, physical (2026-11-20, the owner on Windows):** the mouse over the timeline showed the scene pictures from the Jellyfin test server; a work without previews showed the time alone.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-031"></a>
### B-031: Linux libmpv fails under non-C numeric locales

- **Category:** Bug (Linux compatibility) · **Priority:** High; potentially release-blocking · **Platforms:** L
- **Components:** `playback:mpv` (`NumericLocale`, `createHandle`, `MpvPlayer`, `MpvProbe`, `MpvDecodeProbe`); `desktop:app` (`main`); `desktop:engine` (`DiagnosticLog`)
- **Phase:** 1, step 2 · **Status:** Fixed (verified in a VM desktop session and in containers; physical Linux pending) · **Last updated:** 2026-11-19
- **Related:** B-043 (the Linux install hint)

**Original description.** libmpv 0.42.0 reports an error when launched with `LC_NUMERIC=en_US.UTF-8`: `Non-C locale detected. This is not supported. Call 'setlocale(LC_NUMERIC, "C");'`. The application had a workaround using `LC_NUMERIC=C` to launch Reflux. *Expected:* establish the C numeric locale safely before libmpv initialization as required; preserve the user's system language and regional formatting for the rest of the application; avoid forcing the entire application into English or changing all user locale settings; log a meaningful error when needed without flooding logs; investigate Wayland/XWayland separately where relevant, without claiming it is the cause unless confirmed. *Investigate:* initialization order, native library calls, locale safety, Compose/JVM/native interactions, and desktop packaging.

**Findings document.** Source: friend's investigation/report supplied on 18 November; not yet independently verified by the Reflux developer. Reported environment: Linux, libmpv 0.42.0, `LC_NUMERIC=en_US.UTF-8`. Reported error: `Non-C locale detected. This is not supported. Call 'setlocale(LC_NUMERIC, "C");' in your code.` Reported behaviour: Reflux finds installed libmpv but cannot initialise it, preventing playback. Reported workaround: `LC_NUMERIC=C ~/Downloads/reflux-0.9.0-beta.1-linux-x64/bin/Reflux`. Likely root cause: the process inherits a non-C numeric locale, and libmpv initialisation does not establish the required C numeric locale first. Required: reproduce on a supported distribution; establish the C numeric locale safely before libmpv starts; verify playback without the workaround; preserve the user's language and formats; log the actual libmpv error; avoid flooding logs on repeated Play. Additional findings: the log said only that libmpv was found but could not be initialised; repeated attempts repeated the errors; Wayland/XWayland is a possible later issue, to investigate only if playback still fails or video stays black after the locale fix. The environment-variable workaround is diagnostic, not the desired product fix.

**Steps to reproduce.** Linux with any `LC_NUMERIC` other than "C" (`en_US.UTF-8` as much as `de_DE.UTF-8`): start the tarball and press Play. Reproduced in WSL2 under `de_DE.UTF-8` (22 of 24 engine tests failing on HEAD).

**Expected behaviour.** libmpv starts under the person's locale, with everything but `LC_NUMERIC` untouched; a meaningful, non-repeating log.

**Actual behaviour (before the fix).** `mpv_create()` returned NULL ("Non-C locale detected"); the log said only "libmpv was found but could not be initialized", once per Play press.

**Acceptance criteria.** libmpv starts under a non-C numeric locale without the workaround; language and formats preserved; Windows unchanged; repeated errors do not flood; verified on a physical Linux desktop under `en_US.UTF-8` and `de_DE.UTF-8`, with local and Jellyfin playback.

**Work record**

- **Investigation findings:** `mpv/client.h`: "The LC_NUMERIC locale category must be set to "C"", and `mpv_create()` returns NULL otherwise. On Linux the JVM adopts the person's locale for every category before `main()`. `setlocale(LC_NUMERIC, "C")` alone makes libmpv work with `LC_CTYPE` still the person's. An AWT window with focus on Xvfb did not set it back (no real input method available, so it is re-checked anyway).
- **Root cause:** established (the probe, and the before/after engine tests).
- **Implementation:** `NumericLocale` (`playback:mpv`): on Linux, through libc via JNA, if `LC_NUMERIC` is not "C" it is set to "C" (only that category; Java's `Locale` untouched); first thing in `main()`, and re-checked before every engine and probe (written only if something changed it); Windows untouched. Failures say why; `DiagnosticLog` counts identical repeats instead of writing them again. Commit `c1ca1d1`; DECISIONS.md, 2026-11-18.
- **Tests added or modified:** `NumericLocaleTest` (new); `DiagnosticLogTest` (3).
- **Automated verification (Windows):** `:playback:mpv:test` all passed; `NumericLocaleTest` 4 + 1 skipped (Linux-only); `DiagnosticLogTest` 3/3.
- **Desktop/OS-specific verification (VM and containers, not physical):** WSL2 Ubuntu 24.04, libmpv 0.37, `de_DE.UTF-8`: HEAD `MpvEngineTest` 22 of 24 failed; fixed tree 24/24 and `NumericLocaleTest` 3 + 1 skipped, 50 engines made and destroyed, a real file played at speed 1.5. Packaged tarball in Docker containers under `de_DE.UTF-8`: engine started on Ubuntu 22.04, Ubuntu 24.04, Debian 12, Fedora 41; Arch started its engine (image has no locale data, so the locale case was not exercised); playback through the CLI to the end on Ubuntu 22.04 and 24.04.
- **Desktop session in a VM (Phase 1 closeout, 2026-11-19; a VM result, not physical):** the Ubuntu 26.04 QA VM (`Reflux-QA-Ubuntu-26.04`: GNOME on Wayland, Reflux's window through XWayland, VMSVGA with 3D; the distribution's `libmpv2` 0.41.0). **Before:** the released `0.9.0-beta.1` tarball (SHA-256 matching the release's SHA256SUMS) under the session's own `en_US.UTF-8`: Play printed "Non-C locale detected. This is not supported. Call 'setlocale(LC_NUMERIC, "C");'" and logged "libmpv was found but could not be initialized": the tester's report, reproduced. **After:** the tarball built from `556887b`, no workaround: under `en_US.UTF-8` the film played (clock seen advancing 00:00:06 → 00:00:21), the log said "The numeric locale was en_US.UTF-8; it is C for libmpv (language and formats are unchanged)" and reported the audio output; under `de_DE.UTF-8` the same, with "was de_DE.UTF-8". The CLI's probe under `en_US.UTF-8` read the film's streams through libmpv. The version line showed the measured `1920 × 1080` (B-024's technical line).
- **Physical/device verification:** none. No physical Linux desktop is available to the agent.
- **Regression testing:** full `./gradlew check` (Windows) passed; the Windows engine tests unchanged.
- **Evidence:** VERIFICATION.md, "Phase 1", B-031 and Workstream 12; "Website documentation portal…" (Arch, the wording correction).
- **Outstanding work:** a physical Linux desktop under `en_US.UTF-8` (as reported) and `de_DE.UTF-8`, local and Jellyfin playback, ideally libmpv 0.42.0 as reported (owner check; the VM had 0.41.0). Wayland: the VM's session was Wayland with XWayland and the picture showed, so nothing points at it.

<a id="b-032"></a>
### B-032: Disconnected-source badge overlaps poster/watch indicator

- **Category:** Bug (UI) · **Priority:** Medium · **Platforms:** P T (potentially others)
- **Components (likely):** `ui` (`Artwork` card badges)
- **Phase:** 2, step 16 (with B-009) · **Status:** Fixed (automated; device walk below) · **Last updated:** 2026-11-19
- **Related:** B-009, B-005, B-033

**Original description.** The disconnected-source indicator text (e.g., "Not connected…") is truncated or overlaps the watched/checkmark indicator. *Expected:* a compact, responsive, accessible treatment; avoid collision between source connectivity state and watched state; ensure both remain understandable on narrow layouts.

**Findings document.** Evidence: multiple screenshots supplied on 18 November. The "Not connected…" source-status badge appears over poster artwork and is truncated with an ellipsis. The watched checkmark can overlap the same badge. Expected: status understandable without covering poster content; watched state distinguishable; indicators never collide; readable at phone and tablet sizes. Investigate: badge placement, width constraints, priority, icon positioning, responsive card layouts and whether the status should be a compact icon or another treatment. Design direction: avoid long, truncated toast-like labels over posters; consider a compact, consistent status treatment with accessible explanation.

**Steps to reproduce.** Disconnect a source on Android; look at its posters: a truncated "Not connected…" badge, overlapped by the watched checkmark (reported, with screenshots).

**Expected behaviour.** Compact status and watched state that never collide.

**Actual behaviour.** Truncated badge over artwork; checkmark overlap (reported).

**Acceptance criteria.** Badge and checkmark bounds never intersect at phone and tablet sizes (layout test); accessible description; device walk.

**Work record**

- **Fix:**
  - "Not connected" is a small round mark (the offline icon) at the card's top start, and watched the same mark with a check at its top end.
  - Both are 22 dp with an 8 dp inset, so they need 60 dp, and the narrowest poster is 112 dp: they cannot meet or be cut short.
  - Not connected also dims the artwork.
  - The words are in the card's description ("…, not connected, watched") for a screen reader, and in full on the work's page.
- **Tests:** `CardLayoutTest.aCardsStateMarksNeverMeetOnTheNarrowestCard`.
- **Device evidence before the fix:** the store build on the Phone 2a showed "Not connec…" truncated on every poster (screenshot, 2026-11-19).
- **Outstanding:** a device walk with a disconnected source; owner acceptance.
<a id="b-033"></a>
### B-033: Orphan favourites after source removal

- **Category:** Bug (data integrity/UX) · **Priority:** Medium · **Platforms:** reported Android; the library rules are shared
- **Components:** `library` (`favorites()`, availability); `ui` (`DetailsScreen` `Gone` page, Home shelf)
- **Phase:** 1, step 6 · **Status:** Verified (physical phone and tablet; owner acceptance pending) · **Last updated:** 2026-11-19
- **Related:** B-032 (disconnected-source display)

**Original description.** Removing a source can leave orphaned favourites that point to media no longer in the library. Selecting them produces a "no longer in library" type of message. *Expected:* provide a way to manage and remove orphaned favourites; distinguish temporarily disconnected sources from permanently removed sources; do not silently delete favourites without appropriate handling; preserve favourites when a source is temporarily unavailable.

**Findings document.** Evidence: screenshots supplied on 18 November. Jungle Book remains in Favorites after its original demo library/source was disconnected and removed. Selecting it opens "This is no longer in your library". Expected: a favourite whose item is no longer available should have a clear management path; the user must be able to remove the stale favourite without reconnecting the old source. Investigate: favourite persistence, source removal, item identity, tombstones/orphan records, stale UI cache and deletion semantics. Do not silently delete user favourites merely because a source is temporarily unavailable; distinguish temporary disconnection from permanent source removal and provide explicit cleanup.

**Steps to reproduce.** Favourite a title from a source; remove the source; open the favourite: "This is no longer in your library" with only Back.

**Expected behaviour.** The favourite kept and marked unavailable, removable without the source; never deleted automatically.

**Actual behaviour (before the fix).** The `Gone` page offered only Back.

**Acceptance criteria.** Favourite kept, marked, removable without the source; not returned when the source is re-added after removal; temporary disconnection keeps it playable on return; other profiles untouched.

**Work record**

- **Investigation findings:** `favorites()` resolved item rows without regard to versions; Details' model had no visible version, so the page was `Gone`. Favourites are profile state and correctly survived.
- **Root cause:** established in code.
- **Implementation:** the `Gone` page says it is a favourite (or in a collection) and offers "Remove from favorites"; favourite cards with nothing to play are marked "Not available". Nothing deleted automatically. Commit `c1ca1d1`; DECISIONS.md, 2026-11-18.
- **Tests added or modified:** `UnavailableFavoritesTest` (2, new); `ScreensTest` (1 new); at closeout, on-device `PhaseOneWalkOnDeviceTest.aFavouriteOfARemovedSourceIsKeptAndCanBeRemoved` (the reported case through the real activity).
- **Automated verification:** `:library:jvmTest --tests UnavailableFavoritesTest` 2/2; `:ui:jvmTest --tests ScreensTest` 23/23. Windows.
- **Physical/device verification (Phase 1 closeout, 2026-11-19):** the reported case on the Phone 2a and the Pad 7 (debug build beside the store build): a favourite film; its source removed the way Sources removes it (`AppState.removeSource`); still a favourite; its page read "This is no longer in your library" with "Its files were moved or deleted, or its source was removed. It stays in your favorites until you remove it…" and a "Remove from favorites" button; pressed through the accessibility tree; the favourite gone. Passed on both; screenshots of each step kept (VERIFICATION.md).
- **Emulator verification:** the same walk passed on the Google TV emulator.
- **Regression testing:** full `./gradlew check` passed.
- **Evidence:** VERIFICATION.md, "Phase 1", B-033, and "Phase 1 closeout walks".
- **Outstanding work:** owner acceptance.

<a id="b-034"></a>
### B-034: Mobile search placeholder is too long and visually heavy

- **Category:** UX (polish) · **Priority:** Low to Medium · **Platforms:** P T
- **Components (likely):** `ui` (Search field)
- **Phase:** 2, step 12 · **Status:** Fixed (automated; device walks below) · **Last updated:** 2026-11-19
- **Related:** B-035, B-036, B-037

**Original description.** The search placeholder is too long, large, and bold. *Expected:* a shorter, understated placeholder with lighter visual weight and colour; entered text must be visually distinct; prefer a short placeholder rather than marquee behaviour.

**Findings document.** Placeholder "Search titles, people, genres, or…" is too long, too large, bold and prominent. Desired: shorten it (preferred over a marquee); reduce size and weight; a faded colour; entered text visually distinct from the placeholder.

**Steps to reproduce.** Open Search on a phone.

**Expected behaviour / acceptance criteria.** A short placeholder that fits without truncation at phone width; lighter weight and colour than entered text; screenshot comparison on phone and tablet.

**Actual behaviour.** As reported.

**Work record**

- **Fix:** the placeholder is "Search your library", in the body style and the tertiary colour, smaller and lighter than typed text, which keeps the field's larger, brighter title style.
- **Tests:** `ScreensTest` (Search screenshots); on device, `PhaseTwoWalkOnDeviceTest` (the short placeholder shown).
- **Outstanding:** owner acceptance.
<a id="b-035"></a>
### B-035: Mobile search chips are oversized and contain unnecessary "Try" label

- **Category:** UX (polish) · **Priority:** Low to Medium · **Platforms:** P T
- **Components (likely):** `ui` (Search suggestion chips)
- **Phase:** 2, step 13 · **Status:** Fixed (automated; device walks below) · **Last updated:** 2026-11-19
- **Related:** B-010, B-034

**Original description.** Search chips are too large, and "Try" is unnecessary. *Expected:* remove "Try", reduce chip dimensions/padding, and use an edge-to-edge scrollable row on mobile while retaining accessibility and touch usability.

**Findings document.** Suggested query chips are large, and the row begins with an unnecessary "Try" label. Desired: remove "Try"; smaller chips; on mobile, an edge-to-edge chip row; comfortable touch targets and horizontal scrolling kept.

**Steps to reproduce.** Open Search on a phone.

**Expected behaviour / acceptance criteria.** No "Try"; smaller chips with touch targets at least 48 dp; an edge-to-edge scrolling row on phones; UI test and screenshots.

**Actual behaviour.** As reported.

**Work record**

- **Fix:**
  - No "Try".
  - Suggestions are compact chips: caption-strong text, 14 dp padding, a hairline edge. Each is at least the layout's minimum target tall, so 48 dp on touch.
  - The chips are on a `LazyRow` that runs edge to edge (it bleeds through the page's margins and starts in line with them), 12 dp apart.
- **Tests:** `ScreensTest.searchSuggestsAndAdmitsWhenNothingMatches` (no "Try"); `CardLayoutTest.suggestionChipsKeepTheirRingsApartAndScrollWholeIntoView`; on device, chip height ≥ 48 dp.
- **Outstanding:** owner acceptance.
<a id="b-036"></a>
### B-036: Mobile search/IME lifecycle breaks navigation

- **Category:** Bug (mobile UI/navigation) · **Priority:** Medium · **Platforms:** P T
- **Components (likely):** `ui` (Search, navigation scaffold, `CompactNavigation`); `android:app` (window insets, `adjustResize`)
- **Phase:** 2, step 11 · **Status:** Fixed (automated; device and emulator walks below) · **Last updated:** 2026-11-19
- **Related:** B-034, B-035, B-037, B-025

**Original description.** Search interaction can cause vertical shifts, missing back controls, and disappearing bottom navigation after the keyboard is dismissed. *Expected:* stable search layout; predictable keyboard resize/insets; clear back-button behaviour; correct distinction between dismissing the IME and leaving Search; bottom navigation remains correct after keyboard dismissal; query is preserved where appropriate.

**Findings document.** Entering Search shifts the UI as the keyboard appears; no obvious in-app back button; after dismissing the keyboard the bottom navigation bar disappears; recovery needs the Android back gesture. Expected: Search a stable destination; predictable resize; navigation available (or an intentional replacement) with the keyboard hidden; Back tells dismissing the keyboard from leaving Search; no layout jumps or lost query. Investigate: Compose WindowInsets, IME animation, `adjustResize`, navigation scaffold visibility and focus state.

**Steps to reproduce.** On a phone: open Search (the UI shifts); dismiss the keyboard (the bottom bar disappears); only the system back gesture recovers.

**Expected behaviour.** As above.

**Actual behaviour.** As reported.

**Acceptance criteria.** Each expectation above checked on the Phone 2a and Pad 7 (physical), with the query kept; instrumented or UI tests where feasible.

**Work record**

- **Cause:**
  - `CompactNavigation` hid the bottom bar for as long as a field had focus (`!textInputActive`), not for as long as the keyboard was up. Putting the keyboard away with the system's Back left the field focused and the bar gone, so only the system's Back gesture recovered.
  - The next Back then only let go of the field (the desktop rule), which looked like nothing to the viewer.
  - Search is a section, so it has no Back arrow of its own.
- **Fix:**
  - The Android shell reports whether the system keyboard is up (`WindowInsets.isImeVisible` → `AppState.systemKeyboardShown`). The bar, and the mini-player's row, step aside only while it is up.
  - On a touchscreen the system keyboard takes the first Back, so the next Back navigates (and lets go of the field) instead of only letting go.
  - The query lives in `AppState` and is kept.
  - Layout: the page keeps the window's layout, and only the keyboard's inset is given to pages (unchanged since 2026-11-16).
- **Tests:** on device, `PhaseTwoWalkOnDeviceTest.searchKeepsItsNavigationAndBackTellsTheKeyboardFromLeaving`; JVM `HandheldLayoutTest` (unchanged, green).
- **Outstanding:** the Phone 2a walk (it was unplugged during this phase's walks); owner acceptance.
<a id="b-037"></a>
### B-037: Search has no designed empty state

- **Category:** UX · **Priority:** Low to Medium · **Platforms:** all, especially mobile
- **Components (likely):** `ui` (Search, and the Movies/Shows/filter empty states)
- **Phase:** 2, step 14 · **Status:** Fixed (automated; device walks below) · **Last updated:** 2026-11-19
- **Related:** B-034 to B-036, B-038

**Original description.** Search returns a plain text empty state without a designed experience. *Expected:* a deliberate, lightweight empty state with appropriate messaging and a useful next action without cluttering the interface.

**Findings document.** No-results state is plain text ("Nothing in your library matches 'abc'.") with a large empty area. Expected: a deliberate empty state: a visual, a concise explanation, a useful next action; consistent across Search, Movies, Shows, filters, and source states; lightweight.

**Steps to reproduce.** Search for "abc" in a library with no match.

**Expected behaviour / acceptance criteria.** A designed empty state with a next action, consistent across Search, Movies, Shows, filters and source states; UI test.

**Actual behaviour.** Plain text, large empty area.

**Work record**

- **Fix:** `NoResults`, a light, deliberate empty state:
  - a search mark in a soft circle;
  - "No matches for “…”";
  - "Try fewer or different words, or ask for something like “unfinished shows”.";
  - two quiet actions: Clear search (back to the field) and Browse movies.

  Movies, Shows and filters have their own empty states, which B-038 (Phase 3) revisits.
- **Tests:** `ScreensTest.searchSuggestsAndAdmitsWhenNothingMatches`; on device, `PhaseTwoWalkOnDeviceTest`.
- **Outstanding:** owner acceptance.
<a id="b-038"></a>
### B-038: Movies and Shows sorting/filtering

- **Category:** Feature / product requirement · **Priority:** Medium · **Platforms:** all
- **Components (likely):** `library` (queries, indexes); `ui` (`BrowseScreen`, settings persistence)
- **Phase:** **V1.1 candidate** (the owner's Phase 3 work order, 2026-11-20: researched in Phase 3, not implemented) · **Status:** Not Started (researched: [V1.1-LIBRARY-RESEARCH.md](V1.1-LIBRARY-RESEARCH.md)) · **Last updated:** 2026-11-20
- **Related:** B-027 (filter by library), B-037, B-041 (large libraries)

**Original description.** Add meaningful sorting and filtering for Movies and Shows. *Expected sorting* includes appropriate supported fields such as title, year, date added, rating, runtime. *Expected filtering* includes supported fields such as genre, year, watched/unwatched, favourites, resolution, source, library. *Requirements:* only show options supported by available metadata; Movies and Shows should have consistent, sensible behaviour; filters must be clearable/resettable; persist state appropriately; avoid unnecessary full reloads and expensive work for every filter change; test with realistic library sizes.

**Findings document.** Expected: a discoverable sort/filter entry point; sorting by title, release year, date added, rating, runtime, and other supported metadata; filters by genre, year, watched, favourites, resolution, source/library, and other meaningful fields; no unsupported or misleading filters; predictable state; obvious clearing. Investigate: existing query capabilities, metadata completeness, cross-platform UI and large-library performance.

**Steps to reproduce.** Not applicable (a feature).

**Expected behaviour.** As above.

**Actual behaviour.** No sort or filter controls in Movies and Shows.

**Acceptance criteria.** BETA-2.md, "Acceptance criteria … Sorting and filtering", and the owner's confirmed requirements: only options supported by actual data; consistent Movies and Shows behaviour; persistent state where appropriate; clear reset; no expensive repeated reloads; tested on large libraries (the 11,000-item fixture at least).

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Outstanding work:** everything.

<a id="b-039"></a>
### B-039: Controller mapping design

- **Category:** Product decision / UX investigation · **Priority:** Medium (UX requirement) · **Platforms:** controller-capable
- **Components (likely):** `ui` (input handling, hints); docs/INPUT_MODEL.md
- **Phase:** 2, step 8 (with B-012) · **Status:** Fixed (mapping decided, documented, conflict-checked; hints) · **Last updated:** 2026-11-19
- **Related:** B-012, F-005, F-P2 (Y toggles the sidebar), B-029 (right stick), B-025

**Original description.** Review the current mapping. A selects, B backs, Y opens Sources, X is unused, and LB/RB/LT/RT are largely unused. *Expected investigation:* evaluate Y opening the sidebar from anywhere in the application; find a useful, distinct action for X; consider LB/RB for section/page navigation; consider LT/RT for contextual actions or playback; avoid conflicts with player controls, dialogs, text input, and platform conventions; provide controller hints; consider custom mapping as a future feature. Do not implement arbitrary mapping changes without evaluating conflicts and documenting the decision.

**Findings document.** Current: A selects; B goes back; Y opens Sources; X does nothing; LB/RB and LT/RT have no clearly useful behaviour. Proposed direction: Y opens the sidebar immediately from anywhere; X a distinct useful action; LB/RB section or page navigation; LT/RT playback or consistent contextual actions. Requirements: no conflicts with platform conventions, remotes, or player shortcuts; discoverable hints; consider configurable mappings. Status (original): proposed interaction design, not an approved mapping specification.

**Steps to reproduce.** Not applicable (a design review).

**Expected behaviour.** A conflict-checked, documented mapping with hints.

**Actual behaviour.** As "Current" above.

**Acceptance criteria.** A conflict analysis (player, dialogs, text input, remotes, platform conventions) and the chosen mapping recorded in DECISIONS.md and INPUT_MODEL.md; hints; tests per mapped action; walks on Windows, TV and Fire TV.

**Work record**

- **Decision:** INPUT_MODEL.md, "Controller mapping", with its conflict matrix (browsing, player, Reflux keyboard, gate and panels; text input, playback, Back, remotes, platform conventions, F-P2, Phase 3).
  - A, B, Start/Menu, View and LB/RB are unchanged while browsing.
  - **X is reserved** for Phase 3's Media Quick View (the owner's decision, 2026-11-19) and is unmapped while browsing and playing.
  - **Y** ~~keeps Sources~~: the sidebar toggle (F-P2), the owner's decision on the Windows walk (2026-11-19). Start and a remote's Menu open Sources.
  - **RT** closes the mini-player while it is up (B-052); it has no other browsing meaning.
  - **LT/RT** have no browsing meaning: none that is useful without a conflict.
  - **The right stick** is reserved for B-029's Trickplay.
  - The keyboard's buttons are B-023's.
- **Hints:** `browsePrompts`, shared by the phone's bar and the page bar: Select; Back (where there is somewhere to go back to); Y Sources (where Sources is not already shown and no panel is open). X is never offered.
- **Tests:** `DirectionalInputTest.theFirstPressMorphsTheLayoutAndFocusesTheMainAction` (Triangle/Y named for Sources, Square never); `KeyboardSessionTest` (the keyboard context's buttons).
- **Outstanding:** walks with physical controllers; owner acceptance.
<a id="b-040"></a>
### B-040: Home recommendations/discovery lack variety

- **Category:** Product improvement · **Priority:** Low to Medium · **Platforms:** all (Home)
- **Components:** `library` (`Home.kt`, `Library.home()` rows, smart search); `ui` (`HomeScreen`)
- **Phase:** **V1.1 candidate** (the owner's Phase 3 work order, 2026-11-20: researched in Phase 3, not implemented) · **Status:** Not Started (researched: [V1.1-LIBRARY-RESEARCH.md](V1.1-LIBRARY-RESEARCH.md)) · **Last updated:** 2026-11-20
- **Related:** B-014, B-027

**Original description.** Improve the variety and usefulness of Home recommendations and discovery. *Potential sections* based on available metadata and watch state: because you liked…; similar content; genre-based recommendations; unwatched favourites; recently added; hidden gems; continue watching; next episode; director-based recommendations; franchise collections. *Expected:* meaningful, deterministic, and explainable selection logic where appropriate; avoid duplicate or redundant sections; avoid empty rows; avoid excessive Home scrolling; respect profile privacy and watch-state separation; do not present recommendations that lack sufficient data as if they were personalized.

**Findings document.** Investigate: deterministic sections from existing metadata and watch state (Because you liked…, similar genres, unwatched favourites, recently added, hidden gems, Continue Watching, Next Up, directors, actors, franchises), no opaque or random results. Requirements: no redundant or empty rows, no excessive scrolling; respect the active profile and its privacy boundary. Status (original): improvement request, not a confirmed defect.

**Steps to reproduce.** Not applicable (an improvement).

**Expected behaviour.** As above.

**Actual behaviour.** Home today: Continue Watching, Recently Added, Favorites, Ready to Watch, Stunning in 4K HDR, franchises, TV Shows, up to four top genres, Movies (from `Library.home()`; empty rows omitted).

**Acceptance criteria.** Each new section deterministic and explainable, with a minimum-data rule; no duplicate or empty rows; profile-separated; tests per section.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Outstanding work:** everything.

<a id="b-041"></a>
### B-041: Cross-platform performance audit

- **Category:** Performance (release readiness) · **Priority:** High (V1 readiness requirement) · **Platforms:** P, T, TV (Android TV, Google TV), F, W, L
- **Components:** all (measurement harnesses: `SearchPerformanceTest`; packaged desktop timing; `am start -W` on Android)
- **Phase:** **one issue, two checkpoints**: Phase 1 step 11 (baseline) and Phase 3 step 9 (final optimisation and comparison) · **Status:** Fixed (both checkpoints recorded: the Phase 1 baseline and Phase 3's comparison, no regression where comparable; gaps named, for release verification) · **Last updated:** 2026-11-20
- **Related:** B-006, B-022, B-026 (shutdown), B-030, B-038

**Original description.** Perform a comprehensive cross-platform performance audit and establish measurable baselines and optimization criteria. *Platforms:* Android phone, Android tablet, Android TV, Google TV, Fire TV, Windows, Linux. *Audit areas:* cold startup; warm startup; first usable UI; content loading; library scanning; search; artwork and cache performance; memory use; scrolling/frame pacing; UI transitions; playback startup and transitions; CPU/GPU use; battery where applicable; shutdown; long sessions. *Test conditions:* release builds; physical devices where available; desktop operating systems; different densities and window sizes; realistic/large libraries; connected and disconnected sources. *Expected:* document measured baselines; identify bottlenecks supported by measurements; prioritize optimizations based on evidence; establish sensible regression thresholds. *Phase allocation:* baseline measurements belong in Phase 1; final optimization and repeat measurements belong in Phase 3. This is a single issue with separate checkpoints, not two duplicated issues.

**Findings document.** Areas to measure: cold and warm startup; first usable screen; first library content; large-library scan and metadata; search latency; artwork loading, caching, memory; scrolling and frame pacing; navigation transitions; playback startup, buffering, seeking, recovery; CPU, memory, GPU, battery; background work and shutdown; long-session stability and memory growth. Test matrix: physical devices, representative desktop hardware, densities and resolutions, large libraries, connected and disconnected sources. Expected output: measured baseline, bottleneck evidence, platform findings, regression thresholds. Do not optimise on assumptions or one device. Compare release builds, not only debug builds.

**Checkpoint 1: baseline (Phase 1). Status: recorded, with the gaps named below** (every platform has numbers: Windows, Android phone, tablet and Fire TV physical, Google TV on the emulator, Linux in a VM).

| Area | Platform | Measured | Kind |
| --- | --- | --- | --- |
| Start to window (packaged, empty library, 7 runs) | Windows | HEAD median 2.7 s, Phase 1 tree 2.6 s; first cold run 5.0–5.2 s | Physical (development machine) |
| Working set at 6 s | Windows | median 492 MB (HEAD), 500 MB (Phase 1) | Physical |
| Close to both processes gone | Windows | 0.71–0.80 s (HEAD), 0.71–0.82 s (Phase 1) | Physical |
| Release cold start (`am start -W -S`, store build, 7 runs) | Phone 2a | median 347 ms, p95 1,283 ms (one outlier; others 313–412) | Physical |
| | Pad 7 | median 302 ms, p95 397 ms | Physical |
| | Fire TV Stick 4K Plus | median 480 ms, p95 517 ms (6 of 7 runs reported) | Physical |
| | Google TV (`0.9.0-alpha.2` under the store ID) | median 238 ms, p95 419 ms | **Emulator** |
| Local search (11,000-item fixture) | JVM on Windows | see [B-006](#b-006) | Physical (JVM) |
| Start to window (packaged, empty library, 7 runs; run 1 after a page-cache drop) | Linux (Ubuntu 26.04 QA VM, GNOME/XWayland, 4 vCPU, 8 GB) | cold 6.45 s; warm 2.84–3.35 s; median of 7: 2.90 s | **VM** |
| Resident memory at 6 s (one process on Linux) | Linux VM | warm 676–721 MB (cold run 346 MB, its window just up) | **VM** |
| Close (WM_DELETE) to process gone | Linux VM | 454–709 ms, one run 2,046 ms; median 591 ms | **VM** |
| Memory after a cold start (store build, `dumpsys meminfo` TOTAL PSS at 6 s, 3 runs) | Phone 2a | 300, 323, 367 MB | Physical |
| | Pad 7 | 273, 282, 326 MB | Physical |
| Frame pacing scrolling Home (store build, `dumpsys gfxinfo`, 16 swipes, 3 runs) | Phone 2a | 2,780–2,824 frames; janky 0.04–0.43 %; p50 10 ms, p90 14–17 ms, p95 21 ms, p99 40–46 ms | Physical |
| | Pad 7 | 1,260–1,352 frames; janky 0.16–0.22 %; p50 13 ms, p90 18–22 ms, p95 30 ms, p99 36–38 ms | Physical |
| Close during playback to both processes gone (5 rounds) | Windows | 738–816 ms (B-026) | Physical |

The store build is `0.9.0-beta.1` (release), with the owner's own library as installed (its size was not read); the frame figures are Home only. Not measured yet: an Android release build of the Phase 1 code (none can be installed beside the store build without replacing it), Linux on hardware, scrolling frame times beyond Home and on TV, Android CPU, GPU, battery, large-library scans on devices, artwork, playback start and transitions, long sessions; regression thresholds not set. Macrobenchmark recommended for M12, at its cost (a module, an AndroidX dependency, a benchmark build type).

**Checkpoint 2: final optimisation and comparison (Phase 3). Status: recorded where a like-for-like comparison is possible; gaps named.**

| Area | Platform | Phase 1 | Phase 3 (2026-11-20) | Kind |
| --- | --- | --- | --- | --- |
| Start to window (packaged, empty library, 7 runs) | Windows | median 2.6 to 2.7 s; cold 5.0 to 5.2 s | median 2.23 s; cold 5.09 s | Physical (development machine) |
| Working set at 6 s | Windows | 492 to 500 MB | 446 to 490 MB | Physical |
| Close to processes gone | Windows | 0.71 to 0.82 s | 0.68 to 0.71 s | Physical |
| Start-up breakdown | Windows | none | environment 0.78 s; first composition to window 0.70 s; window to first frame 0.31 s | Physical |
| Local search, 11,000 items | JVM | B-006's fix | unchanged code path; `SearchPerformanceTest` in the full check | Physical (JVM) |
| Trickplay | Jellyfin 12.4.0 (Docker), JVM | none | a version described in one request; sheets 0.37 to 0.81 MB each, fetched once and kept | Container server |

- **Rendering cost of Phase 3's features:**
  - The keyboard's morph places about 60 keys from animated values read at placement (no recomposition per frame).
  - Trickplay decodes at most three sheets, off the UI thread.
  - Atmosphere samples each image once (unchanged).
  - A static composition local that would have recomposed every page on each recomposition of the root was found and fixed before commit (`LocalQuickView`).
- **Not measured again, and why:**
  - **Android cold start:** the store build on the devices is still `0.9.0-beta.1`, so measuring it again says nothing about Phase 3's code, and no release build of Phase 3's code can be installed beside it. The debug build's times are not comparable.
  - **Linux:** the QA VM was not run in Phase 3.
  - **Frame pacing, CPU, battery:** not measured.
  - Regression thresholds are not set. Macrobenchmark is still recommended for M12.
- **Conclusion:** no regression where a like-for-like comparison exists; no bottleneck established beyond the desktop start-up breakdown (B-022).

**Acceptance criteria.** Every audit area measured on the matrix's platforms with method, device, OS, build and commit, sources, cold/warm, run count, median and P95, and the physical/emulator/VM distinction; bottlenecks evidenced; optimisations prioritised by evidence; regression thresholds set; the Phase 3 run compared with this baseline.

**Work record**

- **Investigation findings:** as the table. The desktop is a launcher plus a JVM child (B-026).
- **Root cause:** not applicable yet (no bottleneck established except local search, B-006).
- **Implementation:** measurement procedures only.
- **Tests added or modified:** `SearchPerformanceTest` (B-006).
- **Evidence:** VERIFICATION.md, "Phase 1", Baselines (WS11).
- **Outstanding work:** the unmeasured areas above; thresholds; then Phase 3's checkpoint.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-042"></a>
### B-042: Controller focus and mouse hover conflict

- **Category:** Bug (UI/controller interaction) · **Priority:** To be determined (master tracker prompt); "Major" in the master prompt that first reported it (see owner decisions) · **Platforms:** controller-capable desktop layouts (W, and L)
- **Components (likely):** `ui` (`Artwork` card focus/hover state, `Focus`)
- **Phase:** **3** (the owner's Phase 3 work order, 2026-11-20: "reverify B-042 as part of the bug sweep") · **Status:** Verified (physical: the owner's walk on Windows with a controller and a mouse, 2026-11-20) · **Last updated:** 2026-11-20
- **Related:** B-025, B-009, B-015

**Original description.** When controller focus expands a media card, moving the mouse over a different card can cause hover behaviour to interact incorrectly with the previously focused card, producing flicker or confusing expansion behaviour. *Expected:* controller focus and mouse hover must coexist predictably; moving the mouse should not cause the previous controller-focused card to incorrectly behave as hovered; focus and hover state must remain separate and correctly managed; avoid globally disabling hover as a simplistic fix. *Recommended phase:* Phase 2, alongside B-025 shared focus architecture. This is a recommendation, not yet an owner-confirmed allocation; record it as pending approval.

**Master prompt (2026-11-19), as first registered in BETA-2.md.** Severity: Major. When browsing movie cards with a controller, the focused card expands into its highlighted state. If the pointer rests near the edge of another card when controller focus moves, pointer hover can activate the previously focused card, competing with controller focus; the visual state changes repeatedly and flickers. Expected: controller focus and pointer hover never fight over a card's state; moving focus never makes the previous card flicker; mouse hover works normally when the mouse is in use; switching between mouse and controller is predictable; controller navigation and focus accessibility preserved. Test controller-only use and simultaneous mouse and controller input. Investigate the interaction architecture; do not globally disable hover.

**Steps to reproduce.** On Windows: browse cards with a controller with the pointer resting near the edge of another card; move focus: the previous card flickers between states (reported).

**Expected behaviour.** As above.

**Actual behaviour.** Flicker and competing expansion (reported).

**Acceptance criteria.** Focus and hover held as separate states; no flicker in a UI test that moves focus with the pointer resting on another card; controller-only and simultaneous input walks on Windows; hover not disabled globally.

**Work record**

- **Reverification (2026-11-20):** the work order believed B-042 fixed. **It was not:** `Pressable` still computed `active = hovered || ring`, and a card's lift (`liftedBy`) still lifted on hover whatever the modality, as recorded under B-025.
- **Reproduced:** `CardLayoutTest.aRestingPointerNeverLiftsACardWhileAControllerHasFocus` rests the pointer on a card, then moves focus with the controller; with the old rule the card under the pointer stayed lifted beside the focused one (the test failed: two lifted cards in one row).
- **Root cause:** hover and focus were one "active" state, so a resting pointer was a second focus while a controller drove.
- **Fix:** `LayoutProfile.hoverShows` (true only while the modality is POINTER); `Pressable` and `liftedBy` count hover only then. Hover is not disabled: it shows again once the pointer is moved (the test checks this too). Focus rings are unchanged (`showsFocus`).
- **Tests:** the test above, failing before the fix and passing after; `CardLayoutTest` 5/5 (Windows JVM, 2026-11-20).
- **Outstanding:** a walk on Windows with the mouse and a controller together (VERIFICATION.md, "Phase 3"); owner acceptance.

## Issues: original feature findings (F-001 to F-005)

Feature proposals, kept apart from bugs. The confirmed defaults are acceptance criteria, not preferences (BETA-2.md, "Acceptance criteria the owner set"; CLAUDE.md).
- **Verified, physical (2026-11-20, the owner on Windows):**
  - With the mouse resting over another card while the controller moved, only the controller's card lifted and was outlined; the hovered card stayed flat.
  - Moving the mouse again brought hover back.
  - The owner's words: "This is correct. I like this behaviour." Kept as it is.

<a id="f-001"></a>
### F-001: Interface haptics

- **Category:** Feature · **Platforms:** Android phone and tablet with haptic capability
- **Components (likely):** a new central haptics service (`ui` / `android:app`), Settings
- **Phase:** 2, step 10 · **Status:** Fixed (automated; device walk below) · **Last updated:** 2026-11-19
- **Confirmed default:** **OFF**: the user must explicitly enable it in Settings.
- **Related:** F-002, F-003 (independent of it)

**Original description.** Add optional interface haptic feedback for actions such as focus, selection, navigation, and errors. *Requirements:* subtle, purposeful feedback; no repeated vibration tied to animation frames or continuous rendering; capability-aware behaviour; centralized haptics service/gateway; every vibration request must check the user's preference centrally to prevent feature code from bypassing the preference; no device vibration whatsoever before opt-in.

**Findings document.** Optional interface haptics (research proposal, not approved): feedback for focus movement, selection, confirmation, dialogs, navigation boundaries, errors; subtle and restrained; never on every animation frame; platform APIs with capability-aware fallbacks.

**Acceptance criteria.** Off by default; explicit opt-in in Settings; absolutely no device vibration before opt-in; one central service enforces the preference on every request and no feature can bypass it (a test that fails if code reaches the platform vibrator outside the service); capability-aware; subtle; never repeated per animation frame; controller input never triggers device vibration; settings persist and behave the same on every platform; unsupported hardware disables the option or explains why. Physical phone and tablet checks.

**Work record**

- **Implementation:**
  - **Through the same gateway:** interface haptics **off by default** (kept as `interface_haptics=on` only once turned on), for touch only. A controller never makes the device vibrate.
  - **Android:** `AndroidDeviceHaptics`, the platform's own effect on the window (`CONFIRM`, `REJECT`, `CLOCK_TICK`), so the system's touch-feedback setting applies too. Television: none.
  - **The toolkit's own haptics** (`LocalHapticFeedback`, which Compose's text selection uses) are routed into the gateway, so nothing vibrates before opt-in, even selecting text.
  - **Events:** a pull that refreshes (B-048); turning interface haptics on; a wrong PIN.
  - **Settings:** "Interface haptics", offered only where the device has a vibrator.
- **Tests:** `HapticsTest` (off by default: no vibration of any kind before opting in; only touch events; repeats limited; the source scan above).
- **Outstanding:** feeling it on the Phone 2a and the Pad 7 (physical); owner acceptance.

<a id="f-002"></a>
### F-002: Controller rumble

- **Category:** Feature · **Platforms:** supported game controllers (W, TV, F, controller P/T)
- **Components (likely):** controller input (`desktop:app` XInput, `android:app` `InputDevice` vibrator), a rumble service, Settings
- **Phase:** 2, step 9 · **Status:** Fixed (felt on Windows with a physical controller; Linux implemented and tested against a faked kernel, physical pending) · **Last updated:** 2026-11-19
- **Confirmed default:** **ON**, with a user-disableable setting. **Revised by the owner (2026-11-19, final verification brief):** controller UI haptics are required, on by default, on **Windows and Linux**; Android interface haptics stay off and opt-in, and are not the same thing. Unsupported controllers must not produce errors or unusable behaviour.
- **Related:** F-001, F-003, B-025

**Original description.** Add controller rumble feedback for suitable events. *Requirements:* detect controller rumble capability; gracefully handle unsupported devices; stop rumble immediately when controller disconnects; use appropriate intensity and duration; avoid continuous or annoying vibration; user can disable rumble; persist preference.

**Findings document.** Optional controller rumble (research proposal, not approved): compatible controllers' motors for interface confirmations and selected playback events; detect support and handle its absence gracefully.

**Acceptance criteria.** On by default; disableable globally; only on compatible controllers; capability detected; unsupported devices handled without error; stops at once on disconnect; no excessive intensity or duration; preference persisted. Physical controller checks on Windows, tablet, TV and Fire TV.

**Work record**

- **Implementation:**
  - **The gateway** (`ui/state/Haptics.kt`):
    - events `CONFIRM`, `TOGGLE`, `BOUNDARY`, `ERROR`, each with a short, light pattern and a minimum interval (a boundary at most every 450 ms);
    - rumble **on by default**, kept as `controller_rumble=off` only once it is turned off, so it persists;
    - capability checked on each request, an unsupported controller a quiet no-op, a failing platform never reaching the caller;
    - `stopRumble()` when it is turned off.
  - **Windows:** `XInputSetState` for XInput pads (`WindowsGamepads.rumble`). `GamepadInput` implements `ControllerRumble` for the controller last used, stops it after its duration from the polling thread, forgets it at once when it disconnects, and stops it on close. Joystick-API pads (DualSense, Switch Pro, generic) have no motors Reflux can drive and are not offered rumble.
  - **Linux:** ~~the joystick interface has no force feedback, so it is not offered~~ (superseded). `LinuxRumble` uses the kernel's force-feedback interface:
    - it finds the event node of the controller's joystick node in sysfs (`/sys/class/input/jsN/device/eventM`);
    - it offers rumble only where that node has `FF_RUMBLE` in `capabilities/ff` and the session may write to it (systemd's `uaccess` grants game controllers' nodes to the seat's user);
    - it uploads one `FF_RUMBLE` effect (`EVIOCSFF`), changes it in place, and plays or stops it with `EV_FF` events;
    - it closes the node, which removes the effect, on unplug, on a rescan that no longer finds it, and on close.
    - A node that cannot be opened is not asked again; no force-feedback interface at all is a quiet no-op.
  - **Android:** `AndroidControllerRumble`: the `InputDevice`'s vibrator (`VibratorManager` on 12+) for the controller last used; an input-device listener forgets one that is removed; stopped when the activity ends.
  - **Events:** a direction with nowhere to go, and on the Reflux keyboard; a wrong PIN. ~~Never moving between items.~~ Revised on the owner's walk: a light tick on each move (`NAVIGATE`, at most every 300 ms, so a held direction patters rather than buzzes), a confirmation when Select takes effect, the mini-player closed (B-052).
  - **Stop:** also when Reflux stops being the application in use (`GamepadInput`).
  - **Settings:** "Controller rumble", offered where the platform can drive motors.
- **Tests:** `HapticsTest` (on by default; off means none; repeats limited; unsupported a no-op; failures contained; stop reaches the controller); a source scan that fails if anything but the gateway's platform halves reaches a vibrator, `VibrationEffect`, `XInputSetState`, `EVIOCSFF`, or `LocalHapticFeedback.current`. `ControllerFeelTest` (2: moves tick, choices confirm, all subtle; a keyboard feels nothing; rumble off is nothing). `LinuxRumbleTest` (4: the kernel's request number; the effect and play/stop events sent; no motors, no permission, no interface; unplug).
- **Walk (Windows, physical GameSir G7 Pro, XInput, 2026-11-19), by the owner:** ticks on every move by D-pad and stick; X and Y and LT/RT give none of their own; Controller rumble off: nothing; on again: back.
- **Found by lint (full check):** the manifest lacked `VIBRATE`, so `Vibrator.vibrate` would have failed, quietly caught, on every Android device; `USE_BIOMETRIC` likewise for Forgot PIN. Both added.
- **Outstanding:** a physical Xbox controller on Windows, and a controller on the tablet and TV, to feel it and to pull the cable mid-rumble; owner acceptance.

<a id="f-003"></a>
### F-003: Video-synchronised haptics

- **Category:** Feature (experimental) · **Platforms:** supported controllers/devices
- **Components (likely):** playback audio analysis, the rumble/haptics services, player settings
- **Phase:** **none: a deferred future feature** (the owner, 2026-11-20: not part of Phase 3 or Beta 2; no release assigned; Phase 4 places it) · **Status:** Deferred (owner's decision) · **Last updated:** 2026-11-20
- **Confirmed defaults:** **OFF** by default; controller video haptics **OFF** by default; explicit opt-in required; must remain experimental and must not be enabled automatically.
- **Related:** F-001, F-002, B-029, B-030

**Original description.** Explore haptics synchronized with video content using available signal information, including audio energy, transients, frequency bands, dedicated tracks, or platform APIs. *Requirements:* volume alone must not drive constant rumble; video haptics must be independent from interface haptics; provide separate controls; intensity ceiling; rate limits/cooldowns; sensible envelopes; pause/stop/seek/error/disconnect handling; avoid unexpectedly activating when switching playback device, profile, or media; test for performance and playback impact.

**Findings document.** Optional video-synchronised haptics (experimental; not a V1 commitment): audio-derived haptics (low-frequency energy, transients), dedicated haptic tracks, or platform audio-coupled haptics; explicit opt-in, intensity limits, cooldowns, pause and mute behaviour, synchronisation, hardware detection; volume alone must not determine intensity; research Android HapticGenerator, controller vibration APIs, media haptic formats.

**Acceptance criteria.** Off by default and independent of interface haptics (enabling one never enables the other); controller video haptics off until enabled there; explicit opt-in; intensity ceiling; rate limits and cooldowns; short envelopes; pause, stop, seek, error and disconnect handled; never continuous rumble from audio volume alone; no activation on switching media, profile or output device; playback performance measured with it on.

**Work record**

- **Feasibility probe (2026-11-20, Windows, Reflux's own libmpv binding; a scratch test, not committed):** a generated file with a 60 Hz pulse for 0.25 s every 2 s (plus a quiet 1 kHz tone), played with `af=@haptics:lavfi=[lowpass=f=150,astats=metadata=1:reset=1]` and `ao=null`; `af-metadata/haptics` read every 10 ms against `time-pos`. The low band's RMS went from −62 dB to about −5 dB for each pulse, cleanly, and **every pulse was seen about 400 ms before the playback clock reached it** (pulses at 2.0 and 4.0 s seen at positions 1.60–1.92 and 3.60–3.92 s): the filter runs ahead of output by the audio buffer.
- **Conclusion:**
  - **Detection is feasible on the desktop:** low-band energy and onsets, never volume alone.
  - **Synchronisation needs the lead measured against the real output device:** WASAPI, PulseAudio/PipeWire and passthrough each add their own latency, which `ao=null` cannot show. Without that, rumble would land early by an unknown amount.
  - **Android needs a custom Media3 `AudioProcessor`** in the renderer chain: a separate piece of work. `Visualizer` needs the recording permission and is not acceptable.
  - **Passthrough (bitstreamed audio) bypasses filters:** no haptics there, and that must be said.
  - **Validation needs a controller someone can feel,** on each platform: the owner's.
- **The owner's decision (2026-11-20, Phase 3's closure):** video-synchronised haptics are deferred as a future experimental feature. They are not part of Phase 3 or Beta 2, are not to be implemented, shipped or enabled, and have no release yet. V1.2, V1.3, V1.4 and Version 2 are possible, deliberately undecided, and Phase 4's roadmap work places them ([PHASE-4-HANDOFF.md](PHASE-4-HANDOFF.md)). Before then they need their own research, design, hardware validation, synchronisation investigation, platform analysis, safety controls, and implementation and testing plan. The findings below are kept for that work.
- **Earlier reasoning (before the owner's decision):** no haptic output was shipped in Phase 3. A setting for a feature that does not exist would misrepresent it (the acceptance requires settings that reflect behaviour), and an unsynchronised rumble is the "fake implementation" the work order rules out. The owner decides whether F-003 moves to a later phase.
- **Design for when it is scheduled:**
  1. A core `HapticDirector`, pure and tested: onsets from band energy over a running floor; an intensity ceiling; envelopes of at most 150 ms; at most one event per 250 ms; a 2 s cooldown after five in a row; stop on pause, seek, end, error, disconnect, preference off.
  2. The desktop engine adds the analysis filter only while video haptics are on, and reads its metadata at 50 Hz.
  3. Each event is scheduled for `lead + measured device latency` after its detection.
  4. An Android `AudioProcessor` feeds the same director.
  5. Off by default, separate from interface haptics, and off on controllers until enabled there (BETA-2.md, the acceptance list).
- **Outstanding:** the owner's scheduling decision; then the design above; physical validation.

<a id="f-004"></a>
### F-004: Privacy-respecting analytics

- **Category:** Feature / architecture / privacy review · **Platforms:** all
- **Components:** `core` (`dev.reflux.core.telemetry`: `TelemetryEvent`, `TelemetryContext`, `TelemetryConsent`, `ConsentStore`, `TelemetryTransport`, `TelemetryRecorder`); docs/PRIVACY.md
- **Phase:** **two checkpoints**: Phase 1 step 10 (architecture, schema, privacy, consent) and Phase 3 step 10 (final validation) · **Status:** In Progress (carried beyond Phase 3 by design: the foundation is implemented and checkpoint 2 validated that nothing can be sent; no provider is integrated until the owner chooses one) · **Last updated:** 2026-11-20
- **Confirmed default:** **OFF**. No data may be transmitted until explicit informed consent.
- **Related:** B-041 (performance events would carry its measurements)

**Original description.** Investigate whether useful analytics can be implemented without compromising Reflux's privacy principles. *Requirements:* prefer local logs and explicitly user-submitted diagnostics; if aggregate analytics are implemented, require informed explicit opt-in; no advertising; no fingerprinting; no cross-app tracking; no media titles, filenames, library contents, paths, server addresses, credentials, tokens, watch history, profile names, PII, or device identifiers in transmitted events; define an event schema; document each event's purpose; define retention; provide user controls for consent and withdrawal; consider deletion where applicable; review any SDK/provider, data transmission, privacy policy, and store Data Safety/legal declarations before implementation or release; no transmission before consent; verify that turning consent off prevents subsequent transmissions.

**Findings document.** Privacy-respecting analytics and diagnostics (research and product decision required): crash reporting, performance metrics, anonymous aggregate usage, user-submitted reports, opt-in beta diagnostics, each investigated separately; start with local logs and user-submitted reports; never advertising trackers, cross-app tracking, fingerprinting, personal media information, filenames, paths, server addresses, credentials, profile names, device identifiers, or watch history; documented purpose, minimal fields, retention, clear controls, accurate disclosures; assess any SDK's dependencies, network behaviour, and Data safety implications first; never a prerequisite for using Reflux.

**Acceptance criteria.** Phase 1: schema, consent, persistence interface, transport interface, off means nothing sent, provider evaluation, documentation. Phase 3: event purposes, retention, consent and withdrawal controls, deletion where applicable, actual network behaviour validated (nothing before consent; nothing after opt-out), provider/SDK, privacy policy and store declarations reviewed and updated before any collection.

**Checkpoint 1 (Phase 1): Fixed (foundation).**

- **Implementation:** a closed schema (every field an enum, a coarse bucket, or the build's context; no free text), consent `OFF` by default, installation-wide; a recorder that drops everything without consent, keeps a bounded queue, and forgets it on opt-out; a transport interface whose only implementation in any build is `TelemetryTransport.Disabled`; no SDK, no endpoint, no Settings switch while no transport exists. Commit `c1ca1d1`; DECISIONS.md, 2026-11-18 (with the provider evaluation: Aptabase recommended for evaluation behind Reflux's own transport; Firebase, PostHog SDK, Sentry, Plausible/Umami, TelemetryDeck rejected for stated reasons); PRIVACY.md, "Statistics".
- **Tests added:** `TelemetryTest`, `TelemetrySchemaTest`.
- **Automated verification:** `:core:jvmTest --tests "dev.reflux.core.telemetry.*"` 5 + 1 passed (off by default; nothing recorded or sent; the build's transport sends nothing even with a yes and makes Settings offer nothing; a yes sends one batch, a no stops and forgets; a refused or offline batch dropped once; bounded queue; buckets; every field an enum or the build's context). Nothing in the app records or sends an event.
- **Physical/device verification:** not applicable yet (nothing is sent).

**Checkpoint 2 (Phase 3): validated (2026-11-20); nothing is sent, so the validation is that nothing can be.**
- **Code paths:** `TelemetryRecorder` is constructed only in tests; no production module records an event; the only transport in any build is `TelemetryTransport.Disabled`; Settings offers no switch (nothing to consent to). Read from the code, not the documents.
- **Dependencies:** every Gradle build file, version catalogue and Android manifest searched for analytics, crash-reporting or telemetry SDKs (Firebase, Crashlytics, Sentry, PostHog, Aptabase, Amplitude, Mixpanel, Segment, TelemetryDeck, App Center, Bugsnag): none.
- **Network, on the Google TV emulator (Phase 3's debug build, a fresh start, then 30 s on Home; every TCP and UDP socket of the app's UID read from `/proc/net` every 2.5 s):**
  - two connections to `10.0.2.2:8096`, the Jellyfin server the emulator's library was set up with: a source the user added;
  - one socket listening on `127.0.0.1`: Reflux's own loopback stream server for SMB files, which nothing outside the device can reach;
  - nothing else.
- **Consent, opt-out, withdrawal, retention:** the recorder's tests (`TelemetryTest`, `TelemetrySchemaTest`) still pass in the full check: off by default; nothing recorded or sent without consent; a no stops and forgets; a bounded queue; every field an enum or the build's context. Retention and deletion stay to be set with a provider, as no data exists.
- **Documents:** PRIVACY.md ("RSX Develops: Nothing"; the statistics foundation, with no transport and no switch), the website's privacy page (no analytics), and the code agree. No change was needed.
- **Outstanding (unchanged, the owner's):** a provider and its data contract; then the transport, the Settings switch, PRIVACY.md, the website's privacy page, and the store declarations, before anything is sent.

**Outstanding work.** The owner's provider approval and data contract; retention; the Settings switch (only with a transport); PRIVACY.md, the website's privacy page, Play's Data safety and Amazon's questionnaire updated before anything is sent; network-level validation in Phase 3.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="f-005"></a>
### F-005: Controller mapping customisation

- **Category:** Feature (future UX) · **Platforms:** controller-capable
- **Phase:** **Unassigned**: do not place in Phase 2 or 3 automatically · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-039 (its "consider custom mapping as a future feature"), B-012

**Original description.** Explore user-configurable controller mappings. *Requirements/considerations:* allow users to configure controls in a future iteration if appropriate; consider platform compatibility; persist custom mappings; reset to defaults; avoid conflicts with text input, playback, navigation, dialogs, and platform remotes; provide accessible mapping UI.

**Findings document.** Controller mapping customisation (future design proposal): configurable A/B/X/Y, bumpers, triggers, sticks; platform expectations and sensible defaults kept; no conflicts with playback or navigation contexts.

**Acceptance criteria.** To be set if the owner schedules it.

**Work record:** none. **Outstanding work:** the owner's decision.

<a id="f-p1-and-f-p2"></a>
### F-P1 and F-P2: sidebar proposals (registered, outside the original findings)

Registered in BETA-2.md from the owner's master prompt of 2026-11-19, which numbered them F-005 and F-006; those numbers clash with the findings document's F-005, so they are held as F-P1 and F-P2 until the owner numbers them. Not counted in the 47 original findings.

- **F-P1 Collapsible sidebar.** Expanded (icon and label) and collapsed (icons only); cross-platform and accessible; desktop, phone, tablet, Android TV, and Fire TV considered separately; preference persisted where appropriate; transitions tested; no one behaviour or size forced on every form factor. **Phase:** owner to confirm (Phase 3 in the master prompt; absent from the updated phase plan). **Status:** Not Started.
- **F-P2 Sidebar focus toggle with Y.** With a controller, Y moves focus to the sidebar and Y again returns it to the exact previous item, selection and scroll kept, repeated toggles not resetting state, no shortcut conflicts, an unavailable previous item handled; developed and tested with F-P1, and to be reconciled with B-039 (whose proposed Y is the same). **Phase:** 2 (the owner's decision on the Windows controller walk, 2026-11-19, confirmed again on the follow-up: "Y was supposed to be for opening up the sidebar"). **Status:** Fixed (walked on Windows with a physical controller).
  - **Implementation:** `SemanticAction.TOGGLE_NAVIGATION` for a controller's Y while browsing. `RefluxApp` remembers the item left (`RailFocus.leavingPage`) and returns to it (`enterPage`), falling back to the page's start when that item is gone. It applies where the navigation is beside the page; there is none to toggle with a phone's bottom bar.
  - **Sources:** Start, and a remote's Menu, still open Sources. Hints say "Y Sidebar". A light tick is felt on the controller.
  - **Tests:** `HomeFocusTest.yGoesToTheSidebarAndBackToExactlyWhereFocusWas` (three round trips to the same card and place; moving in the sidebar without choosing; another destination chosen); `DirectionalInputTest` hints.
  - **Walk:** the owner on Windows: "works perfectly fine".

<a id="f-p3"></a>
### F-P3: Media Quick View (the owner's Phase 3 requirement; X reserved)

- **Category:** Feature (future UX) · **Platforms:** all (controller, mouse, touch, remote)
- **How found:** the owner's Phase 3 feature planning notice, given during Phase 2 (2026-11-19; DECISIONS.md, same date, "Controller X is reserved for Media Quick View")
- **Phase:** **3** (owner's decision) · **Status:** Verified (physical: Pad 7, Fire TV, and the owner's Windows walk with a controller and a mouse, 2026-11-20) · **Last updated:** 2026-11-20
- **Related:** B-039, B-012 (X kept free in Phase 2's mapping), F-005, F-P2

**Original description (the owner's).** A controller's X is reserved as the universal contextual action for movies and TV shows, opening a new Media Quick View popup.
- It opens when a media item is focused. Otherwise X has no media-context action.
- The popup is Phase 3 scope and must not be implemented in Phase 2.
- Phase 2's mapping must not give X a conflicting global action.
- The Phase 3 feature will also open from a mouse's right-click, a touch long-press, a remote's Options/Menu, and a long press of OK/Select.

**Phase 2's part (done).**
- X is unmapped while browsing and in the player. Its only meaning is Erase on the Reflux keyboard, a text-entry context with no media item focused.
- Long press of OK, right-click and touch long-press on a card are given no other meaning.
- Recorded for Phase 3: Menu/Options currently opens Sources while browsing, and Quick View's Menu trigger on a focused item has to be reconciled with that (docs/INPUT_MODEL.md, "Controller mapping").

**Phase 3's implementation (2026-11-20).**
- **Activation:** a controller's X (`SemanticAction.CONTEXT`, BROWSE context), a pointer's right-click, a long touch, and a remote's or keyboard's Menu, on a focused film or show's card (`Pressable(onContext)`, `FocusedControl.context`, `LocalQuickView`). With nothing to show, X does nothing and Menu opens Sources.
- **Menu and Sources reconciled:** a controller's Start is never a context key and always opens Sources. A remote's Menu is the card's context on a card, and Sources elsewhere.
- **Long press of OK is not a trigger:** Reflux acts on OK when it is pressed (B-025's contract), so a long press would open the card first. Changing that contract is not a Phase 3 bug fix (DECISIONS.md, 2026-11-20).
- **The popup (`screens/QuickView.kt`):** poster, title, facts (year, runtime, rating), genres, tagline or overview; Play/Resume (focused on opening), Favorite, Download where the source and platform can keep a copy, and Details. Only supported actions are shown. Missing artwork, facts or overview leave their place out; a work gone from the library says so and offers Close.
- **Closing:** Back, X, Menu or a click outside close it, and focus returns to the card. Play and Details close it first. The page behind is inert while it is open.
- **Found on the Pad 7's walk and fixed:** the system's Back (a gesture) did not close it, because Android routes it by `AppState.backRoute`, which did not yet know Quick View and handed Back at Home to the system. `backRoute` is now the app's while Quick View is open; `QuickViewUiTest.aLongTouchOpensIt` asserts it; the walk then passed.
- **Device walks (`PhaseThreeWalkOnDeviceTest.quickViewOpensOnAFilmAndBackClosesIt`), all passed:**
  - Pad 7 (physical): a long touch, then Back;
  - Fire TV Stick 4K Plus (physical): the remote's Menu, then Back;
  - Google TV emulator: the remote's Menu on a work whose source is away, showing Favorite, Download and Details without Play;
  - phone emulator: a long touch.
- **Tests:** `QuickViewUiTest` (6): X opens it on Play, navigating and playing nothing, and B returns focus to the card; Play plays and Details opens the page; right-click opens it and a click outside closes it; a long touch opens it on a phone; a remote's Menu opens it on a TV while Start still opens Sources; X with no film or show focused does nothing.
- **Renders looked at:** `quick-view-controller`, `-phone`, `-tv`.
- **Outstanding:** walks (Fire TV remote, Windows mouse and controller, a phone's long touch); owner acceptance.
- **Verified, physical (2026-11-20, the owner on Windows):** X on a focused card, a right-click, and closing; it works. Its look was then made more solid (B-069).
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="f-p4"></a>
### F-P4: Adaptive Virtual Keyboard and Cross-Modality Layout Morphing (Phase 3)

- **Category:** Feature (input and UX) · **Platforms:** controller layout everywhere a controller or remote types (W, L, Android phone and tablet with a controller, TV, Fire TV); mouse layout on Windows and Linux desktop only; touch on Android keeps the system keyboard
- **How found:** the owner's steer (2026-11-20): research and plan now, implement in Phase 3
- **Phase:** **3** (the owner's decision) · **Status:** Fixed (automated; Fire TV physical and Google TV emulator grid walks; the owner's Windows walk found B-064 and B-065, fixed in the accepted build) · **Last updated:** 2026-11-20
- **Related:** B-023, B-051, B-054, B-004, B-011, B-025, B-057, F-P3
- **Plan (full research, design, acceptance and tests):** [F-P4-ADAPTIVE-KEYBOARD.md](F-P4-ADAPTIVE-KEYBOARD.md)

**Problem.** One controller grid serves every input. On a desk the mouse gets the same grid, which looks nothing like a keyboard. The controller grid itself was never designed against evidence, and nothing adapts when the hand changes while the keyboard is open.

**Recommendation (from the plan).**
- **Controller:** a column-aligned QWERTY with the digits on top, a right-hand action column (Erase, Space, Done), a short bottom row (Shift, &123, ←, →, Clear), horizontal wrap, and the existing button shortcuts (X erase, Y space, LB/RB cursor, LT Shift, RT symbols, Start done).
  - An alphabetic layout is not the default: there is no controller evidence that it beats QWERTY, and the industry moved from ABC to QWERTY. Not offered in F-P4 (owner's decision D1); the architecture stays open to it.
- **Mouse (W, L):** a staggered ANSI-style keyboard with a number row, the physical proportions (wide Backspace, Tab, Caps, Enter as Done, both Shifts, a 6u Space), hover, transient press, and no persistent highlight.
- **One system:** stable key identities, layouts as data, one session (text, cursor, Shift/Caps, page, chosen key), and only presentation adapting.
- **Arbitration:** explicit acts (a click, a button) switch at once; pointer travel switches only after 48 dp within 400 ms over the keyboard; 500 ms of hysteresis against incidental signals.
- **Morph:** a per-key animation of each key's rectangle, about 300 ms on a near-critically damped spring.
  - Keys in both layouts slide and resize.
  - Keys unique to one layout fade and scale out first (120 ms), and in as the others settle.
  - The container follows; reduced motion is a 150 ms crossfade.
- **State:** nothing is lost on a switch, and focus is mapped by key identity, then to the last controller key, then to the nearest key.

**Milestones.** M1 data → M2 controller layout → M3 mouse layout → M4 arbiter → M5 morph → M6 focus and state → M7 verification (the plan, §14).

**Owner's decisions (2026-11-20; the plan, §16):**
- **D1:** refined QWERTY only (no alphabetic option in F-P4; extensible).
- **D2:** library-backed title completions in scope. They never take focus; selecting fills the query and keeps the keyboard open; Done goes to the results.
- **D3:** the mouse keyboard follows the system layout (AZERTY, QWERTZ…), labels always matching what is typed, with a US fallback and a visible note.
- **D4:** dictation and phone entry excluded.
- **D5:** physical typing never hides or presses the virtual keyboard.

**Acceptance criteria and test matrix:** the plan, §12. **Risks:** §15.

**Phase 3's implementation (2026-11-20).**
- **Layouts as data** (`ui/state/KeyboardLayouts.kt`):
  - the controller's column-aligned QWERTY grid with the digits on top, the action column (Erase, Space, Done; `http://` and `.com` for an address) and the bottom row (Shift, &123, ←, →, Clear);
  - the desk keyboard: staggered ANSI with Backspace, Caps, Enter (Done), both Shifts, &123, a 6.5u Space, Clear, Hide; a symbols page that never repeats a key on the board;
  - the PIN pad, unchanged.
- **Key identity:** keys are identified by what they are (`KeyboardKey.Type` by its text), and focus is mapped by key (§9).
- **The system layout (D3, `desktop/SystemKeyboardLayout.kt`):**
  - Windows: `GetKeyboardLayout` of the focused window's thread, `MapVirtualKeyEx` and `ToUnicodeEx` with an empty state and the no-state-change flag, plain and Shift.
  - Linux: XKB (`XkbKeycodeToKeysym` in the current group), keysyms as text.
  - Fallback: US with a "US layout" note for Chinese, Japanese and Korean input methods, a layout giving nothing printable, or no display; a failure to read is logged once.
  - Dead keys type their spacing accent.
  - **Not done:** an AltGr key (third-level characters are on &123) and dead-key composition, as the plan allows.
- **Arbitration (`core/input/KeyboardModeArbiter`):** explicit acts switch at once; pointer travel switches only after 48 dp within 400 ms over the keyboard; motion is ignored for 500 ms after a switch; a stick at 0.8 or more counts as explicit. A controller press that only reveals the grid types nothing.
- **The morph (`components/RefluxKeyboard.kt`):**
  - each key is composed by identity, and its rectangle is animated on a spring (damping 0.9, stiffness 500), read at placement so nothing recomposes per frame;
  - keys of one layout only fade and scale out (120 ms) and in (180 ms after 120 ms);
  - the container follows;
  - under reduced motion, a 150 ms crossfade with nothing sliding.
- **State:** text, cursor, Shift/Caps and page are the session's and never change with the presentation.
- **Title completions (D2):**
  - from the library's titles only, read once per search keyboard; no network or metadata request;
  - case- and accent-insensitive (`TitleText.key`): prefix first, then word-prefix; at most 3 (grid) or 5 (desk), after Search's 140 ms pause;
  - never focused by appearing; Up from the top row, Tab, or a click reaches them;
  - choosing fills the query and keeps the keyboard open, and Done goes to the results.
- **Physical keyboard (D5):** off a television, a key never presses, lights or moves a virtual key, nor changes the presentation. Letters and arrows are the field's; Enter finishes (or chooses the focused completion); Escape closes (or leaves the completions); Tab moves along the completions. Before this, a physical Enter pressed the chosen virtual key.
- **Tests:**
  - `KeyboardLayoutsTest` (8): no overlap or duplicate key in any layout; grid alignment and the action column; wrap and edges; the desk's stagger and special keys; an AZERTY keymap typing what its keys show; switching keeps everything and maps focus by key; a desk-only key maps to the nearest grid key; completions never focused, reached by Up, filling the query.
  - `KeyboardModeArbiterTest` (4).
  - `AdaptiveKeyboardTest` (5): the desk keyboard for a pointer; a controller press morphs it into the grid, q travelling between its two places, nothing lost, focus from the last clicked key; a nudge does not switch and deliberate travel does; physical keys never touch the virtual keyboard; completions fill without opening anything; the US note; a TV has only the grid.
  - `KeyboardSessionTest` (2 updated to the approved grid: Done in the action column, left and right wrapping), `PointerKeyboardTest`, `InteractionContractTest`, `TelevisionTest`, `SearchDesignTest` passing.
- **Renders looked at:** `keyboard-desk`, `keyboard-desk-azerty`, `keyboard-grid-after-morph`, `keyboard-completions`.
- **Outstanding:** a Windows walk with a real AZERTY or QWERTZ layout and two DPIs (the owner's machine); the Linux VM's XKB reading; the morph measured on the Fire TV (no desk keyboard there, so the grid alone); owner acceptance.

## Unassigned issues and unresolved owner decisions

Nothing here is assigned to a phase until the owner decides; each decision, when made, goes into DECISIONS.md and this tracker's change history.

| Issue | Decision required | Recommendation | Reasoning | Options | Current allocation / status |
| --- | --- | --- | --- | --- | --- |
| [B-016](#b-016) | ~~Whether to change start-up to a profile chooser, and when~~ **Decided by the owner's Phase 3 work order (2026-11-20): open the last-used profile; Phase 3; Fixed** | Phase 2, with B-017 | It is interaction work next to B-019/B-020's profile inputs, and it changes documented design (WATCH_STATE.md), which is the owner's call | Phase 2 · Phase 3 · V1.1 · keep the current design (and close as by design) | Phase 3 · Fixed |
| [B-017](#b-017) | ~~Whether and when to add a discoverable profile switch~~ **Decided (2026-11-20): Phase 3; Fixed** | Phase 2, with B-016 | One entry point serves both | Phase 2 · Phase 3 · V1.1 | Phase 3 · Fixed |
| [B-022](#b-022) | Phase for any Windows start-up optimisation: **Phase 3 (the work order); measured, no optimisation justified yet** | Measure under B-041 (Phase 1 baseline taken), optimise in Phase 3 only if the breakdown justifies it | Start-up is one of B-041's audit areas; no bottleneck is established yet | Phase 3 under B-041 · its own Phase 2/3 item · V1.1 | Phase 3 · Investigating |
| [B-042](#b-042) | ~~Approve Phase 2 allocation~~ **Phase 3 (the work order); Fixed** | Phase 2, with B-025 | It is a focus/hover architecture question that B-025's shared model must answer | Phase 2 · Phase 3 · later | Phase 3 · Fixed |
| [F-005](#f-005) | Whether to schedule controller remapping | V1.1 | B-039 should first settle the default mapping; customisation depends on it | Phase 2 (after B-039) · Phase 3 · V1.1 · not planned | Unassigned · Not Started |
| [B-027](#b-027) | **Resolved by the work order (2026-11-20): a V1.1 candidate, researched in Phase 3, not implemented.** (Was: confirm the scope change from "V1.1 candidate" to Beta 2 Phase 3) | Confirm Phase 3 as the owner's plan of 2026-11-19 states | The owner's phase plan listed "Separate Jellyfin libraries: Included" and DECISIONS.md records it; the tracker prompt asks for confirmation before implementation | Confirm Phase 3 · return to V1.1 | Phase 3 (owner's plan) · Not Started; **not to be implemented until confirmed** |
| [F-P1, F-P2](#f-p1-and-f-p2) | Number them and place them | Number F-006, F-007; Phase 3 with B-027 (sidebar entries for libraries) or Phase 2 with B-039 for F-P2 | The sidebar's library entries (B-027) and Y's role (B-039) both touch them | Phase 2 · Phase 3 · V1.1 | Unallocated · Not Started |
| [B-018](#b-018) | "Forgot PIN" still erases a protected profile's private data without its PIN | **Decided by the owner (2026-11-19):** Keep profile / Reset profile, plus system authentication where it is secure | Implemented in Phase 2: Android phones and tablets through the system's prompt; TV, Windows, Linux keep the two choices (PHASE-2-PLAN.md) | — | Phase 1 · Fixed; decision **resolved** (DECISIONS.md, 2026-11-19) |
| [F-004](#f-004) | Statistics provider, region, data contract, retention | Aptabase through its HTTP API behind Reflux's transport (EU-hosted or self-hosted), not its SDKs | Evaluated in Phase 2 (PHASE-2-PLAN.md, "F-004"): fits; its daily IP-derived count is still processing of the address, to be disclosed | Aptabase · own endpoint · none | Foundation done; evaluated; nothing sent |
| [B-006](#b-006) | Which provider the original report meant | — | Needed to measure the right path | Ask the tester | Investigating |

**Differences between sources, recorded and not resolved by the tracker:** B-042's severity (above); B-042's phase (BETA-2.md listed it in Phase 2; the tracker prompt says pending approval, so BETA-2.md now shows it as pending); B-027's scope (above); the F-005 numbering (above); titles differ slightly between the tracker prompt and the findings document for most issues (both kept in each entry; meanings identical); the tracker prompt gives no priority for B-019 to B-021 (the findings document's "Medium" is kept).

## Newly discovered issues (B-043 onward)

Found during Beta 2 work, not in the original findings. Each was allocated by existing scope; none is a scope change.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-043"></a>
### B-043: Linux install hint names a package that the Ubuntu 22.04 base of Mint 21 and Pop!_OS 22.04 lacks

- **Category:** Bug · **Priority:** Medium (wrong instructions for a supported path) · **Platforms:** L (Linux Mint 21, Pop!_OS 22.04, other Ubuntu-based derivatives)
- **Components:** `desktop:app` (`LinuxPlaybackSetup`)
- **How found:** 2026-11-19, writing the website's Linux Mint install tab during Phase 1 closeout. The hint itself was new in Phase 1 (workstream 12, uncommitted at the time), so the defect was caught before commit and **never reached a released build**.
- **Phase:** 1 (introduced and fixed within it; part of B-031's Linux playback path) · **Status:** Fixed · **Last updated:** 2026-11-19
- **Related:** B-031

**Description.** Reflux's in-app hint for installing libmpv read the distribution's own version, so it told Linux Mint 21 (whose base is Ubuntu 22.04) and Pop!_OS 22.04 to install `libmpv2`, which does not exist on that base (`libmpv1` does).

**Steps to reproduce.** Run the tarball without libmpv on Linux Mint 21: the hint says `sudo apt install libmpv2`.

**Expected behaviour.** The hint names the package of the base release (`libmpv1` on a 22.04 base, `libmpv2` on 24.04).

**Work record**

- **Root cause:** established in code: the hint chose the package by the distribution's own `VERSION_ID` (Mint's "21"), not by its Ubuntu base.
- **Implementation:** reads the base from `UBUNTU_CODENAME` (or Debian's `VERSION_CODENAME`). Commit `c1ca1d1`.
- **Tests:** `LinuxPlaybackSetupTest` 2/2 (Mint 21 and 22, Pop!_OS 22.04, Raspbian 11 added).
- **Desktop/OS-specific verification:** the base packages verified in Ubuntu 22.04 and 24.04 containers (B-031); Mint itself not run.
- **Outstanding work:** a run on Linux Mint (VM or physical).

<a id="b-044"></a>
### B-044: `AccessibilityTest.withoutAScreenReaderTheyStillGo` timed out once under full-check load

- **Category:** Bug (test reliability) · **Priority:** Low (test intermittent; no user-facing defect shown) · **Platforms:** Windows JVM test run
- **Components:** `ui` tests (`ui/src/jvmTest/.../AccessibilityTest.kt`, line 218)
- **How found:** 2026-11-19, the forced full check (`./gradlew check --continue --rerun-tasks`) on the four committed Beta 2 commits.
- **Phase:** none needed (test reliability; carried with M12's intermittents) · **Status:** Investigating (carried: test infrastructure; cause known, not changed; see also B-074) · **Last updated:** 2026-11-20
- **Related:** the known intermittents `PointerTest.controlsLeaveWhilePlayingAndReturnWithThePointer` and `AboutWindowTest` (CLAUDE.md, "Known intermittents"); B-045

**Description.** `ComposeTimeoutException` in `await("the controls to leave") { !exists("Timeline") }` after `mainClock.advanceTimeBy(Motion.CONTROLS_AUTO_HIDE_MS * 3)`: the player controls had not left within the wait while every module was compiling and testing at once.

**Work record**

- **Investigation findings:** the test file is unchanged since `540147c`; none of the four commits touches it or the player controls. `:ui:jvmTest --tests "*AccessibilityTest*" --rerun` alone: 3/3 passed. Suspected the same timing family as `PointerTest` (a wall-clock wait under load); not confirmed.
- **Root cause:** not established.
- **Implementation:** none. Not to be "fixed" by weakening the test.
- **Later run:** the next forced full check (Phase 1 closeout, 2026-11-19) passed it: 1,496 tests, 0 failures.
- **Root cause (2026-11-20, from the code):** the player's chrome waits with `delay` (the test's virtual clock) but reads the time from `UiClock` (wall-clock monotonic time). After `mainClock.advanceTimeBy`, the chrome wakes, finds by the wall clock that its time has not come, and waits again, so the controls leave only once real seconds pass; under a full-check load the virtual clock advances slowly enough for the 30-second wait to run out.
- **Recommended fix (not applied):** let tests drive `UiClock` from the Compose test clock. It changes the time base of every UI test that touches the player, so it is test-infrastructure work for M12's intermittents, not a Phase 3 bug fix.
- **Outstanding work:** that change, then re-running the known intermittents (`PointerTest`, this one) under load.

<a id="b-045"></a>
### B-045: `AboutWindowTest` fails on unmodified HEAD (a focus stop added after the test was written)

- **Category:** Bug (test defect) · **Priority:** Low · **Platforms:** Windows (real-window tests, `-Preflux.windowTests=true`)
- **Components:** `desktop:app` tests (`AboutWindowTest`)
- **How found:** 2026-11-19, during the GPL transition's About-page checks.
- **Phase:** none needed (test defect; picked up in Phase 3's bug sweep) · **Status:** Fixed (test updated; the window run is recorded in VERIFICATION.md, "Phase 3") · **Last updated:** 2026-11-20
- **Related:** B-044

**Description.** On unmodified HEAD the test failed 2 of 2 runs at the arrow to Reflux's terms row, which lands on Privacy, a stop added after the test was written; on the GPL branch 1 of 2 runs passed the whole flow and 1 failed at the same step.

**Work record:** recorded in VERIFICATION.md, "Open-source transition".
- **Cause (2026-11-20):** the test pressed Down twice from the build's stop, expecting Reflux's terms; the page now has a Privacy stop, and a provider's credits stop before it when a provider is configured, so two presses land on Privacy or the credits.
- **Fix:** the test moves a stop at a time, waiting for each press, until "Copyright and license" (at most three stops), and asserts that Privacy is the stop just before it. What it checks is unchanged or stronger.
- **Outstanding:** the window run (VERIFICATION.md, "Phase 3").

<a id="b-046"></a>
### B-046: Redundant Profile navigation button on the Profile destination

- **Category:** Bug (navigation UX) · **Priority:** Low · **Platforms:** P (phone, upright; the bottom-bar layout)
- **Components:** `ui` (`RefluxApp.CompactNavigation`, `ProfileButton`)
- **How found:** the owner's Phase 2 brief (2026-11-19), where it was numbered B-043. Renumbered because B-043 was taken (DECISIONS.md, 2026-11-19, "Phase 2 authorised").
- **Phase:** 2 (owner's brief, section 7) · **Status:** Fixed (automated; device and emulator walks below) · **Last updated:** 2026-11-19
- **Related:** B-047, B-017

**Original description.** On mobile, the top-right Profile icon opens the Profile page, which holds profile management, Downloads, Sources and Settings. On the Profile destination itself, the same icon stays visible in the top right and still navigates to the current destination, which is redundant.
- *Investigate:* the shared header and scaffold; destination awareness; navigation state; selected destination; Back; whether similar self-navigation controls exist elsewhere; phone and tablet differences; controller focus and accessibility.
- *Fix:* hide, remove or transform the control on its own destination, without awkward alignment or layout jumps, in the shared navigation model, without redesigning unrelated navigation.
- *Tests:* the icon opens Profile; on Profile it is not shown, or acts appropriately; navigation from Profile into nested screens and back works; header alignment holds; other destinations are unaffected; phone and tablet tested.

**Investigation.** `CompactNavigation` drew `ProfileButton` on every section's page except Search, Profiles included.
- **Phone, upright** (`BOTTOM_BAR`): the profile entry is that button.
- **Phone on its side** (`COMPACT_RAIL`), **tablet**, **desktop** and **television**: the profile entry is the last item of the rail or sidebar, marked as the current destination, not a self-link in a header. No other header control navigates to its own page; Search already hid it.
- **Downloads, Sources and Settings** are sections reached from Profiles on a phone. There the button is the way back to Profiles, lit as where the viewer is, so it is kept.
- **Focus:** it is reachable only on a phone (touch, or a controller on a phone). Without it on Profiles, focus starts on the first profile as before.

**Implementation.** `showsProfileButton(destination)` is one rule for the top row: a section, not Search, not Profiles. The button floats in a row of its own, so hiding it moves nothing else.

**Tests / verification:** `PhaseTwoWalkOnDeviceTest.profilesHasNoButtonToItselfAndHomeHasOne` (Home has it; Profiles does not; Settings has it as the way back); the JVM suites green. Phone and tablet walks: VERIFICATION.md, "Phase 2". Owner acceptance pending.

<a id="b-047"></a>
### B-047: Unexplained dot beside the Profile icon

- **Category:** Bug (UI clarity) · **Priority:** Low · **Platforms:** P (reported); every layout with the marks
- **Components:** `ui` (`RefluxApp.ActivityIndicator` compact form; `ProfileButton` and the navigation's downloading mark)
- **How found:** the owner's Phase 2 brief (2026-11-19), where it was numbered B-044. Renumbered because B-044 was taken.
- **Phase:** 2 (owner's brief, section 7) · **Status:** Fixed (both of Reflux's marks now say what they are; which dot the owner saw to be confirmed) · **Last updated:** 2026-11-19
- **Related:** B-005 (the compact activity sign), B-046

**Original description.** A small, persistent dot sits next to the Profile button, on Home and seemingly every screen.
- *Investigate:* identify exactly what it is, through the shared header, state, notification and status systems, profile state, debugging UI, placeholders, theme and decorative code. Do not assume.
- *Fix:* if intentional, make it meaningful and show it only when its state warrants; if obsolete or meaningless, remove it; never remove it before its source is understood.
- *Tests:* every relevant screen; phone, tablet, desktop and TV; light and dark themes where they apply; no accidental indicator left.

**Investigation (code and device).** Two things in Reflux draw a dot there, and a third dot sits just above it on the owner's phone.

1. **The library-activity sign, compact.** A 6 dp grey dot to the left of the Profile button on a phone's top row (and on a television's bar). It shows only while the library pipeline is busy (`PipelineActivity` not `Idle`: scanning, reading media details, finding titles and details, fetching artwork). It came from Phase 1's B-005 fix, which cut the sign's words where the page's heading starts beside it. So it is **not in 0.9.0-beta.1**. It appears in the Phase 1 debug build installed beside the store build for the closeout walks, whose 400-file walk library kept it busy for a while.
2. **The downloading mark.** A 7 dp white dot at the top-right corner of the Profile button (and of the rail's Downloads and Profile items). It shows while any visible download is queued, preparing or downloading. It **is in beta.1**.
   - A download waiting for its source is `FAILED` with `SOURCE_UNAVAILABLE`, not queued, so a disconnected source does not keep it lit.
   - It is lit only while there is real work.
3. **Android's status bar.** On the owner's Phone 2a it shows its own "more notifications" dot at the far right, directly above Reflux's Profile button, on every screen of every app. Reflux does not draw it.

**Device evidence, 2026-11-19.** A read-only screenshot of the store build (0.9.0-beta.1) on Home, every source "Not connected". It showed neither Reflux dot at that moment (no downloads, no activity), and the status bar's dot above the button.

**Which dot the owner saw is not established.** "Persistent… on every screen" fits the status bar's dot best. In a Reflux build, it fits the activity sign during a long refresh.

**Fix (both of Reflux's marks are intentional; each now says what it is).**
- **The compact activity sign** is a small turning arc, the common "work in progress" sign. It still has its full words for a screen reader, says them on Sources, and is shown only while there is activity.
- **The downloading mark** is a download arrow in a small lit badge, named "…, downloading" to a screen reader, and shown only while a download is active.
- Nothing is removed.
- **Owner check:** a screenshot of the dot, if it is still seen, would settle which one it is.

**Tests / verification:** JVM suites green (`ActivityIndicatorLayoutTest`, B-005's, still holds with the arc). The arc, and the badge on a download, are looked at in the walks' screenshots (VERIFICATION.md, "Phase 2"). Owner check: a screenshot if a dot is still seen.

<a id="b-048"></a>
### B-048: Touchscreen pull-to-refresh for library changes

- **Category:** Feature (touch UX) · **Priority:** Medium · **Platforms:** P T (touch)
- **Components:** `ui` (`components/PullToRefresh`, `HomeScreen`, `BrowseScreen.PosterGrid`, `AppState.pullToRefresh`); `library` (`LibraryPipeline.refresh`, unchanged)
- **How found:** the owner's Phase 2 brief (2026-11-19), where it was numbered B-045. Renumbered because B-045 was taken.
- **Phase:** 2 (owner's brief, section 7) · **Status:** Fixed (automated; device and emulator walks below) · **Last updated:** 2026-11-19
- **Related:** B-005 (the activity sign), B-041

**Original description.** A pull-down refresh gesture at the top of the scrollable content on Home, Movies and TV Shows, to check for library changes and refresh what is shown.
- *Expected:* it triggers the existing refresh/synchronisation pipeline, and:
  - detects added and removed media, and reconciles changes;
  - updates the listings and Home's sections;
  - handles connected, disconnected and multiple sources;
  - keeps cached content during errors;
  - does not redownload metadata or artwork unnecessarily;
  - avoids concurrent refreshes;
  - keeps the indicator's lifecycle correct and reports failure gracefully;
  - does not disrupt ordinary scrolling, and works on touch.

  It is a library change check, not an indiscriminate metadata refresh.
- *Implementation:* reuse the existing pipeline; the Compose approach that fits the project's Material/Compose versions.
- *Tests:* Home, Movies and Shows; added and removed media; multiple and disconnected sources; cache kept on failure; no storms; ordinary scrolling unaffected; only at the top, not mid-scroll; the indicator resets after success and after failure.

**Investigation.** `AppState.refresh()` runs `LibraryPipeline.refresh(null)`, the same path as Sources' refresh, the automatic availability checks and a watched folder's changes:
- every source is scanned, then probing, metadata and artwork run only for what is new or due;
- an unreachable source keeps its works (its availability is recorded, nothing deleted);
- calls are serialised, and one already waiting its turn is joined.

The project draws its own components on Compose Foundation and depends on no Material library, whose pull-to-refresh lives there. A Material dependency for one gesture was judged not worth it.

**Implementation.**
- **The gesture.** `PullToRefresh`, built on nested scrolling:
  - only a finger's drag that the list leaves over at its top pulls;
  - a fling reaching the top does not pull, a pointer's wheel never does, and pushing back retracts it;
  - the pull is resisted, its threshold is 72 dp, and the sign turns while refreshing and goes when the work is done, success or failure.
- **The refresh.** `AppState.pullToRefresh()`: one at a time (a pull while one runs starts nothing), through `refresh()`, which already reports a failure ("The library couldn't be updated") and leaves everything shown as it was.
- **Where.** Home and the Movies and Shows pages; not Search, listings or a source's own page.
- **Feedback.** A light confirmation tap through the haptics gateway, only if interface haptics are on (F-001).

**Tests / verification:** `PullToRefreshTest` (4: Home finds an added film and loses a removed one, every page reads the library again; Movies and Shows pull too; one refresh at a time, a short pull is nothing; a drag in the middle of the page only scrolls). On device: `PhaseTwoWalkOnDeviceTest.aPullAtTheTopOfHomeFindsWhatWasAdded`. A disconnected source keeps its works (the pipeline's existing behaviour, `SourceSilenceTest`). Owner acceptance pending.

<a id="b-049"></a>
### B-049: Allow PIN removal from a profile (authorised)

- **Category:** Bug (security/usability) · **Priority:** High · **Platforms:** all
- **Components:** `library` (`Profiles.kt`: `setPin`, `changePin`, `removePin`, `removePinConfirmedBySystem`; `Library.sq` `setFirstProfilePin`); `ui` (`SettingsScreen`, `AppState.setPin`, `removePin`, `pinRefused`)
- **How found:** the owner's Phase 2 brief (2026-11-19), where it was numbered B-046. Renumbered because B-046 was taken by the brief's B-043.
- **Phase:** 2 (owner's brief, section 7) · **Status:** Fixed (automated) · **Last updated:** 2026-11-19
- **Related:** B-018, Forgot PIN (DECISIONS.md, 2026-11-19)

**Original description.** Once a profile has a PIN there is no proper way to remove it. Users need a legitimate way to disable PIN protection without deleting the profile.
- *Expected:* a clear, discoverable security option to remove the PIN that:
  - requires authorisation (the current PIN or an explicitly authorised recovery mechanism) and never creates a bypass;
  - explains that the profile will no longer be protected, and confirms first;
  - removes the requirement properly;
  - keeps the profile's data, and other profiles and their security;
  - persists across restarts and works across profile switching;
  - is distinct from resetting or deleting a profile.

  It is not to be an unprotected toggle.
- *Security tests:* authorised removal succeeds; a wrong PIN cannot remove it; cancel keeps it; data intact; other profiles unaffected; persists after restart; no longer required afterwards; a PIN can be set again; no alternate route around profile security.

**Investigation.** The defect was worse than "no proper way": the way that existed was **unprotected**.
- Settings showed "Remove PIN" (a quiet button) and "Change PIN", calling `Library.removePin(id)` and `setPin(id, pin)` with no check.
- Anyone at an unlocked device could strip, or silently replace, the open profile's PIN in two taps.

**Implementation.** Authorisation is in the operation, as for deletion (B-018):
- `Library.removePin(id, current, key)` and `changePin(id, current, next, key)` verify the current PIN in the same transaction, with `checkPin`'s counting and lockout. A wrong PIN changes nothing and counts as a try.
- `setPin` only gives a first PIN: one SQL statement that refuses a profile which already has one, so it cannot be used to go around the PIN.
- **Settings:**
  - "Remove PIN" opens a panel that says what it means ("…will open without a PIN. Anyone using this device can then see its watch history…"), asks for the current PIN, and has the safe "Keep PIN" first.
  - "Change PIN" asks for the current PIN before the new one, twice.
  - A refused PIN is said, announced, and felt (haptics gateway).
- The only way to drop a PIN without it is `removePinConfirmedBySystem`, used only after the system's own confirmation of the device's owner in Forgot PIN (Android phones and tablets).

**Tests / verification:** Library: `ProfilesTest.aPinIsChangedOrRemovedOnlyWithItself` (wrong, missing, or another profile's PIN refused and counted; the lockout applies; a first PIN cannot replace one; change and removal with the right PIN; history kept; the other profile untouched; kept after reopening; a PIN can be set again) and the existing PIN tests. UI: `ProfileSecurityTest` (4: removing needs the PIN, Keep PIN changes nothing, a wrong one is refused, the right one removes only the PIN, and it holds after a restart; changing needs the current PIN; Forgot PIN with the device lock, cancelled, failed, then confirmed; Keep profile and Back through each question), `ProfilesUiTest` updated. Owner acceptance pending.

<a id="b-050"></a>
### B-050: Reflux crashes when a controller is switched off and on (Windows)

- **Category:** Bug (crash) · **Priority:** Critical · **Platforms:** W
- **Components:** `desktop:app` (`gamepad/WindowsGamepads.kt`)
- **How found:** the Windows controller walk (2026-11-19, GameSir G7 Pro on its 2.4 GHz receiver). Reflux disappeared twice when the owner switched the controller off; Windows logged `APPCRASH`, exception `0xc0000374` (heap corruption) in `ntdll.dll`, at the moments the press log showed a disconnect. One earlier crash with the same signature is logged for 2026-11-18.
- **Phase:** 2 (found in its verification) · **Status:** Fixed (automated; walked on Windows with a physical controller) · **Last updated:** 2026-11-19
- **Related:** F-002, B-039

**Cause (isolated).** Four probe processes, each calling one of the reader's native paths the way Reflux does, ran while the owner switched the controller off and on three times:
- the joystick API (`winmm` `joyGetNumDevs` / `joyGetPosEx` / `joyGetDevCapsW`) crashed with the same heap corruption when the controller came back;
- so did the full reader;
- XInput (with rumble pulses three times a second) and raw-input enumeration survived every cycle.

The joystick API is asked for every HID game controller, XInput ones included, every 2 s.

**Fix.** The joystick API is asked only when raw input lists a HID game controller (usage page 1, usage 4 or 5) that XInput does not read, and not while the set of controllers has changed in the last 4 s. An XInput pad (Xbox, GameSir, most PC pads) never reaches it.

**Verification.**
- **Windows, physical:** the same three off/on cycles with Reflux open and the full-reader probe running. No crash: the reader went pads 1→0→1 three times, Reflux stayed open, and the controller still navigated. No new Application Error events.
- **Outstanding:** a non-XInput controller (the owner's DualSense) still goes through the joystick API, now only after a settled 4 s. Its hot-plug walk is pending. If it still crashes, the joystick API is to be replaced (raw HID through `hid.dll`).

<a id="b-051"></a>
### B-051: Mouse-accessible on-screen keyboard in Search (Windows and Linux)

- **Category:** Feature (accessibility/input) · **Priority:** Medium · **Platforms:** W, L
- **Components:** `ui` (`BrowseScreen` Search row, `RefluxKeyboard` `KeyCap`, `components/TextEntry.kt` `FieldKeyboard`, `Icons.kt` `KEYBOARD`); `Platform.pointerKeyboard` (true in `DesktopPlatform` only)
- **How found:** the owner's Phase 2 follow-up (2026-11-19, issue 1)
- **Phase:** 2 (approved follow-up) · **Status:** Fixed (automated) · **Last updated:** 2026-11-19
- **Related:** B-023, B-025, B-042

**Description.** On a desk with only a mouse, Search could not be typed into: the Reflux keyboard opened for a controller or a remote only. *Expected:* a keyboard button beside the clear button in Search, on Windows and Linux only. Keys are clicked with transient press feedback; a controller keeps its lit key.

**Implementation.**
- **The button:** Search shows a keyboard button where `Platform.pointerKeyboard` (the desktop's only) says so. It is always shown, beside the clear button when there is text, in the field's row.
  - It focuses the field and opens the Reflux keyboard through the field's own `FieldKeyboard` opener, the same request a controller makes.
  - Results update as typed, through the field's usual `onValueChange`.
- **Visuals by modality:**
  - The lit (white-filled) chosen key is shown only under directional input or on a television.
  - A pointer or a finger gets no lit key. A pressed key sinks to 93% and brightens while down, and for 110 ms after a click too quick to see. A hovered key brightens a little.
  - Picking up a controller lights its key again.
- **Not touched:** Android phone, tablet and TV keep their keyboards as they were (`pointerKeyboard` false).

**Tests.** `PointerKeyboardTest`:
- a mouse alone types "heat" (results shown), then Erase, Space, Shift and a capital, with no key left lit after any click;
- both buttons beside the field;
- a controller lights a key again, and Start finishes;
- the button is not offered on a phone, tablet or television platform;
- a controller's lit key unchanged.

**Walk (Windows, packaged app, by the owner):** the keyboard button, keys clicked with no key left lit, Erase, Space: "All worked".

**Outstanding:** the Linux VM.

<a id="b-052"></a>
### B-052: Close playback directly from the mini-player with a controller or remote

- **Category:** Bug (controller UX) · **Priority:** Medium · **Platforms:** W, L, A (phone, tablet); TV has no mini-player
- **Components:** `ui` (`AppState.closeMiniPlayer`, `onGamepadHold`, `onBackHeld`, `backWaitsForRelease`, `BACK_HOLD_MS`; `browsePrompts`); `desktop:app` (`GamepadTranslator` `waitsForRelease`/`hold`); `android:app` (`MainActivity.heldBack`)
- **How found:** the owner's Phase 2 follow-up (2026-11-19, issue 2)
- **Phase:** 2 (approved follow-up) · **Status:** Fixed (automated) · **Last updated:** 2026-11-19
- **Related:** B-039, F-002, M10 player spec

**Description.** B in the full player minimises it into the mini-player, but closing playback then needed a walk to the mini-player's own close button.

**Decision (owner, 2026-11-19):**
- Two ways, each enough alone: **RT**, or **holding B** (a remote's Back).
- View stays fullscreen. X stays reserved (F-P3).

**Implementation.**
- **RT:** with the mini-player up and nothing over it (no full player, keyboard, profile gate, panel), RT closes it. The session is closed and released, not hidden; the page and its focus stay. The triggers had no other browsing meaning; in the player RT is still faster.
- **Holding B:** in the same state, B waits for its release. A short B is Back, as before. Held 700 ms (`BACK_HOLD_MS`), it closes the player instead, never both.
  - The desktop's controller translator does this for the cancel button.
  - Android does it for a controller's B, and for a remote's Back up to Android 12 (`MainActivity.heldBack`). From Android 13 Back is the back callbacks' (predictive back, which lint enforces), and they know no held key; there a remote closes it with Stop.
- **Also:** a remote's Stop key already closed it (`STOP` is a docked action). A confirmation is felt on the controller.
- **Hints:** "RT Close player", and B as "Back · hold to close player" (or "Hold to close player" at a root). B is marked as a button to hold: its circle fills as a ring while it is held, and a small ▼ sits above it (B-055).
- **Unchanged:** B in the full player still minimises.

**Tests.**
- `MiniPlayerTest.withAControllerBMinimisesAndRtOrAHeldBClosesForGood`:
  - B minimises;
  - both hints offered;
  - RT closes, the player released and the page unchanged;
  - again by a held B;
  - holding B with nothing playing does nothing;
  - a remote's held Back closes it.
- `GamepadTest.aButtonThatWaitsForItsReleaseIsAPressWhenTappedAndAHoldWhenHeldNeverBoth`.

**Walk (Windows, physical GameSir G7 Pro, by the owner):** RT closes the mini-player, and holding B does too, with the ring filling; "All worked". The screen's brief blank on closing is B-056 (seen by mouse too, and with the B-002 change off).

**Outstanding:** a remote's held Back on an Android handheld.

<a id="b-053"></a>
### B-053: Focus Play when a media details page opens from a card

- **Category:** Bug (controller/remote UX) · **Priority:** Medium · **Platforms:** all with a controller or remote
- **Components:** `ui` (`components/Focus.kt` `initialFocus(onArrival)`, `DetailsScreen`)
- **How found:** the owner's Phase 2 follow-up (2026-11-19, issue 3)
- **Phase:** 2 (approved follow-up) · **Status:** Fixed (automated) · **Last updated:** 2026-11-19
- **Related:** B-008, B-013, B-025

**Description.** A on a card opened its page with nothing focused, so playing took a D-pad press and a second A.

**Cause.** `initialFocus` marked a page's main action only for the *first directional press*; nothing took focus when the page arrived.

**Fix.**
- `initialFocus(onArrival = true)` also focuses the control as the page is shown, but only where focus is shown (directional input, a television), not while focus is in the navigation, and once.
- Details gives it to its main action: Play, Resume or Play episode, by `playLabel`. Where nothing can be played (offline, no playable copy), the first action beside it gets it.
- A pointer or a finger is never handed focus. Nothing plays on arrival. Back's return to the card is the existing `rememberFocusReturn`.

**Tests.**
- `DetailsFocusTest`:
  - A on a Home card opens on Play, nothing playing, and A again plays;
  - Back returns to the card;
  - the same from Movies and from Shows;
  - a mouse click opens the page with nothing focused.
- `DirectionalInputTest.aControllerBrowsesOpensAndGoesBack` updated, since arrival on Play is now by design.

**Walks.**
- **Windows, physical controller (the owner):** A on a poster lands on Play, and A again plays.
- **Fire TV Stick 4K Plus, physical,** with the remote's key codes injected by the system's input service (`input keyevent`, as adb does), in `PhaseTwoWalkOnDeviceTest.onATelevisionOkOnACardOpensItsPageOnPlayAndOkAgainPlays`:
  - OK on a card opens Details with the ring on Play (screenshot looked at);
  - nothing plays on arrival, and OK again plays.

<a id="b-054"></a>
### B-054: Reflux keyboard: cursor jumps to the end; holding X does not keep erasing

- **Category:** Bug (input) · **Priority:** High · **Platforms:** all with the Reflux keyboard
- **Components:** `ui` (`state/Keyboard.kt` `KeyboardSession`, `components/TextEntry.kt` `rememberFieldValue`, `AppState.repeatsWhileHeld`); `desktop:app` (`GamepadTranslator` `repeats`)
- **How found:** the Windows controller walk (2026-11-19).
- **Phase:** 2 · **Status:** Fixed (automated; walked on Windows with a physical controller) · **Last updated:** 2026-11-19
- **Related:** B-023, B-011

**Description (owner).** Typing A, B, D, LB once, C, then E gave ABCDE rather than ABCED: after one letter the cursor went back to the end. The cursor was not drawn where it was, in either the keyboard's box or the search field, whose caret stayed at the start. Holding X erased only one character.

**Cause.**
- After each key the session read the field's text back at once. A field hands its new text back only when it is composed again, so the session took the old text as a refusal and put the cursor at the end.
- The field (a `String` `BasicTextField`) kept its own selection, at 0.
- The desktop controller translator repeated only directions.

**Fix.**
- The session keeps what it wrote as its record; the cursor stays where it was put while the field shows that text. It goes to the end only when the field shows something else (a refused character, a physical keyboard's edit).
- Fields take their caret from the keyboard's cursor while it is open for them (`rememberFieldValue`).
- Erase and the cursor buttons repeat while held on the keyboard (`repeatsWhileHeld`). Android already repeats held buttons.

**Tests.**
- `KeyboardSessionTest.theCursorStaysWhereItWasPutWhenTheFieldCatchesUpLate`, the reported sequence with a lagging field.
- `InteractionContractTest.searchIsUsableWithAControllerAlone`: the field's caret follows, letters go in at the cursor, and X repeats only while the keyboard is open.
- `GamepadTest.aButtonTheAppSaysRepeatsRepeatsLikeADirectionAndStopsWhenItNoLongerDoes`.

**Verification:** Windows, physical controller, by the owner: "All correct" (ABCED, the cursor visible in both places, holding X keeps erasing).

<a id="b-055"></a>
### B-055: Controller hints: LT hides them; keys in their controller shapes; calm motion

- **Category:** Feature (controller UX) · **Priority:** Medium · **Platforms:** all with a controller
- **Components:** `ui` (`components/Focus.kt` `ButtonPrompts`, `KeyCap`, `HoldPrompt`; `RefluxApp.browsePrompts`, `rememberBackHold`; `AppState.hintsHidden`, `toggleHints`); `core` (`SemanticAction.TOGGLE_HINTS`, `InputMapping`)
- **How found:** the owner, during the follow-up's Windows walk (2026-11-19)
- **Phase:** 2 (the owner's request during the approved follow-up) · **Status:** Fixed (walked on Windows by the owner: "Looks right") · **Last updated:** 2026-11-19
- **Related:** B-039, B-052, F-P2

**Requests (owner, in order).**
- LT hides the hints; hidden, only LT's own hint stays, to show them again.
- The hints made elegant, shown only where they mean something, and the bar expanding and shrinking smoothly.
- After a first attempt:
  - the keys outlined as before, not filled;
  - A, B, X and Y round, and the bumpers and triggers in their own shapes;
  - more legible;
  - calm motion: the first attempt's spring bounced, and LT moved as the bar resized;
  - the hold hint not making the bar taller.

**Implementation.**
- **LT** (`TOGGLE_HINTS`) while browsing toggles `hintsHidden`, kept as the device setting `controller_hints`. Hidden, the bar shows only "LT Show hints"; shown, it ends with "LT Hide hints". In the player LT is still slower, and on the keyboard Shift.
- **Keys:** drawn outlined in their controller shapes. Face buttons are circles (PlayStation's ✕ ○ □ △ as shapes), bumpers flat with rounded tops, triggers taller with rounder tops, and menu buttons small pills. Outline 42% and fill 16%; labels at 86% white.
- **Motion:**
  - Each hint keeps its place while the bar is up, opening or closing where it belongs, toward the bar's anchored end, on one 220 ms ease with no overshoot.
  - The bar is exactly as wide as its hints at every moment; nothing animates its size separately, so LT at the end stays put.
  - A changed label cross-fades in place.
- **Hold (B-052):** B's own circle is the ring that fills over the 700 ms hold, and a small ▼ sits in the bar's margin above it. The bar's height never changes.

**Tests.** `HintsTest`:
- LT hides, leaving its own hint; the buttons still work while hidden; LT shows them again;
- a PlayStation pad named in its own terms;
- kept after a restart.

`MiniPlayerTest` (the hold mark, the ring half full half way and full at the end, empty on release), `DirectionalInputTest`, `ControllerFeelTest`, `InteractionContractTest`. Rendered and looked at: Xbox, PlayStation, hidden, and the mini-player's hold.

<a id="b-056"></a>
### B-056: The screen blanks briefly when the mini-player is closed (Windows)

- **Category:** Bug (rendering) · **Priority:** Medium · **Platforms:** W (seen); others not checked
- **Components (likely):** `desktop:app` (`VideoSurface`, `VideoOverlay`, `SurfaceCorners`: the video surface and its controls window torn down together); `playback:mpv`
- **How found:** the owner, closing the mini-player during the follow-up's Windows walk (2026-11-19). By controller (RT or a held B) and by mouse alike: "the screen blanks for a few 100 ms".
- **Phase:** 2 (found in its verification) · **Status:** Deferred (the owner's decision, 2026-11-20: accepted as a known minor issue; skipped in Phase 2, a later beta if evidence comes up) · **Last updated:** 2026-11-20
- **Related:** B-002, B-052

**Owner's decision (2026-11-20, Phase 2's closure):** a minor nitpick and not a Beta 2 blocker. No further investigation in Phase 2. It stays documented, to be revisited in a later beta if reproducible evidence (an on-screen capture of the blank) comes up. It is not a failure of Phase 2's verification.

**Investigation so far.**
- **Not today's B-002 change:** the owner saw the same blank in a build with Java2D's Direct3D pipeline forced back on.
- **Not the new buttons:** a mouse click on the mini-player's own close does it too.
- **Not in Reflux's drawing:** 20 PrintWindow captures of Reflux's window across 1.5 s after the click showed no blank frame. It is therefore likely at the composition level, as the native video surface and the controls window go: a heavyweight child removed from a Compose window, or DWM recomposing.
- A capture of the window's area on screen was tried, but the driver's own setup of playback was unreliable, and it was stopped rather than kept trying.

**Next:**
- an on-screen capture of the close, done with the owner present;
- comparing the full player's close (Back from it), which tears down the same surface;
- if confirmed, hiding the surface and the controls window first and disposing them a frame later, or keeping the surface until the page under it has drawn.

<a id="b-057"></a>
### B-057: Search redesign: the idle page, a real filter, shortcuts from the library, results in sections

- **Category:** Feature (UX) · **Priority:** Medium · **Platforms:** all
- **Components:** `ui` (`screens/SearchScreen.kt`, new; `state/SearchModels.kt`, new: `SearchScope`, `QuickFind`, `GenreFind`, `SearchDiscovery`, `searchDiscovery`, `searchSections`, recent searches; `AppState.searchScope`; `BrowseScreen.kt` loses the old Search)
- **How found:** the owner's Search steer (2026-11-19): "stale, under-designed, and visually empty; the filter chips and suggestions are the weakest part"
- **Phase:** 2 (approved Search UX improvement) · **Status:** Fixed (automated; renders looked at; device walks below) · **Last updated:** 2026-11-19
- **Related:** B-011, B-034 to B-037, B-010, B-051, B-054

**Audit (before).**
- **The page:** a field, a single row of five fixed text chips, and an empty page below.
- **The chips** were not filters. They were smart-search queries ("4K movies I haven't watched", "unfinished shows", "90s movies", "favorites", "movies under 2 hours"), offered whether or not the library had anything for them.
- **Interpretation:** the only "filters" were the read-only chips saying how a query was understood. There was no way to narrow a search.
- **Results:** one grid, films and shows mixed. The no-results state was B-037's.
- **Input:** controller and remote reach the field (B-011, B-023); a mouse has the Reflux keyboard on a desk (B-051).

**Design decisions.**
- **The field leads:** taller (64 dp; 56 on a phone; 60 on a TV), with a brighter rim and icon when focused. Clear and (on a desk) the keyboard button stay inside it. The placeholder stays B-034's.
- **A filter, separate from suggestions:** All / Movies / Shows, a segmented switch that narrows typed queries, shortcuts, their counts and the empty state alike. It sits on the field's line where there is room, and under it on a phone.
  - With results, Down from the field (and the keyboard's Done) goes straight to the first result, as before. The switch is one step up from the results.
- **The idle page, from this library only:**
  - **Recent:** the profile's own recent searches on this device, recorded when a search led somewhere (a result opened, or Done), six at most; Clear forgets them. They are never sent anywhere.
  - **Quick finds:** compact tiles, each an emblem (New, 4K, HDR, 2h, ’10, a play or heart icon), a name and how many titles it finds here. They are offered only when the library has something for them: continue watching, not seen yet, favorites, 4K, HDR, under 2 hours, and the library's two biggest decades.
  - **Genres:** the library's own genres by size (up to 12; 8 on a phone), as tiles with a muted hue of their own and a count.
  - A library with nothing to suggest yet gets a short word on what Search does, not an empty page.
  - No posters fill space; nothing is offered that finds nothing.
- **Results:** a quiet line with the count and each thing understood ("12 results · Movies · 4K"); sections with headings and counts when films, shows and episodes are all found; the same poster cards, opening Details as before.
- **Nothing found:** no "0 results" line. The query stays in the field, and the page says so. Clear search; Search everything when the switch narrowed it (else Browse movies); and up to three quick finds that do find something.
- **Layout by screen:** tiles in as many equal columns as fit (four quick finds and five genres on a desktop window; two across a phone; three or four larger ones on a television). Plain rows, so a D-pad moves along and between them. Tiles light on focus without lifting, so a focus ring never meets a neighbour (B-010).

**Tests.**
- **`SearchDesignTest` (6):**
  - only what the library has, with counts, and a quick find asks its query;
  - the switch narrows results and the empty state, offers Search everything, and the idle counts follow it;
  - sections by kind;
  - a search that opened something is remembered, asked again, and cleared;
  - a controller goes from the field to a tile and chooses it, without typing;
  - renders at a phone, a tablet (1280 × 800), a desktop window and a television.
- **Updated:**
  - `CardLayoutTest` (tiles in rows, rings apart);
  - `ScreensTest` (idle and no results);
  - `PointerTest` (a genre tile by mouse);
  - the Android walk (a quick find is a finger's target).
- **Unchanged and passing:** `InteractionContractTest` (Search with a controller alone), `TelevisionTest` (Search from the remote), and `PointerKeyboardTest` (the desk keyboard button).

**Second design (2026-11-19, after the owner's review of the first on Windows).** The owner found the first design out of place in Reflux:
- the scope switch was a widget bolted to the field;
- quick finds looked like dashboard metric tiles;
- genres were a rainbow of gradient boxes;
- too many modules, each in its own box.

Asked for a rethink, not a tweak, the page was rebuilt as **one search line with an editorial index beneath it**:
- **Scope as part of the search:** All / Movies / Shows are plain text tabs on the field's own line, the chosen one bright with a short rule under it, no container. It is the same on every screen. With a query, the count and what was understood sit at the far end of that line ("Unwatched · 12 results").
- **No helper line, no boxes, no badges, no colours.** Hierarchy is the field first, then type: small letter-spaced labels ("Recent", "Start with", "Genres"), body-sized lines, and display-sized genre words.
- **"Start with"** replaces the quick-find tiles: a list of lines, each a name with its count quietly at the right edge, lit softly when pointed at or focused. Recent searches sit above it in the same column, with Clear.
- **Genres as an index:** the library's genres set as words in the section-title size, flowing and wrapping, each with a small superscript-like count, underlined and lit when pointed at or focused. Counts are secondary.
- **Layout:** wide screens put "Start with" in a narrow left column and the genre index in the wide right one; a phone stacks them; a television sets the same at its larger type with wider gaps.
- **Focus path:** Down from the field goes straight to the results; Up walks back over what is on screen (results, then the tabs, then the field). Idle, Down goes from the field to the tabs, then into the index.
- **Nothing found:** the title-sized message, its actions, and "Or start with" as lines of the same index.
- **Kept:** every useful shortcut, genre filtering, the scope filter, results in sections, clear, the desk keyboard button, IME, controller and remote, and focus rules.

**Tests (second design).**
- `SearchDesignTest` 6/6 (counts shown, none zero); `CardLayoutTest` (lines and words keep their rings apart);
- `ScreensTest`, `PointerTest`, `InteractionContractTest`, `PointerKeyboardTest`;
- `TelevisionTest` (Up from a result reaches the tabs, then the field);
- renders looked at: desktop idle with Recent, phone, television, results, no results.

**Outstanding:** the owner's review.

<a id="b-058"></a>
### B-058: Linux on a Wayland session: the video covered the player's controls and sat out of place

- **Category:** Bug (playback, platform) · **Priority:** High (Linux) · **Platforms:** L (GNOME on Wayland, Ubuntu's default; Reflux runs through XWayland)
- **Components:** `desktop:app` (`DesktopPlatform.embeddedOptions`), `playback:mpv` (`MpvPlayer.create`, list options)
- **How found:** Phase 2's closing Linux VM walk (2026-11-20). In the player, only the controls' ▼ and × showed (above the picture), never the bottom bar. The pointer never brought the controls back, and the picture sat about 22 px low.
- **Phase:** found in Phase 2's closure and fixed there. It is older than Phase 2: the released `0.9.0-beta.1` does the same in the same VM. Its allocation is the owner's to confirm. · **Status:** Fixed · **Last updated:** 2026-11-20
- **Related:** B-031, B-002

**Cause (verified).**
- Reflux gives libmpv its own X11 window (`wid`). On a Wayland session, mpv's GPU output probes Wayland first. It cannot embed into an X11 window, so it made a Wayland surface of its own above Reflux's windows. That surface covered the controls window (which was correct in its own pixels, read with `XGetImage`) and took the pointer.
- **Shown three ways:**
  - Reflux's redacted mpv log (`REFLUX_MPV_LOG`): "Initializing GPU context 'auto'" then `'waylandvk'`;
  - a bare libmpv with an X11 `wid` reports `current-gpu-context = wayland`;
  - starting Reflux without `WAYLAND_DISPLAY` set put everything right.

**Fix.**
- On Linux the embedded player is given `gpu-context=x11egl,x11,x11vk`: X11 contexts, EGL first.
- **One name a build lacks no longer drops the whole list.** Ubuntu's libmpv 0.41 has no `x11` (GLX) context and rejected the list as a whole. So a list option that is rejected (`gpu-context`, `gpu-api`, `vo`) is set again with only the names this libmpv takes, in order.

**Tests:** `EmbeddedOptionsTest` (3), `ListOptionTest` (3).

**Verification (Linux VM, not physical):**
- under Wayland, mpv's log says "Initializing GPU context 'x11egl'";
- the full controls show over the picture, and the pointer brings them back;
- the picture is in place;
- playback advanced 0:18 → 0:24 over 5 s;
- pause, seek +10 s, Esc to the mini-player (the picture follows into the dock), and the mini-player's close all work.

**Outstanding:** a physical Linux desktop (the owner's procedure in VERIFICATION.md), with a real GPU, on Wayland and on X11.

<a id="b-059"></a>
### B-059: A Search tab clicked with the mouse took the typing from the field, and Backspace then left Search

- **Category:** Bug (input) · **Priority:** Medium · **Platforms:** W, L (pointer)
- **Components:** `ui` (`SearchScreen`: `ScopeTabs`, "Search everything")
- **How found:** Phase 2's closing Linux VM walk (2026-11-20). Type "night", click the All tab, press Backspace to correct it: Reflux went back to Home. Backspace outside a text field is Back on a desktop (INPUT_MODEL.md), and the click had put focus on the tab.
- **Phase:** 2 (a regression of B-057's design) · **Status:** Fixed · **Last updated:** 2026-11-20
- **Related:** B-057, B-020

**Fix.**
- A tab chosen with the pointer hands the typing back to the field. A controller or the arrows keep focus on the tabs, to move along them.
- "Search everything" (in the empty state) gives the field its focus back for every input, as the quick finds already did.

**Test:** `SearchDesignTest.aClickedTabLeavesTheTypingInTheField`. It fails with the fix turned off and passes with it.

**Verification (Linux VM, not physical):** with the fix, "night", a click on Movies, then Backspace gives "nigh", still on Search.

<a id="b-060"></a>
### B-060: Panels opened in place (a confirmation, a rename, a PIN panel) lost focus, and Back skipped them

- **Category:** Bug (input, focus) · **Priority:** High (controller and remote) · **Platforms:** all with directional input (TV, controller, keyboard)
- **Components:**
  - `ui`: `Focus.kt` (`takesFocus`, `PanelReturn`/`returnsFocus`); `AppState` (Back closes an open panel first, and its route stays in the app while one is open);
  - the panels: `ProfilesScreen` (`Confirm`, `ClosesOnBack`, add and delete a profile, the deletion PIN) and `SettingsScreen` (rename, the PIN panels, clear watch history).
- **How found:** Phase 2's closing Google TV emulator walk (2026-11-20). A on "Clear watch history" showed its confirmation, but focus fell out of the page to the sidebar. B, from there, then left Settings for Home with the confirmation still open.
- **Phase:** 2 · **Status:** Fixed · **Last updated:** 2026-11-20
- **Related:** B-025 (the input contract), B-020, B-053

**Cause.**
- Each panel replaces, in place, the control that opened it. With that control gone, focus fell to the navigation.
- The panels' first controls were marked `initialFocus()`, which only says where the first directional press should go. It does not move focus.
- Back had no notion of a panel open on the page: `pane()`'s dismiss is a screen reader's action only.

**Fix.**
- **On opening:** a panel's first control takes focus as it appears, for input that shows focus. That is "Keep" on a confirmation, the field on a rename or PIN panel, and "Keep PIN" on removal. A pointer or a finger sees nothing change.
- **On closing:** when it closes (cancelled, kept, or done), focus goes back to the control that opened it.
- **Back:** Back (a remote's, B, Escape) closes the innermost open panel before anything else on the page, and its route stays in the app while one is open.
- **B-020's contract, refined:** with a keyboard, the first Escape still lets go of the field. The next one now closes the panel, and the one after leaves the page. Before, the second left the page with the panel open.

**Tests:**
- `PanelFocusTest` (3: confirmation, rename, PIN), on the television harness;
- `InteractionContractTest` (B-020's sequence updated as above);
- `AccessibilityTest` (39), `TelevisionTest` (5).

**Verification (Google TV emulator, by hand with gamepad keys):** A on Clear watch history puts focus on Keep. B closes the confirmation, stays on Settings, and focus is on Clear watch history again.

**Not walked on a device by a test:** on the emulator the accessibility tree runs behind a move, so a walk's check of where focus is pressed once too often. That landed on "Online metadata & artwork", and A turned it on. It was turned back off by hand, and verified off. The device walk therefore leaves Settings alone, and this behaviour is `PanelFocusTest`'s.

<a id="b-061"></a>
### B-061: The desktop app ended in Skia's native code when Direct3D failed while its window was resized (Windows)

- **Category:** Bug (crash) · **Priority:** High, provisional (a crash; one occurrence seen) · **Platforms:** W
- **Components:** `desktop:app` (the window); Compose Multiplatform's Skiko renderer (`skiko-windows-x64.dll`)
- **How found:** 2026-11-20, Phase 3's work: a JVM crash log (`hs_err_pid40688.log`) left in the repository's root by the owner's packaged desktop build (`jpackage` app version 0.9.31, run from `desktop/app/build/compose/binaries`), which had run for 11 h 43 min. Not caused by Phase 3's changes (the build predates them).
- **Phase:** none assigned (found in Phase 3; the owner allocates) · **Status:** Investigating (carried open; not fixed in Beta 2; placement the owner's: a Beta 3 candidate) · **Last updated:** 2026-11-20
- **Related:** B-002 (monitors and window moves), B-056 (blank on closing the mini-player)

**Description.** `EXCEPTION_ACCESS_VIOLATION` reading `0xffffffffffffffff` in `skiko-windows-x64.dll` on the AWT event thread. The Java stack: a Swing layout pass resized the window's `ComposeWindowPanel` → `SkiaLayer.reshape` → `Direct3DRedrawer.renderImmediately` failed → `RedrawerManager.findNextWorkingRenderApi` (Skiko giving up on Direct3D and trying the next renderer) → `Direct3DRedrawer.dispose` → `ContextHandler.dispose` → a Skia object's native finalizer, which crashed. Heap 66 of 120 MB used: not memory exhaustion.

**Reading (not a confirmed cause).** Direct3D stopped working under the window (a device lost: a driver reset, the display sleeping and waking, a monitor or GPU change after almost twelve hours), and Skiko's fallback freed its Direct3D context in a way that crashed. That path is Skiko's, not Reflux's; Reflux chooses no renderer.

**Work record**
- **Reproduction:** not attempted on the owner's machine (the owner's displays and the owner's running session). Nothing in the log says what changed the window's size.
- **Options, for the owner:** (1) report it upstream with the log (JetBrains' Skiko) and update Compose Multiplatform when a fix lands; (2) pin `skiko.renderApi` to OpenGL or software on Windows (avoids the Direct3D fallback path at a performance cost, and needs its own measurement); (3) catch nothing: a native crash cannot be caught in Java.
- **Outstanding:** the owner's account of what happened at 11:37 (sleep, a monitor, a game); a reproduction; the decision above. The log is left in place, uncommitted.

<a id="b-062"></a>
### B-062: A television started Home in the navigation when the featured work could not play, and Right could not leave it

- **Category:** Bug (focus) · **Priority:** Medium · **Platforms:** TV, F (any start with the hero's Play disabled)
- **Components:** `ui` (`HomeScreen` hero); `library` (`Featured.choose`)
- **How found:** 2026-11-20, Phase 3's Google TV emulator walk. The emulator's library was from a Jellyfin source that needed signing in again, so the featured work's Play was disabled.
- **Phase:** 3 (found and fixed in Phase 3's walks) · **Status:** Fixed (Google TV emulator walked; automated) · **Last updated:** 2026-11-20
- **Related:** B-014, B-053, B-025

**Description.** Home started with focus in the navigation rail rather than on the page; Right from the rail did not reach the page (nothing focusable level with it), and Down moved within the rail.

**Work record**
- **Not caused by Phase 3:** the build before Phase 3 (`9903e86`, built in a temporary worktree) behaved the same on the same emulator and library.
- **Cause:** the hero's Play is the page's starting focus (`initialFocus`), and a disabled button cannot take focus, so the start fell back to the navigation.
- **Fix:**
  - Where the hero cannot play now, Details is the starting focus.
  - The featured pick prefers works that can play now (`Featured.choose`), falling back to the rest.
- **Tests:**
  - `HeroFocusTest`: a television with the library away starts on Details; failed before the fix and passes with it. With the library there it starts on Play.
  - `FeaturedTest.whatCanBePlayedNowComesFirst`.
- **Walked:** the Google TV emulator starts on Details. Down then reaches a card, and the remote's Menu opens Quick View (screenshot looked at).
- **Outstanding:** owner acceptance.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-063"></a>
### B-063: On a Linux desktop scaled above 100%, Reflux was drawn at 100%: small, or enlarged and blurred by the compositor

- **Category:** Bug (rendering) · **Priority:** High (text blurred on a HiDPI laptop: unreadable) · **Platforms:** L (X11, and Wayland through XWayland)
- **Components:** `desktop:app` (`LinuxDisplayScale`, `Main.kt`, `VideoSurface.kt`)
- **How found:** 2026-11-20, the owner's cousin: CachyOS (KDE) on a Microsoft Surface Laptop 5, "extremely blurry" text (an owner's screenshot beside the file manager).
- **Phase:** 3 (found in its final validation; the owner's steer) · **Status:** Fixed (verified in the Ubuntu VM only; physical Linux, the Surface Laptop 5 with KDE, and mixed-scale monitors not tested: an accepted limitation) · **Last updated:** 2026-11-20
- **Related:** B-031 (Linux playback), B-002 (scaling on Windows)

**Versions:** Compose Multiplatform 1.12.1 (Skiko); Java 21 (bundled runtime); X11 toolkit (XToolkit; no native Wayland toolkit in that runtime), so on Wayland Reflux runs through XWayland. Renderer: Skiko's OpenGL on Linux; it plays no part (the scale is wrong before anything is drawn).

**Cause (measured).**
- **Java ignores the desktop's scale.** Java 21's X11 toolkit scales a window only by a whole `GDK_SCALE`: it ignores Xft.dpi, and rounds a fractional `sun.java2d.uiScale` down.
- **The probe:** Java 21.0.13 under Xvfb, `ScaleProbe` printing the default transform. The results were 1.0 plain; 2.0 with `GDK_SCALE=2`; 1.0 with `uiScale=1.5`; 2.0 with `uiScale=2`.
- **What the desktops do:** KDE and GNOME tell X11 applications their scale through Xft.dpi (144 at 150%, 192 at 200%), which GTK and Qt apply. Reflux ignored it and drew at 100%.
- **The two outcomes:**
  - Where the desktop lets applications scale themselves (KDE's "Legacy applications: Apply scaling themselves"; GNOME's XWayland native scaling), Reflux was half size or smaller.
  - Where the compositor scales X11 windows instead ("Scaled by the system"), it enlarged Reflux's 100% picture: blurred everything, text most visibly. This matches the report.
- **Not the cause:**
  - fonts or hinting (the text is rasterised at 1× correctly, then enlarged);
  - CachyOS itself;
  - Skiko's renderer.

**Fix.**
- **Reflux applies the desktop's scale itself** (`LinuxDisplayScale`): Xft.dpi ÷ 96 (from XSETTINGS, else `xrdb -query`), less what the toolkit already applies, so nothing is scaled twice.
  - Compose content is drawn at that density in the window's own pixels, in both Compose roots (the window and the player's controls window).
  - The window opens at the same size for the eye, within 90% of the screen.
- **Rounding:** to the desktops' eighths.
- **GNOME's large text:** its text-scaling-factor (which Reflux already applies to text alone) is taken out.
- **Overrides:** an explicit `GDK_SCALE` or `sun.java2d.uiScale` is respected, and `REFLUX_SCALE` sets the scale outright for a desktop that says nothing.
- **Recorded:** the applied scale goes into diagnostics.
- **Unchanged:** Windows and Android (the code returns 1 off Linux).

**Tests:** `LinuxDisplayScaleTest`, 5 cases:
- 125/150/175/200% at toolkit scale 1;
- `GDK_SCALE=2`, never doubled;
- 100%, and nothing known;
- large text taken out;
- the override, the rounding, and the `xrdb` parsing.

**Verified (VM, not physical): the Ubuntu 26.04 QA VM**, GNOME on Wayland, Reflux through XWayland, display set to 2560 × 1600. Captures are of Reflux's own window and compared at native pixels, without resampling.
- **100%:** Xft.dpi 96; nothing applied; unchanged.
- **200%:**
  - Xft.dpi 192.
  - Before (`REFLUX_SCALE=1`, the old behaviour): a 1360 × 823 window, everything half size.
  - After: ×2, a 2183 × 1345 window. The text is crisp at native pixels and the layout correct (the compact rail, as a 1150 dp window has).
- **125%** (GNOME's fractional and XWayland native scaling): Xft.dpi 192 over a 4096 × 2560 XWayland screen. GNOME draws X11 applications at 2× and reduces them, and Reflux's ×2 matches it.
- **Restored afterwards:** the VM's display (1280 × 800, 100%, no experimental features).

**Remaining:**
- **Physical checks (the owner's):**
  - the Surface Laptop 5 with CachyOS (KDE): 150%, both legacy-scaling modes;
  - Linux Mint (X11, Cinnamon);
  - a second monitor at another scale (the scale is read once at start: moving between monitors of different scales is not followed).
- **KDE's "Scaled by the system" mode:** it still enlarges every X11 application, Reflux included. The remedy there is the desktop's own setting, "Apply scaling themselves" (System Settings › Display & Monitor › Legacy Applications (X11)), which Reflux now honours. Its user documentation is for Phase 4's website work.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-064"></a>
### B-064: The adaptive keyboard did not follow the mouse: moving it changed only the hints, a click's first key was lost, a controller's first A typed nothing

- **Category:** Bug (input) · **Priority:** High (the owner: "the most important functional issue") · **Platforms:** W, L (the desk keyboard)
- **Components:** `ui` (`RefluxKeyboard`, `AppState` keyboard and modality), `core` (`KeyboardModeArbiter`)
- **How found:** 2026-11-20, the owner's physical controller and mouse walk on Windows.
- **Phase:** 3 (final validation) · **Status:** Fixed (automated with real mouse events; part of the build the owner tested and accepted; no separate owner confirmation recorded) · **Last updated:** 2026-11-20
- **Related:** F-P4, B-051, B-023

**Causes (each reproduced by `KeyboardWalkTest`, with real mouse events, before its fix).**
1. **Moving the mouse never showed the desk keyboard.** The keyboard measured the pointer's travel with `positionChange()`, which was zero for every move of a hovering mouse. It now measures from the last position it saw.
2. **The hints and the keyboard switched at different times.** The app's modality (which the hints follow) does not see moves over the keyboard. So the hints changed later, on a click's press, and the hints bar leaving moved the keys from under the pointer before its release. That click typed nothing.
   - Now the keyboard's switch sets the modality too, and the pointer taking over anywhere shows the desk keyboard after the arbiter's hold.
   - Not on a click's press.
   - A click anywhere on the panel, not only on a key, shows it on release.
3. **A controller's first A after the mouse only revealed the grid's focus.** It now types at once.

**Tests:** `KeyboardWalkTest`:
- every key by mouse, including Erase, Space, Shift, Caps Lock, Clear and Tab;
- the controller's first press types, X erases, and a mouse click types after the switch;
- the pointer's travel shows the desk keyboard without a key being clicked.

The existing `AdaptiveKeyboardTest`, `PointerKeyboardTest` and `KeyboardTest` pass unchanged.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-065"></a>
### B-065: The desk keyboard had an empty place where Tab is, and Backspace and Space were spelt out in large words

- **Category:** Polish (keyboard) · **Priority:** Low · **Platforms:** W, L
- **Components:** `ui` (`KeyboardLayouts`, `Keyboard`, `RefluxKeyboard`)
- **How found:** 2026-11-20, the owner's walk.
- **Phase:** 3 (final validation) · **Status:** Fixed (automated; part of the build the owner accepted) · **Last updated:** 2026-11-20

**Fix.**
- **Tab is a key where a physical keyboard has it.** In a search it takes the offered title (the focused one, else the first), as Tab completes in a terminal; elsewhere it finishes the field.
- **Erase is the conventional erase glyph,** drawn as a key outline pointing left with a cross. It deletes before the cursor, as before, and is never the cursor's arrow.
- **Space is a long bar.**
- **Screen reader names:** "Backspace", "Space", "Tab, take the suggestion".
- **Test:** `KeyboardWalkTest` (Tab fills "Blade Runner 2049").
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-066"></a>
### B-066: Profiles listed every other profile's Delete below them all, and the navigation's profile initial was oversized

- **Category:** UX · **Priority:** Medium · **Platforms:** All
- **Components:** `ui` (`ProfilesScreen`, `RefluxApp` navigation)
- **How found:** 2026-11-20, the owner's walk.
- **Phase:** 3 (final validation) · **Status:** Fixed (automated; part of the build the owner accepted) · **Last updated:** 2026-11-20

**Fix.**
- **A Manage button (⋯) beside each profile** opens that profile's options under its row, one at a time; Back closes the panel first.
  - **The open profile:** Rename (here) and Add a PIN or Change PIN (in Settings, where the current PIN is asked). It is not deleted while open, which keeps Reflux from being left without a profile; the panel says why.
  - **Another profile:** Delete…, confirmed, and with its own PIN where it has one. Its name and PIN stay its owner's.
- **Add a profile** stays apart from any one profile.
- **The navigation's initial** is icon-sized (20 dp beside 20 dp icons; its letter about half the circle). Profiles' own large initials are unchanged.

**Tests:** `ProfilesUiTest.eachProfileIsManagedFromItsOwnMenu_theOpenOneRenamedButNeverDeleted`. The deletion tests (B-018, B-019) go through the new menu and pass.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-067"></a>
### B-067: The player's hints could not be hidden, LT changed the speed there, and hidden hints still showed LT's

- **Category:** UX (controller) · **Priority:** Medium · **Platforms:** All with a controller
- **Components:** `core` (`InputMapping`), `ui` (`AppState`, `PlayerOverlay`, `RefluxApp`)
- **How found:** 2026-11-20, the owner's walk.
- **Phase:** 3 (final validation) · **Status:** Fixed (automated; part of the build the owner accepted; B-071 followed from it and is verified) · **Last updated:** 2026-11-20
- **Related:** B-055

**Fix.**
- **LT shows and hides the hints in the player** as it does everywhere: one preference, kept across restarts (`controller_hints`).
- **Hidden means none,** LT's own included.
- **The triggers no longer change the speed in the player.** Speed is in Options, as before, and on the keyboard's `,` and `.`.

**Tests:**
- `HintsTest`: everything hidden including LT, and kept after a restart;
- `MiniPlayerTest`: the player's hints hidden by LT, playback untouched, and the choice kept.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-068"></a>
### B-068: With a controller, nothing brought the mini-player back to the full player

- **Category:** UX (controller) · **Priority:** Medium · **Platforms:** All with a controller and a mini-player
- **Components:** `ui` (`AppState`, `RefluxApp`), `core` (`InputMapping` glyphs)
- **How found:** 2026-11-20, the owner's walk: only by moving focus across the page to the video.
- **Phase:** 3 (final validation) · **Status:** Fixed (automated; part of the build the owner accepted) · **Last updated:** 2026-11-20
- **Related:** B-052

**Fix.**
- **Pressing the right stick** (RS on Xbox, R3 on PlayStation) brings the same playback back to the full player, from wherever focus is.
- **Why this button:**
  - It is the player's own stick (its scenes, B-029).
  - Nothing else uses it.
  - It never plays, closes or opens anything else.
  - Every other button is taken: A, B, X and Y; Start (Sources); View (fullscreen); the bumpers (sections); LT (hints); RT (closes the mini-player).
- **Hinted** as "RS Full player" beside "RT Close player", and hidden with the other hints.

**Tests:** `MiniPlayerTest.theRightStickBringsTheMiniPlayerBackToTheFullPlayer_andLtHidesThePlayersHintsToo`: minimise and restore three times, with focus moved, the same playback each time.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-069"></a>
### B-069: Quick View was too translucent over too strong a blur, and scene navigation spelt out "A Go here, B Stay"

- **Category:** Polish · **Priority:** Low · **Platforms:** All
- **Components:** `ui` (`QuickView`, `Glass`, `PlayerOverlay`)
- **How found:** 2026-11-20, the owner's walk.
- **Phase:** 3 (final validation) · **Status:** Fixed (automated; part of the build the owner accepted) · **Last updated:** 2026-11-20

**Fix.**
- **Quick View's surface:** a slightly darker, more solid tint (90%, a third of the way from the surface colour to the background) over 40% of the blur.
- **The dim behind:** 60% instead of 72%.
- **Unchanged:** the layout, the motion and the hierarchy.
- **Scene navigation's preview** keeps its picture and time; the "A Go here, B Stay" line is gone, and A and B still commit and cancel.

**Tests:** the Quick View and Trickplay suites pass.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-070"></a>
### B-070: Added to Steam as a non-Steam game, Reflux showed only its name

- **Category:** Polish (desktop, outside the app) · **Priority:** Low · **Platforms:** W, L (Steam's library and Big Picture)
- **Components:** `design/brand/steam/`, `tools/brand/make-steam-artwork.py`
- **How found:** 2026-11-20, the owner's cousin, on Windows and Linux.
- **Phase:** 3 (the owner's steer) · **Status:** Fixed (the files made and checked; never applied in Steam on Windows or Linux: an accepted limitation) · **Last updated:** 2026-11-20

**Done.**
- **A Steam artwork set,** rendered from the brand's master geometry, in the same way as the store artwork:
  - the portrait cover, 600 × 900;
  - the landscape capsule, 920 × 430;
  - the hero, 3840 × 1240, with no lettering;
  - the lockup on transparency, 1280 × 264;
  - the icon, 256.
- **A README for people,** on Windows and Linux, covering every slot through Steam's own menus.
- **Nothing in the app changed.**

**Checked:** dimensions, modes (RGB covers, an RGBA logo and icon), the logo's alpha from 0 to 255, the brand's clear space, and the cover at 150 px wide. The covers and the hero, with the logo over it, were looked at together.

**Not done:**
- **Not applied in Steam:** Steam on this machine is the owner's, and adding a shortcut there changes their library. The owner's (or the cousin's) check.
- **Not checked against a client:** Steam's slot sizes are from Valve's library-asset sizes and current community guides, not checked against a client here.
- **Closure (2026-11-20):** accepted with Phase 3 by the owner (DECISIONS.md, "Phase 3 accepted and formally closed").

<a id="b-071"></a>
### B-071: Hidden hints left a small dot where the bar was

- **Category:** Bug (visual) · **Priority:** Low · **Platforms:** All with a controller
- **Components:** `ui` (`RefluxApp`, `ButtonPrompts`)
- **How found:** 2026-11-20, the owner's manual test of the build with B-067 in it.
- **Phase:** 3 (final validation) · **Status:** Verified (physical: the owner's manual test of the rebuilt app on Windows, 2026-11-20) · closed · **Last updated:** 2026-11-20
- **Related:** B-067, B-055

**Cause:** B-067 made hidden mean no hints at all, LT's own included, but the browsing bars stayed shown while a controller was in use. A bar with no hints was still its frosted pill (its padding on a round surface), drawn as a small dot.

**Fix.**
- **Both browsing bars hide with the hints** (the bottom bar beside the navigation, and the top bar).
- **The bar keeps its last hints while it fades out,** and draws nothing when it has none.

**Test:** `RailEntryTest.hiddenHintsLeaveNoBarBehind`: once the hints are hidden there is no bar, empty or not.
- **Owner-verified (2026-11-20):** the owner tested the rebuilt application on Windows by hand and confirmed the fix ("all fixed"). Closed with Phase 3.

<a id="b-072"></a>
### B-072: Right from the sidebar sometimes did not enter Home (an old issue)

- **Category:** Bug (focus) · **Priority:** High (the owner: Home unreachable from the sidebar by controller or keyboard) · **Platforms:** W, L, tablets (the sidebar or rail beside the page)
- **Components:** `ui` (`RefluxApp`: focus from the navigation into the page; `RailFocus`, `FocusEntry`)
- **How found:** 2026-11-20, the owner's manual test of the final build. The owner says it was an old issue, never recorded.
- **Phase:** 3 (final validation) · **Status:** Verified (physical: the owner's manual test of the rebuilt app on Windows, 2026-11-20) · closed · **Last updated:** 2026-11-20
- **Related:** B-008 (the same failure when nothing had focus), B-062

**Reproduction (the test harness, before the fix).** Choose Movies in the sidebar, then Up and A on Home, then Right after a delay:

| Delay after choosing Home | Result before the fix |
| --- | --- |
| 0 to 120 ms | Focus ended on nothing |
| 250 ms | Focus stayed in the sidebar |
| 400 ms | Worked |

A second Right always worked. Every slower route tried (by Left or Y, after Quick View or Details, with a work in progress, by keys or a controller: 18 in all) worked. That is the "sometimes": in the real app Home reads its library and artwork before it can offer a place to start, so the window was wider than in the harness. Movies, starting on a plain grid, offered sooner.

**Causes.**
1. **The page's start was taken from the leaving page.** The focus entry learned which page was on screen only when it was next composed. A Right in the same instant as the choice took the start of the page that was leaving, and focus went with that page.
2. **No wait from the sidebar.** With nothing to start on yet, Right fell to the toolkit's own search, which found the leaving page (nothing) or nothing at all (the sidebar). B-008 had added a wait only for the case where nothing has focus.
3. **The sidebar returned focus to any page.** It went back to the item last left without checking which page that item was on.

**Fix.**
- **The entry follows the navigator** at once, for Right and for Y.
- **Right from the sidebar waits for the chosen page's start,** for up to about 1.5 s and as long as focus is still in the sidebar on that page, before the toolkit's search.
- **Focus returns only to the same page:** the sidebar goes back to an item only on the page it was left from.

**Tests:**
- `RailEntryTest.rightFromTheSidebarEntersHomeHoweverSoonAfterChoosingIt`: 0, 16, 120 and 250 ms. It failed at every one of these delays before the fix (by the probe) and passes with it.
- `HomeFocusTest`, `NavigationTest`, `TelevisionTest`, `HintsTest` and `MiniPlayerTest` pass.
- **Owner-verified (2026-11-20):** the owner tested the rebuilt application on Windows by hand and confirmed the fix ("all fixed"). Closed with Phase 3.

<a id="b-073"></a>
### B-073: A Home row's paging arrows showed while a controller was in use

- **Category:** Bug (input modality) · **Priority:** Low · **Platforms:** W, L (a mouse beside a controller or keys)
- **Components:** `ui` (`HomeScreen` rows)
- **How found:** 2026-11-20, the owner's manual test.
- **Phase:** 3 (final validation) · **Status:** Verified (physical: the owner's manual test of the rebuilt app on Windows, 2026-11-20) · closed · **Last updated:** 2026-11-20
- **Related:** B-042 (the same rule for cards)

**Cause:** the arrows showed while the row was hovered. A mouse resting over the row stays "hovering" while a controller or the keys move, so the arrows stayed.

**Fix:** the arrows show only while the row is hovered and the mouse is the input in use. They fade when a controller or the keys move, and come back when the mouse moves.

**Test:** `RailEntryTest.aRowsArrowsAreTheMousesAndFadeWhenTheControllerMoves`.

<a id="b-074"></a>
### B-074: Two `SourcesUiTest` cases time out under the full UI and core run

- **Category:** Test reliability (not a product bug) · **Priority:** Low · **Platforms:** the test machine (Windows)
- **Components:** `ui` tests (`SourcesUiTest`)
- **How found:** 2026-11-20, the UI and core run after B-071 to B-073 (941 tests, 2 failures).
- **Phase:** **Beta 3** (test infrastructure; registered at Phase 3's closure, after the freeze) · **Status:** Investigating · **Last updated:** 2026-11-20
- **Related:** B-044 (the other known intermittents)

**Observed.** `aServerThatIsAwayOrStopsAnsweringIsRemovedAtOnce` ("Timed out waiting for the server's films") and `aServerThatIsAwayStaysListedAndInTheLibraryAndCanBeSignedInAgain` ("Timed out waiting for the notice") failed in the full `:ui:jvmTest :core:jvmTest` run.
- **Rerun alone:** `SourcesUiTest` passed 11/11 at once.
- **History:** both passed in the full `./gradlew check` of the same day before B-071 to B-073, and those changes touch nothing they exercise.

**Reading (not a confirmed cause).** Their waits on servers going away are too short when the machine is loaded by the whole suite. A Jellyfin test container was also running on the machine at the time.

**To do (Beta 3):** run them repeatedly under load, find what they wait on, and make the waits deterministic without weakening the test (CLAUDE.md: intermittents are not "fixed" by weakening).

## Beta 3 backlog (from Phase 3's closure, 2026-11-20)

Phase 3 is closed and Beta 2's fix scope is frozen (DECISIONS.md, 2026-11-20, "Phase 3 accepted and formally closed"). Every bug, UX issue, request or improvement found from now on is entered here, and in its own entry with the next free number, for **Beta 3 or a later release**, unless the owner explicitly authorises it as a critical Beta 2 blocker.

| Item | What | Status |
| --- | --- | --- |
| [B-074](#b-074) | Two `SourcesUiTest` cases time out under the full run | Investigating (test reliability) |
| [B-061](#b-061) | A Skia crash after a Direct3D failure (Windows, the owner's build) | Investigating; a Beta 3 candidate (the owner places it) |
| [B-044](#b-044) | Test intermittents mixing the test and wall clocks | Investigating (test infrastructure) |
| [B-056](#b-056) | Blank on closing the mini-player | Deferred by the owner (a later beta) |
| [F-003](#f-003) | Video-synchronised haptics | Deferred: a future version, **no release assigned**; needs its own research, hardware validation, design and plan first |

## Regression tracking

Every regression found, and every recurring failure watched, with the run that found it. A regression introduced by a Beta 2 change gets a new B-number in the section above and a row here.

| Date | What | Found by | Caused by Beta 2 work? | State |
| --- | --- | --- | --- | --- |
| 2026-11-18 | Phone Search: the scanning dot over the Search field | `ActivityIndicatorLayoutTest`'s first run | No: a second instance of B-005 | Fixed with B-005 |
| 2026-11-19 | Linux hint named `libmpv2` for Ubuntu 22.04-based derivatives | Writing the website's Mint tab | **Yes**: workstream 12's new hint, before commit | [B-043](#b-043), fixed before commit |
| 2026-11-18 | `JellyfinSourceTest` expected file-name versions to be editions | B-024's change | No: the test asserted the defect | Test corrected |
| 2026-11-19 | `PointerTest.controlsLeaveWhilePlayingAndReturnWithThePointer` failed in the GPL branch's full check | `./gradlew check --continue` | No (known intermittent, M12 backlog; the change touched no pointer or player code) | 3 isolated reruns 18/18; open in M12's backlog |
| 2026-11-19 | `AboutWindowTest` fails | GPL transition checks | No (fails on unmodified HEAD) | [B-045](#b-045) |
| 2026-11-19 | `AccessibilityTest.withoutAScreenReaderTheyStillGo` timed out | Forced full check on `0a0396f` | Not shown (file and player untouched by the commits) | [B-044](#b-044); 3/3 alone |

**Full-check history for Beta 2:** Phase 1 tree (uncommitted, before the license change): `./gradlew check --continue` BUILD SUCCESSFUL, 1,496 tests, 0 failures, 24 skipped. GPL branch: 1,496 tests, 1 failure (`PointerTest`, known). Committed tree `0a0396f`, forced (`--rerun-tasks`): BUILD FAILED in 10 min 53 s, 1,496 tests, 1 failure (B-044), 0 errors, 24 skipped. Hosted CI has not run on any of it (nothing pushed).

## Verification and evidence summary

Per fixed or investigated issue, by kind of verification. ✓ done · ✗ required and not done · — not required by the matrix · ◐ partial.

| Issue | Automated tests | Emulator | Physical device | Desktop/OS (VM, container) | Regression (full check) | Owner acceptance |
| --- | --- | --- | --- | --- | --- | --- |
| B-001 | ✓ (instrumented) | ✓ Google TV (server case too) | ✓ Pad 7, Phone 2a (with a server source), Fire TV | — | ✓ | Pending |
| B-003 | ✓ 15 new | — | ✗ live two-address server, device walk | — | ✓ | Pending |
| B-005 | ✓ layout, 5 sizes; on-device walk | ✓ Google TV | ✓ Phone 2a, Pad 7; ✗ Fire TV | — | ✓ | Pending |
| B-006 | ✓ fixture (JVM) | — | ✗ Android fixture | — | ✓ | Pending |
| B-018 | ✓ 9 new | — | ✗ walk of the reported case | ✓ Windows JVM | ✓ | Pending |
| B-022 | measurement | — | ✓ Windows timing (one machine) | — | — | Pending |
| B-024 | ✓ | — | ✗ Windows walk | ✓ Windows JVM | ✓ | Pending |
| B-026 | measurement (19 closes; 5 during playback) | — | ✓ owner's Windows machine (not reproduced) | — | — | Pending |
| B-031 | ✓ | — | ✗ physical Linux desktop | ✓ Ubuntu 26.04 VM desktop session (before and after, `en_US` and `de_DE`); WSL2 VM; Ubuntu 22.04/24.04, Debian 12, Fedora 41, Arch containers | ✓ | Pending |
| B-033 | ✓; on-device walk | ✓ Google TV | ✓ Phone 2a, Pad 7 | ✓ Windows JVM | ✓ | Pending |
| B-041 | measurement | ✓ Google TV cold start | ◐ Windows; Phone 2a, Pad 7 (cold start, memory, Home frames); Fire TV cold start | ◐ Linux in a VM (start, memory, close) | — | Pending |
| B-043 | ✓ | — | — | ◐ base packages in containers; Mint not run | ✓ | Pending |
| F-004 | ✓ 6 | — | — (nothing sent) | — | ✓ | Pending |
| B-025, B-008, B-007, B-013 | ✓ HomeFocusTest, InteractionContractTest, existing suites | ◐ Google TV (Search, keyboard) | ✗ controller walks (Windows, Fire TV) | — | ✓ full check, 1,537 tests, 0 failures (after a lint fix) | Pending |
| B-019 to B-021, B-023, B-011, B-004 | ✓ | ✓ Google TV (remote, Reflux keyboard) | ✗ physical controller; Fire TV | — | ✓ full check, 1,537 tests, 0 failures (after a lint fix) | Pending |
| B-039, B-012 | ✓ | — | ✗ physical controllers | — | ✓ full check, 1,537 tests, 0 failures (after a lint fix) | Pending |
| F-001, F-002 | ✓ HapticsTest + source scan | — | ✗ felt on device / controller | — | ✓ full check, 1,537 tests, 0 failures (after a lint fix) | Pending |
| B-034 to B-037, B-010 | ✓ | ✓ tablet emulator; Google TV (empty state) | ✓ Phone 2a; ✗ Pad 7 (PIN) | — | ✓ full check, 1,537 tests, 0 failures (after a lint fix) | Pending |
| B-009, B-032, B-015 | ✓ CardLayoutTest | — | ✗ Windows look with the owner's library | — | ✓ full check, 1,537 tests, 0 failures (after a lint fix) | Pending |
| B-002 | — | — | ✗ (stopped; owner session) | — | — | Pending |
| B-046, B-048 | ✓ | ✓ tablet emulator | ✓ Phone 2a | — | ✓ full check, 1,537 tests, 0 failures (after a lint fix) | Pending |
| B-047 | ✓ | — | ✓ Phone 2a (arc seen); owner to confirm | — | ✓ full check, 1,537 tests, 0 failures (after a lint fix) | Pending |
| B-049, Forgot PIN | ✓ ProfilesTest, ProfileSecurityTest | — | ✓ Phone 2a (system prompt opened, cancelled, nothing changed) | — | ✓ full check, 1,537 tests, 0 failures (after a lint fix) | Pending |

Machines (CLAUDE.md, "Testing environment"): Windows 11 development machine (physical); Xiaomi Pad 7, Nothing Phone (2a), Fire TV Stick 4K Plus (physical); Google TV emulator; WSL2 Ubuntu 24.04 (VM); Docker containers; the **Ubuntu 26.04 VirtualBox VM** (`Reflux-QA-Ubuntu-26.04`, created 2026-11-19: 4 vCPU, 8 GB, 60 GB, VMSVGA with 3D, 128 MB video memory; GNOME on Wayland; used for B-031's desktop walk and B-041's Linux baseline). Full runs: VERIFICATION.md, "Phase 1", "Open-source transition", "Website documentation portal…", and "Beta bug tracker and register reconciliation".

## Change history

| Date | Change | By |
| --- | --- | --- |
| 2026-11-19 | Tracker created from the owner's master tracker prompt, BETA-2.md's register, PHASE-1-PLAN.md, VERIFICATION.md, DECISIONS.md and the code at `0a0396f`. All of B-001 to B-042 and F-001 to F-005 entered once, with both source texts. Statuses moved to the tracker's eight words. **B-001** recorded as Fixed (BETA-2.md had "Verified on physical device"; its "different source types" criterion is not met). **B-042** shown as pending owner approval (BETA-2.md had listed it in Phase 2). **B-026** Not Reproduced. New issues **B-043** (Linux hint), **B-044** (`AccessibilityTest` timeout), **B-045** (`AboutWindowTest`). F-P1 and F-P2 kept. The per-issue text in BETA-2.md moved here; BETA-2.md keeps phases, order, exit criteria and the gate. | Claude (agent), for the owner's review |
| 2026-11-19 | **Phase 1 closeout walks.** B-001 → Verified (server-source case on Phone 2a and Pad 7, and on the Google TV emulator); B-033 → Verified (reported case walked on Phone 2a and Pad 7, and the emulator); B-005 walked on phone, tablet and TV emulator (Fire TV pending, stays Fixed); B-031 walked in the Ubuntu 26.04 VM's desktop session, before (beta.1 reproduces) and after, under `en_US` and `de_DE` (physical pending, stays Fixed); B-026 closed during playback 5 times, clean (Not Reproduced); B-041 baseline recorded on all six platforms (Linux VM; Android memory and Home frame pacing); B-044 passed in the next full check. Counts: 35 Not Started, 3 Investigating, 1 In Progress, 5 Fixed, 2 Verified, 1 Not Reproduced. | Claude (agent) |
| 2026-11-19 | **Phase 2 implemented** (the owner's Phase 2 brief; PHASE-2-PLAN.md; code commit `77b994c`).
- **New entries:** B-046 to B-049 (the brief's B-043 to B-046, renumbered because those IDs were taken) and F-P3 (Media Quick View, Phase 3, with X reserved).
- **Fixed:** B-004, B-007 to B-013, B-019 to B-021, B-023, B-025, B-032, B-034 to B-037, B-039, B-046 to B-049, F-001, F-002.
- **Not Reproduced:** B-015.
- **Investigating:** B-002 (reproduction stopped; the owner was using the machine).
- **Forgot PIN** decided and implemented. **F-004** provider evaluated (Aptabase, not integrated).
- **Counts:** 12 Not Started, 4 Investigating, 1 In Progress, 26 Fixed, 2 Verified, 2 Not Reproduced (47 original). | Claude (agent) |
| 2026-11-19 | Consistency check (throwaway script): 51 entries, one anchor each, no duplicates; every link and anchor in the tracker, BETA-2.md, CLAUDE.md, ROADMAP.md, DECISIONS.md, PHASE-1-PLAN.md and VERIFICATION.md resolves; every entry's status is one of the eight; the phase overview matches the entries; counts 35 Not Started, 3 Investigating, 1 In Progress, 7 Fixed, 1 Not Reproduced (47). 0 problems. | Claude (agent) |
| 2026-11-20 | **Phase 3 implemented** (the owner's Phase 3 work order; commits `dcef9fb`, `b2af2b7` and the closing commit; VERIFICATION.md, "Phase 3").
- **Fixed:** B-014, B-016, B-017, B-028, B-029, B-030, B-042 (believed fixed, it was not; reproduced first), B-045, F-P3, F-P4.
- **New:** B-061 (a Skia crash in the owner's build: Investigating) and B-062 (TV start focus: Fixed).
- **Investigating:** B-022 (measured with a breakdown), F-003 (feasibility shown, not shipped).
- **Checkpoints:** B-041 and F-004 second checkpoints recorded.
- **V1.1 candidates (the work order):** B-027, B-038, B-040 researched (V1.1-LIBRARY-RESEARCH.md), not implemented.
- **B-044:** cause found (mixed virtual and wall clocks), not changed. | Claude (agent) |
| 2026-11-20 | **Phase 3 final validation** (the owner's physical controller and mouse walk on Windows, and the owner's steers on Linux scaling and Steam).
- **B-042 Verified (physical):** with a mouse resting over one card and a controller moving, only the controller's card lifts; the owner likes it, and it is unchanged.
- **New and Fixed:** B-063 (Linux scale), B-064 (keyboard following the mouse), B-065 (Tab and the key glyphs), B-066 (profile management), B-067 (player hints and LT), B-068 (the mini-player back to the full player), B-069 (Quick View look; the Trickplay hint), B-070 (Steam artwork).
- **F-003:** Deferred by the owner as a future feature.
- **A stale on-device test:** the Phase 2 Fire TV walk still said "X does nothing" (Quick View since F-P3); updated. | Claude (agent) |
| 2026-11-20 | **The owner's manual test of the final build:** B-071 (hidden hints left a dot), B-072 (Right from the sidebar into Home, an old issue never recorded), B-073 (row arrows with a controller): found, reproduced in tests, and fixed. | Claude (agent) |
- **Owner-verified (2026-11-20):** the owner tested the rebuilt application on Windows by hand and confirmed the fix ("all fixed"). Closed with Phase 3.
| 2026-11-20 | **Phase 3 accepted and formally closed by the owner.**
- **Owner-verified and closed:** B-071, B-072, B-073 (the owner's manual test of the rebuilt app).
- **Reconciled with the evidence:** B-016, B-017, B-029, B-030 and F-P3 are Verified (physical walks, the owner's among them, which still read "pending"); B-041 is Fixed (both checkpoints); the Phase 3 final-validation fixes are recorded as in the accepted build.
- **Carried open:** B-022, B-044, B-061, F-004 and the deferred items.
- **New:** B-074, the two timing-sensitive `SourcesUiTest` cases, for Beta 3.
- **Freeze:** a Beta 3 backlog section opened. New findings go there unless the owner authorises a critical Beta 2 blocker.
- **Counts:** 82 entries: 11 Verified, 56 Fixed, 5 Investigating, 1 In Progress, 2 Deferred, 5 Not Started, 2 Not Reproduced. | Claude (agent), for the owner |
