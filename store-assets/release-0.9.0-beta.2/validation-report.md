# Validation report

- Release folder: `release-0.9.0-beta.2`
- Run date: 2026-11-21
- Platform profile checked against official docs on: **2026-11-20**
- Targets from: release.json
- Result: **PASS** - 0 error(s), 5 warning(s)

Errors are official requirements that failed. Warnings are recommendations or hygiene issues.
PASS means only that the automatic checks listed below passed; see 'Not checked automatically'.

## Google Play - Phone screenshots (`google-play/phone`): PASS

| File | Size (px) | Format | Mode | KB | Result |
|---|---|---|---|---|---|
| 01.png | 1080x1920 | PNG | RGB | 873 | ok |
| 02.png | 1080x1920 | PNG | RGB | 1061 | ok |
| 03.png | 1080x1920 | PNG | RGB | 621 | ok |
| 04.png | 1080x1920 | PNG | RGB | 1091 | ok |
| 05.png | 1080x1920 | PNG | RGB | 802 | ok |
| 06.png | 1080x1920 | PNG | RGB | 787 | ok |

Checked: format PNG/JPEG, sides 320-3840px, ratio <= 2.3, <= 8 MB, alpha forbidden, count 2-8, file type vs extension, color mode, naming, duplicates.

Not checked automatically (review by eye):
- [ ] Show the real app experience; prioritize UI in the first three screenshots.
- [ ] Tagline/text covers no more than ~20% of the image.
- [ ] No ranking, price, promo or call-to-action text (e.g. 'Best', '#1', 'Free', 'Sale', 'Download now').
- [ ] Clean status bar: no notifications or carrier name; full battery/Wi-Fi/signal.
- [ ] No device-holding hands.
- [ ] Alt text per screenshot, 140 characters or fewer.

## Google Play - Feature graphic (`google-play/feature-graphic`): PASS

| File | Size (px) | Format | Mode | KB | Result |
|---|---|---|---|---|---|
| 01.png | 1024x500 | PNG | RGB | 228 | ok |

Checked: format PNG/JPEG, exact size list, alpha forbidden, count 1-1, file type vs extension, color mode, naming, duplicates.

Note: Required to publish.

Not checked automatically (review by eye):
- [ ] Key content centered; nothing important in the edge cutoff zones.
- [ ] Avoid a pure white, black or dark-gray ground.
- [ ] No device mockups, store badges, or third-party trademarks.
- [ ] Don't simply repeat the app icon.
- [ ] Alt text, 140 characters or fewer.

## Google Play - 7-inch tablet screenshots (`google-play/tablet-7-inch`): PASS

| File | Size (px) | Format | Mode | KB | Result |
|---|---|---|---|---|---|
| 01.png | 1920x1080 | PNG | RGB | 804 | ok |
| 02.png | 1920x1080 | PNG | RGB | 644 | ok |
| 03.png | 1920x1080 | PNG | RGB | 516 | ok |
| 04.png | 1920x1080 | PNG | RGB | 1251 | ok |
| 05.png | 1920x1080 | PNG | RGB | 994 | ok |

Checked: format PNG/JPEG, sides 1080-7680px, <= 8 MB, alpha forbidden, count 0-8, file type vs extension, color mode, naming, duplicates.

Note: Official sources disagree: the Play help article says 16:9 or 9:16 and gives both 1,080-7,680 px and a general 3,840 px max; the Android CLI screenshot guide captures tablets at 2560x1600 (16:10). 1920x1080 / 1080x1920 satisfies every source; 16:10 is accepted here with a note.

Not checked automatically (review by eye):
- [ ] Avoid extra text near edges that may be cut off.
- [ ] Alt text per screenshot, 140 characters or fewer.

## Google Play - 10-inch tablet screenshots (`google-play/tablet-10-inch`): PASS

| File | Size (px) | Format | Mode | KB | Result |
|---|---|---|---|---|---|
| 01.png | 2560x1440 | PNG | RGB | 1257 | warning |
| 02.png | 2560x1440 | PNG | RGB | 1006 | warning |
| 03.png | 2560x1440 | PNG | RGB | 1342 | warning |
| 04.png | 2560x1440 | PNG | RGB | 1451 | warning |
| 05.png | 2560x1440 | PNG | RGB | 2216 | warning |

- **WARN** 01.png: not a recommended size (1920x1080, 1080x1920, 3840x2160, 2560x1600, 1600x2560)
- **WARN** 02.png: not a recommended size (1920x1080, 1080x1920, 3840x2160, 2560x1600, 1600x2560)
- **WARN** 03.png: not a recommended size (1920x1080, 1080x1920, 3840x2160, 2560x1600, 1600x2560)
- **WARN** 04.png: not a recommended size (1920x1080, 1080x1920, 3840x2160, 2560x1600, 1600x2560)
- **WARN** 05.png: not a recommended size (1920x1080, 1080x1920, 3840x2160, 2560x1600, 1600x2560)

Checked: format PNG/JPEG, sides 1080-7680px, <= 8 MB, alpha forbidden, count 0-8, file type vs extension, color mode, naming, duplicates.

Note: Same source disagreement as tablet-7-inch (16:9/9:16 vs 16:10).

Not checked automatically (review by eye):
- [ ] Avoid extra text near edges that may be cut off.
- [ ] Alt text per screenshot, 140 characters or fewer.

## Google Play - Android TV / Google TV screenshots (`google-play/tv`): PASS

| File | Size (px) | Format | Mode | KB | Result |
|---|---|---|---|---|---|
| 01.png | 1920x1080 | PNG | RGB | 878 | ok |
| 02.png | 1920x1080 | PNG | RGB | 675 | ok |
| 03.png | 1920x1080 | PNG | RGB | 227 | ok |
| 04.png | 1920x1080 | PNG | RGB | 901 | ok |
| 05.png | 1920x1080 | PNG | RGB | 1059 | ok |
| 06.png | 1920x1080 | PNG | RGB | 1991 | ok |

Checked: format PNG/JPEG, sides 320-3840px, <= 8 MB, alpha forbidden, count 1-8, file type vs extension, color mode, naming, duplicates.

Note: Part of the Google Play listing (device-specific TV section), not a separate store. Quality guideline TV-G4 asks for at least one unaltered, high-resolution screenshot of the current TV experience.

Not checked automatically (review by eye):
- [ ] At least one screenshot is an unaltered capture of the TV UI (TV-G4).

## Google Play - TV banner (store) (`google-play/tv-banner`): PASS

| File | Size (px) | Format | Mode | KB | Result |
|---|---|---|---|---|---|
| 01.png | 1280x720 | PNG | RGB | 46 | ok |

Checked: format PNG/JPEG, exact size list, alpha forbidden, count 1-1, file type vs extension, color mode, naming, duplicates.

Note: Required for Android TV apps. Distinct from the 320x180 launcher banner inside the APK.

## Amazon Appstore - Fire TV screenshots (`amazon-appstore/fire-tv`): PASS

| File | Size (px) | Format | Mode | KB | Result |
|---|---|---|---|---|---|
| 01.png | 1920x1080 | PNG | RGB | 878 | ok |
| 02.png | 1920x1080 | PNG | RGB | 675 | ok |
| 03.png | 1920x1080 | PNG | RGB | 227 | ok |
| 04.png | 1920x1080 | PNG | RGB | 901 | ok |
| 05.png | 1920x1080 | PNG | RGB | 1991 | ok |

Checked: format PNG/JPEG, exact size list, alpha forbidden, count 3-10, file type vs extension, color mode, naming, duplicates.

## Amazon Appstore - Fire TV app icon (`amazon-appstore/fire-tv-icon`): PASS

| File | Size (px) | Format | Mode | KB | Result |
|---|---|---|---|---|---|
| 01.png | 1280x720 | PNG | RGB | 46 | ok |

Checked: format PNG, exact size list, alpha forbidden, count 1-1, file type vs extension, color mode, naming, duplicates.

Not checked automatically (review by eye):
- [ ] Main subject inside the central 882x448 safe zone.

## Amazon Appstore - Fire TV background image (`amazon-appstore/fire-tv-background`): PASS

| File | Size (px) | Format | Mode | KB | Result |
|---|---|---|---|---|---|
| 01.png | 1920x1080 | PNG | RGB | 507 | ok |

Checked: format PNG/JPEG, exact size list, alpha forbidden, count 1-1, file type vs extension, color mode, naming, duplicates.

Not checked automatically (review by eye):
- [ ] Main subject inside the 1214x830 safe area, preferably on the right.
- [ ] No embedded logos or titles.

## Release checks

- **INFO** sources by provenance: auto-captured 24

## Not checked automatically (all targets)

- Visual quality, legibility at store thumbnail size, brand fit.
- Truthfulness of headlines and that UI is authentic and unaltered.
- Store content policies (claims, trademarks, personal data in screenshots).
- Whether the official requirements changed after the profile date above.

Sources:
- https://developer.amazon.com/docs/app-submission/add-store-listing-information.html#image-asset-guidelines
- https://support.google.com/googleplay/android-developer/answer/9866151
- https://developer.android.com/google/play/requirements/screenshots-android-cli
- https://developer.android.com/docs/quality-guidelines/tv-app-quality
- https://developer.android.com/training/tv/publishing/distribute
