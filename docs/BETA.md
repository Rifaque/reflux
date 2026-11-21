# Reflux Beta Roadmap

The beta programme from `0.9.0-beta.1` to the release candidate: what each beta contains, its gate, its known issues, and what 1.0 still needs from it. The product plan after 1.0 is [ROADMAP.md](ROADMAP.md); each issue's full record (report, investigation, fix, tests, evidence) is the [tracker](planning/BETA-BUG-TRACKER.md); what testers read is [BETA-NOTES.md](BETA-NOTES.md). Statuses live only in the tracker; this file links to them.

Written in Phase 4 (2026-11-21). It replaces [planning/BETA-2.md](history/phases/BETA-2-PHASES.md) (archived unchanged, with the three phases' work orders, exit criteria and the Phase 3 closure record) as the current account of the beta.

## Builds

| Build | Contents | State |
| --- | --- | --- |
| `0.9.0-beta.1` (`versionCode` 90031, tag `v0.9.0-beta.1`, 2026-11-16) | M8 to M11: profiles, the navigation's final structure, Player 2.0, the mini-player, distribution | **In testing**: Play closed testing (sent for review 2026-11-17), Amazon Live App Test 1 (submitted 2026-11-17), GitHub **pre-release** (Windows zip, Linux tarball). Testers: the owner only. Play's 14-day clock not started |
| `0.9.0-beta.2` (`versionCode` 90032) | Beta 2's three phases (below) | **Approved and versioned (`ecce561`), not released: held on hosted CI** ([B-076](planning/BETA-BUG-TRACKER.md#b-076)); no tag, no publication |
| `0.9.0-beta.3` | The [Beta 3 backlog](planning/BETA-BUG-TRACKER.md#beta-3-backlog-from-phase-3s-closure-2026-11-20) and tester reports | Not started |
| `0.9.0-rc.N` | Feature freeze, blocker fixes only, store submissions | Not started |

## Beta 2: scope (frozen since 2026-11-20)

Three phases, each accepted by the owner: **Phase 1** (accepted 2026-11-19) reliability, security, data integrity, telemetry consent groundwork, performance baselines; **Phase 2** (accepted and closed 2026-11-20) controller, focus, interaction, text input, haptics; **Phase 3** (accepted and formally closed 2026-11-20) media experience, Trickplay, Quick View, the featured hero, Atmosphere, the adaptive keyboard, profile entry. Work orders, exit criteria and closure records: [history/phases/BETA-2-PHASES.md](history/phases/BETA-2-PHASES.md), [PHASE-1-PLAN.md](history/phases/PHASE-1-PLAN.md), [PHASE-2-PLAN.md](history/phases/PHASE-2-PLAN.md).

**The freeze.** Beta 2's fix scope is closed. Anything found from now on goes to the Beta 3 backlog unless the owner authorises a critical Beta 2 blocker. Phase 4 (documentation, roadmap, store assets, website, release infrastructure) changes no product behaviour.

**What testers get** (by area; the user-facing list is CHANGELOG.md's Unreleased section, and the tracker IDs behind each are in the tracker's [phase overview](planning/BETA-BUG-TRACKER.md#phase-overview)):

| Area | In Beta 2 |
| --- | --- |
| Playback | Trickplay scene previews (Jellyfin, Plex); right-stick scenes; the Android frozen-frame fix; Linux under any numeric locale and on Wayland; multi-monitor controls on Windows; truthful resolution and HDR |
| Controllers and navigation | One controller contract across the app; Media Quick View; Y to the navigation; LT hides hints; the mini-player's RT; Home and See all focus fixes; rumble on, touch haptics off by default |
| Search and typing | Redesigned Search; Reflux's adaptive keyboard (controller grid, desktop layout following the system's); title completions; Backspace never navigates |
| Home | An intentional featured title; pull to refresh; much faster search on large libraries |
| Profiles and privacy | Last-used profile; Switch profile and Log out; PIN-protected deletion; Forgot PIN; statistics validated off |
| Sources and stability | One Jellyfin server at several addresses; orphaned favourites removable; the Windows controller-reconnect crash fixed; the Linux libmpv hint |

**Deferred out of Beta 2, by the owner:** F-003 video haptics (no release); B-056 (a later beta); B-027, B-038, B-040 (V1.1, ROADMAP.md). F-005 and F-P1 are unassigned (ROADMAP.md suggests V1.5 and V1.1).

## The Beta 2 gate

Beta 2 (`0.9.0-beta.2`, VERSIONING.md) is built only when every step holds **and the owner approves it**. State on 2026-11-21 (evidence: [planning/PHASE-4.md](planning/PHASE-4.md), "Beta 2 readiness"):

| # | Step | State |
| --- | --- | --- |
| 1–5 | Phases 1, 2 and 3 implemented, tested, platform-verified, and accepted | **Done** (owner acceptance 2026-11-19 and 2026-11-20) |
| 6 | The tracker reconciled: every item with a status and evidence | **Done** at Phase 3's closure; re-checked in Phase 4 |
| 7 | No open release-blocking bug | **Holds**: no blocker open; B-061 (Skia fallback crash) is Investigating, a Beta 3 candidate |
| 8 | The full checks green locally and on hosted CI | **B-076 fixed 2026-11-22** (local full check 1,651 tests, 0 failures; hosted CI in VERIFICATION.md, "B-076 fix"). Before: **blocked by B-076.** Local full check green on `ecce561` (1,650 tests, 0 failures); hosted CI run 44912262295: every job green except Check (ubuntu-24.04), one test failing on both attempts |
| 9 | Cross-platform validation on the matrix's devices | **Done with the accepted limitations** (physical Linux, Steam, the Phone 2a after the final changes) |
| 10 | The owner's review and approval | **Done**: approved 2026-11-21 (DECISIONS.md, "Beta 2 release") |
| 11 | The owner's authorisation of the version and build | **Done**: `0.9.0-beta.2` authorised 2026-11-21 |
| 12 | Release notes from CHANGELOG.md's Unreleased | **Prepared** ([BETA-NOTES.md](BETA-NOTES.md); CHANGELOG.md grouped) |
| 13 | The build and its distribution through the release workflow | **Prepared**: the workflow, store assets and website are ready; nothing run for release (RELEASE.md, "Releasing a version") |

## Known issues carried to Beta 3

Each is in the tracker with its evidence. User-visible ones are listed for testers in BETA-NOTES.md.

| ID | What | Status |
| --- | --- | --- |
| B-061 | A Skia crash after a Direct3D failure on Windows (upstream, Skiko) | Investigating |
| B-074 | Two `SourcesUiTest` cases time out under the full test run (pass alone) | Investigating (test reliability) |
| B-044 | Two intermittent tests mixing the test clock and the wall clock | Investigating (test infrastructure) |
| B-056 | A brief blank frame when the mini-player closes | Deferred by the owner |
| B-022 | Windows start-up slower than Android: measured, no optimisation justified yet | Investigating |
| B-063 | Linux display scale: verified in a VM; KDE's "scaled by the system" and mixed-scale monitors not followed | Fixed, with accepted limits |
| B-070 | Steam library artwork made, never applied in Steam | Fixed, unverified in Steam |

## From Beta 3 to 1.0

What the beta still has to prove for the [V1.0 criteria](ROADMAP.md#v10-criteria), in order:

1. **Testers on real hardware** (the size target: 15–20 testers, at least 3 on Google TV, 3 on Fire TV, 2 with controllers). The project holds no physical Google TV device and one Fire TV stick.
2. **Play's production access:** 12 testers opted in for 14 continuous days on an approved closed-track build ([Beta operations](#beta-operations)).
3. **An upgrade test** from one beta to the next keeping library, profiles, and downloads (criterion 11): first possible with Beta 2 over Beta 1.
4. **Platform decisions:** G-WIN (Windows signing and installer), the Linux formats, Watch Next certification.
5. **The release candidate:** feature freeze, store reviews, a targeted re-qualification, the website's 1.0 state.

**Exit:** no open blocker or critical report, the V1.0 criteria hold, and the last beta ran a week without a new critical report.

## Beta operations

Carried from M12's preparation ([history/milestones/LAUNCH.md](history/milestones/LAUNCH.md), "Beta operations", which keeps the full texts).

- **Channels:** Play closed testing (testers' Google accounts; the opt-in link appears on the website only once `release/stores.json` records it); Amazon Live App Testing (testers' Amazon e-mails; installed through Appstore Beta Hub, `release/amazon-appstore.md`); Windows and Linux from the website's Download page, from the verified release record only.
- **Feedback:** one route, support@rsxdevelops.dev, with the device, the build (Settings → About Reflux) and the support report; GitHub issues stay open but are public and secondary.
- **Triage:** *blocker* (data loss, a crash on launch, playback impossible on a supported target, a privacy breach) → fixed before the next build; *critical* (a main flow broken for a form factor, source, or input) → fixed in the cycle; *normal* → a known issue, fixed when it fits; *later* → ROADMAP.md. Each report gets a level, a device and build, and a link to its fix or known-issue entry.
- **The Play clock:** starts when an owner-approved build is on the closed track and 12 testers are opted in (counted from the console, never from replies); recruit 15 or more so a drop-out does not reset it.
- **Release notes per build:** the version's CHANGELOG.md section through `release.py notes` (both stores' "What's new" and the GitHub draft); BETA-NOTES.md adds highlights, known issues, and what to test.
- **A new beta** is cut only when permitted fixes justify one (a blocker, a crash or data loss, security or privacy, a critical playback failure, a serious compatibility defect), never for a cosmetic issue.

## Beta 2 release attempt (2026-11-21): held

The owner approved Beta 2 and authorised `0.9.0-beta.2` (DECISIONS.md, "Beta 2 release"). Done: the Fire TV listing captured on the physical stick; the store media moved to a release archive; the promotion commit `ecce561` (`release: 0.9.0-beta.2`; the build derives `versionCode` 90032, Windows 0.9.32, Linux 0.9.0~beta.2); the branch pushed; the local full check green. **Held** because hosted CI's Linux job fails `DownloadsUiTest` deterministically: the first Right after arriving on the Downloads page is lost on Linux timing ([B-076](planning/BETA-BUG-TRACKER.md#b-076), bisected to Phase 2's `77b994c`). The owner chose a scoped fix before release; the investigation narrowed it but no fix held, so none was kept. **Not done:** the tag, the release workflow, the GitHub pre-release, the Play and Amazon uploads, the website's deployment.

**To resume:** fix B-076 (its entry says where to look next), commit, push, and see hosted CI green on every job; then, because a version once tagged is never rebuilt with other contents, the fix goes before the tag on top of `ecce561` (still `0.9.0-beta.2`, since nothing was given to anyone), and the release follows RELEASE.md, "Releasing a version".
