#!/usr/bin/env bash
# Block until a container reports healthy, or fail loudly.
#
#   ./scripts/wait-for-healthy.sh tco-backend 120
#
# Takes a container name (not a compose service name) so it needs no compose
# context — no -f flag, no --env-file, no project-name guessing. The deploy
# jobs already know the container names because docker-compose.unraid.yml
# pins them.
#
# Exits non-zero on: unknown container, no healthcheck declared, `unhealthy`
# status, or timeout. A container that exits mid-wait fails immediately rather
# than burning the full timeout.

set -euo pipefail

CONTAINER="${1:?usage: wait-for-healthy.sh <container-name> [timeout-seconds]}"
TIMEOUT="${2:-120}"

if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
    echo "::error::no such container: ${CONTAINER}"
    exit 1
fi

# A container with no HEALTHCHECK reports an empty .State.Health — waiting on
# it would spin until timeout and then report a confusing "never became
# healthy". Say what's actually wrong instead.
if [[ -z "$(docker inspect --format '{{if .State.Health}}yes{{end}}' "$CONTAINER")" ]]; then
    echo "::error::${CONTAINER} declares no healthcheck — nothing to wait for"
    exit 1
fi

echo "waiting up to ${TIMEOUT}s for ${CONTAINER} to report healthy..."

for ((elapsed = 0; elapsed < TIMEOUT; elapsed += 2)); do
    state=$(docker inspect --format '{{.State.Status}}' "$CONTAINER")
    health=$(docker inspect --format '{{.State.Health.Status}}' "$CONTAINER")

    case "${state}/${health}" in
        running/healthy)
            echo "${CONTAINER} healthy after ${elapsed}s"
            exit 0
            ;;
        running/unhealthy)
            # Docker retries a failing healthcheck before flipping to
            # unhealthy, so this verdict is already the retried one.
            echo "::error::${CONTAINER} is unhealthy"
            docker inspect --format '{{range .State.Health.Log}}{{.Output}}{{end}}' "$CONTAINER" | tail -20
            exit 1
            ;;
        running/starting) ;;
        *)
            echo "::error::${CONTAINER} left the running state (status=${state}, health=${health})"
            exit 1
            ;;
    esac

    sleep 2
done

echo "::error::${CONTAINER} did not become healthy within ${TIMEOUT}s"
docker inspect --format '{{range .State.Health.Log}}{{.Output}}{{end}}' "$CONTAINER" | tail -20
exit 1
