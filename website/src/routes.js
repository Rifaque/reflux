// Every page of the site, with its title and description: the app's routes, the prerendered files, the links check,
// and the search engines all read this one list (docs/WEBSITE.md).
import { DOC_PAGES } from './docs/pages.js'

export const SITE = 'https://reflux.rsxdevelops.dev'

export const routes = [
  { path: '/', title: 'Reflux | Your media, played well', description: 'A free and open-source media player for your own films and shows: local folders, Jellyfin, Plex, and network shares, on Android, Android TV, Fire TV, Windows, and Linux.' },
  { path: '/platforms', title: 'Platforms and compatibility', description: 'Where Reflux runs, what each platform needs, and what it plays: formats, HDR, sound, and subtitles, as verified on real devices.' },
  { path: '/sources', title: 'Sources', description: 'Folders on your device, Jellyfin, Plex, Windows and NAS shares (SMB), and web shares (WebDAV): what each needs and what Reflux does with it.' },
  { path: '/download', title: 'Download', description: 'Get the Reflux beta: checksummed downloads for Windows and Linux, and Google Play and Amazon test programmes for Android and Fire TV.' },
  { path: '/testing', title: 'The Reflux beta', description: 'Join the Reflux beta (17 November to 15 December 2026): how to install it on each platform, known issues, reporting a problem, and the 1.0 plan.' },
  { path: '/docs', title: 'Documentation', description: 'How to install Reflux on Windows, Linux, Android, Android TV, and Fire TV, add your sources, and get the most from playback.' },
  ...DOC_PAGES.map((p) => ({ path: p.path, title: p.title, description: p.summary })),
  { path: '/releases', title: 'Releases', description: 'What changes in Reflux, version by version, from its changelog.' },
  { path: '/support', title: 'Support', description: 'Get help with Reflux, report a problem, and send feedback.' },
  { path: '/privacy', title: 'Privacy', description: 'What Reflux keeps on your device, what it sends and to whom, and what this website does.' },
  { path: '/license', title: 'License', description: 'Reflux is free and open-source software under the GNU GPL, version 3 or later: its notice, the full license, and what stays under its own terms.' },
  { path: '/legal/third-party', title: 'Third-party notices', description: 'The software Reflux and this website are built with, each under its own license, and the credits for the artwork shown here.' },
  { path: '/security', title: 'Security', description: 'How to report a security problem in Reflux, and how to verify a download.' },
]

export const notFound = { path: '/404', title: 'Not found', description: 'There is no page at this address.' }

export const routeFor = (path) => routes.find((r) => r.path === path) ?? notFound

/**
 * A page's title as browsers and search results show it (DECISIONS.md, 2026-11-11): the home page's is its own,
 * `Reflux | Your media, played well`; every other page is `Page | Reflux`. Never an em dash.
 */
export const titleOf = (route) => (route.path === '/' ? route.title : `${route.title} | Reflux`)
