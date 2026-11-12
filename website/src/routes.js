// Every page of the site, with its title and description: the app's routes, the prerendered files, the links check,
// and the search engines all read this one list (docs/WEBSITE.md).
export const SITE = 'https://reflux.rsxdevelops.dev'

export const routes = [
  { path: '/', title: 'Reflux | Your media, played well', description: 'A media player for your own films and shows: local folders, Jellyfin, Plex, and network shares, on Android, Android TV, Fire TV, Windows, and Linux.' },
  { path: '/platforms', title: 'Platforms and compatibility', description: 'Where Reflux runs, what each platform needs, and what it plays: formats, HDR, sound, and subtitles, as verified on real devices.' },
  { path: '/sources', title: 'Sources', description: 'Folders on your device, Jellyfin, Plex, Windows and NAS shares (SMB), and web shares (WebDAV): what each needs and what Reflux does with it.' },
  { path: '/download', title: 'Download', description: 'Get Reflux: Google Play for Android and Google TV, the Amazon Appstore for Fire TV, and checksummed builds for Windows and Linux.' },
  { path: '/testing', title: 'Testing Reflux', description: 'How the Reflux beta will work: which build you test, how to install it on each platform, known limits, and how to report a problem.' },
  { path: '/docs', title: 'Documentation', description: 'Getting started with Reflux, adding sources, how playback chooses a version, how the library is built, and troubleshooting.' },
  { path: '/docs/getting-started', title: 'Getting started', description: 'Install Reflux and point it at your first folder or server.' },
  { path: '/docs/sources', title: 'Adding sources', description: 'Add a folder, a Jellyfin or Plex server, or an SMB or WebDAV share to Reflux, and include or leave out each library.' },
  { path: '/docs/playback', title: 'How playback works', description: 'How Reflux chooses a version, what it plays directly, what a server converts and why, and what the Info list tells you.' },
  { path: '/docs/library', title: 'Your library', description: 'How Reflux identifies files, where descriptions and artwork come from, and how to correct a match with Identify.' },
  { path: '/docs/troubleshooting', title: 'Troubleshooting and FAQ', description: 'Common questions and problems, and how to send a support report.' },
  { path: '/releases', title: 'Releases', description: 'What changes in Reflux, version by version, from its changelog.' },
  { path: '/support', title: 'Support', description: 'Get help with Reflux, report a problem, and send feedback.' },
  { path: '/privacy', title: 'Privacy', description: 'What Reflux keeps on your device, what it sends and to whom, and what this website does.' },
  { path: '/terms', title: 'Terms', description: "Reflux's ownership notice and terms of use: proprietary, free to use, and what applies to the stores' downloads." },
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
