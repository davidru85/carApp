# ADR-0069 / D-68 - Retain the Official Functions Stack with an Unreachable Moderate Advisory

## Status

Accepted

## Context

The E0-07 production Functions lockfile reports seven moderate entries for
GHSA-w5hq-g745-h8pq. The advisory affects `uuid` v3, v5 and v6 calls when a caller supplies an
external output buffer. `uuid@9.0.1` enters through Firebase Admin's Cloud Storage dependency.

`stopBilling` uses Firebase Functions' 2nd gen Pub/Sub builder and `@google-cloud/billing`; it does
not use Firebase Admin APIs. Removing the direct `firebase-admin` manifest entry does not remove
the package: Firebase Functions 7.3.2 declares Firebase Admin as a mandatory, non-optional peer,
so a clean deployment install restores it. The finding is not development-only: it remains in
`npm audit --omit=dev`.

The acceptance therefore depends on runtime reachability, not merely dependency-tree inspection.
The executable reachability test invokes the actual exported Pub/Sub wrapper with a CloudEvent,
passes through `CloudBillingGateway`, reaches the billing-disassociation request and records every
loaded CommonJS module. Neither Cloud Storage nor `uuid` is loaded, so the affected UUID variants
cannot execute on this function path.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Retain the official SDK graph with an expiring reachability-based acceptance | Preserves the supported Firebase deployment path and the single D-63 codebase while making the residual executable and reviewable. | The deployed lockfile continues to contain a moderate transitive advisory. |
| Replace Firebase Functions with Functions Framework | Removes Firebase Admin's mandatory peer from this function. | Abandons the official Firebase SDK and creates a divergent deployment path that must be migrated again for D-63. |
| Force a transitive UUID override or SDK downgrade | May make the current audit output green. | Uses an unsupported dependency combination or deliberately downgrades official SDKs, contradicting the standing no-forced-transitives policy. |

## Decision

Retain the official Firebase Functions 7.3.2 and Firebase Admin 14.3.0 dependency graph. Accept
GHSA-w5hq-g745-h8pq only while the affected UUID variants remain dynamically unreachable from
`stopBilling`.

The acceptance date is **2026-08-26**. It expires into the existing TD-01 quarterly review, first
due **2026-12-01**, owned by **David Ruiz**. It does not create a separate debt or review cycle.

CI runs `npm audit --omit=dev --audit-level=high`: moderate findings remain visible, while any high
or critical finding fails the pipeline. The explicit register records the advisory, evidence,
changing deployment state and each review outcome.

## Consequences

### Positive

- Official Firebase SDK compatibility and one deployment path remain intact.
- Reachability is continuously tested against the actual exported trigger path.
- Security CI stays actionable rather than permanently red.

### Negative

- Seven moderate production dependency entries remain until an official compatible update removes
  the affected transitive version.
- The acceptance must be re-evaluated as the project moves from unbilled and undeployed to active.

### Constraints Introduced

- The acceptance is reviewed immediately on any high or critical finding, any expansion of this
  function into Storage, or the availability of a compatible official update.
- Each TD-01 quarterly review reruns the production audit and dynamic reachability test and records
  the then-current billing, deployment and user state.
- Transitive overrides, forced audit fixes and SDK downgrades are forbidden without a superseding
  owner decision.

## Verification

- `npm audit --omit=dev` proves the finding is in production dependencies.
- `functions/test/dependencyReachability.test.mjs` executes the exported CloudEvent path, reaches
  the Billing update call and fails if Cloud Storage or `uuid` loads.
- `functions/package.json` keeps the high/critical audit threshold explicit.
- `docs/SECURITY_ADVISORY_REGISTER.md` records acceptance and later state changes.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-68`)
- `docs/SECURITY.md`
- `docs/SECURITY_ADVISORY_REGISTER.md`
- `docs/TECHNICAL_PLAN.md §13` (`TD-01`)
- [GHSA-w5hq-g745-h8pq](https://github.com/advisories/GHSA-w5hq-g745-h8pq)
