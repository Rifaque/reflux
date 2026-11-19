# Beta 2: the bug and feature register, the three phases, and the gate

The authoritative record of everything Beta 2 is to fix and add (DECISIONS.md, 2026-11-19, "Beta 2 is cut only after Phases 2 and 3"). ROADMAP.md says where the work stands and links here; VERIFICATION.md holds the evidence; this file holds the register and the plan, and nothing here is repeated there.

**Beta 2 is not cut, built, or versioned** until the gate at the end of this file holds and the owner approves it. `0.9.0-beta.1` (`versionCode` 90031, tag `v0.9.0-beta.1`) stays in testing, untouched.

## Sources

- **The owner's findings document**, "Reflux Beta Testing Findings" (build 0.9.0-beta.1, last updated 18 November 2026), provided on 2026-11-19: the authoritative text of B-001 to B-041 and F-001 to F-005. Each entry below carries its original wording; an entry's own status and evidence follow it, marked as such.
- **The owner's master prompt** of 2026-11-19: B-042 and two sidebar features (below, F-P1 and F-P2), and the Phase 1 brief before it (2026-11-18).
- **The owner's phase plan** of 2026-11-19 ("Updated Beta 2 phase allocation"): the three phases, their order, and the acceptance requirements for haptics, analytics, sorting and filtering, and Trickplay.
- No other source holds these IDs: the repository never did, and no earlier session's notes on this machine mention them (searched 2026-11-19). Nothing below is reconstructed from memory.

## The three phases (the owner's order)

| Phase | Theme | Order |
| --- | --- | --- |
| **1** | Reliability, security, data integrity; telemetry architecture and consent groundwork; performance baselines | B-018, B-031, B-001, B-026, B-003, B-033, B-024, B-005, B-006, analytics/telemetry (architecture, event schema, privacy review, consent), B-041 (baselines) |
| **2** | Interaction, controller, and haptics | B-025, B-008, B-007, B-004, B-019 + B-020 + B-021, B-023, B-011, B-039 + B-012, F-002, F-001, B-036, B-034, B-035, B-037, B-010, B-009 + B-032, B-002, B-015, B-013 |
| **3** | Media experience, Trickplay, and advanced features | B-027, B-038, B-030, B-029, F-003, B-028, B-040, B-014, B-041 (final optimisation and benchmarks), analytics/telemetry (final event validation, retention, end-to-end verification) |

One follow-up per phase at most, for a genuine blocker or an essential correction (AGENTS.md, milestone execution rules).

### Reconciliation: what the allocations do not settle (owner decisions; nothing moved)

| Item | Finding | Recommendation, not applied |
| --- | --- | --- |
| **B-042** | In Phase 2 in the master prompt; absent from the updated phase list | Keep in Phase 2, with B-025 (it is a focus/hover architecture question). Listed in Phase 2 below |
| **B-016, B-017** | In no phase | Phase 2 (profile entry and chooser are interaction work beside B-019/B-020), or V1.1 |
| **B-022** | In no phase | Under B-041 (startup is one of its measurements): measured in Phase 1's baseline, optimised in Phase 3 if the evidence says so |
| **B-029** | In Phases 2 and 3 in the master prompt | **Resolved by the owner's plan: Phase 3, after B-030**, whose previews it navigates |
| **B-041** | In Phases 1 and 3 | **One issue**: Phase 1 owns the baseline, Phase 3 the final optimisation and comparison (a milestone requirement, not a second issue) |
| **B-027** | "V1.1 candidate" in the findings document | **Moved into Beta 2 Phase 3 by the owner's plan** ("Separate Jellyfin libraries: Included"); recorded in DECISIONS.md |
| **F-005** | The findings document's F-005 is "Controller mapping customisation"; the master prompt used F-005 and F-006 for a collapsible sidebar and a Y-button sidebar toggle | IDs kept as the findings document has them; the sidebar items are registered as **F-P1** and **F-P2** until the owner numbers them. F-005 itself is in no phase (it overlaps B-039's "consider configurable mappings") |
| **F-P1, F-P2** | Allocated to Phase 3 in the master prompt; absent from the updated phase list | Owner to confirm the phase |

## Status words

Not started · Investigating · Planned · Implemented, not verified · Patched, tests passing · Verified on physical device · Verified on emulator · Verified in a VM or container · Verified in CI · Blocked · Deferred · Closed. A status says what the evidence shows, never more (VERIFICATION.md says what ran).

## The register: bugs

Platforms: **P** Android phone, **T** Android tablet, **TV** Android TV / Google TV, **F** Fire TV, **W** Windows, **L** Linux.

| ID | Title | Priority (original) | Platforms | Phase | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| B-001 | Video frame stuck while audio continues | High, provisional | P T TV F | 1 | Verified on physical device | Reproduced by a new test on the old code; fixed; Pad 7, Phone 2a, Fire TV, Google TV emulator |
| B-002 | Playback controls disappear after moving window between monitors | Medium | W | 2 | Not started | |
| B-003 | Multiple Jellyfin addresses overwrite each other | Medium | all | 1 | Patched, tests passing | Fake-server and library tests; no live server |
| B-004 | Source configuration and controller input unreliable | Medium | TV | 2 | Not started | |
| B-005 | Library scan status toast overlaps interface | Low–Medium | P T | 1 | Patched, tests passing | Offscreen layout tests at five sizes; no device walk |
| B-006 | Metadata/search loading feels slow | Low–Medium | P W | 1 | Patched, tests passing (local search only) | Fixture before/after; provider timings not measured; the report's provider still to clarify |
| B-007 | Home D-pad navigation skips or mishandles See All | Medium | W (TV?) | 2 | Not started | |
| B-008 | Returning Home can leave controller navigation stuck | Medium | W | 2 | Not started | |
| B-009 | Focus outline obscures movie titles | Medium | W F TV | 2 | Not started | |
| B-010 | Search suggestions have poor focus spacing and clipping | Medium | W | 2 | Not started | |
| B-011 | Controller search has no usable on-screen keyboard | Medium | W TV | 2 | Not started | |
| B-012 | Controller shortcuts incomplete or unclear | Low | W | 2 | Not started | |
| B-013 | Home hero does not restore its full position after scrolling | Medium | W | 2 | Not started | |
| B-014 | Home hero appears to show one alphabetically selected movie | Low, design question | all | 3 | Not started | |
| B-015 | Poster rows have inconsistent vertical alignment or clipping | Low | W | 2 | Not started | |
| B-016 | App opens last-used profile instead of profile selection | Medium | W L P T | **unallocated** | Not started | |
| B-017 | No obvious way to log out or switch active profile | Medium | all | **unallocated** | Not started | |
| B-018 | Profiles can be deleted without proving ownership | Critical, security | all | 1 | Patched, tests passing | 6 library + 3 UI tests |
| B-019 | Backspace in profile PIN input triggers Home | Medium | W L | 2 | Not started | |
| B-020 | Backspace in profile-name input triggers Home | Medium | W L | 2 | Not started | |
| B-021 | Backspace behaviour inconsistent across input fields | Medium | W L | 2 | Not started | |
| B-022 | Windows startup slower than Android | Low–Medium | W | **unallocated** (see B-041) | Investigating | Phase 1 baseline: packaged start to window median 2.6–2.7 s on one Windows machine |
| B-023 | Reflux-designed on-screen keyboard for controller input | High, UX requirement | W T (TV to assess) | 2 | Not started | |
| B-024 | Conflicting resolution values in version description | Medium, provisional | W | 1 | Patched, tests passing | Core and UI tests (also removes a lone `1080p` "edition" beside `1080p`) |
| B-025 | Controller support robust across the whole app | High, UX requirement | W TV F P T | 2 | Not started | |
| B-026 | Reflux remains as a Windows background process after closing | Medium, provisional | W | 1 | Investigating (not reproduced) | 19 clean closes on the owner's machine; see the entry |
| B-027 | Preserve and expose separate Jellyfin libraries | V1.1 requirement → **Beta 2 by owner decision** | all | 3 | Not started | |
| B-028 | Media atmosphere changes have little or no visible effect | Medium, provisional | all | 3 | Not started | |
| B-029 | Right analogue stick has no useful playback navigation | Medium, UX requirement | controllers | 3 (after B-030) | Not started | |
| B-030 | Trickplay preview thumbnails are missing | High, playback UX requirement | W L TV F P T | 3 | Not started | |
| B-031 | Linux playback fails under a non-C numeric locale | High, potentially release-blocking | L | 1 | Patched, tests passing; verified in a VM and containers | WSL2 before/after; five distributions' containers; no physical Linux desktop |
| B-032 | Disconnected-source badge overlaps poster content and watch indicators | Medium | P T (others?) | 2 | Not started | |
| B-033 | Orphaned favourites remain after their source is removed | Medium | all | 1 | Patched, tests passing | Library and UI tests |
| B-034 | Mobile search placeholder too long and visually dominant | Low–Medium | P T | 2 | Not started | |
| B-035 | Mobile search suggestion chips oversized and labelled "Try" | Low–Medium | P T | 2 | Not started | |
| B-036 | Mobile Search layout and navigation break with the keyboard | Medium | P T | 2 | Not started | |
| B-037 | Search has no designed empty state | Low–Medium | all | 2 | Not started | |
| B-038 | Movies and Shows lack sorting and filtering | Medium, product requirement | all | 3 | Not started | |
| B-039 | Controller button mapping needs a deliberate design | Medium, UX requirement | controllers | 2 | Not started | |
| B-040 | Home recommendations need more variety | Low–Medium, improvement | all | 3 | Not started | |
| B-041 | Cross-platform performance audit | High, V1 readiness | all | 1 (baseline) + 3 (final) | Investigating (baseline partial) | Desktop before/after; Android release cold starts on four devices; see the entry |
| B-042 | Controller focus and pointer hover conflict on media cards | Major | W (pointer + controller) | 2 | Not started | |

### Each bug, as reported (original text), with status

#### B-001: Video frame becomes stuck while audio continues
- **Priority:** High, provisional | Android
- **Details:** On two occasions, opening another movie or episode left the previous video's final frame visible while new audio and playback time continued.
- **Investigate:** Player reuse/release lifecycle, decoder/surface attachment, asynchronous transitions and race conditions.
- *Status (2026-11-19): Verified on physical device. The engine was attached only when its surface view was first made; a new engine made in the same frame as the old one's release was never attached. `SurfaceHandoverOnDeviceTest` reproduces it (fails on the old code from the mini-player), passes with the fix on the Pad 7, Phone 2a, Fire TV Stick 4K Plus (4/4, the dock case not applicable), and the Google TV emulator. Outstanding: a server source in the handover test.*

#### B-002: Playback controls disappear after moving window between monitors
- **Priority:** Medium | Windows
- **Details:** Moving the window between monitors causes controls to disappear or render incorrectly. Expanding the mini-player can reproduce it.
- **Investigate:** Per-monitor DPI, Compose sizing and surface recreation.

#### B-003: Multiple addresses for the same Jellyfin server overwrite each other
- **Priority:** Medium | Android/tablet
- **Details:** Adding a Tailscale endpoint overwrites the existing LAN endpoint.
- **Expected:** One logical server with multiple endpoints, LAN preferred and Tailscale fallback.
- **Investigate:** Server identity, endpoint storage, deduplication and failover.
- *Status: Patched, tests passing. One source keeps every address (in its sealed connection; no SQL migration); home network first, fallback when an address does not answer, back to the LAN within a minute. 15 tests. Outstanding: a live server at two real addresses; a device walk of Sources' address list.*

#### B-004: Source configuration and controller input unreliable
- **Priority:** Medium | Android TV/controller
- **Details:** Address field opens the keyboard and then loses it. Pressing A/select again does not reliably reopen it. Source setup navigation is inconsistent.
- **Investigate:** Focus management, keyboard activation and input-method lifecycle.

#### B-005: Library scan status toast overlaps interface
- **Priority:** Low–Medium | Android phone/tablet
- **Details:** "Finding titles and details" overlaps search, poster rows and headers.
- **Evidence:** Multiple screenshots supplied.
- **Investigate:** Placement, duration, responsive behaviour and component lifecycle.
- *Status: Patched, tests passing. On phones and TVs the sign is a dot in a fixed place (its words for screen readers); on Search's row it steps aside; beside a rail or sidebar its words keep to their bar. Layout tests at five sizes found and fixed a second overlap. Outstanding: phone, tablet, and TV device walk.*

#### B-006: Metadata/search loading feels slow
- **Priority:** Low–Medium | Android/Windows
- **Details:** Search and movie/show metadata loading feel slow. The exact provider referred to in the original report needs clarification.
- **Investigate:** Network requests, cache behaviour, artwork loading, database access and main-thread work.
- *Status: Patched, tests passing, for local search and listings (title search median 236 → 13 ms on an 11,000-item fixture, JVM). Not measured: Trakt lookups, artwork fetch and decode, episode enrichment, offline. The provider question is still open.*

#### B-007: Home D-pad navigation skips or mishandles See All
- **Priority:** Medium | Windows controller, potentially TV
- **Details:** Vertical navigation sometimes skips See All. Moving down from See All can focus the next row's See All instead of its corresponding content.
- **Investigate:** Focus graph, row boundaries and lazy-list behaviour.

#### B-008: Returning Home can leave controller navigation stuck
- **Priority:** Medium | Windows controller
- **Details:** After Home → Movies → Home, right D-pad/joystick navigation sometimes fails to enter the main content. Focus restoration and scroll position are inconsistent.
- **Investigate:** Focus restoration and lazy-list state.

#### B-009: Focus outline obscures movie titles
- **Priority:** Medium | Windows, Fire TV, Android TV
- **Details:** White focus border extends over movie titles. More severe on Windows, smaller on Fire TV.
- **Evidence:** Windows and mobile screenshots supplied.
- **Investigate:** Card bounds, clipping, elevation and scaling.

#### B-010: Search suggestions have poor focus spacing and clipping
- **Priority:** Medium | Windows controller
- **Details:** Suggestion chips are too close together, focus outlines overlap neighbours, and the final suggestion is clipped.
- **Investigate:** Responsive layout, horizontal scrolling and padding.

#### B-011: Controller search has no usable on-screen keyboard
- **Priority:** Medium | Windows/controller, TV relevance
- **Details:** A user cannot reliably enter a search query using only a controller.

#### B-012: Controller shortcuts incomplete or unclear
- **Priority:** Low | Windows controller
- **Details:** Y opens Sources, X does nothing, and triggers appear unused.
- **Improvement:** Useful documented and configurable mappings.

#### B-013: Home hero does not restore its full position after scrolling
- **Priority:** Medium | Windows controller
- **Details:** Returning to the featured area leaves only Play/Details visible rather than restoring the full hero artwork and content.

#### B-014: Home hero appears to show one alphabetically selected movie
- **Priority:** Low, design question
- **Details:** Hero displays 2 Fast 2 Furious. It may be selecting the first alphabetical title.
- **Investigate:** Intended selection logic and whether multiple featured titles should rotate.

#### B-015: Poster rows have inconsistent vertical alignment or clipping
- **Priority:** Low | Windows desktop
- **Details:** Some posters appear vertically offset or partially hidden.
- **Evidence:** Screenshots supplied.

#### B-016: App opens last-used profile instead of profile selection
- **Priority:** Medium | Desktop/Android
- **Details:** After creating and using a Guest profile, restarting the application automatically opens the last-used profile.
- **Expected:** A startup profile chooser, with the most recently used profile first.
- *Note: the current design (WATCH_STATE.md, "Profiles": a phone or a computer opens the profile it had; a TV asks) is the behaviour reported. Changing it is a design decision as well as a fix.*

#### B-017: No obvious way to log out or switch active profile
- **Priority:** Medium
- **Details:** Profile switching is not discoverable.
- **Expected:** A clear "Who's watching?" or profile-switching entry point.

#### B-018: Profiles can be deleted without proving ownership
- **Priority:** Critical, security-sensitive
- **Details:** While signed into Guest, the user could delete Main without entering Main's PIN.
- **Expected:** Authorization enforced at the underlying deletion operation, not merely through UI visibility or confirmation.
- **Investigate:** Profile ownership and authorization boundaries.
- *Status: Patched, tests passing. The target profile's PIN is checked in the transaction that deletes, with the same count of wrong entries and waits as opening it; nothing is signed out or deleted on a wrong PIN. Owner decision open: "Forgot PIN" still erases a protected profile's private data without its PIN (by design, for recovery).*

#### B-019: Backspace in profile PIN input triggers Home
- **Priority:** Medium
- **Details:** Backspace navigates Home instead of deleting a digit.

#### B-020: Backspace in profile-name input triggers Home
- **Priority:** Medium
- **Details:** Backspace navigates Home instead of deleting a character.

#### B-021: Backspace behaviour inconsistent across input fields
- **Priority:** Medium
- **Details:** PIN/name fields are affected, while Search and some source fields behave differently.
- **Investigate:** Global shortcut propagation and input focus.

#### B-022: Windows startup slower than Android
- **Priority:** Low–Medium
- **Details:** Windows startup takes approximately 3–4 seconds, while the tested Android device starts faster.
- **Investigate:** Cold/warm startup, JVM/Compose initialisation, database and source loading.
- *Status: Investigating. Measured in Phase 1 (one machine, empty library, packaged): start to window 5.0–5.2 s on a cold first run, 2.2–2.7 s warm; Android release cold start 302–480 ms medians on four devices. Not a like-for-like comparison (a JVM desktop window against an Android activity).*

#### B-023: Reflux-designed on-screen keyboard for controller input
- **Priority:** High, UX requirement
- **Platforms:** Windows desktop and Android tablets; TV applicability to be assessed.
- **Details:** Native OS keyboard alone is not the desired experience. Reflux should provide an integrated, polished keyboard navigable with D-pad/joystick.
- **Expected:** Letters, numbers, symbols, space, backspace, confirm, clear focus states, reliable insertion, dismissal/reopening without losing text and responsive layouts.
- **Fields:** Search, profile creation, PIN entry and source configuration.
- **Related:** B-004, B-011, B-019, B-020 and B-021.

#### B-024: Conflicting resolution values in version description
- **Priority:** Medium, provisional | Windows
- **Details:** Version list shows `1080p · 1080p` for one copy and `1440p · Dolby Vision · 2160p HDR` for another.
- **Clarification:** Display resolution does not determine source resolution. The second version appears to contain two conflicting source-resolution values.
- **Expected:** One authoritative source resolution, with HDR/Dolby Vision compatibility, codec and audio presented separately.
- **Investigate:** Media probing, metadata precedence and release-title parsing.
- *Status: Patched, tests passing. Jellyfin's version name (from the file name) was shown as an edition; now a name is an edition only when it names one, and the exact size is shown only when measured.*

#### B-025: Controller support should be robust across the entire application
- **Priority:** High, cross-platform UX requirement
- **Platforms:** Windows, Android TV, Google TV, Fire TV and Android devices with controllers.
- **Details:** Controller support should be consistent and complete, not merely basic button handling.
- **Inputs:** D-pad, analogue sticks, face buttons, bumpers, triggers, Start/Select and platform-specific remotes.
- **Areas to validate:** Navigation, focus restoration, scrolling, dialogs, text entry, profile management, source setup, playback controls, mini-player, settings, downloads and all primary actions.
- **Expected:** Predictable focus movement, no trapped focus, no accidental navigation, clear focus indication, coherent back/cancel behaviour and complete controller-only operation.
- **Related:** B-007 through B-012 and B-023.

#### B-026: Reflux remains as a Windows background process after closing
- **Priority:** Medium, provisional | Windows
- **Correction:** This was observed on the friend's PC, not the user's own PC.
- **Details:** Friend opened Reflux, closed it, and later found Reflux listed under Task Manager's Background processes. The screenshot showed two Reflux entries, each using approximately 22 MB.
- **Reproduction:** User could not reproduce the behaviour on their own PC.
- **Important:** This is not a report that Reflux is classified as a background process while its window is open. The reported issue is that it appears to remain after closing.
- **Investigate:** executable paths, PIDs and parent/child relationships; whether the two entries are instances, JVM processes, or helpers; whether the window closes or hides; non-daemon threads; native libmpv resources; a tray component; particular operations or Windows configurations.
- **Expected:** Closing the last application window should terminate Reflux unless an explicitly designed background operation is active.
- **Do not assume:** Task Manager classification alone proves a defect. Establish actual process lifecycle first.
- *Status: Investigating, not reproduced. Found: a packaged Reflux is two processes, a launcher (5 threads) and its child application (about 100 threads, about 500 MB working set on an empty library); 19 closes on the owner's machine ended both within a second. The report's two entries of about 22 MB each match neither a running application nor this pair as measured, so the report is not explained either: it stays open. Not tested: closing during playback, a hung engine. The tester procedure is in VERIFICATION.md, "Phase 1".*

#### B-027: Preserve and expose separate Jellyfin libraries
- **Priority:** V1.1 product requirement | Jellyfin and other multi-library sources
- **Correction:** This is not about genres or show taxonomy.
- **Details:** A friend has multiple separate Jellyfin libraries, such as a Marvel library containing only Marvel movies and TV shows, alongside other libraries.
- **Desired library model:** Movies and TV Shows remain the two primary aggregated sections; media from all relevant libraries also appears in the appropriate aggregated section; each original source library remains separately accessible as its own view (Marvel movies in Movies and Marvel; Marvel shows in TV Shows and Marvel); other Jellyfin libraries are preserved similarly.
- **Desktop/TV direction:** Sidebar can contain primary Movies and TV Shows plus individual source-library entries.
- **Mobile direction:** Do not simply copy the desktop sidebar. Reconsider the phone navigation and collection discovery experience to expose separate libraries without overcrowding the bottom navigation.
- **Investigate:** Jellyfin user views/library IDs, library content types, membership, item deduplication across aggregate and individual views, source-specific permissions, refresh and offline behaviour.
- **Target:** V1.1 candidate, not a V1.0 acceptance requirement.
- *Owner decision 2026-11-19: included in Beta 2, Phase 3. Separate from B-003 (one server at several addresses). Note: Reflux already keeps each server library's own page under Sources (M3); this is about first-class library views in the navigation.*

#### B-028: Media atmosphere changes appear to have little or no visible effect
- **Priority:** Medium, provisional | UI/visual presentation
- **Details:** Tester did not notice meaningful changes when media atmosphere was expected to respond to selected media.
- **Investigate:** whether active; colour extraction and application; subtlety; updates on change; caching or rendering; platform/theme differences.
- **Expected:** If enabled and advertised, it should produce a perceptible, consistent result. Otherwise communicate its purpose and limitations honestly.

#### B-029: Right analogue stick has no useful playback navigation behaviour
- **Priority:** Medium, UX requirement | Controller playback
- **Details:** Left joystick and D-pad both perform directional navigation. Right joystick currently does nothing.
- **Desired:** Use the right analogue stick during playback for scene-based seeking through Trickplay previews.
- **Related:** B-030. *Depends on B-030; Phase 3, after it.*

#### B-030: Trickplay preview thumbnails are missing
- **Priority:** High, playback UX requirement | Desktop and controller playback
- **Details:** Moving the mouse over the playback progress/seek bar does not show preview images of scenes at corresponding timestamps.
- **Expected mouse behaviour:** hover over the seek bar; a small preview window with the scene thumbnail and the timestamp, updating as the pointer moves; seek to the chosen timestamp when selected.
- **Expected controller behaviour:** during playback, the right analogue stick moves backward and forward through scene previews shown with their timestamp in a floating window; A/select seeks there; Back/cancel exits without seeking.
- **Investigate:** Thumbnail generation/availability, indexing, caching, playback-engine integration, synchronisation and performance.
- **Source support:** Work with local media and supported remote sources where preview generation is possible.
- **Fallback:** If a source or media item has no Trickplay thumbnails, provide a graceful fallback rather than pretending previews are available.
- **Note:** This is a playback capability, not merely a cosmetic tooltip.

#### B-031: Linux playback fails when libmpv is initialised under a non-C numeric locale
- **Priority:** High, potentially release-blocking | Linux desktop
- **Source:** Friend's investigation/report supplied on 18 November. Not yet independently verified by the Reflux developer.
- **Reported environment:** Linux, libmpv 0.42.0, `LC_NUMERIC=en_US.UTF-8`.
- **Reported error:** `Non-C locale detected. This is not supported. Call 'setlocale(LC_NUMERIC, "C");' in your code.`
- **Reported behaviour:** Reflux finds installed libmpv but cannot initialise it, preventing playback.
- **Reported workaround:** `LC_NUMERIC=C ~/Downloads/reflux-0.9.0-beta.1-linux-x64/bin/Reflux`
- **Likely root cause:** The process inherits a non-C numeric locale, and libmpv initialisation does not establish the required C numeric locale first.
- **Required:** reproduce on a supported distribution; establish the C numeric locale safely before libmpv starts; verify playback without the workaround; preserve the user's language and formats; log the actual libmpv error; avoid flooding logs on repeated Play.
- **Additional findings:** the log said only that libmpv was found but could not be initialised; repeated attempts repeated the errors; Wayland/XWayland is a possible later issue, to investigate only if playback still fails or video stays black after the locale fix.
- **Important:** The environment-variable workaround is diagnostic, not the desired product fix.
- *Status: Patched, tests passing; verified in a VM and in containers. Reproduced (WSL2 Ubuntu 24.04, libmpv 0.37, `de_DE.UTF-8`: 22 of 24 engine tests failing), fixed, re-run passing; the log now says why; identical problems are counted, not repeated. The packaged app started its engine under `de_DE.UTF-8` in Ubuntu 22.04, 24.04, Debian 12, and Fedora 41 containers (Arch: engine started; its image has no locale data). Not verified: a physical Linux desktop, `en_US.UTF-8` as reported, libmpv 0.42.0 with the locale, Jellyfin playback, Wayland.*

#### B-032: Disconnected-source badge overlaps poster content and watch indicators
- **Priority:** Medium | Android phone/tablet, potentially other platforms
- **Evidence:** Multiple screenshots supplied on 18 November.
- **Details:** The "Not connected…" source-status badge appears over poster artwork and is truncated with an ellipsis. The watched checkmark can overlap the same badge.
- **Expected:** status understandable without covering poster content; watched state distinguishable; indicators never collide; readable at phone and tablet sizes.
- **Investigate:** Badge placement, width constraints, priority, icon positioning, responsive card layouts and whether the status should be a compact icon or another treatment.
- **Design direction:** Avoid long, truncated toast-like labels over posters. Consider a compact, consistent status treatment with accessible explanation.

#### B-033: Orphaned favourites remain after their source is removed
- **Priority:** Medium | Android, potentially all platforms
- **Evidence:** Screenshots supplied on 18 November.
- **Details:** Jungle Book remains in Favorites after its original demo library/source was disconnected and removed. Selecting it opens "This is no longer in your library".
- **Expected:** A favourite whose item is no longer available should have a clear management path. The user must be able to remove the stale favourite without reconnecting the old source.
- **Investigate:** Favourite persistence, source removal, item identity, tombstones/orphan records, stale UI cache and deletion semantics.
- **Important:** Do not silently delete user favourites merely because a source is temporarily unavailable. Distinguish temporary disconnection from permanent source removal and provide explicit cleanup.
- *Status: Patched, tests passing. The favourite is kept and marked unavailable; its page offers "Remove from favorites" without the source. Outstanding: an Android device walk of the reported case.*

#### B-034: Mobile search placeholder is too long and visually dominant
- **Priority:** Low–Medium | Android phone/tablet
- **Details:** Placeholder "Search titles, people, genres, or…" is too long, too large, bold and prominent.
- **Desired:** shorten it (preferred over a marquee); reduce size and weight; a faded colour; entered text visually distinct from the placeholder.

#### B-035: Mobile search suggestion chips are oversized and unnecessarily labelled
- **Priority:** Low–Medium | Android phone/tablet
- **Details:** Suggested query chips are large, and the row begins with an unnecessary "Try" label.
- **Desired:** remove "Try"; smaller chips; on mobile, an edge-to-edge chip row; comfortable touch targets and horizontal scrolling kept.

#### B-036: Mobile Search layout and navigation break when the keyboard opens/closes
- **Priority:** Medium | Android phone/tablet
- **Details:** entering Search shifts the UI as the keyboard appears; no obvious in-app back button; after dismissing the keyboard the bottom navigation bar disappears; recovery needs the Android back gesture.
- **Expected:** Search a stable destination; predictable resize; navigation available (or an intentional replacement) with the keyboard hidden; Back tells dismissing the keyboard from leaving Search; no layout jumps or lost query.
- **Investigate:** Compose WindowInsets, IME animation, `adjustResize`, navigation scaffold visibility and focus state.

#### B-037: Search has no designed empty state
- **Priority:** Low–Medium | All platforms, especially mobile
- **Details:** No-results state is plain text ("Nothing in your library matches 'abc'.") with a large empty area.
- **Expected:** a deliberate empty state: a visual, a concise explanation, a useful next action; consistent across Search, Movies, Shows, filters, and source states; lightweight.

#### B-038: Movies and Shows lack sorting and filtering controls
- **Priority:** Medium, product requirement | All platforms
- **Expected:** a discoverable sort/filter entry point; sorting by title, release year, date added, rating, runtime, and other supported metadata; filters by genre, year, watched, favourites, resolution, source/library, and other meaningful fields; no unsupported or misleading filters; predictable state; obvious clearing.
- **Investigate:** Existing query capabilities, metadata completeness, cross-platform UI and large-library performance.

#### B-039: Controller button mapping needs a deliberate design
- **Priority:** Medium, UX requirement
- **Current:** A selects; B goes back; Y opens Sources; X does nothing; LB/RB and LT/RT have no clearly useful behaviour.
- **Proposed direction:** Y opens the sidebar immediately from anywhere; X a distinct useful action; LB/RB section or page navigation; LT/RT playback or consistent contextual actions.
- **Requirements:** no conflicts with platform conventions, remotes, or player shortcuts; discoverable hints; consider configurable mappings.
- **Status (original):** Proposed interaction design, not an approved mapping specification.

#### B-040: Home recommendations and discovery sections need more variety
- **Priority:** Low–Medium, product improvement | Home
- **Investigate:** deterministic sections from existing metadata and watch state (Because you liked…, similar genres, unwatched favourites, recently added, hidden gems, Continue Watching, Next Up, directors, actors, franchises), no opaque or random results.
- **Requirements:** no redundant or empty rows, no excessive scrolling; respect the active profile and its privacy boundary.
- **Status (original):** Improvement request, not a confirmed defect.

#### B-041: Cross-platform performance audit
- **Priority:** High, V1 readiness requirement
- **Platforms:** Android phone, Android tablet, Android TV/Google TV, Fire TV, Windows and Linux.
- **Areas to measure:** cold and warm startup; first usable screen; first library content; large-library scan and metadata; search latency; artwork loading, caching, memory; scrolling and frame pacing; navigation transitions; playback startup, buffering, seeking, recovery; CPU, memory, GPU, battery; background work and shutdown; long-session stability and memory growth.
- **Test matrix:** physical devices, representative desktop hardware, densities and resolutions, large libraries, connected and disconnected sources.
- **Expected output:** measured baseline, bottleneck evidence, platform findings, regression thresholds. Do not optimise on assumptions or one device. **Compare release builds**, not only debug builds.
- *Status: Investigating; Phase 1 baseline partial (VERIFICATION.md, "Phase 1"): Windows packaged start, memory, and shutdown, before and after (one machine); Android release cold start on the Phone 2a, Pad 7, Fire TV Stick 4K Plus, and the Google TV emulator (store build, pre-Phase-1 code); a JVM search fixture. Missing: an Android release build of later code, Linux on hardware, frame pacing, GPU and battery, large-library scans on devices, playback timings, long sessions, regression thresholds. Phase 3 completes it.*

#### B-042: Controller focus and pointer hover conflict on media cards
- **Severity:** Major (master prompt, 2026-11-19)
- **Details:** When browsing movie cards with a controller, the focused card expands into its highlighted state. If the pointer rests near the edge of another card when controller focus moves, pointer hover can activate the previously focused card, competing with controller focus; the visual state changes repeatedly and flickers.
- **Expected:** controller focus and pointer hover never fight over a card's state; moving focus never makes the previous card flicker; mouse hover works normally when the mouse is in use; switching between mouse and controller is predictable; controller navigation and focus accessibility preserved. Test controller-only use and simultaneous mouse and controller input. Investigate the interaction architecture; do not globally disable hover.
- *Phase 2, with B-025 (see the reconciliation table).*

## The register: features

| ID | Feature | Phase | Default | Status |
| --- | --- | --- | --- | --- |
| F-001 | Interface haptics (phone, tablet) | 2 | **Off**: explicit opt-in | Not started |
| F-002 | Controller rumble | 2 | **On** on compatible hardware; toggle in Settings | Not started |
| F-003 | Video haptics | 3 | **Off**, independent of interface haptics; **off on controllers** until explicitly enabled | Not started |
| F-004 | Privacy-respecting analytics and diagnostics | 1 (foundation) + 3 (verification) | **Off**: explicit informed consent | Foundation patched, tests passing; nothing transmitted |
| F-005 | Controller mapping customisation | **unallocated** (overlaps B-039) | — | Future design proposal |
| F-P1 | Cross-platform collapsible sidebar (master prompt's "F-005") | owner to confirm (Phase 3 in the master prompt) | Expanded or collapsed, per form factor | Not started |
| F-P2 | Controller sidebar focus toggle with Y (master prompt's "F-006") | owner to confirm (with F-P1) | — | Not started |

### Original proposals (findings document)

- **F-001 Optional interface haptics** (research proposal, not approved): feedback for focus movement, selection, confirmation, dialogs, navigation boundaries, errors; subtle and restrained; never on every animation frame; platform APIs with capability-aware fallbacks.
- **F-002 Optional controller rumble** (research proposal, not approved): compatible controllers' motors for interface confirmations and selected playback events; detect support and handle its absence gracefully.
- **F-003 Optional video-synchronised haptics** (experimental; not a V1 commitment): audio-derived haptics (low-frequency energy, transients), dedicated haptic tracks, or platform audio-coupled haptics; explicit opt-in, intensity limits, cooldowns, pause and mute behaviour, synchronisation, hardware detection; **volume alone must not determine intensity**; research Android HapticGenerator, controller vibration APIs, media haptic formats.
- **F-004 Privacy-respecting analytics and diagnostics** (research and product decision required): crash reporting, performance metrics, anonymous aggregate usage, user-submitted reports, opt-in beta diagnostics, each investigated separately; start with local logs and user-submitted reports; never advertising trackers, cross-app tracking, fingerprinting, personal media information, filenames, paths, server addresses, credentials, profile names, device identifiers, or watch history; documented purpose, minimal fields, retention, clear controls, accurate disclosures; assess any SDK's dependencies, network behaviour, and Data safety implications first; never a prerequisite for using Reflux.
- **F-005 Controller mapping customisation** (future design proposal): configurable A/B/X/Y, bumpers, triggers, sticks; platform expectations and sensible defaults kept; no conflicts with playback or navigation contexts.
- **F-P1 Collapsible sidebar** (master prompt): expanded (icon and label) and collapsed (icons only); cross-platform and accessible; desktop, phone, tablet, Android TV, and Fire TV considered separately; preference persisted where appropriate; transitions tested; no one behaviour or size forced on every form factor.
- **F-P2 Sidebar focus toggle** (master prompt): with a controller, Y moves focus to the sidebar and Y again returns it to the exact previous item, selection and scroll kept, repeated toggles not resetting state, no shortcut conflicts, an unavailable previous item handled; developed and tested with F-P1 (and to be reconciled with B-039, whose Y direction is the same).

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
6. This register reconciled: every B and F item with a status and evidence, nothing silently dropped.
7. No open release-blocking bug.
8. The full application checks green locally and on hosted CI.
9. Cross-platform validation on the matrix's devices.
10. The owner's review and approval.
11. The owner's authorisation of the version and build (VERSIONING.md).
12. Release notes, from CHANGELOG.md's Unreleased.
13. The build and its distribution prepared through the release workflow (RELEASE.md).
