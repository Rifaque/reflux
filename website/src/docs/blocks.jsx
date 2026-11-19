// The documentation's building blocks: commands with a copy button, notes, and the tabs a platform's variants are
// chosen with (Linux distributions). No library: the site's content security policy allows only its own scripts, and
// these need nothing more than React.
import { Children, isValidElement, useEffect, useId, useState } from 'react'

/**
 * A command or a file to type, with a button that copies it. The text is selectable as it is, so it works without
 * JavaScript and with a screen reader; the button only appears once the page can copy.
 */
export function Code({ children, label = 'Copy' }) {
  const text = String(children).replace(/\n$/, '')
  const [state, setState] = useState('idle')
  const [ready, setReady] = useState(false)
  useEffect(() => setReady(typeof navigator !== 'undefined' && !!navigator.clipboard), [])
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text)
      setState('copied')
    } catch {
      setState('failed')
    }
    setTimeout(() => setState('idle'), 2000)
  }
  return (
    <div className="code">
      <pre><code>{text}</code></pre>
      {ready && (
        <button type="button" className="code-copy" onClick={copy} aria-label={state === 'copied' ? 'Copied' : `${label}: ${text.split('\n')[0]}`}>
          {state === 'copied' ? 'Copied' : state === 'failed' ? 'Select and copy' : label}
        </button>
      )}
      <span className="visually-hidden" aria-live="polite">{state === 'copied' ? 'Copied to the clipboard' : ''}</span>
    </div>
  )
}

/** A note beside the text: `tip`, `note`, or `warning`. */
export function Note({ kind = 'note', title, children }) {
  const label = title ?? { tip: 'Tip', note: 'Note', warning: 'Before you go on' }[kind]
  return (
    <aside className={`callout callout-${kind}`}>
      <p className="callout-title">{label}</p>
      {children}
    </aside>
  )
}

/**
 * Tabs for choosing one variant of the same instructions (one per Linux distribution). Each tab has a stable id, so
 * `/docs/install/linux#fedora` opens on Fedora; without JavaScript the first is shown and the others are listed below
 * it. Arrow keys move between tabs, as the ARIA tab pattern asks.
 */
export function Tabs({ tabs, label }) {
  const base = useId()
  const [current, setCurrent] = useState(tabs[0].id)
  useEffect(() => {
    const pick = () => {
      const wanted = window.location.hash.slice(1)
      if (tabs.some((t) => t.id === wanted)) setCurrent(wanted)
    }
    pick()
    window.addEventListener('hashchange', pick)
    return () => window.removeEventListener('hashchange', pick)
  }, [tabs])
  const choose = (id) => {
    setCurrent(id)
    if (typeof history !== 'undefined') history.replaceState(null, '', `#${id}`)
  }
  const onKey = (event, index) => {
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0
    if (!step) return
    event.preventDefault()
    const next = tabs[(index + step + tabs.length) % tabs.length]
    choose(next.id)
    document.getElementById(`${base}-tab-${next.id}`)?.focus()
  }
  return (
    <div className="tabs">
      <div role="tablist" aria-label={label} className="tab-list">
        {tabs.map((tab, index) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            id={`${base}-tab-${tab.id}`}
            aria-selected={current === tab.id}
            aria-controls={`${base}-panel-${tab.id}`}
            tabIndex={current === tab.id ? 0 : -1}
            onClick={() => choose(tab.id)}
            onKeyDown={(event) => onKey(event, index)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      {tabs.map((tab) => (
        <section
          key={tab.id}
          id={tab.id}
          role="tabpanel"
          aria-labelledby={`${base}-tab-${tab.id}`}
          hidden={current !== tab.id}
          className="tab-panel"
        >
          {tab.content}
        </section>
      ))}
    </div>
  )
}

/** The words of a page's content, for search: every string in its elements, whatever the elements are. */
export function textOf(node) {
  if (node == null || typeof node === 'boolean') return ''
  if (typeof node === 'string' || typeof node === 'number') return String(node)
  if (Array.isArray(node)) return node.map(textOf).join(' ')
  if (isValidElement(node)) {
    const { children, tabs } = node.props
    return [textOf(children), tabs ? tabs.map((t) => `${t.label} ${textOf(t.content)}`).join(' ') : ''].join(' ')
  }
  return ''
}

/** A heading's anchor: lower case, words joined by hyphens. */
export const anchorOf = (text) => text.toLowerCase().replace(/[’']/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')

/** The section headings of a page's body, for its table of contents. */
export function headingsOf(body) {
  const found = []
  const walk = (node) => {
    if (!isValidElement(node)) return Children.forEach(node, (child) => isValidElement(child) && walk(child))
    if (node.type === 'h2') found.push(textOf(node.props.children).trim())
    else Children.forEach(node.props.children, walk)
  }
  walk(body)
  return found
}
