# ADR-0265 / D-264 - Verify Each iOS Benchmark Install Online Before Measuring Offline

## Status

Accepted

The owner selected option A on 2026-11-04, after the first O18 session on the iPhone 13 mini found that
no step of the iOS procedure could start in airplane mode. Amends `D-253` (ADR-0254).

## Context

`D-253` (ADR-0254) measures iOS on the device with `xcodebuild test` on the `carAppPerformance` scheme,
and `docs/runbooks/performance-measurement.md` §3 runs every step in airplane mode: the seeding entry
point refuses to seed while the app sees a network (`D-254`, refusal `ONLINE`), and the returning-user and
fling figures exclude the network.

On 2026-11-03 and 2026-11-04, on the iPhone 13 mini with iOS 27.0.1 (the substitute device of
`docs/handoffs/E4-03.md`, follow-up `Q1`), step I3 failed twice before measuring, with `The application
could not be launched because the Developer App Certificate is not trusted`. The agent established, on
that device:

- iOS verifies the developer certificate online at the first launch after **every** install, including an
  identical reinstall over the installed app. Offline, the launch is refused (`its profile has not been
  explicitly trusted by the user`) and the system shows "Unable to Verify App".
- An app that was verified online and has not been reinstalled since launches offline.
- Another verified app of the same developer on the device does not exempt a new install.
- While the "Unable to Verify App" alert is on screen, every launch is refused, online too.
- `xcodebuild test` installs the test runner and `carApp` at every invocation, so every step starts with
  two installs that iOS has not verified.
- `-allowProvisioningUpdates` changes nothing, and the signature is valid
  (`codesign --verify --deep --strict`).

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Each test launches `carApp` once while the device is online, waits until the device is offline, waits the idle period, then measures | Every measured launch and swipe keeps the method of `D-253`, offline; no new tool | The owner switches airplane mode on during the step, when the log asks; each step takes the idle period plus the owner's reaction longer |
| B. Measure online | No wait | The seeding refuses while online (`D-254`), and the returning-user launch would include the network and a sync cycle, so the figures would not be the gated ones |
| C. A device with an iOS release earlier than 27 | The method unchanged | No such device is available, and iOS 27 is the current release |
| D. Record O18 as not run | No work | The iOS gates of `E4-03` stay unmeasured |

## Decision

The selected option is **A**.

- `iosApp/PerformanceUITests/PerformanceUITestSupport.swift` adds
  `XCUIApplication.verifyOnlineThenWaitForOffline(idle:)`. It requires the device to be online, launches
  and terminates the app once so that iOS verifies the new install, prints
  `PERF: turn airplane mode on and Wi-Fi off now`, waits up to `PerformancePlan.offlineTimeout` (300 s)
  until the device is offline, and then waits `idle` seconds.
- Online and offline use the criterion of `IosConnectivityObserver`: a default `NWPathMonitor` path is
  online when its status is `.satisfied` and offline otherwise.
- `testSignedOutLaunchToWelcomeScreen`, `testReturningUserLaunchToPopulatedVehicleList` and
  `testFlingAcrossOneThousandFuelEntries` call it with `PerformancePlan.idleBeforeMeasuring` (120 s, the
  idle period of the runbook §1) before their first launch. `testSeedsTheBenchmarkFuelHistoryWhileOffline`
  calls it with no idle period before it adds the seed argument, so the verification launch never seeds.
- Every measured launch and swipe still runs offline after the idle period, with the metrics, iterations
  and launch arguments of `D-253` and `D-254`.
- The runbook §3 starts each step online and tells the owner to switch the iPhone offline when the log
  prints the line, or when `carApp` has opened and closed once, and to go back online after the step.
- Amended by `D-266` (ADR-0267) on 2026-11-05: `testFlingAcrossOneThousandFuelEntries` calls
  `verifyOnlineThenIdle(idle:)` instead and measures online, one block per run over five runs; both cold
  starts and the seeding keep this helper.

## Consequences

### Positive

- O18 can run on iOS 27, on the substitute device, with every measured figure taken offline.
- The rule is written in the harness and enforced by a guard, not only in the runbook.

### Negative

- The owner touches the device during each step, before the idle period starts.
- The verification launch of the returning-user and fling steps runs online, so the app may run one sync
  cycle before the device goes offline. That launch is not measured.
- The signed-out figure is no longer the first launch after the install; it already was not, because
  `D-253` discards an unmeasured warm-up launch before the ten measured ones.

### Constraints Introduced

- Every test of `carAppPerformanceUITests` that launches `carApp` MUST call
  `verifyOnlineThenWaitForOffline(idle:)` before any other launch, and a test that measures MUST pass
  `PerformancePlan.idleBeforeMeasuring`.
- The seeding test MUST NOT pass the seed argument to the verification launch.
- The offline criterion MUST stay the criterion of `IosConnectivityObserver`.

## Verification

- `PerformanceHarnessContractTest.iosPerformanceTestsVerifyEachInstallOnlineBeforeMeasuringOffline`
  asserts the helper, its `NWPathMonitor` criterion and the call order in the four tests; it failed before
  the harness change.
- The unsigned `build-for-testing` of `carAppPerformance` for `generic/platform=iOS` compiles the target.
- O18 on the iPhone 13 mini (`docs/handoffs/E4-B2.md` §Owner-Run Checklist).

## References

- `docs/DECISION_BOARD.md` (decision ID `D-264`); `D-253` (ADR-0254); `D-254` (ADR-0255); `D-259`
- `docs/runbooks/performance-measurement.md` §3; `docs/handoffs/E4-B2.md` (O18)
