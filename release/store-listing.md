# Store listing material

The single source of text and artwork for Reflux's Google Play and Amazon Appstore listings, prepared in M7 and audited for M11 on 2026-11-15 (docs/RELEASE.md, "Store readiness (M7)"; the Amazon owner steps are in `release/amazon-appstore.md`). Nothing has been submitted. Keep it to what the product does today; change it with the product. Both consoles' fields are filled from this file; do not retype the text elsewhere.

**Metadata provider: Trakt** (M11 plan D5, decided 2026-11-15; DECISIONS.md, "Trakt is the metadata provider"). The full description names Trakt and ends with Trakt's required sentence; if the provider ever changes, change the metadata sentence, the attribution sentence, and the privacy answers (docs/PRIVACY.md) together. **The console drafts saved on 2026-11-15 still carry the earlier TMDB wording** (Play's full description and Amazon's long description): replace them with the text below before anything is submitted.

## Identity

| Field | Value |
| --- | --- |
| App name | Reflux |
| Package | `dev.rsxdevelops.reflux` (both stores, the same `versionCode`) |
| Developer | RSX Develops |
| Category | Google Play: Video Players & Editors. Amazon: chosen in the console (the Appstore's video / movies and TV category for Fire TV; confirm the exact name there) |
| Price | Free. No in-app purchases, no ads, no subscription |
| Privacy policy | https://reflux.rsxdevelops.dev/privacy |
| Website | https://reflux.rsxdevelops.dev |
| Support e-mail | **To be set by the owner** (both stores require one; also published on /support) |
| Platforms the listings cover | Google Play: Android phones and tablets, Android TV, Google TV. Amazon Appstore: Fire TV devices running Fire OS only (never described as Android TV). Windows and Linux are downloads from the website, never named as store platforms. Vega OS is not supported and is never targeted |

## Short description

**Google Play (at most 80 characters):**

> Your films and shows from folders, Jellyfin, Plex, and shares. No account.

(75 characters.)

**Amazon Appstore.** Amazon's "short description" is its main description field (limit 2,000 bytes), and **Fire TV shows only its first 200 characters**, so the opening carries the listing:

> Reflux plays the films and shows you already have, from folders, Jellyfin, Plex, and network shares, in one library. It picks the copy your device plays best. No account, no ads, no tracking.

(193 characters.) **The console's limit is 1,200 characters** (read in the console on 2026-11-15, not the 2,000 bytes above), so the opener plus the full description (about 1,480) does not fit: the short description is the opener alone, which is all Fire TV shows. Amazon's long description (4,000 characters, plain text, no HTML; shown on the website and Fire tablets, not on Fire TV) takes the full description unchanged.

**Amazon product feature bullets (3 to 5, one per line):**

> One library across folders, Jellyfin, Plex, and network shares
> Play picks the best copy for this device and resumes on any copy
> The original file when your device can play it; Jellyfin converts only what is needed
> Keep films and episodes for offline
> Works with the Fire TV remote and game controllers; no account, no ads, no tracking

**Amazon keywords (optional, comma-separated):** media player, video player, Jellyfin, Plex, library, local media, offline, subtitles, home media, controller (the console splits on spaces as well as commas, so it stores these as single words)

## Full description (both stores; plain text)

Dashes are plain text on purpose (Amazon rejects markup). One thing differs by store, and only this: the Watch Next clause is **Google Play only** (Fire TV has no home-screen row, DECISIONS.md, 2026-10-18; delete the clause from the Amazon copy). Both stores keep the last line, Trakt's attribution, which its API use policy requires, word for word. Trakt is described only as what it does for Reflux (recognising works, descriptions, artwork), never as a partner or sponsor.

> Reflux is a media player for the films and shows you already have, wherever they live: in a folder on your device or a drive plugged into it, on a Jellyfin or Plex server, or on a Windows, NAS, or WebDAV share.
>
> Point Reflux at a folder and it builds your library by itself: films, shows, seasons, and episodes recognised from ordinary file names, with descriptions and artwork from your servers and from Trakt. No renaming, no setup files, no account.
>
> - One library across every source. The same film on your server and on your tablet is one film, with every copy listed as a version.
> - Play picks the copy this device plays best, and starts where you left off, on any copy.
> - The original file whenever your device can play it. When it can't, a Jellyfin server converts only what's needed, usually just the sound.
> - Keep films, episodes, and whole seasons for offline, and watch them with the source away.
> - Continue Watching and Next Up, and on Android TV and Google TV, the home screen's Watch Next row.
> - Built for touch, TV remotes, game controllers, and keyboards, with screen-reader labels throughout.
> - Private: no account, no analytics, no ads, no tracking. Reflux sends nothing to its makers.
>
> Reflux never renames, moves, or changes your files.
>
> Reflux plays your own media. It does not include any films, shows, or channels.
>
> Reflux uses Trakt but is not endorsed or certified by Trakt.

## Artwork (design/brand/store, generated by tools/brand/make-brand-assets.py)

| Google Play | File |
| --- | --- |
| App icon 512 × 512 | `play-icon-512.png` |
| Feature graphic 1024 × 500 | `play-feature-graphic-1024x500.png` |
| TV banner 1280 × 720 | `play-tv-banner-1280x720.png` |
| Phone screenshots (2:1) | `screenshots/phone-home-1080x2160.jpg` |
| Tablet screenshots | `screenshots/tablet-home-2328x1554.jpg`, `tablet-details-2328x1554.jpg` |
| TV screenshots 1920 × 1080 | `screenshots/tv-home-1920x1080.jpg`, `tv-movies-1920x1080.jpg`, `tv-details-1920x1080.jpg` |

| Amazon Appstore (Fire TV) | File |
| --- | --- |
| Small icon 114 × 114, large icon 512 × 512 | `amazon-icon-114.png`, `amazon-icon-512.png` |
| Fire TV app icon 1280 × 720 | `amazon-firetv-icon-1280x720.png` |
| Background 1920 × 1080 (required by the console) | `amazon-firetv-background-1920x1080.png` |
| Screenshots 1920 × 1080 (3 to 10) | `screenshots/tv-*.jpg`: three (home, movies, a film's details), regenerated 2026-11-15 from the final interface (M10), RGB JPG |

Amazon's asset rules (read 2026-11-15, developer.amazon.com, "Step 3: Add Appstore Details"): small and large icons PNG; Fire TV app icon 1280 × 720 PNG **with no transparency**, subject inside the 882 × 448 safe area; Fire TV background 1920 × 1080 (required by the console), JPG or 24-bit PNG with no transparency, subject inside 1214 × 830; screenshots 3 to 10, 1920 × 1080 landscape, JPG or 24-bit PNG with no transparency, not distorted or pixelated. The Fire TV files in `design/brand/store` are fully opaque but saved as RGBA PNG; export them as RGB if the console objects.

The screenshots are Reflux's real layouts rendered offscreen with the Blender Foundation's open films (CC BY; credited on the website), not mock-ups. Google Play asks for at least two phone screenshots: the second phone image is captured from a device when the listing is made.

## Content rating (questionnaire answers, both stores)

A media player for the user's own files. No user-generated content, no communication between users, no location sharing, no purchases, no gambling, no ads. It shows whatever media the user adds, as any file manager or player does; Reflux contains no films, shows, or channels. Expected: the lowest rating (PEGI 3 / Everyone) as a utility; the questionnaires' own results are recorded in docs/RELEASE.md when they are answered. Amazon may assign its own maturity rating, and a published app's rating changes only with a new app file.

**Google Play (IARC):** answer as above: no violence, sexual content, language, or controlled-substance content of its own; no user interaction or sharing; no location; no purchases.

**Amazon's content rating questionnaire (13 fields, read 2026-11-15):**

| Field | Answer |
| --- | --- |
| Violence | None |
| Cartoon violence | None |
| Drugs | None |
| Nudity | None |
| Sex | None |
| Intolerance | None |
| Profanity | None |
| Academic | None |
| Account creation or other personal information collected | No (no Reflux account; a media server's sign-in is stored on the device only and never sent to RSX Develops) |
| Advertisements | No |
| Gambling | No |
| Location detection or location-based services | No |
| User-generated content or user-to-user communication | No |

The answers describe Reflux itself; the user's own library is theirs. Re-read the console's wording when answering (Amazon's field names can change) and record the result.

## Data safety and Amazon's privacy questions

The facts in docs/PRIVACY.md, "Store disclosures", and only those: no account; no advertising ID; nothing collected by RSX Develops; with "Online metadata & artwork" on (the default, and it can be turned off), the titles of the user's films and shows, or catalog IDs for them, are sent from the device to Trakt, a third party, over HTTPS, for app functionality (identifying works and fetching descriptions and artwork), never for analytics or advertising. "No data leaves the device" is not true and is not to be answered. The data type and the shared/collected classification are chosen against each console's current definitions when the form is filled, and recorded. Amazon's User Data Privacy Questionnaire must be complete before a new app or an update can be published (Amazon, "Appstore privacy labels", read 2026-11-15), and third-party code's data collection is declared in it too.

## What's new (release notes)

For each version, the release-notes text in both consoles is that version's section of `CHANGELOG.md`, copied and never retyped, trimmed only to each console's length limit; the release tooling generates it from that section, so use its output rather than a hand-edited copy. While the changelog says "Unreleased" no store text is written.
