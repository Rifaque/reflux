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
          <p>E-mail <a href="mailto:support@rsxdevelops.dev">support@rsxdevelops.dev</a>: it is the one place every report is read, and it needs no account. Say what you were doing and on which device, and attach a support report: in Reflux, <strong>Settings → About Reflux → Save a support report</strong>. It has passwords, addresses, file paths, and titles taken out, and Reflux never sends it by itself. Beta testers: <Link to="/testing#report">what to include</Link>. If you prefer GitHub, an <Out href="https://github.com/Rifaque/reflux/issues">issue</Out> works too, but it is public.</p>
        </section>
        <section aria-labelledby="security">
          <h2 id="security">Security problems</h2>
          <p>Please report them privately, never in a public issue: through GitHub’s private vulnerability reporting, as <Link to="/security">Security</Link> explains.</p>
        </section>
        <section aria-labelledby="contact">
          <h2 id="contact">Contact address</h2>
          <p><a href="mailto:support@rsxdevelops.dev">support@rsxdevelops.dev</a> reaches RSX Develops, who make Reflux, for questions, problems, and feedback. Store listings name the same address.</p>
        </section>
      </div>
    </article>
  )
}
