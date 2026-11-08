import { Link } from 'react-router-dom'
import { Out, PageHead } from '../components.jsx'

export default function Support() {
  return (
    <article className="page">
      <PageHead eyebrow="Support" title="Help and feedback.">
        <p>Reflux is made by RSX Develops. Questions, problems, and ideas are all welcome.</p>
      </PageHead>
      <div className="support-grid">
        <section aria-labelledby="first">
          <h2 id="first">Start here</h2>
          <p>Most questions are answered in the <Link to="/docs">documentation</Link>, and the common problems in <Link to="/docs/troubleshooting">Troubleshooting and FAQ</Link>.</p>
        </section>
        <section aria-labelledby="problem">
          <h2 id="problem">Report a problem or send feedback</h2>
          <p>Open an issue on <Out href="https://github.com/Rifaque/reflux/issues">Reflux’s GitHub</Out>. Say what you were doing and on which device, and attach a support report: in Reflux, <strong>Sources → About Reflux → Save a support report</strong>. It has passwords, addresses, file paths, and titles taken out, and Reflux never sends it by itself.</p>
        </section>
        <section aria-labelledby="security">
          <h2 id="security">Security problems</h2>
          <p>Please report them privately, not in a public issue: see <Link to="/security">Security</Link>.</p>
        </section>
        <section aria-labelledby="contact">
          <h2 id="contact">Contact address</h2>
          <p className="quiet">A support e-mail address for Reflux will be published here before testing opens.</p>
        </section>
      </div>
    </article>
  )
}
