# ADR-0102 / D-101 - iOS Deployment Target and ObservableObject Lifecycle

## Status

Accepted

Selected by the owner on 2026-09-01 for story E1-09.

## Context

Story E1-09 delivers the native iOS user interface for Vehicle and Fuel Entry flows. The repository
targets iOS devices while maintaining compatibility with the Phase 0 toolchain and Swift conventions.
SwiftUI offers multiple state modeling approaches: iOS 16 `@MainActor ObservableObject` with `@Published`
properties, versus iOS 17 `@Observable` macro. Furthermore, Kotlin StateHolders exposed via SKIE
produce coroutine-backed StateFlows that must be observed safely on the main thread and released when
the screen or form is dismissed to satisfy D-90 / D-91 bounding constraints.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| `IPHONEOS_DEPLOYMENT_TARGET = 16.0` with `@MainActor ObservableObject` screen models, single observation task in `init`, cancellation in `deinit`, keyed release call | Supports iOS 16+ devices; standard, battle-tested observation pattern without macro dependencies; strict scope cancellation and holder release. | Requires boilerplate `ObservableObject` and `Task.cancel()` in `deinit`. |
| `IPHONEOS_DEPLOYMENT_TARGET = 17.0` with `@Observable` macro | Cleaner syntax with less boilerplate. | Drops iOS 16 support; risks compiler or tooling friction with earlier Swift toolchains. |
| Direct view observation of SKIE flows without ViewModel layer | Minimizes intermediate classes. | Blurs presentation and view boundaries, mixes asynchronous lifecycle management into SwiftUI view structs, and complicates unit testing. |

## Decision

Set `IPHONEOS_DEPLOYMENT_TARGET = 16.0` across the project and test targets. Screen models are
structured as `@MainActor final class ...: ObservableObject`. Each ViewModel subscribes to its
underlying Kotlin StateHolder via a single `Task { for await state in holder.state }` created in
`init` and explicitly cancelled in `deinit`. Keyed StateHolders (`VehicleFormStateHolder`,
`FuelEntryFormStateHolder`) call `close()` and the corresponding `SwiftAppGraph.release...` function
in `deinit`. The iOS 17 `@Observable` macro is NOT used.

## Consequences

### Positive

- Retains broad iOS 16+ device reach.
- Deterministic lifecycle: holders and observation coroutines are active only while the view model is alive.
- Conforms strictly to D-90 / D-91 resource cleanup requirements.
- View models are cleanly testable via unit tests in isolation from SwiftUI rendering.

### Negative

- Requires explicit `ObservableObject` definitions and manual task cancellation in `deinit`.

### Constraints Introduced

- ViewModels MUST be annotated with `@MainActor`.
- Every observation task created in `init` MUST be cancelled in `deinit`.
- Keyed holders MUST be closed and released in `deinit`.
- The `@Observable` macro MUST NOT be used for screen models.

## Verification

- `ViewModelLifecycleTests` validates initial state, field editing, validation error flags, and save completions.
- Deallocation and cancellation are confirmed through unit test teardown.

## References

- ADR-0091 / D-90
- ADR-0092 / D-91
- `docs/BACKLOG.md` E1-09
