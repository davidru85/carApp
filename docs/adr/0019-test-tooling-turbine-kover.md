# ADR-0019 / D-17, D-18 - Test Tooling: Turbine and Kover

## Status

Proposed

Requires owner confirmation in `E0-00`, before `E0-05`.

## Context

Two testing decisions were left open and both block Phase 0 acceptance criteria.

`docs/BACKLOG.md` asks for Flow assertions across shared presentation, repositories and the sync engine, which is painful without a helper. Separately, `E0-03` originally required "high coverage" for value objects, which is not a pass/fail criterion without a measurement tool and a threshold.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Turbine for Flow testing | Concise, well-established, works in KMP common tests. | Must match the pinned coroutines version. |
| Manual Flow collection | No dependency. | Verbose and error-prone; encourages skipping emission assertions. |
| Kover for coverage | Kotlin-native, works with KMP, integrates with Gradle verification. | Reporting on Kotlin/Native targets is more limited than on JVM. |
| No coverage measurement | Nothing to maintain. | "High coverage" stays unverifiable and silently degrades. |

## Decision

Use Turbine for Flow testing and Kover for coverage measurement, with per-module thresholds enforced in CI: `:core:model` and `:core:common` at 90%, feature `domain` at 85%, `:core:sync` at 80%.

If Turbine turns out to be incompatible with the pinned coroutines version during `E0-06`, fall back to hand-written collection helpers in `:core:testing` and record that in `docs/PROJECT_LOG.md`; this does not require a new ADR because the abstraction is test-only.

## Consequences

### Positive

- Emission assertions are cheap enough that agents actually write them.
- "High coverage" becomes a build failure instead of an opinion.

### Negative

- Coverage thresholds need occasional tuning as modules grow.

### Constraints Introduced

- Thresholds are declared in `docs/versions-matrix.md` and enforced by CI, not by review.
- Hand-written fakes remain the preferred test double; mocking libraries are still not accepted.

## Verification

- `E0-05` fails the build when coverage drops below a threshold.
- `E0-06` validates Turbine against the pinned coroutines version.

## References

- `docs/DECISION_BOARD.md` (`D-17`, `D-18`)
- `docs/versions-matrix.md`
