---
name: committing
description: Pre-commit review of your own diff — the comment audit that turns "default to no comments" into checks you can apply, including files you added (which have no prior version to measure against and are the case that slips through), plus the scope and branch checks and the commit message shape. Load before every `git commit`, and before pushing a branch for review.
---

# Before you commit

Three passes over your own diff, then the message. The comment pass is the one that actually fails;
do it first.

## 1. The comment pass

The rule is in root `CLAUDE.md` § Comments. A principle on its own doesn't stop anything — "default
to no comments" reads as satisfied by whoever just wrote the comment. This pass makes it checkable.

Get the added lines:

```bash
# Before a commit — what you're committing is staged, so it is NOT in HEAD yet:
git diff --staged -U0 | grep '^+' | grep -v '^+++ '

# Before a push — the whole branch:
git diff -U0 "$(git merge-base origin/main HEAD)...HEAD" | grep '^+' | grep -v '^+++ '
```

Use the staged form at commit time; `<base>...HEAD` excludes the change you're about to make, so
auditing with it before a commit reviews nothing. The trailing space in `'^+++ '` matters: a file
header is `+++ <path>`, while an added line whose content starts with `++` arrives as `+++…`.

Count added comment lines (`//`, `/*`, `*`, `{/*`, and `--` in SQL, `#` in YAML, shell, Dockerfiles
and `nginx.conf`) against added code lines, and compare with the same ratio in the file **before
your change**. A ratio well above the file's own, with three or more added comment lines, is the
signal. The tests below decide.

**A file you added has no *before* — measure it against its directory.** Skipping this is how a new
file ends up the most heavily commented in its package. List what you added, then score it with its
neighbours:

```bash
git diff --staged --diff-filter=A --name-only   # before a push: "$(git merge-base origin/main HEAD)...HEAD"

awk 'FNR==1 { if (NR>1) emit(); c=0; k=0; name=FILENAME }
     { s=$0; sub(/^[ \t]+/,"",s); if (s=="") next
       if (s ~ /^(\/\/|\/\*|\*|\{\/\*|--|#)/) c++; else k++ }
     END { emit() }
     function emit() { if (c+k) printf "%3d%%  %3d cmt %4d code  %s\n", c*100/(c+k), c, k, name }' \
  <dir>/*.kt | sort -rn   # or *.ts / *.tsx / *.groovy / *.yml
```

**If the file you added tops its own directory, it's wrong.** No ratio argument rescues that. A new
file in a new directory has no neighbours either — then the ceilings in `CLAUDE.md` are the whole
budget.

### The three tests

Apply all three to every comment block you added. One failure means cut or compress.

**Signature test.** Cover the comment and read the declaration. If the name, parameter types and
return type already say it, the comment is noise.

**Neighbour test.** Count the comment lines on sibling declarations in the same file — that's the
budget, a ceiling not a target. Siblings carry none → you get at most one line, and only if it passes
the third test. In a file you added, the neighbours are the *other files in its directory*, never the
declarations you just wrote beside it; scoring your own new lines against each other is circular.

**Duplication test.** Is this *why* already written where the reader will be standing — at the
caller, on the type, in `docs/architecture.md`, a runbook, a skill? Then cut it here. Two copies rot
apart, and the second is the one nobody updates. Infra files are where this bites: a compose or CI
comment that re-explains a runbook section should shrink to the one line that matters plus a pointer.

### What earns its place

A constraint that's invisible in the code and expensive to rediscover, in one or two lines:

- *"Transaction-local (`true`), so the binding can't outlive the request on a pooled connection."*
- *"Same absolute path on both sides: compose bind mounts resolve on the host daemon, not in here."*

Neither restates a signature, and deleting either invites someone to "simplify" a bug back in.

### PR prose is not a comment

A sentence that explains **the change** — what was broken, when it regressed, which issue, who hit
it — belongs in the commit message, PR, or issue. A sentence that explains **the code as it now
stands** can stay. Issue and PR numbers stay out of code comments for the same reason.

### Two places the ratio lies

- **Signature-only additions.** Two new interface methods are ~2 lines of code, so one line of doc
  each reads as 50%. Judge on lines per declaration — one is fine, four is not.
- **Tests.** A test comment naming the defect it pins is high-value and will always look dense
  against a terse suite. Exempt, as long as it says *what breaks if this regresses* rather than
  narrating arrange/act/assert.

## 2. The scope pass

- Anything the task didn't ask for? Refactors of surrounding code, comments or annotations on lines
  you didn't otherwise change, a helper extracted "while I was in there". Split it out or drop it.
- A file touched only to revert it? Confirm `git diff` is empty for it.
- New files staged? `git status` should show no `??` you meant to include.
- Dead code from your own change: a DAO method whose only caller you deleted, an orphaned import, a
  field left behind. The compiler catches few of these.
- A backend DTO or endpoint change? Regenerate `frontend/src/gen/api.d.ts` in the same commit, or CI
  fails on drift.

## 3. The branch pass

`git branch --show-current` **immediately before** the commit and again before the push. The checkout
can move between turns, and nothing stops a direct push to `main`.

## Then the message

Subject: sentence case, no `feat:`-style prefix, optionally scoped (`Unraid runner: persist its
registration…`).

Body: the failure mode and why *this* fix — what a reader of `git log` needs that the subject and the
diff don't carry: the concrete symptom or error string, the constraint that forced the shape, an
alternative rejected. **Six lines is the ceiling.** More than that is PR material.

- Don't restate what the PR or the linked issue already carries.
- Never narrate the change in order ("first X, then Y, also Z") — that's the diff read back.
- A trivial change (typo, version bump, revert) gets no body.

The `Co-Authored-By` trailer goes last, copied exactly from this session's attribution guidance —
the model name varies, so copy rather than recall. Git reads trailers from the **final paragraph
only**: blank line above, nothing below. It doesn't count toward the six lines.
