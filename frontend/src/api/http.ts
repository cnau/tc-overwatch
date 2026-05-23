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
  const res = await fetch(input, {
    ...init,
    credentials: 'include',
    headers,
  })

  if (!res.ok) {
    throw await parseError(res)
  }

  // 204 No Content — caller's `T` should be `void`, but the cast is on them.
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
      // fall through — non-JSON body (proxy error page, etc.)
    }
  }
  return new ApiError(
    'HTTP_ERROR',
    `Request failed: ${res.status} ${res.statusText}`.trim(),
    res.status,
  )
}
