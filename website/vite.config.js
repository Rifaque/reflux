import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// A static site, prerendered (docs/WEBSITE.md). No source maps in production: nothing to hide, but nothing to ship
// either. Nothing is inlined as a data: URL, which the content security policy would refuse for fonts.
export default defineConfig({
  plugins: [react()],
  build: { sourcemap: false, outDir: 'dist', assetsInlineLimit: 0 },
})
