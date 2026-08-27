# ADR-0076 / D-75 - Exempt Firebase Standalone Native Tests

## Status

Accepted

## Context

E0-07 adds common unit tests to `:integration:firebase-auth` and
`:integration:firebase-firestore`. Both modules compile their production code for iOS through
GitLive 2.6.0 and Firebase Apple 11.8.0, but their standalone `iosSimulatorArm64Test` executables
fail to link with `framework 'FirebaseCore' not found`. GitLive does not supply the Apple Firebase
SDK transitively; the Xcode application receives those products from Swift Package Manager, while
a standalone Kotlin/Native test executable has no Xcode host from which to receive them. This is
the upstream limitation tracked by
[GitLive issue #499](https://github.com/GitLiveApp/firebase-kotlin-sdk/issues/499).

Kotlin's direct
[SwiftPM import](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html) is still
experimental and requires a Kotlin combination outside the deliberately pinned stack. Adding
CocoaPods only for tests would give the exact Firebase Apple 11.8.0 compatibility pin from D-65 a
second manager and a second place to diverge silently. Such divergence can compile and later fail
as a native `NSException`.

## Options Considered

| Option | Benefits | Costs and risks |
|--------|----------|-----------------|
| Exempt exactly the two current Firebase integration modules | Keeps the accepted Kotlin, GitLive, Firebase Apple and SwiftPM stack; real iOS code still runs through the signed Xcode host. | Loses standalone Kotlin/Native unit-test execution for those modules. |
| Add CocoaPods only for Native tests | Follows GitLive's current test-linking guidance. | Adds a second Firebase resolver beside SwiftPM and duplicates the D-65 pin. |
| Upgrade to beta Kotlin and experimental SwiftPM import | Could give standalone tests a single SwiftPM-based link path. | Moves Kotlin, Compose, SKIE, SQLDelight and AGP onto an unaccepted beta/experimental combination. |
| Maintain Firebase XCFrameworks for tests | Preserves the current Kotlin version. | Creates a second native artifact chain with manual transitive linking and independent drift. |

## Decision

The standalone Kotlin/Native test exemption set is exactly:

```text
:integration:firebase-auth
:integration:firebase-firestore
```

Their common unit tests continue to execute through `testAndroidHostTest`. Every other module with
an `iosSimulatorArm64Test` task continues to execute it. CI asserts equality with the exact set
above and fails if a module is added to or removed from it; a new integration module cannot inherit
the exception implicitly.

XCUITest is a partial compensating control, not equivalent coverage. The current UI acceptance
exercises these real iOS Firebase paths:

- Firebase Core and the local App Check debug provider initialize in the signed application;
- Firebase Auth creates an anonymous session;
- the GitLive Auth state reaches the shared state holder and visible SwiftUI state;
- Firebase Auth restores that session from the application's Keychain after a real process
  termination and relaunch.

It does **not** exercise Firestore reads, writes, Vehicle serialization or backup, Auth failure and
collision paths, or any Firebase path not reached by that UI flow. It also cannot replace a
standalone compiler test: a Native-compiler-specific failure in either integration module remains
undetected by that route. The real-host coverage list MUST be reviewed whenever the Firebase
surface grows.

## Consequences

### Positive

- One Firebase Apple dependency manager and one exact D-65 compatibility pin remain authoritative.
- Provider-free common code and every non-exempt module retain Android-host and Kotlin/Native test
  execution.
- The exact-set guard prevents accidental expansion and accidental disappearance of the exception.

### Negative

- The two Firebase modules lose unit tests running as standalone Kotlin/Native binaries.
- A Native-compiler-specific failure in those modules is not caught by that route.
- XCUITest partially compensates by executing real Native code, but only on the four listed paths;
  the uncovered Firebase paths remain explicit above.

## Expiry and Review

D-75 uses the existing D-63/TD-01 quarterly review owned by David Ruiz, beginning 2026-12-01. It
does not create another review cycle. Each review watches both:

1. GitLive issue #499, including any release that supplies supported transitive Apple linking.
2. SwiftPM import reaching stable in a Kotlin release compatible with the pinned project stack.

The exception expires when either cause is resolved. A GitLive release with bindings for Firebase
Apple 12.x or another successor triggers a joint D-65/D-75 evaluation, because the compatibility
migration may also remove the test-linking exception.

## Verification

- The protected `contract-check` asserts the exact two-module exemption set.
- The `shared-tests` job runs Android-host tests for all KMP modules, Native tests for every
  non-exempt module and no standalone Native test for either exempt module.
- The provider-free job still runs both Android-host and Native tests because Firebase integration
  projects are absent from that graph.
- ADR-0066 cross-references this decision, and TD-01 records both expiry signals.

## References

- [ADR-0066](0066-pin-firebase-apple-to-gitlive-bindings.md) (`D-65`)
- [ADR-0075](0075-use-xcuitest-for-keychain-persistence-acceptance.md) (`D-74`)
- `docs/TECHNICAL_PLAN.md §13` (`TD-01`)
- [GitLive issue #499](https://github.com/GitLiveApp/firebase-kotlin-sdk/issues/499)
- [Kotlin SwiftPM import documentation](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)
