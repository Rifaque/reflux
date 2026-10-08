# Reflux Roadmap

## Phase 0 — Product and architecture foundation

- Define universal media entities.
- Define source and capability abstraction.
- Define library, metadata, artwork, playback, availability, version, watch-state, download, and search contracts.
- Define normalized input actions.
- Define adaptive UI states.
- Establish default-experience requirements.

## Phase 1 — Visual language and interaction prototype

- Build the liquid-glass + cinematic design language.
- Establish typography, spacing, poster/backdrop treatment, materials, focus states, motion, navigation, player surfaces, and empty/loading states.
- Prototype controller morph transitions before large-scale UI implementation.
- Use a Jellyfin-connected prototype only as a design laboratory if useful; do not make Jellyfin the architecture.

## Phase 2 — Scanner and identification engine

- Filesystem scanning.
- Incremental scanning and file watching.
- Large-library behavior.
- Disconnected-source behavior.
- Filename parsing.
- Movie/show/season/episode identification.
- Confidence scoring.
- Manual "Identify this movie/show" correction.
- Persistent per-item association overrides.

## Phase 3 — Metadata and artwork engine

- Posters, backdrops, logos, titles, synopsis, cast, genres, runtime, release data.
- Seasons and episodes.
- Local caching.
- Offline browsing.
- Provider abstraction.

## Phase 4 — Local media MVP

Target loop:

`folder -> scan -> identify -> artwork -> virtual library -> play`

Initial target:

- movies,
- TV shows,
- seasons,
- episodes,
- search,
- continue watching,
- resume,
- favorites,
- collections,
- cached local metadata.

## Phase 5 — Smart library

- Automatic collections.
- User collections.
- Rule-based smart collections.
- Diagnostics for duplicates, unidentified files, missing episodes, bad metadata, multiple versions, missing artwork, and disconnected storage.
- Opt-in library intelligence.

## Phase 6 — Smart search

Start deterministic and rule based.

Examples:

- `4k movies I haven't watched`
- `Nolan movies`
- `science fiction under 2 hours`
- `movies with Ryan Gosling`

Natural-language intelligence can be layered above this later.

## Phase 7 — Jellyfin integration

- Server connection and authentication.
- Libraries.
- Remote metadata.
- Playback.
- Resume/watch-state sync.
- Collections and playlists where practical.
- Local cache of server library information.

## Phase 8 — Universal source architecture

Candidate adapters:

- Local filesystem
- Jellyfin
- SMB
- WebDAV
- Plex
- Emby
- DLNA

Priority is based on product value and platform capability, not feature-count for its own sake.

## Phase 9 — Best Version

- Resolve equivalent media across sources.
- Prefer local when quality/compatibility are otherwise equivalent.
- Consider resolution, HDR, audio, compatibility, reliability, and availability.
- Allow user overrides.

## Phase 10 — Native Android

- Android phone.
- Android tablet.
- Touch-first adaptive layout.
- Native playback integration.
- Strong controller support.

## Phase 11 — Serious playback

- Hardware decoding.
- Practical H.264 / HEVC / AV1 support by platform.
- HDR where supported.
- Audio track controls.
- Subtitle engine and styling.
- Chapters.
- Resume.
- Playback speed.
- Picture controls.
- Passthrough where supported.
- PiP/background behavior where supported.

## Phase 12 — Controller and adaptive UI

First-class support for:

- PlayStation controllers,
- Xbox controllers,
- Nintendo controllers where practical,
- generic Bluetooth/USB controllers,
- Android TV remotes,
- keyboard/media keys,
- handheld devices.

The first meaningful controller input should trigger a smooth transition into controller-optimized interaction.

## Phase 13 — Smart Offline

- Download media.
- "Prepare for offline".
- Consider storage, quality, audio/subtitles, unwatched content, and next episodes.
- Future trip mode.

## Phase 14 — Android TV / Google TV

- D-pad-first interaction.
- 10-foot layouts.
- TV-oriented focus system.
- HDR/4K/audio support within platform capabilities.

## Phase 15 — Desktop

- Windows.
- Linux.
- Mouse/keyboard/controller.
- Large-library performance.
- Hardware acceleration.
- Fullscreen and multi-monitor considerations.

## Phase 16 — Ecosystem features

- Optional device handoff.
- No mandatory Reflux account.
- Local-network continuity where practical.
- Casting.

## Phase 17 — Advanced intelligence

- Natural-language search.
- Recommendations.
- Context-aware discovery.
- Higher-level library intelligence.

The deterministic local engine remains the source of truth.

## Phase 18 — v1.0 target

Target platforms:

- Android phone/tablet,
- Android TV / Google TV,
- Windows,
- Linux.

Target foundations:

- local media,
- Jellyfin,
- at least one network source,
- universal source architecture,
- automatic identification and artwork,
- virtual organization,
- smart collections,
- search,
- continue watching,
- version detection,
- serious playback,
- controller support,
- adaptive liquid-glass/cinematic UI,
- excellent defaults,
- no mandatory account,
- no filesystem mutation.

Explicitly outside the initial v1 target:

- iOS/iPadOS commitment,
- social features,
- Watch Together,
- own streaming service,
- mandatory cloud backend,
- AI everywhere,
- unnecessary server administration complexity,
- own transcoding stack unless justified by product requirements.
