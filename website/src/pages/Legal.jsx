import { Link } from 'react-router-dom'
import release from '../generated/release.json'
import showcase from '../showcase.json'
import { Out, Publisher } from '../components.jsx'

const Review = () => (
  <p className="quiet review">This page describes Reflux as it is built today. Its wording has not yet had legal review, which is due before Reflux is released (it is pre-release software, not yet distributed).</p>
)

/**
 * What Reflux does with data, as its code does it (docs/PRIVACY.md is the inventory this is written from).
 * Nothing here is a promise the code does not keep.
 */
export function Privacy() {
  return (
    <article className="page legal">
      <h1>Privacy</h1>
      <Review />
      <h2>In short</h2>
      <p>Reflux has no account, no analytics, no advertising, and no tracking. It sends nothing to RSX Develops, its maker, which runs no server for it. What it knows about your media stays on your device, apart from what is listed below: the requests to the servers and shares you add, and, while "Online metadata & artwork" is on (as it is unless you turn it off), the titles of your films and shows, looked up on Trakt.</p>

      <h2>What Reflux keeps on your device</h2>
      <ul>
        <li>Your library: the folders, servers, and shares you add, the titles found in them, their descriptions and pictures, what you have watched and where you stopped, your favorites and choices.</li>
        <li>How to reach your servers and shares. On Android it is encrypted with a key held in the system's keystore; on Windows it is kept in Windows Credential Manager; on Linux in the system's secret store where one runs, otherwise in Reflux's own files. Passwords for Jellyfin and Plex servers are used to sign in and are not kept; the session they open is.</li>
        <li>Copies you choose to keep for watching offline, in Reflux's own storage.</li>
        <li>Profiles: the people sharing a device each get their own watch state and choices. A profile's optional PIN is never stored: only a check value made with a key that the system's secure store holds (Credential Manager, the Secret Service, or the Android keystore). It is a lock between people sharing a device, not encryption of the library, and a forgotten PIN is not recovered. On Linux without a Secret Service the key is in Reflux's own files and the PIN locks the interface only.</li>
        <li>A diagnostics log of problems, with credentials and tokens removed. It never leaves your device unless you send it yourself.</li>
      </ul>
      <p>On Android, Reflux's data is excluded from device backups. Uninstalling Reflux removes it. On Windows and Linux it stays in Reflux's data folder (see <Link to="/download">Download</Link>) until you delete it.</p>

      <h2>What Reflux sends, and to whom</h2>
      <ul>
        <li><strong>Your own servers and shares</strong> (Jellyfin, Plex, SMB, WebDAV): when you add one, your user name and password, to sign in (Jellyfin and Plex passwords are used once and not kept; for a share, they are kept as described above). With a Jellyfin or Plex server, Reflux also names itself the way media apps do: "Reflux on" followed by your device's make and model (on a computer, its name on your network), an identifier Reflux makes for that account on this device, and Reflux's version. Then the requests needed to list and play your media, and where you stopped and what you have watched, to keep it in step with the server. They see what you play from them, as with any client. These connections are only as private as the address you give: an address that begins with "http" rather than "https" is not encrypted.</li>
        <li><strong>Plex</strong>: when you sign in with a Plex account, your e-mail address or user name and password go to plex.tv, Plex's service, with the same device name and identifier, to sign in and find your servers. Plex handles them under its own <Out href="https://www.plex.tv/about/privacy-legal/">privacy policy</Out>.</li>
        <li><strong>Trakt</strong> (trakt.tv), a third-party metadata service, not part of Reflux: to recognise a film or show and fetch its description and artwork, your device asks Trakt directly, over an encrypted connection. It sends the title Reflux read from the file's name or your server (or a catalog ID it already has, such as an IMDb or TMDB number), your language when it is not English, and Reflux's application identifier (its Trakt Client ID, which identifies the app, not you); Trakt sees your IP address, as any website does. Reflux then downloads the pictures Trakt names and keeps them on your device. Trakt handles these requests under its own <Out href="https://trakt.tv/privacy">privacy policy</Out>. It never sends the file's location, your servers' addresses, your sign-ins or tokens, your profiles or PINs, or anything else about your library or you, and it uses no Trakt account. Turn it off in Settings, "Online metadata & artwork": Reflux then sends Trakt nothing and fetches no new pictures from it, and uses your servers, your files, and what it already has.</li>
        <li>Nothing else. Reflux contains no analytics, crash reporting, or advertising service.</li>
      </ul>

      <h2>Removing what was sent or kept</h2>
      <p>Removing a server or share in Sources signs Reflux out of it and deletes what Reflux kept to reach it. Reflux cannot delete what your servers, plex.tv, or Trakt keep about their own requests: use their own settings for that. RSX Develops holds nothing about you to delete.</p>

      <h2>On Android TV and Google TV</h2>
      <p>To show "continue watching" on the home screen, Reflux writes the titles you are watching, with their pictures, into the system's TV provider, which the launcher reads, each naming the profile it belongs to. A profile with a PIN writes nothing there, because the launcher is outside the PIN's lock. On Fire TV it does not.</p>

      <h2>This website</h2>
      <p>This website sets no cookies, runs no analytics, and loads nothing from anyone else: its fonts and images are its own. It is hosted by Vercel, which handles the requests your browser makes to it (including your IP address) to serve its pages.</p>

      <h2>Children</h2>
      <p>Reflux plays media you already have. RSX Develops collects no information from anyone, children included; the lookups on Trakt described above carry the titles of films and shows, not information about the person watching.</p>

      <h2>Changes and contact</h2>
      <p>Changes to this page are listed in Reflux's <Link to="/releases">release notes</Link>. Questions: <Link to="/support">Support</Link>.</p>
    </article>
  )
}

export function Terms() {
  return (
    <article className="page legal">
      <h1>Terms</h1>
      <p className="lead">Reflux is free, made and published by RSX Develops, and proprietary. Its ownership notice:</p>
      <Publisher className="publisher publisher-terms" />
      <pre className="notice-text">{release.license}</pre>
      <p className="quiet review">Whether Reflux needs end-user terms beyond this notice and the stores' own terms is a question for its legal review, due before release. Until then, these are its terms.</p>
    </article>
  )
}

export function Notices() {
  return (
    <article className="page legal">
      <h1>Third-party notices</h1>
      <p className="lead">Reflux is built with the software below, each under its own license. Every build lists these with their full license texts in the app, under About Reflux; the texts are also in the legal folder of <a href="https://github.com/Rifaque/reflux" rel="noopener noreferrer">Reflux's source repository</a>.</p>
      <table>
        <thead><tr><th scope="col">Component</th><th scope="col">License</th><th scope="col">In</th></tr></thead>
        <tbody>
          {release.components.map((c) => (
            <tr key={c.name}><td>{c.name}</td><td>{c.license}</td><td>{c.targets.replace('android', 'Android').replace('desktop', 'Windows and Linux').replace(' ', ', ')}</td></tr>
          ))}
        </tbody>
      </table>
      <h2 id="libmpv-source">Source for the playback engine on Windows</h2>
      <p>
        Reflux for Windows includes libmpv, which is licensed under the LGPL. Its complete corresponding source, as built into the release, is offered
        {release.latest && release.latest.artifacts.find((a) => a.kind === 'source' && a.url)
          ? <> as <Out href={release.latest.artifacts.find((a) => a.kind === 'source').url}>{release.latest.artifacts.find((a) => a.kind === 'source').name}</Out>, beside the Windows download on Reflux’s GitHub release.</>
          : <> beside the Windows download on Reflux’s GitHub release, and will be linked here when a release is published.</>}
        {' '}Reflux for Linux uses the libmpv your distribution provides.
      </p>
      <h2>This website</h2>
      <p>Its type is Manrope, by The Manrope Project Authors, under the SIL Open Font License 1.1, served from this site; Reflux’s wordmark is drawn from it. The website is built with the open-source libraries below, which your browser receives as part of its pages. Their license texts: <a href="/third-party-licenses.txt">third-party-licenses.txt</a>.</p>
      <table>
        <thead><tr><th scope="col">Library</th><th scope="col">Version</th><th scope="col">License</th></tr></thead>
        <tbody>{release.site.map((p) => <tr key={p.name}><td>{p.name}</td><td>{p.version}</td><td>{p.license}</td></tr>)}</tbody>
      </table>
      <h2 id="trakt">Trakt</h2>
      <p>Reflux uses <Out href="https://trakt.tv">Trakt</Out> for film and show metadata and artwork: it identifies titles and fetches their descriptions and pictures from Trakt's public data, without a Trakt account. Reflux is an independent product.</p>
      <p><strong>Reflux uses Trakt but is not endorsed or certified by Trakt.</strong></p>
      <h2 id="media-servers">Media servers Reflux works with</h2>
      <p>Reflux connects to Jellyfin and Plex servers that you run or have access to. It is an independent client: it is not an official Jellyfin or Plex app, and it is not affiliated with, endorsed by, or certified by the Jellyfin project or Plex, Inc. Their names are used only to say that Reflux works with them, and Reflux uses neither one's logo. Jellyfin is the Jellyfin project's name. Plex and Plex Media Server are trademarks of Plex and used under a license.</p>
      <h2 id="artwork">Artwork on this site</h2>
      <p>The films in the pictures of Reflux on this site are the Blender Foundation's open films; their posters and stills are used under their Creative Commons licenses (one is dedicated to the public domain), unchanged apart from being scaled and shown inside Reflux. The titles' descriptions are Reflux's own words. The films' titles and the logos on some posters belong to their owners and are not licensed by these Creative Commons licenses.</p>
      <table>
        <thead><tr><th scope="col">Artwork</th><th scope="col">By</th><th scope="col">License</th></tr></thead>
        <tbody>{showcase.artwork.map((a) => <tr key={a.file}><td><Out href={a.source}>{a.file}</Out></td><td>{a.author}</td><td><Out href={a.licenseUrl}>{a.license}</Out></td></tr>)}</tbody>
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
      <p>{release.latest ? 'Every Windows and Linux build is published' : 'No build has been published yet. When one is, each Windows and Linux build will be published'} on Reflux's GitHub releases with its SHA-256 checksum, the commit it was built from, and GitHub's build provenance. Windows builds are not signed with a publisher certificate yet: check the checksum before opening one (see <Link to="/download">Download</Link>).</p>
      <h2>Other questions</h2>
      <p>For anything that is not a vulnerability, e-mail <a href="mailto:support@rsxdevelops.dev">support@rsxdevelops.dev</a> or see <Link to="/support">Support</Link>.</p>
      <h2>How Reflux handles your sign-ins</h2>
      <p>See <Link to="/privacy">Privacy</Link>, and the project's <a href="https://github.com/Rifaque/reflux/blob/main/SECURITY.md" rel="noopener noreferrer">security policy</a>.</p>
    </article>
  )
}
