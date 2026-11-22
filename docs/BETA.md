# Reflux Beta Roadmap

The beta programme from `0.9.0-beta.1` to the release candidate: what each beta contains, its gate, its known issues, and what 1.0 still needs from it. The product plan after 1.0 is [ROADMAP.md](ROADMAP.md); each issue's full record (report, investigation, fix, tests, evidence) is the [tracker](planning/BETA-BUG-TRACKER.md); what testers read is [BETA-NOTES.md](BETA-NOTES.md). Statuses live only in the tracker; this file links to them.

Written in Phase 4 (2026-11-21). It replaces [planning/BETA-2.md](history/phases/BETA-2-PHASES.md) (archived unchanged, with the three phases' work orders, exit criteria and the Phase 3 closure record) as the current account of the beta.

## Builds

| Build | Contents | State |
| --- | --- | --- |
| `0.9.0-beta.1` (`versionCode` 90031, tag `v0.9.0-beta.1`, 2026-11-16) | M8 to M11: profiles, the navigation's final structure, Player 2.0, the mini-player, distribution | **In testing**: Play closed testing (sent for review 2026-11-17), Amazon Live App Test 1 (submitted 2026-11-17), GitHub **pre-release** (Windows zip, Linux tarball). Testers: the owner only. Play's 14-day clock not started |
| `0.9.0-beta.2` (`versionCode` 90032, tag `v0.9.0-beta.2` on `ff98a0d`, 2026-11-22) | Beta 2's three phases (below) and the release fixes B-076, B-078, B-079 | **In testing since 2026-11-22**: GitHub **pre-release** (desktop); Amazon Live App Test 1 (**Test in-progress**); Google Play closed testing: **upload pending (the owner)**. See [Beta 2 testing](#beta-2-testing-from-2026-11-22) |
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
| 8 | The full checks green locally and on hosted CI | **Done 2026-11-22:** hosted CI run 44994139313 on `ff98a0d` green on every job; local full check 1,651 tests, 0 failures. **B-079: Linux's limit raised to 75 minutes by the owner's decision** (parallel UI tests tried and reverted as less reliable); hosted CI in VERIFICATION.md. B-076 and B-078 fixed; hosted Linux "Build and test" green twice. **B-076 fixed 2026-11-22** (local full check 1,651 tests, 0 failures; hosted CI in VERIFICATION.md, "B-076 fix"). Before: **blocked by B-076.** Local full check green on `ecce561` (1,650 tests, 0 failures); hosted CI run 44912262295: every job green except Check (ubuntu-24.04), one test failing on both attempts |
| 9 | Cross-platform validation on the matrix's devices | **Done with the accepted limitations** (physical Linux, Steam, the Phone 2a after the final changes) |
| 10 | The owner's review and approval | **Done**: approved 2026-11-21 (DECISIONS.md, "Beta 2 release") |
| 11 | The owner's authorisation of the version and build | **Done**: `0.9.0-beta.2` authorised 2026-11-21 |
| 12 | Release notes from CHANGELOG.md's Unreleased | **Prepared** ([BETA-NOTES.md](BETA-NOTES.md); CHANGELOG.md grouped) |
| 13 | The build and its distribution through the release workflow | **Done 2026-11-22** (release run 44999770048; [Beta 2 release](#beta-2-release-2026-11-22)), apart from the Play upload (the owner's) |

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
| B-077 | `MpvEngineTest`'s credentials-log test can race its log thread (hosted Linux, once; test only) | Open |
| B-080 | The live-Jellyfin test's remux case can find the server's session unconfirmed (local only) | Open |
| B-081 | `FirstRunTest` once found the first folder empty (hosted Linux window tests) | Open |

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

## Beta 2 release (2026-11-22)

What was done, under the owner's authorisations of 2026-11-21 and 2026-11-22 (DECISIONS.md, "Beta 2 release"; the mission's messages):

- **Fixes before the tag** (the owner's choice: fix, then release): B-076 (a focus fix with its regression test), B-078 (three real-window tests brought up to Phase 2), B-079 (Linux CI's limit 75 minutes after a measured investigation; parallel test JVMs tried and reverted). Hosted CI run 44994139313 on `ff98a0d`: **every job green**.
- **`main`**: fast-forwarded `044c8f0..ff98a0d` (no merge commit, no force), as the release workflow requires.
- **Tag** `v0.9.0-beta.2` (annotated) on `ff98a0d`. **Release workflow** run 44999770048: green on all five jobs, the draft verified independently on another OS.
- **Checked by hand:** `release.py verify` on the full artifact set (5) and on the public draft (3, `--public`); `SHA256SUMS`; `apksigner`: the APK's signer `288c3810…3b1370`, equal to `REFLUX_UPLOAD_CERT_SHA256`; `aapt2`: `dev.rsxdevelops.reflux`, `versionCode` 90032, `0.9.0-beta.2`; `gh attestation verify` on all five artifacts, built by `release.yml` at `refs/tags/v0.9.0-beta.2`. No APK or AAB in the public release.
- **GitHub:** published as a **pre-release**, not latest (no stable release exists), immutable: <https://github.com/Rifaque/reflux/releases/tag/v0.9.0-beta.2>. Publication record `release/published/0.9.0-beta.2.json` (identical to the workflow's `published-record`).
- **Amazon:** Live App Test 1, upcoming version `0.9.0-beta.2`: the APK (90032), Fire TV only (83 devices; no tablets, no Automotive), DRM **No** (the console now asks; Beta 1 had none recorded), the tester group "Reflux", the five Fire TV screenshots and the background captured on the physical stick. **Submitted 2026-11-22** ("Test submitted"); Beta 1 stays in progress until Amazon processes it.
- **Google Play: not uploaded.** The AAB is 11.9 MB and the browser upload available to the agent is limited to 10 MB; there are no Play API credentials. **Owner step:** Play Console → Reflux → Test and release → Closed testing - Alpha → Create new release → upload `reflux-0.9.0-beta.2-android.aab` from release run 44999770048's `release` artifact (kept 14 days) → release notes: `release-notes` artifact, `store-whats-new.txt` → Next → Save and publish (to the closed track only; never production).
- **Website:** `main` fast-forwarded to `b7d5ba2` (the record) after CI was green; Vercel deployed it, and the live Download, testing and install pages name Beta 2 (VERIFICATION.md, "Beta 2 release").

## Beta 2 testing (from 2026-11-22)

**Beta 2 testing is active** on the desktop and on Fire TV from 2026-11-22; on Android, Google TV and Android TV it starts when the owner uploads the bundle to Play (below). Checked in the consoles on 2026-11-22:

| Channel | State | Testers | How testers get it |
| --- | --- | --- | --- |
| GitHub pre-release (Windows, Linux) | Published, immutable | Anyone with the link | <https://reflux.rsxdevelops.dev/download> |
| Amazon Live App Test 1 (Fire TV) | `0.9.0-beta.2` **Test in-progress** (Beta 1 no longer listed); 83 Fire TV devices; DRM No (the console reports no Amazon DRM integration in the APK) | Group "Reflux", 1 tester | Appstore Beta Hub / the LAT invitation; Amazon offers the update to accepted testers |
| Google Play, Closed testing - Alpha | **Still `0.9.0-beta.1` (90031), "Available to selected testers"**; no Beta 2 release, no draft | E-mail list "Reflux Beta", 12 users; feedback to support@rsxdevelops.dev | Opt-in <https://play.google.com/apps/testing/dev.rsxdevelops.reflux>; Beta 2 arrives as an update once uploaded and through Google's review |

**Google Play (owner step).** The agent's browser upload is capped at 10.4 MB a call and the AAB is 11.9 MB (tried 2026-11-22: refused before upload; the empty draft discarded, the track left as it was). Upload the verified bundle from `Documents/Reflux-release-archives/0.9.0-beta.2/release/reflux-0.9.0-beta.2-android.aab` (or run 44999770048's `release` artifact until 2026-12-06): Closed testing - Alpha → Create new release → Upload → release name `90032 (0.9.0-beta.2)` → release notes below → Next → review warnings → Save and publish to the closed track only. Then check the release reads "In review", and later "Available to selected testers".

Release notes for Play (en-US, 420 characters of Play's 500; `store-whats-new.txt` covers only the first two changelog items, so these summarise Beta 2 instead, from CHANGELOG.md):

```
<en-US>
Beta 2:
• Scene previews on the timeline where your Jellyfin (10.9+) or Plex server makes them; the right stick steps through them.
• One controller layout everywhere: X opens a quick view, Y the navigation, LT hides hints.
• Redesigned Search with suggestions from your library and real filters.
• Reflux's own keyboard for remotes and controllers.
• Many focus, navigation and playback fixes on TV, phones and tablets.
</en-US>
```

**Tester announcement.** No e-mail or messaging channel is connected to the agent, so it was **not sent**; the owner sends it (Play's list "Reflux Beta" and the Amazon "Reflux" group, by blind copy so no tester sees another's address; never publicly):

> **Reflux Beta 2 is out! 🚀**
>
> Reflux Beta 2 is now available for testing.
>
> What's new: scene previews on the timeline; consistent controller navigation with Quick View on X; a redesigned Search; Reflux's own keyboard for remotes and controllers.
>
> Get the update: Windows & Linux: https://reflux.rsxdevelops.dev/download. Fire TV: update through your Amazon Live App Test invitation. Android: update through your Google Play testing invitation once Beta 2 is available on the closed testing track.
>
> Please report bugs, unexpected behaviour, or anything that feels off through Settings → About. Feedback from real devices is especially valuable during this beta. Thank you for testing Reflux and helping RSX Develops make it better.

**During Beta 2 testing.** Reports come to support@rsxdevelops.dev (Settings → About Reflux has the support report); each becomes a tracker entry with the next free ID, its device and build, and whether it reproduces. Triage as in "Beta operations": blockers before any new build, criticals in the cycle, the rest to the Beta 3 backlog. No features during testing; Beta 3 is not authorised by this. The known issues above (B-077, B-080, B-081 among them) stay open and do not block testing unless new evidence makes one release-critical.

**Release artifacts preserved** in `Documents/Reflux-release-archives/0.9.0-beta.2/` (local, outside the repository): the `release` artifact (APK, AAB, Windows zip, Linux tarball, libmpv source, `SHA256SUMS`, manifest, provenance), `release-notes`, `published-record`, and the `linux` and `libmpv` build intermediates; 18 files, 462 MB, with `README.txt` and `ARCHIVE-SHA256SUMS` (VERIFICATION.md, "Beta 2 testing activation").
