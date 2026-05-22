// Placeholder shell. The dashboard and transaction-details pages described in
// docs/architecture.md § User interface land in subsequent PRs alongside the
// Connect-ES client wiring.
export function App() {
  return (
    <main style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem' }}>
      <h1>tc-overwatch</h1>
      <p>Scaffold is live. Backend gRPC at <code>localhost:9090</code> (proxied via <code>/rpc</code>), HTTP at <code>localhost:8080</code>.</p>
    </main>
  )
}
