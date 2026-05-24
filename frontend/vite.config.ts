import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// `/api/*`, `/oauth2/*` (OAuth start), and `/login/oauth2/*` (OAuth callback) forward to
// Spring Boot on :8080. Same-origin during dev — no CORS needed locally. Prod talks to the
// backend by its public hostname (see docs/architecture.md § CORS).
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: true },
      '/login/oauth2': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
