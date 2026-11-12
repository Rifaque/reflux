# Contributing to Reflux

Reflux is a pre-release working towards a public beta (docs/ROADMAP.md). Contributions should preserve the project's core principles rather than optimize for feature count.

Reflux is proprietary, not open source ([LICENSE](LICENSE)). An outside contribution would first need an agreement about who owns it, and there is none yet (docs/RELEASE.md, "Legal review").

## Before contributing

Read:

- [Product](docs/PRODUCT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Versioning](docs/VERSIONING.md) (before touching versions, tags, or release metadata)
- [Design](docs/DESIGN.md)
- [Input model](docs/INPUT_MODEL.md)
- [Metadata](docs/METADATA.md)
- [Decision log](docs/DECISIONS.md)
- [Release readiness](docs/RELEASE.md)

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
- `./gradlew :desktop:app:test -Preflux.windowTests=true` runs the real desktop shell end to end: first run with the platform's folder dialog, browsing with a mouse and a (scripted) controller, and playback of generated files with the controls operated by a mouse. It opens windows on your screen, so it only runs on request; add `-Preflux.screenshots=<dir>` to save what it showed. The tests act through the application's own event queue and never move your pointer, but your pointer can still reach the test window, so leave it alone while they run. The playback test also checks the screen itself, so the desktop has to be unlocked and the window uncovered; it says so when it is not. On Linux they need a display with a compositing manager; `Xvfb` with `openbox` and `xcompmgr` is what CI uses.

To run the Linux CI job from any machine with Docker (it builds and tests a copy of the tree, headless first and then in a real window on a virtual display):

```sh
docker build -t reflux-linux-check tools/ci
docker run --rm -v "$PWD:/src:ro" -v reflux-gradle:/home/ubuntu/.gradle reflux-linux-check
```

With a game controller connected, `./gradlew :desktop:app:test --tests "*PhysicalControllerTest*" -Preflux.windowTests=true -Preflux.controller=true -i` opens the application and asks you to press buttons, checking what each press does.

The Android client (`android:app`) is part of the build wherever an Android SDK is installed (`ANDROID_HOME`, or `sdk.dir` in `local.properties`); without one, everything else builds and tests as usual, and `-Preflux.android=false` leaves it out on purpose. `./gradlew :android:app:assembleDebug` builds it, and `./gradlew :android:app:connectedDebugAndroidTest` runs the on-device tests (they need FFmpeg on the build machine, for the test clip) on **every** connected device: set `ANDROID_SERIAL` to the one you mean, for example `ANDROID_SERIAL=emulator-5554`. Phone and tablet layouts are rendered offscreen by the `ui` tests like every other screen, so most layout work needs no device.

The exchange with a real Jellyfin server has a test of its own, `JellyfinLiveTest`, which runs when a server is named and is skipped otherwise. To name yours, create `%USERPROFILE%\.reflux-live\jellyfin.env` (`~/.reflux-live/jellyfin.env` elsewhere), outside the repository, with three lines:

```
REFLUX_JELLYFIN_TEST=<server url>
REFLUX_JELLYFIN_TEST_USER=<a user there for testing>
REFLUX_JELLYFIN_TEST_PASSWORD=<its password>
```

Variables of the same names in the environment take precedence, key by key; only the test reads the file, never Reflux, and nothing in it is repeated in test output. Then `./gradlew :desktop:engine:test --tests "*JellyfinLiveTest*" --rerun-tasks` (Gradle does not treat the file or the environment as an input, so without a forced rerun it may report the test up to date without running it). The test rewrites its user's watch state, so use a dedicated user, not one you care about. With no server of your own, `tools/ci/jellyfin-test-server.sh <scratch-dir>` starts a throwaway one in Docker, and the comment at its top gives the command for it.

Two accounts from one installation (M8, docs/WATCH_STATE.md, "Profiles") have live tests of their own. `JellyfinTwoAccountsLiveTest` makes a second user and deletes it again, so it runs only against a throwaway server whose administrator is named: start one with `tools/ci/jellyfin-test-server.sh <scratch-dir>`, then `REFLUX_JELLYFIN_ADMIN_URL=http://127.0.0.1:8096 REFLUX_JELLYFIN_ADMIN_USER=reflux REFLUX_JELLYFIN_ADMIN_PASSWORD=reflux-test ./gradlew :desktop:engine:test --tests "*JellyfinTwoAccountsLiveTest*" --rerun`; never point it at your own server. `PlexTwoAccountsLiveTest` needs two plex.tv accounts' tokens and a server they can reach, as `REFLUX_PLEX_URL`, `REFLUX_PLEX_TOKEN_A`, and `REFLUX_PLEX_TOKEN_B` in the environment or in `~/.reflux-live/plex.env`. Both write what they observed, without tokens, to `desktop/engine/build/live/`.

To read a failed CI run without its whole log: `tools/ci/failures.sh <run-id>` prints the failed jobs and the failed tests with their first assertion.

Tests that need libmpv, FFmpeg, or a display skip themselves on a machine without them. Set `REFLUX_REQUIRE_ENGINE_TESTS=true` (CI and the container do) to have them fail instead, when a green run must mean they ran.

Shared (`commonMain`) code must stay free of platform APIs; `check` fails when it is not, and the message says which lines.

## Changes

Keep changes focused and explain architectural consequences when they cross core boundaries.

When introducing a significant product or architectural decision, update docs/DECISIONS.md. Record what was verified, and how, in docs/VERIFICATION.md, and user-visible changes under `Unreleased` in CHANGELOG.md.
