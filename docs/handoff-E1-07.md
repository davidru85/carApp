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
- [ ] Decisions checked — D-3 fixes constructor injection and Koin to wiring; D-4 keeps
  `fuelType` persisted but absent from the MVP form; D-7 keeps navigation native; D-8 fixes shared
  state holders; D-16 and D-28 select Konsist and assign the three package rules to E1-07; D-17
  selects Turbine; D-18 requires 85% feature-domain coverage; D-20 fixes native localization;
  D-55 limits the E0-07 staged behavior; and D-76 fixes the pure Vehicle validation boundary. The
  existing decisions are `Accepted`, but the official AndroidX instrumented Compose test runner
  required by the UI-test acceptance criterion is not yet an accepted tool, and D-7 does not pin
  the required `navigation-compose` version. Owner approval of the proposed E1-07 Android UI
  support stack is required before implementation code starts.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §3`, `§5.1`, `§7 F-2`, `§8.3`, `§8.4`
  and `§11`; `docs/CONTRACTS.md §6`, `§12`, `§14`, `§15`, `§20.2`, `§20.4`, `§20.5` and
  `§20.10`; `docs/DECISION_BOARD.md` D-3, D-4, D-7, D-8, D-16, D-17, D-18, D-20, D-28, D-55 and
  D-76; `docs/TECHNICAL_PLAN.md §3`, `§4`, `§10` and `§12`; ADR-0017, ADR-0029; the E0-07,
  E1-03 and E1-06 handoffs; and the non-normative Android design references indexed by
  `docs/DESIGN.md §4`.
- [x] Expected verification identified — baseline architecture and contract checks; RED/GREEN
  Android-host and iOS shared-state tests; RED/GREEN D-28 fixture tests; Android Compose UI tests;
  focused feature/shared lint, detekt and Kover; Android assembly; provider-free graph tests; the
  complete repository command from `AGENTS.md`; the Shared framework link; `git diff --check`; and
  an Android emulator smoke check when an emulator is available.
- [x] Human review gates identified before work — module boundaries and dependency rules and the
  Swift-facing API surface are gated topics. Owner review is required before merge; the agent MUST
  NOT merge the pull request.
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
- Pending implementation. Native Compose host code uses the TDD-order exemption in
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

## Contract Impact

- Pending implementation review.

## Decision Board Impact

- No decision changes expected.

## Shared-Write Modules Touched

- None expected.

## Project Log Entry

- [ ] Entry appended.

## Risks or Follow-ups

- Ready is blocked until the owner approves or rejects the proposed official Android UI support
  stack: BOM-managed Compose UI test artifacts, AndroidX Test Runner 1.7.0 and stable
  `navigation-compose` 2.9.8. Approval will be recorded as a decision and ADR before product code.

## Human Review Gate

- Applies: module boundaries and dependency rules and the Swift-facing API surface are gated
  topics. Owner review is required before merge.
