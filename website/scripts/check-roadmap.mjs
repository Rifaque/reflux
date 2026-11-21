// Checks the roadmap page and its easter egg (the reel) in Chromium against the production build, served with the
// site's real headers and content security policy (scripts/serve.mjs): the stages are readable without script; with
// script, the hidden mark opens the reel as a modal dialog, arrows move between versions, Escape closes it and focus
// returns; with reduced motion it works the same; the page logs no error and no CSP violation.
//   node scripts/check-roadmap.mjs          (after npm run build)
import { spawn } from 'node:child_process'
import { join, dirname } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { chromium } from 'playwright'

const site = join(dirname(fileURLToPath(import.meta.url)), '..')
const { STAGES } = await import(pathToFileURL(join(site, 'src', 'roadmap.js')).href)
const port = 4199
const server = spawn(process.execPath, [join(site, 'scripts', 'serve.mjs'), String(port)], { stdio: ['ignore', 'pipe', 'inherit'] })
await new Promise((ready) => server.stdout.once('data', ready))
const url = `http://127.0.0.1:${port}/roadmap`
const problems = []
const check = (ok, what) => { if (!ok) problems.push(what) }
const browser = await chromium.launch()

try {
  // Without script: every stage is in the HTML and the egg is not offered (it would do nothing).
  {
    const context = await browser.newContext({ javaScriptEnabled: false })
    const page = await context.newPage()
    await page.goto(url)
    check(await page.locator('.stage').count() === STAGES.length, 'without script: every stage is on the page')
    check(!(await page.locator('.egg').isVisible()), 'without script: the egg is hidden')
    await context.close()
  }
  for (const reducedMotion of ['no-preference', 'reduce']) {
    const context = await browser.newContext({ reducedMotion, viewport: { width: 1280, height: 800 } })
    const page = await context.newPage()
    const errors = []
    page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
    page.on('pageerror', (e) => errors.push(String(e)))
    await page.goto(url)
    const egg = page.locator('button.egg')
    await egg.scrollIntoViewIfNeeded()
    check(await egg.isVisible(), `${reducedMotion}: the egg is shown once the script runs`)
    await egg.focus()
    await page.keyboard.press('Enter')
    const reel = page.locator('dialog.reel')
    check(await reel.evaluate((d) => d.open), `${reducedMotion}: Enter on the egg opens the reel`)
    const current = STAGES.findIndex((s) => s.status === 'current')
    const slider = page.locator('.reel-track')
    check(await slider.getAttribute('aria-valuenow') === String(current + 1), `${reducedMotion}: the reel starts at the current stage`)
    await slider.focus()
    await page.keyboard.press('ArrowRight')
    check(await slider.getAttribute('aria-valuenow') === String(current + 2), `${reducedMotion}: Right moves to the next version`)
    check((await page.locator('.reel-version').innerText()) === STAGES[current + 1].version, `${reducedMotion}: the frame shows the next version`)
    await page.keyboard.press('End')
    check(await slider.getAttribute('aria-valuenow') === String(STAGES.length), `${reducedMotion}: End goes to the last version`)
    await page.keyboard.press('Escape')
    check(!(await reel.evaluate((d) => d.open)), `${reducedMotion}: Escape closes the reel`)
    check(await page.evaluate(() => document.activeElement?.classList.contains('egg')), `${reducedMotion}: focus returns to the egg`)
    check(errors.length === 0, `${reducedMotion}: no console error or CSP violation (${errors.join(' | ')})`)
    await context.close()
  }
} finally {
  await browser.close()
  server.kill()
}

for (const p of problems) console.log('PROBLEM', p)
console.log(`roadmap and reel: ${problems.length} problems`)
process.exit(problems.length ? 1 : 0)
