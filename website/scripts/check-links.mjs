// Checks every link on the built site (docs/WEBSITE.md, "Checks"): internal routes must be routes the app defines,
// external links must answer. Run after `npm run build`: node scripts/check-links.mjs [--offline]
import { readFileSync, readdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const src = join(dirname(fileURLToPath(import.meta.url)), '..', 'src')
const files = []
const walk = (d) => readdirSync(d, { withFileTypes: true }).forEach((e) => e.isDirectory() ? walk(join(d, e.name)) : /\.jsx?$/.test(e.name) && files.push(join(d, e.name)))
walk(src)
const code = files.map((f) => readFileSync(f, 'utf8')).join('\n')
const routes = new Set([...code.matchAll(/<Route path="([^"*]+)"/g)].map((m) => m[1]))
const internal = new Set([...code.matchAll(/(?:to|href)="(\/[^"#]*)/g)].map((m) => m[1]))
const external = new Set([...code.matchAll(/href="(https:\/\/[^"]+)"/g)].map((m) => m[1]))
let failed = 0
const statics = new Set(readdirSync(join(src, '..', 'public')).map((f) => '/' + f))
for (const link of internal) if (!routes.has(link) && !statics.has(link)) { console.log('broken internal link', link); failed++ }
if (!process.argv.includes('--offline')) {
  for (const link of external) {
    try {
      const r = await fetch(link, { method: 'GET', redirect: 'follow' })
      if (r.status >= 400) { console.log(r.status, link); failed++ }
    } catch (e) { console.log('unreachable', link); failed++ }
  }
}
for (const link of external) if (!link.startsWith('https://')) { console.log('not https', link); failed++ }
console.log(`${internal.size} internal and ${external.size} external links checked, ${failed} broken`)
process.exit(failed ? 1 : 0)
