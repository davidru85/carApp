# ADR-0170 / D-169 - Pull-Cursor Sub-Millisecond Truncation

## Status

Accepted

The owner selected option A on 2026-09-13 before `E3-03` started.

**Superseded in part by ADR-0175 / D-174.** D-169 still governs the persisted epoch-millisecond
anchor and the fail-closed rule at the active cursor's precision. D-174 governs the in-cycle
provider-precision boundary.

## Context

`docs/CONTRACTS.md §9.4` requires later pages to advance with
`startAfter(pageCursor.lastServerUpdatedAt, pageCursor.lastDocumentId)`. The persisted
`sync_cursor.lastServerUpdatedAt` anchor and conflict arbitration use epoch milliseconds, while the
in-cycle `RemoteCursor.lastServerUpdatedAt` is an `Instant` capable of carrying provider precision.

When D-169 was accepted, the integration converted Firestore server timestamps to milliseconds, so
the later-page boundary could sit below the document's real provider timestamp. ADR-0175 subsequently
removed that in-cycle truncation while deliberately retaining the persisted millisecond anchor.

Later-page query:

```text
where updatedAt >= truncated(last.updatedAt)
orderBy updatedAt ASC, documentId ASC
startAfter(truncated(last.updatedAt), last.documentId)
```

Two consequences follow.

### No data loss

The filter is `updatedAt >= boundary` and the boundary is truncated **downwards**, so
`boundary <= real`. The predicate is monotone in the boundary: lowering it can only add documents,
never remove one. Every document the untruncated query would return at `updatedAt >= real` is still
returned at `updatedAt >= truncated`. Therefore the truncation cannot skip a document: there is no
data loss. This conclusion is independent of the pagination defect below.

### Duplicate and non-advancing pages

Because every document in the cluster has a real timestamp strictly greater than the truncated
boundary, the first ordering component already sorts each of them after `(truncated, last.documentId)`
— the document-ID tie-break never applies against a boundary that is below the real timestamp. The
last document of the page is therefore re-delivered on the next page. That duplication is benign:
`docs/CONTRACTS.md §9.4` makes apply idempotent, and the 30-second overlap already implies
duplicates.

The defect is narrower and hard: if a full 200-document page falls inside a single truncated
millisecond, the next page returns the **same** 200 documents and produces the **same** cursor
`(truncated, last.documentId)`. The page cursor does not advance, and the `E3-03` engine's `§9.4`
progress invariant fires `SyncError.ConflictUnresolved` and fails the cycle. That is exactly the
scenario `§9.4` claims to prevent.

The failure requires more than the page limit of documents whose provider timestamps collapse into
one millisecond. The single-owner, foreground MVP cannot generate such a cluster through ordinary
use: writes are discrete user actions and recovery timestamps are spread across their original write
times.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Keep the persisted millisecond anchor and fail closed when the cursor cannot advance | Preserves the local schema and epoch-millisecond LWW rule while making non-progress terminate deterministically instead of looping. | A full page whose ordering keys are not distinguishable at the active cursor's precision can strand the pull until the cursor row is deleted or the data shape changes. |
| B. Persist provider precision and remove the bound | Makes the persisted and in-cycle cursor equally precise. | Requires a persisted-unit migration and a schema-tooling change; it does not remove the need to fail closed against a malformed or non-advancing provider cursor. |

## Decision

**Option A for the persisted anchor and progress invariant.** Keep `sync_cursor` in epoch
milliseconds and fail the cycle with `SyncError.ConflictUnresolved` when a full page cannot advance
at the active cursor's precision. ADR-0175 supersedes this decision only for the in-cycle boundary:
`RemoteCursor` carries Firestore's provider microseconds between pages.

## Consequences

### Positive

- The contract bounds progress to the active cursor's precision, so a later story cannot assume
  progress when the provider supplies a non-advancing ordering key.
- No persisted representation change is required, and the millisecond-Long LWW rule stays coherent.

### Negative

- A cluster larger than the page limit whose ordering keys are identical at the active cursor's
  precision can still strand the pull cursor.

### Constraints Introduced

- The persisted `sync_cursor` anchor remains epoch milliseconds unless a later accepted decision
  supplies the schema migration and tooling change.
- No document may claim the `§9.4` progress guarantee for a timestamp cluster that is not
  distinguishable at the active cursor's precision.
- ADR-0175 requires provider microseconds in the in-cycle `RemoteCursor`; this does not change the
  persisted anchor or the `§9.6` millisecond LWW comparison.

## Verification

- `SyncDatabaseAccessTest` verifies that the persisted millisecond anchor advances monotonically;
  the 30-second overlap makes its downward truncation safe across cycles.
- `DefaultSyncControllerTest` verifies the fail-closed non-progress invariant at the active cursor's
  precision, while ADR-0175's cases verify the provider-microsecond later-page boundary.

## References

- `docs/CONTRACTS.md §9.4`, `§9.6`, `§20.7`
- `docs/SPECIFICATION.md §9.4`
- `docs/TECHNICAL_PLAN.md §8`
- `docs/BACKLOG.md` (`E3-03`)
- `docs/handoff-E3-02.md`
