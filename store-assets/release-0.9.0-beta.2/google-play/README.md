# Google Play — Reflux 0.9.0-beta.2

**Status: generated and validated, not uploaded.** Nothing here has been uploaded to the Play Console; uploading changes a listing that testers see, so it is the owner's action (or an agent's on the owner's explicit request), and only once Beta 2 is approved (docs/release/BETA.md, "The Beta 2 gate").

App: `dev.rsxdevelops.reflux` (Play Console app ID in `release/google-play.md`). Sources: emulator captures, 2026-11-21 (`../capture-plan.md`); provenance in `../source-screenshots/captures.json`.

## Asset sets

### Phone screenshots — `phone/` (1080 × 1920, PNG, no alpha)

| Order | File | Feature shown | Headline | Source capture | Alt text (≤ 140 chars) |
| --- | --- | --- | --- | --- | --- |
| 1 | 01.png | Home, featured title, Continue Watching | Your media, played well. | phone-home.png (auto, emulator Reflux_Phone) | Reflux on a phone: Home with Coffee Run to resume and a Continue Watching row. |
| 2 | 02.png | A film's page with its version | Know it before you press Play. | phone-details.png | Sintel's page in Reflux: 4K HDR10, HEVC, Dolby Digital, from a home server, with Play. |
| 3 | 03.png | Quick View | A closer look, one long press away. | phone-quickview.png | A quick view over the Movies grid: Sintel's details with Play, Favorite, Download and Details. |
| 4 | 04.png | The player | The picture comes first. | phone-player-landscape.png | Reflux's player on a phone held sideways, playing Elephants Dream with simple controls. |
| 5 | 05.png | The mini-player | Keep browsing while it plays. | phone-miniplayer.png | Home in Reflux with Elephants Dream still playing in the mini-player above the navigation. |
| 6 | 06.png | Search as you type | Your library answers as you type. | phone-search.png | Reflux search after typing one letter: Sintel, Sprite Fright and Tears of Steel. |

### 7-inch tablet screenshots — `tablet-7-inch/` (1920 × 1080)

| Order | File | Headline | Source | Alt text |
| --- | --- | --- | --- | --- |
| 1 | 01.png | Your media, played well. | tablet7-home.png (emulator Reflux_Tablet7) | Reflux on a 7-inch tablet: Home with a navigation rail and Coffee Run to resume. |
| 2 | 02.png | Pick up exactly where you stopped. | tablet7-details.png | Cosmos Laundromat's page with Resume, From the start and 5 minutes left. |
| 3 | 03.png | A closer look, in place. | tablet7-quickview.png | A quick view of Elephants Dream over the Movies grid on a tablet. |
| 4 | 04.png | The picture comes first. | tablet7-player.png | Reflux's player on a tablet playing Elephants Dream. |
| 5 | 05.png | Your collection at a glance. | tablet7-movies.png | The Movies grid on a 7-inch tablet, with progress on films you started. |

### 10-inch tablet screenshots — `tablet-10-inch/` (2560 × 1440)

| Order | File | Headline | Source | Alt text |
| --- | --- | --- | --- | --- |
| 1 | 01.png | Your media, played well. | tablet10-home.png (emulator Reflux_Tablet, Pixel Tablet profile) | Reflux on a 10-inch tablet: a sidebar and Home with Coffee Run to resume. |
| 2 | 02.png | Pick up exactly where you stopped. | tablet10-details.png | Sprite Fright's page with Resume, From the start and 7 minutes left. |
| 3 | 03.png | A closer look without leaving the grid. | tablet10-quickview.png | A quick view of Sintel over the Movies grid on a 10-inch tablet. |
| 4 | 04.png | Your whole collection at a glance. | tablet10-movies.png | The Movies grid on a 10-inch tablet with the sidebar. |
| 5 | 05.png | Made for the big screen in your hands. | tablet10-player.png | Reflux's player on a large tablet playing Elephants Dream. |

### Android TV screenshots — `tv/` (1920 × 1080)

| Order | File | Headline | Source | Alt text |
| --- | --- | --- | --- | --- |
| 1 | 01.png | Your media, played well. | tv-home.png (emulator Reflux_TV, Google TV image) | Reflux on a TV: Home with Resume focused for the remote. |
| 2 | 02.png | Press Menu for a quick look. | tv-quickview.png | A quick view of Sintel on a TV, opened with the remote's Menu button. |
| 3 | 03.png | A keyboard made for the remote. | tv-keyboard.png | Reflux's on-screen keyboard on a TV, with the title Sintel suggested. |
| 4 | 04.png | Everything is one press of Left away. | tv-movies-rail.png | The TV navigation rail open over Movies. |
| 5 | 05.png | Read it, then press Play. | tv-details.png | Elephants Dream's page on a TV with Play focused. |
| 6 | 06.png | (none: the unaltered capture) | tv-player.png | Reflux's player on a TV playing Elephants Dream. |

### Feature graphic — `feature-graphic/01.png` (1024 × 500) and TV banner — `tv-banner/01.png` (1280 × 720)

The feature graphic: the lockup and "Your media, played well." beside a real crop of the TV Home's featured title. The TV banner is the brand's own (`design/brand/store/play-tv-banner-1280x720.png`, from `tools/brand/make-brand-assets.py`), saved as opaque RGB. The 512 × 512 icon is unchanged (`design/brand/store/play-icon-512.png`).

## Where to upload

Play Console → Reflux → Grow → Store presence → Main store listing → Graphics (phone, 7-inch, 10-inch, TV screenshots; feature graphic; TV banner). Android TV screenshots and banner appear once the TV form factor is added (Advanced settings → Form factors).

## Upload sequence

Remove Beta 1's screenshots in each set, upload 01 → last in order, set each image's alt text from the tables, save as a draft; send the listing change for review only with the Beta 2 release, after the owner's approval.

## Verify after upload

Order matches the files; the TV set shows 6; no image is cropped in the preview; alt text present.

## Known limitations

- Captures come from emulators (phone, both tablets, Google TV), not physical devices; the interface is the same build.
- Trickplay scene previews are not shown (the demo server has none).
- Specs read 2026-11-21 from support.google.com/googleplay/android-developer/answer/9866151; Play does not publish TV screenshot dimensions (1920 × 1080 is used).
