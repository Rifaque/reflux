import { Link } from 'react-router-dom'

const platforms = ['Android phones and tablets', 'Android TV and Google TV', 'Fire TV', 'Windows', 'Linux']
const sources = ['Folders on your device', 'Jellyfin', 'Plex', 'Windows and NAS shares (SMB)', 'Web shares (WebDAV)']

export default function Home() {
  return (
    <>
      <section className="hero">
        <h1>Your media, played well.</h1>
        <p className="lead">
          Reflux is a media player for your own films and shows, wherever they live: on your device, on a Jellyfin or
          Plex server, or on a share. It finds them, understands what they are, and plays the best copy for the screen
          you are on.
        </p>
        <p className="status">In development. The beta is not open yet. <Link to="/testers">How testing will work</Link>.</p>
      </section>
      <section className="columns">
        <div>
          <h2>Where it runs</h2>
          <ul>{platforms.map((p) => <li key={p}>{p}</li>)}</ul>
        </div>
        <div>
          <h2>What it plays from</h2>
          <ul>{sources.map((s) => <li key={s}>{s}</li>)}</ul>
        </div>
        <div>
          <h2>What it keeps to itself</h2>
          <p>No account, no tracking, and nothing sent to its makers. <Link to="/privacy">Privacy</Link>.</p>
        </div>
      </section>
    </>
  )
}
