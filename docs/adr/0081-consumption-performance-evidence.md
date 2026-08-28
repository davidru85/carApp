# ADR-0081 / D-80 - Measure Consumption Outside Coverage Instrumentation

## Status

Accepted

## Context

E1-05 must process 1,000 synthetic Fuel Entries below 100 ms after five discarded warm-up runs,
using the median of twenty measured runs on JVM and a real iPhone. Normal feature tests execute
under Kover when coverage is collected. Agent instrumentation changes wall-clock behavior and is
therefore not valid evidence for this target. The current Kotlin/Native convention also creates
only the normal debug test binary, while the measurement baseline requires optimized code.

The owner requires the actual JVM figure before deciding whether the `< 100 ms` threshold is safe
as a shared-runner CI gate. The real iPhone is currently unavailable, and the version matrix says
that absence must remain visible rather than being replaced by a simulator or debug result.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Standalone uninstrumented JVM task plus optimized iOS device-test binary | Measures production code without Kover; reuses one cross-platform dataset and procedure; keeps normal tests fast. | Adds focused Gradle binary/task configuration; the real-device result remains pending until hardware is available. |
| Measure inside normal Android-host and simulator tests | Requires no build configuration. | Coverage and debug Native compilation invalidate the measurement and can differ by an order of magnitude. |
| Defer all formal evidence to E4-03 | Measures only after final release hardening. | Leaves an explicit E1-05 acceptance criterion unevidenced and delays regression detection. |

## Decision

E1-05 adds a standalone `:feature:fuel:consumptionBenchmark` JVM task. It compiles shared benchmark
data and procedure but executes as a separate Java process without the Kover agent. It is not a
dependency of normal tests or the repository-wide verification command. The task discards five
warm-up runs, records twenty measurements and reports their median.

The task initially reports rather than enforces the `< 100 ms` threshold. The owner reviews the
actual measured margin before any CI gate is enabled. The same synthetic data and procedure are
linked into an optimized `iosArm64` test binary for a later manual run on a real device. Linking is
required in E1-05; a missing device result remains an explicit E1-05 handoff and project-log item
owned as an open E4-03 entry.

## Consequences

### Positive

- JVM timing is isolated from Kover and normal test-suite overhead.
- JVM and iOS use the same 1,000-entry workload, warm-up count and median calculation.
- A slow shared runner does not become flaky policy without owner review of the measured margin.
- The unavailable iOS result cannot pass silently.

### Negative

- The first E1-05 PR cannot claim complete iOS performance evidence without a connected iPhone.
- A dedicated Gradle task and optimized Native test binary require maintenance.
- CI enforcement requires a follow-up owner confirmation after the first measurement.

### Constraints Introduced

- `consumptionBenchmark` MUST run without `-javaagent` or Kover task instrumentation.
- The benchmark MUST NOT run transitively from normal test or verification tasks.
- Simulator and debug Native figures MUST NOT substitute for the real-device result.
- The handoff records the device and date when the manual result becomes available.

## Verification

- Gradle task inspection proves the benchmark is a standalone `JavaExec` task without an agent.
- The task output reports all required run counts and the median.
- `linkReleaseTestIosArm64` produces the optimized device-test binary.
- The handoff records the measured JVM result and explicitly leaves the iOS device result pending.

## References

- `docs/SPECIFICATION.md §11`
- `docs/versions-matrix.md` performance measurement baselines
- `docs/BACKLOG.md` (`E1-05`, `E4-03`)
