# Reflux Roadmap

The product roadmap: where Reflux is, what 1.0 must be, and how it grows after 1.0. It is the internal, detailed plan; the website's public roadmap is derived from it ([the public roadmap source](#public-roadmap-source), below) and says nothing this file does not.

- **The beta in progress** (Beta 2's scope and gate, Beta 3, the release candidate) is [BETA.md](BETA.md). Each issue's detail is the [tracker](planning/BETA-BUG-TRACKER.md).
- **The milestones that built Reflux** (M0 to M13, with every item and limit) are archived unchanged in [history/roadmaps/ROADMAP-M0-M13.md](history/roadmaps/ROADMAP-M0-M13.md). M0 to M11 are complete; M12 (qualification and beta) continues as the beta in BETA.md, and M13 (release candidate and 1.0) is [V1.0](#v10-stable) below.
- What was run to verify work: [VERIFICATION.md](VERIFICATION.md). Release mechanics: [RELEASE.md](RELEASE.md). Decisions: [DECISIONS.md](DECISIONS.md) (this plan's: 2026-11-21, "The post-beta product roadmap").

Written in Phase 4 (2026-11-21) and replacing the M0–M13 roadmap as the source of truth for product status. **No date is promised.** Versions are numbered by [VERSIONING.md](VERSIONING.md) and cut only when the owner asks.

## Where Reflux is

Reflux is a **beta**. `0.9.0-beta.1` (2026-11-16) is in testing: Google Play closed testing, Amazon Live App Testing (Fire TV), and the Windows zip and Linux tarball as a GitHub pre-release. Beta 2's work (three phases: reliability; controller and input; media experience) is implemented and accepted by the owner, and its scope is frozen; it is not cut yet (BETA.md, "The Beta 2 gate"). Since 2026-11-19 Reflux is free and open source under GPL-3.0-or-later, prepared on the branch `open-source-gpl`.

What Reflux does today, everywhere it runs unless a platform is named (details: ARCHITECTURE.md and the topic documents):

- **The benchmark loop.** A normal folder of films and shows becomes an identified library with metadata and artwork (Trakt, optional), with no account, no `.nfo` files, and no renaming; Identify fixes a weak match.
- **Sources.** Local folders, Jellyfin, Plex, SMB, WebDAV; one work across sources, with Best Version per device; a source that is away stays in the library.
- **Playback.** libmpv on Windows and Linux, Media3 on Android; a deterministic plan (Direct Play first, the smallest conversion when needed) that the player can explain; versions listed, recommended, and pinnable; chapters, skip intro and credits, next episode; Trickplay scene previews from Jellyfin and Plex; a mini-player and picture-in-picture.
- **Watch state.** One state per work, carried between copies, reconciled with Jellyfin and Plex; Continue Watching, Next Up, Watch Next on Android TV.
- **Smart Offline.** Films, episodes, seasons, and a show's next episodes, on Android, Windows, and Linux.
- **Every input.** Touch, mouse, keyboard, Xbox and PlayStation controllers, TV remotes; Reflux's own adaptive keyboard; Media Quick View; rumble on, interface haptics off by default.
- **Profiles.** Each viewer's history, favourites, versions, and server accounts are their own, with optional PINs.
- **Privacy.** Nothing is sent to the people who make Reflux; statistics are off and cannot be sent.

## Platforms

| Target | In V1 | Distribution | Notes |
| --- | --- | --- | --- |
| Android phone and tablet | Yes | Google Play | One package, `dev.rsxdevelops.reflux` |
| Android TV / Google TV | Yes | Google Play | The same package; verified on the Google TV emulator, no physical Google TV device yet |
| Fire TV (Fire OS) | Yes | Amazon Appstore | A separate target with its own store and review, never described as Android TV |
| Windows | Yes | GitHub release (zip) | Unsigned by decision; signing and an installer are decided at gate G-WIN before 1.0 |
| Linux | Yes | GitHub release (x64 tarball) | Needs the distribution's libmpv; further packages only if the beta shows the need |
| Vega OS (newer Fire TV devices) | No | — | Not Android; a future compatibility track, evaluation first |
| iOS / iPadOS, macOS | No | — | Not a V1 commitment |

## Status words

| Word | Meaning |
| --- | --- |
| **Completed** | Built, tested, released or accepted |
| **Current** | Being worked on now |
| **Committed** | Will be in that version; its scope is decided |
| **Planned** | Placed in a version by this plan; scope and order can still change |
| **Under consideration** | A candidate with a placement suggested; needs research or the owner's decision before it is planned |
| **Long-term vision** | A direction, not a plan |

Confidence (high, medium, low) says how likely an item is to stay where it is placed.

## The path

| Stage | Theme | Status |
| --- | --- | --- |
| Alpha (`0.9.0-alpha.1`, `.2`; October 2026) | The engine and one experience layer on five targets | **Completed** |
| Beta 1 (`0.9.0-beta.1`, 2026-11-16) | First build for people outside the project; distribution rehearsed end to end | **Completed** (in testing) |
| Beta 2 | Reliability, controller and input, media experience (Phases 1–3) | **Current**: implemented and accepted; frozen; awaiting its gate |
| Beta 3 and the release candidate | Fix what the beta finds; qualify on the real device matrix | **Committed** (scope comes from the beta) |
| [V1.0](#v10-stable) | Stable: the benchmark loop, excellent on every V1 target | **Committed** |
| [V1.1](#v11-your-library-your-way) | Your library, your way | **Planned** |
| [V1.2](#v12-playback-depth) | Playback depth | **Planned** |
| [V1.3](#v13-more-sources-easier-installs) | More sources, easier installs | **Planned** |
| [V1.5](#v15-the-household) | The household | **Under consideration** |
| [V2.0](#v20-reflux-across-your-devices) | Reflux across your devices | **Long-term vision** |

**Why this order.** 1.0 adds no features: it proves the product that exists. V1.1 comes first after it because organising and browsing a large library is the most-asked gap (the beta's B-027, B-038, B-040) and because it is already researched and needs one schema change. V1.2 deepens playback, Reflux's centre, where the remaining work needs real hardware (receivers, HDR displays). V1.3 widens reach (sources and installation) once the core is settled, since every new adapter multiplies the qualification matrix. V1.5 gathers the household features that depend on V1.1's per-profile settings. V2.0 is reserved for what changes the architecture: state that moves between devices, with no Reflux account.

## Beta maturation (post-alpha to the release candidate)

The beta exists to make 1.0 trustworthy, not to grow it. Its programme, gates and backlog are in [BETA.md](BETA.md); in short:

1. **Beta 2** ships the accepted Phases 1–3 once the gate holds (hosted CI green, the owner's approval and version, release notes, the release workflow).
2. **Beta 3** takes the Beta 3 backlog (B-074 and B-044 test reliability, B-061 the Skia fallback crash, B-056, whatever testers report) and the qualification the project's own hardware cannot give: a physical Google TV device, Fire TV hardware beyond one stick, physical Linux desktops, more controllers.
3. **The release candidate** (`0.9.0-rc.N`) freezes features; blocker fixes only; store submissions (Play's TV review and Watch Next certification, Amazon's review); a targeted re-qualification.
4. **Exit to 1.0:** the [V1.0 criteria](#v10-criteria) hold, no open blocker or critical report, and the last beta ran at least a week without a new critical report.

## V1.0: stable

**Theme:** the benchmark loop, excellent on every V1 target. **Status:** Committed. **Confidence:** high in scope; no date.

**What 1.0 is not:** a feature release. Every V1 function exists in Beta 2. What remains is proof, polish where the beta finds it lacking, and the release surfaces (signed builds, store listings, a stable download path, support).

### V1.0 criteria

1.0 ships when all of these hold on every V1 target, verified as VERIFICATION.md records (the fifteen criteria of 2026-10-17, kept, with the Phase 4 additions marked):

| # | Area | Criterion |
| --- | --- | --- |
| 1 | Core experience | A normal folder of films and shows becomes an identified library with metadata and artwork, with no account, no `.nfo` files, and no renaming; every weak match can be fixed with Identify |
| 2 | Playback | Play picks a version this device plays well, says why, and resumes on any copy of the same cut; "From the start" and pinning behave as WATCH_STATE.md says |
| 3 | Reliability | Watch state survives sources going away or being removed, re-identification, starting over, and syncing; never overwritten by a stale server record |
| 4 | Integration | Watch Next on Android TV / Google TV reflects Continue Watching and Next Up; Fire TV does what Amazon permits, as the decision record says |
| 5 | Offline | Download a film, an episode, or a season; manage every copy from Downloads; play and resume with the source away, on Android and desktop |
| 6 | Sources and security | Folders, Jellyfin, Plex, SMB, and WebDAV are added, signed in again, narrowed, and removed from the app; removing a server ends its session; no credential in a log, a clear-text database, or a report |
| 7 | Input | Touch, pointer, keyboard, Xbox and PlayStation controllers, and the Android TV and Fire TV remotes reach every V1 function, verified with physical hardware |
| 8 | Playback quality | Hardware decoding where the device has it; HDR and Dolby Vision handled honestly per device; audio and subtitle tracks; readable subtitles; chapters; network failures stated |
| 9 | Accessibility | Labels for screen readers, coherent focus order, WCAG AA contrast, system text size, reduced motion |
| 10 | Legal and privacy | Copyright and licence (GPL-3.0-or-later), third-party notices, Trakt's attribution, a privacy policy, and a support contact, in the app and on the website; statistics off, nothing sent before an informed opt-in |
| 11 | Updates | Signed builds from CI; an upgrade from the last beta keeps the library, watch state, sources, profiles, and downloads |
| 12 | Verified for real | The live metadata provider, a physical phone, a physical Google TV device, a physical Fire TV, physical controllers, clean desktop installs, and a large-library baseline, recorded |
| 13 | Out of the box | No V1 function needs a setting changed first; the beta's critical and blocker reports are closed |
| 14 | Profiles | One profile's history, favourites, collections, versions, and accounts are never shown to or sent from another; clearing local history never changes a server |
| 15 | Installation | Every published desktop format installs, upgrades, and uninstalls cleanly on the supported Linux matrix and on Windows |
| 16 | Support (Phase 4) | The website's documentation covers installing, adding each source, playback problems, and reporting a bug; every known issue of the final build is listed; support@rsxdevelops.dev answers |
| 17 | Platform decisions (Phase 4) | G-WIN (Windows signing and installer) decided and applied; the Linux formats decided from the beta's reports; Watch Next certification and both stores' reviews passed |

**Known gaps against these today** (the evidence: BETA.md, "Readiness"): criterion 7 and 12 (no physical Google TV, one Fire TV stick, physical Linux only in a VM), 11 (no upgrade test from beta to beta yet, because no second beta exists), 15 (Linux matrix covered by containers and a VM), 17 (all three open).

## V1.1: your library, your way

**Theme:** a large library organised and browsed the way the viewer thinks of it. **Status:** Planned. **Confidence:** high (researched against the code: [planning/V1.1-LIBRARY-RESEARCH.md](planning/V1.1-LIBRARY-RESEARCH.md)).

| Initiative | User problem | Outcome | Priority | Depends on | Platforms | Risk | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Separate libraries (B-027) | A server's own libraries (a "Marvel" or "Kids" library) disappear into one Movies grid | Each Jellyfin and Plex library browsable beside the unified Movies and Shows | P1 | A many-to-many library membership relation and migration | All | Medium: a migration under profile isolation | Planned |
| Sorting and filtering (B-038) | Title order only; no way to find "unwatched 4K sci-fi" in Movies | Sort by title, year, added, rating, runtime; filter by genre, decade, watched, favourite, resolution/HDR, library; persisted per profile | P1 | Shares smart search's vocabulary; the library filter needs B-027 | All | Low | Planned |
| Collapsible sidebar (F-P1) | The desktop and TV sidebar has no room for libraries, and takes width | A sidebar that collapses to a rail and carries library entries | P2 | B-027 (its main use) | Windows, Linux, tablet, TV | Low: the focus contract must hold | Planned |
| Discovery variety (B-040) | Home's rows are the library's, not the viewer's | "Because you watched", your genres, unwatched favourites, hidden gems; every row says why; deterministic, no AI | P2 | None hard | All | Low | Planned |
| Collections (engine done) | Franchises and personal sets exist in the engine with no screen | Automatic, user, and smart collections browsable and editable | P3 | B-038's filter model for smart collections | All | Low | Under consideration |

**Rationale:** the beta's library findings are the largest group of product requests after 1.0's fixes; all five reuse existing read models; nothing needs new hardware.

## V1.2: playback depth

**Theme:** the player on steroids, where it needs real equipment to prove. **Status:** Planned. **Confidence:** medium (hardware-dependent).

| Initiative | User problem | Outcome | Priority | Depends on | Platforms | Risk | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Audio passthrough | Home-cinema receivers get decoded PCM instead of Dolby/DTS | Bitstream passthrough where the device and receiver support it, observed rather than declared | P1 | Receivers to test with | Windows, Linux, Android TV, Fire TV | Medium | Planned |
| HDR output on desktop | The desktop engine does not output HDR; Reflux says so | HDR10 output where Windows and the display have it on | P2 | libmpv's output path; HDR displays | Windows (Linux later) | High | Under consideration |
| Background audio and downloads on Android | Playback and downloads stop when Reflux leaves the screen | Background audio with a media notification; downloads continuing in a foreground service | P1 | Android's foreground-service rules | Android | Medium: store policy declarations | Planned |
| Desktop media integration | Linux media keys and the systems' global media controls are absent | MPRIS on Linux; the system media controls on Windows | P2 | — | Windows, Linux | Low | Planned |
| Multi-part and disc structures | CD1/CD2 files and BDMV/VIDEO_TS folders are separate or unplayable | One version for a multi-part film; disc folders playable where the engine can | P3 | Identification rules | All | Medium | Under consideration |
| Subtitle styling and picture controls | Subtitles' size and look, and the picture, cannot be adjusted | Per-profile subtitle style; basic picture controls | P3 | — | All | Low | Under consideration |

**Rationale:** these are the playback items repeatedly deferred to "V1.x" in M2P, M7 and M10 for want of hardware; grouping them makes one hardware campaign possible.

## V1.3: more sources, easier installs

**Theme:** reach: more of the media people have, and less friction installing Reflux. **Status:** Planned. **Confidence:** medium.

| Initiative | User problem | Outcome | Priority | Depends on | Platforms | Risk | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Emby and DLNA sources | Emby servers and DLNA devices cannot be added | Two adapters behind the existing source contract | P1 | The adapter contract (unchanged) | All | Medium: each adapter widens qualification | Planned |
| Plex Home users | A Plex Home's managed users cannot be chosen | Managed-user selection per profile | P2 | Profiles | All | Low | Planned |
| Linux packages | A tarball needs manual setup and has no updates | The format the beta's Linux users need (Flatpak is the leading candidate: libmpv inside the runtime, sandboxed, one build for every distribution); signed repositories only if demand shows | P1 if the beta asks | The beta's Linux reports; DECISIONS.md, 2026-11-13 | Linux | Medium: libmpv packaging, the LGPL | Under consideration |
| Desktop updates | Desktop users learn of a new version only from the website | A check for a newer release (opt-in, nothing sent but the request), and an installer where G-WIN chose one | P2 | G-WIN | Windows, Linux | Low | Planned |
| "Open with Reflux" | A loose video file cannot be opened with Reflux | Reflux as a handler for video files, playing outside the library | P3 | — | Windows, Linux, Android | Low | Under consideration |
| Linux credentials without a Secret Service | Minimal Linux desktops cannot keep server sign-ins | An encrypted local store when no Secret Service exists | P3 | — | Linux | Medium: security review | Under consideration |
| Casting | Playing from a phone onto a TV | Decided at the 2026-11-11 gate: not in V1; re-evaluated here against the TV apps Reflux already has | P3 | — | Android | High | Under consideration |

## V1.5: the household

**Theme:** Reflux shared by a household and set up the way each person likes. **Status:** Under consideration. **Confidence:** low to medium.

| Initiative | User problem | Outcome | Priority | Depends on | Platforms | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| Guest mode | A visitor's viewing lands in someone's profile | A guest session wiped on leaving | P2 | Profiles | All | Low |
| Per-profile visibility | Every profile sees every download and source | Per-profile download visibility and source inclusion | P2 | V1.1's per-profile settings | All | Medium |
| Controller remapping (F-005) | Some players want other button assignments | Remapping of semantic actions, with conflict checks and a reset; the default mapping (B-039) unchanged | P3 | INPUT_MODEL.md's mapping and conflict rules | Windows, Linux, Android TV, Fire TV | Medium: the focus contract and hints. The Phase 4 handoff listed F-005 among V1.1 candidates "as the owner places them"; this plan suggests V1.5 because the default mapping is new and should meet testers first. The owner may pull it forward |
| A TV mini-player | On a TV, leaving the player ends what is playing | A mini-player in the TV layout | P3 | M10's session model | Android TV, Fire TV | Medium |
| Vega OS evaluation | Newer Fire TV devices run Vega OS, which cannot run the Android app | A written evaluation: framework, cost, what can be shared; no build is promised | P3 | Amazon's Vega tooling | Vega OS | High |

## V2.0: Reflux across your devices

**Theme:** a viewer's Reflux follows them between devices, still with no Reflux account and no Reflux server. **Status:** Long-term vision. **Confidence:** low; a direction, not a plan.

Why it is a major version: each item changes an architectural boundary (state that leaves the device, identity across devices, other applications driving Reflux), which needs new contracts, conflict rules, and a security review, unlike the V1.x items.

- **Profile transfer:** an encrypted export first, then local-network transfer with QR or code pairing.
- **Synchronisation and handoff:** watch state and preferences between a household's devices; start on one, continue on another.
- **An integration interface** for assistants and other applications, on the seams kept for it: semantic actions, one playback session with presentations, the profile as the identity boundary.
- **Library intelligence above the deterministic engine:** natural-language search and recommendations, never required for the basics.
- **Themes** (deferred from M4: one visual identity until then).
- **New platforms**, each by evaluation: iOS and iPadOS; Vega OS if V1.5's evaluation recommends it.

## Unscheduled, by the owner's decision

- **Video-synchronised haptics (F-003):** deferred by the owner (2026-11-20) as a future experimental feature with no release assigned. Before any placement it needs its own research, design, hardware validation, synchronisation investigation, platform analysis, safety controls, and a test plan (tracker, F-003). It is not on the public roadmap until the owner has reviewed that research.
- **Reflux Pro:** nothing is decided; 1.0 is free (GPL) and supported by donations, and nothing is ever taken from V1.

## Not planned

Social features and Watch Together, a Reflux streaming service, a mandatory account or cloud backend, AI required for basic features, server administration, and a transcoding stack of Reflux's own unless a product requirement justifies one.

## Ecosystem notes (2026-11-21)

Read to check placement, not to copy feature lists. Current Jellyfin and Plex clients (Infuse 8.5, Plezy, Swiftfin) compete on library sorting, filtering and per-library browsing, and Infuse on format support and background downloads; that supports V1.1's placement and V1.2's background work. Live TV, Watch Together and server-side request integrations, common in those clients, stay outside Reflux's product principles (player first, local first) and are not planned. Sources: Firecore's Infuse 8.5 notes (firecore.com/blog/infuse-85-smarter-streaming), the Plezy App Store listing, perfectmediaserver.com's tvOS client guide.

## Public roadmap source

The website's roadmap (`website/src/roadmap.js`) is this plan, simplified. It shows each stage's theme, status, and three to five plain-language items; it never shows an item as done before it is in a downloadable build, never shows F-003, and marks V1.5 and V2.0 as exploration. When this file changes, that file changes in the same commit.
