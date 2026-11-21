# Design system — Reflux 0.9.0-beta.2

## Evidence reviewed

`design/src/commonMain/kotlin/dev/reflux/design/Tokens.kt` and `Roles.kt` (colour roles), `docs/DESIGN.md` (Visual System 2.0, Media Atmosphere, "let the media be colorful, let the interface be calm"), `design/brand/` (mark, lockup, Manrope, brand README), the website's tokens (`website/src/styles.css`), `scan_brand.py` and `extract_palette.py` over the 24 captures (pixel-weighted luminance 0.08: a dark UI whose only colour is artwork).

## Visual DNA

| Area | Finding | Evidence | Confidence |
| --- | --- | --- | --- |
| Theme | Dark only; neutral interface, colour from media | Tokens.kt; DESIGN.md "Identity"; palette 37 % #0A0A0B | high |
| Primary / accent | None of its own; white (#F5F5F6) primary actions | Tokens.kt `primaryAction`, captures (Resume/Play pills) | high |
| Background / surfaces | #0A0A0B, #141416, #1D1D20 | Tokens.kt | high |
| Text | #F5F5F6, secondary #B2B2B8, tertiary #85858C | Tokens.kt | high |
| Typography | Brand: Manrope (the wordmark's face, OFL). App UI: the platform's (Roboto on Android) | design/brand/README.md, Manrope-OFL.txt; captures | high |
| Shape | Pills for actions, 16–24 dp cards, round icon buttons, hairline borders | captures; Roles.kt `hairline` | high |
| Elevation | Glass only on navigation; soft depth, no heavy shadows | DESIGN.md "Material tiers" | medium |
| Iconography | Thin outline icons | captures | high |
| Imagery | Film artwork tints the page (Media Atmosphere); hero artwork under navigation | DESIGN.md; tv-home, phone-home | high |

## Campaign system (held constant)

- **Ground:** #0A0A0B, tinted by that shot's own capture blurred to 90 px (unreadable: colour only), under a radial veil to the ground colour. Reason: it is how Reflux itself treats artwork (Media Atmosphere).
- **Type:** Manrope 760 for headlines (−3.5 % tracking, sentence case, the brand's voice), 500 in #B2B2B8 for one supporting line. One emphasis device: the colour step between them, nothing else.
- **UI:** real captures, uniformly scaled, rounded to the device's proportion, a 1 px white hairline at 12 % (Roles.kt `hairline`) and one soft shadow for depth. No device frames: Reflux's own UI is the subject, and frames would add hardware that is not Reflux's.
- **Motif:** the Reflux lockup, only on each set's first shot and the feature graphic.
- **Margins:** phone 84 px; tablets 112–160 px; TV 96 px (5 % title-safe).

## Storyboards (composition varies; headline proven by the capture under it)

**Phone (1080 × 1920):** 01 *Your media, played well.* Home (lockup, offset bleed) · 02 *Know it before you press Play.* Sintel's page, detail crop from title to version (4K, HDR10, HEVC 3840 × 2160, Dolby Digital, Home Server) · 03 *A closer look, one long press away.* Quick View, edge-to-edge band · 04 *The picture comes first.* landscape player turned with the phone · 05 *Keep browsing while it plays.* Home with the mini-player · 06 *Your library answers as you type.* results for "s", headline below.

**10-inch tablet (2560 × 1440, 16:9):** 01 *Your media, played well.* text column + sidebar Home bleeding right · 02 *Pick up exactly where you stopped.* Sprite Fright's page (Resume, From the start, 7 min left), text right · 03 *A closer look without leaving the grid.* Quick View crop, headline below · 04 *Your whole collection at a glance.* Movies grid with progress bars, text right · 05 *Made for the big screen in your hands.* player, headline above.

**7-inch tablet (1920 × 1080):** the rail layout, a different capture for every shot: 01 Home · 02 Cosmos Laundromat's page · 03 *A closer look, in place.* · 04 *The picture comes first.* player with text right · 05 *Your collection at a glance.*

**TV (1920 × 1080):** 01 *Your media, played well.* Home with remote focus on Resume · 02 *Press Menu for a quick look.* (Quick View opened with `KEYCODE_MENU`) · 03 *A keyboard made for the remote.* Reflux's keyboard, completion "Sintel" · 04 *Everything is one press of Left away.* the rail · 05 *Read it, then press Play.* Elephants Dream's page · 06 the player, **unaltered** (Google's TV quality guideline asks for one).

**Fire TV (1920 × 1080):** TV 01–04 and 06 as separate files (Amazon's order: Home, Quick View, keyboard, rail, player). **Feature graphic (1024 × 500):** lockup and tagline left; a real crop of the TV home hero (title, description, Resume) right; artwork-tinted ground. **TV banner and Fire TV icon:** the brand's own (`design/brand/store`, `tools/brand/make-brand-assets.py`), converted to opaque RGB. **Fire TV background:** a logo-free field of colour from the player capture, weighted right within 1214 × 830.

## Audit of the previous screenshots (`design/brand/store/screenshots/`, 2026-11-16)

Twelve raw captures (Beta 1's interface), uploaded to the consoles as plain screenshots. Findings: **outdated** (no Quick View, Reflux keyboard, featured-title choice, the Phase 2 controller hints); **raw, not designed** (no headline or story, so the first three shots say nothing about what Reflux is); **phone captures cropped to 2:1** from the Phone 2a (fine, but now superseded by 9:16 compositions); **tablet 2328 × 1554** (3:2, not the 16:9 Play's page now asks for); **Fire TV set captured on the physical stick** (the one advantage they keep: real Fire OS provenance); desktop captures are website material, not store assets. They stay in the repository as Beta 1's record; this release replaces them for Beta 2's listing.

## Review log

Round 1 (full contact sheet, every set at reduced size, then individual images):
- tablet-10 02, 04: the frame edge sliced the sidebar's labels ("flux", "Movies") → recropped to start after the sidebar (02) and placed the frame inside the canvas (04).
- tablet-10 03: crop began inside the grid's first poster → crop moved right to the grid's edge.
- tablet-7 02: "Version" heading sliced to "ersion" → crop widened; 03: "Movies" heading sliced and Quick View's description cut at the right → crop widened both sides.
- tv 03 / fire-tv 03: frame bled off the left edge through the rail icons → placed inside the title-safe area.
- feature graphic: a strip of cards cut at the top, top-heavy, ground nearly black (Play advises against) → rebuilt around the TV hero crop on a brighter artwork ground, centred.
- phone 04: the turned player read top-to-bottom → turned the other way (as a phone turned left), reading upwards.
- phone 05: crop ended on "Recent…" where the app's own navigation bar overlaps the heading → crop ends above the navigation bar.
- phone 03: band ran to the canvas edge through the next row → ends just under the card.

Round 2 (re-rendered sets and the store-scale sheet at 180 px): headlines legible at store scale on every set; no sliced text found; neighbours differ in skeleton (phone: top/bottom headline alternation, bleed vs band vs turned; tablet: left/right/below/above). Remaining by choice: the 10-inch size 2560 × 1440 is not in LaunchFrame's recommended list (it prefers 2560 × 1600), but Play's page (read 2026-11-21) asks for 16:9 landscape for tablets, so 16:9 is kept.

Round 3 (after the Fire TV session): the Amazon set rebuilt from the physical stick's captures with the same storyboard and compositions; looked at as a set and at store scale: the stick's rail capture is cleaner than the emulator's (the grid is not scrolled); no sliced text; `validate_assets.py --final` exit 0.

## Generated elements

None. No image generation was used; every picture element is a capture or the brand's own files.
