# tc-overwatch — PERSONAL project

This repo is **Christian's personal monorepo**, not work. Multiple Claude sessions may be open across personal and work projects at the same time — this file is the signal that you're in the personal context.

## Project goals

Read `GOALS.md` for what this project is, who it's for, the current focus (email triage / sort / prioritize), auth model, storage tiering, and explicit non-goals. The full task map and status flags live in `docs/task-inventory.md`. Project-specific vocabulary (TC, transaction key, primary vs. cooperating agent, triage labels, hot/cold storage, etc.) is defined in `docs/glossary.md` — consult it before introducing alternative terms in code or docs. The implementation stack (Spring Boot 4 + Kotlin, Postgres 18, gRPC + Connect, React, GKE + Helm) is documented in `docs/architecture.md`. Keep work aligned with that scope.

## Context flag

At the start of each response in this repo, lead with a short tag like `[personal:tc-overwatch]` so Christian can tell at a glance which session he's looking at.

## Identity & remotes

- GitHub account: `cnau` (personal), email `christian.nau@gmail.com`
- SSH remote URLs must use the `github.com-personal` host alias, e.g. `git@github.com-personal:cnau/...`. Never rewrite a remote here to bare `github.com:` — that routes through the work key.
- Commits are SSH-signed with `~/.ssh/id_github_personal` (the same key used for push auth). After uploading that key as a *signing* key on GitHub, commits show as Verified.

## If a prompt sounds work-related

If Christian asks you to do something in this repo that sounds like work (references to HaulerHero, work tickets, work services, internal tooling, the `christiannau` GitHub account, the `haulerhero.com` email, etc.), **stop and confirm** before acting — he may have typed into the wrong session. A quick "this looks work-related but we're in your personal repo — confirm?" is the right move.

## Where to look

- `GOALS.md` — strategy, v0 boundary, current focus, non-goals.
- `docs/task-inventory.md` — full task map with `[FOCUS]` / `[REVIEW]` / `[BACKLOG]` flags.
- `docs/glossary.md` — project vocabulary. Use these terms; don't invent alternates.
- `docs/architecture.md` — stack, layering, security zones, deployment, multi-tenancy/RLS.
- `docs/claude/` — shared coding conventions, imported by module-level `CLAUDE.md` files.
- Module-level `CLAUDE.md` (in `proto/`, `server/`, `frontend/`) — module overview, commands, module-specific tech.

## Guiding principles

### Keep it simple
Write the minimum code necessary. Don't add abstractions, helpers, or utilities unless they're needed in more than one place *right now*. Three similar lines is better than a premature abstraction. If a solution feels complex, step back.

### Change only what's asked
Don't refactor surrounding code while working on a task. Don't add comments, documentation, or type annotations to code you didn't change. A bug fix is just a bug fix. A new feature is just a new feature.

### Greenfield discipline
There is no legacy code yet — everything you write becomes the baseline. Be deliberate. A shortcut shipped today is a pattern other code will copy.

### Honor pinned architecture
`docs/architecture.md` captures decisions that came out of explicit trade-off discussions (layered DTOs, MapStruct at boundaries, RLS day one, three Postgres roles, proto-first API, separate frontend deploy, no retroactive Gmail reorganization). Don't quietly diverge — surface the case for changing a pinned decision instead of silently doing it differently.

## Working agreements

- **Verify before claiming done.** Tests passing or type checks clean is not the same as "the feature works." For backend changes, run them; for UI changes, exercise the path in the browser. If you can't verify, say so explicitly.
- **No destructive shortcuts.** Don't bypass safety checks (`--no-verify`, `--force`, `reset --hard`, dropping a lock file) to make an obstacle disappear. Diagnose the root cause. When in doubt, ask before acting.
- **Investigate unexpected state.** Unfamiliar files, branches, or local changes may be work in progress. Don't delete or overwrite without asking.
- **Confirm before shared-state actions.** Pushing, opening PRs, posting to GitHub, sending messages, uploading to external services — pause and confirm scope, unless the user has already authorized that scope in this conversation.
- **Add new files to git.** After creating a file, stage it. An unstaged new file is invisible to anyone looking at the diff.

## Monorepo rules

### Always
- **Version catalog** (`gradle/libs.versions.toml`) is the source of truth for all backend dependencies. Reference via `libs.*` from build files.
- **Kotlin DSL** for all Gradle build files (`*.gradle.kts`). No Groovy build scripts.
- **Liquibase Groovy DSL** for all migrations (`*.groovy` under `server/src/main/resources/db/changelog/`). See `docs/claude/liquibase.md`.
- **Run from the root**: `./gradlew build`, `./gradlew :server:test`, `npm --prefix frontend run build` — paths anchored at the repo root.
- **Proto-first**: API contract changes start in `proto/`, then regen, then code. Backend Kotlin stubs come from the Gradle protobuf plugin; frontend TS clients come from Buf (`buf generate`).
- Run the relevant build before committing — `./gradlew build` for backend changes, `npm --prefix frontend run build` for frontend changes.

### Never
- Hardcode versions in build files — they go in the catalog.
- Add `mavenLocal()` to any build file.
- Add a per-module `.gitignore`, `.github/`, or Gradle wrapper — the root covers these.
- Introduce per-environment config files — use Spring profiles (`local`, `unraid`, `prod`) + env vars + secrets, per `docs/architecture.md`.
- Bundle the frontend into the backend container — they deploy separately.
- Skip RLS or bypass tenant isolation in application code paths. See `docs/architecture.md` § Multi-tenancy and `docs/claude/spring-boot.md`.
