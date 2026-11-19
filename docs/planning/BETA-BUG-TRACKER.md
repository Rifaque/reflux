# Beta bug tracker: every finding, its status, and its evidence

**Purpose.** The permanent, detailed record of every bug, UX issue, product improvement, feature request, investigation, performance concern, and verification task found in Beta testing and during Beta 2 development: what was reported, what is known, what was done, what was tested and where, and what is left. A future developer or agent should be able to pick up any issue from its entry alone.

**Scope.** B-001 to B-042 and F-001 to F-005 (the owner's original findings), two registered sidebar proposals (F-P1, F-P2), and every issue found since (B-043 onward). Release gates, phase scope, phase order, and exit criteria are **not** here: they are in [BETA-2.md](BETA-2.md), which links back to this file. What each test run was, in full, is in [VERIFICATION.md](../VERIFICATION.md); decisions and their reasons are in [DECISIONS.md](../DECISIONS.md).

**Last updated:** 2026-11-19 (branch `open-source-gpl`, after commit `0a0396f`).

## Contents

1. [Status definitions and how this tracker is kept](#status-definitions-and-how-this-tracker-is-kept)
2. [Executive status summary](#executive-status-summary)
3. [Phase overview](#phase-overview)
4. [Issues: bugs and product issues (B-001 to B-042)](#issues-bugs-and-product-issues-b-001-to-b-042)
5. [Issues: original feature findings (F-001 to F-005)](#issues-original-feature-findings-f-001-to-f-005)
6. [Unassigned issues and unresolved owner decisions](#unassigned-issues-and-unresolved-owner-decisions)
7. [Newly discovered issues (B-043 onward)](#newly-discovered-issues-b-043-onward)
8. [Regression tracking](#regression-tracking)
9. [Verification and evidence summary](#verification-and-evidence-summary)
10. [Change history](#change-history)

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

As of 2026-11-19. Counts are reconciled against the code, the commits, and VERIFICATION.md.

| | Count |
| --- | --- |
| Original bugs and product issues (B-001 to B-042) | **42** |
| Original feature findings (F-001 to F-005) | **5** |
| Registered proposals outside the original findings (F-P1, F-P2) | 2 |
| Newly discovered issues (B-043 to B-045) | 3 |

Status of the 47 original findings:

| Status | Count | Issues |
| --- | --- | --- |
| Not Started | 35 | B-002, B-004, B-007 to B-017, B-019 to B-021, B-023, B-025, B-027 to B-030, B-032, B-034 to B-040, B-042; F-001, F-002, F-003, F-005 |
| Investigating | 3 | B-006, B-022, B-041 |
| In Progress | 1 | F-004 (Phase 1 foundation implemented; Phase 3 validation to come) |
| Fixed | 7 | B-001, B-003, B-005, B-018, B-024, B-031, B-033 |
| Verified | 0 | (none: every fixed issue still lacks a required check or owner acceptance) |
| Blocked | 0 | |
| Deferred | 0 | |
| Not Reproduced | 1 | B-026 |

Newly discovered: B-043 Fixed; B-044 Investigating; B-045 Not Started. F-P1 and F-P2: Not Started.

| Phase | Items | Fixed | Investigating / In Progress | Not Reproduced | Not Started | Verified | Owner acceptance |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **1** | 11 (9 bugs, F-004 foundation, B-041 baseline) | 7 | 3 (B-006, B-041 baseline, F-004) | 1 (B-026) | 0 | 0 | **Pending**: implemented, with named checks outstanding |
| **2** | 23 issues in 19 work-order steps | 0 | 0 | 0 | 23 | 0 | Not started |
| **3** | 8 issues + 2 checkpoints (B-041 final, F-004 final) | 0 | 0 | 0 | 8 | 0 | Not started |
| Unassigned / pending | B-016, B-017, B-022, B-042, F-005 (and F-P1, F-P2) | 0 | 1 (B-022, measured under B-041) | 0 | 6 | 0 | Owner decisions needed |

**Phase 1 is not complete**: its exit criteria in BETA-2.md are not all met (B-041's baseline is partial; physical checks for B-031, B-003, B-005, B-033, B-024 and B-026 are outstanding), and the owner has not accepted it. Phase 2 has not started. Beta 2 is not cut.

## Phase overview

Scope, order, dependencies, exit criteria, and the gate: [BETA-2.md](BETA-2.md#the-three-phases-the-owners-order). Status here.

**Phase 1: reliability, security, data integrity, foundational investigations**

| # | Issue | Status |
| --- | --- | --- |
| 1 | [B-018](#b-018) Profile deletion authorization | Fixed |
| 2 | [B-031](#b-031) Linux libmpv locale/init | Fixed (VM and containers; physical Linux pending) |
| 3 | [B-001](#b-001) Playback lifecycle/frozen frame | Fixed (physical devices passed; one case outstanding) |
| 4 | [B-026](#b-026) Windows process shutdown | Not Reproduced |
| 5 | [B-003](#b-003) Multiple Jellyfin endpoints | Fixed (live server pending) |
| 6 | [B-033](#b-033) Orphaned favourites | Fixed (device walk pending) |
| 7 | [B-024](#b-024) Resolution/HDR metadata | Fixed (Windows walk pending) |
| 8 | [B-005](#b-005) Scan status UI | Fixed (device walks pending) |
| 9 | [B-006](#b-006) Metadata/search latency | Investigating (local search part fixed) |
| 10 | [F-004](#f-004) Analytics architecture, schema, privacy, consent | In Progress (foundation implemented; nothing transmitted) |
| 11 | [B-041](#b-041) Performance baseline | Investigating (baseline partial) |

**Phase 2: controller, focus, interaction, input, and haptics**

| # | Issue | Status |
| --- | --- | --- |
| 1 | [B-025](#b-025) Shared whole-app controller/focus architecture | Not Started |
| 2 | [B-008](#b-008) Focus recovery after Home | Not Started |
| 3 | [B-007](#b-007) See All and row navigation | Not Started |
| 4 | [B-004](#b-004) Source configuration input | Not Started |
| 5 | [B-019](#b-019), [B-020](#b-020), [B-021](#b-021) Backspace and shortcut handling | Not Started |
| 6 | [B-023](#b-023) Reflux on-screen keyboard | Not Started |
| 7 | [B-011](#b-011) Controller-only search | Not Started |
| 8 | [B-039](#b-039), [B-012](#b-012) Controller mapping review | Not Started |
| 9 | [F-002](#f-002) Controller rumble | Not Started |
| 10 | [F-001](#f-001) Interface haptics | Not Started |
| 11 | [B-036](#b-036) Mobile search/keyboard navigation | Not Started |
| 12 | [B-034](#b-034) Mobile search placeholder | Not Started |
| 13 | [B-035](#b-035) Mobile search chips | Not Started |
| 14 | [B-037](#b-037) Search empty state | Not Started |
| 15 | [B-010](#b-010) Search suggestion focus/clipping | Not Started |
| 16 | [B-009](#b-009), [B-032](#b-032) Focus outlines and poster indicators | Not Started |
| 17 | [B-002](#b-002) Multi-monitor playback controls | Not Started |
| 18 | [B-015](#b-015) Poster alignment | Not Started |
| 19 | [B-013](#b-013) Home hero restoration | Not Started |
| — | [B-042](#b-042) Controller focus and mouse hover conflict | **Pending owner approval** (recommended here, beside B-025); not in the phase until approved |

**Phase 3: media experience, discovery, Trickplay, and final performance**

| # | Issue | Status |
| --- | --- | --- |
| 1 | [B-027](#b-027) Separate Jellyfin libraries and aggregate views | Not Started (owner to confirm the V1.1 → Phase 3 scope before implementation) |
| 2 | [B-038](#b-038) Sorting and filtering | Not Started |
| 3 | [B-030](#b-030) Trickplay previews | Not Started |
| 4 | [B-029](#b-029) Right-stick Trickplay navigation | Not Started (depends on B-030) |
| 5 | [F-003](#f-003) Video-synchronised haptics | Not Started |
| 6 | [B-028](#b-028) Media atmosphere | Not Started |
| 7 | [B-040](#b-040) Recommendations/discovery variety | Not Started |
| 8 | [B-014](#b-014) Featured hero | Not Started |
| 9 | [B-041](#b-041) Final performance optimisation and comparison | Not Started (final checkpoint) |
| 10 | [F-004](#f-004) Final analytics validation | Not Started (final checkpoint) |

**Unassigned or pending:** [B-016](#b-016), [B-017](#b-017), [B-022](#b-022), [B-042](#b-042), [F-005](#f-005), [F-P1 and F-P2](#f-p1-and-f-p2). See [owner decisions](#unassigned-issues-and-unresolved-owner-decisions).

## Issues: bugs and product issues (B-001 to B-042)

Each entry: the header facts, the original report (the master tracker prompt's text, then the findings document's where it adds detail), reproduction, expected and actual behaviour, acceptance criteria, and the work record. Platforms: **P** Android phone, **T** Android tablet, **TV** Android TV / Google TV, **F** Fire TV, **W** Windows, **L** Linux. Components named as "likely" are a reading of the code's layout, not a confirmed location.

<a id="b-001"></a>
### B-001: Video frame stuck while audio continues

- **Category:** Bug (playback) · **Priority:** High, provisional · **Platforms:** P T TV F (Android)
- **Components:** `android:app` (`MainActivity.VideoLayer`, the `SurfaceView` host), `ui` (`AppState.play` / `finishPlayback` / `createPlayer`), the Media3 engine adapter
- **Phase:** 1, step 3 · **Status:** Fixed · **Last updated:** 2026-11-19
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
- **Tests added or modified:** new `SurfaceHandoverOnDeviceTest` (red and blue generated films, colour read off the surface with `PixelCopy`; five cases: mini-player to another film, close then play and reopen, end then another, stop then another, five changes in a row).
- **Automated verification:** the instrumented tests below; full `./gradlew check` on Windows for the JVM modules.
- **Emulator verification:** Google TV emulator (`Reflux_TV`): 4/4 + 1 skipped (no mini-player on TV).
- **Physical/device verification:** old code on the Pad 7: 4 passed, mini-player case **failed** (the reproduction). Fixed code: Pad 7 5/5, Phone 2a 5/5, Fire TV Stick 4K Plus 4/4 + 1 skipped.
- **Regression testing:** unchanged and passing: `SurfaceContinuityOnDeviceTest` 2/2 (Pad 7, Phone 2a, Fire TV, emulator); `PlayerAccessibilityOnDeviceTest` 1/1 (Pad 7, Phone 2a); `ActivityRecreationOnDeviceTest` 1/1 (all four); `TelevisionOnDeviceTest` 2/2 (Fire TV, emulator); `PlaybackOnDeviceTest` 4 + 2 skipped (both handhelds).
- **Evidence:** VERIFICATION.md, "Phase 1", B-001. Debug build `dev.rsxdevelops.reflux.debug`, installed beside the store build.
- **Outstanding work:** "different source types": the handover test used two files from a folder; a server (Jellyfin or Plex) source was not tried. Owner acceptance. *(BETA-2.md had registered this as "Verified on physical device"; it is Fixed here because the source-type criterion is not yet met.)*

<a id="b-002"></a>
### B-002: Playback controls disappear or misrender after moving windows between monitors

- **Category:** Bug (desktop UI/playback) · **Priority:** Medium · **Platforms:** W
- **Components (likely):** `desktop:app` window handling; `ui` (`PlayerControls`, `PlayerOverlay`, `MiniPlayer`, `VideoStage`); the libmpv video surface (`playback:mpv`)
- **Phase:** 2, step 17 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-025 (shared layout and focus); B-041 (window sizes and densities)

**Original description.** Playback controls become mispositioned, disappear, or render incorrectly when the application window is moved between monitors. Mini-player expansion can reproduce or contribute to the issue. *Investigate:* monitor DPI, display scaling, Compose layout sizing, window changes, player surface dimensions, overlays, and mini-player transitions. *Expected:* playback controls and player UI remain correctly positioned and functional across monitor changes and display scaling configurations.

**Findings document.** Moving the window between monitors causes controls to disappear or render incorrectly. Expanding the mini-player can reproduce it. Investigate: per-monitor DPI, Compose sizing and surface recreation.

**Steps to reproduce.** Reported: play on Windows and move the window to another monitor (whether the monitors' scaling differs is not stated), or expand the mini-player. Not yet reproduced by Reflux.

**Expected behaviour.** Controls and player UI positioned and working across monitor changes and scaling.

**Actual behaviour.** Controls mispositioned, missing, or misrendered (reported).

**Acceptance criteria.** Controls correct after moving between monitors of equal and different scaling, while playing, paused, in the mini-player, and on expanding it; a Windows walk on two monitors.

**Work record**

- **Investigation findings / root cause:** none yet; not established.
- **Implementation / tests:** none.
- **Automated / physical / regression verification:** none.
- **Evidence:** the tester's report only.
- **Outstanding work:** everything. Reproducing it needs a two-monitor Windows setup, ideally with different scaling (to arrange with the owner).

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
- **Phase:** 2, step 4 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-023, B-025, B-011, B-019 to B-021

**Original description.** Address entry and source configuration using a controller is unreliable. The address field can lose its keyboard, and pressing A may not reliably reopen it. Navigation behaviour is inconsistent. *Expected:* all source configuration actions must be accessible and predictable with a controller, including focusing fields, opening text input, entering/editing an address, and completing configuration. *Investigate:* focus management, text input lifecycle, controller action dispatch, and keyboard interactions.

**Findings document.** Address field opens the keyboard and then loses it. Pressing A/select again does not reliably reopen it. Source setup navigation is inconsistent. Investigate: focus management, keyboard activation and input-method lifecycle.

**Steps to reproduce.** Reported: on Android TV with a controller, add a source, focus the address field, press A (the keyboard opens, then closes); press A again (it does not reliably reopen). Not yet reproduced by Reflux.

**Expected behaviour.** Every source-setup action reachable and predictable with a controller; the keyboard opens on A and reopens reliably; text kept.

**Actual behaviour.** Keyboard lost; A does not reliably reopen it; inconsistent navigation (reported).

**Acceptance criteria.** Source setup completed end to end with a controller only on Android TV (emulator), Fire TV (physical), and Windows with a controller; text never lost on dismissing and reopening.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Evidence:** the tester's report only.
- **Outstanding work:** everything; builds on B-025's shared architecture and uses B-023's keyboard where that is the chosen input.

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
- **Tests added or modified:** new `ActivityIndicatorLayoutTest` (the sign's bounds against every heading and text field).
- **Automated verification:** 4/4 at phone (412×915: Home, Movies with a very long source name, Search), tablet (1200×800), desktop (1440×900), small window (960×640) and television sizes. Its first run found a second overlap (the dot over the phone's Search field: x 372–396 against the field's 68–388), since fixed.
- **Physical/device verification:** none yet.
- **Regression testing:** full `./gradlew check` passed with it.
- **Evidence:** VERIFICATION.md, "Phase 1", B-005.
- **Outstanding work:** phone, tablet, and TV device walks; owner acceptance.

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
- **Phase:** 2, step 3 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-025, B-008, B-013

**Original description.** Home navigation can skip or mishandle "See All" controls. Focus and row-boundary navigation can be inconsistent. *Expected:* predictable focus order across cards, rows, section actions, and adjacent content using D-pad navigation.

**Findings document.** Vertical navigation sometimes skips See All. Moving down from See All can focus the next row's See All instead of its corresponding content. Investigate: focus graph, row boundaries and lazy-list behaviour.

**Steps to reproduce.** Reported: Home on Windows with a controller; move down through the rows; See All is sometimes skipped; down from See All lands on the next row's See All. Not yet reproduced by Reflux.

**Expected behaviour.** Predictable D-pad order across cards, rows, section actions and adjacent content.

**Actual behaviour.** See All skipped; down from See All goes to the next See All (reported).

**Acceptance criteria.** A defined focus order for Home, documented and covered by UI tests; walked with a controller on Windows and on TV.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Evidence:** the tester's report only.
- **Outstanding work:** everything, on B-025's architecture.

<a id="b-008"></a>
### B-008: Returning Home leaves controller navigation stuck

- **Category:** Bug (controller navigation) · **Priority:** Medium · **Platforms:** W (controller)
- **Components (likely):** `ui` (`HomeScreen`, `Navigator`, `Focus`, lazy-list state)
- **Phase:** 2, step 2 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-025, B-007, B-013

**Original description.** Navigating Home → Movies → Home can leave focus, navigation, or scroll restoration in an unusable state. *Expected:* returning Home restores a valid, usable focus target and appropriate scroll state; navigation must not become stuck or lose focus.

**Findings document.** After Home → Movies → Home, right D-pad/joystick navigation sometimes fails to enter the main content. Focus restoration and scroll position are inconsistent. Investigate: focus restoration and lazy-list state.

**Steps to reproduce.** Reported: Windows with a controller; Home → Movies → Home; press right; focus sometimes does not enter the content. Not yet reproduced by Reflux.

**Expected behaviour.** A valid focus target and scroll state on return; never stuck.

**Actual behaviour.** Right does not enter the content; restoration inconsistent (reported).

**Acceptance criteria.** Round trips between every sidebar destination and Home restore focus and scroll (UI tests); controller walks on Windows and TV.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Evidence:** the tester's report only.
- **Outstanding work:** everything, on B-025's architecture.

<a id="b-009"></a>
### B-009: Focus outline obscures titles

- **Category:** Bug (UI/accessibility) · **Priority:** Medium · **Platforms:** W, F, TV (findings: more severe on Windows, smaller on Fire TV)
- **Components (likely):** `ui` (`Artwork` cards, `Focus`); `design` (scale)
- **Phase:** 2, step 16 (with B-032) · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-032, B-042, B-015

**Original description.** Controller focus outlines can obscure poster/title text or other content. *Expected:* focus indicators must remain visually clear without obstructing important information, appropriate across layouts, screen densities, and TV distances.

**Findings document.** White focus border extends over movie titles. More severe on Windows, smaller on Fire TV. Evidence: Windows and mobile screenshots supplied. Investigate: card bounds, clipping, elevation and scaling.

**Steps to reproduce.** Reported: focus a poster card with a controller; the white outline covers the title below it. The screenshots are the owner's.

**Expected behaviour.** Clear focus that never covers titles or other content.

**Actual behaviour.** The outline covers titles (reported, with screenshots).

**Acceptance criteria.** A focused card's outline never overlaps title text at any density or size (a layout test like `ActivityIndicatorLayoutTest`); looked at on Windows, Fire TV, and TV.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Evidence:** the tester's screenshots (not in the repository).
- **Outstanding work:** everything.

<a id="b-010"></a>
### B-010: Search suggestion focus spacing/clipping

- **Category:** Bug (controller UI) · **Priority:** Medium · **Platforms:** W (controller)
- **Components (likely):** `ui` (Search's suggestion chips)
- **Phase:** 2, step 15 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-011, B-035, B-025

**Original description.** Search suggestion focus behaviour and spacing are inconsistent; suggestions can be clipped or poorly positioned. *Expected:* search suggestions should be visible, correctly spaced, unclipped, and navigable through controller focus.

**Findings document.** Suggestion chips are too close together, focus outlines overlap neighbours, and the final suggestion is clipped. Investigate: responsive layout, horizontal scrolling and padding.

**Steps to reproduce.** Reported: open Search on Windows with a controller and move through the suggestion chips; outlines overlap neighbours; the last chip is clipped.

**Expected behaviour.** Spaced, unclipped, navigable suggestions.

**Actual behaviour.** Overlapping outlines; the last chip clipped (reported).

**Acceptance criteria.** A focused chip's outline never overlaps a neighbour, and the last chip scrolls fully into view (layout test); a controller walk on Windows.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Evidence:** the tester's report only.
- **Outstanding work:** everything; coordinate with B-035 (the mobile chips).

<a id="b-011"></a>
### B-011: Controller-only search lacks usable keyboard

- **Category:** Bug (input/UX) · **Priority:** Medium · **Platforms:** W (controller), TV relevance
- **Components (likely):** `ui` (Search, `TextInput`, `Focus`)
- **Phase:** 2, step 7 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-023, B-025, B-004, B-010

**Original description.** Search is not reliably usable when operated exclusively by a controller. *Expected:* a controller user must be able to open search, focus the field, enter text, navigate suggestions/results, and finish the interaction without requiring a mouse or touch.

**Findings document.** A user cannot reliably enter a search query using only a controller.

**Steps to reproduce.** Reported: on Windows with only a controller, open Search and try to enter a query. Not yet reproduced by Reflux.

**Expected behaviour.** Open, type, browse suggestions and results, and finish with a controller alone.

**Actual behaviour.** A query cannot reliably be entered (reported).

**Acceptance criteria.** The whole search flow completed controller-only on Windows, Android TV (emulator) and Fire TV (physical); UI test of the flow.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Evidence:** the tester's report only.
- **Outstanding work:** everything; depends on B-023 (keyboard) and B-025.

<a id="b-012"></a>
### B-012: Controller shortcuts incomplete or unclear

- **Category:** Product decision / UX investigation · **Priority:** Low · **Platforms:** W (controller)
- **Components (likely):** `ui` (input handling, `Controls`); docs/INPUT_MODEL.md
- **Phase:** 2, step 8 (with B-039) · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-039 (the mapping design), F-005 (customisation), F-P2 (Y and the sidebar)

**Original description.** Existing controller shortcuts are incomplete or not clearly assigned. Y currently opens Sources, X has no useful action, and other buttons/triggers appear unused. *Expected:* establish a consistent, intentional controller mapping; document it and expose useful hints where appropriate; do not assign controls without checking for conflicts with existing behaviour.

**Findings document.** Y opens Sources, X does nothing, and triggers appear unused. Improvement: useful documented and configurable mappings.

**Steps to reproduce.** Press X, LB/RB, LT/RT on Windows with a controller: no useful action (reported).

**Expected behaviour.** A deliberate, documented, conflict-checked mapping with hints.

**Actual behaviour.** As described above.

**Acceptance criteria.** Delivered with B-039: a mapping decision in DECISIONS.md, INPUT_MODEL.md updated, hints shown, conflict analysis recorded, tests of each mapped action.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Evidence:** the tester's report only.
- **Outstanding work:** everything, together with B-039.

<a id="b-013"></a>
### B-013: Home hero position is not restored after scrolling

- **Category:** Bug (navigation UX) · **Priority:** Medium · **Platforms:** W (controller)
- **Components (likely):** `ui` (`HomeScreen` hero and scroll state)
- **Phase:** 2, step 19 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-008, B-007, B-014

**Original description.** Home hero content is not restored to its expected full position after navigating or scrolling. *Expected:* hero and Home layout restoration should be consistent and predictable.

**Findings document.** Returning to the featured area leaves only Play/Details visible rather than restoring the full hero artwork and content.

**Steps to reproduce.** Reported: on Windows with a controller, scroll down Home, then move back up to the featured area: only Play/Details are visible. Not yet reproduced by Reflux.

**Expected behaviour.** Moving back to the hero restores its full artwork and content.

**Actual behaviour.** Only Play/Details visible (reported).

**Acceptance criteria.** Returning focus to the hero scrolls to its top (UI test); walked on Windows and TV.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Evidence:** the tester's report only.
- **Outstanding work:** everything.

<a id="b-014"></a>
### B-014: Featured hero appears alphabetically selected

- **Category:** Product decision / UX investigation · **Priority:** Low (design question) · **Platforms:** all (Home)
- **Components:** `library` (`Home.kt`, `Library.home`, the `featured` selection); `ui` (`HomeScreen`)
- **Phase:** 3, step 8 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-040 (discovery variety), B-013

**Original description.** The featured Home hero appears to be selected alphabetically, for example "2 Fast 2 Furious". It is unclear whether this is intended. *Investigate:* hero selection logic, rotation, randomization, recommendations, and sorting. *Expected:* decide on an intentional, sensible featured-content strategy; do not assume alphabetical selection is desired.

**Findings document.** Hero displays 2 Fast 2 Furious. It may be selecting the first alphabetical title. Investigate: intended selection logic and whether multiple featured titles should rotate.

**Steps to reproduce.** Reported: open Home; the hero shows "2 Fast 2 Furious".

**Expected behaviour.** An intentional, explainable featured-content strategy decided by the owner.

**Actual behaviour.** The same alphabetically early title (reported).

**Acceptance criteria.** A strategy decided and recorded in DECISIONS.md; implemented with tests; never alphabetical by default merely because it happens now.

**Work record**

- **Code note (written while building this tracker; not an investigation and not a confirmed root cause):** `Library.home()` features the first entry with a backdrop from Continue Watching, then Recently Added, then all movies, then shows. When none of the first two has a backdrop, the first movie of the `movies()` listing is featured, which would explain an alphabetical pick if that listing is ordered by title. To confirm in Phase 3.
- **Root cause:** not established.
- **Implementation / tests / verification:** none.
- **Evidence:** the tester's report; `library/src/commonMain/kotlin/dev/reflux/library/Home.kt`.
- **Outstanding work:** the owner's strategy decision; implementation; tests.

<a id="b-015"></a>
### B-015: Poster alignment/clipping vertically

- **Category:** Bug (UI) · **Priority:** Low · **Platforms:** W (desktop)
- **Components (likely):** `ui` (`Artwork` cards, rows)
- **Phase:** 2, step 18 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-009, B-042

**Original description.** Posters can appear vertically misaligned or clipped. *Expected:* poster cards and artwork should align consistently within their rows and containers.

**Findings document.** Some posters appear vertically offset or partially hidden. Evidence: screenshots supplied.

**Steps to reproduce.** Reported on the Windows desktop with screenshots (the owner's). Not yet reproduced by Reflux.

**Expected behaviour.** Consistent alignment within rows and containers.

**Actual behaviour.** Offset or partly hidden posters (reported).

**Acceptance criteria.** Cards in a row share top and bottom edges at every size (layout test); looked at on Windows.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Evidence:** the tester's screenshots (not in the repository).
- **Outstanding work:** everything.

<a id="b-016"></a>
### B-016: App opens last-used profile instead of profile selection

- **Category:** Product decision / UX · **Priority:** Medium · **Platforms:** W L P T (desktop and Android)
- **Components (likely):** `ui` (`ProfilesScreen`, start-up), `library` (profiles); docs/WATCH_STATE.md ("Profiles")
- **Phase:** **Unassigned** (owner decision) · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-017, B-018

**Original description.** The application opens directly into the last-used profile instead of presenting an obvious profile-selection experience. *Expected:* provide a startup profile chooser with the last-used profile prominently available, rather than making profile selection invisible or implicit. Phase allocation is unresolved; do not assign without recording the owner decision.

**Findings document.** After creating and using a Guest profile, restarting the application automatically opens the last-used profile. Expected: a startup profile chooser, with the most recently used profile first.

**Steps to reproduce.** Create and use a Guest profile; restart: the app opens Guest without asking.

**Expected behaviour.** A start-up chooser with the last-used profile first.

**Actual behaviour.** Opens the last-used profile. **This is the current design** (WATCH_STATE.md, "Profiles": a phone or a computer opens the profile it had; a TV asks), so changing it is a design decision as well as a fix.

**Acceptance criteria.** To be set with the owner's decision.

**Work record**

- **Investigation findings:** the behaviour matches the documented design (above). No other work.
- **Root cause / implementation / tests / verification:** none.
- **Evidence:** the tester's report; WATCH_STATE.md.
- **Outstanding work:** the owner's decision (phase and design); see [owner decisions](#unassigned-issues-and-unresolved-owner-decisions).

<a id="b-017"></a>
### B-017: No obvious logout/profile switch

- **Category:** Product decision / UX · **Priority:** Medium · **Platforms:** all
- **Components (likely):** `ui` (`ProfilesScreen`, navigation chrome)
- **Phase:** **Unassigned** (owner decision) · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-016, B-018

**Original description.** There is no obvious way to log out, switch profiles, or return to "Who's watching?". *Expected:* profile switching/logout must be discoverable and predictable without damaging profile data. Phase allocation is unresolved; do not assign without recording the owner decision.

**Findings document.** Profile switching is not discoverable. Expected: a clear "Who's watching?" or profile-switching entry point.

**Steps to reproduce.** Look for a way to switch profile or log out (reported as not obvious).

**Expected behaviour.** A discoverable, predictable switch that never damages profile data.

**Actual behaviour.** Not discoverable (reported).

**Acceptance criteria.** To be set with the owner's decision.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Evidence:** the tester's report only.
- **Outstanding work:** the owner's decision; see [owner decisions](#unassigned-issues-and-unresolved-owner-decisions).

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
- **Outstanding work:** the owner's decision on "Forgot PIN" (DECISIONS.md, 2026-11-18); a walk of the reported case; owner acceptance.

<a id="b-019"></a>
### B-019: Backspace in PIN input triggers Home

- **Category:** Bug (input/navigation) · **Priority:** Medium (findings document) · **Platforms:** controller and keyboard inputs (W L)
- **Components (likely):** `ui` (PIN pad, global shortcut handling in `RefluxApp` / `Controls`)
- **Phase:** 2, step 5 (with B-020, B-021) · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-020, B-021, B-023, B-025

**Original description.** Pressing Backspace while entering a PIN can trigger Home navigation instead of deleting the input character. *Expected:* input editing must take precedence while a text/PIN field is actively receiving input; Backspace must edit the field instead of propagating to global navigation.

**Findings document.** Backspace navigates Home instead of deleting a digit.

**Steps to reproduce.** Open a PIN prompt; type digits; press Backspace: Home opens (reported).

**Expected behaviour.** Backspace deletes a digit.

**Actual behaviour.** Home navigation (reported).

**Acceptance criteria.** Backspace edits every active PIN field and never navigates (UI test); one input/shortcut priority model (B-021).

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Evidence:** the tester's report only.
- **Outstanding work:** everything, with B-020 and B-021.

<a id="b-020"></a>
### B-020: Backspace in profile-name input triggers Home

- **Category:** Bug (input/navigation) · **Priority:** Medium (findings document) · **Platforms:** controller and keyboard inputs (W L)
- **Components (likely):** `ui` (profile name field, global shortcut handling)
- **Phase:** 2, step 5 (with B-019, B-021) · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-019, B-021, B-023

**Original description.** Backspace in profile-name input can trigger Home navigation. *Expected:* Backspace correctly edits text without triggering unrelated navigation.

**Findings document.** Backspace navigates Home instead of deleting a character.

**Steps to reproduce.** Create or rename a profile; type a name; press Backspace: Home opens (reported).

**Expected behaviour.** Backspace deletes a character.

**Actual behaviour.** Home navigation (reported).

**Acceptance criteria.** As B-019, for the profile name field.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Evidence:** the tester's report only.
- **Outstanding work:** everything, with B-019 and B-021.

<a id="b-021"></a>
### B-021: Backspace behaviour inconsistent across inputs

- **Category:** Bug (input/navigation) · **Priority:** Medium (findings document) · **Platforms:** cross-platform input (reported W L)
- **Components (likely):** `ui` (`TextInput`, global shortcut propagation)
- **Phase:** 2, step 5 (with B-019, B-020) · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-019, B-020, B-023, B-025

**Original description.** Backspace handling is inconsistent across text inputs and can leak into global shortcut handling. *Expected:* establish a consistent input event/shortcut priority model; active text inputs must handle editing keys before global navigation shortcuts.

**Findings document.** PIN/name fields are affected, while Search and some source fields behave differently. Investigate: global shortcut propagation and input focus.

**Steps to reproduce.** Compare Backspace in the PIN, profile-name, Search and source fields (reported: PIN and name navigate; Search and some source fields do not).

**Expected behaviour.** One priority model: an active input handles editing keys first.

**Actual behaviour.** Inconsistent; leaks into global shortcuts (reported).

**Acceptance criteria.** A documented priority model (INPUT_MODEL.md); a test per text input that editing keys never navigate.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Evidence:** the tester's report only.
- **Outstanding work:** everything.

<a id="b-022"></a>
### B-022: Windows startup slower than Android

- **Category:** Performance (investigation) · **Priority:** Low to Medium · **Platforms:** W
- **Components (likely):** `desktop:app` (start-up, JVM and Compose initialisation), `library` (database open), `playback:mpv` (engine initialisation)
- **Phase:** **Unassigned** (owner decision; measured under B-041) · **Status:** Investigating · **Last updated:** 2026-11-19
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
- **Outstanding work:** the owner's phase decision; a start-up breakdown (JVM, Compose, database, sources, engine); a larger library; then optimisation if justified (B-041's Phase 3 checkpoint).

<a id="b-023"></a>
### B-023: Reflux-designed controller keyboard

- **Category:** Feature / UX improvement · **Priority:** High (UX requirement) · **Platforms:** W, T; TV relevance to assess
- **Components (likely):** `ui` (new keyboard component, `TextInput`, `Focus`); `design`
- **Phase:** 2, step 6 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-004, B-011, B-019, B-020, B-021, B-025

**Original description.** Provide a Reflux-designed on-screen keyboard suitable for controller and touch input. *Expected capabilities:* letters; numbers; symbols; space; backspace; confirm/submit; clear and predictable focus behaviour; reliable insertion and dismissal; ability to reopen; suitable layouts for search, profile names, PINs, and source addresses; appropriate touch and controller operation. Investigate whether platform-native keyboards or custom keyboard layouts are more suitable per platform, but keep cross-platform UX consistent.

**Findings document.** Platforms: Windows desktop and Android tablets; TV applicability to be assessed. Native OS keyboard alone is not the desired experience. Reflux should provide an integrated, polished keyboard navigable with D-pad/joystick. Expected: letters, numbers, symbols, space, backspace, confirm, clear focus states, reliable insertion, dismissal/reopening without losing text and responsive layouts. Fields: search, profile creation, PIN entry and source configuration.

**Steps to reproduce.** Not applicable (a feature).

**Expected behaviour.** As the capabilities above.

**Actual behaviour.** No Reflux keyboard; the platform's keyboard (or none, on Windows with a controller).

**Acceptance criteria.** Each capability above works with controller and touch in each field type (search, profile name, PIN, source address); dismiss and reopen never lose text; a per-platform decision (native or custom) recorded in DECISIONS.md; UI tests; walks on Windows, tablet, TV.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Outstanding work:** everything.

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
- **Phase:** 2, step 1 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-007 to B-013, B-004, B-019 to B-021, B-023, B-039, B-042, F-002, F-P2

**Original description.** Controller support needs to be robust throughout the entire application, not just Home or playback. *Expected coverage:* D-pad navigation; analog sticks; face buttons; bumpers; triggers; Start/Select; platform remotes where relevant; focus and focus restoration; scroll navigation; dialogs and overlays; search and text entry; profile management; source management; playback controls; mini-player; settings; downloads and other screens. *Expected:* no trapped focus, inaccessible controls, unexpected focus loss, mouse-only actions, or navigation dead ends. Establish a shared controller/focus architecture and regression test strategy.

**Findings document.** Platforms: Windows, Android TV, Google TV, Fire TV and Android devices with controllers. Controller support should be consistent and complete, not merely basic button handling. Inputs: D-pad, analogue sticks, face buttons, bumpers, triggers, Start/Select and platform-specific remotes. Areas to validate: navigation, focus restoration, scrolling, dialogs, text entry, profile management, source setup, playback controls, mini-player, settings, downloads and all primary actions. Expected: predictable focus movement, no trapped focus, no accidental navigation, clear focus indication, coherent back/cancel behaviour and complete controller-only operation.

**Steps to reproduce.** Not a single defect: an audit of every screen with a controller only.

**Expected behaviour.** As above.

**Actual behaviour.** Not audited yet; the reported controller defects (B-007 to B-013, B-004) are its symptoms.

**Acceptance criteria.** A shared focus/input architecture recorded in DECISIONS.md and INPUT_MODEL.md; an audit table of every screen and dialog with controller-only results; a regression test strategy (UI tests per screen); walks per BETA-2.md's matrix (tablet, Android TV, Fire TV, Windows), each for focus movement, Back, sidebar entry and exit, focus restoration, mouse and controller together, hover and focus states, screen sizes, mapping, and unsupported controllers.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Outstanding work:** everything; the foundation for the rest of Phase 2.

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
- **Physical/device verification:** the 19 rounds ran on the owner's physical Windows machine.
- **Evidence:** VERIFICATION.md, "Phase 1", B-026 (with the tester procedure: `Get-CimInstance Win32_Process | ? ExecutablePath -like '*Reflux*' | select ProcessId,ParentProcessId,ThreadCount,CreationDate,CommandLine`, plus a support report, and whether something was playing).
- **Outstanding work:** closing during playback; a hung engine (the 5 s release patience); the tester's process listing if it recurs; an attempt on a second Windows machine.

<a id="b-027"></a>
### B-027: Separate Jellyfin libraries and aggregate views

- **Category:** Feature / product requirement · **Priority:** V1.1 product requirement in the findings document; moved into Beta 2 by the owner's phase plan (confirmation requested, below) · **Platforms:** all, with platform-specific navigation
- **Components (likely):** `sources:jellyfin` (user views, library IDs); `library` (library identity, aggregate queries); `ui` (`BrowseScreen`, `SourceLibraryScreen`, sidebar, mobile navigation)
- **Phase:** 3, step 1 (owner's phase plan, 2026-11-19; DECISIONS.md) · **Status:** Not Started · **Last updated:** 2026-11-19
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
- **Phase:** 3, step 6 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** none

**Original description.** Media atmosphere/ambience functionality appears to have little or no visible effect. *Investigate:* colour extraction, active state, animation, theme integration, rendering, cache updates, intensity, and whether changes are perceptible. *Expected:* if advertised, the feature should visibly work; effects must be intentional and tasteful, not so subtle that users cannot tell it is active; document limitations if any platform cannot support it.

**Findings document.** Tester did not notice meaningful changes when media atmosphere was expected to respond to selected media. Investigate: whether active; colour extraction and application; subtlety; updates on change; caching or rendering; platform/theme differences. Expected: if enabled and advertised, it should produce a perceptible, consistent result; otherwise communicate its purpose and limitations honestly.

**Steps to reproduce.** Reported: change the selected media with atmosphere enabled; no noticeable change.

**Expected behaviour.** A perceptible, consistent, tasteful effect, or honest communication of its limits.

**Actual behaviour.** Little or no visible effect (reported).

**Acceptance criteria.** Effect measurable (colour difference between two items' backgrounds above a set threshold) and visible on each platform; limitations documented.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Outstanding work:** everything.

<a id="b-029"></a>
### B-029: Right analogue stick Trickplay navigation

- **Category:** Feature requirement · **Priority:** Medium (UX requirement) · **Platforms:** controller-capable (W, TV, F, controller P/T)
- **Components (likely):** `ui` (`PlayerControls`, input handling), Trickplay from B-030
- **Phase:** 3, step 4 (after B-030) · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-030 (dependency), B-039 (mapping), F-003

**Original description.** Use the right analog stick for scene-based Trickplay navigation. *Expected:* the stick should navigate scene previews, not blindly seek by arbitrary time increments; integrate with preview selection and confirmation.

**Findings document.** Left joystick and D-pad both perform directional navigation. Right joystick currently does nothing. Desired: use the right analogue stick during playback for scene-based seeking through Trickplay previews.

**Steps to reproduce.** Move the right stick during playback: nothing happens.

**Expected behaviour.** Scene previews navigated; A confirms the seek; B cancels without seeking.

**Actual behaviour.** No action.

**Acceptance criteria.** BETA-2.md, "Acceptance criteria … Trickplay": right-stick navigation through preview timestamps; A confirms; B cancels without seeking; graceful when no previews exist.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Outstanding work:** everything; blocked in order (not formally Blocked) on B-030.

<a id="b-030"></a>
### B-030: Trickplay previews missing

- **Category:** Feature requirement (playback UX) · **Priority:** High · **Platforms:** W L TV F P T
- **Components (likely):** `sources:jellyfin`, `sources:plex` (preview data); `ui` (`PlayerControls` timeline); `library` (cache); `playback:mpv` / Media3 (local generation, if pursued)
- **Phase:** 3, step 3 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-029, B-041

**Original description.** Playback needs scene thumbnail previews, both for mouse timeline hover and controller-driven scene navigation. *Mouse expectations:* hover over timeline positions; show the scene preview image and timestamp; update as the pointer moves; clicking/selecting the position seeks appropriately. *Controller expectations:* right stick navigates scene previews; preview selection is visible and updated; A confirms/seeks; B cancels without accidentally seeking. *Investigate:* thumbnail generation, indexing, caching, player timeline integration, sync, performance, media source capabilities, and local/remote sources. Support Jellyfin and Plex where usable preview data exists. Investigate local-file support separately. Gracefully fall back if previews are unavailable; do not break normal seeking.

**Findings document.** Moving the mouse over the playback progress/seek bar does not show preview images of scenes at corresponding timestamps. Expected mouse behaviour: hover over the seek bar; a small preview window with the scene thumbnail and the timestamp, updating as the pointer moves; seek to the chosen timestamp when selected. Expected controller behaviour: during playback, the right analogue stick moves backward and forward through scene previews shown with their timestamp in a floating window; A/select seeks there; Back/cancel exits without seeking. Source support: work with local media and supported remote sources where preview generation is possible. Fallback: if a source or media item has no Trickplay thumbnails, provide a graceful fallback rather than pretending previews are available. Note: this is a playback capability, not merely a cosmetic tooltip.

**Steps to reproduce.** Hover the seek bar during playback: no preview.

**Expected behaviour.** As above.

**Actual behaviour.** No previews.

**Acceptance criteria.** BETA-2.md, "Acceptance criteria … Trickplay": mouse hover previews with timestamp; right-stick navigation; A confirms; B cancels; thumbnails cached, never the same tile downloaded twice; Jellyfin and Plex where their data exists; sources without Trickplay handled clearly; local files investigated separately, not promised.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Outstanding work:** everything.

<a id="b-031"></a>
### B-031: Linux libmpv fails under non-C numeric locales

- **Category:** Bug (Linux compatibility) · **Priority:** High; potentially release-blocking · **Platforms:** L
- **Components:** `playback:mpv` (`NumericLocale`, `createHandle`, `MpvPlayer`, `MpvProbe`, `MpvDecodeProbe`); `desktop:app` (`main`); `desktop:engine` (`DiagnosticLog`)
- **Phase:** 1, step 2 · **Status:** Fixed (verified in a VM and in containers; physical Linux pending) · **Last updated:** 2026-11-19
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
- **Physical/device verification:** none. No physical Linux desktop is available to the agent.
- **Regression testing:** full `./gradlew check` (Windows) passed; the Windows engine tests unchanged.
- **Evidence:** VERIFICATION.md, "Phase 1", B-031 and Workstream 12; "Website documentation portal…" (Arch, the wording correction).
- **Outstanding work:** a physical Linux desktop under `en_US.UTF-8` (as reported) and `de_DE.UTF-8`, local and Jellyfin playback, libmpv 0.42.0 (owner check). A new Ubuntu 26.04 VirtualBox VM was created on 2026-11-19 for desktop-session walks; a result from it is a VM result, not physical. Wayland only if playback still fails or stays black.

<a id="b-032"></a>
### B-032: Disconnected-source badge overlaps poster/watch indicator

- **Category:** Bug (UI) · **Priority:** Medium · **Platforms:** P T (potentially others)
- **Components (likely):** `ui` (`Artwork` card badges)
- **Phase:** 2, step 16 (with B-009) · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-009, B-005, B-033

**Original description.** The disconnected-source indicator text (e.g., "Not connected…") is truncated or overlaps the watched/checkmark indicator. *Expected:* a compact, responsive, accessible treatment; avoid collision between source connectivity state and watched state; ensure both remain understandable on narrow layouts.

**Findings document.** Evidence: multiple screenshots supplied on 18 November. The "Not connected…" source-status badge appears over poster artwork and is truncated with an ellipsis. The watched checkmark can overlap the same badge. Expected: status understandable without covering poster content; watched state distinguishable; indicators never collide; readable at phone and tablet sizes. Investigate: badge placement, width constraints, priority, icon positioning, responsive card layouts and whether the status should be a compact icon or another treatment. Design direction: avoid long, truncated toast-like labels over posters; consider a compact, consistent status treatment with accessible explanation.

**Steps to reproduce.** Disconnect a source on Android; look at its posters: a truncated "Not connected…" badge, overlapped by the watched checkmark (reported, with screenshots).

**Expected behaviour.** Compact status and watched state that never collide.

**Actual behaviour.** Truncated badge over artwork; checkmark overlap (reported).

**Acceptance criteria.** Badge and checkmark bounds never intersect at phone and tablet sizes (layout test); accessible description; device walk.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Evidence:** the tester's screenshots (not in the repository).
- **Outstanding work:** everything.

<a id="b-033"></a>
### B-033: Orphan favourites after source removal

- **Category:** Bug (data integrity/UX) · **Priority:** Medium · **Platforms:** reported Android; the library rules are shared
- **Components:** `library` (`favorites()`, availability); `ui` (`DetailsScreen` `Gone` page, Home shelf)
- **Phase:** 1, step 6 · **Status:** Fixed · **Last updated:** 2026-11-19
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
- **Tests added or modified:** `UnavailableFavoritesTest` (2, new); `ScreensTest` (1 new).
- **Automated verification:** `:library:jvmTest --tests UnavailableFavoritesTest` 2/2; `:ui:jvmTest --tests ScreensTest` 23/23. Windows.
- **Physical/device verification:** none.
- **Regression testing:** full `./gradlew check` passed.
- **Evidence:** VERIFICATION.md, "Phase 1", B-033.
- **Outstanding work:** an Android device walk of the reported case; owner acceptance.

<a id="b-034"></a>
### B-034: Mobile search placeholder is too long and visually heavy

- **Category:** UX (polish) · **Priority:** Low to Medium · **Platforms:** P T
- **Components (likely):** `ui` (Search field)
- **Phase:** 2, step 12 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-035, B-036, B-037

**Original description.** The search placeholder is too long, large, and bold. *Expected:* a shorter, understated placeholder with lighter visual weight and colour; entered text must be visually distinct; prefer a short placeholder rather than marquee behaviour.

**Findings document.** Placeholder "Search titles, people, genres, or…" is too long, too large, bold and prominent. Desired: shorten it (preferred over a marquee); reduce size and weight; a faded colour; entered text visually distinct from the placeholder.

**Steps to reproduce.** Open Search on a phone.

**Expected behaviour / acceptance criteria.** A short placeholder that fits without truncation at phone width; lighter weight and colour than entered text; screenshot comparison on phone and tablet.

**Actual behaviour.** As reported.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Outstanding work:** everything.

<a id="b-035"></a>
### B-035: Mobile search chips are oversized and contain unnecessary "Try" label

- **Category:** UX (polish) · **Priority:** Low to Medium · **Platforms:** P T
- **Components (likely):** `ui` (Search suggestion chips)
- **Phase:** 2, step 13 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-010, B-034

**Original description.** Search chips are too large, and "Try" is unnecessary. *Expected:* remove "Try", reduce chip dimensions/padding, and use an edge-to-edge scrollable row on mobile while retaining accessibility and touch usability.

**Findings document.** Suggested query chips are large, and the row begins with an unnecessary "Try" label. Desired: remove "Try"; smaller chips; on mobile, an edge-to-edge chip row; comfortable touch targets and horizontal scrolling kept.

**Steps to reproduce.** Open Search on a phone.

**Expected behaviour / acceptance criteria.** No "Try"; smaller chips with touch targets at least 48 dp; an edge-to-edge scrolling row on phones; UI test and screenshots.

**Actual behaviour.** As reported.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Outstanding work:** everything.

<a id="b-036"></a>
### B-036: Mobile search/IME lifecycle breaks navigation

- **Category:** Bug (mobile UI/navigation) · **Priority:** Medium · **Platforms:** P T
- **Components (likely):** `ui` (Search, navigation scaffold, `CompactNavigation`); `android:app` (window insets, `adjustResize`)
- **Phase:** 2, step 11 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-034, B-035, B-037, B-025

**Original description.** Search interaction can cause vertical shifts, missing back controls, and disappearing bottom navigation after the keyboard is dismissed. *Expected:* stable search layout; predictable keyboard resize/insets; clear back-button behaviour; correct distinction between dismissing the IME and leaving Search; bottom navigation remains correct after keyboard dismissal; query is preserved where appropriate.

**Findings document.** Entering Search shifts the UI as the keyboard appears; no obvious in-app back button; after dismissing the keyboard the bottom navigation bar disappears; recovery needs the Android back gesture. Expected: Search a stable destination; predictable resize; navigation available (or an intentional replacement) with the keyboard hidden; Back tells dismissing the keyboard from leaving Search; no layout jumps or lost query. Investigate: Compose WindowInsets, IME animation, `adjustResize`, navigation scaffold visibility and focus state.

**Steps to reproduce.** On a phone: open Search (the UI shifts); dismiss the keyboard (the bottom bar disappears); only the system back gesture recovers.

**Expected behaviour.** As above.

**Actual behaviour.** As reported.

**Acceptance criteria.** Each expectation above checked on the Phone 2a and Pad 7 (physical), with the query kept; instrumented or UI tests where feasible.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Outstanding work:** everything.

<a id="b-037"></a>
### B-037: Search has no designed empty state

- **Category:** UX · **Priority:** Low to Medium · **Platforms:** all, especially mobile
- **Components (likely):** `ui` (Search, and the Movies/Shows/filter empty states)
- **Phase:** 2, step 14 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-034 to B-036, B-038

**Original description.** Search returns a plain text empty state without a designed experience. *Expected:* a deliberate, lightweight empty state with appropriate messaging and a useful next action without cluttering the interface.

**Findings document.** No-results state is plain text ("Nothing in your library matches 'abc'.") with a large empty area. Expected: a deliberate empty state: a visual, a concise explanation, a useful next action; consistent across Search, Movies, Shows, filters, and source states; lightweight.

**Steps to reproduce.** Search for "abc" in a library with no match.

**Expected behaviour / acceptance criteria.** A designed empty state with a next action, consistent across Search, Movies, Shows, filters and source states; UI test.

**Actual behaviour.** Plain text, large empty area.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Outstanding work:** everything.

<a id="b-038"></a>
### B-038: Movies and Shows sorting/filtering

- **Category:** Feature / product requirement · **Priority:** Medium · **Platforms:** all
- **Components (likely):** `library` (queries, indexes); `ui` (`BrowseScreen`, settings persistence)
- **Phase:** 3, step 2 · **Status:** Not Started · **Last updated:** 2026-11-19
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
- **Phase:** 2, step 8 (with B-012) · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-012, F-005, F-P2 (Y toggles the sidebar), B-029 (right stick), B-025

**Original description.** Review the current mapping. A selects, B backs, Y opens Sources, X is unused, and LB/RB/LT/RT are largely unused. *Expected investigation:* evaluate Y opening the sidebar from anywhere in the application; find a useful, distinct action for X; consider LB/RB for section/page navigation; consider LT/RT for contextual actions or playback; avoid conflicts with player controls, dialogs, text input, and platform conventions; provide controller hints; consider custom mapping as a future feature. Do not implement arbitrary mapping changes without evaluating conflicts and documenting the decision.

**Findings document.** Current: A selects; B goes back; Y opens Sources; X does nothing; LB/RB and LT/RT have no clearly useful behaviour. Proposed direction: Y opens the sidebar immediately from anywhere; X a distinct useful action; LB/RB section or page navigation; LT/RT playback or consistent contextual actions. Requirements: no conflicts with platform conventions, remotes, or player shortcuts; discoverable hints; consider configurable mappings. Status (original): proposed interaction design, not an approved mapping specification.

**Steps to reproduce.** Not applicable (a design review).

**Expected behaviour.** A conflict-checked, documented mapping with hints.

**Actual behaviour.** As "Current" above.

**Acceptance criteria.** A conflict analysis (player, dialogs, text input, remotes, platform conventions) and the chosen mapping recorded in DECISIONS.md and INPUT_MODEL.md; hints; tests per mapped action; walks on Windows, TV and Fire TV.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Outstanding work:** everything, with B-012.

<a id="b-040"></a>
### B-040: Home recommendations/discovery lack variety

- **Category:** Product improvement · **Priority:** Low to Medium · **Platforms:** all (Home)
- **Components:** `library` (`Home.kt`, `Library.home()` rows, smart search); `ui` (`HomeScreen`)
- **Phase:** 3, step 7 · **Status:** Not Started · **Last updated:** 2026-11-19
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
- **Phase:** **one issue, two checkpoints**: Phase 1 step 11 (baseline) and Phase 3 step 9 (final optimisation and comparison) · **Status:** Investigating (baseline partial) · **Last updated:** 2026-11-19
- **Related:** B-006, B-022, B-026 (shutdown), B-030, B-038

**Original description.** Perform a comprehensive cross-platform performance audit and establish measurable baselines and optimization criteria. *Platforms:* Android phone, Android tablet, Android TV, Google TV, Fire TV, Windows, Linux. *Audit areas:* cold startup; warm startup; first usable UI; content loading; library scanning; search; artwork and cache performance; memory use; scrolling/frame pacing; UI transitions; playback startup and transitions; CPU/GPU use; battery where applicable; shutdown; long sessions. *Test conditions:* release builds; physical devices where available; desktop operating systems; different densities and window sizes; realistic/large libraries; connected and disconnected sources. *Expected:* document measured baselines; identify bottlenecks supported by measurements; prioritize optimizations based on evidence; establish sensible regression thresholds. *Phase allocation:* baseline measurements belong in Phase 1; final optimization and repeat measurements belong in Phase 3. This is a single issue with separate checkpoints, not two duplicated issues.

**Findings document.** Areas to measure: cold and warm startup; first usable screen; first library content; large-library scan and metadata; search latency; artwork loading, caching, memory; scrolling and frame pacing; navigation transitions; playback startup, buffering, seeking, recovery; CPU, memory, GPU, battery; background work and shutdown; long-session stability and memory growth. Test matrix: physical devices, representative desktop hardware, densities and resolutions, large libraries, connected and disconnected sources. Expected output: measured baseline, bottleneck evidence, platform findings, regression thresholds. Do not optimise on assumptions or one device. Compare release builds, not only debug builds.

**Checkpoint 1: baseline (Phase 1). Status: partial.**

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

Not measured yet: an Android release build of the Phase 1 code (none can be installed beside the store build without replacing it), Linux on hardware, scrolling frame times, Android memory and CPU, GPU, battery, large-library scans on devices, artwork, playback start and transitions, long sessions; regression thresholds not set. Macrobenchmark recommended for M12, at its cost (a module, an AndroidX dependency, a benchmark build type).

**Checkpoint 2: final optimisation and comparison (Phase 3). Status: Not Started.**

**Acceptance criteria.** Every audit area measured on the matrix's platforms with method, device, OS, build and commit, sources, cold/warm, run count, median and P95, and the physical/emulator/VM distinction; bottlenecks evidenced; optimisations prioritised by evidence; regression thresholds set; the Phase 3 run compared with this baseline.

**Work record**

- **Investigation findings:** as the table. The desktop is a launcher plus a JVM child (B-026).
- **Root cause:** not applicable yet (no bottleneck established except local search, B-006).
- **Implementation:** measurement procedures only.
- **Tests added or modified:** `SearchPerformanceTest` (B-006).
- **Evidence:** VERIFICATION.md, "Phase 1", Baselines (WS11).
- **Outstanding work:** the unmeasured areas above; thresholds; then Phase 3's checkpoint.

<a id="b-042"></a>
### B-042: Controller focus and mouse hover conflict

- **Category:** Bug (UI/controller interaction) · **Priority:** To be determined (master tracker prompt); "Major" in the master prompt that first reported it (see owner decisions) · **Platforms:** controller-capable desktop layouts (W, and L)
- **Components (likely):** `ui` (`Artwork` card focus/hover state, `Focus`)
- **Phase:** **Pending owner approval** (recommended: Phase 2, beside B-025) · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-025, B-009, B-015

**Original description.** When controller focus expands a media card, moving the mouse over a different card can cause hover behaviour to interact incorrectly with the previously focused card, producing flicker or confusing expansion behaviour. *Expected:* controller focus and mouse hover must coexist predictably; moving the mouse should not cause the previous controller-focused card to incorrectly behave as hovered; focus and hover state must remain separate and correctly managed; avoid globally disabling hover as a simplistic fix. *Recommended phase:* Phase 2, alongside B-025 shared focus architecture. This is a recommendation, not yet an owner-confirmed allocation; record it as pending approval.

**Master prompt (2026-11-19), as first registered in BETA-2.md.** Severity: Major. When browsing movie cards with a controller, the focused card expands into its highlighted state. If the pointer rests near the edge of another card when controller focus moves, pointer hover can activate the previously focused card, competing with controller focus; the visual state changes repeatedly and flickers. Expected: controller focus and pointer hover never fight over a card's state; moving focus never makes the previous card flicker; mouse hover works normally when the mouse is in use; switching between mouse and controller is predictable; controller navigation and focus accessibility preserved. Test controller-only use and simultaneous mouse and controller input. Investigate the interaction architecture; do not globally disable hover.

**Steps to reproduce.** On Windows: browse cards with a controller with the pointer resting near the edge of another card; move focus: the previous card flickers between states (reported).

**Expected behaviour.** As above.

**Actual behaviour.** Flicker and competing expansion (reported).

**Acceptance criteria.** Focus and hover held as separate states; no flicker in a UI test that moves focus with the pointer resting on another card; controller-only and simultaneous input walks on Windows; hover not disabled globally.

**Work record**

- **Investigation findings / root cause / implementation / tests / verification:** none yet.
- **Outstanding work:** the owner's phase decision; then everything.

## Issues: original feature findings (F-001 to F-005)

Feature proposals, kept apart from bugs. The confirmed defaults are acceptance criteria, not preferences (BETA-2.md, "Acceptance criteria the owner set"; CLAUDE.md).

<a id="f-001"></a>
### F-001: Interface haptics

- **Category:** Feature · **Platforms:** Android phone and tablet with haptic capability
- **Components (likely):** a new central haptics service (`ui` / `android:app`), Settings
- **Phase:** 2, step 10 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Confirmed default:** **OFF**: the user must explicitly enable it in Settings.
- **Related:** F-002, F-003 (independent of it)

**Original description.** Add optional interface haptic feedback for actions such as focus, selection, navigation, and errors. *Requirements:* subtle, purposeful feedback; no repeated vibration tied to animation frames or continuous rendering; capability-aware behaviour; centralized haptics service/gateway; every vibration request must check the user's preference centrally to prevent feature code from bypassing the preference; no device vibration whatsoever before opt-in.

**Findings document.** Optional interface haptics (research proposal, not approved): feedback for focus movement, selection, confirmation, dialogs, navigation boundaries, errors; subtle and restrained; never on every animation frame; platform APIs with capability-aware fallbacks.

**Acceptance criteria.** Off by default; explicit opt-in in Settings; absolutely no device vibration before opt-in; one central service enforces the preference on every request and no feature can bypass it (a test that fails if code reaches the platform vibrator outside the service); capability-aware; subtle; never repeated per animation frame; controller input never triggers device vibration; settings persist and behave the same on every platform; unsupported hardware disables the option or explains why. Physical phone and tablet checks.

**Work record:** none yet. **Outstanding work:** everything.

<a id="f-002"></a>
### F-002: Controller rumble

- **Category:** Feature · **Platforms:** supported game controllers (W, TV, F, controller P/T)
- **Components (likely):** controller input (`desktop:app` XInput, `android:app` `InputDevice` vibrator), a rumble service, Settings
- **Phase:** 2, step 9 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Confirmed default:** **ON**, with a user-disableable setting. Unsupported controllers must not produce errors or unusable behaviour.
- **Related:** F-001, F-003, B-025

**Original description.** Add controller rumble feedback for suitable events. *Requirements:* detect controller rumble capability; gracefully handle unsupported devices; stop rumble immediately when controller disconnects; use appropriate intensity and duration; avoid continuous or annoying vibration; user can disable rumble; persist preference.

**Findings document.** Optional controller rumble (research proposal, not approved): compatible controllers' motors for interface confirmations and selected playback events; detect support and handle its absence gracefully.

**Acceptance criteria.** On by default; disableable globally; only on compatible controllers; capability detected; unsupported devices handled without error; stops at once on disconnect; no excessive intensity or duration; preference persisted. Physical controller checks on Windows, tablet, TV and Fire TV.

**Work record:** none yet. **Outstanding work:** everything.

<a id="f-003"></a>
### F-003: Video-synchronised haptics

- **Category:** Feature (experimental) · **Platforms:** supported controllers/devices
- **Components (likely):** playback audio analysis, the rumble/haptics services, player settings
- **Phase:** 3, step 5 · **Status:** Not Started · **Last updated:** 2026-11-19
- **Confirmed defaults:** **OFF** by default; controller video haptics **OFF** by default; explicit opt-in required; must remain experimental and must not be enabled automatically.
- **Related:** F-001, F-002, B-029, B-030

**Original description.** Explore haptics synchronized with video content using available signal information, including audio energy, transients, frequency bands, dedicated tracks, or platform APIs. *Requirements:* volume alone must not drive constant rumble; video haptics must be independent from interface haptics; provide separate controls; intensity ceiling; rate limits/cooldowns; sensible envelopes; pause/stop/seek/error/disconnect handling; avoid unexpectedly activating when switching playback device, profile, or media; test for performance and playback impact.

**Findings document.** Optional video-synchronised haptics (experimental; not a V1 commitment): audio-derived haptics (low-frequency energy, transients), dedicated haptic tracks, or platform audio-coupled haptics; explicit opt-in, intensity limits, cooldowns, pause and mute behaviour, synchronisation, hardware detection; volume alone must not determine intensity; research Android HapticGenerator, controller vibration APIs, media haptic formats.

**Acceptance criteria.** Off by default and independent of interface haptics (enabling one never enables the other); controller video haptics off until enabled there; explicit opt-in; intensity ceiling; rate limits and cooldowns; short envelopes; pause, stop, seek, error and disconnect handled; never continuous rumble from audio volume alone; no activation on switching media, profile or output device; playback performance measured with it on.

**Work record:** none yet. **Outstanding work:** everything.

<a id="f-004"></a>
### F-004: Privacy-respecting analytics

- **Category:** Feature / architecture / privacy review · **Platforms:** all
- **Components:** `core` (`dev.reflux.core.telemetry`: `TelemetryEvent`, `TelemetryContext`, `TelemetryConsent`, `ConsentStore`, `TelemetryTransport`, `TelemetryRecorder`); docs/PRIVACY.md
- **Phase:** **two checkpoints**: Phase 1 step 10 (architecture, schema, privacy, consent) and Phase 3 step 10 (final validation) · **Status:** In Progress (Phase 1 foundation: implemented, tests passing; nothing transmitted) · **Last updated:** 2026-11-19
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

**Checkpoint 2 (Phase 3): Not Started.**

**Outstanding work.** The owner's provider approval and data contract; retention; the Settings switch (only with a transport); PRIVACY.md, the website's privacy page, Play's Data safety and Amazon's questionnaire updated before anything is sent; network-level validation in Phase 3.

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
- **F-P2 Sidebar focus toggle with Y.** With a controller, Y moves focus to the sidebar and Y again returns it to the exact previous item, selection and scroll kept, repeated toggles not resetting state, no shortcut conflicts, an unavailable previous item handled; developed and tested with F-P1, and to be reconciled with B-039 (whose proposed Y is the same). **Phase:** owner to confirm (with F-P1). **Status:** Not Started.

## Unassigned issues and unresolved owner decisions

Nothing here is assigned to a phase until the owner decides; each decision, when made, goes into DECISIONS.md and this tracker's change history.

| Issue | Decision required | Recommendation | Reasoning | Options | Current allocation / status |
| --- | --- | --- | --- | --- | --- |
| [B-016](#b-016) | Whether to change start-up to a profile chooser, and when | Phase 2, with B-017 | It is interaction work next to B-019/B-020's profile inputs, and it changes documented design (WATCH_STATE.md), which is the owner's call | Phase 2 · Phase 3 · V1.1 · keep the current design (and close as by design) | Unassigned · Not Started |
| [B-017](#b-017) | Whether and when to add a discoverable profile switch / "Who's watching?" | Phase 2, with B-016 | One entry point serves both | Phase 2 · Phase 3 · V1.1 | Unassigned · Not Started |
| [B-022](#b-022) | Phase for any Windows start-up optimisation | Measure under B-041 (Phase 1 baseline taken), optimise in Phase 3 only if the breakdown justifies it | Start-up is one of B-041's audit areas; no bottleneck is established yet | Phase 3 under B-041 · its own Phase 2/3 item · V1.1 | Unassigned · Investigating |
| [B-042](#b-042) | Approve Phase 2 allocation | Phase 2, with B-025 | It is a focus/hover architecture question that B-025's shared model must answer | Phase 2 · Phase 3 · later | **Pending approval** · Not Started. Its severity also differs between sources ("Major" in the first master prompt; "To be determined" in the tracker prompt): owner to set |
| [F-005](#f-005) | Whether to schedule controller remapping | V1.1 | B-039 should first settle the default mapping; customisation depends on it | Phase 2 (after B-039) · Phase 3 · V1.1 · not planned | Unassigned · Not Started |
| [B-027](#b-027) | Confirm the scope change from "V1.1 candidate" (findings document) to Beta 2 Phase 3 | Confirm Phase 3 as the owner's plan of 2026-11-19 states | The owner's phase plan listed "Separate Jellyfin libraries: Included" and DECISIONS.md records it; the tracker prompt asks for confirmation before implementation | Confirm Phase 3 · return to V1.1 | Phase 3 (owner's plan) · Not Started; **not to be implemented until confirmed** |
| [F-P1, F-P2](#f-p1-and-f-p2) | Number them and place them | Number F-006, F-007; Phase 3 with B-027 (sidebar entries for libraries) or Phase 2 with B-039 for F-P2 | The sidebar's library entries (B-027) and Y's role (B-039) both touch them | Phase 2 · Phase 3 · V1.1 | Unallocated · Not Started |
| [B-018](#b-018) | "Forgot PIN" still erases a protected profile's private data without its PIN | Keep (recovery), and say so in the PIN prompt | Removing it makes a forgotten PIN unrecoverable; keeping it means a PIN protects privacy, not against deletion at the device | Keep · require something else to reset · remove | Phase 1 · Fixed; decision open (DECISIONS.md, 2026-11-18) |
| [F-004](#f-004) | Statistics provider and data contract | Evaluate Aptabase (EU or self-hosted) behind Reflux's transport | DECISIONS.md, 2026-11-18, provider evaluation | Aptabase · own endpoint · none | Phase 1 foundation done; nothing sent |
| [B-006](#b-006) | Which provider the original report meant | — | Needed to measure the right path | Ask the tester | Investigating |

**Differences between sources, recorded and not resolved by the tracker:** B-042's severity (above); B-042's phase (BETA-2.md listed it in Phase 2; the tracker prompt says pending approval, so BETA-2.md now shows it as pending); B-027's scope (above); the F-005 numbering (above); titles differ slightly between the tracker prompt and the findings document for most issues (both kept in each entry; meanings identical); the tracker prompt gives no priority for B-019 to B-021 (the findings document's "Medium" is kept).

## Newly discovered issues (B-043 onward)

Found during Beta 2 work, not in the original findings. Each was allocated by existing scope; none is a scope change.

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
- **Phase:** none needed (test reliability; carried with M12's intermittents) · **Status:** Investigating · **Last updated:** 2026-11-19
- **Related:** the known intermittents `PointerTest.controlsLeaveWhilePlayingAndReturnWithThePointer` and `AboutWindowTest` (CLAUDE.md, "Known intermittents"); B-045

**Description.** `ComposeTimeoutException` in `await("the controls to leave") { !exists("Timeline") }` after `mainClock.advanceTimeBy(Motion.CONTROLS_AUTO_HIDE_MS * 3)`: the player controls had not left within the wait while every module was compiling and testing at once.

**Work record**

- **Investigation findings:** the test file is unchanged since `540147c`; none of the four commits touches it or the player controls. `:ui:jvmTest --tests "*AccessibilityTest*" --rerun` alone: 3/3 passed. Suspected the same timing family as `PointerTest` (a wall-clock wait under load); not confirmed.
- **Root cause:** not established.
- **Implementation:** none. Not to be "fixed" by weakening the test.
- **Outstanding work:** confirm whether the wait depends on wall-clock time under load; fix the test's timing if so, keeping what it asserts.

<a id="b-045"></a>
### B-045: `AboutWindowTest` fails on unmodified HEAD (a focus stop added after the test was written)

- **Category:** Bug (test defect) · **Priority:** Low · **Platforms:** Windows (real-window tests, `-Preflux.windowTests=true`)
- **Components:** `desktop:app` tests (`AboutWindowTest`)
- **How found:** 2026-11-19, during the GPL transition's About-page checks.
- **Phase:** none needed (test defect; carried with M12's intermittents) · **Status:** Not Started · **Last updated:** 2026-11-19
- **Related:** B-044

**Description.** On unmodified HEAD the test failed 2 of 2 runs at the arrow to Reflux's terms row, which lands on Privacy, a stop added after the test was written; on the GPL branch 1 of 2 runs passed the whole flow and 1 failed at the same step.

**Work record:** recorded in VERIFICATION.md, "Open-source transition"; not fixed. **Outstanding work:** update the test's expected focus order to the current About page.

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
| B-001 | ✓ (instrumented) | ✓ Google TV | ✓ Pad 7, Phone 2a, Fire TV (server source ✗) | — | ✓ | Pending |
| B-003 | ✓ 15 new | — | ✗ live two-address server, device walk | — | ✓ | Pending |
| B-005 | ✓ layout, 5 sizes | ✗ | ✗ phone, tablet, TV walks | — | ✓ | Pending |
| B-006 | ✓ fixture (JVM) | — | ✗ Android fixture | — | ✓ | Pending |
| B-018 | ✓ 9 new | — | ✗ walk of the reported case | ✓ Windows JVM | ✓ | Pending |
| B-022 | measurement | — | ✓ Windows timing (one machine) | — | — | Pending |
| B-024 | ✓ | — | ✗ Windows walk | ✓ Windows JVM | ✓ | Pending |
| B-026 | measurement (19 closes) | — | ✓ owner's Windows machine (not reproduced) | — | — | Pending |
| B-031 | ✓ | — | ✗ physical Linux desktop | ✓ WSL2 VM; Ubuntu 22.04/24.04, Debian 12, Fedora 41, Arch containers | ✓ | Pending |
| B-033 | ✓ | — | ✗ Android walk | ✓ Windows JVM | ✓ | Pending |
| B-041 | measurement | ✓ Google TV cold start | ◐ Windows; Phone 2a, Pad 7, Fire TV cold start | ✗ Linux | — | Pending |
| B-043 | ✓ | — | — | ◐ base packages in containers; Mint not run | ✓ | Pending |
| F-004 | ✓ 6 | — | — (nothing sent) | — | ✓ | Pending |

Machines (CLAUDE.md, "Testing environment"): Windows 11 development machine (physical); Xiaomi Pad 7, Nothing Phone (2a), Fire TV Stick 4K Plus (physical); Google TV emulator; WSL2 Ubuntu 24.04 (VM); Docker containers; a new **Ubuntu 26.04 VirtualBox VM** (`Reflux-QA-Ubuntu-26.04`, created 2026-11-19: 4 vCPU, 8 GB, 60 GB, VMSVGA with 3D, 128 MB video memory; Ubuntu not yet installed). Full runs: VERIFICATION.md, "Phase 1", "Open-source transition", "Website documentation portal…", and "Beta bug tracker and register reconciliation".

## Change history

| Date | Change | By |
| --- | --- | --- |
| 2026-11-19 | Tracker created from the owner's master tracker prompt, BETA-2.md's register, PHASE-1-PLAN.md, VERIFICATION.md, DECISIONS.md and the code at `0a0396f`. All of B-001 to B-042 and F-001 to F-005 entered once, with both source texts. Statuses moved to the tracker's eight words. **B-001** recorded as Fixed (BETA-2.md had "Verified on physical device"; its "different source types" criterion is not met). **B-042** shown as pending owner approval (BETA-2.md had listed it in Phase 2). **B-026** Not Reproduced. New issues **B-043** (Linux hint), **B-044** (`AccessibilityTest` timeout), **B-045** (`AboutWindowTest`). F-P1 and F-P2 kept. The per-issue text in BETA-2.md moved here; BETA-2.md keeps phases, order, exit criteria and the gate. | Claude (agent), for the owner's review |
| 2026-11-19 | Consistency check (throwaway script): 51 entries, one anchor each, no duplicates; every link and anchor in the tracker, BETA-2.md, CLAUDE.md, ROADMAP.md, DECISIONS.md, PHASE-1-PLAN.md and VERIFICATION.md resolves; every entry's status is one of the eight; the phase overview matches the entries; counts 35 Not Started, 3 Investigating, 1 In Progress, 7 Fixed, 1 Not Reproduced (47). 0 problems. | Claude (agent) |
