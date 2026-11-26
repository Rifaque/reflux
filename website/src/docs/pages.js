// The documentation's pages, in reading order, grouped as the sidebar shows them (docs/release/WEBSITE.md, "Documentation").
// Plain data with no JSX: the routes, the prerendered files, the sitemap, and the link check read this list, and
// src/docs/content.jsx gives each page its body. A slug never changes once published: it is the page's address.

export const DOC_GROUPS = [
  {
    title: 'Getting started',
    pages: [
      { slug: 'getting-started', title: 'Quick start', summary: 'From installing Reflux to playing your first film.' },
    ],
  },
  {
    title: 'Installation',
    pages: [
      { slug: 'install/windows', title: 'Windows', summary: 'Install, update, and remove Reflux on Windows 10 and 11.' },
      { slug: 'install/linux', title: 'Linux', summary: 'Install the playback engine your distribution packages, then Reflux.' },
      { slug: 'install/android', title: 'Android phones and tablets', summary: 'Install Reflux from Google Play on phones and tablets.' },
      { slug: 'install/android-tv', title: 'Android TV and Google TV', summary: 'Install Reflux from Google Play on a TV, and find your way with the remote.' },
      { slug: 'install/fire-tv', title: 'Fire TV', summary: 'Install Reflux from the Amazon Appstore on Fire TV.' },
      { slug: 'install/verify', title: 'Check a download', summary: 'Check a Windows or Linux download against its published SHA-256 and build attestation.' },
    ],
  },
  {
    title: 'Using Reflux',
    pages: [
      { slug: 'sources', title: 'Adding sources', summary: 'Folders, Jellyfin, Plex, SMB, and WebDAV, and choosing what is in your library.' },
      { slug: 'library', title: 'Your library', summary: 'How titles are recognised, where details come from, and Identify.' },
      { slug: 'playback', title: 'How playback works', summary: 'Which copy plays, what a server converts and why, and the Info list.' },
      { slug: 'profiles', title: 'Profiles', summary: 'A profile for each person who watches, with its own history, favourites, and accounts.' },
    ],
  },
  {
    title: 'Help',
    pages: [
      { slug: 'troubleshooting', title: 'Troubleshooting and FAQ', summary: 'Common questions and problems, and how to send a support report.' },
    ],
  },
]

/** Pages of the site that are reference material for the documentation, listed at the foot of its sidebar. */
export const DOC_REFERENCE = [
  ['/platforms', 'Platforms and compatibility'],
  ['/sources', 'What each source supports'],
  ['/download', 'Download'],
  ['/releases', 'Release notes'],
  ['/privacy', 'Privacy'],
  ['/license', 'License'],
  ['/legal/third-party', 'Third-party notices'],
  ['/security', 'Security'],
]

/** Every page in reading order, with its group. */
export const DOC_PAGES = DOC_GROUPS.flatMap((group) => group.pages.map((page) => ({ ...page, group: group.title, path: `/docs/${page.slug}` })))
