# Reflux

> A universal, local-first media player built around your media, not your media server.

Reflux is a media player on steroids: a cross-platform media experience that automatically understands, organizes, and plays media from local storage and remote sources without making the underlying filesystem conform to the application.

## Product principles

- **Player first.** Playback quality is the center of the product.
- **Local first.** A user's files, library state, metadata cache, and core experience should remain useful without an account or a live server.
- **Source agnostic.** Local files, Jellyfin, and future sources are adapters into one universal media model.
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
5. remember watch and resume state,
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
      Local         Jellyfin      Network Sources
        |                              |
      Files                    SMB / WebDAV / ...
```

UI and playback features should consume normalized media entities and source capabilities rather than being tightly coupled to one backend.

## Project status

The engine (Media Core, sources, metadata, playback) is implemented, and the desktop app is a working vertical slice on top of it: point it at a folder, browse, search, and play, with a mouse, a keyboard, or a controller. See the status section of [docs/ROADMAP.md](docs/ROADMAP.md).

## Documentation

- [Product](docs/PRODUCT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Design direction](docs/DESIGN.md)
- [Input model](docs/INPUT_MODEL.md)
- [Metadata and identification](docs/METADATA.md)
- [Decision log](docs/DECISIONS.md)
- [Release readiness](docs/RELEASE.md)

## Development

Reflux is written in Kotlin (Multiplatform). Gradle is provided by the wrapper, and the wrapper downloads JDK 21 if the machine has none.

```sh
./gradlew check        # build and run all tests
./gradlew :core:jvmTest
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#implementation) for the module layout.

### Desktop app

```sh
./gradlew :desktop:app:run
```

Choose a media folder on first run; everything else is automatic. Mouse, keyboard, and game controllers (Xbox, PlayStation, Switch Pro, and generic pads) all work without setup; picking up a controller reshapes the interface for it. Playback needs libmpv (below); without it the library is still fully browsable. Titles, artwork, and details come from TMDB when `REFLUX_TMDB_TOKEN` is set; without it Reflux shows what it identified from file names. Library state lives in the platform data directory (override with `REFLUX_HOME`), never in your media folders; problems are written to `reflux.log` there.

### Developer CLI

`tools/cli` runs the same engine from a terminal, for diagnosing identification, metadata, and playback on real libraries: it scans folders (read-only), probes files with libmpv when installed, fetches metadata when `REFLUX_TMDB_TOKEN` is set, and plays in an mpv window.

```sh
./gradlew :tools:cli:installDist
tools/cli/build/install/reflux/bin/reflux add ~/Videos
tools/cli/build/install/reflux/bin/reflux search "4k movies I haven't watched"
tools/cli/build/install/reflux/bin/reflux play breaking bad
tools/cli/build/install/reflux/bin/reflux doctor
```

Playback and probing need libmpv (`libmpv2` on Debian/Ubuntu, `mpv-libs` on Fedora, `libmpv-2.dll` on Windows). When running from Gradle, a library placed in `.reflux/native/` (ignored by Git) is found without installing it system-wide. Nothing compiles against it: without libmpv everything builds, the tests that need an engine skip themselves, and the application browses but does not play. Shipping it is a release question, covered in [docs/RELEASE.md](docs/RELEASE.md). To see playback from the engine's side, set `REFLUX_MPV_LOG` to a file and libmpv writes its own verbose log there.

## License

Not decided yet.
