---
name: writing-issues-and-prs
description: Use when drafting or revising a GitHub issue or pull request body for this repo — opening an issue, writing a PR description, or restructuring one that reads as a file-by-file changelog. Covers the house split (PR = why + branch overview, issue = what + how in implementable detail) and the sections that keep a later session from undoing a deliberate choice.
---

# Writing issues and PRs

Work is tracked in GitHub Issues + a Projects board. Read two or three neighbours before
writing — `gh issue view 83`, `gh issue view 90`, `gh pr view 109`, `gh pr view 104` are the
reference set. `gh` must be on the `cnau` account first (see `~/projects/CLAUDE.md`).

Universal to both: **exact names, always.** File paths, symbols, class names, env vars, npm
scripts, gradle tasks, error strings, config keys — all in backticks. Reference related work by
number (`#71`, `#102`). Prose over bullet-soup for reasoning; bullets for enumerable facts.

## Pull requests — the WHY and a whole-branch overview

Two sections: `## Summary`, then `## Test plan`. That's the house shape.

**Summary** opens with the problem in concrete terms — the actual error string where there is
one (`Fixes Error 400: redirect_uri_mismatch`), or the precise thing that was silently wrong
(`OAuthFailureHandler accepted the AuthenticationException argument but threw it away`). Then
what the change does and why *this* fix. Then, when relevant, the mechanism a reviewer would
otherwise have to derive: why `NATIVE` forward-headers works, which trusted IP ranges cover
cloudflared, which container resolves `localhost` to `::1`.

**Pre-empt what looks wrong but isn't.** This is the highest-value sentence in most of these
PRs and it's never omitted:

- *"The SPA's error code stays generic on purpose (don't leak token-exchange details to the
  browser)"*
- *"Unraid profile only — local dev hits the backend directly, no proxy in between"*
- *"Nothing `depends_on` the frontend yet, but cloudflared will once it lands"*

**Not in a PR body**: a commit-by-commit walk, a file inventory, or a restatement of the diff.
Describe the branch as one coherent change. A bullet list is fine when the items are
*decisions with reasons* (see #104) — not when they're a list of touched files.

**Test plan** is a checkbox list: `[x]` for what you actually ran, `[ ]` for what's pending.
Include the post-merge manual verification when the change only proves out on the pilot, and be
specific about the observable result:

```
- [x] `./gradlew :server:build :server:ktlintCheck` clean
- [ ] CI green
- [ ] After merge + `docker compose pull backend && up -d backend` on the pilot: re-trigger a
      failing OAuth sign-in → backend log shows the full `AuthenticationException` at WARN;
      the SPA still sees `?error=OAUTH_FAILED`.
```

Never check a box you didn't verify.

## Issues — the WHAT and HOW, in implementable detail

An issue is written so a future session can act on it **without redoing the investigation**. It
carries the detail; the PR that closes it carries the narrative.

Start with a plain-language **TL;DR** — two or three sentences a human can skim to decide
whether they care. The reference issues predate this and jump straight into detail; add the
TL;DR anyway.

Then the body. Open with current state and why it's insufficient, quoting the convention or code
being changed in italics. #83 is the model: *"types matching the backend DTOs (kept aligned by
review)"* — that works at one endpoint, doesn't scale to fifteen, and the cost of fixing
misalignment grows linearly while the cost of preventing it is bounded.

Group the work by area with exact names throughout — `## Backend changes`, `## Frontend
changes`, `## CI changes`. Name the dependency to add, the config key, the npm script, the file
to edit.

**State alternatives considered and rejected, with reasons.** #83 rejects "don't commit the
generated file" in three bullets: typechecking shouldn't need a running backend, frontend-only
PRs would need backend spin-up, CI diff beats "build broke after pull."

**Pre-empt the objection the title invites.** #83: *"This isn't a return to proto-first /
codegen-everything (the path we walked away from in `pivot-to-json`). That pivot was about a
wire-format problem… only the type-sync mechanism changes."* Without that paragraph the issue
reads like a reversal of a settled decision.

**Acceptance criteria are concrete and checkable, and include a negative test** where one
applies — #83: rename a field server-side, don't regenerate, push, and CI must fail on the diff.

## Sections to include when they have content

Drop the heading entirely when it would be empty. Never write "N/A" under one.

- **Deviations from the original plan, with reasoning.** This is the section that stops a later
  session from "fixing" a deliberate choice. Real case: #83 specified
  `components['schemas']['PingApiRequest']`, but the shipped naming is `PingRequest` /
  `PingResponse`, and *"don't rename to `*ApiRequest` / `*ApiResponse`"* became a pinned
  convention. Undocumented, that reads as a bug for the rest of the project's life.
- **Follow-ups not done here, and why not.** #90 accepts localStorage tokens as
  XSS-exfiltrable, states the conditions that make it acceptable (CSP, dependency vetting, no
  third-party JS, short lifetime), and points at the tracking issue — which is #91. A known
  trade-off with a pointer is engineering; the same trade-off unstated is a latent surprise.
- **Origin, with search hints.** Where this came from and how to find it again: the commit or
  tag (`pivot-to-json`), the epic, the file and symbol to grep for. Saves the next session the
  archaeology.
- **Open questions for the implementer.** Genuinely unresolved decisions, each with the options
  and what the choice hinges on — #83 leaves prod exposure of `/v3/api-docs` open with both
  options spelled out and says when to decide it.
- **Effort estimate**, when it's load-bearing for sequencing. #83's "~3–4 hours, tiny now while
  there's one endpoint, grows linearly with each endpoint we skip past it" is the argument for
  doing it now, not just a number.
