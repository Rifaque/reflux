import { Link, NavLink, useLocation } from 'react-router-dom'
import { useEffect, useRef } from 'react'
import release from './generated/release.json'
import showcase from './showcase.json'

/** The Reflux mark, in the colour of the text around it. Decorative unless given a label. */
export function Mark({ className = 'mark', label }) {
  const { viewBox, d } = release.brand.mark
  return (
    <svg className={className} viewBox={viewBox} role={label ? 'img' : undefined} aria-label={label} aria-hidden={label ? undefined : 'true'} focusable="false">
      <path fill="currentColor" fillRule="evenodd" d={d} />
    </svg>
  )
}

/** Mark and wordmark together: the site's name in the header. */
export function Lockup({ className = 'lockup' }) {
  const { viewBox, d } = release.brand.lockup
  return (
    <svg className={className} viewBox={viewBox} role="img" aria-label="Reflux" focusable="false">
      <path fill="currentColor" fillRule="evenodd" d={d} />
    </svg>
  )
}

/** A real Reflux screen, rendered from the app (docs/WEBSITE.md, "Product images"). */
export function Shot({ name, alt, className = '', eager = false }) {
  const [width, height] = showcase.images[name]
  return (
    <img
      className={`shot ${className}`}
      src={`/showcase/${name}.webp`}
      width={width}
      height={height}
      alt={alt}
      loading={eager ? 'eager' : 'lazy'}
      decoding="async"
      fetchPriority={eager ? 'high' : undefined}
    />
  )
}

/** An outside link: always HTTPS, never told where it came from. */
export function Out({ href, children, className }) {
  return <a className={className} href={href} rel="noopener noreferrer">{children}</a>
}

const primary = [
  ['/platforms', 'Platforms'],
  ['/sources', 'Sources'],
  ['/docs', 'Docs'],
  ['/releases', 'Releases'],
  ['/support', 'Support'],
]

export function Header() {
  const menu = useRef(null)
  const { pathname } = useLocation()
  // A page chosen from the menu closes it.
  useEffect(() => { if (menu.current) menu.current.open = false }, [pathname])
  const links = primary.map(([to, label]) => <NavLink key={to} to={to}>{label}</NavLink>)
  return (
    <header className="site-header">
      <div className="bar">
        <Link to="/" className="home-link" aria-label="Reflux home"><Lockup /></Link>
        <nav className="nav-wide" aria-label="Main">{links}</nav>
        <Link to="/download" className="button small nav-cta">Get Reflux</Link>
        <details className="nav-menu" ref={menu}>
          <summary aria-label="Menu"><span aria-hidden="true" className="burger" /></summary>
          <nav aria-label="Main">
            {links}
            <NavLink to="/download">Download</NavLink>
            <NavLink to="/testing">Testing</NavLink>
          </nav>
        </details>
      </div>
    </header>
  )
}

export function Footer() {
  const status = release.latest ? `Reflux ${release.latest.version}` : `Reflux ${release.version}, in development`
  return (
    <footer className="site-footer">
      <div className="footer-grid">
        <div className="footer-brand">
          <Mark className="mark footer-mark" />
          <p>Your media, played well.</p>
          <p className="quiet small">{status}. Made by RSX Develops.</p>
        </div>
        <nav aria-label="Product">
          <h2>Product</h2>
          <Link to="/platforms">Platforms</Link>
          <Link to="/sources">Sources</Link>
          <Link to="/download">Download</Link>
          <Link to="/releases">Releases</Link>
          <Link to="/testing">Testing</Link>
        </nav>
        <nav aria-label="Help">
          <h2>Help</h2>
          <Link to="/docs">Documentation</Link>
          <Link to="/docs/troubleshooting">FAQ</Link>
          <Link to="/support">Support</Link>
          <Link to="/security">Security</Link>
        </nav>
        <nav aria-label="Legal">
          <h2>Legal</h2>
          <Link to="/privacy">Privacy</Link>
          <Link to="/terms">Terms</Link>
          <Link to="/legal/third-party">Third-party notices</Link>
        </nav>
      </div>
      <p className="footer-fine quiet small">
        Copyright © 2026 RSX Develops. Reflux is proprietary software, free to use. Film artwork shown on this site is from the
        Blender Foundation's open films, used under Creative Commons licenses (<Link to="/legal/third-party#artwork">credits</Link>).
      </p>
    </footer>
  )
}

/** A page's opening: a small label, the title, and what the page is for. */
export function PageHead({ eyebrow, title, children }) {
  return (
    <header className="page-head">
      {eyebrow && <p className="eyebrow">{eyebrow}</p>}
      <h1>{title}</h1>
      {children && <div className="lead">{children}</div>}
    </header>
  )
}

/** The project's current state, said the same way everywhere. */
export function Status() {
  return release.latest
    ? <span className="status-pill"><span className="dot" aria-hidden="true" />Reflux {release.latest.version} is available</span>
    : <span className="status-pill"><span className="dot pending" aria-hidden="true" />In development · beta coming</span>
}
