import { Link, NavLink, Route, Routes, useLocation } from 'react-router-dom'
import { useEffect } from 'react'
import release from './generated/release.json'
import Home from './pages/Home.jsx'
import Download from './pages/Download.jsx'
import Releases from './pages/Releases.jsx'
import Docs from './pages/Docs.jsx'
import Support from './pages/Support.jsx'
import Testers from './pages/Testers.jsx'
import { Privacy, Terms, Notices, Security } from './pages/Legal.jsx'
import NotFound from './pages/NotFound.jsx'

const nav = [
  ['/download', 'Download'],
  ['/docs', 'Docs'],
  ['/releases', 'Releases'],
  ['/support', 'Support'],
]

const titles = {
  '/': 'Reflux', '/download': 'Download', '/docs': 'Documentation', '/releases': 'Releases', '/support': 'Support',
  '/testers': 'Testing Reflux', '/privacy': 'Privacy', '/terms': 'Terms', '/legal/third-party': 'Third-party notices', '/security': 'Security',
}

export default function App() {
  const { pathname } = useLocation()
  useEffect(() => {
    document.title = pathname === '/' ? 'Reflux' : `${titles[pathname] ?? 'Not found'} · Reflux`
    window.scrollTo(0, 0)
  }, [pathname])
  return (
    <>
      <a className="skip" href="#main">Skip to content</a>
      <header className="top">
        <Link to="/" className="wordmark">Reflux</Link>
        <nav aria-label="Main">
          {nav.map(([to, label]) => <NavLink key={to} to={to}>{label}</NavLink>)}
        </nav>
      </header>
      <main id="main">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/download" element={<Download />} />
          <Route path="/releases" element={<Releases />} />
          <Route path="/docs" element={<Docs />} />
          <Route path="/support" element={<Support />} />
          <Route path="/testers" element={<Testers />} />
          <Route path="/privacy" element={<Privacy />} />
          <Route path="/terms" element={<Terms />} />
          <Route path="/legal/third-party" element={<Notices />} />
          <Route path="/security" element={<Security />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
      <footer className="bottom">
        <p>Reflux {release.version}, in development. Made by RSX Develops.</p>
        <nav aria-label="Legal">
          <Link to="/privacy">Privacy</Link>
          <Link to="/terms">Terms</Link>
          <Link to="/legal/third-party">Third-party notices</Link>
          <Link to="/security">Security</Link>
          <Link to="/testers">Testing</Link>
        </nav>
      </footer>
    </>
  )
}
