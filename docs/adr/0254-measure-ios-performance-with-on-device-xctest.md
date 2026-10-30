# ADR-0254 / D-253 - Measure iOS Performance with On-Device XCTest

## Status

Accepted

The owner selected "XCTest where it expresses the metric exactly, Instruments otherwise" on 2026-10-24.
The `staging`-based execution (R-1/R-2/R-3) was confirmed on 2026-10-30.

## Context

The iOS test targets are simulator-only, so no performance figure can be taken on a device. The
measured configuration is `Staging` (`D-259`), which runs release code against the development project.
Two metrics are needed: cold start to the `D-251` frame and the count of frames over 32 ms during a
fling across 1,000 entries. `D-261` (ADR-0262) requires every configuration to bundle only its own
Firebase plist and keeps the plist choice free of compilation conditions.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. XCTest performance tests on a device | Scripted and repeatable | Needs a device-capable test target and owner signing |
| B. Instruments templates run by the owner | Per-frame detail | Manual, error-prone readout |

## Decision

- **Cold start: A.** `XCTApplicationLaunchMetric(waitUntilResponsive: false)` ends when the app has
  drawn its first frame and finished every MetricKit extended launch task; `D-251`'s task ends at first
  content, so the metric is exact. 10 iterations.
- **Fling: A on iOS 26 or later at 60 Hz, B otherwise.** `XCTHitchMetric(application:)` counts
  hitches. At 60 Hz every hitch keeps the previous frame on screen for at least 33.3 ms, so a hitch
  count of zero is exactly "0 frames over 32 ms". On a device below iOS 26, or with another refresh
  rate, the owner runs the Instruments "Animation Hitches" procedure of
  `docs/runbooks/performance-measurement.md` and counts hitches with a duration above
  `(floor(32 / T) - 1) * T` ms. 5 iterations of 30 fast upward swipes.
- **The `Benchmark` configuration (R-2)** is `Benchmark: release` in `iosApp/project.yml`, the measured
  derivative of `Staging`: it uses `Config/Staging/Firebase.xcconfig`, the bundle identifier
  `com.ruizurraca.carapp.debug`, `KOTLIN_FRAMEWORK_BUILD_TYPE = release`, and bundles and loads the
  Staging plist `GoogleService-Info-Staging.plist` (it excludes `GoogleService-Info-Debug.plist` and
  `GoogleService-Info.plist`, `D-261`). `firebaseConfigurationResourceName` maps `Benchmark` to the Staging
  plist and the `D-238` pre-build script validates it. Its only addition to `Staging` is the compilation
  condition `CARAPP_BENCHMARK` (`D-254`). Figures are labelled "`Staging` (`D-259`), measured through
  its `Benchmark` derivative (`D-253`)".
- A device-only UI-test target `carAppPerformanceUITests` (`SUPPORTED_PLATFORMS: iphoneos`, bundle
  identifier `com.ruizurraca.carapp.perfuitests`, decided by the owner) and a scheme `carAppPerformance`
  whose test, run and profile actions use `Benchmark`. The `carApp` scheme and the simulator CI jobs are
  unchanged.

## Consequences

### Positive

- Both gates are scripted on the device; the owner only signs and runs a command.

### Negative

- The performance target is not built in CI.
- A benchmark install replaces any debug or staging install on the device (shared bundle identifier).

### Constraints Introduced

- The `Benchmark` configuration never archives, never uses the production configuration and differs from
  `Staging` only by `CARAPP_BENCHMARK`.
- `FirebaseConfigurationTest` maps `Benchmark` to the Staging plist and counts four `Firebase.xcconfig`
  base configurations (`D-238`, `D-261`).

## Verification

- `PerformanceHarnessContractTest` pins the configuration, target and scheme; `FirebaseConfigurationTest`
  pins the plist exclusions.
- A local unsigned `build-for-testing` for `generic/platform=iOS` compiles the target.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-253`)
- `docs/identifiers.md`; `docs/runbooks/performance-measurement.md`; `D-74`, `D-118`, `D-238`, `D-259`,
  `D-261`
