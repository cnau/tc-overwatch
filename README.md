# tc-overwatch

[![CI](https://github.com/cnau/tc-overwatch/actions/workflows/ci.yml/badge.svg)](https://github.com/cnau/tc-overwatch/actions/workflows/ci.yml)

Transaction Overwatch for Transaction Coordinators.

Automates the repetitive, time-consuming tasks that fill a real-estate transaction coordinator's day. Current focus: Gmail triage + sort + prioritize. See `GOALS.md` for full scope.

## Documentation

- **`GOALS.md`** — strategy, principles, current focus, non-goals.
- **`docs/task-inventory.md`** — full task map with status flags (`[FOCUS]` / `[REVIEW]` / `[BACKLOG]` / etc.).
- **`docs/glossary.md`** — project-specific vocabulary.
- **`docs/architecture.md`** — implementation stack, security zones, deployment paths, CI/CD.

## Repo layout

```
.
├── server/                         # Spring Boot 4 + Kotlin backend
│   ├── src/main/kotlin/com/tcoverwatch/
│   │   ├── Application.kt
│   │   └── feature/<name>/         # Feature-by-package layout
│   │       ├── api/                # @RestController + request/response DTOs + api mappers
│   │       ├── service/            # Service classes + service DTOs
│   │       └── persistence/        # DAO + Entity + Repository + entity mapper
│   └── src/main/resources/
│       ├── application*.yml
│       └── db/changelog/           # Liquibase Groovy DSL migrations
├── frontend/                       # React + TypeScript + Vite (deploys separately)
├── deploy/helm/tc-overwatch-server/  # Helm chart for GKE (future prod target)
├── scripts/db-init/                # Local Postgres init (roles + extensions)
├── scripts/db-init-unraid/         # Unraid Postgres init (env-driven passwords)
├── docker-compose.local.yml        # Local Postgres for development
├── docker-compose.unraid.yml       # Unraid pilot deploy stack
└── .github/workflows/ci.yml        # Build pipelines + GHCR image publish
```

## Local development

Requires **JDK 21+** (Spring Boot 4 minimum; the Gradle toolchain pins to JDK 23, auto-provisioned via Foojay if not already installed), **Node 22+**, and **Docker**.

```bash
# 1. Start local Postgres + role setup
docker compose -f docker-compose.local.yml up -d

# 2. Run the server (HTTP/JSON on :8080)
./gradlew :server:bootRun --args='--spring.profiles.active=local'

# 3. In a separate terminal: run the frontend dev server
cd frontend && npm install && npm run dev
# → http://localhost:5173
```

The Vite dev server proxies `/api/*`, `/oauth2/*`, and `/login/oauth2/*` to `localhost:8080` so the browser sees same-origin during development — no CORS needed locally.

## Status

Pre-pilot. The auth + multi-tenancy foundation is live: Google OAuth sign-in via Spring Security `oauth2Login()`, invitation-only signup gate, bearer JWT paradigm, RLS-enforced multi-tenancy backed by a three-role Postgres setup. CI builds the backend image and pushes it to GHCR (`ghcr.io/cnau/tc-overwatch-server`) on every main commit. Deployment to the Unraid pilot stack is in progress — Postgres landed; backend, migrate, frontend, and Cloudflare Tunnel services come next. Real product features (email triage, transaction lifecycle, dashboard) land on top of this baseline — see `docs/task-inventory.md`.

## License

[Business Source License 1.1](./LICENSE). Non-production use is permitted today. On the Change Date (2030-05-24) the Licensed Work converts to the Apache License, Version 2.0. For commercial / production-use licensing, see the contact link in [LICENSE](./LICENSE).
