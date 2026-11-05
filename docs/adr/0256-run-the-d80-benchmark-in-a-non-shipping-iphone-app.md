# ADR-0256 / D-255 - Run the D-80 Benchmark in a Non-Shipping iPhone App

## Status

Accepted

The owner selected option A on 2026-10-24.

## Context

`D-80` requires the 1,000-entry consumption benchmark (5 warm-ups, median of 20, `< 100 ms`) on a real
iPhone. `E1-05` linked an optimized `iosArm64` test binary, but a Kotlin/Native test executable cannot
be run on an unjailbroken iPhone, and `IosConsumptionPerformanceTest` only printed the median.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. A non-shipping iOS app target linking a separate `iosArm64` Release framework from `:feature:fuel` | Runs the exact procedure on the device; the `Shared` ABI is untouched | A new framework binary and app target |
| B. An XCTest calling an exported Kotlin entry point in `Shared` | No new target | Expands the closed Swift ABI |
| C. A documented manual procedure only | No code | No repeatable execution path |

## Decision

The selected option is **A**.

- `:feature:fuel` declares a custom `iosArm64` compilation `deviceBenchmark`, associated with the
  `iosArm64` test compilation, and a Release framework `ConsumptionBenchmark` built from it. The
  compilation contains one file whose only public declaration is
  `@ObjCName(name = "runConsumptionBenchmark") fun runConsumptionBenchmark(): Long`, returning the median
  in nanoseconds of the unchanged `measureConsumptionBenchmark` procedure. The procedure stays in
  `commonTest`, so nothing reaches the production `Shared` framework or the Android artifact.
- `iosApp/project.yml` declares a device-only application target `ConsumptionBenchmarkApp` (bundle
  identifier `com.ruizurraca.carapp.consumptionbenchmark`, decided by the owner) that builds and embeds
  only that framework and shows the median. It is in no archive and in no shipping scheme.
- The `< 100 ms` rule is one tested predicate, `exceedsConsumptionThreshold(medianNanos)`, used by the JVM
  task and asserted by `IosConsumptionPerformanceTest`.

## Consequences

### Positive

- The `D-80` device figure can be produced and recorded.

### Negative

- The app is built locally only; CI links the framework but does not build the app.

### Constraints Introduced

- The framework exports nothing but `runConsumptionBenchmark`.

## Verification

- `ConsumptionBenchmarkThresholdTest`; `:feature:fuel:linkConsumptionBenchmarkReleaseFrameworkIosArm64`
  in the existing CI link step; `PerformanceHarnessContractTest`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-255`); ADR-0081 (`D-80`); `docs/handoffs/E1-05.md`
