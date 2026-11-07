import { Link } from 'react-router-dom'
import release from '../generated/release.json'

const Review = () => (
  <p className="quiet review">This page describes Reflux as it is built today. Its wording has not yet had legal review, which is due before Reflux is released (it is pre-release software, not yet distributed).</p>
)

/**
 * What Reflux does with data, as its code does it (docs/PRIVACY-INVENTORY.md is the inventory this is written from).
 * Nothing here is a promise the code does not keep.
 */
export function Privacy() {
  return (
    <article className="page legal">
      <h1>Privacy</h1>
      <Review />
      <h2>In short</h2>
      <p>Reflux has no account, no analytics, no advertising, and no tracking. It sends nothing to RSX Develops, its maker. What it knows about your media stays on your device, apart from what it must send to the servers and services you choose to use, listed below.</p>

      <h2>What Reflux keeps on your device</h2>
      <ul>
        <li>Your library: the folders, servers, and shares you add, the titles found in them, their descriptions and pictures, what you have watched and where you stopped, your favorites and choices.</li>
        <li>How to reach your servers and shares. On Android it is encrypted with a key held in the system's keystore; on Windows it is kept in Windows Credential Manager; on Linux in the system's secret store where one runs, otherwise in Reflux's own files. Passwords for Jellyfin and Plex servers are used to sign in and are not kept; the session they open is.</li>
        <li>Copies you choose to keep for watching offline, in Reflux's own storage.</li>
        <li>A diagnostics log of problems, with credentials and tokens removed. It never leaves your device unless you send it yourself.</li>
      </ul>
      <p>On Android, Reflux's data is excluded from device backups. Uninstalling Reflux removes it. On Windows and Linux it stays in Reflux's data folder (see <Link to="/download">Download</Link>) until you delete it.</p>

      <h2>What Reflux sends, and to whom</h2>
      <ul>
        <li><strong>Your own servers and shares</strong> (Jellyfin, Plex, SMB, WebDAV): the requests needed to list and play your media and to keep what you watched in step with them. They see what you play from them, as with any client.</li>
        <li><strong>Plex</strong>: when you sign in with a Plex account, your sign-in goes to plex.tv, Plex's service, to find your servers.</li>
        <li><strong>Metadata lookups</strong>: public builds of Reflux do not look anything up online. They use what your servers provide and what is in your files' names. If online lookups are offered in a later release, this page will say what is sent and to whom before they are.</li>
        <li>Nothing else. Reflux contains no analytics, crash reporting, or advertising service.</li>
      </ul>

      <h2>On Android TV and Google TV</h2>
      <p>To show "continue watching" on the home screen, Reflux writes the titles you are watching, with their pictures, into the system's TV provider, which the launcher reads. On Fire TV it does not.</p>

      <h2>This website</h2>
      <p>This website sets no cookies and runs no analytics. It is hosted by Vercel, which handles the requests your browser makes to it (including your IP address) to serve its pages.</p>

      <h2>Children</h2>
      <p>Reflux plays media you already have; it does not collect information from anyone, children included.</p>

      <h2>Changes and contact</h2>
      <p>Changes to this page are listed in Reflux's <Link to="/releases">release notes</Link>. Questions: <Link to="/support">Support</Link>.</p>
    </article>
  )
}

export function Terms() {
  return (
    <article className="page legal">
      <h1>Terms</h1>
      <p className="lead">Reflux is free, made by RSX Develops, and proprietary. Its ownership notice:</p>
      <pre className="notice-text">{release.license}</pre>
      <p className="quiet review">Whether Reflux needs end-user terms beyond this notice and the stores' own terms is a question for its legal review, due before release. Until then, these are its terms.</p>
    </article>
  )
}

export function Notices() {
  return (
    <article className="page legal">
      <h1>Third-party notices</h1>
      <p className="lead">Reflux is built with the software below, each under its own license. Every build lists these with their full license texts on its About page; the texts are also in the legal folder of <a href="https://github.com/Rifaque/reflux" rel="noopener noreferrer">Reflux's source repository</a>.</p>
      <table>
        <thead><tr><th scope="col">Component</th><th scope="col">License</th><th scope="col">In</th></tr></thead>
        <tbody>
          {release.components.map((c) => (
            <tr key={c.name}><td>{c.name}</td><td>{c.license}</td><td>{c.targets.replace('android', 'Android').replace('desktop', 'Windows and Linux').replace(' ', ', ')}</td></tr>
          ))}
        </tbody>
      </table>
      <h2>This website</h2>
      <p>The website is built with the open-source libraries below, which your browser receives as part of its pages. Their license texts: <a href="/third-party-licenses.txt">third-party-licenses.txt</a>.</p>
      <table>
        <thead><tr><th scope="col">Library</th><th scope="col">Version</th><th scope="col">License</th></tr></thead>
        <tbody>{release.site.map((p) => <tr key={p.name}><td>{p.name}</td><td>{p.version}</td><td>{p.license}</td></tr>)}</tbody>
      </table>
    </article>
  )
}

export function Security() {
  return (
    <article className="page legal">
      <h1>Security</h1>
      <h2>Reporting a problem</h2>
      <p>Report security problems privately through <a href="https://github.com/Rifaque/reflux/security/advisories/new" rel="noopener noreferrer">GitHub's private vulnerability reporting</a> for Reflux, not in a public issue. Please include what is affected and how to reproduce it.</p>
      <h2>Verifying a download</h2>
      <p>Every Windows and Linux build is published on Reflux's GitHub releases with its SHA-256 checksum and the commit it was built from. Windows builds are not signed with a publisher certificate yet: check the checksum before opening one (see <Link to="/download">Download</Link>).</p>
      <h2>How Reflux handles your sign-ins</h2>
      <p>See <Link to="/privacy">Privacy</Link>, and the project's <a href="https://github.com/Rifaque/reflux/blob/main/SECURITY.md" rel="noopener noreferrer">security policy</a>.</p>
    </article>
  )
}
