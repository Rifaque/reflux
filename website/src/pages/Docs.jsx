// The documentation portal (docs/release/WEBSITE.md, "Documentation"): a sidebar of every page in groups, search over every
// page's words, breadcrumbs, the page's own contents, and the pages before and after it. Everything is rendered on the
// server: the sidebar is a list of links, the narrow screen's menu a native disclosure, so the documentation reads and
// navigates without JavaScript; search and the copy buttons are what JavaScript adds.
import { Children, Fragment, cloneElement, isValidElement, useId, useMemo, useState } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { PageHead } from '../components.jsx'
import { DOC_GROUPS, DOC_PAGES, DOC_REFERENCE } from '../docs/pages.js'
import { DOC_BODIES } from '../docs/content.jsx'
import { anchorOf, headingsOf, textOf } from '../docs/blocks.jsx'

export const docSlugs = DOC_PAGES.map((p) => p.slug)

/** The words of every page, made the first time someone searches. */
let index = null
const searchIndex = () => (index ??= DOC_PAGES.map((page) => ({ ...page, text: `${page.title} ${page.summary} ${textOf(DOC_BODIES[page.slug])}`.replace(/\s+/g, ' ') })))

/** Pages whose words contain every word of [query], best first: a title match before a match in the text. */
export function searchDocs(query) {
  const words = query.toLowerCase().split(/\s+/).filter((w) => w.length > 1)
  if (!words.length) return []
  return searchIndex()
    .filter((page) => words.every((w) => page.text.toLowerCase().includes(w)))
    .map((page) => {
      const lower = page.text.toLowerCase()
      const at = lower.indexOf(words[0], page.title.length + page.summary.length)
      const snippet = at < 0 ? page.summary : '…' + page.text.slice(Math.max(0, at - 60), at + 110).trim() + '…'
      const score = words.filter((w) => page.title.toLowerCase().includes(w)).length
      return { ...page, snippet, score }
    })
    .sort((a, b) => b.score - a.score)
}

function Search() {
  const [query, setQuery] = useState('')
  const results = useMemo(() => searchDocs(query), [query])
  const id = useId()
  return (
    <div className="doc-search" role="search">
      <label htmlFor={id} className="visually-hidden">Search the documentation</label>
      <input id={id} type="search" placeholder="Search the documentation" value={query} onChange={(e) => setQuery(e.target.value)} autoComplete="off" />
      {query.trim().length > 1 && (
        <div className="doc-results" aria-live="polite">
          {results.length
            ? <ul>{results.slice(0, 8).map((r) => <li key={r.slug}><Link to={r.path} onClick={() => setQuery('')}><strong>{r.title}</strong> <span className="quiet small">{r.group}</span><span className="small">{r.snippet}</span></Link></li>)}</ul>
            : <p className="small quiet">Nothing in the documentation matches “{query.trim()}”. <Link to="/support">Support</Link> can help.</p>}
        </div>
      )}
    </div>
  )
}

/** Every page, in groups that open and close; the group of the page being read starts open, and so do all on the index. */
function DocNav({ current }) {
  return (
    <>
      {DOC_GROUPS.map((group) => (
        <details key={group.title} className="doc-group" open={!current || group.pages.some((p) => p.slug === current)}>
          <summary>{group.title}</summary>
          <ul>{group.pages.map((p) => <li key={p.slug}><NavLink to={`/docs/${p.slug}`} end viewTransition>{p.title}</NavLink></li>)}</ul>
        </details>
      ))}
      <details className="doc-group" open={!current}>
        <summary>Reference</summary>
        <ul>{DOC_REFERENCE.map(([to, label]) => <li key={to}><Link to={to} viewTransition>{label}</Link></li>)}</ul>
      </details>
    </>
  )
}

/** The sidebar beside the page on a wide screen, and a menu that opens above it on a narrow one. */
function Sidebar({ current }) {
  return (
    <>
      <nav className="doc-sidebar" aria-label="Documentation">
        <Link to="/docs" className="doc-home" viewTransition>Documentation</Link>
        <Search />
        <DocNav current={current} />
      </nav>
      <details className="doc-menu">
        <summary>Documentation menu</summary>
        <nav aria-label="Documentation">
          <Link to="/docs" className="doc-home" viewTransition>Documentation</Link>
          <Search />
          <DocNav current={current} />
        </nav>
      </details>
    </>
  )
}

/** [body] with an anchor on each section heading, so the contents and a link can reach it. */
function withAnchors(body) {
  const anchor = (node) => (isValidElement(node) && node.type === 'h2' ? cloneElement(node, { id: anchorOf(textOf(node.props.children)) }) : node)
  if (isValidElement(body) && body.type === Fragment) return <>{Children.map(body.props.children, anchor)}</>
  return body
}

export function DocsIndex() {
  return (
    <div className="doc-layout">
      <Sidebar current={null} />
      <article className="page doc-page">
        <PageHead eyebrow="Documentation" title="Using Reflux.">
          <p>From installing Reflux to playing your first film, every platform's install guide, and what to do when something does not work.</p>
        </PageHead>
        {DOC_GROUPS.map((group) => (
          <section key={group.title} aria-labelledby={anchorOf(group.title)}>
            <h2 id={anchorOf(group.title)}>{group.title}</h2>
            <ul className="doc-index">
              {group.pages.map((p) => <li key={p.slug}><Link to={`/docs/${p.slug}`} viewTransition><h3>{p.title}</h3><p>{p.summary}</p></Link></li>)}
            </ul>
          </section>
        ))}
      </article>
    </div>
  )
}

export function DocArticle({ slug }) {
  const position = DOC_PAGES.findIndex((p) => p.slug === slug)
  const page = DOC_PAGES[position]
  const previous = DOC_PAGES[position - 1]
  const next = DOC_PAGES[position + 1]
  const body = DOC_BODIES[slug]
  const headings = headingsOf(body)
  return (
    <div className="doc-layout">
      <Sidebar current={slug} />
      <article className="page doc-page prose">
        <nav aria-label="Breadcrumb" className="breadcrumb">
          <ol>
            <li><Link to="/docs" viewTransition>Documentation</Link></li>
            <li>{page.group}</li>
            <li aria-current="page">{page.title}</li>
          </ol>
        </nav>
        <PageHead eyebrow={page.group} title={page.title}><p>{page.summary}</p></PageHead>
        {headings.length > 2 && (
          <nav className="doc-toc" aria-label="On this page">
            <p className="doc-toc-title">On this page</p>
            <ul>{headings.map((h) => <li key={h}><a href={`#${anchorOf(h)}`}>{h}</a></li>)}</ul>
          </nav>
        )}
        {withAnchors(body)}
        <nav className="doc-pager" aria-label="More documentation">
          {previous ? <Link to={previous.path} rel="prev" viewTransition><span className="quiet small">Previous</span>{previous.title}</Link> : <span />}
          {next ? <Link to={next.path} rel="next" viewTransition className="next"><span className="quiet small">Next</span>{next.title}</Link> : <Link to="/download" className="next" viewTransition><span className="quiet small">Next</span>Download Reflux</Link>}
        </nav>
      </article>
    </div>
  )
}
