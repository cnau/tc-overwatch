// Default runtime config (empty apiBaseUrl → SPA uses relative URLs, which
// the Vite dev proxy handles). The production Docker image overwrites this
// file at container start from $APP_API_BASE_URL — see
// frontend/docker-entrypoint.d/40-generate-config-js.sh.
window.__APP_CONFIG__ = {
  apiBaseUrl: '',
};
