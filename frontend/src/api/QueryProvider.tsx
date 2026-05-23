import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

// TanStack Query is the server-state cache for the whole app. Backend calls go
// through plain fetch against /api/* (see vite.config.ts proxy in dev,
// architecture.md § Frontend in prod).
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000 },
  },
})

export function QueryProvider({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}
