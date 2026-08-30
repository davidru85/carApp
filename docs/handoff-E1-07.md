# Agent Handoff - E1-07

## Story

`E1-07 - Android UI: Vehicles - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — implement the Android Vehicle list, create/edit form and detail
  shell backed by shared presentation state holders, and activate the D-28 feature package rules.
- [x] Acceptance criteria reviewed — state holders live in `commonMain`, take a caller-owned
  `CoroutineScope` on the Kotlin-facing graph, expose idempotent `close()`, use the injected
  `DispatcherProvider` and publish on `dispatchers.main`; `UiState` carries no display copy;
  loading, empty and typed-error states exist; Android maps codes to English and Spanish resources
  with no hardcoded user-facing strings; a Vehicle creation UI test exists; the production list
  requests `includeDeleted = false` and never exposes tombstones; the form preserves `fuelType`
  while rendering no selector; and D-28 supplies one firing fixture for each feature `domain`,
  `data` and `presentation` package rule.
- [x] Dependencies checked — E1-01 through E1-06 and E0-07 are complete. E1-02 provides the
  Vehicle commands and repository contract, E1-03 provides the local repository implementation,
  E0-07 provides the staged graph and Android host, and E1-06 leaves `E1-07` as the next story.
- [x] Decisions checked — D-3 fixes constructor injection and Koin to wiring; D-4 keeps
  `fuelType` persisted but absent from the MVP form; D-7 keeps navigation native; D-8 fixes shared
  state holders; D-16 and D-28 select Konsist and assign the three package rules to E1-07; D-17
  selects Turbine; D-18 requires 85% feature-domain coverage; D-20 fixes native localization;
  D-55 limits the E0-07 staged behavior; D-76 fixes the pure Vehicle validation boundary; and
  owner-approved D-84 through D-88 fix the Android UI stack, feature presentation ownership,
  graph separation, reactive edit facts and the pre-E3-03 sync-status exception. Every dependency
  is `Accepted`; no unresolved decision blocks the story.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §3`, `§5.1`, `§7 F-2`, `§8.3`, `§8.4`
  and `§11`; `docs/CONTRACTS.md §6`, `§12`, `§14`, `§15`, `§20.2`, `§20.4`, `§20.5` and
  `§20.10`; `docs/DECISION_BOARD.md` D-3, D-4, D-7, D-8, D-16, D-17, D-18, D-20, D-28, D-55 and
  D-76 and D-84 through D-88; `docs/TECHNICAL_PLAN.md §3`, `§4`, `§10` and `§12`; ADR-0017,
  ADR-0029 and ADR-0085 through ADR-0089; the E0-07,
  E1-03 and E1-06 handoffs; and the non-normative Android design references indexed by
  `docs/DESIGN.md §4`.
- [x] Expected verification identified — baseline architecture and contract checks; RED/GREEN
  Android-host and iOS shared-state tests; RED/GREEN D-28 fixture tests; Android Compose UI tests;
  focused feature/shared lint, detekt and Kover; Android assembly; provider-free graph tests; the
  complete repository command from `AGENTS.md`; the Shared framework link; `git diff --check`; and
  an Android emulator smoke check when an emulator is available.
- [x] Human review gates identified before work — module boundaries and dependency rules, the
  Swift-facing API surface and pinned versions are gated topics. Owner review is required before
  merge; the agent MUST NOT merge the pull request.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Pending implementation.

## Acceptance Evidence

- Pending implementation and verification.

## Out of Scope / Not Done

- E1-08 and later Fuel Entry UI, complete synchronization, authentication and settings behavior.

## Files Changed

- Pending implementation.

## Decisions Made

- The owner explicitly replaced the default separate-push cadence with three ordered commits —
  RED, GREEN and REFACTOR — followed by one push and pull-request creation.
- The owner approved all five E1-07 choices. D-84 records Compose Navigation 2.9.8, BOM-managed UI
  tests, AndroidX Test runner/rules 1.7.0 and the SHA-pinned emulator job. D-85 places Vehicle
  presentation in `:feature:vehicle` and shared UI primitives in `:core:common`. D-86 separates
  the Kotlin `AppGraph` from its composed Swift facade. D-87 adds reactive Vehicle edit facts.
  D-88 keeps direct restoration and constant `Idle` until E3-03 instead of adding a provisional
  controller.
- Native Compose host code uses the TDD-order exemption in
  `docs/SPECIFICATION.md §11`; it still requires executing UI tests.

## Verification Run

- Baseline `./gradlew architectureCheck contractCheck` — successful before RED; 16 module rules and
  84 decision/ADR mirrors passed, with no pending contract assertion.
- D-28 RED `./gradlew :feature:vehicle:testAndroidHostTest --tests
  '*FeaturePackageRulesTest'` — failed as required: six tests executed, the three production-scope
  scaffold checks passed and the domain, data and presentation fixtures failed because their rules
  did not yet reject the forbidden imports.
- D-28 GREEN `./gradlew :feature:vehicle:testAndroidHostTest :feature:vehicle:ktlintCheck
  :feature:vehicle:detekt` — successful after moving the executable fixtures into correctly matched
  Android-host test packages; all Vehicle host tests, the three production package rules and the
  three firing fixtures passed with clean lint and static analysis.
- D-28 REFACTOR repeated the same test, lint and detekt command unchanged — successful after
  replacing per-layer branching with declarative allowed-layer policies and exact package-segment
  matching.
- Decision-contract verification `./gradlew contractCheck` — successful after recording D-84
  through D-88; 89 decision/ADR mirrors passed with no pending assertion.
- Vehicle behavior RED `./gradlew :feature:vehicle:testAndroidHostTest
  :shared:testAndroidHostTest --tests '*VehicleRepositoryEditFactsTest' --tests
  '*VehicleStateHoldersTest' --tests '*AppGraphContractTest'` — failed as required during
  `:feature:vehicle:compileAndroidHostTest`: `VehicleEditFacts`,
  `observeVehicleEditFacts`, `SyncStatus`/`UiMessageKind` in `:core:common`, the moved Vehicle
  state holders and the Kotlin `AppGraph` behavior were absent.

## Contract Impact

- `buildAppGraph` returns Kotlin-facing `AppGraph`; `SwiftAppGraph` wraps it.
- Vehicle presentation ownership moves to `:feature:vehicle`; `UiMessage`, `UiMessageKind` and
  `SyncStatus` move to `:core:common` with the prior Swift ABI preserved.
- `VehicleRepository` gains reactive `observeVehicleEditFacts`; `VehicleEditFacts` is Kotlin-only.
- D-88 records the temporary constant-`Idle` exception to the final single-controller contract.

## Decision Board Impact

- Added accepted D-84 through D-88 with ADR-0085 through ADR-0089 and identical mirrors.

## Shared-Write Modules Touched

- `:core:database` read access gains a reactive active-Fuel-Entry count projection. No schema,
  query source, mutation path or migration changes.

## Project Log Entry

- [ ] Entry appended.

## Risks or Follow-ups

- D-88 remains open by design after E1-07: Vehicle presentation reports constant `Idle` while the
  D-55 direct restoration adapter exists. E3-03 must wire every exposing holder to one
  `SyncController.status` and add the two-holder convergence test.
- E3-08 still owns completing the staged Fuel, Session and Sync factories on `AppGraph`.

## Human Review Gate

- Applies: module boundaries and dependency rules, the Swift-facing API surface and pinned
  versions are gated topics. Owner review is required before merge.
