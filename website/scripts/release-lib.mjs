// The release data's rules as pure functions (docs/release/WEBSITE.md, "Release data"): what a store record and a publication
// record must say before the site may present them. release-data.mjs applies them and fails the build on a violation;
// release-lib.test.mjs holds them to it. Nothing here reads a file.

export const REPOSITORY = 'https://github.com/Rifaque/reflux'
export const STORE_STATES = ['not listed', 'testing', 'available']
export const TESTING_KINDS = ['closed-test-opt-in', 'live-app-testing']
/** Each store the site knows, and the domain its address must be on. */
export const STORES = { googlePlay: 'play.google.com', amazonAppstore: 'amazon.com' }
export const DISTRIBUTIONS = ['github-release', 'store']

const onDomain = (url, domain) => {
  try {
    const u = new URL(url)
    return u.protocol === 'https:' && (u.hostname === domain || u.hostname.endsWith('.' + domain))
  } catch { return false }
}

/** Problems with release/stores.json, as sentences; empty when it is sound. */
export function validateStores(stores, androidPackage) {
  const problems = []
  if (!stores || typeof stores !== 'object') return ['release/stores.json is not an object']
  for (const key of Object.keys(STORES)) if (!(key in stores)) problems.push(`stores.json: no ${key} record`)
  for (const [key, listing] of Object.entries(stores)) {
    const at = `stores.json: ${key}`
    const domain = STORES[key]
    if (!domain) { problems.push(`${at} is not a store the site knows (${Object.keys(STORES).join(', ')})`); continue }
    if (listing.package !== androidPackage) problems.push(`${at} is for ${listing.package}, the app is ${androidPackage}`)
    if (!STORE_STATES.includes(listing.state)) { problems.push(`${at}: state "${listing.state}" is not one of ${STORE_STATES.join(', ')}`); continue }
    if (listing.state === 'not listed') {
      if (listing.url !== null) problems.push(`${at}: "not listed" must have a null url`)
    } else if (listing.state === 'testing' && listing.url === null) {
      // A closed or invite-only test has no public listing to link; it is reached by its tester route alone.
      if (!listing.testing) problems.push(`${at}: "testing" with no public listing needs a tester route`)
    } else if (typeof listing.url !== 'string' || !onDomain(listing.url, domain)) {
      problems.push(`${at}: state "${listing.state}" needs an https url on ${domain}`)
    } else if (key === 'googlePlay' && !listing.url.includes(androidPackage)) {
      problems.push(`${at}: the Google Play url does not name ${androidPackage}`)
    }
    if (!('testing' in listing)) problems.push(`${at}: no "testing" field (null when there is no public tester route)`)
    else if (listing.testing !== null) {
      const t = listing.testing
      if (typeof t !== 'object' || !TESTING_KINDS.includes(t.kind)) problems.push(`${at}: testing.kind must be one of ${TESTING_KINDS.join(', ')}`)
      else {
        if (t.url !== undefined && !onDomain(t.url, domain)) problems.push(`${at}: testing.url must be https on ${domain}`)
        if (t.instructions !== undefined && !/^\/[a-z0-9/-]*$/.test(t.instructions)) problems.push(`${at}: testing.instructions must be a path on the site`)
        if (t.url !== undefined && t.url === listing.url) problems.push(`${at}: a tester route is not the public listing`)
      }
    }
  }
  return problems
}

const semver = /^(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)\.(\d+))?$/
const rank = { alpha: 0, beta: 1, rc: 2 }
/** Orders two versions (alpha < beta < rc < the release); negative when a is older. */
export function compareVersions(a, b) {
  const x = semver.exec(a), y = semver.exec(b)
  if (!x || !y) throw new Error(`not a version: ${!x ? a : b}`)
  for (let i = 1; i <= 3; i++) if (x[i] !== y[i]) return Number(x[i]) - Number(y[i])
  const px = x[4] === undefined ? 3 : rank[x[4]], py = y[4] === undefined ? 3 : rank[y[4]]
  return px !== py ? px - py : Number(x[5] ?? 0) - Number(y[5] ?? 0)
}

/** Where an artifact is distributed: its record's word, or, for records that predate the field, Android is a store's. */
export const distributionOf = (a) => a.distribution ?? (a.platform === 'android' ? 'store' : 'github-release')

/**
 * Problems with the publication records ({ file, record } each), against the version in development and the changelog's
 * version headings. The stale-record check: the site presents the newest record, and it must be a record that agrees
 * with itself, with its file name, its tag, and its artifacts' names.
 */
export function validatePublished(entries, { version, changelogVersions = [] }) {
  const problems = []
  const seen = new Set()
  for (const { file, record: m } of entries) {
    const at = `release/published/${file}`
    if (!semver.test(m.version ?? '')) { problems.push(`${at}: version "${m.version}" is malformed`); continue }
    if (file !== `${m.version}.json`) problems.push(`${at}: names version ${m.version}, so the file must be ${m.version}.json`)
    if (seen.has(m.version)) problems.push(`${at}: version ${m.version} is published twice`)
    seen.add(m.version)
    if (m.tag !== 'v' + m.version) problems.push(`${at}: tag "${m.tag}" is not v${m.version}`)
    if (m.repository !== REPOSITORY) problems.push(`${at}: repository "${m.repository}" is not ${REPOSITORY}`)
    if (!/^[0-9a-f]{40}$/.test(m.commit ?? '')) problems.push(`${at}: names no full commit`)
    if (!Array.isArray(m.artifacts) || !m.artifacts.length) { problems.push(`${at}: no artifacts`); continue }
    if (compareVersions(m.version, version) > 0) problems.push(`${at}: ${m.version} is newer than the version in development (${version})`)
    if (!changelogVersions.includes(m.version)) problems.push(`${at}: CHANGELOG.md has no "## ${m.version}" section for the release notes`)
    for (const a of m.artifacts) {
      const what = `${at}: ${a.name}`
      if (!/^[0-9a-f]{64}$/.test(a.sha256 ?? '')) problems.push(`${what} has no SHA-256`)
      if (!Number.isInteger(a.size) || a.size <= 0) problems.push(`${what} has no size`)
      if (!a.name?.startsWith(`reflux-${m.version}-`)) problems.push(`${what} is not named reflux-${m.version}-...`)
      const d = distributionOf(a)
      if (!DISTRIBUTIONS.includes(d)) problems.push(`${what}: distribution "${d}" is not one of ${DISTRIBUTIONS.join(', ')}`)
      if (/^(apk|aab)$/.test(a.kind) && d !== 'store') problems.push(`${what}: an Android package is never distributed from GitHub`)
    }
  }
  const valid = entries.filter((e) => semver.test(e.record.version ?? ''))
  const byVersion = [...valid].sort((a, b) => compareVersions(b.record.version, a.record.version))
  const byTime = [...valid].sort((a, b) => (b.record.builtAt ?? '').localeCompare(a.record.builtAt ?? ''))
  if (byVersion[0] && byTime[0] && byVersion[0].record.version !== byTime[0].record.version) {
    problems.push(`release/published: the newest build (${byTime[0].record.version}) is not the highest version (${byVersion[0].record.version})`)
  }
  return problems
}

/** Published records, newest version first, each artifact given the address it is offered at (store ones get none). */
export function presentPublished(entries) {
  const records = entries.map(({ record }) => structuredClone(record))
  for (const m of records) {
    for (const a of m.artifacts) {
      a.distribution = distributionOf(a)
      if (a.distribution === 'github-release') a.url = `${m.repository}/releases/download/${m.tag}/${a.name}`
    }
    for (const [key, file] of [['checksumsUrl', 'SHA256SUMS'], ['manifestUrl', 'manifest.json'], ['provenanceUrl', 'provenance.intoto.json']]) {
      m[key] = `${m.repository}/releases/download/${m.tag}/${file}`
    }
    m.releaseUrl = `${m.repository}/releases/tag/${m.tag}`
  }
  return records.sort((a, b) => compareVersions(b.version, a.version))
}

/** True when one of these publication records is this version or newer (the site's "is it out yet", src/released.js). */
export const releasedIn = (published, version) => (published ?? []).some((r) => compareVersions(r.version, version) >= 0)
