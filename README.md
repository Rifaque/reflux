# Reflux

> A universal, local-first media player built around your media, not your media server.

Free and open-source software under the GNU GPL, version 3 or later ([License](#license)).

Reflux is a media player on steroids: a cross-platform media experience that automatically understands, organizes, and plays media from local storage and remote sources without making the underlying filesystem conform to the application.

## Product principles

- **Player first.** Playback quality is the center of the product.
- **Local first.** A user's files, library state, metadata cache, and core experience should remain useful without an account or a live server.
- **Source agnostic.** Local files, Jellyfin, Plex, network shares, and future sources are adapters into one universal media model.
- **Automatic by default.** Reflux should identify media, fetch artwork and metadata, and build a useful library with minimal setup.
- **Excellent out of the box.** Defaults must be genuinely good. Customization deepens the product; it must not be required to make the default experience good.
- **Virtual organization.** Reflux organizes media without renaming, moving, or modifying the user's files.
- **Simple by default, deep when needed.** Everyday playback should be clean. Advanced controls should be available without overwhelming the default UI.
- **Input adaptive.** Touch, mouse, keyboard, controllers, and TV remotes are first-class input methods.
- **No mandatory account.** Core local functionality must not depend on Reflux cloud services or login.
- **Platform native, product consistent.** Phone, tablet, TV, desktop, and future platforms should adapt to their ergonomics while sharing the same product model.

## Core experience

Point Reflux at a media folder.

Reflux should be able to:

1. scan and watch the folder,
2. recognize movies and TV shows from ordinary filenames,
3. retrieve and cache artwork and metadata,
4. build a virtual library,
5. remember watch and resume state for the work itself, so that it continues from any copy on any source,
6. select the best available version,
7. play the media with a serious playback stack,
8. keep the library useful when a source temporarily disappears.

## Architecture direction

The product is organized around a **Media Core** rather than any single server.

```text
                    Reflux
                       |
                   Media Core
                       |
        +--------------+--------------+
        |              |              |
      Local     Jellyfin / Plex   Network Sources
        |                              |
      Files                    SMB / WebDAV / ...
```

UI and playback features should consume normalized media entities and source capabilities rather than being tightly coupled to one backend.

## Project status

Pre-release, in a beta since 17 November 2026; Beta 2 is finished, accepted and frozen, awaiting its release gate (`0.9.0-beta.1`: Play closed testing, Amazon Live App Testing, and a public GitHub pre-release for Windows and Linux). The engine (Media Core, sources, identification, metadata, watch state, Best Version, playback, Smart Offline) is implemented and heavily tested, and one experience layer runs on desktop (Windows, Linux), Android phones and tablets, Android TV / Google TV, and Fire TV: point it at a folder or connect a Jellyfin or Plex server or a network share, browse, search, play, and resume on any copy, with a profile of their own for each person who watches. Release builds have been verified on a physical Nothing Phone 2a, Xiaomi Pad 7, and Fire TV Stick 4K Plus, on Windows, and on Linux in a container (docs/verification/VERIFICATION.md). V1 targets Android phones and tablets, Android TV / Google TV, Fire TV (Fire OS), Windows, and Linux, through a public beta first. The Android application ID is `dev.rsxdevelops.reflux`. The product plan is [docs/ROADMAP.md](docs/ROADMAP.md) and the beta in progress [docs/release/BETA.md](docs/release/BETA.md); the current version and how versions work are in [docs/release/VERSIONING.md](docs/release/VERSIONING.md); the brand is in [design/brand](design/brand/README.md); the website is [docs/release/WEBSITE.md](docs/release/WEBSITE.md).

## Documentation

Which document holds what, and which one wins when two seem to disagree. Each fact has one home; other documents link to it rather than repeat it. Agents start with [AGENTS.md](AGENTS.md) (and Claude Code with [CLAUDE.md](CLAUDE.md)).

`docs/` holds two live documents at its root, [the roadmap](docs/ROADMAP.md) and [the bug tracker](docs/BETA-BUG-TRACKER.md), and five folders: `product/` (what Reflux is and how it behaves), `engineering/` (how it is built), `release/` (the beta, releasing, versions, legal, the website), `decisions/` (the decision log), and `verification/` (what was run, and the audit and validation reports). `history/` is the past: completed plans, handovers and older records, never current.

### Start here

1. [README.md](README.md) (this file): what Reflux is, how to build it.
2. [product/PRODUCT.md](docs/product/PRODUCT.md): the product promise, pillars and non-goals.
3. [ROADMAP.md](docs/ROADMAP.md): where Reflux is, the V1.0 criteria, and the plan from V1.1 to V2.0.
4. [release/BETA.md](docs/release/BETA.md): the beta in progress, its gate, and its known issues.
5. [engineering/ARCHITECTURE.md](docs/engineering/ARCHITECTURE.md): the layers and their boundaries.

### The documents

| Area | Document | Authoritative for |
| --- | --- | --- |
| Status and issues | [docs/ROADMAP.md](docs/ROADMAP.md) | Product status, V1.0 criteria, versions after 1.0, the public roadmap's source |
| | [docs/BETA-BUG-TRACKER.md](docs/BETA-BUG-TRACKER.md) | **Every issue's status** and evidence (B-001 onward, F-001 onward) |
| Product | [product/PRODUCT.md](docs/product/PRODUCT.md) | Positioning, pillars, the default-experience requirement |
| | [product/PLAYBACK.md](docs/product/PLAYBACK.md) | Capabilities, the planner, execution, diagnostics, Trickplay |
| | [product/INPUT_MODEL.md](docs/product/INPUT_MODEL.md) | Semantic actions, controller mapping, text entry, haptics |
| | [product/NAVIGATION.md](docs/product/NAVIGATION.md) | Destinations, back stack, source libraries, the player's presentations |
| | [product/METADATA.md](docs/product/METADATA.md) | Identification, providers, artwork |
| | [product/WATCH_STATE.md](docs/product/WATCH_STATE.md) | Watch state, versions, handoff, profiles |
| | [product/OFFLINE.md](docs/product/OFFLINE.md) | Smart Offline |
| | [product/DESIGN.md](docs/product/DESIGN.md) | The visual system and motion |
| | [product/V1.1-LIBRARY-RESEARCH.md](docs/product/V1.1-LIBRARY-RESEARCH.md) | The researched V1.1 library work (not to be built without the owner) |
| Engineering | [engineering/ARCHITECTURE.md](docs/engineering/ARCHITECTURE.md) | Layers, Media Core, local-first state, sources, the module layout |
| | [engineering/CI-PERFORMANCE-AUDIT.md](docs/engineering/CI-PERFORMANCE-AUDIT.md) | The CI workflows' performance baseline, bottleneck analysis and optimisation record: what was measured, changed and deferred, and why |
| Beta and release | [release/BETA.md](docs/release/BETA.md) | The beta programme: builds, Beta 2's scope and gate, known issues, beta operations |
| | [release/BETA-NOTES.md](docs/release/BETA-NOTES.md) | What testers read about each beta |
| | [release/BETA-3-TESTING.md](docs/release/BETA-3-TESTING.md) | **The testing freeze from 2026-11-27**: what is under test, how to build it, what to test, the known issues, how findings are triaged |
| | [release/RELEASE.md](docs/release/RELEASE.md) | The release process, platform matrix, signing, artifacts, checklists |
| | [release/VERSIONING.md](docs/release/VERSIONING.md) | Version numbers, tags, the changelog (mandatory before any version change) |
| | [release/PRIVACY.md](docs/release/PRIVACY.md) | What is kept and sent; store disclosures |
| | [release/THIRD-PARTY.md](docs/release/THIRD-PARTY.md) | What each artifact ships and under which licence |
| | [release/WEBSITE.md](docs/release/WEBSITE.md) | How the site is built, checked and deployed |
| | [release/](release/README.md) (repository root) | Store runbooks, store listing text, publication records |
| | [CHANGELOG.md](CHANGELOG.md) | User-visible changes per version: the release notes' source |
| | [SECURITY.md](SECURITY.md) | Reporting vulnerabilities |
| | [store-assets/](store-assets/release-0.9.0-beta.2/README.md) | Store screenshots and listing graphics per release, with provenance |
| Decisions | [decisions/DECISIONS.md](docs/decisions/DECISIONS.md) | Durable product and architecture decisions (append-only; superseded entries say so in place) |
| Verification | [verification/VERIFICATION.md](docs/verification/VERIFICATION.md) | **The verification log**: what was run, on what, with what result (Beta 2 onward) |
| | [verification/LINUX-PLAYBACK-ACCEPTANCE.md](docs/verification/LINUX-PLAYBACK-ACCEPTANCE.md) | **The Linux playback acceptance procedure**: the checks every Linux desktop must pass, and what depends on the distribution, desktop and GPU |
| | [verification/UBUNTU-VALIDATION-REPORT.md](docs/verification/UBUNTU-VALIDATION-REPORT.md) | The Linux player's validation on Ubuntu 26.04.1 (2026-11-26) |
| | [verification/BETA-3-INTEGRATION-AUDIT.md](docs/verification/BETA-3-INTEGRATION-AUDIT.md) | The Beta 3 integration, the Windows CI diagnosis (B-105), its tests (2026-11-26) |
| | [verification/CACHYOS-FINAL-AUDIT-LEDGER.md](docs/verification/CACHYOS-FINAL-AUDIT-LEDGER.md) | The CachyOS final audit (2026-11-24 and 25): issues, evidence with its screenshots, closure, and the owner decisions it left open |
| History | [history/](docs/history/README.md) | Archived plans, milestone and phase records, the CachyOS and Ubuntu handovers, older verification (historical, never current) |

### When documents disagree

The more specific record wins: an issue's status is the tracker's; a run's result is VERIFICATION.md's (a report's detail is the report's); a decision is DECISIONS.md's latest entry on it; product status is ROADMAP.md's; anything under `docs/history/` is the past. Fix the other document rather than live with the disagreement.

### Checking the documentation

`python -I tools/docs/check_links.py` checks every relative link and `#anchor` in the repository's Markdown (CI runs it). The website has its own link check (WEBSITE.md, "Checks").

## Development

Reflux is written in Kotlin (Multiplatform). Gradle is provided by the wrapper, and the wrapper downloads JDK 21 if the machine has none.

```sh
./gradlew check        # build and run all tests
./gradlew :core:jvmTest
```

See [docs/engineering/ARCHITECTURE.md](docs/engineering/ARCHITECTURE.md#implementation) for the module layout.

### Desktop app

```sh
./gradlew :desktop:app:run
```

Choose a media folder on first run, or connect a Jellyfin or Plex server or a network share from the same screen; everything else is automatic. Mouse, keyboard, and game controllers (Xbox, PlayStation, Switch Pro, and generic pads) all work without setup; picking up a controller reshapes the interface for it. Playback needs libmpv (below); without it the library is still fully browsable. Titles, artwork, and details come from Trakt when a Trakt Client ID is given (`REFLUX_TRAKT_CLIENT_ID`, or `-Preflux.trakt.clientId=<id>` when building); without one Reflux shows what it identified from file names and what your servers say. Library state lives in the platform data directory (override with `REFLUX_HOME`), never in your media folders; problems are written to `reflux.log` there.

### Developer CLI

`tools/cli` runs the same engine from a terminal, for diagnosing identification, metadata, and playback on real libraries: it scans folders (read-only), probes files with libmpv when installed, fetches metadata when `REFLUX_TRAKT_CLIENT_ID` is set, and plays in an mpv window.

```sh
./gradlew :tools:cli:installDist
tools/cli/build/install/reflux/bin/reflux add ~/Videos
tools/cli/build/install/reflux/bin/reflux search "4k movies I haven't watched"
tools/cli/build/install/reflux/bin/reflux play breaking bad
tools/cli/build/install/reflux/bin/reflux doctor
```

Playback and probing need libmpv (`libmpv2` on Debian/Ubuntu, `mpv-libs` on Fedora, `libmpv-2.dll` on Windows). When running from Gradle, a library placed in `.reflux/native/` (ignored by Git) is found without installing it system-wide. Nothing compiles against it: without libmpv everything builds, the tests that need an engine skip themselves, and the application browses but does not play. Shipping it is a release question, covered in [docs/release/RELEASE.md](docs/release/RELEASE.md). To see playback from the engine's side, set `REFLUX_MPV_LOG` to a file (or a folder, for a file per engine) and the engine's verbose log is written there, with credentials removed.

## License

Reflux is free and open-source software: Copyright © 2026 RSX Develops, licensed under the **GNU General Public License, version 3 or later** (`GPL-3.0-or-later`). The license is [LICENSE](LICENSE), unmodified; Reflux's notice, with its additional terms under section 7 (no right to the Reflux name or logo; modified versions marked as changed), is [NOTICE](NOTICE). You may use, study, change, and share Reflux under those terms. Reflux costs nothing, and no payment or donation unlocks anything.

The third-party software Reflux is built with keeps its own licenses, listed with their texts in [legal/](legal) and on Reflux's About page; the services it works with (Trakt, Jellyfin, Plex) and their names are their owners'.

## Contributing, problems, and security

- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md).
- Problems and feedback: [an issue](https://github.com/Rifaque/reflux/issues), or support@rsxdevelops.dev ([support](https://reflux.rsxdevelops.dev/support)).
- Security problems: privately, as [SECURITY.md](SECURITY.md) explains, never in a public issue.
