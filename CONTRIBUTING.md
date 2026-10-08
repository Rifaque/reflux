# Contributing to Reflux

The Media Core, sources, metadata engine, and desktop playback engine are implemented; platform shells are next. Contributions should preserve the project's core principles rather than optimize for feature count.

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

JDK 21 is the only requirement; run `./gradlew check`. Installing libmpv and FFmpeg enables the real-engine playback tests, which are skipped otherwise. Tests use file-backed databases, like production.

## Changes

Keep changes focused and explain architectural consequences when they cross core boundaries.

When introducing a significant product or architectural decision, update docs/DECISIONS.md.
