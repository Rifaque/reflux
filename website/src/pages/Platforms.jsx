import { Link } from 'react-router-dom'
import release from '../generated/release.json'
import { PageHead } from '../components.jsx'

// Each platform's needs and how it is distributed (docs/ROADMAP.md, Platforms; docs/RELEASE.md).
const platforms = [
  { name: 'Android phones and tablets', needs: 'Android 8.0 or later', from: 'Google Play (closed beta until 1.0)', notes: 'Folders are chosen with the system picker, one at a time, read-only. Picture-in-picture where the device offers it.' },
  { name: 'Android TV and Google TV', needs: 'Android 8.0 or later', from: 'Google Play (closed beta until 1.0)', notes: 'Built for the remote. What you are watching and your next episodes appear in the home screen’s Watch Next row.' },
  { name: 'Fire TV', needs: 'Fire OS 7 or later', from: 'Amazon Appstore (beta by invitation until 1.0)', notes: 'Devices on Vega OS do not run Android apps and are not supported.' },
  { name: 'Windows', needs: 'Windows 10 or 11, 64-bit (tested on Windows 11)', from: 'GitHub Release, linked from Download', notes: 'Everything included, playback engine too. Not yet signed by a publisher, so Windows may warn before it first opens.' },
  { name: 'Linux', needs: '64-bit, with your distribution’s libmpv', from: 'GitHub Release, linked from Download', notes: 'Runs on X11 and through XWayland. Install libmpv2 (Debian, Ubuntu) or mpv-libs (Fedora).' },
]

// What was seen playing on real devices and the Google TV emulator (docs/PLAYBACK.md, section 17), and nothing more.
// "Not decoded*": the device cannot play it; from a Jellyfin server that part is converted (see the note under the table).
const rows = [
  ['H.264 and HEVC (8-bit) to 1080p', 'Yes', 'Yes', 'Yes', 'Yes'],
  ['HEVC 10-bit to 1080p', 'Yes', 'Yes', 'Yes', 'Not decoded*'],
  ['HEVC 10-bit 4K', 'Yes', 'Yes', 'Yes on the Pad 7; not tested on the phone', 'Not decoded*'],
  ['H.264 10-bit', 'Yes', 'Not decoded*', 'Yes on the Phone 2a; not decoded* on the Pad 7', 'Not decoded*'],
  ['AV1', 'Yes', 'Yes, in hardware, to 4K', 'To 1080p in software on the Pad 7; not tested on the phone', 'To 1080p in software'],
  ['VP9', 'Yes', 'Yes', 'Yes on the Pad 7; not tested on the phone', 'Yes'],
  ['AAC, AC-3, E-AC-3', 'Yes', 'Yes', 'Yes on the Pad 7; AC-3 and E-AC-3 not decoded* on the Phone 2a', 'AAC yes; AC-3 and E-AC-3 not decoded*'],
  ['DTS and TrueHD', 'Yes', 'Not decoded*', 'Not decoded*', 'Not decoded*'],
  ['Opus', 'Yes', 'Yes', 'Yes on the Pad 7; not tested on the phone', 'Yes'],
  ['Text subtitles (SRT, ASS)', 'Yes', 'Yes', 'Yes', 'Yes'],
  ['Image subtitles (PGS, VobSub)', 'Yes', 'Yes', 'Yes', 'Yes'],
]

export default function Platforms() {
  return (
    <article className="page">
      <PageHead eyebrow="Platforms and compatibility" title="Where Reflux runs, and what it plays.">
        <p>One Reflux on every platform below, laid out for each and sharing one idea of your library. What follows is what has been seen working on real devices, and nothing more.</p>
      </PageHead>

      <section aria-labelledby="where">
        <h2 id="where">Platforms</h2>
        <div className="table-wrap">
          <table>
            <thead><tr><th scope="col">Platform</th><th scope="col">Needs</th><th scope="col">From</th><th scope="col">Good to know</th></tr></thead>
            <tbody>{platforms.map((p) => <tr key={p.name}><th scope="row">{p.name}</th><td>{p.needs}</td><td>{p.from}</td><td>{p.notes}</td></tr>)}</tbody>
          </table>
        </div>
        <p className="quiet small">Not supported: iOS, iPadOS, macOS, and Fire TV devices on Vega OS. {release.latest ? `The current ${release.latest.version.includes('-') ? 'beta' : 'release'} is ${release.latest.version}; ` : 'Reflux has not been released yet; '}see <Link to="/download">Download</Link> for where each platform stands.</p>
      </section>

      <section aria-labelledby="plays">
        <h2 id="plays">What it plays</h2>
        <p>Reflux plays your files as they are whenever the device can. When it cannot and the file is on a Jellyfin server, Reflux asks the server to convert only what the device lacks, usually the sound, and keeps the original picture. Local files and shares are played by the device alone, so what the device cannot decode is said, not hidden.</p>
        <div className="table-wrap">
          <table>
            <thead><tr><th scope="col">Format</th><th scope="col">Windows</th><th scope="col">Fire TV Stick 4K Plus</th><th scope="col">Android phone and tablet</th><th scope="col">Google TV (emulator)</th></tr></thead>
            <tbody>{rows.map(([name, ...cells]) => <tr key={name}><th scope="row">{name}</th>{cells.map((c, i) => <td key={i}>{c}</td>)}</tr>)}</tbody>
          </table>
        </div>
        <p className="quiet small">* Not decoded by that device. When the file is on a Jellyfin server, Reflux asks the server to convert only that part (the sound alone when only the sound is the problem). From a folder, a share, or Plex, Reflux plays what the device can and says what it cannot: a film whose sound the device cannot decode plays without that track, and Reflux says so.</p>
        <p className="quiet small">Seen on a Windows 11 laptop, a Fire TV Stick 4K Plus, a Xiaomi Pad 7, a Nothing Phone 2a, and the Google TV emulator (no physical Google TV device yet); other devices differ by their decoders, and Reflux asks each device what it has rather than assuming. Linux plays what its libmpv was built with.</p>
      </section>

      <section aria-labelledby="hdr">
        <h2 id="hdr">HDR, Dolby Vision, and surround sound</h2>
        <ul className="plain">
          <li><strong>HDR10 and HLG</strong> are read from every file and shown as such where the device and display say they can show them. On Windows, Reflux shows HDR video tone-mapped to SDR for now.</li>
          <li><strong>Dolby Vision</strong> is not yet claimed on any device: it plays as its HDR10 or SDR base where the file has one.</li>
          <li><strong>Surround sound</strong> plays to as many channels as your output takes. Passing Dolby or DTS untouched to a receiver is not verified yet.</li>
        </ul>
      </section>

      <section aria-labelledby="input">
        <h2 id="input">Remotes, controllers, and screen readers</h2>
        <p>Reflux is built for touch, mouse, keyboard, TV remotes, and game controllers with Xbox and PlayStation layouts; the first press of a controller turns the interface into one laid out for it. The Fire TV and Google TV remotes are used on real devices and the emulator every milestone; physical controllers are among what the beta qualifies. Every control is labelled for screen readers, and Reflux follows the system's text size and reduce-motion settings.</p>
      </section>
    </article>
  )
}
