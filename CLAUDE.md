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
