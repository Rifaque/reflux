# Reflux Architecture

## Guiding rule

**Jellyfin is an adapter, not the foundation.**

The architecture must allow Reflux to evolve across local storage and remote services without changing the core product model.

## High-level layers

```text
Platform Shell
  - Android
  - Android TV / Google TV
  - Windows
  - Linux
  - future platforms

        |
        v

Experience Layer
  - navigation
  - library
  - search
  - player UI
  - settings
  - controller-adaptive UI
  - design system

        |
        v

Media Core
  - media entities
  - source abstraction
  - library/index
  - metadata
  - artwork
  - watch state
  - versions
  - availability
  - search
  - collections

        |
        +---------------------+
        |                     |
        v                     v
Playback Core            Source Adapters
- decoding               - Local filesystem
- tracks                 - Jellyfin
- subtitles              - SMB
- chapters               - WebDAV
- HDR                    - Plex / Emby later
- audio                   - DLNA / other sources later
- platform capabilities
```

## Universal media model

The UI should work with normalized concepts such as:

- Movie
- TV Show
- Season
- Episode
- Collection
- Person
- Genre
- Media Version
- Media Source
- Artwork
- Watch State
- Availability
- Playback Capability

The model should distinguish **what a media item is** from **where a playable version currently lives**.

## Local-first state

Core local state should include, where applicable:

- library index,
- identification results,
- metadata cache,
- artwork cache,
- watch state,
- resume position,
- user overrides,
- source availability,
- version relationships,
- search index.

A disconnected source should not erase the user's understanding of their library.

## Source abstraction

Each source adapter should expose capabilities rather than forcing a common lowest-denominator feature set.

Examples:

- browse libraries,
- resolve metadata,
- enumerate playable versions,
- stream or open media,
- report availability,
- read/write watch state where supported,
- expose subtitles and audio tracks,
- expose remote capabilities.

A source is allowed to lack capabilities. The Media Core should represent that explicitly.

Two kinds of sources exist today:

- **File-enumerating sources** (`FileEnumeratingSource`: local folders; later SMB, WebDAV) list files that Reflux parses and identifies itself.
- **Catalog sources** (`CatalogSource`: media servers such as Jellyfin) list works they already identified. Their descriptions use the same shape as a file-name parse, so they pass through the same identity rules and merge with local copies of the same work; the same work on two sources is one item with two versions, and Best Version chooses between them. Server-reported stream data is trusted and not probed; server watch state is imported when newer, and `WatchStateSyncSource` lets Reflux report back.

## Implementation

Kotlin Multiplatform (see the decision log). Modules:

| Module | Contents | Depends on |
| --- | --- | --- |
| `core` | Media Core contracts and pure logic. No platform APIs. | kotlinx-coroutines |
| `library` | The local-first library engine: SQLite schema (SQLDelight), scan planning and reconciliation, read models (movies, shows, Continue Watching, search), watch state, favorites, identity corrections, version preferences, metadata storage and refresh, Identify flow, smart search, collections, the default Home feed, diagnostics, and the background pipeline. JVM: database driver, `JdkHttpFetcher`, `ArtworkCache`. | `core`, SQLDelight |
| `sources:local` | Read-only local folder adapter for desktop JVMs. | `core` |
| `metadata:tmdb` | TMDB v3 metadata provider over the core `HttpFetcher` (platform-free). | `core`, kotlinx-serialization-json |
| `playback:mpv` | Desktop playback engine and media probe on libmpv (JNA binding), desktop capability profile. | `core`, JNA, kotlinx-serialization-json |

Packages in `core` (`dev.reflux.core`):

- `model` — `MediaItem` (Movie, Show, Season, Episode), `MediaVersion`, `MediaLocation`, `Artwork`, `WatchState`, `Availability`, deterministic `StableIds`.
- `source` — `MediaSource` / `FileEnumeratingSource` adapter contracts, `SourceCapability`, `SourceLocality`, shared `ScanRules`.
- `identify` — path parsing (`MediaPathParser`), identity keys and grouping (`Identifier`), user overrides, sidecar subtitle/artwork matching.
- `playback` — stream description (`StreamInfo`), `DeviceCapabilities`, `PlaybackAssessor` (optimal / degraded / unsupported with reasons), the engine-agnostic `Player` contract, `MediaProber`, default `TrackSelector`, and `PlaybackController` (semantic actions, default tracks, watch reporting).
- `versions` — Best Version (`VersionSelector`), ranked and explained.
- `watch` — resume/completion rules and Next Up.
- `search` — deterministic title search (`SearchMatcher`) and smart query understanding (`SmartQueryParser`).
- `metadata` — provider contract (`MetadataProvider`), work/season/episode metadata, deterministic `MetadataMatcher`.
- `net` — `HttpFetcher`, the only network abstraction core-level code sees.
- `input` — semantic actions, default mappings, glyphs, and input-modality tracking.

Playing a work: `Library.planPlayback` picks the version (Best Version or the user's choice), resolves it and its external subtitles through the source adapter, and adds the resume position. A `PlaybackController` then drives the platform `Player` and reports progress back to the library. Probing (`Library.probePending` with a `MediaProber`) replaces file-name hints with real stream data in the background; failures are not retried until the file changes.

`LibraryPipeline` keeps the library current without user involvement: scan → probe → metadata → artwork prefetch, run on demand, on debounced source change signals, and when a periodic availability check sees an offline source come back. A `SourceRegistry` recreates adapters from persisted source records through per-platform factories.

A scan is a pure plan followed by one transaction: the source's file listing is parsed, corrected by user overrides, identified, matched with sidecars, and then applied so the plan becomes the complete contents of that source. The same listing always yields the same library.

Contracts follow three rules: unknown is not failure, capabilities are explicit, and every decision is deterministic and explainable (identification signals, playback issues, selection criteria).

## File safety

Reflux must not silently rename, move, delete, or modify user media files.

Virtual corrections and organization belong in Reflux state.
