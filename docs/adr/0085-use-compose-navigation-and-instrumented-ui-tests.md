# ADR-0085 / D-84 - Use Compose Navigation and Instrumented Android UI Tests

## Status

Accepted

Accepted by the owner on 2026-08-30.

## Context

E1-07 introduces the first complete Android product flow: Vehicle list, create/edit form and
detail shell. D-7 already selects native navigation, but it does not pin the Android navigation
artifact or the execution environment for the mandatory Compose UI test. A host-only test would
not exercise the Android activity, resources or real Compose semantics tree.

The official AndroidX release pages list Navigation 2.9.8 and AndroidX Test runner/rules 1.7.0 as
stable. Compose UI test artifacts remain managed by the pinned Compose BOM 2026.08.00.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Compose Navigation 2.x with BOM-managed Compose UI tests on an instrumented emulator | Implements D-7 directly; exercises the real Android host, resources and semantics; uses stable AndroidX releases. | Adds an emulator CI job and its startup cost. |
| Navigation 3 with instrumented tests | Compose-first API with newer navigation concepts. | Supersedes D-7 for no MVP benefit and increases migration risk for four destinations. |
| Manual destination state with Robolectric tests | Avoids emulator startup and a navigation dependency. | Reimplements navigation, adds an unapproved test tool and does not exercise the required instrumented path. |

## Decision

Use `androidx.navigation:navigation-compose:2.9.8`, `androidx.test:runner:1.7.0` and
`androidx.test:rules:1.7.0`. Manage `ui-test-junit4` and `ui-test-manifest` through the Compose BOM.
Run Vehicle creation acceptance on an API 36 x86_64 emulator in a protected
`android-instrumented-tests` CI job. Pin `ReactiveCircus/android-emulator-runner` v2.38.0 by the
immutable commit `a421e43855164a8197daf9d8d40fe71c6996bb0d`.

## Consequences

### Positive

- Android navigation follows the already accepted native-navigation decision.
- The acceptance test covers activity wiring, localized resources, semantics and navigation.
- Every version and external action is reviewable and reproducible.

### Negative

- CI gains a tenth protected check and an emulator boot dependency.
- The Navigation 2.x API is retained for the MVP even though Navigation 3 exists.

### Constraints Introduced

- Navigation 3 and Robolectric remain unapproved for the MVP.
- Android UI acceptance MUST run as an instrumented test in CI.
- The emulator action MUST remain pinned by immutable commit SHA.

## Verification

- `:androidApp:connectedDebugAndroidTest` runs the Vehicle creation flow on an emulator.
- `contractCheck` verifies the accepted decision and ADR mirrors.
- `docs/versions-matrix.md` and `gradle/libs.versions.toml` carry the exact pins.

## References

- ADR-0008 / D-7
- `docs/BACKLOG.md` E1-07
- `docs/CONTRACTS.md §18`
- AndroidX Navigation and Test stable release pages, reviewed 2026-08-30
