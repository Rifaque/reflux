import { Link } from 'react-router-dom'
import { PageHead } from '../components.jsx'

const sources = [
  {
    name: 'Folders on your device',
    what: 'A folder on your computer, phone, tablet, or TV, or on a drive plugged into it.',
    how: 'Windows and Linux: choose a folder. Android phones and tablets: the system picker grants one folder at a time, read-only. TVs: Reflux shows the device’s storage and you choose with the remote.',
    limits: 'On Android 13 and later TVs, artwork kept beside your videos (poster.jpg, fanart.jpg) is not read; Reflux uses your servers’ artwork or its own designed look.',
  },
  {
    name: 'Jellyfin',
    what: 'Your Jellyfin server’s libraries, with what the server knows about each title.',
    how: 'Add it by its address and sign in. Your password is used once and never kept; Reflux keeps the session in the system’s credential store where there is one.',
    limits: 'Watched and resume positions go both ways. When a device cannot play a file, the server converts only what it must. Removing the server signs this device out of it.',
  },
  {
    name: 'Plex',
    what: 'Your Plex Media Server’s films and shows, with descriptions, cast, and progress.',
    how: 'Sign in with your Plex account, use a Plex token, or connect without either where your server lets your network in.',
    limits: 'Plays files directly; Reflux does not ask Plex to convert. What a device cannot play is said.',
  },
  {
    name: 'Windows and NAS shares (SMB)',
    what: 'A shared folder on a PC or a NAS.',
    how: 'Add it by its address (\\\\nas\\Media or //nas/Media) with a user name and password if it has them.',
    limits: 'Read-only. Files are streamed to the player, not copied.',
  },
  {
    name: 'Web shares (WebDAV)',
    what: 'A NAS, Nextcloud, or any web server that shares files over WebDAV.',
    how: 'Add it by its address, with a user name and password if it asks.',
    limits: 'Read-only. Reflux only reads listings and files at the address you gave it.',
  },
]

const slug = (name) => name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')

export default function Sources() {
  return (
    <article className="page">
      <PageHead eyebrow="Sources" title="Wherever your media lives, one library.">
        <p>Add as many sources as you like. The same film on two of them is one film in Reflux, with every copy as a version. A source that goes away keeps its titles in your library until it comes back.</p>
      </PageHead>
      <div className="source-list">
        {sources.map((s) => (
          <section key={s.name} className="source" aria-labelledby={slug(s.name)}>
            <h2 id={slug(s.name)}>{s.name}</h2>
            <p>{s.what}</p>
            <dl>
              <div><dt>Adding it</dt><dd>{s.how}</dd></div>
              <div><dt>Good to know</dt><dd>{s.limits}</dd></div>
            </dl>
          </section>
        ))}
      </div>
      <section aria-labelledby="control">
        <h2 id="control">You decide what is in your library</h2>
        <p>Each source, and each library on a server, can be left out of your library without disconnecting it: it stays under Sources, where you can still open it and play from it, and what you watched is kept. Reflux never renames, moves, or changes your files.</p>
        <p><Link to="/docs/sources" className="more">Adding and managing sources</Link></p>
      </section>
    </article>
  )
}
