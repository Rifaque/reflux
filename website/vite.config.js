import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// A static site (docs/WEBSITE.md). No source maps in production: nothing to hide, but nothing to ship either.
export default defineConfig({
  plugins: [react()],
  build: { sourcemap: false, outDir: 'dist' },
})
