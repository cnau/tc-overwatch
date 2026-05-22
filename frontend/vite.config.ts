import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Vite dev-server config.
//
// The `/rpc` proxy forwards Connect-ES (gRPC-Web) calls to the backend in
// development so the browser sees same-origin (no CORS in dev). In production
// the frontend talks to the backend by its public hostname and CORS handles
// it (see docs/architecture.md § CORS and cross-origin sessions).
//
// When Connect-ES codegen is wired up, configure the transport with
// `createConnectTransport({ baseUrl: '/rpc' })` so calls hit /rpc/... and
// route through this proxy in dev.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/rpc': {
        target: 'http://localhost:9090',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/rpc/, ''),
      },
      '/oauth': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
