# Amazon Appstore (Fire TV) — Reflux 0.9.0-beta.2

**Status: generated and validated, not uploaded.** Nothing has been uploaded to the Amazon Developer Console; that is the owner's action, after Beta 2 is approved.

**Provenance, said plainly:** the five screenshots are compositions of captures from the **Google TV emulator** (`Reflux_TV`), not from a Fire TV device. They show Reflux's television interface, which is the same build and layout Reflux runs on Fire OS, but they were **not captured on Fire OS**, and nothing in them is Fire OS's own UI. No Fire TV device was attached for this release, and LaunchFrame has no Fire OS emulator. Before upload, the owner chooses one:

1. **Re-capture on the Fire TV Stick** (recommended): with the stick on adb and the demo server reachable, the same screens are taken with `capture_android.py shot --allow-physical` and `python -I working/build.py` plus the render command in `../README.md` produce the set again from those sources; or
2. **Accept these** as representative of the TV interface, with this note kept.

Beta 1's Amazon screenshots (in `design/brand/store/screenshots/tv-*.jpg`) were captured on the physical stick, so they keep that provenance; they show Beta 1's interface.

## Asset sets

### Fire TV screenshots — `fire-tv/` (1920 × 1080, PNG, no alpha; 3 to 10 required)

| Order | File | Headline | Source capture | Caption / alt text |
| --- | --- | --- | --- | --- |
| 1 | 01.png | Your media, played well. | tv-home.png (Google TV emulator) | Reflux on a TV: Home with Resume focused for the remote. |
| 2 | 02.png | Press Menu for a quick look. | tv-quickview.png | A quick view of Sintel, opened with the remote's Menu button. |
| 3 | 03.png | A keyboard made for the remote. | tv-keyboard.png | Reflux's on-screen keyboard, with the title Sintel suggested. |
| 4 | 04.png | Everything is one press of Left away. | tv-movies-rail.png | The navigation rail open over Movies. |
| 5 | 05.png | (none: the unaltered capture) | tv-player.png | Reflux's player playing Elephants Dream. |

### Fire TV app icon — `fire-tv-icon/01.png` (1280 × 720, PNG, opaque)

The brand's own (`design/brand/store/amazon-firetv-icon-1280x720.png`), saved as opaque RGB; the lockup sits inside the 882 × 448 safe area.

### Fire TV background — `fire-tv-background/01.png` (1920 × 1080, opaque)

New for this release: no logo or title (Amazon draws its own over it); colour from Reflux's player capture, blurred to atmosphere and weighted right within the 1214 × 830 subject area. It replaces the earlier background, which carried the lockup.

The small and large icons (114 and 512) are unchanged (`design/brand/store/amazon-icon-*.png`).

## Where to upload

developer.amazon.com → Apps & Services → My Apps → Reflux (app ID in `release/amazon-appstore.md`) → the next version's submission → Appstore Details → Images & Multimedia → Fire TV.

## Upload sequence

Replace the screenshots in order 01 → 05, the background, then the icon; save. Amazon's file inputs re-order after each upload: check each slot after uploading.

## Known limitations

- Not captured on Fire OS (above).
- Specs read 2026-11-21 from developer.amazon.com/docs/app-submission/add-store-listing-information.html (last modified 2026-11-16).
