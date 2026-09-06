# ADR-0130 / D-129 - Use Firestore Recursive Delete per Registered Collection

## Status

Accepted

## Context

D-63 fixes the deletion registry and D-23 fixes the collection order, but the server-side deletion
primitive was open. The implementation must be idempotent, await complete deletion and remain
bounded to the registered `users/{uid}` subtree.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Await Admin SDK `recursiveDelete` once per registered collection | SDK-owned bulk execution, idempotent missing-data behavior and a small auditable path boundary. | Retry and throttling internals belong to the pinned Admin SDK. |
| Implement hand-written paged batch deletion | Full control over page size, retry and metrics. | More code and failure boundaries for behavior already owned by the Admin SDK. |
| Fetch and delete every document individually | Simple per-document logic. | Slow, request-heavy and poorly suited to accounts with many records. |

## Decision

`deleteUserData` walks the D-63 registry sequentially. Its Firebase gateway constructs
`users/{uid}/{registeredCollection}` and awaits Firestore Admin `recursiveDelete` for each
collection. The parent user document is not part of the closed schema and is not deleted.

## Consequences

- `fuelEntries` completes before `vehicles` starts.
- Repeating the operation after partial completion is safe.
- Future data locations must join the registry and parity test before they can be deleted.
- Changing the pinned Admin SDK requires rechecking `recursiveDelete` behavior.

## Verification

- Server-side tests cover order, repeated deletion, partial-failure retry and the exact owner path.
- The registry contract test compares implementation collections with the closed remote schema.

## References

- `docs/CONTRACTS.md §11.5`, `§16`
- `docs/BACKLOG.md` (`E3-10`)
- `D-23`, `D-63`
