# Reflux — store assets for 0.9.0-beta.2

Made with LaunchFrame 2.0.0 (the Claude Code plugin `launch-frame@launch-frame`) in Phase 4, 2026-11-21. **Generated and validated; nothing uploaded.** Beta 2 is not approved or cut yet (docs/BETA.md); these are ready for its listing update. The folder name is the version these assets are for, not a version cut.

**The images are in the release archive, not in Git:** see [ARCHIVE.md](ARCHIVE.md) to restore them.

## Status

| Store | Device / asset | Folder | Count | Size (px) | Status |
| --- | --- | --- | --- | --- | --- |
| Google Play | Phone screenshots | `google-play/phone` | 6 | 1080 × 1920 | validated |
| Google Play | 7-inch tablet screenshots | `google-play/tablet-7-inch` | 5 | 1920 × 1080 | validated |
| Google Play | 10-inch tablet screenshots | `google-play/tablet-10-inch` | 5 | 2560 × 1440 | validated |
| Google Play | Android TV screenshots | `google-play/tv` | 6 | 1920 × 1080 | validated |
| Google Play | Feature graphic | `google-play/feature-graphic` | 1 | 1024 × 500 | validated |
| Google Play | TV banner (brand's own) | `google-play/tv-banner` | 1 | 1280 × 720 | validated |
| Amazon Appstore | Fire TV screenshots (captured on a physical Fire TV Stick 4K Plus) | `amazon-appstore/fire-tv` | 5 | 1920 × 1080 | validated |
| Amazon Appstore | Fire TV icon (brand's own) | `amazon-appstore/fire-tv-icon` | 1 | 1280 × 720 | validated |
| Amazon Appstore | Fire TV background | `amazon-appstore/fire-tv-background` | 1 | 1920 × 1080 | validated |

## What's here

`project-profile.md` (what Reflux is and which features can be claimed), `capability-report.md` (targets from repository evidence), `capture-plan.md` (every source: screen, device, how), `design-system.md` (evidence, campaign system, storyboards, the audit of Beta 1's screenshots, the review log), `validation-report.md`, `source-screenshots/` (24 untouched captures and `captures.json`), `working/` (`build.py`, the storyboard as data; `campaign.css`; one HTML per image; Manrope with its OFL licence), `previews/` (contact sheet, store-scale sheet, thumbnails), and one README per store.

## Screenshot story

Phone, in order: what Reflux is (Home) → what you know before playing (a film's page) → Quick View → the player → the mini-player → search as you type. Tablets and TV follow the same arc with their own screens; the TV set adds the remote (Menu, the keyboard, Left to the rail) and ends with one unaltered capture. Each headline's evidence: `design-system.md` and `project-profile.md`.

## Re-rendering

From this folder, with LaunchFrame installed (`/plugin install launch-frame@launch-frame`) and `S` its `scripts` folder:

```
python -I working/build.py
python S/render_html.py "working/google-play/phone/*.html" --outdir google-play/phone --size 1080x1920
python S/render_html.py "working/google-play/tablet-10-inch/*.html" --outdir google-play/tablet-10-inch --size 2560x1440
python S/render_html.py "working/google-play/tablet-7-inch/*.html" --outdir google-play/tablet-7-inch --size 1920x1080
python S/render_html.py "working/google-play/tv/*.html" --outdir google-play/tv --size 1920x1080
python S/render_html.py "working/amazon-appstore/fire-tv/*.html" --outdir amazon-appstore/fire-tv --size 1920x1080
python S/render_html.py "working/google-play/feature-graphic/*.html" --outdir google-play/feature-graphic --size 1024x500
python S/render_html.py "working/amazon-appstore/fire-tv-background/*.html" --outdir amazon-appstore/fire-tv-background --size 1920x1080
python S/contact_sheet.py . && python S/contact_sheet.py . --thumb-height 180 --out previews/store-scale.png --thumbnails previews/thumbnails
python S/validate_assets.py . --final --report validation-report.md
```

## Assumptions

- Beta 2's interface is final (scope frozen since 2026-11-20); the debug build used for capture is that code.
- Tablets use 16:9 (Play's page, 2026-11-21: "16:9 for landscape"), so the 10-inch set is 2560 × 1440, which LaunchFrame's profile lists as non-recommended (a warning, explained in `design-system.md`).

## Known limitations

- The phone, tablet and Google Play TV sources are emulator captures; the Fire TV set's sources are from a physical Fire TV Stick 4K Plus.
- Trickplay scene previews, a Beta 2 highlight, are not shown: the demo server had none.
- The demo library is seven films from one demo server, so no shot can prove "every source in one view"; no headline claims it.

## Remaining tasks (owner)

1. Approve Beta 2, then upload: Play (`google-play/README.md`), Amazon (`amazon-appstore/README.md`).
