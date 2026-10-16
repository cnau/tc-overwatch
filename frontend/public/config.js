// Dev default: empty apiBaseUrl means relative URLs for the Vite proxy. The prod image
// overwrites this at start (frontend/docker-entrypoint.d/40-generate-config-js.sh).
window.__APP_CONFIG__ = {
  apiBaseUrl: '',
};
