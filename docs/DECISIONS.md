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
