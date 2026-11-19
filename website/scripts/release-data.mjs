// Builds the website's release data from the repository (docs/WEBSITE.md, "Release data"). The site never types a
// version, an artifact name, a checksum, or a download address: they come from here.
//
//   gradle.properties          the product version in development (reflux.version)
//   CHANGELOG.md               release notes: "Unreleased" and each "## <version> — <date>" section
//   release/published/*.json   manifests of published versions (tools/release/release.py), newest first
//   release/stores.json        store listings and their state (schema: release-lib.mjs)
//   legal/components.txt       third-party components, for the notices page
//   NOTICE, LICENSE            Reflux's notice (copyright, GPL-3.0-or-later, source) and the GNU GPL text, for the license page
//
// Writes src/generated/release.json. Fails the build if a source is missing or malformed, rather than publishing a
// page that says something untrue.
import { readFileSync, readdirSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { presentPublished, validatePublished, validateStores } from './release-lib.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const root = join(here, '..', '..')
const read = (path) => readFileSync(join(root, path), 'utf8')

const version = /^reflux\.version=(.+)$/m.exec(read('gradle.properties'))?.[1]?.trim()
if (!version || !/^\d+\.\d+\.\d+(-(alpha|beta|rc)\.\d+)?$/.test(version)) throw new Error('reflux.version missing or malformed')

// Release notes: the changelog's sections, each a list of plain-language items.
const changelog = []
let current = null
for (const line of read('CHANGELOG.md').split(/\r?\n/)) {
  const heading = /^## (.+?)(?: — (\d{4}-\d{2}-\d{2}))?$/.exec(line)
  if (heading) {
    current = { version: heading[1], date: heading[2] ?? null, items: [] }
    changelog.push(current)
  } else if (current && line.startsWith('- ')) {
    current.items.push(line.slice(2).trim())
  }
}

// Published releases: the manifests release.py wrote, copied byte for byte into release/published/<version>.json when a
// release is published. Their rules are release-lib.mjs's: the build fails on a record that disagrees with itself or
// with the repository (a stale record), rather than present it. RELEASE_PUBLISHED_DIR names another folder of records;
// it exists for the website's own tests and is never set in a build that is deployed.
const publishedDir = process.env.RELEASE_PUBLISHED_DIR ?? join(root, 'release', 'published')
const entries = existsSync(publishedDir)
  ? readdirSync(publishedDir).filter((f) => f.endsWith('.json')).sort().map((file) => ({ file, record: JSON.parse(readFileSync(join(publishedDir, file), 'utf8')) }))
  : []
const publishedProblems = validatePublished(entries, { version, changelogVersions: changelog.map((c) => c.version) })
if (publishedProblems.length) throw new Error(`published releases:\n  ${publishedProblems.join('\n  ')}`)
// The download address is the GitHub release asset, and only for what GitHub distributes; a store's package gets none.
const published = presentPublished(entries)

const stores = JSON.parse(read('release/stores.json'))
// The store listings are for the one Android identity the build declares (DECISIONS.md, "The permanent Android identity").
const androidPackage = /^reflux\.android\.applicationId=(.+)$/m.exec(read('gradle.properties'))?.[1]?.trim()
const storeProblems = validateStores(stores, androidPackage)
if (storeProblems.length) throw new Error(`release/stores.json:\n  ${storeProblems.join('\n  ')}`)

// The brand's mark and lockup (design/brand), drawn inline so they take the page's colour.
const brandPath = (file) => {
  const svg = read(`design/brand/${file}`)
  return { viewBox: /viewBox="([^"]+)"/.exec(svg)[1], d: / d="([^"]+)"/.exec(svg)[1] }
}
const stroke = read('design/brand/reflux-mark-stroke.svg')
const pass = (id) => new RegExp(`id="${id}"[^>]* d="([^"]+)"`).exec(stroke)[1]
// The centreline the mark is drawn from, for its motion (DESIGN.md, "Motion language").
const brand = {
  mark: brandPath('reflux-mark.svg'), lockup: brandPath('reflux-lockup.svg'),
  stroke: { first: pass('first'), return: pass('return'), width: Number(/stroke-width="([\d.]+)"/.exec(stroke)[1]) },
}

// Third-party components: name, license, and the platforms they ship on.
const components = read('legal/components.txt').split(/\r?\n/)
  .filter((l) => l.trim() && !l.startsWith('#'))
  .map((l) => l.split('|').map((s) => s.trim()))
  .map(([name, license, , targets]) => ({ name, license, targets }))

const notice = read('NOTICE').trim()
// The license itself is served whole as /license.txt, the repository's LICENSE byte for byte, rather than carried in
// every page's script: it is 36 KB that only the license page links to.
writeFileSync(join(here, '..', 'public', 'license.txt'), read('LICENSE'))

// What this website itself ships to browsers: its production dependencies, with the license each declares.
const lock = JSON.parse(readFileSync(join(here, '..', 'package-lock.json'), 'utf8'))
const site = Object.entries(lock.packages)
  .filter(([path, p]) => path.startsWith('node_modules/') && !p.dev)
  .map(([path, p]) => ({ name: path.slice('node_modules/'.length), version: p.version, license: p.license ?? 'unknown' }))
  .sort((a, b) => a.name.localeCompare(b.name))
if (site.some((p) => p.license === 'unknown')) throw new Error('a website dependency declares no license')
// Their license texts, verbatim from each package, served beside the site as /third-party-licenses.txt.
const texts = site.map((p) => {
  const dir = join(here, '..', 'node_modules', p.name)
  const file = readdirSync(dir).find((f) => /^licen[cs]e/i.test(f))
  if (!file) throw new Error(`${p.name} ships no license file`)
  return `===== ${p.name} ${p.version} (${p.license}) =====

${readFileSync(join(dir, file), 'utf8').trim()}
`
})
mkdirSync(join(here, '..', 'public'), { recursive: true })
writeFileSync(join(here, '..', 'public', 'third-party-licenses.txt'), texts.join('\n'))
const data = { version, androidPackage, changelog, published, latest: published[0] ?? null, stores, components, notice, site, brand }
// RELEASE_DATA_OUT, like RELEASE_PUBLISHED_DIR, is for the website's own tests.
const out = process.env.RELEASE_DATA_OUT ?? join(here, '..', 'src', 'generated', 'release.json')
mkdirSync(dirname(out), { recursive: true })
writeFileSync(out, JSON.stringify(data, null, 2) + '\n')
console.log(`release data: Reflux ${version}, ${published.length} published, ${changelog.length} changelog sections, ${components.length} components`)
