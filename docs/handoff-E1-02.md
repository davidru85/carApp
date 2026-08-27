# Agent Handoff - E1-02

## Story

`E1-02 - Vehicle Domain - S` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — implement the `:feature:vehicle` domain package, canonical
  commands, repository interface and pure Vehicle validation use cases.
- [x] Acceptance criteria reviewed — Kotlin-pure domain; normalisation before validation; exact
  `canonicalVehicleName(name).lowercase()` uniqueness; create range and update edit restriction;
  the closed MVP `FuelType` set with `GASOLINE` default; canonical command fields; success and every
  declared error covered by unit tests.
- [x] Dependencies checked — E1-01 and E0-07 are complete; E0-07 staged `Vehicle`, `FuelType` and
  the final feature shell under D-55; E1-03 consumes this story and remains out of scope.
- [x] Decisions checked — D-4 fixes the MVP `FuelType` values and default; D-18 requires 85% feature
  coverage; D-19 fixes `Outcome`; D-28 defers feature package-rule enforcement to E1-07; D-55
  stages the existing entity and module; D-76 closes the previously undeclared Vehicle validation
  boundary. Every applicable decision is `Accepted`, and no `Proposed` or `Pending` row blocks the
  story.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §3`, `§5.1`, `§7 F-2`, `§8.3`, `§11`;
  `docs/CONTRACTS.md §3`, `§5`, `§6`, `§12`, `§13`, `§20.1`, `§20.2`, `§20.4`, `§20.5`;
  `docs/DECISION_BOARD.md` D-4, D-18, D-19, D-28, D-55 and D-76; and
  `docs/TECHNICAL_PLAN.md §3`, `§4`, `§10`, `§12`.
- [x] Expected verification identified — focused `:feature:vehicle` Android-host and
  `iosSimulatorArm64` tests, lint, detekt and Kover; architecture fixtures and contract checks; the
  complete repository command from `AGENTS.md`.
- [x] Human review gates identified before work — gated paths `docs/SPECIFICATION.md`,
  `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md` and `docs/adr/**`; the public use case contract is a
  gated representation change. Owner review is required before merge.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Added canonical `CreateVehicleCommand` and `UpdateVehicleCommand` shapes in the Vehicle domain,
  including the D-4 `GASOLINE` create default and no owner or timestamp fields.
- Added the exact `VehicleRepository` interface from `docs/CONTRACTS.md §12`.
- Added pure `ValidateCreateVehicle` and `ValidateUpdateVehicle` use cases with immutable D-76
  contexts, exact normalisation, range checks, edit locking and folded-name uniqueness.
- Reused the canonical `Vehicle` entity and closed `FuelType` enum staged in `:core:model` by E0-07
  rather than duplicating either type in the feature.
- Added common tests executed unchanged on Android host and `iosSimulatorArm64`.

## Acceptance Evidence

- `VehicleNameNormalizationTest` proves trimming and collapse of Unicode whitespace to U+0020;
  create/update tests prove normalised values are returned before validation and blank nullable
  fields become null.
- Duplicate-name tests use canonical whitespace and Unicode lowercase; a separate test proves
  composed and decomposed spellings remain distinct, with no unapproved Unicode normalisation.
- Create and update tests cover both closed odometer bounds and both out-of-range sides. Update
  tests prove a non-null odometer is rejected after fuel entries exist while null remains unchanged.
- The 1..40 bounds, required name and `InvalidLength` errors for name, brand and model are covered
  independently for both validators. All errors declared by the use cases have focused tests.
- `VehicleCommandsTest` proves `GASOLINE` is the create default and `FuelType` contains exactly
  `GASOLINE`, `DIESEL`, `LPG`, `CNG` and `OTHER`.
- The RED commit `68ed40f` executed 29 tests: 28 failed for deliberately absent behavior and the
  pre-existing enum-inventory test passed. GREEN commit `f5fb5dd` made the full suite pass before
  the final refactor centralized shared field validation without changing outcomes.
- Source inspection and architecture checks prove the domain package imports only Kotlin,
  `:core:model`, `:core:common` and `kotlinx.coroutines.flow`; it references no platform,
  persistence, sync, provider or wiring API.

## Out of Scope / Not Done

- E1-03 persistence, transactional validation-fact loading and replacement of the E0-07 runtime
  adapter.
- E1-07 presentation and executable feature package-layer rules.

## Files Changed

- `feature/vehicle/src/commonMain/**` — commands, repository contract, contexts, normalisation and
  validators.
- `feature/vehicle/src/commonTest/**` — focused command, normalisation, create and update tests.
- D-76 records — `docs/adr/0077-*` and the four decision mirrors.
- Story records and current state — `docs/handoff-E1-02.md`, `docs/PROJECT_LOG.md`, `AGENTS.md`,
  `README.md`, `docs/DEFINITION.md` and `docs/BACKLOG.md`.

## Decisions Made

- D-76 selects pure validators over repository query ports or data-layer-only validation.
- The owner explicitly requested one push after the RED, GREEN and REFACTOR commits for E1-02,
  superseding the default one-push-per-phase cadence of `docs/SPECIFICATION.md §11` while
  preserving the required commit order.
- D-55's staged `Vehicle` and `FuelType` ownership remains authoritative; E1-02 adds no duplicate
  feature entity.
- The derived E1-02 acceptance wording was corrected to match the authoritative command contract:
  create carries no ID, while update carries only its target ID. No normative behavior changed.
- No TDD order exemption was used.

## Verification Run

- `./gradlew :feature:vehicle:testAndroidHostTest` during RED — failed as required: 29 tests
  executed, 28 failed for absent validation/default/normalisation behavior and the pre-existing
  FuelType inventory passed.
- `./gradlew :feature:vehicle:testAndroidHostTest :feature:vehicle:iosSimulatorArm64Test
  :feature:vehicle:ktlintCheck :feature:vehicle:detekt :feature:vehicle:koverVerify` after GREEN and
  again after REFACTOR — successful; all 29 tests passed on both targets and the 85% feature
  coverage gate held.
- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
  koverVerify :androidApp:assembleDebug testAndroidHostTest iosSimulatorArm64Test
  -x :integration:firebase-auth:iosSimulatorArm64Test
  -x :integration:firebase-firestore:iosSimulatorArm64Test
  -x :wiring:firebase:iosSimulatorArm64Test
  -x :composition:ios:iosSimulatorArm64Test` — successful; 583 actionable tasks, with all 77
  decisions and ADR statuses aligned and the exact D-75 Native-test exemption unchanged.
- `git diff --check` — successful after final documentation.

## Contract Impact

- Updated `docs/CONTRACTS.md §5`, `§13` and `§20.5` for D-76 and the existing D-4 default.

## Decision Board Impact

- Added D-76 and ADR-0077.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [x] Entry appended.

## Risks or Follow-ups

- E1-03 must load D-76 validation facts and write inside one local transaction so the pure
  validation snapshot cannot become stale before mutation.
- The removable E0-07 Vehicle runtime remains until E1-03 supplies the complete data implementation.
- E1-07 still owns executable feature-layer package rules under D-28. E1-02 source inspection and
  the existing module checks provide current evidence, but package enforcement is not claimed.

## Human Review Gate

- Applies: gated contract, specification, decision-board and ADR paths. The owner must review and
  merge the pull request.
