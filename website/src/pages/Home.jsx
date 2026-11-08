import { Link } from 'react-router-dom'
import { Shot, Status } from '../components.jsx'
import { MarkMotion, useDepth } from '../motion.jsx'

const loop = [
  ['Point it at a folder', 'A normal folder of films and shows is enough. No renaming, no .nfo files, no account.'],
  ['It understands what is there', 'Films, shows, seasons, and episodes from ordinary file names, with every copy of the same work brought together.'],
  ['It builds a library', 'Descriptions and artwork from your servers and your files, and a designed look for anything without them.'],
  ['You press Play', 'Reflux picks the copy this screen plays best, starts where you stopped, and can tell you why.'],
]

const sources = [
  ['Folders', 'On your computer, phone, tablet, or TV, and the drives plugged into it.'],
  ['Jellyfin', 'Libraries, direct streams, and your progress kept in step both ways.'],
  ['Plex', 'Sign in with your Plex account, a token, or nothing on a trusted network.'],
  ['Windows and NAS shares', 'SMB shares, read-only, streamed without copying.'],
  ['Web shares', 'WebDAV: a NAS, Nextcloud, or any web server that serves files.'],
]

const platforms = [
  ['Android phones and tablets', 'Google Play'],
  ['Android TV and Google TV', 'Google Play'],
  ['Fire TV', 'Amazon Appstore'],
  ['Windows', 'Download'],
  ['Linux', 'Download'],
]

/** Three copies of one film, from three places, becoming one film in Reflux: the library's idea, in motion. */
const copies = [
  ['On this laptop', '1080p'],
  ['On your Jellyfin server', '4K · HDR10'],
  ['Kept for offline', '720p'],
]

function Convergence() {
  return (
    <figure className="converge" data-reveal>
      <div className="converge-stage">
        {copies.map(([where, what], i) => (
          <div key={where} className={`copy copy-${i}`} aria-hidden="true">
            <img src="/showcase/poster-sintel.webp" width="360" height="509" alt="" loading="lazy" decoding="async" />
            <span className="copy-label"><span>{where}</span><b>{what}</b></span>
          </div>
        ))}
        <div className="converge-result">
          <Shot name="details-desktop" alt="Sintel's page in Reflux: one film with both of its copies, the 4K HDR10 one recommended." />
        </div>
      </div>
      <figcaption className="converge-caption"><span className="converge-chip">Three copies · one film · Play picks 4K HDR10</span></figcaption>
    </figure>
  )
}

export default function Home() {
  const depth = useDepth()
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <div className="hero-brand"><MarkMotion className="mark-motion hero-mark" /><Status /></div>
          <h1>Your media,<br />played well.</h1>
          <p className="lead">
            Reflux is a media player for the films and shows you already have, wherever they live: on your device, on a
            Jellyfin or Plex server, or on a network share. It finds them, understands them, and plays the best copy for
            the screen in front of you.
          </p>
          <div className="actions">
            <Link to="/download" className="button">Get Reflux</Link>
            <Link to="/testing" className="button ghost">Join the beta</Link>
          </div>
        </div>
        <figure className="hero-shot" ref={depth}>
          <Shot name="home-desktop" eager alt="Reflux on a desktop: Home, with Coffee Run to continue and a Continue Watching row." />
        </figure>
      </section>

      <section className="band">
        <div className="section-head" data-reveal>
          <p className="eyebrow">How it works</p>
          <h2>From a folder to Play, with nothing to set up.</h2>
        </div>
        <ol className="loop" data-reveal data-stagger>
          {loop.map(([title, text], i) => (
            <li key={title}>
              <span className="loop-step" aria-hidden="true">{String(i + 1).padStart(2, '0')}</span>
              <h3>{title}</h3>
              <p>{text}</p>
            </li>
          ))}
        </ol>
      </section>

      <section className="split">
        <div className="split-copy" data-reveal>
          <p className="eyebrow">One library</p>
          <h2>Every copy of a film is one film.</h2>
          <p>
            A film on your laptop, the same film in 4K on your server, and a copy you kept for a flight are one work in
            Reflux, with one place you left off. Play chooses the copy this device plays best, and the page shows every
            version with the one it recommends and why.
          </p>
          <ul className="ticks">
            <li>Resume from any copy of the same cut</li>
            <li>Pin the version Play should always use</li>
            <li>Different cuts are never mixed up</li>
          </ul>
        </div>
        <Convergence />
      </section>

      <section className="band screens">
        <div className="section-head" data-reveal>
          <p className="eyebrow">Every screen</p>
          <h2>Built for the remote, the touchscreen, and the keyboard alike.</h2>
          <p className="lead">
            One Reflux on every device, laid out for each: a rail you reach with Left on a TV remote, a bar under your
            thumb on a phone, a sidebar on a desktop. Remotes, game controllers, keyboards, and screen readers are all part of the design.
          </p>
        </div>
        <figure className="screen-tv" data-reveal><Shot name="movies-tv" alt="Reflux on a television: the Movies grid, with focus on Big Buck Bunny." /></figure>
        <div className="screen-pair" data-reveal data-stagger>
          <figure className="screen-tablet"><Shot name="details-tablet" alt="Reflux on a tablet held sideways: Sprite Fright's page." /></figure>
          <figure className="screen-phone"><Shot name="home-phone" alt="Reflux on a phone held upright: Home." /></figure>
        </div>
      </section>

      <section className="split reverse">
        <div className="split-copy" data-reveal>
          <p className="eyebrow">Playback</p>
          <h2>The original, whenever it can be.</h2>
          <p>
            Reflux knows what this device decodes, and plays your file as it is whenever it can. When it cannot, it asks
            your server to change only the part that needs it: the sound, not the picture, if the sound is the problem.
            The player's Info list says how something is playing and why.
          </p>
          <p><Link to="/docs/playback" className="more">How playback works</Link></p>
        </div>
        <div className="split-copy facts" data-reveal>
          <dl>
            <div><dt>Video</dt><dd>H.264, HEVC, AV1, VP9, in hardware where the device has it</dd></div>
            <div><dt>Sound</dt><dd>AAC, AC-3, E-AC-3, DTS, TrueHD, Opus, decoded where the device can</dd></div>
            <div><dt>Subtitles</dt><dd>Text and image subtitles, in the file or beside it</dd></div>
            <div><dt>HDR</dt><dd>Read from every file, and stated honestly per device</dd></div>
          </dl>
          <p className="quiet small"><Link to="/platforms">What each platform plays</Link></p>
        </div>
      </section>

      <section className="band">
        <div className="section-head" data-reveal>
          <p className="eyebrow">Sources</p>
          <h2>Wherever your media lives.</h2>
        </div>
        <ul className="cards" data-reveal data-stagger>
          {sources.map(([name, text]) => <li key={name}><h3>{name}</h3><p>{text}</p></li>)}
        </ul>
        <p className="center"><Link to="/sources" className="more">About each source</Link></p>
      </section>

      <section className="split">
        <div className="split-copy">
          <p className="eyebrow">Private by design</p>
          <h2>No account. Nothing sent to us.</h2>
          <p>
            Reflux has no account, no analytics, no advertising, and no tracking. Your library, your progress, and your
            downloads stay on your device; your servers see only what you play from them. When a source is away, the
            library stays, and anything you kept for offline still plays.
          </p>
          <p><Link to="/privacy" className="more">Read the privacy page</Link></p>
        </div>
        <div className="split-copy">
          <p className="eyebrow">Platforms</p>
          <ul className="platform-list">
            {platforms.map(([name, where]) => <li key={name}><span>{name}</span><span className="quiet">{where}</span></li>)}
          </ul>
        </div>
      </section>

      <section className="closing" data-reveal>
        <MarkMotion className="mark-motion closing-mark" />
        <h2>Reflux is free.</h2>
        <p className="lead">No subscription, no paid tier, no ads. The beta opens soon on every platform above.</p>
        <div className="actions center">
          <Link to="/testing" className="button">How testing will work</Link>
          <Link to="/docs/getting-started" className="button ghost">Getting started</Link>
        </div>
      </section>
    </>
  )
}
