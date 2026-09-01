# ADR-0103 / D-102 - iOS Unit and UI Test Targets in CI

## Status

Accepted

Selected by the owner on 2026-09-01 for story E1-09.

## Context

Prior to story E1-09, the Xcode project had a single UI test target (`carAppUITests`) containing only
the D-74 Keychain persistence test. E1-09 introduces Swift helpers for scaled value formatting,
calendar day conversions, UI error message localization mapping, and ViewModel lifecycle management.
Per `SPECIFICATION.md §11`, non-UI Swift helpers require unit tests following TDD. Furthermore, the
complete Vehicle and Fuel Entry user flows require automated UI testing on iOS Simulator. The protected
GitHub Actions CI job `ios-simulator-build` must execute both unit and UI tests without altering the
names of protected status checks enforced on branch `main`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Dedicated `carAppTests` unit test target + extended `carAppUITests`, both run in protected `ios-simulator-build` | Clean separation of fast unit tests from slow UI tests; automated execution on CI; zero change to protected branch check names. | Increases CI duration slightly for simulator execution. |
| Put all unit tests into `carAppUITests` | Single test bundle. | Launches the full simulator app harness for pure algorithmic unit tests, slowing down test cycles dramatically. |
| Create a new CI job `ios-tests` | Dedicated visibility for testing. | Requires modifying protected branch rules in GitHub repository settings, violating governance. |

## Decision

Configure a dedicated unit test target `carAppTests` in `iosApp/project.yml` for fast unit tests
(`ScaledFormattingTests`, `CalendarDayTests`, `UiMessageMappingTests`, `ViewModelLifecycleTests`),
alongside the existing `carAppUITests` target containing end-to-end UI tests (`VehicleAndFuelFlowUITests`,
`CarAppKeychainPersistenceUITests`). Both targets are included in the `carApp` scheme and executed
during the protected `ios-simulator-build` CI workflow job. The protected status check name
`ios-simulator-build` remains strictly unchanged.

## Consequences

### Positive

- Fast, isolated execution for Swift unit tests.
- Continuous automated verification of iOS user flows and persistence on CI.
- GitHub branch protection rules remain intact without manual admin intervention.

### Negative

- CI runner executes simulator tests, requiring simulator runtime availability.

### Constraints Introduced

- The protected CI check name `ios-simulator-build` MUST NOT be renamed.
- Swift formatting, message mapping, and calendar day helpers MUST be covered by unit tests in `carAppTests`.

## Verification

- `xcodebuild -project iosApp/carApp.xcodeproj -scheme carApp -sdk iphonesimulator test` passes both `carAppTests` (15 unit tests) and `carAppUITests`.
- CI workflow `ios-simulator-build` step executes `xcodebuild test` successfully.

## References

- ADR-0075 / D-74
- `docs/SPECIFICATION.md §11`
- `docs/BACKLOG.md` E1-09
