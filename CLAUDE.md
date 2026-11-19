# Reflux — Claude Code

**Read [AGENTS.md](AGENTS.md) first.** It is the project's instructions for every agent: mission, principles, architecture rules, workflow, quality bar, and the documents that are the source of truth. This file only adds what is specific to working here with Claude Code: where the work stands and the machines it is tested on.

## Where the work stands

- **Beta 2** is planned in three phases: scope, phase and work order, exit criteria, the owner's defaults, the verification matrix, and the gate are in [docs/planning/BETA-2.md](docs/planning/BETA-2.md). Phase 1 is in [docs/planning/PHASE-1-PLAN.md](docs/planning/PHASE-1-PLAN.md).
- **Every issue's detail is in [docs/planning/BETA-BUG-TRACKER.md](docs/planning/BETA-BUG-TRACKER.md)** (B-001 to B-042, F-001 to F-005, F-P1/F-P2, and new issues from B-043). Read an issue's entry before working on it and update it *as the work happens*: investigation, cause, fix, tests, each kind of verification, blockers. Code changed without its tracker entry updated is incomplete work. New issues take the next free ID; phase changes and deferrals need the owner's decision.
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
| Ubuntu QA virtual machine `Reflux-QA-Ubuntu-26.04` (VirtualBox 7.2, host name `rs-vm`) | **VM** | A Linux desktop session for walks. Created 2026-11-19 from the Ubuntu 26.04 desktop ISO: 4 vCPU, 8 GB RAM, 60 GB disk, EFI, VMSVGA with 3D acceleration and 128 MB video memory, NAT. Ubuntu 26.04 installed (GNOME on Wayland; Reflux runs through XWayland), with `libmpv2` 0.41, ffmpeg, OpenJDK 21 (for the CLI), wmctrl, the `de_DE.UTF-8` locale, and an SSH server reached at `127.0.0.1:2222` through a VirtualBox NAT forward, by key only (a session adds its own key through the VM's terminal). Drive it with `VBoxManage controlvm … keyboardputscancode` and `screenshotpng`; xdotool's input asks GNOME for remote-desktop permission, which is not to be granted. Its sign-in is the owner's and is never written down. An older, unregistered `Ubuntu` VM folder (6 vCPU, 6 GB, no 3D, saved state) is left as it was; say which VM a result came from |

No physical Linux desktop and no live Jellyfin server at two addresses are available to the agent; checks that need them are the owner's, written as procedures in VERIFICATION.md.

**Android instrumented tests** run with `am instrument` against the debug build (`dev.rsxdevelops.reflux.debug`), installed beside the store build, never over it. **Website checks** (`website/`): `npm test`, `npm run build`, `npm run check-dist`, `npm run check-links`, `node scripts/check-layout.mjs --screenshots` (screenshots in `website/test-results/layout/`, to be looked at, not only counted).

**Known intermittents** (not to be "fixed" by weakening the test): `PointerTest.controlsLeaveWhilePlayingAndReturnWithThePointer`; `AboutWindowTest` (fails on unmodified HEAD too). Re-run, and record both runs.
