# Beta 2: the bug and feature register, the three phases, and the gate

The authoritative record of Beta 2's **scope, phase order, work order, dependencies, exit criteria, and gate** (DECISIONS.md, 2026-11-19, "Beta 2 is cut only after Phases 2 and 3"). **Each issue's detail lives in [BETA-BUG-TRACKER.md](BETA-BUG-TRACKER.md)**: the original report, reproduction, status, investigation, implementation, tests, verification by kind, evidence, and what is left. ROADMAP.md says where the work stands and links here; VERIFICATION.md holds the runs. Nothing is repeated between them.

**Beta 2 is not cut, built, or versioned** until the gate at the end of this file holds and the owner approves it. `0.9.0-beta.1` (`versionCode` 90031, tag `v0.9.0-beta.1`) stays in testing, untouched.

## Sources

- **The owner's findings document**, "Reflux Beta Testing Findings" (build 0.9.0-beta.1, last updated 18 November 2026), provided on 2026-11-19: the original text of B-001 to B-041 and F-001 to F-005, kept word for word in the tracker's entries.
- **The owner's master prompt** of 2026-11-19: B-042 and two sidebar features (F-P1 and F-P2), and the Phase 1 brief before it (2026-11-18).
- **The owner's phase plan** of 2026-11-19 ("Updated Beta 2 phase allocation"): the three phases, their order, and the acceptance requirements for haptics, analytics, sorting and filtering, and Trickplay.
- **The owner's master tracker prompt** of 2026-11-19 ("Complete Beta Bug Tracker & Beta 2 Phase 1–3 Roadmap"): the authoritative text of every finding (B-001 to B-042, F-001 to F-005), each phase's work order and exit criteria (below), and the list of unresolved allocations. It created the tracker.
- No other source holds these IDs: the repository never did, and no earlier session's notes on this machine mention them (searched 2026-11-19). Nothing is reconstructed from memory.

## The three phases (the owner's order)

| Phase | Theme | Order |
| --- | --- | --- |
| **1** | Reliability, security, data integrity; telemetry architecture and consent groundwork; performance baselines | B-018, B-031, B-001, B-026, B-003, B-033, B-024, B-005, B-006, F-004 (analytics architecture, event schema, privacy review, consent), B-041 (baselines) |
| **2** | Controller, focus, interaction, input, and haptics | B-025, B-008, B-007, B-004, B-019 + B-020 + B-021, B-023, B-011, B-039 + B-012, F-002, F-001, B-036, B-034, B-035, B-037, B-010, B-009 + B-032, B-002, B-015, B-013 |
| **3** | Media experience, discovery, Trickplay, and final performance | B-027, B-038, B-030, B-029, F-003, B-028, B-040, B-014, B-041 (final optimisation and comparison), F-004 (final validation) |

Phases are implemented, tested, documented, and reviewed **in order**; none is skipped, and no release build or Beta 2 release is started while any phase is incomplete or awaiting acceptance. One follow-up per phase at most, for a genuine blocker or an essential correction (AGENTS.md, milestone execution rules). Each issue's status: the tracker's [phase overview](BETA-BUG-TRACKER.md#phase-overview).

### Phase 1: reliability, security, data integrity, foundational investigations

**Work order.**
1. **B-018** profile deletion authorization: critical; authorization enforced in the deleting operation, not only the UI; regression coverage for Guest deleting Main and other bypass routes.
2. **B-031** Linux libmpv locale/init: the C numeric locale before libmpv; system language and formats preserved; verified in the Linux environment and documented; physical Linux verification remains necessary.
3. **B-001** playback lifecycle/frozen frame: media switching, surfaces, lifecycle races, state reset; reproduced and fixed, with regression tests.
4. **B-026** Windows process shutdown: investigated; not assumed resolved because it does not reproduce on the developer PC.
5. **B-003** multiple Jellyfin endpoints: one logical server, several endpoints; LAN preference, fallback and persistence validated.
6. **B-033** orphaned favourites: removed sources and stale favourites handled without deleting favourites on temporary connectivity loss.
7. **B-024** resolution/HDR metadata: resolution, HDR, codec and audio classification and display.
8. **B-005** scan status UI: overlap and adaptive placement.
9. **B-006** metadata/search latency: measured, bottlenecks identified, justified fixes; not only perceived loading.
10. **F-004** analytics architecture, event schema, privacy, consent: the privacy foundation, consent handling, schema, controls, validation; no transmission before explicit opt-in; auditable.
11. **B-041** performance baseline: cross-platform baseline with methodology, environment, measurements and bottlenecks; Phase 3 compares against it.

**Exit criteria.**
- Every Phase 1 issue has its implementation or investigation results documented.
- B-018 is addressed and regression-tested.
- Playback lifecycle and locale concerns have appropriate tests.
- Data integrity and connectivity issues are addressed.
- Every fix has corresponding tests where feasible.
- Unreproduced issues stay transparently marked, never falsely closed.
- Performance baseline measurements are recorded.
- Analytics privacy and consent groundwork is implemented and verified.
- The Phase 1 entries of the tracker are current.
- Relevant documentation and decision records are updated.
- The Phase 1 review is presented to the owner for acceptance before Phase 2 is considered complete.

*State (2026-11-19):* **accepted by the owner**, with the owner's own checks named (planning/PHASE-1-PLAN.md, "Stage G").

### Phase 2: controller, focus, interaction, input, and haptics

**Work order.**
1. **B-025** shared whole-app controller/focus architecture: audit every screen and component, focus management and restoration, navigation, controller input routing, mouse interactions, platform remotes; no isolated focus patches where a shared architecture is needed.
2. **B-008** focus recovery after Home: navigation and focus/scroll restoration.
3. **B-007** See All and row navigation: D-pad traversal, row boundaries, section actions, skipping.
4. **B-004** source configuration input: reliable with controller and text entry.
5. **B-019, B-020, B-021** Backspace and shortcut handling: global shortcuts never leak into active input; one priority between editing and navigation.
6. **B-023** Reflux on-screen keyboard: reliable, cross-platform, for controller and touch contexts, with layouts per field.
7. **B-011** controller-only search: fully usable without mouse or touch.
8. **B-039 and B-012** X/Y/LB/RB/LT/RT mapping: evaluated after conflict analysis, implemented coherently, documented, with hints; no conflicting actions.
9. **F-002** controller rumble: on by default, disableable, capability-aware, safe on disconnect.
10. **F-001** interface haptics: subtle; explicit opt-in, off by default; every request through the central preference-enforcing service.
11. **B-036** mobile search/keyboard navigation: keyboard lifecycle, IME, back handling, insets, bottom navigation, query kept.
12. **B-034** mobile search placeholder: shorter and visually softer.
13. **B-035** mobile search chips: no "Try", smaller, edge-to-edge scrolling row where appropriate.
14. **B-037** search empty state: deliberate and useful.
15. **B-010** search suggestion focus/clipping: spacing, visibility, controller focus.
16. **B-009 and B-032** focus outlines and poster indicators: outline obstruction; badge and watched-indicator overlap.
17. **B-002** multi-monitor playback controls: monitors, DPI, scaling, window transitions, mini-player expansion.
18. **B-015** poster alignment: vertical alignment and clipping.
19. **B-013** Home hero restoration: scroll and position restoration.

**Added by the owner's Phase 2 brief (2026-11-19)**, numbered in the brief B-043 to B-046 and renumbered in the tracker because those IDs were taken (DECISIONS.md, 2026-11-19):
- **B-046** redundant Profile button on Profiles;
- **B-047** the unexplained dot beside the Profile button;
- **B-048** pull-to-refresh on Home, Movies and Shows;
- **B-049** authorised PIN removal.

Also in Phase 2: **Forgot PIN** as the owner decided (Keep profile, Reset profile, and system authentication where available), and the **F-004** provider evaluation (no integration). Plan, audit and interaction contract: [PHASE-2-PLAN.md](PHASE-2-PLAN.md).

**Exit criteria.**
- Controller navigation is consistent across all applicable screens.
- No controller focus traps or inaccessible primary actions remain in the audited flows.
- Text input and keyboard interactions work correctly.
- Global shortcuts no longer interfere with active text input.
- The controller mapping is consistent, documented, and conflict-checked.
- Controller rumble and interface haptics follow their confirmed defaults and safety controls.
- Mobile Search and IME behaviour is verified.
- UI regressions are tested across phone, tablet, desktop, and TV/controller layouts as appropriate.
- B-042 is either assigned to Phase 2 with the owner's approval or stays explicitly unassigned; it is not quietly added.
- Every assigned issue has tracker evidence and relevant regression coverage.
- The Phase 2 review is presented to the owner for acceptance before Phase 3 is considered complete.

*State (2026-11-19):* **implemented, awaiting the owner's review** (PHASE-2-PLAN.md; VERIFICATION.md, "Phase 2"). The exit criteria against the evidence:
- **Controller navigation, focus traps, primary actions:** the shared contract is implemented and the audited flows are covered by tests. Physical-controller and Fire TV walks are still owed.
- **Text input, and shortcuts kept out of it:** B-019 to B-021, B-023, B-011.
- **Mapping:** documented and conflict-checked, with X reserved (INPUT_MODEL.md).
- **Haptics defaults and safety:** F-001 and F-002, through one gateway, with a source scan.
- **Mobile Search and the IME:** verified on the Phone 2a, physical.
- **UI regressions:** tested across phone, tablet (emulator), desktop and TV (emulator).
- **B-042:** still unassigned.
- **Tracker evidence:** every assigned issue has it.

*Final verification (2026-11-19):* on the owner's hardware (VERIFICATION.md, "Phase 2: final verification"; PHASE-2-PLAN.md, "Final verification and the follow-up").
- **Fixed and walked:** B-002 (two monitors); B-050 (the controller-reconnect crash); B-054 (the keyboard cursor); F-P2 (Y toggles the sidebar); controller feedback on Windows. Also the follow-up's B-051, B-052, B-053 and B-055.
- **Walked on the Pad 7 and the Fire TV (remote).**
- **Linux rumble:** implemented and tested in a VM.
- **Aptabase:** not selected.

Open: B-056 (the blank on closing the mini-player, Investigating); a DualSense hot-plug (B-050); the controller on the Fire TV and Android; physical Linux; the Phone 2a; and the owner's acceptance.

### Phase 3: media experience, discovery, Trickplay, and final performance

**Work order.**
1. **B-027** separate Jellyfin libraries and aggregate views: individual library browsing beside aggregate Movies and Shows, with navigation for TV, desktop, tablet and mobile. **The scope change (V1.1 candidate → Phase 3) is confirmed with the owner before implementation** if not already approved (tracker, owner decisions).
2. **B-038** sorting and filtering: supported options only; consistent; persistent; resettable; fast with realistic library sizes.
3. **B-030** Trickplay previews: mouse timeline previews and scene thumbnails with timestamps, selection, seeking, caching, and graceful fallback.
4. **B-029** right-stick Trickplay navigation: controller scene navigation with confirmation and cancellation (after B-030).
5. **F-003** video-synchronised haptics: experimental and separately controlled, only after a safe architecture and testing exist; off by default; controller video haptics off by default; explicit opt-in; no volume-only continuous rumble.
6. **B-028** media atmosphere: advertised effects perceptible and working; platform behaviour validated.
7. **B-040** recommendations/discovery variety: from metadata and watch state; no duplicate, empty, or misleading rows; profile privacy respected.
8. **B-014** featured hero: an intentional selection; alphabetical not kept as a default merely because it happens now.
9. **B-041** final performance optimisation and comparison: repeated against Phase 1's baseline; evidence-based optimisation; regression thresholds; one issue with two checkpoints.
10. **F-004** final analytics validation: architecture, schema, privacy, consent, transmission, withdrawal, retention, policy declarations; actual network behaviour validated and opt-out shown to prevent transmission.
11. **F-P3** Media Quick View (owner's decision, 2026-11-19): a contextual popup for a focused movie or TV show.
    - Opened by: the controller's **X** (reserved for it since Phase 2), a mouse's right-click, a touch long-press, a remote's Options/Menu, and a long press of OK/Select.
    - With no media item focused, X has no media-context action.
    - To reconcile here: Menu currently opens Sources while browsing.
    - Not implemented in Phase 2.
12. **F-P4** Adaptive Virtual Keyboard and Cross-Modality Layout Morphing (owner's steer, 2026-11-20). One keyboard system, researched and planned in [F-P4-ADAPTIVE-KEYBOARD.md](F-P4-ADAPTIVE-KEYBOARD.md):
    - a column-aligned QWERTY controller layout with an action column;
    - a conventional staggered mouse layout on Windows and Linux;
    - live, hysteresis-guarded switching while open, the keys morphing between layouts (about 300 ms; a crossfade under reduced motion);
    - nothing typed is lost, and focus is mapped by key.
    - Android touch keeps the system keyboard. Milestones M1–M7; acceptance and test matrix in the plan.

**Exit criteria.**
- Every assigned media-experience feature is implemented and reviewed.
- Sorting and filtering work reliably and efficiently.
- Trickplay is usable with mouse and controller where supported.
- Graceful fallback exists for unsupported media, source, or platform scenarios.
- Video haptics are restricted, controlled, and off by default.
- Home discovery and featured-content behaviour is intentional.
- The final performance audit is compared with the Phase 1 baseline.
- Analytics consent and privacy are verified end to end.
- Regression tests pass, or documented failures are reviewed.
- The tracker is complete and current.
- The Phase 3 review is presented to the owner for acceptance.

### Reconciliation: what the allocations do not settle (owner decisions; nothing moved)

The tracker's [owner-decision table](BETA-BUG-TRACKER.md#unassigned-issues-and-unresolved-owner-decisions) holds each one's options and reasoning.

| Item | Finding | Recommendation, not applied |
| --- | --- | --- |
| **B-042** | In Phase 2 in the first master prompt; absent from the updated phase list; "pending approval" in the master tracker prompt | **Pending owner approval; not in Phase 2 until approved.** Recommended: Phase 2, with B-025 (a focus/hover architecture question) |
| **B-016, B-017** | In no phase | Phase 2 (profile entry and chooser are interaction work beside B-019/B-020), or V1.1 |
| **B-022** | In no phase | Under B-041 (start-up is one of its measurements): measured in Phase 1's baseline, optimised in Phase 3 if the evidence says so |
| **B-029** | In Phases 2 and 3 in the master prompt | **Resolved by the owner's plan: Phase 3, after B-030**, whose previews it navigates |
| **B-041** | In Phases 1 and 3 | **One issue**: Phase 1 owns the baseline, Phase 3 the final optimisation and comparison (two checkpoints, not a second issue) |
| **B-027** | "V1.1 candidate" in the findings document | **In Beta 2 Phase 3 by the owner's plan** ("Separate Jellyfin libraries: Included"; DECISIONS.md). The master tracker prompt asks that the change be confirmed before implementation: owner to confirm |
| **F-005** | The findings document's F-005 is "Controller mapping customisation"; the master prompt used F-005 and F-006 for a collapsible sidebar and a Y-button sidebar toggle | IDs kept as the findings document has them; the sidebar items are registered as **F-P1** and **F-P2** until the owner numbers them. F-005 itself is in no phase (it overlaps B-039's "consider configurable mappings") |
| **F-P1, F-P2** | Allocated to Phase 3 in the master prompt; absent from the updated phase list | Owner to confirm the phase |
| **F-P3** | Media Quick View; the owner placed it in Phase 3 on 2026-11-19 | **Phase 3 (owner's decision).** Numbered F-P3 beside F-P1 and F-P2 until the owner numbers the proposals |

## Status words

The tracker's eight: Not Started · Investigating · In Progress · Fixed · Verified · Blocked · Deferred · Not Reproduced ([definitions](BETA-BUG-TRACKER.md#status-definitions-and-how-this-tracker-is-kept)). Verification is reported by kind (automated, emulator, physical device, desktop/OS in a VM or container, regression, owner acceptance), never merged. Statuses are kept only in the tracker.

## The register: bugs

Platforms: **P** Android phone, **T** Android tablet, **TV** Android TV / Google TV, **F** Fire TV, **W** Windows, **L** Linux. Status, evidence, and the original text: each ID's tracker entry.

| ID | Title | Priority (original) | Platforms | Phase |
| --- | --- | --- | --- | --- |
| [B-001](BETA-BUG-TRACKER.md#b-001) | Video frame stuck while audio continues | High, provisional | P T TV F | 1 |
| [B-002](BETA-BUG-TRACKER.md#b-002) | Playback controls disappear after moving window between monitors | Medium | W | 2 |
| [B-003](BETA-BUG-TRACKER.md#b-003) | Multiple Jellyfin addresses overwrite each other | Medium | all | 1 |
| [B-004](BETA-BUG-TRACKER.md#b-004) | Source configuration and controller input unreliable | Medium | TV | 2 |
| [B-005](BETA-BUG-TRACKER.md#b-005) | Library scan status toast overlaps interface | Low–Medium | P T | 1 |
| [B-006](BETA-BUG-TRACKER.md#b-006) | Metadata/search loading feels slow | Low–Medium | P W | 1 |
| [B-007](BETA-BUG-TRACKER.md#b-007) | Home D-pad navigation skips or mishandles See All | Medium | W (TV?) | 2 |
| [B-008](BETA-BUG-TRACKER.md#b-008) | Returning Home can leave controller navigation stuck | Medium | W | 2 |
| [B-009](BETA-BUG-TRACKER.md#b-009) | Focus outline obscures movie titles | Medium | W F TV | 2 |
| [B-010](BETA-BUG-TRACKER.md#b-010) | Search suggestions have poor focus spacing and clipping | Medium | W | 2 |
| [B-011](BETA-BUG-TRACKER.md#b-011) | Controller search has no usable on-screen keyboard | Medium | W TV | 2 |
| [B-012](BETA-BUG-TRACKER.md#b-012) | Controller shortcuts incomplete or unclear | Low | W | 2 |
| [B-013](BETA-BUG-TRACKER.md#b-013) | Home hero does not restore its full position after scrolling | Medium | W | 2 |
| [B-014](BETA-BUG-TRACKER.md#b-014) | Home hero appears to show one alphabetically selected movie | Low, design question | all | 3 |
| [B-015](BETA-BUG-TRACKER.md#b-015) | Poster rows have inconsistent vertical alignment or clipping | Low | W | 2 |
| [B-016](BETA-BUG-TRACKER.md#b-016) | App opens last-used profile instead of profile selection | Medium | W L P T | **unallocated** |
| [B-017](BETA-BUG-TRACKER.md#b-017) | No obvious way to log out or switch active profile | Medium | all | **unallocated** |
| [B-018](BETA-BUG-TRACKER.md#b-018) | Profiles can be deleted without proving ownership | Critical, security | all | 1 |
| [B-019](BETA-BUG-TRACKER.md#b-019) | Backspace in profile PIN input triggers Home | Medium | W L | 2 |
| [B-020](BETA-BUG-TRACKER.md#b-020) | Backspace in profile-name input triggers Home | Medium | W L | 2 |
| [B-021](BETA-BUG-TRACKER.md#b-021) | Backspace behaviour inconsistent across input fields | Medium | W L | 2 |
| [B-022](BETA-BUG-TRACKER.md#b-022) | Windows startup slower than Android | Low–Medium | W | **unallocated** (see B-041) |
| [B-023](BETA-BUG-TRACKER.md#b-023) | Reflux-designed on-screen keyboard for controller input | High, UX requirement | W T (TV to assess) | 2 |
| [B-024](BETA-BUG-TRACKER.md#b-024) | Conflicting resolution values in version description | Medium, provisional | W | 1 |
| [B-025](BETA-BUG-TRACKER.md#b-025) | Controller support robust across the whole app | High, UX requirement | W TV F P T | 2 |
| [B-026](BETA-BUG-TRACKER.md#b-026) | Reflux remains as a Windows background process after closing | Medium, provisional | W | 1 |
| [B-027](BETA-BUG-TRACKER.md#b-027) | Preserve and expose separate Jellyfin libraries | V1.1 requirement → Beta 2 by the owner's plan (confirmation requested) | all | 3 |
| [B-028](BETA-BUG-TRACKER.md#b-028) | Media atmosphere changes have little or no visible effect | Medium, provisional | all | 3 |
| [B-029](BETA-BUG-TRACKER.md#b-029) | Right analogue stick has no useful playback navigation | Medium, UX requirement | controllers | 3 (after B-030) |
| [B-030](BETA-BUG-TRACKER.md#b-030) | Trickplay preview thumbnails are missing | High, playback UX requirement | W L TV F P T | 3 |
| [B-031](BETA-BUG-TRACKER.md#b-031) | Linux playback fails under a non-C numeric locale | High, potentially release-blocking | L | 1 |
| [B-032](BETA-BUG-TRACKER.md#b-032) | Disconnected-source badge overlaps poster content and watch indicators | Medium | P T (others?) | 2 |
| [B-033](BETA-BUG-TRACKER.md#b-033) | Orphaned favourites remain after their source is removed | Medium | all | 1 |
| [B-034](BETA-BUG-TRACKER.md#b-034) | Mobile search placeholder too long and visually dominant | Low–Medium | P T | 2 |
| [B-035](BETA-BUG-TRACKER.md#b-035) | Mobile search suggestion chips oversized and labelled "Try" | Low–Medium | P T | 2 |
| [B-036](BETA-BUG-TRACKER.md#b-036) | Mobile Search layout and navigation break with the keyboard | Medium | P T | 2 |
| [B-037](BETA-BUG-TRACKER.md#b-037) | Search has no designed empty state | Low–Medium | all | 2 |
| [B-038](BETA-BUG-TRACKER.md#b-038) | Movies and Shows lack sorting and filtering | Medium, product requirement | all | 3 |
| [B-039](BETA-BUG-TRACKER.md#b-039) | Controller button mapping needs a deliberate design | Medium, UX requirement | controllers | 2 |
| [B-040](BETA-BUG-TRACKER.md#b-040) | Home recommendations need more variety | Low–Medium, improvement | all | 3 |
| [B-041](BETA-BUG-TRACKER.md#b-041) | Cross-platform performance audit | High, V1 readiness | all | 1 (baseline) + 3 (final) |
| [B-042](BETA-BUG-TRACKER.md#b-042) | Controller focus and pointer hover conflict on media cards | Major (first master prompt) / to be determined (tracker prompt) | W (pointer + controller) | **pending owner approval** (recommended 2) |

Issues found during Beta 2 work (B-043 onward) are in the tracker's [newly discovered issues](BETA-BUG-TRACKER.md#newly-discovered-issues-b-043-onward); they are allocated by existing scope only.

## The register: features

| ID | Feature | Phase | Default |
| --- | --- | --- | --- |
| [F-001](BETA-BUG-TRACKER.md#f-001) | Interface haptics (phone, tablet) | 2 | **Off**: explicit opt-in |
| [F-002](BETA-BUG-TRACKER.md#f-002) | Controller rumble | 2 | **On** on compatible hardware; toggle in Settings |
| [F-003](BETA-BUG-TRACKER.md#f-003) | Video haptics | 3 | **Off**, independent of interface haptics; **off on controllers** until explicitly enabled |
| [F-004](BETA-BUG-TRACKER.md#f-004) | Privacy-respecting analytics and diagnostics | 1 (foundation) + 3 (validation) | **Off**: explicit informed consent |
| [F-005](BETA-BUG-TRACKER.md#f-005) | Controller mapping customisation | **unallocated** (overlaps B-039) | — |
| [F-P1](BETA-BUG-TRACKER.md#f-p1-and-f-p2) | Cross-platform collapsible sidebar (master prompt's "F-005") | owner to confirm (Phase 3 in the master prompt) | Expanded or collapsed, per form factor |
| [F-P2](BETA-BUG-TRACKER.md#f-p1-and-f-p2) | Controller sidebar focus toggle with Y (master prompt's "F-006") | owner to confirm (with F-P1) | — |

### Acceptance criteria the owner set (2026-11-19): defaults and consent are requirements

**Haptics.**
- Phone and tablet interface haptics are **off** until the user opts in; no vibration motor is used for Reflux interface feedback before that. One central haptics service checks the preference before every device haptic request, so that no screen or component can bypass it; controller input handling never triggers device vibration.
- Controller rumble is **on** by default: the preference starts on; rumble happens only with a connected controller that supports it; support is detected; unsupported controllers get none and cause no error; disconnecting stops rumble at once; it can be disabled globally.
- Video haptics are **off** by default, enabled separately (in the player's settings and an app-level control), **off on controllers** until enabled there; enabling interface haptics never enables video haptics; changing playback device or profile never switches them on; an intensity ceiling and a rate limit; short envelopes; never constant vibration from sustained audio; respect pause, stop, seek, playback errors, and controller disconnects; designed from audio energy, transients, and optional frequency bands, never volume alone.
- Settings persist and behave the same on every supported platform; unsupported hardware disables the option or explains why.

**Analytics and telemetry.** Off by default; explicit, informed opt-in; a clear statement of exactly what is collected; nothing from the library or watch history, no credentials, paths, or server addresses; easy opt-out, and deletion where applicable; no advertising trackers; every third-party service documented and the legal disclosures updated before any collection; no third-party SDK without privacy, dependency, and legal review.

**Sorting and filtering (B-038).** Sorting by title, year, date added, and other meaningful metadata; filters by genre, watched, resolution, and other supported attributes; the same behaviour in Movies and Shows; no full-library reload on every filter change.

**Trickplay (B-030, B-029).** Mouse hover previews on the timeline with a timestamp; right-stick navigation through preview timestamps; A confirms the seek; B cancels; thumbnails cached, never the same tile downloaded twice; Jellyfin and Plex where their preview data exists; sources without Trickplay handled clearly; local-file previews investigated separately, not promised.

## Verification matrix (what each item must be verified on)

● required · ○ where it applies · — not applicable. "Physical" means a physical device; an emulator, VM, or container result is recorded as such, never as physical (CLAUDE.md, "Testing environment").

| Item | Phone | Tablet | Android TV | Fire TV | Windows | Linux |
| --- | --- | --- | --- | --- | --- | --- |
| B-018, B-033, B-024, B-006 (library rules) | ○ | ○ | ○ | ○ | ● | ○ |
| B-031 | — | — | — | — | — | ● (physical desktop) |
| B-001 | ● | ● | ● (emulator) | ● | — | — |
| B-026 | — | — | — | — | ● | — |
| B-003 | ● | ● | ○ | ○ | ● | ○ |
| B-005 | ● | ● | ● | ● | ○ | ○ |
| B-041 | ● | ● | ● | ● | ● | ● |
| Controller and focus (B-025, B-007 to B-013, B-039, B-042) | ○ | ● | ● | ● | ● | ○ |
| Text entry (B-004, B-011, B-019 to B-021, B-023) | ○ | ● | ● | ● | ● | ● |
| Mobile Search (B-034 to B-037) | ● | ● | — | — | ○ | ○ |
| F-001 interface haptics | ● | ● | — | — | — | — |
| F-002 controller rumble | ○ | ● | ● | ● | ● | ○ |
| B-030, B-029, F-003 | ○ | ○ | ● | ● | ● | ● |
| B-027, B-038, B-028, B-040, B-014 | ● | ● | ● | ● | ● | ○ |

For controller work, each item is walked for: focus movement, Back, sidebar entry and exit, focus restoration, mouse and controller together, hover and focus states, screen sizes, button mapping, and unsupported controllers. For performance work: device, OS, build and commit, sources, cold and warm, run count, median and P95, CPU and memory, and the physical, emulator, or VM distinction.

## The Beta 2 gate

Beta 2 (`0.9.0-beta.2`, VERSIONING.md) is built only when every step holds and the owner has approved it:

1. Phase 1 closed, its residual checks done or accepted by the owner (planning/PHASE-1-PLAN.md, "Stage F: closeout").
2. Phase 2 implemented.
3. Phase 2's tests and its platform verification done (the matrix above).
4. Phase 3 implemented.
5. Phase 3's tests and its platform verification done.
6. The tracker (BETA-BUG-TRACKER.md) reconciled: every B and F item with a status and evidence, nothing silently dropped, newly discovered issues included.
7. No open release-blocking bug.
8. The full application checks green locally and on hosted CI.
9. Cross-platform validation on the matrix's devices.
10. The owner's review and approval.
11. The owner's authorisation of the version and build (VERSIONING.md).
12. Release notes, from CHANGELOG.md's Unreleased.
13. The build and its distribution prepared through the release workflow (RELEASE.md).
