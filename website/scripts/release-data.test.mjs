// The build step end to end, over temporary publication records (RELEASE_PUBLISHED_DIR, RELEASE_DATA_OUT): a good record
// is presented, and a stale one stops the build. Nothing is written under release/published/.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, writeFileSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const root = join(here, '..', '..')
// The newest released section of the changelog: a version the changelog has notes for and that is not ahead of the repository.
const released = /^## (\d+\.\d+\.\d+(?:-(?:alpha|beta|rc)\.\d+)?) — /m.exec(readFileSync(join(root, 'CHANGELOG.md'), 'utf8'))[1]

const record = (version, over = {}) => ({
  schema: 1, version, tag: 'v' + version, repository: 'https://github.com/Rifaque/reflux', commit: 'b'.repeat(40), builtAt: '2027-01-01T00:00:00Z',
  artifacts: [
    { name: `reflux-${version}-android.apk`, platform: 'android', kind: 'apk', size: 10, sha256: '1'.repeat(64), distribution: 'store' },
    { name: `reflux-${version}-windows-x64.zip`, platform: 'windows', kind: 'zip', size: 94371840, sha256: '3'.repeat(64), distribution: 'github-release' },
  ],
  ...over,
})
const run = (records) => {
  const dir = mkdtempSync(join(tmpdir(), 'reflux-published-'))
  for (const [file, r] of Object.entries(records)) writeFileSync(join(dir, file), JSON.stringify(r))
  const out = join(dir, 'out', 'release.json')
  const result = spawnSync(process.execPath, [join(here, 'release-data.mjs')], { env: { ...process.env, RELEASE_PUBLISHED_DIR: dir, RELEASE_DATA_OUT: out }, encoding: 'utf8' })
  return { ...result, data: result.status === 0 ? JSON.parse(readFileSync(out, 'utf8')) : null }
}

test('a good fixture record becomes the latest release, with no address for the Android package', () => {
  const r = run({ [`${released}.json`]: record(released) })
  assert.equal(r.status, 0, r.stderr)
  assert.equal(r.data.latest.version, released)
  const apk = r.data.latest.artifacts.find((a) => a.kind === 'apk')
  assert.equal(apk.url, undefined)
  assert.match(r.data.latest.artifacts.find((a) => a.kind === 'zip').url, /\/releases\/download\/v.+\/reflux-.+-windows-x64\.zip$/)
})
test('a record whose tag disagrees with its version stops the build', () => {
  const r = run({ [`${released}.json`]: record(released, { tag: 'v0.0.1' }) })
  assert.notEqual(r.status, 0)
  assert.match(r.stderr, /tag/)
})
test('a record naming another version in its artifacts stops the build', () => {
  const stale = record(released)
  stale.artifacts[1].name = 'reflux-0.0.1-windows-x64.zip'
  const r = run({ [`${released}.json`]: stale })
  assert.notEqual(r.status, 0)
  assert.match(r.stderr, /not named reflux-/)
})
test('a record newer than the repository stops the build', () => {
  const r = run({ '99.0.0.json': record('99.0.0') })
  assert.notEqual(r.status, 0)
  assert.match(r.stderr, /newer than the version in development/)
})

test('changelog groups ("### Title") are kept with their items, and every item stays in the flat list too', () => {
  const r = run({})
  assert.equal(r.status, 0, r.stderr)
  const unreleased = r.data.changelog.find((c) => c.version === 'Unreleased')
  if (!unreleased.groups.length) return
  assert.deepEqual(unreleased.groups.flatMap((g) => g.items), unreleased.items, 'a grouped section has no item outside its groups')
  for (const group of unreleased.groups) assert.ok(group.title && group.items.length, `group ${group.title} has items`)
})
