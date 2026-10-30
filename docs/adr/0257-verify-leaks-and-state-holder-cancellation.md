# ADR-0257 / D-256 - Verify Leaks and State-Holder Cancellation

## Status

Accepted

The owner selected option A on 2026-10-24.

## Context

`E4-03` requires "no memory leaks in critical flows; state holder scopes are cancelled". Only
`VehicleFormStateHolder` (through `SwiftAppGraph`), `SyncStateHolder` and `SettingsStateHolder` had a
test proving their own work is cancelled, none proved that the caller's scope survives `close()`, and no
leak tooling existed. `docs/CONTRACTS.md §14` states that `close()` cancels the work a holder owns.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. LeakCanary's instrumentation rule on every Compose instrumented test, iOS weak-reference deallocation tests and `Job`-cancellation tests | Executable, repeated on every instrumented run | A new test library |
| B. Profiler and Instruments Leaks evidence only | No dependency | Not repeatable, not enforced |

## Decision

The selected option is **A**.

- `com.squareup.leakcanary:leakcanary-android-instrumentation` 2.14, `androidTestImplementation` only.
  Every Compose instrumented test class (24 on `bf6c8e21`) wraps its Compose rule in `leakDetecting(...)`:
  a rule chain that installs `AppWatcher` on the main thread when it is not installed (the app-module test
  APK's content providers never start in the app process) and then applies `DetectLeaksAfterTestSuccess`
  outside the Compose rule, so activity leaks are detected after the activity is destroyed.
- iOS XCTests assert that each view model is deallocated once released and that releasing a form or
  fuel-list view model returns its holder to `SwiftAppGraph`.
- Common tests assert, for `FuelEntryListStateHolder`, `FuelEntryFormStateHolder`,
  `VehicleListStateHolder` and `SessionStateHolder`, that `close()` leaves no active coroutine under the
  caller's job and leaves the caller's job active; for `SettingsStateHolder` the existing
  `closeCancelsTheHolderJob` test gains the assertion that the caller's job stays active.

## Consequences

### Positive

- Leaks and uncancelled holder work fail the build.

### Negative

- Each instrumented test with watched objects waits about four seconds for the retained-object check.

### Constraints Introduced

- A new Compose instrumented test class MUST use `leakDetecting(...)`; a build-logic guard enforces it.

## Verification

- `PerformanceHarnessContractTest.everyComposeInstrumentedTestDetectsLeaks`.
- The cancellation tests in `FuelEntryStateHoldersTest`, `VehicleStateHoldersTest`,
  `SessionStateHolderCancellationTest` and `SettingsStateHolderTest.closeCancelsTheHolderJob`.
- `ViewModelDeallocationTests` in `carAppTests`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-256`); `docs/CONTRACTS.md §14`; `D-84`
