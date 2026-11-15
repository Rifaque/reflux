// Where each platform stands, from the data the build read (docs/WEBSITE.md, "Release data"): the store records and
// the newest publication record. Plain and free of React, so the page and its tests share it.

export const STATE_LABELS = { 'not listed': 'Not listed yet', testing: 'In testing', available: 'Available' }

/** The desktop archive a record offers for a platform: a GitHub-distributed one, never a store package. */
export const desktopFile = (latest, platform) =>
  latest?.artifacts.find((a) => a.platform === platform && a.distribution === 'github-release' && (a.kind === 'zip' || a.kind === 'tar.gz')) ?? null

/** The rows of the availability table: name, channel, state, and the address once there is one. */
export function availability({ stores, latest }) {
  const store = (channel, listing) => ({ channel, state: listing.state, url: listing.state === 'available' ? listing.url : null })
  const desktop = (name, platform) => {
    const file = desktopFile(latest, platform)
    if (file) return { name, channel: 'GitHub Release', state: 'available', url: file.url, version: latest.version }
    return { name, channel: 'GitHub Release', state: 'not listed', note: latest ? `Not in Reflux ${latest.version}` : 'No public build yet', url: null }
  }
  return [
    { name: 'Android phones and tablets', ...store('Google Play', stores.googlePlay) },
    { name: 'Android TV and Google TV', ...store('Google Play', stores.googlePlay) },
    { name: 'Fire TV', ...store('Amazon Appstore', stores.amazonAppstore) },
    desktop('Windows', 'windows'),
    desktop('Linux', 'linux'),
  ]
}
