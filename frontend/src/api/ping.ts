import { useMutation } from '@tanstack/react-query'

import { requestJson } from '@/api/http'
import type { components } from '@/gen/api'

export type PingRequest = components['schemas']['PingRequest']
export type PingResponse = components['schemas']['PingResponse']

async function sendPing(req: PingRequest): Promise<PingResponse> {
  return requestJson<PingResponse>('/api/ping', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

export function useSendPing() {
  return useMutation({ mutationFn: sendPing })
}
