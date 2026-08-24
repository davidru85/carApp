# ADR-0053 - Retain Firebase CLI with Moderate Audit Residual

## Status

Accepted

## Context

At E3-01 implementation time, `npm audit` reports five moderate entries in the transitive tree of
Firebase CLI 15.28.1. They reduce to two advisories: unbounded W3C baggage allocation in
`@opentelemetry/core` and a missing buffer bounds check in old `uuid` APIs. The CLI is a development
dependency used only to run a local Firestore emulator with repository-owned fixtures. Neither
affected path is used by that harness, and none of these packages is linked into the Android or iOS
application.

npm proposes a forced downgrade to Firebase CLI 14.23.0. That contradicts the exact D-46 pin.
Forcing unsupported transitive major versions through overrides would move compatibility ownership
from Firebase to this repository.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Retain 15.28.1 and document the residual | Preserves the reviewed current stack and confines exposure to the emulator process. | The audit continues to report moderate findings until upstream dependencies change. |
| Apply npm's forced CLI downgrade | May clear the current report. | Supersedes D-46, uses an older CLI and requires complete stack revalidation. |
| Override transitive major versions | Attempts remediation without changing the direct pin. | Uses combinations Firebase does not declare compatible and may break CLI behavior. |

## Decision

Retain Firebase CLI 15.28.1 and accept the two-advisory, five-entry moderate residual for the
development-only emulator harness. Do not run `npm audit fix --force` and do not add transitive
major-version overrides.

## Consequences

### Positive

- The owner-selected D-46 stack remains exact and reproducible.
- The repository does not claim compatibility for unsupported dependency combinations.

### Negative

- `npm audit` reports five moderate vulnerabilities for the current lockfile.

### Constraints Introduced

- The residual is accepted only while the affected packages remain development-only and the
  emulator receives no untrusted baggage or UUID buffer input.
- Any high or critical advisory, affected-path expansion, application packaging or Firebase CLI
  version change requires re-evaluation.
- The next reviewed Firebase CLI update MUST rerun `npm audit` and remove this residual if upstream
  has remediated it.

## Verification

- `npm audit --json` identifies only moderate findings below Firebase CLI.
- The dependency tree confirms the affected packages are transitive to `firebase-tools`.
- Android and iOS dependency graphs contain none of the npm packages.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-52`)
- `docs/SECURITY.md`
- `docs/versions-matrix.md`
- `docs/BACKLOG.md` (`E3-01`)
