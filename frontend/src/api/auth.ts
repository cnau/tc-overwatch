import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError, requestJson } from '@/api/http'
import type { components } from '@/gen/api'

export type MeResponse = components['schemas']['MeResponse']
export type DevLoginRequest = components['schemas']['DevLoginRequest']

const authKeys = {
  me: ['auth', 'me'] as const,
}

// 401 → null data, not error. Signed-out is expected, not exceptional.
async function fetchMe(): Promise<MeResponse | null> {
  try {
    return await requestJson<MeResponse>('/api/auth/me')
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null
    throw e
  }
}

async function devLogin(req: DevLoginRequest): Promise<MeResponse> {
  return requestJson<MeResponse>('/api/auth/dev-login', {
    method: 'POST',
    body: JSON.stringify(req),
  })
}

async function logout(): Promise<void> {
  await requestJson<void>('/api/auth/logout', { method: 'POST' })
}

export const useMe = () => useQuery({ queryKey: authKeys.me, queryFn: fetchMe })

export function useDevLogin() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: devLogin,
    onSuccess: () => qc.invalidateQueries({ queryKey: authKeys.me }),
  })
}

export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: logout,
    onSuccess: () => qc.setQueryData(authKeys.me, null),
  })
}
