# ADR-0144 / D-143 - Erase Orphan-Cleanup Authorizations on Account Deletion

## Status

Accepted

## Context

PR #60 introduced the root-level `orphanCleanupTickets` collection (D-141) but did not declare it
anywhere in the `docs/CONTRACTS.md` §16 remote schema, which states that the remote schema is
closed and that unknown collections are invalid. The D-63 `USER_DATA_LOCATIONS` deletion registry
consequently did not cover the collection, and the E3-10 `deleteAccount` operation left records
carrying `anonymousUid` in place for at least the 30-day `expiresAt` horizon after account
deletion, and then for however long the provider's asynchronous TTL cleanup took to reach them.

That retention conflicts with the account-erasure expectation of the deletion flow: a deleted
account's identifier would remain readable in a server-only collection until that eventual TTL
cleanup removed it, at no guaranteed time. The review also required an explicit, documented
treatment of internal server-only collections, so a future internal collection cannot be introduced
silently.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **Purge UID-bound authorizations during account deletion (accepted)** | Erasure of the authorizations the purge can see is owned by the deletion flow itself rather than by TTL cleanup; the 30-day TTL becomes only a fallback for abandoned or completed authorizations; no accepted-retention decision is needed. | Adds one paged query plus batch deletes to every account deletion; a new stage in the normative deletion order. |
| Accept TTL-only retention with an owner decision | No deletion-flow change. | A deleted account's UID remains stored after erasure, for the 30-day expiration horizon plus an asynchronous, non-hard-bounded provider cleanup delay; requires an explicit accepted-residual-risk entry and weakens the erasure posture. |
| Register the collection in D-63 `USER_DATA_LOCATIONS` | Reuses the existing registry. | The registry drives the `users/{uid}` recursive-delete traversal and the client-facing schema parity test; a root collection outside that subtree does not fit either contract. |

## Decision

Account deletion purges internal orphan-cleanup authorizations as a normative stage of the
deletion order in `docs/CONTRACTS.md` §11.5: after registered remote document deletion fully
succeeds, and before the Firebase Auth user is deleted, the server operation deletes every
`orphanCleanupTickets` record whose `anonymousUid` equals the target UID. The purge:

- is paged (batches of 200) and repeated until no matching records remain, so it is complete
  regardless of how many tickets the UID accumulated;
- is idempotent: an already-purged UID converges;
- is separately registered: a purge failure returns `internal`, logs the redacted `AUTHORIZATION`
  stage, and prevents Auth deletion, so the deletion flow aborts safely and retries.

**Scope of the guarantee.** The purge removes every matching authorization **that is visible to it
when it runs**. That is the whole of what this decision guarantees, and it is deliberately not a
zero-retention guarantee for the account as a whole: `D-149` (ADR-0150) documents a concurrent
issuance whose eligibility check passes before the purge and whose write lands after it, so one
UID-bound authorization can still exist when `deleteAccount` returns success. Closing that race is
outside this decision — it is owned by `D-149` and the story `E3-15`, and it requires a change to
the deletion order or a new store, not a change to this purge. Until `D-149` is decided and `E3-15`
ships, the zero-retention reading of this decision is **conditional**, and no document may state it
unconditionally.

Internal server-only collections are declared in a new "Internal server-only collections"
subsection of `docs/CONTRACTS.md` §16 with an executable registry
(`INTERNAL_SERVER_DATA_LOCATIONS` in `functions/src/deletion/dataLocationRegistry.ts`). The
registry states that the collection is excluded from the client-facing remote schema and from the
D-63 user-data deletion registry, with the reason: it is authorization lifecycle state, not
client-facing application data, and it lives outside the `users/{uid}` subtree. An internal
parity test compares the declaration with the registry, proves the two registries do not overlap,
and fails when a server-only collection is declared in the contract without a registry entry.

The 30-day TTL remains enabled as a fallback for abandoned or completed authorizations; it is no
longer the normal account-deletion retention path. That fallback is provider-managed **eventual**
cleanup: at `expiresAt` a record becomes eligible for asynchronous deletion, expired records may
still be queryable, and deletion typically follows within 24 hours of expiration without that being
a guaranteed maximum or an SLA. The TTL therefore MUST NOT be described as a hard 30-day deletion
bound, nor cited as proof of the maximum time a record can survive.

## Consequences

### Positive

- `anonymousUid` does not survive account deletion in any authorization the purge can see: for
   those records the erasure is owned by the deletion flow rather than by asynchronous TTL cleanup.
   This is **not** an unconditional guarantee that no `anonymousUid` survives — the ADR-0150
   interleaving can leave one authorization written after the purge has run, and that record is
   removed only by provider-managed asynchronous TTL cleanup, with no proven maximum. The
   unconditional guarantee is `D-149`'s to deliver, not this decision's.
- The client-facing remote schema stays closed while every server-only collection is explicit and
  executable.
- The D-63 registry keeps its exact `users/{uid}` meaning; the internal collection cannot leak
  into client contract tests or deletion traversals.

### Negative

- Account deletion performs one additional paged Firestore query and batch deletes.
- The normative deletion order gains a stage; the app-side flow description must keep its
  numbering in sync (`docs/CONTRACTS.md` §11.5).
- A purge failure blocks Auth deletion until it succeeds, matching the existing
  abort-and-retry posture but extending the failure window.

### Constraints Introduced

- The account-deletion operation MUST purge every internal authorization bound to the target UID
  after remote data deletion and before Auth deletion.
- The purge MUST be idempotent, paged and bounded to records whose `anonymousUid` equals the
  target UID.
- A purge failure MUST map to `internal` with the redacted `AUTHORIZATION` stage log and MUST NOT
  leak UIDs or raw failures.
- Every internal server-only collection MUST be declared in the CONTRACTS §16 internal registry
  and in `INTERNAL_SERVER_DATA_LOCATIONS`; the internal parity test MUST fail on an undeclared
  collection or a D-63 overlap.
- No document MAY state this decision's erasure guarantee unconditionally. Every restatement MUST
  scope it to the authorizations visible to the purge and MUST name `D-149` / `E3-15` as the owner
  of the remaining concurrent-issuance race.

## Verification

- `functions/test/accountDeletion.test.mjs` pins the purge ordering
  (`deleteCollection` stages, then purge, then Auth deletion), the typed `internal` purge failure
  that prevents Auth deletion, redacted `AUTHORIZATION` logging and gateway batch semantics.
- `functions/test/dataLocationRegistry.test.mjs` compares the internal registry with the
  contract declaration and proves no overlap with D-63 user-data locations and rejection of an
  undeclared internal collection.
- `functions/test/orphanedAnonymousAccountEmulator.test.mjs` proves the real Admin gateway purge
  deletes only the UID-bound authorizations in the Firestore emulator.

## References

- `docs/CONTRACTS.md` §11.5 (deletion order, registry exclusion), §16 (internal registry)
- `docs/adr/0142-use-server-issued-orphan-cleanup-tickets.md` (Negative consequences amended)
- `docs/adr/0130-use-firestore-recursive-delete-per-registered-collection.md` (D-129)
- `docs/adr/0150-close-the-ticket-issuance-and-account-deletion-race.md` (`D-149`, the concurrent
  issuance that keeps this decision's zero-retention reading conditional)