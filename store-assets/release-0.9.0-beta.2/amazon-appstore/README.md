# Amazon Appstore (Fire TV) — Reflux 0.9.0-beta.2

**Status: generated and validated, not uploaded.** Nothing has been uploaded to the Amazon Developer Console; that is the owner's action, after Beta 2 is approved.

**Provenance:** the five screenshots and the background are built from captures taken on a **physical Fire TV Stick 4K Plus** (`AFTMA08C15`, Fire OS 8.1.8.5, API 30, 1920 × 1080 at 320 dpi), on 2026-11-21, by adb over the network, with a side-by-side capture build (`dev.rsxdevelops.reflux.shot.debug`, the Beta 2 code, no Trakt Client ID) installed beside the stick's own Reflux installs and removed afterwards. They replace the Google TV emulator captures this set used first (those remain in `source-screenshots/tv-*.png` for the Google Play TV set). Every capture is in `captures.json` as `auto-captured`, device `AFTMA08C15`.

## Asset sets

### Fire TV screenshots — `fire-tv/` (1920 × 1080, PNG, no alpha; 3 to 10 required)

| Order | File | Headline | Source capture | Caption / alt text |
| --- | --- | --- | --- | --- |
| 1 | 01.png | Your media, played well. | firetv-home.png (Fire TV Stick 4K Plus) | Reflux on a TV: Home with Resume focused for the remote. |
| 2 | 02.png | Press Menu for a quick look. | firetv-quickview.png | A quick view of Sintel, opened with the remote's Menu button. |
| 3 | 03.png | A keyboard made for the remote. | firetv-keyboard.png | Reflux's on-screen keyboard, with the title Sintel suggested. |
| 4 | 04.png | Everything is one press of Left away. | firetv-movies-rail.png | The navigation rail open over Movies. |
| 5 | 05.png | (none: the unaltered capture) | firetv-player.png | Reflux's player playing Elephants Dream. |

### Fire TV app icon — `fire-tv-icon/01.png` (1280 × 720, PNG, opaque)

The brand's own (`design/brand/store/amazon-firetv-icon-1280x720.png`), saved as opaque RGB; the lockup sits inside the 882 × 448 safe area.

### Fire TV background — `fire-tv-background/01.png` (1920 × 1080, opaque)

New for this release: no logo or title (Amazon draws its own over it); colour from Reflux's player as captured on the Fire TV, blurred to atmosphere and weighted right within the 1214 × 830 subject area. It replaces the earlier background, which carried the lockup.

The small and large icons (114 and 512) are unchanged (`design/brand/store/amazon-icon-*.png`).

## Where to upload

developer.amazon.com → Apps & Services → My Apps → Reflux (app ID in `release/amazon-appstore.md`) → the next version's submission → Appstore Details → Images & Multimedia → Fire TV.

## Upload sequence

Replace the screenshots in order 01 → 05, the background, then the icon; save. Amazon's file inputs re-order after each upload: check each slot after uploading.

## Known limitations

- Specs read 2026-11-21 from developer.amazon.com/docs/app-submission/add-store-listing-information.html (last modified 2026-11-16).
