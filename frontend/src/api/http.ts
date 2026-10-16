import { appConfig } from '@/config'

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly details?: Record<string, unknown>

  constructor(code: string, message: string, status: number, details?: Record<string, unknown>) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.details = details
  }
}

// Persists across browser restarts. XSS exfiltration is an accepted risk, mitigated by
// CSP and dependency vetting rather than at the storage level.
const TOKEN_KEY = 'tco_auth_token'

export const getAuthToken = (): string | null => localStorage.getItem(TOKEN_KEY)
export const setAuthToken = (token: string): void => localStorage.setItem(TOKEN_KEY, token)
export const clearAuthToken = (): void => localStorage.removeItem(TOKEN_KEY)

type ApiErrorBody = {
  code?: unknown
  message?: unknown
  details?: unknown
}

export async function requestJson<T>(input: RequestInfo | URL, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getAuthToken()
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const res = await fetch(withBaseUrl(input), {
    ...init,
    headers,
  })

  if (!res.ok) {
    throw await parseError(res)
  }

  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

// Only leading-slash strings get apiBaseUrl; empty (dev) leaves them relative for the
// Vite proxy.
function withBaseUrl(input: RequestInfo | URL): RequestInfo | URL {
  if (typeof input !== 'string') return input
  if (!input.startsWith('/')) return input
  if (!appConfig.apiBaseUrl) return input
  return `${appConfig.apiBaseUrl}${input}`
}

async function parseError(res: Response): Promise<ApiError> {
  const text = await res.text().catch(() => '')
  if (text) {
    try {
      const body = JSON.parse(text) as ApiErrorBody
      if (typeof body.code === 'string' && typeof body.message === 'string') {
        return new ApiError(
          body.code,
          body.message,
          res.status,
          body.details as Record<string, unknown> | undefined,
        )
      }
    } catch {
      // non-JSON body (proxy error page, etc.)
    }
  }
  return new ApiError(
    'HTTP_ERROR',
    `Request failed: ${res.status} ${res.statusText}`.trim(),
    res.status,
  )
}
