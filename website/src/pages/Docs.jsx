import { Link } from 'react-router-dom'

// The documentation's structure (docs/WEBSITE.md). Each section is written as the product it describes is
// released; until then it says what it will cover, and nothing claims support the app does not have.
const sections = [
  ['Getting started', 'Installing Reflux on each platform, and pointing it at your first folder or server.'],
  ['Sources', 'Folders on your device, Jellyfin, Plex, Windows and NAS shares (SMB), and web shares (WebDAV): what each needs and its limits.'],
  ['Playback', 'How Reflux chooses a version, what it plays directly, and what a server converts and why.'],
  ['Your library', 'How Reflux identifies files, where descriptions and artwork come from, and how to correct a match.'],
  ['Platforms and compatibility', 'Android phones and tablets, Android TV and Google TV, Fire TV, Windows, and Linux: requirements and known limits.'],
  ['Troubleshooting', 'Common problems, and how to send a report with Reflux\'s redacted diagnostics.'],
]

export default function Docs() {
  return (
    <article className="page">
      <h1>Documentation</h1>
      <p className="lead">Written as Reflux is released. These are the sections it will have; the project's design documents are public in the meantime, on <a href="https://github.com/Rifaque/reflux/tree/main/docs" rel="noopener noreferrer">GitHub</a>.</p>
      <dl className="sections">
        {sections.map(([name, about]) => (<div key={name}><dt>{name}</dt><dd>{about}</dd></div>))}
      </dl>
      <p>Questions in the meantime: <Link to="/support">Support</Link>.</p>
    </article>
  )
}
