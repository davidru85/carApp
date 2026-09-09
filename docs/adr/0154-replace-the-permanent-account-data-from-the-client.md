# ADR-0154 / D-153 - Replace the Permanent Account Data From the Client

## Status

Accepted

## Context

Step 3 of the confirmed collision flow in `docs/CONTRACTS.md §11.3` replaces the permanent account's
remote `vehicles` and `fuelEntries` data with the snapshot captured from the anonymous session. The
owner has explicitly confirmed `Confirmation.AdoptExistingAccount` after being told the existing
data will be replaced, so the destructive intent is settled; how the replacement is performed is not.

The replacement is not a push of local changes. It must also remove permanent-account documents that
the anonymous snapshot does not contain, including documents the anonymous device has never seen,
and it must be idempotent and resumable, because it runs after the session switch and may be
interrupted at any point.

Two project rules bound the design. `docs/SPECIFICATION.md §3.2` excludes automatic account merging
from the MVP, and it limits Cloud Functions-mediated product read/write validation to the `D-23` and
`D-63` account identity and data-deletion operations. Client hard deletes are rejected by the
Firestore rules, so removal is expressed as tombstones.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. The client replaces: push every captured snapshot, then tombstone every remaining remote document | Uses the durable snapshot the client already holds; every write is an idempotent full-document put, so a replay converges; no new server surface; the existing Firestore rules and tombstone semantics apply unchanged | The client must page through the permanent owner's remote documents to find what to tombstone; the work is proportional to both data sets and is not atomic, so an interruption leaves a partially replaced account until the replay finishes |
| B. A new server-side replacement operation beside the D-23 and D-63 admin operations | Could be atomic on the server; the client would issue one call | Widens exactly the Cloud Functions product-write surface that `docs/SPECIFICATION.md §3.2` limits to identity and deletion; needs a new callable, new authorization, new rules review and a new owner decision; the server has no access to the device-local snapshot, so the client would have to upload it anyway |
| C. Merge semantics that reconcile both data sets | No data is lost from either account | Automatic account merging is explicitly out of MVP scope in `docs/SPECIFICATION.md §3.2`; it also contradicts the confirmation text the owner just accepted |

## Decision

The selected option is **A**. After the session switch the client pulls the permanent owner's
existing `VEHICLE` and `FUEL_ENTRY` documents, then writes, in this order:

1. captured live vehicles;
2. captured live fuel entries;
3. captured fuel-entry tombstones;
4. tombstones for the permanent account's remaining fuel entries;
5. captured vehicle tombstones;
6. tombstones for the permanent account's remaining vehicles.

Every captured payload is rewritten with the permanent `ownerId` before it is pushed, and each
acknowledgement is recorded on its own `account_conversion_snapshot` row, so a replay resumes rather
than restarts. Only when the whole ordering completes does `phase` advance to `REMOTE_REPLACED`.

## Consequences

### Positive

- No new server operation, no new callable authorization and no rules change.
- The ordering is dependency-driven in both directions: creations write vehicles before fuel
  entries, deletions remove fuel entries before vehicles, so no fuel entry is ever left referencing
  a vehicle that is already a tombstone.
- Replay converges because each write is a full-document put keyed by entity id.

### Negative

- The replacement is not atomic. Between an interruption and its replay the permanent account holds
  a mixture of both data sets, visible to any other device signed into that account.
- The pull is proportional to the permanent account's data, which is unbounded from the client's
  point of view and is paged.

### Constraints Introduced

- Replacement MUST NOT merge. A permanent-account document absent from the captured snapshot is
  tombstoned, never kept.
- Removal MUST be expressed as tombstones; client hard deletes stay rejected by the Firestore rules.
- Normal recovery pull MUST NOT re-enter while the marker exists, or it would reintroduce the rows
  the replacement is removing.
- The MVP one-active-device rule bounds the visibility of the non-atomic window; a story that
  relaxes it MUST revisit this decision.

## Verification

- `AccountConversionCoordinatorTest` asserts the push ordering, tombstoning of documents absent from
  the snapshot, per-row acknowledgement, and convergence when the operation is replayed from every
  post-confirmation boundary.
- `FirebaseRemoteSyncSourceTest` closes Fuel Entry snapshot decoding, which the replacement relies on
  to read the permanent account's existing documents.
- `contractCheck` and `architectureCheck` keep the remote schema and module boundaries intact.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-153`)
- `docs/SPECIFICATION.md §3.2`, `§7 F-4`
- `docs/CONTRACTS.md §11.3`, `§16`, `§20.2`
- `docs/TECHNICAL_PLAN.md §2`, `§5`
- `docs/handoff-E2-04.md`
