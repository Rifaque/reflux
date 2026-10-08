# Reflux — Agent Instructions

## Mission

Reflux is a **media player on steroids**.

It is a universal, local-first media client built around the user's media rather than any particular media server. The core experience must make media easy to find, understand, organize, and play while remaining excellent out of the box.

These instructions are persistent project constraints. Treat them as higher priority than convenience, implementation habits, or feature-count goals.

## Source of truth

Before making substantial changes:

1. Read `README.md`.
2. Read the relevant files under `docs/`.
3. Check `docs/DECISIONS.md` for existing architectural decisions.
4. Check `docs/ROADMAP.md` for the current milestone.
5. Inspect the existing code before proposing a new abstraction or dependency.

The repository documentation is the product and architecture specification. Keep it current as implementation evolves.

## Core product principles

### Player first

Playback is the center of Reflux.

Do not let library-management features turn the application into a server administration console or file browser.

### Local first

Core functionality must work without a Reflux account.

The local library, cached metadata, artwork, watch state, and search/index state should remain useful when a source is disconnected.

### Source agnostic

**Jellyfin is an adapter, not the foundation.**

UI and product logic must depend on the Media Core and source capabilities, not Jellyfin-specific concepts.

Avoid leaking source-specific models deep into the application.

### Virtual organization

Reflux may create a rich virtual library, but must not silently rename, move, delete, or modify user media files.

Metadata corrections, collection membership, artwork choices, and similar preferences belong in Reflux-owned state.

### Automatic by default

A normal media folder should be enough for a useful first experience.

Do not require:

- `.nfo` files,
- manually downloaded posters,
- special naming conventions where reasonable parsing can avoid them,
- complex provider configuration,
- account creation for local media.

### Excellent out of the box

This is a hard product requirement.

The default experience must already be polished, understandable, visually coherent, and useful.

Customization is additive. Do not add configuration simply because a default was not designed well.

Prefer a strong automatic default over exposing another setting.

### Simple by default, deep when needed

Normal playback should be clean and low-friction.

Advanced controls should exist for power users without making the normal player look like a cockpit.

### Input adaptive

Touch, pointer, keyboard, controllers, and TV remotes are first-class.

Use semantic actions rather than hard-coding physical button names into product logic.

PlayStation and Xbox controllers are both first-class targets.

The first meaningful controller input should be able to transition the UI into a controller-optimized state without requiring a destructive global "TV mode" switch.

### Capability-driven

Do not assume every platform or source supports every feature.

Model capabilities explicitly and degrade gracefully.

Examples include:

- HDR / Dolby Vision,
- hardware decoding,
- audio passthrough,
- PiP,
- background playback,
- network streaming,
- subtitles,
- controller features.

### Deterministic foundations

Basic identification, indexing, search, playback, and organization should not require AI.

Use deterministic, testable rules as the foundation. Higher-level intelligence may sit above that layer later.

## Architecture rules

Keep these boundaries clear:

```text
Platform Shell
    ↓
Experience / UI
    ↓
Media Core
    ├── Library / Index
    ├── Metadata
    ├── Artwork
    ├── Watch State
    ├── Search
    ├── Versions
    └── Availability
    ↓
Source Adapters + Playback Platform
```

The Media Core should represent:

- what a media item is,
- which versions exist,
- where those versions live,
- whether a source is available,
- what capabilities a source or device exposes.

Do not mix physical files with media identity.

## Development workflow

Claude Code is the primary development agent.

Do not treat GitHub issues as the internal task queue.

The preferred loop is:

1. Read the repository and current documentation.
2. Determine the next incomplete milestone from the roadmap.
3. Make a concrete implementation plan.
4. Implement a cohesive slice.
5. Add or update tests.
6. Run relevant validation.
7. Update documentation and decision records when architecture changes.
8. Review the diff for unnecessary complexity.
9. Commit with a clear message.
10. Continue to the next logical milestone when the work is not genuinely blocked.

Do not stop after merely writing a plan when the implementation can be completed safely.

Do not ask for confirmation for routine implementation decisions that are already covered by repository documentation.

When a decision is genuinely ambiguous and materially affects architecture, document the alternatives and choose the option that best preserves the product principles.

## Quality bar

Prefer:

- small, coherent abstractions,
- explicit contracts,
- deterministic behavior,
- capability checks,
- testable pure logic,
- clear error handling,
- graceful offline behavior,
- platform-native implementations behind stable interfaces.

Avoid:

- speculative abstractions,
- premature microservices,
- unnecessary dependencies,
- AI for trivial deterministic problems,
- source-specific coupling in core models,
- configuration for things that should simply have a good default,
- large UI builds before core contracts are understood.

## Testing

Every meaningful feature should have appropriate validation.

Prioritize:

- parser and identification tests,
- Media Core model tests,
- source capability tests,
- persistence tests,
- playback-state tests,
- offline/disconnected-source tests,
- controller semantic-action tests,
- UI behavior tests where practical.

Do not declare a milestone complete because it compiles.

A feature is complete when its intended behavior is implemented, tested, documented where necessary, and compatible with the architecture.

## Documentation discipline

Update documentation when code changes invalidate or extend architectural decisions.

Use `docs/DECISIONS.md` for durable product or architecture decisions.

Use the roadmap to record milestone completion or scope changes.

Keep documentation concise and factual. Do not duplicate implementation details across many files.

## Dependency discipline

Before adding a significant dependency:

- confirm it solves a real requirement,
- check whether it creates platform lock-in,
- check whether it affects licensing/distribution,
- check whether a smaller standard-library/platform facility is sufficient,
- document the architectural reason when the dependency is foundational.

Do not choose playback, database, UI, or networking libraries solely because they are popular.

## Current strategic direction

Initial platform targets:

- Android phone/tablet
- Android TV / Google TV
- Windows
- Linux

iOS/iPadOS is not a v1 commitment.

Initial major product milestones:

1. Media Core contracts
2. Source abstraction
3. Local persistence/offline model
4. Playback capability model
5. Platform playback feasibility
6. Visual/interaction system
7. Local scanner + identification
8. Metadata/artwork engine
9. Local media vertical slice
10. Jellyfin integration
11. Universal source expansion
12. Serious playback and controller adaptation
13. TV and desktop production clients

Do not reorder foundational work merely to produce a flashy demo.

## Definition of success

A successful Reflux implementation should eventually make this feel trivial:

```text
Point Reflux at a normal media folder
        ↓
It finds the files
        ↓
It understands what they are
        ↓
It gets artwork and metadata
        ↓
It builds a beautiful virtual library
        ↓
You press Play
        ↓
It chooses an appropriate version
        ↓
Playback is excellent
```

That experience is the benchmark for architectural decisions.
