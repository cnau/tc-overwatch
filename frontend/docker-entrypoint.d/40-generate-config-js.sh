#!/bin/sh
# Writes /config.js from APP_API_BASE_URL at container start. The SPA loads it
# as a classic <script> before the bundle, so it's set on first render. Empty
# means relative URLs — only right when proxied behind the backend's hostname.
set -eu

# Escape backslashes and double-quotes so any value can ride inside a double-
# quoted JS string literal without breaking syntax.
escaped=$(printf '%s' "${APP_API_BASE_URL:-}" | sed 's/\\/\\\\/g; s/"/\\"/g')

cat > /usr/share/nginx/html/config.js <<EOF
window.__APP_CONFIG__ = {
  apiBaseUrl: "${escaped}",
};
EOF
