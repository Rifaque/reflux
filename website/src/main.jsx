import { StrictMode } from 'react'
import { hydrateRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import './styles.css'

// Motion is the script's to start (src/motion.jsx): without it, every page is shown as it is.
document.documentElement.classList.add('motion')

// Every page is prerendered (scripts/prerender.mjs); the script takes over the page that is already there.
hydrateRoot(
  document.getElementById('root'),
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
