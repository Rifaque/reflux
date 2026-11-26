# Release archive: Reflux 0.9.0-beta.2 store assets

The generated media of this release (captures, store exports, previews: 94 PNG files) are kept **outside Git** in one archive; this folder keeps what is needed to understand, check and regenerate them (decided 2026-11-21; docs/decisions/DECISIONS.md, "Beta 2 release"). Commit `85d6d26` added them to Git and they remain in its history; they were removed from the tree in the next release commit, without rewriting history.

| | |
| --- | --- |
| Archive | `Reflux-0.9.0-beta.2-store-assets.zip` (the whole release folder, 140 files) |
| Size | 86,993,534 bytes |
| SHA-256 | `f9827de18b48ef5135289771e9dd57a8b0e06b359f635a36b17474d608511cc8` |
| Where | The owner's copy at `Documents/Reflux-release-archives/` on the development PC, and a **draft** GitHub release "Reflux 0.9.0-beta.2 store assets (not the app)" visible to the repository's writers only |
| To restore | Unzip at the repository root: the files land in their places here; then `validate_assets.py store-assets/release-0.9.0-beta.2 --final` and `captures.py verify` work again |

## Size before and after

| | Files | Size |
| --- | --- | --- |
| The folder before (all tracked) | 140 | 80.5 MB |
| Tracked now | 46 | 0.32 MB |

## Moved to the archive (not tracked)

**Source captures (30: 24 emulator, 6 physical Fire TV)** (30 files, 46.8 MB): `source-screenshots/firetv-details.png`, `source-screenshots/firetv-home.png`, `source-screenshots/firetv-keyboard.png`, `source-screenshots/firetv-movies-rail.png`, `source-screenshots/firetv-player.png`, `source-screenshots/firetv-quickview.png`, `source-screenshots/phone-details.png`, `source-screenshots/phone-home.png`, `source-screenshots/phone-miniplayer.png`, `source-screenshots/phone-movies.png`, `source-screenshots/phone-player-landscape.png`, `source-screenshots/phone-quickview.png`, `source-screenshots/phone-search.png`, `source-screenshots/tablet10-details.png`, `source-screenshots/tablet10-home.png`, `source-screenshots/tablet10-movies.png`, `source-screenshots/tablet10-player.png`, `source-screenshots/tablet10-quickview.png`, `source-screenshots/tablet7-details.png`, `source-screenshots/tablet7-home.png`, `source-screenshots/tablet7-movies.png`, `source-screenshots/tablet7-player.png`, `source-screenshots/tablet7-quickview.png`, `source-screenshots/tv-details.png`, `source-screenshots/tv-home.png`, `source-screenshots/tv-keyboard.png`, `source-screenshots/tv-movies-focus.png`, `source-screenshots/tv-movies-rail.png`, `source-screenshots/tv-player.png`, `source-screenshots/tv-quickview.png`.

**Google Play exports and graphics** (24 files, 23.3 MB): `google-play/feature-graphic/01.png`, `google-play/phone/01.png`, `google-play/phone/02.png`, `google-play/phone/03.png`, `google-play/phone/04.png`, `google-play/phone/05.png`, `google-play/phone/06.png`, `google-play/tablet-10-inch/01.png`, `google-play/tablet-10-inch/02.png`, `google-play/tablet-10-inch/03.png`, `google-play/tablet-10-inch/04.png`, `google-play/tablet-10-inch/05.png`, `google-play/tablet-7-inch/01.png`, `google-play/tablet-7-inch/02.png`, `google-play/tablet-7-inch/03.png`, `google-play/tablet-7-inch/04.png`, `google-play/tablet-7-inch/05.png`, `google-play/tv-banner/01.png`, `google-play/tv/01.png`, `google-play/tv/02.png`, `google-play/tv/03.png`, `google-play/tv/04.png`, `google-play/tv/05.png`, `google-play/tv/06.png`.

**Amazon exports and graphics** (7 files, 5.1 MB): `amazon-appstore/fire-tv-background/01.png`, `amazon-appstore/fire-tv-icon/01.png`, `amazon-appstore/fire-tv/01.png`, `amazon-appstore/fire-tv/02.png`, `amazon-appstore/fire-tv/03.png`, `amazon-appstore/fire-tv/04.png`, `amazon-appstore/fire-tv/05.png`.

**Contact sheet, store-scale sheet, thumbnails** (33 files, 12.2 MB): `previews/contact-sheet.png`, `previews/store-scale.png`, `previews/thumbnails/amazon-appstore-fire-tv-01.png`, `previews/thumbnails/amazon-appstore-fire-tv-02.png`, `previews/thumbnails/amazon-appstore-fire-tv-03.png`, `previews/thumbnails/amazon-appstore-fire-tv-04.png`, `previews/thumbnails/amazon-appstore-fire-tv-05.png`, `previews/thumbnails/amazon-appstore-fire-tv-background-01.png`, `previews/thumbnails/amazon-appstore-fire-tv-icon-01.png`, `previews/thumbnails/google-play-feature-graphic-01.png`, `previews/thumbnails/google-play-phone-01.png`, `previews/thumbnails/google-play-phone-02.png`, `previews/thumbnails/google-play-phone-03.png`, `previews/thumbnails/google-play-phone-04.png`, `previews/thumbnails/google-play-phone-05.png`, `previews/thumbnails/google-play-phone-06.png`, `previews/thumbnails/google-play-tablet-10-inch-01.png`, `previews/thumbnails/google-play-tablet-10-inch-02.png`, `previews/thumbnails/google-play-tablet-10-inch-03.png`, `previews/thumbnails/google-play-tablet-10-inch-04.png`, `previews/thumbnails/google-play-tablet-10-inch-05.png`, `previews/thumbnails/google-play-tablet-7-inch-01.png`, `previews/thumbnails/google-play-tablet-7-inch-02.png`, `previews/thumbnails/google-play-tablet-7-inch-03.png`, `previews/thumbnails/google-play-tablet-7-inch-04.png`, `previews/thumbnails/google-play-tablet-7-inch-05.png`, `previews/thumbnails/google-play-tv-01.png`, `previews/thumbnails/google-play-tv-02.png`, `previews/thumbnails/google-play-tv-03.png`, `previews/thumbnails/google-play-tv-04.png`, `previews/thumbnails/google-play-tv-05.png`, `previews/thumbnails/google-play-tv-06.png`, `previews/thumbnails/google-play-tv-banner-01.png`.

## Kept in Git

Every other file of this folder: the READMEs, `release.json`, `capability-report.md`, `project-profile.md`, `capture-plan.md`, `design-system.md`, `validation-report.md`, `source-screenshots/captures.json` (provenance, with each capture's SHA-256 and the rejected captures' reasons; the rejected images themselves were deleted when rejected), and `working/` (the storyboard generator `build.py`, `campaign.css`, every composition's HTML, the brand SVGs and Manrope with its licence).

**Website exception:** none needed: the website's images are its own WebP files in `website/public/showcase/` (made by `website/scripts/make-showcase.py` from these captures) and nothing in the website build reads this folder.
