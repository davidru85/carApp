# ADR-0110 / D-109 - Enforce Native Locale-Provider Verification

## Status

Accepted

Selected during the E1-10 owner review on 2026-09-01 and completed by E1-13 on 2026-09-03.

## Context

E1-10 added native locale adapters under D-108. `AndroidLocaleProviderTest` existed under
`:androidApp`, but the canonical non-instrumented command and CI `shared-tests` job ran only KMP
Android-host tasks. Android application modules expose `testDebugUnitTest`, not
`testAndroidHostTest`, so the test never executed in standard verification.

The Foundation adapter has a different constraint. `:composition:ios:iosSimulatorArm64Test` is in
the exact D-75 exclusion set because its standalone binary transitively links the Firebase
integrations without an Xcode host. The framework link proves compilation and composition, but a
source assertion or link does not execute the `NSNumberFormatter.maximumFractionDigits` path.

E1-13 must therefore execute the same production source from a standard-command Native test
compilation that does not introduce a project dependency on `:composition:ios`. The route must
also keep the provider internal, preserve the closed Swift ABI and make source/test drift fail a
guard rather than silently losing coverage.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **Selected: compile the exact composition-owned adapter source into `:shared` `iosTest`** | Executes the production implementation under the existing canonical `iosSimulatorArm64Test`; keeps the file, provider construction and native APIs owned by `:composition:ios`; adds no module edge, framework runtime, Swift export or Firebase route | `shared/build.gradle.kts` contains an explicit test-only source path into the composition owner; a guard is required so a move cannot silently drop coverage |
| Export a Swift test facade and execute the provider through XCTest | Uses the existing Xcode host and Firebase SwiftPM graph; tests could stay beside other Swift tests | Expands the contractually closed `Shared` ABI with test-only surface or needs configuration-dependent headers; couples coverage to an exported seam that production callers must never use |
| Move the adapter into a dedicated testable KMP module | Gives the adapter a conventional direct Native-test owner and avoids cross-project source reuse | Changes the canonical module graph and D-108 host ownership solely for test topology; requires new architecture rules and a reviewed production dependency |

Adding CocoaPods, experimental direct SwiftPM import or committed Firebase XCFrameworks remains
excluded by D-75 and is not a fourth E1-13 option without a superseding owner decision.

## Decision

The canonical non-instrumented command and the CI `shared-tests` job include
`:androidApp:testDebugUnitTest`. This makes `AndroidLocaleProviderTest` mandatory without changing
the protected job topology or applying the KMP Kover convention to the Android application.

For iOS, `IosLocaleProvider.kt` remains an internal source owned by `:composition:ios`. The
`:shared` `iosTest` compilation adds that exact source directory without a Gradle project
dependency, then `IosLocaleProviderTest` invokes the provider with concrete Foundation `NSLocale`
instances. The provider accepts an internal locale factory whose production default remains
`NSLocale.currentLocale`, so tests can select deterministic locales without changing production
construction.

`IosCompositionContractTest.iosHostLocaleProviderTestsRunInCanonicalVerification` guards the
source reuse, behavior-test file and root Native task mirrors. The unchanged canonical
`iosSimulatorArm64Test` invocation therefore executes the behavior outside the excluded
`:composition:ios:iosSimulatorArm64Test` binary.

## Consequences

### Positive

- Android native currency code and fraction-digit behavior now fail canonical local and CI runs.
- iOS Foundation currency, fraction-digit, language-tag and region behavior now fail canonical
  local and CI runs.
- Production ownership, explicit injection and the closed Swift ABI are unchanged.
- D-75 and D-108 remain intact instead of being weakened for convenience.

### Negative

- The canonical command gains one Android application unit-test task.
- `:shared` test configuration intentionally references a source directory owned by another
  project, so the build-logic guard must change with any future source move.

### Constraints Introduced

- `AGENTS.md`, every current-command mirror and the CI unit-test job MUST include
  `:androidApp:testDebugUnitTest`.
- Android native locale-provider acceptance MUST rely on an executed test, not source inspection.
- The iOS behavior test MUST compile the exact composition-owned adapter source and execute under
  the canonical root `iosSimulatorArm64Test`; a copied implementation or source-only assertion is
  insufficient.
- The adapter MUST remain internal to the D-108 host boundary, and its production locale factory
  MUST remain `NSLocale.currentLocale`.
- D-75's graph-derived exclusion and sole Firebase Apple dependency authority remain unchanged.

## Verification

- `AndroidLocaleProviderTest` runs under `:androidApp:testDebugUnitTest`.
- `IosLocaleProviderTest` runs four behavior tests under `:shared:iosSimulatorArm64Test`: supported
  `USD`, non-two-decimal `JPY` fallback to `EUR`, `es_ES` to `es-ES` language-tag conversion and
  `en_US` to `US` region extraction.
- `IosCompositionContractTest.androidHostLocaleProviderTestsRunInCanonicalVerification` and
  `iosHostLocaleProviderTestsRunInCanonicalVerification` keep both native routes and command
  mirrors aligned.
- `contractCheck` proves D-109 and ADR status parity across all four mirrors.
- The exact complete non-instrumented command in `AGENTS.md` executes the iOS behavior tests while
  retaining the four D-75 exclusions.

## References

- ADR-0076 / D-75
- ADR-0109 / D-108
- `AGENTS.md` §`Build and verify`
- `docs/BACKLOG.md` E1-13
