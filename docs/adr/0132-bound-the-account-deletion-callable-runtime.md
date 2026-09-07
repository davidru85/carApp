# ADR-0132 / D-131 - Bound the Account-Deletion Callable Runtime

## Status

Accepted

## Context

The 2nd gen `deleteAccount` callable performs a deliberately long-running destructive operation:
it recursively deletes each registered Firestore collection and then deletes the Firebase Auth
user. Provider defaults leave its instance count, concurrency and memory unspecified, while the
default timeout can terminate the operation before the registered deletion finishes.

D-66 provides a development billing cutoff, but budget reporting and cutoff execution are delayed.
That mechanism is a last-resort safety net, not the primary bound on callable work. D-70 likewise
requires realistic runaway Cloud Function invocation scenarios to be contained and tested.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Bound the callable to three single-concurrency 256 MiB instances with a 300-second timeout | Caps simultaneous deletion work and resource allocation while allowing enough time for an awaited recursive purge. | At most three deletion requests progress at once, and a slow purge still terminates after five minutes. |
| Leave the callable unbounded and rely on the D-66 automatic billing cutoff | Uses provider defaults and adds no endpoint configuration. | Treats a delayed emergency cutoff as the primary control, leaves workload concurrency implicit and retains a timeout that may truncate the purge. |
| Add the runtime bounds and extend D-67 App Check enforcement to Cloud Functions now | Also rejects requests without a valid App Check token before the handler runs. | Changes the accepted D-67 scope, which currently covers Authentication and Firestore, and pre-empts the shared Functions security decision that E3-11 must make for both deletion callables. |

## Decision

Configure `deleteAccount` with `maxInstances: 3`, `concurrency: 1`, `memory: "256MiB"` and
`timeoutSeconds: 300`, while retaining `region: "europe-west1"`. Do not enable Cloud Functions App
Check or select a service account in this decision.

## Consequences

- Explicit endpoint metadata makes the workload bounds reviewable and executable.
- The D-66 automatic billing cutoff remains a delayed safety net rather than the primary control.
- Single-request instances prevent concurrent recursive purges from sharing one runtime, while the
  instance cap limits aggregate work.
- Cloud Functions App Check is deferred until E3-11 adds `deleteOrphanedAnonymousAccount`; the
  current specification and D-67 require App Check only for Authentication and Firestore.
- A dedicated least-privilege service account is deferred because it requires GCP provisioning and
  the function has not been deployed.

## Verification

- `dependencyReachability.test.mjs` reads the emitted `deleteAccount.__endpoint` metadata and pins
  `availableMemoryMb`, `maxInstances`, `concurrency` and `timeoutSeconds`.
- `npm test` passes the complete Cloud Functions test suite.
- `contractCheck` validates D-131 and ADR-0132 across every required decision mirror.

## References

- `functions/src/callable/deleteAccount.ts`
- `functions/test/dependencyReachability.test.mjs`
- `docs/CONTRACTS.md §11.5`
- [ADR-0067](0067-contain-development-cloud-costs.md)
- `D-66`, `D-67`, `D-70`, `D-128`, `D-129`
