import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Vite dev-server config.
//
// spring-grpc serves gRPC (and gRPC-Web / Connect for browsers) over the Spring MVC
// servlet on :8080 — single port, same handlers, both wire formats. So `/rpc/*`
// forwards to :8080 and gets rewritten to drop the `/rpc` prefix; the backend sees
// the canonical gRPC service path (e.g. `/com.tcoverwatch.v1.PingService/Ping`).
//
// In production the frontend talks to the backend by its public hostname and CORS
// handles cross-origin cookies (see docs/architecture.md § CORS).
//
// When Connect-Query codegen lands, the transport is configured with
// `createConnectTransport({ baseUrl: '/rpc', credentials: 'include' })`.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/rpc': {
        target: 'http://localhost:8080',
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
