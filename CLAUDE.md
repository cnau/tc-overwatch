# tc-overwatch — PERSONAL project

This repo is **Christian's personal monorepo**, not work. Multiple Claude sessions may be open across personal and work projects at the same time — this file is the signal that you're in the personal context.

## Session tag

Lead each response with `[personal:tc-overwatch]` so Christian can tell at a glance which session he's looking at.

## Where to look

- `GOALS.md` — strategy, v0 boundary, current focus, non-goals.
- `docs/architecture.md` — stack, layering, security zones, deployment, multi-tenancy/RLS. Decisions here are pinned via explicit trade-off discussions (layered DTOs, Kotlin extension-function mappers, RLS day one, three Postgres roles, HTTP/JSON API, separate frontend deploy, no retroactive Gmail reorganization). Surface the case for changing one — don't silently diverge.
- `docs/task-inventory.md` — full task map with `[FOCUS]` / `[REVIEW]` / `[BACKLOG]` flags.
- `docs/glossary.md` — project vocabulary (TC, transaction key, primary vs. cooperating agent, triage labels, hot/cold storage). Use these terms; don't invent alternates.
- `docs/claude/` — shared coding conventions, imported by module-level `CLAUDE.md` files.
- Module-level `CLAUDE.md` (`proto/`, `server/`, `frontend/`) — module overview, commands, module-specific tech.

## Greenfield

No legacy code — what you ship today becomes the baseline. A shortcut now is a pattern other code will copy.

## Identity & remotes

- GitHub account `cnau` (personal), email `christian.nau@gmail.com`.
- SSH remotes use the `github.com-personal` host alias (e.g. `git@github.com-personal:cnau/...`). Never rewrite to bare `github.com:` — routes through the work key.
- Commits are SSH-signed with `~/.ssh/id_github_personal`.

## If a prompt sounds work-related

References to HaulerHero, work tickets, work services, the `christiannau` GitHub account, or `haulerhero.com` email = stop and confirm. Christian may have typed into the wrong session. Quick "this looks work-related but we're in your personal repo — confirm?" is the right move.

## Monorepo rules

### Always
- **Version catalog** (`gradle/libs.versions.toml`) is the source of truth for all backend dependencies. Reference via `libs.*` from build files.
- **Kotlin DSL** for all Gradle build files (`*.gradle.kts`). No Groovy build scripts.
- **Liquibase Groovy DSL** for all migrations (`*.groovy` under `server/src/main/resources/db/changelog/`). See `docs/claude/liquibase.md`.
- **Run from the root**: `./gradlew build`, `./gradlew :server:test`, `npm --prefix frontend run build` — paths anchored at the repo root.
- **HTTP/JSON API**: backend exposes Spring MVC `@RestController`s. Frontend types in `frontend/src/api/<domain>.ts` mirror the backend DTO shapes by review, not codegen — keep them aligned when touching either side.
- Run the relevant build before committing — `./gradlew build` for backend changes, `npm --prefix frontend run build` for frontend changes.

### Never
- Hardcode versions in build files — they go in the catalog.
- Add `mavenLocal()` to any build file.
- Add a per-module `.gitignore`, `.github/`, or Gradle wrapper — the root covers these.
- Introduce per-environment config files — use Spring profiles (`local`, `unraid`, `prod`) + env vars + secrets, per `docs/architecture.md`.
- Bundle the frontend into the backend container — they deploy separately.
- Skip RLS or bypass tenant isolation in application code paths. See `docs/architecture.md` § Multi-tenancy and `docs/claude/spring-boot.md`.
