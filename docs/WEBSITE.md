# Reflux Website

reflux.rsxdevelops.dev: Reflux's public home (history/milestones/LAUNCH.md for its stages and visual direction, ROADMAP.md for when each is due). This document is how it is built, checked, and deployed. Its M7 redesign (Website 2.0) replaced the M6 foundation's presentation; the foundation's routing, release data, and security model are kept.

## Architecture

- **React 19 + Vite, prerendered, static.** `npm run build` runs `scripts/release-data.mjs`, the client build, a server-side build of `src/entry-server.jsx`, and `scripts/prerender.mjs`, which writes every page in `src/routes.js` as its own HTML file (`index.html`, `download.html`, `docs/playback.html`, …) with its title, description, canonical address, and sharing card, plus `404.html`, `sitemap.xml`, and `robots.txt`. Every page is readable without JavaScript; the script then takes the page over for client-side navigation. No server code, backend, forms, or database.
- **Routes** (`src/routes.js`, the one list the app, the prerenderer, and the checks read): Home, Features (Phase 4: one section per capability with its real screens; what arrives with Beta 2 is labelled "Coming in Beta 2" until a Beta 2 publication record exists, `src/released.js`), Roadmap (Phase 4, below), Platforms and compatibility, Sources, Download, Testing (the Beta page: joining per platform, the report template, known issues, the timeline to 1.0; `/beta` redirects to it; the season's dates and the 1.0 window are typed once, in `src/beta.js`), Documentation (a portal of its own, below), Releases, Support, Privacy, License (`/terms` redirects to it), Third-party notices (with the artwork credits), Security, and a not-found page.
- **Visual system** (`src/styles.css`): the product's own rules (DESIGN.md, history/milestones/LAUNCH.md "Visual direction"): near-black, one typeface (Manrope, self-hosted from `@fontsource-variable/manrope`, the brand's wordmark face; declared in `styles.css` with `font-display: optional` and its Latin file preloaded on every page by `prerender.mjs`, so text is never re-flowed when the font arrives: DECISIONS.md, 2026-11-12), hairlines and quiet surfaces, glass only on the sticky header, colour only from real product images, motion only on the hero's entrance and nothing with reduced motion. The brand (design/brand) is drawn inline from the master geometry.
- **Motion** (`src/motion.jsx`, the end of `src/styles.css`): the shared motion language (DESIGN.md, "Motion language") with browser features only. The script sets `html.motion`; without it, or with `prefers-reduced-motion`, every element is in its finished state. Navigation uses React Router's `viewTransition` (the View Transition API where the browser has it).
- **RSX Develops** is credited in the footer and on License with its own wordmark (`public/brand/`); Trakt on the third-party page with its notice (no logo).
- **Hosting: Vercel** (DECISIONS.md, 2026-11-08), team "rifaque's projects", project `reflux`.

## Roadmap and its easter egg (Phase 4)

`/roadmap` is docs/ROADMAP.md simplified (`src/roadmap.js`, changed in the same commit as ROADMAP.md): each stage with its status in words (Released, In progress, Committed, Planned, Exploring), never a date. On wide screens the stages alternate either side of a line that fills as the page is read, resting at the current stage without script or with reduced motion. The easter egg is the Reflux mark at the end of the page (shown only once the script runs): it opens **the reel**, the road ahead as a Reflux player, a native modal dialog where the versions are chapters on a timeline; moving along it shows each one's preview as Reflux's Trickplay does, arrows step between versions as the right stick does, the play button plays through them, Escape closes and focus returns to the mark. Everything is set through classes and data attributes (no inline style), so the CSP is untouched. `node scripts/check-roadmap.mjs` checks it (CI).

## Product images

**Since Phase 4 (2026-11-21)** most product images come from the release's LaunchFrame source captures (`store-assets/release-<version>/source-screenshots`, emulators: `Reflux_Phone`, `Reflux_Tablet`, `Reflux_Tablet7`, the Google TV emulator), made by `python -I website/scripts/make-showcase.py <source-screenshots>` (system bars cropped, scaled, WebP, sizes into `src/showcase.json`); alt text names a TV, not Fire TV, for Google TV emulator captures. The desktop captures (`home-desktop`, `details-desktop`) are still Beta 1's from Windows. Before that: the pictures of Reflux on the site were captures of the real app on real devices (since 2026-11-16: a Nothing Phone 2a, a Xiaomi Pad 7, a Fire TV Stick 4K Plus, the Google TV emulator, and Windows), never mock-ups. Their library is the Blender Foundation's open films, served by a throwaway local Jellyfin with its internet metadata off: each film's video is made from one of its own Creative Commons stills, its NFO holds Reflux's own one-line description, and its poster and backdrop are the Creative Commons files credited on `/legal/third-party#artwork` (from `src/showcase.json`, each with its author, license, and license link). The debug build is built with no Trakt Client ID, so no provider artwork or text appears. No commercial title, personal library, server address, or profile name is shown. Desktop captures copy only Reflux's own window (Windows `PrintWindow`), never the screen; `PrintWindow` returns a few pixels of the window fully transparent (1,537 of 1.7 million on Home, 1 on Sintel's page), and only those are filled from their neighbours, as the screen shows them. Phone and tablet captures have their system bars (status and gesture bars) cropped off, so no notification icon appears; the phone's are cropped to Google Play's 2:1. Every capture was looked at for personal data before use. `src/showcase.json` records each image's size; `public/showcase/*.webp` are the files. The offscreen renderer (`WebsiteShowcaseTest`) remains for a quick layout preview but no longer supplies the site's images.

## Release data

`website/scripts/release-data.mjs` runs before every build and writes `src/generated/release.json` (not committed) from the repository:

| Source | Used for |
| --- | --- |
| `gradle.properties` (`reflux.version`, `reflux.android.applicationId`) | "Reflux 0.9.0-alpha.2, in development"; the store records are checked against the Android identity |
| `CHANGELOG.md` | The Releases page |
| `release/published/*.json` (manifests from `tools/release/release.py`) | Download buttons, sizes, SHA-256, commit, `SHA256SUMS` link, the Windows libmpv source bundle: each at `https://github.com/Rifaque/reflux/releases/download/v<version>/<name>` |
| `release/stores.json` | Google Play and Amazon Appstore links once listings exist |
| `legal/components.txt`, `NOTICE`, `LICENSE`, the website's lockfile | Third-party notices, License (`/license`; `/terms` redirects there permanently) |
| `design/brand/reflux-mark.svg`, `reflux-lockup.svg` | The mark and lockup, inline |

No version, address, or checksum is typed into a page; the site is not a second registry. The rules live in `scripts/release-lib.mjs` (pure, tested by `npm test`, which runs `node --test` over `scripts/*.test.mjs`) and the build fails on any violation:

- **`release/stores.json`**, one record per store (`googlePlay`, `amazonAppstore`): `package` equals `reflux.android.applicationId`; `state` is `not listed`, `testing`, or `available`; `url` is `null` when `not listed`, and may be `null` for `testing` when a tester route exists (a closed or invitation-only test with no public listing; 2026-11-17), otherwise an https address on the store's domain (`play.google.com`, `amazon.com`; a Play address names the package); `testing` is `null` or `{ "kind": "closed-test-opt-in" | "live-app-testing", "url"?: https on the store's domain, "instructions"?: "/testing" }`, the tester route, shown on the Testing page and never as a download. Only an `available` store is linked from the availability table and the Download page.
- **`release/published/<version>.json`**, a byte-identical copy of the build's `manifest.json`, written by `release.py publish-record`: the file is named for its `version`; `tag` is `v<version>`; `repository` is the project's; `commit` is a full SHA; every artifact is named `reflux-<version>-...` and has a size and a SHA-256; the version is not newer than `reflux.version`; `CHANGELOG.md` has a `## <version>` section (the release notes); the newest build is the highest version. Each artifact's `distribution` is `github-release` (zip, tar.gz, libmpv source: given a download address) or `store` (apk, aab: given none); a missing field means `store` for Android and `github-release` otherwise, and an APK or AAB marked `github-release` fails the build. This is the stale-record check: a record that disagrees with itself, its tag, or the repository stops the build rather than reach a page. `check-dist.mjs` then reads `download.html` against the data: the newest record's version is presented, every GitHub archive has its link and SHA-256, no store package is linked, `SHA256SUMS` is linked, a store is linked only when `available`, and with nothing published no release download link exists.

**Download routing:** Android and Google TV → Google Play; Fire TV → Amazon Appstore; Windows and Linux → the official GitHub release (the zip or tar.gz, the libmpv source beside the Windows zip, `SHA256SUMS`, `manifest.json`, `provenance.intoto.json`). Android packages are never offered from the site. The Download page opens with one availability table (platform, channel, state, link) built from both sources; a desktop platform is `available` only when a record has its archive, otherwise it says "No public build yet". With nothing published, each section says so.

**Tests of the data step** set `RELEASE_PUBLISHED_DIR` (another folder of records) and `RELEASE_DATA_OUT` (another output file); they are for the tests only and are never set in a deployed build (the project has no environment variables).

## Documentation and Download (2026-11-19)

- **Documentation** (`/docs`, DECISIONS.md, 2026-11-19): the pages are listed once in `src/docs/pages.js` (groups, order, titles, descriptions) and written in `src/docs/content.jsx`; routes, prerendering, the sidebar, search, breadcrumbs, previous and next all come from that list. Blocks in `src/docs/blocks.jsx`: a command with a copy button, a callout (tip, note, warning), and tabs with a link per tab (`#linux-fedora`). Headings get anchors; a page with more than two gets a table of contents. Search runs in the page over the pages' own text: no index service. A command or a distribution is written only with where it was checked (VERIFICATION.md).
- **Download** is a card per platform: its state (from the release data and `release/stores.json`), what it needs, its one action, and its install guide. Windows and Linux link to the GitHub release's files with size, SHA-256, and `SHA256SUMS`; store platforms show the store or the beta, never a file. *Pending (2026-11-25):* when a release carries B-102 (Reflux adding its own desktop entry on Linux, on `cachyos-final-audit`), the Linux install guide (`src/docs/content.jsx`) should say so; the released Beta 2 does not, so the site is unchanged.
- **The GPL's text** is written to `public/license.txt` from `LICENSE` at build time (`scripts/release-data.mjs`; ignored by Git), so the License page links to it rather than carrying 36 KB in the script.

## Search (M8)

The site is built to be read correctly by search engines and to be useful to whoever arrives from one. Nothing here promises a ranking.

- **Titles:** `Reflux | Your media, played well` for the home page, `Page | Reflux` for every other (DECISIONS.md, 2026-11-11), from one function (`titleOf` in `src/routes.js`) that the prerenderer and the browser both use. Never an em dash: `check-dist` fails on one.
- **Descriptions:** one per page in `src/routes.js`, 40 to 170 characters, held by `check-dist`.
- **Canonical addresses, Open Graph, structured data:** each prerendered page names its canonical address, its `og:url`, the sharing image and its alt text, and JSON-LD (`src/seo.js`): RSX Develops as the Organization, the WebSite, Reflux as a SoftwareApplication (multimedia, its platforms, free) on the home page, and a BreadcrumbList on pages beneath it. It claims nothing the site cannot back up: no rating, review, or download count, which Google requires for a software rich result and Reflux does not have; `check-dist` fails on any.
- **Sitemap and robots:** every route in `sitemap.xml`, with `<lastmod>` from the newest commit touching what the page is made of (in a shallow clone, as CI and Vercel make, the boundary commit is never taken for a change, and a date the history cannot show is left out rather than guessed); `robots.txt` allows everything and names the sitemap; the not-found page is `noindex` and served with status 404.
- **One address:** any host other than reflux.rsxdevelops.dev (a `*.vercel.app` alias) answers with `X-Robots-Tag: noindex` (`vercel.json`), so only the canonical host is indexed.
- **Structure:** one heading 1 per page and no skipped heading level; alt text on every image; every documentation page reached from the docs index, the docs navigation, and the footer (`check-dist`, `check-links`).
- **Search Console** (owner): verify the domain with its DNS TXT record at Cloudflare, submit `https://reflux.rsxdevelops.dev/sitemap.xml`, and inspect the key pages' URLs. Not done from the repository (RELEASE.md, owner actions).

## Responsive (M8)

`node scripts/check-layout.mjs [--screenshots]` serves `dist/` as Vercel does and opens every page in Chromium (Playwright, a development dependency only, pinned; never shipped and not in the site's notices) at seven sizes: a 2560 × 1440 display, a 1440 desktop, a 1280 laptop, an 820 tablet and a 1180 one on its side, a 390 phone upright and an 844 phone on its side. Each page must not scroll sideways, keep its text at 12 px or more, keep every control that is not inline text at 24 × 24 px or more, show its heading and a way to the rest of the site, and stay within the performance budget (layout shift under 0.05, largest contentful paint under 2.5 s, and under 1.6 MB on a desktop; lab numbers, since a new site has no field data). With `--screenshots` it writes every page at every size to `test-results/layout/` (not committed): the set reviewed for M8, and compared against when M11 replaces the content and M12 qualifies the site. CI runs it and keeps the screenshots as an artifact for 14 days.

## Checks

`npm audit --audit-level=moderate`, `npm test`, `npm run build`, `node scripts/check-dist.mjs`, `node --max-http-header-size=65536 scripts/check-links.mjs` (trakt.tv answers with about 32 KB of headers, over Node's default), `node scripts/check-layout.mjs --screenshots`, `node scripts/check-roadmap.mjs` (CI job "Website"):

- `check-dist`: every route prerendered with its title in the site's form and no em dash, a description of a readable length, its canonical address and `og:url`, structured data that parses and claims no rating or review, one heading 1 and no skipped heading level, alt text on every image, its place in the sitemap, and its content in the HTML; the not-found page `noindex`; `robots.txt` naming the sitemap; a `404.html`; no source maps; no development or private address (localhost, LAN ranges, `*.vercel.app`), plain-HTTP address, credential-shaped string, build secret name, or environment-variable read in any shipped file; no inline script or `style` attribute the CSP would refuse.
- `check-links`: every internal link and `#anchor` reaches a page or a file; every external link is HTTPS and answers. A link that gives no answer or a 5xx is asked up to three times (20 s each) and the cause is printed; a 4xx, or no answer after the last attempt, fails. **Not asked, by the owner's decision (B-098, 2026-11-25):** `gnu.org` and `www.gnu.org` only, because GitHub-hosted runners cannot connect to them (ETIMEDOUT on every attempt, on fresh runners too) while they answer from the owner's machine; the link stays on the site, and the GPL's full text is served by the site itself at `/license`. The check prints each link it does not ask. Every other external link is checked.
- `node scripts/serve.mjs` serves `dist/` as Vercel does (clean URLs, `404.html` with status 404, every header in `vercel.json`), so the production build and its CSP are tested locally in a browser.

## Deployment

| Environment | How | Who can see it |
| --- | --- | --- |
| Preview | Every push to a branch other than `main`, by Vercel's GitHub integration | Vercel team members only (Vercel Authentication on every deployment except custom domains; on in the project) |
| Production | `main`, at reflux.rsxdevelops.dev | Everyone |

Project settings: root directory `website`, "include files outside the root directory" on (the build reads `gradle.properties`, `CHANGELOG.md`, `release/`, `legal/`, `design/brand/`), framework preset "Other", install `npm ci`, build `npm run build`, output `dist`, **Node.js 22.x** (the project currently says 26.x; set it to 22.x, which CI uses). No environment variables: the build has none to read. `website/vercel.json` holds only serving rules: clean URLs, no trailing slashes, headers, and caching.

**DNS, as Vercel states it (read 2026-11-08 from the project's domain configuration):** rsxdevelops.dev is served by Cloudflare (nameservers `norm.ns.cloudflare.com`, `arushi.ns.cloudflare.com`). Vercel's recommended record for `reflux.rsxdevelops.dev` is **`CNAME reflux → 5abf2611cfb13f8d.vercel-dns-017.com`** (rank 1; the generic `cname.vercel-dns.com` is its second choice), **DNS only, not proxied**, so that Vercel issues and renews the certificate. The domain is not yet added to the project and no record exists ("misconfigured" until both are done).

**State:** live at reflux.rsxdevelops.dev since M7's closure (the owner's actions below were done). Kept for the record: The owner's actions, in order:

1. Push the branch, and connect the GitHub repository to the `reflux-website` project (Vercel → Project → Settings → Git), with the settings above; or, without the Git integration, `npx vercel login`, then in `website/` `npx vercel link` (choose `reflux-website`) and `npx vercel deploy --prod`.
2. Add `reflux.rsxdevelops.dev` to the project (Settings → Domains), and at Cloudflare add the CNAME above, DNS only.
3. Check `https://reflux.rsxdevelops.dev/` answers with the headers below, and `/no-such-page` with 404.

## Security

- `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; upgrade-insecure-requests`, HSTS (two years, subdomains), `nosniff`, `Referrer-Policy: no-referrer`, a restrictive `Permissions-Policy`, `COOP: same-origin`, `X-Frame-Options: DENY`. Verified in a browser through `serve.mjs` on every route with no violation (VERIFICATION.md, M7); the one it found (an inlined `data:` font) was fixed by turning inlining off.
- No source maps, no environment variables or secrets, no third-party origin: fonts, images, and scripts are the site's own.
- External links are HTTPS with `rel="noopener noreferrer"`.
- No analytics, cookies, or third-party requests (ROADMAP.md, "The Reflux website", Privacy).

## Ownership

The domain rsxdevelops.dev (registrar and Cloudflare DNS), the Vercel team, and the GitHub repository belong to RSX Develops' owner. Deploy access is through the Vercel GitHub integration; no deploy token is kept in the repository.
