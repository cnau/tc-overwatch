# Glossary — tc-overwatch

Terms with project-specific meaning. Where a term has both an industry meaning and a tighter project meaning, the project meaning takes precedence here.

## People & roles

**TC** — Transaction coordinator. A person hired by a real estate agent to handle the paperwork side of a real estate transaction (contracts, disclosures, signatures, deadlines). Not a personal assistant; the roles sometimes blur in practice but are scoped differently.

**Pilot TC** — The first user of tc-overwatch; the TC against whom the v0 product is being validated.

**RE agent / Agent** — A real estate agent (buyer's or seller's representative).

**Primary agent** — Per-transaction role: the agent who hired the TC on that specific deal. The TC's direct client on this transaction.

**Cooperating agent** — Per-transaction role: the agent on the *other side* of a deal where the TC's primary agent represented the opposite party. Also called "other-side agent." A high-value class of contact because cooperating agents are common sources of future referrals.

**Lender / Title / Escrow** — Parties typically involved in a real estate transaction. Title and escrow are often shared *role-account* inboxes (e.g. `closings@firsttitle.com`).

**Vendor** — A commercial sender not party to a specific transaction (home warranty, MLS, insurance offers, real-estate tech pitches). Vendor email is `FYI` if it references a property address; `junk` otherwise.

**MLS** — Multiple Listing Service. Industry-standard real estate listing database; MLS announcements are typically vendor email.

## Transactions & lifecycle

**Transaction** — A real estate deal, in progress or already closed. Each transaction is independent and gets its own Gmail label.

**Transaction key** — Internal identifier for a transaction: `normalized_address + contract_open_date`. The address alone is insufficient because the same property can be the subject of multiple transactions over time (re-sale, failed contract re-listed, simultaneous active deals on multiple units). Address normalization always includes state and ZIP so variants like `123 Main St` and `123 Main Street, Anytown, CA 94000` resolve to the same property; the open date then distinguishes deals at that property.

**Active** — Lifecycle state: the deal is in progress; the TC is currently working on it. Email triage prioritizes normally.

**Closed** — Lifecycle state: the deal has closed escrow. The TC's professional scope of work ends here — she is not allowed to act on it further. Email is still labeled (history / continuity) but never prioritized; carries a `Closed Transaction` warning indicator.

**Lifecycle state** — `active` or `closed`. Orthogonal to *storage state*.

## Email classification

**New business** — Email class: inbound lead from an unfamiliar sender (capacity inquiry, "looking for a TC", referral inquiry). The highest-priority class in the triage system. Gets a `New Business` Gmail label, starred, marked important.

**Referral warning** — Email class: a heads-up from a *known referrer* ("I gave your name to a friend, she'll reach out") signaling an upcoming new-business email from someone else. Sender is known; *content* is the signal. Same priority tier as new business. Gets a `Referral Warning` label. Never mechanically chained to the subsequent new-business email — both are flagged independently.

**Transaction email** — Email class: anything that pertains to an existing transaction, active or closed. Routed by property address.

**Junk** — Email class: cold outreach that isn't new business; generic vendor email with no transaction reference. Trashed, no label applied. (Gmail's spam filter handles outright spam upstream.)

**Unmatched** — Fallback Gmail label applied to transaction email whose property address could not be identified. Not a triage label — a queue for manual TC review.

**Triage label** — One of three labels applied to active-transaction email after classification: `action-needed`, `awaiting-response`, or `FYI`.

- **`action-needed`** — Triage label: the TC herself must do something on this thread before it can move forward.
- **`awaiting-response`** — Triage label: the ball is in someone else's court; the TC has done her part and is waiting. Never starred or marked important.
- **`FYI`** — Triage label: informational; doesn't require action and isn't blocking the TC. Never starred or marked important.

**`Closed Transaction`** — Indicator label applied to all email about a closed transaction alongside the transaction's per-property label. Tells the UI to render a "don't act" warning.

**Priority signals** — Gmail's native importance markers (yellow vs. white arrows) and stars. The system uses these to express urgency *within* a triage category; we do not add additional labels for urgency.

**Classifier bias** — Asymmetric-cost-based default routing rules. When uncertain between two classes, route to whichever has the higher cost of a miss:

- `new-business` over transaction-email (a lost lead is the worst outcome of the system).
- `action-needed` over `FYI` (missing actionable email is worse than mildly mislabeling).
- `FYI` over `junk` (trashing something she needed is catastrophic).

## Data model

**Known-contacts directory** — The set of contacts the TC works with. Determines whether an inbound sender is "familiar" (→ likely transaction email or referral warning) or "unfamiliar" (→ likely new business).

**Contact** — Data model entity: one record per person the system knows about. Fields: `email` (nullable), `display_name`, `phone` (E.164), `last_seen_at`, `role`, `auto_promoted`, `referred_by_contact_id` (nullable), `notes`. A contact requires at least one of `email` or `phone` plus `display_name`. Lookup is by either identity field. Phone is the primary identifier in real estate, not email.

**Role** (on a contact) — Default classification: `agent`, `lender`, `title`, `escrow`, `client`, `vendor`, `unknown`. Starts `unknown` and is refined over time.

**`auto_promoted`** — Contact flag: `true` if the contact was added by the *bidirectional promotion* rule rather than by manual entry or initial import. Lets the TC review and downgrade.

**Bidirectional promotion** — The rule that a sender becomes "known" only when there's evidence of two-way communication: the TC has sent them mail, OR the TC has replied to one of their incoming messages. Inbound-only senders never auto-promote regardless of volume — this is the spam guard.

**TransactionParticipation** — Data model entity: links a contact to a specific transaction with a per-deal role. A contact can have many participation rows (same agent might be primary on one deal, cooperating on three others).

**`role_on_deal`** — Per-transaction-participation property. For agents: `primary` (TC was hired by them) or `cooperating` (other side). For non-agents: mirrors the contact's `role`.

**Lead attribution** — The process of associating a new-business email with a *past* transaction by scanning the email for references to known agents and looking up their `TransactionParticipation` history. Five signals in precision order: thread context, phone match, email match, domain match, name match. Soft-fails when no agent reference can be found.

**Lead capture** — Extracting contact info (phone, email, name) for a *new* lead from a referral warning or new-business email and creating or updating a Contact record so the lead is on file before — or as soon as — they engage. Phone is captured especially because real estate communication is phone-primary.

**Referral context** — Explicit, persisted relationship between a lead Contact and their referrer (also a Contact). Stored on the lead via `referred_by_contact_id`. Set only from unambiguous evidence: a referral warning (sender = referrer), an explicit "referred by [known name]" mention in a new-business email, or a thread-context match (forwarded by a known agent). Distinguished from **lead attribution**, which surfaces possible referrers as *hints* without persisting them as hard data.

**Signup mode** — Server-side setting that determines who is allowed to sign up and provision a tenant. v0 ships with `invitation` (only invited emails); future modes `open` (anyone with a Google account) and `paid` (subscription required) slot into the same auth gate without code changes.

**Invitation** — Data model entity authorizing a specific email address to sign up and create a tenant under `invitation` signup mode. Fields: `email`, `token`, timestamps, `accepted_at`, `tenant_id` (assigned at acceptance). One invitation = one new tenant for v0; SaaS may later add invite-to-existing-tenant flows.

**Auth gate** — The server-side check that runs after Google OAuth callback to decide whether the authenticated user is allowed to provision (or sign into) a tenant. Behavior depends on the current `signup_mode`.

## Storage

**Hot storage** — Dropbox. Holds files for active transactions and any closed deal whose closing date is less than 6 months ago.

**Cold storage** — NAS / network storage. Holds files from transactions whose closing date is more than 6 months ago.

**Storage state** — `hot` or `cold`. Orthogonal to *lifecycle state* — a transaction can be `closed + hot` (recently closed) or `closed + cold` (closed > 6 months ago).

## System scope

**v0** — The first shipping version of the system. Single-user (pilot TC); read + label + prioritize only; no outbound email of any kind.

**v0 boundary** — The "read, label, prioritize — never initiate" constraint. The system can never send, reply, draft, or otherwise originate outbound email in v0. Required Gmail OAuth scope is and remains `gmail.modify`. Any feature that would lift this boundary is `[BACKLOG]`.

**Initiate** (verb) — System action of sending, drafting, or replying to email. Explicitly out of scope in v0; gating capability for several `[BACKLOG]` features (auto-reply, follow-up nudges, status snapshots, timeline replies).

**Label format** — Per-account preference for how property-address labels render in Gmail: `full` (`123 Main St, Anytown CA 94000`, default — required for TCs working in multiple cities) or `short` (`123 Main St, Anytown`, opt-in for single-market TCs only, since it collides across same-street-name addresses in nearby towns). The internal transaction *key* is always the fully normalized form regardless of label format.

**Label disambiguation** — Rule for handling Gmail labels when multiple transactions share a property address. Active deals keep the clean address label; closed deals at the same address are renamed in place to add `(closed YYYY)` when a new active deal opens there; two or more simultaneous active deals at one address all get `(YYYY)` suffixes from their contract open dates (TC can override with a custom suffix). Automatic and deterministic.

**Address extraction** — Recovering a property address from email content (subject, body, thread context) and resolving it to one of the TC's active transactions. Implemented as a **layered pipeline** that exploits the closed-set nature of the problem (only ~10–30 active deals to choose from): subject-line parsing → body parsing → fuzzy match against active transactions → thread-context shortcut → sender-context narrowing → `Unmatched` fallback. LLM is held as a `[BACKLOG]` fallback for the long tail.

**Address normalization** — Converting variant address forms into the canonical transaction key (street + city + state + ZIP).

**Thread state** — The set of facts the classifier tracks about a Gmail thread: most-recent sender; whether the TC has already replied to the latest incoming; Gmail read state per message. Used to refine triage labels — the TC's manual actions on a thread are authoritative.

**Async backfill** — Background job that runs after first-sync consent. Reads the TC's historical Gmail (no writes) to populate the data model: contact directory, transaction records from existing labels, transaction-participation graph, sender/role inference. Lets onboarding feel instant (live processing starts immediately) while the dashboard fills in over minutes to hours as historical data is indexed.

**Dashboard** — Default landing page of the tc-overwatch UI. Comprehensive view of the TC's business across all transactions: active transactions list, new business + referral warning queue, review lists (auto-promoted contacts, unmatched email, label disambiguation), backfill progress.

**Transaction details page** — Per-transaction drill-down in the UI, reached by clicking a transaction on the dashboard. Shows the property address (display + canonical), lifecycle and storage state, parties grouped by role, email thread history under the transaction's label, and recent activity log.

**Forward-only writes** — Operational rule: the system applies new labels and priority signals only to mail that arrives *after* consent. It freely reads historical mail (for backfill and context) but never writes to it.
