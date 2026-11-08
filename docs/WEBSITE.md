# Reflux Website

reflux.rsxdevelops.dev: Reflux's public home (LAUNCH.md for its stages and visual direction, ROADMAP.md for when each is due). This document is how it is built, checked, and deployed. Its M7 redesign (Website 2.0) replaced the M6 foundation's presentation; the foundation's routing, release data, and security model are kept.

## Architecture

- **React 19 + Vite, prerendered, static.** `npm run build` runs `scripts/release-data.mjs`, the client build, a server-side build of `src/entry-server.jsx`, and `scripts/prerender.mjs`, which writes every page in `src/routes.js` as its own HTML file (`index.html`, `download.html`, `docs/playback.html`, …) with its title, description, canonical address, and sharing card, plus `404.html`, `sitemap.xml`, and `robots.txt`. Every page is readable without JavaScript; the script then takes the page over for client-side navigation. No server code, backend, forms, or database.
- **Routes** (`src/routes.js`, the one list the app, the prerenderer, and the checks read): Home, Platforms and compatibility, Sources, Download, Testing (the tester hub), Documentation (Getting started, Adding sources, How playback works, Your library, Troubleshooting and FAQ), Releases, Support, Privacy, Terms, Third-party notices (with the artwork credits), Security, and a not-found page.
- **Visual system** (`src/styles.css`): the product's own rules (DESIGN.md, LAUNCH.md "Visual direction"): near-black, one typeface (Manrope, self-hosted from `@fontsource-variable/manrope`, the brand's wordmark face), hairlines and quiet surfaces, glass only on the sticky header, colour only from real product images, motion only on the hero's entrance and nothing with reduced motion. The brand (design/brand) is drawn inline from the master geometry.
- **Hosting: Vercel** (DECISIONS.md, 2026-11-08), team "rifaque's projects", project `reflux-website`.

## Product images

The pictures of Reflux on the site are the real experience layer rendered offscreen (`ui/src/jvmTest/.../WebsiteShowcaseTest.kt`) over a library of the Blender Foundation's open films, with their Creative Commons posters and stills (credited on `/legal/third-party#artwork`, from `src/showcase.json`). No commercial title, poster, or mock-up appears. To regenerate: put the artwork in `<dir>/art` (the files and sources listed in `src/showcase.json`), run `./gradlew :ui:jvmTest --tests '*WebsiteShowcaseTest*' -Preflux.showcase=<dir>`, and convert `<dir>/out/*.png` to WebP in `public/showcase/` at the widths in `src/showcase.json`.

## Release data

`website/scripts/release-data.mjs` runs before every build and writes `src/generated/release.json` (not committed) from the repository:

| Source | Used for |
| --- | --- |
| `gradle.properties` (`reflux.version`, `reflux.android.applicationId`) | "Reflux 0.9.0-alpha.2, in development"; the store records are checked against the Android identity |
| `CHANGELOG.md` | The Releases page |
| `release/published/*.json` (manifests from `tools/release/release.py`) | Download buttons, sizes, SHA-256, commit, `SHA256SUMS` link, the Windows libmpv source bundle: each at `https://github.com/Rifaque/reflux/releases/download/v<version>/<name>` |
| `release/stores.json` | Google Play and Amazon Appstore links once listings exist |
| `legal/components.txt`, `LICENSE`, the website's lockfile | Third-party notices, Terms |
| `design/brand/reflux-mark.svg`, `reflux-lockup.svg` | The mark and lockup, inline |

No version, address, or checksum is typed into a page. The build fails rather than publish a manifest without a commit or a checksum, or a store record for another package. **Download routing:** Android and Google TV → Google Play; Fire TV → Amazon Appstore; Windows and Linux → the official GitHub release. Android packages are never offered from the site (`check-dist.mjs` fails on an `.apk` or `.aab` link). With nothing published, each section says so.

## Checks

`npm audit --audit-level=moderate`, `npm run build`, `node scripts/check-dist.mjs`, `node scripts/check-links.mjs` (CI job "Website"):

- `check-dist`: every route prerendered with a title, a canonical address, and its content in the HTML; a `404.html`; no source maps; no development or private address (localhost, LAN ranges, `*.vercel.app`), plain-HTTP address, credential-shaped string, build secret name, or environment-variable read in any shipped file; no inline script or `style` attribute the CSP would refuse.
- `check-links`: every internal link and `#anchor` reaches a page or a file; every external link is HTTPS and answers.
- `node scripts/serve.mjs` serves `dist/` as Vercel does (clean URLs, `404.html` with status 404, every header in `vercel.json`), so the production build and its CSP are tested locally in a browser.

## Deployment

| Environment | How | Who can see it |
| --- | --- | --- |
| Preview | Every push to a branch other than `main`, by Vercel's GitHub integration | Vercel team members only (Vercel Authentication on every deployment except custom domains; on in the project) |
| Production | `main`, at reflux.rsxdevelops.dev | Everyone |

Project settings: root directory `website`, "include files outside the root directory" on (the build reads `gradle.properties`, `CHANGELOG.md`, `release/`, `legal/`, `design/brand/`), framework preset "Other", install `npm ci`, build `npm run build`, output `dist`, **Node.js 22.x** (the project currently says 26.x; set it to 22.x, which CI uses). No environment variables: the build has none to read. `website/vercel.json` holds only serving rules: clean URLs, no trailing slashes, headers, and caching.

**DNS, as Vercel states it (read 2026-11-08 from the project's domain configuration):** rsxdevelops.dev is served by Cloudflare (nameservers `norm.ns.cloudflare.com`, `arushi.ns.cloudflare.com`). Vercel's recommended record for `reflux.rsxdevelops.dev` is **`CNAME reflux → 5abf2611cfb13f8d.vercel-dns-017.com`** (rank 1; the generic `cname.vercel-dns.com` is its second choice), **DNS only, not proxied**, so that Vercel issues and renews the certificate. The domain is not yet added to the project and no record exists ("misconfigured" until both are done).

**State (M7):** the site is built, checked, and ready; nothing is deployed. The owner's actions, in order:

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
