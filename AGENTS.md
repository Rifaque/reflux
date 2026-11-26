# Reflux — Agent Instructions

## Mission

Reflux is a **media player on steroids**.

It is a universal, local-first media client built around the user's media rather than any particular media server. The core experience must make media easy to find, understand, organize, and play while remaining excellent out of the box.

These instructions are persistent project constraints. Treat them as higher priority than convenience, implementation habits, or feature-count goals.

## Source of truth

Before making substantial changes:

1. Read `README.md`.
2. Read the relevant files under `docs/`.
3. Check `docs/decisions/DECISIONS.md` for existing architectural decisions.
4. Check `docs/ROADMAP.md` for product status and the version plan, and `docs/release/BETA.md` for the beta in progress. ROADMAP.md is the one source of truth for product status; the root `README.md`, "Documentation", says which document holds what.
5. Inspect the existing code before proposing a new abstraction or dependency.

The repository documentation is the product and architecture specification. Keep it current as implementation evolves.

## Versions and releases

**Before changing anything about versions or releases, read `docs/release/VERSIONING.md` and follow it.** That covers `reflux.version` in `gradle.properties`, Android `versionCode`/`versionName`, desktop package versions, `CHANGELOG.md` release headings, Git tags, and store or package metadata. In particular: never change the version unless the user explicitly asks for a build or release to be cut, never write a `versionCode` by hand, never reuse a version, and never create or push tags unless asked. Add user-visible changes to `CHANGELOG.md` under `Unreleased` as part of the change that makes them.

Where things are recorded:

- `docs/ROADMAP.md`: where Reflux is, the V1.0 criteria, and the versions after 1.0 (V1.1 to V2.0).
- `docs/release/BETA.md`: the beta programme: builds, scope, gates, known issues, beta operations. `docs/release/BETA-NOTES.md`: what testers read.
- `docs/BETA-BUG-TRACKER.md`: every issue's status and evidence.
- `docs/verification/VERIFICATION.md`: what was run to verify work, on what, with what result. Verification logs do not go in the roadmap.
- `docs/release/RELEASE.md`: the release process, the platform matrix, and the release checklists.
- `docs/release/WEBSITE.md`: the website. `store-assets/`: store screenshots and graphics per release (LaunchFrame).
- `docs/decisions/DECISIONS.md`: durable product and architecture decisions.
- `docs/history/`: archived plans, milestone records and older verification. Historical, never current.

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

Use `docs/decisions/DECISIONS.md` for durable product or architecture decisions.

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

V1 platform targets:

- Android phone/tablet (Google Play)
- Android TV / Google TV (Google Play)
- Fire TV running Fire OS (Amazon Appstore), a separate distribution target, never described as Android TV
- Windows
- Linux

Not V1: Vega OS (a future compatibility track) and iOS/iPadOS.

The product moved from milestones to versions in Phase 4 (2026-11-21). The milestones M0 to M11 that built Reflux are complete and archived (`docs/history/roadmaps/ROADMAP-M0-M13.md`, whose "Earlier numbering" maps every older name); M12's qualification and beta continue as the beta programme in `docs/release/BETA.md`, and M13 (release candidate and 1.0) is V1.0 in `docs/ROADMAP.md`. After 1.0 the plan is by version: V1.1, V1.2, V1.3, V1.5, V2.0. Work on what BETA.md and ROADMAP.md say is next. The Reflux website is release infrastructure; it is live.

### Milestone execution rules (from the 2026-11-11 rebaseline)

- One master prompt per milestone takes it from implementation through testing, fixes, documentation, verification, and closure; at most one follow-up, and only for a genuine blocker or an essential correction. The beta runs one prompt per beta build cycle.
- Run the minimum validation the change needs: targeted tests locally, the full `check` on GitHub CI, device walks once per changed form factor at a milestone's end. The comprehensive cross-platform qualification is the beta's (BETA.md). Never weaken a test or an assertion to save effort; defer what is not required instead.

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
