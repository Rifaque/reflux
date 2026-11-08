// Reflux's motion on the web (docs/DESIGN.md, "Motion language"): the mark drawing itself, reveals, and the product's
// depth. Browser features only (CSS animation, IntersectionObserver, the View Transition API through React Router);
// everything here gives way to prefers-reduced-motion, where the CSS shows the finished state at once.
import { useEffect, useRef } from 'react'
import { useLocation } from 'react-router-dom'
import release from './generated/release.json'

/**
 * The mark drawing itself: the first pass flows in and turns, the return passes over it and flows on, and the line
 * settles into the solid mark (flow → gather → transform → settle). [play] starts it: on load, or when in view.
 */
export function MarkMotion({ className = 'mark-motion', label }) {
  const { mark, stroke } = release.brand
  return (
    <svg className={className} viewBox={mark.viewBox} role={label ? 'img' : undefined} aria-label={label} aria-hidden={label ? undefined : 'true'} focusable="false">
      <path className="mm-fill" fill="currentColor" fillRule="evenodd" d={mark.d} />
      <path className="mm-first" pathLength="1" fill="none" stroke="currentColor" strokeWidth={stroke.width} strokeLinecap="round" d={stroke.first} />
      <path className="mm-return" pathLength="1" fill="none" stroke="currentColor" strokeWidth={stroke.width} strokeLinecap="round" d={stroke.return} />
    </svg>
  )
}

/**
 * Marks every [data-reveal] element of the page as it comes into view (class `in`), once. The CSS hides them only
 * while the script runs (html.motion), so a page without script, or with reduced motion, shows everything at once.
 */
export function useReveal() {
  const { pathname } = useLocation()
  useEffect(() => {
    const items = [...document.querySelectorAll('[data-reveal]:not(.in)')]
    if (!('IntersectionObserver' in window)) { items.forEach((el) => el.classList.add('in')); return }
    const observer = new IntersectionObserver((entries) => {
      for (const entry of entries) if (entry.isIntersecting) { entry.target.classList.add('in'); observer.unobserve(entry.target) }
    }, { rootMargin: '0px 0px -12% 0px', threshold: 0.12 })
    items.forEach((el) => observer.observe(el))
    return () => observer.disconnect()
  }, [pathname])
}

/**
 * A product surface that answers a fine pointer with a little depth (at most 3 degrees), set as CSS variables through
 * the CSSOM, which the content security policy allows. Nothing on touch, nothing with reduced motion.
 */
export function useDepth() {
  const ref = useRef(null)
  useEffect(() => {
    const el = ref.current
    if (!el || !window.matchMedia('(pointer: fine) and (prefers-reduced-motion: no-preference)').matches) return
    let frame = 0
    const move = (e) => {
      const r = el.getBoundingClientRect()
      const x = (e.clientX - r.left) / r.width - 0.5
      const y = (e.clientY - r.top) / r.height - 0.5
      cancelAnimationFrame(frame)
      frame = requestAnimationFrame(() => {
        el.style.setProperty('--ry', `${(x * 3).toFixed(2)}deg`)
        el.style.setProperty('--rx', `${(-y * 2.4).toFixed(2)}deg`)
      })
    }
    const leave = () => { el.style.setProperty('--ry', '0deg'); el.style.setProperty('--rx', '0deg') }
    el.addEventListener('pointermove', move)
    el.addEventListener('pointerleave', leave)
    return () => { el.removeEventListener('pointermove', move); el.removeEventListener('pointerleave', leave); cancelAnimationFrame(frame) }
  }, [])
  return ref
}
