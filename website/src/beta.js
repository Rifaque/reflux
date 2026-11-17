// The beta season and the 1.0 plan, as the roadmap states them (docs/ROADMAP.md, "Beta Season"). Dates are a plan, not
// a promise: the site always says what 1.0 depends on. The version, artifacts, and store states come from the release
// data (scripts/release-data.mjs), never from here.

export const SEASON = { start: '17 November 2026', end: '15 December 2026' }

export const SUPPORT = 'support@rsxdevelops.dev'

/** The steps from the beta to 1.0, in order. Only the first has happened. */
export const TIMELINE = [
  ['17 November', 'The beta opens', 'Reflux 0.9.0-beta.1 for testers on Android, Fire TV, Windows, and Linux.'],
  ['10 December', 'Readiness checkpoint', 'An internal review of what testers found and what is fixed.'],
  ['15 December', 'The beta ends', 'The decision on releasing 1.0, from the feedback and each platform’s qualification.'],
  ['December 2026', 'Reflux 1.0, planned', 'Published together on every platform once each store has approved it.'],
]

export const V1_WINDOW =
  'Reflux 1.0 is planned for December 2026. Until then a cross-platform beta runs on Android, Fire TV, Windows, and Linux. ' +
  'The final date depends on what testers find, on each platform’s qualification, and on Google’s and Amazon’s approval.'

/** A bug report's skeleton, for the e-mail link and the page alike. */
export const REPORT_FIELDS = [
  ['Platform and device', 'For example: Fire TV Stick 4K, Windows 11 laptop, Pixel 8.'],
  ['Reflux version', 'Settings → About Reflux. Today it is 0.9.0-beta.1.'],
  ['Media source', 'Folder, Jellyfin, Plex, SMB share, or WebDAV, and roughly what the file was (for example, a 4K HEVC film with DTS sound).'],
  ['Steps to reproduce', 'What you did, one step at a time.'],
  ['What you expected', 'What should have happened.'],
  ['What happened', 'What happened instead, with any message Reflux showed.'],
  ['Screenshot or support report', 'If you can: a photo or screenshot, and the support report from Settings → About Reflux → Save a support report.'],
]

const body = REPORT_FIELDS.map(([name]) => `${name}:\n`).join('\n')
export const REPORT_MAILTO = `mailto:${SUPPORT}?subject=${encodeURIComponent('Reflux beta: ')}&body=${encodeURIComponent(body)}`
export const JOIN_MAILTO = `mailto:${SUPPORT}?subject=${encodeURIComponent('Join the Reflux beta')}&body=${encodeURIComponent(
  'Devices (model and system version):\n\nMedia sources (folders, Jellyfin, Plex, SMB, WebDAV):\n\nGame controller (yes or no, which):\n\n' +
  'Google account e-mail (Android) or Amazon account e-mail (Fire TV):\n')}`
