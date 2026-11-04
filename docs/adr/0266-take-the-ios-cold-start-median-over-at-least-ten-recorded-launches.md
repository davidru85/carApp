# ADR-0266 / D-265 - Take the iOS Cold-Start Median Over at Least Ten Recorded Launches

## Status

Accepted

The owner selected option A on 2026-11-04, after O18 found that the launch metric records a value for
only part of the measured launches. Amends `D-253` (ADR-0254).

## Context

`D-253` (ADR-0254) measures the iOS cold start with `XCTApplicationLaunchMetric(waitUntilResponsive:
false)` over 10 iterations, and the gate of `docs/SPECIFICATION.md` §11 (Non-Functional Requirements) was a
median of 10 runs under 2 s; it becomes a median of at least 10 measured runs.

On 2026-11-04, on the iPhone 13 mini with iOS 27.0.1 and Xcode 27.2, the agent found:

- Until Device Hub (the Xcode 27 successor of "Devices and Simulators") had been opened with the iPhone
  connected, `xcrun xctrace list devices` listed the iPhone under `Devices Offline`, and the launch metric
  recorded no value at all (`hasPerformanceMetrics false`), while `XCTClockMetric` and
  `XCTHitchMetric(application:)` recorded values.
- Afterwards, the launch metric records a value for only part of the iterations. XCTest reports each
  missing one as `Received unexpected number of metrics: 0 in iteration with index <n>` and keeps the test
  green: I3 offline recorded 3 of 10; two probes online recorded 5 of 10, the second with a 3-second pause
  after first content in each iteration. The recorded values lie between 0.461 and 0.518 s.
- Network state and the pause do not change the loss, so it is a loss of the device's trace data, not a
  property of the app.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Run 25 iterations and take the median of every recorded value, requiring at least 10 | One run per figure; the metric, the end marker and the offline method of `D-253` and `D-264` stay | If the lost iterations were systematically slower, the median would be optimistic |
| B. Measure the cold start inside the app, in the `Benchmark` configuration, from process start to first content | No lost iteration; independent of the trace service | New benchmark-only product code, with its own TDD cycle, before any figure |
| C. Accept fewer than 10 values and record their number | No work | Departs from the median of 10 of the gate |

## Decision

The selected option is **A**.

- `PerformancePlan.coldStartIterations` is 25 for both iOS launch tests.
- The iOS cold-start figure is the median of every value the `Duration (AppLaunch)` line records in one
  run. A run that records fewer than 10 values is repeated; values from different runs are never pooled.
- The record of each figure states how many of the 25 iterations recorded a value.
- `docs/runbooks/performance-measurement.md` §3 opens Device Hub before the first step and checks that
  `xcrun xctrace list devices` lists the iPhone outside `Devices Offline`.
- Android keeps 10 iterations (`D-252`): Macrobenchmark fails an iteration it cannot measure instead of
  dropping it silently.

## Consequences

### Positive

- The iOS gate figures are obtainable on the substitute device with the method of `D-253` and `D-264`.
- Each figure rests on at least 10 measured launches, as the gate requires.

### Negative

- Each launch step takes about a minute longer.
- The lost iterations are not random by proof; the residual risk is an optimistic median, recorded in the
  `E4-03` handoff with the number of recorded values.

### Constraints Introduced

- An iOS cold-start figure MUST come from one run that recorded at least 10 values.
- `PerformancePlan.coldStartIterations` MUST stay at 25 or more while the trace service loses iterations.

## Verification

- `PerformanceHarnessContractTest.iosColdStartRunsEnoughIterationsForTenRecordedLaunches` pins the
  iteration count; it failed before the change.
- O18 records the number of values behind each figure (`docs/handoffs/E4-B2.md`).

## References

- `docs/DECISION_BOARD.md` (decision ID `D-265`); `D-253` (ADR-0254); `D-264` (ADR-0265); `D-252`
- `docs/runbooks/performance-measurement.md` §3; `docs/versions-matrix.md` "Performance measurement
  baselines"
