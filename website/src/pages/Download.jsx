import { Link } from 'react-router-dom'
import release from '../generated/release.json'

const latest = release.latest
const artifact = (platform, kind) => latest?.artifacts.find((a) => a.platform === platform && (!kind || a.kind === kind))
const megabytes = (bytes) => `${Math.round(bytes / 1048576)} MB`

/** Where each platform gets Reflux: a store where there is one, the official GitHub release where there is not. */
function Store({ name, listing }) {
  if (listing?.url) return <p><a href={listing.url} rel="noopener noreferrer">Get Reflux on {name}</a></p>
  return <p className="quiet">Not on {name} yet. It will be listed here, from {name} itself, when testing opens.</p>
}

function Desktop({ platform, label, children }) {
  const file = artifact(platform)
  return (
    <section className="platform" id={platform}>
      <h2>{label}</h2>
      {file ? (
        <>
          <p>
            <a className="button" href={file.url} rel="noopener noreferrer">Download {file.name}</a>{' '}
            <span className="quiet">{megabytes(file.size)}, Reflux {latest.version}, from GitHub</span>
          </p>
          <p className="checksum">SHA-256 <code>{file.sha256}</code> (<a href={latest.checksumsUrl} rel="noopener noreferrer">SHA256SUMS</a>, built from commit <code>{latest.commit.slice(0, 7)}</code>)</p>
        </>
      ) : (
        <p className="quiet">No build has been published yet. Desktop builds will be downloaded from Reflux's official GitHub releases, linked from here.</p>
      )}
      {children}
    </section>
  )
}

export default function Download() {
  return (
    <article className="page">
      <h1>Download</h1>
      <p className="lead">
        Reflux {release.version} is in development and has not been released. This page will link every build that is,
        and nothing else: Android from Google Play, Fire TV from the Amazon Appstore, and Windows and Linux from Reflux's
        GitHub releases. <Link to="/testers">How testing will work</Link>.
      </p>

      <section className="platform" id="android">
        <h2>Android phones, tablets, Android TV, and Google TV</h2>
        <Store name="Google Play" listing={release.stores.googlePlay} />
      </section>

      <section className="platform" id="fire-tv">
        <h2>Fire TV</h2>
        <Store name="the Amazon Appstore" listing={release.stores.amazonAppstore} />
        <p className="quiet">Fire TV devices on Fire OS. Devices on Vega OS do not run Android applications.</p>
      </section>

      <Desktop platform="windows" label="Windows (64-bit)">
        <aside className="notice" aria-label="About Windows builds">
          <h3>Windows builds are not signed yet</h3>
          <p>
            Reflux for Windows is not yet signed with a publisher certificate, so Windows may show a warning such as
            "Windows protected your PC" the first time you open it. That is expected for this build.
          </p>
          <p>
            Download Reflux only from this website or from Reflux's GitHub releases, and check that the file's SHA-256
            matches the one published here before you open it. In PowerShell: <code>Get-FileHash .\reflux-…-windows-x64.zip</code>.
            If it does not match, do not open it.
          </p>
          <p>
            Keep Windows security features such as SmartScreen turned on. If Smart App Control is on, Windows does not
            run unsigned apps it does not recognize, and this build of Reflux will not open there; that is by design,
            and Reflux does not ask you to change it. Signed Windows builds are planned.
          </p>
        </aside>
        <p className="quiet">A zip: extract it anywhere you like and open Reflux.exe. To remove Reflux, delete that folder. Your library is kept in %LOCALAPPDATA%\Reflux, and server and share sign-ins in Windows Credential Manager (under "Reflux"), until you delete them too.</p>
      </Desktop>

      <Desktop platform="linux" label="Linux (64-bit)">
        <p className="quiet">
          A tar.gz with its own Java runtime. It plays through your distribution's libmpv:{' '}
          <code>sudo apt install libmpv2</code> (Debian, Ubuntu) or <code>sudo dnf install mpv-libs</code> (Fedora).
          Verify with <code>sha256sum -c SHA256SUMS</code>. Your library is kept in ~/.local/share/reflux.
        </p>
      </Desktop>
    </article>
  )
}
