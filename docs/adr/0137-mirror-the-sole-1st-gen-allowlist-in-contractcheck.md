# ADR-0137 / D-136 - Mirror the Sole-1st-Gen Allowlist in `contractCheck`

## Status

Accepted

## Context

TD-01 (`docs/TECHNICAL_PLAN.md §13`) permits exactly one Cloud Functions 1st gen function,
`onAnonymousUserDeleted`, and names a Functions test as its allowlist. Architecture rules in this
repository are required to be executable checks, and `contractCheck` is the Gradle entry point
that the CI contract job runs. Keeping the allowlist only inside `functions/` means a check that
the repository treats as a contract is invisible to the contract job.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Keep the allowlist only in the Functions test suite | Matches the TD-01 file naming exactly; no `build-logic` change. | `contractCheck` cannot see it; a second 1st gen function could pass the contract job while only `npm test` fails; the TD-01 guard remains invisible to Gradle-only verification. |
| Add a `FunctionGenerationContract` to `contractCheck` that inspects `functions/src` and `firebase.json`, keeping the Functions suite as the behavioral owner | The contract job fails on any additional 1st gen source module, undeclared export or hidden deployment entry, while the Functions suite keeps the runtime assertions; both are derived from the same normative surface. | The rule exists in two places and a TD-01 closure touches both. |
| Report it from `contractCheck` as informational only | Visibility without duplication of enforcement. | `contractCheck` has no PENDING assertions by design and an informational line does not fail CI, so the real protection would still be only `npm test`. |

## Decision

The Functions test suite remains the behavioral owner of the allowlist. In addition, a
`FunctionGenerationContract` runs inside `contractCheck`: it reads `functions/src/**` and
`firebase.json` and fails when a module other than `functions/src/auth/onAnonymousUserDeleted.ts`
depends on `firebase-functions/v1`, when the index export set differs from the TD-01 surface, or
when the deployment configuration declares a second codebase or a non-Node.js-22 runtime. The
check carries a failing fixture in `:build-logic:convention:test` proving it fires.

## Consequences

### Positive

- The ten-check CI surface and the local contract command both reject a second 1st gen function.
- The rule is parsed from the same sources TD-01 names, so closing TD-01 has an exact edit list.

### Negative

- The allowlist assertion is intentionally duplicated between the Functions suite and
  `build-logic`; TD-01 closure MUST update both in the same change.

### Constraints Introduced

- No new module under `functions/src/**` may import `firebase-functions/v1`.
- `firebase.json` continues to declare exactly one Functions codebase on the Node.js 22 runtime.

## Verification

- `functionGenerationPolicy.test.mjs` (Functions suite) and the new
  `FunctionGenerationContract` (`:build-logic:convention:test` with a failing fixture) both pass
  on `main` and fail on a fixture that adds a second v1 module.
- `./gradlew contractCheck` reports the new assertion as PASS.

## References

- `docs/TECHNICAL_PLAN.md §13` (`TD-01`)
- `functions/test/functionGenerationPolicy.test.mjs`
- `build-logic/convention/src/main/kotlin/com/ruizurraca/carapp/buildlogic/contract/FunctionGenerationContract.kt`
- `docs/BACKLOG.md` (`E3-11`)
