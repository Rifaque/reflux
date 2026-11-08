// Serves dist/ the way Vercel serves it (vercel.json): clean URLs (/download is download.html), 404.html with status
// 404, and every header vercel.json sets, so the production build, its content security policy included, can be
// checked locally. node scripts/serve.mjs [port]
import { createServer } from 'node:http'
import { readFileSync, existsSync, statSync } from 'node:fs'
import { join, extname, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const site = join(dirname(fileURLToPath(import.meta.url)), '..')
const dist = join(site, 'dist')
const config = JSON.parse(readFileSync(join(site, 'vercel.json'), 'utf8'))
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.webp': 'image/webp', '.woff2': 'font/woff2', '.txt': 'text/plain; charset=utf-8', '.xml': 'application/xml', '.webmanifest': 'application/manifest+json' }
const matches = (source, path) => new RegExp('^' + source.replace(/\(\.\*\)/g, '(.*)') + '$').test(path)

createServer((req, res) => {
  const path = decodeURIComponent(new URL(req.url, 'http://x').pathname)
  if (path !== '/' && path.endsWith('/')) { res.writeHead(308, { Location: path.slice(0, -1) }); return res.end() }
  const candidates = path === '/' ? ['index.html'] : [path.slice(1), path.slice(1) + '.html']
  const found = candidates.map((c) => join(dist, c)).find((f) => f.startsWith(dist) && existsSync(f) && statSync(f).isFile())
  const file = found ?? join(dist, '404.html')
  const headers = { 'Content-Type': types[extname(file)] ?? 'application/octet-stream' }
  for (const rule of config.headers) if (matches(rule.source, path)) for (const h of rule.headers) headers[h.key] = h.value
  res.writeHead(found ? 200 : 404, headers)
  res.end(readFileSync(file))
}).listen(Number(process.argv[2] ?? 4180), '127.0.0.1', () => console.log('serving dist on http://127.0.0.1:' + (process.argv[2] ?? 4180)))
