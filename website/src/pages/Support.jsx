import { Link } from 'react-router-dom'

export default function Support() {
  return (
    <article className="page">
      <h1>Support</h1>
      <p className="lead">Reflux is made by RSX Develops.</p>
      <h2>Problems and questions</h2>
      <p>Open an issue on <a href="https://github.com/Rifaque/reflux/issues" rel="noopener noreferrer">Reflux's GitHub</a>. Say what you were doing, on which device, and attach Reflux's diagnostics log if you can: it is kept on your device, never sent anywhere by itself, and has addresses' credentials and tokens removed.</p>
      <h2>Security</h2>
      <p>Please do not report security problems in public issues. See <Link to="/security">Security</Link>.</p>
      <h2>Contact address</h2>
      <p className="quiet">A support email address for Reflux will be published here before testing opens.</p>
    </article>
  )
}
