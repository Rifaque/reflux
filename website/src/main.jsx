import { StrictMode } from 'react'
import { hydrateRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import '@fontsource-variable/manrope/wght.css'
import App from './App.jsx'
import './styles.css'

// Every page is prerendered (scripts/prerender.mjs); the script takes over the page that is already there.
hydrateRoot(
  document.getElementById('root'),
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
