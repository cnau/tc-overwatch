// Runtime config read once at module load. The actual values come from
// /public/config.js (dev) or the container-generated /config.js (prod).
// index.html loads that script before the React bundle, so window.__APP_CONFIG__
// is populated by the time this module runs.

type AppConfig = {
  apiBaseUrl: string
}

declare global {
  interface Window {
    __APP_CONFIG__?: AppConfig
  }
}

export const appConfig: AppConfig = {
  // Trailing slash stripped so callers can write `${apiBaseUrl}/api/...`
  // without worrying about doubling up.
  apiBaseUrl: (window.__APP_CONFIG__?.apiBaseUrl ?? '').replace(/\/$/, ''),
}
