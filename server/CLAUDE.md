# CLAUDE.md — server

@../docs/claude/spring-boot.md
@../docs/claude/kotlin.md
@../docs/claude/liquibase.md
@../docs/claude/proto.md

## Module overview

`:server` is the Spring Boot 4 + Kotlin backend. It owns:

- The gRPC + Connect API surface (controllers in `feature/<name>/api/`).
- The small HTTP surface for OAuth callback, auth endpoints, and actuator health (per `docs/architecture.md` § HTTP surface).
- Business logic in feature-scoped services (`feature/<name>/service/`).
- JPA persistence with Liquibase-managed schema (`feature/<name>/persistence/`, `src/main/resources/db/changelog/`).
- Background jobs via the Postgres-backed work queue (per `docs/architecture.md` § Background jobs — none implemented yet).

## Commands

```
./gradlew :server:build
./gradlew :server:test
./gradlew :server:test --tests "com.tcoverwatch.feature.ping.PingServiceTest"
./gradlew :server:bootRun --args='--spring.profiles.active=local'
./gradlew :server:ktlintCheck :server:detekt
./gradlew :server:ktlintFormat                       # auto-fix style
./gradlew :server:bootBuildImage                     # build OCI image
```

Local dev assumes Postgres is up via `docker compose -f docker-compose.local.yml up -d postgres`.

## Ports

- **8080** — Spring MVC servlet. Serves both **gRPC-Web** (the main API, at `/com.tcoverwatch.<feature>.v<N>.<Service>/<Method>`) and the small HTTP surface (OAuth callback, `/api/auth/*`, `/actuator/health`). Single port, single process — spring-grpc-server-web bridges gRPC handlers to the servlet via `grpc-servlet-jakarta`.

The Vite dev server proxies `/rpc/*` → backend `/` and `/oauth/*` + `/api/*` → `/`, so the browser sees same-origin during local dev. `grpcurl -plaintext localhost:8080 ...` also works against the same endpoint via h2c.

## Spring profiles

- `local` — laptop dev. Postgres in Docker, OAuth client tied to `localhost:5173`, secrets in `application-local.yml` (gitignored) or `.env.local`.
- `unraid` — homelab pilot. `.env` file outside the repo, Cloudflare-fronted hostnames.
- `prod` — GKE production. Secret Manager + Workload Identity.

Profile selection drives DB connection details, secret source, and OAuth client ID. Never branch on profile inside business logic — branch on configuration values.

## Module-specific notes

- **Three Postgres roles** (`tco_app`, `tco_admin`, `tco_migrate`) are provisioned for local dev in `scripts/db-init/01-roles.sql`. Application code uses `tco_app` by default; cross-tenant operations require an explicit `withAdminConnection { ... }` block on the `tco_admin` pool. Migrations run as `tco_migrate`. See `docs/claude/liquibase.md` and `docs/architecture.md` § Multi-tenancy.
- **`ping` is a scaffold smoke test**, not a pattern to extend. The first real feature replaces it and becomes the reference shape for everything that follows.
- **Mappers are Kotlin extension functions, not MapStruct.** No `kapt`, no annotation-processor pipeline, no generated mapper beans. See `docs/architecture.md` § Kotlin extension-function mappers at boundaries and `docs/claude/spring-boot.md` § Mappers.
- **Kotlin `internal` visibility on persistence classes**: per scaffold notes in `docs/architecture.md`, the public `PingDao` can't consume `internal` entity / repository types as constructor parameters, so those types stay public and the "entities don't leak from DAOs" rule is enforced by code review. Extension-function mappers themselves can stay `internal` (they're not Spring beans).
- **`build/` directory is gitignored** and includes Gradle-generated sources. Don't import from there directly — the `:proto` module's generated stubs are accessible via the project dependency.
