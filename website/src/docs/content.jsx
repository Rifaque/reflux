// The documentation's pages: what each says. Their order, titles, and addresses are src/docs/pages.js. Everything here
// describes the published build (release.latest), not unreleased work; a fact about a platform is one that was checked
// (docs/VERIFICATION.md). Linux package names were read from each distribution's own repositories on 2026-11-19.
import { Link } from 'react-router-dom'
import release from '../generated/release.json'
import { desktopFile } from '../availability.js'
import { Code, Note, Tabs } from './blocks.jsx'

const latest = release.latest
const version = latest?.version ?? release.version
const windowsFile = desktopFile(latest, 'windows')
const linuxFile = desktopFile(latest, 'linux')
const windowsName = windowsFile?.name ?? `reflux-${version}-windows-x64.zip`
const linuxName = linuxFile?.name ?? `reflux-${version}-linux-x64.tar.gz`
const linuxFolder = linuxName.replace(/\.tar\.gz$/, '')
const megabytes = (bytes) => `${Math.round(bytes / 1048576)} MB`
const playOptIn = release.stores.googlePlay?.testing?.url
const ExternalLink = ({ href, children }) => <a href={href} rel="noopener noreferrer">{children}</a>

/** The steps every Linux distribution shares once its playback engine is installed. */
const linuxReflux = (
  <>
    <h3>Install Reflux</h3>
    <p>Download <code>{linuxName}</code> from <Link to="/download">Download</Link>{linuxFile ? ` (${megabytes(linuxFile.size)})` : ''}, <Link to="/docs/install/verify">check it</Link>, then extract it to a folder of your own and run it. No root access is needed for Reflux itself.</p>
    <Code>{`mkdir -p ~/Applications
tar -xzf ~/Downloads/${linuxName} -C ~/Applications
~/Applications/${linuxFolder}/bin/Reflux`}</Code>
  </>
)

/** A distribution's own package for the playback engine, and what the command does. */
function Engine({ command, packageName, tested, children }) {
  return (
    <>
      <h3>Install the playback engine</h3>
      <p>Reflux plays through libmpv, the engine of the mpv player, which your distribution packages as <code>{packageName}</code>. Install it from your distribution's own repositories:</p>
      <Code>{command}</Code>
      <p className="small quiet"><code>sudo</code> is needed because this installs a system package for every user of the computer; it adds a library, not an application, and changes nothing else. {tested}</p>
      {children}
    </>
  )
}

export const DOC_BODIES = {
  'getting-started': (
    <>
      <h2>Install</h2>
      <p>Choose your platform: <Link to="/docs/install/windows">Windows</Link>, <Link to="/docs/install/linux">Linux</Link>, <Link to="/docs/install/android">Android phones and tablets</Link>, <Link to="/docs/install/android-tv">Android TV and Google TV</Link>, or <Link to="/docs/install/fire-tv">Fire TV</Link>. During the beta, Android and Fire TV builds reach invited testers only (<Link to="/testing">the beta</Link> says how to join). Reflux has no account of its own: you sign in only to your own servers, if you add any.</p>
      <h2>Your first library</h2>
      <ol className="plain numbered">
        <li>Open Reflux. It offers two ways in: <strong>Choose a folder</strong>, or <strong>Connect a server or share</strong>.</li>
        <li>Choose the folder your films and shows are in. Sub-folders are fine, and so are your existing names: <code>Heat (1995)/Heat.1995.1080p.mkv</code>, <code>Show/Season 1/Show.S01E01.mkv</code>, or plainer.</li>
        <li>Reflux scans it and builds your library in the background. Home fills as it goes.</li>
        <li>Press Play. Reflux remembers where you stop, on every copy of that title.</li>
      </ol>
      <p>Add more folders, servers, and shares under <strong>Sources</strong> at any time (<Link to="/docs/sources">Adding sources</Link>).</p>
      <h2>Moving around</h2>
      <p>Home, Movies, Shows, and Search are always one step away, and so are Downloads, Sources, and Settings: a rail on a tablet or TV (press Left on the remote), a sidebar on a desktop, and on a phone a bar at the bottom with your profile button leading to Downloads, Sources, and Settings. On a desktop, Ctrl+1 to Ctrl+6 go straight to Home through Sources; with a controller, the bumpers step through them.</p>
    </>
  ),

  'install/windows': (
    <>
      <h2>What you need</h2>
      <ul className="plain">
        <li>Windows 10 or 11, 64-bit (x64). Reflux is tested on Windows 11.</li>
        <li>About {windowsFile ? megabytes(windowsFile.size) : '100 MB'} for the download, and room for your library's pictures.</li>
        <li>Nothing else: the zip carries the playback engine and its own Java runtime.</li>
      </ul>
      <h2>Install</h2>
      <ol className="plain numbered">
        <li>Download <code>{windowsName}</code> from <Link to="/download">Download</Link> and <Link to="/docs/install/verify">check its SHA-256</Link>.</li>
        <li>Extract it to a folder you will keep, for example <code>Documents\Reflux</code>. There is no installer: the folder is Reflux.</li>
        <li>Open <code>Reflux.exe</code> in it. For a shortcut, right-click <code>Reflux.exe</code> and choose Send to → Desktop (create shortcut); on Windows 11 it is under Show more options.</li>
      </ol>
      <h2>“Windows protected your PC”</h2>
      <p>Reflux for Windows is not yet signed with a publisher certificate, so the first time you open it Windows may show this. If the download's SHA-256 matched, choose <strong>More info</strong>, then <strong>Run anyway</strong>, once.</p>
      <Note kind="warning">
        <p>Keep SmartScreen and your other Windows protections on. Where Smart App Control is on, Windows does not run unsigned apps it does not know, and this build will not open there; Reflux will never ask you to turn it off. Signed builds are planned.</p>
      </Note>
      <h2>Update</h2>
      <p>Reflux does not update itself. When a new build is announced on <Link to="/releases">Releases</Link>, extract the newer zip to a new folder, open it, and delete the old folder. Your library is not in the folder, so it is kept.</p>
      <h2>Remove</h2>
      <p>Delete the folder. To remove your library too, delete <code>%LOCALAPPDATA%\Reflux</code>, and in Windows Credential Manager the entries named “Reflux” (your server sign-ins).</p>
      <h2>If something is wrong</h2>
      <ul className="plain">
        <li><strong>Two “Reflux” entries in Task Manager</strong> while Reflux runs are expected: a small launcher and Reflux itself. Both end within a second of closing the window. If one stays after closing, please tell <Link to="/support">Support</Link> whether something was playing.</li>
        <li><strong>Problems are written to</strong> <code>%LOCALAPPDATA%\Reflux\reflux.log</code>; a support report (Settings → About Reflux) gathers what is useful, with your private details removed.</li>
      </ul>
    </>
  ),

  'install/linux': (
    <>
      <h2>What you need</h2>
      <ul className="plain">
        <li>A 64-bit x86 (x86-64) computer with a graphical desktop. Reflux runs on X11, and on Wayland desktops through XWayland.</li>
        <li>Your distribution's libmpv, the playback engine (below). Without it Reflux opens and browses your library, but does not play, and says what to install.</li>
        <li>Reflux itself is a tar.gz with its own Java runtime: there is no AppImage, Flatpak, or distribution package of it yet.</li>
      </ul>
      <h2>Choose your distribution</h2>
      <Tabs
        label="Linux distribution"
        tabs={[
          {
            id: 'ubuntu', label: 'Ubuntu and Debian',
            content: (
              <>
                <Engine packageName="libmpv2" command="sudo apt install libmpv2" tested="Checked with Reflux on Ubuntu 24.04 and Debian 12.">
                  <p><strong>Ubuntu 22.04</strong> has no <code>libmpv2</code>; its package is the older <code>libmpv1</code>, which Reflux plays with too (checked on Ubuntu 22.04):</p>
                  <Code>sudo apt install libmpv1</Code>
                </Engine>
                {linuxReflux}
                <p className="small quiet">To keep server sign-ins in your desktop's keyring, install <code>libsecret-tools</code> (it provides <code>secret-tool</code>). Without it Reflux keeps them in its own files, and its log says so.</p>
              </>
            ),
          },
          {
            id: 'mint', label: 'Linux Mint',
            content: (
              <>
                <Engine packageName="libmpv2" command="sudo apt install libmpv2" tested="Linux Mint takes these packages from the Ubuntu release it is built on, where Reflux was checked; Mint itself was not tested.">
                  <p>That is for <strong>Linux Mint 22</strong>, built on Ubuntu 24.04. <strong>Linux Mint 21</strong> is built on Ubuntu 22.04, which has only the older package:</p>
                  <Code>sudo apt install libmpv1</Code>
                </Engine>
                {linuxReflux}
                <p className="small quiet">For server sign-ins in your keyring, install <code>libsecret-tools</code>.</p>
              </>
            ),
          },
          {
            id: 'fedora', label: 'Fedora',
            content: (
              <>
                <Engine packageName="mpv-libs" command="sudo dnf install mpv-libs" tested="It is in Fedora's own repositories; checked with Reflux on Fedora 41." />
                {linuxReflux}
                <p className="small quiet">For server sign-ins in your keyring, install <code>libsecret</code> (it provides <code>secret-tool</code>); it is usually installed already.</p>
              </>
            ),
          },
          {
            id: 'arch', label: 'Arch Linux',
            content: (
              <>
                <Engine packageName="mpv" command="sudo pacman -S mpv" tested="On Arch, libmpv comes with the mpv package; checked with Reflux on Arch Linux (mpv 0.42.0)." />
                {linuxReflux}
                <p className="small quiet">For server sign-ins in your keyring, install <code>libsecret</code> (it provides <code>secret-tool</code>).</p>
              </>
            ),
          },
          {
            id: 'other', label: 'Other distributions',
            content: (
              <>
                <h3>Install the playback engine</h3>
                <p>Install the package of your distribution that provides libmpv (<code>libmpv.so.2</code>, or the older <code>libmpv.so.1</code>). It is usually named <code>libmpv2</code>, <code>mpv-libs</code>, or comes with <code>mpv</code> itself. Reflux has been checked on the distributions in the other tabs only.</p>
                {linuxReflux}
              </>
            ),
          },
        ]}
      />
      <h2>Update</h2>
      <p>Reflux does not update itself. Extract the newer archive next to the old one, run it, then delete the old folder. Your library is kept in <code>~/.local/share/reflux</code>.</p>
      <h2>Remove</h2>
      <Code>{`rm -rf ~/Applications/${linuxFolder}
rm -rf ~/.local/share/reflux   # your library too, if you want it gone`}</Code>
      <p className="small quiet">Server sign-ins kept in your keyring are removed with your desktop's passwords app. The playback engine is a normal package of your distribution; remove it with your package manager if nothing else uses it.</p>
      <h2>If something is wrong</h2>
      <ul className="plain">
        <li><strong>“Playback needs libmpv”</strong>: install the package for your distribution above, then restart Reflux.</li>
        <li><strong>Playback never starts, and the log says libmpv “could not be initialized”</strong>, in Reflux {version}: libmpv refuses to start unless the system's numeric format is plain C, which most desktops' (en_US.UTF-8, de_DE.UTF-8, and others) are not. Start Reflux with the numeric format set to C; your language and other formats are unchanged. Reflux sets this itself from the next build.</li>
      </ul>
      <Code>{`LC_NUMERIC=C ~/Applications/${linuxFolder}/bin/Reflux`}</Code>
      <ul className="plain">
        <li><strong>Problems are written to</strong> <code>~/.local/share/reflux/reflux.log</code>; a support report (Settings → About Reflux) gathers what is useful, with your private details removed.</li>
      </ul>
    </>
  ),

  'install/android': (
    <>
      <h2>What you need</h2>
      <ul className="plain">
        <li>An Android phone or tablet with Android 8.0 or later.</li>
        <li>During the beta: a Google account on Reflux's tester list. Reflux is not on public Google Play until 1.0, and there is no APK to download: Reflux for Android comes only from Google Play.</li>
      </ul>
      <h2>Install</h2>
      <ol className="plain numbered">
        <li>Ask to join the beta (<Link to="/testing#join">how</Link>), giving the Google account you use on the device.</li>
        <li>Signed in to that account, open the opt-in page and choose <strong>Become a tester</strong>.{playOptIn && <> <ExternalLink href={playOptIn}>The opt-in page</ExternalLink>.</>}</li>
        <li>Install Reflux from Google Play as usual. Updates arrive through Google Play.</li>
      </ol>
      <h2>First launch</h2>
      <p>Choose a folder with the system's folder picker, or connect a server or share. Reflux reads only the folders you choose, one at a time, and never changes them.</p>
      <h2>If something is wrong</h2>
      <ul className="plain">
        <li><strong>Google Play says the app is not available</strong>: the account on the device is not on the tester list, or has not opted in yet.</li>
        <li><strong>A title plays without sound</strong>: see <Link to="/docs/troubleshooting">Troubleshooting</Link>.</li>
      </ul>
    </>
  ),

  'install/android-tv': (
    <>
      <h2>What you need</h2>
      <ul className="plain">
        <li>An Android TV or Google TV device with Android 8.0 or later, signed in to a Google account on Reflux's tester list (during the beta).</li>
        <li>Its remote; a game controller works too.</li>
      </ul>
      <h2>Install</h2>
      <ol className="plain numbered">
        <li>Join the beta and opt in with the Google account the TV uses, as for <Link to="/docs/install/android">phones and tablets</Link>.</li>
        <li>On the TV, open Google Play, find Reflux, and install it. It updates through Google Play.</li>
      </ol>
      <h2>Finding your way with the remote</h2>
      <p>Press Left to reach the navigation rail, Back to go back a step, and Select to choose. Your Continue Watching and next episodes also appear in the home screen's Watch Next row. When several profiles share the TV, Reflux asks who is watching when it starts.</p>
      <h2>If something is wrong</h2>
      <ul className="plain">
        <li><strong>A folder's posters do not show</strong> on Android 13 and later: Reflux may read your videos but not the images beside them. Server titles show the server's artwork.</li>
      </ul>
    </>
  ),

  'install/fire-tv': (
    <>
      <h2>What you need</h2>
      <ul className="plain">
        <li>A Fire TV with Fire OS 7 or later. Fire TV devices on Vega OS do not run Android apps and cannot run Reflux.</li>
        <li>During the beta: an invitation through Amazon Live App Testing to your Amazon account. Reflux is not on the public Amazon Appstore until 1.0.</li>
      </ul>
      <h2>Install</h2>
      <ol className="plain numbered">
        <li>Ask to join the beta (<Link to="/testing#join">how</Link>), giving your Amazon account's e-mail. Amazon sends that address an invitation.</li>
        <li>On the Fire TV, signed in to that Amazon account, install <strong>Appstore Beta Hub</strong> from the Appstore.</li>
        <li>Open Beta Hub: Reflux is under <strong>Beta Apps Not Installed</strong>. Installing it accepts the invitation. Updates arrive through Beta Hub.</li>
      </ol>
      <Note kind="tip"><p>If Reflux or an update does not appear: Settings → Account &amp; Profile Settings → Sync Amazon Content.</p></Note>
      <h2>Finding your way with the remote</h2>
      <p>Press Left to reach the navigation rail, Back to go back a step, and Select to choose; the remote's play, rewind, and fast-forward buttons work in the player.</p>
    </>
  ),

  'install/verify': (
    <>
      <h2>Why check</h2>
      <p>Every Windows and Linux release lists the SHA-256 of each file{latest ? <> in <ExternalLink href={latest.checksumsUrl}>SHA256SUMS</ExternalLink>, beside the <ExternalLink href={latest.manifestUrl}>manifest</ExternalLink> and the <ExternalLink href={latest.provenanceUrl}>build provenance</ExternalLink></> : ''}. A file that matches is exactly the file the release published. Only download Reflux from this site or Reflux's GitHub releases.</p>
      <h2>Linux</h2>
      <p>Put the file and SHA256SUMS in one folder, then:</p>
      <Code>sha256sum -c --ignore-missing SHA256SUMS</Code>
      <h2>Windows</h2>
      <p>In PowerShell, in the folder with the zip, then compare the hash it prints with the zip's line in SHA256SUMS:</p>
      <Code>{`Get-FileHash .\\${windowsName} -Algorithm SHA256`}</Code>
      <h2>The build attestation</h2>
      <p>For a stronger check, GitHub's command-line tool verifies that Reflux's release workflow built exactly this file from the commit it names:</p>
      <Code>{`gh attestation verify ${linuxName} -R Rifaque/reflux --signer-workflow Rifaque/reflux/.github/workflows/release.yml`}</Code>
      <p className="small quiet">An attestation says where the file came from and that it has not changed since. It is not a publisher signature, and it does not say the software has no bugs.</p>
    </>
  ),

  sources: (
    <>
      <h2>Add a source</h2>
      <p>Open <strong>Sources</strong> and choose <strong>Add media</strong>. Pick a folder, or enter a server's or share's address. For Jellyfin, sign in with your user name and password; for Plex, your Plex account, a token, or nothing where your server allows your network. Passwords are used to sign in and are not kept.</p>
      <h2>Each source's own page</h2>
      <p>Every source, and every library of a server, has its own page under Sources with everything on it, whether or not it is in your library.</p>
      <h2>Leaving something out</h2>
      <p>Switch a source, or one of its libraries, out of your library without disconnecting it. It stays under Sources, playable from its page, and what you watched is kept. Switch it back and everything returns at once, with nothing to scan.</p>
      <h2>Removing a source</h2>
      <p>Removing a source takes its titles out of Reflux and forgets its sign-in; removing a Jellyfin server also signs this device out of it. Your files are never touched.</p>
      <p><Link to="/sources" className="more">What each source supports</Link></p>
    </>
  ),

  playback: (
    <>
      <h2>Which copy plays</h2>
      <p>When a title has more than one copy (a 4K and a 1080p, a local file and a server's), Play chooses the one this device plays best: the picture it can decode and show, the sound it can play, the language you speak. A title's page lists every version with the recommended one marked and why. <strong>Always play this</strong> pins a version; <strong>Let Reflux choose</strong> gives the choice back.</p>
      <h2>The original, whenever it can be</h2>
      <p>Reflux plays your file as it is whenever the device can. When it cannot and the file is on a Jellyfin server, Reflux asks the server to change only what it must: the sound alone if only the sound is the problem, the picture only when the device cannot decode it. Local files and shares are played by the device alone; what it cannot play is said, not hidden.</p>
      <h2>Info</h2>
      <p>While something plays, the player's <strong>Info</strong> list says how it is playing: the original file or a conversion, the decoder in hardware or software, the sound, the subtitles, and whether that is what was planned.</p>
      <h2>Resuming</h2>
      <p>Progress belongs to the title, not the file: resume on any copy of the same cut, on any device that shares the source. A different cut (an extended edition) starts at the right place for it, and Reflux says so. <strong>From the start</strong> keeps your place until the new playback is under way.</p>
      <h2>Offline</h2>
      <p>Keep a film, an episode, or a season for watching without its source from its page. <strong>Downloads</strong> lists everything kept, on its way, or unfinished. Downloads continue while Reflux is open.</p>
    </>
  ),

  library: (
    <>
      <h2>How Reflux recognises titles</h2>
      <p>From ordinary file and folder names: title, year, season and episode numbers, and the edition or quality where the name says. Episodes in a folder with no show name become a show named after the folder. Disc folders (BDMV, VIDEO_TS) are not played yet.</p>
      <h2>Where details and artwork come from</h2>
      <p>Your servers describe their titles: summaries, genres, ratings, runtimes, dates, cast, posters, and episode stills. Files bring their own artwork where it sits beside them, and Trakt, an online film and TV database, fills in what neither has. A title with no artwork gets a designed look of its own rather than a blank.</p>
      <h2>Online metadata &amp; artwork</h2>
      <p>To recognise a title, your device asks Trakt directly with the title Reflux read (or an ID such as an IMDb number, when it already has one), and your language when it is not English. The year is used on your device to pick the right match and is not sent; nothing about you, your files' locations, or your servers is sent, and no Trakt account is used. Reflux then downloads the pictures Trakt names and keeps them on your device.</p>
      <p>Turn <strong>Online metadata &amp; artwork</strong> off in Settings and Reflux sends Trakt nothing and fetches no new pictures from it. What it already found stays, your servers and files still describe your titles, and Identify waits until you turn it back on. See <Link to="/privacy">Privacy</Link>.</p>
      <h2>Identify</h2>
      <p>When Reflux has a title wrong, open its page and choose <strong>Identify</strong>: search, pick the right one, and Reflux remembers it through rescans, keeping your progress, favourites, and chosen version. <strong>Let Reflux identify it</strong> undoes it.</p>
      <h2>Nothing changes on your disk</h2>
      <p>Your library, corrections, and choices are Reflux's own. It never renames, moves, tags, or deletes your files.</p>
    </>
  ),

  profiles: (
    <>
      <h2>A profile for each person</h2>
      <p>Each profile keeps its own watch history, favourites, collections, chosen versions, and preferences, and its own Jellyfin and Plex accounts: one person's history is never shown to another. Folders and shares are shared by everyone on the device. Add a profile from the profile entry (on a phone, the button at the top right).</p>
      <h2>Who is watching</h2>
      <p>With one profile, Reflux never asks. With several, a TV asks who is watching each time it starts; a phone or a computer opens the profile it had.</p>
      <h2>A PIN</h2>
      <p>A profile can have a four-digit PIN, asked before it opens. Wrong entries make it wait, longer each time. A PIN keeps a profile private from the others sharing the device; it is not encryption. A forgotten PIN cannot be recovered: <strong>Forgot PIN</strong> starts that profile over, erasing what it kept on this device.</p>
    </>
  ),

  troubleshooting: (
    <>
      <h2>Is Reflux free? Is it open source? Does it need an account?</h2>
      <p>Reflux is free of charge, with no subscription, paid tier, or ads, and no payment or donation unlocks anything. It is free and open-source software under the GNU GPL, version 3 or later: you may read, change, and share its source, at <ExternalLink href="https://github.com/Rifaque/reflux">github.com/Rifaque/reflux</ExternalLink>, under that license (see <Link to="/license">License</Link>). It has no account of its own; you sign in only to your own Jellyfin or Plex server, if you use one.</p>
      <h2>A title plays without sound</h2>
      <p>The device cannot decode its audio (DTS or TrueHD on most phones and TVs). Reflux says so on screen. If the file is on a Jellyfin server, Reflux asks the server to convert the sound; from a folder or a share, choose another audio track or another version.</p>
      <h2>A server says it cannot be reached</h2>
      <p>Check that the device is on the same network and the server is awake. Reflux keeps the server's titles in your library while it is away and picks them up when it answers again. If the server stopped accepting Reflux's sign-in, its row under Sources asks you to sign in again.</p>
      <h2>Installing</h2>
      <p>Windows' security warning, Linux's playback engine, and joining the Android and Fire TV betas each have their own page under <strong>Installation</strong>: <Link to="/docs/install/windows">Windows</Link>, <Link to="/docs/install/linux">Linux</Link>, <Link to="/docs/install/android">Android</Link>, <Link to="/docs/install/android-tv">Android TV</Link>, <Link to="/docs/install/fire-tv">Fire TV</Link>.</p>
      <h2>Sending a support report</h2>
      <p>In Reflux, open <strong>Settings → About Reflux → Save a support report</strong>. It lists your version, device, what it plays, your sources' kinds, how the last playback went, and recent problems, with passwords, addresses, file paths, and titles removed. On a computer it lands in Downloads; on a phone or tablet you choose where to share it; on a TV it is saved in the TV's Download folder, under Reflux. Reflux never sends it itself. Attach it when you contact <Link to="/support">Support</Link>.</p>
    </>
  ),
}
