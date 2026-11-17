import { Link } from 'react-router-dom'
import release from '../generated/release.json'
import { Out, PageHead, Timeline } from '../components.jsx'
import { desktopFile } from '../availability.js'
import { JOIN_MAILTO, REPORT_FIELDS, REPORT_MAILTO, SEASON, SUPPORT, V1_WINDOW } from '../beta.js'

const latest = release.latest
const play = release.stores.googlePlay
const amazon = release.stores.amazonAppstore

/** What each platform's tester does, in order. Store links come from release/stores.json, the files from the record. */
function Platform({ id, name, status, children }) {
  return (
    <section className="download" aria-labelledby={id}>
      <h3 id={id}>{name}</h3>
      <p className="quiet small">{status}</p>
      {children}
    </section>
  )
}

const knownIssues = [
  'On Fire TV and Android TV the interface can look large, as if zoomed in. It is Reflux’s ten-foot sizing (a safe margin around the screen and slightly larger type), not your TV’s settings; we are reviewing the scale.',
  'On a TV, the focus ring around a poster in the Movies grid can overlap its title.',
  'Windows builds are not signed by a publisher yet: Windows may warn the first time Reflux opens, and where Smart App Control is on it will not open at all.',
  'Windows and Linux do not update themselves: download each new beta from this site.',
  'Dolby Vision is not claimed on any device (it plays as its HDR10 or SDR base), and HDR on Windows is shown tone-mapped to SDR.',
  'Passing Dolby or DTS sound untouched to a receiver is not verified yet.',
  'Downloads for offline continue only while Reflux is open.',
  'On Linux, Reflux runs on X11 or through XWayland, and needs your distribution’s libmpv (Ubuntu 22.04 does not package it).',
  'Reflux has not yet been tried on a physical Google TV device, only the Google TV emulator; reports from Google TV owners are especially welcome.',
]

export default function Testing() {
  const windows = desktopFile(latest, 'windows')
  const linux = desktopFile(latest, 'linux')
  return (
    <article className="page">
      <PageHead eyebrow="Beta" title="The Reflux beta.">
        <p>
          {latest ? `Reflux ${latest.version}` : `Reflux ${release.version}`} is being tested from {SEASON.start} to {SEASON.end} on Android
          phones and tablets, Android TV, Fire TV, Windows, and Linux. This page is how to join, what to expect, and how to tell us what happened.
        </p>
      </PageHead>

      <section aria-labelledby="what">
        <h2 id="what">What the beta is</h2>
        <p>
          A beta is Reflux as we intend to release it, before it has been tried on as many devices, libraries, and networks as it will meet.
          You may find bugs. Your library, progress, and settings are meant to carry over into each newer beta and into 1.0; if a build ever
          cannot keep them, its release notes will say so first.
        </p>
        <p>
          During the beta, Reflux gets fixes, not new features: crashes, lost data, playback that fails, and problems on a platform are fixed;
          cosmetic issues are collected for later. A new beta build comes only when a fix is worth it.
        </p>
      </section>

      <section aria-labelledby="join">
        <h2 id="join">How to join, platform by platform</h2>
        <p>
          Windows and Linux builds can be downloaded by anyone. Android and Fire TV builds go through Google’s and Amazon’s test programmes,
          which only reach the accounts we add, so those need an invitation first: e-mail <a href={JOIN_MAILTO}>{SUPPORT}</a> with your devices,
          the sources you use (folders, Jellyfin, Plex, SMB, WebDAV), whether you use a game controller, and the e-mail address of your Google
          account (Android) or Amazon account (Fire TV). We choose testers to cover as many setups as possible, so not every request can be taken.
        </p>
        <div className="download-grid">
          <Platform id="android" name="Android phones, tablets, and Android TV" status={`Google Play closed testing${play.state === 'testing' ? ', invited testers only' : ''}. Android 8.0 or later.`}>
            <ol className="plain numbered small">
              <li>Ask to join (above), giving the Google account you use on the device. We add it to the tester list.</li>
              <li>On the device or in a browser, signed in to that same Google account, open the opt-in page and choose “Become a tester”.</li>
              <li>Install Reflux from Google Play as usual (the opt-in page links to it). Updates arrive through Google Play.</li>
              <li>Please stay opted in for at least 14 days: Google requires a two-week closed test before Reflux can be published to everyone.</li>
            </ol>
            {play.testing?.url && <p><Out className="button" href={play.testing.url}>Open the opt-in page</Out></p>}
            <p className="small quiet">The opt-in page only works for accounts on the tester list. Reflux is not on public Google Play yet.</p>
          </Platform>

          <Platform id="fire-tv" name="Fire TV" status={`Amazon Live App Testing${amazon.state === 'testing' ? ', by invitation' : ''}. Fire TV on Fire OS 7 or later.`}>
            <ol className="plain numbered small">
              <li>Ask to join (above), giving your Amazon account’s e-mail. Amazon sends that address an invitation.</li>
              <li>On the Fire TV, signed in to that Amazon account, install <strong>Appstore Beta Hub</strong> from the Appstore.</li>
              <li>Open Beta Hub: Reflux is under “Beta Apps Not Installed”. Installing it accepts the invitation. Updates arrive through Beta Hub.</li>
              <li>If Reflux or an update does not appear: Settings → Account &amp; Profile Settings → Sync Amazon Content.</li>
            </ol>
            <p className="small quiet">
              Reflux is not in the public Amazon Appstore yet, and there is no download link: only invited accounts can install it. It is built
              for Fire TV devices running Fire OS; devices on Vega OS cannot run it, even though Beta Hub exists there.
            </p>
          </Platform>

          <Platform id="windows" name="Windows" status="A public beta download. Windows 10 or 11, 64-bit.">
            <ol className="plain numbered small">
              <li>Download the zip from <Link to="/download#windows">Download</Link> and check its SHA-256 there.</li>
              <li>Extract it to a folder you will keep (for example, Documents\Reflux) and open <code>Reflux.exe</code>.</li>
              <li>If Windows says “Windows protected your PC”, choose More info → Run anyway, once.</li>
              <li>To update, download the newer zip, extract it to a new folder, and delete the old one. Your library is kept.</li>
            </ol>
            {windows && <p className="small quiet">{windows.name}. There is no installer yet.</p>}
          </Platform>

          <Platform id="linux" name="Linux" status="A public beta download. 64-bit x86 (x86-64), X11 or XWayland.">
            <ol className="plain numbered small">
              <li>Install libmpv: <code>libmpv2</code> on Debian 12+ and Ubuntu 24.04+, <code>mpv-libs</code> on Fedora.</li>
              <li>Download the tar.gz from <Link to="/download#linux">Download</Link> and check its SHA-256.</li>
              <li>Extract it (<code>tar -xzf</code>) and run <code>bin/Reflux</code> inside it. No root access is needed.</li>
              <li>To update, extract the newer archive to a new folder and delete the old one. Your library is kept.</li>
            </ol>
            {linux && <p className="small quiet">{linux.name}. There is no AppImage, Flatpak, or distribution package.</p>}
          </Platform>
        </div>
      </section>

      <section aria-labelledby="report">
        <h2 id="report">Reporting a problem</h2>
        <p>
          E-mail <a href={REPORT_MAILTO}>{SUPPORT}</a>: one e-mail per problem, with the details below (the link fills them in as headings).
          You need no account anywhere, and you do not need to know how Reflux is built.
        </p>
        <dl className="steps">{REPORT_FIELDS.map(([name, help]) => <div key={name}><dt>{name}</dt><dd>{help}</dd></div>)}</dl>
        <p className="small quiet">
          The support report holds your version, device, the kinds of sources you use, the last playback, and recent problems, with passwords,
          addresses, file paths, and titles taken out. Reflux never sends it by itself; you attach it. Security problems go privately through
          <Link to="/security"> Security</Link>, never in a public issue.
        </p>
      </section>

      <section aria-labelledby="known">
        <h2 id="known">Known issues in this beta</h2>
        <ul className="plain">{knownIssues.map((k) => <li key={k}>{k}</li>)}</ul>
        <p className="small quiet">More on what each device plays: <Link to="/platforms">Platforms and compatibility</Link>. Common questions: <Link to="/docs/troubleshooting">Troubleshooting and FAQ</Link>.</p>
      </section>

      <section aria-labelledby="privacy">
        <h2 id="privacy">Privacy while testing</h2>
        <p>
          The beta collects nothing more than 1.0 will: Reflux sends nothing to its makers, and nothing about your library leaves your device
          unless you attach a support report yourself. The e-mail addresses you give us are used only to add you to Google’s or Amazon’s tester
          lists and to reply to you. The details are on <Link to="/privacy">Privacy</Link>.
        </p>
      </section>

      <section aria-labelledby="schedule">
        <h2 id="schedule">From the beta to 1.0</h2>
        <Timeline />
        <p>{V1_WINDOW}</p>
        <p className="small quiet">
          Each platform is released on its own: Google Play, the Amazon Appstore, and the Windows and Linux downloads each have their own review
          or release step, and approval on one does not publish the others. Until 1.0, Reflux is not in the public Google Play or Amazon Appstore.
        </p>
      </section>
    </article>
  )
}
