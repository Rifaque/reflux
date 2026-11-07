import release from '../generated/release.json'

export default function Releases() {
  return (
    <article className="page">
      <h1>Releases</h1>
      <p className="lead">What changes for people using Reflux, version by version, from its changelog. Nothing has been released yet; "Unreleased" is what the next build will bring.</p>
      {release.changelog.map((section) => (
        <section key={section.version}>
          <h2>{section.version === 'Unreleased' ? `Unreleased (Reflux ${release.version} in development)` : section.version}{section.date ? ` — ${section.date}` : ''}</h2>
          <ul>{section.items.map((item, i) => <li key={i}>{item}</li>)}</ul>
        </section>
      ))}
    </article>
  )
}
