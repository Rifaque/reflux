// Structured data for search engines (docs/WEBSITE.md, "Search"): JSON-LD in each prerendered page. It says only what
// the site can substantiate: who makes Reflux, what the site is, what Reflux is and where it runs, and where a page
// sits. No rating, review, download count, or price other than free is ever claimed (Google's guidelines for
// software apps require real ones, and Reflux has none to show).
import { SITE } from './routes.js'

const publisher = { '@type': 'Organization', '@id': 'https://rsxdevelops.dev/#organization', name: 'RSX Develops', url: 'https://rsxdevelops.dev' }

const website = { '@type': 'WebSite', '@id': `${SITE}/#website`, name: 'Reflux', url: `${SITE}/`, publisher: { '@id': publisher['@id'] }, inLanguage: 'en' }

const application = {
  '@type': 'SoftwareApplication',
  '@id': `${SITE}/#software`,
  name: 'Reflux',
  url: `${SITE}/`,
  applicationCategory: 'MultimediaApplication',
  operatingSystem: 'Android, Android TV, Fire OS, Windows, Linux',
  description: 'A media player for your own films and shows: local folders, Jellyfin, Plex, and network shares. No account, no tracking.',
  offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
  publisher: { '@id': publisher['@id'] },
}

/** Where a page sits, for search results: Home, then its section, then the page. Only beneath the home page. */
function breadcrumb(route, routes) {
  if (route.path === '/') return null
  const parts = route.path.split('/').filter(Boolean)
  const trail = [{ name: 'Reflux', url: `${SITE}/` }]
  parts.forEach((_, i) => {
    const path = '/' + parts.slice(0, i + 1).join('/')
    const known = routes.find((r) => r.path === path)
    if (known) trail.push({ name: known.title, url: `${SITE}${path}` })
  })
  if (trail.length < 2) return null
  return {
    '@type': 'BreadcrumbList',
    itemListElement: trail.map((step, i) => ({ '@type': 'ListItem', position: i + 1, name: step.name, item: step.url })),
  }
}

/** The JSON-LD for a page, as the text of a script element of type application/ld+json. */
export function structuredData(route, routes) {
  const graph = route.path === '/' ? [publisher, website, application] : [publisher, website, breadcrumb(route, routes)].filter(Boolean)
  // `<` escaped so the data can never close the element it sits in.
  return JSON.stringify({ '@context': 'https://schema.org', '@graph': graph }).replace(/</g, '\\u003c')
}
