# Reflux — Claude Code

**Read [AGENTS.md](AGENTS.md) first.** It is the project's instructions for every agent: mission, principles, architecture rules, workflow, quality bar, and the documents that are the source of truth. This file only adds what is specific to working here with Claude Code: where the work stands and the machines it is tested on.

## Where the work stands

- **Beta 2** is planned in three phases: scope, phase and work order, exit criteria, the owner's defaults, the verification matrix, and the gate are in [docs/planning/BETA-2.md](docs/history/phases/BETA-2-PHASES.md). Phase 1 (accepted) is in [docs/history/phases/PHASE-1-PLAN.md](docs/history/phases/PHASE-1-PLAN.md); Phase 2 (accepted and closed by the owner, 2026-11-20: its audit, the interaction contract, the plan, the closure) in [docs/history/phases/PHASE-2-PLAN.md](docs/history/phases/PHASE-2-PLAN.md).
- **Phase 3** (the owner's work order, 2026-11-20) is **accepted and formally closed by the owner** (2026-11-20).
  - Its closure record: BETA-2.md, "Phase 3 closure"; the runs: VERIFICATION.md's three "Phase 3" sections.
  - **Hard freeze:** Beta 2's fix scope is closed. Anything found from now on goes to the tracker's Beta 3 backlog, unless the owner authorises a critical Beta 2 blocker.
  - B-027, B-038 and B-040 are V1.1 candidates, researched in [docs/planning/V1.1-LIBRARY-RESEARCH.md](docs/planning/V1.1-LIBRARY-RESEARCH.md), not to be implemented without the owner.
  - F-003 (video haptics) is deferred by the owner with no release assigned.
- **Phase 4's brief** (documentation consolidation, the website, Beta 2's contents, the roadmap, the roadmap easter egg) is [docs/history/phases/PHASE-4-HANDOFF.md](docs/history/phases/PHASE-4-HANDOFF.md). Nothing of it is started without the owner.
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
| Phone and tablet emulators (AVDs `Reflux_Phone`; `Reflux_Tablet`, 2560 × 1600 at 320 dpi, made 2026-11-19) | **Emulator** | When the physical phone or tablet is not attached, or cannot be used. Run them headless (`-no-window`) so nothing appears on the owner's screens. At 1280 dp wide `Reflux_Tablet` is laid out with the desktop sidebar |
| WSL2 Ubuntu 24.04 (JDK 21, libmpv2, `de_DE` locale, Xvfb, ffmpeg) | **VM** | Linux unit and engine tests, packaging the Linux tarball. Not a Linux desktop: no real display or sound |
| Docker Desktop containers (Ubuntu 22.04 and 24.04, Debian 12, Fedora 41, Arch) | **Containers** | Clean-distribution checks of the tarball and the libmpv hint |
| Ubuntu QA virtual machine `Reflux-QA-Ubuntu-26.04` (VirtualBox 7.2, host name `rs-vm`) | **VM** | A Linux desktop session for walks. Created 2026-11-19 from the Ubuntu 26.04 desktop ISO: 4 vCPU, 8 GB RAM, 60 GB disk, EFI, VMSVGA with 3D acceleration and 128 MB video memory, NAT. Ubuntu 26.04 installed (GNOME on Wayland; Reflux runs through XWayland), with `libmpv2` 0.41, ffmpeg, OpenJDK 21 (for the CLI), wmctrl, the `de_DE.UTF-8` locale, and an SSH server reached at `127.0.0.1:2222` through a VirtualBox NAT forward, by key only (a session adds its own key through the VM's terminal). Drive it with `VBoxManage controlvm … keyboardputscancode` and `screenshotpng`; xdotool's input asks GNOME for remote-desktop permission, which is not to be granted. Its sign-in is the owner's and is never written down. An older, unregistered `Ubuntu` VM folder (6 vCPU, 6 GB, no 3D, saved state) is left as it was; say which VM a result came from |

No physical Linux desktop and no live Jellyfin server at two addresses are available to the agent; checks that need them are the owner's, written as procedures in VERIFICATION.md.

**Android instrumented tests** run with `am instrument` against the debug build (`dev.rsxdevelops.reflux.debug`), installed beside the store build, never over it. **Website checks** (`website/`): `npm test`, `npm run build`, `npm run check-dist`, `npm run check-links`, `node scripts/check-layout.mjs --screenshots` (screenshots in `website/test-results/layout/`, to be looked at, not only counted).

**Never drive the owner's desktop or devices while they are in use.** Before any SendKeys, window move, or screen capture on Windows, or an instrumented run on the phone or tablet, check what is in front, and ask if unsure. On 2026-11-19 a script's keystrokes reached the owner's game. The Pad 7's debug build has a PIN on Main: never guess it, clear the app, change it, or remove it. When the owner gives it for a session, pass it only on the command line (`am instrument -e unlockPin …`, read by `PhaseTwoWalkOnDeviceTest`), never into a file. The Pad 7 has no vibrator, so it shows no Interface haptics switch.

**Input and haptics rules (Phase 2):**
- Every text field goes through `Modifier.textEntry`; no screen sets `textInputActive`.
- Everything felt goes through `Haptics`; `HapticsTest` fails otherwise.
- X opens Media Quick View on a focused film or show (F-P3), and a remote's Menu does on a card; Y toggles the sidebar (F-P2); a controller's Start always opens Sources; the right stick moves through scenes in the full player (B-029), and pressing it brings the mini-player back to the full player (B-068); RT, or B held, closes the mini-player (B-052); LT hides and shows every hint, in the player too, kept across restarts (B-067); View stays fullscreen; see INPUT_MODEL.md, "Controller mapping".
- A physical keyboard never presses, lights or moves a key of the Reflux keyboard (F-P4, D5).
- Desktop input automation goes through a guard: send keys only after checking the foreground window is the Reflux test process (raise it with `AttachThreadInput`, never by sending keys), move the pointer only where Reflux's window is under it, and capture only Reflux's own windows (PrintWindow), never the screen.

**Known intermittents** (not to be "fixed" by weakening the test): `PointerTest.controlsLeaveWhilePlayingAndReturnWithThePointer` and `AccessibilityTest.withoutAScreenReaderTheyStillGo` (B-044: the player's chrome mixes the test clock and the wall clock). Re-run, and record both runs. `AboutWindowTest`'s failure was a stale test, fixed in Phase 3 (B-045).
