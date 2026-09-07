# ADR-0139 / D-138 - Bound the Anonymous Cleanup Trigger Runtime and Enable Retries

## Status

Accepted

## Context

`onAnonymousUserDeleted` executes destructive Cloud Firestore bulk deletion upon Firebase Auth
user deletion. D-131 and D-135 established explicit runtime bounds as the primary workload and
cost control for every deletion entry point, with the D-66 cutoff as a delayed safety net only.
Leaving the trigger on provider defaults leaves instance limits and timeouts unmanaged.

Furthermore, in Cloud Functions 1st gen background functions, unhandled rejections do NOT result
in platform event redelivery unless `failurePolicy: true` is explicitly configured. If an error
occurs during Firestore deletion (e.g. a transient network disruption), without
`failurePolicy: true` the execution terminates permanently, leaving orphaned user data in
production with no retry backstop.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Explicit runtime bounds (`maxInstances: 2`, `memory: "256MB"`, `timeoutSeconds: 60`) and `failurePolicy: true` | Aligns bounds with the D-135 anonymous cleanup profile; guarantees that transient Firestore failures trigger platform redelivery, which converges safely thanks to the idempotent design of `deleteUserData`. | Repeated persistent failures will be retried until the platform event expiration window (up to 7 days) closes. |
| Provider defaults without `failurePolicy: true` | No configuration. | Unbounded runtime and instance concurrency; silent failure on transient errors leaves orphaned data permanently without a backstop. |
| Runtime bounds without retry (`failurePolicy` omitted or false) | Bounded resource consumption. | Leaves transient Firestore failures unrecovered, abandoning orphaned user data. |

## Decision

Configure `onAnonymousUserDeleted` with
`runWith({ failurePolicy: true, maxInstances: 2, memory: "256MB", timeoutSeconds: 60 })`.
Endpoint metadata, including `eventTrigger.retry === true`, is pinned by the Functions test suite.

## Consequences

### Positive

- Workload and cost bounds are explicit and match the D-135 anonymous deletion profile.
- Transient errors trigger automatic Cloud Functions redelivery with exponential backoff.
- Redelivery is provably safe because `deleteUserData` is fully idempotent.

### Negative

- A non-transient bug in the trigger would cause repeated retries until event expiration; logs
  must be monitored for `Anonymous cleanup failed`.

### Constraints Introduced

- `docs/CONTRACTS.md §11.5` records these exact runtime bounds and retry configuration.
- The TD-01 migration surface (`docs/TECHNICAL_PLAN.md §13`) MUST carry forward runtime bounds
  and retry capability when migrating to 2nd gen.

## Verification

- `functions/test/dependencyReachability.test.mjs` pins
  `onAnonymousUserDeleted.__endpoint.availableMemoryMb: 256`, `maxInstances: 2`,
  `timeoutSeconds: 60` and `eventTrigger.retry === true`.
- `functions/test/anonymousCleanup.test.mjs` asserts that failures are rethrown and that subsequent
  redelivery converges idempotently.

## References

- `docs/CONTRACTS.md §11.5`
- `docs/DECISION_BOARD.md` (`D-131`, `D-135`, `D-137`)
- `docs/adr/0136-bound-the-orphan-cleanup-callable-runtime.md` (`D-135`)
- `docs/TECHNICAL_PLAN.md §13` (`TD-01`)
