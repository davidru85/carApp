# Agent Handoff - E1-03

## Story

`E1-03 - Vehicle Data, Local Only - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — implement the local Vehicle data source, mappers and complete
  `VehicleRepository` implementation in `:feature:vehicle`.
- [x] Acceptance criteria reviewed — mapper round trips; `OwnerContext` stamping without
  `AuthClient`; `PENDING` create, update and tombstone writes; fresh shared
  `localMutationSeq` values; no `LOCAL_OWNER` outbox rows; transactional Vehicle and FuelEntry
  tombstones; and no Firebase or GitLive types.
- [x] Dependencies checked — E1-01, E0-07 and E1-02 are complete. E1-01 provides SQLDelight schema
  v1 and the D-38 mutation facade; E0-07 provides the removable runtime and final module topology;
  E1-02 provides the canonical repository contract and D-76 pure validators.
- [x] Decisions checked — D-1 selects SQLDelight; D-17 selects Turbine; D-18 requires 85% feature
  coverage; D-19 fixes `Outcome`; D-28 defers feature package-rule enforcement to E1-07; D-36
  fixes the database driver; D-38 fixes `DatabaseMutations` as the synchronized-entity write
  boundary; D-55 fixes staged ownership; and D-76 requires transactional fact loading, validation
  and mutation. Every applicable decision is `Accepted`, and no `Proposed` or `Pending` row blocks
  the story.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §3`, `§5.1`, `§6 R-4`, `§8.3`, `§9`,
  `§11`; `docs/CONTRACTS.md §3`, `§5`, `§6`, `§8`, `§12`, `§13`, `§20.2`, `§20.3.2`, `§20.4`,
  `§20.5`; `docs/DECISION_BOARD.md` D-1, D-17, D-18, D-19, D-28, D-36, D-38, D-55 and D-76;
  `docs/TECHNICAL_PLAN.md §4`, `§6`, `§10`, `§12`; and ADR-0077.
- [x] Expected verification identified — focused `:feature:vehicle` and `:core:database`
  Android-host tests; `:feature:vehicle` `iosSimulatorArm64` tests, lint, detekt and Kover;
  architecture fixtures and contract checks; the complete repository command from `AGENTS.md`;
  and `git diff --check`.
- [x] Human review gates identified before work — `core/database/**` is a gated path and requires
  owner review before merge. No gated normative-document change is expected.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Added SQLDelight-backed Vehicle observation, mapping and repository operations in
  `:feature:vehicle`.
- Added owner-scoped database queries and D-38 mutations for Vehicle update and transactional
  Vehicle/FuelEntry tombstoning.
- Kept SQLDelight-generated types and transaction ownership inside `:core:database` through a
  neutral Vehicle database access boundary.
- Replaced the E0-07 local Vehicle adapter with the complete E1-03 repository while retaining its
  staged remote push and recovery orchestration.

## Acceptance Evidence

- RED phase: 57 Android-host tests compiled and executed; 26 focused E1-03 tests failed for absent
  mapper, observation, create, update, delete, error-boundary and transaction behavior, while all
  31 pre-existing tests passed.
- GREEN phase: all 57 Vehicle tests passed unchanged on Android host and `iosSimulatorArm64`; the
  existing `:core:database` Android-host suite also remained green after extending D-38.
- Mapper tests cover row/domain round trips. Repository tests cover owner changes during
  observation, deleted-row filtering, single-entity absence, normalisation, immutable metadata,
  `PENDING` state, shared mutation sequences, duplicate-name validation and persistence errors.
- Local-owner tests prove create, update and cascade delete produce no outbox rows. Permanent-owner
  tests prove complete snapshots and coalescing preserve outbox order.
- Transaction tests prove D-76 fact loading, validation and mutation share one transaction, and
  prove both create and Vehicle/FuelEntry cascade changes roll back atomically on failure.
- Source boundaries and `architectureCheck` prove `:feature:vehicle` references neither
  `AuthClient`, Firebase, GitLive nor SQLDelight-generated row types.

## Out of Scope / Not Done

- E1-04 FuelEntry domain behavior and E1-06 FuelEntry persistence beyond the deletion cascade
  required by E1-03.
- E1-07 presentation and executable feature package-layer rules.
- E2-06 local-owner adoption and E3-03 outbox processing or remote synchronization.

## Files Changed

- `core/database/src/commonMain/sqldelight/com/ruizurraca/carapp/core/database/database.sq`
- `core/database/src/commonMain/kotlin/com/ruizurraca/carapp/core/database/DatabaseMutations.kt`
- `core/database/src/commonMain/kotlin/com/ruizurraca/carapp/core/database/DatabaseReadQueries.kt`
- `core/database/src/commonMain/kotlin/com/ruizurraca/carapp/core/database/VehicleDatabaseAccess.kt`
- `feature/vehicle/build.gradle.kts`
- `feature/vehicle/src/commonMain/kotlin/com/ruizurraca/carapp/feature/vehicle/data/**`
- `feature/vehicle/src/commonTest/kotlin/com/ruizurraca/carapp/feature/vehicle/data/**`
- `shared/src/commonMain/kotlin/com/ruizurraca/carapp/VehicleSliceRuntime.kt`
- `AGENTS.md`, `README.md`, `docs/DEFINITION.md`, `docs/BACKLOG.md`, `docs/PROJECT_LOG.md` and this
  handoff.

## Decisions Made

- No new product, representation, technology or build-model decision was required.
- SQLDelight query definitions use the `docs/SPECIFICATION.md §11` schema exemption. Every
  observable repository behavior was nevertheless specified by an executing RED test before the
  query or implementation that made it pass.
- The first GREEN architecture run exposed feature-data references to SQLDelight-generated types.
  Refactoring introduced a neutral `:core:database` access boundary; this applies the existing
  D-38 and module-boundary rules and does not introduce a new decision.
- The owner required the story branch to start from freshly synchronized `main`; the initial branch
  was safely recreated from `origin/main` before RED implementation began.

## Verification Run

- `./gradlew :feature:vehicle:testAndroidHostTest` during RED — failed as required: 57 tests
  executed, 26 failed for deliberately absent E1-03 behavior and 31 pre-existing tests passed.
- `./gradlew :feature:vehicle:testAndroidHostTest` after GREEN — successful; all 57 tests passed.
- `./gradlew :core:database:testAndroidHostTest :feature:vehicle:iosSimulatorArm64Test` after GREEN
  — successful; the database regression suite and all 57 Vehicle tests passed on Kotlin/Native.
- `./gradlew :core:database:ktlintCheck :core:database:detekt :feature:vehicle:ktlintCheck
  :feature:vehicle:detekt :feature:vehicle:koverVerify :shared:ktlintCheck :shared:detekt
  architectureCheck contractCheck` after REFACTOR — successful; 16 architecture rules across 23
  modules and all 77 decision/ADR mirrors passed.
- `./gradlew :core:database:testAndroidHostTest :feature:vehicle:testAndroidHostTest
  :feature:vehicle:iosSimulatorArm64Test :shared:testAndroidHostTest` after REFACTOR — successful.
- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
  koverVerify :androidApp:assembleDebug testAndroidHostTest iosSimulatorArm64Test -x
  :integration:firebase-auth:iosSimulatorArm64Test -x
  :integration:firebase-firestore:iosSimulatorArm64Test -x
  :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test` after
  documentation closure — successful; all 583 actionable tasks completed, including lint, detekt,
  16 architecture rules, 77 decision/ADR contract mirrors, coverage, Android assembly,
  Android-host tests and required iOS simulator tests.
- `git diff --check` — successful.
- Source scan of the Vehicle data package for `AuthClient`, Firebase, GitLive, SQLDelight imports
  and generated row imports — no matches.

## Contract Impact

- No contract changes. E1-03 implements the existing `VehicleRepository`, persistence and outbox
  contracts without changing their public shapes.

## Decision Board Impact

- No decision changes.

## Shared-Write Modules Touched

- `:core:database` — E1-03 is the only story modifying it during this work.

## Project Log Entry

- [x] Entry appended.

## Risks or Follow-ups

- The E0-07 remote push and recovery orchestration remains deliberately staged. E3-02 and E3-03
  replace it with the complete backup engine and outbox processor.
- E1-06 owns Fuel Entry persistence beyond the deletion cascade required here. E1-07 owns Vehicle
  presentation and executable package-level feature rules.

## Human Review Gate

- Applies: `core/database/**` is a gated path. Owner review is required before merge.
