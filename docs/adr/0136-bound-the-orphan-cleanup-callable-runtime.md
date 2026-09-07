# ADR-0136 / D-135 - Bound the Orphan-Cleanup Callable Runtime

## Status

Accepted

## Context

D-131 fixed explicit runtime bounds for `deleteAccount`. `deleteOrphanedAnonymousAccount`
performs an authorization-record lookup and one Admin Auth deletion before delegating to the same
`deleteUserData` purge, so it is lighter than the account-deletion callable but equally
destructive. Leaving it on provider defaults would repeat the exact risk D-131 rejected.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Reuse the exact D-131 bounds (`maxInstances: 3`, `concurrency: 1`, `memory: "256MiB"`, `timeoutSeconds: 300`) | One runtime rule to audit. | Overprovisions an authorization-lookup-and-deletion operation; the 300-second timeout was chosen for the user-requested purge, not this path. |
| Bounds fitted to the workload (`maxInstances: 2`, `memory: "256MiB"`, `timeoutSeconds: 60`, default concurrency) | Keeps an explicit instance cap and memory floor while matching the shorter operation; enough headroom for the awaited `deleteUserData` purge given the bounded MVP collection sizes. | Introduces a second runtime profile to review; a pathological purge can still reach the 60-second limit and must be retried idempotently. |
| No explicit bounds; rely on the D-66 billing cutoff | No configuration. | Repeats the configuration D-131 already rejected: a delayed cutoff as the primary control and a default timeout that can truncate a destructive operation. |

## Decision

Configure `deleteOrphanedAnonymousAccount` with `maxInstances: 2`, `memory: "256MiB"`,
`timeoutSeconds: 60` and `region: "europe-west1"`, leaving concurrency at the provider default.
Endpoint metadata is pinned by the Functions test suite.

## Consequences

### Positive

- Explicit bounds keep the D-66 cutoff as a safety net rather than the primary control.
- The shorter timeout matches the collision-path workload and fails fast into an idempotent
  retry instead of holding resources.

### Negative

- Two callable runtime profiles exist and must be reviewed together whenever the deletion
  registry grows (e.g. Storage prefixes).

### Constraints Introduced

- `docs/CONTRACTS.md §11.5` records these exact values; changing them is a new decision.
- When the data-location registry gains Storage prefixes or larger collections, this bound MUST
  be re-evaluated before the schema change merges.

## Verification

- `functions/test/dependencyReachability.test.mjs` pins the emitted
  `deleteOrphanedAnonymousAccount.__endpoint` values.
- The retry and idempotency tests prove a timed-out or failed attempt converges on a second call.

## References

- `docs/adr/0132-bound-the-account-deletion-callable-runtime.md` (`D-131`)
- `docs/CONTRACTS.md §11.5`
- `docs/BACKLOG.md` (`E3-11`)
