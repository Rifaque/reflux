> **Historical (archived 2026-11-26).** The procedure for the move to Ubuntu, carried out on 2026-11-26: the result is [UBUNTU-VALIDATION-REPORT.md](../../verification/UBUNTU-VALIDATION-REPORT.md). Kept unchanged as a record. Its section 4, the Linux playback acceptance checklist that validation followed, is now the current procedure [LINUX-PLAYBACK-ACCEPTANCE.md](../../verification/LINUX-PLAYBACK-ACCEPTANCE.md). Current documents: [README.md](../../../README.md#documentation); this archive: [history/README.md](../README.md).

# Ubuntu transition: from the CachyOS laptop to Ubuntu, and validating Reflux there

**What this is.** The practical procedure for the owner's laptop moving from CachyOS to Ubuntu (2026-11-25 onward): what to keep before the wipe, what to install afterwards, and the checklist that decides whether Reflux's Linux player works there. Written in the Windows repository audit of 2026-11-25; **nothing in it has been run on Ubuntu on this laptop yet**. The machine as CachyOS knew it (hardware, drivers, Hyprland, what was verified) is in [CACHYOS-FINAL-HANDOVER.md](CACHYOS-FINAL-HANDOVER.md) and is not repeated here; the CachyOS record is [planning/CACHYOS-FINAL-AUDIT-LEDGER.md](../../verification/CACHYOS-FINAL-AUDIT-LEDGER.md); results go in [VERIFICATION.md](../../verification/VERIFICATION.md), issues in [planning/BETA-BUG-TRACKER.md](../../BETA-BUG-TRACKER.md).

**Status (2026-11-26).** The validation was carried out on Ubuntu 26.04.1 on 2026-11-26, on `cachyos-final-audit` at `5de9fae` (code `406382b`): [UBUNTU-VALIDATION-REPORT.md](../../verification/UBUNTU-VALIDATION-REPORT.md) (no playback blocker; one new issue, [B-104](../../BETA-BUG-TRACKER.md#b-104)). Its record was merged into `beta-3` the same day: [planning/BETA-3-INTEGRATION-AUDIT.md](../../verification/BETA-3-INTEGRATION-AUDIT.md). The text below is the procedure as written before it.

**Scope after the install** (the owner's plan): bring Reflux up on Ubuntu, fix only **major playback blockers**, then pause development and return to systematic Beta 2 testing. Not in scope: the Beta 3 separation, new features, a release.

**Which branch to test.** `main` (`1d65dcf`) is Beta 2 as released: its Linux player is the old two-window design (B-084), which the integrated renderer replaces. The integrated renderer, B-092, B-099 and B-102 exist only on `cachyos-final-audit` and `beta-3` (code `406382b` on both; `beta-3` differs only by documentation commits after the closure's `5de9fae`). Validate the player on `cachyos-final-audit` (or `beta-3`: the same code); use the released Beta 2 archive only to compare with what testers have.

## 1. Before the wipe (on CachyOS)

The repository needs nothing from the laptop: every branch is on GitHub, with no stash and no untracked work (handover, "Where the work is"; re-checked from Windows on 2026-11-25: `origin/cachyos-final-audit` = `origin/beta-3` = `5de9fae`). What follows is the owner's own data, which the repository cannot recover.

- [ ] **Reflux's own data**, if it is to be kept: `~/.local/share/reflux` (or `$XDG_DATA_HOME/reflux`; `REFLUX_HOME` if set). It holds the library database, profiles, settings and `reflux.log`. Copy the folder while Reflux is closed.
- [ ] **Server sign-ins** are not in that folder: on Linux they are in the desktop keyring (the Secret Service, through `secret-tool`). They will be lost with the keyring; plan to sign in to Jellyfin and Plex again rather than copy keyring files between distributions.
- [ ] **Live-server test credentials**, if the laptop has `~/.reflux-live/jellyfin.env` (on Windows it does; on CachyOS it was not recorded): keep it outside the repository, never commit it.
- [ ] **Any local test media** the owner wants (the session's own scratch media is disposable: the window tests make their own films with `ffmpeg`).
- [ ] **The owner's desktop configuration** (Hyprland, Noctalia), only if wanted on Ubuntu; Reflux needs none of it.
- [ ] **Git:** no Reflux branch needs pushing. If anything else was committed on the laptop since, `git status` and `git log origin/<branch>..` on it before wiping.
- [ ] **Not needed:** the portable JDK (`~/.local/share/jdks`), `build/`, `.gradle/`, Gradle caches, the session's `/tmp/claude-1000/` scratch folder (handover, "Lost with the wipe").

**Disk and firmware.** The project has no record of the laptop's disk layout, partition table, other operating systems on it, or Secure Boot state: **unknown**. Identify the target disk by model and size in the Ubuntu installer (and `lsblk -o NAME,SIZE,MODEL,FSTYPE,MOUNTPOINTS` from the live session) before choosing "erase", and confirm nothing else (a Windows install, a data partition) is on it. Firmware is EFI on this class of laptop, but that too was not recorded. Reflux itself has no Secure Boot or bootloader dependency.

**Graphics drivers.** The laptop is hybrid: Intel Arrow Lake integrated graphics (Mesa) and an NVIDIA RTX 5060 Max-Q. On CachyOS every Reflux playback drew on the Intel GPU through Mesa; that is the only configuration the integrated renderer was verified on. Whatever NVIDIA driver Ubuntu installs, record which GPU draws Reflux's window (section 4, R1): it decides which decoding Reflux asks for and whether the result is comparable with CachyOS. NVIDIA's driver with Secure Boot on asks for module signing during the install: that is Ubuntu's procedure, not the project's, and was never exercised here.

## 2. After the install: the development environment

Every version below is from the repository, not from memory. Ubuntu's release is the owner's choice and is **unknown** here; the package names are those hosted CI installs on Ubuntu 24.04 (`tools/ci/linux-packages.txt`) and those checked in the Ubuntu 26.04 QA VM.

| Need | Value | Source |
| --- | --- | --- |
| JDK | **21** (any vendor; CI uses Temurin). Gradle's toolchain asks for 21 | `jvmToolchain(21)` in every module; `.github/workflows/ci.yml` |
| Gradle | the wrapper, **9.7.1**: always `./gradlew`, never a system Gradle | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin / Compose Multiplatform | **2.4.21 / 1.12.1** | `gradle/libs.versions.toml` |
| Skiko | **0.150.1** (comes with Compose 1.12.1). `SkikoGl.TESTED_VERSION` pins it: a different Skiko makes `LinuxGlVideoTest` fail and the log say "untested" | `desktop/app/.../SkikoGl.kt` |
| JNA | 5.19.1 | `gradle/libs.versions.toml` |
| libmpv | the distribution's: **`libmpv2`**. Verified versions: mpv **0.42.0** (CachyOS, the integrated renderer), libmpv2 **0.41.0-2ubuntu4** (Ubuntu 26.04 VM, *before* the integrated renderer existed). The render API code was written against 0.42's `render.h`/`render_gl.h`; an older libmpv is unverified for it | handover; VERIFICATION.md, B-031 |
| Test media | `ffmpeg` (the window tests make their own films) | `tools/ci/linux-packages.txt` |
| Virtual display (optional) | `xvfb openbox xcompmgr x11-utils` | same |
| OpenGL (software in CI) | `libgl1-mesa-dri libglx-mesa0 libegl1` | same |
| Fonts for the layout tests | `fonts-liberation` (and `fonts-dejavu-core`), with **`FONTCONFIG_FILE=tools/ci/fonts.conf`** around the Gradle check, as CI does: without it Linux measures text with a wider font and two phone-layout tests fail (B-089) | `ci.yml`; `tools/ci/linux-check.sh`; DECISIONS.md, "Linux CI measures text with Liberation Sans" |
| Keyring (server sign-ins) | `libsecret-tools` (provides `secret-tool`) | RELEASE.md; the website's Linux guide |
| Desktop entry checks (optional) | `desktop-file-utils` (`desktop-file-validate`, used on CachyOS for CACHY-001) | the ledger, CACHY-001 |

On Ubuntu, in one line (the CI list plus the keyring tool; the owner runs it):

```sh
sudo apt-get install --no-install-recommends libmpv2 ffmpeg xvfb openbox xcompmgr x11-utils \
  libgl1-mesa-dri libglx-mesa0 libegl1 fonts-dejavu-core fonts-liberation libsecret-tools git binutils
```

**Build and test** (from the repository root):

| What | Command |
| --- | --- |
| Unit tests as hosted CI runs them on Linux | `FONTCONFIG_FILE=$PWD/tools/ci/fonts.conf REFLUX_REQUIRE_ENGINE_TESTS=true ./gradlew check :tools:cli:installDist -Preflux.android=false` |
| Run the desktop app from source | `./gradlew :desktop:app:run` |
| Desktop window tests (real windows) | `tools/ci/desktop-window-tests.sh --rerun-tasks` (stubs `zenity`/`kdialog`; never run them with Gradle directly: `FirstRunTest` would open a real folder dialog) |
| The Linux archive (a local test build, never published) | `./gradlew :desktop:app:packageDesktopArchive` → `desktop/app/build/release/reflux-<version>-linux-x64.tar.gz` |
| The whole Linux CI job in a container | `tools/ci/linux-check.sh` (its header has the `docker build`/`docker run` lines) |
| Documentation links | `python3 -I tools/docs/check_links.py` |

The Android modules need the Android SDK, which was never set up on the laptop; leave them to `-Preflux.android=false` and to Windows and CI.

## 3. A CI-identical virtual display (as used for B-092)

Hosted CI runs the window tests on **Xvfb `:99`, 1600 × 1000 × 24, with openbox and xcompmgr**; there Skiko draws in software, so every playback takes the fallback (`linux-x11-shape-fallback`). In the final CachyOS session the same three ran from Arch packages extracted into a scratch folder (nothing installed); on Ubuntu they are ordinary packages (section 2). Exactly as the handover records:

```sh
Xvfb :99 -screen 0 1600x1000x24 -nolisten tcp &
DISPLAY=:99 openbox &
DISPLAY=:99 xcompmgr &
export DISPLAY=:99 PULSE_SERVER=unix:/nonexistent PIPEWIRE_REMOTE=/nonexistent   # no sound device: the films carry test tones
unset WAYLAND_DISPLAY
bash tools/ci/desktop-window-tests.sh --rerun
```

`tools/ci/desktop-window-tests.sh` starts the same three itself when `DISPLAY` is empty. Rules learned on CachyOS (B-092):

- **Always `--rerun` (or `--rerun-tasks`).** An environment variable is not a Gradle input; without it a repeat can be a cached result, which counts for nothing.
- **Run the whole suite, as CI does**, not only the failing class: B-092 appeared only after other playback tests had run in the same JVM.
- **Keep sound unreachable** (`PULSE_SERVER`, `PIPEWIRE_REMOTE` above) and test windows off any screen in use (CLAUDE.md: never drive the owner's desktop while it is in use).

On a real Ubuntu desktop session (GNOME on Wayland runs Reflux through XWayland) the same script runs the window tests on the real display, and the player takes the integrated renderer where Skiko gets OpenGL. GNOME does not tile, so the five or six tiling failures seen on Hyprland (VERIFICATION.md, "CachyOS final audit") are not expected there; any that remain are new evidence, to be compared against `1d65dcf` before being called regressions.

## 4. Linux playback acceptance checklist

Record every result in VERIFICATION.md with the environment (Ubuntu release, desktop session, GPU and driver, libmpv version, Skiko version, branch and commit) and mark each **physical Linux**. A failure gets a tracker entry with the next free ID and is compared with `1d65dcf` (the released player) before it is called a regression. Before any check: `REFLUX_HOME` pointed at a copy or a fresh folder, never the owner's own data; `reflux.log` is in that folder.

**Environment first**

- [ ] **E1** Record: Ubuntu release and kernel; session (`echo $XDG_SESSION_TYPE`, the desktop); GPUs and which drives the display; Mesa and NVIDIA driver versions; `libmpv2` version (`apt-cache policy libmpv2`); `vainfo` output if installed. Unknown until measured: all of it.

**Renderer and decoding**

- [ ] **R1 Renderer selection.** Play a film; `reflux.log` has `Video renderer: linux-mpv-gl-integrated (…, vo=libmpv, hwdec=…)`. If it says `linux-x11-shape-fallback (reason)`, the reason is the finding (Skiko not on OpenGL, a reflection step failed, a newer Skiko).
- [ ] **R2 Which GPU draws.** The log's renderer line and the decoding report name the GPU and `hwdec`: `vaapi-copy` is expected on Mesa (copy-back, as on CachyOS), `auto-safe` on NVIDIA's own OpenGL (zero-copy `nvdec` was measured there only on a hidden workspace). NVIDIA drawing Reflux's window was **never verified**.
- [ ] **R3 Hardware decoding.** The decoding report after 120 frames: `hwdec-current` not `no` for H.264, HEVC, HEVC 10-bit, AV1 and VP9 at 1080p and 4K where the GPU supports them; dropped frames 0; A/V sync near 0.
- [ ] **R4 Software fallback.** `REFLUX_RENDER_HWDEC=no` (software decoding): the picture still plays, CPU higher, no failure.
- [ ] **R5 The fallback renderer.** `REFLUX_NATIVE_VIDEO_WINDOW=1`: the log names `linux-x11-shape-fallback`; one window; controls over black bands; docked corners square (B-100, known).
- [ ] **R6 Start-up.** The render context is made **before** the file opens (log order); no "sound without picture" (CACHY-011's fix 3).

**The player**

- [ ] **P1 Fullscreen** and back; the picture upright (timecode at top left of the test card), the aspect right (pillarbox and letterbox), BT.709 limited range looking right.
- [ ] **P2 Controls over the picture**: gradient, title, timeline, speed, audio and subtitle menus (the last two were never walked on a real desktop), all over the real video, in one window.
- [ ] **P3 Resize**: drag the window while playing; the picture and controls follow in the same frame.
- [ ] **P4 Mini-player**: dock, the video's corners rounded at the dock's radius with no video outside the curve (accepted on CachyOS at scale 1.6 and 1.25), back to the full player; on the fallback, square (B-100).
- [ ] **P5 Context replacement** (B-099): `IntegratedContextReplacementTest` on the real session (it skips on the fallback): PASS, and the picture comes back.
- [ ] **P6 Disposal and shutdown**: close the player, play again (20 times: `IntegratedPlayerLifecycleTest`); close the window mid-playback (the position is kept); Reflux's process ends (`pgrep -f Reflux` empty). A process left without a window is B-101: take `kill -QUIT <pid>` with output to a file before killing it.
- [ ] **P7 Audio**: sound through the session's default output (PipeWire on current Ubuntu); a device change while playing. (Tests run with sound unreachable; this one is by hand, at the owner's choice of volume.)
- [ ] **P8 Formats**: MKV, MP4, WebM; H.264, HEVC, HEVC 10-bit, AV1, VP9; AAC, AC-3, E-AC-3, Opus; SRT, ASS and PGS subtitles; one file over a network share and one from Jellyfin. HDR: no HDR display or source was ever available; record it as not tested.
- [ ] **P9 Display**: a second monitor and a fractional scale if available (`REFLUX_SCALE` overrides Reflux's own; B-063 covers the scale).

**Desktop integration** (needs the packaged archive)

- [ ] **D1 Window identity**: `xprop WM_CLASS` on Reflux's window says `reflux` (under GNOME, `xprop` works on XWayland windows).
- [ ] **D2 Desktop entry** (B-102): first start of the extracted archive writes `~/.local/share/applications/reflux.desktop` and `Reflux.desktop` beside `bin/`; the dock or Activities shows Reflux's logo and groups the window; removing the entry is respected on the next start. `bin/install-desktop-entry --remove` takes it away.
- [ ] **D3 Focus**: Reflux comes forward when launched; a second launch opens a second Reflux (B-103, known, the owner's decision).
- [ ] **D4 Keyring**: sign in to a server, restart, still signed in (`secret-tool` present).

**Automated, on the real session and on Xvfb**

- [ ] **A1** The unit tests (section 2), with `FONTCONFIG_FILE` set: B-089's pair should then pass, as on hosted CI; if they still fail, B-089's entry gets that result.
- [ ] **A2** The window tests on the virtual display (section 3): expected as hosted CI on `406382b` (passed) and the CachyOS Xvfb runs of the final code (101 tests, 14 skipped, 0 failed).
- [ ] **A3** The window tests on the real GNOME session: a first real-desktop baseline for Ubuntu.

**What is not expected to carry over from CachyOS**: Hyprland's rules, Noctalia's dock, `force_zero_scaling`, and the tiling failures. **What is unknown**: everything about NVIDIA's OpenGL drawing Reflux, AMD, GNOME and KDE behaviour, Wayland-native drawing (Reflux runs through XWayland; it has no Wayland backend).

## 5. What a "major playback blocker" is here

For the owner's stop-after-blockers plan: a blocker is no picture, no sound, a crash, a hang, or a player that cannot be closed, on the default path (R1's renderer with R3's decoding) for a common format (P8). Everything else (a fallback cosmetic, a single exotic format, a performance figure) goes to the tracker for Beta 3, as the freeze requires.
