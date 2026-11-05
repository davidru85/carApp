# ADR-0267 / D-266 - Measure the iOS Fling Online, One Block per Run

## Status

Accepted

The owner selected option A on 2026-11-05, after O18 found that the iOS 27 hitch metric stops recording once
about 60 full-list flings have accumulated in one run. Two earlier versions of this decision were accepted
the same night, each on a diagnosis that the next run disproved. The first blamed the offline state and
measured online. The second blamed the block length and returned each block to the top by reopening the
vehicle. Amends `D-253` (ADR-0254) for the fling's iterations and `D-264` (ADR-0265) for the fling's network
state.

## Context

`D-253` (ADR-0254) measures iOS list smoothness with `XCTHitchMetric(application:)` over 5 iterations of 30
fast swipes. XCTest runs one more invocation first and discards it. `D-264` (ADR-0265) makes every iOS
performance test verify its install online, then measure offline after the idle period. Each block of
`testFlingAcrossOneThousandFuelEntries` flung 30 times toward the end of the list, called `stopMeasuring()`,
and flung 30 times back to the top.

Runs on 2026-11-04 and 2026-11-05, on the iPhone 13 mini with iOS 27.0.1 and Xcode 27.2, with Device Hub
open. Every swipe takes about 2.9 s.

| Run | Network | Flings that moved the list | Result |
|-----|---------|----------------------------|--------|
| Original block (30 up, `stopMeasuring()`, 30 down), two runs | offline | 60, all inside the discarded first invocation | no value |
| Original block, one run | online | 60, all inside the discarded first invocation | no value |
| 30 up, `stopMeasuring()`, then the block reopens the vehicle | online | 60, at the end of the first measured invocation | no value |
| Probe: two invocations of 10 up, each followed by a 90 s pause after `stopMeasuring()` | online | 20 | values, 0 hitches |
| Probe: two invocations of 30 up; the second starts at the end of the list | online | 30; the second invocation's swipes do not move the list | values, 0 hitches |

- After its 60th fling that moved the list, each failing run logged `Error occured with connection: Error
  Domain=NSCocoaErrorDomain Code=4097 "connection to service named
  com.apple.metricmeasurement.MetricMeasurementHelper" ... Nilling the manager`. It printed no
  `measured [` line, and it still passed.
- The network state does not separate the failures from the successes. Neither does the block length, nor
  the time since the first swipe: the second probe swiped for about 174 s, as long as the failing runs, and
  recorded values.
- What separates them is how far the list moved in one run. Runs with 60 full-list flings fail; runs with
  30 or fewer succeed.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. One measured block of 30 flings per run, five runs scripted online | Each run moves the list within the amount every successful run stayed within; the metric of `D-253`; scripted and unattended | Five installs, verification launches and idle periods, about 5 minutes each; the figure comes from five runs; the app is online |
| B. Instruments "Animation Hitches" offline (the runbook's I7b) | Independent of the XCTest metric helper; offline, as `D-264` | Five manual recordings and a manual count by the owner |
| C. As A, but offline | The offline state of `D-264` and of Android | The owner switches airplane mode during each of the five runs; a one-block run was never tried offline |

## Decision

The selected option is **A**.

- `PerformancePlan.flingBlocksPerRun` is 1, and `testFlingAcrossOneThousandFuelEntries` uses it as its
  `iterationCount`. XCTest's discarded first invocation calls `stopMeasuring()` without flinging. The
  measured invocation flings 30 times toward the end of the list, then calls `stopMeasuring()`. The run
  never flings back to the top.
- The block records `XCTClockMetric()` beside `XCTHitchMetric(application: app)`. A measured duration of
  about 85 s shows that the recorded invocation is the one that flings.
- `docs/runbooks/performance-measurement.md` §3 I7 runs the test five times in a loop, with one result
  bundle and one log per run. The loop stops at the first run that records no hitch value.
- The figure is the five hitch counts, and the gate holds when each count is 0. This is not the pooling that
  `D-265` forbids. `D-265` takes a median of launch durations within one run; here each count is a
  separate block, judged by itself against zero.
- `iosApp/PerformanceUITests/PerformanceUITestSupport.swift` adds `XCUIApplication.verifyOnlineThenIdle(idle:)`.
  It shares the verification launch of `D-264` and waits `idle` seconds. It then fails with `Stay online:
  the fling is measured online (D-266)` unless the device is still online, using the criterion of
  `IosConnectivityObserver`.
- `testFlingAcrossOneThousandFuelEntries` calls it with `PerformancePlan.idleBeforeMeasuring` and never asks
  for airplane mode, so the five runs need no owner action.
- The owner turns on Do Not Disturb before I7. Online, other apps' notification banners otherwise cover the
  list during the measured flings, as they did in the first one-block run.
- Both cold-start tests and the seeding test keep `verifyOnlineThenWaitForOffline(idle:)`. They measure or
  seed offline, as `D-264` decides.
- Android keeps its offline fling (`D-252`).
- I7b stays the fallback in three cases:
  - the hitch metric is unavailable (iOS older than 26);
  - the device is not 60 Hz;
  - a run records no hitch value.

## Consequences

### Positive

- The iOS smoothness gate is obtainable on the substitute device, scripted and unattended, with the metric
  of `D-253`.
- Each run stays within the scrolling that every successful run stayed within.

### Negative

- I7 takes about 25 to 30 minutes.
- The iOS fling and the Android fling run under different network states. Network activity of the app can
  overlap the measured swipes: an authentication refresh, App Check, analytics and Crashlytics uploads, or
  a sync cycle with an empty outbox. That activity adds load, so it can only add hitches.
- The limit is observed, not documented. A later iOS release may move it, so a run that logs `Code=4097` is
  stopped and reported.
- It is not known whether a one-block run also records offline.

### Constraints Introduced

- An iOS fling run MUST measure exactly one block. The discarded first invocation of that block MUST NOT
  fling, and the run MUST NOT fling back to the top.
- The iOS fling figure is five runs, and each run's hitch count is judged against zero.
- The iOS fling is measured online; the cold starts and the seeding stay offline.
- The iOS fling runs with Do Not Disturb on.
- A non-zero iOS hitch count MUST be investigated for overlapping network work before it is reported as a
  gate failure.

## Verification

- `PerformanceHarnessContractTest.iosFlingIsMeasuredOnlineAndColdStartOffline` requires:
  - the online helper and its final online check;
  - the fling's call to that helper;
  - no airplane-mode request in the fling;
  - the offline helper in both cold-start tests.

  It failed before its change (`51f49ac6`).
- `PerformanceHarnessContractTest.iosFlingMeasuresOneBlockOfThirtyFlingsPerRun` requires:
  - `flingBlocksPerRun = 1`, and that iteration count in the test;
  - the clock metric beside the hitch metric;
  - a warm-up branch without swipes;
  - the flings in the measured branch;
  - no `swipeDown`.

  It failed before its change (`cd30c410`).
- O18 records the five runs and their hitch counts (`docs/handoffs/E4-B2.md`).

## References

- `docs/DECISION_BOARD.md` (decision ID `D-266`); `D-253` (ADR-0254); `D-264` (ADR-0265); `D-265`
  (ADR-0266); `D-252`
- `docs/runbooks/performance-measurement.md` §3; `docs/versions-matrix.md` "Performance measurement
  baselines"
