# Implementation Backlog - Vehicle Expense Tracking MVP

Each story is designed as a deliverable unit for an AI agent: closed scope, explicit dependencies, and verifiable acceptance criteria.

Size guide: **S** up to half a day, **M** 1 to 2 days, **L** 3 to 5 days.

## Agent Conventions

- Normative reference: `SPECIFICATION.md`.
- If documents conflict, `SPECIFICATION.md` wins.
- No story is done without relevant tests, clean lint, and acceptance criteria evidence.
- Do not implement anything listed as out of scope.
- Do not violate architecture rules.
- Data model changes require migrations and migration tests.
- Monetary values never use `Float` or `Double`.

## Phase 0 - Foundations

Goal: a project skeleton that compiles on both platforms and has enforceable architecture boundaries.

### E0-01 - KMP Project Bootstrap - M

Create the KMP project with Android and iOS targets, Android host app, SwiftUI iOS host app, and `:shared` framework.

Acceptance criteria:

- Android debug app builds.
- iOS simulator app builds and shows text coming from `commonMain`.
- Build scripts use Kotlin DSL only.
- `gradle/libs.versions.toml` is the single source of dependency versions.

Blocks: all other stories.

### E0-02 - Gradle Convention Plugins - M

Create convention plugins for KMP libraries, features, Android application, Compose, Room, and SKIE.

Acceptance criteria:

- Creating a new module requires no more than five lines in its `build.gradle.kts`.
- SKIE is applied only to `:shared`.
- Test and Kotlin toolchain configuration is centralized.
- Plugins make future feature splitting possible without redesign.

### E0-03 - Base Core Modules - M

Create `:core:model`, `:core:common`, and `:core:testing`.

Acceptance criteria:

- `Money` uses integer minor units plus currency code.
- A test proves money arithmetic does not accumulate floating-point error.
- `Clock` is injectable.
- UUID generation is abstracted.
- `:core:model` has high coverage for value objects.

### E0-04 - Architecture Guards - M

Create Gradle task or tests validating module and package dependency rules.

Acceptance criteria:

- Feature `domain` dependency on Room, Firebase, Android, Ktor, data, or presentation fails the build with a clear message.
- Feature-to-feature dependency fails the build.
- `:core:sync` or `:shared` dependency on `:integration:*` fails the build.
- Feature `presentation` dependency on feature `data` fails the build.
- The check runs on every PR.

### E0-05 - Quality Tooling and CI - S

Configure ktlint, detekt, and CI.

Acceptance criteria:

- Style violations fail CI.
- Failing tests fail CI.
- Android and iOS simulator/shared framework verification run on macOS CI.
- CI target duration is under 20 minutes.

### E0-06 - ADRs D-0 through D-9 - S

Record all closed technical decisions as ADRs in `docs/adr/`.

Acceptance criteria:

- One accepted ADR exists per decision D-0 through D-9.
- Kotlin, SKIE, Xcode, Room, and GitLive versions are pinned.
- Version choices are reflected in the version catalog.

Blocks: E0-07.

### E0-07 - Walking Skeleton - L

Build a single screen crossing native UI, shared state holder, Room, Firestore, and real anonymous auth.

Acceptance criteria:

- A value written on Android appears on iOS after sync.
- A value written on iOS appears on Android after sync.
- `iosSimulatorArm64` runs in CI.
- iOS consumes the shared framework through direct SPM integration, not CocoaPods.
- Firestore offline persistence is disabled.

Decision gate:

- If Room KMP/KSP blocks iOS progress, switch to SQLDelight the same day.

Human review required.

## Phase 1 - Local Persistence

Goal: the app stores and displays vehicles and fuel entries locally. It is useful without remote sync.

### E1-01 - `:core:database` - M

Implement Room 3.0 KMP with bundled SQLite, schema v1, DAOs, transactions, and migration strategy.

Acceptance criteria:

- Database instantiates and persists on Android and iOS.
- Bundled SQLite is used.
- Tables include `vehicle`, `fuel_entry`, `outbox`, and `sync_cursor`.
- Synchronized entities include control columns.
- Outbox has `UNIQUE(entityType, entityId)` and preserves original `seq` when coalescing.
- A v1 migration test exists, even if trivial.
- Observable queries return `Flow`; one-shot queries are `suspend`.

### E1-02 - Vehicle Domain - S

Implement `:feature:vehicle` domain package: entity, repository interface, and use cases.

Acceptance criteria:

- Domain is Kotlin pure.
- Name validation and uniqueness rules are implemented.
- `initialOdometer` range is implemented.
- `fuelType` exists with default `GASOLINE`.
- Use cases have unit tests for success and errors.

### E1-03 - Vehicle Data, Local Only - M

Implement local data source, mappers, and repository implementation for vehicles.

Acceptance criteria:

- Mappers have round-trip tests.
- Created and edited rows become `PENDING`.
- Vehicle deletion is logical and cascades to fuel entries.
- No Firebase or GitLive type is referenced.

### E1-04 - Fuel Entry Domain - M

Implement `:feature:fuel` domain package, CRUD use cases, and rules R-1 and R-2.

Acceptance criteria:

- Price/total/liters derivation works for all valid input pairs.
- `totalCostMinor` is integer minor units.
- No monetary path uses `Float` or `Double`.
- Odometer inconsistency warns but allows save with marker.
- Future date beyond 1 hour is rejected.

### E1-05 - Consumption Calculation R-3 - M

Implement pure `CalculateConsumption` use case.

Acceptance criteria:

- Happy path with two full tanks.
- First full tank produces no consumption.
- Partial intermediate refuel contributes to next full segment.
- Segment with `hasMissedEntries` is invalid.
- Segment with `odometerInconsistent` is invalid.
- `distanceKm <= 0` is invalid and cannot divide by zero.
- Average consumption is distance-weighted, not arithmetic mean.
- 1,000 entries are processed in under 100 ms.

Human review required.

### E1-06 - Fuel Entry Data, Local Only - M

Implement local data source, mappers, and repository implementation for fuel entries.

Acceptance criteria:

- Queries support ordering by date and odometer.
- Created and edited rows become `PENDING`.
- Logical delete works.
- Mappers have round-trip tests.

### E1-07 - Android UI: Vehicles - M

Implement vehicle list, create/edit form, and detail shell with shared presentation state holder.

Acceptance criteria:

- State holder lives in `commonMain` and exposes `StateFlow<UiState>`.
- Loading, empty, and error states exist.
- No hardcoded user-facing strings.
- Spanish and English strings exist.
- Vehicle creation UI test exists.

### E1-08 - Android UI: Fuel Entries - L

Implement fuel entry list, create/edit form, segment consumption display, and average consumption display.

Acceptance criteria:

- Form defaults follow F-3.
- R-2 derived value recalculates while typing.
- Entries with no consumption show an accessible explanation.
- Empty consumption state follows the specification.

### E1-09 - iOS UI: Vehicles and Fuel Entries - L

Implement SwiftUI screens consuming shared state holders.

Acceptance criteria:

- No business logic is duplicated in Swift.
- Functional parity with Android for F-2 and F-3.
- Dynamic Type is usable for critical flows.

## Phase 2 - Authentication

### E2-01 - `:core:auth` - S

Implement auth interfaces and models.

Acceptance criteria:

- `AuthClient`, `TokenProvider`, `AuthSession`, and typed `AuthError` exist.
- No Firebase type appears in this module.

### E2-02 - Firebase Auth Integration - L

Implement anonymous, Google, Apple, credential linking, sign-out, account deletion, and token refresh.

Acceptance criteria:

- Anonymous login works on both platforms.
- Google works on both platforms.
- Apple works on iOS.
- Cancelled system dialogs produce typed errors.
- Native UI obtains credentials; common code exchanges them.

### E2-03 - Onboarding Flow F-1 - M

Implement welcome screen and provider selection.

Acceptance criteria:

- iOS offers Apple when Google is offered.
- Retry after network failure does not leave the UI stuck.
- Routing after authentication depends on whether vehicles exist.

### E2-04 - Anonymous Account Conversion F-4 - M

Implement account linking and credential collision handling.

Acceptance criteria:

- Successful linking preserves vehicles and fuel entries.
- Collision offers explicit destructive choice with data-loss count.
- Automatic merge is not implemented.

### E2-05 - Sign-Out and Account Deletion F-5 - M

Implement sign-out and account deletion.

Acceptance criteria:

- Sign-out warns about pending sync and offers to wait.
- Account deletion removes remote and local data after two-step confirmation.
- Account deletion is accessible from settings.

## Phase 3 - Backend and Synchronization

### E3-01 - Firestore Structure and Security Rules - M

Create Firestore rules, indexes, and emulator tests.

Acceptance criteria:

- User A cannot read or write under `users/B`.
- Writes with client-controlled `updatedAt` are rejected.
- Anonymous users can read/write under their own UID.
- Delta pull returns tombstones.
- Firestore offline persistence is disabled in client configuration.

Human review required.

### E3-02 - Firestore RemoteSyncSource - M

Implement Firestore remote sync integration.

Acceptance criteria:

- Writes use `serverTimestamp()`.
- Delta pull is paginated.
- Firestore errors map to typed app errors.
- Expired token refresh is retried once.
- No Firestore/GitLive type crosses the module boundary.

### E3-03 - `:core:sync` Engine - L

Implement outbox, cursor, push, pull, LWW, overlap window, backoff, observable sync status, and debug support.

Acceptance criteria:

- Required sync tests from `TECHNICAL_PLAN.md` pass.
- Deterministic convergence simulation exists with fixed seed.
- Debug screen exposes outbox, cursors, and row sync state.

Human review required.

### E3-04 - Repository Sync Wiring - M

Replace no-op remote sources with real sync wiring and platform triggers.

Acceptance criteria:

- UI still observes only local database flows.
- Foreground, connectivity recovery, post-write debounce, pull-to-refresh, and periodic triggers exist.
- No state holder changes are required for sync correctness.

### E3-05 - Sync Status UI - S

Add non-intrusive sync status.

Acceptance criteria:

- Pending, syncing, failed, and retry states are represented.
- Failed state offers manual retry.
- Normal offline use is not alarming.

### E3-06 - Provider Decoupling Proof - S

Make P4 executable.

Acceptance criteria:

- Excluding `:integration:*` and `:wiring:firebase` leaves `:core:*` and `:feature:*` compiling and testing with fakes.
- Check runs in CI.

## Phase 4 - MVP Hardening

### E4-01 - Settings - S

Implement minimal settings.

Acceptance criteria:

- Currency, units, session, account deletion, and app version are visible.
- Settings are persisted locally and synchronized where applicable.

### E4-02 - Accessibility and Localization - M

Harden accessibility and ES/EN localization.

Acceptance criteria:

- TalkBack and VoiceOver audits pass for F-1 through F-3.
- UI is usable at 200% font size.
- Spanish and English strings are complete.
- No hardcoded user-facing strings remain.

### E4-03 - Performance and Reliability Hardening - M

Measure and fix MVP performance.

Acceptance criteria:

- Cold start target is met.
- 1,000-entry list remains smooth.
- Consumption calculation performance target is met.
- No memory leaks in critical flows.

### E4-04 - Release Preparation - M

Prepare release assets and store requirements.

Acceptance criteria:

- App icons and splash are present.
- Privacy policy and store privacy labels are prepared.
- Release builds are installable on both platforms.
- Account deletion and Apple sign-in requirements are satisfied.

## Execution Order

```text
Phase 0
  -> E0-07 walking skeleton gate
      -> Phase 1 local persistence
      -> Phase 2 auth can overlap with late Phase 1
      -> Phase 3 can start E3-01 earlier, but sync wiring depends on Phase 1 and 2
      -> Phase 4
```

## Human Review Gates

- Phase 0 closure.
- E0-07 walking skeleton.
- E1-05 consumption calculation.
- E3-01 Firestore rules.
- E3-03 sync engine.
- Any stack, scope, backend, auth, sync, architecture, or money representation change.
