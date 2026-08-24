# ADR-0051 - Firestore First-Page Cursor Is Timestamp-Only

## Status

Accepted

## Context

The delta-pull contract specified `startAt(overlapSince, "")` so the first page had the same two
cursor components as later pages. The official Firebase JS 12.18.0 SDK selected by D-46 rejects
the empty document-ID component before executing the query because it is not a valid document ID.
The first page must still include every document at the overlap timestamp, including a malformed
document ID that a defensive recovery path must quarantine.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Use `startAt(overlapSince)` first, then the full cursor later | Works with the pinned SDK, keeps an explicit first boundary and includes every ID at that timestamp. | First and later pages have different cursor arity. |
| Rely only on `where(updatedAt >= overlapSince)` for the first page | Is semantically sufficient and has fewer query clauses. | Removes the explicit first-page boundary and diverges further from the established query shape. |
| Use the minimum valid UUID as the first document-ID component | Keeps two cursor components on every page. | Can exclude malformed document IDs that sort before the UUID sentinel and therefore evade quarantine. |

## Decision

The first page of every delta-pull cycle uses `startAt(overlapSince)`. Later pages use
`startAfter(lastServerUpdatedAt, lastDocumentId)`. The first page therefore includes every
document at the overlap timestamp, while later pages retain the complete deterministic cursor.

## Consequences

### Positive

- The canonical query executes through the exact Firebase SDK pinned by D-46.
- No artificial document-ID sentinel can exclude malformed documents from recovery and quarantine.
- Timestamp clusters remain deterministic because every page after the first uses both ordering
  components.

### Negative

- The query adapter must distinguish the first page from later pages when constructing cursors.

### Constraints Introduced

- `RemoteCursor.INITIAL` materialises as a timestamp-only first-page boundary and never supplies a
  nullable or empty document-ID cursor component.
- Later pages MUST continue to use `startAfter(lastServerUpdatedAt, lastDocumentId)`.
- Emulator evidence MUST cover both the first-page boundary and a later page within a timestamp
  cluster.

## Verification

- The E3-01 emulator test executes both query shapes through Firebase JS 12.18.0.
- The test proves a tombstone is returned and equal-timestamp documents paginate by document ID.
- E3-03 tests prove `RemoteCursor.INITIAL` never reaches `RemoteSyncSource`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-50`)
- `docs/CONTRACTS.md §9.4`, `§16` and `§20.7`
- `docs/BACKLOG.md` (`E3-01`, `E3-02` and `E3-03`)
