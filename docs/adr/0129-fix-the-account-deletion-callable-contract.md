# ADR-0129 / D-128 - Fix the Account-Deletion Callable Contract

## Status

Accepted

## Context

D-23 requires a Firebase Admin server operation that verifies the authenticated caller UID equals
the target UID, but the wire name, payload, success value and transport failures were not fixed.
E3-10 needs an executable mismatch case and later client stories need one stable boundary.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A 2nd gen `deleteAccount` callable with `targetUid`, caller equality, `{ status: "ACCOUNT_DELETED" }` and closed callable error codes | Makes caller mismatch executable, maps cleanly to the existing client errors and does not create another 1st gen function. | Adds a small explicit wire schema the client must follow. |
| Infer the target solely from the callable auth context | Smallest payload and makes cross-owner deletion structurally impossible. | Cannot execute the D-23 caller/target mismatch requirement and leaves the existing criterion meaningless. |
| Accept an arbitrary target UID for a privileged administrative caller | Could support future support tooling. | Expands MVP scope, creates a general privileged deletion API and violates the owner-only operation boundary. |

## Decision

Use the Cloud Functions 2nd gen callable name `deleteAccount`. The request contains a non-empty
`targetUid`; callable-verified authentication is required and its UID must equal `targetUid`.
Success returns `{ status: "ACCOUNT_DELETED" }`. Transport errors use `unauthenticated`,
`invalid-argument`, `permission-denied` or `internal` as fixed by `docs/CONTRACTS.md §11.5`.

## Consequences

- Caller mismatch is covered by a server-side test.
- The operation remains owner-only rather than becoming an administrative endpoint.
- A later client adapter must send its current Firebase UID as `targetUid`.
- Logs remain independent of UID, token and payload values.

## Verification

- `functions/test/accountDeletion.test.mjs` covers unauthenticated, malformed and mismatched calls,
  success and typed failure.
- `functions/test/dependencyReachability.test.mjs` proves the export is 2nd gen in `europe-west1`.

## References

- `docs/CONTRACTS.md §11.5`
- `docs/BACKLOG.md` (`E3-10`)
- `D-23`, `D-63`
