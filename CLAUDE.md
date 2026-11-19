# Reflux — Claude Code

**Read [AGENTS.md](AGENTS.md) first.** It is the project's instructions for every agent: mission, principles, architecture rules, workflow, quality bar, and the documents that are the source of truth. This file only adds what is specific to working here with Claude Code: where the work stands and the machines it is tested on.

## Where the work stands

- **Beta 2** is planned in three phases; the register of every bug and feature (B-001 to B-042, F-001 to F-005, and two sidebar features), the owner's defaults, the verification matrix, and the gate are in [docs/planning/BETA-2.md](docs/planning/BETA-2.md). Phase 1 is in [docs/planning/PHASE-1-PLAN.md](docs/planning/PHASE-1-PLAN.md).
- **Beta 2 is not cut, versioned, built for release, or tagged** until Phases 2 and 3 are accepted and the gate holds (DECISIONS.md, 2026-11-19). `0.9.0-beta.1` stays as it is. Versions follow docs/VERSIONING.md, and only when the owner asks.
- Nothing is merged, pushed, deployed, released, or changed in a store console without the owner's explicit approval for that action.
- Defaults that are acceptance criteria, not preferences: phone and tablet interface haptics **off** (opt-in); controller rumble **on** where supported (disableable); video haptics **off**, separately from both; statistics **off** (informed opt-in, nothing sent before it, no third-party SDK without review).

## Testing environment

Record every result in docs/VERIFICATION.md with the environment it ran in, and **never report an emulator, VM, or container result as a physical-device result.** Device serials, network addresses, and credentials are kept out of the repository: find devices with `adb devices`, and ask the owner when one is missing. Never write a password, token, or key into a file.

| Environment | Kind | Used for |
| --- | --- | --- |
| Windows 11 development machine | Physical | Desktop app, packaged builds, the full `./gradlew check`, window tests (`-Preflux.windowTests=true`) |
| Xiaomi Pad 7 (HyperOS, Android 16) | Physical, USB adb | Tablet. HyperOS refuses USB installs (`INSTALL_FAILED_USER_RESTRICTED`) until the owner accepts them on the device |
| Nothing Phone (2a) (Android 16) | Physical, USB adb | Phone |
| Fire TV Stick 4K Plus | Physical, network adb (its address changes; ask) | Fire TV |
| Google TV emulator (AVD `Reflux_TV`, Android 16) | **Emulator** | Android TV / Google TV. There is no physical Google TV; results are "Verified on emulator" |
| WSL2 Ubuntu 24.04 (JDK 21, libmpv2, `de_DE` locale, Xvfb, ffmpeg) | **VM** | Linux unit and engine tests, packaging the Linux tarball. Not a Linux desktop: no real display or sound |
| Docker Desktop containers (Ubuntu 22.04 and 24.04, Debian 12, Fedora 41, Arch) | **Containers** | Clean-distribution checks of the tarball and the libmpv hint |
| Ubuntu QA virtual machine (VirtualBox) | **VM** | A Linux desktop session for walks. Intended: 4 vCPU, 8 GB RAM, 60 GB disk, 3D acceleration. As last found (2026-11-19): 6 vCPU, 6 GB RAM, VMSVGA with 16 MB video memory and no 3D, not registered with VirtualBox, in a saved state; reconcile with the owner before relying on it, and say which configuration a result came from |

No physical Linux desktop and no live Jellyfin server at two addresses are available to the agent; checks that need them are the owner's, written as procedures in VERIFICATION.md.

**Android instrumented tests** run with `am instrument` against the debug build (`dev.rsxdevelops.reflux.debug`), installed beside the store build, never over it. **Website checks** (`website/`): `npm test`, `npm run build`, `npm run check-dist`, `npm run check-links`, `node scripts/check-layout.mjs --screenshots` (screenshots in `website/test-results/layout/`, to be looked at, not only counted).

**Known intermittents** (not to be "fixed" by weakening the test): `PointerTest.controlsLeaveWhilePlayingAndReturnWithThePointer`; `AboutWindowTest` (fails on unmodified HEAD too). Re-run, and record both runs.
