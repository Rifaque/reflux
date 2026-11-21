# Project profile — Reflux 0.9.0-beta.2

## In one sentence

Reflux is a free, open-source media player for the films and shows people already have (local folders, Jellyfin, Plex, SMB and WebDAV shares), one library on Android phones and tablets, Android TV / Google TV, Fire TV, Windows and Linux.

## Audience

People with their own media collection, often a home server, who watch on several devices including a TV and a controller or remote. No account is needed; nothing is sent to the makers.

## Platforms

| Target | Evidence | Selected |
| --- | --- | --- |
| google-play/phone | `android/app/src/main/AndroidManifest.xml` LAUNCHER activity; walks on the physical Nothing Phone (2a) (docs/VERIFICATION.md) | yes |
| google-play/tablet-7-inch | `design/.../Layout.kt` `formFactor()`: shortest side ≥ 600 dp and width < 1200 dp → tablet rail; confirmed at runtime on the `Reflux_Tablet7` emulator (960 × 600 dp) | yes (forced from Probable with that evidence) |
| google-play/tablet-10-inch | Same rule: ≥ 1200 dp → sidebar; runtime on `Reflux_Tablet` (Pixel Tablet profile, 1280 × 800 dp); physical Xiaomi Pad 7 walks | yes (forced from Probable) |
| google-play/tv | Manifest LEANBACK_LAUNCHER, `android:banner`, leanback not required | yes |
| amazon-appstore/fire-tv | The same TV build is Reflux's Fire OS release (`release/amazon-appstore.md`, Amazon Live App Test 1 submitted 2026-11-17) | yes (sources are Android TV emulator captures; see README) |
| Fire tablet, foldable, Chromebook, Wear OS, Automotive, XR | No declaration; Android XR explicitly excluded at M11 | no |

## Features (evidence-backed)

| Feature | Evidence (file / screen) | Visual? | Differentiator? |
| --- | --- | --- | --- |
| One library from folders, Jellyfin, Plex, SMB, WebDAV | ARCHITECTURE.md "Source abstraction"; Sources page | weak (one demo server) | yes |
| Home with featured title and Continue Watching | `phone-home.png`, `tablet10-home.png`, `tv-home.png`; B-014 | strong | yes |
| Details: resolution, HDR, codec, sound, source per version | `phone-details.png` (4K · HDR10, HEVC 3840 × 2160, Dolby Digital, Home Server) | strong | yes |
| Media Quick View (long press, right-click, remote Menu, controller X) | `*-quickview.png`; CHANGELOG Unreleased; F-P3 | strong | yes |
| Resume / From the start, progress | `tablet10-details.png`, `tablet7-details.png`; WATCH_STATE.md | strong | no |
| Player whose controls step aside | `*-player*.png`; CHANGELOG 0.9.0-beta.1 "they go away while you watch" | strong | no |
| Mini-player while browsing | `phone-miniplayer.png`; CHANGELOG 0.9.0-beta.1 | strong | yes |
| Search as you type, from the library | `phone-search.png`, `tv-keyboard.png` | strong | yes |
| Reflux's own keyboard for the remote, with title completions | `tv-keyboard.png`; F-P4 | strong | yes |
| TV rail opened with Left | `tv-movies-rail.png`; NAVIGATION.md | strong | no |

## Not claimable

Trickplay scene previews (the demo server has none generated; not captured), "every source in one view" as a picture (one demo server), speed or "best" claims, ratings, user counts, anything about statistics beyond "nothing is sent" (not on a screen, so not a headline).

## Existing listing and brand material

`design/brand/store/` (icons, feature graphic, TV banner, Fire TV icon and background from `tools/brand/make-brand-assets.py`) and twelve raw captures in `design/brand/store/screenshots/` from 2026-11-16 (Beta 1's interface). Audited in design-system.md.

## Constraints

The demo library is the Blender Foundation's open films with their Creative Commons artwork (credited at reflux.rsxdevelops.dev/legal/third-party#artwork), served by a throwaway local Jellyfin with no internet metadata; the debug build carries no Trakt Client ID. No personal library, server address or profile name appears.
