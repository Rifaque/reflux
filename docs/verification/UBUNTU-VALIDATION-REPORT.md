# Ubuntu validation report (2026-11-26)

**What this is.** The first validation of Reflux on the owner's laptop after it moved from CachyOS to Ubuntu: the environment as measured, the compatibility research, the automated baseline, and the real-desktop playback checks of the Linux player. It follows the checklist in `docs/history/linux/UBUNTU-TRANSITION.md` (on the `beta-3` branch; written before the install) and the knowledge in [CACHYOS-FINAL-HANDOVER.md](../history/linux/CACHYOS-FINAL-HANDOVER.md). Results are also recorded in [VERIFICATION.md](VERIFICATION.md) ("Ubuntu validation"); issues in [BETA-BUG-TRACKER.md](../BETA-BUG-TRACKER.md).

**Kind of environment.** Unless a line says otherwise, everything here ran on the **physical laptop** (Ubuntu installed on its own drive). The window tests marked "Xvfb" ran on a **virtual X display on that physical laptop**, which is CI's environment, not a desktop; they are never counted as real-desktop results.

**Code tested.** Branch `cachyos-final-audit`, commit `5de9fae` (code identical to `406382b`, whose hosted CI passed). `main` (`1d65dcf`, Beta 2 as released) is the baseline for calling anything a regression.

## 1. Environment (measured 2026-11-26)

| Item | Value |
| --- | --- |
| Distribution | Ubuntu **26.04.1 LTS** (resolute) |
| Kernel | `7.0.0-44-generic` (PREEMPT_DYNAMIC), x86_64 |
| Desktop | GNOME Shell **50.1** on **Wayland** (GDM); XWayland running. Mutter experimental features: `scale-monitor-framebuffer`, **`xwayland-native-scaling`** |
| Displays | eDP-1 2560 × 1600 at 165 Hz, scale 1.333 (Intel). HDMI-1 2560 × 1440 at 144 Hz, scale 1.25, primary, **wired to the NVIDIA GPU**. With native XWayland scaling X11 clients see 3840 × 2400 and 4096 × 2304 (scale 2 in X11 pixels) |
| CPU, memory | Intel Core Ultra 7 255HX (20 threads), 30 GiB |
| Integrated GPU | Intel Arrow Lake-S `8086:7d67`, kernel driver `i915`, `/dev/dri/card2`, `renderD129` |
| Discrete GPU | NVIDIA GeForce RTX 5060 Laptop `10de:2d59`, `nvidia-driver-595-open` **595.99.02**, `card1`, `renderD128`; PRIME profile `on-demand`; runtime power management active |
| Mesa | **26.0.8-1ubuntu0.3** (`libgl1-mesa-dri`, `libglx-mesa0`, `libegl-mesa0`, `mesa-libgallium`) |
| GLX on XWayland (default) | **Mesa Intel(R) Graphics (ARL)**, OpenGL 4.6 core, direct, accelerated (`glxinfo -B`) |
| GLX with PRIME offload | NVIDIA GeForce RTX 5060 Laptop GPU, OpenGL 4.6.0 NVIDIA 595.99.02 (only with `__NV_PRIME_RENDER_OFFLOAD=1 __GLX_VENDOR_LIBRARY_NAME=nvidia`) |
| EGL | X11 and Wayland platforms: Mesa (Intel). GBM and surfaceless platforms: NVIDIA |
| VA-API | libva 2.23.0; **Intel iHD 26.1.2** (`intel-media-va-driver`, the free build) on `renderD129`: VLD (decode) for H.264 High, HEVC Main and Main10, VP9 profiles 0 and 2, AV1 profile 0. NVIDIA: no VA-API driver (expected; NVIDIA decodes through NVDEC: `libnvcuvid.so.595.99.02` present) |
| libmpv | **`libmpv2` 0.41.0-2ubuntu4** (`/usr/lib/x86_64-linux-gnu/libmpv.so.2` → `libmpv.so.2.5.0`), client API **2.5**; libplacebo 7.360.0; FFmpeg 8.0.1-3ubuntu2. mpv's hwdec backends: vulkan, nvdec, vaapi, vdpau (and their `-copy` forms) |
| Sound | PipeWire 1.6.2, pipewire-pulse, WirePlumber 0.5.13. Sinks: a USB podcast microphone's headphone output (default), the laptop's ACE analogue output, NVIDIA HDMI |
| Keyring | gnome-keyring 50.0 owns `org.freedesktop.secrets`; `secret-tool` (libsecret-tools 0.21.7) works |
| JDK | **OpenJDK 21.0.13.1+2** (`openjdk-21-jdk` 21.0.13.1+2-1~26.04.1, Ubuntu); `JAVA_HOME` not set by the system, set per command to `/usr/lib/jvm/java-21-openjdk-amd64`. The full JDK, not `-headless`: the headless one has no `libawt_xawt.so`, which Reflux and the window tests need |
| Gradle, Kotlin, Compose, Skiko, JNA | The wrapper's **9.7.1**; 2.4.21; 1.12.1; 0.150.1 (from Compose; pinned by `SkikoGl.TESTED_VERSION`); 5.19.1 (`gradle/libs.versions.toml`) |
| Virtual display | Xvfb 21.1.22-1ubuntu1.3, Openbox 3.6.1, xcompmgr 1.1.8, x11-utils, wmctrl 1.07 |
| Fonts | fonts-dejavu-core 2.37, fonts-liberation 2.1.5 (used through `tools/ci/fonts.conf`) |

**Packages installed for this work** (by the owner, from Ubuntu's own archive, no PPA or third-party repository, `--no-install-recommends`): `openjdk-21-jdk libmpv2 ffmpeg intel-media-va-driver xvfb openbox xcompmgr libsecret-tools vainfo mesa-utils mpv wmctrl`. The rest of CI's list (`tools/ci/linux-packages.txt`) was already installed. `mpv`, `vainfo`, `mesa-utils` and `wmctrl` are diagnostics only. Nothing about drivers, the kernel, Secure Boot or the display configuration was changed.

**Before the install** the machine had no JDK, no libmpv, no VA-API driver for the Intel GPU (Mesa ships none for Intel; `mesa-va-drivers` no longer exists on 26.04, its drivers are inside `mesa-libgallium`), no Reflux data folder and no Gradle cache.

### Differences from the CachyOS machine that matter

| | CachyOS (handover) | Ubuntu (measured) | Consequence |
| --- | --- | --- | --- |
| libmpv | 0.42.0 | **0.41.0** | Researched and checked in section 2: compatible |
| Mesa | 26.3.1 | 26.0.8 | Same GPU family support (ARL is in both); measured in section 4 |
| Compositor | Hyprland 0.57.2, `force_zero_scaling` | GNOME 50.1, `xwayland-native-scaling` | X11 windows are in scale-2 pixels here, not physical; Reflux's own scale (B-063) is checked in section 4 |
| External monitor | HDMI, scale 1.25 | HDMI on the **NVIDIA** GPU | Whether a window there changes the renderer or GPU: section 4 |
| Intel `card` node | `card1` | **`card2`** (NVIDIA is `card1`) | `IntegratedPlayerPerfTest` reads `/sys/class/drm/card1/gt/gt0/rc6_residency_ms`: on this machine that figure is not the Intel GPU's (a test-only measurement; nothing in the product reads it) |

## 2. Compatibility research

| Component | Expected | Actual | Compatibility | Evidence |
| --- | --- | --- | --- | --- |
| JDK | 21 | OpenJDK 21.0.13.1+2 (Ubuntu) | Confirmed | `jvmToolchain(21)`; the check below built and ran with it |
| Gradle | 9.7.1 | the wrapper, 9.7.1 | Confirmed | `gradle-wrapper.properties`; the check below |
| libmpv | 0.42 (where written) | 0.41.0, client API 2.5 | **Confirmed** | Symbols, options and properties below; the engine tests and the players in sections 3 and 4 |
| Skiko | 0.150.1 | 0.150.1 (from the project, not the system) | Confirmed | `LinuxGlVideoTest` passed (it pins the version) |
| Compose | 1.12.1 | 1.12.1 (project) | Confirmed | The check below |
| Mesa/OpenGL | an OpenGL renderer Skiko accepts | Mesa Intel ARL, OpenGL 4.6, accelerated | Confirmed (by section 4's renderer line) | `glxinfo -B`; `reflux.log` |
| VA-API | a decode path for the Intel GPU | iHD 26.1.2 decodes H.264, HEVC 8/10-bit, VP9 0/2, AV1 | Confirmed (by section 4's decoding reports) | `vainfo`; mpv's `hwdec-current` |

**libmpv 0.41 against code written on 0.42.**

- *Functions.* All 21 `mpv_*` functions the code declares through JNA (`LibMpv.kt`, `MpvGlRender.kt`) are exported by Ubuntu's `libmpv.so.2` (`nm -D --defined-only`; none missing).
- *Render API.* The render parameters Reflux passes are API type (1), OpenGL init params (2), FBO (3), flip Y (4), X11 display (8) and block-for-target-time (12) (`MpvGlRender.kt`), all long-standing in `render.h`. mpv's [client API changes](https://github.com/mpv-player/mpv/blob/master/DOCS/client-api-changes.rst) list for 0.42.0 (client API 2.6) only the new D3D11 render backend (`MPV_RENDER_API_TYPE_D3D11`, its two parameters and `render_d3d11.h`); nothing about the OpenGL functions or parameters Reflux uses, and no 0.41 entry. Read 2026-11-26.
- *Options and properties.* Every mpv option or property name among the string literals of the mpv-facing sources (68, from `hwdec`, `vo`, `gpu-api` and `wid` to `video-dec-params/hw-pixelformat` and `audio-out-params/channel-count`) is known to 0.41 (`mpv --list-options`, `--list-properties`). The track-list sub-fields Reflux reads (`codec-profile`, `demux-w`, `demux-samplerate`, `demux-channel-count`, `demux-fps`, `hearing-impaired`) answered on a test file; `dolby-vision-profile` is unavailable on a file that is not Dolby Vision, as expected.
- *Conclusion:* Ubuntu's 0.41.0 is sufficient for this release's Linux player; no newer libmpv, PPA or bundled library is needed. The behaviour, not only the linkage, is confirmed by the engine tests (section 3) and the players (section 4).

**Intel media driver and Arrow Lake.** Arrow Lake-S has been supported by Intel's media driver since its 2024Q1 release ([Phoronix](https://Phoronix.com/news/Intel-Media-Driver-2024Q1)); VA-API on Intel GPUs comes from that driver, not from Mesa (a [mesa-dev reply](https://lists.freedesktop.org/archives/mesa-dev/2025-July/226528.html) about this very PCI ID, 0x7d67). Ubuntu's 26.1.2 is well after that, and `vainfo` on this machine is the direct evidence. The free build (`+dfsg`) was chosen; its decode entry points are all present.

**Not researched further, because nothing failed:** Skiko's GLX on XWayland under GNOME, PRIME selection rules. Section 4 measures what actually happens.

## 3. Automated baseline

| Run | Command | Environment | Result |
| --- | --- | --- | --- |
| Unit check as hosted CI | `FONTCONFIG_FILE=$PWD/tools/ci/fonts.conf REFLUX_REQUIRE_ENGINE_TESTS=true ./gradlew check :tools:cli:installDist -Preflux.android=false --continue --stacktrace`, with `DISPLAY`/`WAYLAND_DISPLAY` unset and sound unreachable (as the hosted step) | Physical laptop, no display | **PASS**: `BUILD SUCCESSFUL in 51m 24s`, exit 0; **1,639 tests, 0 failed, 49 skipped** |

Per module (tests / failed / skipped): core 393/0/0, design 43/0/0, desktop/app 101/0/24, desktop/engine 52/0/18, library 263/0/1, metadata/trakt 23/0/0, playback/mpv 62/0/1, sources/jellyfin 74/0/0, sources/local 6/0/0, sources/plex 25/0/0, sources/smb 10/0/2, sources/webdav 20/0/0, ui 567/0/3. The skips are the window tests (run separately below), the live-server tests (no server credentials on this machine), and Windows-only tests (`CredentialStorageTest`, `NumericLocaleTest`).

- **B-089:** `DetailActionsLayoutTest.withAFingerAllFourActionsStillSitOnOneLine` and `EpisodeVersionsUiTest.theVersionsAreBeneathTheEpisodeOnAPhone` **PASS** with CI's font configuration, as on hosted CI.
- **B-077:** `MpvEngineTest.theEnginesLogCarriesNoCredentials` PASS this run (it is intermittent; one pass decides nothing).
- **libmpv 0.41:** all 61 run `playback/mpv` tests pass with `REFLUX_REQUIRE_ENGINE_TESTS=true`, so none skipped for want of the engine (`MpvGlRenderTest` among them).

### Window tests on the virtual display (Xvfb `:99`, 1600 × 1000 × 24, openbox, xcompmgr, no sound device)

Every playback here took `linux-x11-shape-fallback (Skiko 0.150.1 draws with SOFTWARE_FAST, not OpenGL)`, as on hosted CI; the tests' own `TRIAL` line confirms they saw the one 1600 × 1000 screen (not the real displays). All runs `--rerun`.

**How the environment was controlled, and a lesson.** The handover's recipe (`unset WAYLAND_DISPLAY`, `DISPLAY=:99`) keeps the rest of the GNOME session's environment, and on GNOME that matters: `XDG_CURRENT_DESKTOP=ubuntu:GNOME` makes the JDK's system look-and-feel `GTKLookAndFeel` (hosted CI's environment and CachyOS's `Hyprland` give `MetalLookAndFeel`: measured with a one-line Java program on `:99`). Also, a variable given to `./gradlew` does not reliably reach the test JVM while a Gradle daemon started in another environment is running (an `env -u XDG_CURRENT_DESKTOP` run still behaved as GNOME). The controlled runs therefore stop every daemon first and start Gradle from `env -i` with only `HOME`, `USER`, `PATH`, `LANG=C.UTF-8`, `JAVA_HOME`, `DISPLAY=:99`, `PULSE_SERVER=unix:/nonexistent`, `PIPEWIRE_REMOTE=/nonexistent`, `REFLUX_REQUIRE_ENGINE_TESTS=true` (plus the one variable under test), with `--no-daemon`, and record the test JVM's real environment from `/proc/<pid>/environ` during the run.

| Run | Environment of the test JVM | Result |
| --- | --- | --- |
| 1 | the handover's recipe on GNOME (GNOME variables inherited) | **FAIL**: 101 tests, 2 failed, 14 skipped (`FirstRunTest`; `MiniPlayerWindowTest` stalled) |
| 2 (the same, repeated) | the same (daemon environment read: `DISPLAY=:99`, `XDG_CURRENT_DESKTOP=ubuntu:GNOME`, no Wayland, no sound) | **FAIL**: 101, 2 failed, 14 skipped (`FirstRunTest`; `OfflineWindowTest` stalled; `MiniPlayerWindowTest` passed) |
| 3 | verified CI-like (`env -i`, no desktop variables) | **PASS**: `BUILD SUCCESSFUL`, exit 0, 0 failed (as hosted CI on `406382b` and CachyOS's Xvfb runs: 101 tests, 14 skipped, 0 failed) |

**Finding 1: `FirstRunTest` fails whenever `XDG_CURRENT_DESKTOP` names GNOME (deterministic; test environment, not a product defect, not a regression).**

- *Symptom:* `choosingAFolderBuildsTheLibraryAndTheNextLaunchRemembersIt` times out at `FirstRunTest.kt:64` ("Timed out waiting for \"Recently Added\" to be shown"); the screen still shows "Point Reflux at your media", with no notice.
- *Experiment (one variable):* `FirstRunTest` alone, verified environments: CI-like **PASS 2 of 2** (7.3 s, 7.4 s); the same plus only `XDG_CURRENT_DESKTOP=ubuntu:GNOME` **FAIL 2 of 2** (and 2 of 2 more in the full suites, and 2 more with the session's whole environment).
- *Mechanism (from the code, partly):* with the desktop helpers stubbed (`zenity`, `kdialog` exit 127, as the script does), `FolderPicker.toolkit()` shows Swing's `JFileChooser` after `UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())`, which is GTK's look-and-feel when `XDG_CURRENT_DESKTOP` names GNOME. The test answers the chooser in process (`NativeDialogs`: `selectedFile = folder; approveSelection()`); on Xvfb with the GTK look-and-feel the dialog closes but no folder comes back, so Reflux treats it as a cancel. **On the real GNOME session (the same GTK look-and-feel) the same test passed** (section 4), so it takes GTK's look-and-feel *on a bare X server without the session's GTK settings*; which line of GTK's chooser UI drops the selection there was not traced.
- *Baseline:* `FolderPicker.kt`, `FirstRunTest.kt`, `NativeDialogs.kt` and `tools/ci/desktop-window-tests.sh` are **identical on `main` (`1d65dcf`)**; the `setLookAndFeel` call dates from `71d2c39` (vertical slice 0.2). Pre-existing, and it never showed because neither hosted CI nor CachyOS (Hyprland) names GNOME.
- *Users:* on GNOME Reflux uses `zenity` first (installed on Ubuntu 26.04 desktop); the Swing chooser is only the fallback where no helper works, and there a person picks with the GTK chooser's own controls, which this test does not exercise. Not a playback issue; no product change proposed.
- *Classification:* test-harness/environment interaction, pre-existing. Confidence: high for the trigger (single-variable experiment, 6 of 6 and 2 of 2), medium for the exact place in GTK's chooser UI. Recorded so the window suite on a GNOME machine is run with the CI-like environment (or with `XDG_CURRENT_DESKTOP` unset).

**Finding 2: one playback per GNOME-environment full suite never left `LOADING` (intermittent; cause unknown).**

- *Symptom:* run 1 `MiniPlayerWindowTest` ("Timed out waiting for playback to start"), run 2 `OfflineWindowTest` ("Timed out waiting for playback from the copy"); both `PlayerState(status=LOADING, positionMs=0, durationMs=null, …, videoDecoder=null)` with every track already known (the file was opened and demuxed; no decoder was ever reported). The same test passed in the other run. The other four or five playbacks of each suite played.
- *Where it was and was not seen:* GNOME-environment full suites **2 of 2** (one playback each, always after `FirstRunTest`); verified CI-like full suite **0 of 1**; reduced runs (`MiniPlayerWindowTest`, `OfflineWindowTest`, `SilentSourceTest` ×2, with the GNOME variable, three times with `FirstRunTest` first and three without) **0 of 24 playbacks**.
- *Hypotheses examined:* (H1) an audio output hanging while sound is unreachable; (H2) hardware decoder start-up colliding with the start-up decoding probe's (CUDA and VA-API) in the same process; (H4) the GTK look-and-feel installed by Finding 1 in the shared JVM.
- *Experiments:* the `mpv` command (same libmpv) with Reflux's fallback options (`hwdec=auto-safe`, `vo=gpu-next,gpu,xv,x11`, `gpu-context=x11egl,x11vk`, `audio-fallback-to-null=yes`) on `:99` with sound unreachable: **40 of 40 started** (~1.6 s each; audio output `null`, decoding `nvdec-copy`): no audio-output hang (against H1). Two libmpv handles in one process (a Python `ctypes` harness: the playback with those options, and a probe with `MpvDecodeProbe`'s options over the 16 bundled samples at offsets 0–1.5 s): **0 stalls in 30**, and 0 in 30 with the playback alone (513–712 ms to start in both) (against H2 as a libmpv-level cause). The reduced runs above (against H4 on its own).
- *Not yet known:* what in a whole suite's JVM leads to it (B-092 on CachyOS also appeared only after other playback tests in the same JVM); whether it happens on the real desktop (section 4) or on `main` (below).
- *Classification:* **open, unexplained, Xvfb fallback only so far.** Not a Beta 2 issue ID until it is established (reproduced off the test harness, or understood).

Note on `libmpv` and `gpu-context`: Ubuntu's mpv 0.41 is built without GLX (its features list `egl-x11`, no `gl-x11`), so `gpu-context=x11egl,x11,x11vk` is refused as a whole by `mpv` itself; Reflux already keeps the entries a build accepts, in order (`MpvPlayer`, B-058, found in the Ubuntu 26.04 VM), so it uses `x11egl,x11vk`. The integrated renderer does not use mpv's GPU contexts (it renders through the render API into Skiko's context).

The **`main` baseline** (`1d65dcf` in a separate worktree, the same Xvfb and the same GNOME-inherited environment as run 1): 77 tests, **1 failed, `FirstRunTest`, the identical timeout**, 7 skipped; no playback stalled in that one run (`main`'s Linux player is the older two-window design, so one clean run says nothing about Finding 2 there).

## 4. Real GNOME desktop (physical laptop, the owner's session)

Authorised by the owner for this validation (2026-11-26). The owner's logged-in GNOME 50.1 Wayland session; Reflux through XWayland (`DISPLAY=:0`), sound reachable (default output: the USB headset, 40 %), no display, driver or system setting changed. Window tests ran through `tools/ci/desktop-window-tests.sh --no-daemon --rerun` in the session's own environment (daemons stopped first). An outside observer sampled the test JVM every ~8 s: per-GPU engine time from DRM `fdinfo` (Intel reports it; NVIDIA's driver does not, so `nvidia-smi pmon` and the libraries mapped into the process stand in), the active X window, PipeWire's audio streams, and the RMS level of 2.5 s recorded from the default output's monitor (negative control: the idle output measured RMS 0.0).

**Covered windows are throttled, and that matters for every real-desktop result.** The first real-desktop runs opened their windows behind the owner's (GNOME does not raise windows launched from the background); XWayland then presents a covered window at about one frame a second. Measured: the same performance walk gave `dropped(vo) +581` of ~600 frames per file with the window covered, and **`+0`** with it visible and focused (Intel render engine 0.4–2.1 % busy covered, 29–35 % visible); the whole window suite took 12 min covered and 2 min 21 s visible. Only runs with visible windows (the owner kept one screen empty, the observer logging the active window as `reflux`) are counted below.

### 4.1 Renderer, GPU and decoding

| Check | Result | Evidence |
| --- | --- | --- |
| Renderer selection (R1) | **PASS** | Every playback logged `Video renderer: linux-mpv-gl-integrated (libmpv render API into Skiko 0.150.1's OpenGL context, vo=libmpv, hwdec=vaapi-copy,auto-copy-safe)`; "after 120 frames: … Vendor: Intel; Model: Mesa Intel(R) Graphics (ARL)"; texture 2720 × 1684 at Reflux's scale ×2.0 |
| Which GPU draws (R2) | **PASS: Intel** | Skiko's own report (above); Intel render engine 29–35 % busy in the test JVM while playing (`fdinfo`); `libGLX_mesa` mapped. NVIDIA's GLX library is also mapped (GLVND loads it), but nothing drew there |
| Which GPU decodes, integrated (R3) | **FAIL (B-104)**: hardware, but on **NVIDIA** | `hwdec-current=nvdec-copy` for every file; Intel's iHD driver never loaded in the process; NVIDIA 6.7–9.4 W idle → 11.6–14.8 W playing. Cause: libmpv's `vaapi-copy` opens `renderD128`, NVIDIA's node here (section 5) |
| Hardware decoding, fallback | **PASS** (Intel, zero-copy) | `REFLUX_NATIVE_VIDEO_WINDOW=1`: `vo=gpu-next hwdec-current=vaapi dec-format=vaapi` for H.264, HEVC 10-bit, VP9, AV1 |
| Software decoding (R4) | **NOT TESTED** on the real desktop | (`REFLUX_RENDER_HWDEC=no` not run) |
| The external monitor on NVIDIA | **PASS (no effect seen)** | GLX on XWayland is Intel's whichever monitor; the windows of these runs opened on the screen the owner kept empty; a window on the HDMI monitor specifically was not separately measured |
| Context replacement (P5, B-099) | **PASS** | `IntegratedContextReplacementTest` passed on the real session (visible run) |
| Disposal and repeated playback (P6) | **PASS** | `IntegratedPlayerLifecycleTest`, 8 rounds (play, pause, resume, seek, fullscreen and back, close): render contexts made 8, released 8, none failed; RSS after each round 585–659 MB (levels off) |

**Formats played in the integrated picture** (visible window; each sampled 20 s; stream properties from `ffprobe`, not file names):

| File | Video | Audio | Decoder | Dropped (renderer / decoder) | A/V | CPU (one core) |
| --- | --- | --- | --- | --- | --- | --- |
| MKV | H.264 High 8-bit 1920 × 1080 30p | AAC + AC-3, SRT | nvdec-copy | 0 / 0 | 0.000006 | 34 % |
| MKV | HEVC Main 10 1920 × 1080 30p | E-AC-3 5.1 | nvdec-copy | 0 / 0 | 0.000004 | 31 % |
| MKV | HEVC Main 3840 × 2160 30p | AAC | nvdec-copy | 0 / 0 | 0.000005 | 32 % |
| WebM | VP9 profile 0 1920 × 1080 30p | Opus | nvdec-copy | **23** / 0 | 0.000003 | 35 % |
| MP4 | AV1 Main 1920 × 1080 30p | AAC | nvdec-copy | 0 / 0 | 0.000005 | 31 % |
| MP4 | H.264 1920 × 804 (2.39:1) 24p | AAC | nvdec-copy | 0 / 0 | 0.000003 | 24 % |
| MP4 | H.264 1440 × 1080 (4:3) 25p | AAC | nvdec-copy | 0 / 0 | 0.000004 | 26 % |

The fallback renderer played the first six of these (all but the 4K HEVC: the walk could not find a seventh card on Home, a limit of the walk, not of playback) with 0 dropped frames, A/V ~0.000007 s, 18–27 % of one core. The VP9 file's 23 dropped frames (3.8 %) were one sample; not a finding. Not covered: HDR, PGS and ASS subtitles, multichannel PCM/DTS/TrueHD, a network share, Jellyfin (no server here).

### 4.2 Player, mini-player, fullscreen, sound

| Check | Result | Evidence |
| --- | --- | --- |
| Controls over the picture, pause, seek, volume, mute, fullscreen (P1, P2) | **PASS** | `EmbeddedPlaybackTest` (visible run) |
| Mini-player: dock, back to the full player, window resized under the dock (P4, B-092) | **PASS** | `MiniPlayerWindowTest` (visible run; also passed in the covered run) |
| Rounded clipping of the docked video | **NOT TESTED** (visually) | `MiniPlayerCornersWalkTest` uses Hyprland's `grim`/`hyprctl`; no capture of the owner's screen was taken |
| Resize while playing (P3) | **INCONCLUSIVE** | `PlayerChromeResizeTest` fails on GNOME before it resizes anything: Finding 3 below |
| Audio output (P7) | **PASS** | While Reflux played, PipeWire showed a stream `mpv` (`Stream/Output/Audio`, running) and the default output carried the test tone (e.g. RMS 2035, −24.1 dBFS for AAC; 7976, −12.3 dBFS for E-AC-3 5.1 downmixed), on the integrated and the fallback renderer; silence (RMS 0.0) between playbacks. Device changes while playing: **NOT TESTED** |
| Audio through fullscreen and the mini-player | **PASS** (as far as measured) | the observer's samples during `EmbeddedPlaybackTest`, `MiniPlayerWindowTest` and the lifecycle walk show the stream running and signal at the output; the samples are every ~8 s, not aligned with each transition |

### 4.3 Packaged archive and desktop integration (`packageDesktopArchive`, a local test build, never published)

| Check | Result | Evidence |
| --- | --- | --- |
| Window identity (D1) | **PASS** | `WM_CLASS "reflux", "reflux"`, `_NET_WM_PID` the process |
| Desktop entry (D2, B-102) | **PASS** | First start (no entry before) logged "Reflux's desktop entry: added …"; `~/.local/share/applications/reflux.desktop` with `StartupWMClass=reflux`, `Exec`/`Icon` in the extracted folder, `X-Reflux-Managed=true`; `desktop-file-validate` OK; `Reflux.desktop` written beside `bin/`. The dock icon itself was not looked at. Removed afterwards with `bin/install-desktop-entry --remove` |
| Second launch (D3, B-103) | as recorded | A second Reflux opened (two windows, two processes), as on CachyOS: the owner's decision, unchanged |
| Closing, no process left (P6, B-101) | **PASS** | Four rounds of two instances closed through the window manager (`wmctrl -c`): **8 of 8 processes exited within 2 s** |
| Keyring (D4) | **NOT TESTED** | Needs a server sign-in; the Secret Service and `secret-tool` work |

### 4.4 The window suite on the real desktop

Visible run: **101 tests, 2 failed, 13 skipped** (2 min 21 s): `FirstRunTest` (Finding 1) and `PlayerChromeResizeTest` (Finding 3); everything else passed, `IntegratedContextReplacementTest`, `MiniPlayerWindowTest`, `EmbeddedPlaybackTest`, `OfflineWindowTest` and both `SilentSourceTest` cases among them. (The covered run's four timeouts, in 12 min, are not counted: section 4's note.) `FirstRunTest` on the real desktop: 1 pass (covered run), 1 fail (visible run), so on a real GNOME session it is intermittent rather than deterministic; why was not established.

**Finding 3: `PlayerChromeResizeTest` cannot get its exact window size on GNOME (test assumption; pre-existing; established).** It asks AWT for a 1014 × 677 frame and waits for exactly that. Measured with `xwininfo` during the test: the client area requested is 1014 × 603 (the size hint), and the X window is **1014 × 604**: mutter, with `xwayland-native-scaling` (X11 at scale 2), rounded the odd height to an even one, so AWT reports 678 and the wait times out (2 of 2 visible-window runs, and with `REFLUX_SCALE=1` too, which rules out Reflux's own minimum size). The same exact-size `resize()` is on `main`. A window one pixel off is not a playback defect; whether the test should accept a compositor's rounding (as B-092 already does for the dock) is for the owner.

## 5. Findings

| ID | What | Kind | Status |
| --- | --- | --- | --- |
| [B-104](../BETA-BUG-TRACKER.md#b-104) | Integrated picture decodes on NVIDIA (`vaapi-copy` opens `renderD128`, NVIDIA's node here) | Product (power; playback works) | Open, cause established, fix proposed (Beta 3) |
| Finding 1 | `FirstRunTest` fails with GNOME's `XDG_CURRENT_DESKTOP` on Xvfb (GTK look-and-feel); intermittent on the real session | Test harness / environment, pre-existing (`main` fails identically) | Recorded here; run the suite on GNOME with the CI-like environment |
| Finding 2 | One playback per GNOME-environment Xvfb suite stayed in `LOADING` | Unknown | Open, not attributed; not seen in the CI-like suite, the reduced runs, libmpv alone, or on the real desktop |
| Finding 3 | `PlayerChromeResizeTest`'s exact 1014 × 677 vs mutter's even-pixel rounding | Test assumption, pre-existing | Recorded here; for the owner |

No production code was changed. No finding is a major playback blocker by the definition in `UBUNTU-TRANSITION.md` (no picture, no sound, a crash, a hang, or a player that cannot be closed, on the default path, for a common format).

## 6. Acceptance summary

| Area | Status |
| --- | --- |
| Environment verified; libmpv 0.41 compatible | PASS |
| Unit check (CI-equivalent) | PASS (1,639 tests, 0 failed) |
| Window suite, Xvfb, CI-like environment | PASS |
| Window suite, Xvfb, GNOME environment | FAIL (Findings 1 and 2) |
| Window suite, real GNOME | FAIL (Findings 1 and 3), playback tests PASS |
| Integrated renderer: selection, Intel drawing, frames, context replacement, disposal | PASS |
| Hardware decoding on the drawing GPU (integrated) | FAIL (B-104; decodes in hardware on NVIDIA) |
| Fallback renderer: selection, playback, zero-copy VA-API on Intel | PASS |
| Software decoding | NOT TESTED |
| Formats: H.264, HEVC 8/10-bit, 4K HEVC, VP9, AV1; MKV, MP4, WebM; AAC, AC-3, E-AC-3 5.1, Opus; letterbox and pillarbox | PASS (integrated; fallback all but 4K) |
| Pause, seek, volume, mute, fullscreen, mini-player | PASS |
| Resize while playing | INCONCLUSIVE (Finding 3) |
| Rounded mini-player clipping (visual) | NOT TESTED |
| Audio output through PipeWire | PASS; device change NOT TESTED |
| Shutdown, no process left (B-101) | PASS (8 of 8) |
| Desktop entry (B-102), window identity | PASS; dock icon not looked at |
| Keyring sign-in | NOT TESTED |

**Recommendation: READY WITH DOCUMENTED LIMITATIONS** for systematic Beta 2 testing on this Ubuntu machine. Playback works on both renderers with picture and sound; the open items are B-104 (efficiency), two pre-existing test assumptions, one unexplained Xvfb-only stall, and the checks marked NOT TESTED above.

**For whoever runs the window suite on this machine next:** start Gradle from a clean environment (`./gradlew --stop`, then `env -i … DISPLAY=:99 …`) for CI-identical Xvfb runs; on the real desktop keep the windows' screen uncovered; stop the Xvfb, openbox and xcompmgr that `desktop-window-tests.sh` leaves running; and to identify the test JVM, match `/proc/<pid>/cmdline` of `java` processes, not `pgrep -f` (which matches the shell running it).
