---
name: writing-issues
description: Use when drafting, rewriting, or splitting a GitHub issue for this repo — filing a bug, turning a conversation into tracked work, breaking an epic into issues, or when asked to "file this" or "write this up as an issue". Covers the shape, falsifiable acceptance criteria, non-goals, and the sections that stop a later session from redoing the investigation or undoing a deliberate choice.
---

# GitHub issues

Work is tracked in GitHub Issues + a Projects board. `gh` must be on the `cnau` account first (see
`~/projects/CLAUDE.md`). Read a neighbour or two before writing — `gh issue view 83` and
`gh issue view 90` are the reference set for depth.

## Who reads this

A later session that picks the issue up with full repo access and no other context, and Christian
scanning the board deciding what's next. Both want the same thing: exactly what "done" means, what
must not be touched, and where to start looking — **without redoing the investigation**.

The failure mode is ambiguity, not length. Every sentence that reads two ways is a coin flip on a
wasted cycle.

## What stays out

Standing instructions live in `CLAUDE.md` and the skills. The issue carries only what's unique to this
task: no repo conventions, no "run the build first", no style rules. If you're addressing the
implementer in second person, you've drifted.

## Shape

Use only the sections that carry content — drop an empty heading entirely, never write "N/A".

```
Title: <declarative statement of the change, under ~70 chars>

<TL;DR — 2-3 plain sentences: what's wrong or missing, and why it matters.>

<Context — current state and why it's insufficient. Observed facts only.>

## Approach            (or ## Backend changes / ## Frontend changes / ## CI changes)
## Alternatives rejected
## Acceptance criteria
## Non-goals
## Pointers
## Open questions
```

### Title

A statement of the change, not a topic: `Dedupe Gmail message processing by message ID` — not `Gmail
issues`. Titles are read in lists and cross-references far more often than bodies.

### Context

What's true now and why it's a problem, concretely. Quote the convention or code being changed in
italics — #83's *"types matching the backend DTOs (kept aligned by review)"* is the model.

**Keep observation and proposal apart.** A diagnosis stated as fact gets implemented even when it's
wrong. Anything not yet confirmed is labeled a hypothesis and goes in Pointers.

### Approach

The how, in implementable detail, grouped by area when it spans several: exact dependency, config
key, npm script, gradle task, file to edit. Point at the real code rather than pasting large blocks —
a pasted snippet gets copied verbatim, stale parts included.

### Alternatives rejected

With reasons. #83 rejects "don't commit the generated file" in three bullets. **Pre-empt the
objection the title invites**: if the issue could read as reversing a settled decision, say why it
isn't — #83's paragraph on why this isn't a return to proto-first codegen is the model.

### Acceptance criteria

The definition of done, and what the PR is reviewed against. Each one **falsifiable**: you can point
at the running system and say met or not met without argument. "Error handling is improved" is a
wish; "a request failing validation returns 422 with the field in `errors[].field`" is a criterion.

- Prefer criteria that map to something executable — a test that will exist, a command that exits
  zero, a log line that appears. Write the command out literally.
- Include a **negative test** where one applies. #83: rename a field server-side, don't regenerate,
  push — CI must fail on the diff.
- Three to five is healthy. Past six, split the issue; one issue should fit one PR.

### Non-goals

Implementers are eager: they refactor adjacent code, rename for consistency, and build the feature
the title vaguely implies. Silence doesn't communicate a boundary, so state it. Include a non-goal
when the implementer would *plausibly* do the thing, not for everything conceivable.

### Pointers

The file or function most likely to change, an existing pattern to mirror, the exact error string,
a related issue, a migration or config that must move in lockstep, unconfirmed hypotheses.

### Open questions

Only questions where a different answer produces a different implementation — each with the options
and what the choice hinges on. "What should the flag be called?" is noise; "migrate existing rows or
expire them?" is a question. An issue with open questions isn't ready to pick up — say so.

## Sections when they have content

- **Deviations from the original plan, with reasoning.** Stops a later session "fixing" a deliberate
  choice. #83 specified `PingApiRequest`; shipped naming is `PingRequest`, and that became a pinned
  convention. Undocumented, it reads as a bug forever.
- **Follow-ups not done here, and why not**, with a pointer. #90 accepts localStorage tokens as
  XSS-exfiltrable, states the conditions that make it acceptable, and points at #91.
- **Origin, with search hints** — the commit, tag, epic, or symbol to grep for.
- **Effort estimate**, only when it argues for sequencing. #83's "tiny now, grows linearly with each
  endpoint we skip past it" is the argument for doing it now.

## Voice

- Present tense for current behavior, imperative for desired behavior.
- Exact names in backticks: paths, symbols, env vars, error strings, issue numbers.
- No hedging or intensifiers — "should probably" and "ideally" read as optional, and get treated
  that way.
- Prose for reasoning, bullets for enumerable facts.

## Before returning

Read the acceptance criteria and ask: could two competent implementers satisfy all of them and still
differ in a way that would matter in review? If yes, a constraint is missing — add it, or move the
disagreement into Open questions.
