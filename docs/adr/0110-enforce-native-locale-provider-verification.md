# ADR-0110 / D-109 - Enforce Native Locale-Provider Verification

## Status

Accepted

Selected during the E1-10 owner review on 2026-09-01.

## Context

E1-10 added native locale adapters under D-108. `AndroidLocaleProviderTest` existed under
`:androidApp`, but the canonical non-instrumented command and CI `shared-tests` job ran only KMP
Android-host tasks. Android application modules expose `testDebugUnitTest`, not
`testAndroidHostTest`, so the test never executed in standard verification.

The Foundation adapter has a different constraint. `:composition:ios:iosSimulatorArm64Test` is in
the exact D-75 exclusion set because its standalone binary transitively links the Firebase
integrations without an Xcode host. The framework link proves compilation and composition, but a
source assertion or link does not execute the `NSNumberFormatter.maximumFractionDigits` path.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Add the Android application unit-test task now and register the iOS executable gap as E1-13 | Enforces the available Android behavior immediately; preserves D-108 host ownership and D-75's single Firebase Apple dependency route; gives the iOS gap an explicit owner | iOS runtime behavior remains uncovered until E1-13 |
| Move the Foundation adapter logic into a KMP module whose Native tests already run | Could execute the behavior from the current standard command | Moves native host composition logic away from the D-108 boundary solely for test topology and would expose or duplicate Foundation-specific behavior |
| Add CocoaPods, direct SwiftPM import or maintained Firebase XCFrameworks to link the composition test binary | Could execute `:composition:ios:iosSimulatorArm64Test` directly | Reopens the dependency routes rejected by D-75 and creates a second Firebase Apple version authority |

## Decision

The canonical non-instrumented command and the CI `shared-tests` job include
`:androidApp:testDebugUnitTest`. This makes `AndroidLocaleProviderTest` mandatory without changing
the protected job topology or applying the KMP Kover convention to the Android application.

E1-10 records the iOS behavioral gap explicitly. E1-13 must add an executable standard-command
route that exercises the production `IosLocaleProvider` Foundation fraction-digits behavior while
preserving D-108 and D-75. A framework link and a source-text contract are not behavioral evidence.

## Consequences

### Positive

- Android native currency code and fraction-digit behavior now fail canonical local and CI runs.
- The complete verification command matches the actual Android Gradle task model.
- D-75 and D-108 remain intact instead of being weakened for convenience.
- The iOS gap has a concrete backlog owner and closure criteria.

### Negative

- E1-10 cannot claim executable iOS locale-provider behavior coverage.
- The canonical command gains one Android application unit-test task.
- E1-13 must design a host-executable route without using the excluded standalone composition
  test binary.

### Constraints Introduced

- `AGENTS.md`, every current-command mirror and the CI unit-test job MUST include
  `:androidApp:testDebugUnitTest`.
- Android native locale-provider acceptance MUST rely on an executed test, not source inspection.
- Until E1-13 completes, documentation MUST distinguish the iOS framework link from behavioral
  provider coverage.
- E1-13 MUST preserve the D-75 dependency rule and the D-108 host boundary.

## Verification

- `AndroidLocaleProviderTest` runs under `:androidApp:testDebugUnitTest`.
- `IosCompositionContractTest.androidHostLocaleProviderTestsRunInCanonicalVerification` keeps the
  canonical command and CI job aligned.
- `contractCheck` proves D-109 and ADR status parity across all four mirrors.
- `docs/BACKLOG.md` contains E1-13 with executable iOS behavior acceptance criteria.

## References

- ADR-0076 / D-75
- ADR-0109 / D-108
- `AGENTS.md` §`Build and verify`
- `docs/BACKLOG.md` E1-13
