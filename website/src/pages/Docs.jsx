import { Link, NavLink } from 'react-router-dom'
import { PageHead } from '../components.jsx'

const articles = [
  {
    slug: 'getting-started', title: 'Getting started', summary: 'Install Reflux and point it at your first folder or server.',
    body: (
      <>
        <h2>Install</h2>
        <p>Get Reflux for your platform from <Link to="/download">Download</Link>: Google Play on Android phones, tablets, and Google TV; the Amazon Appstore on Fire TV; a zip for Windows and a tar.gz for Linux. Nothing asks you for an account.</p>
        <h2>Your first library</h2>
        <ol className="plain numbered">
          <li>Open Reflux. It offers two ways in: <strong>Choose a folder</strong>, or <strong>Connect a server or share</strong>.</li>
          <li>Choose the folder your films and shows are in. Sub-folders are fine, and so are your existing names: <code>Heat (1995)/Heat.1995.1080p.mkv</code>, <code>Show/Season 1/Show.S01E01.mkv</code>, or plainer.</li>
          <li>Reflux scans it and builds your library in the background. Home fills as it goes.</li>
          <li>Press Play. Reflux remembers where you stop, on every copy of that title.</li>
        </ol>
        <p>Add more folders, servers, and shares under <strong>Sources</strong> at any time.</p>
        <h2>Moving around</h2>
        <p>Home, Movies, Shows, Search, Downloads, and Sources are always one step away: a bar at the bottom on a phone, a rail on a tablet or TV (press Left on the remote), a sidebar on a desktop. On a desktop, Ctrl+1 to Ctrl+6 go straight to each; with a controller, the bumpers step through them.</p>
      </>
    ),
  },
  {
    slug: 'sources', title: 'Adding sources', summary: 'Folders, Jellyfin, Plex, SMB, and WebDAV, and choosing what is in your library.',
    body: (
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
  },
  {
    slug: 'playback', title: 'How playback works', summary: 'Which copy plays, what a server converts and why, and the Info list.',
    body: (
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
  },
  {
    slug: 'library', title: 'Your library', summary: 'How titles are recognised, where details come from, and Identify.',
    body: (
      <>
        <h2>How Reflux recognises titles</h2>
        <p>From ordinary file and folder names: title, year, season and episode numbers, and the edition or quality where the name says. Episodes in a folder with no show name become a show named after the folder. Disc folders (BDMV, VIDEO_TS) are not played yet.</p>
        <h2>Where details and artwork come from</h2>
        <p>Your servers describe their titles: summaries, genres, ratings, runtimes, dates, cast, posters, and episode stills. Files bring their own artwork where it sits beside them, and TMDB fills in what neither has: Reflux sends it a title and year, nothing about you. A title with no artwork gets a designed look of its own rather than a blank. "Online metadata & artwork" under Sources turns the lookups off.</p>
        <h2>Identify</h2>
        <p>When Reflux has a title wrong, open its page and choose <strong>Identify</strong>: search, pick the right one, and Reflux remembers it through rescans, keeping your progress, favourites, and chosen version. <strong>Let Reflux identify it</strong> undoes it.</p>
        <h2>Nothing changes on your disk</h2>
        <p>Your library, corrections, and choices are Reflux's own. It never renames, moves, tags, or deletes your files.</p>
      </>
    ),
  },
  {
    slug: 'troubleshooting', title: 'Troubleshooting and FAQ', summary: 'Common questions, and how to send a support report.',
    body: (
      <>
        <h2>Is Reflux free? Does it need an account?</h2>
        <p>Yes, and no. Reflux is free, with no subscription, paid tier, or ads, and nothing in it needs an account.</p>
        <h2>A title plays without sound</h2>
        <p>The device cannot decode its audio (DTS or TrueHD on most phones and TVs). Reflux says so on screen. If the file is on a Jellyfin server, Reflux asks the server to convert the sound; from a folder or a share, choose another audio track or another version.</p>
        <h2>A server says it cannot be reached</h2>
        <p>Check that the device is on the same network and the server is awake. Reflux keeps the server's titles in your library while it is away and picks them up when it answers again. If the server stopped accepting Reflux's sign-in, its row under Sources asks you to sign in again.</p>
        <h2>Windows says “Windows protected your PC”</h2>
        <p>Reflux for Windows is not yet signed by a publisher. Check the download's SHA-256 first; <Link to="/download">Download</Link> explains, and never asks you to turn off Windows' protections.</p>
        <h2>On a TV, a folder's posters do not show</h2>
        <p>On Android 13 and later TVs, Reflux is allowed to read your videos but not the images beside them. Titles from a server show the server's artwork; others get Reflux's designed look.</p>
        <h2>Sending a support report</h2>
        <p>In Reflux, open <strong>Sources → About Reflux → Save a support report</strong>. It lists your version, device, what it plays, your sources' kinds, how the last playback went, and recent problems, with passwords, addresses, file paths, and titles removed. On a computer it lands in Downloads; on a phone or tablet you choose where to share it; on a TV it is saved in the TV's Download folder, under Reflux. Reflux never sends it itself. Attach it when you contact <Link to="/support">Support</Link>.</p>
      </>
    ),
  },
]

export const docSlugs = articles.map((a) => a.slug)

export function DocsIndex() {
  return (
    <article className="page">
      <PageHead eyebrow="Documentation" title="Using Reflux.">
        <p>Everything you need to get from a folder to Play, and what to do when something does not.</p>
      </PageHead>
      <ul className="doc-index">
        {articles.map((a) => (
          <li key={a.slug}><Link to={`/docs/${a.slug}`}><h2>{a.title}</h2><p>{a.summary}</p></Link></li>
        ))}
      </ul>
    </article>
  )
}

export function DocArticle({ slug }) {
  const article = articles.find((a) => a.slug === slug)
  return (
    <div className="doc-layout">
      <nav className="doc-nav" aria-label="Documentation">
        <Link to="/docs" className="quiet small">Documentation</Link>
        {articles.map((a) => <NavLink key={a.slug} to={`/docs/${a.slug}`}>{a.title}</NavLink>)}
      </nav>
      <article className="page prose">
        <PageHead eyebrow="Documentation" title={article.title}><p>{article.summary}</p></PageHead>
        {article.body}
      </article>
    </div>
  )
}
