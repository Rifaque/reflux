import { Route, Routes, useLocation } from 'react-router-dom'
import { useEffect } from 'react'
import { routeFor, titleOf } from './routes.js'
import { Footer, Header } from './components.jsx'
import { useReveal } from './motion.jsx'
import Home from './pages/Home.jsx'
import Platforms from './pages/Platforms.jsx'
import Sources from './pages/Sources.jsx'
import Download from './pages/Download.jsx'
import Testing from './pages/Testing.jsx'
import { DocArticle, DocsIndex, docSlugs } from './pages/Docs.jsx'
import Releases from './pages/Releases.jsx'
import Support from './pages/Support.jsx'
import { Notices, Privacy, Security, Terms } from './pages/Legal.jsx'
import NotFound from './pages/NotFound.jsx'

export default function App() {
  const { pathname, hash } = useLocation()
  useReveal()
  // Prerendered pages carry their own titles; moving between them in the browser keeps them right.
  useEffect(() => {
    const route = routeFor(pathname)
    document.title = titleOf(route)
    if (!hash) window.scrollTo(0, 0)
  }, [pathname, hash])
  return (
    <>
      <a className="skip" href="#main">Skip to content</a>
      <Header />
      <main id="main" tabIndex={-1}>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/platforms" element={<Platforms />} />
          <Route path="/sources" element={<Sources />} />
          <Route path="/download" element={<Download />} />
          <Route path="/testing" element={<Testing />} />
          <Route path="/docs" element={<DocsIndex />} />
          {docSlugs.map((slug) => <Route key={slug} path={`/docs/${slug}`} element={<DocArticle slug={slug} />} />)}
          <Route path="/releases" element={<Releases />} />
          <Route path="/support" element={<Support />} />
          <Route path="/privacy" element={<Privacy />} />
          <Route path="/terms" element={<Terms />} />
          <Route path="/legal/third-party" element={<Notices />} />
          <Route path="/security" element={<Security />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
      <Footer />
    </>
  )
}
