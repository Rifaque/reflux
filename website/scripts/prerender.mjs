// Writes every page of the site as its own HTML file (docs/WEBSITE.md, "Architecture"): the app rendered on the server
// side for each route in src/routes.js, with that page's title, description, canonical address, sharing card, and
// structured data, plus 404.html, sitemap.xml (with each page's last change, where git can say), and robots.txt.
// The browser's script then takes the page over. Run after both Vite builds (npm run build).
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { execFileSync } from 'node:child_process'
import { fileURLToPath, pathToFileURL } from 'node:url'

const site = join(dirname(fileURLToPath(import.meta.url)), '..')
const dist = join(site, 'dist')
const { render } = await import(pathToFileURL(join(site, 'dist-server', 'entry-server.js')).href)
const { routes, notFound, SITE, titleOf } = await import(pathToFileURL(join(site, 'src', 'routes.js')).href)
const { structuredData } = await import(pathToFileURL(join(site, 'src', 'seo.js')).href)
const template = readFileSync(join(dist, 'index.html'), 'utf8')
const escape = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

function page(route, url) {
  const title = titleOf(route)
  const address = `${SITE}${route.path === '/' ? '/' : route.path}`
  const indexed = route !== notFound
  const head = [
    `<title>${escape(title)}</title>`,
    `<meta name="description" content="${escape(route.description)}" />`,
    indexed ? `<link rel="canonical" href="${address}" />` : '',
    indexed ? '' : '<meta name="robots" content="noindex" />',
    `<meta property="og:type" content="website" />`,
    `<meta property="og:site_name" content="Reflux" />`,
    `<meta property="og:title" content="${escape(title)}" />`,
    `<meta property="og:description" content="${escape(route.description)}" />`,
    indexed ? `<meta property="og:url" content="${address}" />` : '',
    `<meta property="og:image" content="${SITE}/og-image.png" />`,
    `<meta property="og:image:alt" content="Reflux: your media, played well" />`,
    `<meta name="twitter:card" content="summary_large_image" />`,
    indexed ? `<script type="application/ld+json">${structuredData(route, routes)}</script>` : '',
  ].filter(Boolean).join('\n    ')
  return template.replace('<!--head-->', head).replace('<!--app-->', render(url))
}

/**
 * When a page last changed, from git: the newest commit touching the files it is made of (its page, the shared parts,
 * and the release data it shows). Null where git cannot say (a shallow or missing history), and then the sitemap
 * leaves the date out rather than guess one.
 */
const shared = ['src/components.jsx', 'src/styles.css', 'src/App.jsx', 'src/routes.js']
const sources = {
  '/': ['src/pages/Home.jsx'], '/platforms': ['src/pages/Platforms.jsx'], '/sources': ['src/pages/Sources.jsx'],
  '/download': ['src/pages/Download.jsx', '../release'], '/testing': ['src/pages/Testing.jsx'], '/releases': ['src/pages/Releases.jsx', '../CHANGELOG.md'],
  '/support': ['src/pages/Support.jsx'], '/privacy': ['src/pages/Legal.jsx'], '/terms': ['src/pages/Legal.jsx', '../LICENSE'],
  '/legal/third-party': ['src/pages/Legal.jsx', '../legal'], '/security': ['src/pages/Legal.jsx'],
}
function lastChanged(path) {
  const files = [...(sources[path] ?? ['src/pages/Docs.jsx']), ...shared]
  try {
    // In a shallow clone (CI, Vercel) the oldest commit fetched seems to add every file; it is never taken for a change.
    const shallowFile = execFileSync('git', ['rev-parse', '--git-path', 'shallow'], { cwd: site, encoding: 'utf8' }).trim()
    const boundary = new Set(existsSync(join(site, shallowFile)) ? readFileSync(join(site, shallowFile), 'utf8').split(/\s+/).filter(Boolean) : [])
    const log = execFileSync('git', ['log', '--format=%H %cs', '--', ...files], { cwd: site, encoding: 'utf8' }).trim().split('\n')
    const latest = log.map((line) => line.split(' ')).find(([hash, date]) => hash && !boundary.has(hash) && /^\d{4}-\d{2}-\d{2}$/.test(date ?? ''))
    return latest ? latest[1] : null
  } catch {
    return null
  }
}

for (const route of routes) {
  // /download is download.html: what Vercel's cleanUrls serves at /download, with no directory or trailing slash.
  const file = route.path === '/' ? join(dist, 'index.html') : join(dist, route.path.slice(1) + '.html')
  mkdirSync(dirname(file), { recursive: true })
  writeFileSync(file, page(route, route.path))
}
writeFileSync(join(dist, '404.html'), page(notFound, '/this-page-does-not-exist'))
const entries = routes.map((r) => {
  const date = lastChanged(r.path)
  return `  <url><loc>${SITE}${r.path}</loc>${date ? `<lastmod>${date}</lastmod>` : ''}</url>`
})
writeFileSync(join(dist, 'sitemap.xml'), `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${entries.join('\n')}\n</urlset>\n`)
writeFileSync(join(dist, 'robots.txt'), `User-agent: *\nAllow: /\nSitemap: ${SITE}/sitemap.xml\n`)
