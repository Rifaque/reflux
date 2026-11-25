# CachyOS final handover (2026-11-25)

The owner's CachyOS laptop was the only physical Linux desktop Reflux was developed and verified on. Its drive is to be wiped for Ubuntu. This page keeps what that machine knew and what an Ubuntu session needs to carry on. Every value here was read on the machine on 2026-11-25; what could not be read is marked **unknown**. The audit's record is [planning/CACHYOS-FINAL-AUDIT-LEDGER.md](planning/CACHYOS-FINAL-AUDIT-LEDGER.md); test results are in [VERIFICATION.md](VERIFICATION.md); issues in [planning/BETA-BUG-TRACKER.md](planning/BETA-BUG-TRACKER.md).

## Where the work is

| Branch | Commit | What it is |
| --- | --- | --- |
| `main` | `1d65dcf` | Untouched; Beta 2 as released |
| `cachyos-final-audit` | the closure commit on `406382b` | The review branch: everything from the CachyOS and Windows audits. Its code is exactly `406382b`, whose hosted CI passed in full (CI 45585080925, Release 45585081047). **The Beta 3 features are still in it**: separating them needs hand edits and full test runs (the ledger, "Closure"). |
| `beta-3` | the same closure commit | Created from the audit branch so the Beta 3 work (F-P1 with B-091, B-085, B-086) is preserved on a branch of its own whatever is done to the review branch. |

Nothing of the project is kept only on this machine: no stash, no untracked work; build outputs (`build/`, `.gradle/`) and the session's scratch folder are disposable.

## The machine (as read on 2026-11-25)

| Item | Value |
| --- | --- |
| Distribution | CachyOS (rolling) |
| Kernel | 7.3.5-2-cachyos |
| Desktop | Hyprland 0.57.2 (Lua configuration), Wayland session; Reflux runs through XWayland (xorg-xwayland 24.1.15) |
| Shell (bar, dock, launcher) | Noctalia 5.6.0 (Quickshell-based; its dock shows pinned and running apps by desktop entry) |
| File manager | Dolphin (KDE) |
| Displays | eDP-1 2560 × 1600 at 165 Hz, Hyprland scale 1.6; HDMI-A-1 2560 × 1440 at 144 Hz, scale 1.25. `xwayland { force_zero_scaling = true }`, so X11 windows are in physical pixels and Reflux applies the scale itself (B-063) |
| GPUs | Intel Arrow Lake integrated graphics; NVIDIA GeForce RTX 5060 Max-Q / Mobile (hybrid; `nvidia-prime` installed) |
| Graphics stack | Mesa 26.3.1 (Intel), intel-media-driver 26.3.5, libva 2.24.1; NVIDIA 620.73.04 (open kernel modules) |
| What Reflux drew with | Skiko on **OpenGL, Intel, Mesa** ("Mesa Intel(R) Graphics (ARL)"); decoding `vaapi-copy` on the Intel GPU. Every playback logged in the session drew on the Intel GPU; whether anything was set to offload to the NVIDIA GPU was not checked. **Unknown:** how the integrated picture behaves on NVIDIA's own OpenGL |
| libmpv | mpv 0.42.0 (`/usr/lib/libmpv.so.2`); FFmpeg 9.1 |
| Sound | PipeWire 1.6.11 (tests ran with no sound device reachable, below) |
| JDK used to build | Temurin 21.0.13.1+2, a portable copy in the user's folder (not a system package); no system `java` |
| Libraries in the build | Kotlin 2.4.21, Compose Multiplatform 1.12.1, Skiko 0.150.1 (pinned by `LinuxGlVideoTest`: a new Skiko needs `SkikoGl`'s reflection checked again), JNA 5.19.1 |

## The Linux player, as it stands

- **Integrated picture** (`linux-mpv-gl-integrated`, B-084/CACHY-011): where Skiko draws with OpenGL, libmpv renders each frame into a texture in Skiko's own context and Compose draws it with the controls over it, in one window. The mini-player's video is clipped to the dock's rounded corners (`pictureClip`). A replaced OpenGL context is handled (B-099).
- **Fallback** (`linux-x11-shape-fallback`): where Skiko does not draw with OpenGL (hosted CI's Xvfb, a VM without 3D), the video is a native X window inside Reflux's window, shaped around the controls. Its docked video is square (B-100, pre-existing).
- The log names which one each playback used (`Video renderer: …` in `reflux.log`).
- **Environment variables** (all optional): `REFLUX_NATIVE_VIDEO_WINDOW=1` forces the fallback; `REFLUX_RENDER_HWDEC=<mpv hwdec>` overrides the decoding the integrated picture asks for (default `vaapi-copy,auto-copy-safe` on Mesa, `auto-safe` on NVIDIA's OpenGL); `REFLUX_SCALE=<factor>` overrides the desktop scale Reflux applies; `REFLUX_STARTUP_TRACE=1` (or `REFLUX_STARTUP_TRACE_FILE=<path>`) writes a start-up trace. Test-only: `REFLUX_CORNERS_WALK=<dir>`, `REFLUX_HYPRLAND_WALK=<dir>`, `REFLUX_LIFECYCLE_OUT=<dir>` (opt-in walks), `REFLUX_REQUIRE_ENGINE_TESTS=true` (libmpv tests fail instead of skipping without libmpv).

## Build and run on Linux

Runtime needs only the distribution's libmpv (Ubuntu: `libmpv2`); the archive carries its own Java runtime. To build and test:

1. A JDK 21 (any vendor; the Gradle wrapper fetches nothing else system-wide).
2. libmpv and FFmpeg (Ubuntu: `libmpv2`, `ffmpeg`; the window tests make sample films with `ffmpeg`).
3. `./gradlew check` (unit tests); `./gradlew :desktop:app:packageDesktopArchive` builds `desktop/app/build/release/reflux-<version>-linux-x64.tar.gz` (Linux only; it adds `--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED` for the window class).
4. **Desktop entry:** from an extracted archive, `bin/install-desktop-entry` writes `~/.local/share/applications/reflux.desktop` (`--remove` takes it away). On the review branch a packaged Reflux also writes that entry itself at start, and `Reflux.desktop` beside `bin/` (B-102); an entry someone made, or removed, is respected.

## Hyprland notes

- No Hyprland configuration is needed by Reflux; the session wrote nothing to the configuration. The window class is `reflux`.
- To keep test windows off the owner's screens, the session used **runtime-only** named rules (`hyprctl eval 'hl.window_rule({ name = "…", match = { class = "…" }, workspace = "N silent", float = true })'`, disabled afterwards with `enabled = false`), and showed an empty workspace with `hl.dsp.focus({ workspace = N })`, putting the monitors back afterwards. Hyprland's tiling resizes windows the tests ask for, so on a tiling compositor a rule with `float = true` is needed (five to six window tests fail tiled, identically on the untouched baseline: the ledger, "Testing").
- A headless Hyprland (`AQ_BACKENDS=headless`) did **not** start on this machine (`CBackend::create() failed`).

## The virtual display used for B-092 (CI-identical)

Hosted CI runs the desktop window tests on Xvfb with openbox and xcompmgr (`tools/ci/desktop-window-tests.sh`), where Skiko draws in software and every playback takes the fallback. To reproduce that on any Linux:

```sh
Xvfb :99 -screen 0 1600x1000x24 -nolisten tcp &
DISPLAY=:99 openbox &           # needs its theme and rc.xml: XDG_DATA_DIRS / XDG_CONFIG_DIRS if not installed normally
DISPLAY=:99 xcompmgr &
export DISPLAY=:99 PULSE_SERVER=unix:/nonexistent PIPEWIRE_REMOTE=/nonexistent   # no sound device
unset WAYLAND_DISPLAY
bash tools/ci/desktop-window-tests.sh --rerun
```

On Ubuntu the three come from `xvfb openbox xcompmgr`. On CachyOS they were run from the Arch packages extracted into a scratch folder (nothing installed), openbox pointed at its extracted theme and configuration. **Use `--rerun`** (or `--rerun-tasks`): an environment variable is not a Gradle input, so without it a repeated test task can be a cached result. Run the whole suite as CI does, not only the failing class: B-092's failures appeared only after other playback tests had run in the same JVM.

## Reproducing the Linux window tests on Ubuntu

- On a **real desktop** session (GNOME on Wayland runs Reflux through XWayland): `./gradlew :desktop:app:test -Preflux.windowTests=true`, through `tools/ci/desktop-window-tests.sh` so `zenity`/`kdialog` are stubbed (otherwise `FirstRunTest` opens a real folder dialog). Expect the integrated picture where Mesa or NVIDIA gives Skiko OpenGL; check `reflux.log`. `IntegratedContextReplacementTest` needs it and skips on the fallback.
- On the **virtual display** above: every playback takes the fallback, as on hosted CI.
- Keep test windows off a screen in use (the rules in CLAUDE.md: never drive the owner's desktop while it is in use) and keep sound unreachable: the sample films carry test tones.

## Known limitations carried over

- The integrated picture was verified on **one machine** (Intel, Mesa, Hyprland, XWayland). NVIDIA's OpenGL, AMD, GNOME and KDE sessions are unverified. Decoding copies back to system memory on Mesa (no zero-copy with Skiko's GLX), measured and documented in CACHY-011.
- Fallback: square docked video (B-100).
- Open from this machine: B-101 (once, a closed Reflux stayed running; not reproduced), B-103 (a second launch opens a second Reflux).

## Lost with the wipe (and does not matter)

The session's scratch folder (test media, captures beyond those committed under `docs/planning/cachyos-audit/`, logs, the extracted X packages), the portable JDK, Gradle caches, and the owner's own Reflux data and Hyprland/Noctalia configuration, none of which the repository depends on. No credential or token was ever written into the repository or the handover.
