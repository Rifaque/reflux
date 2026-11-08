import { Link } from 'react-router-dom'
import { Mark, PageHead } from '../components.jsx'

export default function NotFound() {
  return (
    <article className="page not-found">
      <Mark className="mark not-found-mark" />
      <PageHead title="Nothing here.">
        <p>There is no page at this address. <Link to="/">Back to Reflux’s home</Link>.</p>
      </PageHead>
    </article>
  )
}
