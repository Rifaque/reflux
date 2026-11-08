import { Link } from 'react-router-dom'
import release from '../generated/release.json'
import { Out, PageHead } from '../components.jsx'

const latest = release.latest
const artifact = (platform) => latest?.artifacts.find((a) => a.platform === platform)
const megabytes = (bytes) => `${Math.round(bytes / 1048576)} MB`

/** A store's listing, once it exists; until then, a plain word about when. The site never offers an Android file. */
function Store({ name, listing }) {
  if (listing?.url) return <Out className="button" href={listing.url}>Get it on {name}</Out>
  return <p className="quiet">Not listed yet. Reflux will be on {name} when testing opens; it will be linked here.</p>
}

/** A desktop build from Reflux's GitHub release, with what to check it against. */
function Desktop({ platform }) {
  const file = artifact(platform)
  const source = latest?.artifacts.find((a) => a.kind === 'source')
  if (!file) return <p className="quiet">No build has been published yet. Desktop builds will come from Reflux’s official GitHub releases, linked here with their checksums.</p>
  return (
    <>
      <p><Out className="button" href={file.url}>Download for {platform === 'windows' ? 'Windows' : 'Linux'}</Out></p>
      <p className="small quiet">{file.name}, {megabytes(file.size)}, Reflux {latest.version}, built from commit <code>{latest.commit.slice(0, 7)}</code>.</p>
      <p className="checksum small">SHA-256 <code>{file.sha256}</code> · <Out href={latest.checksumsUrl}>SHA256SUMS</Out></p>
      {platform === 'windows' && source && <p className="small quiet">The source of the playback engine inside it: <Out href={source.url}>{source.name}</Out>.</p>}
    </>
  )
}

export default function Download() {
  return (
    <article className="page">
      <PageHead eyebrow="Download" title="Get Reflux.">
        <p>
          {latest ? `Reflux ${latest.version}.` : `Reflux ${release.version} is in development and has not been released yet.`} Android and Google TV come from
          Google Play, Fire TV from the Amazon Appstore, and Windows and Linux from Reflux's GitHub releases. Nothing else is official.
          {' '}<Link to="/testing">How testing will work</Link>.
        </p>
      </PageHead>

      <div className="download-grid">
        <section className="download" aria-labelledby="android">
          <h2 id="android">Android phones, tablets, Android TV, and Google TV</h2>
          <p className="quiet small">Android 8.0 or later.</p>
          <Store name="Google Play" listing={release.stores.googlePlay} />
        </section>
        <section className="download" aria-labelledby="fire-tv">
          <h2 id="fire-tv">Fire TV</h2>
          <p className="quiet small">Fire OS 7 or later. Not for devices on Vega OS.</p>
          <Store name="the Amazon Appstore" listing={release.stores.amazonAppstore} />
        </section>
        <section className="download" aria-labelledby="windows">
          <h2 id="windows">Windows</h2>
          <p className="quiet small">Windows 10 or 11, 64-bit. A zip: extract it and open Reflux.exe; delete the folder to remove it.</p>
          <Desktop platform="windows" />
        </section>
        <section className="download" aria-labelledby="linux">
          <h2 id="linux">Linux</h2>
          <p className="quiet small">64-bit. A tar.gz with its own Java runtime; it plays through your distribution’s libmpv (<code>libmpv2</code> on Debian and Ubuntu, <code>mpv-libs</code> on Fedora). Verify with <code>sha256sum -c SHA256SUMS</code>.</p>
          <Desktop platform="linux" />
        </section>
      </div>

      <aside className="notice" aria-labelledby="unsigned">
        <h2 id="unsigned">About Windows security warnings</h2>
        <p>Reflux for Windows is not yet signed with a publisher certificate, so Windows may show “Windows protected your PC” the first time you open it. That is expected for this build.</p>
        <p>Download it only from this site or Reflux’s GitHub releases, and check that its SHA-256 matches the one above before you open it. In PowerShell: <code>Get-FileHash .\reflux-…-windows-x64.zip</code>. If it does not match, do not open it.</p>
        <p>Keep SmartScreen and your other Windows protections on. Where Smart App Control is on, Windows does not run unsigned apps it does not know, and this build will not open there; Reflux will never ask you to turn it off. Signed builds are planned.</p>
      </aside>

      <section aria-labelledby="where-data">
        <h2 id="where-data">Where Reflux keeps your library</h2>
        <ul className="plain">
          <li><strong>Windows:</strong> %LOCALAPPDATA%\Reflux, and server sign-ins in Windows Credential Manager under “Reflux”.</li>
          <li><strong>Linux:</strong> ~/.local/share/reflux, and sign-ins in your desktop’s secret store where it has one.</li>
          <li><strong>Android, TVs:</strong> Reflux’s own storage, removed when you uninstall it.</li>
        </ul>
      </section>
    </article>
  )
}
