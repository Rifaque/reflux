# Reflux brand

The identity of Reflux: one mark, one wordmark, one lockup, and the rules for using them. Decision and reasoning: docs/DECISIONS.md, 2026-11-08, "The Reflux mark".

## The mark: the Reflux Loop

One continuous line that flows in, rises, turns back over itself, and flows on, higher than it came. It is the product's name drawn: *re-flux*, a flow that returns and continues. Where the turn passes over the first pass, the first pass is cut, so the line reads as two layers, one over the other: depth from layers, not decoration, as the interface itself has it.

It says what Reflux does without a picture of a film or a play button: media that comes from many places and moves on as one stream, continuity across devices, and returning to where you were.

| File | What |
| --- | --- |
| `reflux-mark.svg` | The mark, in a 100-unit square, `currentColor` |
| `reflux-wordmark.svg` | "reflux" in Manrope at weight 600, outlined, tracked −1.5% with 0.06 em after the f |
| `reflux-lockup.svg` | Mark and wordmark side by side |
| `store/` | Google Play and Amazon Appstore listing artwork |
| `exploration/` | The rounds of concepts that led here, as rendered |

All three are filled polygons with the even-odd rule (absolute `M`/`L`/`Z` only), so every tool reads them the same way. Built by `tools/brand/make-brand-geometry.py` from the stroke definition in that script (four cubic Béziers, 11 units wide, round ends; the cut 0.42 of the stroke on each side of the returning pass, within 2.4 strokes of the crossing) and Manrope's variable TTF (google/fonts, `ofl/manrope/Manrope[wght].ttf`, SHA-256 `3ae11c49…6ae6`, OFL-1.1: `Manrope-OFL.txt`). Every platform's file is rendered from these by `tools/brand/make-brand-assets.py`; nothing is drawn twice by hand.

## Colour

The mark has no colour of its own, as the interface has none (DESIGN.md: let the media be colorful; let the interface be calm).

| Use | Mark | Ground |
| --- | --- | --- |
| App icon, banners, dark pages | `#F5F5F6` | Graphite, lit from the top left: `#2C2C33` to `#0D0D10` |
| Light pages and print | `#101012` | White or near white |
| Themed and monochrome icons | Whatever the system tints it | The system's |

Never: gradients or colours inside the mark, outlines, glows, shadows under it, other colours for the cut.

## Clear space and size

- **Clear space:** at least the width of the line (11 % of the mark's square) on every side; in the lockup, the same around the whole lockup.
- **Smallest:** the mark 16 px (favicons) and 24 dp in an interface; the lockup 80 px wide on screen. Below that, use the mark alone.
- **In an app icon** the mark is 66 % of the tile's side, centred; in an Android adaptive icon, 56 dp wide in the 108 dp layer, inside the 66 dp safe zone.

## Don't

Rotate, mirror, stretch, or redraw the mark; close the cut; set the wordmark in another typeface or in capitals; put the mark on busy artwork without a scrim; place it in a circle or a frame other than the app tile; add a play triangle or any media symbol to it.

## Where it is used

| Place | File (generated) |
| --- | --- |
| Android launcher (phones, tablets, Google TV, Fire TV) | `android/app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` on `ic_launcher_background.xml` |
| Android TV and Fire TV launcher banner | `android/app/src/main/res/drawable-xhdpi/tv_banner.png` |
| Windows (`.ico`) and Linux (`.png`) | `desktop/app/packaging/reflux.ico`, `reflux.png` |
| The desktop window and taskbar | `desktop/app/src/main/resources/reflux-mark.path`, drawn by `Main.kt` |
| Website | `website/public/favicon.svg`, `favicon-32.png`, `apple-touch-icon.png`, `icon-192.png`, `icon-512.png`, `og-image.png`, `mark.svg`, `lockup.svg` |
| Google Play | `store/play-icon-512.png`, `store/play-feature-graphic-1024x500.png`, `store/play-tv-banner-1280x720.png` |
| Amazon Appstore (Fire TV) | `store/amazon-icon-512.png`, `store/amazon-icon-114.png`, `store/amazon-firetv-icon-1280x720.png`, `store/amazon-firetv-background-1920x1080.png` |

## RSX Develops, the publisher

Reflux is the product; RSX Develops makes and publishes it. RSX Develops' own wordmark (the owner's existing artwork, `publisher/`, copied unchanged from the RSX Develops brand assets with their README) appears only where the maker is named: the website's footer and Terms page ("Made by", linking to rsxdevelops.dev), and the app's About page beneath Reflux's own name. Its rules are its README's: the dark-ground wordmark (light lettering) on Reflux's dark surfaces, never stretched or filtered, never derived into icons. It is never placed beside the Reflux mark as a co-brand, in the app icon, or in store artwork.

