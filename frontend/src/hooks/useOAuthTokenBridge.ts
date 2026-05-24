import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { setAuthToken } from '@/api/http'

const TOKEN_HASH_PREFIX = '#token='
const AUTH_ME_KEY = ['auth', 'me'] as const

// Reads the post-OAuth handoff: token in URL fragment (so the server never sees
// it in logs), error code in query string. Both get cleared via replaceState so
// they don't sit in browser history. Returns the error code (if any) for the UI.
export function useOAuthTokenBridge(): string | null {
  const qc = useQueryClient()
  const [oauthError, setOAuthError] = useState<string | null>(null)

  useEffect(() => {
    const hash = window.location.hash
    if (hash.startsWith(TOKEN_HASH_PREFIX)) {
      const token = decodeURIComponent(hash.slice(TOKEN_HASH_PREFIX.length))
      setAuthToken(token)
      window.history.replaceState(null, '', window.location.pathname + window.location.search)
      qc.invalidateQueries({ queryKey: AUTH_ME_KEY })
      return
    }
    const params = new URLSearchParams(window.location.search)
    const errorCode = params.get('error')
    if (errorCode) {
      setOAuthError(errorCode)
      params.delete('error')
      const qs = params.toString()
      window.history.replaceState(null, '', window.location.pathname + (qs ? `?${qs}` : ''))
    }
  }, [qc])

  return oauthError
}
