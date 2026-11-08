// Writes every page of the site as its own HTML file (docs/WEBSITE.md, "Architecture"): the app rendered on the server
// side for each route in src/routes.js, with that page's title, description, canonical address, and sharing card.
// The browser's script then takes the page over. Run after both Vite builds (npm run build).
import { readFileSync, writeFileSync, mkdirSync, rmSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const site = join(dirname(fileURLToPath(import.meta.url)), '..')
const dist = join(site, 'dist')
const { render } = await import(pathToFileURL(join(site, 'dist-server', 'entry-server.js')).href)
const { routes, notFound, SITE } = await import(pathToFileURL(join(site, 'src', 'routes.js')).href)
const template = readFileSync(join(dist, 'index.html'), 'utf8')
const escape = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

function page(route, url) {
  const title = route.path === '/' ? route.title : `${route.title} · Reflux`
  const canonical = route === notFound ? '' : `<link rel="canonical" href="${SITE}${route.path === '/' ? '/' : route.path}" />`
  const head = [
    `<title>${escape(title)}</title>`,
    `<meta name="description" content="${escape(route.description)}" />`,
    canonical,
    route === notFound ? '<meta name="robots" content="noindex" />' : '',
    `<meta property="og:type" content="website" />`,
    `<meta property="og:site_name" content="Reflux" />`,
    `<meta property="og:title" content="${escape(title)}" />`,
    `<meta property="og:description" content="${escape(route.description)}" />`,
    `<meta property="og:image" content="${SITE}/og-image.png" />`,
    `<meta name="twitter:card" content="summary_large_image" />`,
  ].filter(Boolean).join('\n    ')
  return template.replace('<!--head-->', head).replace('<!--app-->', render(url))
}

for (const route of routes) {
  // /download is download.html: what Vercel's cleanUrls serves at /download, with no directory or trailing slash.
  const file = route.path === '/' ? join(dist, 'index.html') : join(dist, route.path.slice(1) + '.html')
  mkdirSync(dirname(file), { recursive: true })
  writeFileSync(file, page(route, route.path))
}
writeFileSync(join(dist, '404.html'), page(notFound, '/this-page-does-not-exist'))
writeFileSync(join(dist, 'sitemap.xml'), `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${routes.map((r) => `  <url><loc>${SITE}${r.path}</loc></url>`).join('\n')}\n</urlset>\n`)
writeFileSync(join(dist, 'robots.txt'), `User-agent: *\nAllow: /\nSitemap: ${SITE}/sitemap.xml\n`)
rmSync(join(site, 'dist-server'), { recursive: true, force: true })
console.log(`prerendered ${routes.length} pages and 404.html`)
