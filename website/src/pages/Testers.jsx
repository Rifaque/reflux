import { Link } from 'react-router-dom'

export default function Testers() {
  return (
    <article className="page">
      <h1>Testing Reflux</h1>
      <p className="lead">The beta is not open yet. When it is, this page will say how to join and what to test.</p>
      <h2>How it will work</h2>
      <ul>
        <li>Android phones, tablets, and Google TV: through Google Play's closed testing.</li>
        <li>Fire TV: through the Amazon Appstore's testing.</li>
        <li>Windows and Linux: builds from Reflux's GitHub releases, with their checksums, linked from <Link to="/download">Download</Link>.</li>
      </ul>
      <p>Testers will be chosen for the devices, sources, and controllers they use, so that Reflux is tried on as many kinds of setup as possible.</p>
    </article>
  )
}
