# ADR-0022 / D-18 - Coverage Measurement with Kover

## Status

Proposed

Requires owner confirmation in `E0-00`, before `E0-05`.

## Context

`E0-03` and `E0-05` require coverage thresholds for core and domain modules. Without a coverage tool, "high coverage" is review language rather than a pass/fail criterion, and future agents can silently under-test core contracts.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Kover for coverage | Kotlin-native, works with KMP, integrates with Gradle verification. | Reporting on Kotlin/Native targets is more limited than on JVM. |
| JaCoCo | Mature JVM coverage tool. | Poorer fit for Kotlin Multiplatform. |
| No coverage measurement | Nothing to maintain. | Coverage targets stay unverifiable and silently degrade. |

## Decision

Use Kover for coverage measurement, with per-module thresholds enforced in CI: `:core:model` and `:core:common` at 90%, feature `domain` at 85%, `:core:sync` at 80%.

## Consequences

### Positive

- "High coverage" becomes a build failure instead of an opinion.
- Coverage thresholds are visible in `docs/versions-matrix.md`.

### Negative

- Coverage thresholds need occasional tuning as modules grow.
- Kotlin/Native coverage reporting may be less complete than JVM reporting.

### Constraints Introduced

- Thresholds are declared in `docs/versions-matrix.md` and enforced by CI, not by review.
- Baseline suppression files remain forbidden.

## Verification

- `E0-05` fails the build when coverage drops below a threshold.
- `E0-06` validates Kover against the pinned Kotlin and Gradle versions.

## References

- `docs/DECISION_BOARD.md` (`D-18`)
- `docs/versions-matrix.md`
