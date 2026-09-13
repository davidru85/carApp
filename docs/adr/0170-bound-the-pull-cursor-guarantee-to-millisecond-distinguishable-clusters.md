# ADR-0170 / D-169 - Pull-Cursor Sub-Millisecond Truncation

## Status

Proposed

A recommendation is on the table (option A) and requires owner confirmation before `E3-03` starts.
No option is selected here.

## Context

`docs/CONTRACTS.md §9.4` requires later pages to advance with
`startAfter(pageCursor.lastServerUpdatedAt, pageCursor.lastDocumentId)` and states that "the complete
later-page cursor prevents re-reading the same page forever whenever a timestamp cluster exceeds the
page size". `RemoteCursor.lastServerUpdatedAt` is typed as an epoch-millisecond `Instant`
(`§20.7`), and conflict arbitration compares `serverUpdatedAt` as epoch milliseconds (`§9.4`,
`§9.6`).

Firestore server timestamps (`request.time`) carry microsecond resolution. The integration converts
them with `timestamp.toMilliseconds().toLong()`, which truncates the sub-millisecond component down
to a whole millisecond. A cursor materialised from the last document of a page therefore stores a
timestamp that is **at or below** that document's real provider timestamp.

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
| A. Accept the behavior and bound the guarantee to millisecond-distinguishable clusters | Preserves the epoch-millisecond `RemoteCursor` type and the millisecond-Long LWW rule of `§9.4`/`§9.6`; no change to `:core:sync`, the local schema or the remote schema; zero implementation cost. The documented bound states the exact condition under which the progress invariant is guaranteed. | The `§9.4` guarantee is weakened and MUST be restated, not merely annotated. A same-millisecond cluster larger than the page limit still fails the cycle with `SyncError.ConflictUnresolved`, and no mechanism recovers it; the owner accepts that an unsupported data shape can strand the pull cursor until the `sync_cursor` row is deleted. |
| B. Preserve provider precision in the cursor | The later-page boundary is exact, so the progress invariant holds for every cluster, including one larger than the page limit. | Changes a representation contract: `RemoteCursor.lastServerUpdatedAt` (`§20.7`) would stop being an epoch-millisecond `Instant`, and `§9.4`/`§9.6` mandate millisecond-Long conflict arbitration, so the two comparisons would either diverge or both move. It also changes `RemoteSnapshot.serverUpdatedAt` unless conversion happens only at the cursor, which creates a second time representation at the boundary. Touches `:core:sync`, the integration, the golden Swift surface if exposed, and the `E3-02`/`E3-03` acceptance tests. Requires an owner decision and a contract change before any code. |

## Decision

Not yet taken. The recommendation is **option A**: accept the behavior and amend `§9.4` and `§20.7`
to bound the later-page progress guarantee to timestamp clusters that are distinguishable at
millisecond resolution, explicitly stating that a cluster larger than the page limit within one
millisecond can fail the cycle with `SyncError.ConflictUnresolved`. The owner chooses; the decision
is `Proposed`, not `Accepted`.

## Consequences

### Positive

- The contract no longer claims a guarantee the millisecond cursor cannot deliver, so a later story
  cannot rest on it.
- No representation change is required, and the millisecond-Long LWW rule stays single and coherent.

### Negative

- Until the owner decides, `§9.4` overstates its guarantee and `E3-03` cannot start.
- Under option A, an unsupported same-millisecond cluster can still strand the pull cursor.

### Constraints Introduced

- No document may claim the `§9.4` progress guarantee for a timestamp cluster that is not
  distinguishable at millisecond resolution.
- Option B MUST NOT be implemented before it is `Accepted`, because it changes the `RemoteCursor`
  representation and the `§9.4`/`§9.6` arbitration contract.

## Verification

- The no-data-loss argument above is the required confirmation: a downward-truncated lower bound on
  an `>=` filter excludes nothing.
- The defect is reproducible with the existing focused tests by constructing a page whose documents
  share one truncated millisecond; the accepted option defines whether that becomes a `§9.4` caveat
  test or an exact-boundary test.

## References

- `docs/CONTRACTS.md §9.4`, `§9.6`, `§20.7`
- `docs/SPECIFICATION.md §9.4`
- `docs/TECHNICAL_PLAN.md §8`
- `docs/BACKLOG.md` (`E3-03`)
- `docs/handoff-E3-02.md`
