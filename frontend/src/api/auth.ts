import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError, clearAuthToken, requestJson } from '@/api/http'
import type { components } from '@/gen/api'

export type MeResponse = components['schemas']['MeResponse']

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

async function logout(): Promise<void> {
  // The server call is a no-op for stateless bearer tokens, kept for future revocation.
  try {
    await requestJson<void>('/api/auth/logout', { method: 'POST' })
  } catch {
    // ignore — local clear is what matters
  }
  clearAuthToken()
}

export const useMe = () => useQuery({ queryKey: authKeys.me, queryFn: fetchMe })

export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: logout,
    onSuccess: () => qc.setQueryData(authKeys.me, null),
  })
}
