# Reflux Roadmap

The one source of truth for what Reflux is building, in what order, and what is finished. It is organized by outcome, not by layer.

- What was run to verify each piece of work is in [VERIFICATION.md](VERIFICATION.md); it is not repeated here.
- What a release build needs is in [RELEASE.md](RELEASE.md), and the release and beta checklists are there.
- How versions are numbered and promoted is [VERSIONING.md](VERSIONING.md).
- The website, demo video, and beta program are planned in [LAUNCH.md](LAUNCH.md).
- Decisions behind the plan are in [DECISIONS.md](DECISIONS.md) (the rebaseline: 2026-10-17).

This roadmap replaces the earlier Phase 0–18 plan, which was ordered by architectural layer and no longer matched the state of the code (DECISIONS.md, 2026-10-17, "Roadmap rebaseline").

## Where Reflux is

Reflux is a **pre-release**. The engine (Media Core, sources, identification, metadata, watch state, Best Version, playback, Smart Offline) is mature and heavily tested; the experience layer runs on desktop, Android phones and tablets, and Android TV. Nothing has been released: there is no signed Android build, no desktop installer, no store listing, no public website, and no tester outside the project. Android phones and Google TV have been verified on emulators only; one physical Fire TV (a Fire TV Stick 4K Plus on Fire OS 8) has had a smoke test of a debug build (2026-10-18). The current version is `0.9.0-alpha.1` ([VERSIONING.md](VERSIONING.md)).

Status words used below:

| Word | Meaning |
| --- | --- |
| **Done** | Implemented, tested, documented, and verified as far as the environment allowed (VERIFICATION.md says how far) |
| **Partial** | Some of it exists; the gap is stated |
| **Open** | Not started |
| **Gate** | A decision point with criteria; the outcome moves the item into V1 or to V1.x |

## Platforms

| Target | V1 | Notes |
| --- | --- | --- |
| Android phone and tablet | Yes | Google Play |
| Android TV / Google TV | Yes | Google Play, the same application package as phones |
| **Fire TV running Fire OS** | **Yes** | Amazon Appstore. A separate distribution target from Android TV, with its own store, review, device matrix, and launcher integration; not "Android TV on another box" (DECISIONS.md, 2026-10-17, "Fire TV is a V1 target") |
| Windows | Yes | Installer with libmpv bundled |
| Linux | Yes | Package with libmpv (bundled or from the distribution) |
| Vega OS (newer Fire TV devices) | No | Future compatibility track. Vega OS is not Android; Reflux's Android application does not run on it. Promoted only by an evaluation and an explicit decision |
| iOS / iPadOS | No | Not a V1 commitment |

## Milestones

| Milestone | Outcome | State |
| --- | --- | --- |
| M0 — Rebaseline & governance | One roadmap, a versioning protocol, verification history separated, product decisions recorded, V1 acceptance criteria | Done (2026-10-17) |
| M1 — Product completeness | Everything V1 must contain is built and tested | Partial |
| M2 — Release engineering | Signed, shrunk, installable, upgradable builds for every V1 target, in CI, legally ready | Open |
| M3 — Real-world validation | Live services, physical devices, real controllers, real networks, large libraries | Open |
| M4 — Beta | `0.9.0-beta.1` in testers' hands through the stores and the website | Open |
| M5 — Beta iteration | `beta.2`, `beta.3`, … until the beta exit criteria hold | Open |
| M6 — Release candidate | `0.9.0-rc.1`, feature freeze, final validation, store submissions prepared | Open |
| M7 — Public 1.0 | `1.0.0` on Google Play, the Amazon Appstore, Windows, and Linux; the website in its launch state | Open |
| W0–W4 — Website | reflux.rsxdevelops.dev from foundation to launch, in parallel with M1–M7 ([LAUNCH.md](LAUNCH.md)) | Open |

M1, M2, and M3 overlap: release engineering and validation start as soon as the work they depend on is stable, not after M1 is finished. Beta (M4) starts only when the beta entry checklist in RELEASE.md holds.

## Done

What the product already does, everywhere it runs unless a platform is named. Details: ARCHITECTURE.md and the topic documents; evidence: VERIFICATION.md.

- **Media Core**: works, versions, locations, availability, watch state, and artwork as separate concepts; source capabilities explicit.
- **Identification**: films, shows, seasons, and episodes from ordinary file and folder names, with confidence; sidecar subtitles and artwork; identity keys for every script.
- **Metadata and artwork**: deterministic matching, TMDB provider (fixture-tested), episode metadata, artwork cache, one artwork fallback rule, a designed state for works without artwork.
- **Library**: local-first SQLite, offline and unmount safety, background pipeline (scan, probe, metadata, artwork), a source that is away stays in the library.
- **Sources**: local folders (desktop paths, the Storage Access Framework on Android, folders by path on television), Jellyfin (catalog, libraries, direct streams, two-way watch state), SMB and WebDAV (read-only); added and managed from the application; credentials in the system store on desktop and sealed by the keystore on Android.
- **Cross-source unification**: one work across sources, Best Version per device, local-first among equals.
- **Universal watch state**: one state per work, carried between copies by timeline, never blindly onto another cut, reconciled with sources that keep their own.
- **Continue Watching and Next Up** inside the application (Home and details).
- **Search**: deterministic smart search over metadata, credits, stream facts, and watch state.
- **Playback**: libmpv on desktop, Media3 on Android; default track selection, chapters (desktop), skip intro/recap/credits, next-episode autoplay and "still watching?", a source that goes silent becomes a stated failure, picture-in-picture on Android handhelds.
- **Versions**: every version of a film or an episode listed and playable by choice, the recommended one explained, any one pinned.
- **Smart Offline (Android, Windows, Linux)**: films, single episodes, whole seasons, and a show's next episodes; the review sheet; status counted per episode; a Downloads page with every copy and its actions; copies as versions; playback and resume with the source away.
- **Input**: pointer, keyboard, touch, controllers (desktop readers, Android key events), television remotes; the controller morph.
- **Form factors**: desktop, phone, tablet, television layouts of one experience layer, with the neutral-first visual system.
- **Developer CLI**, **Linux CI workflow** (written and run in its container; never run on GitHub), regression tests for everything the 2026-10-17 stress cycle found.

## M0 — Rebaseline & governance

Done on 2026-10-17:

- This roadmap, replacing the Phase 0–18 plan.
- Verification history moved to [VERIFICATION.md](VERIFICATION.md).
- [VERSIONING.md](VERSIONING.md), linked from AGENTS.md as mandatory for version and release changes; the version single-sourced in `gradle.properties`.
- [LAUNCH.md](LAUNCH.md): website track, demo video, beta program, store tracks.
- [RELEASE.md](RELEASE.md) rewritten around the release, beta-entry, and store checklists.
- Stale claims corrected in README.md, SECURITY.md, CONTRIBUTING.md, ARCHITECTURE.md, DESIGN.md, OFFLINE.md, WATCH_STATE.md, and AGENTS.md; superseded decisions marked.
- Product decisions recorded (DECISIONS.md, 2026-10-17): "From the start", television sidecar subtitles, desktop Smart Offline, collections in 1.0, episodes without a show name, the casting gate, Jellyfin sign-out on removal, truthful desktop capabilities, Watch Next, Fire TV, Vega OS, the beta stage.
- V1 acceptance criteria (below).

## M1 — Product completeness

V1 contains what Reflux fundamentally is, and no more. Each row is required for V1 unless marked as a gate.

| Item | State | What is left |
| --- | --- | --- |
| Automatic organization and identification | Done | — (episodes without a show name are grouped per folder and offered to Identify, 2026-10-18) |
| Identify / fix match | Done (automated) | Built on every form factor (the shared experience layer): search, results told apart, choose, undo, reached from every details page and offered plainly where Reflux is unsure (DECISIONS.md, 2026-10-18). Verified with fixtures on the desktop renderer, including a remote-only television path; not yet against live TMDB (no credential here, M3) nor driven on a device, which needs a build with the credential. Episodes' own numbers cannot be corrected from it (their show can) |
| Continue Watching and Next Up | Done | — |
| **Watch Next** (the platform's own "continue" row) | Done (emulator) | Android TV / Google TV: programs derived from Continue Watching and Next Up, added, updated, and removed with watch state, opening Reflux on the work (DECISIONS.md, 2026-10-18). What Google TV's launcher draws is unseen: it shows only certified integrations, so Google's Watch Next certification belongs to the Play submission (M2), and a physical Google TV check to M3. Fire TV: no home-screen row, because Amazon's Watch Activity needs a content catalog Reflux does not have, and no programs written to Fire OS's TV provider (DECISIONS.md, 2026-10-18) |
| Cross-source watch state | Done | — ("From the start" keeps the place, 2026-10-17) |
| Best Version | Done | Desktop capabilities are observed since 2026-10-19: hardware decoding from a start-up probe and real playbacks, unknown until then; the display the window is on (DECISIONS.md, "How the desktop observes what it can show and decode"). HDR output on the desktop is not claimed: the engine does not output HDR yet |
| Visible version selection | Done | Films and episodes list every version and play any of them; an episode's are on its own page, offered from its row where it has more than one (DECISIONS.md, 2026-10-18) |
| Version pinning | Done | Films (2026-10-17) and episodes (2026-10-18): "Always play this" and "Let Reflux choose"; a pin set aside is said and kept; an episode's pin follows it through Identify |
| Smart Offline, user-facing | Done | Films, single episodes (from their row or their own page), whole seasons, and a show's next episodes; a show or season counted per episode; a Downloads page; Windows and Linux wired like Android (DECISIONS.md, 2026-10-18). Verified offscreen and in the engine on the desktop, and physically on the Fire TV as VERIFICATION.md records |
| Serious playback parity | Partial | Television: an explanation where sidecar subtitles cannot be read (decided); held seek acceleration; chapters on Android where Media3 can supply them; media keys on desktop |
| Jellyfin correctness and security | Partial | Removing a server signs its session out since 2026-10-19 (DECISIONS.md, "How a Jellyfin server is signed out of"; verified live, VERIFICATION.md). Left: the plain-HTTP policy on Android (RELEASE.md) |
| Source onboarding and management | Done | WebDAV can be added on Android since 2026-10-19 (its own transport on OkHttp; DECISIONS.md, 2026-10-18, "Web shares on Android"), verified on the Fire TV and the phone emulator (VERIFICATION.md) |
| Android phone and tablet | Done (emulator) | Physical validation in M3 |
| Android TV / Google TV | Done (emulator) | Physical validation in M3 |
| Fire TV / Fire OS | Partial (one physical device) | Smoke-tested on a Fire TV Stick 4K Plus (`AFTMA08C15`, Fire OS 8.1.8.5, API 30, 32-bit userland) on 2026-10-18 with a debug build: installation, launcher, remote navigation, the folder chooser, browsing, details, search, playback up to 4K60 HEVC Main10 on the hardware decoder, lifecycle, the media session, and the instrumented suite (VERIFICATION.md). Five Fire OS defects found and fixed (DECISIONS.md, 2026-10-18). Left: the Fire TV television (not reachable), a release build (M2), Jellyfin from the device (SMB was used for Smart Offline, 2026-10-18; WebDAV added, scanned, played, and removed on the device, 2026-10-19), a USB drive, HDR output, sleep and wake, Amazon's own test criteria. Fire OS has the TV provider, and Reflux writes no Watch Next programs to it; with Reflux in the background a remote's Play key is not obeyed (both DECISIONS.md, 2026-10-18). Fire OS 6 devices (`AFTMM`, API 25) are below `minSdk`; Vega OS devices (`AFTCR001`) do not run Android applications |
| Windows and Linux | Partial | Capabilities observed since 2026-10-19 (the display the window is on, hardware decoding as the engine shows it). Left: media keys; scrollbars; HDR output |
| Accessibility baseline | Partial | Contrast and system text size are tested. Screen-reader labels on every control, focus order, reduced motion, and a TalkBack / Narrator pass are not |
| About, legal, and attribution | Open | An About page: version and build, licenses (Reflux and third-party, including libmpv/FFmpeg obligations), TMDB attribution and logo, privacy and support links |
| Casting | Gate | Time-boxed feasibility before beta; V1.1 if it fails the gate (DECISIONS.md, 2026-10-17, "Casting is a V1 feasibility gate") |

First M1 slice, done 2026-10-17: "From the start" keeps the place (Reflux and Jellyfin), and films' versions can be pinned and unpinned on every form factor. Second, done 2026-10-18: Watch Next on Android TV / Google TV, and the Fire TV home-screen decision. Third, 2026-10-18: the Fire TV smoke test on physical hardware and its fixes. Fourth, 2026-10-18: Identify, and episodes without a show name grouped by folder. Fifth, 2026-10-18: no Watch Next programs on Fire OS, Play ignored out of sight, and an episode's versions (list and pin). Sixth, 2026-10-18: Smart Offline complete (episodes, seasons, the Downloads page, the desktop). Seventh, 2026-10-19: WebDAV on Android (phones, televisions, Fire TV). Eighth, 2026-10-19: removing a Jellyfin server signs its session out. Ninth, 2026-10-19: desktop capabilities observed, not declared. Recommended order after it: About/legal, accessibility, the casting spike, performance R&D.

## M2 — Release engineering

| Item | State |
| --- | --- |
| License chosen for Reflux (blocks every distribution) | Open |
| Android signed release build, R8 shrinking with keep rules validated on device | Open |
| Android install, upgrade, and data migration tests (debug → release, release → release) | Open |
| Android in CI (unit tests, lint, a release build; instrumented tests where a runner allows) | Open |
| The CI workflow run on GitHub at least once, green | Open |
| Intermittent tests fixed: `SourcesUiTest.theRemoteChoosesLibrariesToo` times out under a loaded full `check` (VERIFICATION.md, first M1 slice) | Open |
| Linux window tests: in the `tools/ci` container a show's card on screen is never located for a click, failing `EmbeddedPlaybackTest` and `OfflineWindowTest` (already failing on `a48cd67`; VERIFICATION.md, Smart Offline) | Open |
| Google Play readiness: listing, data safety form, content rating, target API, TV quality checklist, Watch Next certification for Google TV, closed-testing track | Open |
| Fire OS / Amazon Appstore readiness: listing, Fire TV device targeting, Amazon's app review requirements, test on the declared devices | Open |
| Desktop packaging: Windows installer (signed), Linux package(s) | Open |
| libmpv distribution with provenance (LGPL build, license texts, source offer) | Open |
| Desktop metadata: version, icon, file associations if any, data directory | Open |
| TMDB credential injected into release builds, never committed | Open |
| Release checklist (RELEASE.md) exercised end to end once before beta | Open |

## M3 — Real-world validation

| Item | State |
| --- | --- |
| TMDB against the live service, with attribution in place | Open |
| A real Android phone and tablet | Open |
| A real Google TV / Android TV device | Open |
| Real Fire TV hardware (the developer's Fire TV Stick 4K Plus and Fire TV television) | Partial: the stick smoke-tested with a debug build (2026-10-18); the television not yet reachable |
| Physical Xbox and PlayStation controllers on desktop, Android, and television | Open |
| Desktop installation from the package on a clean Windows and a clean Linux machine | Open |
| Network failure and recovery on real networks (Wi-Fi drop, server sleep, VPN) | Open |
| A large-library performance baseline (scan, browse, search, start-up) recorded for regression | Open |
| A reproducible stress harness: the 2026-10-17 fuzzing and race runs were not committed (only the regression tests for what they found are in the suite); decide whether to keep a seeded harness in the tree | Open |
| A visual regression strategy for the offscreen renderings | Open |
| Broader compatibility: other Jellyfin versions, NAS SMB servers, HDR/DV/passthrough on real hardware | Open |

## M4 — Beta

`0.9.0-beta.1`. Entry requires the beta checklist in RELEASE.md.

- Google Play closed testing track; Amazon Appstore testing (Live App Testing) for Fire TV; desktop builds from the website.
- About 15–20 testers, recruited through the website's beta signup, covering phones, tablets, Google TV, Fire TV, Windows, and Linux, local folders and servers.
- The beta website (W3) with the demo video (W2), signup, supported platforms, and the changelog.
- A feedback path (in-app link to a form or email, a diagnostics export) and a public changelog per beta.

## M5 — Beta iteration

`0.9.0-beta.2`, `beta.3`, … Bug fixes, compatibility, UX, performance, accessibility. New features enter only by an explicit decision recorded in DECISIONS.md; otherwise they wait for V1.x. Exit: no open blocker or critical bug, the V1 acceptance criteria hold, and the last beta ran at least a week without a new critical report.

## M6 — Release candidate

`0.9.0-rc.1`, then `rc.N` only for fixes. Feature freeze, final validation on every target, store listings and submissions prepared, legal pages final.

## M7 — Public 1.0

`1.0.0`, built from the final release candidate's commit with only the version changed ([VERSIONING.md](VERSIONING.md)). Google Play (phones, tablets, Google TV), Amazon Appstore (Fire TV on Fire OS), Windows, Linux; reflux.rsxdevelops.dev in its launch state (W4).

## Website track (parallel)

| Stage | Outcome | Needed by |
| --- | --- | --- |
| W0 | Design system and site foundation | Before beta preparation |
| W1 | Early public landing page | During M1–M2 |
| W2 | Product demo video | M4 entry |
| W3 | Beta signup and launch content | M4 entry |
| W4 | Final 1.0 product site | M7 |

Requirements and the visual direction are in [LAUNCH.md](LAUNCH.md).

## V1 acceptance criteria

1.0 ships when all of these hold on every V1 target (Android phone/tablet, Android TV / Google TV, Fire TV on Fire OS, Windows, Linux), verified as VERIFICATION.md describes:

1. **The benchmark loop works without setup.** A normal folder of films and shows becomes an identified library with metadata and artwork, with no account, no `.nfo` files, and no renaming; a folder with poor names still yields a browsable library and every weak match can be fixed with Identify.
2. **Play does the right thing.** Play picks a version this device plays well, says why, and starts where the viewer left off on any copy of the same cut; "From the start" and a pinned version behave as WATCH_STATE.md says.
3. **Progress is never lost.** Watch state survives sources going away, sources being removed, re-identification, starting over, and syncing with Jellyfin; it is never overwritten by a stale server record.
4. **Continue where you are, from outside the app.** Watch Next on Android TV / Google TV reflects Continue Watching and Next Up; Fire TV has the platform's equivalent where Amazon permits it, and the decision record says what it does where not.
5. **Offline is a feature, not a file manager.** Download a film, an episode, or a season from its details page; see and manage every copy from a Downloads entry point; play and resume with the source away; on Android and on desktop.
6. **Sources are manageable and safe.** Folders, Jellyfin, SMB, and WebDAV are added, signed in again, narrowed, and removed from the app; removing a Jellyfin server ends its session; no credential appears in a log, a database in the clear (where secure storage exists), or a crash report.
7. **Every input works.** Touch, pointer, keyboard, Xbox and PlayStation controllers, and the Android TV and Fire TV remotes reach every V1 function, verified with physical hardware.
8. **Playback is serious.** Hardware decoding where the device has it, HDR/Dolby Vision handled honestly per device, multiple audio and subtitle tracks, subtitles readable over the controls, chapters where the engine reports them, and network failures stated rather than silent.
9. **Accessible baseline.** Every control has a label for screen readers, focus order is coherent, contrast meets WCAG AA, system text size is respected, and motion respects the system's reduce-motion setting where the platform reports one.
10. **Legal and honest.** A chosen license, third-party notices (including libmpv/FFmpeg), TMDB attribution, a privacy policy, and a support contact are in the app and on the website.
11. **Installable and upgradable.** Signed builds from CI; an upgrade from the last beta keeps the library, watch state, sources, and downloads.
12. **Verified for real.** Live TMDB, physical phone, physical Google TV device, physical Fire TV device, physical controllers, clean desktop installs, and a large-library baseline are recorded in VERIFICATION.md.
13. **Excellent out of the box.** No V1 function requires a setting to be changed first; the beta's critical and blocker reports are closed.

## V1.x

Candidates after 1.0, each needing its own decision to be promoted:

- Casting, if it does not pass the V1 gate.
- Collections UI: automatic (franchise), user, and smart collections (engine done).
- Library diagnostics as a page (engine done).
- Downloads that continue with the application closed (a foreground service and notification).
- Background audio playback on Android.
- Sidecar artwork and subtitles on Android 13+ televisions without broad storage access (embedded cover art, video frames).
- Multi-part files (CD1/CD2) as one version; disc structures (BDMV/VIDEO_TS) playable.
- Plex, Emby, and DLNA sources.
- Linux credential storage without a Secret Service.

## Future

- Vega OS client (a compatibility track; evaluation first).
- Device handoff and synchronization of watch state between devices without a server.
- Recommendations, natural-language search, and higher-level library intelligence, above the deterministic engine.
- Advanced playback (picture controls, per-title audio/subtitle styling, trip mode for offline).
- iOS / iPadOS.

## Not planned

Social features, Watch Together, a Reflux streaming service, a mandatory cloud backend or account, AI required for basic features, server administration, and a transcoding stack of Reflux's own unless a product requirement justifies one.
