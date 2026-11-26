# Beta bug tracker: every finding, its status, and its evidence

**Purpose.** The permanent, detailed record of every bug, UX issue, product improvement, feature request, investigation, performance concern, and verification task found in Beta testing and during Beta 2 development: what was reported, what is known, what was done, what was tested and where, and what is left. A future developer or agent should be able to pick up any issue from its entry alone.

**Scope.** B-001 to B-042 and F-001 to F-005 (the owner's original findings), two registered sidebar proposals (F-P1, F-P2), and every issue found since (B-043 onward). Release gates, phase scope, phase order, and exit criteria are **not** here: they are in [BETA-2.md](history/phases/BETA-2-PHASES.md), which links back to this file. What each test run was, in full, is in [VERIFICATION.md](verification/VERIFICATION.md); decisions and their reasons are in [DECISIONS.md](decisions/DECISIONS.md).

**Last updated:** 2026-11-26: the Beta 3 integration of the Ubuntu validation ([BETA-3-INTEGRATION-AUDIT.md](verification/BETA-3-INTEGRATION-AUDIT.md)), and the Ubuntu validation itself (B-104; Ubuntu results on B-077, B-084, B-089, B-092, B-099, B-101 to B-103; [UBUNTU-VALIDATION-REPORT.md](verification/UBUNTU-VALIDATION-REPORT.md)). Before that, 2026-11-25: the Windows repository audit before the move to Ubuntu (statuses reconciled with the final CachyOS session; [the review-branch work's release placement](#review-branch-work-and-its-release-placement-2026-11-25); [how Beta 2 testing findings are entered](#entering-beta-2-testing-findings)). Before that, 2026-11-24 and 25, the CachyOS final audit, the Windows acceptance audit and the owner's final CachyOS session (B-082 to B-103, F-P1; [their ledger](verification/CACHYOS-FINAL-AUDIT-LEDGER.md)). Before that, 2026-11-20, Phase 3 implementation (branch `open-source-gpl`; Phase 3's commits from `dcef9fb`). Phase 3's summary and walks: VERIFICATION.md, "Phase 3".

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
| **Verified** | The acceptance criteria are satisfied and the verification the [matrix](history/phases/BETA-2-PHASES.md#verification-matrix-what-each-item-must-be-verified-on) requires exists. |
| **Blocked** | Work cannot proceed because of an identified dependency or blocker. |
| **Deferred** | Explicitly moved out of current scope by an authorised (owner) decision. |
| **Not Reproduced** | Investigation has failed to reproduce the issue. This does **not** mean it is resolved. |

**Verification is reported by kind, never merged.** Each entry keeps these apart: *automated tests passed* · *emulator verification* · *physical-device verification* · *desktop/OS-specific verification* (a VM or container is named as such) · *regression testing* · *owner acceptance*. An issue is never Verified because a patch compiles or a unit test passes when its acceptance needs a device, a platform, an integration, or a manual walk. Owner acceptance is pending for every issue until the owner accepts the phase that holds it.

**Rules for keeping it** (also in [CLAUDE.md](../CLAUDE.md)):

- Read an issue's entry before working on it; update the entry **as the work happens** (investigation started, reproduction found, cause confirmed or rejected, fix landed, tests added, runs on each kind of environment, platform differences, regressions, blockers, acceptance), not at the end of a phase. Code changed without its tracker entry updated is incomplete work.
- IDs are stable and never reused or deleted; a fixed issue stays, with its history.
- A new issue takes the next free ID (B-043, B-044, …; a new feature F-006 onward, once the owner has numbered F-P1 and F-P2), keeps its description as found, says how it was found, and is allocated only by existing scope or an owner decision.
- A phase change or a deferral needs the owner's decision, recorded in DECISIONS.md and in the [change history](#change-history).
- Nothing is invented to fill a field: unknown is written as unknown or pending.

**Sources of each entry.** *Original description* is the owner's master tracker prompt of 2026-11-19 (authoritative for the findings and the phase plan). *Findings document* is the owner's "Reflux Beta Testing Findings" (build 0.9.0-beta.1, 18 November 2026), whose text was first registered in BETA-2.md and is kept here word for word where it adds detail (reproduction notes, screenshots, corrections). Where the two disagree, both are kept and the difference is listed under [owner decisions](#unassigned-issues-and-unresolved-owner-decisions).

## Executive status summary

**Phase 4 (2026-11-21):** one entry added, **B-075** (release tooling: store "What's new" text), **Fixed**, which makes 83 entries and 57 Fixed (and at the Beta 2 release, **B-076**, Fixed by the owner's scoped fix, **B-077**, a test race, Open for Beta 3, **B-078**, stale window tests, Fixed, **B-079**, the Linux CI time limit, Fixed by the owner's decision, **B-080**, a live-server test, and **B-081**, a window-test intermittent, Open for Beta 3: 89 entries); B-074 gained Phase 4 evidence (not reproduced in two full runs, see its entry) and stays Investigating. No other status changed: Phase 4 changed no product behaviour. The table below is as of Phase 3's closure.

**The CachyOS final audit (2026-11-24, the owner's brief):** of these, only B-082, B-083 and B-087 are bug fixes; F-P1 (the sidebar shrink), B-085 (the keyboard redesign) and B-086 (the PIN pad) are features and redesigns of the kind Beta 2's freeze sends to Beta 3, done on the brief and for the owner to place in a release (the ledger's "What kind of change each is"). nine entries added, which makes **98**: B-082, B-083, B-085, B-086, B-087 **Fixed**; B-084 **Fixed** (one window on Linux; the owner's check pending); B-088 **Blocked** (no device); B-089 **Open** (test reliability, pre-existing); B-090 **Open** (pre-existing, reproduced on the baseline). F-P1 Not Started → **Fixed**. The session's own record is [CACHYOS-FINAL-AUDIT-LEDGER.md](verification/CACHYOS-FINAL-AUDIT-LEDGER.md).

**The Windows acceptance audit (2026-11-24) and the owner's final CachyOS session (2026-11-25):** thirteen entries added, which makes **111** (B-001 to B-103, F-001 to F-005, F-P1 to F-P4). Fixed on the review branch `cachyos-final-audit`: B-091 (a regression the branch introduced, Start on Quick View), B-092 (Linux fallback window tests: test synchronisation, plus a one-frame production lag), B-093 and B-097 (test fixes), B-099 (the integrated renderer's context replacement), B-102 (Reflux keeps its own Linux desktop entry, a feature by the owner's choice); B-098 **Resolved** by the owner's decision. Open: B-094 (Fire TV, unattributed), B-095 (emulator data, pre-existing), B-096 (Android accessibility, pre-existing), B-100 (fallback square corners, pre-existing), B-101 (not reproduced), B-103 (the owner's decision). None of the review branch's fixes is in a released build; `main` is Beta 2 as released (`1d65dcf`). Where each belongs: [Review-branch work and its release placement](#review-branch-work-and-its-release-placement-2026-11-25).

**The Ubuntu validation and the Beta 3 integration (2026-11-26):** two entries added, which makes **113** (B-001 to B-105, F-001 to F-005, F-P1 to F-P4): B-104 (Linux integrated player decodes on the NVIDIA GPU; Open, cause established, fix proposed) and B-105 (a Windows CI test race; Fixed, test only).

**The CI performance work (2026-11-26):** one entry added, which makes **114** (B-001 to B-106, F-001 to F-005, F-P1 to F-P4): B-106 (`AppState`'s start-up coroutines and construction order; a test race, Investigating).

**The attribution cleanup and the testing handoff (2026-11-27):** one entry added, which makes **115** (B-001 to B-107, F-001 to F-005, F-P1 to F-P4): B-107 (`SilentSourceTest`, an intermittent window test, Open).

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

Scope, order, dependencies, exit criteria, and the gate: [BETA-2.md](history/phases/BETA-2-PHASES.md#the-three-phases-the-owners-order). Status here.

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
| — | [B-027](#b-027), [B-038](#b-038), [B-040](#b-040) | **V1.1 candidates** (the work order): researched ([V1.1-LIBRARY-RESEARCH.md](product/V1.1-LIBRARY-RESEARCH.md)), not implemented |
| + | Final validation: [B-063](#b-063) Linux display scale | Fixed (Ubuntu VM only; physical Linux and the Surface not tested: accepted limitation) |
| + | [B-064](#b-064), [B-065](#b-065) The keyboard following the mouse; Tab, ⌫, spacebar | Fixed (in the accepted build) |
| + | [B-066](#b-066) Profile management | Fixed (in the accepted build) |
| + | [B-067](#b-067), [B-068](#b-068) LT hides every hint; RS back to the full player | Fixed (in the accepted build) |
| + | [B-069](#b-069) Quick View look; the Trickplay hint removed | Fixed (in the accepted build) |
| + | [B-070](#b-070) Steam artwork | Fixed (files only; never applied in Steam: accepted limitation) |
| + | [B-071](#b-071), [B-072](#b-072), [B-073](#b-073) The empty hint bar; Right from the sidebar into Home; row arrows | **Verified by the owner** (manual test of the rebuilt app) and closed |
| ✓ | **Phase 3** | **Accepted and formally closed by the owner, 2026-11-20** ([BETA-2.md, "Phase 3 closure"](history/phases/BETA-2-PHASES.md#phase-3-closure-2026-11-20-accepted-and-formally-closed-by-the-owner)) |

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
- **Components (likely):** `ui` (input handling, `Controls`); docs/product/INPUT_MODEL.md
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
- **Components (likely):** `ui` (`ProfilesScreen`, start-up), `library` (profiles); docs/product/WATCH_STATE.md ("Profiles")
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
- **Components (likely):** `ui` (`Focus`, `Navigator`, `Controls`, every screen); `android:app` and `desktop:app` input; docs/product/INPUT_MODEL.md, docs/product/NAVIGATION.md
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
- **Phase:** **V1.1 candidate** (the owner's Phase 3 work order, 2026-11-20: researched in Phase 3, not implemented) · **Status:** Not Started (researched: [V1.1-LIBRARY-RESEARCH.md](product/V1.1-LIBRARY-RESEARCH.md)) · **Last updated:** 2026-11-20
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

- **Implementation:** docs/product/PLAYBACK.md, "Trickplay". `core/input/StickStepper` (dead zone 0.5 engage / 0.3 release, one step at once, repeats 260→70 ms by deflection, ×3 and ×8 steps after 2 and 4 s held); `PlaybackSession.rightStick` with `SceneScrub(origin, target)`: a scene is a preview picture, 10 s where there are none; playback does not move until **A** (`commitScrub`), **B** stays (`cancelScrub`), D-pad left/right fine-tune, 8 s untouched or the controller lost (`stickLost`) ends it without seeking. Only in the full player; nothing while browsing.
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

- **Research:** Jellyfin (10.9+) keeps trickplay tiles per media source and width when its administrator turns on extraction (`BaseItemDto.Trickplay`, `Videos/{item}/Trickplay/{width}/{index}.jpg`); Plex keeps a BIF index per part when its owner turns on video preview thumbnails (`/library/parts/{id}/indexes/sd`). Neither is generated by Reflux: local generation would compete with playback for the decoder and is a later investigation. Recorded in docs/product/PLAYBACK.md, "Trickplay".
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
- **Phase:** **V1.1 candidate** (the owner's Phase 3 work order, 2026-11-20: researched in Phase 3, not implemented) · **Status:** Not Started (researched: [V1.1-LIBRARY-RESEARCH.md](product/V1.1-LIBRARY-RESEARCH.md)) · **Last updated:** 2026-11-20
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
- **Components (likely):** `ui` (input handling, hints); docs/product/INPUT_MODEL.md
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
- **Phase:** **V1.1 candidate** (the owner's Phase 3 work order, 2026-11-20: researched in Phase 3, not implemented) · **Status:** Not Started (researched: [V1.1-LIBRARY-RESEARCH.md](product/V1.1-LIBRARY-RESEARCH.md)) · **Last updated:** 2026-11-20
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
- **The owner's decision (2026-11-20, Phase 3's closure):** video-synchronised haptics are deferred as a future experimental feature. They are not part of Phase 3 or Beta 2, are not to be implemented, shipped or enabled, and have no release yet. V1.2, V1.3, V1.4 and Version 2 are possible, deliberately undecided, and Phase 4's roadmap work places them ([PHASE-4-HANDOFF.md](history/phases/PHASE-4-HANDOFF.md)). Before then they need their own research, design, hardware validation, synchronisation investigation, platform analysis, safety controls, and implementation and testing plan. The findings below are kept for that work.
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
- **Components:** `core` (`dev.reflux.core.telemetry`: `TelemetryEvent`, `TelemetryContext`, `TelemetryConsent`, `ConsentStore`, `TelemetryTransport`, `TelemetryRecorder`); docs/release/PRIVACY.md
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

- **F-P1 Collapsible sidebar.** Expanded (icon and label) and collapsed (icons only); cross-platform and accessible; desktop, phone, tablet, Android TV, and Fire TV considered separately; preference persisted where appropriate; transitions tested; no one behaviour or size forced on every form factor. **Phase:** the owner's CachyOS audit brief (2026-11-24), which specified it. **Status:** Fixed (automated and renders; walks with a controller outstanding).
  - **As specified:** a controller's Start (≡) toggles the sidebar between icons with names and icons alone, focus staying where it is; Y still goes into it. Phone unchanged.
  - **Implementation:** `SemanticAction.COLLAPSE_NAVIGATION` (Start while browsing); `AppState.navigationCollapsed`, kept as a setting; `LayoutProfile.navigationWidthDp` (76 dp shrunk), which the page's insets, the page bar and the grid's columns follow; the shrunk sidebar shows the mark and the icons, each destination keeping its name for a screen reader. For a pointer, a finger or a keyboard, Reflux's name at the head of the sidebar is the toggle ("Reflux, shrink sidebar" / "Reflux, expand sidebar"; the owner's correction of 2026-11-24 replaced a button at the sidebar's foot); Y never shrinks it. Only `SIDEBAR` shrinks: a tablet's rail (the Pad 7, about 1164 dp, is always one) already has small icons with names beneath, and a television's rail is icons until focused; there Start opens Sources as before. Hints: "Shrink sidebar" or "Expand sidebar".
  - **Tests:** `SidebarCollapseTest` (5, new: Start shrinks and expands without moving focus, Y and the sidebar still work shrunk, the choice survives a restart, a tablet-sized window keeps Start on Sources, the name toggles for a pointer and a finger with no other button, Y never shrinks it); `SourcesInlineAndBrandHomeTest` (the tablet's name still goes Home); `ShellSurfaceTest`; `DirectionalInputTest.theSourcesPageIsAButtonAwayAndBackIsTheWayOut` now reaches Sources through the sidebar (Start shrinks it there). Renders looked at.
  - **Ledger:** CACHY-004 in [the CachyOS audit ledger](verification/CACHYOS-FINAL-AUDIT-LEDGER.md).
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
- Recorded for Phase 3: Menu/Options currently opens Sources while browsing, and Quick View's Menu trigger on a focused item has to be reconciled with that (docs/product/INPUT_MODEL.md, "Controller mapping").

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
- **Plan (full research, design, acceptance and tests):** [F-P4-ADAPTIVE-KEYBOARD.md](history/phases/F-P4-ADAPTIVE-KEYBOARD.md)

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
- **Phase:** **Beta 3** (test infrastructure; registered at Phase 3's closure, after the freeze) · **Status:** Investigating (cause not found; the wait now reports its state) · **Last updated:** 2026-11-25
- **Related:** B-044 (the other known intermittents)

**Observed.** `aServerThatIsAwayOrStopsAnsweringIsRemovedAtOnce` ("Timed out waiting for the server's films") and `aServerThatIsAwayStaysListedAndInTheLibraryAndCanBeSignedInAgain` ("Timed out waiting for the notice") failed in the full `:ui:jvmTest :core:jvmTest` run.
- **Rerun alone:** `SourcesUiTest` passed 11/11 at once.
- **History:** both passed in the full `./gradlew check` of the same day before B-071 to B-073, and those changes touch nothing they exercise.

**Reading (not a confirmed cause).** Their waits on servers going away are too short when the machine is loaded by the whole suite. A Jellyfin test container was also running on the machine at the time.

**To do (Beta 3):** run them repeatedly under load, find what they wait on, and make the waits deterministic without weakening the test (CLAUDE.md: intermittents are not "fixed" by weakening).

**Phase 4 (2026-11-21).**
- **What they wait on:** `Harness.await` is Compose's `waitUntil` with `PATIENCE_MS` = 30 s of wall-clock time; the two waits are for the library pipeline (the server's films arriving in `world.library`) and for the notice, both driven by coroutines on real dispatchers, not by the test clock. Under a loaded machine 30 s of wall clock can pass while the software-rendered frames and the scan compete for CPU; the reading above stands and is still not a confirmed cause.
- **Fresh full run:** `./gradlew check` at the start of Phase 4 (Windows 11, no Docker or emulator running, 16.8 GB free): `:ui:jvmTest` 548 tests, **0 failures**; `SourcesUiTest` passed in the full run. Not reproduced.
- **Second sample:** `./gradlew check --rerun-tasks` at Phase 4's end (every task executed): `:ui:jvmTest` 548, **0 failures**. Two full runs in a row without the failure; with Phase 3's failing run on a machine also running a Jellyfin container, load remains the likeliest reading.
- **Disposition:** stays **Investigating**, Beta 3 (test infrastructure). No test was changed: lengthening the patience would only hide a slow pipeline, and the fix is to make the wait deterministic (drive the pipeline from the test's dispatcher), which is Beta 3 work.

**The Windows acceptance audit (2026-11-24/25).**
- **Seen again, on Linux this time:** hosted CI run 45460552173 on `cachyos-final-audit` at `4be4c45`, Check (ubuntu-24.04), `:ui:allTests` 567, 1 failed: `aServerThatIsAwayOrStopsAnsweringIsRemovedAtOnce`, "Timed out waiting for the server's films" (line 533) after 30 s. The class's other tests took 2.7 to 22 s each on that runner. The branch changes nothing this test exercises (sources, the connect form, the pipeline).
- **Measured (Windows 11, a temporary probe in the test, removed afterwards: the pipeline's activity, the sources, the films and the notice every 20 ms from the sign-in):** unloaded, the films arrive **1.5 s** after the form is filled (sign-in 1 s, the source listed at 1.2 s, its scan at 1.5 s); the startup refresh had ended before the test reached the form. **Pinned to one CPU core** (the whole build, `start /affinity 0x1`, `--no-daemon`), three runs: **1.4 to 1.8 s**. The **full `:ui:jvmTest :core:jvmTest` run pinned to two cores**, twice (15 to 16 min each): **under 0.5 s**, both PASS. With the full `./gradlew check` earlier in the audit: **8 of 8 PASS** here.
- **Ruled out, by reading and by those measurements:** CPU load on the test (it barely slows at one core); state left in `HomeNetwork` by another test (`world()` resets it; `AccessibilityTest` only reads it; the UI tests run one at a time); a refresh dropped while another runs (`LibraryPipeline.refresh` queues behind one lock and joins a waiting request for the same source; nothing is dropped); the startup refresh holding that lock (over before the form here; its enrichment cannot wait: `FakeCatalog` has no delays and `ProviderGate` throws at once while resting); work left by earlier tests in the same JVM (the full suites on two cores did not slow it).
- **Not found:** the cause. The wait at line 533, unlike its twin at line 552, said nothing of the app's state when it timed out, so no failure so far has left evidence of where it stuck.
- **Changed:** the wait now reports, on timeout, where the app is, the pipeline's activity, each source with its availability, the films counted, the notice and the platform's reports (as line 552 already did). The patience is unchanged (30 s): not weakened. This is an investigation step, not a fix.
- **Next:** read that state from the next hosted Linux failure; until then, re-run and record both runs (CLAUDE.md, "Known intermittents").

<a id="b-075"></a>
### B-075: Store "What's new" text included category headings

- **Category:** Release tooling (not in the app) · **Priority:** Medium (it would have reached both stores' listings) · **Platforms:** Google Play and Amazon Appstore release notes
- **Components:** `tools/release/release.py` (`plain_text`, `store_text`)
- **How found:** 2026-11-21, Phase 4, while grouping every CHANGELOG release under categories: Phase 3 had introduced `###` category headings in Unreleased, and `plain_text` treats any non-empty line that is not a `- ` item as an item, so Beta 2's store text would have begun "• ### Playback & Media".
- **Phase:** Phase 4 (release infrastructure, in the owner's Phase 4 scope; no product code) · **Status:** Fixed · **Last updated:** 2026-11-21

**Second part (found in the Phase 4 rehearsal).** Unreleased's preface paragraph ("Not in a downloadable build yet…") became the first store bullet; promotion renames only the heading, so it would have shipped too.

**Fix.** Lines starting with `#` end the current item and are skipped; a paragraph before the first item is skipped (a paragraph after the list is still kept, as an existing test requires). **Tests:** `test_category_headings_are_not_store_items`, `test_a_preface_before_the_list_is_not_a_store_item` (new); `python -I -m unittest discover -s tools/release -p "test_*.py"`: 49 tests, OK. **Rehearsed:** `release.py notes --version 0.9.0-beta.2 --allow-unreleased` on the real CHANGELOG: store text 446 of 500 characters, starting with the first change, no heading. **Not verified:** a run in the release workflow (no release has been cut). **For the owner:** Beta 2's long items leave room for two in Play's 500 characters; BETA-NOTES.md's highlights can replace them by hand.

<a id="b-076"></a>
### B-076: On the Downloads page, a remote's Right can be lost just after the list changes (CI, Linux)

- **Category:** Focus timing (TV and controllers) · **Priority:** Low · **Platforms:** found in a test on the hosted Linux runner
- **Components:** `ui` (`DownloadsUiTest.aRemoteReachesEveryDownloadControlAndFocusComesBack`; the television's focus-entry rule)
- **How found:** 2026-11-21, hosted CI run 44912262295 on `ecce561` (the `0.9.0-beta.2` promotion): Check (ubuntu-24.04), `:ui:jvmTest` 548 tests, 1 failed: "Timed out waiting for focus on its removal. Instead: [the download's row]" (`DownloadsUiTest.kt:441`), after a second copy was added and the page opened, one Right press did not move focus to the row's removal button in 30 s.
- **Phase:** Beta 2 (a release blocker by the owner's decision of 2026-11-21: a scoped fix before release) · **Status:** Fixed (hosted CI green: run 44994139313 on `ff98a0d`) · **Last updated:** 2026-11-22

**Diagnosis (2026-11-21, Linux CI image locally, `reflux-linux-check`):**
- **Deterministic on Linux, not intermittent:** the hosted rerun of the failed job failed the same way, and the test failed 2 of 2 in the Linux image locally; it passes on Windows (hosted and local, 6 of 6).
- **Bisected** between the last green hosted Linux CI (`044c8f0`, main, 2026-11-17) and the release: the first failing commit is **`77b994c`**, "Beta 2 Phase 2: interaction contract, Reflux keyboard, controller mapping…"; its parent passes. B-072's `03b4ec2` is not the cause (its parent fails too).
- **What happens** (instrumented in a throwaway worktree, nothing committed): on the Downloads page the Right press reaches the toolkit's `moveFocus(Right)` through the ordinary path (focus in the page, not in the navigation), which **returns true**; the removal button is focusable and enabled, and the row and button bounds are the same as on Windows (only a 17 px vertical offset from font metrics). Focus is then **pulled back to the page's first row** before the test's next check: a focus-restart in the page's start rule, the family of the M11 race, newly reachable since Phase 2's contract.
- **Narrowed further (the owner authorised a scoped fix, 2026-11-21; eight instrumented runs in the Linux image, none committed):**
  - **The first Right after arriving is consumed:** with one press and then 3 s of the app's clock advanced, focus never reaches the removal button; a **second** press (after 100 ms) does. Advancing 2 s and requesting the button directly also lets the test pass.
  - **Ruled out:** the television start rule re-placing focus (no programmatic focus request happens after the press; stopping that loop on the viewer's move changed nothing); the toolkit's geometric search (an explicit `focusProperties { right = … }` from the line to its first action fails the same way); `FocusDirection.Enter` in `FocusEntry.start` (a plain request fails the same way; `Enter` dates from October anyway); the line taking focus itself on arrival (`initialFocus(onArrival = true)`); the input modality (already DIRECTIONAL before the press); the notice (still shown when the second press works); geometry (identical horizontally on Windows and Linux).
  - **So:** something in Phase 2's `77b994c` makes the first directional press after this arrival a no-op on Linux timing, while `moveFocus` reports success. Next step: bisect *within* `77b994c` by reverting its `RefluxApp.kt`/`AppState.kt` hunks one at a time in the Linux image (about 3 minutes a run).
- **Release consequence:** none of the candidate fixes held, so none was kept; under the owner's choice (fix before release) **Beta 2 is held** until this is fixed and hosted CI is green.

**Intra-commit bisect of `77b994c` (2026-11-22, Linux CI image, a throwaway worktree at `77b994c`; one change reversed per run, `DownloadsUiTest.aRemoteReaches…` once each):**

| Reversed (alone) | Result |
| --- | --- |
| nothing (baseline `77b994c`) | **Fails** (reproduced) |
| `RefluxApp.kt`: the directional routing (hunks 3+4, and 4 alone) | Fails |
| `RefluxApp.kt`: the text-entry bridge (5); the docked chrome condition (6); the keyboard panel (7); the page prompts (10); the activity arc (12+13) | Fails, each |
| `AppState.kt`: controller dispatch (6); the move's haptic feedback (7) | Fails, each |
| `RefluxApp.kt` 8+11, 0+1+2; `AppState.kt` 0, 1+2, 3, 4+5+8+9, 10–15 | Do not build alone (later code depends on them); not decisive |
| `components/Focus.kt` (its whole change in `77b994c`) | **Passes** |
| `Controls.kt`, `Artwork.kt`, `Platform.kt` | Do not build alone |

- **Cause:** `77b994c` changed `rememberFocusReturn` so an item becomes the one to come back to as soon as it **has focus** (B-008, `onFocusChanged` sets `entry.left`). An item's `returning` used to change only while the page was away; now it flips to true *while the item is on screen*, at its next recomposition after gaining focus. That recomposition enters the return branch for the first time, so its `LaunchedEffect` starts and **requests focus back onto the item**. On the Downloads page the line gains focus on arrival and recomposes as its copy's state settles: on Windows before the test's Right, on Linux's slower timing **after** it, so the Right's move (which `moveFocus` reported as done) was undone. The earlier instrumentation watched only `FocusEntry`'s requests, which is why it saw no programmatic request.
- **Fix** (`ui/.../components/Focus.kt`, `rememberFocusReturn`): focus is put back on an item only if it was the one to come back to **when it appeared** (`cameBackTo = remember(entry, screen) { returning }`). Coming back to a page composes its items afresh, so that case is unchanged; the item's offer to the focus entry (`DisposableEffect`) is unchanged, so B-008's return still works through it. Five lines; no test changed.
- **Regression test:** `FocusEntryTest.anItemThatBecameTheOneToComeBackToDoesNotTakeFocusBackWhenItRecomposes`: a television layout, a line and its action; focus on the line, then the action, then the line recomposes. **Fails without the fix on Windows** (focus back on the line), so it reproduces B-076 on every OS without depending on timing; passes with it.
- **Verification so far (2026-11-22):** Windows: `FocusEntryTest`, `DownloadsUiTest`, `HomeFocusTest`, `DetailsFocusTest`, `NavigationTest`, `TelevisionTest` pass. Linux CI image: `DownloadsUiTest` **3 of 3** pass (was 0 of 2 and failing at every Phase 2 hunk), `FocusEntryTest` passes. The full local check and hosted CI are in VERIFICATION.md, "B-076 fix".

**Evidence so far.** Passed on the hosted Windows runner in the same run, in the local full `check --rerun-tasks` on the same commit (1,650 tests, 0 failures), and 6 of 6 repeated local runs of `DownloadsUiTest` (Windows). No product or test code changed since Phase 3's accepted build. The key was lost, not slow: the likeliest reading (not confirmed) is the same family as the M11 race (history/verification/VERIFICATION-ALPHA-TO-BETA-1.md, "Hosted CI on bf71793"): the page's focus-entry rule placing focus again when its first item changes, after the key had moved it. A failed-job rerun of the CI run is recorded in VERIFICATION.md, "Beta 2 release".

**Release decision (2026-11-21, superseded the same day):** first judged not a blocker; then, with hosted CI red on every Linux run, the owner chose a scoped fix before release, so it **blocked Beta 2's gate (step 8)** until fixed; fixed 2026-11-22 (above), the test unchanged.

<a id="b-077"></a>
### B-077: `MpvEngineTest.theEnginesLogCarriesNoCredentials` can fail with a ConcurrentModificationException (test race)

- **Category:** Test reliability · **Priority:** Low · **Platforms:** hosted Linux runner (four times by 2026-11-26)
- **Components:** `playback/mpv` tests only (`MpvEngineTest.kt`, `theEnginesLogCarriesNoCredentials`)
- **How found:** 2026-11-21, hosted CI run 44934158483 on `9604cc5`, Check (ubuntu-24.04): `:playback:mpv:test` 60 tests, 1 failed: `java.util.ConcurrentModificationException`. It passed in the next run (44941967966 on `dc70958`) and in every local run.
- **Phase:** Beta 3 (found after the freeze; not covered by the owner's B-076 authorisation) · **Status:** Open (seen four times on hosted Linux) · **Last updated:** 2026-11-26
- **Cause (from the code, not yet reproduced):** the test collects the engine's log lines into `Collections.synchronizedList` from mpv's log thread, and reads it with `lines.none { … }` / `lines.any { … }` (an iteration) without holding the list's lock, while the engine may still be appending; a synchronized list must be iterated under its own lock. The engine and its redaction are not involved: what the test checks (no token in the log) is unaffected.
- **Fix (Beta 3):** iterate a snapshot taken under the lock (`synchronized(lines) { lines.toList() }`), without loosening what is asserted.
- **Seen again (2026-11-25):** hosted CI on `406382b`, the Linux check's first attempt (CI 45585080925): `theEnginesLogCarriesNoCredentials` FAILED with a `ConcurrentModificationException` (reported at line 512, Kotlin's inline mapping of `lines.none { … }`), so the window tests after it did not run; the re-run of the job passed. Also the cause of the stop on `0377cab` (CACHYOS-FINAL-AUDIT-LEDGER.md). Still Beta 3 by the tracker; it now blocks a green hosted Linux check now and then: a candidate for the owner to authorise early.
- **Ubuntu (2026-11-26):** passed in the CI-equivalent unit check on the owner's laptop under Ubuntu 26.04.1 (one run; it is intermittent, so this decides nothing). Still open. [UBUNTU-VALIDATION-REPORT.md](verification/UBUNTU-VALIDATION-REPORT.md)
- **Seen again (2026-11-26), the fourth time on hosted Linux:** CI [45829860032](https://github.com/Rifaque/reflux/actions/runs/45829860032) on `b823a74` (a documentation commit; `MpvEngineTest.kt` unchanged since `406382b`), Check (ubuntu-24.04): `theEnginesLogCarriesNoCredentials` FAILED, `ConcurrentModificationException` at `MpvEngineTest.kt:512`, as on `406382b`; the build stopped, so the desktop window tests did not run. Not re-run (the owner's instruction: no retry to obtain a green result). The occurrences: CI 44934158483 (`9604cc5`), the stop on `0377cab` (CI 45379331219), CI 45585080925 (`406382b`, first attempt), CI 45829860032 (`b823a74`). The specified fix is unchanged and not implemented.

<a id="b-078"></a>
### B-078: Three real-window tests still expected the interface before Phase 2 (stale tests, CI Linux)

- **Category:** Test maintenance (stale expectations) · **Priority:** High for the release (it blocks hosted CI), none for viewers · **Platforms:** the Linux CI job's "Desktop window tests" step (Xvfb); these tests run nowhere else by default
- **Components:** `desktop/app` tests only: `BrowsingWindowTest` (`theMouseBrowsesTheRealWindow`, `aControllerBrowsesTheRealWindow`), `EmbeddedPlaybackTest` (`playsInsideTheWindowAndAnswersToTheMouse`)
- **How found:** 2026-11-22, hosted CI run 44941967966 attempt 2 on `dc70958`: with B-076 fixed, Linux's "Build and test" step passed for the first time since Phase 2 (`BUILD SUCCESSFUL in 51m 32s`), so the window tests after it ran; 77 tests, 3 failed. Every Linux run since `77b994c` had stopped before this step, and Phase 3 ran only `AboutWindowTest` (B-045), so the three had not run since Phase 2.
- **Phase:** Beta 2 (needed for the release's CI gate; test code only, no product change) · **Status:** Fixed (hosted CI green: run 44994139313) · **Last updated:** 2026-11-22
- **Cause, each an accepted Phase 2 change the test had not followed** (checked in the code, not assumed):
  - Search's idle page lists suggestions under "Start with" by title (B-034 to B-037): the test clicked a literal `favorites` and waited for "Try"; the page shows "Favorites" and "Start with".
  - The page's controller prompts are Select, Back, Y "Sidebar" and LT "Hide hints" (F-P2, B-055); Start (PlayStation: "Options") still opens Sources but is no longer advertised. The test waited for an "Options" prompt.
  - With a film in progress, Home leads with it (the featured title) and Continue Watching, so Recently Added is below the window, and Home is a lazy list that does not compose rows out of view (the row is still there, `library/Home.kt`). The test waited for "Recently Added" before opening a show.
- **Fix (tests only):** click "Favorites" and wait for "Start with"; wait for the `Triangle Sidebar` prompt (still a prompt in the controller's family's terms, as the test meant); after Back, wait for Home, then open the show from Shows. Nothing asserted was removed or loosened.
- **Verification:** reproduced in the Linux CI image (`tools/ci/linux-check.sh window`): 3 failed, the same messages. With the fix, `window 2`: **2 of 2 runs pass**, all three cases executed (not skipped). Hosted CI: VERIFICATION.md, "B-076 fix".

<a id="b-079"></a>
### B-079: The Linux CI job no longer fits its 60-minute limit (CI capacity)

- **Category:** CI infrastructure · **Priority:** High for the release (it blocks hosted CI) · **Platforms:** hosted `ubuntu-24.04` runner only
- **Components:** `.github/workflows/ci.yml`, job "Check" on Linux (`timeout-minutes: 60`: "Build and test", then "Desktop window tests")
- **How found:** 2026-11-21/22, with B-076 and B-078 fixed. Run 44941967966 attempt 1 (`dc70958`): cancelled at 60 min inside "Build and test". Run 44953745884 (`3986850`) attempts 1 and 2: **"Build and test" passed both times** (every test on Linux green), then cancelled at 60 min inside "Desktop window tests" (22:52 and 23:53 UTC). The only Linux job to finish since the fixes (44941967966 attempt 2, 56 min) finished early because three window tests failed fast (B-078).
- **Measured:** Linux `:ui:jvmTest` alone is 48.6 min of test time over 67 classes (no hang; slowest class 4.4 min); "Build and test" ~51–52 min; the window tests ~4–8 min more on the hosted runner. Before Phase 2 the job's tests were mostly restored from Gradle's cache, so the limit was never approached.
- **Phase:** Beta 2 (blocks the release's CI gate) · **Status:** Fixed (hosted CI run 44994139313 on `ff98a0d`: Linux 53.8 min, every job green) · **Last updated:** 2026-11-22
- **Options first offered (none applied):** raise the Linux job's `timeout-minutes` (for example to 90; nothing skipped or loosened, every test still runs and must pass); or move "Desktop window tests" into a job of its own with its own limit; or speed up the UI tests (Beta 3 work, not a release fix).
- **The owner's decision (2026-11-22):** not to raise the limit first, but to measure and shorten the Linux job, keeping the 60 minutes unless optimisation proves insufficient.
- **Measurements (2026-11-22; hosted logs of runs 44953745884 and 44941967966, and the Linux CI image limited to 4 CPUs and 14 GB like the runner):**
  - **Per step, hosted:** Linux "Build and test" 51–60 min, window tests 2–4 min, the rest under 1 min; Windows runs the **same Gradle command** in 25–30 min.
  - **Per task, hosted (gaps between task lines):** `:ui:jvmTest` **54.7 min on Linux, 25.2 min on Windows**; everything else (compilation, the other modules' tests, the CLI) about 3 min on both.
  - **Per test:** 542 UI tests in both; Linux 1.70× slower overall, **uniformly** (every class 1.6–2.3×; no outlier, no Linux-only wait or timeout). So the cost is per-frame work, not a hot spot.
  - **Why it did not use the runner:** the test task ran one JVM; sampling the container showed **about 1.06 cores busy** of the 4 allowed. Each class renders in software on one thread.
  - **Ruled out:** the build cache (working: unchanged inputs come from it, which is why `044c8f0`'s run took 2m43s; on a changed tree nothing can, and compilation is about 3 min); dependency downloads (restored by `setup-gradle`); package installation (20–40 s); the window tests themselves (2–4 min; the step reruns `:desktop:app`'s 77 tests, about 30 s of them not window tests: left as it is, not worth a risk to coverage).
- **Experiment (the 8 heaviest classes, 103 tests, 4 CPUs):** 1 JVM 927 s; 2 JVMs 548 s (1.69×); 4 JVMs 382 s (2.43×); the same 103 tests passing in each.
- **Change** (`ui/build.gradle.kts`, the UI module's test task only): `maxParallelForks = (availableProcessors / 2).coerceIn(1, 4)`: two JVMs on a 4-CPU runner, leaving a core for Gradle, the collector and Skia's threads (B-044's tests also read the wall clock). Safe because the UI tests share nothing across processes (no fixed port, file, home directory or preference; each world is made in its test; temporary directories are unique). No test, assertion, step or limit changed; `timeout-minutes` stays 60.
- **Verification:** the complete Linux job (`linux-check.sh`: `check`, then the window tests) in the CI image on 4 CPUs, **twice, every task executed** (the second with the build cache off): exit 0 both times, **25.8 and 26.4 min**; 1,595 distinct tests in `check` (549 UI), 0 failures, the same two UI skips as hosted CI (AboutUiTest's documented headless-Linux case; WebsiteShowcaseTest, on request); the window tests ran (10 more executed). Windows, the complete `./gradlew check --rerun-tasks`: **32m10s before, 6m31s after** on the development machine (4 JVMs there), 1,651 tests, 0 failures (one earlier run met B-080, unrelated). Hosted timings: VERIFICATION.md, "B-076 fix".

- **Hosted result of the change (run 44991408564 on `2f59c37`, 2026-11-22):** faster, but **less reliable**, so **reverted**:

  | | One JVM (before) | Two JVMs (`2f59c37`) |
  | --- | --- | --- |
  | Linux "Build and test" | 51.5–57.9 min; green 3 of 3 since B-076's fix | **28.5 min, 2 failures** |
  | Windows "Build and test" | 25–30 min; green in every run | **23.1 min, 1 failure** |

  - Linux: `VisualSystemTest.artworkIsReadForItsColorOnceNotOnEveryVisit` failed with "uncaught exceptions before the test started" (a `NullPointerException` from a coroutine on `Dispatchers.Default`, left by an earlier test in the same JVM: a latent leak that a different class order lands on another test); `SourcesUiTest.removingAServerSignsOutOfItAndAddingItAgainSignsInAfresh`, "Timed out waiting for the server's films" (B-074's class).
  - Windows: `HomeFocusTest.everyRoundTripFromHomeComesBackUsable`, "round 1: right lands in the page" (focus left on the navigation).
  - None of the three had failed with one JVM on the hosted runners; the container at 4 CPUs did not show it (2 of 2 green), so the hosted runners' 4 vCPUs are more contended than the container's.
  - The owner's constraints exclude making the tests less reliable, and rerunning until green would be exactly that; fixing the leak and the timing sensitivity is test-reliability work (B-044, B-074; Beta 3), not a CI performance change. `ui/build.gradle.kts` is back as it was.
- **Remaining bottleneck (evidence above):** `:ui:jvmTest`, serial: 51.5–57.9 min of "Build and test" on hosted Linux (one run was still in it at 59m45s when the limit stopped it), then the window tests 2–4 min, setup under 1 min: **about 54 to over 64 minutes** in all. No other safe saving exists in the job (compilation ~3 min, cached when it can be).
- **Recommendation:** raise the Linux Check job's `timeout-minutes` from 60 to **75**: the slowest observed need (over 64 min) plus about 15 % margin; every test still runs and must pass, nothing is skipped or loosened; Windows' jobs finish in 25–30 min, well inside either limit. Parallel test JVMs are a Beta 3 candidate once the leak and B-044/B-074 are fixed.
- **The owner's decision (2026-11-22):** approved 75 minutes for the Linux Check job only; the parallel experiment stays reverted; serial execution and every test kept. **Applied** in `.github/workflows/ci.yml`: `timeout-minutes: ${{ startsWith(matrix.os, 'ubuntu') && 75 || 60 }}` (Windows stays at 60). Nothing else in CI changed.
- **2026-11-26:** the `VisualSystemTest` failure of the parallel experiment has the same suppressed exception as [B-106](#b-106), which reproduces its cause (`AppState`'s start-up coroutines and construction order); B-106 holds that work. The other two failures here are not part of it.

<a id="b-080"></a>
### B-080: `JellyfinExecutionLiveTest`'s remux case can find the server's session unconfirmed (live test, intermittent)

- **Category:** Test reliability (live server) · **Priority:** Low · **Platforms:** the Windows development machine with a live Jellyfin server configured (`~/.reflux-live/jellyfin.env`); never runs in CI (it skips without a server)
- **Components:** `desktop/engine` tests only (`JellyfinExecutionLiveTest.everyDeliveryIsPlannedNegotiatedPlayedAndConfirmed`, line 147)
- **How found:** 2026-11-22, a complete local `check` during B-079's work: "expected PRESERVED but was UNKNOWN" for the remux delivery; its evidence line reads `actual UNKNOWN … unconfirmed CONTAINER VIDEO AUDIO`. The other five deliveries matched their plans. Run alone: failed once, passed once (`actual REMUX, video PRESERVED, audio PRESERVED`); the next complete check passed. It had passed in the morning's check too.
- **Phase:** Beta 3 (found after the freeze) · **Status:** Open · **Last updated:** 2026-11-22
- **Not caused by B-079's change:** it failed alone, with no UI test running; the engine and this test are unchanged.
- **Likely cause (not confirmed):** the test reads the server's account of the session 500 ms after the seek; when the server has not yet registered the remuxed session, Reflux correctly reports the delivery as unconfirmed. To investigate in Beta 3: wait for the server's session facts, as the subtitle case already waits, without loosening what is compared.

<a id="b-081"></a>
### B-081: `FirstRunTest` can find the first folder empty (real-window test, intermittent, Linux CI)

- **Category:** Test reliability (window tests) · **Priority:** Low · **Platforms:** hosted Linux runner, "Desktop window tests"
- **Components:** `desktop/app` tests (`FirstRunTest.choosingAFolderBuildsTheLibraryAndTheNextLaunchRemembersIt`, line 64); possibly the first scan's timing
- **How found:** 2026-11-22, hosted CI run 45003843008 on `b7d5ba2` (documentation only above `ff98a0d`): "Timed out waiting for \"Recently Added\"… On screen: Nothing to watch here yet, No movies or shows were found in My Media". The same code passed on hosted CI (44994139313 on `ff98a0d`) and in four Linux-image runs (B-078, B-079).
- **Phase:** Beta 3 (found after the freeze) · **Status:** Open · **Last updated:** 2026-11-22
- **To investigate (Beta 3):** whether the first scan can run before the test's media is visible, and whether Reflux then picks it up by itself as the empty state promises (a product question, not only a test one); not weakened.

<a id="b-082"></a>
### B-082: Quick View waited for every episode of a long show

- **Category:** Performance · **Priority:** Medium · **Platforms:** all (shared UI); measured on the CachyOS laptop (physical Linux)
- **Components:** `ui` (`QuickView.kt`, `state/Models.kt`), `library` (`Library.showOutline`)
- **How found:** the owner's CachyOS audit brief (2026-11-24): Quick View took about 5 to 6 seconds on *The Vampire Diaries* (8 seasons) and *The Originals* (5 seasons). CACHY-003 in [the CachyOS audit ledger](verification/CACHYOS-FINAL-AUDIT-LEDGER.md).
- **Phase:** the owner's CachyOS audit (authorised in the brief) · **Status:** Fixed · **Last updated:** 2026-11-24
- **Cause (measured):** Quick View built the whole details page model: an entry (artwork, watch state, metadata, availability) for the show, every season and every episode, a card per episode, the cast and version choices, none of which it shows. About 1 ms an episode on a fast desktop; many times that on a tablet or a Fire TV.
- **Fix:** `Library.showOutline` (seasons and episodes as plain items, with Next Up, an entry only for the show) and `quickViewModel`, which shares the show's facts and main-button label with the details page (`showSummary`).
- **Measured (same method before and after):** the model, on a copy of the owner's library: 143–161 → 6–7 ms (171 episodes), 77–84 → 4–5 ms (92), 39–41 → 3 ms (42). In the real shell (pane shown, polled every 80 ms): cold 813 → 202 ms and warm 242 → 81–84 ms for *The Vampire Diaries*.
- **Tests:** `QuickViewModelTest` (3, new: Quick View says exactly what the details page says, a started show included); `QuickViewUiTest` (6) and `DetailsFocusTest` (3) pass. Opt-in measurements: `QuickViewTimingTest`, `QuickViewWalkTest`.
- **Outstanding:** timing on the Pad 7 and the Fire TV (no device attached in the session).

<a id="b-083"></a>
### B-083: On Linux the dock showed no Reflux icon: the window had no stable class and the archive no desktop entry

- **Category:** Bug (platform integration) · **Priority:** Medium · **Platforms:** Linux (found on Hyprland, XWayland)
- **Components:** `desktop/app` (`LinuxWindowClass.kt`, `Main.kt`, `build.gradle.kts`), `desktop/app/packaging/linux/install-desktop-entry`
- **How found:** the owner's CachyOS audit brief (BUG D). CACHY-001 in [the CachyOS audit ledger](verification/CACHYOS-FINAL-AUDIT-LEDGER.md).
- **Phase:** the owner's CachyOS audit · **Status:** Fixed · **Last updated:** 2026-11-24
- **Cause (verified):** the portable Beta 2 window's class was `dev-reflux-desktop-MainKt` (the toolkit's default, from the Java main class; under tests another), and the archive shipped no `.desktop` file, so nothing could match the window to Reflux's icon.
- **Fix:** Reflux names its X11 windows `reflux` (with `--add-opens java.desktop/sun.awt.X11`; without it Reflux runs as before and says so in its log). The Linux archive has `bin/install-desktop-entry` (and `--remove`), which writes a per-user `reflux.desktop`: `StartupWMClass=reflux`, the archive's icon, the Exec path quoted as the specification says; a folder path with `%` is refused with a reason, since GLib's launchers cannot start it.
- **Tests:** `LinuxDesktopEntryTest` (3, new); the entry was also checked with `desktop-file-validate` and opened and launched through GLib (`Gio.DesktopAppInfo`) from paths with spaces, `$`, quotes, backquotes and backslashes.
- **Verified (physical Linux, Hyprland 0.57.2):** a development build's two windows are class `reflux` (`hyprctl clients`).
- **Outstanding:** pinning the entry in a dock and relaunching from it (the owner's procedure in VERIFICATION.md: it needs the owner's own dock).
- **Update (2026-11-25, the owner's final CachyOS session; physical Linux, Noctalia's dock):** the installer's entry pinned, launched by the dock's own activation (`gio launch`): class `reflux`, focused, Reflux's logo on the pinned icon, the running window grouped on it (VERIFICATION.md, "The owner's final CachyOS session"). The dock-pin check above is **done**; only a press on the dock by a person's finger remains the owner's. Reflux now also writes the entry itself (B-102). Release placement: with B-102 (the owner's decision). **Last updated:** 2026-11-25.

<a id="b-084"></a>
### B-084: On Hyprland the player was two windows: a video window without controls and a controls window on a blank panel

- **Category:** Bug (platform, rendering) · **Priority:** High (release-blocking on the owner's CachyOS acceptance) · **Platforms:** Linux; found on Hyprland 0.57 with a hybrid Intel/NVIDIA laptop
- **Components:** `desktop/app` (`Main.kt`, `LinuxGlVideo.kt`, `SkikoGl.kt`, `DesktopPlatform.kt`; the fallback `LinuxVideoShape.kt`, `VideoSurface.kt`), `playback/mpv` (`MpvGlRender.kt`), `ui` (`player/VideoCutouts.kt`, the `cutsVideo` marks in `PlayerOverlay.kt` and `MiniPlayer.kt`)
- **How found:** the owner's CachyOS audit brief (BUG C), then the owner's rejection of the audit (2026-11-24): "one window contains the video with no visible controls; a second contains only the controls, on a blank background". CACHY-002 in [the CachyOS audit ledger](verification/CACHYOS-FINAL-AUDIT-LEDGER.md).
- **Phase:** the owner's CachyOS audit · **Status:** Fixed (physical Linux, the owner's own check pending) · **Last updated:** 2026-11-24
- **Cause (measured on the real desktop):** the desktop player was the video (libmpv's X11 window in the main window) and the controls in a second, transparent window above it. On that XWayland, windows reach the compositor **without alpha**: the controls window's buffer was `rgb24`, and so was a plain Java window with a clear background, under every Java 2D pipeline and Skiko's software renderer, though the JDK reports per-pixel translucency supported. Its transparent areas were black: a dark panel over the picture, the controls' first frame laid out too small, and, as a separate toplevel, floated and blurred by the compositor and left behind on a workspace move. That XWayland had both GPUs open; a nested Hyprland on the Intel GPU alone kept alpha, which is why the audit's first check (a window rule, `opacity 1.0 override` and `no_blur`, in a nested Hyprland) passed there and failed on the real desktop. The rule cannot help: the window has no alpha to keep.
- **Fix (the owner chose a single window):** on Linux the video surface is the main window's own native child, placed where Compose lays out the video stage, and the controls are composed in the main window beneath it, over black. The surface's bounding shape (X SHAPE, `LinuxVideoShape`) leaves out the areas the controls draw, which they report with `Modifier.cutsVideo()`; its input shape is empty, so the pointer reaches the controls everywhere. No transparency is needed, and the compositor sees one window. Windows keeps its controls window; Android is unchanged.
- **Tests:** `LinuxVideoShapeTest` (6), `VideoCutoutsTest` (2), new; `EmbeddedPlaybackTest`, `MiniPlayerWindowTest`, `PlayerChromeResizeTest` branch where Linux has no controls window; the full desktop and UI runs (VERIFICATION.md).
- **Verified on the owner's real desktop** (Hyprland, a test workspace on the HDMI monitor, captures of Reflux's window only): playing tiled, floating, resized, paused, seeked, fullscreen and back, moved to another workspace and back, and the mini-player: one window throughout, a sharp picture, the controls over it, nothing left on a workspace.
- **Superseded on Linux (2026-11-24, CACHY-011):** the owner rejected the black bands. The picture is now drawn by Compose: libmpv's render API renders into a texture in Skiko's own OpenGL context, inside Compose's draw, and Skia draws it under the player's overlay (`MpvGlRender.kt`, `LinuxGlVideo.kt`, `SkikoGl.kt`, `GlibcTrim.kt`; `DesktopPlatform.kt`, `Main.kt`). The shaped window above is kept only as the fallback, named in the log (`linux-x11-shape-fallback`). Found and fixed while verifying: the renderer chosen before the window existed; the picture upside down; a playback with sound and no picture (the render context made after the file opened); decoding on the NVIDIA GPU for a picture drawn on the Intel one; ~12 MB of malloc arenas kept per playback. Decoding on Mesa is copy-back (`vaapi-copy`): zero-copy VA-API needs EGL and Skiko uses GLX (measured: `hwdec=vaapi` falls to software); on NVIDIA's OpenGL it is zero-copy `nvdec`. Tests: `MpvGlRenderTest` 2, `LinuxGlVideoTest` 5, opt-in `IntegratedPlayerLifecycleTest` and `IntegratedPlayerPerfTest`; `HyprlandWalkTest` +3 steps; `EmbeddedPlaybackTest` and `MiniPlayerWindowTest` branch for a drawn picture. Verified on the owner's real desktop (VERIFICATION.md, "The Linux picture in Reflux's own scene"); measurements and limits in CACHYOS-FINAL-AUDIT-LEDGER.md, CACHY-011. Then, from the owner's test: the docked video kept square corners inside the rounded card; the video canvas is now clipped to the dock's own radius (`pictureClip`), verified in the floating nested Hyprland (CACHY-011).
- **Outstanding:** the owner's acceptance of the Linux player (the owner played it on the CachyOS desktop and reported it working, then the corners, fixed in `1da6aaf`); CachyOS was retired on 2026-11-24, so the acceptance and the corner fix's real-desktop check wait for the next Linux machine. Not merged into `main`.
- **Update (2026-11-25, the owner's final CachyOS session; physical Linux):** the corner fix **was** checked on the real desktop after all (the owner brought the laptop back): the docked video's own pixels stop at the curve, at the display's scale and at 1.25, during the move and at rest (`MiniPlayerCornersWalkTest`); accepted as measured. One lifecycle defect found and fixed (B-099). The player window tests passed on both renderers (B-092). The sentence above ("wait for the next Linux machine") is superseded for the corners; **still open:** the owner's formal acceptance of the integrated player and its release placement (the ledger, "Closure": recommended for the Linux beta, "Beta 3-class in size"), and its first validation on Ubuntu ([UBUNTU-TRANSITION.md](history/linux/UBUNTU-TRANSITION.md), section 4). Verified on one machine only (Intel, Mesa, Hyprland, XWayland). **Last updated:** 2026-11-25.
- **Ubuntu (2026-11-26), physical laptop, GNOME 50.1 on Wayland (XWayland), Mesa 26.0.8, libmpv 0.41.0:** the integrated picture was selected for every playback and drew on Intel's Mesa (ARL), 0 dropped frames with the window visible, A/V ~0, the lifecycle walk 8 of 8 contexts released; decoding went to NVIDIA instead of `vaapi-copy` on Intel (new: [B-104](#b-104)). Covered XWayland windows are throttled to ~1 fps, which a perf walk must not mistake for dropped frames. [UBUNTU-VALIDATION-REPORT.md](verification/UBUNTU-VALIDATION-REPORT.md), section 4

<a id="b-085"></a>
### B-085: The Reflux keyboard was too large, repeated the field, and hid the results; Xbox-style shortcuts

- **Category:** UX · **Priority:** Medium · **Platforms:** every platform where the Reflux keyboard is used (controller, remote, desk)
- **Components:** `ui` (`RefluxKeyboard.kt`, `RefluxApp.kt`, `TextEntry.kt`, `state/Keyboard.kt`), `core` (`InputMapping`)
- **How found:** the owner's CachyOS audit brief (BUGs F and G). CACHY-005 in [the CachyOS audit ledger](verification/CACHYOS-FINAL-AUDIT-LEDGER.md).
- **Phase:** the owner's CachyOS audit · **Status:** Fixed · **Last updated:** 2026-11-24
- **Before (renders):** a panel up to 980 dp wide with blank glass beside its keys, a copy of the field under Search's own field, and on a television a panel over the tabs and every result.
- **Fix:** the panel is as wide as its keys (about 660 dp at a desk, 600 dp on a television), with smaller keys (unit 74 → 56 dp at a desk, 66 → 50 on a television; height 46 → 40) and gaps (6 → 4); the typed text is shown in the keyboard only where the panel would cover the field (the field reports where it is), and is still said to a screen reader; suggestions kept; hints wrap on a narrow panel. Shortcuts as on an Xbox: the left stick's press is Shift, LT the &123 page (RT no longer is). Docked rather than dropped down from the field: DECISIONS.md, 2026-11-24.
- **Tests:** `KeyboardRendersTest` (6 renders, looked at); the keyboard and interaction suites in the full UI run.
- **Outstanding:** walks with a controller on a television, a tablet and a desk (no controller or device in the session).

<a id="b-086"></a>
### B-086: The PIN pad was a small pad in a wide panel

- **Category:** UX · **Priority:** Low · **Platforms:** all · **Components:** `ui` (`RefluxKeyboard.kt`)
- **How found:** the owner's CachyOS audit brief (BUG H). CACHY-006 in [the CachyOS audit ledger](verification/CACHYOS-FINAL-AUDIT-LEDGER.md).
- **Phase:** the owner's CachyOS audit · **Status:** Fixed · **Last updated:** 2026-11-24
- **Fix:** the PIN pad is sized by its own design: three keys across (68 dp at a desk, 60 on a television, 76 on a phone), the panel as wide as them (420 → about 224 dp at a desk), hints in two rows within its width, no copy of the profile gate's own PIN field.
- **Tests:** `KeyboardRendersTest` (`pin-pad-desk`, `pin-pad-tv`, `pin-gate-desk`, looked at); `InteractionContractTest.aPinIsEnteredWithAControllerOverTheQuestionOfWhoIsWatching`.

<a id="b-087"></a>
### B-087: While the Reflux keyboard was open, the page's hints still named browsing's buttons

- **Category:** Bug (UX) · **Priority:** Low · **Platforms:** all with a controller · **Components:** `ui` (`RefluxApp.kt`)
- **How found:** the session's keyboard renders (2026-11-24): "Y Sidebar" stayed at the top of the page while Y typed a space. CACHY-008 in [the CachyOS audit ledger](verification/CACHYOS-FINAL-AUDIT-LEDGER.md).
- **Phase:** the owner's CachyOS audit · **Status:** Fixed · **Last updated:** 2026-11-24
- **Fix:** the page's hint bars step aside while the keyboard is open; the keyboard names its own buttons.

<a id="b-088"></a>
### B-088: Android: a film sometimes plays in a small box with black on all four sides

- **Category:** Bug (playback, Android) · **Priority:** Medium · **Platforms:** Android phones (reported); other Android devices to check
- **Components:** `android/app` (`MainActivity.VideoLayer`, `Media3Player`)
- **How found:** the owner's CachyOS audit brief (BUG E). CACHY-007 in [the CachyOS audit ledger](verification/CACHYOS-FINAL-AUDIT-LEDGER.md).
- **Phase:** Beta 3 (no device in the session) · **Status:** Blocked (no device) · **Last updated:** 2026-11-24
- **Analysis (code only):** the picture is laid out at its own shape (pixel aspect included) and centred, so a correctly flagged film is boxed on two sides at most. Black on all four sides means a picture smaller than the box both ways: a source with bars of its own (a windowboxed encode), or a surface buffer (`setFixedSize`) that disagrees with what the decoder renders (rotation or crop metadata).
- **Procedure (needs the phone and the film):** a screenshot; `adb shell dumpsys SurfaceFlinger` (the video layer's buffer and display size); `ffmpeg -i <file> -vf cropdetect -frames:v 300 -f null -` on the same file (bars in the source). Then either a zoom to fill (a source with bars) or a surface fix (a mismatch), with a test of its own.

<a id="b-089"></a>
### B-089: Two phone-layout UI tests depend on the machine's default font (fail on CachyOS, pass on hosted CI)

- **Category:** Test reliability · **Priority:** Low · **Platforms:** the desktop JVM test run on a machine whose default sans font is wider (CachyOS: Noto Sans)
- **Components:** `ui` tests `DetailActionsLayoutTest.withAFingerAllFourActionsStillSitOnOneLine`, `EpisodeVersionsUiTest.theVersionsAreBeneathTheEpisodeOnAPhone`
- **How found:** the CachyOS audit's full UI run (2026-11-24). CACHY-009 in [the CachyOS audit ledger](verification/CACHYOS-FINAL-AUDIT-LEDGER.md).
- **Phase:** Beta 3 · **Status:** Open · **Last updated:** 2026-11-24
- **Evidence:** both fail alone and in the full run, **and identically on an untouched worktree of `1d65dcf`**, so the session's changes are not their cause; hosted CI passed them on `ff98a0d`. Reflux ships no font on desktop, so Skia uses the system's default sans (`fc-match sans-serif` → Noto Sans here): the first test's four actions measured 16–292 dp for the two buttons and wrapped the other two to a second line in a 412-dp phone layout.
- **To do (Beta 3):** pin a font for UI tests (or ship one), rather than loosen either assertion; check whether the second test's timeout has the same cause.
- **Lead found in the Windows repository audit (2026-11-25; not verified by a run):** the Linux CI job **already pins** the test font: `FONTCONFIG_FILE=tools/ci/fonts.conf` (Liberation Sans for `sans-serif`) around `./gradlew check`, in `ci.yml` and `tools/ci/linux-check.sh` (DECISIONS.md, "Linux CI measures text with Liberation Sans"; the first of these two tests is the one that decision was made for). Nothing in this entry or the CachyOS record says the CachyOS runs set it, and the ledger's "Likely cause" (a font wider than the CI image's) matches running without it. So the likely explanation is **the local runs' configuration, not a new defect**: on Ubuntu, run the `ui` suite with `FONTCONFIG_FILE` set and `fonts-liberation` installed ([UBUNTU-TRANSITION.md](history/linux/UBUNTU-TRANSITION.md), A1). If both pass, this becomes a documentation item (the local procedure), not a code change; the font margin DECISIONS.md notes (0 dp to spare under Liberation Sans) still stands. Status unchanged until that run. **Last updated:** 2026-11-25.
- **Ubuntu (2026-11-26):** both tests **pass** on the owner's laptop under Ubuntu 26.04.1 with `FONTCONFIG_FILE=tools/ci/fonts.conf`, as on hosted CI; the font remains the cause found on CachyOS. Still open for Beta 3 (pin a font for UI tests). [UBUNTU-VALIDATION-REPORT.md](verification/UBUNTU-VALIDATION-REPORT.md)

<a id="b-090"></a>
### B-090: After the dock's Stop, the details page's Back did not return Home (real-window test, reproduced on the baseline)

- **Category:** Bug or test timing (to be told apart) · **Priority:** Medium · **Platforms:** Linux desktop (hosted CI's openbox; a nested Hyprland whose rule floats Reflux's windows)
- **Components:** `desktop/app` test `EmbeddedPlaybackTest.playsInsideTheWindowAndAnswersToTheMouse` (line 209, "Home again"); the shell's Back after playback ends from the mini-player
- **How found:** hosted CI on the `cachyos-final-audit` branch (run 45345526419, Linux "Desktop window tests"), then reproduced on CachyOS. CACHY-010 in [the CachyOS audit ledger](verification/CACHYOS-FINAL-AUDIT-LEDGER.md).
- **Phase:** Beta 3 · **Status:** Open · **Last updated:** 2026-11-24
- **Evidence:** on CachyOS, in a nested Hyprland that floats Reflux's windows, the test fails at the same step **on an untouched `1d65dcf` worktree and on the branch alike**: the page shows the film's details and its Back button, Back is clicked, and Home never comes. So it is not caused by the audit's changes. Hosted CI passed the same test on `ff98a0d` (2026-11-22), so it depends on timing or the window manager.
- **To investigate:** whether Back is lost while the closing player's windows (the dock and its controls window) are still going, which a person clicking quickly after Stop could meet too.

<a id="b-091"></a>
### B-091: A controller's Start no longer closed Quick View (regression on `cachyos-final-audit`)

- **Category:** Bug (regression introduced by the branch) · **Priority:** Medium · **Platforms:** every platform with a controller (desktop, Android tablet and phone with a controller, Android TV, Fire TV with a game controller); a remote's or keyboard's Menu was not affected
- **Components:** `ui` `AppState.dispatch` (Quick View's own actions); `core` `InputMapping` (Start while browsing)
- **How found:** the Windows acceptance audit of `cachyos-final-audit` (2026-11-24), by source review: F-P1 (CACHY-004, `17aba72`) mapped Start while browsing from `MENU` to the new `COLLAPSE_NAVIGATION`, but Quick View's dispatch closed itself only on `BACK`, `CONTEXT` and `MENU`, and swallowed everything else. So Start with Quick View open did nothing, where before it closed it; INPUT_MODEL.md's own table ("Profile gate, panels": Start "Closes Quick View") still said it did.
- **Phase:** found in the audit of a review branch, before any release; fixed on that branch · **Status:** Fixed (automated) · **Last updated:** 2026-11-24
- **Fix:** Quick View also closes on `COLLAPSE_NAVIGATION` (`AppState.kt`), as it did on `MENU`. It neither shrinks the sidebar behind it nor opens Sources.
- **Regression test:** `QuickViewUiTest.aControllersStartClosesQuickView_withoutTouchingTheSidebarOrSources` (a 1440 × 900 desk with a sidebar, and a television): **failed before the fix** (Windows 11, "Timed out waiting for closed by Start" at the desk), **passes after**; `QuickViewUiTest`, `SidebarCollapseTest`, `DirectionalInputTest` PASS on Windows 11 after it.
- **Device check:** on the **Google TV emulator** (`Reflux_TV`, Android 16, the branch's debug build with this fix; an emulator, not a physical TV): Movies, a film's card focused, a remote's Menu (`keyevent 82`) opened Quick View; a controller's Start (`keyevent 108`, BUTTON_START) closed it, and Reflux stayed on Movies, not Sources. **Physical Fire TV, phone and Pad 7:** the debug build with the fix installed and their device walks run (VERIFICATION.md, "Windows acceptance audit"); none of those walks presses Start with Quick View open, so B-091's press itself was not made on a physical device.

<a id="b-092"></a>
### B-092: Linux player window tests fail intermittently on the shaped-window fallback (`linux-x11-shape-fallback`)

- **Category:** Test synchronisation (both failures), plus a small production lag found on the way (fixed) · **Priority:** Medium (blocked a green hosted Linux CI on `cachyos-final-audit`) · **Platforms:** Linux desktop where Skiko does not draw with OpenGL (hosted CI's Xvfb), so the player takes the fallback
- **Components:** `desktop/app` `Main.kt` (the fallback's surface placement); tests `MiniPlayerWindowTest.theDockFollowsTheWindow`/`comesBackToTheWholePlayer`, `PlayerChromeResizeTest`, `WindowHarness.clickSurface`/`hoverSurface`
- **How found:** hosted CI run 45416718643 on `fec8a4a` (2 window tests failed), again on `5b39a27` and `3197225` (run 45551089504: `PlayerChromeResizeTest` NPE at line 58; `MiniPlayerWindowTest` passed that time).
- **Phase:** found in the audit of a review branch; fixed on that branch · **Status:** Fixed (CI-identical Xvfb and physical Linux; hosted CI green on `99fff18` and `406382b`, below) · **Last updated:** 2026-11-25
- **The earlier diagnosis (2026-11-24, WSL2) is withdrawn.** It said the fallback's dock layer was laid out as a 226 × 33 box below the picture. On the real desktop (scale 1.6, both renderers) and on a CI-identical Xvfb the layer is laid out **exactly over the picture** (for example 998,557, 320 × 180, the surface's own place) and receives the pointer events; that reading did not hold.
- **Environment of this investigation (the owner's final CachyOS session, 2026-11-25):** Xvfb `:99` 1600 × 1000 × 24, openbox, xcompmgr (the Arch packages of the three, extracted into the session's scratch folder, nothing installed; **a virtual display on the physical laptop**, as hosted CI's), through `tools/ci/desktop-window-tests.sh` as CI runs it; every playback `linux-x11-shape-fallback (Skiko 0.150.1 draws with SOFTWARE_FAST, not OpenGL)`, as on hosted CI; no sound device. Every run with `--rerun` (an environment variable is not a Gradle input: without it a repeat can be a cached result, which made two earlier repeats in this session meaningless; they are not counted).
- **Reproduction:** both tests alone: **PASS 2 of 2**. The whole window suite as CI runs it: **FAIL 2 of 2** (`PlayerChromeResizeTest` both times, `MiniPlayerWindowTest` once). `EmbeddedPlaybackTest` → `FirstRunTest` → `MiniPlayerWindowTest` in one JVM: `MiniPlayerWindowTest` **FAIL 3 of 4** (with diagnostics never committed).
- **Cause 1, `PlayerChromeResizeTest`: its film ran out.** The sample film was 150 s; the walk takes 149–152 s alone and 173–177 s in the whole suite here, longer on a hosted runner. The film ended, the player closed as it should, and the walk failed for want of a player: the details page with "Mark as unwatched" on screen here, and on hosted CI, sooner, the NullPointerException at line 58 (`controlsSize()` finds no video surface). Not the player. **Fix (test fixture):** a 600 s film; no assertion changed. After: the walk takes 126–134 s and the film never ends.
- **Cause 2, `MiniPlayerWindowTest`: the test went on before the dock had moved, and clicked where the picture had been.** Diagnostic trail of a failing run: the click "on the picture" went to window pixel (950, 504), the centre of where the dock was **before** the window was made its size again (814,428, 272 × 153), while the dock was already being laid out at 1006,561. Two reasons in the test: (a) `theDockFollowsTheWindow` waited for "window wide again, `docked()`, `covered()`", which the **old** state satisfies right after `setBounds`, before Reflux has laid itself out again (old dock, old surface, still in agreement): the walk went on while the dock was still to move (and so never checked that it followed); (b) `clickSurface` read where the picture is in one pass of the UI thread and clicked in another, so a frame between them could move the dock. **Fix (test, stricter):** the dock's own bounds must change on the smaller window and come back to where they were (within the tolerance `docked()` uses: a window manager may give the window back a pixel off, as Hyprland did); the harness reads the picture's place and clicks (or hovers) in **one** pass.
- **Production lag found on the way (fixed):** after a resize, the fallback's native surface followed its controls a frame late: the dock layer (`MiniPlayerSurface`) and the black stage box were laid out in one pass, but the surface was moved only in the recomposition the box's state change brought (`SideEffect { placeSurface(…) }`), so for a frame or more the picture one saw and the layer that takes its clicks were in different places. The surface is now placed in the same layout pass (`placeSurface` from the box's `onGloballyPositioned`; the state is kept for the surface's shape). Trace after the fix: layer, box and the surface's move in the same pass.
- **Negative control (honest):** with the production change removed and the corrected tests kept, `MiniPlayerWindowTest` **PASS 6 of 6** in the failing sequence. So the hosted failures were the test's synchronisation (cause 2), not the lag; the lag is real but these tests do not measure it.
- **After (Xvfb `:99`, CI-identical):** the failing sequence **8 of 8** `MiniPlayerWindowTest` PASS (one run had a failure in `EmbeddedPlaybackTest` or `FirstRunTest`, its result file not kept: not attributed); the whole window suite **3 of 3 PASS** (101 tests, 14 skipped, 0 failed), then, after the tolerance change, see VERIFICATION.md. **Physical Linux (the owner's laptop, Hyprland, workspace 2 or 3 shown on the laptop's screen):** `MiniPlayerWindowTest` on the fallback (`REFLUX_NATIVE_VIDEO_WINDOW=1`) and on the integrated picture, **2 of 2 each**. First version of the stricter wait compared the dock's bounds exactly and **failed on Hyprland** (the window came back 861 px high instead of 860): corrected to the tolerance above.
- **Not weakened:** no assertion removed or loosened beyond the tolerance `docked()` already had; no sleep added; nothing skipped.
- **Hosted CI on `99fff18` (run 45576109106): Check (ubuntu-24.04) PASS**, the desktop window tests on the fallback included: the first green hosted Linux check on this branch.
- **Hosted CI on `406382b` (CI 45585080925, Release 45585081047): every job PASS**, the Linux check on its second attempt (the first stopped before the window tests on B-077, in `playback:mpv`); the window tests on the fallback passed. Final state; no CI was run after it (the closure, 2026-11-25).
- **Ubuntu (2026-11-26):** the window suite on a CI-identical Xvfb (verified clean environment) **passed**; `MiniPlayerWindowTest` and `PlayerChromeResizeTest` passed there, and `MiniPlayerWindowTest` on the real GNOME session. With GNOME's session variables inherited, one playback per Xvfb suite stayed in `LOADING` (2 of 2 suites, different tests; not attributed; recorded in the report as Finding 2, not under this ID). On the real GNOME session `PlayerChromeResizeTest` cannot get its exact window size (mutter rounds an odd height; pre-existing test assumption, the report's Finding 3). [UBUNTU-VALIDATION-REPORT.md](verification/UBUNTU-VALIDATION-REPORT.md)

<a id="b-093"></a>
### B-093: On the Xiaomi Pad 7, the Search walk's tap on a suggestion does not fill the query (pre-existing)

- **Category:** Bug or test input (to be told apart) · **Priority:** Low · **Platforms:** Xiaomi Pad 7 (HyperOS, Android 16), physical
- **Components:** `android` test `PhaseTwoWalkOnDeviceTest.searchOnAPhoneFindsNarrowsEditsAndKeepsItsQuery` (line 673, "Timed out waiting for asked"); Search's suggestions ("Not seen yet")
- **How found:** the Windows acceptance audit of `cachyos-final-audit` (2026-11-24), on the physical Pad 7. The test was added late in Phase 2 and verified then only on the phone emulator (VERIFICATION.md, Phase 2); there was no earlier Pad 7 result for it.
- **Phase:** found in the audit of a review branch; fixed on that branch (a test fix; the product defect it uncovered is B-096) · **Status:** Fixed (physical Pad 7) · **Last updated:** 2026-11-25
- **Evidence (physical Pad 7, the same device and session, only the debug app swapped):** the branch's debug build fails it **6 of 6**; the untouched baseline `1d65dcf` fails it **4 of 5**, at the same step with the same message. So it is **not caused by the branch**. It passes on the physical Nothing Phone (2a) (this audit) and on the phone emulator (Phase 2). Every other test of the Pad 7 set passes (below).
- **Investigation (physical Pad 7, landscape 1164 × 777 dp; screen recordings of the run, temporary logging in the test, removed afterwards):**
  1. Search opens with its field focused and the system keyboard up (by design on a touchscreen: `showsKeyboardOnFocus`); the keyboard rises about half a second **after** the page has settled, and takes about half the height.
  2. Taps were not landing on the suggestion: UiAutomation's window list is empty here (no keyboard window to measure), and a scroll of the page into view moved the suggestion but the tap at its reported centre still did nothing.
  3. Decisive experiment, keyboard up: a tap on the "Not seen yet" text at its **reported** centre does nothing; the "Movies" tab's reported bounds were in the status bar; a tap at the **drawn** position of the words (from a screenshot at that moment) **works** (query `unwatched`). Keyboard down: the reported and drawn positions agree, and the same reported-centre tap works.
  So the cause: **while the system keyboard is up, the accessibility tree reports Search's index about 200 px below where it is drawn** (text drawn at about y 681–731, reported 899–954; still so after a second's rest). The test taps where accessibility says. A person's finger, on the drawn words, is not affected. The reporting defect is recorded as B-096.
- **Fix (test, `PhaseTwoWalkOnDeviceTest`):** before tapping the suggestion the walk puts the system keyboard away (`keyboardAway()`): it waits for the keyboard to be up (Back before then would leave Search), presses Back, and checks it is still on Search. The step's purpose (a suggestion asks its question, by touch) is unchanged; the keyboard's own walk is `searchKeepsItsNavigationAndBackTellsTheKeyboardFromLeaving`.
- **After (physical Pad 7):** the test **5 of 5** PASS; the whole class **11/11** (7 run, 4 TV-only skipped). Phone emulator: `PhaseTwoWalk` 11/11 twice (its Search failures in B-095 had the same cause).

<a id="b-094"></a>
### B-094: Fire TV: `TelevisionOnDeviceTest`'s playback from a chosen path intermittently does not start (unattributed)

- **Category:** Bug or test environment (to be told apart) · **Priority:** Medium (playback on Fire TV) · **Platforms:** Fire TV Stick 4K Plus (Fire OS, Android 11), physical
- **Components:** `android` test `TelevisionOnDeviceTest.aFolderChosenByItsPathBecomesALibraryThatPlaysAndSurvivesItsStorage` ("Timed out waiting for playback from a path"; the player in `ERROR` once and `PAUSED` at 0 once)
- **How found:** the Windows acceptance audit of `cachyos-final-audit` (2026-11-24), on the physical Fire TV.
- **Phase:** Beta 3 · **Status:** Open (unattributed) · **Last updated:** 2026-11-24
- **Evidence:** the branch's debug build failed it in its first **3** runs and passed the next **3** (3 of 6); the untouched baseline passed **4 of 4**, run between them. The branch changes no Android code (`android/` is untouched; the shared player changes are `Modifier.cutsVideo()`, which does nothing without a provider, and only Linux provides one), and the branch's failures were all in the runs that started on a debug data directory left by an older debug build. Neither "caused by the branch" nor "pre-existing" is shown: recorded as unattributed.
- **Also seen and explained:** `PhaseTwoWalkOnDeviceTest` failed 5 TV walks twice on the Fire TV's old debug data ("Timed out waiting for Home" and similar); after the baseline had run on the same device, the branch passed **11/11** (the baseline also 11/11). Recorded as the old data's state, not a product failure.
- **To investigate:** a clean debug data directory, then repeated runs of both builds; the player's error on the `ERROR` run.

<a id="b-095"></a>
### B-095: On the phone and tablet emulators, three device walks fail on the baseline as on the branch (emulator data)

- **Category:** Test environment (to be told apart from a bug) · **Priority:** Low · **Platforms:** `Reflux_Phone` and `Reflux_Tablet7` emulators (Android 16); **not** the physical Nothing Phone (2a) or Pad 7, where the same classes pass (B-093 apart)
- **Components:** `android` tests `PhaseThreeWalkOnDeviceTest.quickViewOpensOnAFilmAndBackClosesIt` ("Timed out waiting for a card on Home", line 166), `PhaseTwoWalkOnDeviceTest.searchOnAPhoneFindsNarrowsEditsAndKeepsItsQuery`, `…searchKeepsItsNavigationAndBackTellsTheKeyboardFromLeaving`
- **How found:** the Windows acceptance audit of `cachyos-final-audit` (2026-11-24), emulator runs.
- **Phase:** Beta 3 · **Status:** Open (pre-existing) · **Last updated:** 2026-11-24
- **Evidence (the same emulator and data, only the debug app swapped, two runs each):** `Reflux_Tablet7`: baseline `1d65dcf` fails all three, 2 of 2; the branch the same three, 2 of 2. `Reflux_Phone`: baseline fails `quickViewOpens…` 2 of 2 and one Search walk in each run; the branch `quickViewOpens…` 2 of 2 and one Search walk in one run. **Not caused by the branch.**
- **Likely cause of `quickViewOpens…` (not confirmed):** the emulators' debug libraries are seeded by earlier sessions, and Home's first cards are landscape Continue Watching cards; the test's card filter requires a portrait card (`width < height`).
- **After B-093's test fix (2026-11-25):** `Reflux_Phone`: `PhaseTwoWalk` **11/11 twice** (its Search failures shared B-093's cause); `quickViewOpens…` still fails (2 of 2). `Reflux_Tablet7` (960 × 600 dp): both Search walks still fail 2 of 2, with a different message from the start ("actual value is null": the suggestion node not found at all), so a cause of their own; `quickViewOpens…` still fails.
- **To investigate:** a clean debug data directory on the emulators; the test's card filter; why the tablet emulator's Search has no "Not seen yet" node.

<a id="b-096"></a>
### B-096: Android: while the system keyboard is up, the accessibility tree places Search's index about 200 px below where it is drawn

- **Category:** Accessibility bug · **Priority:** Medium (TalkBack users) · **Platforms:** Android; measured on the Xiaomi Pad 7 (HyperOS, Android 16, landscape); other devices not measured
- **Components:** `ui` `SearchScreen` (the idle index in its `LazyVerticalGrid`), the shell's keyboard padding (`MainActivity`: `windowInsetsPadding(WindowInsets.ime…)` around the page; `adjustResize`); Compose's accessibility bounds
- **How found:** B-093's investigation (2026-11-25).
- **Phase:** Beta 3 · **Status:** Open (pre-existing: the baseline behaves the same) · **Last updated:** 2026-11-25
- **Evidence (physical Pad 7):** with the keyboard up, "Not seen yet" drawn at about y 681–731 px (screenshot at the moment) and reported by accessibility at 899–954, a second later still; the "Movies" tab reported in the status bar; a tap at the reported centre does nothing, a tap at the drawn words fills the query. With the keyboard down, reported and drawn positions agree.
- **Impact:** TalkBack's focus outline and its double-tap target on Search would be misplaced while the system keyboard is up; a sighted finger is not affected.
- **Not known:** the mechanism (whether the reported bounds lag the page's resize or scroll while the keyboard animates in, or are computed against the window before its keyboard inset); whether a phone shows it. Not changed blind: to be measured with TalkBack on a device, then fixed with a test of its own.

<a id="b-097"></a>
### B-097: `PlannedPlaybackWindowTest` named a film that has left the live test server

- **Category:** Test fixture · **Priority:** Low · **Platforms:** Windows (the live-server window test; run only where `~/.reflux-live/jellyfin.env` names a server)
- **Components:** `desktop/app` test `PlannedPlaybackWindowTest.aPlanIsCarriedOutInTheRealWindow`
- **How found:** the Windows acceptance audit (2026-11-24): `NoSuchElementException` (no film titled "Mission: Impossible - Fallout").
- **Phase:** found in the audit of a review branch; fixed on that branch · **Status:** Fixed (Windows 11, the live server) · **Last updated:** 2026-11-25
- **Investigation:** a temporary test (never committed; removed from both scratch worktrees) connected as the test does and printed counts and two titles only: the branch and the baseline sync identically (one scan report, `added=999`, 181 films, 38 shows, 814 episodes); "Mickey 17" present, nothing titled "Mission" at all. The Jellyfin read pages by 500 until the server's own `TotalRecordCount` (no cap), so the film is not among what the server lists for this account: the test failed on the baseline as on the branch. (The branch's first sync took 170 s against a cold server; warm, 8.4 s, as the baseline's 8.1 s.) 66 of the server's films have a non-forced PGS track.
- **Fix:** the test still prefers Fallout where the server has it; otherwise it takes the first film, by title, with what its steps need, not a name: a non-forced PGS track **and** only AC-3 or E-AC-3 audio (the stand-in device has neither, so the plan's `AUDIO_CONVERSION` holds whichever track plays). A first version that asked only for PGS chose a film that plays directly (`DIRECT_PLAY`) and failed the plan's check, so the audio condition is required. With none suitable it fails saying so.
- **After (Windows 11, physical, the live server):** PASS in 98 s on *American Assassin*: planned `AUDIO_CONVERSION` = actual, the other language from its place, the PGS subtitle extracted with the picture running (picture by 7 s, subtitle by 35 s).

<a id="b-098"></a>
### B-098: The website's link check failed CI whenever gnu.org dropped one connection from a runner

- **Category:** CI reliability (the check, not the site) · **Priority:** Low · **Platforms:** hosted CI (the Website job)
- **Components:** `website/scripts/check-links.mjs` (external links)
- **How found:** CI run 45460552173 on `4be4c45` (2026-11-24): "unreachable https://www.gnu.org/licenses/gpl-3.0.html", 1 of 31 external links. The site is unchanged by the branch, and the same job passed on `fec8a4a` the day before.
- **Phase:** found in the audit of a review branch; resolved on that branch by the owner's decision (option 3) · **Status:** Resolved (gnu.org not asked from CI; the cause outside Reflux, not diagnosed further) · **Last updated:** 2026-11-25
- **Research:** the link is valid: the checker's own request (Node `fetch`, its user agent) answers **200 in about 1 s** from the Windows machine, and `curl` likewise. gnu.org has failed on GitHub's runners before and passed on the next run (VERIFICATION.md: runs on `dc70958`/`3986850`, "gnu.org reachable again" on `2f59c37`, and run 45345526419), and the `LICENSE` download from gnu.org was once "reset by the network". The checker asked each link **once** and turned any thrown error into a bare "unreachable", so the log never said why.
- **Fix:** a link is asked up to **three times, only on a transient failure** (no answer at all, or a 5xx), with a short back-off; each attempt has a 20 s timeout; the error and its cause code are printed. A 4xx, or no answer after the last attempt, fails exactly as before: not weakened.
- **Verification (Windows 11):** the site's 27 pages, 31 external links, **0 broken**; fault injection, a host that does not exist: retried twice, then **unreachable** with `ENOTFOUND` (still a failure); the website's `npm test` 34/34.
- **Hosted CI on `5b39a27` (run 45477982184), with the retry and the cause printed:** gnu.org failed **all three attempts with `ETIMEDOUT`** (the TCP connection never answered), and again on a re-run of the Website job on a fresh runner. So it was not a dropped connection a retry recovers: those runners could not connect at all, while the Windows machine gets 200 in about 1 s. The retry stays (harmless, and it names the cause); it is **not** the fix.
- **Narrowed:** www.gnu.org has an IPv4 (209.51.188.116) and an IPv6 (2001:470:142:5::116) address; GitHub's Ubuntu runners have no IPv6, and Node 22's `fetch` tries the addresses in turn with a short per-address window. Either gnu.org does not answer those runners' addresses (it has failed on some runs and passed on others for weeks), or the IPv4 attempt does not answer within Node's window. The combined `ETIMEDOUT` does not tell them apart.
- **Options, for the owner:** (1) print each address's own error and widen Node's per-address window (fixes the second cause, diagnoses the first; one more CI run); (2) not fail CI when a third-party host times out from the runner (a weakening: the owner's decision); (3) keep the link (the site serves the GPL itself at `/license` and `license.txt`; gnu.org is linked from `Legal.jsx` and `seo.js`) and decide how CI treats it.
- **The owner's decision (2026-11-25): option 3.** The gnu.org link stays on the site (`Legal.jsx`, `seo.js`); the CI link check does **not ask `gnu.org` or `www.gnu.org`**, and only those exact hosts (`ftp.gnu.org`, look-alike hosts and every other external link are still checked; the retry and the printed cause stay for them). The check prints each link it does not ask, with the reason. No further CI run is spent diagnosing IPv4 and IPv6 for this domain. Documented in WEBSITE.md, "Checks".
- **Verified (Windows 11):** `check-links`: 27 pages, **30 external links checked, 1 not asked (www.gnu.org, listed), 0 broken**; the exclusion is exact (`gnu.org`, `www.gnu.org` not asked; `ftp.gnu.org`, `notgnu.org`, `www.gnu.org.evil.example` asked); `npm test` 34/34.

<a id="b-099"></a>
### B-099: Linux integrated player: when Skiko replaces its OpenGL context during playback, libmpv's objects were freed in the new context and the picture was lost

- **Category:** Bug (resource lifecycle) · **Priority:** Medium · **Platforms:** Linux desktop with OpenGL (`linux-mpv-gl-integrated`)
- **Components:** `desktop/app` `LinuxGlVideo.draw` (the `context !== skia` branch), `MpvPlayer.selectVideo`; test `IntegratedContextReplacementTest` (new)
- **How found:** the Windows audit's review noted the edge (CACHYOS-FINAL-AUDIT-LEDGER.md, "Review of the change set"); examined and reproduced in the owner's final CachyOS session (2026-11-25).
- **Phase:** found in the audit of a review branch; fixed on that branch · **Status:** Fixed (physical Linux) · **Last updated:** 2026-11-25
- **What Skiko does (Skiko 0.150.1's bytecode, `javap`):** `LinuxOpenGLRedrawer.dispose()` makes its context current, closes its Skia `DirectContext` (an unref, not an abandon) and destroys the GLX context; a new redrawer makes a new context. Skiko makes a new redrawer when the layer is re-added to a window or when drawing fails (`RedrawerManager.findNextWorkingRenderApi`). So when Reflux's draw first sees the new context, the old one, and everything libmpv and the texture had in it, is already gone.
- **The defect (three parts):** (1) `render.free()` ran with the **new** context current, so libmpv's `glDelete*` calls went to names that may be Skia's own live objects there; (2) `image.close()` dropped the last reference to the old Skia context, whose teardown deletes its resources the same way; (3) libmpv turns video **off** when its render context is freed during playback (the video track is deselected), so after the render context was made again **no frame ever came**: sound only. Also found when it was first exercised: (4) one draw during the replacement could not reach Skiko's context, and `fail()` marked the integrated picture failed for good ("the next one uses `linux-x11-shape-fallback`").
- **Fix:** on a replacement, the old render context and image are let go with **no** GLX context current (`glXGetCurrent*`, `glXMakeCurrent(dpy, None, NULL)`, then the previous context made current again), where OpenGL calls do nothing; the video track in use is selected again once the new render context exists (`MpvPlayer.selectVideo`); a draw that cannot reach the context after the render context exists skips that frame and asks for the next (logged once), and fails only while the playback's render context has never been made.
- **Test:** `IntegratedContextReplacementTest` (window test, Linux with OpenGL; skips on the fallback): plays a film, makes Skiko replace its redrawer through its own recovery path, and requires a new context, the "selected again" log line, **frames rendered in the new context**, playback going on, nothing failed, two render contexts made and one released.
- **Verified (physical Linux: the owner's CachyOS laptop, Hyprland, Intel Arc Mesa, workspace 3 shown on the laptop's screen, no sound device):** before the fourth part's fix the test failed on "nothing failed" (the transient `fail`) while the picture did come back; after: **PASS 4 of 4**. **Negative control:** with `selectVideo` taken out, the test **FAILS** ("Timed out waiting for frames rendered in the new context"): without it the picture never comes back.
- **Limits of the verification (recorded 2026-11-25):** one machine and one driver (Intel Arrow Lake, Mesa, under Hyprland through XWayland); the replacement was **forced** through Skiko's own recovery path by the test, never seen happening by itself in use; NVIDIA's OpenGL, AMD, GNOME and KDE not exercised; hosted CI cannot run the test (no OpenGL on its runner: it skips on the fallback). First re-check: Ubuntu, [UBUNTU-TRANSITION.md](history/linux/UBUNTU-TRANSITION.md), P5.
- **Ubuntu (2026-11-26), physical laptop, GNOME on Wayland:** `IntegratedContextReplacementTest` **passed** on the real session (window visible). [UBUNTU-VALIDATION-REPORT.md](verification/UBUNTU-VALIDATION-REPORT.md)

<a id="b-100"></a>
### B-100: Linux fallback player: the mini-player's video has square corners (pre-existing)

- **Category:** Visual · **Priority:** Low · **Platforms:** Linux desktop on the native-window fallback (`linux-x11-shape-fallback`) only
- **Components:** `LinuxVideoShape` (the X shape is rectangles minus the controls' holes), `SurfaceCorners` (Windows only)
- **How found:** the owner's final CachyOS session (2026-11-25), `MiniPlayerCornersWalkTest` with `REFLUX_NATIVE_VIDEO_WINDOW=1` on the real desktop: in every docked capture the test card reaches the very corner pixel of the picture box (diagonal offset 0), and the rim's arcs (drawn beneath the native surface) are covered.
- **Phase:** Beta 3 · **Status:** Open (pre-existing) · **Last updated:** 2026-11-25
- **Not a regression:** on `main` (`1d65dcf`) `SurfaceCorners` says "Only on Windows; elsewhere it does nothing and the picture stays square"; Linux's docked video has always been square on the native-window path. The branch rounds only the integrated picture (`pictureClip`, `1da6aaf`), which is what a Linux desktop with OpenGL uses.
- **Possible fix (for the owner to place):** cut the corners from the surface's X bounding shape (a few rectangles per corner at the dock's radius), with the rim drawn beneath showing at the edge.

<a id="b-101"></a>
### B-101: Once, a Reflux whose window was closed stayed running without a window (unattributed)

- **Category:** Bug (to be told apart) · **Priority:** Medium (a process left behind) · **Platforms:** Linux (the packaged build, XWayland on Hyprland)
- **How found:** the owner's final CachyOS session (2026-11-25), the dock walk: two instances launched from the desktop entry, both windows closed by the compositor (`hl.dsp.window.close`). The second instance exited; the first (started 4 s earlier) stayed: no window, 70 threads (the AWT event and shutdown threads among them, `reflux-stream` in `accept`, a `cuda…` thread from the start-up decoding probe), every thread sleeping. It did not answer a JVM attach (`jstack`: no response) and ignored SIGTERM; it was ended with SIGKILL. Its SIGQUIT thread dump went to a pipe that kept only the last lines, so it was lost.
- **Phase:** Beta 3 · **Status:** Open (not reproduced: 1 of 2 tries; the same walk again, both exited; and every other close in the session exited) · **Last updated:** 2026-11-25
- **Known:** the exit path (`leave` → `finishPlayback` → `exitApplication`, `exitProcessOnExit`) is unchanged from `main`; no playback had run in that instance. A process that keeps its AWT threads, refuses an attach and ignores SIGTERM looks stuck inside the JVM's shutdown (a shutdown hook or a native call). **Not known:** which; whether `main` does it too.
- **Next:** repeat two-instance launch and close with output to a file, and take `kill -QUIT` the moment one lingers.
- **Ubuntu (2026-11-26):** not reproduced: the packaged archive, four rounds of two instances closed through the window manager (`wmctrl -c`) on GNOME, **8 of 8 processes exited within 2 s**. Still open (unattributed). [UBUNTU-VALIDATION-REPORT.md](verification/UBUNTU-VALIDATION-REPORT.md)

<a id="b-102"></a>
### B-102: Linux: Reflux's logo missing on the dock and in the file manager until `install-desktop-entry` was run

- **Category:** Bug / integration (the owner's request in the final CachyOS session) · **Priority:** Medium · **Platforms:** Linux desktop (the packaged archive)
- **Components:** `desktop/app` `LinuxDesktopEntry` (new), `Main.kt` (start-up), `packaging/linux/install-desktop-entry`; tests `LinuxDesktopEntryTest`
- **How found:** the owner asked that the logo show on the dock and in the file manager (2026-11-25). Measured on the real desktop (Hyprland, Noctalia's dock, Dolphin): with no entry, a running Reflux showed a **generic gear** on the dock; the extracted `bin/Reflux` showed a generic executable icon in Dolphin; with the entry installed, the dock showed Reflux's logo (512 × 512, sharp), grouped the running window on the pinned icon, counted two instances on it, and Dolphin showed the logo on `reflux.desktop`.
- **Cause:** on Wayland a dock finds a window's icon only through a desktop entry named for its app id (`reflux`, CACHY-001); it does not read the icon Reflux sets on its window. A file manager shows no icon for a Linux executable. So nothing in the window can fix it: the entry has to exist.
- **Phase:** found in the owner's final CachyOS session; fixed on the review branch **as a feature, the owner's choice** ("Self-install on start"); release scope with the installer's (decision 3) · **Status:** Fixed (automated; physical Linux check below) · **Last updated:** 2026-11-25
- **Fix:** a **packaged** Reflux (the jpackage launcher's `jpackage.app-path`; never a Gradle or test run) writes, at every start, the same entry the script writes (byte for byte; one quoting) to `$XDG_DATA_HOME/applications/reflux.desktop`, atomically: added when absent and never written before; updated when it is Reflux's own (`X-Reflux-Managed=true`, which the script now writes too) and the folder has moved; **an entry someone made or edited is left alone; one Reflux wrote and someone removed is not put back** (a mark in Reflux's data folder). It also writes `Reflux.desktop` beside `bin/` in the extracted folder (where writable), so the file manager shows the logo there. A failure is only logged.
- **Verified (physical Linux, the owner's laptop, Noctalia's dock and Dolphin, 2026-11-25):** the packaged archive started with **no** entry anywhere and nothing pinned: Reflux added its entry and `Reflux.desktop` beside `bin/` (logged), the dock showed **Reflux's logo** for the running window from this first launch, and Dolphin showed the logo on `Reflux.desktop` in the extracted folder; Reflux exited when its window was closed. The entry and the mark were removed afterwards, leaving the owner's system as it was.
- **Hosted CI on `99fff18`:** the Windows check failed one of these tests (line 99 compared the `Exec` line with a raw path; a Windows path's backslashes are doubled by the quoting, correctly): the test now compares the whole entry for the moved folder. The product code was unchanged.
- **Tests:** `LinuxDesktopEntryTest` 6/6: the entry equals the script's for a folder name with `$`, quotes, a backtick and a backslash; not written off Linux or outside a package; added once, unchanged on the next start, updated after a move, not re-added after removal, someone's own entry untouched; a path a menu cannot start from gets none.
- **Ubuntu (2026-11-26), GNOME 50.1:** the packaged archive's first start wrote `~/.local/share/applications/reflux.desktop` (valid by `desktop-file-validate`, `StartupWMClass=reflux`) and `Reflux.desktop` beside `bin/`; the window's class is `reflux`. The dock icon was not looked at. The entry was removed afterwards (`install-desktop-entry --remove`). [UBUNTU-VALIDATION-REPORT.md](verification/UBUNTU-VALIDATION-REPORT.md)

<a id="b-103"></a>
### B-103: Linux: launching Reflux again opens a second Reflux (observation)

- **Category:** Behaviour (for the owner) · **Priority:** Low · **Platforms:** Linux (seen); not checked elsewhere
- **How found:** the dock walk (2026-11-25): a second launch from the desktop entry started a second process with its own window (the dock counted "2"), rather than bringing the first forward. Reflux has no single-instance handling (none in the code, on `main` either).
- **Phase:** Beta 3 · **Status:** Open (the owner's decision whether a second launch should focus the first) · **Last updated:** 2026-11-25
- **Ubuntu (2026-11-26):** the same on GNOME: a second launch opened a second Reflux. Unchanged (the owner's decision).

<a id="b-104"></a>
### B-104: Linux integrated player: `vaapi-copy` opens the first render node, which on the Ubuntu laptop is the NVIDIA GPU, so decoding wakes NVIDIA

- **Category:** Bug (power and efficiency; playback works) · **Priority:** Medium · **Platforms:** Linux desktop with the integrated picture on a hybrid laptop whose discrete GPU owns `/dev/dri/renderD128` (seen: the owner's laptop on Ubuntu 26.04.1; not seen on the same laptop under CachyOS)
- **Components:** `desktop/app` `DesktopPlatform.renderedHwdec` (asks `vaapi-copy,auto-copy-safe` on Mesa); libmpv's VA-API device choice
- **How found:** the Ubuntu validation (2026-11-26, [UBUNTU-VALIDATION-REPORT.md](verification/UBUNTU-VALIDATION-REPORT.md)), `IntegratedPlayerPerfTest` on the real GNOME session: the integrated picture drew on Intel's Mesa (as designed), but every file decoded with **`nvdec-copy`** (1080p H.264, 1080p HEVC 10-bit, 4K HEVC); NVIDIA's power rose from 6.7 W (library shown) to 11.6–12.0 W while playing. On CachyOS the same walk gave `vaapi-copy` on Intel (CACHY-011).
- **Phase:** Beta 3 (found after the freeze; not a playback blocker) · **Status:** Open (cause established; fix proposed, not implemented) · **Last updated:** 2026-11-26
- **Cause (measured):** on this Ubuntu install the NVIDIA GPU is `card1`/`renderD128` and the Intel GPU `card2`/`renderD129`. libmpv's `vaapi-copy` makes its own VA display on the default DRM render node, `renderD128`: libva looks for `nvidia_drv_video.so` there and fails ("Failed to initialize VAAPI"), so `auto-copy-safe` takes `nvdec-copy`. `mpv --hwdec=vaapi-copy` (the same libmpv 0.41.0) reproduces it; with `--vaapi-device=/dev/dri/renderD129` VA-API initializes with Intel's iHD driver at once. `vainfo` agrees (`renderD128`: `vaInitialize failed`; `renderD129`: iHD 26.1.2, H.264/HEVC/VP9/AV1 decode). So the design's assumption, "decode on the GPU that draws (`vaapi-copy`) and leave the other asleep", silently fails wherever the drawing GPU is not the first render node.
- **Impact:** none on the picture or sound (hardware decoding, no dropped decoder frames, A/V sync ~0); the NVIDIA GPU is woken for every playback (about +5 W on this laptop, which matters on battery). Not a regression of the released player (`main`'s Linux player uses its own window and `hwdec=auto-safe`, with no device chosen either).
- **Proposed fix (Beta 3, for the owner to place):** pass libmpv's `vaapi-device` the render node of the GPU Skiko draws with (the node whose PCI vendor matches the OpenGL vendor Skiko reports, from `/sys/class/drm/renderD*/device/vendor`), and keep today's request where none matches; a unit test for the node choice, and a perf walk on this laptop showing `vaapi-copy` and NVIDIA idle.
- **Related:** the start-up decoding probe (`MpvDecodeProbe`, `auto-copy-safe`, the same on `main`) also initializes CUDA on the NVIDIA GPU at every start (the test JVM is listed by `nvidia-smi` as a compute process); whether that alone keeps the GPU awake could not be measured here (the external monitor is wired to the NVIDIA GPU, which keeps it active).

<a id="b-105"></a>
### B-105: `LocalFolderSourceTest.watchesForNewMediaIncludingNewFolders` waited a fixed 300 ms for the folder watcher to register (test race, Windows CI)

- **Category:** Test race · **Priority:** Medium (it fails the hosted Windows check, which stops the build) · **Platforms:** seen on hosted Windows CI; reproduced on Windows 11 under CPU load; any platform in principle
- **Components:** `sources/local` test `LocalFolderSourceTest.watchesForNewMediaIncludingNewFolders`; `LocalFolderSource.changes()` (unchanged)
- **How found:** hosted CI run 45785524870 on `cachyos-final-audit` at `76b526a` (a documentation-only commit), job 139791205394, Check (windows-2025), Windows Server 2025, Temurin 21.0.13, Gradle 9.7.1: `:sources:local:test` FAILED, `TimeoutCancellationException: Timed out waiting for 10000 ms` at `LocalFolderSourceTest.kt:94`, the **first** wait; 6 tests, 1 failed. CI does not pass `--continue`, so the build stopped there and the remaining tasks (the UI tests among them) did not run; the window-test step was skipped. Every other job of the run passed.
- **Phase:** Beta 3 (found in the Beta 3 integration, [BETA-3-INTEGRATION-AUDIT.md](verification/BETA-3-INTEGRATION-AUDIT.md)) · **Status:** Fixed (test only; Windows 11, physical; hosted CI on `54c21a4` passed) · **Last updated:** 2026-11-26
- **Not caused by the Linux work:** the production tree of `76b526a` is identical to `406382b`'s, on which every CI job passed (run 45585080925); `sources/local` was last changed on 2026-10-18 (`c8b5820`). No earlier failure of this test in the reachable CI history (the failed runs since 2026-11-13 were checked; each failed on something else, already tracked).
- **Cause:** `changes()` registers its `WatchService` inside `callbackFlow`, once collection has started on `Dispatchers.IO`; the test waited a fixed `delay(300)` and then created the first file. When starting the collector takes longer than 300 ms (a loaded runner: the failure came while `:ui:compileKotlinJvm` and other tasks ran beside it), the file is created before the folder is watched, no event is ever reported, and the 10 s wait times out. Every later wait in the test comes after the watcher has reported, which is why only the first one failed.
- **Reproduced (Windows 11, physical, the development machine):** in isolation 15 of 15 passed; with every core busy (40 spinning shells on 20 logical processors) **8 of 12 failed**, the same exception at the same line. **Mechanism confirmed:** with the wait lengthened to 5 s (an experiment, not committed) 12 of 12 passed under the same load.
- **Fix (test only):** the fixed wait is replaced by a handshake: rewrite the already-present `Movies/existing.mkv` until the watcher reports (each try 200 ms, at most 10 s), then let events settle and drain them, as the test already does between its steps. Nothing asserted is removed or loosened: a new file, a new folder, a file in that folder, and an ignored `.txt` are checked as before.
- **After (Windows 11, physical):** the class 6 of 6; the test under the same load **12 of 12**. **Negative control:** with the watcher no longer registering new subfolders (product code changed temporarily, reverted) the test **fails** (timeout at the wait for the file in the new folder), so the handshake does not hide a broken watcher.
- **Hosted CI (2026-11-26):** `beta-3` pushed with the owner's approval; `54c21a4` ran CI [45811843168](https://github.com/Rifaque/reflux/actions/runs/45811843168) and the release rehearsal [45811843105](https://github.com/Rifaque/reflux/actions/runs/45811843105): every applicable job passed on its first attempt. The Windows check ran `:sources:local:test` and this test passed; the Windows desktop-window step is Linux-only by design (skipped), and the Linux check's desktop window tests passed. One green run does not rule out a rare recurrence.
- **Question, not verified:** in the app, the library's initial refresh and the watcher start independently (`AppState.start`, `LibraryPipeline.watch`), so a file added in the moment before the watcher registers would presumably wait for the next refresh. Not observed, not measured; recorded for the owner, not changed.

<a id="b-106"></a>
### B-106: `AppState`'s start-up coroutines can touch members not yet initialised (NullPointerException), reported as `UncaughtExceptionsBeforeTest` in `VisualSystemTest` (test race)

- **Category:** Test reliability (a construction-order hazard in `AppState`, seen only in tests) · **Priority:** Medium (it fails the hosted Linux check) · **Platforms:** hosted Linux CI; reproduced on Windows 11
- **Components:** `ui` `AppState` (`init` at `AppState.kt:237–251`; `haptics` at 284, `profiles` at 1528); test `VisualSystemTest.theMediaAtmosphereSwitchIsAnOrdinarySwitchAndIsRemembered` (line 167); the victim `VisualSystemTest.artworkIsReadForItsColorOnceNotOnEveryVisit`
- **How found:** CI [45853439508](https://github.com/Rifaque/reflux/actions/runs/45853439508) on `9e5769e`, Check (ubuntu-24.04), the ordinary single-JVM UI test run: `VisualSystemTest.artworkIsReadForItsColorOnceNotOnEveryVisit` FAILED, `kotlinx.coroutines.test.UncaughtExceptionsBeforeTest` at `VisualSystemTest.kt:250` (1 of 567); the window tests did not run. Its report's suppressed exception: `NullPointerException: Cannot invoke "kotlin.Lazy.getValue()" because "<local1>" is null` at `AppState.getHaptics(AppState.kt:284)` from `AppState$2.invokeSuspend(AppState.kt:242)`, a `StandaloneCoroutine` on `Dispatchers.Default`. The first time this has been seen in the normal, serial UI test run.
- **Phase:** Beta 3 · **Status:** Investigating (cause established by reproduction; fix proposed, not implemented) · **Last updated:** 2026-11-26
- **What the failure means:** `UncaughtExceptionsBeforeTest` is `kotlinx-coroutines-test` reporting an exception that escaped some coroutine while no test was running; the test that reports it is the next one to start, not necessarily its cause. Here `artworkIsReadForItsColorOnceNotOnEveryVisit` is the victim.
- **Cause (from the code, then reproduced):** `AppState`'s `init` block launches coroutines on the `scope` it is given; one of them, after `read { … }`, sets `haptics.interfaceOn`. `haptics` is a `by lazy` property declared **after** that `init` block, and Kotlin assigns its delegate only when construction reaches it. With a scope that dispatches to another thread, the coroutine can resume before the constructor has got there: the delegate is still null. `theMediaAtmosphereSwitchIsAnOrdinarySwitchAndIsRemembered` builds a second `AppState` on `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, outside any `runTest` and with no exception handler, so the exception is collected by `kotlinx-coroutines-test` and thrown at the next test's start.
- **Reproduced (Windows 11, physical; a temporary probe test, never committed, deleted):** `AppState` built as line 167 builds it, 400 times, with a handler counting the exceptions: **1 of 400** `getHaptics` NullPointerExceptions idle; **10 of 400** with every core busy. The same probe found a **second instance of the same hazard**: 6 of 400 under load in `AppState.setProfiles`, from `loadProfiles`, launched by `init` through `askWhoIsWatchingAtStart`: the `profiles` property (`AppState.kt:1528`, a `mutableStateOf`) not yet initialised (the trace's `AppState.kt:2111` is Kotlin's mapping of the inlined `setValue`, past the file's end).
- **Not in the product (from the code):** the desktop builds `AppState` with `rememberCoroutineScope` (the UI thread, which is running the constructor) and Android with `Dispatchers.Main.immediate` on the main thread, where each launch runs only to its first suspension (`read` always suspends) and resumes on that same thread after the constructor. Neither can resume a start-up coroutine before construction ends. Only a scope on another thread, as in this test, can.
- **Relation to [B-079](#b-079):** B-079's parallel-JVM experiment (run 44991408564 on `2f59c37`) failed the same test with the identical suppressed exception (`AppState.getHaptics(AppState.kt:283)` from `AppState$2.invokeSuspend(AppState.kt:242)`; one line earlier in that version of the file). That failure is very likely this cause; it is not proven to be, and B-079's two other failures (`SourcesUiTest`, B-074's class; `HomeFocusTest`) are different and stay with B-079. More contention (two JVMs then, a busier hosted runner now) widens the race.
- **Proposed fix (not implemented; the owner places it):** (1) `AppState`: launch the start-up coroutines only after every property is initialised: move that `init` block to the end of the class body (property initialisers and `init` blocks run in order), or start them from a method called after construction. This removes the hazard at its source, for both instances found. (2) `VisualSystemTest.kt:167`: give the extra scope a handler that fails the test that made it (or build it from the test's own scope), so a coroutine's error is reported where it happens, not by the next test. (3) Keep the probe's check as a regression test once (1) is in: many constructions on `Dispatchers.Default`, no exception.
- **Splitting the UI tests (S1 in [the CI performance audit](engineering/CI-PERFORMANCE-AUDIT.md)) waits for this fix.**

<a id="b-107"></a>
### B-107: `SilentSourceTest.dataReturningOnTheHeldConnectionClearsTheFailureAndPlaybackGoesOn` failed once in CI's window tests (intermittent)

- **Category:** Test reliability · **Priority:** Low · **Platforms:** hosted Linux CI (the window-test job, Xvfb, the shaped-window fallback)
- **Components:** `desktop/app` test `SilentSourceTest` (unchanged since `0fbae46`, 2026-10-16)
- **How found:** CI [45897629306](https://github.com/Rifaque/reflux/actions/runs/45897629306) attempt 1, on `10177a3` (the gitleaks fingerprint commit after the attribution cleanup; code identical to `0bd84c9`), job "Desktop window tests (ubuntu-24.04)": `java.lang.AssertionError at SilentSourceTest.kt:118` (101 tests, 1 failed, 14 skipped). Line 118 is `assertNotNull(world.app.playback?.error)`, after the source is made to stall and 30 s of the test's virtual time are advanced while the real libmpv engine runs on the wall clock.
- **Phase:** Beta 3 · **Status:** Open (intermittent; not reproduced) · **Last updated:** 2026-11-27
- **Evidence:** the same job on identical code passed on `fdb68c7` (45881492615), `0bd84c9` (45891073808), `0efc15e` (45894717175, before it was superseded) and in this run's **attempt 2** (the failed jobs re-run once, to classify the failure, both attempts recorded here). The runner environment differed from the previous run only by a binutils security update (`2.42-4ubuntu2.10` to `.11`; libmpv, FFmpeg, Mesa and fonts the same), which is why the test environment fingerprint changed and the Linux check re-ran its tests (`docs/engineering/CI-PERFORMANCE-AUDIT.md`, L2). Locally, WSL2 Ubuntu 24.04 (a VM) on Xvfb with libmpv 0.37.0: the class 10 times, **20 of 20** executions passed; that is the class alone, not the whole suite in one JVM as CI runs it.
- **Suspected, not established:** the test mixes the test clock and the wall clock, the pattern of [B-044](#b-044); a busier runner could leave the engine short of the state the advanced virtual time assumes. No fix proposed until it is reproduced or understood.

## Beta 3 backlog (from Phase 3's closure, 2026-11-20)

Phase 3 is closed and Beta 2's fix scope is frozen (DECISIONS.md, 2026-11-20, "Phase 3 accepted and formally closed"). Every bug, UX issue, request or improvement found from now on is entered here, and in its own entry with the next free number, for **Beta 3 or a later release**, unless the owner explicitly authorises it as a critical Beta 2 blocker.

| Item | What | Status |
| --- | --- | --- |
| [B-074](#b-074) | Two `SourcesUiTest` cases time out under the full run (seen on hosted Linux 2026-11-24) | Investigating (not reproduced in 8 runs here; the wait now reports its state) |
| [B-061](#b-061) | A Skia crash after a Direct3D failure (Windows, the owner's build) | Investigating; a Beta 3 candidate (the owner places it) |
| [B-044](#b-044) | Test intermittents mixing the test and wall clocks | Investigating (test infrastructure) |
| [B-056](#b-056) | Blank on closing the mini-player | Deferred by the owner (a later beta) |
| [B-081](#b-081) | `FirstRunTest` once found the first folder empty (window test, Linux CI) | Open (Beta 3) |
| [B-088](#b-088) | Android: a film in a small box with black on all four sides | Blocked (no device in the CachyOS audit) |
| [B-089](#b-089) | Two phone-layout UI tests depend on the machine's default font | Open (test reliability) |
| [B-090](#b-090) | After the dock's Stop, Back on the details page did not return Home (window test; baseline too) | Open |
| [B-091](#b-091) | A controller's Start no longer closed Quick View (regression on `cachyos-final-audit`) | Fixed on the branch (automated) |
| [B-092](#b-092) | Linux player window tests failed on the shaped-window fallback: a film shorter than its walk, and a walk that clicked before the dock had moved | Fixed on the branch (tests; and the fallback surface's one-frame lag) |
| [B-093](#b-093) | Pad 7: the Search walk's tap on a suggestion did not fill the query (baseline too) | Fixed on the branch (test; physical Pad 7) |
| [B-094](#b-094) | Fire TV: playback from a chosen path intermittently does not start in `TelevisionOnDeviceTest` | Open (unattributed) |
| [B-095](#b-095) | Phone and tablet emulators: device walks fail on the baseline as on the branch | Open (pre-existing, emulator data; the phone's Search part fixed by B-093) |
| [B-096](#b-096) | Android: with the system keyboard up, accessibility places Search's index about 200 px below where it is drawn | Open (pre-existing; accessibility) |
| [B-097](#b-097) | `PlannedPlaybackWindowTest` named a film that has left the live test server | Fixed on the branch (test; Windows, live server) |
| [B-098](#b-098) | The website's link check failed CI because GitHub's runners cannot connect to gnu.org | Resolved by the owner's decision: gnu.org not asked from CI, every other link still checked |
| [B-099](#b-099) | Linux integrated player: a replaced OpenGL context freed libmpv's objects in the new one and lost the picture | Fixed on the branch (physical Linux) |
| [B-100](#b-100) | Linux fallback player: the mini-player's video has square corners | Open (pre-existing) |
| [B-101](#b-101) | Once, a closed Reflux stayed running without a window | Open (unattributed; not reproduced) |
| [B-102](#b-102) | Linux: no logo on the dock or in the file manager until `install-desktop-entry` had run | Fixed on the branch (Reflux keeps its own entry; the owner's choice) |
| [B-103](#b-103) | Linux: launching Reflux again opens a second Reflux | Open (the owner's decision) |
| [B-104](#b-104) | Linux integrated player: `vaapi-copy` opens the first render node (the NVIDIA GPU on the Ubuntu laptop), so decoding wakes NVIDIA | Open (cause established; fix proposed) |
| [B-105](#b-105) | `LocalFolderSourceTest` waited a fixed 300 ms for the folder watcher (Windows CI test race) | Fixed (test only; hosted CI passed on `54c21a4`) |
| [B-106](#b-106) | `AppState`'s start-up coroutines can touch members not yet initialised; seen as `VisualSystemTest`'s `UncaughtExceptionsBeforeTest` | Investigating (cause reproduced; fix proposed) |
| [B-107](#b-107) | `SilentSourceTest` failed once in CI's window tests | Open (intermittent; not reproduced) |
| [B-084](#b-084) | Hyprland: the player as two windows (video without controls, controls on a blank panel) | Fixed on the branch: the integrated renderer, the shaped window its fallback (physical Linux, one machine); the owner's acceptance and release placement pending; validated on Ubuntu 26.04.1 (physical, GNOME, 2026-11-26) |
| [F-P1](#f-p1-and-f-p2) | A controller's Start (≡) shrinks the sidebar to its icons; Reflux's name toggles it (with B-091, which exists only because of it) | Fixed on the branch; **Beta 3** by the closure's recommendation (the owner places it); still on `cachyos-final-audit`, to be separated |
| [B-085](#b-085) | The compact keyboard and its LS (Shift) / LT (&123) shortcuts | Fixed on the branch; **Beta 3** by recommendation; to be separated |
| [B-086](#b-086) | The smaller PIN pad | Fixed on the branch; **Beta 3** by recommendation, with B-085 (one file's layout); to be separated |
| [B-080](#b-080) | A live-server test's remux case can be unconfirmed (local only) | Open (Beta 3) |
| [B-079](#b-079) | The Linux CI job no longer fits its 60-minute limit | Fixed: Linux's limit 75 min (the owner's decision); parallel JVMs tried and reverted |
| [B-078](#b-078) | Three real-window tests expected the interface before Phase 2 (tests only) | Fixed before Beta 2's release |
| [B-077](#b-077) | `MpvEngineTest`'s credentials-log test can race its log thread (test only) | Open (Beta 3) |
| [B-076](#b-076) | A remote's Right lost on Downloads just after the list changes (CI, Linux) | Fixed before Beta 2's release (the owner's scoped fix) |
| [B-075](#b-075) | Store "What's new" included category headings (release tooling) | Fixed in Phase 4 |
| [F-003](#f-003) | Video-synchronised haptics | Deferred: a future version, **no release assigned**; needs its own research, hardware validation, design and plan first |

### Review-branch work and its release placement (2026-11-25)

Everything from the CachyOS and Windows audits is on `cachyos-final-audit` and `beta-3` (code `406382b` on both, every hosted CI job green; `cachyos-final-audit` at the closure's `5de9fae`, `beta-3` with later documentation-only commits), **not on `main`** and in no released build. The ledger's "Closure" is the authority for the recommendations and the separation recipe; this table only says, per item, where it is meant to go. Nothing here is decided until the owner decides it.

| Item | Kind | Meant for | Decided by the owner? |
| --- | --- | --- | --- |
| B-082 Quick View, B-087 hints while typing | Bug fixes | The current release (stays on `cachyos-final-audit`) | Recommended; the merge is the owner's |
| B-083 window class, `bin/install-desktop-entry`; B-102 Reflux keeps its own entry | Bug fix and features (Linux only) | The next Linux release that ships an archive, the two together or neither | **Open** |
| B-084 / CACHY-011 integrated renderer (with B-092's production change, B-099) | Architectural (Linux only) | Recommended for the Linux beta | **Open** |
| B-092, B-093, B-097 test fixes; B-098 CI link check | Tests and CI | Wherever the code they test goes | Follows the above |
| F-P1 sidebar shrink, with B-091 | Feature | **Beta 3** | Recommended; separation pending |
| B-085 compact keyboard and LS/LT; B-086 PIN pad | Redesigns | **Beta 3** | Recommended; separation pending |

**The separation** (taking F-P1 with B-091, B-085 and B-086 out of `cachyos-final-audit`, leaving them on `beta-3`) is not done: it needs hand edits across shared files and the full suites, window tests, device walks and hosted CI. Its recipe and the files it touches: the ledger, "Closure", with the corrections of "Windows repository audit (2026-11-25)" below it.

### Entering Beta 2 testing findings

The next phase is systematic Beta 2 testing (after Ubuntu is set up and its major playback blockers fixed). Each finding:

- takes the **next free ID: B-108** onward (F-006 onward only for a feature, once the owner numbers it);
- gets its own entry under "Newly discovered issues" with an `<a id="b-nnn">` anchor (CI's link check fails without one), the fields the entries above use (Category, Priority, Platforms, Components, How found, Phase, Status, Last updated), and **the build it was seen on** (`0.9.0-beta.2` as released, or a branch and commit) and **the device or environment**, physical, emulator or VM said plainly;
- says whether it reproduces on `1d65dcf` (Beta 2 as released) before it is called a regression of anything newer;
- goes to this backlog for Beta 3 unless the owner authorises it as a critical Beta 2 blocker (the freeze; BETA.md, "Beta operations", for the triage levels);
- is linked from BETA.md's known issues if testers can meet it.

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
| 2026-11-24 | **The owner's CachyOS final audit** (the owner's brief authorised these fixes inside Beta 2's freeze; DECISIONS.md, 2026-11-24; [the ledger](verification/CACHYOS-FINAL-AUDIT-LEDGER.md)).
- **New and Fixed:** B-082 (Quick View on a long show), B-083 (Linux window class and desktop entry), B-085 (a compact keyboard, Xbox-style shortcuts), B-086 (the PIN pad), B-087 (page hints under the keyboard).
- **New, Fixed in part:** B-084 (Hyprland: blur and opacity over the picture, mitigated by a rule the new class allows; the workspace move a known limitation).
- **New, Blocked:** B-088 (Android: a small box with black all round; no device in the session).
- **New, Open:** B-089 (two phone-layout UI tests depend on the machine's default font; pre-existing, fail identically on the baseline); B-090 (after the dock's Stop, Back did not return Home in a window test; reproduced on the baseline).
- **F-P1:** Not Started → Fixed, as the owner specified it (Start shrinks the sidebar). | Claude (agent), for the owner |
| 2026-11-24 | **The owner rejected the CachyOS audit; B-084 reopened and fixed** (DECISIONS.md, 2026-11-24 later).
- **B-084:** the real cause was measured on the owner's desktop (windows without alpha on that XWayland); the window-rule mitigation is withdrawn; on Linux the player is now one window, the video surface shaped around the controls beneath it. Fixed in part → Fixed; the owner's check pending.
- **F-P1:** corrected as the owner asked: Reflux's name at the head of the sidebar is its toggle; the button at the foot is gone; Y never shrinks it. | Claude (agent), for the owner |
| 2026-11-24 | **The Windows acceptance audit of `cachyos-final-audit`** (recorded here on 2026-11-25; the entries were written at the time).
- **New and Fixed on the branch:** B-091 (a regression of F-P1), B-093 and B-097 (test fixes).
- **New, Open:** B-092 (then: Linux fallback window tests), B-094 (Fire TV, unattributed), B-095 (emulator data, pre-existing), B-096 (Android accessibility, pre-existing).
- **New, then Resolved:** B-098 (gnu.org from CI; the owner's option 3). | For the owner's review |
| 2026-11-25 | **The owner's final CachyOS session** (recorded here on 2026-11-25).
- **B-092 → Fixed** (two test-synchronisation causes; a production lag fixed separately; negative control); hosted CI green on `99fff18`'s Linux check and on every job of `406382b`.
- **New and Fixed on the branch:** B-099 (context replacement), B-102 (Reflux keeps its own Linux desktop entry: the owner's choice, reversing a decision of 2026-11-24).
- **New, Open:** B-100 (fallback square corners, pre-existing), B-101 (not reproduced), B-103 (the owner's decision).
- **B-077:** seen again on `406382b`. **Closure:** `beta-3` created; the Beta 3 separation deferred (the ledger, "Closure"). | For the owner's review |
| 2026-11-25 | **Windows repository audit before Ubuntu** (documentation only, no code, no runs).
- **Reconciled:** B-083 (the dock pin done in the final session), B-084 (the corners checked on the real desktop; acceptance and placement still open), B-092 (hosted CI recorded in its status), B-099 (verification limits written down), B-089 (a lead: the CachyOS runs did not use CI's pinned test font; status unchanged until a run). Header, executive summary and this history brought up to B-103 (111 entries).
- **Added:** the Beta 3 backlog rows for F-P1, B-085, B-086; [the review-branch work's placement](#review-branch-work-and-its-release-placement-2026-11-25); [how Beta 2 testing findings are entered](#entering-beta-2-testing-findings) (next free ID B-104). | For the owner's review |
| 2026-11-26 | **The Beta 3 integration** ([BETA-3-INTEGRATION-AUDIT.md](verification/BETA-3-INTEGRATION-AUDIT.md)): `cachyos-final-audit`'s Ubuntu validation (`76b526a`: B-104 and the Ubuntu bullets on B-077, B-084, B-089, B-092, B-099, B-101 to B-103) merged into `beta-3`, both branches' bullets kept; B-084's backlog row says it was validated on Ubuntu.
- **New and Fixed (test only):** B-105 (the Windows CI failure of run 45785524870: a test race, reproduced under load, fixed with a handshake, negative control).
- Next free ID **B-106**. | For the owner's review |
| 2026-11-26 | **After the documentation reorganisation's push (`b823a74`).** B-077 seen a fourth time on hosted Linux (CI 45829860032); still Open. The release rehearsal's libmpv source-bundle failure and its correction are not a tracker issue: a documentation change's mistake, recorded in the reorganisation report and VERIFICATION.md. | For the owner's review |
| 2026-11-26 | **The CI performance work** ([CI-PERFORMANCE-AUDIT.md](engineering/CI-PERFORMANCE-AUDIT.md)). New, Investigating: B-106 (the `VisualSystemTest` failure of CI 45853439508; cause reproduced, fix proposed, not implemented). B-079 points to it. Next free ID **B-107**. | For the owner's review |
| 2026-11-27 | **The attribution cleanup and the testing handoff.** New, Open: B-107 (`SilentSourceTest`, intermittent; CI 45897629306 attempt 1 failed, attempt 2 passed; 20 of 20 locally). Next free ID **B-108**. Commit hashes in this tracker are those before the cleanup: [COMMIT-MAP-2026-11-27.md](history/verification/COMMIT-MAP-2026-11-27.md). | For the owner's review |
