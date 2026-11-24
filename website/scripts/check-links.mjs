// Checks every link on the built pages (docs/WEBSITE.md, "Checks"): internal links must reach a page or a file of
// the site (and an #anchor an element on it), external links must be HTTPS and answer. Run after npm run build:
// node scripts/check-links.mjs [--offline]
import { readFileSync, existsSync, readdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const dist = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist')
const pages = []
const walk = (d, base = '') => readdirSync(d, { withFileTypes: true }).forEach((e) => e.isDirectory() ? walk(join(d, e.name), base + '/' + e.name) : e.name.endsWith('.html') && pages.push([base + '/' + e.name, join(d, e.name)]))
walk(dist)
const route = (file) => file === '/index.html' ? '/' : file.replace(/\.html$/, '')
const html = Object.fromEntries(pages.map(([f, p]) => [route(f), readFileSync(p, 'utf8')]))
const exists = (path) => path in html || (existsSync(join(dist, path)) && !path.endsWith('/'))
let failed = 0
const external = new Set()
for (const [page, s] of Object.entries(html)) {
  for (const [, href] of s.matchAll(/(?:href|src)="([^"]+)"/g)) {
    if (/^https?:/.test(href)) { if (!href.startsWith('https://')) { console.log(page, 'not https', href); failed++ } else if (!href.startsWith('https://reflux.rsxdevelops.dev')) external.add(href); continue }
    if (!href.startsWith('/') && !href.startsWith('#')) continue
    const [path, anchor] = href.split('#')
    const target = path || page
    if (!exists(target)) { console.log(page, 'broken', href); failed++; continue }
    if (anchor && html[target] && !html[target].includes(`id="${anchor}"`)) { console.log(page, 'no anchor', href); failed++ }
  }
}
// A link is asked up to three times, but only when the failure is transient: no answer at all (a reset or refused
// connection, DNS, TLS, a timeout) or a server error (5xx). Some hosts drop connections from shared CI addresses now and
// then (gnu.org did, intermittently, on GitHub's runners in 2026-11, while answering 200 elsewhere). A 4xx, or no answer
// after the last attempt, fails as before; the error itself is printed, so a failure says why.
const ATTEMPTS = 3
const ask = (link) => fetch(link, { redirect: 'follow', headers: { 'User-Agent': 'reflux-website-link-check' }, signal: AbortSignal.timeout(20_000) })
const why = (e) => [e?.name, e?.message, e?.cause?.code ?? e?.cause?.message].filter(Boolean).join(': ')
if (!process.argv.includes('--offline')) {
  for (const link of external) {
    for (let attempt = 1; ; attempt++) {
      try {
        const r = await ask(link)
        if (r.status >= 500 && attempt < ATTEMPTS) { console.log(`retrying ${link} after ${r.status} (attempt ${attempt})`); await new Promise((ok) => setTimeout(ok, attempt * 3_000)); continue }
        if (r.status >= 400 && r.status !== 429) { console.log(r.status, link); failed++ }
        break
      } catch (e) {
        if (attempt < ATTEMPTS) { console.log(`retrying ${link} after ${why(e)} (attempt ${attempt})`); await new Promise((ok) => setTimeout(ok, attempt * 3_000)); continue }
        console.log('unreachable', link, why(e)); failed++
        break
      }
    }
  }
}
console.log(`${Object.keys(html).length} pages, ${external.size} external links checked, ${failed} broken`)
process.exit(failed ? 1 : 0)
