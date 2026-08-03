import PingCard from '@/components/PingCard'

// feature/ping is the scaffold's end-to-end smoke test, not product surface.
// It lives on its own route so it stays reachable without sitting on the
// dashboard the TC actually uses.
export default function ScaffoldPage() {
  return <PingCard />
}
