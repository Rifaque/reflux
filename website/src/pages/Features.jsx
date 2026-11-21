import { Link } from 'react-router-dom'
import { PageHead, Shot } from '../components.jsx'
import { beta2Out } from '../released.js'

/** Marks what arrives with Beta 2 until a Beta 2 build is published (src/released.js): never shown as current early. */
function Soon({ beta2 }) {
  if (!beta2 || beta2Out) return null
  return <span className="chip chip-current soon">Coming in Beta 2</span>
}

function Feature({ id, eyebrow, title, beta2, children, shots, reverse }) {
  return (
    <section id={id} className={`feature${reverse ? ' reverse' : ''}`} aria-labelledby={`${id}-title`}>
      <div className="feature-copy" data-reveal>
        <p className="eyebrow">{eyebrow} <Soon beta2={beta2} /></p>
        <h2 id={`${id}-title`}>{title}</h2>
        {children}
      </div>
      <div className={`feature-shots shots-${shots.length}`} data-reveal>
        {shots.map(({ name, alt, kind }) => <figure key={name} className={`shot-${kind}`}><Shot name={name} alt={alt} /></figure>)}
      </div>
    </section>
  )
}

export default function Features() {
  return (
    <article className="page features-page">
      <PageHead eyebrow="Features" title="What Reflux does.">
        <p>
          Reflux plays the films and shows you already have, and makes them easy to find, understand, and enjoy on every
          screen in the house. Everything here works without a Reflux account, and nothing about what you watch is sent
          to the people who make it.
          {!beta2Out && ' Items marked “Coming in Beta 2” are finished and being prepared for testers; they are not in a build you can download yet.'}
        </p>
      </PageHead>

      <Feature
        id="library"
        eyebrow="One library"
        title="Every source, one library."
        shots={[{ name: 'home-tablet', kind: 'wide', alt: 'Reflux on a 10-inch Android tablet: Home, with Coffee Run to resume and a Continue Watching row.' }]}
      >
        <p>Folders on your devices, Jellyfin and Plex servers, Windows and NAS shares (SMB), and web shares (WebDAV) become one library. Reflux recognises films, shows, seasons, and episodes from ordinary file names, brings every copy of the same work together, and keeps the library useful while a source is away.</p>
        <p><Link to="/sources" className="more">About each source</Link></p>
      </Feature>

      <Feature
        id="versions"
        eyebrow="Versions"
        title="Know what you are about to play."
        reverse
        shots={[{ name: 'details-tablet', kind: 'wide', alt: 'Sprite Fright’s page in Reflux on a tablet: part-watched, with Resume, From the start, and its 1080p version from a home server.' }]}
      >
        <p>A film’s page shows each of its versions with its resolution, HDR, codecs, sound, and where it lives. Play chooses the one this device plays best and can say why; you can pin another. Resume works from any copy of the same cut.</p>
        <p><Link to="/docs/playback" className="more">How playback works</Link></p>
      </Feature>

      <Feature
        id="quick-view"
        eyebrow="Quick View"
        title="A closer look without leaving the row."
        beta2
        shots={[
          { name: 'quickview-tv', kind: 'wide', alt: 'Reflux on a TV: a quick view of Sintel over the Movies grid, opened with the remote’s Menu button.' },
          { name: 'quickview-phone', kind: 'tall', alt: 'Reflux on a phone: a quick view of Sintel over the Movies grid, with Play, Favorite, Download, and Details.' },
        ]}
      >
        <p>Long-press a film or show, right-click it, press X on a controller, or Menu on a TV remote: Reflux shows what it is, with Play or Resume, Favorite, Download, and Details. Back closes it.</p>
      </Feature>

      <Feature
        id="player"
        eyebrow="The player"
        title="The picture comes first."
        reverse
        shots={[
          { name: 'player-tv', kind: 'wide', alt: 'Reflux’s player on a TV: Elephants Dream playing, with its title and timeline.' },
          { name: 'miniplayer-phone', kind: 'tall', alt: 'Reflux on a phone: Home with Elephants Dream still playing in the mini-player above the navigation.' },
        ]}
      >
        <p>The controls are a title, the way out, and one row with the timeline, and they step aside while you watch. Back puts what you are watching in a mini-player while you browse; on Android phones and tablets it continues in picture-in-picture when you leave Reflux.</p>
        <ul className="ticks">
          <li>Chapters, skip intro and credits, the next episode</li>
          <li>Audio and subtitle tracks, with your subtitle choice remembered for each profile</li>
          <li>Scene previews on the timeline from Jellyfin and Plex <Soon beta2 /></li>
        </ul>
      </Feature>

      <Feature
        id="remote"
        eyebrow="Remote and controller"
        title="Made for the couch, too."
        beta2
        shots={[
          { name: 'keyboard-tv', kind: 'wide', alt: 'Reflux’s own on-screen keyboard on a TV, with “si” typed and the title Sintel suggested.' },
          { name: 'movies-tv', kind: 'wide', alt: 'Reflux on a TV: the navigation rail opened with Left over the Movies grid.' },
        ]}
      >
        <p>TV remotes and Xbox and PlayStation controllers reach everything, with one consistent layout: Y goes to the navigation and back, the bumpers move between sections, LT hides the button hints, and the right stick moves through scenes in the player. Reflux has its own keyboard for remotes and controllers, which suggests titles from your library as you type.</p>
        <p><Link to="/docs" className="more">Controller and remote guide</Link></p>
      </Feature>

      <section className="feature-grid" aria-label="More">
        <div data-reveal>
          <h2>Offline</h2>
          <p>Keep a film, an episode, or a whole season for watching without its source, on Android, Windows, and Linux. Copies are versions of the same film, so your place carries over.</p>
        </div>
        <div data-reveal>
          <h2>Profiles</h2>
          <p>Everyone who watches on a device can have their own history, favourites, and server accounts, with an optional PIN. One profile never sees another’s.</p>
        </div>
        <div data-reveal>
          <h2>Private</h2>
          <p>No Reflux account, no analytics, no ads. Your library stays on your device; to recognise titles Reflux can ask Trakt with the title alone, and you can turn that off. <Link to="/privacy">Privacy</Link>.</p>
        </div>
        <div data-reveal>
          <h2>Everywhere</h2>
          <p>Android phones and tablets, Android TV and Google TV, Fire TV, Windows, and Linux, laid out for each. <Link to="/platforms">Platforms</Link>.</p>
        </div>
      </section>

      <section className="closing-band" data-reveal>
        <h2>What comes next.</h2>
        <p className="lead">Separate libraries, sorting and filters, more playback depth, and more sources are planned after 1.0.</p>
        <p><Link to="/roadmap" className="more">See the roadmap</Link></p>
      </section>
    </article>
  )
}
