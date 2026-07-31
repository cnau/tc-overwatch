# tc-overwatch — PERSONAL project

Lead each response with `[personal:tc-overwatch]`.

Identity, remotes, `gh` account switching, and the work-vs-personal confirm rule are defined
once in `~/projects/CLAUDE.md`. Read it before any `gh` or `git push` operation.

## Where to look

| Need | File |
|---|---|
| Strategy, v0 boundary, non-goals | `GOALS.md` |
| Pinned architecture decisions + trade-off records | `docs/architecture.md` |
| Task map with `[FOCUS]` / `[REVIEW]` / `[BACKLOG]` | `docs/task-inventory.md` |
| Project vocabulary — use these terms, don't invent alternates | `docs/glossary.md` |

Architecture decisions are pinned via explicit trade-off discussions. Surface the case for
changing one — don't silently diverge.

## Greenfield

No legacy code. What you ship today becomes the baseline other code copies, so a shortcut
now is a pattern, not an isolated compromise.

## Non-negotiables

- **A controller never touches a Hibernate entity** — not as a parameter, return type, local,
  or import. Service public signatures are DTO-only. Binary check: entity in a controller
  signature or import = block before merge.
- **Never bypass tenant isolation.** RLS filters by `app.tenant_id`; never hand-write
  `WHERE tenant_id = ?`, never reach for the admin pool to dodge a policy.
- **Never modify a Liquibase changeset that has been committed.** `id` + `author` is the
  checksum key. New change → new changeset.
- **The version catalog (`gradle/libs.versions.toml`) and Maven Central are the only sources
  of backend dependencies.** No versions in build files, no `mavenLocal()`.
- **The repo root owns cross-cutting config.** No per-module `.gitignore`, `.github/`, or
  Gradle wrapper.
- **Environments are Spring profiles (`local`, `unraid`, `prod`) + env vars + secrets** — never
  a per-environment config file. Secrets are never committed.
- **The backend never bundles the frontend.** They build and deploy as separate images.

## Conventions

- **Kotlin DSL** for all Gradle build files. **Liquibase Groovy DSL** for migrations
  (`.sql` only for functions/views/triggers).
- **Run commands from the repo root**: `./gradlew :server:build`,
  `npm --prefix frontend run build`.
- Run the relevant build before committing — `./gradlew build` for backend,
  `npm --prefix frontend run build` for frontend.
- The backend's OpenAPI spec is the FE/BE type contract. Frontend types are **generated**
  into `frontend/src/gen/api.d.ts`, committed, and CI fails on drift. Never hand-write a type
  that has a backend counterpart.

## Repo etiquette

- Branch from `main` as `feat/<slug>` or `chore/<slug>`. Issue numbers go in the PR body, not
  the branch name.
- Commit subjects are sentence case, no `feat:` prefix, optionally scoped
  (`Unraid: trust X-Forwarded-Proto…`). The body explains the failure mode and why this fix —
  see `git log` for the standard.
- Work is tracked in GitHub Issues + Projects. For issue and PR prose, use the
  `writing-issues-and-prs` skill.

## When to add a rules file

Module-level `CLAUDE.md` carries only what causes a **wrong action** if missed. Guidance that
merely improves output belongs in a skill under `.claude/skills/<name>/SKILL.md`, loaded on
demand. Anything under ~10 lines belongs inline in the module `CLAUDE.md` — a skill's
frontmatter would cost as much as its content. Don't reintroduce a shared always-imported
conventions doc; that's the pattern this layout replaced.
