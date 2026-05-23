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
├── deploy/helm/tc-overwatch-server/  # Helm chart for GKE
├── scripts/db-init/                # Local Postgres init (roles + extensions)
├── docker-compose.local.yml        # Local Postgres for development
└── .github/workflows/ci.yml        # Parallel build pipelines (server + frontend)
```

## Local development

Requires **JDK 21+** (Spring Boot 4 minimum; the current scaffold pins to JDK 23 because it was the locally-installed version on the dev machine — see `docs/architecture.md` § Scaffold notes for the foojay auto-download caveat), **Node 22+**, and **Docker**.

```bash
# 1. Start local Postgres + role setup
docker compose -f docker-compose.local.yml up -d

# 2. Run the server (HTTP/JSON on :8080)
./gradlew :server:bootRun --args='--spring.profiles.active=local'

# 3. In a separate terminal: run the frontend dev server
cd frontend && npm install && npm run dev
# → http://localhost:5173
```

The Vite dev server proxies `/api/*` and `/oauth/*` to `localhost:8080` so the browser sees same-origin during development — no CORS needed locally.

## Status

The current state is a scaffold: layered Spring Boot + Kotlin app with one smoke-test feature (`/api/ping`) exercising the full controller → service → DAO → repository → Postgres path. Real features land in subsequent branches.
