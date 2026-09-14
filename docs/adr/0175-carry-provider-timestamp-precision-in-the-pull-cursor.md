# ADR-0175 / D-174 - Carry Provider Timestamp Precision in the Pull Cursor

## Status

Accepted

The owner selected option A with an in-memory scope on 2026-09-13 during the eighth `E3-03` review
round.

## Context

`docs/CONTRACTS.md §9.4` requires later pages to advance with
`startAfter(pageCursor.lastServerUpdatedAt, pageCursor.lastDocumentId)` and claims that "the complete
later-page cursor advances every timestamp cluster distinguishable at millisecond resolution".

Two facts make that claim false as implemented:

1. `EntitySnapshot.toFirestoreWrite` maps `updatedAt` to `FirestoreServerTimestamp`, so Firestore
   stores a server timestamp at microsecond precision.
2. The integration truncated it with `Instant.fromEpochMilliseconds(timestamp.toMilliseconds())`, so
   `RemoteDocument.serverUpdatedAt`, `RemoteCursor.lastServerUpdatedAt` and the persisted
   `sync_cursor.lastServerUpdatedAt` all carried a millisecond value.

Firestore orders by the stored `(updatedAt, __name__)` pair. The last document of a page has a true
`updatedAt` of `truncatedMs + fraction`, which sorts strictly after the boundary
`(truncatedMs, documentId)`. That document is re-delivered.

Two reachable failures follow:

- When the change set remaining after the stored cursor is an exact multiple of the page limit,
  page N+1 returns **only** that repeated document, so its `nextCursor` equals the request cursor and
  `DefaultSyncController.failProgressInvariant` fires `SyncError.ConflictUnresolved` — with no
  document actually stranded.
- Two documents inside the same millisecond that straddle a page boundary trip the same false
  failure.

This is not the shape D-169 accepts. `§9.4` fails closed only for a cluster **larger than the page
limit**, not for a single millisecond-distinguishable document at the end of the collection. The
suite stayed green because the controller fake modelled `startAfter` as index-exclusive on the
document id alone, a semantics the production query does not have.

### Scope decision: in-memory precision

Option A ("preserve provider precision end to end") was investigated. Carrying microseconds into the
persisted `sync_cursor` requires migrating the existing millisecond rows, and this repository's
SQLDelight configuration does not let an `.sqm` reference a `schema.sq` table: only `CREATE TABLE` of
new tables works, as the three existing migrations do. A persisted-unit change would therefore
require changing the schema tooling (an `deriveSchemaFromMigrations` switch, a reference `.db`, or a
post-migration code step) plus a migration test, touching the gated `core/database/**` path.

The defect, however, is intra-cycle: the engine's `RemoteCursor` is already an `Instant` and already
carries whatever precision the integration delivers. Truncation happened only at the integration
boundary. Preserving precision **in memory** fixes every reachable failure without a schema or tooling
change.

## Decision

**Option A, in-memory scope.** The integration delivers the provider's full-precision ordering
timestamp into `RemoteDocument.serverUpdatedAt`; the engine's in-cycle cursor carries it untruncated
and rebuilds the later-page `startAfter` boundary from it. The persisted `sync_cursor` anchor stays an
epoch-millisecond cycle anchor: the `§9.4` 30-second overlap re-includes anything at or after it, so a
cross-cycle truncation cannot skip a document.

## Consequences

### Positive

- Later-page `startAfter` is exclusive again, so no document is re-delivered and no false
  `ConflictUnresolved` is raised.
- No local-schema change, no data migration and no `core/database/**` tooling change.
- The whole-document LWW comparison of `§9.6` stays epoch milliseconds, so it needs no change.

### Negative

- The ordering timestamp has two representations: full precision in memory and milliseconds in
  `sync_cursor`. The overlap window is what makes the persisted one sufficient, and `§9.4` states it.

### Constraints Introduced

- `RemoteDocument.serverUpdatedAt` and `RemoteCursor.lastServerUpdatedAt` MUST carry the provider's
  ordering precision; the integration MUST NOT truncate it.
- The persisted `sync_cursor` MAY stay epoch milliseconds because the overlap re-includes its
  timestamp; removing the overlap requires revisiting this decision.
- `§9.6` conflict arbitration stays epoch milliseconds and MUST NOT be changed by this decision.
- A fake that models `startAfter` as index-exclusive MUST NOT be used to prove pagination behaviour.

## Verification

- `DefaultSyncControllerTest` covers a change set of exactly one page and two same-millisecond
  documents straddling a boundary, each asserting `Ok`, no `onPoisoned`, a non-`Failed` status and an
  advanced cursor. Both fail when the fake truncates delivery.
- `FirebaseRemoteSyncSourceTest` asserts the full-precision cursor reached `FirestoreQuery` and that
  the later-page boundary preserves the microsecond value.
- `SyncDatabaseAccessTest` asserts the stored cursor never moves backwards.

## References

- `docs/CONTRACTS.md §9.3`, `§9.4`, `§9.6`, `§20.7`
- `docs/SPECIFICATION.md §8`, `§12`
- `docs/TECHNICAL_PLAN.md §2`, `§9`
- `docs/BACKLOG.md` (`E3-03`)
- ADR-0170 / D-169, `docs/handoff-E3-03.md`
