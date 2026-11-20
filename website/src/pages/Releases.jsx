import release from '../generated/release.json'
import { Link } from 'react-router-dom'
import { PageHead } from '../components.jsx'

export default function Releases() {
  return (
    <article className="page">
      <PageHead eyebrow="Releases" title="What's new in Reflux.">
        <p>Every change people using Reflux will notice, version by version, from its changelog. {release.latest ? '' : 'Nothing has been released yet; “Unreleased” is what the next build brings.'}</p>
      </PageHead>
      {release.changelog.filter((section) => section.items.length || section.version !== 'Unreleased').map((section) => (
        <section key={section.version} className="release" aria-labelledby={`v-${section.version}`}>
          <h2 id={`v-${section.version}`}>
            {section.version === 'Unreleased' ? 'Unreleased' : `Reflux ${section.version}`}
            {section.date && <span className="quiet release-date"> · {section.date}</span>}
          </h2>
          {section.version === 'Unreleased' && <p className="quiet small">In development; not in a downloadable build yet. This is what the next beta is being built with, not a promise of when it arrives.</p>}
          {section.version === release.latest?.version && <p className="quiet small">{section.version.includes('-') ? <>The current beta. <Link to="/testing">Join the beta</Link> or <Link to="/download">download it</Link>.</> : <>The current release. <Link to="/download">Download</Link>.</>}</p>}
          {section.groups?.length
            ? section.groups.map((group) => (
              <div key={group.title} className="release-group">
                <h3>{group.title}</h3>
                <ul className="changes">{group.items.map((item, i) => <li key={i}>{item}</li>)}</ul>
              </div>
            ))
            : <ul className="changes">{section.items.map((item, i) => <li key={i}>{item}</li>)}</ul>}
        </section>
      ))}
    </article>
  )
}
