// Tests for the release data's rules (node --test; `npm test`). A record that disagrees must fail, and a good one pass.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { compareVersions, distributionOf, presentPublished, validatePublished, validateStores } from './release-lib.mjs'
import { availability } from '../src/availability.js'

const PKG = 'dev.rsxdevelops.reflux'
const store = (over = {}) => ({ package: PKG, state: 'not listed', url: null, testing: null, ...over })
const stores = (play = {}, amazon = {}) => ({ googlePlay: store(play), amazonAppstore: store(amazon) })
const PLAY = `https://play.google.com/store/apps/details?id=${PKG}`
const AMAZON = 'https://www.amazon.com/dp/B0EXAMPLE'

test('the committed shape (nothing listed) is valid', () => assert.deepEqual(validateStores(stores(), PKG), []))
test('available stores with urls on their own domains are valid', () => {
  assert.deepEqual(validateStores(stores({ state: 'available', url: PLAY }, { state: 'available', url: AMAZON }), PKG), [])
})
test('a store for another package fails', () => assert.match(validateStores(stores({ package: 'com.example' }), PKG)[0], /com\.example/))
test('an unknown state fails', () => assert.match(validateStores(stores({ state: 'beta' }), PKG)[0], /state "beta"/))
test('"not listed" with a url fails', () => assert.match(validateStores(stores({ url: PLAY }), PKG)[0], /null url/))
test('a listed state without a url fails', () => {
  assert.equal(validateStores(stores({ state: 'testing', url: null }), PKG).length, 1)
  assert.equal(validateStores(stores({ state: 'testing', url: 'https://example.com/?id=' + PKG }), PKG).length, 1)
  assert.equal(validateStores(stores({ state: 'available' }), PKG).length, 1)
})
test('a url off the store domain, or not https, fails', () => {
  assert.equal(validateStores(stores({ state: 'available', url: 'https://example.com/?id=' + PKG }), PKG).length, 1)
  assert.equal(validateStores(stores({ state: 'available', url: 'http://play.google.com/?id=' + PKG }), PKG).length, 1)
  assert.equal(validateStores(stores({ state: 'available', url: 'https://play.google.com.evil.example/?id=' + PKG }), PKG).length, 1)
  assert.equal(validateStores(stores({}, { state: 'available', url: 'https://play.google.com/x' }), PKG).length, 1)
})
test('a Play url that does not name the package fails', () => {
  assert.equal(validateStores(stores({ state: 'available', url: 'https://play.google.com/store/apps/details?id=com.other' }), PKG).length, 1)
})
test('a missing store record, an unknown store, and a missing testing field fail', () => {
  assert.equal(validateStores({ googlePlay: store() }, PKG).length, 1)
  assert.equal(validateStores({ ...stores(), huawei: store() }, PKG).length, 1)
  const { testing, ...bare } = store()
  assert.equal(validateStores({ googlePlay: bare, amazonAppstore: store() }, PKG).length, 1)
})
test('a closed test with no public listing is valid only with its tester route', () => {
  const play = { kind: 'closed-test-opt-in', url: 'https://play.google.com/apps/testing/' + PKG, instructions: '/testing' }
  const amazon = { kind: 'live-app-testing', instructions: '/testing' }
  assert.deepEqual(validateStores(stores({ state: 'testing', url: null, testing: play }, { state: 'testing', url: null, testing: amazon }), PKG), [])
  assert.match(validateStores(stores({ state: 'testing', url: null, testing: null }), PKG)[0], /tester route/)
})
test('testing routes: valid kinds pass, bad ones fail', () => {
  const ok = { kind: 'closed-test-opt-in', url: 'https://play.google.com/apps/testing/' + PKG, instructions: '/testing' }
  assert.deepEqual(validateStores(stores({ state: 'testing', url: PLAY, testing: ok }), PKG), [])
  assert.equal(validateStores(stores({ testing: { kind: 'telegram' } }), PKG).length, 1)
  assert.equal(validateStores(stores({ testing: { ...ok, url: 'https://example.com/join' } }), PKG).length, 1)
  assert.equal(validateStores(stores({ testing: { ...ok, instructions: 'https://example.com' } }), PKG).length, 1)
  assert.equal(validateStores(stores({ state: 'available', url: PLAY, testing: { ...ok, url: PLAY } }), PKG).length, 1)
})

const sha = (c) => c.repeat(64)
const record = (version = '1.0.0', over = {}) => ({
  version, tag: 'v' + version, repository: 'https://github.com/Rifaque/reflux', commit: 'a'.repeat(40), builtAt: '2027-01-01T00:00:00Z',
  artifacts: [
    { name: `reflux-${version}-android.apk`, platform: 'android', kind: 'apk', size: 10, sha256: sha('1'), distribution: 'store' },
    { name: `reflux-${version}-android.aab`, platform: 'android', kind: 'aab', size: 10, sha256: sha('2') },
    { name: `reflux-${version}-windows-x64.zip`, platform: 'windows', kind: 'zip', size: 1048576 * 90, sha256: sha('3'), distribution: 'github-release' },
    { name: `reflux-${version}-linux-x64.tar.gz`, platform: 'linux', kind: 'tar.gz', size: 1048576 * 80, sha256: sha('4') },
    { name: `reflux-${version}-libmpv-source.tar.xz`, platform: 'source', kind: 'source', size: 5, sha256: sha('5') },
  ],
  ...over,
})
const entry = (r) => ({ file: `${r.version}.json`, record: r })
const ctx = { version: '1.0.0', changelogVersions: ['Unreleased', '1.0.0'] }
const check = (r, c = ctx) => validatePublished([entry(r)], c)

test('no records is valid (the repository today)', () => assert.deepEqual(validatePublished([], ctx), []))
test('a good record is valid', () => assert.deepEqual(check(record()), []))
test('a record whose tag is not v + version fails (stale)', () => assert.match(check(record('1.0.0', { tag: 'v0.9.0' }))[0], /tag/))
test('a record whose file is named for another version fails', () => {
  assert.match(validatePublished([{ file: '0.9.0.json', record: record() }], ctx)[0], /must be 1\.0\.0\.json/)
})
test('a record naming artifacts of another version fails', () => {
  const r = record(); r.artifacts[2].name = 'reflux-0.9.0-windows-x64.zip'
  assert.match(check(r)[0], /not named reflux-1\.0\.0-/)
})
test('a record newer than the version in development fails', () => assert.match(check(record(), { ...ctx, version: '0.9.0' })[0], /newer than/))
test('a record without release notes in the changelog fails', () => assert.match(check(record(), { ...ctx, changelogVersions: ['Unreleased'] })[0], /CHANGELOG/))
test('a record without a commit, a checksum, or a repository fails', () => {
  assert.match(check(record('1.0.0', { commit: 'abc1234' }))[0], /commit/)
  const r = record(); r.artifacts[2].sha256 = 'x'
  assert.match(check(r)[0], /SHA-256/)
  assert.match(check(record('1.0.0', { repository: 'https://example.com/x' }))[0], /repository/)
})
test('an Android package marked as a GitHub distribution fails', () => {
  const r = record(); r.artifacts[0].distribution = 'github-release'
  assert.match(check(r)[0], /never distributed from GitHub/)
})
test('an unknown distribution fails', () => {
  const r = record(); r.artifacts[2].distribution = 'cdn'
  assert.match(check(r)[0], /distribution "cdn"/)
})
test('two records where the newer build is the lower version fail', () => {
  const a = record('1.0.0', { builtAt: '2027-01-01T00:00:00Z' }), b = record('0.9.0', { builtAt: '2027-02-01T00:00:00Z' })
  assert.match(validatePublished([entry(a), entry(b)], { version: '1.0.0', changelogVersions: ['1.0.0', '0.9.0'] })[0], /highest version/)
})
test('versions order alpha < beta < rc < release', () => {
  assert.ok(compareVersions('1.0.0-alpha.2', '1.0.0-beta.1') < 0)
  assert.ok(compareVersions('1.0.0-rc.1', '1.0.0') < 0)
  assert.ok(compareVersions('0.10.0', '0.9.0') > 0)
})
test('a missing distribution means a store for Android and GitHub for the rest', () => {
  assert.equal(distributionOf({ platform: 'android' }), 'store')
  assert.equal(distributionOf({ platform: 'windows' }), 'github-release')
  assert.equal(distributionOf({ platform: 'source' }), 'github-release')
})

test('presented records give GitHub artifacts an address and store ones none', () => {
  const [m] = presentPublished([entry(record())])
  const urls = Object.fromEntries(m.artifacts.map((a) => [a.kind, a.url]))
  assert.equal(urls.apk, undefined)
  assert.equal(urls.aab, undefined)
  assert.equal(urls.zip, 'https://github.com/Rifaque/reflux/releases/download/v1.0.0/reflux-1.0.0-windows-x64.zip')
  assert.ok(urls['tar.gz'] && urls.source)
  assert.equal(m.checksumsUrl, 'https://github.com/Rifaque/reflux/releases/download/v1.0.0/SHA256SUMS')
})

test('availability: nothing published, nothing listed', () => {
  const rows = availability({ stores: stores(), latest: null })
  assert.equal(rows.length, 5)
  assert.ok(rows.every((r) => r.state === 'not listed' && !r.url))
  assert.equal(rows[3].note, 'No public build yet')
})
test('availability: stores come from stores.json, desktop from the record', () => {
  const [latest] = presentPublished([entry(record())])
  const rows = availability({ stores: stores({ state: 'testing', url: PLAY }, { state: 'available', url: AMAZON }), latest })
  assert.deepEqual(rows.map((r) => r.state), ['testing', 'testing', 'available', 'available', 'available'])
  assert.equal(rows[0].url, null) // a testing listing is not a public link
  assert.equal(rows[2].url, AMAZON)
  assert.match(rows[3].url, /windows-x64\.zip$/)
  assert.match(rows[4].url, /linux-x64\.tar\.gz$/)
})
test('availability: a desktop platform missing from the record is not claimed', () => {
  const r = record(); r.artifacts = r.artifacts.filter((a) => a.platform !== 'linux')
  const [latest] = presentPublished([entry(r)])
  const rows = availability({ stores: stores(), latest })
  assert.equal(rows[4].state, 'not listed')
  assert.equal(rows[4].note, 'Not in Reflux 1.0.0')
})

// The site's "is this version out" (src/released.js), from publication records only.
test('a version counts as released only once it or a newer one is published', async () => {
  const { releasedIn } = await import('./release-lib.mjs')
  const beta1 = [{ version: '0.9.0-beta.1' }]
  assert.equal(releasedIn(beta1, '0.9.0-beta.2'), false)
  assert.equal(releasedIn(beta1, '0.9.0-beta.1'), true)
  assert.equal(releasedIn([{ version: '1.0.0' }], '0.9.0-beta.2'), true)
  assert.equal(releasedIn([], '0.9.0-beta.2'), false)
})
