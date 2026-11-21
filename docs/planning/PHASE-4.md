# Phase 4: consolidation, product direction, store assets, website, release infrastructure

The execution record for Phase 4 (the owner's master prompt of 2026-11-21, "Reflux | Phase 4 Autonomous Mission", from the brief in [history/phases/PHASE-4-HANDOFF.md](../history/phases/PHASE-4-HANDOFF.md)): what was done in each workstream, its evidence, what is left, and the Beta 2 readiness assessment. Decisions: [DECISIONS.md](../DECISIONS.md), 2026-11-21. Runs: [VERIFICATION.md](../VERIFICATION.md), "Phase 4".

**Phase 4 authorises no release.** Nothing was pushed, merged, tagged, deployed, uploaded to a store, submitted, or announced. Beta 2's scope stayed frozen: no product behaviour changed.

## Baseline (P0, 2026-11-21)

| Item | State |
| --- | --- |
| Repository | `Rifaque/reflux`, branch `open-source-gpl` |
| HEAD at start | `3aa5964` "docs: Phase 3 accepted and formally closed by the owner" (the expected closure commit) |
| Working tree at start | Clean: no tracked changes, no untracked files; nothing of the owner's to preserve |
| Devices | None attached to adb (Pad 7, Phone 2a, Fire TV absent); no game running before each heavy run |
| Hosted CI | Not observable: the branch is local only (never pushed), and the GitHub connector failed to connect this session |
| Website | Live at reflux.rsxdevelops.dev from `main` (the M11/Beta 1 content); not changed there |
| Stores | Play closed testing (Beta 1, sent for review 2026-11-17); Amazon Live App Test 1 (submitted 2026-11-17); GitHub `v0.9.0-beta.1` pre-release |
| Store screenshots | `design/brand/store/screenshots/` (12 raw captures of Beta 1, 2026-11-16); audited in the store-assets design system |

**Fresh `./gradlew check`** (14:20–14:58 IST, Windows 11, JDK by toolchain, live Jellyfin env present): **exit 0**. Executed in this run: `:ui:jvmTest` 548 tests, 0 failures, 1 skipped (`SourcesUiTest` included: **B-074 did not reproduce**); `:desktop:app:test` 77 (15 skipped: window tests off); `:android:app:testDebugUnitTest` 56; Android lint; `verifySharedSources`. Up to date from 2026-11-20 (unchanged inputs, results still on disk, 0 failures): core 393, library 263, design 43, desktop engine 52, mpv 60, jellyfin 74, plex 25, trakt 23, smb 10, webdav 20, local 6. The historical 1,647-test run and Phase 3's 941/2 are history, not this result. The end-of-phase full rerun is under [Verification](#verification).

## Workstreams

| WS | What | State | Evidence |
| --- | --- | --- | --- |
| P0 | Baseline and health | Done | Above |
| P1 | Documentation audit and consolidation | Done | [P1](#p1-documentation) |
| P2 | Product roadmap and versions | Done | [ROADMAP.md](../ROADMAP.md); [P2](#p2-roadmap) |
| P3 | LaunchFrame and store screenshots | Done, Fire TV provenance pending the owner | [store-assets/release-0.9.0-beta.2](../../store-assets/release-0.9.0-beta.2/README.md); [P3](#p3-store-assets) |
| P4 | Website overhaul | Done, not deployed | [P4](#p4-website) |
| P5 | Distribution | Rehearsed locally; owner steps remain | [P5](#p5-distribution) |
| P6 | Beta 2 readiness | Assessed: ready with known risks | [Beta 2 readiness](#beta-2-readiness) |
| P7 | Final review and closure | See [Closure](#closure) | |

## P1 documentation

- **Inventory and audit** (41 Markdown documents in `docs/`, `docs/planning/`, `release/`, the root). Authoritative and current, kept: PRODUCT, ARCHITECTURE, PLAYBACK, INPUT_MODEL, NAVIGATION, METADATA, WATCH_STATE, OFFLINE, DESIGN, PRIVACY, THIRD-PARTY, VERSIONING, WEBSITE, DECISIONS (append-only), the tracker, V1.1 research, the release runbooks. Current but revised: README, AGENTS, CLAUDE, RELEASE (the unified process and platform matrix added), WEBSITE, VERSIONING (changelog categories), CHANGELOG (every release categorised). Historical, archived unchanged: the M0–M13 roadmap, LAUNCH, the M8–M13 rebaseline, the M10 and M11 plans, BETA-2 (with the Phase 3 closure record), the Phase 1 and 2 plans, the F-P4 plan, the Phase 4 handoff, and VERIFICATION's sections before Beta 2. Conflicting or misleading, fixed: ROADMAP's "Current state" (M8–M11 language after the beta had moved to phases); the website's "1.0 planned for December"; CLAUDE.md pointing at Beta 2's plans as current.
- **Structure:** `docs/README.md` (reading guide; which document wins); `docs/ROADMAP.md` (product); `docs/BETA.md` (beta roadmap); `docs/BETA-NOTES.md` (tester notes); `docs/history/` with `README.md` (the legacy index). Moves by `git mv`, so history follows.
- **Links:** `tools/docs/check_links.py` (new; relative links and `#anchors`, GitHub slugs and explicit anchors): 341 links before, 0 broken; after: 460, 0 broken. In CI. Code comments citing moved specifications were updated (comment-only changes in 20 files).

## P2 roadmap

[ROADMAP.md](../ROADMAP.md): where Reflux is; the platforms; the path (alpha, Beta 1, Beta 2 current, Beta 3 and RC, V1.0, V1.1, V1.2, V1.3, V1.5, V2.0) with themes and statuses (Completed, Current, Committed, Planned, Under consideration, Long-term vision); V1.0's 17 criteria and today's gaps against them; for each later version, initiatives with user problem, outcome, priority, dependencies, platforms, risk and status; F-003 unscheduled by the owner's decision; not planned; ecosystem notes (2026-11-21); the public roadmap's source (`website/src/roadmap.js`). Rationale: DECISIONS.md, 2026-11-21.

## P3 store assets

- **LaunchFrame:** `claude plugin marketplace add Rifaque/launch-frame` and `claude plugin install launch-frame@launch-frame` (the CLI form of the documented `/plugin` commands; 2.0.0, user scope, enabled). The skill became available as `launch-frame:launch-frame` and was loaded and followed (its SKILL.md and the direction, production, copy and quality-gate references read). Dependencies were present: Python 3.11.9, Pillow 12.3.0, Playwright, Chrome, the Android SDK and emulator.
- **Targets** (`discover_targets.py`): phone Confirmed; Android TV Confirmed; Fire TV Confirmed (derived); tablet Probable, confirmed at runtime and by `Layout.kt`'s form factors (forced with that evidence); foldable, Chromebook, Wear, Automotive, XR, Fire tablet not selected.
- **Official requirements rechecked 2026-11-21:** Play's preview-assets page (2–8 per type; tablets ≥ 4, 1,080–7,680 px, 16:9 or 9:16; a 2.3× ratio limit; feature graphic 1024 × 500; TV banner 1280 × 720 required; 4+ screenshots at ≥ 1080 px for promotion) and Amazon's listing page (last modified 2026-11-16: Fire TV screenshots 3–10 at 1920 × 1080, icon 1280 × 720, background 1920 × 1080 required).
- **Emulators:** `Reflux_Phone` 1080 × 2400 @ 420 dpi; `Reflux_Tablet7` (new) 1920 × 1200 @ 320 dpi = 960 × 600 dp, the tablet rail; `Reflux_Tablet` 2560 × 1600 @ 320 dpi (Pixel Tablet profile), the sidebar; `Reflux_TV` Google TV 1920 × 1080. Demo library: seven Blender open films (CC) on a throwaway local Jellyfin; debug build with no Trakt Client ID.
- **Produced:** 24 source captures (provenance in `captures.json`, 8 rejected captures recorded with reasons); Google Play phone 6, 7-inch 5, 10-inch 5, TV 6, feature graphic, TV banner; Amazon Fire TV 5, icon, a new logo-free background. Two review rounds (design-system.md's log). `validate_assets.py --final`: exit 0; warnings only for the 10-inch 16:9 size, explained.
- **Limits:** emulator captures only; Fire TV set from Google TV emulator captures (owner decides: re-capture on the stick, or accept); no Trickplay shot (no previews on the demo server).

## P4 website

- **Audit** (before): sound architecture (prerendered React, release data from the repository, strict CSP, checks for SEO, links and layout); weaknesses: a promised December 1.0, no features overview, no public roadmap, Releases ungrouped for older versions, Beta 1 images.
- **Changes:** Features page; Roadmap page with the reel (the easter egg); Home's roadmap teaser in place of the dated timeline; categorised Releases with a version index; new product images from the LaunchFrame captures; "Coming in Beta 2" labels driven by publication records; corrected alt text (Google TV emulator images no longer called Fire TV); Download's 1.0 sentence.
- **Checks (local, 2026-11-21):** `npm audit` 0 vulnerabilities; `npm test` 34/34; `npm run build`; `check-dist` 26 pages, 0 problems; `check-links` 27 pages, 31 external links, 0 broken; `check-layout --screenshots` 26 pages × 7 sizes, 0 problems (screenshots looked at: Home, Features, Roadmap, Releases at desktop and phone); `check-roadmap` 0 problems (no script, keyboard, Escape and focus return, reduced motion, no CSP error); all images load when scrolled.
- **Not deployed.** The site deploys from `main` (Vercel); it should go out with Beta 2's publication (DECISIONS.md, 2026-11-21).

## P5 distribution

- **Audit.** The release system built in M6–M11 is sound and was kept: `release.yml` rehearses every push (throwaway signing key, provenance verified, nothing kept) and, by hand with a tag, builds, signs, attests and drafts a release; workflow permissions are read-only by default with `contents: write` only in the draft job and `id-token`/`attestations` only where provenance is made; secrets are GitHub's encrypted secrets, never printed; publishing the draft and uploading to stores are people's steps. Versioning (VERSIONING.md) derives `versionCode`, the Windows and Linux package versions, and artifact names from `reflux.version` alone: unchanged in Phase 4.
- **Added:** the unified release process and the platform matrix (RELEASE.md, "Releasing a version"); the docs link check and the roadmap check in CI; B-075's two fixes to store "What's new" (49 release-tool tests).
- **Rehearsal (local, nothing published), on the committed tree, with the dry-run key** (`~/.reflux-release/dry-run.env`, certificate "Reflux internal dry run (not for distribution)"):
  - `release.py build` at `85d6d26`: APK, AAB, Windows zip, libmpv source, SHA256SUMS, manifest, provenance; `verify` (4 artifacts) and `stage-public` (2 public artifacts) pass.
  - Linux tarball in the CI container (`reflux-linux-check package`) at `1aa0ce8`: 92,587,050 bytes, `modified: false`; then `release.py build --add <tarball>` from a worktree at the same commit: 5 artifacts, `verify` passes; `stage-public`: the zip, the tarball and the libmpv source with the three records, **no APK or AAB** (store-only); `publish-record` into a scratch folder; the website built against that record shows the tarball with its size and SHA-256 and `check-dist` passes (rebuilt with the real data afterwards).
  - `release.py notes --version 0.9.0-beta.2 --allow-unreleased`: GitHub notes and store text (446/500 characters, first real change first).
- **Android:** APK `dev.rsxdevelops.reflux`, `versionCode` 90031, target SDK 36, leanback launchable, leanback not required, signed by the dry-run key (as a rehearsal must be). Play App Signing, the upload key, and console state are the owner's (`release/google-play.md`); not inspected this session (no browser session was driven on the owner's machine).
- **Fire TV:** the same APK identity; Amazon LAT and submission are the owner's (`release/amazon-appstore.md`); no device attached.
- **Windows:** the zip extracted cleanly to a scratch folder (276 files, `Reflux.exe`, bundled `libmpv-2.dll`, runtime, LICENSE and NOTICE), its SHA-256 matching SHA256SUMS; `Reflux.exe` and `libmpv-2.dll` are not code-signed (by decision until G-WIN); uninstall is deleting the folder (the library is in `%LOCALAPPDATA%\Reflux`). **Launch not run:** it would open a window on the owner's desktop; Beta 1's packaged app was accepted by the owner on Windows (2026-11-17).
- **Linux, clean containers** (Docker Desktop; containers, not a Linux desktop; the tarball from `1aa0ce8`, extracted as a user, started for 25 s under Xvfb with `de_DE.UTF-8`): Ubuntu 22.04 (glibc 2.35), Ubuntu 24.04 (2.39), Debian 12 (2.36), Fedora 41 (2.40): **started without libmpv and with it**; with it, the engine started, set the C numeric locale for libmpv while keeping de_DE, and probed decoders. Arch (glibc 2.44): started both ways once Mesa and fonts were present (a desktop has them; the base image does not); no locale data there. The libmpv install hint is shown in the app; it was not read from these runs. A first pass without a desktop's X11 libraries failed in AWT before Reflux's code, a test setup error, not counted.
- **Not done (owner or later):** a physical Linux desktop; Windows launch, upgrade over Beta 1 and SmartScreen on a clean user account; the consoles; G-WIN.

## Beta 2 readiness

**Assessment: Ready with known risks.** Every part of Beta 2 that can be prepared without the owner is prepared and checked; what remains is owner action (approval, version, push and hosted CI, publication) and the known risks below. This assessment is not permission to release.

| Category | State | Evidence |
| --- | --- | --- |
| Scope | Frozen since 2026-11-20; no product change in Phase 4 (only comments, docs, site, tooling) | Commits in [Closure](#closure); tracker |
| Phase acceptance | Phases 1–3 accepted by the owner | BETA.md, "The Beta 2 gate" 1–5 |
| Tracker | Reconciled; B-075 added and fixed; B-074 evidence added | Tracker summary |
| Tests (local) | Full `check` green at the start; the end-of-phase rerun under [Verification](#verification) | VERIFICATION.md, "Phase 4" |
| Hosted CI | **Not run**: the branch has never been pushed | Gate step 8, the owner's |
| Known issues | Disclosed: B-061, B-063 limits, B-056, B-022, B-070, B-074, B-044 | BETA.md, BETA-NOTES.md |
| Release notes | BETA-NOTES.md (testers); CHANGELOG Unreleased grouped; store text generated (446/500 characters) | P5 |
| Store artwork | Play and Amazon sets validated | P3 |
| Website | Overhauled, all checks pass, not deployed; Beta 2 features labelled until a Beta 2 record exists | P4 |
| Artifacts | Rehearsed: APK, AAB, Windows zip, Linux tarball, libmpv source, SHA256SUMS, manifest, provenance; verified; public set staged | P5 |
| Installation | Windows: clean extract and checksum; launch not run (it would open on the owner's screen). Linux: clean distributions in containers (P5). Android: unchanged identity and signer chain; Beta 1's install path stands | P5 |
| Versioning | `0.9.0-beta.1` unchanged; Beta 2 will be `0.9.0-beta.2` with the owner's authorisation | VERSIONING.md |
| Privacy and legal | Unchanged in Phase 4: statistics off and unsendable (F-004); GPL notices; Trakt attribution; site privacy page unchanged | PRIVACY.md |
| Release process | Documented end to end with owner gates | RELEASE.md, "Releasing a version" |

**Known risks:** B-061 (a Windows crash after a Direct3D failure, upstream; Investigating); no hosted CI on this branch yet; emulator-only store captures and a Fire TV set from Google TV emulator captures; physical Linux, Steam and a physical Google TV device still unverified; the Windows build unsigned (SmartScreen warning).

**Remaining gates (owner):** review and approve Beta 2 → push the branch and get hosted CI green (and merge per the open-source plan) → authorise `0.9.0-beta.2` (promotion commit and tag) → run the release workflow → publish the GitHub pre-release and write the publication record → upload to Play closed testing and Amazon LAT with the new artwork (decide the Fire TV provenance first) → deploy the website with the record → invite testers.

## Verification

New results only; the record is VERIFICATION.md, "Phase 4". Historical results (Phase 3's 941/2, the 1,647 run before B-071 to B-073) are not restated as current.

| Check | Result |
| --- | --- |
| `./gradlew check` at the start | Exit 0 (681 tests executed, 969 up to date with unchanged inputs; 0 failures) |
| `./gradlew check --rerun-tasks` at the end | 1,650 tests, 2 failures, 26 skipped; both failures explained and passing on rerun (a doc edit during the run; the live server's late session report) |
| B-074 (`SourcesUiTest`) | Not reproduced in either full run (548/548 both times); stays Investigating, Beta 3 |
| Release tools | 49 tests OK |
| Docs links | 477 links, 0 broken |
| Website | audit 0; tests 34/34; build; check-dist 0; check-links 0; check-layout 0 (26 × 7); check-roadmap 0 |
| Workflows, secrets | actionlint clean; gitleaks over 294 commits: no leaks |
| Store assets | `validate_assets.py --final` exit 0; `captures.py verify` OK |
| Release rehearsal | 5 artifacts verified; public set staged without APK/AAB; record → Download page |
| Linux containers | Ubuntu 22.04, 24.04, Debian 12, Fedora 41, Arch: start with and without libmpv |
| Hosted CI | **Not run** (branch not pushed) |

## Closure

**Phase 4 is complete, with owner-dependent items.** Every in-scope workstream was carried out and checked; what remains needs the owner's accounts, devices, or approval. **Phase 4 does not authorise any release:** Beta 2 still waits for the gate in [BETA.md](../BETA.md).

**Commits** (branch `open-source-gpl`, local, not pushed): `232ef76` documentation consolidation, roadmaps, beta documents, changelog categories, link check, B-075; `684158c` the website; `85d6d26` the store assets; `1aa0ce8` RELEASE.md's process and matrix, CLAUDE.md; `90a42da` B-075's second part; `5491309` README, CONTRIBUTING, readiness; then the P5 and closure records.

**Exit criteria against the evidence.**
- Documentation: audited, consolidated, authoritative sources named (docs/README.md), history preserved and indexed, links 0 broken, roadmaps written (post-alpha to V2.0, V1.0 criteria), Beta roadmap and notes written. **Done.**
- LaunchFrame and store assets: installed as a plugin, skill loaded and followed, targets discovered, old screenshots audited, official requirements read today, phone/7"/10"/TV emulators configured (one new), 24 authentic captures with provenance, storyboards and a campaign system, two review rounds, contact and store-scale sheets, validation passing, per-store upload instructions, Fire TV limits stated. **Done**; the Fire TV provenance choice is the owner's.
- Website: audited, Features and Roadmap pages, the easter egg, categorised releases, new images, privacy and legal unchanged and still accurate, responsive and reduced-motion checks, all checks green. **Done; not deployed.**
- Distribution: unified process documented, versioning reviewed (unchanged), CI improved (two checks), artifacts rehearsed for every platform, Linux installs checked in containers, Windows package checked without launching, download path proven against a record. **Done locally**; console, signing and publication steps are the owner's.
- Beta 2: scope frozen, fresh validation run, B-074 disposition recorded, known issues disclosed, notes ready, readiness assessed ("Ready with known risks"). Hosted CI and approval remain. **Done to the gate.**

**Owner-dependent (none blocks Phase 4's own work):**
1. Approve Beta 2 and authorise `0.9.0-beta.2`; push the branch for hosted CI.
2. The Fire TV screenshots: re-capture on the stick (recommended) or accept the Google TV emulator captures.
3. Keep the store-assets commit (`85d6d26`, about 78 MB of images) in Git, or move the folder elsewhere before pushing.
4. The website: deploy with Beta 2's publication; review the removal of "1.0 in December 2026" from the site.
5. F-005's placement (ROADMAP.md suggests V1.5; the handoff listed it for V1.1).
6. G-WIN (Windows signing and installer) before 1.0; the Windows app launch and upgrade check on the owner's machine.
7. Console checks (Play closed-testing review state, Amazon LAT) were not read this session.

**Next step for Beta 2:** the owner reviews this record and BETA.md's gate, then pushes `open-source-gpl` so hosted CI runs; on green, authorises the version, and the release follows RELEASE.md, "Releasing a version".

## Follow-through: the Beta 2 release attempt (2026-11-21, after the closure)

Recorded here so the closure above stays as it was. The owner's release mission (2026-11-21) approved Beta 2 and authorised its release; what happened is in BETA.md, "Beta 2 release attempt", and VERIFICATION.md, "Beta 2 release attempt". In short: the store media moved to the release archive (ARCHIVE.md), the Fire TV set re-captured on the physical stick, `0.9.0-beta.2` committed and pushed; **held** on hosted CI's Linux job (B-076), with the owner's choice of a scoped fix before release not yet achieved.
