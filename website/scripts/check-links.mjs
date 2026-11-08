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
if (!process.argv.includes('--offline')) {
  for (const link of external) {
    try {
      const r = await fetch(link, { redirect: 'follow', headers: { 'User-Agent': 'reflux-website-link-check' } })
      if (r.status >= 400 && r.status !== 429) { console.log(r.status, link); failed++ }
    } catch { console.log('unreachable', link); failed++ }
  }
}
console.log(`${Object.keys(html).length} pages, ${external.size} external links checked, ${failed} broken`)
process.exit(failed ? 1 : 0)
