# ADR-0150 / D-149 - Close the Ticket Issuance and Account Deletion Race

## Status

Pending

No option is recommended here. This ADR presents the trade-off and its proof obligations; the
selection is the owner's, and `E3-15` MUST NOT start until this decision is `Accepted`.

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

## What the existing TTL does and does not provide

`orphanCleanupTickets` records carry `expiresAt`, a server-generated timestamp 30 days after
issuance, with a Firestore TTL policy enabled on that field (`D-141`). This ADR states the
semantics of that mechanism precisely, because the earlier draft of this ADR — and several
documents that mirrored it — described the residual risk as "bounded by the 30-day TTL", which
overstates what Firestore TTL guarantees.

**Firestore TTL is not a hard deletion bound.** At `expiresAt` a document becomes *eligible* for
asynchronous deletion. Firebase's documentation states that expired documents may still be
returned by reads and queries after their expiration time, and that deletion typically happens
within 24 hours of expiration; that figure is a documented typical latency, not a guaranteed maximum and not an SLA.

Therefore, for this project's purposes:

- the current TTL supplies **provider-managed eventual cleanup after a 30-day expiration horizon,
  with an asynchronous and non-hard-bounded deletion delay**;
- the TTL MUST NOT be cited as proof of the maximum time a record can survive, and no option may
  claim a bounded residual window on the strength of the existing TTL alone;
- if `D-149` is to be answered with a **provable maximum retention period**, `E3-15` needs an
  additional deterministic cleanup mechanism, selected by the owner. This ADR neither designs nor
  implements such a mechanism, and the choice of one is part of the decision, not a consequence of
  it.

Everything below uses "eventual cleanup" in this exact sense. Where an option is described as
converging, the convergence it inherits from the existing TTL is provider-managed and
non-hard-bounded.

## Two properties that must not be confused

Any candidate mechanism MUST be assessed against one of two distinct properties, and this ADR
deliberately does not treat them as equivalent:

- **P1, eventual convergence:** after any interleaving of issuance and deletion, and after all
  retries settle, no UID-bound authorization record for the deleted UID remains, even if one was
  observable for a while. This is a liveness-plus-cleanup property. A compensating delete, a later
  purge pass, or a deterministic scheduled cleanup can provide it with a stated bound; the existing
  Firestore TTL provides only the non-hard-bounded eventual form described above.
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

The authorization then survives past the return of success, and nothing in the flow revisits it: it
is removed only when the provider's asynchronous TTL cleanup eventually reaches it, at an
unbounded delay after the 30-day expiration horizon. Steps 2–3 have no atomicity between them: a
purge pass is a read-then-delete on Firestore, the issuer's write is an independent Firestore
create, and no Firestore operation on one document serializes with a create on another. A
post-write check is not atomic with either the deletion or the authorization creation, so it can
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
can still be chosen — but then the choice is to accept a residual window whose cleanup, with the
mechanisms that exist today, is eventual and not hard-bounded, and the residual risk MUST be
recorded in `docs/SECURITY.md`.

## Options Considered

All three options change the deletion flow or the store; none is free. The table states, for each,
which property it can deliver and what it cannot. **Which property comes from the option itself,
and which comes from the pre-existing TTL fallback, is stated separately for each row**, because
conflating the two is exactly the error the first draft made.

| Option | Mechanism | Property delivered by the option itself | Convergence inherited from the pre-existing TTL | What it cannot deliver |
|--------|-----------|------------------------------------------|--------------------------------------------------|------------------------|
| **A. Second purge pass after Auth deletion** | `§11.5` runs the authorization purge a second time after the Auth user is deleted. | **Partial P1 only.** The option itself removes exactly those authorizations that had already landed when the second pass ran; that removal is synchronous with the deletion flow and needs no TTL. | For any write that lands after the second pass, the option contributes nothing further; the record is removed only by the pre-existing provider-managed TTL cleanup, eventually and without a hard bound. | **Not P2**, and not full P1 with a proven bound. The owner's counterexample: the issuer writes after the second purge and crashes; nothing in the option revisits the record. The pass is a convergence argument over one ordering, not a serialization point. |
| **B. Disable the Auth user as the first deletion stage** | `§11.5` disables the account before deleting data; `D-148` rejects a disabled record, so most racing issuances are refused at the eligibility read. | **Neither P1 nor P2 — a probability reduction only.** The option itself refuses those issuances whose eligibility read happens after the disable, which is a real and useful narrowing of the window, but it removes no record that has already been written and adds no cleanup step at all. | Every record written by an issuance whose eligibility read preceded the disable survives the flow entirely; the only thing that removes it is the pre-existing provider-managed TTL cleanup, eventually and without a hard bound. | **Not P2, and no convergence of its own.** To reach even partial P1 it must be combined with option A's second purge pass. It also adds a user-visible lockout when a deletion fails midway. |
| **C. Internal `deletedUids` marker collection read inside the issuance transaction** | A server-only marker is written by the deletion flow before the purge; the issuer reads the marker inside the same Firestore transaction as its authorization write and refuses if present. | **P2 for the interleaving, by construction — conditional on an unresolved retention design.** The transaction is a real serialization point: the marker write and the authorization create conflict on Firestore's transaction ordering, so no crash interleaving leaves a UID-bound authorization after success, *provided the marker is written before the purge and is still present when a racing issuance runs*. | None needed for the authorizations themselves. | **Not a clean solution as currently drafted.** The marker lifetime that makes the serialization argument work is itself unresolved and conflicts with `D-143`; see the next section. It is also the heaviest option: a new internal server-only collection, a `§16` internal-registry entry, the parity test, and an extra transaction on every issuance. |

No option is recommended. Only option C can provide P2 as stated, and it can only do so once its
marker retention problem is answered; whether the project wants P2 at that cost, or accepts a
residual window under option A or B with the residual risk recorded, is the owner's trade to make.
The issuer's post-write read-back — never returning a ticket whose authorization was concurrently
removed — remains cheap and uncontroversial under every option, but it is a courtesy to the caller,
not a component of either P1 or P2: a read-back that happens before a later racing write proves
nothing about that later write.

## Option C is not an unqualified solution: the marker retention problem

The first draft of this ADR stated that the `deletedUids` marker "MUST outlive any token lifetime,
so markers accumulate" and treated indefinite accumulation as a mere cost. That is not a cost, it
is a second unresolved problem, and it must be stated separately from the serialization property.
Two facts about option C are independent and MUST NOT be collapsed:

1. **Its Firestore transaction may provide the required serialization point.** Reading a marker
   document inside the same transaction that creates the authorization makes the two operations
   conflict under Firestore's transaction ordering, which is the property no pair of independent
   reads and writes across Firestore and Firebase Auth can supply. This part of the argument stands
   on its own and is what makes option C the only P2 candidate.
2. **Its proposed never-expiring marker creates a separate indefinite UID-linked retention
   problem.** A marker that must never expire necessarily retains a stable UID, or a key from which
   the UID can be derived or against which a candidate UID can be tested, for every account ever
   deleted, forever. That directly conflicts with `D-143` (ADR-0144), whose accepted rationale is
   that retaining a deleted account's identifier in a server-only collection violates this
   project's account-erasure expectation — the very rationale that made the deletion-time purge
   normative. Option C as drafted would therefore fix the interleaving by reintroducing, in a
   different collection and permanently, the class of retention that `D-143` was accepted to
   remove.

Option C therefore MUST NOT be described as a clean P2 solution. It is a candidate whose
serialization half is sound and whose retention half is unresolved, and the retention half MUST be
resolved before the option can satisfy the project's broader erasure posture.

### What a valid serialization design would have to prove

This section enumerates obligations. It does not select a design, does not invent a retention
policy, and does not decide `D-149`; naming a safety horizon or an alternative representation is
the owner's decision, informed by whatever analysis `E3-15` is directed to perform.

A design that keeps a UID-linked marker with a **finite lifetime** would have to establish:

- a **justified safety horizon**: an upper bound on how long after the deletion flow writes its
  marker a racing issuance can still reach the issuance transaction. That bound must cover every
  already-issued credential or token that could still authenticate such an issuance — including the
  Firebase ID-token lifetime and any refresh-token-derived issuance path — and every in-flight
  callable execution, including the configured callable timeout and any platform retry of it;
- **clock and scheduling slack**: the horizon must absorb clock skew between the deletion flow, the
  issuer and Firestore's own timestamps, plus retry and backoff behaviour that can re-execute a
  request well after its first attempt;
- **what happens at the boundary**: an argument for why an issuance arriving after the horizon
  cannot produce a UID-bound authorization for the deleted UID by some other path, given that the
  marker is gone by then;
- **the deletion of the marker itself**: by what mechanism markers are removed at the end of the
  horizon, and — per the TTL section above — that mechanism's actual guarantee. A Firestore TTL
  policy on the marker collection would give the marker the same non-hard-bounded eventual cleanup,
  which is acceptable for a marker that is allowed to outlive its horizon but is not by itself a
  proof that it is ever removed at a stated time.

A design that avoids storing a UID-linked value would instead have to prove its own properties,
for example that its representation is not correlatable back to a UID by a party holding candidate
UIDs, that it still serializes correctly under Firestore transactions, and that any collision or
false-positive behaviour is safe (a false positive refuses a legitimate issuance; a false negative
breaks P2). No such representation is proposed, evaluated or accepted here.

Whichever direction is taken, the outcome must be reconciled with `D-143` explicitly: either the
retained value is shown not to be an account identifier in the sense `D-143` forbids, or `D-143` is
amended by a new decision that the owner takes deliberately.

## Decision

Not yet taken. This ADR presents the corrected options and the owner selects one. It does not
select one on the owner's behalf, it does not present a default, and it offers no recommendation —
which is why this decision's status is `Pending` rather than `Proposed`.

Whichever option is accepted, `E3-15` MUST discharge the proof obligations below before the story
can close.

## Proof obligations for the accepted option

Any accepted option MUST come with a complete crash-safe argument, not a convergence argument. For
options A and B, that argument can only justify P1, and — with the mechanisms that exist today —
only the non-hard-bounded eventual form of it; the handoff and `docs/SECURITY.md` MUST then state
the residual risk explicitly, including that no maximum retention time is proven. For option C, the
argument MUST establish P2 **and** resolve the marker retention problem above.

Concretely, `E3-15` MUST, on the real Firestore emulator (plus the Auth emulator if the accepted
option touches Auth state):

- enumerate every interleaving of the issuer's steps and the deletion flow's steps, including every
  crash point between two consecutive steps of either flow, and show, for each, the property that
  holds and the mechanism that delivers it, distinguishing what the accepted option delivers from
  what the pre-existing TTL fallback delivers;
- for option C, demonstrate the transaction conflict itself — a marker write concurrent with an
  authorization-creating transaction aborts or re-reads the transaction — rather than asserting it,
  and state and justify the marker's retention rule against `D-143`;
- state the accepted option's actual guarantee: for P1 claims, which records the option itself
  removes and when, and for every record it does not remove, that its cleanup is the provider's
  asynchronous TTL and therefore has no proven maximum. **A provable maximum retention period MAY
  NOT be claimed from the existing TTL**; if the owner requires one, the accepted option MUST
  include an additional deterministic cleanup mechanism, which is itself part of the owner's
  decision and is not designed here. For P2 claims, prove that no interleaving leaves a record;
- keep consumption, completion-last semantics and idempotent retries intact;
- introduce no client-selected UID, no JWT verification fallback and no weaker authorization path.

## Consequences

### Positive

- The options presented to the owner are technically sound: each states what it delivers, what
  comes only from the pre-existing TTL fallback, what it cannot deliver, and what accepting it
  obliges the story to prove.

### Negative

- Every option changes the normative deletion order of `§11.5` or adds a store, so `E3-10` and
  `E3-11` tests that pin that order change with it.
- Until the decision is taken, the erasure guarantee remains conditional, and the record that
  survives the interleaving is removed only by provider-managed asynchronous TTL cleanup after its
  30-day expiration horizon, with no proven maximum.

### Constraints Introduced

- Until this decision is accepted and `E3-15` ships, the residual risk stands and MUST be recorded
  as an accepted residual risk in `docs/SECURITY.md` if the owner chooses to defer.
- No implementation under this decision may claim P2 without the crash-safe serialization proof of
  the **Proof obligations** section.
- No document may describe the residual window as bounded by the 30-day TTL, or cite that TTL as
  the maximum time a record can survive.

## Verification

- To be defined by the accepted option, per the proof obligations above. In every case `E3-15`
  MUST prove the interleavings against the real Firestore emulator, and MUST state which property —
  P1 with what the option itself guarantees, or P2 — its evidence establishes.

## References

- `docs/CONTRACTS.md` §11.5
- `docs/adr/0149-verify-the-issuing-account-through-the-admin-sdk.md` (`D-148`)
- `docs/adr/0144-erase-orphan-cleanup-authorizations-on-account-deletion.md` (`D-143`)
- `docs/adr/0142-use-server-issued-orphan-cleanup-tickets.md` (`D-141`, the TTL policy)
- `docs/BACKLOG.md` (`E3-15`)
