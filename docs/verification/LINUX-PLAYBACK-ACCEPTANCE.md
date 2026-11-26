# Linux playback acceptance

The procedure that decides whether Reflux's Linux player works on a given Linux desktop: before a Linux release, after a change to the Linux renderer or its packaging, and on each new Linux machine. Results go in [VERIFICATION.md](VERIFICATION.md); failures in the [tracker](../BETA-BUG-TRACKER.md).

Extracted on 2026-11-26 from section 4 of the archived Ubuntu transition procedure ([history/linux/UBUNTU-TRANSITION.md](../history/linux/UBUNTU-TRANSITION.md), which stays as written), with what its first run taught ([UBUNTU-VALIDATION-REPORT.md](UBUNTU-VALIDATION-REPORT.md)). The check IDs (E1, R1–R6, P1–P9, D1–D4, A1–A3) are the original ones, so earlier results stay comparable.

**Part 1** applies to every Linux desktop. **Part 2** is what depends on the distribution, the desktop or the hardware.

## Rules for every run

- **Say what it ran on**, and never count one kind as another: a **physical Linux desktop**, a virtual X display on a physical machine (Xvfb: CI's environment, not a desktop), a VM, a container. Record the environment (E1) with every result.
- **Data:** `REFLUX_HOME` pointed at a copy or a fresh folder, never someone's own data; `reflux.log` is in that folder.
- **A failure** gets a tracker entry with the next free ID, and is compared with the released player (the latest release's tag) before it is called a regression.
- **Never drive a desktop someone is using** (CLAUDE.md). Real-desktop runs need the owner's agreement, a screen kept free for Reflux's windows, and sound at a volume the owner chooses.
- **Windows must be visible.** On GNOME a covered XWayland window is presented at about one frame a second (the Ubuntu report, section 4): with the window covered a performance walk dropped ~581 of ~600 frames per file, and 0 visible. A real-desktop result counts only with Reflux's window visible.
- **Gradle runs are fresh:** always `--rerun` (or `--rerun-tasks`); an environment variable is not a Gradle input, so a repeat can otherwise be a cached result. Stop running Gradle daemons before changing the environment: a daemon started elsewhere keeps its own.

### What counts as a blocker

As the transition procedure defined it (its section 5): no picture, no sound, a crash, a hang, or a player that cannot be closed, **on the default path** (R1's renderer with R3's decoding) **for a common format** (P8). Everything else (a fallback cosmetic, one exotic format, a performance figure) is a tracker entry, not a blocker.

## Part 1: acceptance checks (every Linux desktop)

**Environment**

- [ ] **E1** Record: distribution and release, kernel; the session (`echo $XDG_SESSION_TYPE`) and the desktop; the GPUs and which drives the display; Mesa and NVIDIA driver versions; libmpv's version; Skiko's (from `reflux.log`); `vainfo` if installed; branch and commit.

**Renderer and decoding**

- [ ] **R1 Renderer selection.** Play a film; `reflux.log` has `Video renderer: linux-mpv-gl-integrated (…, vo=libmpv, hwdec=…)`. If it says `linux-x11-shape-fallback (reason)`, the reason is the finding (Skiko not on OpenGL, a reflection step failed, an untested Skiko).
- [ ] **R2 Which GPU draws.** The renderer line and the decoding report name the GPU and `hwdec`: `vaapi-copy` is expected on Mesa, `auto-safe` on NVIDIA's own OpenGL. NVIDIA's OpenGL drawing Reflux's window has never been verified.
- [ ] **R3 Hardware decoding, and on which GPU.** The decoding report after 120 frames: `hwdec-current` not `no` for H.264, HEVC, HEVC 10-bit, AV1 and VP9 at 1080p and 4K where the GPU supports them; dropped frames 0; A/V sync near 0. **The decoding GPU should be the drawing GPU.** On a hybrid laptop whose discrete GPU owns the first render node this part is **expected to fail until B-104 is fixed** (Part 2, "Hybrid graphics"): record it as B-104, not as a pass and not as a new issue. The per-file reports come from `IntegratedPlayerPerfTest`, a window test run only when asked (`REFLUX_PERF_MEDIA` = a folder of films, `REFLUX_PERF_OUT` = a report file; `REFLUX_NATIVE_VIDEO_WINDOW=1` measures the fallback).
- [ ] **R4 Software decoding.** `REFLUX_RENDER_HWDEC=no`: the picture still plays, CPU higher, nothing fails.
- [ ] **R5 The fallback renderer.** `REFLUX_NATIVE_VIDEO_WINDOW=1`: the log names `linux-x11-shape-fallback`; one window; the controls over black bands; the docked video's corners square (B-100, known).
- [ ] **R6 Start-up order.** The render context is made **before** the file opens (log order); no sound without picture.

**The player**

- [ ] **P1 Fullscreen** and back; the picture upright (the test card's timecode at top left), the aspect right (pillarbox and letterbox), BT.709 limited range looking right.
- [ ] **P2 Controls over the picture:** gradient, title, timeline, speed, audio and subtitle menus, all over the real video, in one window.
- [ ] **P3 Resize** while playing: the picture and controls follow in the same frame.
- [ ] **P4 Mini-player:** dock, the video's corners rounded at the dock's radius with no video outside the curve, back to the full player; on the fallback, square (B-100).
- [ ] **P5 Context replacement** (B-099): `IntegratedContextReplacementTest` on the real session (it skips on the fallback) passes, and the picture comes back.
- [ ] **P6 Disposal and shutdown:** close the player and play again (`IntegratedPlayerLifecycleTest`, a window test run only when `REFLUX_LIFECYCLE_OUT` names a report file: 8 rounds by default, `REFLUX_LIFECYCLE_ROUNDS` to change it; every render context made is released); close the window mid-playback (the position is kept); Reflux's process ends (`pgrep -f Reflux` empty). A process left without a window is B-101: take `kill -QUIT <pid>` with output to a file before ending it.
- [ ] **P7 Sound** through the session's default output; a device change while playing (by hand).
- [ ] **P8 Formats:** MKV, MP4, WebM; H.264, HEVC, HEVC 10-bit, AV1, VP9; AAC, AC-3, E-AC-3, Opus; SRT, ASS and PGS subtitles; one file over a network share and one from Jellyfin. HDR: record "not tested" unless an HDR display and source exist.
- [ ] **P9 Display:** a second monitor, and a fractional scale if available (`REFLUX_SCALE` overrides Reflux's own; B-063).

**Desktop integration** (needs the packaged archive: `./gradlew :desktop:app:packageDesktopArchive`, a local test build, never published)

- [ ] **D1 Window identity:** `xprop WM_CLASS` on Reflux's window says `reflux`.
- [ ] **D2 Desktop entry** (B-102): the extracted archive's first start writes `~/.local/share/applications/reflux.desktop` (valid by `desktop-file-validate`) and `Reflux.desktop` beside `bin/`; the dock or launcher shows Reflux's logo and groups the window; a removed entry stays removed on the next start; `bin/install-desktop-entry --remove` takes it away.
- [ ] **D3 Launching:** Reflux comes forward when launched; a second launch opens a second Reflux (B-103, known; the owner's decision).
- [ ] **D4 Keyring:** sign in to a server, restart, still signed in.

**Automated**

- [ ] **A1 Unit tests as hosted CI runs them:** `FONTCONFIG_FILE=$PWD/tools/ci/fonts.conf REFLUX_REQUIRE_ENGINE_TESTS=true ./gradlew check :tools:cli:installDist -Preflux.android=false --continue`, with no display and sound unreachable. Without CI's font configuration two phone-layout tests fail (B-089).
- [ ] **A2 Window tests on a CI-identical virtual display** (below): expected as hosted CI (every playback takes the fallback there).
- [ ] **A3 Window tests on the real desktop** (`tools/ci/desktop-window-tests.sh --no-daemon --rerun` in the session, windows visible): the real-desktop baseline for that machine.

### The CI-identical virtual display (A2)

Hosted CI runs the window tests on **Xvfb `:99`, 1600 × 1000 × 24, with openbox and xcompmgr**; Skiko draws in software there, so every playback takes the fallback. Start the test JVM from a **clean environment**, not from a desktop session's (`env -i`, with only `HOME`, `PATH`, `DISPLAY=:99` and what Gradle needs): desktop variables change what the JDK does (Part 2, GNOME).

```sh
Xvfb :99 -screen 0 1600x1000x24 -nolisten tcp &
DISPLAY=:99 openbox &
DISPLAY=:99 xcompmgr &
export DISPLAY=:99 PULSE_SERVER=unix:/nonexistent PIPEWIRE_REMOTE=/nonexistent   # no sound device: the films carry test tones
unset WAYLAND_DISPLAY
bash tools/ci/desktop-window-tests.sh --rerun
```

`tools/ci/desktop-window-tests.sh` starts the three itself when `DISPLAY` is empty, and stubs `zenity`/`kdialog`; never run the window tests through Gradle directly (`FirstRunTest` would open a real folder dialog). Run the **whole suite**, as CI does: B-092 appeared only after other playback tests had run in the same JVM. `tools/ci/linux-check.sh` runs the whole Linux CI job in a container.

## Part 2: what depends on the environment

**Packages.** The list hosted CI installs is `tools/ci/linux-packages.txt` (Ubuntu 24.04); the keyring check needs `libsecret-tools`, the desktop-entry check `desktop-file-utils`, R3's GPU check `vainfo` and, on Intel, its VA-API driver `intel-media-va-driver`. On Ubuntu, from Ubuntu's own archive (no PPA), the CI list plus those tools and a JDK:

```sh
sudo apt-get install --no-install-recommends openjdk-21-jdk libmpv2 ffmpeg xvfb openbox xcompmgr x11-utils \
  libgl1-mesa-dri libglx-mesa0 libegl1 fonts-dejavu-core fonts-liberation libsecret-tools desktop-file-utils vainfo \
  intel-media-va-driver git
```

Other distributions: their libmpv package (`mpv-libs` on Fedora, `mpv` on Arch). The toolchain (JDK 21, the Gradle wrapper, Kotlin, Compose, Skiko) comes from the repository; Skiko's version is pinned by `LinuxGlVideoTest`.

**libmpv.** Verified: 0.42.0 (CachyOS) and 0.41.0 (Ubuntu 26.04.1; the render API, options and properties Reflux uses were checked against it). Older versions are unverified for the integrated renderer. A libmpv built without GLX (Ubuntu's 0.41) refuses `gpu-context=x11`; Reflux keeps the contexts a build accepts (B-058), and the integrated renderer does not use them.

**GNOME (Wayland, Reflux through XWayland).**

- Covered windows are throttled (Rules, above): keep a screen free for the run.
- `XDG_CURRENT_DESKTOP` naming GNOME makes the JDK use the GTK look-and-feel; on Xvfb that makes `FirstRunTest` fail (the Ubuntu report, Finding 1). Run A2 from a clean environment.
- mutter rounds window sizes to even pixels, so `PlayerChromeResizeTest`'s exact odd height fails on the real session (Finding 3, a test assumption).
- `xprop` works on XWayland windows (D1). A2 with GNOME's variables inherited once left one playback per suite in `LOADING` (Finding 2, unexplained, Xvfb only).

**Tiling desktops (Hyprland).** Window rules, docks and `force_zero_scaling` are the desktop's own; five or six window tests failed under tiling on CachyOS (VERIFICATION.md, "CachyOS final audit"). Compare any such failure with the released player before calling it a regression.

**Hybrid graphics (an integrated and a discrete GPU).**

- R2/R3: find which render node belongs to which GPU (`/sys/class/drm/renderD*/device/vendor`: `0x8086` Intel, `0x1002` AMD, `0x10de` NVIDIA) and check that decoding happens on the GPU that draws (`hwdec-current`, `nvidia-smi`). Where the discrete GPU owns `renderD128`, the integrated picture decodes on it with `nvdec-copy` instead of `vaapi-copy` on the drawing GPU (**B-104**, open), waking it for every playback.
- `IntegratedPlayerPerfTest` reads the Intel GPU's idle residency from `/sys/class/drm/card1/...`; where Intel is not `card1` (the Ubuntu laptop: `card2`) that figure is not Intel's. A test-only measurement.
- An external monitor wired to the discrete GPU keeps it active, which hides power differences.

## Where the checks stand

The last run was the Ubuntu validation (2026-11-26, one laptop: GNOME 50.1, Intel Arrow Lake and RTX 5060 Laptop, libmpv 0.41.0; the [report](UBUNTU-VALIDATION-REPORT.md), section 6). Passing there is not passing everywhere. **Open:** R3's decoding GPU on that laptop (B-104). **Not run in that validation:** R4 (software decoding), P7's device change while playing, P8's network share and Jellyfin files and HDR, D4 (keyring). **Not looked at** on GNOME: P4's rounded corners and D2's dock icon (both checked by eye only on CachyOS). The known issues B-100, B-101 and B-103 stand, as the tracker records them.

**Not known for any machine yet:** NVIDIA's OpenGL drawing Reflux, AMD, KDE, and Wayland-native drawing (Reflux runs through XWayland; it has no Wayland backend).
