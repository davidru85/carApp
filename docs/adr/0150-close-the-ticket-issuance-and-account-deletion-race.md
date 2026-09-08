# ADR-0150 / D-149 - Close the Ticket Issuance and Account Deletion Race

## Status

Proposed

This decision is the owner's. `E3-15` MUST NOT start until it is `Accepted`.

## Context

`D-148` (ADR-0149) stops a stale anonymous token from minting a cleanup ticket, but it cannot
guarantee the `D-143` erasure invariant on its own: *no UID-bound authorization record exists once
`deleteAccount` has returned success*.

The normative deletion order of `docs/CONTRACTS.md §11.5` is:

1. delete remote data under `users/{uid}`;
2. purge every `orphanCleanupTickets` record bound to the UID;
3. delete the Firebase Auth user;
4. return success.

The issuer, after `D-148`, is:

1. resolve the caller's Admin record and check eligibility;
2. write the authorization;
3. return the ticket.

The surviving interleaving is `I1, D1, D2, I2, D3, D4`: the issuer's eligibility check passes while
the account is still perfectly valid, the purge runs and finds nothing, the issuer's write lands,
and the Auth user is then deleted and success is returned. A UID-bound authorization outlives a
successful deletion, and its record contains `anonymousUid`.

No check inside the issuer closes this window, because at the moment of the write the account
legitimately still exists and is still anonymous. The window is therefore closed only by changing
something outside the issuer, which is why this is an owner decision rather than an implementation
detail.

## Two properties that must not be confused

Any candidate mechanism MUST be assessed against one of two distinct properties, and this ADR
deliberately does not treat them as equivalent:

- **P1, eventual convergence:** after any interleaving of issuance and deletion, and after all
  retries settle, no UID-bound authorization record for the deleted UID remains, even if one was
  observable for a while. This is a liveness-plus-cleanup property. A TTL, a compensating delete or
  a later purge pass can provide it.
- **P2, synchronous crash-safe erasure:** at the instant `deleteAccount` returns success, no
  UID-bound authorization record for the deleted UID exists, and this holds even if any process
  crashes at any point. This requires a serialization point between the issuer's write and the
  deletion flow's purge — an atomic ordering that no pair of independent reads and writes on two
  separate services (Firestore and Firebase Auth) can provide by itself, because the issuer's write
  can always land between the mechanism's last step and the response.

**Neither a second purge pass nor an issuer post-write read-back provides P2.** The owner's
counterexample, which any proposed mechanism MUST survive, is:

1. the issuer's initial Admin eligibility read succeeds — the account still exists and is eligible;
2. `deleteAccount` deletes the Auth user, runs its **second** purge pass and finds no authorization;
3. the issuer's authorization write lands **after** that second purge;
4. `deleteAccount` returns success;
5. the issuer crashes (or its post-write revalidation, compensating delete or read-back never
   executes, times out or is skipped for any reason).

The authorization then survives until its 30-day TTL expiry. Steps 2–3 have no atomicity between
them: a purge pass is a read-then-delete on Firestore, the issuer's write is an independent
Firestore create, and no Firestore operation on one document serializes with a create on another.
A post-write check is not atomic with either the deletion or the authorization creation, so it can
only shrink the window, never eliminate it. Any claim that "the second pass runs last, so the
record is caught" is a statement about the happy ordering, not a proof; the crash in step 5 is
always available to the interleaving.

Option B has the analogous race with the roles of the steps exchanged: the issuer's eligibility
read can succeed before the account is disabled, and its write can land after the deletion flow has
completed its purge; the disabled marker then no longer matters, because the write has already
happened and the record is never revisited.

The earlier draft of this ADR recommended the second-purge-pass option as satisfying the erasure
invariant. That recommendation was **unsound**: it asserted P2 while the mechanism delivers at
most P1. The owner's review rejected it, and this ADR is reworked so that no option is described as
providing P2 without a complete crash-safe serialization proof. A mechanism that cannot provide P2
can still be chosen — but then the choice is to accept a bounded window (P1 with a named cleanup
bound), and the residual risk MUST be recorded in `docs/SECURITY.md`.

## Options Considered

All three options change the deletion flow or the store; none is free. The table states, for each,
which property it can deliver and what it cannot.

| Option | Mechanism | Property it can deliver | What it cannot deliver |
|--------|-----------|--------------------------|------------------------|
| **A. Second purge pass after Auth deletion** | `§11.5` runs the authorization purge a second time after the Auth user is deleted. | P1 in the no-crash interleavings the second pass actually observes; it compensates writes that landed before it ran. | **Not P2.** The owner's counterexample above: the issuer writes after the second purge and crashes; nothing revisits the record. The pass is a convergence argument, not a serialization point. |
| **B. Disable the Auth user as the first deletion stage** | `§11.5` disables the account before deleting data; `D-148` rejects a disabled record, so most racing issuances are refused at the eligibility read. | Refusal of most racing issuances, using the account's own state as the marker. | **Not P2, and not even P1 alone.** The issuer's eligibility read can precede the disable and its write land after the flow's purge; the record is then never revisited, so this option needs the same second purge pass as option A to converge, plus it adds a user-visible lockout when a deletion fails midway. |
| **C. Internal `deletedUids` marker collection read inside the issuance transaction** | A server-only marker is written by the deletion flow before the purge; the issuer reads the marker inside the same Firestore transaction as its authorization write and refuses if present. | **P2, by construction.** The transaction is a real serialization point: the marker write and the authorization create conflict on Firestore's transaction ordering, so no crash interleaving leaves a UID-bound authorization after success, provided the marker is written before the purge and never expires. | The heaviest option: a new internal server-only collection, a `§16` internal-registry entry, the parity test, a retention rule for markers (they MUST outlive any token lifetime, so they accumulate), and an extra transaction on every issuance. |

No option is recommended as satisfying P2 here. Only option C can provide P2 as stated; whether the
project wants P2 at that cost, or accepts a bounded window under option A or B with the residual
risk recorded, is the owner's trade to make. The issuer's post-write read-back — never returning a
ticket whose authorization was concurrently removed — remains cheap and uncontroversial under every
option, but it is a courtesy to the caller, not a component of either P1 or P2: a read-back that
happens before a later racing write proves nothing about that later write.

## Decision

Not yet taken. This ADR presents the corrected options and the owner selects one. It does not
select one on the owner's behalf, and it does not present a default.

Whichever option is accepted, `E3-15` MUST discharge the proof obligations below before the story
can close.

## Proof obligations for the accepted option

Any accepted option MUST come with a complete crash-safe argument, not a convergence argument. For
options A and B, that argument can only justify P1 with a named cleanup bound (the existing 30-day
TTL, or a shorter one if the owner directs); the handoff and `docs/SECURITY.md` MUST then state the
residual risk explicitly. For option C, the argument MUST establish P2.

Concretely, `E3-15` MUST, on the real Firestore emulator (plus the Auth emulator if the accepted
option touches Auth state):

- enumerate every interleaving of the issuer's steps and the deletion flow's steps, including every
  crash point between two consecutive steps of either flow, and show, for each, the property that
  holds and the mechanism that delivers it;
- for option C, demonstrate the transaction conflict itself — a marker write concurrent with an
  authorization-creating transaction aborts or re-reads the transaction — rather than asserting it;
- prove the accepted option's bound: for P1 claims, the maximum time a record can outlive a
  successful deletion and the mechanism that removes it; for P2 claims, that no interleaving leaves
  a record;
- keep consumption, completion-last semantics and idempotent retries intact;
- introduce no client-selected UID, no JWT verification fallback and no weaker authorization path.

## Consequences

### Positive

- The options presented to the owner are technically sound: each states what it delivers, what it
  cannot, and what accepting it obliges the story to prove.

### Negative

- Every option changes the normative deletion order of `§11.5` or adds a store, so `E3-10` and
  `E3-11` tests that pin that order change with it.
- Until the decision is taken, the erasure guarantee remains conditional, bounded by the 30-day TTL.

### Constraints Introduced

- Until this decision is accepted and `E3-15` ships, the residual risk stands and MUST be recorded
  as an accepted residual risk in `docs/SECURITY.md` if the owner chooses to defer.
- No implementation under this decision may claim P2 without the crash-safe serialization proof of
  the **Proof obligations** section.

## Verification

- To be defined by the accepted option, per the proof obligations above. In every case `E3-15`
  MUST prove the interleavings against the real Firestore emulator, and MUST state which property —
  P1 with its bound, or P2 — its evidence establishes.

## References

- `docs/CONTRACTS.md` §11.5
- `docs/adr/0149-verify-the-issuing-account-through-the-admin-sdk.md` (`D-148`)
- `docs/adr/0144-erase-orphan-cleanup-authorizations-on-account-deletion.md` (`D-143`)
- `docs/BACKLOG.md` (`E3-15`)