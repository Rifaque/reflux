# Reflux Architecture

## Guiding rule

**Jellyfin is an adapter, not the foundation.**

The architecture must allow Reflux to evolve across local storage and remote services without changing the core product model.

## High-level layers

```text
Platform Shell
  - Android (phones, tablets, Android TV / Google TV, Fire TV on Fire OS: one application)
  - Windows
  - Linux
  - future platforms (Vega OS, iOS: not committed)

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
- subtitles              - Plex
- chapters               - SMB, WebDAV
- HDR                    - Emby later
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

Watch state hangs on the first of these, never on the second:

```text
Media work (movie, episode)
    |
    +-- Unified watch state
    |
    +-- Version / cut
           +-- Source A
           +-- Source B
```

A copy kept on the device for watching offline is one more entry under "Version / cut", in a source of Reflux's own. It is never a work of its own ([OFFLINE.md](OFFLINE.md)).

**Playback progress follows the media work, not the physical file or source.** A work has one watch state per profile, owned by Reflux; its position is carried to whichever version plays next, and never blindly onto a different cut. Sources that keep their own watch state are reconciled with it, not obeyed. The contract is in [WATCH_STATE.md](WATCH_STATE.md).

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

### Profiles (M8)

The viewer is the identity boundary of an installation: a **profile**, local, never an account. Media truth (works, versions, metadata, artwork, Identify's corrections, shared sources, server addresses) is the installation's; what a viewer does (watch state, favorites, collections, pinned versions, preferences, server accounts, copies of those accounts' media) is one profile's, keyed by `profile_id` in every table that holds it. Each Jellyfin and Plex account is its own source, owned by one profile, signed in as a device identity of its own; reconciliation runs per account with its owner's state; a playback writes to the profile it started in; and what a profile can see is one flag recomputed with inclusion (`version.visible`), so every read is bound to it. The contract is in [WATCH_STATE.md](WATCH_STATE.md), "Profiles"; the navigation's utility area is where profiles are switched and managed ([NAVIGATION.md](NAVIGATION.md)).

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

Kinds of sources:

- **File-enumerating sources** (`FileEnumeratingSource`: local folders, WebDAV and SMB shares) list files that Reflux parses and identifies itself.
- **Downloadable sources** (`DownloadableSource`, capability `DOWNLOAD`) hand out their files whole, so a copy can be kept. Jellyfin, WebDAV, and SMB are; a source that could only show a file would not be, and nothing assumes it is.
- **Sources reached by an address** (Jellyfin, WebDAV, SMB) come with a `SourceConnector`: what kind of thing it is to the user (a server or a share), whether an address is of its form, and how to connect, with every failure a user can cause as a `ConnectionProblem` rather than an exception. `SourceConnections` in `library` routes an address to the connector for it and makes the connection a source; the experience layer never names an adapter (decision log, 2026-10-14).
- **Sources made of libraries** (`SectionedSource`: Jellyfin, Plex) list their libraries; a catalog entry says which library it is in (`CatalogEntry.section`). Every library is read: whether it takes part in the unified library is the library's own state, never the adapter's (NAVIGATION.md, section 5).
- **Catalog sources** (`CatalogSource`: media servers such as Jellyfin and Plex) list works they already identified. Their descriptions use the same shape as a file-name parse, so they pass through the same identity rules and merge with local copies of the same work; the same work on two sources is one item with two versions, and Best Version chooses between them. Server-reported stream data is trusted and not probed. A server's watch records are translated by its adapter into `SourceUserState` and reconciled with Reflux's canonical state (`WatchReconciler`): what is news from the server is adopted, what the server has not heard of is sent to it through `WatchStateSyncSource`, and a record that was already weighed never overwrites anything.

## Implementation

Kotlin Multiplatform (see the decision log). Modules:

| Module | Contents | Depends on |
| --- | --- | --- |
| `core` | Media Core contracts and pure logic. No platform APIs. | kotlinx-coroutines |
| `library` | The local-first library engine: SQLite schema (SQLDelight) and its migrations, scan planning and reconciliation, read models (movies, shows, Continue Watching, search), the canonical watch state and its synchronization with sources, favorites, identity corrections, version preferences, metadata storage, resolution, and refresh (`ProviderGate` in front of the provider), Identify flow, smart search, collections, the default Home feed, diagnostics, the background pipeline, Smart Offline (`Offline`: download records, the queue, copying, and registering copies as versions), and adding sources by address (`SourceConnections`). JVM: database driver, `JdkHttpFetcher`, `ArtworkCache`, `FileDownloadStore`, `HttpDownloadTransport`. | `core`, SQLDelight |
| `design` | Platform-free design system: color tokens with enforced contrast, glass material roles, motion specs, adaptive layout profiles (including the controller morph), artwork atmosphere extraction. Consumed by the Compose shells. | `core` |
| `sources:local` | Read-only local folder adapter for desktop JVMs. | `core` |
| `sources:webdav` | Read-only WebDAV share source (PROPFIND listing, streamed playback with share credentials) and its connector. | `core` |
| `sources:smb` | Read-only SMB share source (smbj), its connector, and a loopback range-capable stream server that lets any player open share files. | `core`, smbj |
| `sources:jellyfin` | Jellyfin catalog source (platform-free): sign-in, catalog paging, direct-stream playback targets, watch-state reporting, and its connector. | `core`, kotlinx-serialization-json |
| `sources:plex` | Plex Media Server catalog source (platform-free): plex.tv or token sign-in, libraries, catalog per library with parts and streams, direct file playback and downloads, watch-state reporting, and its connector. | `core`, kotlinx-serialization-json |
| `metadata:tmdb` | TMDB v3 metadata provider over the core `HttpFetcher` (platform-free). | `core`, kotlinx-serialization-json |
| `playback:mpv` | Desktop playback engine and media probe on libmpv (JNA binding), desktop capability profile. | `core`, JNA, kotlinx-serialization-json |
| `ui` | The experience layer, shared by every shell: navigation and playback state, screens, player controls, phone, tablet, desktop, and television layouts, and the design system rendered with Compose. No platform APIs; shells implement its `Platform` interface. Built for the desktop JVM and for Android. | `core`, `library`, `design`, Compose runtime/foundation/ui |
| `desktop:engine` | The engine wired for a desktop machine (data directories, source factories, metadata, probing, pipeline, the rule for adding folders, the diagnostics log). No UI. | everything except `ui` |
| `desktop:app` | The Windows/Linux shell: window, native video surface with the player overlay above it, fullscreen, keyboard and controller input, folder picker, artwork decoding. | `ui`, `desktop:engine`, Compose Desktop, JNA |
| `android:app` | The Android shell for phones, tablets, and televisions (one application), and the engine wired for Android: the activity, the folder source over the Storage Access Framework, the storage browser for televisions, Media3 playback, the platform prober and capabilities, Android's SQLite driver. Part of the build only where an Android SDK is installed. | `ui`, the engine modules, `sources:local`, Media3, AndroidX Activity |
| `tools:cli` | Developer and diagnostic CLI over the same `desktop:engine` wiring as the shell. | `desktop:engine` |

Packages in `core` (`dev.reflux.core`):

- `model` — `MediaItem` (Movie, Show, Season, Episode), `MediaVersion`, `MediaLocation`, `Artwork`, `WatchState`, `Availability`, deterministic `StableIds`.
- `source` — `MediaSource` / `FileEnumeratingSource` adapter contracts, `SourceCapability`, `SourceLocality`, shared `ScanRules`, `SourceConnector` with its connection kinds, requests, and problems, and `SectionedSource` for sources made of parts the user chooses between (a server's libraries).
- `identify` — path parsing (`MediaPathParser`), identity keys and grouping (`Identifier`), user overrides, sidecar subtitle/artwork matching.
- `playback` — stream description (`StreamInfo`), `DeviceCapabilities`, `PlaybackAssessor` (optimal / degraded / unsupported with reasons), the engine-agnostic `Player` contract, `MediaProber`, default `TrackSelector`, and `PlaybackController` (semantic actions, default tracks, watch reporting).
- `versions` — Best Version (`VersionSelector`), ranked and explained: what this device can play, the cut in progress, the user's pinned version, quality as this device delivers it, and the closest copy among versions that are equivalent by all of that.
- `watch` — the rules around the canonical watch state: completion and resume thresholds (`WatchRules`), timelines and cuts (`Timeline`, `Cuts`), carrying a position to another version (`Handoff`), reconciliation with a source's own records (`WatchReconciler`), and Next Up.
- `offline` — what a download is and can be (`Download`, its states and failures), how much of the device may be used (`StoragePolicy`), and `OfflinePlanner`: which version to keep for this device in the room there is.
- `search` — deterministic title search (`SearchMatcher`) and smart query understanding (`SmartQueryParser`).
- `metadata` — provider contract (`MetadataProvider`), work/season/episode metadata, deterministic `MetadataMatcher`, and provenance: `Description` (what one witness says), `MetadataResolver` (field-level precedence), `ArtworkPrecedence`, `MatchEvidence` (METADATA.md, "Library depth").
- `net` — `HttpFetcher`, the only network abstraction core-level code sees.
- `input` — semantic actions, default mappings, glyphs, and input-modality tracking.

Playing a work: `Library.planPlayback` picks the version (Best Version or the user's choice), resolves it and its external subtitles through the source adapter, and carries the work's watch position onto that version's timeline (`Handoff`). A `PlaybackController` then drives the platform `Player` and reports progress back to the library, which records it for the work together with the timeline it was measured on. None of this differs by kind of source. Probing (`Library.probePending` with a `MediaProber`) replaces file-name hints with real stream data in the background; failures are not retried until the file changes.

A source's life: it is added by a connector (or a folder rule) and kept as a record; scanned while reachable; marked unavailable, never emptied, while it is not; checked again periodically and on request; changed by signing in again or narrowing what it includes, which keeps its identity; and removed only when the user removes it, which takes its versions out of the library, forgets its connection, and keeps every work's watch history and every copy kept for offline; a server is then asked to end the session the connection opened, best-effort and without holding the removal up (`SourceConnections.remove`). Adapters that are replaced or removed are closed (`SourceRegistry`). Adding the same source again is the same source: identity comes from what the source is (server and account, share and folder), not from when it was added.

`LibraryPipeline` keeps the library current without user involvement: scan → probe → metadata → artwork prefetch, run on demand, on debounced source change signals, and when a periodic availability check sees an offline source come back. A `SourceRegistry` recreates adapters from persisted source records through per-platform factories.

A scan is a pure plan followed by one transaction: the source's file listing is parsed, corrected by user overrides, identified, matched with sidecars, and then applied so the plan becomes the complete contents of that source. The same listing always yields the same library.

Contracts follow three rules: unknown is not failure, capabilities are explicit, and every decision is deterministic and explainable (identification signals, playback issues, selection criteria). A capability that is not known is said to be unknown (a decoder's `hardware` null, a display's size null), never guessed.

### Experience layer and shells

`ui` is the experience layer. `AppState` holds where the user is, what is playing, and how they are interacting; screens read the library through it off the UI thread and reload when it changes. A `PlaybackSession` is one visit to the player: it plans playback through the library, drives the core `PlaybackController`, and decides what follows an ending (next-episode countdown, "still watching?", or leaving the player). Everything the UI says about a work goes through one pure `Formatting` object.

A shell supplies what only a platform can, through `Platform`: decoded artwork, device capabilities, a playback engine attached to a video surface, a way to add a media folder (its own picker, or its storage for the experience layer to draw one over), the servers and shares it can connect to (`connections`), whether it is a television, whether loudness is the device's own, fullscreen, and somewhere to report problems. It also normalizes raw input into core `Key`s, gamepad buttons by position, and `InputSignal`s and hands them to `AppState`, which maps them to semantic actions for the current context; the UI never sees physical keys or controller brands.

Failures are part of the contracts rather than exceptions that escape: engines say why playback stopped, adding a folder has explicit outcomes, and the experience layer turns each into words in one place (`Problems`) while the cause goes to the shell's diagnostics (decision log, "Failures are states").

A session has a **presentation**, and it is the same session in each (PLAYBACK.md, section 18): the full player, or, where the platform can place its video surface (`Platform.docksVideo`), the mini-player in the navigation's dock while the viewer browses. The experience layer says where the surface goes (`AppState.videoFrame`: the whole window, the dock's picture box, or out of sight under whatever covers the page) and the shell puts it there (`VideoStage`); the experience layer never knows what the surface is, and no shell decides where it goes. Nothing below the experience layer knows a presentation exists.

Video is always a platform surface beneath the experience layer's transparent `PlayerOverlay` (a native child window with libmpv on desktop, a `SurfaceView` with Media3 on Android, laid out to the picture's own shape), so the engine keeps its direct path to the display. The layout comes from `AdaptiveLayout` for the window and the current input modality, and is animated when the modality changes.

### What a platform supplies

There are two shells, `desktop:app` and `android:app`. Each supplies what only its platform can, behind the same contracts, and nothing of either reaches the shared modules. A television is not a third shell: it is the Android shell on a device that says it is a television, and the television layout of the one experience layer (below). Fire TV on Fire OS is the same Android shell, distributed separately through the Amazon Appstore (ROADMAP.md); a Vega OS client would be a new shell.

| Piece | Desktop | Android |
| --- | --- | --- |
| `Platform` (artwork decoding, device capabilities, player and video surface, adding media, fullscreen, problem reports, which build it is and whose notices apply): the whole contract between `ui` and a shell | `DesktopPlatform`: Skia decoding, AWT windows | `AndroidPlatform`: the platform decoder, the system folder picker, system bars and orientation |
| Hosting the experience layer | A Compose window, with the player's controls in a window above the video | One activity (`MainActivity`), edge to edge, with the controls composed above the video |
| Input normalization into `Key`, `GamepadButton`, and `InputSignal` | AWT keys; XInput, HID, and joystick readers | Key and motion events (`Input`, `StickDirections`); Back through the system's back dispatcher |
| Playback engine behind `Player` | libmpv into a native child window | Media3 (ExoPlayer) into a `SurfaceView` (`Media3Player`); subtitles are drawn by the shell. The same engine is the device's media session, so headset buttons and the system's own controls act on the one playback state |
| Presenting a session beside the page (`Platform.docksVideo`) | Yes: the native child window is moved to the dock's picture box in one move, its controls window with it (the mini-player, M9) | Not yet: the player is the whole screen or closed (M10 places the `SurfaceView`); never on a television |
| Leaving the app while playing | The window stays where it is | On a phone or tablet, picture-in-picture: the activity becomes a small window of the picture's shape showing only the picture. On a television, and when stopped, covered, or with the screen off, playback pauses and the player releases its decoders and audio focus until it is seen again |
| Problem reports | `DiagnosticLog`, a file in the data directory | The system log and a file in the app's storage. Both pass through `Redaction` in `core`, so no log carries credentials |
| `MediaProber` | libmpv | The platform's `MediaExtractor` (`AndroidMediaProber`) |
| `DeviceCapabilities` | What was observed (`ObservedCapabilities`): libmpv's decoders as a start-up probe and real playbacks showed them, unknown until then, and the display the window is on, read again as it moves | What the device's own codec list and display report (`AndroidCapabilities`) |
| Engine wiring (data directory, source factories, pipeline, the rule that one folder is one source) | `desktop:engine` (`DesktopEnvironment`) | `AndroidEnvironment`, in the app's private storage |
| Database driver | JDBC SQLite | Android's SQLite, through the same generated schema and migrations |
| HTTP | The JDK client (`JdkHttpFetcher`) | `HttpURLConnection` (`AndroidFetcher`), which also reads `content:` addresses; web shares (WebDAV) through OkHttp (`WebDavTransport`), since `HttpURLConnection` cannot send `PROPFIND` (DECISIONS.md, 2026-10-18) |
| Local media source | `sources:local`: a folder as a filesystem path | `DocumentTreeSource`: a folder granted through the Storage Access Framework, which is a tree of documents and a grant that can be revoked, not a path. On a television, whose system has no such picker, the same `sources:local` folder-by-path as on the desktop |
| Choosing a folder | The system's folder dialog | The system's folder picker; on a television, Reflux's own picker over `StorageFolders` (the mounted volumes and their folders), after the system's permission question |
| Being a television | Never | What the system reports (`UiModeManager`, the leanback feature): listed in the TV launcher with its own banner, no touchscreen required |
| The launcher's "continue" row | None | Where the system has the TV provider: Watch Next programs derived from Continue Watching by `WatchNext` (`library`), written by `WatchNextPublisher`, opening `MainActivity` on the work (DECISIONS.md, 2026-10-18) |
| Servers and shares (`Platform.connections`) | Jellyfin, Plex, Windows and NAS shares (SMB), web shares (WebDAV) | The same four; SMB through the same adapter and loopback stream server as the desktop |
| What a connection stores (`ConfigSeal`) | The system's credential store (`KeychainSeal` over Windows Credential Manager or the Secret Service); the database keeps a reference. Where Linux has no Secret Service, stored as it is (see RELEASE.md) | Sealed with an AES key held in the Android keystore (`KeystoreSeal`); what an earlier version stored in the open is sealed at start |
| Smart Offline (`Platform.offline`) | `Offline` over Reflux's data directory (`FileDownloadStore` in `downloads`) and `HttpDownloadTransport`, wired in `DesktopEnvironment` and running in the application's scope | `Offline` over the application's internal storage (`FileDownloadStore`) and `HttpURLConnection` (`HttpDownloadTransport`), running in a scope that lives as long as the process |
| Sound output | What libmpv opens | Stereo unless an HDMI output reports more channels or formats it decodes itself, which become `maxOutputChannels` and `audioPassthrough` |
| Language names for track labels | `Formatting.jvm.kt` | `Formatting.android.kt` |

How the shared code reaches each platform:

- The engine (`core`, `library`, `design`, the platform-free adapters, the metadata provider) is compiled once, to JVM bytecode, and both shells use it as it is. Android brings its own database driver and leaves the desktop one out of the application.
- `ui` is compiled twice, because Compose is a different artifact on each platform: for the desktop JVM and, where an Android SDK is installed, for Android.
- Without an Android SDK (`ANDROID_HOME`, or `sdk.dir` in `local.properties`), the Android variant of `ui` and the `android` modules are simply not part of the build. Everything else builds and tests as before.

Two things keep the shared code honest about platform APIs. The compiler does for `ui`, which no longer has a single target. For the engine modules, `./gradlew check` still fails when `commonMain` sources import or call platform-only APIs (`verifySharedSources`), and the on-device tests run the engine on a real Android runtime, which is where differences in behavior rather than in API show up: Android's regular expressions are a stricter engine than the JDK's, and that was found there.

The About page's third-party notices are generated into `ui` from `legal/` at build time, and `check` holds that list to each shell's runtime classpath (`checkThirdPartyNotices`; RELEASE.md, "Third-party notices"). The commit a build is made from is written into each shell's package by its build, read by `Platform.build`; the version stays `RefluxBuild.VERSION` (VERSIONING.md).

### Layout by form factor

The experience and its information architecture are the same everywhere; where things sit follows the form factor and the input in use (`AdaptiveLayout`).

- **Every form factor.** Four primary destinations (Home, Movies, Shows, Search) and a utility area (Downloads where the platform keeps copies, Sources, Settings, and the profile), one back stack, and the unified library told apart from each source's own (NAVIGATION.md). Sources, adding media, and connecting a server or a share are pages of one centered column; a source's own library is a grid like Movies.
- **Phone held upright.** Home, Movies, Shows, and Search are icons along the bottom, in reach of a thumb. The top carries only what a page needs there: the way back, a sign of the library being updated, and the utility area as one entry, the profile, which leads to Downloads, Sources, and Settings. The offline sheet rises from the bottom. The player puts play, back, and ahead in the middle of the picture and keeps the timeline, track menus, speed, and fullscreen in a bar beneath; volume is the device's own buttons. A tap shows or hides the controls, and two taps on a side of the picture skip that way.
- **Phone on its side, tablet, and desktop.** The navigation runs down the left side, and pages start beside it: a compact rail of icons on a phone on its side, a rail with names on a tablet, a sidebar with the wordmark and names on a desktop. The top of a page carries the way back. More titles in a row, and details pages with the poster beside the description. A tablet is the same experience as a desktop window of its size, not a larger phone.
- **Controller in hand**, on any of them: larger targets, visible focus, and button prompts, without a separate mode.
- **Television.** A rail of icons on the left, which shows their names while it has focus, and the same pages, laid out for a canvas of 960 by 540 inside a 5% safe margin and operated through focus alone. Something always has focus: a page starts on its main action and comes back to the item it was left from; Back on a destination goes to the rail first. Home moves a row at a time, the row with focus at the top with its title showing. The player shows where playback is and nothing to point at: OK plays and pauses, left and right seek, down opens the lists (episodes, chapters, audio, subtitles, speed), up takes an offer on screen such as skipping an intro, and Back closes a list and then the player. Choosing a media folder is a walk through storage with the remote.

## File safety

Reflux must not silently rename, move, delete, or modify user media files.

Virtual corrections and organization belong in Reflux state.
