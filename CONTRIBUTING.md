# Contributing to Reflux

The Media Core, sources, metadata engine, desktop playback engine, the shared experience layer, and the first slice of the desktop shell are implemented. Contributions should preserve the project's core principles rather than optimize for feature count.

## Before contributing

Read:

- [Product](docs/PRODUCT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Design](docs/DESIGN.md)
- [Input model](docs/INPUT_MODEL.md)
- [Metadata](docs/METADATA.md)
- [Decision log](docs/DECISIONS.md)

## Principles

- Keep the default experience excellent.
- Prefer local-first designs.
- Keep sources behind adapters.
- Do not modify user media files unless an explicit future feature and permission model says otherwise.
- Prefer deterministic foundations over unnecessary AI dependencies.
- Treat controller, touch, pointer, keyboard, and TV interaction as first-class concerns.
- Avoid adding configuration when a sensible default can solve the problem.

## Building and testing

Run `./gradlew check`; the wrapper downloads JDK 21 if needed. Installing libmpv and FFmpeg (or placing libmpv in `.reflux/native/`) enables the real-engine playback tests, which are skipped otherwise. Tests use file-backed databases, like production.

UI changes should be looked at, not only tested:

- `./gradlew :ui:jvmTest -Preflux.screenshots=<dir>` renders every screen offscreen over a real test library and saves it as a PNG.
- `./gradlew :desktop:app:test -Preflux.windowTests=true` runs the real desktop shell and plays a generated file in its window. It opens a window on your screen, so it only runs on request; add `-Preflux.screenshots=<dir>` to save what it showed.

## Changes

Keep changes focused and explain architectural consequences when they cross core boundaries.

When introducing a significant product or architectural decision, update docs/DECISIONS.md.
