import { Link } from 'react-router-dom'
import { PageHead } from '../components.jsx'

// Each platform's needs and how it is distributed (docs/ROADMAP.md, Platforms; docs/RELEASE.md).
const platforms = [
  { name: 'Android phones and tablets', needs: 'Android 8.0 or later', from: 'Google Play', notes: 'Folders are chosen with the system picker, one at a time, read-only. Picture-in-picture where the device offers it.' },
  { name: 'Android TV and Google TV', needs: 'Android 8.0 or later', from: 'Google Play', notes: 'Built for the remote. What you are watching and your next episodes appear in the home screen’s Watch Next row.' },
  { name: 'Fire TV', needs: 'Fire OS 7 or later', from: 'Amazon Appstore', notes: 'Devices on Vega OS do not run Android apps and are not supported.' },
  { name: 'Windows', needs: 'Windows 10 or 11, 64-bit (tested on Windows 11)', from: 'A download from this site', notes: 'Everything included, playback engine too. Not yet signed by a publisher, so Windows may warn before it first opens.' },
  { name: 'Linux', needs: '64-bit, with your distribution’s libmpv', from: 'A download from this site', notes: 'Runs on X11 and through XWayland. Install libmpv2 (Debian, Ubuntu) or mpv-libs (Fedora).' },
]

// What was seen playing on real devices (docs/PLAYBACK.md, section 17). "Converted": a server changes that part.
const rows = [
  ['H.264 and HEVC to 1080p', 'Yes', 'Yes', 'Yes', 'Yes'],
  ['HEVC 10-bit 4K', 'Yes', 'Yes', 'Yes', 'Depends on the device'],
  ['AV1', 'Yes', 'Yes', 'Yes, to 1080p in software on some', 'Depends on the device'],
  ['VP9', 'Yes', 'Yes', 'Yes', 'Yes'],
  ['AAC, AC-3, E-AC-3', 'Yes', 'Yes', 'Yes on most; converted where a phone lacks AC-3', 'Depends on the device'],
  ['DTS and TrueHD', 'Yes', 'Converted by a server', 'Converted by a server', 'Converted by a server'],
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
        <p className="quiet small">Not supported: iOS, iPadOS, macOS, and Fire TV devices on Vega OS. Reflux has not been released yet; see <Link to="/download">Download</Link> for where each platform stands.</p>
      </section>

      <section aria-labelledby="plays">
        <h2 id="plays">What it plays</h2>
        <p>Reflux plays your files as they are whenever the device can. When it cannot and the file is on a Jellyfin server, Reflux asks the server to convert only what the device lacks, usually the sound, and keeps the original picture. Local files and shares are played by the device alone, so what the device cannot decode is said, not hidden.</p>
        <div className="table-wrap">
          <table>
            <thead><tr><th scope="col">Format</th><th scope="col">Windows</th><th scope="col">Fire TV Stick 4K Plus</th><th scope="col">Android phones and tablets</th><th scope="col">Google TV</th></tr></thead>
            <tbody>{rows.map(([name, ...cells]) => <tr key={name}><th scope="row">{name}</th>{cells.map((c, i) => <td key={i}>{c}</td>)}</tr>)}</tbody>
          </table>
        </div>
        <p className="quiet small">Seen on a Windows 11 laptop, a Fire TV Stick 4K Plus, a Xiaomi Pad 7, a Nothing Phone 2a, and the Google TV emulator; other devices differ by their decoders, and Reflux asks each device what it has rather than assuming. Linux plays what its libmpv was built with.</p>
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
        <p>Touch, mouse, keyboard, TV remotes, and Xbox and PlayStation controllers all drive Reflux (physical controllers are among what the beta verifies); the first press of a controller turns the interface into one laid out for it. Every control is labelled for screen readers, and Reflux follows the system's text size and reduce-motion settings.</p>
      </section>
    </article>
  )
}
