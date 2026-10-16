import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { setAuthToken } from '@/api/http'

const TOKEN_HASH_PREFIX = '#token='
const AUTH_ME_KEY = ['auth', 'me'] as const

// The token rides the fragment so server logs never see it. Token and ?error are both
// cleared via replaceState so neither sits in browser history.
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
