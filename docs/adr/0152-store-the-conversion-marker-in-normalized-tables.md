# ADR-0152 / D-151 - Store the Conversion Marker in Normalized Tables

## Status

Accepted

## Context

`docs/CONTRACTS.md §11.3` orders the confirmed anonymous-account collision flow as five steps that
span a Firebase session switch: capture the anonymous snapshot, obtain and persist the E3-11 cleanup
ticket, switch to the permanent account, replace its remote data, rebuild the local rows, and delete
the orphaned anonymous account. The process is destructive and crosses a boundary the app does not
control — the owner can kill the app, the device can lose power, and the session switch itself
invalidates the identity that produced the data being replaced.

The flow is therefore only correct if it is durable and replayable: after any interruption the app
MUST converge on the same permanent-account snapshot and the same deleted anonymous identity, and it
MUST NOT re-enter normal recovery pull while the marker exists. That requires persisting, before the
session switch, both the captured rows and enough process state to know which step to resume from.

What shape that persistence takes is the decision. The marker is not a product entity: it is
device-local, never synchronized, never enqueued in the outbox and absent from the closed remote
schema of `docs/CONTRACTS.md §16`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Two normalized tables — a single-row operation table plus a per-entity snapshot table | Each captured entity is addressable, so a remote acknowledgement can be recorded per row as it arrives; `CHECK` constraints make `phase` and `entityType` closed sets enforced by the database; the resume point is one indexed read | Two tables and one migration to maintain; the schema encodes a process, which is unusual for this database |
| B. One opaque serialized marker row (JSON blob) | One table, one row, no per-entity schema; the payload shape can change without a migration | The whole blob is rewritten on every acknowledgement, so an N-entity replacement performs N full-marker writes; per-row `remoteServerUpdatedAt` cannot be recorded without parsing and re-serializing; `phase` and `entityType` become untyped strings no constraint can close; a partially written blob is unreadable rather than partially usable |
| C. Reuse the existing outbox to carry the captured snapshot | No new tables at all; the outbox already knows how to retry a pending write | Mixes a one-off destructive replacement with normal synchronization, so outbox retry, backoff and ordering semantics would apply to an operation that must not follow them; the outbox is owner-scoped and the operation deliberately spans two owners; a bug in either mechanism would now corrupt the other |

## Decision

The selected option is **A**. Schema v3 adds two device-local tables through the additive `2.sqm`
migration:

- `account_conversion_operation`, pinned to `id = 0` by a `CHECK` constraint so at most one
  conversion can be in flight, holding `anonymousUid`, the nullable `permanentUid`, the nullable
  `cleanupTicket`, and a `phase` restricted to `SNAPSHOT_CAPTURED`, `SESSION_SWITCHED`,
  `REMOTE_REPLACED` and `LOCAL_REPLACED`;
- `account_conversion_snapshot`, keyed by `(entityType, entityId)`, holding the captured `payload`,
  its `localRevision`, its `localMutationSeq` and the nullable `remoteServerUpdatedAt`.

`phase` is the resume point and advances only after the work it names has completed, so a replay
repeats at most one already-completed step and never skips one.

## Consequences

### Positive

- The replacement is resumable at entity granularity, not only at step granularity.
- An unknown phase or entity type is a database error, not a silently unhandled branch.
- The captured payload survives independently of the session that produced it, which is what makes
  step 4 able to rebuild local rows for an owner that no longer exists.

### Negative

- The local schema now contains process state as well as product state.
- A future change to the captured payload shape needs a migration rather than a serializer change.

### Constraints Introduced

- The two tables MUST stay device-local: never synchronized, never enqueued in the outbox, and
  absent from the closed remote schema of `docs/CONTRACTS.md §16`.
- `verifyMigrations` stays enabled and destructive schema recreation stays forbidden, so every later
  schema change carries these tables forward.
- The marker MUST be cleared only after the step 5 orphan deletion returns, which is what makes
  step 5 the completion boundary rather than the local rebuild of step 4.

## Verification

- `2.sqm` is an additive migration and `verifyMigrations` runs in CI.
- `SchemaV1Test` and `AnonymousReminderMigrationTest` assert the schema version and table list, and
  a populated v2-to-v3 migration test proves existing product rows survive the upgrade.
- `AccountConversionDatabaseAccessTest` covers atomic capture, the durability of the ticket and the
  snapshot until the operation is cleared, and local replacement without merging.
- `AccountConversionCoordinatorTest` replays the operation from every post-confirmation boundary.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-151`)
- `docs/SPECIFICATION.md §7 F-4`, `§12`
- `docs/CONTRACTS.md §11.3`, `§16`
- `docs/TECHNICAL_PLAN.md §2`, `§6`
- `docs/handoff-E2-04.md`
