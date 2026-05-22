# Task Inventory — tc-overwatch

The repetitive TC tasks that tc-overwatch may eventually automate, grouped by area. Each item carries a status flag so it's clear what's the current focus, what's confirmed real, what still needs the pilot TC to review, and what's out of scope.

## Status legend

- `[FOCUS]` — actively being designed or built right now
- `[CONFIRMED]` — pilot TC has confirmed this is a real, repetitive task
- `[REVIEW]` — strawman item; needs validation with the pilot TC before treating as real
- `[BACKLOG]` — confirmed in principle, queued for after the current focus ships
- `[OUT-OF-SCOPE]` — explicitly excluded (see Non-goals at the bottom)

## 1. Email & communication

### Foundations — known-contacts directory

Not a user-visible feature, but every triage decision sits on top of it. Powers both "unfamiliar sender" detection (→ new business) and "known sender + heads-up content" detection (→ referral warning).

**Bootstrap order, by priority:**

- `[FOCUS]` **Gmail contacts** — primary source. Fastest, most curated, the TC already maintains it.
- `[FOCUS]` **Sent-mail history** — anyone the TC has emailed. Broader recall than Gmail contacts; catches agents and parties she communicates with but hasn't formally saved as contacts. Particularly valuable for identifying **agents she's worked with on the other side of past transactions** (listing agent if she represented the buyer, or vice versa). These are high-value referrer candidates.
- `[REVIEW]` **Spreadsheet import** — the TC keeps a contact spreadsheet. Unknown whether it contains email addresses outside her Gmail contacts. Worth importing only if it does — ask her during the review pass.

**Chicken-and-egg dependency:**

Identifying "agents on the other side of past transactions" requires being able to (a) detect a transaction-related email, (b) recognize the sender as an agent, and (c) distinguish them from the TC's primary client agent on that deal. So *role labeling* (tagging known contacts as agent / lender / title / escrow / client / vendor, and flagging other-side agents as potential referrers) is a **second pass** that runs after transaction detection comes online. v0 ships with everyone-known-is-just-known; role labeling layers in later.

**Contact schema (v0):**

- `email` — primary identity when present; may be null for contacts captured by phone/name only (e.g. a referred lead whose info appeared in a referral warning before they emailed in)
- `display_name` — required
- `phone` — normalized to E.164 format (e.g. `+15551234567`). **Real estate is a phone-primary industry** — phone is more load-bearing for contact identification than email. This field powers both lead attribution and the lead-capture flow.
- `last_seen_at` — feeds promotion-to-known logic and staleness
- `role` — one of `agent`, `lender`, `title`, `escrow`, `client`, `vendor`, `unknown`. Starts `unknown`, refined over time.
- `auto_promoted` — `true` if added by the bidirectional promotion rule rather than by manual entry or initial import. Lets the TC review and downgrade if needed.
- `referred_by_contact_id` — nullable foreign key to another Contact. Set **only when there's explicit referral context** (a referral warning from a known referrer, or a new-business email that explicitly names a referrer who resolves to a known Contact). Soft attribution signals (domain match, weak name match) do *not* set this field — they're surfaced as hints, not persisted as hard data.
- `notes` — optional, manual

A contact requires at least one of `email` or `phone`, plus `display_name`. Lookup is by either identity field — the system never assumes contacts can only be identified by email.

**Phone capture and enrichment:**

- `[FOCUS]` **Async backfill of known-contact phones.** Parse email signatures of every known contact to populate their `phone` field. Phones are typically in signatures; this is one-time enrichment that runs during the historical backfill. New incoming mail from a known contact whose signature contains a phone we don't yet have updates the record live.
- `[FOCUS]` **Lead phone capture from referral warnings.** When a referral warning arrives, scan the body for phone numbers (and email addresses, names) referring to the new lead. If a phone is found that isn't already associated with a known contact, create a new contact record with `display_name` + `phone` (+ `email` if also present), `role = unknown`. This pre-populates the lead's record before they make first contact.
- `[FOCUS]` **Lead phone capture from new-business emails.** Same extraction logic — parse signatures and body for the lead's own phone. Update or create the contact record.
- `[FOCUS]` **Phone normalization.** All phones stored in E.164 format. Format variants (`(555) 123-4567`, `555-1234`, `555.123.4567`, `+15551234567`) normalize to the same canonical key. Lookup is format-insensitive.

**Referral context capture (hard data, distinct from attribution hints):**

Explicit referral context is captured on the lead's Contact via `referred_by_contact_id`. This is *hard data* — the system only sets it when the referrer is unambiguous. The five-signal lead-attribution pipeline (described under *Triage taxonomy*) detects *possible* referrers; only the strong signals graduate to setting `referred_by_contact_id`.

- `[FOCUS]` **From a referral warning** — the warning's sender is the referrer, unambiguously. Set `referred_by_contact_id` on the lead's Contact (creating it if not yet present from the warning's body extraction).
- `[FOCUS]` **From a new-business email's explicit text** — when the body explicitly names a referrer ("I was referred by Sarah Johnson") and the name resolves to a known Contact via fuzzy match, set `referred_by_contact_id` on the lead's Contact. If the named referrer is *not* in the directory, store the referrer's name in `notes` for traceability — do not auto-create a Contact for an unknown referrer.
- `[FOCUS]` **From thread context** — when the new-business email is a Fw:/Re: of a thread that includes a known agent's email, that agent is the referrer. Set `referred_by_contact_id` to the agent's Contact.
- `[FOCUS]` Soft attribution signals (email domain match, weak name match alone) **never** set `referred_by_contact_id`. They surface as "possible attribution" hints on the dashboard only.
- `[FOCUS]` First-explicit-referrer wins on conflict. If a lead has a referrer set from a warning and a later new-business email names a different person, the system keeps the original and notes the discrepancy in `notes` for the TC to resolve via the editing surface.
- `[FOCUS]` UI surfacing: new-business items show the referrer prominently when set; transaction details pages show the referral source for the deal when applicable. Referrer-value analytics (rolling up business attributed to each referrer) is `[BACKLOG]`.

**Promotion to "known" (bidirectional-only):**

A new sender becomes "known" only when there's evidence of real back-and-forth:

- The TC has sent them an email (outbound first), OR
- The TC has replied to one of their emails

Inbound-only senders **never** auto-promote regardless of volume. This is the spam guard — cold outreach can't promote itself into the known-contacts directory just by hammering the inbox. `[FOCUS]`

v0 behavior on promotion: **silent**, with an on-demand "review auto-promoted contacts" list the TC can pull up when curious. No per-promotion notifications (would add noise to a system whose purpose is to reduce email noise). A periodic digest is `[BACKLOG]`.

**Role accounts (shared inboxes like `closings@firsttitle.com`):**

Treated as a single contact, **no disambiguation in v0**. The TC works with title/escrow/lender shops as institutions; she doesn't usually need to know which human behind a shared inbox sent today's message. If the same human later emails from a personal address, that becomes a *separate* contact (same `role`, same email domain). A domain-rollup view (e.g. "all First Title contacts") is `[BACKLOG]` for if/when a feature needs it.

**TransactionParticipation schema (v0):**

A contact's relationship to a specific transaction. A contact can have many participation rows (an agent might appear on three deals — direct on one, cooperating on two).

- `contact_id`
- `transaction_id` — transaction key = normalized property address
- `role_on_deal` — for agents: `primary` (the TC was hired by them on this deal) or `cooperating` (other side). For non-agents: mirrors the contact's `role`.

Intentionally not stored on the contact: phone, address, brokerage grouping, per-contact override rules. Add only when a feature needs them.

### New business & referral warnings (highest priority)

New business and referral warnings sit in the same priority tier. They are detected independently — no mechanical attempt to chain a warning to the new-business email it precedes — but both must be flagged immediately.

- `[FOCUS]` Detect **new-business** emails — inbound leads, capacity inquiries, "looking for a TC" intros. Independent TCs are business owners; new business must never get lost under transaction operations email. This is the highest priority class in the triage system.
  - Detection signals: **unfamiliar sender** (not in known contacts directory), referral language ("referred by", "do you have capacity", "looking for a TC"), unknown agent introducing themselves, quote/pricing requests, absence of any existing transaction association.
- `[FOCUS]` Detect **referral warnings** — a heads-up from a **known referrer** ("I gave your name to a friend, she'll reach out") signaling an incoming new-business email from someone else. Sender is known; content is the signal.
  - Detection signals: known sender + content patterns like "I gave them your name", "expect a call from", "I referred [name]", "introducing", "they'll reach out", "she/he will be in touch".
  - Explicitly **do not** try to match a warning to the new-business email it precedes. Flag both independently and let the TC make the connection.
- `[FOCUS]` Apply a dedicated `New Business` label (star + important) to new-business emails. Skip *active* transaction lookup — no transaction exists yet.
- `[FOCUS]` **Lead attribution.** For each new-business email, attempt to associate it with a *past* transaction by scanning the email (body, signature, thread context) for references to known agents. If a known agent is matched, look up their `TransactionParticipation` rows and surface the connection to the TC ("this lead may be from Sarah Johnson's network; she's appeared on N past deals as a cooperating agent"). Soft-fail if no agent reference is found — the email is still flagged as new business with no attribution.
  - **Attribution signals**, in precision order:
    1. **Thread context** — Fw:/Re: of a thread that already includes a known agent's email. Highest precision.
    2. **Phone match** — phone number in body/signature matches a known agent's `phone` (after E.164 normalization). High precision, especially in real estate where phone is the primary form of contact.
    3. **Direct email match** — email address in body/signature matches a known agent's `email`. High precision.
    4. **Email domain match** — new-business sender's domain matches a known agent's email domain (suggests same brokerage / firm).
    5. **Full-name fuzzy match** — names in body/signature against known contacts' `display_name`. Lower precision (common names collide) — used as a corroborating signal, not alone.
  - **Confidence layering**: strong signals (thread context, phone, email exact match) → surface attribution prominently on the new-business item. Medium (name + domain) → surface as "possible attribution". Single weak signal alone (just name, just domain) → don't surface unless multiple signals converge.
- `[FOCUS]` Apply a dedicated `Referral Warning` label (star + important) to referral warnings. Skip transaction lookup.
- `[REVIEW]` Push/desktop notification on new-business or referral-warning arrival so the TC sees it in real time. *(Future — out of scope for v0 since notifications need a separate channel.)*

### Transaction email — active deals

- `[FOCUS]` Identify the transaction for each incoming email. Transaction key = property address. Address may live in subject, body, attachment, or only be implied by thread context. Must normalize variants ("123 Main St" ≡ "123 Main Street, Anytown CA 94000").
- `[FOCUS]` If a transaction match cannot be found, apply an `Unmatched` label (no triage label, no priority signal). The TC reviews these to either teach the system or manually file. Fallback only — not a fourth taxonomy category.
- `[FOCUS]` Triage active-deal email into one of three labels. **Definitions below are the strawman target for the classifier — `[REVIEW]` open questions follow.**
- `[FOCUS]` Apply the per-transaction Gmail label and move the message into it. **The label name IS the property address.** One transaction = one label, always. Never grouped by agent, client, lender, or brokerage — same agent on five deals means five separate labels, not one. This is non-negotiable; it keeps each transaction a self-contained unit.
- `[FOCUS]` **Label format default is FULL** — `123 Main St, Anytown CA 94000`. The pilot TC works in multiple cities, so short-form (`123 Main St, Anytown`) is unsafe: `123 Main St` can collide across nearby towns. Short remains an opt-in for single-market TCs who explicitly accept that risk. The internal transaction key is always the fully normalized form with state + ZIP; the label is a *display* of that key.
- `[FOCUS]` **No retroactive reorganization on first sync.** The system never modifies existing read or filed emails, never renames existing labels, never restructures the TC's inbox. Her current organization is left exactly as she has it.
- `[FOCUS]` **Read vs. write distinction.** The system freely *reads* historical mail to build internal state (dashboard data, known-contacts directory, transaction-participation graph). It never *writes* to historical mail — no labels applied, no priority signals changed, no Gmail-level modifications. Reads = OK; writes = forward-only.
- `[FOCUS]` **First-sync consent screen.** Before processing begins, show a one-time UI screen explaining what will happen (new incoming unread mail will be classified, labeled, prioritized; historical mail will be read to populate the dashboard) and what won't (no changes to existing organization; no outbound email; v0 boundary). The user must explicitly proceed.
- `[FOCUS]` **Forward-only writes.** From the moment access is granted, only newly-arriving incoming mail gets new system-applied labels or priority signals. Historical mail is read (for data extraction) but never written to.
- `[FOCUS]` **Async backfill.** After consent, two things happen in parallel: (a) live processing of new incoming mail begins immediately — no blocking on backfill, onboarding feels instant; (b) a background job reads historical mail to populate the data model (contact directory, transaction records from existing labels, transaction-participation graph, sender/role inference). Backfill may take minutes to hours depending on mailbox size; the dashboard fills in as data becomes available. Backfill performs reads only — never writes back to Gmail.
- `[FOCUS]` **Each existing address-shaped Gmail label = one discrete Transaction record.** On first sync, every address-shaped label becomes its own Transaction record in the data store with a unique ID, holding whatever address info can be parsed from the label name — even if partial (e.g. `123 Main St` with no city/state/ZIP). The label name is the display; the data store holds the canonical record. Partial address details can be enriched silently from email contents within the label (via async backfill), but the Gmail label itself is never renamed.
- `[FOCUS]` **New transactions always get new IDs.** Even when a new transaction's address is the same as or similar to an existing transaction, the new deal gets a new ID in the data store. The system never repurposes an existing transaction's ID for a new deal. (Combines with the *Multi-transaction labels* rules above: internal IDs are always distinct; user-facing labels are disambiguated only when needed.)
- `[FOCUS]` **Idempotent processing.** Track per-message processing state (Gmail message IDs the system has acted on, plus what it did) so a restart or re-sync never reprocesses or duplicates labels on a message. The state of record for what the system has done is its own store, not Gmail.
- `[FOCUS]` Format-change migration (TC opts to change format preference): the system renames existing *system-created* labels via the Gmail API in place rather than creating duplicates. **System-created labels only** — user-pre-existing labels are still untouched.

#### Multi-transaction labels (same address)

A single property address can be the subject of multiple transactions over time — re-sale to a new owner years later, a failed contract re-listed, or (rarely) simultaneous active deals on different units of one property. The address alone is not unique forever.

**Transaction key = normalized address + contract open date.** The internal key is sortable, deterministic, human-readable, and guaranteed unique even when the same address recurs. The Gmail label remains a *display* of the key — clean when there's no collision, suffixed when there is.

Disambiguation rules:

- `[FOCUS]` Single transaction at an address → label is the clean address, no suffix.
- `[FOCUS]` New active deal opens at an address that already has a *closed* transaction → active label stays clean; the closed deal's label is renamed in place via the Gmail API to add `(closed YYYY)` (e.g. `123 Main St, Anytown CA 94000 (closed 2024)`). The active deal always gets the visually-clean label.
- `[FOCUS]` Two or more *active* deals at the same address simultaneously → all involved labels get a `(YYYY)` suffix from the contract open date. The TC can override with a custom suffix (unit number, client last name, etc.) via the standard manual-edit surface.
- `[FOCUS]` Operation is automatic and deterministic — no per-collision TC prompt. Matches the auto-promotion pattern: silent on routine collisions, visible in the on-demand review list, manually overridable.

#### Address extraction strategy

Address extraction is a layered pipeline. The key insight: we are not extracting arbitrary addresses from arbitrary text — we are identifying which of the TC's ~10–30 active transactions an email belongs to. This **closed-set classification** problem is much easier than open-ended NLP, and most layers below exploit that.

Layers in cost / complexity order:

1. `[FOCUS]` **Parse the subject line first** with a dedicated US-address parser. The TC trains her agents to put the address in the subject; when they comply this is the cheapest path.
2. `[FOCUS]` **Parse the body** if the subject yields nothing. Same machinery, broader haystack, slightly more noise.
3. `[FOCUS]` **Resolve parsed candidates against the closed set of active transactions** via fuzzy matching on tokens (street number, first word of street name, ZIP if present). With ~15 active deals at a time, ambiguity is rare once a partial address is in hand. When multiple deals share an address (active + closed, or simultaneous active), prefer active over closed; remaining ties are broken by thread and sender context (layers 4–5).
4. `[FOCUS]` **Thread-context shortcut.** If a prior message in the same Gmail thread already matched transaction X, default the current message to X unless the parser strongly contradicts. Catches the large fraction of replies where the address isn't repeated.
5. `[FOCUS]` **Sender-context narrowing.** If the sender has `TransactionParticipation` rows on exactly one active deal, that deal is the strong default. Apply before fuzzy matching to prune the candidate set.
6. `[FOCUS]` Apply the `Unmatched` label when nothing crosses a confidence threshold.

`[BACKLOG]` **LLM fallback** for the long tail (image-only signatures, weird forwarded chains, addresses written in prose like "the place on Maple"). Run only on emails that fail layers 1–5; cheap structured-output models keep cost minimal. Add only if observed miss rate justifies it.

**Collisions across cities** are a real concern — the pilot TC works in multiple markets, so `123 Main St` can resolve to two different active deals. The full-form label default (above) is the primary defense; address extraction layers 4 and 5 (thread + sender context) break remaining ties.

**Implementation libraries** are intentionally not pinned here — choice of US-address parser, fuzzy-match library, and any normalization service belongs to the implementation-design phase, not requirements. Multiple solid open-source options exist for each layer.

#### Triage taxonomy — definitions

**`action-needed`** — *the TC herself must do something on this thread before it can move forward.*

- The sender is asking her a direct question
- A document needs to be sent, forwarded, or retrieved by her
- A signature, disclosure, or piece of paperwork is being requested from her
- A deadline or scheduling decision needs her input
- A missing item needs to be chased

Signals: TC is on the `To:` line (not just CC); body contains a request, question, or deadline directed at her; sender expects a reply.

**`awaiting-response`** — *the ball is in someone else's court; the TC has done her part and is waiting.*

- Acknowledgments ("got it, will review")
- "Checking with my client" / "I'll get back to you tomorrow"
- Out-of-office auto-replies for someone the TC emailed
- Status updates from lender/title saying "in progress, will send when ready"

Signals: explicit "will get back", "give me until X", "OOO until Y"; or an acknowledgment of a request the TC previously made.

**`FYI`** — *informational; doesn't require action and isn't blocking the TC.*

- Brokerage announcements, newsletters
- TC is CC'd on a thread that doesn't involve her directly
- Document-shared notifications (DocuSign, Dropbox)
- Closing congratulations
- **Vendor email that references a specific property address** — filed under that transaction's label as FYI. Even a home-warranty pitch or MLS notification about a listing she's working has context-value if it can be tied to a deal.

Signals: TC on CC rather than To; no question, request, or deadline directed at her; sender is a tool generating a courtesy notification; vendor email *with* a property address match.

**junk** (trashed, no label applied) — *generic vendor email with no transaction reference, cold outreach that isn't new business, leftover bulk mail.*

Signals: bulk-mail patterns; product pitches with no specific property reference; no TC-specific context.

> Gmail's built-in spam filter handles outright spam and phishing upstream — the classifier only acts on mail that's already passed that bar. We don't try to replicate Gmail's spam detection.

#### Triage taxonomy — classifier bias rules

Mis-triage has asymmetric costs. The classifier defaults toward keeping the TC over-informed rather than under-informed.

- `[FOCUS]` **When uncertain between `action-needed` and `FYI`, route to `action-needed`.** Missing an actionable email is much worse than mildly mislabeling an FYI.
- `[FOCUS]` **When uncertain between `FYI` and `junk`, route to `FYI`.** Trashing something the TC needed to see is catastrophic.
- `[FOCUS]` **When uncertain between `new-business` and transaction-email, route to `new-business`.** A lost lead is the worst outcome of the whole system. *(Cross-references the new-business section above.)*

#### Triage taxonomy — thread-state signals

A new email's triage label is informed by what's already happened in the thread it belongs to. The TC currently does this manually using Gmail's read state and her own filtering — the system augments her workflow, doesn't fight it.

- `[FOCUS]` Track per-thread state: who sent the most recent message; whether the TC has already replied to the latest incoming message; Gmail read state on each message in the thread.
- `[FOCUS]` Use thread state to refine triage:
  - Latest message in thread is from the TC → the next inbound on this thread is typically `awaiting-response` content unless it asks her a new question.
  - Latest incoming is unread AND TC hasn't replied → more likely `action-needed`.
  - Latest incoming is read AND TC has replied since → thread is being actively handled; new replies default to lower urgency.
- `[FOCUS]` Never re-flag an email the TC has already manually dealt with (read + replied to, or moved/labeled by hand). Her actions are authoritative.

#### Triage taxonomy — deadline handling principle

**Deadlines live in the contract, not in email.** Inspection contingency, financing contingency, closing dates — all stipulated by the contract. Email mentions of deadlines are *informational context* only; the system never infers, schedules, or escalates based on dates that exist only in email body text.

Practical consequences for the classifier:

- An email like *"FYI, inspection ends Thursday"* → `FYI`. The date is restated context, not new state.
- An email *proposing* a deadline change (*"can we extend inspection?"*) → `action-needed`. The TC must coordinate a contract amendment. But the new date isn't real until the amendment is signed; the system does not update any deadline state from the email alone.
- Contract-driven deadline extraction is a separate (future) feature; it reads the actual executed contract, not its mentions in email.

#### Triage taxonomy — urgency signals within a category

The taxonomy is flat (3 labels + junk + Unmatched). Degrees of urgency **within** a category are expressed in Gmail's native signals, not as additional labels:

- `action-needed + starred + important` → urgent, deadline-sensitive
- `action-needed + neither` → normal pending work
- `awaiting-response` → never starred or marked important (by definition the TC is not currently blocked)
- `FYI` → never starred or marked important

#### Triage taxonomy — resolved (was open during refinement)

- **Vendor email** → if it references a specific property address, FYI under that transaction's label; otherwise junk. Gmail spam filter is the first line of defense; we act on what reaches the inbox.
- **Thread context matters.** The system tracks last-sender-in-thread, whether the TC has already replied to the latest incoming, and Gmail read state. The TC's manual actions (read, reply, label, move) are authoritative — the system never re-flags what she's already handled.
- **Deadlines live in the contract.** Email mentions of deadlines are informational, not state-changing. Deadline-change proposals are `action-needed` because the TC must coordinate a contract amendment.
- **No urgency sub-categorization.** `action-needed` is not split into "urgent" vs. "needs eventual response". Gmail's star marker is sufficient — `action-needed + starred` is the urgent variant. Re-evaluate only if the TC reports the single label feels too coarse in practice.

#### Triage taxonomy — still open for TC review

- `[REVIEW]` Is "TC on CC" always a strong FYI signal? Are there scenarios where she's CC'd but expected to act? *(Pending TC clarification.)*
- `[FOCUS]` Reflect triage urgency in Gmail's native priority signals (importance markers, stars).
- `[BACKLOG]` Generate "what's next" / timeline replies to clients. *(Requires lifting the v0 no-initiate boundary.)*
- `[BACKLOG]` Auto-follow-up for missing documents or unsigned DocuSign. *(Requires no-initiate boundary lift.)*
- `[BACKLOG]` Canned replies for recurring questions ("when does inspection end?", "what's the closing date?"). *(Requires no-initiate boundary lift.)*
- `[BACKLOG]` Notify agent + parties when transaction status changes. *(Requires no-initiate boundary lift.)*

### Transaction email — closed deals

After a transaction is closed, the TC is no longer professionally permitted to act on it. The system still labels these emails for history/continuity but actively *de-prioritizes* them and warns the TC against engaging.

- `[FOCUS]` Apply the per-transaction label as normal so the message is filed against the right deal.
- `[FOCUS]` Apply an additional `Closed Transaction` indicator label so the UI can render a "don't act" warning icon / banner.
- `[FOCUS]` **Do not** apply any priority signal — no star, no important marker, no urgency label. Even if the email content reads as urgent, the TC can't act on it.

## 2. Documents & signatures

- `[REVIEW]` Track signed / pending / missing docs per transaction.
- `[REVIEW]` Send DocuSign / e-sign requests from templates. *(Open question: does the TC actually send, or only prepare for the agent to send?)*
- `[REVIEW]` Chase unsigned DocuSign envelopes.
- `[REVIEW]` Auto-name + file incoming documents into the right transaction folder (Dropbox hot storage).
- `[REVIEW]` Extract key fields from executed contracts (parties, addresses, price, dates, contingencies).
- `[REVIEW]` Generate state-required disclosures from contract data.

## 3. Timeline & deadlines

- `[REVIEW]` Compute key dates from contract (inspection deadline, financing contingency, appraisal, closing).
- `[REVIEW]` Generate calendar entries / reminders for agent + parties.
- `[REVIEW]` Pre-deadline nudges ("inspection contingency expires in 2 days").
- `[REVIEW]` Produce a client-facing timeline summary.

## 4. Compliance & checklists

- `[REVIEW]` Brokerage compliance checklist per transaction. *(Open question: repetitive enough to automate, or genuinely one-off per deal?)*
- `[REVIEW]` Verify state-required disclosures present and signed.
- `[REVIEW]` Pre-close file audit / readiness check.

## 5. Coordination with parties

- `[REVIEW]` Schedule inspections, appraisals, walk-throughs. *(Open question: is this on the TC or the buyer's agent?)*
- `[REVIEW]` Status pings to title / lender / escrow.
- `[REVIEW]` Wire-instruction confirmations & fraud-warning reminders.
- `[REVIEW]` Closing-day logistics (who needs to be where, when).

## 6. Status reporting

- `[REVIEW]` Weekly status snapshot to the agent across all active transactions.
- `[REVIEW]` Per-client status updates.
- `[REVIEW]` Internal tracker / spreadsheet updates.

## 7. Transaction lifecycle

A transaction has one lifecycle state: `active` or `closed`. The state controls how the email triage system handles incoming mail for that transaction (see "Transaction email — closed deals" above).

- `[FOCUS]` Maintain transaction lifecycle state (`active` / `closed`) per transaction.
- `[REVIEW]` **Mechanic for transitioning a transaction to `closed`.** Default proposal: a manual "mark this transaction as closed" action on the transaction-details page — simplest reliable approach for v0. The TC may have a more natural mechanic in mind (e.g. inferred from a "closing complete" email pattern she already recognizes, or triggered by a specific document arriving). Confirm with TC before committing.
- `[REVIEW]` Auto-infer closure from contract close date (if extracted) or extended inactivity. Refinement only; not v0 unless TC says manual marking is impractical.
- `[REVIEW]` **Reopening a closed transaction.** Default assumption: closed is terminal — once a transaction is closed it never reopens. Confirm with TC. If reopens *do* happen (e.g. post-close issue requiring reactivation), the data model needs either a closed → active transition or a separate `archived` state.

## 8. Known-contacts directory governance

The directory is mostly self-maintaining (Gmail-contacts bootstrap + bidirectional auto-promotion + silent operation). The TC only intervenes when she notices something wrong.

- `[FOCUS]` View the auto-promoted contacts list (on-demand).
- `[FOCUS]` Edit a contact: change name, role, notes.
- `[FOCUS]` Add or remove a contact manually.
- `[FOCUS]` Correct a transaction participation: change an agent's `role_on_deal` between `primary` and `cooperating`, or remove a wrong association entirely. Critical because lead attribution depends on it being right.
- *(Intentionally absent: scheduled review prompts, required cleanup cadence, approval queue for auto-promotions. The TC's whole problem is too much email work; we don't invent new chores.)*

## 9. User interface

The TC's primary surface beyond Gmail itself. v0 ships two main pages.

### Dashboard (default landing page)

A comprehensive view of the TC's business across all transactions. Populated by both live processing and the async backfill.

- `[FOCUS]` Active transactions list with status indicators (lifecycle, urgency, last activity).
- `[FOCUS]` New business + referral warning queue (highest visible priority).
- `[FOCUS]` Auto-promoted contacts review list (governance — section 8).
- `[FOCUS]` Unmatched-email review list (transaction email with no identified address).
- `[FOCUS]` Label disambiguation review list (collisions surfaced for TC awareness).
- `[FOCUS]` Backfill progress indicator while historical data is still being indexed.
- `[BACKLOG]` Activity / metrics widgets (emails processed today, distribution by triage label, etc.).

### Transaction details page

Per-transaction drill-down. Reached by clicking a transaction on the dashboard.

- `[FOCUS]` Property address (display label + internal canonical address).
- `[FOCUS]` Lifecycle state (active / closed) and storage state (hot / cold).
- `[FOCUS]` Parties involved, grouped by role; for agents, primary vs. cooperating flag.
- `[FOCUS]` Email thread history under this transaction's label.
- `[FOCUS]` Recent activity log.
- `[BACKLOG]` Document list (Dropbox / NAS links — depends on storage tiering work).
- `[BACKLOG]` Contract timeline (requires contract extraction — gated on future feature).

### Editing surfaces

The minimal reactive editing surface (section 8) is reached from these pages:

- Manual contact edits / role corrections
- Manual transaction-participation corrections (flip agent between primary and cooperating, remove a wrong association)
- Manual transaction lifecycle marking (active → closed)
- Manual label disambiguation overrides

## 10. Access control & signup

Invitation-only for MVP. Implemented as a configurable **signup mode** so future modes (open, paid, referral) slot in without code changes.

- `[FOCUS]` **Configurable signup mode** — server-side setting with values: `invitation` (v0 default), `open`, `paid`. Enforced on the auth callback path; never a frontend-side check.
- `[FOCUS]` **`Invitation` entity.** Fields: `id`, `email` (the email expected on Google authentication), `token` (UUID for the invite URL), `created_at`, `created_by` (nullable admin/user reference), `expires_at` (optional), `accepted_at` (null until accepted), `tenant_id` (assigned at acceptance — for v0, each invitation creates a new tenant; SaaS may add invite-to-existing-tenant flows later).
- `[FOCUS]` **Auth gate** — after Google OAuth callback, the system applies the current signup mode's check before provisioning a tenant. In `invitation` mode: the authenticated email must match a pending unaccepted `Invitation` row. Mismatch returns a clear "currently invitation-only" message; no tenant or user is created.
- `[FOCUS]` **Invitation acceptance** — on the matching path, mark the invitation `accepted_at`, create the tenant + user account, then route to the **first-sync consent screen** (separate step from invitation acceptance — see section 1 onboarding rules).
- `[FOCUS]` **Invitation creation (v0)** — direct DB insert or a minimal admin RPC. The developer is implicitly the only admin in v0; no UI required.
- `[BACKLOG]` Admin UI for invitation management (list, create, revoke, view-status).
- `[BACKLOG]` Invitation expiry policy (default: no expiry; revisit when SaaS launches).
- `[BACKLOG]` Email-delivered invite links (v0 admins share links out of band; SaaS adds an actual invitation-email flow).
- `[BACKLOG]` Multi-user-per-tenant invitations (a brokerage with multiple TCs joining one tenant). v0 is strictly one user per tenant.
- `[BACKLOG]` Paid signup mode wiring (subscription check at the auth gate).

## 11. Storage management

- `[CONFIRMED]` Hot storage = Dropbox for active / < 6mo transactions.
- `[CONFIRMED]` Cold storage = NAS / network storage for files > 6 months old.
- `[REVIEW]` Automated migration job from hot → cold at the 6-month threshold.
- `[REVIEW]` Retrieval flow when an archived transaction reactivates.

## Non-goals

- `[OUT-OF-SCOPE]` Personal-assistant features (errands, generic calendar, non-transaction scheduling).
- `[OUT-OF-SCOPE]` Replacing the TC — this augments, doesn't substitute.
- `[OUT-OF-SCOPE]` Custom auth (Google OAuth only).
- `[OUT-OF-SCOPE]` General-purpose email/CRM features not tied to a recurring TC task.

## Review notes

Each `[REVIEW]` item needs a yes / no / refine pass with the pilot TC. As items are confirmed or rejected:

- Confirm → flip to `[CONFIRMED]` (or `[BACKLOG]` if not the current focus).
- Reject → move to the Non-goals section with a one-line reason it's not a real recurring task.
- Refine → reword the item in place to reflect what's actually repetitive about it.
