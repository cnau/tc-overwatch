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
- **ktlint owns formatting, detekt owns structure.** Where they overlap the detekt rule is
  disabled in `detekt.yml`, so line length has exactly one owner. Every override in that file
  states its reason — add yours, or fix the code. Don't add a detekt baseline file; 0 findings
  is the current state and a baseline would hide the next one.
- **detekt runs pinned to Kotlin 2.0.21 and `jvmTarget = "21"`** (`build.gradle.kts`) because
  1.23.8 can't run under this project's Kotlin 2.2 or JDK 23. Both workarounds come out
  together when detekt 2.0.0 ships — see #112.
- **There are no tests yet** (`server/src/test` is empty). The dependencies and conventions are
  in place; the first test author sets the precedent, so follow the `backend-feature` skill's
  testing section rather than improvising.
- `build/` is gitignored and holds generated sources. Never import from it.
