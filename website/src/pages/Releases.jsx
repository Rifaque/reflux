import release from '../generated/release.json'
import { PageHead } from '../components.jsx'

export default function Releases() {
  return (
    <article className="page">
      <PageHead eyebrow="Releases" title="What's new in Reflux.">
        <p>Every change people using Reflux will notice, version by version, from its changelog. {release.latest ? '' : 'Nothing has been released yet; “Unreleased” is what the next build brings.'}</p>
      </PageHead>
      {release.changelog.map((section) => (
        <section key={section.version} className="release" aria-labelledby={`v-${section.version}`}>
          <h2 id={`v-${section.version}`}>
            {section.version === 'Unreleased' ? 'Unreleased' : `Reflux ${section.version}`}
            {section.date && <span className="quiet release-date"> · {section.date}</span>}
          </h2>
          {section.version === 'Unreleased' && <p className="quiet small">In development as {release.version}.</p>}
          <ul className="changes">{section.items.map((item, i) => <li key={i}>{item}</li>)}</ul>
        </section>
      ))}
    </article>
  )
}
