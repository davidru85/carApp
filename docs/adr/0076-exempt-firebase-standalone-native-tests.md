# ADR-0076 / D-75 - Derive Firebase Standalone Native-Test Exemptions

## Status

Accepted

## Context

E0-07 adds common unit tests to `:integration:firebase-auth` and
`:integration:firebase-firestore`. Their standalone `iosSimulatorArm64Test` executables fail to
link with `framework 'FirebaseCore' not found`: GitLive 2.6.0 does not supply Firebase Apple
dependencies transitively, and a standalone Kotlin/Native test binary has no Xcode host from which
to receive the Swift Package Manager products. The upstream limitation is tracked by
[GitLive issue #499](https://github.com/GitLiveApp/firebase-kotlin-sdk/issues/499).

The first full verification after D-75 initially approved a two-module exemption failed in
`:wiring:firebase`. That module transitively depends on both integrations, so its Native test
binary inherits the same linker requirement. `:composition:ios` has the same transitive chain;
Gradle creates its empty Native test binary even though the module currently has no test sources.
The exception therefore propagates by transitive closure. A hand-maintained permanent list states
the symptom, not the rule, and would become stale again when the graph changes.

Kotlin's direct
[SwiftPM import](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html) remains
experimental and requires a Kotlin combination outside the deliberately pinned stack. Adding
CocoaPods only for tests would give the exact Firebase Apple 11.8.0 compatibility pin from D-65 a
second manager and a second place to diverge silently; such divergence can compile and later fail
as a native `NSException`.

## Options Considered

| Option | Benefits | Costs and risks |
|--------|----------|-----------------|
| Derive the exemption from the transitive project graph | Expresses the actual linker rule, admits future graph changes through a failing exact comparison and keeps the accepted stack. | Every transitive Native-test consumer loses its standalone binary route until the upstream cause expires. |
| Split pure wiring from concrete Firebase constructors | Could retain standalone Native tests for a newly isolated pure wiring boundary. | Moves architectural boundaries solely to work around a temporary third-party limitation that disappears when GitLive resolves issue #499; boundaries moved for transient tooling constraints age badly. |
| Add CocoaPods, beta Kotlin SwiftPM import or maintained Firebase XCFrameworks | Could link every standalone Native test binary. | Reopens K2 through K4 without a material change: each option adds a duplicate or experimental dependency path already rejected by the owner. |

W2 is rejected on proportionality and architectural durability. W3 is already settled by K2
through K4 and MUST NOT be relitigated without a material change in the underlying constraint.

## Decision

The rule is:

> A KMP module whose Kotlin/Native test binary transitively links
> `:integration:firebase-auth` or `:integration:firebase-firestore` cannot execute its standalone
> Native tests while GitLive does not provide the required Apple dependencies transitively.

CI derives the expected exemption set from the Native-test project dependency graph and compares
it with the paths explicitly excluded by the `shared-tests` command. Equality is required: a
qualifying module absent from the declaration and a declared module that no longer qualifies both
fail. The declared paths are therefore a reviewed snapshot of the rule's current resolution, not a
second static source of truth.

The current resolution is:

| Module | Why it qualifies | Current coverage loss |
|--------|------------------|-----------------------|
| `:integration:firebase-auth` | Root Firebase integration module. | Its common unit tests run on Android host but not as a standalone Native binary. |
| `:integration:firebase-firestore` | Root Firebase integration module. | Its common unit tests run on Android host but not as a standalone Native binary; Firestore read and write paths have no Native-side automated iOS coverage. |
| `:wiring:firebase` | Transitively depends on both roots. | Its provider-assembly unit tests run on Android host but not as a standalone Native binary. |
| `:composition:ios` | Transitively depends on `:wiring:firebase`. | No coverage is lost today because it has no test sources; only Gradle's empty test binary is suppressed. If the module gains tests, that is a larger loss and triggers an explicit coverage review. |

Every KMP module outside the derived set continues to execute `iosSimulatorArm64Test`.

## Compensating Coverage and Its Gap

XCUITest is a partial compensating control, not equivalent coverage. The current real iOS UI
acceptance exercises:

- `createSwiftAppGraph` in `:composition:ios`;
- concrete provider construction in `:wiring:firebase`;
- Firebase Core, App Check, Auth and Firestore initialization in the signed application;
- anonymous Firebase Auth sign-in and propagation to visible SwiftUI state;
- Firebase Auth session restoration from the application's Keychain after real process
  termination and relaunch.

It performs **no Firestore operations**. Firestore read and write paths have no Native-side
automated coverage on iOS at present. It also does not cover Auth failure or collision paths, or
any Firebase path not reached by that UI flow. A Native-compiler-specific failure in the three
modules that currently contain tests remains undetected by the standalone route. This coverage
list MUST be reviewed whenever the Firebase surface grows.

## Consequences

### Positive

- One Firebase Apple dependency manager and one exact D-65 compatibility pin remain authoritative.
- The guard follows dependency-graph growth instead of silently inheriting a stale list.
- Provider-free common code and every module outside the derived closure retain Android-host and
  Kotlin/Native test execution.

### Negative

- Auth, Firestore and provider-wiring unit tests do not run as standalone Kotlin/Native binaries.
- Firestore read and write paths have no Native-side automated iOS coverage today.
- XCUITest executes real Native code only along the explicitly listed paths.
- `:composition:ios` currently loses no tests, but adding tests there would enlarge the loss.

## Expiry and Review

D-75 uses the existing D-63/TD-01 quarterly review owned by David Ruiz, beginning 2026-12-01. It
does not create another review cycle. The watch signals remain unchanged:

1. GitLive issue #499, including any release that supplies supported transitive Apple linking.
2. SwiftPM import reaching stable in a Kotlin release compatible with the pinned project stack.

The exception expires when either cause is resolved. A GitLive release with bindings for Firebase
Apple 12.x or another successor triggers a joint D-65/D-75 evaluation, because the compatibility
migration may also remove the test-linking exception.

## Verification

- The protected `contract-check` derives the qualifying module set from the project graph and
  compares it with the `shared-tests` exclusions in both directions.
- Mutation tests reject both an omitted qualifying module and a stale additional declaration.
- Android-host tests run for all KMP modules; Native tests run for every module outside the derived
  closure.
- The provider-free job still runs both Android-host and Native tests because the Firebase
  integration projects are absent from that graph.
- ADR-0066 cross-references this decision, and TD-01 retains both expiry signals.

## References

- [ADR-0066](0066-pin-firebase-apple-to-gitlive-bindings.md) (`D-65`)
- [ADR-0075](0075-use-xcuitest-for-keychain-persistence-acceptance.md) (`D-74`)
- `docs/TECHNICAL_PLAN.md §13` (`TD-01`)
- [GitLive issue #499](https://github.com/GitLiveApp/firebase-kotlin-sdk/issues/499)
- [Kotlin SwiftPM import documentation](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html)
