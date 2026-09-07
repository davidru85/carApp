# ADR-0138 / D-137 - Pin the Anonymous Cleanup Trigger to europe-west1

## Status

Accepted

## Context

`onAnonymousUserDeleted` is the sole permitted Cloud Functions 1st gen function in the project
(`TD-01`, `D-63`, `D-136`). In Cloud Functions 1st gen, omitting a region configuration defaults
to `us-central1`. D-13 fixes Firestore in `europe-west1`, D-131 pins `deleteAccount` to
`europe-west1`, D-135 pins `deleteOrphanedAnonymousAccount` to `europe-west1`, and D-22 forbids an
agent from inventing a region or departing from established locations. Deploying the Auth trigger
to `us-central1` would introduce cross-region latency to the awaited Firestore `deleteUserData`
calls and fragment the backend deployment posture across multiple regions without justification.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Pin `onAnonymousUserDeleted` explicitly to `europe-west1` via `.region("europe-west1")` | Colocates the trigger execution with Firestore and the other deletion entry points; eliminates cross-region traffic; adheres to D-13 and D-22; fully supported by the 1st gen function builder. | The trigger depends on Google Cloud Auth event routing to `europe-west1`, which is standard for regional event-driven triggers. |
| Leave unconfigured on provider default (`us-central1`) | No code change. | Cross-region calls to `europe-west1` Firestore add network latency and egress; departs from D-13 and D-22 without an explicit decision. |

## Decision

Configure `onAnonymousUserDeleted` with `region("europe-west1")`. The emitted endpoint region is
pinned by the Functions test suite.

## Consequences

### Positive

- All backend workloads and data stores (`deleteAccount`, `deleteOrphanedAnonymousAccount`,
  `onAnonymousUserDeleted`, and Cloud Firestore) run in `europe-west1`.
- Avoids cross-region network latency during recursive deletion operations.

### Negative

- Emitted endpoint metadata alone (`__endpoint.region`) does not prove deployment feasibility;
  regional support was verified via `npx firebase deploy --only functions --dry-run --force --project davidruiz-carapp-dev`,
  which successfully validated `onAnonymousUserDeleted(europe-west1)`. Auth event routing to
  `europe-west1` depends on Google's cross-region event infrastructure between the global Auth service
  and regional functions.

### Constraints Introduced

- `docs/CONTRACTS.md §11.5` records this exact region.
- The TD-01 migration surface (`docs/TECHNICAL_PLAN.md §13`) MUST carry forward `europe-west1`
  when migrating `onAnonymousUserDeleted` to 2nd gen.

## Verification

- `functions/test/dependencyReachability.test.mjs` asserts
  `exportedFunctions.onAnonymousUserDeleted.__endpoint.region` strictly equals `["europe-west1"]`.
- Platform deployment validation verified via:
  ```bash
  npx firebase deploy --only functions --dry-run --force --project davidruiz-carapp-dev
  ```
  which cleanly accepted `onAnonymousUserDeleted(europe-west1)`:
  ```text
  ⚠  functions: The following functions will newly be retried in case of failure: onAnonymousUserDeleted(europe-west1).
  ✔  Dry run complete!
  ```

## References

- `docs/CONTRACTS.md §11.5`
- `docs/DECISION_BOARD.md` (`D-13`, `D-22`, `D-135`)
- `docs/TECHNICAL_PLAN.md §13` (`TD-01`)
