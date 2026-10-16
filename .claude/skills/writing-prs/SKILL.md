---
name: writing-prs
description: Use when writing or revising a pull request description for this repo — running `gh pr create`, editing a PR body, or when asked for a PR summary or to "write up this change". Covers the shape (headless TL;DR, short body, 250-word ceiling), what stays out, and the voice.
---

# PR descriptions

## Who you are writing for

A reviewer about to read the diff, with maybe ninety seconds of patience before they start
reviewing or close the tab. In this repo that is also a later session reading `git log` and the PR
to learn why something is the way it is.

The description is not a record of the work and not a substitute for the diff. Its job is the
context the diff can't carry: why the change exists, and what deserves attention. Anything the diff
already says clearly is not yours to repeat.

## Where the content comes from

Write from `git diff main...HEAD` and the commit log — not from session context.

During the work you accumulated paths you tried and abandoned, things that looked broken but weren't,
decisions you weren't sure about. All of it feels important and none of it is legible to the
reviewer. Re-derive the change from the diff and you describe what shipped instead of narrating how
you got there.

## Shape

```
<TL;DR — one or two sentences, no heading>

<Body — 3-6 sentences of prose, or 3-6 bullets.>

Closes #<n>   (when it closes an issue)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

Under 250 words, footer excluded. No heading above the TL;DR. Extra headings only when the PR has
genuinely separable parts (a migration plus the code that uses it, a PR closing several issues), and
then at most two. The attribution footer goes last on every PR body, verbatim.

**TL;DR**: what changed and why, in the first sentence. A reader who stops there should know whether
they care and roughly what they're in for. Lead with the change, not the background. Where there's a
concrete failure, name it exactly — the error string, the observable wrong behavior.

**Body**: what a reviewer needs that isn't visible in the diff. Usually a subset of: the mechanism of
the fix, a non-obvious constraint that shaped the approach, an alternative rejected and why, an area
that deserves scrutiny, a follow-up deliberately left out (with its issue number). Pick the two or
three that apply. Don't pad to fill the shape.

## Do not include

Unless asked, or the PR genuinely can't be reviewed without it:

- **"How it worked before"** as its own paragraph. The diff shows the before; state the old behavior
  only in the clause that makes the fix make sense.
- **A walkthrough of changed files.** GitHub lists them.
- **"What looks wrong but isn't"** / "Potential concerns." If something in the diff is genuinely
  confusing, fix it at that line — clearer code, or a comment within the root `CLAUDE.md` comment
  rules. A deliberate choice a later session might undo belongs where that session will look: the
  code, `docs/architecture.md`, or the relevant skill.
- **"Open questions."** Ask in the conversation, or as a review comment on the line.
- **A Testing section or checklist** saying tests were added and pass — CI reports that. Mention
  testing only when it's the interesting part: a case deliberately not covered, a gap you couldn't
  close, or a check that can only run after merge (an Unraid deploy, a runner restart). Then say
  exactly what to run and what to observe, in prose.
- **Self-assessment.** No "clean, minimal change," no risk ratings, no confidence statements.
- **Checklists, emoji, per-bullet bold labels** — the footer is the one exception.

## Voice

- Past tense, declarative. "Moved the retry into the client" — not "This PR moves…".
- Name things exactly, in backticks: paths, symbols, env vars, config keys, error strings, issue
  numbers.
- One clause per idea. A sentence that needs a subordinate clause to explain its main clause had a
  vague main clause.
- No hedging ("should now", "hopefully"). If something is uncertain, say what.
- Don't restate the TL;DR at the end.
- Never claim a suite ran, a case is covered, or a path was verified unless it happened. Say plainly
  what you skipped or couldn't check.

## Example

The change: a background job retried on every exception, validation errors included, so malformed
payloads retried five times and delayed the queue behind them.

**Too long — the default failure mode:**

> ## Summary
>
> This PR fixes an issue with the notification worker's retry behavior.
>
> ### How it worked before
>
> Previously, the `NotificationWorker` class caught all exceptions and re-raised them, which caused
> the default retry mechanism to kick in… [400 more words, then Testing, then What Looks Wrong But
> Isn't, then Open Questions]

**Right:**

> Validation failures in `NotificationWorker` no longer retry — they're logged and dropped.
> Malformed payloads were burning all 5 retries and adding ~4 min of backoff to the queue behind them
> (#212).
>
> `ValidationError` is caught before the generic handler; everything else re-raises and retries as
> before. Dropped payloads are logged at WARN with the full body, since there's no dead-letter queue
> yet and losing them silently would make the next one unreproducible.
>
> The catch is on `ValidationError` specifically rather than a broader non-retryable base class —
> worth a look if other error types here belong in the same bucket.
>
> 🤖 Generated with [Claude Code](https://claude.com/claude-code)

Every sentence in the good version carries a fact the diff doesn't: the issue, the concrete cost, the
deliberate logging decision and its reason, one genuine ask. It's shorter *because* it's denser.

## Length check

Count before returning. Over 250 words means something is explaining rather than informing — find
the paragraph that restates the diff and cut it.
