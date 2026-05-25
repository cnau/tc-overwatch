#!/bin/sh
# Generate /config.js from APP_API_BASE_URL before nginx starts. nginx:alpine
# runs anything in /docker-entrypoint.d/ at container start. The SPA loads this
# file synchronously (classic <script>) before the React bundle, so the value
# is available to window.__APP_CONFIG__ on first render.
#
# Empty APP_API_BASE_URL → empty apiBaseUrl → SPA uses relative URLs. Useful
# only when the SPA is reverse-proxied behind the backend's hostname.
set -eu

# Escape backslashes and double-quotes so any value can ride inside a double-
# quoted JS string literal without breaking syntax.
escaped=$(printf '%s' "${APP_API_BASE_URL:-}" | sed 's/\\/\\\\/g; s/"/\\"/g')

cat > /usr/share/nginx/html/config.js <<EOF
window.__APP_CONFIG__ = {
  apiBaseUrl: "${escaped}",
};
EOF
