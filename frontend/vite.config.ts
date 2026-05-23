import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Vite dev-server config.
//
// `/api/*` (JSON HTTP) and `/oauth/*` (OAuth callback) forward to the Spring Boot
// backend on :8080. Same-origin in the browser during dev — no CORS needed locally.
// In production the frontend talks to the backend by its public hostname; CORS rules
// + a shared parent domain handle cross-origin cookies (see docs/architecture.md § CORS).
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
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/oauth': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
