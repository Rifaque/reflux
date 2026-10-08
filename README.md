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

The Media Core is being implemented. See the status section of [docs/ROADMAP.md](docs/ROADMAP.md).

## Documentation

- [Product](docs/PRODUCT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Design direction](docs/DESIGN.md)
- [Input model](docs/INPUT_MODEL.md)
- [Metadata and identification](docs/METADATA.md)
- [Decision log](docs/DECISIONS.md)

## Development

Reflux is written in Kotlin (Multiplatform). Requirements: JDK 21. Gradle is provided by the wrapper.

```sh
./gradlew check        # build and run all tests
./gradlew :core:jvmTest
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#implementation) for the module layout.

## License

Not decided yet.
