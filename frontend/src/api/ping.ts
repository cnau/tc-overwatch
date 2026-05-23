// API wrapper for the Ping endpoint. Keep this pattern: one file per backend
// domain (src/api/contacts.ts, src/api/transactions.ts, …) exporting the typed
// request/response shapes, a typed fetch function, and TanStack Query hook(s).

import { useMutation } from '@tanstack/react-query'

export type PingRequest = {
  message: string
}

export type PingResponse = {
  echo: string
  serverReceivedAt: string
  id: string
}

async function sendPing(req: PingRequest): Promise<PingResponse> {
  const res = await fetch('/api/ping', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(req),
  })
  if (!res.ok) {
    const detail = await res.text().catch(() => '')
    throw new Error(`Ping failed: ${res.status} ${res.statusText}${detail ? ` — ${detail}` : ''}`)
  }
  return (await res.json()) as PingResponse
}

export function useSendPing() {
  return useMutation({ mutationFn: sendPing })
}
