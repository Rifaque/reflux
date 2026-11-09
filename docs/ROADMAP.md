# Reflux Roadmap

The one source of truth for what Reflux is building, in what order, and what is finished. It is organized by outcome, not by layer.

- What was run to verify each piece of work is in [VERIFICATION.md](VERIFICATION.md); it is not repeated here.
- What a release build needs is in [RELEASE.md](RELEASE.md), and the release and beta checklists are there.
- How versions are numbered and promoted is [VERSIONING.md](VERSIONING.md).
- The website is a release workstream across M6, M7, and M8 (below, "The Reflux website"); its visual direction, the demo video, and the beta program are in [LAUNCH.md](LAUNCH.md).
- Decisions behind the plan are in [DECISIONS.md](DECISIONS.md): the first rebaseline (2026-10-17) and the milestone rebaseline (2026-11-02).

**Current state: M7 closing (2026-11-09): every local gate passes; its hosted CI run and GitHub attestation are being verified. M8 follows, once the owner actions in its entry checklist are done.**

This milestone list replaces the one of 2026-10-17 (M0 to M7, with M2 as release engineering and M3 as real-world validation), which replaced the earlier Phase 0–18 plan. Documents and verification sections written before 2026-11-02 use the older numbering; how each old milestone maps to this one is under "Earlier numbering", and DECISIONS.md, 2026-11-02, "The milestone rebaseline", says why.

## Where Reflux is

Reflux is a **pre-release**. The engine (Media Core, sources, identification, metadata, watch state, Best Version, playback, Smart Offline) is mature and heavily tested; the experience layer runs on desktop, Android phones and tablets, and Android TV. Nothing has been released: the release system, the CI and release pipelines, signed Android release builds as `dev.rsxdevelops.reflux`, the desktop archives with Reflux's own LGPL libmpv, and the website exist and have been rehearsed from clean clones (M6, M7), but nothing has been pushed or deployed, and there is no store listing, no public download, no live website, and no tester outside the project. Playback has been verified on a physical Nothing Phone 2a, Xiaomi Pad 7, and Fire TV Stick 4K Plus, and on the Google TV emulator (VERIFICATION.md); a physical Google TV device has not been used. The current version is `0.9.0-alpha.2` ([VERSIONING.md](VERSIONING.md)).

Status words used below:

| Word | Meaning |
| --- | --- |
| **Complete** / **Done** | Implemented, tested, documented, and verified as far as the environment allowed (VERIFICATION.md says how far) |
| **Next** | The milestone to work on now |
| **Future** | Planned, not started |
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
| M0 — Governance & Rebaseline | Product governance, versioning, development rules, roadmap governance, release and version discipline | **Complete** (2026-10-17; milestones rebaselined 2026-11-02) |
| M1 — Accessibility & Interaction Baseline | Accessibility foundation, TalkBack and television accessibility, keyboard and focus behavior, interaction semantics, reduced motion and text size, screen-reader isolation, touch and focus targets | **Complete** (2026-11-01) |
| M2P — Playback Compatibility & Direct Play | Reflux knows what the client can actually play, preserves the original whenever possible, degrades the smallest necessary part when it cannot, and can explain why | **Complete** (2026-11-02), with explicit waivers and unknowns (PLAYBACK.md, section 17) |
| M3 — Navigation & Source Libraries | How the viewer moves through Reflux and controls the media universe playback works on: global and platform navigation, the unified library and each source's own, source inclusion | **Complete** (2026-11-07), with the limits in its section |
| M4 — Visual System 2.0 | A higher-end visual system around that structure | **Complete** (2026-11-07), with the limits in its section |
| M5 — Library Depth & Reflux Magic | A richer library and the features that set Reflux apart | **Complete** (2026-11-07), with the limits in its section |
| M6 — Release Engineering / Security / Legal | Production builds, packaging, signing, security hardening, legal and licensing, store preparation; the website's foundation and its legal and privacy pages | **Complete** (2026-11-08), with the limits in its section |
| M7 — Compatibility, Release Proof, Brand + Website, and Beta/Test Readiness | The permanent Android identity, the brand, the redesigned website, CI and release proof, the source-built Windows libmpv, the diagnostics export, the M6 stability leftovers, store readiness, and the M8 entry checklist | **Closing** (2026-11-09): local gates pass; hosted CI and attestation being verified |
| M8 — Beta | `0.9.0-beta.N` in testers' hands until the beta exit criteria hold, with the website operating as their entry point | Future |
| M9 — Release Candidate | `0.9.0-rc.N`, feature freeze, final validation, submissions prepared | Future |
| M10 — 1.0 | `1.0.0` on every V1 target | Future |
| The Reflux website | Release infrastructure, not a milestone of its own: foundation in M6, public and tester readiness in M7, required before M8 begins, kept in M8 to M10 (below, "The Reflux website") | **Partial**: built and checked (M7); deployment is an owner action |

**How they relate.** M2P established playback truth and compatibility. M3 establishes how the viewer navigates and controls the media universe that playback operates on. M4 improves the visual system around that product structure. M5 deepens the library experience. M6 prepares the mature product for production release, with its security, legal, and the website's foundation. M7 proves it across the final production matrix and brings the website to public and tester readiness. M8 is the beta, with the website operating as the testers' entry point; M9 and M10 are the release candidate and 1.0.

**Why release engineering is M6, not M2.** The roadmap separates building the product from hardening it for release. Release engineering, security, and legal work come after navigation, the source model, the visual system, and the library's depth are settled, so that what is signed, packaged, reviewed, and submitted is the product as it will ship. Security is not deferred: foundational security work continues whenever it is needed (credentials in the system store, redaction, sign-out on removal, and the checks in VERIFICATION.md are in place now). It does not define the current milestone.

## Done

What the product already does, everywhere it runs unless a platform is named. Details: ARCHITECTURE.md and the topic documents; evidence: VERIFICATION.md.

- **Media Core**: works, versions, locations, availability, watch state, and artwork as separate concepts; source capabilities explicit.
- **Identification**: films, shows, seasons, and episodes from ordinary file and folder names, with confidence; sidecar subtitles and artwork; identity keys for every script.
- **Metadata and artwork**: deterministic matching, TMDB provider (fixture-tested), episode metadata, artwork cache, one artwork fallback rule, a designed state for works without artwork.
- **Library**: local-first SQLite, offline and unmount safety, background pipeline (scan, probe, metadata, artwork), a source that is away stays in the library.
- **Sources**: local folders (desktop paths, the Storage Access Framework on Android, folders by path on television), Jellyfin (catalog, libraries, direct streams, two-way watch state), Plex (catalog, libraries, direct files, two-way watch state), SMB and WebDAV (read-only); added and managed from the application; credentials in the system store on desktop and sealed by the keystore on Android.
- **Navigation and source libraries (M3)**: six global destinations on one back stack, placed for each window; each source's and each library's own page; source and library inclusion in the unified library, kept apart from connection and availability.
- **Cross-source unification**: one work across sources, Best Version per device, local-first among equals.
- **Universal watch state**: one state per work, carried between copies by timeline, never blindly onto another cut, reconciled with sources that keep their own.
- **Continue Watching and Next Up** inside the application (Home and details).
- **Search**: deterministic smart search over metadata, credits, stream facts, and watch state.
- **Playback**: libmpv on desktop, Media3 on Android; planned, negotiated, and explained (M2P); default track selection, chapters (desktop), skip intro/recap/credits, next-episode autoplay and "still watching?", a source that goes silent becomes a stated failure, picture-in-picture on Android handhelds.
- **Versions**: every version of a film or an episode listed and playable by choice, the recommended one explained, any one pinned.
- **Smart Offline (Android, Windows, Linux)**: films, single episodes, whole seasons, and a show's next episodes; the review sheet; status counted per episode; a Downloads page with every copy and its actions; copies as versions; playback and resume with the source away.
- **Input**: pointer, keyboard, touch, controllers (desktop readers, Android key events), television remotes; the controller morph.
- **Form factors**: desktop, phone, tablet, television layouts of one experience layer, with the neutral-first visual system.
- **Visual System 2.0 (M4)**: semantic tokens, material tiers chosen per device, functional glass, one navigation family, hero artwork under the navigation where the material carries it, Media Atmosphere (off by default), edge to edge on Android, the system's high contrast and reduced transparency followed. One visual identity; themes are V2.
- **Developer CLI**, **Linux CI workflow** (written and run in its container; never run on GitHub), regression tests for everything the 2026-10-17 stress cycle found.

## M0 — Governance & Rebaseline

Complete. Done on 2026-10-17:

- A roadmap replacing the Phase 0–18 plan.
- Verification history moved to [VERIFICATION.md](VERIFICATION.md).
- [VERSIONING.md](VERSIONING.md), linked from AGENTS.md as mandatory for version and release changes; the version single-sourced in `gradle.properties`.
- [LAUNCH.md](LAUNCH.md): website track, demo video, beta program, store tracks.
- [RELEASE.md](RELEASE.md) rewritten around the release, beta-entry, and store checklists.
- Stale claims corrected in README.md, SECURITY.md, CONTRIBUTING.md, ARCHITECTURE.md, DESIGN.md, OFFLINE.md, WATCH_STATE.md, and AGENTS.md; superseded decisions marked.
- Product decisions recorded (DECISIONS.md, 2026-10-17): "From the start", television sidecar subtitles, desktop Smart Offline, collections in 1.0, episodes without a show name, the casting gate, Jellyfin sign-out on removal, truthful desktop capabilities, Watch Next, Fire TV, Vega OS, the beta stage.
- V1 acceptance criteria (below).

On 2026-11-02 the milestones were rebaselined to the list above (DECISIONS.md, "The milestone rebaseline").

## M1 — Accessibility & Interaction Baseline

Complete (closed 2026-11-01; its recorded limits stand). Built 2026-10-31 (DECISIONS.md): the player and panels isolate what is behind them, every control has a label, role, and state, pages and panels are named, what happens is announced, reduced motion and text size follow the system on every platform, and touch targets are 48 dp. Walked with real TalkBack on the Pad 7, with reduced motion and 200% text, 2026-11-01. The Fire TV passed on hardware by remote, with its tree and timeline actions read through the accessibility API.

Recorded limits, in VERIFICATION.md: a screen reader heard on a television, where TalkBack resumes after an overlay closes (the toolkit offers no way to move it), and a person's ear on it; a desktop screen reader depends on the toolkit (Narrator is not reached at all).

## M2P — Playback Compatibility & Direct Play

Complete. Named M2P to tell it from the earlier M2, which was release engineering and is now M6. Specification, evidence, compatibility matrix, and what it does and does not prove: [PLAYBACK.md](PLAYBACK.md), section 17. Physical Linux hardware validation was waived for M2P, and virtual Linux validation accepted (VERIFICATION.md, "M2P.4 physical Linux gate: waived"). Reopened only for a regression a later milestone finds.

It contains: the capability model; `PlaybackAssessor`; `PlannedPlayback` and the deterministic planner; Best Version integration; source negotiation and Jellyfin's `PlaybackInfo`; Direct Play, remux, audio conversion, and video transcode; subtitle planning; playback evidence and `PlaybackComparison`; track directives; the one-time re-plan; Android Media3 and desktop mpv; the real-media compatibility matrix; generated compatibility fixtures; diagnostics (the player's Info list); chained-SeekHead handling; the Android PGS fix; and documented compatibility boundaries.

| Item | State |
| --- | --- |
| Current-state audit, compatibility matrix, proposed capability model, planner, negotiation, and diagnostics | Done 2026-11-01 (PLAYBACK.md) |
| The truth layer: what the server really did, read from its session; a true `PlayMethod`; silent audio stated; the engine's token reaching the server | Done 2026-11-01 (PLAYBACK.md, section 3a) |
| Shared capability model extended, stream facts migrated, and the audit's other defects | Done 2026-11-01 (PLAYBACK.md, section 6) |
| Explicit, tested playback planner | Done 2026-11-01 (PLAYBACK.md, section 7) |
| The plan executed on every source and both engines, the selected track assessed | Done 2026-11-01 (PLAYBACK.md, section 8) |
| Jellyfin negotiation through `PlaybackInfo` and a device profile, server behavior read from its session | Done 2026-11-01 (PLAYBACK.md, section 8) |
| Diagnostics that explain a playback, planned against observed | Done 2026-11-02: `PlaybackReport`, the player's Info list and the diagnostics log |
| Desktop audio outputs and passthrough; the Android decoder decision, with measurements | Decided 2026-11-02: output channels observed (section 6); passthrough deferred to M7, where real receivers are in scope; no new Android decoder (DECISIONS.md, "M2P's last decisions") |
| Test corpus, and validation on Windows, Linux, the Phone 2a, the Pad 7, the Google TV emulator, and the Fire TV | Done 2026-11-02: the real library and generated fixtures on every device here (VERIFICATION.md). Linux on physical hardware waived; the Phone 2a did not run the fixtures (not attached) |
| The real Iron Man file has a reproducible result | Done 2026-11-02: the server serves it again, and it played as planned on Windows and the Fire TV (VERIFICATION.md) |
| Compatibility expansion and real media matrix | Done 2026-11-02: the real library walked on Windows, the Fire TV, the Pad 7, the Phone 2a, and the Google TV emulator; chained SeekHeads; PGS beside a conversion on Android; fixtures for AV1, HEVC 4K HDR10 and HLG, TrueHD, VP9 (PLAYBACK.md, sections 15 to 17) |

Its working slices (M2P.2 to M2P.5 in PLAYBACK.md and VERIFICATION.md) are implementation history, not roadmap milestones.

## M3 — Navigation & Source Libraries

**Complete** (2026-11-07). How the viewer moves through Reflux, and controls which media the library is made of. Specification: [NAVIGATION.md](NAVIGATION.md). Decisions: DECISIONS.md, 2026-11-02, "Navigation and source libraries". Evidence: VERIFICATION.md, "M3: navigation and source libraries".

| Item | State |
| --- | --- |
| Global navigation: Home, Movies, Shows, Search, Downloads, Sources, as sections of one back stack | Done |
| Platform navigation: icon bottom bar (phone upright), compact rail (phone on its side), named rail (tablet), sidebar (desktop), widening rail (television) | Done; verified on the Phone 2a, the Pad 7, the Fire TV, the Google TV emulator, and real windows on Windows and Linux |
| Deterministic Back: a destination's Back is Home; on a television, the rail first; Left to the rail and Right back to where focus was | Done |
| The unified library and each source's (and library's) own page, on one index and one playback path (`LibraryScope`) | Done |
| Connection, availability, inclusion, browsing kept apart; sign-in required stated | Done |
| Source and per-library inclusion, with the edge cases in NAVIGATION.md, section 5; earlier left-out libraries migrated | Done |
| Plex on the same source model | Done: fake-server tests, and a real Plex Media Server 1.43.5 in Docker (live test and the app's own connect form) |
| Show → Season → Episode, player return, Downloads and Search as destinations | Done |
| M1 accessibility kept: names, roles, selected state, isolation behind the player and sheets, TV focus | Done (automated semantics tests and device trees; no screen reader walk in M3) |

**Limits recorded, not blocking:**

- Plex: no server-side conversion (what a device cannot play directly is said by the planner), no plex.tv server discovery or PIN sign-in, the device's plex.tv token is forgotten on removal but not revoked at plex.tv (for M6's security review). Validated against one server version, in a container, without a plex.tv account; sign-in by account is covered by fake-server tests only.
- No TalkBack or other screen-reader walk was repeated for the new navigation; its semantics are covered by the automated tests and read from device accessibility trees.
- Linux: virtual (the `tools/ci` container), as accepted for M2P; not physical hardware.
- `Modifier.onFocusedBoundsChanged`, used to tell a partly-left move, is deprecated in Compose; the regression test fails if it stops reporting (M7 to replace it with `getFocusedRect`).

### Sources: four separate questions

| Concept | Question |
| --- | --- |
| **Connection** | Can Reflux connect to the source (it is configured, and its credentials work)? |
| **Availability** | Is the source reachable now? |
| **Inclusion** | Does content from this source, or this library of it, take part in the unified Reflux library? |
| **Source browsing** | Can the viewer open and browse this source or library on its own? |

A source is never "disabled" by deleting its credentials or disconnecting it. Exclusion is about aggregation only. It does not delete the source, remove or forget credentials, sign out, destroy cached metadata, delete or touch local files, disable playback, prevent browsing the source on its own, change watch state, or remove the source from source management. The exact rules, including libraries found later or removed, are in NAVIGATION.md, section 5.

### Architecture

No source gets a playback system of its own:

```text
Source adapter → source and library contents → unified Reflux media model → Best Version → playback planner → existing engines
```

## M4 — Visual System 2.0

**Complete** (2026-11-07), with the limits below. A calmer, deeper visual system around M3's structure, with one visual identity. Specification: DESIGN.md, "Visual System 2.0 (M4)". Decisions: DECISIONS.md, 2026-11-07, "Visual System 2.0" and "One visual identity; themes are V2". Evidence: VERIFICATION.md, "M4: Visual System 2.0".

| Item | State |
| --- | --- |
| Semantic tokens: color roles (with selected, focused, pressed, disabled, error, success, warning), type roles, spacing, a radius family, elevation, state layers; components draw with roles, not values | Done |
| Material tiers BASIC, ENHANCED, PREMIUM, chosen internally from the device and the viewer's system settings; never a setting | Done |
| Functional glass only (navigation, panels, sheets, the player's chrome), legible over any artwork on every tier; PREMIUM measures what is behind it | Done |
| Navigation surfaces as one family: phone bar, compact rail, tablet rail, desktop sidebar, television rail (quiet closed, material open); where the viewer is shown by shape and light | Done; verified on the Phone 2a, the Pad 7, the Fire TV, the Google TV emulator, and real windows on Windows and Linux |
| Home's hero and a work's header under the rail or sidebar on ENHANCED and PREMIUM; beside it on BASIC | Done |
| Media Atmosphere: optional, off by default, one switch under Sources, kept in the library | Done |
| Motion vocabulary with a restrained television profile; reduced motion makes it immediate | Done |
| Edge to edge on Android, the status bar protected, controls clear of the system bars | Done |
| High contrast and reduced transparency followed from the system on Android, Windows, and Linux | Done |
| Representative visual tests over offscreen renderings (`VisualSystemTest`) | Done; the broader strategy stays in M7 |
| Performance measured on devices against M3 | Done (VERIFICATION.md) |
| User-selectable themes | Deferred until after 1.0, a potential V2 feature (DECISIONS.md) |

**Limits recorded, not blocking:**

- Performance on the Fire TV Stick was measured with debug builds, which are slow there in M3 and M4 alike (M4 no worse); release-build performance belongs to M6 and M7.
- The Google TV emulator renders in software, so its frame times say nothing about a real device; a physical Google TV device is still M7's.
- Linux: virtual (the `tools/ci` container), as accepted for M2P; not physical hardware.
- No screen reader walk was repeated; the semantics are covered by the automated tests and read from device accessibility trees.
- The visual tests are pixel probes of representative layouts, not image baselines; a visual regression strategy is still M7's.

## M5 — Library Depth & Reflux Magic

**Complete** (2026-11-07), with the limits below. Sources provide media truth, metadata providers descriptive truth, and Reflux owns canonical identity, provenance, the cache, and presentation. Specification: METADATA.md, "Library depth (M5)". Decision: DECISIONS.md, 2026-11-07, "Library depth". Evidence: VERIFICATION.md, "M5: Library depth".

| Item | State |
| --- | --- |
| Canonical identity: movie, show, season, and episode works independent of files and sources; the same work on several sources one work with several versions | Done (the existing model, extended; no second identity system) |
| Sources describe their works: Jellyfin and Plex overviews, genres, ratings, runtimes, dates, networks or studios, seasons with names, overviews, and posters, episode stills; Plex's cast | Done; verified on a real Jellyfin library |
| Provenance for every resolved field and picture; deterministic field-level precedence (`MetadataResolver`, `ArtworkPrecedence`), independent of scan order | Done |
| Provider-neutral contract; TMDB the one isolated adapter, with end dates, networks, season dates, episode ratings and guest cast | Done; TMDB against the live service is still M7's |
| Identify integrated: evidence recorded, the user's choice outranking sources, durable across rescans, refreshes, and unification | Done (and a defect fixed: a correction kept the IDs it corrected) |
| Scoped refresh (movie, show, season) that keeps everything on failure; staleness re-reads a few works at a time | Done |
| Request gate: coalescing, memory, spacing, backing off | Done |
| Artwork cache: one download per picture, failures not retried at once, accounting, bounded, no writes beside media, offline reuse; Jellyfin pictures at display size | Done |
| Online metadata & artwork: one switch, a privacy choice | Done |
| Richer movie, show, season, and episode details; episode stills in rows, pages, and Continue Watching; parent fallback | Done; verified on the Fire TV with real media |
| Reflux Magic, kept small: one work across sources, the right picture for each place, no blank states, provider franchises on Home | Done |
| Performance: scan no slower than M4, show pages faster | Done (measured against M4 on the same server) |
| Licensing and privacy of the provider documented; production approval | Documented; not approved (RELEASE.md, legal review) |

**Limits recorded, not blocking:**

- Jellyfin's cast is not read (it would make every scan about eight times slower); cast comes from the provider when online. Reading one work's cast from its server when its page opens is possible later.
- Plex descriptions are verified against fake-server tests only; no real Plex server was used in M5. Plex pictures are fetched at the server's size.
- TMDB was exercised through fakes; no credential is available locally (as since M0). Its use in production waits for the legal review: non-commercial licence, six-month cache limit, attribution wording.
- No artwork picker: the user tier of the artwork precedence is reserved. Embedded artwork (Matroska attachments, MP4 `covr`) is not read.
- Linux was not re-verified for M5: the container run was stopped for lack of memory on the developer's machine (VERIFICATION.md). M5 has no Linux-specific code.
- The Xiaomi Pad 7 was not walked: installing a new package there needs an on-screen approval. Tablet layouts are covered by renders at its size. The Phone 2a was locked, so it ran the on-device tests without a visual walk.

**Moved out of M5:** the playback-parity items carried here at the rebaseline (on television an explanation where sidecar subtitles cannot be read, decided; held seek acceleration; chapters on Android where Media3 can supply them; media keys on desktop) are playback work, outside M5's library scope. They are open, placed in M7 before M9's feature freeze.

## M6 — Release Engineering / Security / Legal

**Complete** (2026-11-08), with the limits below. This is the work the 2026-10-17 roadmap called M2. Production release engineering, packaging and distribution, signing, security hardening, legal and EULA, licensing review, privacy and security review, store preparation, and production release infrastructure. How each part works: RELEASE.md; the evidence: VERIFICATION.md, "M6: Release engineering, security, legal".

| Item | State |
| --- | --- |
| Reflux's own terms (blocks every distribution) | Decided 2026-10-19: proprietary, all rights reserved (`LICENSE`, shown on the About page). No EULA before the legal review (DECISIONS.md, 2026-11-08). Left: the legal review in RELEASE.md |
| One release identity: version, `versionCode`, package versions, channel, commit from `reflux.version`; artifact names, `SHA256SUMS`, manifest, provenance; `release.py build`/`verify`; release workflow | Done 2026-11-08 (dry run from a clean clone of `9a95fe8`) |
| Android signed release build, R8 shrinking with keep rules validated on device | Done 2026-11-08: the dry run's APK, signed with the dry-run key, validated on the Nothing Phone 2a, the Xiaomi Pad 7, the Fire TV Stick 4K Plus, and a fresh Google TV emulator; no crash and no R8 symptom on any. Signing strategy decided (DECISIONS.md, 2026-11-08). The owner's upload key is made at M8's start |
| Android release on a physical phone (Nothing Phone 2a) and tablet (Xiaomi Pad 7): install, the product's main paths, minified-build behavior | Done 2026-11-08 (VERIFICATION.md, "M6: the release on physical devices") |
| Android release on Fire TV (Fire TV Stick 4K Plus) | Done 2026-11-08: the debug build removed (with the owner's approval), the release installed and validated |
| Android install, upgrade, and data migration tests (debug → release, release → release) | Done 2026-11-08: release → release (the `9a95fe8` release updated in place by the `3ac4fba` release, same version, same key) on all three devices kept every source, download, favorite, resume point, and Continue Watching, and plays afterwards. Debug → release cannot upgrade (different signers): a reinstall, as the stores' first install will be. A `versionCode` increase is first exercised with the first beta (M8), since the version is not changed for a dry run |
| Android in CI (unit tests, lint, a release build; instrumented tests where a runner allows) | Done in the workflow (`android` job); not yet run on GitHub |
| The CI workflow run on GitHub at least once, green | Moved to M7: needs the branch pushed, the owner's action |
| Intermittent tests: `SourcesUiTest.theRemoteChoosesLibrariesToo` and `SourcesUiTest.aServerThatIsAwayOrStopsAnsweringIsRemovedAtOnce` time out under a loaded full `check` | Cause found and fixed 2026-11-08: connecting a server queued two full refreshes behind the pipeline's lock; a waiting refresh is now joined (`LibraryPipelineTest`). Both passed in the two loaded full `check` runs since (VERIFICATION.md). Two passing runs do not prove an intermittent gone; watched in M7's runs |
| Intermittent test: `LibraryDepthUiTest.identifyRefreshesWhatAWorkIsMatchedTo` timed out (30 s) waiting for "Details and artwork refreshed" in a loaded full `check` of 2026-11-08; alone it passes (3 of 3, 1.2 s). Suspects: the refresh waiting on the pipeline's lock behind another, or the notice's few seconds on screen being missed. Not fixed | Open, carried to M7: it passed in the final loaded full `check` (1 failure in 2 loaded runs), which does not make it fixed |
| Linux window tests: in the `tools/ci` container a show's card on screen is never located for a click, failing `EmbeddedPlaybackTest` and `OfflineWindowTest` (already failing on `a48cd67`; VERIFICATION.md, Smart Offline) | Done 2026-10-19: harness causes, fixed (VERIFICATION.md, "Real-window browsing") |
| Intermittent window test: `EmbeddedPlaybackTest`'s missing-file step on Linux, where the engine reported nothing within 20 s for a file deleted just before Resume (1 of 9 container rounds; VERIFICATION.md, "Real-window browsing") | Open, carried to M7 |
| Google Play readiness: listing, data safety form, content rating, target API, TV quality checklist, Watch Next certification for Google TV, closed-testing track | Prepared (RELEASE.md, "Store readiness"; PRIVACY.md): target API 36, data safety answers, the closed-testing rule. Nothing submitted; the account type is read by the owner |
| Fire OS / Amazon Appstore readiness: listing, Fire TV device targeting, Amazon's app review requirements, test on the declared devices | Prepared (RELEASE.md); nothing submitted |
| Android developer verification | Read and recorded (RELEASE.md); no account change |
| Desktop packaging: Windows, Linux | Done 2026-11-08: a Windows zip and a Linux tar.gz with a pinned Temurin 21 runtime (DECISIONS.md, 2026-11-08). Windows is **unsigned by decision**. Installers: later |
| libmpv distribution with provenance (LGPL build, license texts, source offer), and its line in `legal/components.txt` and the About page | Done for internal builds (THIRD-PARTY.md). **Gate before any public Windows download (M7):** Reflux builds libmpv itself and publishes its source |
| Desktop metadata: version, icon, file associations if any, data directory | Done: package versions from the release identity, Reflux's icon, no file associations, data directories as before |
| TMDB in release builds | Decided 2026-11-08: **disabled** in public builds (DECISIONS.md); development builds keep it. Superseded in M7 (2026-11-09) by the working non-commercial classification (DECISIONS.md) |
| The plain-HTTP policy on Android (RELEASE.md) | Decided 2026-11-08: cleartext allowed, user CAs untrusted (DECISIONS.md) |
| Security audit: secrets in history and artifacts, Android, desktop, website, `SECURITY.md` | Done 2026-11-08 (VERIFICATION.md): gitleaks over all history, the live account in no commit or artifact, a Plex-token redaction added, private vulnerability reporting on |
| Third-party inventory and privacy inventory | Done: THIRD-PARTY.md, PRIVACY.md |
| A diagnostics export for bug reports, with file paths and titles redacted, and the in-app feedback link (beta entry, RELEASE.md) | Moved to M7 (needed for the beta, M8) |
| Release checklist (RELEASE.md) exercised end to end once before beta | Done as a dry run 2026-11-08 (local, throwaway key, nothing published) |

Limits of M6, and what moved:

- To M7: the website's redesign (the owner's review found its presentation below the bar; M7, "Reflux Website", Redesign); the CI workflow's first run on GitHub (needs a push); the website deployed at reflux.rsxdevelops.dev (needs the push or a Vercel login, and the Cloudflare record: WEBSITE.md); Reflux's own libmpv build with its published source, before any public Windows download; the diagnostics export and feedback link; the intermittent `LibraryDepthUiTest` and `EmbeddedPlaybackTest` steps.
- Owner actions before M8: the upload key; the Play Console account type (closed-testing rule); `dev.reflux` registered for developer verification before any APK is offered outside the stores.
- Not verified in M6: a `versionCode` increase on a device (first with the beta); a physical Google TV (M7); the Fire TV television (M7); the legal review (M7/M9).
- Seen on the devices, not release regressions, for M7: on the Phone 2a, a subtitle cue already running at the point a seek lands was not drawn until playback crossed a cue start (seen once, with a single 29-second cue; a debug build not compared); on the Fire TV, a subtitle chosen in one play was Off when the same film was played again.

### Reflux Website Foundation

M6 establishes the production foundation of the website, so that it can become the canonical public reference before the beta.

- Website architecture: the simplest that serves. A static site is enough while nothing needs a backend.
- Domain and hosting. The domain is decided: reflux.rsxdevelops.dev (LAUNCH.md). Hosting is not: no provider is established in the repository or its infrastructure. GitHub Pages is a viable choice for a static site and supports custom domains; the decision is made here, against what RSX Develops already runs, and recorded in DECISIONS.md.
- A deployment pipeline, HTTPS, and a production environment.
- Content structure and documentation structure (the routes under "The Reflux website").
- Privacy and legal pages, and a support and contact surface.
- Release and version information from one source (`CHANGELOG.md` and the version in `gradle.properties`, as VERSIONING.md defines them), not typed by hand.
- Basic routing to downloads and tester channels.
- A security review of the site and its pipeline.
- Ownership of the website's infrastructure: who holds the domain, the hosting account, and the deploy keys.

State 2026-11-08 (WEBSITE.md): built, hosting decided (Vercel, DECISIONS.md), every item above in the tree and checked locally; served locally for the owner's review. **Not yet live (moved to M7):** deploying needs the repository pushed and connected (or a Vercel login on this machine), and the domain one CNAME at Cloudflare, both the owner's.

### Public legal information

Planned here, worded in the legal work of M6 and M7 (RELEASE.md, "Legal review"); not written to fill the roadmap:

- A privacy policy at a public URL that store distribution accepts (Google Play requires one, and requires its Data safety answers to match what the app does).
- Terms or an EULA, where the legal review finds them required.
- Ownership and copyright (RSX Develops; DECISIONS.md, "Reflux is proprietary").
- Support and contact, and a release and support contact for the stores.
- Third-party and open-source notices, as the About page already lists them.
- Data handling disclosures consistent with the app: what it stores, what it sends and to whom (TMDB, the user's own servers), and that it has no account and sends nothing to RSX Develops.

## M7 — Compatibility, Release Proof, Brand + Website, and Beta/Test Readiness

**Closing** (2026-11-09; started 2026-11-08 from the M6 closure, `2c92c31`): every local gate passes; the hosted CI run and GitHub attestation of the final commit are being verified. M7 takes Reflux from a working alpha with a proven, manual release process to a product that is ready to begin the beta. It is not a feature milestone: it adds no feature beyond the diagnostics export M6 deferred, does not redesign playback, adds no account, telemetry, or cloud service, and does not start M8 or any public test. Decisions: DECISIONS.md, 2026-11-08, "M7's scope". Evidence: VERIFICATION.md, "M7".

### Workstreams, in order

The order is by dependency: the final package identity comes before anything is registered or listed around it; the brand comes before the website and the store assets that carry it; the source-built libmpv comes before the Windows artifact is called public.

| # | Workstream | Depends on | What done means |
| --- | --- | --- | --- |
| A | **Permanent Android identity**: `dev.reflux` → `dev.rsxdevelops.reflux` | — | The application ID, the release tool's check, the store records, the scripts, and the documents name `dev.rsxdevelops.reflux`; the Kotlin source namespace (`dev.reflux.*`) is unchanged (DECISIONS.md, "The permanent Android identity") |
| B | **Brand identity and app icon** | — | Several genuinely different concepts explored and compared; a mark chosen with its reasons; master artwork, wordmark, lockup, monochrome, and every platform's icon produced from one source; usage rules (`design/brand/`) |
| K | **Diagnostics export** (deferred from M6) | — | A support report generated on request, never sent by itself, with the redaction tested |
| J | **Deferred playback and stability** | — | The Phone 2a subtitle-after-seek and Fire TV subtitle-on-replay observations, and the intermittent `LibraryDepthUiTest` and `SourcesUiTest` tests, each fixed or classified with evidence |
| C | **Website 2.0** | B | The site rebuilt as a product site in the brand, not the M6 scaffold restyled |
| D | **Website deployment** | C | Production configuration ready for reflux.rsxdevelops.dev on Vercel, with the real DNS requirement read from Vercel; any owner action written down exactly |
| F | **Windows libmpv from source** | — | mpv, FFmpeg, and every library in the DLL built by Reflux from pinned, checksummed sources, LGPL, with the source bundle and notices; validated on Windows |
| E | **CI and release automation** | A, F | The first real CI pipeline and a release rehearsal from a clean clone: tests → build → artifacts → checksums → provenance → independent verification |
| G | **Google Play readiness** | A | Final package, target SDK, AAB, signing and upload-key plan, permissions, exported components, network, backup, logging, R8, endpoints, listing material; current requirements read |
| H | **Android developer verification** | A, G | What `dev.rsxdevelops.reflux` needs registered, and by whom |
| I | **Amazon / Fire TV readiness** | A, B | Package, signing, compatibility, listing material, and the physical stick re-run for M7's changes |
| L | **TMDB decision gate** | external | Recorded as it stands; never inferred |
| M | **Third-party, legal, and security review** | B, C, F | Inventories cover the new components; the website and app re-checked against M6's guarantees |
| N | **Documentation and the M8 entry checklist** | all | This section, RELEASE.md, VERIFICATION.md, DECISIONS.md, the changelog, and the other documents agree with what was done |

### Hard acceptance gates

M7 is complete only when every gate holds, with its evidence in VERIFICATION.md:

1. The canonical Android identity is `dev.rsxdevelops.reflux`.
2. The brand mark was explored, chosen (or recommended, awaiting the owner where a visual decision is genuinely theirs), documented, and its production assets prepared.
3. The website is a redesigned product site.
4. The site is production-ready, with any deployment blocker written down as an exact owner action.
5. CI can run the intended checks from a clean checkout.
6. Release artifacts have verifiable checksums and provenance (signed attestation where the platform allows).
7. The Windows libmpv build and its source and license distribution are ready for public Windows distribution.
8. The deferred subtitle observations and intermittent tests are fixed or classified with evidence.
9. The diagnostics export is implemented and its redaction tested.
10. Play, Amazon, and developer-verification readiness are established for the final package.
11. TMDB is recorded as approved, licensed, disabled pending resolution, or still externally pending; never approved by inference.
12. The M8 entry checklist (below) exists, and each item is PASS or a named owner/external action.

### Gates at closure (2026-11-09)

Evidence: VERIFICATION.md, "M7".

| Gate | Result |
| --- | --- |
| 1. Android identity | **Pass.** `dev.rsxdevelops.reflux` in the release APK and AAB, checked by the release tool and CI |
| 2. Brand | **Pass.** Six exploration rounds; the Reflux Loop chosen, kept by the owner, given its motion; every platform's assets from one geometry (`design/brand/`) |
| 3. Website redesigned | **Pass.** Website 2.0, prerendered, in the brand, with real product renders |
| 4. Website production-ready | **Pass, deployment is an owner action**: the Vercel connection, Node 22.x, the domain, and the Cloudflare CNAME (WEBSITE.md, "Deployment") |
| 5. CI from a clean checkout | **Pass locally** (the Windows and Linux checks and the release rehearsal, from clean clones, final on `cfb4865`; the Linux real-window step caught a reading-order regression in About, fixed in `253f21f`); the first hosted run needs the push |
| 6. Checksums and provenance | **Pass locally** (`SHA256SUMS`, in-toto provenance, `release.py verify`, tamper fails); GitHub's signed attestations come from `release.yml` on every push (changed at closure: no tag needed) |
| 7. Windows libmpv | **Pass.** Reflux's own reproducible LGPL build, its source bundle beside each Windows release, per-library notices |
| 8. Subtitles and intermittents | **Pass.** Subtitle after seek fixed and tested on the phone; replay-Off classified (no per-work memory; an M8 decision); `LibraryDepthUiTest` fixed; `SourcesUiTest` not reproduced in 16 rounds |
| 9. Diagnostics export | **Pass.** The support report, redaction tested, never sent by Reflux |
| 10. Store and verification readiness | **Pass, the console steps are owner actions** (RELEASE.md, "Store readiness (M7)", "Android developer verification (M7)") |
| 11. TMDB | **Externally pending.** Working classification: non-commercial under TMDB's published definition; formal clarification request pending. Attribution implemented; on in public builds when the build environment supplies the credential; reconciled when TMDB answers (DECISIONS.md) |
| 12. M8 entry checklist | **Pass.** Below; every item PASS or a named owner or external action |

### Known external dependencies

Nothing here blocks the rest of M7; each is prepared up to the action itself.

| Dependency | Whose | Blocks |
| --- | --- | --- |
| Pushing the branch to GitHub (CI's first hosted run, the release workflow's signed attestations, the Vercel Git integration) | The owner | Gate 5's hosted run, gate 6's GitHub attestations, gate 4's deployment |
| The DNS record at Cloudflare for reflux.rsxdevelops.dev | The owner | The live domain |
| Play Console: account type (personal or organization), developer verification status, the app created as `dev.rsxdevelops.reflux` | The owner | The closed-testing plan; the listing |
| Amazon developer console: the app created | The owner | The Fire TV listing |
| The upload key (made by the owner, kept offline and in GitHub's secrets) | The owner | Any build given to a store |
| TMDB's answer to the clarification request (working classification: non-commercial) | TMDB | Nothing now; a commercial answer would mean a licence or turning it off (DECISIONS.md) |
| A support email address and a donation destination | The owner | The support page's contact line; "Support Reflux" stays unpublished until then |

### Moved out of M7

The roadmap of 2026-11-02 placed these in M7. M7's scope (above) is release readiness, not features or broad hardware campaigns, so they move; none is dropped:

| Item | Now | Why |
| --- | --- | --- |
| Playback parity: held seek acceleration, chapters on Android where Media3 supplies them, media keys on desktop, an explanation on television where sidecar subtitles cannot be read | M8, each by an explicit decision before M9's feature freeze, or V1.x | Features; M7 adds none |
| A physical Google TV / Android TV device; the Fire TV television; physical Xbox and PlayStation controllers | M8 (the beta's device matrix: LAUNCH.md asks for ≥3 Google TV and ≥3 Fire TV testers and ≥2 with controllers), required by the V1 acceptance criteria before M10 | Hardware the project does not hold; the testers bring it |
| Clean-machine desktop installs; network failure and recovery on real networks; broader compatibility (other Jellyfin versions, NAS SMB servers, HDR/DV and passthrough on real receivers, physical Linux) | M8 to M9, recorded in VERIFICATION.md as testers' setups allow; V1 acceptance criterion 12 | As above |
| A large-library performance baseline; a seeded stress harness; image-baseline visual regression | M8 | Engineering hardening that does not gate the beta's entry |
| TMDB against the live service in a release build | M8, once the build environment holds the credential (attribution is done) | Needs the owner's secret |
| The demo video (LAUNCH.md, W2) | M8, before the beta's public page | Needs the owner's chosen video tool and approval |
| Remembering a viewer's subtitle choice per work (the Fire TV replay observation) | M8, as a product decision | A feature; M7 adds none |
| Replacing the deprecated `Modifier.onFocusedBoundsChanged` | M8 | Its regression test still guards it |

### M8 entry checklist

Each item is PASS (with its evidence) or an owner/external action that must happen before M8 begins. The states are M7's closure (2026-11-09).

| Item | State |
| --- | --- |
| Package identity `dev.rsxdevelops.reflux` | **PASS** (VERIFICATION.md, M7) |
| Signing: upload key made, in GitHub secrets (`REFLUX_UPLOAD_*`) and an offline backup | **Owner action** (RELEASE.md, "Signing") |
| Developer verification for the final package | **Owner action**: confirm the account's verification and register `dev.rsxdevelops.reflux` (RELEASE.md, "Android developer verification (M7)") |
| Play account type known (closed-testing rule) | **Owner action** |
| Play app created as `dev.rsxdevelops.reflux`; listing material | **Owner action** to create; the material is **PASS** (`release/store-listing.md`, `design/brand/store/`) |
| Tester requirement (≥12 testers for 14 days, if the account is personal) planned | **PASS** as a plan (RELEASE.md, LAUNCH.md); recruiting is M8 |
| Amazon app created; readiness | **Owner action** to create; readiness **PASS** (RELEASE.md, "Store readiness (M7)") |
| TMDB | **External**: working non-commercial classification, clarification pending; **owner action**: the `REFLUX_TMDB_TOKEN` secret |
| Windows libmpv source build and source distribution | **PASS** |
| Website deployed at reflux.rsxdevelops.dev over HTTPS | **Owner action**: the Vercel connection (Node 22.x), the domain, the Cloudflare CNAME (WEBSITE.md, "Deployment") |
| CI green on GitHub | **Owner action**: push; the local clean-checkout proof is **PASS** |
| Provenance and attestation | **PASS** locally; the GitHub attestations follow the push |
| Diagnostics export | **PASS** |
| Stability: the M6 intermittents classified | **PASS** |
| Branding in the apps, the site, and the store material | **PASS** |
| Release artifacts from a clean clone, verified | **PASS** (`4d6538e`) |
| Support, legal, and privacy pages | **PASS**; **owner action**: a support e-mail address for the contact line |
| Final physical-device evidence for M7's build | **PASS**: Fire TV Stick (`4d6538e`); Phone 2a (`557a54b`) and Pad 7 (`5a55a0a`, then `11ec1af`), with the animated launch screen; two defects found and fixed (VERIFICATION.md, "M7: the release on the Phone 2a and Pad 7") |
| Casting feasibility gate decided (DECISIONS.md, 2026-10-17) | **Open**, decided at M8's start |

### Reflux Website — Public Release & Tester Readiness (workstreams C and D)

M7 turns the M6 foundation into the production website used for the beta and for 1.0.

**Redesign (deferred from M6, 2026-11-08):** the owner reviewed the M6 foundation: its structure and function (routes, release data, download routing, legal pages, security headers) are useful, but its visual and product presentation is below Reflux's quality bar. M7 redesigns and polishes the site's visual system and presentation to the direction in LAUNCH.md ("Visual direction"), on top of the M6 foundation; M6 made no visual changes after the review.

**Public product surface:** what Reflux is; its key features; supported platforms; supported media and source types; compatibility information (from PLAYBACK.md's established boundaries, never claiming more); screenshots and the demo video where they help; Reflux's local-first, source-aware model, explained plainly.

**Tester hub:** what build a tester is testing; how to join the right testing channel; how to install the right version on each platform; known issues; supported platforms; how to report a bug and send feedback. Google Play's internal and closed tests are joined by opt-in links that ordinary search does not find, so the site routes each tester to the right entry point.

**Documentation**, written for the site or linked, without copying the engineering documents wholesale: getting started; setting up sources; compatibility; playback limitations; FAQ and troubleshooting; privacy and legal; support and feedback; release notes and the changelog.

**Release information:** the current stable version; the current beta or release status; release notes; what is available to test or install, where; supported platforms; known limitations.

**Before M8:** final security and accessibility checks of the site, and its production validation on desktop and mobile browsers.

## M8 — Beta

Future. `0.9.0-beta.1`, then `beta.2`, `beta.3`, … Entry requires the beta checklist in RELEASE.md, the website ready to serve testers (below), and the casting feasibility gate decided (time-boxed; V1.1 if it fails; DECISIONS.md, 2026-10-17, "Casting is a V1 feasibility gate").

- Google Play closed testing track; Amazon Appstore testing (Live App Testing) for Fire TV; desktop builds from the website.
- About 15–20 testers, recruited through the website's beta signup, covering phones, tablets, Google TV, Fire TV, Windows, and Linux, local folders and servers.
- The website operating as the testers' entry point: announcements per build, test instructions, known issues kept current, and the feedback loop.
- A feedback path (in-app link to a form or email, a diagnostics export) and a public changelog per beta.
**The website before the beta.** M8 does not begin until the official website can:

1. explain Reflux clearly;
2. show the supported platforms;
3. provide, or route testers to, the right test build;
4. give tester instructions;
5. show known limitations;
6. offer a feedback channel;
7. show the required privacy and legal information;
8. show the right current version and release status;
9. work in desktop and mobile browsers;
10. serve over HTTPS on a stable public domain.

It does not have to be visually finished; it has to be operationally trustworthy. A placeholder page is not enough when the site is the testers' entry point.

- Iteration: bug fixes, compatibility, UX, performance, accessibility. New features enter only by an explicit decision recorded in DECISIONS.md; otherwise they wait for V1.x. **Exit:** no open blocker or critical bug, the V1 acceptance criteria hold, and the last beta ran at least a week without a new critical report.

## M9 — Release Candidate

Future. `0.9.0-rc.1`, then `rc.N` only for fixes. Feature freeze, final validation on every target, store listings and submissions prepared, legal pages final; the website presents the release candidate and its notes.

## M10 — 1.0

Future. `1.0.0`, built from the final release candidate's commit with only the version changed ([VERSIONING.md](VERSIONING.md)). Google Play (phones, tablets, Google TV), Amazon Appstore (Fire TV on Fire OS), Windows, Linux; reflux.rsxdevelops.dev in its 1.0 state: stable store and download links, final documentation, public release notes, and support and legal pages kept current.

## The Reflux website

A required product and release surface, not marketing and not optional. Before the beta, testers need one stable public place to learn what Reflux is, which platforms and sources it supports, what version is being tested and how to get it, how to install it, what its limits are, where its compatibility information and documentation are, how to report a problem and send feedback, what its release notes say, how it handles privacy and security, and where its official support is. It is a workstream across milestones, with no milestone of its own.

### Stages

| Stage | When | What | LAUNCH.md's deliverable |
| --- | --- | --- | --- |
| Planning | During M3 to M5 | Lightweight: what the site must say, as navigation, sources, the visual system, and the library settle. Nothing here blocks product work | — |
| Foundation | M6 | Architecture, domain and hosting, deployment, legal and privacy infrastructure, content model, documentation structure, support and feedback infrastructure, the source of release and version data | W0 foundation; W1 a first public page on it |
| Public and tester readiness | M7 | The production site, product pages, compatibility information, routing to downloads and tests, the tester hub, documentation, feedback and support, release notes, final security and accessibility checks, production validation | W2 the demo video; W3 the beta launch content |
| Beta operation | M8 | The live entry point for testers, build announcements, test instructions, known issues kept current, the feedback loop | W3 in operation |
| Release | M9, M10 | Release candidate and 1.0 presentation, stable store and download links, final documentation, public release notes, support and legal pages maintained | W4 the 1.0 site |

The W labels name the website's deliverables in LAUNCH.md; the milestones say when they are due.

### How it relates to the product milestones

- M3 defines navigation and the source information architecture inside Reflux; M4 establishes the product's visual system, which the site may later use; M5 develops the product and library story it tells. None of them waits for the website, and the website's design is not M3's.
- M6 builds the foundation and the legal and release infrastructure; it is not a product-design milestone. M7 brings the site to polished public and testing readiness. M8 requires it.

### Structure

The target routes, so that the site is planned and does not become a landing page assembled before the beta. Not all are needed at once:

Home · Features · Platforms and compatibility · Sources · Download and test · Documentation · Changelog and releases · FAQ and troubleshooting · Support and feedback · Privacy · Terms or EULA where applicable · Third-party notices where applicable

### Sources on the site

Reflux's sources are local folders, Jellyfin, Plex (M3), SMB, WebDAV, and adapters to come. The site documents each supported source and its compatibility boundaries, and never calls a source supported before the app supports it.

### Feedback

A deliberate path, the simplest that can be kept up, chosen in M6 and M7 from: GitHub issues where appropriate, a feedback form or service, a support email, Google Play's testing feedback, and tester documentation. No custom backend only to collect feedback. The app's own feedback link and redacted diagnostics export (M6) point to the same place.

### Privacy

The site adds no tracking it does not need. If analytics are proposed, the proposal says exactly what is collected, why, whether it is necessary, which third party receives it, and how it changes the privacy disclosures; the default is none. The site's data practices match the app's privacy model and its store disclosures.

### Distribution

- **The website** is the canonical public source of information.
- **The app stores** are the canonical distribution and update channel where they exist: Google Play (phones, tablets, Google TV) and the Amazon Appstore (Fire TV).
- **Tester channels:** Google Play's internal, closed, or open tests and Amazon's Live App Testing.

The site routes people to the right channel and does not mirror store packages. Windows and Linux have no store, so their signed, checksummed builds are downloaded from the site, by the existing decision (LAUNCH.md, "Store and distribution tracks"); any other mirroring needs its own decision.

## Earlier numbering

What each milestone of the 2026-10-17 roadmap became. Older documents and VERIFICATION.md sections keep the labels they were written with.

| 2026-10-17 | Now |
| --- | --- |
| M0 — Rebaseline & governance | M0 — Governance & Rebaseline |
| M1 — Product completeness | Its accessibility baseline is M1. Its other items are recorded below; each open one moved to a milestone |
| M2P — Playback compatibility & direct play | M2P — Playback Compatibility & Direct Play (unchanged) |
| M2 — Release engineering | M6 — Release Engineering / Security / Legal |
| M3 — Real-world validation | M7 — Compatibility / Performance / Release Proof |
| M4 — Beta, M5 — Beta iteration | M8 — Beta |
| M6 — Release candidate | M9 — Release Candidate |
| M7 — Public 1.0 | M10 — 1.0 |

### The product-completeness record (the 2026-10-17 M1)

What V1 contains, as built from 2026-10-17 to 2026-11-01. Each row is required for V1 unless marked as a gate.

| Item | State | What is left, and where it is now |
| --- | --- | --- |
| Automatic organization and identification | Done | — (episodes without a show name are grouped per folder and offered to Identify, 2026-10-18) |
| Identify / fix match | Done (automated) | Built on every form factor (the shared experience layer): search, results told apart, choose, undo, reached from every details page and offered plainly where Reflux is unsure (DECISIONS.md, 2026-10-18). Verified with fixtures on the desktop renderer, including a remote-only television path. Left: live TMDB and a device run with the credential (M7). Episodes' own numbers cannot be corrected from it (their show can) |
| Continue Watching and Next Up | Done | — |
| **Watch Next** (the platform's own "continue" row) | Done (emulator) | Android TV / Google TV: programs derived from Continue Watching and Next Up, added, updated, and removed with watch state, opening Reflux on the work (DECISIONS.md, 2026-10-18). What Google TV's launcher draws is unseen: it shows only certified integrations, so Google's Watch Next certification belongs to the Play submission (M6), and a physical Google TV check to M7. Fire TV: no home-screen row, because Amazon's Watch Activity needs a content catalog Reflux does not have, and no programs written to Fire OS's TV provider (DECISIONS.md, 2026-10-18) |
| Cross-source watch state | Done | — ("From the start" keeps the place, 2026-10-17) |
| Best Version | Done | Desktop capabilities are observed since 2026-10-19 (DECISIONS.md, "How the desktop observes what it can show and decode"); playback since M2P. HDR output on the desktop is not claimed: the engine does not output HDR yet (M7) |
| Visible version selection | Done | Films and episodes list every version and play any of them; an episode's are on its own page, offered from its row where it has more than one (DECISIONS.md, 2026-10-18) |
| Version pinning | Done | Films (2026-10-17) and episodes (2026-10-18): "Always play this" and "Let Reflux choose"; a pin set aside is said and kept; an episode's pin follows it through Identify |
| Smart Offline, user-facing | Done | Films, single episodes, whole seasons, and a show's next episodes; a show or season counted per episode; a Downloads page; Windows and Linux wired like Android (DECISIONS.md, 2026-10-18). Downloads navigation: done in M3 |
| Serious playback parity | Partial | Moved to M5, then to M7 (2026-11-07) |
| Jellyfin correctness and security | Partial | Removing a server signs its session out since 2026-10-19 (DECISIONS.md, "How a Jellyfin server is signed out of"; verified live). The plain-HTTP policy on Android moved to M6 |
| Source onboarding and management | Done | WebDAV can be added on Android since 2026-10-19 (DECISIONS.md, 2026-10-18, "Web shares on Android"). Source navigation and inclusion: done in M3 |
| Android phone and tablet | Done (emulator); playback physical (M2P) | Physical validation across the product: M7 |
| Android TV / Google TV | Done (emulator) | Physical validation: M7 |
| Fire TV / Fire OS | Partial (one physical device) | Smoke-tested on a Fire TV Stick 4K Plus (`AFTMA08C15`, Fire OS 8.1.8.5, API 30, 32-bit userland) on 2026-10-18 with a debug build; five Fire OS defects found and fixed (DECISIONS.md, 2026-10-18); playback validated in M2P. A release build validated on it 2026-11-08 (M6); Amazon's criteria prepared, not yet submitted (RELEASE.md). The rest is M7 (hardware). Fire OS has the TV provider, and Reflux writes no Watch Next programs to it; with Reflux in the background a remote's Play key is not obeyed (both DECISIONS.md, 2026-10-18). Fire OS 6 devices (`AFTMM`, API 25) are below `minSdk`; Vega OS devices (`AFTCR001`) do not run Android applications |
| Windows and Linux | Partial | Capabilities observed since 2026-10-19. Media keys, scrollbars, and HDR output moved to M7 |
| Accessibility baseline | Done | Now M1 |
| About, legal, and attribution | Done 2026-10-19; two parts wait elsewhere | About Reflux: version and build, Reflux's copyright (proprietary, all rights reserved; DECISIONS.md, "Reflux is proprietary"), the metadata provider's credit, what Reflux keeps and sends, and every third-party license text, checked against each build by `check` (DECISIONS.md, 2026-10-19). Waiting: the privacy policy and support links (the website: M6 foundation, live by M8) and libmpv's notice for a bundled build (M6) |
| Casting | Gate | Decided before M8 (above) |

The 2026-10-17 M1 was built in slices, recorded in VERIFICATION.md: "From the start" and film version pins (2026-10-17); Watch Next and the Fire TV home-screen decision; the Fire TV smoke test; Identify and folder grouping; Fire OS Watch Next and an episode's versions; Smart Offline complete (all 2026-10-18); WebDAV on Android; Jellyfin sign-out on removal; desktop capabilities observed; About, legal, and attribution (all 2026-10-19); then accessibility (2026-10-31 to 11-01).

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
10. **Legal and honest.** Reflux's copyright notice, third-party notices (including libmpv/FFmpeg), TMDB attribution, a privacy policy, and a support contact are in the app and on the website.
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
- Emby and DLNA sources. (Plex is done, in M3.)
- Linux credential storage without a Secret Service.

## Future

- Vega OS client (a compatibility track; evaluation first).
- Device handoff and synchronization of watch state between devices without a server.
- Recommendations, natural-language search, and higher-level library intelligence, above the deterministic engine.
- Advanced playback (picture controls, per-title audio/subtitle styling, trip mode for offline).
- Reflux Pro: possibly, for some advanced capabilities after V1, never by taking anything away from V1. Nothing is decided about it; 1.0 is free and supported by donations (DECISIONS.md, 2026-10-19).
- iOS / iPadOS.

## Not planned

Social features, Watch Together, a Reflux streaming service, a mandatory cloud backend or account, AI required for basic features, server administration, and a transcoding stack of Reflux's own unless a product requirement justifies one.
