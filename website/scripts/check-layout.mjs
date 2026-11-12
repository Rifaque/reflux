// Checks the built site at the sizes people read it on (docs/WEBSITE.md, "Responsive"): every page at a large display,
// a desktop, a laptop, a tablet, a phone upright and on its side. A page must never scroll sideways, keep its text at a
// readable size, keep what can be tapped big enough to tap, show its heading and navigation, and stay within the
// performance budget (layout shift, largest paint, and bytes). It serves dist/ as Vercel does (scripts/serve.mjs's
// rules), drives Chromium through Playwright, and writes a screenshot of every page at every size to
// test-results/layout/ for review, the set M11 and M12 compare the final content against. Run after npm run build:
// node scripts/check-layout.mjs [--screenshots]
import { createServer } from 'node:http'
import { readFileSync, existsSync, statSync, mkdirSync } from 'node:fs'
import { join, extname, dirname } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { chromium } from 'playwright'

const site = join(dirname(fileURLToPath(import.meta.url)), '..')
const dist = join(site, 'dist')
const shots = join(site, 'test-results', 'layout')
const screenshots = process.argv.includes('--screenshots')
const { routes } = await import(pathToFileURL(join(site, 'src', 'routes.js')).href)

export const SIZES = [
  { name: 'large-display', width: 2560, height: 1440 },
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'laptop', width: 1280, height: 720 },
  { name: 'tablet', width: 820, height: 1180, touch: true },
  { name: 'tablet-landscape', width: 1180, height: 820, touch: true },
  { name: 'phone', width: 390, height: 844, touch: true },
  { name: 'phone-landscape', width: 844, height: 390, touch: true },
]

/** The budget every page keeps (lab numbers: a new site has no field data yet). */
const BUDGET = { layoutShift: 0.05, largestPaintMs: 2500, bytes: 1_600_000 }

const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.webp': 'image/webp', '.woff2': 'font/woff2', '.txt': 'text/plain', '.xml': 'application/xml', '.webmanifest': 'application/manifest+json' }
const config = JSON.parse(readFileSync(join(site, 'vercel.json'), 'utf8'))
const server = createServer((req, res) => {
  const path = decodeURIComponent(new URL(req.url, 'http://x').pathname)
  const candidates = path === '/' ? ['index.html'] : [path.slice(1), path.slice(1) + '.html']
  const found = candidates.map((c) => join(dist, c)).find((f) => f.startsWith(dist) && existsSync(f) && statSync(f).isFile())
  const file = found ?? join(dist, '404.html')
  const headers = { 'Content-Type': types[extname(file)] ?? 'application/octet-stream' }
  for (const rule of config.headers) if (!rule.missing && new RegExp('^' + rule.source.replace(/\(\.\*\)/g, '(.*)') + '$').test(path)) for (const h of rule.headers) headers[h.key] = h.value
  res.writeHead(found ? 200 : 404, headers)
  res.end(readFileSync(file))
}).listen(0, '127.0.0.1')
await new Promise((ready) => server.once('listening', ready))
const base = `http://127.0.0.1:${server.address().port}`

const browser = await chromium.launch()
const problems = []
if (screenshots) mkdirSync(shots, { recursive: true })
for (const size of SIZES) {
  const context = await browser.newContext({ viewport: { width: size.width, height: size.height }, hasTouch: !!size.touch, isMobile: !!size.touch && size.width < 900, reducedMotion: 'reduce' })
  for (const route of routes) {
    const page = await context.newPage()
    let bytes = 0
    page.on('response', async (response) => { try { bytes += (await response.body()).length } catch { /* redirects have no body */ } })
    await page.addInitScript(() => {
      window.__shift = 0
      window.__paint = 0
      new PerformanceObserver((list) => { for (const e of list.getEntries()) if (!e.hadRecentInput) window.__shift += e.value }).observe({ type: 'layout-shift', buffered: true })
      new PerformanceObserver((list) => { for (const e of list.getEntries()) window.__paint = Math.max(window.__paint, e.startTime) }).observe({ type: 'largest-contentful-paint', buffered: true })
    })
    await page.goto(base + route.path, { waitUntil: 'networkidle' })
    await page.waitForTimeout(300)
    const where = `${route.path} at ${size.name} (${size.width}×${size.height})`
    const facts = await page.evaluate(() => {
      const root = document.documentElement
      const visible = (el) => { const r = el.getBoundingClientRect(); const s = getComputedStyle(el); return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none' }
      // What spills past the right edge: the page itself, and the elements that do it.
      const overflowing = [...document.querySelectorAll('body *')].filter((el) => visible(el) && el.getBoundingClientRect().right > root.clientWidth + 1 && !el.closest('[data-scroll-x]') && getComputedStyle(el).position !== 'fixed')
        .slice(0, 3).map((el) => `${el.tagName.toLowerCase()}.${[...el.classList].join('.')}`)
      const text = [...document.querySelectorAll('main p, main li, main a, footer a, footer p, header a')].filter(visible).map((el) => parseFloat(getComputedStyle(el).fontSize))
      const targets = [...document.querySelectorAll('a, button, summary')].filter(visible).map((el) => { const r = el.getBoundingClientRect(); return { h: r.height, w: r.width, inline: getComputedStyle(el).display === 'inline' && !!el.closest('p, li, dd, td, small'), name: `${el.tagName.toLowerCase()}${[...el.classList].map((c) => '.' + c).join('')} "${(el.textContent || el.getAttribute('aria-label') || '').trim().slice(0, 24)}" ${Math.round(r.width)}×${Math.round(r.height)}` } })
      return {
        scrollsSideways: root.scrollWidth > root.clientWidth + 1,
        overflowing,
        smallestText: Math.min(...text),
        smallTargets: targets.filter((t) => !t.inline && (t.h < 24 || t.w < 24)).map((t) => t.name),
        heading: [...document.querySelectorAll('h1')].some(visible),
        navigation: [...document.querySelectorAll('header nav, .nav-menu summary')].some(visible),
        shift: window.__shift,
        paint: window.__paint,
      }
    })
    if (facts.scrollsSideways) problems.push(`${where}: scrolls sideways (${facts.overflowing.join(', ')})`)
    if (facts.smallestText < 12) problems.push(`${where}: text at ${facts.smallestText}px`)
    if (facts.smallTargets.length > 0) problems.push(`${where}: controls smaller than 24 × 24 px: ${facts.smallTargets.slice(0, 4).join('; ')}`)
    if (!facts.heading) problems.push(`${where}: its heading is not shown`)
    if (!facts.navigation) problems.push(`${where}: no way to the rest of the site is shown`)
    if (facts.shift > BUDGET.layoutShift) problems.push(`${where}: layout shift ${facts.shift.toFixed(3)} (budget ${BUDGET.layoutShift})`)
    if (facts.paint > BUDGET.largestPaintMs) problems.push(`${where}: largest paint at ${Math.round(facts.paint)} ms (budget ${BUDGET.largestPaintMs})`)
    if (size.name === 'desktop' && bytes > BUDGET.bytes) problems.push(`${where}: ${bytes} bytes (budget ${BUDGET.bytes})`)
    if (screenshots) await page.screenshot({ path: join(shots, `${(route.path.slice(1) || 'home').replaceAll('/', '-')}-${size.name}.png`), fullPage: true })
    await page.close()
  }
  await context.close()
}
await browser.close()
server.close()
console.log(`${routes.length} pages at ${SIZES.length} sizes checked, ${problems.length} problems`)
problems.forEach((p) => console.log('  ' + p))
process.exit(problems.length ? 1 : 0)
