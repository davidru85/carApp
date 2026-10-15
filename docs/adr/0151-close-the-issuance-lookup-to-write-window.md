# ADR-0151 / D-150 - Close the Issuance Lookup-to-Write Window

## Status

Accepted

The owner selected **option A** on 2026-10-15: accept the window as a residual risk (see
"Decision"). The context and option analysis below are preserved as written while the decision was
`Pending`, when no option was recommended, except for the retention statements under "A. Linking
between the lookup and the write", in the option A row of "Options Considered" and under "Privacy
and retention implications", which the pull request #89 review corrected on 2026-10-15: successful
consumption retains the record as `COMPLETED`, and only an observing `D-143` purge or asynchronous
Firestore TTL deletion erases it.

## Context

`D-148` (ADR-0149) made `issueOrphanCleanupTicket` resolve the caller's current Firebase Auth record
through the Admin SDK before it writes anything. `createOrphanCleanupTicketHandler` performs three
separate operations:

1. `auth.getUser(anonymousUid)` — resolve the Admin record;
2. `canIssueOrphanCleanupTicket(caller)` — evaluate the shared `D-134` predicate plus the explicit
   `disabled === false` state;
3. `authorizations.issue(...)` — persist the UID-bound authorization in Firestore.

**Firebase Auth and Cloud Firestore share no atomic transaction.** Step 1 reads one service, step 3
writes another, and nothing serialises them. The account can therefore be linked, disabled or
deleted after the lookup has returned an eligible snapshot and before the Firestore write commits.

What `D-148` actually establishes is a fact about the snapshot the lookup returned: it existed, was
explicitly enabled, and was still anonymous. That is a real narrowing — before `D-148` there was no
Admin call at all — and it rejects, fail-closed, every token whose identity was **already**
ineligible when the lookup resolved. It is not a statement about the instant the write commits.

## The three interleavings

Write `I1` for the Admin lookup, `I2` for the predicate evaluation, `I3` for the Firestore
authorization write, and `T` for the Auth-side transition. In every case below the ordering is
`I1, I2, T, I3`: the snapshot read at `I1` is eligible, the predicate passes at `I2`, the transition
lands at `T`, and the write commits at `I3` against an identity that no longer satisfies the
predicate.

### A. Linking between the lookup and the write

The anonymous account links a permanent credential at `T`. At `I3` a UID-bound authorization exists
for an identity whose `providerData` is no longer empty.

`D-142` (ADR-0143) covers **part** of the consequence and no more: the consumption callable
re-resolves the bound UID's Admin record before any destructive stage and rejects a bound account
that still exists and is no longer anonymous, so the linked account is **not** destructively
cleaned up. It does not prevent the authorization from being created, and it does not remove it. The
record, containing `anonymousUid`, remains stored until an observing `D-143` account-deletion purge
or asynchronous Firestore TTL deletion. Successful consumption retains it as `COMPLETED`, with the
same UID and expiry; expiration alone does not erase it.

### B. Disabling between the lookup and the write

The account is disabled at `T` and stays anonymous. At `I3` the authorization exists for a disabled
identity.

This is the gap `D-142` does not reach at all. `D-142`'s consumption check is an **anonymity**
revalidation: a disabled account still has empty `providerData`, so it satisfies the consumption
predicate. Nothing in the current flow independently rejects an account that remains anonymous but
became disabled after issuance eligibility was observed. Disabling is how an account is taken out of
service without deleting it, which is precisely the state `D-148` rejects at lookup time — so the
same state, reached one step later, is not rejected anywhere.

### C. Deletion between the lookup and the write

The account is deleted at `T`. At `I3` the authorization exists for a UID with no Auth record.

Consumption converges: `D-142` preserves a missing bound account as idempotent success so direct
remote deletion and completion-last retries still work. The authorization record itself, carrying
`anonymousUid`, is still created and retained.

**This interleaving is adjacent to `D-149` but is not the same decision, and the two MUST NOT be
merged.** `D-149` (ADR-0150) owns the race between issuance and the `deleteAccount` **server
operation** — specifically an issuance whose write lands after that operation's authorization purge
has run, so a record survives a deletion that returned success. `D-150` owns the window inside the
issuer between its own lookup and its own write, for any Auth-side transition including a deletion
that arrives by another path. They overlap in one ordering and are otherwise distinct; broadening
`D-149` to cover linking and disabling would silently repurpose an owner decision that was framed
and reviewed against the deletion flow.

## What does not close this

Stated explicitly, because each of these looks like a fix and is not:

- **A second Admin read before the write.** It narrows the window to the interval between that read
  and the write; it does not remove it, because the read is still not atomic with the Firestore
  write.
- **A post-write read-back with a compensating delete.** Not atomic with either the Auth transition
  or the authorization creation. It can also crash between the write and the compensation, leaving
  exactly the record it was meant to remove.
- **A retry.** Retries re-run the same non-atomic sequence.
- **Any combination of the above.** Composing non-atomic steps does not produce atomicity.

No option may be described as closing this window unless its argument survives a crash at every
point between two consecutive steps of either flow.

## Options Considered

The table states what each option can deliver and what it cannot. None is recommended.

| Option | Mechanism | What it can deliver | What it cannot deliver |
|--------|-----------|---------------------|------------------------|
| **A. Accept the window as a residual risk** | No mechanism. The issuance contract states the scope honestly and `docs/SECURITY.md` records an accepted residual risk. | An accurate contract at zero implementation cost. The authorization stays a bearer capability bound to one UID, default-denied to clients, and `D-142` still refuses destructive cleanup of a linked account. | Nothing about creation or retention: successful consumption retains the UID-bound record as `COMPLETED`, and expiration alone does not erase it. Physical erasure requires an observing `D-143` purge or asynchronous TTL deletion, with no proven maximum. Requires the owner to accept, in writing, that an authorization can exist for a linked or disabled identity. |
| **B. Revalidate at consumption for the disabled state too** | Extend the `D-142` consumption check to reject a bound account that exists and is disabled, not only one that is no longer anonymous. | Closes the *destructive* consequence of interleaving B, matching what `D-142` already does for interleaving A. Cheap: one predicate, on a path that already resolves the record. | **Not the creation or the retention.** The authorization is still written and still retained. It also changes the `D-142` contract and the `E3-10` / `E3-11` tests that pin it, and it must be reconciled with the missing-account idempotency rule. |
| **C. Serialize issuance against an Auth-lifecycle marker in Firestore** | The Auth lifecycle writes a server-only marker (linked / disabled / deleted) that the issuer reads inside the same Firestore transaction as its authorization write. | A genuine serialization point for transitions that go through a path which writes the marker. | **Only as complete as the marker.** A transition that does not write one — a console action, an Admin SDK call outside the app's flows, a provider-side change — is invisible. It also inherits the whole `D-149` option C retention problem: a UID-keyed marker is UID-correlatable retention, which conflicts with `D-143`, and its lifetime is an unresolved design question, not a detail. |

Options A, B and C are not mutually exclusive; B and C address different consequences, and A can be
combined with either as the statement of what remains.

## Privacy and retention implications

The owner should weigh these explicitly, because they are the substance of the trade:

- The record created by this window contains `anonymousUid`. It is a UID-bound identifier persisted
  for an identity that is, by the time it exists, linked or disabled.
- Successful consumption changes the status to `COMPLETED` and retains `anonymousUid` and the
  unchanged expiry. Expiration makes the ticket invalid and the record eligible for TTL deletion;
  it does not erase the document. Physical erasure is provided by a `D-143` account-deletion purge
  that observes the record, or by Firestore TTL cleanup after the 30-day expiration horizon, with
  an asynchronous deletion delay and no proven maximum.
- Option C would introduce a second UID-linked store to fix the first, which is the same conflict
  with `D-143` that ADR-0150 records for its own option C. Any marker design must state its
  retention rule and justify it against `D-143`, or use a representation that is not
  UID-correlatable and prove that property.

## Decision

The selected option is **A**, accepted by the owner on 2026-10-15.

- No mechanism is added. `D-148` stays scoped to the snapshot its Admin lookup returns, as
  `docs/CONTRACTS.md §11.5` already states.
- An authorization can be created for an identity linked, disabled or deleted between the lookup and
  the write; `docs/SECURITY.md` records that as an accepted residual risk.
- The owner accepted it on these grounds:
  - linking: `D-142` refuses the destructive stage at consumption, so a linked account is never
    cleaned up, and only the account owner can link it;
  - deletion: a missing Auth user is accepted idempotently, so Auth deletion is skipped, but the
    callable still deletes any remaining registered remote data for the bound UID before marking
    the authorization `COMPLETED`. Missing Auth state does not prove that Firestore data is absent,
    and successful consumption does not erase the authorization record;
  - disabling: the only case with a destructive consequence, and it requires an administrator to
    disable the anonymous account inside the interval between the lookup and the write; what
    consumption would then delete is the orphaned anonymous account its owner asked to remove;
  - option C would serialize only transitions that write a marker, missing console and Admin actions,
    and would add UID-correlatable retention against `D-143`; option B was not taken because no
    process disables accounts in the MVP.
- The three `D-150` tests in `functions/test/orphanedAnonymousAccount.test.mjs` keep the limitation
  pinned; `E3-16` records the risk and makes no product change.
- Option B remains available as a later, separate decision if disabling accounts becomes an
  operational process.

## Proof obligations for the accepted option

Option A adds no mechanism. For option A, `E3-16` MUST record the residual risk in
`docs/SECURITY.md`, stating what remains retained and that its TTL cleanup has no proven maximum;
keep `docs/CONTRACTS.md §11.5` scoping `D-148` to the observed snapshot; and keep the three `D-150`
interleaving tests and the `D-148` fail-closed test passing. The obligations below apply to any future
decision that adds a mechanism.

## Proof obligations for a future mechanism

A future story that adds a mechanism for this window MUST, before it can close:

- enumerate the orderings of `I1`, `I2`, `I3` and `T` for each of linking, disabling and deletion,
  including a crash at every point between two consecutive steps of either flow, and state for each
  what holds and which mechanism delivers it;
- attribute every part of the outcome to the mechanism that actually produces it, never to the
  option as a whole, and never present a narrowed window as a closed one;
- for any option that claims serialization, demonstrate the transaction conflict rather than
  asserting it, and enumerate the transition paths that do **not** write the marker;
- state what remains retained and for how long, and — where the answer is the Firestore TTL — say
  that this is provider-managed eventual cleanup with no proven maximum;
- keep the `D-148` fail-closed guarantee intact: a token already ineligible at lookup time MUST
  still be rejected without creating an authorization;
- keep consumption, completion-last semantics and idempotent retries intact, and introduce no
  client-selected UID, no JWT verification fallback and no weaker authorization path;
- leave `D-149` / `E3-15` untouched: the account-deletion race is a separate decision and MUST NOT
  be folded into this one.

## Consequences

### Positive

- The issuance contract states what `D-148` proves and what it does not, so no later story can rest
  on a guarantee the implementation never had.

### Negative

- Under accepted option A, an authorization can exist for an identity linked, disabled or deleted
  between the lookup and the write. Successful consumption retains its UID as `COMPLETED`; only an
  observing `D-143` purge or asynchronous TTL deletion physically erases the record, with no proven
  maximum.
- `docs/SECURITY.md` carries the accepted-residual-risk entry for this window, because the owner
  chose option A.

### Constraints Introduced

- No document may state that `D-148` prevents an authorization for a linked, disabled or deleted
  identity without scoping the claim to the state observed when the Admin lookup resolved.
- No implementation may present a second Admin read, a post-write read-back, a retry or a
  compensating delete as closing this window.
- `D-149` MUST NOT be broadened to cover linking or disabling.

## Verification

- Accepted option A adds no mechanism. Run `npm test` in `functions/`, preserving the three `D-150`
  characterization tests and the `D-148` fail-closed test, and run `./gradlew contractCheck`.
  Exhaustive crash and serialization proofs apply only to a future mechanism under the section
  above.
- The current limitation is already pinned deterministically, so it cannot be re-described as safe:
  `functions/test/orphanedAnonymousAccount.test.mjs` contains three `D-150` tests in which the
  account is linked, disabled or deleted between the Admin lookup and the authorization write, each
  asserting that the authorization is nonetheless created and that the resulting record no longer
  satisfies `canIssueOrphanCleanupTicket`. A fourth test re-asserts the preserved `D-148`
  fail-closed behaviour for every snapshot that is already ineligible at lookup time.

## References

- `docs/CONTRACTS.md` §11.5
- `docs/adr/0149-verify-the-issuing-account-through-the-admin-sdk.md` (`D-148`)
- `docs/adr/0143-revalidate-ticket-bound-anonymity-at-consumption.md` (`D-142`)
- `docs/adr/0144-erase-orphan-cleanup-authorizations-on-account-deletion.md` (`D-143`)
- `docs/adr/0150-close-the-ticket-issuance-and-account-deletion-race.md` (`D-149`, the separate
  account-deletion race)
- `docs/BACKLOG.md` (`E3-16`)
