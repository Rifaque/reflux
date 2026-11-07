import { Link } from 'react-router-dom'

export default function NotFound() {
  return (
    <article className="page">
      <h1>Not here</h1>
      <p>There is no page at this address. <Link to="/">Reflux's home</Link>.</p>
    </article>
  )
}
