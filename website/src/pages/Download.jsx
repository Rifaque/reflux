import { Link } from 'react-router-dom'
import release from '../generated/release.json'
import { Out, PageHead } from '../components.jsx'
import { STATE_LABELS, availability, desktopFile } from '../availability.js'

const latest = release.latest
const megabytes = (bytes) => `${Math.round(bytes / 1048576)} MB`
const notes = latest ? release.changelog.find((s) => s.version === latest.version) : null

/** A store's listing, once it exists; until then, a plain word about when. The site never offers an Android file. */
function Store({ name, listing }) {
  if (listing?.state === 'available' && listing.url) return <Out className="button" href={listing.url}>Get it on {name}</Out>
  if (listing?.state === 'testing') return <p className="quiet">In testing on {name}, not yet open to everyone. <Link to="/testing">How testing works</Link>.</p>
  return <p className="quiet">Not listed yet. Reflux will be on {name} when testing opens; it will be linked here.</p>
}

/** A desktop build from Reflux's GitHub release, with what to check it against. */
function Desktop({ platform }) {
  const file = desktopFile(latest, platform)
  const source = latest?.artifacts.find((a) => a.kind === 'source' && a.distribution === 'github-release')
  if (!file) return <p className="quiet">No public build yet. Desktop builds will come from Reflux’s official GitHub releases, linked here with their checksums.</p>
  return (
    <>
      <p><Out className="button" href={file.url}>Download for {platform === 'windows' ? 'Windows' : 'Linux'}</Out></p>
      <p className="small quiet">{file.name}, {megabytes(file.size)}, Reflux {latest.version}, built from commit <code>{latest.commit.slice(0, 7)}</code>. <Out href={latest.releaseUrl}>Release page</Out>.</p>
      <p className="checksum small">SHA-256 <code>{file.sha256}</code> · <Out href={latest.checksumsUrl}>SHA256SUMS</Out></p>
      {platform === 'windows' && source && <p className="small quiet">The complete source of the playback engine inside it, offered under its license: <Out href={source.url}>{source.name}</Out>.</p>}
    </>
  )
}

function Where({ row }) {
  if (row.url) return <Out href={row.url}>{row.channel === 'GitHub Release' ? `Reflux ${row.version}` : 'Open the listing'}</Out>
  return <span className="quiet">{row.note ?? (row.state === 'testing' ? 'Invited testers only' : 'Not yet')}</span>
}

export default function Download() {
  const rows = availability({ stores: release.stores, latest })
  const windowsName = desktopFile(latest, 'windows')?.name ?? 'reflux-<version>-windows-x64.zip'
  const linuxName = desktopFile(latest, 'linux')?.name ?? 'reflux-<version>-linux-x64.tar.gz'
  return (
    <article className="page">
      <PageHead eyebrow="Download" title="Get Reflux.">
        <p>
          {latest ? `Reflux ${latest.version}.` : `Reflux ${release.version} is in development and has not been released yet.`} Android and Google TV come from
          Google Play, Fire TV from the Amazon Appstore, and Windows and Linux from Reflux's GitHub releases. Nothing else is official.
          {' '}<Link to="/testing">How testing will work</Link>.
        </p>
      </PageHead>

      <section aria-labelledby="availability">
        <h2 id="availability">Where each platform stands</h2>
        <div className="table-wrap">
          <table>
            <thead><tr><th scope="col">Platform</th><th scope="col">From</th><th scope="col">State</th><th scope="col">Link</th></tr></thead>
            <tbody>{rows.map((r) => <tr key={r.name}><th scope="row">{r.name}</th><td>{r.channel}</td><td>{STATE_LABELS[r.state]}</td><td><Where row={r} /></td></tr>)}</tbody>
          </table>
        </div>
      </section>

      <div className="download-grid">
        <section className="download" aria-labelledby="android">
          <h2 id="android">Android phones, tablets, Android TV, and Google TV</h2>
          <p className="quiet small">Android 8.0 or later. Install from Google Play and it updates there; there is no file to download.</p>
          <Store name="Google Play" listing={release.stores.googlePlay} />
        </section>
        <section className="download" aria-labelledby="fire-tv">
          <h2 id="fire-tv">Fire TV</h2>
          <p className="quiet small">Fire OS 7 or later. Not for devices on Vega OS. Install from the Appstore and it updates there.</p>
          <Store name="the Amazon Appstore" listing={release.stores.amazonAppstore} />
        </section>
        <section className="download" aria-labelledby="windows">
          <h2 id="windows">Windows</h2>
          <p className="quiet small">Windows 10 or 11, 64-bit. A zip, with the playback engine inside.</p>
          <Desktop platform="windows" />
          <h3>Install, update, remove</h3>
          <ol className="plain numbered small">
            <li>Extract <code>{windowsName}</code> to a folder you will keep.</li>
            <li>Open <code>Reflux.exe</code> in it.</li>
            <li><strong>Update:</strong> extract the newer zip to a new folder, then delete the old folder. Your library stays in <code>%LOCALAPPDATA%\Reflux</code>.</li>
            <li><strong>Remove:</strong> delete the folder. To remove your library too, delete <code>%LOCALAPPDATA%\Reflux</code> and the “Reflux” entries in Windows Credential Manager.</li>
          </ol>
        </section>
        <section className="download" aria-labelledby="linux">
          <h2 id="linux">Linux</h2>
          <p className="quiet small">64-bit, on X11 or through XWayland. A tar.gz with its own Java runtime; it plays through your distribution’s libmpv.</p>
          <Desktop platform="linux" />
          <h3>Install, update, remove</h3>
          <ol className="plain numbered small">
            <li>Install libmpv: <code>libmpv2</code> on Debian 12 or later and Ubuntu 24.04 or later, <code>mpv-libs</code> on Fedora. Ubuntu 22.04 does not package <code>libmpv2</code>, so there Reflux needs libmpv installed another way, or a newer Ubuntu.</li>
            <li>To keep server sign-ins in your desktop’s Secret Service, install <code>secret-tool</code> (<code>libsecret-tools</code> on Debian and Ubuntu, <code>libsecret</code> on Fedora). Without it Reflux keeps them in its own files and says so in its log.</li>
            <li>Extract <code>{linuxName}</code> (<code>tar -xzf</code>) and run <code>bin/Reflux</code>.</li>
            <li><strong>Update:</strong> extract the newer archive to a fresh folder, then remove the old one. Your library in <code>~/.local/share/reflux</code> is not touched.</li>
            <li><strong>Remove:</strong> delete the folder; delete <code>~/.local/share/reflux</code> as well to remove your library.</li>
          </ol>
        </section>
      </div>

      <aside className="notice" aria-labelledby="unsigned">
        <h2 id="unsigned">About Windows security warnings</h2>
        <p>Reflux for Windows is not yet signed with a publisher certificate, so Windows may show “Windows protected your PC” the first time you open it. That is expected for this build.</p>
        <p>Download it only from this site or Reflux’s GitHub releases, and check that its SHA-256 matches the one above before you open it (see below). If it does not match, do not open it.</p>
        <p>Keep SmartScreen and your other Windows protections on. Where Smart App Control is on, Windows does not run unsigned apps it does not know, and this build will not open there; Reflux will never ask you to turn it off. Signed builds are planned.</p>
      </aside>

      <section aria-labelledby="verify">
        <h2 id="verify">Check what you downloaded</h2>
        <p>
          Every desktop release lists the SHA-256 of each file
          {latest ? <> in <Out href={latest.checksumsUrl}>SHA256SUMS</Out>, with the <Out href={latest.manifestUrl}>manifest</Out> and the <Out href={latest.provenanceUrl}>build provenance</Out> beside it</> : ', and publishes SHA256SUMS, the manifest, and the build provenance beside the files'}.
          Put the file and SHA256SUMS in one folder, then:
        </p>
        <pre className="notice-text">{`# Linux (and macOS)
sha256sum -c --ignore-missing SHA256SUMS

# Windows PowerShell
Get-FileHash .\\${windowsName} -Algorithm SHA256`}</pre>
        <p>On Windows, compare the hash it prints with the line for that file in SHA256SUMS. For a stronger check, GitHub’s CLI verifies the file’s attestation:</p>
        <pre className="notice-text">{`gh attestation verify ${linuxName} -R Rifaque/reflux --signer-workflow Rifaque/reflux/.github/workflows/release.yml`}</pre>
        <p className="small quiet">An attestation proves that GitHub saw Reflux’s release workflow build exactly this file from the commit it names, and that the file has not changed since. It does not say the software has no bugs, and it is not a publisher signature.</p>
      </section>

      <section aria-labelledby="notes">
        <h2 id="notes">{latest ? `What is in Reflux ${latest.version}` : 'What is coming'}</h2>
        {notes
          ? <ul className="changes">{notes.items.slice(0, 8).map((item, i) => <li key={i}>{item}</li>)}</ul>
          : <p>Reflux {release.version} is in development.</p>}
        <p><Link to="/releases" className="more">All release notes</Link></p>
      </section>

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
