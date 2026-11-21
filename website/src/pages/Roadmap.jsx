import { Link } from 'react-router-dom'
import { useCallback, useEffect, useRef, useState } from 'react'
import { PageHead } from '../components.jsx'
import { MarkMotion } from '../motion.jsx'
import { STAGES, STATUS } from '../roadmap.js'
import { beta2Out } from '../released.js'

const CURRENT = STAGES.findIndex((s) => s.status === 'current')
const reduced = () => typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

/**
 * The line beside the stages fills as the page scrolls past them (a CSS variable set through the CSSOM, which the
 * content security policy allows). Without script, or with reduced motion, it rests at the current stage.
 */
function useProgress() {
  const ref = useRef(null)
  useEffect(() => {
    const el = ref.current
    if (!el || reduced()) return
    let frame = 0
    const update = () => {
      cancelAnimationFrame(frame)
      frame = requestAnimationFrame(() => {
        const r = el.getBoundingClientRect()
        const seen = Math.min(1, Math.max(0, (window.innerHeight * 0.6 - r.top) / r.height))
        el.style.setProperty('--seen', seen.toFixed(4))
      })
    }
    el.classList.add('live')
    update()
    window.addEventListener('scroll', update, { passive: true })
    window.addEventListener('resize', update)
    return () => { window.removeEventListener('scroll', update); window.removeEventListener('resize', update); cancelAnimationFrame(frame) }
  }, [])
  return ref
}

function Stage({ stage, index }) {
  const status = STATUS[stage.status]
  return (
    <li id={stage.id} className={`stage stage-${stage.status}`} data-reveal>
      <span className="stage-node" aria-hidden="true" />
      <div className="stage-card">
        <p className="stage-meta">
          <span className="stage-version">{stage.version}</span>
          <span className={`chip chip-${stage.status}`}>{status.label}</span>
          {index === CURRENT && <span className="you-are-here">You are here</span>}
        </p>
        <h2>{stage.theme}</h2>
        <p className="stage-summary">{beta2Out && stage.summaryOut ? stage.summaryOut : stage.summary}</p>
        <ul className="stage-items">{stage.items.map((item) => <li key={item}>{item}</li>)}</ul>
      </div>
    </li>
  )
}

/**
 * The easter egg: the road ahead as a Reflux player. The versions are chapters on a timeline; moving along it shows each
 * one's "scene preview", as Reflux's own Trickplay does, and arrows or the right-stick keys (←/→) step between them, Enter
 * or Space plays through. It is a native dialog: Escape closes it, focus stays inside, and it is fully usable by keyboard.
 */
function Reel({ open, onClose }) {
  const dialog = useRef(null)
  const [at, setAt] = useState(CURRENT)
  const [playing, setPlaying] = useState(false)
  const [hover, setHover] = useState(null)
  const last = STAGES.length - 1

  useEffect(() => {
    const d = dialog.current
    if (!d) return
    if (open && !d.open) { setAt(CURRENT); setPlaying(false); d.showModal() }
    if (!open && d.open) d.close()
  }, [open])

  useEffect(() => {
    if (!playing) return undefined
    if (at >= last) { setPlaying(false); return undefined }
    const t = setTimeout(() => setAt((i) => Math.min(last, i + 1)), reduced() ? 2600 : 2200)
    return () => clearTimeout(t)
  }, [playing, at, last])

  const key = useCallback((e) => {
    if (e.key === 'ArrowRight') { e.preventDefault(); setPlaying(false); setAt((i) => Math.min(last, i + 1)) }
    else if (e.key === 'ArrowLeft') { e.preventDefault(); setPlaying(false); setAt((i) => Math.max(0, i - 1)) }
    else if (e.key === 'Home') { e.preventDefault(); setAt(0) }
    else if (e.key === 'End') { e.preventDefault(); setAt(last) }
  }, [last])

  const shown = hover ?? at
  const stage = STAGES[shown]
  return (
    <dialog ref={dialog} className="reel" aria-labelledby="reel-title" onClose={onClose} onCancel={onClose}>
      <div className="reel-top">
        <p id="reel-title" className="reel-title">Reflux <span className="quiet">· the road ahead</span></p>
        <button type="button" className="reel-close" onClick={onClose} aria-label="Close">×</button>
      </div>
      <div className={`reel-frame reel-${STAGES[at].status}`} aria-live="polite">
        <p className="reel-chapter">Chapter {at + 1} of {STAGES.length}</p>
        <p className="reel-version">{STAGES[at].version}</p>
        <h2 className="reel-theme">{STAGES[at].theme}</h2>
        <p className="reel-status">{STATUS[STAGES[at].status].label}{at === CURRENT ? ' · you are here' : ''}</p>
        <ul className="reel-items">{STAGES[at].items.map((i) => <li key={i}>{i}</li>)}</ul>
      </div>
      <div className="reel-controls">
        <button type="button" className="reel-play" onClick={() => { if (at >= last) setAt(0); setPlaying((p) => !p) }} aria-label={playing ? 'Pause' : 'Play through the versions'}>
          <span aria-hidden="true">{playing ? '❚❚' : '▶'}</span>
        </button>
        <div className="reel-track-wrap">
          {hover !== null && (
            <div className="reel-preview" data-pos={hover} aria-hidden="true">
              <span className="reel-preview-version">{stage.version}</span>
              <span className="reel-preview-theme">{stage.theme}</span>
            </div>
          )}
          <div
            className="reel-track"
            role="slider"
            tabIndex={0}
            aria-label="Version"
            aria-valuemin={1}
            aria-valuemax={STAGES.length}
            aria-valuenow={at + 1}
            aria-valuetext={`${STAGES[at].version}: ${STAGES[at].theme}, ${STATUS[STAGES[at].status].label}`}
            onKeyDown={key}
            onPointerLeave={() => setHover(null)}
          >
            <span className="reel-fill" data-at={at} />
            {STAGES.map((s, i) => (
              <button
                key={s.id}
                type="button"
                tabIndex={-1}
                className={`reel-mark reel-mark-${s.status}${i === at ? ' is-at' : ''}`}
                data-pos={i}
                aria-hidden="true"
                onPointerEnter={() => setHover(i)}
                onFocus={() => setHover(i)}
                onClick={() => { setPlaying(false); setAt(i); setHover(null) }}
              ><span className="sr-only">{s.version}</span></button>
            ))}
          </div>
        </div>
        <span className="reel-time" aria-hidden="true">{STAGES[at].version} / {STAGES[last].version}</span>
      </div>
      <p className="reel-hints quiet small" aria-hidden="true"><kbd>←</kbd> <kbd>→</kbd> scenes · <kbd>▶</kbd> play · <kbd>Esc</kbd> close</p>
      <p className="sr-only">Use the left and right arrow keys on the timeline to move between versions.</p>
    </dialog>
  )
}

export default function Roadmap() {
  const progress = useProgress()
  const [ready, setReady] = useState(false)
  const [reel, setReel] = useState(false)
  useEffect(() => setReady(true), [])
  return (
    <article className="page roadmap-page">
      <PageHead eyebrow="Roadmap" title="Where Reflux is going.">
        <p>What Reflux already does, what is being finished now, and what comes after 1.0. Later versions are plans, and the furthest ones are directions we are exploring: none of it is a promise of a date.</p>
      </PageHead>

      <ul className="legend" aria-label="What the labels mean">
        {Object.entries(STATUS).map(([k, s]) => <li key={k}><span className={`chip chip-${k}`}>{s.label}</span> <span className="quiet small">{s.note}</span></li>)}
      </ul>

      <ol className="stages" ref={progress} data-current={CURRENT}>
        {STAGES.map((stage, i) => <Stage key={stage.id} stage={stage} index={i} />)}
      </ol>

      <section className="roadmap-close">
        <p className="lead">The full list of what each release changed is on <Link to="/releases">Releases</Link>. What the current beta brings, and how to join it: <Link to="/testing">the beta</Link>. Ideas and requests: <Link to="/support">Support</Link>.</p>
        {/* The easter egg: the mark that ends the page plays the road ahead. Shown only once the script runs. */}
        <button type="button" className="egg" hidden={!ready} onClick={() => setReel(true)} aria-label="Play the road ahead">
          <MarkMotion className="mark-motion egg-mark" />
          <span className="egg-hint" aria-hidden="true">▶</span>
        </button>
      </section>
      {ready && <Reel open={reel} onClose={() => setReel(false)} />}
    </article>
  )
}
