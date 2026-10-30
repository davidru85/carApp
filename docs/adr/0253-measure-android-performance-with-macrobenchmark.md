# ADR-0253 / D-252 - Measure Android Performance with Macrobenchmark

## Status

Accepted

The owner selected option A on 2026-10-24. The `staging`-based execution (R-1/R-2/R-3) was confirmed on
2026-10-30.

## Context

`E4-03` must measure Android cold start (median of 10, to the `D-251` frame) and list smoothness (a
scripted fling across 1,000 entries, frames over 32 ms). Since `D-258` (ADR-0259) the production project
belongs to `E4-10`, so release builds fail closed (`D-238`) and the measured configuration is the
`staging` build type of `D-259` (ADR-0260): release code and settings, the `.debug` identity, debug
signing and the development project. Macrobenchmark needs a `profileable` build and a seeding entry
point that `staging` must not carry. The repository had no benchmark tooling.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Macrobenchmark and UiAutomator in a `com.android.test` module `:benchmark`, against a `benchmark` build type derived from `staging` | `StartupTimingMetric` reports the time to full display; `FrameTimingMetric` reports per-frame lateness; repeatable, scripted | New libraries, a new module and a new build type |
| B. `adb shell am start -W -S` ×10 and `dumpsys gfxinfo framestats` | No dependency | Manual parsing, no per-frame deadline data, weaker repeatability |

Ways to measure `staging` itself:

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| R-1. A `benchmark` build type that is `initWith(staging)` plus a declared delta | One install per run; `profileable`; the delta is pinned by a guard | One more build type to keep equal to `staging` |
| Seed with a `benchmark` build, then reinstall `staging` and measure it | Measures the `staging` APK itself | A reinstall per run, and `staging` is not `profileable`, which Macrobenchmark's trace capture relies on |

## Decision

The selected options are **A** and **R-1**.

- Libraries: `androidx.benchmark:benchmark-macro-junit4` 1.5.0, `androidx.test.uiautomator:uiautomator`
  2.4.0 and `androidx.test.ext:junit` 1.3.0, pinned in `gradle/libs.versions.toml`.
- Module `:benchmark`, a `com.android.test` module configured by the convention plugin
  `carapp.android.benchmark`, with `targetProjectPath = ":androidApp"`, self-instrumentation and one
  enabled variant, `benchmark`. Its dependency rule is a `docs/TECHNICAL_PLAN.md §4` row: it may depend
  only on `:androidApp` as the measured application.
- `:androidApp` build type `benchmark`: `initWith(getByName("staging"))`, `applicationIdSuffix = ".debug"`,
  `signingConfigs.findByName("debug")`, `matchingFallbacks` exactly `release`. Its declared delta from
  `staging` is `androidApp/src/benchmark/AndroidManifest.xml` (`<profileable android:shell="true"/>` and the
  exported seeding activity of `D-254`), `BenchmarkSeedActivity.kt`, and byte-identical copies of the two
  per-source-set files of `staging` that a build type cannot inherit:
  `androidApp/src/benchmark/google-services.json` and
  `androidApp/src/benchmark/java/com/ruizurraca/carapp/AppCheckProvider.kt`. It never reaches the
  production project and is never distributed.
- Measurements use `CompilationMode.None()`: R8 is off (`D-245`) and no baseline profile exists, so this is
  the state of a fresh install. Figures are labelled "`staging` (`D-259`), measured through its
  `benchmark` derivative (`D-252`)".
- Cold start: `StartupTimingMetric`, `StartupMode.COLD`, 10 iterations; the gate figure is the median
  of `timeToFullDisplayMs`.
- Smoothness: `FrameTimingMetric`, 5 iterations, each flinging the 1,000-entry list from top to end.
  With `T = 1000 / refreshHz` ms, a frame is over 32 ms when `frameOverrunMs > (floor(32 / T) - 1) * T`;
  at 60 Hz that is any positive overrun. The gate is a count of zero over all iterations.
  `frameDurationCpuMs > 32` is reported as a diagnostic.

## Consequences

### Positive

- Cold start and smoothness are measured by scripted, repeatable tests with JSON output, on release code.

### Negative

- A benchmark install replaces any debug or staging install on the measurement device (shared
  application ID).
- The `:benchmark` module is compiled locally only; the required checks of `main` are unchanged (`D-248`).

### Constraints Introduced

- `:benchmark` depends on nothing but `:androidApp`; the architecture check enforces it.
- The `benchmark` Firebase configuration and App Check provider stay byte-identical to the `staging`
  ones, and `FirebaseConfigurationTest` lists the benchmark configuration among the reviewed development
  locations (`D-238`).

## Verification

- `ArchitectureCheckerTest.benchmarkModuleMayDependOnlyOnTheMeasuredApplication`.
- `PerformanceHarnessContractTest` pins the build type, its delta from `staging` and the module wiring.
- `./gradlew :androidApp:assembleBenchmark :androidApp:assembleStaging :benchmark:assembleBenchmark`
  builds the APKs.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-252`)
- `docs/versions-matrix.md`; `docs/runbooks/performance-measurement.md`
- `D-24`, `D-41`, `D-84`, `D-238`, `D-245`, `D-248`, `D-259`
