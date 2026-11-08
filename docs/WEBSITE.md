# Reflux Website

reflux.rsxdevelops.dev: Reflux's public home (LAUNCH.md for its stages and visual direction, ROADMAP.md for when each is due). This document is how it is built and run.

## Architecture

- **React 19 + Vite, static.** `website/` builds to `website/dist`: one `index.html`, one script, one stylesheet, the icon, and `third-party-licenses.txt`. Client-side routes (React Router). No server code, no backend, no forms, no database.
- **Hosting: Vercel** (decided 2026-11-08, DECISIONS.md), where RSX Develops already hosts its sites (team "rifaque's projects"), project `reflux-website`. Static files on Vercel's CDN with HTTPS.
- **Pages (M6 foundation):** Home, Download, Releases, Docs (structure only), Support, Testing (structure only), Privacy, Terms, Third-party notices, Security, and a not-found page. M7 fills the product pages, the tester hub, and the documentation.

## Release data

`website/scripts/release-data.mjs` runs before every build and writes `src/generated/release.json` (not committed) from the repository:

| Source | Used for |
| --- | --- |
| `gradle.properties` (`reflux.version`) | "Reflux 0.9.0-alpha.2, in development" |
| `CHANGELOG.md` | The Releases page |
| `release/published/*.json` (manifests from `tools/release/release.py`) | Download buttons, sizes, SHA-256, commit, `SHA256SUMS` link: each artifact at `https://github.com/Rifaque/reflux/releases/download/v<version>/<name>` |
| `release/stores.json` | Google Play and Amazon Appstore links once listings exist |
| `legal/components.txt`, `LICENSE`, the website's lockfile | Third-party notices, Terms |

No version, address, or checksum is typed into a page. The build fails rather than publish a manifest without a commit or a checksum. **Download routing:** Android and Google TV → Google Play; Fire TV → Amazon Appstore; Windows and Linux → the official GitHub release. Android artifacts are never offered from the site. With nothing published, each section says so (VERIFICATION.md, M6: the routing was also checked with the dry run's manifest staged).

## Deployment

| Environment | How | Who can see it |
| --- | --- | --- |
| Preview | Every push to a branch other than `main`, by Vercel's GitHub integration | Vercel team members only (Vercel Authentication on every deployment except custom domains) |
| Production | `main`, at reflux.rsxdevelops.dev | Everyone |

Project settings for the Git integration: root directory `website`, "include files outside the root directory" on (the build reads `gradle.properties`, `CHANGELOG.md`, `release/`, `legal/`), build `npm run build`, install `npm ci`, output `dist`, Node 22. `website/vercel.json` holds only serving rules: clean URLs, the single-page fallback, and headers.

**State (2026-11-08):** the project exists; nothing is deployed. Deploying needs either the repository pushed and connected to the project (the planned path) or a Vercel CLI login on this machine (`npx vercel login`; then in `website/`: `npm run build`, `cp vercel.json dist/`, `npx vercel deploy dist` for a preview and `--prod` for production, choosing the `reflux-website` project when asked), both the owner's actions. The domain needs one DNS record at Cloudflare, which serves rsxdevelops.dev: `reflux CNAME cname.vercel-dns.com` (DNS only, not proxied), then the domain added to the project; Vercel issues its certificate.

## Security

- `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; upgrade-insecure-requests`, HSTS (two years, subdomains), `nosniff`, `Referrer-Policy: no-referrer`, a restrictive `Permissions-Policy`, `COOP: same-origin`, `X-Frame-Options: DENY`.
- No source maps in production. No environment variables or secrets: the build has none to leak.
- External links are HTTPS with `rel="noopener noreferrer"`.
- No analytics, cookies, or third-party requests by default (ROADMAP.md, "The Reflux website", Privacy).
- Checks (CI job "Website"): `npm audit --audit-level=moderate` (0 vulnerabilities), the build, and `scripts/check-links.mjs` (every internal link is a route or a file in `public/`, every external link answers).

## Ownership

The domain rsxdevelops.dev (registrar and Cloudflare DNS), the Vercel team, and the GitHub repository belong to RSX Develops' owner. Deploy access is through the Vercel GitHub integration; no deploy token is kept in the repository.
