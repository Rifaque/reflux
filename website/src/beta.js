// The beta season and the road to 1.0, as docs/release/BETA.md states them. Dates are a plan, not a promise: the site always
// says what 1.0 depends on. The version, artifacts, and store states come from the release
// data (scripts/release-data.mjs), never from here.

import release from './generated/release.json'
import { beta2Out } from './released.js'

export const SEASON = { start: '17 November 2026', end: '15 December 2026' }

export const SUPPORT = 'support@rsxdevelops.dev'

/** The steps from the beta to 1.0 (docs/release/BETA.md). The first has happened; the rest are a plan, with no date promised. */
export const TIMELINE = [
  ['17 November', 'The beta opens', 'Reflux 0.9.0-beta.1 for testers on Android, Fire TV, Windows, and Linux.', true],
  beta2Out
    ? ['21 November', 'Beta 2', 'Reflux 0.9.0-beta.2: scene previews, one controller layout everywhere, and Reflux’s own keyboard.', true]
    : ['Next', 'Beta 2', 'Scene previews, one controller layout everywhere, and Reflux’s own keyboard: finished and being prepared for testers.'],
  ['Then', 'Beta 3 and the release candidate', 'Fixes for what testers find, checked on more real TVs, phones, and controllers.'],
  ['When it is ready', 'Reflux 1.0', 'Published on every platform once the 1.0 criteria hold and Google and Amazon have approved it.'],
]

export const V1_WINDOW =
  'Reflux 1.0 comes after the beta, when everything it does works well on every platform and both stores have approved it. ' +
  'No date is promised: it depends on what testers find and on each platform’s checks. The roadmap says what comes after.'

/** A bug report's skeleton, for the e-mail link and the page alike. */
export const REPORT_FIELDS = [
  ['Platform and device', 'For example: Fire TV Stick 4K, Windows 11 laptop, Pixel 8.'],
  ['Reflux version', `Settings → About Reflux.${release.latest ? ` Today it is ${release.latest.version}.` : ''}`],
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
