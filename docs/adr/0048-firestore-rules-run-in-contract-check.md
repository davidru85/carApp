# ADR-0048 - Firestore Rules Run in Contract Check

## Status

Accepted

## Context

Firestore emulator tests must be mandatory on every pull request once the rule files exist. Main is
already protected by the nine fixed check names of D-31, and the rule suite is executable evidence
for the remote contract rather than an application build.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Named step inside `contract-check` | Immediately protected, semantically aligned and no branch-protection mutation. | No separate top-level status; the job takes longer. |
| Tenth `firestore-rules` job | Independent diagnostics and parallel execution. | Supersedes D-31 and requires a branch-protection update. |
| Run under `provider-decoupling` | No new job. | Mixes security behavior with a dependency-boundary proof. |

## Decision

Run Firestore emulator tests as a named step inside the existing protected `contract-check` job.
The local complete-verification command includes the same npm script.

## Consequences

### Positive

- A rule-test failure blocks merge immediately under existing branch protection.
- The nine fixed check names remain stable.
- Contract assertions and executable Firestore contract evidence are reported together.

### Negative

- The `contract-check` job includes Node, npm and emulator startup time.

### Constraints Introduced

- The CI step name identifies Firestore emulator rules tests explicitly.
- Emulator tests MUST NOT run only in an unprotected or provider-decoupling job.
- Renaming the protected `contract-check` job remains forbidden without a branch-protection update.

## Verification

- The `contract-check` job executes both Gradle `contractCheck` and the npm Firestore rules script.
- A deliberately failing emulator assertion makes `contract-check` fail.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-47`)
- `docs/CONTRACTS.md §18`
- `docs/BACKLOG.md` (`E3-01`)
