# Goals — tc-overwatch

## What this is

tc-overwatch automates the repetitive, time-consuming tasks that fill a **transaction coordinator's** day — the person a real estate agent hires to handle the paperwork side of real estate deals (contracts, disclosures, DocuSign, etc.).

The premise: a TC's work is full of patterns that follow the same shape on every transaction. Identify those patterns and automate them, one at a time.

## Who this is for

Transaction coordinators working with real estate agents.

The first user is a single pilot TC (used as a real-world validation channel). The eventual shape is **SaaS for multiple TCs** — data model is tenant-scoped from day one (every record keyed by a user/account ID), but no multi-tenant UI, billing, or admin work happens until the single-user experience is solid.

**Not personal assistants.** The roles sometimes blur in practice (errands, calendar coordination, generic admin), but tc-overwatch is scoped to TC work only.

## Approach

1. Identify a repetitive, time-consuming task that a TC does on most transactions.
2. Build the smallest useful automation for it.
3. Ship, validate with the pilot TC, move to the next task.

See `docs/task-inventory.md` for the full task map, with status flags showing what's the current focus, confirmed, pending TC review, or out of scope.

## Design principle — deadlines live in the contract

Inspection, financing, and closing deadlines are stipulated by the **executed contract**, not by email. Email mentions of dates are informational context only — the system never infers, schedules, or escalates based on a date that exists only in an email body.

A proposed deadline change (e.g. *"can we extend inspection by two days?"*) is `action-needed` because the TC must coordinate a contract amendment; the new deadline isn't real until the amendment is signed and the contract reflects it. Contract-driven deadline extraction is a future feature and reads the actual document, not email.

## Onboarding principle — no retroactive reorganization

When a new user grants Gmail access, the system **does not** retroactively reorganize their inbox. Existing read mail, manually filed mail, and the user's current label structure are left untouched. The system applies labels and priority signals only to **new incoming mail going forward**, using the user's established labels where they already exist.

The first-sync experience starts with a one-time consent screen that explicitly states what will and won't happen:

- **Will**: classify, label, and prioritize new incoming unread mail according to the system's rules. Read historical mail (in the background) to populate the dashboard with a comprehensive view of past business.
- **Won't**: touch existing read or already-filed mail, rename existing labels, or restructure the inbox in any way. No outbound email of any kind (v0 boundary).

**Read vs. write distinction**: the system freely *reads* historical mail to build its internal data model (contact directory, transaction records, participation graph, dashboard content). It never *writes* to historical mail. Reads are required to give the TC a real business view; writes are strictly forward-only.

**Async backfill**: after consent, live processing of new mail begins immediately while a background job reads historical mail to populate the data model. Onboarding feels instant; the dashboard fills in over minutes to hours as backfill completes.

This is a trust principle, not just a UX nicety. A TC's inbox is a workspace she has tuned to her own habits over years. Mass automated reorganization is a violation of that workspace and the fastest way to make her stop using the system. Onboarding should feel like adding a helpful assistant, not migrating to a new platform.

**Reconciliation with the full-form label default**: for transactions she's already been organizing in Gmail, her existing labels (whatever convention she's used — short, full, custom) are canonical and reused as-is. The full-form convention applies only to *new* transactions discovered going forward. Old labels are never auto-upgraded. Each existing address-shaped label becomes a discrete Transaction record in the data store with its own ID; new transactions — even at the same or similar address — always get new IDs.

## User interface (v0)

Two main pages:

- **Dashboard** (default landing page) — a comprehensive view of the TC's business across all active and recently-closed transactions. Surfaces: active transactions, new business + referral warning queue, review lists (auto-promoted contacts, unmatched email, label disambiguation), backfill progress.
- **Transaction details page** — per-transaction drill-down reached by clicking a transaction on the dashboard. Shows address, lifecycle/storage state, parties + roles, email thread history, recent activity log.

Editing surfaces (manual contact and transaction corrections) are reached from these pages. See `docs/task-inventory.md` section 9 for the full per-page item list.

## v0 boundary — read, label, prioritize. Never initiate.

**v0 will never send, reply, draft, or otherwise initiate email on the TC's behalf.** Every action it takes is confined to applying Gmail labels and Gmail's native priority signals (stars, importance markers). This is a trust-building principle as much as a scope-limiting one: the system can't accidentally embarrass the TC by sending something on her behalf because it can't send at all.

Practical consequences:

- Required Gmail scope is and remains `gmail.modify` (label + star + importance). `gmail.send` and `gmail.compose` are out of scope until a future version is explicitly designed around outbound.
- Backlog items like "auto-replies", "timeline replies", and "follow-ups for missing documents" are all post-v0 — they require the no-initiate boundary to be lifted, which is a deliberate decision that happens later.

## Current focus — email triage, sorting & prioritization

Every incoming email is run through a three-step pipeline:

1. **Classify the email's type.** Three top-level types short-circuit normal processing:
   - **New business** — inbound leads, referrals, capacity inquiries, "looking for a TC" emails. Typically from an **unfamiliar sender** (not in the TC's known-contacts directory). Most independent TCs are **business owners**, and new business is the single most important class to surface immediately. A lost lead is the worst-case outcome of this whole system. New business emails must never get buried under transaction operations email.
   - **Referral warning** — a heads-up from a **known referrer** ("I gave your name to a friend, she'll reach out") that precedes an actual new-business email from the unfamiliar lead. The sender is known; the *content* is the signal. We do **not** try to mechanically chain a referral warning to the new-business email that eventually arrives — they're flagged independently so the TC can mentally connect them.
   - **Junk / spam** — discarded.

   Anything else is **transaction email** and proceeds to step 2.

2. **Associate the email with a transaction.**
   - **Transaction email** → an *active* transaction identified by **property address**. The address may or may not appear in the subject line; it may only be in the body, an attachment, or implied by the thread. Address extraction and normalization (e.g. "123 Main St" vs "123 Main Street, Anytown CA 94000" → same transaction) is a core problem to solve.
   - **New business email** → attempt *lead attribution* to a past transaction by scanning for references to known agents (in body, signature, or thread context). If a known agent is found, surface their past deals as context for the TC. Soft-fail when no attribution can be made.

3. **Triage urgency and apply Gmail signals.**
   - **New business** → dedicated `New Business` label, starred, marked important. Highest visible priority.
   - **Referral warning** → dedicated `Referral Warning` label, starred, marked important. Same priority tier as new business.
   - **Transaction email (active deal)** → urgency label (`action-needed` / `awaiting-response` / `FYI` — see `docs/task-inventory.md` for crisp definitions, classifier bias rules, and open questions), importance markers and stars set accordingly, plus the per-transaction label (see below). Unmatched transaction email (no property address could be identified) gets an `Unmatched` label so the TC can review.
   - **Transaction email (closed deal)** → per-transaction label still applied (history and continuity), plus a `Closed Transaction` indicator label so the UI can display a "don't act" warning. **No priority signals** (no star, no important marker). The TC's professional scope ends at closing; she's not allowed to act on these, so we don't draw her attention to them.
   - **Junk** → trashed.

   Gmail "folders" are labels; every priority and sort operation is implemented through label operations and Gmail's native importance markers and stars.

### Per-transaction label rule

**The transaction label IS the property address.** One transaction = one label. Each transaction is independent regardless of which agents, clients, lenders, or brokerages are involved — the same agent appearing on five deals means five separate labels, not one grouped label. There is no nesting by agent, client, or any other party. The flat property-address-as-label scheme is intentional: it keeps each deal a self-contained unit and prevents accidental cross-transaction context bleed.

#### Label format

The transaction label is a *display* of the transaction key (always the fully normalized address with state + ZIP). Two display formats supported:

- **Full** — `123 Main St, Anytown CA 94000` (default). Unambiguous across cities. Required for the pilot TC, who works in multiple markets — short-form would collide across same-street-name addresses in different towns.
- **Short** — `123 Main St, Anytown` (opt-in for single-market TCs only). Shorter to read but **collides across cities** for street names like Main / Oak / 1st that recur in nearby towns. Not safe by default.

**Bootstrap behavior** (`[FOCUS]`): on first sync, the system reads the TC's existing Gmail labels and treats them as **authoritative** — no renames, no upgrades, no reorganization. Labels in any form (short, full, custom) are matched and reused for incoming mail belonging to existing transactions. Only *new* transactions (first appearance going forward) get labels in the new full-form convention. See "Onboarding principle" above.

#### Multi-transaction labels (same address)

A single property can be the subject of multiple transactions over time — re-sale to a new owner years later, a failed contract re-listed, or (rarely) simultaneous active deals on different units of the same property. The **internal transaction key** is therefore `normalized_address + contract_open_date`, not just the address — guaranteeing uniqueness across time.

The **display label** stays clean (no suffix) when there's only one transaction at an address. Collisions are resolved by:

- New active deal at an address with an existing *closed* deal → the closed deal's label is renamed in place to add `(closed YYYY)`. The active deal always gets the clean label.
- Multiple *active* deals at one address simultaneously → all involved labels get a `(YYYY)` suffix from the contract open date. The TC can override with a custom suffix via the standard manual-edit surface.

Operation is automatic and deterministic; the TC sees the result and can override manually if needed — same pattern as auto-promotion of known contacts.

## Authentication

Google OAuth2 with Gmail scopes. No custom auth, no other identity providers.

Minimum scopes for the current focus:

- `https://www.googleapis.com/auth/gmail.modify` — read messages, apply/remove labels, change importance markers, move between labels. Covers triage + sort + prioritize.

Not yet required:

- `gmail.send` — needed only when auto-reply / timeline-reply features land (backlog).

Note: broader Gmail scopes trigger stricter Google verification before public SaaS release, so the auth surface is kept minimal until each capability is actually used.

### Access control — invitation-only for MVP

Signup is **invitation-only** for the MVP. No random Google account can sign in and provision a tenant.

The access gate is implemented as a configurable **signup mode** so that opening up access later (free trial, paid plans, referral program) is a configuration change rather than a code change:

- `invitation` — v0 default. Only emails with a pending unaccepted `Invitation` row may sign in and provision a tenant. Existing accepted users sign in normally.
- `open` — anyone with a Google account can sign up. (Future, for free-tier launch.)
- `paid` — sign-up requires a paid subscription before tenant provisioning. (Future, for monetization.)

For the v0 pilot, invitations are created by direct DB insert or a minimal admin RPC (the developer is implicitly the only admin). A proper admin UI for invitation management is `[BACKLOG]` for when SaaS launches.

The invitation flow is separate from — and precedes — the first-sync consent screen:

1. User receives an invite link with a token (out of band; e.g. via email or messenger).
2. Lands on a marketing/welcome page explaining tc-overwatch and the invite.
3. Clicks "Sign in with Google" → Google OAuth flow.
4. On callback, system checks the authenticated email against the `Invitation` table. Match → invitation marked accepted, tenant + user account provisioned. No match → reject with a clear "this product is currently invitation-only" message.
5. New user is then shown the **first-sync consent screen** (described in the Onboarding principle), where they explicitly proceed with Gmail processing.

Signup mode is enforced server-side. Frontend never decides who's allowed in.

## Transaction lifecycle

A transaction has one of two lifecycle states:

- **Active** — the deal is in progress; the TC is currently working on it. Email triage prioritizes normally.
- **Closed** — the deal has closed escrow. The TC's professional scope of work ends here; she is not allowed to act on it further. Email still arrives (clients with questions, lenders with final docs) and still gets labeled by transaction for history, but **never gets prioritized**, and carries a "don't act" warning indicator.

How transactions transition to closed is `[REVIEW]` for now — manual marking by the TC is the simplest v0 mechanic; inferring from the contract's close date is a backlog refinement.

Lifecycle state (`active` / `closed`) is orthogonal to **storage state** (`hot` / `cold`): a transaction can be `closed + hot` (recently closed, files still in Dropbox) or `closed + cold` (closed > 6 months ago, files moved to NAS).

## Storage tiering

- **Hot storage — Dropbox.** Active transactions and anything < 6 months old.
- **Cold storage — NAS / network storage.** Anything > 6 months old gets moved automatically.

Per-task storage items live in the inventory.

## Backlog (next focus areas, after triage is solid)

- Generating timely replies that include a transaction timeline
- DocuSign / e-sign workflow assistance
- Deadline computation and pre-deadline nudges
- Status snapshots to the agent
- (See `docs/task-inventory.md` for the full list)

## Non-goals

- **Personal-assistant features** — calendar management, errands, generic scheduling, anything not tied to a real estate transaction.
- **Replacing the TC** — this augments their work, it doesn't substitute for them.
- **General-purpose email/CRM tooling** — every feature must be justified by a recurring TC task.
- **Custom auth** — Google OAuth only; no email/password, magic links, or other IdPs.
