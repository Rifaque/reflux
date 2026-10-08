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

- **File-enumerating sources** (`FileEnumeratingSource`: local folders, WebDAV and SMB shares) list files that Reflux parses and identifies itself.
- **Catalog sources** (`CatalogSource`: media servers such as Jellyfin) list works they already identified. Their descriptions use the same shape as a file-name parse, so they pass through the same identity rules and merge with local copies of the same work; the same work on two sources is one item with two versions, and Best Version chooses between them. Server-reported stream data is trusted and not probed; server watch state is imported when newer, and `WatchStateSyncSource` lets Reflux report back.

## Implementation

Kotlin Multiplatform (see the decision log). Modules:

| Module | Contents | Depends on |
| --- | --- | --- |
| `core` | Media Core contracts and pure logic. No platform APIs. | kotlinx-coroutines |
| `library` | The local-first library engine: SQLite schema (SQLDelight), scan planning and reconciliation, read models (movies, shows, Continue Watching, search), watch state, favorites, identity corrections, version preferences, metadata storage and refresh, Identify flow, smart search, collections, the default Home feed, diagnostics, and the background pipeline. JVM: database driver, `JdkHttpFetcher`, `ArtworkCache`. | `core`, SQLDelight |
| `design` | Platform-free design system: color tokens with enforced contrast, liquid-glass material roles, motion specs, adaptive layout profiles (including the controller morph), artwork accent extraction. Consumed by the Compose shells. | `core` |
| `sources:local` | Read-only local folder adapter for desktop JVMs. | `core` |
| `sources:webdav` | Read-only WebDAV share source (PROPFIND listing, streamed playback with share credentials). | `core` |
| `sources:smb` | Read-only SMB share source (smbj) and a loopback range-capable stream server that lets any player open share files. | `core`, smbj |
| `sources:jellyfin` | Jellyfin catalog source (platform-free): sign-in, catalog paging, direct-stream playback targets, watch-state reporting. | `core`, kotlinx-serialization-json |
| `metadata:tmdb` | TMDB v3 metadata provider over the core `HttpFetcher` (platform-free). | `core`, kotlinx-serialization-json |
| `playback:mpv` | Desktop playback engine and media probe on libmpv (JNA binding), desktop capability profile. | `core`, JNA, kotlinx-serialization-json |
| `ui` | The experience layer, shared by every shell: navigation and playback state, screens, player controls, and the design system rendered with Compose. No platform APIs; shells implement its `Platform` interface. | `core`, `library`, `design`, Compose runtime/foundation/ui |
| `desktop:engine` | The engine wired for a desktop machine (data directories, source factories, metadata, probing, pipeline). No UI. | everything except `ui` |
| `desktop:app` | The Windows/Linux shell: window, native video surface with the player overlay above it, fullscreen, keyboard normalization, folder picker, artwork decoding. | `ui`, `desktop:engine`, Compose Desktop, JNA |
| `tools:cli` | Developer and diagnostic CLI over the same `desktop:engine` wiring as the shell. | `desktop:engine` |

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

### Experience layer and shells

`ui` is the experience layer. `AppState` holds where the user is, what is playing, and how they are interacting; screens read the library through it off the UI thread and reload when it changes. A `PlaybackSession` is one visit to the player: it plans playback through the library, drives the core `PlaybackController`, and decides what follows an ending (next-episode countdown, "still watching?", or leaving the player). Everything the UI says about a work goes through one pure `Formatting` object.

A shell supplies what only a platform can, through `Platform`: decoded artwork, device capabilities, a playback engine attached to a video surface, a way to add a media folder, and fullscreen. It also normalizes raw input into core `Key`s and `InputSignal`s and hands them to `AppState`, which maps them to semantic actions for the current context; the UI never sees physical keys.

Video is always a platform surface beneath the experience layer's transparent `PlayerOverlay` (a native child window with libmpv on desktop, a `SurfaceView` with Media3 on Android), so the engine keeps its direct path to the display. The layout comes from `AdaptiveLayout` for the window and the current input modality, and is animated when the modality changes.

## File safety

Reflux must not silently rename, move, delete, or modify user media files.

Virtual corrections and organization belong in Reflux state.
