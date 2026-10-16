// Set by /config.js, which index.html loads before the bundle, so it's populated by the
// time this module runs.

type AppConfig = {
  apiBaseUrl: string
}

declare global {
  interface Window {
    __APP_CONFIG__?: AppConfig
  }
}

export const appConfig: AppConfig = {
  // Stripped so callers can write `${apiBaseUrl}/api/...` without doubling the slash.
  apiBaseUrl: (window.__APP_CONFIG__?.apiBaseUrl ?? '').replace(/\/$/, ''),
}
