#!/usr/bin/env bash
# Post-deploy smoke test. Called by the deploy-unraid-* CI jobs; safe to run
# by hand from anywhere that can reach the public hostnames.
#
#   ./scripts/smoke.sh          # every check
#   ./scripts/smoke.sh api      # backend only  (deploy-unraid-server)
#   ./scripts/smoke.sh app      # frontend only (deploy-unraid-frontend)
#
# Scoped on purpose: a backend deploy must not fail because the frontend is
# down, and vice versa — the two pipelines are independent by design (see
# docs/architecture.md § Unraid deploy).
#
# These hit the *public* Cloudflare hostnames rather than the containers on the
# docker network, so a green run also proves the tunnel and its hostname
# routing survived the deploy — the failure mode a container-local curl misses.
#
# Base URLs resolve from, in order:
#   1. SMOKE_API_BASE_URL / SMOKE_APP_BASE_URL
#   2. APP_API_BASE_URL / APP_FRONTEND_BASE_URL in $TCO_ENV_FILE
#      (default /mnt/user/tco/.env — the same file compose reads)
# The env file is grepped for those two keys, never sourced: it also holds DB
# passwords, the JWT secret, and the tunnel token, and sourcing it would put
# all of them into the CI job's environment.

set -euo pipefail

TARGET="${1:-all}"
ENV_FILE="${TCO_ENV_FILE:-/mnt/user/tco/.env}"

case "$TARGET" in
    api | app | all) ;;
    *)
        echo "usage: smoke.sh [api|app|all]" >&2
        exit 2
        ;;
esac

read_env() {
    local key="$1" value
    [[ -r "$ENV_FILE" ]] || return 0
    # Last assignment wins, matching how compose reads the file.
    value=$(grep -E "^${key}=" "$ENV_FILE" | tail -n1 | cut -d= -f2-)
    value=${value%$'\r'}
    value=${value%\"} && value=${value#\"}
    value=${value%\'} && value=${value#\'}
    printf '%s' "$value"
}

require_url() {
    local name="$1" value="$2"
    if [[ -z "$value" ]]; then
        echo "::error::${name} is unset — pass SMOKE_${name%_BASE_URL}_BASE_URL or set it in ${ENV_FILE}" >&2
        exit 2
    fi
    # Strip a trailing slash so path concatenation below never doubles up.
    echo "${value%/}"
}

failures=0

# Fetch $2 and assert the status code is $3 and the body matches $4 (a grep -F
# pattern; pass '' to skip the body assertion). Prints a one-line verdict per
# check so a failing deploy log says which assertion broke without a re-run.
check() {
    local label="$1" url="$2" want_status="$3" want_body="${4:-}"
    local body status

    body=$(mktemp)
    # On a transport failure (DNS, TLS, timeout) curl writes its diagnostic to
    # stderr, prints 000 as the code, and exits non-zero. Swallow the exit
    # status so every check still runs and reports — one failing hostname
    # shouldn't hide the state of the rest.
    status=$(curl -sS --max-time 15 -o "$body" -w '%{http_code}' "$url" || true)
    status=${status:-000}

    if [[ "$status" != "$want_status" ]]; then
        echo "FAIL  ${label}: expected HTTP ${want_status}, got ${status} (${url})"
        head -c 500 "$body"
        echo
        rm -f "$body"
        failures=$((failures + 1))
        return
    fi

    if [[ -n "$want_body" ]] && ! grep -qF "$want_body" "$body"; then
        echo "FAIL  ${label}: body did not contain '${want_body}' (${url})"
        head -c 500 "$body"
        echo
        rm -f "$body"
        failures=$((failures + 1))
        return
    fi

    rm -f "$body"
    echo "ok    ${label}"
}

if [[ "$TARGET" == "api" || "$TARGET" == "all" ]]; then
    api_base=$(require_url API_BASE_URL "${SMOKE_API_BASE_URL:-$(read_env APP_API_BASE_URL)}")
    echo "--- api @ ${api_base}"

    # Actuator returns 200 only when every component is UP (503 otherwise), so
    # the status code alone is the real assertion; the body check guards
    # against a proxy that 200s an error page.
    check "actuator health" "${api_base}/actuator/health" 200 '"status":"UP"'

    # An unauthenticated /api/auth/me proves the security filter chain is wired
    # and the @RestControllerAdvice envelope is intact. A 200 here would mean
    # auth is bypassed — the failure worth catching before a user finds it.
    check "unauthenticated /api/auth/me" "${api_base}/api/auth/me" 401 '"code":"UNAUTHENTICATED"'
fi

if [[ "$TARGET" == "app" || "$TARGET" == "all" ]]; then
    app_base=$(require_url APP_BASE_URL "${SMOKE_APP_BASE_URL:-$(read_env APP_FRONTEND_BASE_URL)}")
    api_base_for_config=$(read_env APP_API_BASE_URL)
    echo "--- app @ ${app_base}"

    check "index.html" "${app_base}/" 200 '<title>tc-overwatch</title>'

    # /config.js is generated at container start from APP_API_BASE_URL. If the
    # var went missing the SPA still serves a perfectly valid shell and then
    # fails every API call in the browser — nginx returning 200 hides it, so
    # assert the resolved backend URL is actually in there.
    if [[ -n "${api_base_for_config}" ]]; then
        check "config.js carries the API base URL" "${app_base}/config.js" 200 "${api_base_for_config%/}"
    else
        check "config.js" "${app_base}/config.js" 200 '__APP_CONFIG__'
    fi
fi

if ((failures > 0)); then
    echo "::error::smoke test failed (${failures} check(s))"
    exit 1
fi

echo "smoke test passed"
