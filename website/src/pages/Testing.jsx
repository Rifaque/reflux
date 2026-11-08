import { Link } from 'react-router-dom'
import release from '../generated/release.json'
import { PageHead } from '../components.jsx'

const channels = [
  ['Android phones, tablets, and Google TV', 'Google Play’s closed testing. You join with the Google account you use on the device, then install Reflux from Google Play as usual; updates arrive the same way.'],
  ['Fire TV', 'The Amazon Appstore’s Live App Testing. You receive an invitation for your Amazon account and install from the Appstore on the device.'],
  ['Windows and Linux', 'Builds from Reflux’s GitHub releases, linked from Download, each with its SHA-256 and the commit it was built from.'],
]

const report = [
  'What you were doing, on which device, and what you expected.',
  'In Reflux: About Reflux → “Save a support report”. It holds your version, device, sources’ kinds, the last playback, and recent problems, with passwords, addresses, file paths, and titles removed. Reflux never sends it; you attach it.',
  'Send both through Support.',
]

export default function Testing() {
  return (
    <article className="page">
      <PageHead eyebrow="Testing" title="The Reflux beta.">
        <p>The beta is not open yet. When it opens, this page will be where testers start: what to install, where, and how to tell us what happened.</p>
      </PageHead>
      <section aria-labelledby="build">
        <h2 id="build">What will be tested</h2>
        <p>Beta builds are numbered <code>0.9.0-beta.1</code>, <code>beta.2</code>, and so on, each with its notes on <Link to="/releases">Releases</Link>. Today’s development version is {release.version}.</p>
      </section>
      <section aria-labelledby="channels">
        <h2 id="channels">How each platform joins</h2>
        <dl className="steps">{channels.map(([name, how]) => <div key={name}><dt>{name}</dt><dd>{how}</dd></div>)}</dl>
        <p className="quiet small">Testers are chosen for coverage: the devices, sources, and controllers they use, so that Reflux is tried on as many setups as possible.</p>
      </section>
      <section aria-labelledby="report">
        <h2 id="report">Reporting a problem</h2>
        <ol className="plain numbered">{report.map((r) => <li key={r}>{r}</li>)}</ol>
        <p><Link to="/support" className="more">Support</Link></p>
      </section>
      <section aria-labelledby="known">
        <h2 id="known">Known limits</h2>
        <ul className="plain">
          <li>Windows builds are not signed by a publisher yet (<Link to="/download">Download</Link> says what that means).</li>
          <li>Dolby Vision is not claimed on any device, and HDR on Windows is shown tone-mapped.</li>
          <li>Downloads for offline continue only while Reflux is open.</li>
        </ul>
      </section>
    </article>
  )
}
