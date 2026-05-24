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

// Bearer token persists across browser restarts (mirroring how iOS Keychain
// persists across app restarts — *lifecycle* parallel, not a security claim).
// XSS exfiltration risk is accepted; mitigations live at the SPA's CSP +
// dependency-vetting level, not at the token-storage level.
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
  const res = await fetch(input, {
    ...init,
    headers,
  })

  if (!res.ok) {
    throw await parseError(res)
  }

  if (res.status === 204) return undefined as T
  return (await res.json()) as T
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
