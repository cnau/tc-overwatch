# tc-overwatch

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
├── proto/                          # Protobuf definitions + generated Kotlin/Java stubs
│   └── src/main/proto/
├── server/                         # Spring Boot 4 + Kotlin backend
│   ├── src/main/kotlin/com/tcoverwatch/
│   │   ├── Application.kt
│   │   └── feature/<name>/         # Feature-by-package layout
│   │       ├── api/                # gRPC controllers + proto ↔ DTO mappers
│   │       ├── service/            # Service classes + service DTOs
│   │       └── persistence/        # DAO + Entity + Repository + entity mapper
│   └── src/main/resources/
│       ├── application*.yml
│       └── db/changelog/           # Liquibase Groovy DSL migrations
├── frontend/                       # React + TypeScript + Vite (deploys separately)
├── deploy/helm/tc-overwatch-server/  # Helm chart for GKE
├── scripts/db-init/                # Local Postgres init (roles + extensions)
├── docker-compose.local.yml        # Local Postgres for development
├── buf.yaml + buf.gen.yaml         # Proto linting + TS codegen for frontend
└── .github/workflows/ci.yml        # Parallel build pipelines (server + frontend)
```

## Local development

Requires **JDK 21+** (Spring Boot 4 minimum; the current scaffold pins to JDK 23 because it was the locally-installed version on the dev machine — see `docs/architecture.md` § Scaffold notes for the foojay auto-download caveat), **Node 22+**, and **Docker**.

```bash
# 1. Start local Postgres + role setup
docker compose -f docker-compose.local.yml up -d

# 2. Run the server (gRPC on :9090, HTTP on :8080)
./gradlew :server:bootRun --args='--spring.profiles.active=local'

# 3. In a separate terminal: run the frontend dev server
cd frontend && npm install && npm run dev
# → http://localhost:5173
```

The Vite dev server proxies `/rpc/*` to `localhost:9090` (gRPC) and `/oauth/*` + `/api/*` to `localhost:8080` (HTTP), so the browser sees same-origin during development — no CORS needed locally.

## Status

The current state is a scaffold: layered Spring Boot + Kotlin app with one smoke-test feature (`PingService`) exercising the full proto → controller → service → DAO → repository → Postgres path. Real features land in subsequent branches.
