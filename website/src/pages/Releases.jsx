import release from '../generated/release.json'
import { Link } from 'react-router-dom'
import { PageHead } from '../components.jsx'

/** An anchor for a release's category: v-0-9-0-beta-1-playback-media. */
const slug = (...parts) => parts.join(' ').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '')

export default function Releases() {
  const sections = release.changelog.filter((section) => section.items.length || section.version !== 'Unreleased')
  return (
    <article className="page releases-page">
      <PageHead eyebrow="Releases" title="What's new in Reflux.">
        <p>
          Every change people using Reflux will notice, version by version, from its changelog, grouped the same way in every
          release: playback, navigation and controllers, search, the library, profiles and privacy, and more.{' '}
          {release.latest ? '' : 'Nothing has been released yet; “Unreleased” is what the next build brings.'}
          {' '}What comes after is on the <Link to="/roadmap">roadmap</Link>.
        </p>
      </PageHead>
      <nav className="release-index" aria-label="Releases on this page">
        {sections.map((s) => <a key={s.version} href={`#v-${s.version}`}>{s.version === 'Unreleased' ? 'In development' : s.version}</a>)}
      </nav>
      {sections.map((section) => (
        <section key={section.version} className="release" aria-labelledby={`v-${section.version}`}>
          <h2 id={`v-${section.version}`}>
            {section.version === 'Unreleased' ? 'In development' : `Reflux ${section.version}`}
            {section.date && <span className="quiet release-date"> · {section.date}</span>}
          </h2>
          {section.version === 'Unreleased' && <p className="quiet small">Not in a downloadable build yet: this is what the next beta is being built with, not a promise of when it arrives.</p>}
          {section.version === release.latest?.version && <p className="quiet small">{section.version.includes('-') ? <>The current beta. <Link to="/testing">Join the beta</Link> or <Link to="/download">download it</Link>.</> : <>The current release. <Link to="/download">Download</Link>.</>}</p>}
          {section.groups?.length > 1 && (
            <ul className="release-groups" aria-label={`Categories in ${section.version}`}>
              {section.groups.map((g) => <li key={g.title}><a href={`#${slug(section.version, g.title)}`}>{g.title} <span className="quiet">{g.items.length}</span></a></li>)}
            </ul>
          )}
          {section.groups?.length
            ? section.groups.map((group) => (
              <div key={group.title} className="release-group">
                <h3 id={slug(section.version, group.title)}>{group.title}</h3>
                <ul className="changes">{group.items.map((item, i) => <li key={i}>{item}</li>)}</ul>
              </div>
            ))
            : <ul className="changes">{section.items.map((item, i) => <li key={i}>{item}</li>)}</ul>}
        </section>
      ))}
    </article>
  )
}
