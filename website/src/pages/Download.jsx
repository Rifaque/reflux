// Download (docs/WEBSITE.md, "Download"): one card per platform, each saying where Reflux comes from for it, what it
// needs, and the one thing to do next, with its install guide one step away. Every version, file, size, and state is
// the release data's (scripts/release-data.mjs): the publication record and the store records. The site never offers an
// Android file: Android and Fire TV come only from their stores (docs/DECISIONS.md, 2026-11-15).
import { Link } from 'react-router-dom'
import release from '../generated/release.json'
import { Out, PageHead } from '../components.jsx'
import { STATE_LABELS, desktopFile } from '../availability.js'

const latest = release.latest
const megabytes = (bytes) => `${Math.round(bytes / 1048576)} MB`

/** A store-delivered platform: its state, and the way in (the listing once public, the beta while testing). */
function StoreAction({ listing, beta, name }) {
  if (listing?.state === 'available' && listing.url) return <Out className="button" href={listing.url}>Get it on {name}</Out>
  if (listing?.state === 'testing') return <Link className="button" to={`/testing#${beta}`}>Join the beta</Link>
  return <span className="quiet small">Not listed yet</span>
}

/** A GitHub-delivered platform: the file, straight from the release (never through this site), with its size and hash. */
function DesktopAction({ platform, label }) {
  const file = desktopFile(latest, platform)
  if (!file) return <span className="quiet small">No public build yet</span>
  return (
    <>
      <Out className="button" href={file.url}>Download for {label}</Out>
      <p className="small quiet file-facts">{file.name} · {megabytes(file.size)}</p>
      <p className="small quiet checksum">SHA-256 <code title={file.sha256}>{file.sha256.slice(0, 16)}…</code> · <Out href={latest.checksumsUrl}>SHA256SUMS</Out> · <Link to="/docs/install/verify">How to check</Link></p>
    </>
  )
}

function State({ state }) {
  return <span className={`state state-${state}`}>{STATE_LABELS[state] ?? state}</span>
}

const desktopState = (platform) => (desktopFile(latest, platform) ? (latest.version.includes('-') ? 'beta' : 'available') : 'not listed')

export default function Download() {
  const play = release.stores.googlePlay
  const amazon = release.stores.amazonAppstore
  const cards = [
    {
      id: 'windows', name: 'Windows', state: desktopState('windows'), guide: '/docs/install/windows',
      facts: ['Windows 10 or 11, 64-bit (x64)', 'A zip: the playback engine and Java runtime included', 'Not yet publisher-signed: Windows may warn once'],
      action: <DesktopAction platform="windows" label="Windows" />,
    },
    {
      id: 'linux', name: 'Linux', state: desktopState('linux'), guide: '/docs/install/linux',
      facts: ['64-bit x86, on X11 or XWayland', 'A tar.gz with its own Java runtime', 'Needs your distribution’s libmpv: one command, in the guide'],
      action: <DesktopAction platform="linux" label="Linux" />,
    },
    {
      id: 'android', name: 'Android phones and tablets', state: play?.state ?? 'not listed', guide: '/docs/install/android',
      facts: ['Android 8.0 or later', 'From Google Play; it updates there', 'Closed beta: invited Google accounts'],
      action: <StoreAction listing={play} beta="android" name="Google Play" />,
    },
    {
      id: 'android-tv', name: 'Android TV and Google TV', state: play?.state ?? 'not listed', guide: '/docs/install/android-tv',
      facts: ['Android 8.0 or later', 'From Google Play on the TV', 'Closed beta: invited Google accounts'],
      action: <StoreAction listing={play} beta="android" name="Google Play" />,
    },
    {
      id: 'fire-tv', name: 'Fire TV', state: amazon?.state ?? 'not listed', guide: '/docs/install/fire-tv',
      facts: ['Fire OS 7 or later (not Vega OS)', 'From the Amazon Appstore, through Beta Hub', 'Beta by invitation: Amazon accounts'],
      action: <StoreAction listing={amazon} beta="fire-tv" name="the Amazon Appstore" />,
    },
  ]
  return (
    <article className="page">
      <PageHead eyebrow="Download" title="Get Reflux.">
        <p>
          {latest ? `Reflux ${latest.version}.` : `Reflux ${release.version} is in development and has not been released yet.`}
          {latest?.version.includes('-') && ' A beta until 15 December 2026; Reflux 1.0 is planned for December.'} Free on every platform,
          from the places below and nowhere else.
        </p>
      </PageHead>

      <ul className="platform-cards">
        {cards.map((card) => (
          <li key={card.id} id={card.id} className="platform-card">
            <div className="platform-card-head">
              <h2>{card.name}</h2>
              <State state={card.state} />
            </div>
            <ul className="platform-facts">{card.facts.map((f) => <li key={f}>{f}</li>)}</ul>
            <div className="platform-action">{card.action}</div>
            <Link to={card.guide} className="more small" viewTransition>Installation guide</Link>
          </li>
        ))}
      </ul>

      <section aria-labelledby="more" className="download-more">
        <h2 id="more">Before and after you download</h2>
        <ul className="plain">
          <li><Link to="/docs/install/verify">Check a download</Link> against its published SHA-256 and build attestation{latest && <> (<Out href={latest.releaseUrl}>the {latest.version} release on GitHub</Out>)</>}.</li>
          <li><Link to="/releases">Release notes</Link>: what changed in each version.</li>
          <li><Link to="/platforms">Platforms and compatibility</Link>: what each device plays.</li>
          <li><Link to="/testing">The beta</Link>: how to join on Android and Fire TV, and how to report a problem.</li>
        </ul>
        <p className="small quiet">Windows and Linux files are delivered by GitHub's release service directly; this site only links to them. Reflux is free and open source (<Link to="/license">License</Link>).</p>
      </section>
    </article>
  )
}
