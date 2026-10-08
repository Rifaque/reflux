# Reflux Decision Log

## 2026-10-08 — Product identity

Reflux is treated as a universal media player rather than a Jellyfin-specific client.

## 2026-10-08 — Jellyfin architecture

Jellyfin is a source adapter, not the application foundation.

## 2026-10-08 — Local-first

Local media and locally cached library state are first-class. Core functionality must not require a Reflux account.

## 2026-10-08 — Virtual organization

Reflux may organize media virtually, but must not silently rename, move, delete, or otherwise modify user media files.

## 2026-10-08 — Automatic metadata

Automatic identification, metadata, and artwork are core product behavior. Users should not need sidecar files such as poster.jpg or .nfo files for the default experience.

## 2026-10-08 — Default experience

Out-of-the-box quality is a hard requirement. Customization is additive.

## 2026-10-08 — Search foundation

Smart search begins with deterministic, rule-based parsing. Natural-language intelligence can be layered on top later.

## 2026-10-08 — Controller architecture

Controller support is normalized around semantic actions, with first-class PlayStation and Xbox support and adaptive UI transitions.

## 2026-10-08 — Version selection

When otherwise equivalent, local media should be preferred. Best Version logic may also consider resolution, compatibility, HDR, audio, availability, and reliability.

## 2026-10-08 — Platform scope

Initial target scope is Android phone/tablet, Android TV / Google TV, Windows, and Linux. iOS/iPadOS is not a v1 commitment.

## 2026-10-08 — Visual direction

The design language combines restrained premium ergonomics, cinematic media presentation, and liquid glass without copying any single reference product.

## 2026-10-08 — Implementation stack: Kotlin Multiplatform

**Decision.** Reflux is implemented in Kotlin. The Media Core is Kotlin Multiplatform `commonMain` code with no platform dependencies. Platform shells are native Android (Jetpack Compose, including Compose for TV) and Compose Multiplatform on the JVM for Windows and Linux.

**Alternatives evaluated.**

| Option | Platform coverage | Playback | Fit with Media Core | Main problem |
| --- | --- | --- | --- | --- |
| Kotlin Multiplatform + Compose | Android, Android TV, Windows, Linux (iOS possible later) | Media3 natively on Android; libmpv on desktop | Core shared as plain Kotlin; platform code behind interfaces | Desktop video embedding needs native-surface work |
| Flutter + media_kit | All targets | libmpv everywhere | Core in Dart | Android TV integration (display modes, tunneling, passthrough) goes through plugins; media_kit maintenance risk |
| Rust core + native shells | All targets | Any | Strong | Two languages plus FFI bindings; no mature Rust desktop UI, so desktop would still need another UI stack |
| Qt/QML + C++ | Desktop strong, Android weaker | libmpv | Strong | Weak Android TV ergonomics; C++ cost; Qt licensing constraints |
| Web shell (Electron/Tauri) | Desktop; Android TV poor | Browser codecs only | — | Cannot meet the serious-playback requirement |

**Why.** Android and Android TV are the first targets and their serious-playback features (HDR display modes, Dolby Vision, frame-rate matching, tunneled playback, audio passthrough) are only reached reliably from native Android code. Kotlin is native there, runs on the desktop JVM, and lets the Media Core be shared without an FFI boundary. One language keeps the system small.

**Consequences.** The core must not use JVM-only APIs; platform facilities (filesystem walking, SQLite drivers, HTTP, decoders) sit behind interfaces. Builds use Gradle with a version catalog. The Android SDK is not required to build or test the core.

## 2026-10-08 — Playback engines

Playback is a platform capability behind a common contract, not a single cross-platform engine.

- **Android / Android TV:** AndroidX Media3 (ExoPlayer, Apache-2.0) is the primary engine: MediaCodec hardware decoding, HDR/Dolby Vision output, tunneling, passthrough, and frame-rate matching are platform features it integrates with. libmpv remains a candidate fallback engine for formats Media3 handles poorly.
- **Windows / Linux:** libmpv (built LGPL, without GPL-only components) provides hardware decoding (D3D11VA, NVDEC, VAAPI), libplacebo tone mapping, broad codec and container coverage, and libass subtitle rendering.

Each engine reports `DeviceCapabilities`; product logic (Best Version, player UI) only consults those capabilities.

## 2026-10-08 — Identity, versions, and locations are separate

A work (`MediaItem`: movie, show, season, episode) is separate from its `MediaVersion`s, and each version has one `MediaLocation` (source + source-relative path). IDs are derived deterministically from stable keys (normalized title/year/episode for works; source + path for versions) so rebuilding the index yields the same IDs. Watch state belongs to works, never to files.

## 2026-10-08 — Unknown technical information never blocks playback

Compatibility checks (`PlaybackAssessor`) treat unknown stream properties as "no evidence of a problem". Reflux ranks and explains versions but does not refuse to try a file it could not inspect; the platform player remains the final authority.

## 2026-10-08 — Library persistence: SQLite through SQLDelight

The library index, metadata cache references, watch state, favorites, identity overrides, and version preferences live in one SQLite database owned by Reflux, accessed through SQLDelight (Apache-2.0, Kotlin Multiplatform, compile-time-checked SQL, reactive queries). SQLite is available on every target and is the right tool for an embedded, offline, single-user index.

- SQL stays within the SQLite 3.18 dialect so the platform SQLite of older Android TV devices works; upserts are written as insert-or-ignore plus update.
- Desktop uses the JDBC driver (sqlite-jdbc) with WAL; Android will use the platform driver.
- Everything except user state (watch state, favorites, overrides, preferences) can be rebuilt by rescanning.

## 2026-10-08 — Offline and unmount safety

A scan only changes the cached library for a source that is reachable. If a source is unreachable, or reachable but contains no media although Reflux knows media on it (the typical unmounted drive or share leaving an empty mount point), the cached library is kept and the source is marked unavailable. Only a reachable, non-empty source can remove versions. Removing a work's last file hides the work but keeps its watch history; an identity correction carries the history to the corrected work.

## 2026-10-08 — Local folder adapter

`LocalFolderSource` (desktop JVM) walks a folder read-only, follows symbolic links with loop protection, skips hidden and system folders, and tolerates unreadable entries. Android will need a separate Storage Access Framework adapter because user-chosen folders are `content:` trees there, not paths.

## 2026-10-08 — libmpv integration

libmpv is bound through JNA (Apache-2.0/LGPL-2.1) using only the stable string-based client API, so any libmpv 2.x works. Reflux does not load the user's `mpv.conf` or scripts (`config=no`): the default experience must be identical everywhere, and advanced options will be exposed by Reflux itself. External subtitles are attached with the per-load `sub-files` list so that tracks are complete when the file is loaded, before default track selection runs. libmpv is a runtime dependency of desktop builds and is not bundled in the repository.

## 2026-10-08 — Default track selection

Track choice is product logic, identical across engines (`TrackSelector`): audio in the user's first preferred language, else the file's default track; subtitles automatically when the audio is in a language the user does not prefer, forced subtitles otherwise, and non-SDH tracks first. The preferred languages come from the device locale, so this works with no configuration.

## 2026-10-08 — Media probing

File-name hints are a first guess. Real stream data comes from a `MediaProber` (libmpv on desktop, MediaExtractor on Android), run in the background for new or changed files. Probed facts replace hints, except that a Dolby Vision release tag upgrades an HDR10 probe result because probes without DV support only see the base layer.

## 2026-10-08 — Metadata providers

Metadata comes from providers behind `MetadataProvider`; TMDB is the first. Matching is deterministic (`MetadataMatcher`) and conservative: an uncertain match is left for the Identify flow rather than guessed, except that decisive popularity resolves exact-title ties when no year is known. Provider metadata changes display fields only (titles, years, overviews, artwork), never identity. The provider credential is an application credential injected at build time; users never configure providers. HTTP is reached only through the core `HttpFetcher` so providers stay platform-free (the JDK client on desktop).

## 2026-10-08 — Change detection

Sources emit coarse "something changed" signals (`ChangeNotifyingSource`); the pipeline debounces them and runs a full, deterministic rescan of that source. Rescans re-parse paths (cheap) but only re-probe changed files, so correctness never depends on interpreting individual file events. Availability is re-checked periodically so reconnected drives and shares are rescanned automatically. Path-level incremental scans can be added later as an optimization without changing these semantics.

## 2026-10-08 — Smart search vocabulary comes from the library

Smart search recognizes genres and people only if they exist in the user's own library metadata, so a word is never interpreted as a filter that could match nothing by construction, and surnames only count when unambiguous and in context ("Nolan movies", "with Gosling"). A bare year stays title text unless the query is otherwise structured, because titles like "2012" and "1917" exist. Resolution classes use the nominal height (a 3840×1600 scope film is 4K).

## 2026-10-08 — Default Home and collections

Home is composed by fixed rules, not configuration: Continue Watching, Recently Added, Favorites, unwatched movies, 4K HDR, provider franchises with at least two present movies, TV shows, the library's four strongest genres, and all movies; empty or too-small rows are omitted. Rows carry an optional smart query for "see all". User collections are either manual lists or saved smart searches, which stay current without maintenance.

## 2026-10-08 — Catalog sources share the identity pipeline

Media servers are catalog sources whose items are converted into the same identity description a file-name parse produces, then identified by the same deterministic rules. This keeps one notion of identity: a movie on a Jellyfin server and the same movie in a local folder become one work with two versions. User corrections apply to server items too; to make corrections work offline, each server version stores the server's own description. Server watch state is adopted only when newer than Reflux's; server favorites are added but never remove local ones.

## 2026-10-08 — Jellyfin adapter

Jellyfin is integrated as a catalog source, not as the application model. Reflux reads the user's catalog (movies, episodes, every media source of an item as a version), plays Jellyfin's direct static streams in its own player, and reports progress and played state back best-effort; Reflux's local state is always updated first. Server-side transcoding is intentionally not used in v1: Best Version and the device capability model decide what to play. Passwords are used only to sign in; the access token is kept with the source configuration.

## 2026-10-08 — Identity aliases

Identity stays key-based and deterministic, and provider IDs refine it: works sharing a provider identifier are unified by recording an alias from one identity key to the other, applied during identification. This merges differently named copies across sources without letting providers define identity on their own, and without fragile renaming of stored works.

## 2026-10-08 — Playback must degrade, not fail

libmpv's defaults are tuned for hardware but fall back instead of failing: video outputs fall back from `gpu-next` to plain outputs when no usable GPU driver exists, and audio falls back to a null output when no device is available. Found by running real playback in a GPU-less, audio-less environment.

## 2026-10-08 — Developer CLI

`tools/cli` is a long-lived diagnostic tool, not a product shell: it wires the engine exactly as a desktop shell does and makes identification, metadata, and playback behavior observable on real libraries. Tests run against file-backed databases, because the in-memory driver hid a connection-threading bug that the CLI exposed.
