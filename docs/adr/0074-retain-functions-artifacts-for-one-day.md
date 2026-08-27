# ADR-0074 / D-73 - Retain Cloud Functions Artifacts for One Day

## Status

Accepted

## Context

Cloud Functions deployment stores build images in Artifact Registry. Without a cleanup policy,
obsolete images accumulate storage cost even though the source and reproducible deployment
configuration live in Git. A policy is not accepted merely because it exists: it must target the
repository actually used by the deployed function and an eligible artifact must be observed being
removed.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Retain artifacts for one day in the exact Cloud Functions repository | Bounds cost and leaves a brief inspection window while Git remains the source of truth. | Very old deployment images are unavailable for registry-based inspection or rollback. |
| Retain artifacts for seven days | Gives a longer inspection window. | Accumulates unnecessary cost in a single-developer development project. |
| Configure no cleanup | Preserves all images. | Creates indefinite storage growth and contradicts the project's cost-containment purpose. |

## Decision

Resolve the deployed `stopBilling` image to its actual Artifact Registry repository, then apply the
Firebase CLI Cloud Functions artifact cleanup policy there with one-day retention. Do not infer a
repository name from convention. Record the repository, policy and before/after artifact evidence.

## Consequences

### Positive

- Obsolete deployment images have a bounded storage lifetime.
- The policy is verified against the deployment's real repository rather than assumed from
  configuration.
- Source history and reproducibility remain owned by Git.

### Negative

- Registry images older than one day cannot be retained as an informal rollback archive.
- Artifact Registry applies cleanup asynchronously, so observed deletion can lag policy creation.

### Constraints Introduced

- The policy MUST be applied only after resolving the repository from deployed-function evidence.
- Acceptance MUST include an observed deletion of an eligible image, not only policy inspection.
- Changing retention or disabling cleanup requires a superseding owner decision.

## Verification

- Deployed-function and Artifact Registry inspection identify the same repository and location.
- The active cleanup policy specifies deletion after one day.
- Before/after artifact inventories or audit evidence prove that an eligible image was removed.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-73`)
- `docs/SPECIFICATION.md`
- `docs/TECHNICAL_PLAN.md`
- [Manage Cloud Functions deployment artifacts](https://firebase.google.com/docs/functions/manage-functions)
- [Artifact Registry cleanup policies](https://cloud.google.com/artifact-registry/docs/repositories/cleanup-policy-overview)
