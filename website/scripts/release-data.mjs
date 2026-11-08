// Builds the website's release data from the repository (docs/WEBSITE.md, "Release data"). The site never types a
// version, an artifact name, a checksum, or a download address: they come from here.
//
//   gradle.properties          the product version in development (reflux.version)
//   CHANGELOG.md               release notes: "Unreleased" and each "## <version> — <date>" section
//   release/published/*.json   manifests of published versions (tools/release/release.py), newest first
//   release/stores.json        store listings and their state
//   legal/components.txt       third-party components, for the notices page
//   LICENSE                    Reflux's ownership notice, for the terms page
//
// Writes src/generated/release.json. Fails the build if a source is missing or malformed, rather than publishing a
// page that says something untrue.
import { readFileSync, readdirSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

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

// Published releases: their manifests as release.py wrote them. The download address is the GitHub release asset.
const publishedDir = join(root, 'release', 'published')
const published = existsSync(publishedDir)
  ? readdirSync(publishedDir).filter((f) => f.endsWith('.json')).map((f) => JSON.parse(readFileSync(join(publishedDir, f), 'utf8')))
  : []
for (const m of published) {
  if (!/^[0-9a-f]{40}$/.test(m.commit ?? '')) throw new Error(`published ${m.version} names no commit`)
  for (const a of m.artifacts) {
    if (!/^[0-9a-f]{64}$/.test(a.sha256 ?? '')) throw new Error(`published ${m.version}: ${a.name} has no SHA-256`)
    a.url = `${m.repository}/releases/download/${m.tag}/${a.name}`
  }
  m.checksumsUrl = `${m.repository}/releases/download/${m.tag}/SHA256SUMS`
}
published.sort((a, b) => (b.builtAt ?? '').localeCompare(a.builtAt ?? ''))

const stores = JSON.parse(read('release/stores.json'))
// The store listings are for the one Android identity the build declares (DECISIONS.md, "The permanent Android identity").
const androidPackage = /^reflux\.android\.applicationId=(.+)$/m.exec(read('gradle.properties'))?.[1]?.trim()
for (const [store, listing] of Object.entries(stores)) {
  if (listing.package !== androidPackage) throw new Error(`release/stores.json: ${store} is for ${listing.package}, the app is ${androidPackage}`)
}

// The brand's mark and lockup (design/brand), drawn inline so they take the page's colour.
const brandPath = (file) => {
  const svg = read(`design/brand/${file}`)
  return { viewBox: /viewBox="([^"]+)"/.exec(svg)[1], d: / d="([^"]+)"/.exec(svg)[1] }
}
const brand = { mark: brandPath('reflux-mark.svg'), lockup: brandPath('reflux-lockup.svg') }

// Third-party components: name, license, and the platforms they ship on.
const components = read('legal/components.txt').split(/\r?\n/)
  .filter((l) => l.trim() && !l.startsWith('#'))
  .map((l) => l.split('|').map((s) => s.trim()))
  .map(([name, license, , targets]) => ({ name, license, targets }))

const license = read('LICENSE').trim()

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
const data = { version, androidPackage, changelog, published, latest: published[0] ?? null, stores, components, license, site, brand }
mkdirSync(join(here, '..', 'src', 'generated'), { recursive: true })
writeFileSync(join(here, '..', 'src', 'generated', 'release.json'), JSON.stringify(data, null, 2) + '\n')
console.log(`release data: Reflux ${version}, ${published.length} published, ${changelog.length} changelog sections, ${components.length} components`)
