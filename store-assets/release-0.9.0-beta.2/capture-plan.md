# Capture plan — Reflux 0.9.0-beta.2

Build: `:android:app:assembleDebug` of `open-source-gpl` (Phase 3's accepted code, `3aa5964` plus Phase 4's documentation-only changes), `dev.rsxdevelops.reflux.debug`, built with `-Preflux.trakt.clientId=` (empty) and checked to carry no Trakt Client ID. Data: a throwaway Jellyfin 10.10.7 in Docker on the development PC (localhost only; the emulators reach it as 10.0.2.2), seven Blender open films, three part-watched (`Coffee Run` 42 %, `Sprite Fright` 30 %, `Cosmos Laundromat` 55 %), connected through the app's own instrumented helper (`JellyfinExecutionOnDeviceTest#prepareTheInstalledApplication`). Status bar: LaunchFrame `prepare` (demo mode 9:41, dark theme), restored afterwards. Every capture: `capture_android.py shot` (`adb exec-out screencap -p`, native resolution), all on 2026-11-21.

| # | Target | Screen / feature | How it was reached | Device | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | phone | Home, featured title, Continue Watching | launch | emulator `Reflux_Phone` 1080 × 2400 @ 420 dpi, Android 16 | captured `phone-home.png` |
| 2 | phone | Movies grid | tap desc "Movies" | same | captured `phone-movies.png` (not used in the set) |
| 3 | phone | Sintel's page (4K HDR10 version) | tap "Sintel, 2010" | same | captured `phone-details.png` |
| 4 | phone | Quick View | long press (900 ms) on Sintel | same | captured `phone-quickview.png` |
| 5 | phone | Player, landscape, controls shown | rotation 1; Elephants Dream → Play; tap the picture | same, landscape | captured `phone-player-landscape.png` |
| 6 | phone | Mini-player over Home | Back from the player; Home | same | captured `phone-miniplayer.png` |
| 7 | phone | Search as you type ("s") | Search; type "s" | same | captured `phone-search.png` |
| 8 | tablet-10-inch | Home (sidebar layout) | launch | emulator `Reflux_Tablet` 2560 × 1600 @ 320 dpi (Pixel Tablet profile) | captured `tablet10-home.png` |
| 9–12 | tablet-10-inch | Movies, Quick View, Sprite Fright's page, player | taps and long press as above; "Got it" on the system's one-time full-screen hint | same | captured |
| 13–17 | tablet-7-inch | Home (rail layout), Movies, Quick View, Cosmos Laundromat's page, player | as above | emulator `Reflux_Tablet7` 1920 × 1200 @ 320 dpi (created for this release: 960 × 600 dp) | captured |
| 18 | tv | Home, focus on Resume | launch | emulator `Reflux_TV` (Google TV image, API 36) 1920 × 1080 @ 320 dpi | captured `tv-home.png` |
| 19 | tv | Movies with the rail opened by Left | D-pad | same | captured `tv-movies-rail.png` |
| 20 | tv | Movies, a poster focused | D-pad | same | captured `tv-movies-focus.png` (not used) |
| 21 | tv | Quick View by the remote's Menu key | `KEYCODE_MENU` on a focused poster | same | captured `tv-quickview.png` |
| 22 | tv | Elephants Dream's page | D-pad, OK | same | captured `tv-details.png` |
| 23 | tv | Player, controls shown | OK on Play; Up | same | captured `tv-player.png` |
| 24 | tv | Search with Reflux's keyboard, "si" typed, completion "Sintel" | D-pad on Reflux's keyboard | same | captured `tv-keyboard.png` |

Removed (recorded in `captures.json` history by `captures.py remove`, with reasons): two frames where a tap missed (Home shown instead), a paused portrait player frame, a frame still in the player instead of the mini-player, a "no matches" empty state, a tablet player under the system's one-time hint, and a TV details frame of the wrong film for its label.

## Fire TV session (2026-11-21, later; physical)

Device: Fire TV Stick 4K Plus `AFTMA08C15`, Fire OS 8.1.8.5, API 30, 1920 × 1080 at 320 dpi (physical panel 3840 × 2160), adb over the network. Build: `-Preflux.android.applicationId=dev.rsxdevelops.reflux.shot` (installs as `dev.rsxdevelops.reflux.shot.debug`) so the stick's own Reflux installs were untouched; connected to the same demo server over the LAN; removed afterwards. Every key was sent only while the capture build was in front (a guard checked the focused window).

| # | Screen | How | Status |
| --- | --- | --- | --- |
| 25 | Home, focus on Resume | launch | `firetv-home.png` |
| 26 | Movies with the rail opened by Left | D-pad | `firetv-movies-rail.png` (a first frame with focus on a poster was rejected) |
| 27 | Quick View by the remote's Menu key | `KEYCODE_MENU` | `firetv-quickview.png` |
| 28 | Reflux's keyboard, "si" typed, "Sintel" offered | D-pad on Reflux's keyboard | `firetv-keyboard.png` (a first frame with a stray "7" was rejected) |
| 29 | Elephants Dream's page | D-pad, OK | `firetv-details.png` (not used in the set) |
| 30 | Player with controls | OK on Play; Up | `firetv-player.png` |

## Blockers and fallbacks

- **Fire TV:** at first no Fire TV was attached and the set used Google TV emulator captures; the later session above replaced them with captures from the physical stick.
- **Trickplay previews:** not captured: the demo server has no generated previews, and the videos are still images.

## Restore

Each emulator: `capture_android.py restore` (demo mode off, rotation restored) then `stop`. The demo Jellyfin container `reflux-shoot-jf` and its volume are removed after the website captures (VERIFICATION.md, Phase 4).
