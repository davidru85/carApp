# ADR-0017 / D-16 - Architecture Checks with Konsist Plus a Custom Gradle Check

## Status

Proposed

Requires owner confirmation in `E0-00`, before `E0-04`.

## Context

The architecture rules have two different shapes and only one of them is expressible in Gradle.

Module-level rules — `:shared` must not depend on `:integration:*`, features must not depend on each other — are visible in the Gradle dependency graph.

Package-level rules — feature `domain` must not depend on feature `data`, `presentation` must not depend on `data`, `domain` must not import Room, Firebase, Koin or Ktor — are **intra-module**, because each feature is a single Gradle module by design. Gradle cannot see them at all.

Leaving this decision open blocked `E0-04`, whose acceptance criteria are phrased as build failures.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Konsist for package rules, custom Gradle check for module rules | Konsist reads the Kotlin source tree, which is exactly what package rules need. Module rules stay cheap. | Two mechanisms to maintain. |
| Custom checks only | No new dependency. | Reimplements Kotlin source parsing, which is a project in itself. |
| Splitting each feature into three Gradle modules | Rules become expressible in Gradle alone. | Triples the module count and contradicts `docs/TECHNICAL_PLAN.md §3`. |

## Decision

Use Konsist for package-level rules and a custom Gradle configuration check for module-level rules. Generate the check configuration from the dependency table in `docs/TECHNICAL_PLAN.md §4` so the table and the checks cannot drift.

## Consequences

### Positive

- Every rule stated in the documentation is actually enforceable.
- The table stays the single source for both documentation and enforcement.

### Negative

- Two check mechanisms, and Konsist must be revalidated when Kotlin is upgraded.

### Constraints Introduced

- Every architecture rule MUST have a failing fixture proving the check fires. A check that never fails is treated as absent.
- Rule violations MUST fail the build with a rule-specific message, not a generic one.

## Verification

- `E0-04` acceptance criteria require one failing fixture per rule.
- CI check name is `architecture-check`, per `docs/CONTRACTS.md §18`.

## References

- `docs/DECISION_BOARD.md` (`D-16`)
- `docs/TECHNICAL_PLAN.md` §4
