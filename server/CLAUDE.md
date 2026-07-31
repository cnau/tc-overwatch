# CLAUDE.md — server

`:server` is the Spring Boot 4 + Kotlin backend: HTTP/JSON API, business logic, JPA
persistence. Writing or changing a feature → use the `backend-feature` skill. Writing a
migration → use the `db-migrations` skill.

- **The schema is owned by the separate `migrate` Docker image** (`migrate/Dockerfile`), which
  bakes in the changelogs at build time. Liquibase is deliberately absent from this module's
  classpath, so there is no `bootRun`-time migration — run the one-shot container first:
  `docker compose -f docker-compose.local.yml run --rm migrate`.
- **Three Postgres roles** (`tco_app`, `tco_admin`, `tco_migrate`), provisioned locally by
  `scripts/db-init/01-roles.sql`. App code uses `tco_app`; cross-tenant work requires an
  explicit `withAdminConnection { … }` block on the `tco_admin` pool; migrations run as
  `tco_migrate`.
- **Never branch on the active profile inside business logic** — branch on a configuration
  value. Profiles (`local`, `unraid`, `prod`) select DB connection, secret source, and OAuth
  client ID only.
- **`feature/ping` is a scaffold smoke test, not a pattern to extend.** The first real feature
  replaces it and becomes the reference shape.
- **detekt is task-disabled** in `build.gradle.kts` — detekt 1.23 embeds Kotlin 2.0 and fails
  under 2.2. CI's `detekt` invocation is a silent no-op; ktlint is the only active check until
  detekt 2.0.0 ships. Don't trust detekt to catch anything, and don't "fix" the disable block
  without upgrading first.
- **There are no tests yet** (`server/src/test` is empty). The dependencies and conventions are
  in place; the first test author sets the precedent, so follow the `backend-feature` skill's
  testing section rather than improvising.
- `build/` is gitignored and holds generated sources. Never import from it.
