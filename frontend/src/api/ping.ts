import { useMutation } from '@tanstack/react-query'

import { requestJson } from '@/api/http'

export type PingRequest = {
  message: string
}

export type PingResponse = {
  echo: string
  serverReceivedAt: string
  id: string
}

async function sendPing(req: PingRequest): Promise<PingResponse> {
  return requestJson<PingResponse>('/api/ping', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export function useSendPing() {
  return useMutation({ mutationFn: sendPing })
}
