# Agent Handoff - E1-04

## Story

`E1-04 - Fuel Entry Domain - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — implement the `:feature:fuel` domain package, canonical command
  and repository contracts, pure create/update validators and rules R-1 and R-2.
- [x] Acceptance criteria reviewed — `MoneyInput` is the sole supplied-pair representation; all
  three derivations and the largest `Long` intermediate; canonical-triple-only persistence;
  warning then confirmation with no first-attempt mutation; create/update R-1 parity; every closed
  §5 bound; unsupported and supported currencies; and Android/JVM plus iOS/native currency facts.
- [x] Dependencies checked — E1-01, E0-07, E1-02 and E1-03 are complete. `:core:model` already
  owns the canonical money types and exact arithmetic; `:core:common` owns `Outcome`, validation
  errors, confirmations, currency support and minor-unit factors; E1-06 remains the data shell.
- [x] Decisions checked — D-18 requires 85% feature coverage; D-19 fixes `Outcome`; D-26 fixes the
  monetary golden values; D-28 defers package-layer enforcement to E1-07; D-38 keeps database-owned
  derived writes behind `DatabaseMutations`; D-55 fixes staged module ownership; D-76 establishes
  the functional-core precedent; and D-77 applies it to Fuel Entry validation. Every applicable
  decision is `Accepted`, and no `Proposed` or `Pending` row blocks E1-04.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §3`, `§5.2`, `§6` R-1 and R-2, `§7`
  F-3, `§8.3`, `§11`; `docs/CONTRACTS.md §2`, `§3`, `§4`, `§5`, `§6`, `§12`, `§13`, `§20.0`,
  `§20.0.1`, `§20.2`, `§20.4`, `§20.5`; `docs/TECHNICAL_PLAN.md §4`, `§10`, `§12`; and
  ADR-0078.
- [x] Expected verification identified — focused `:feature:fuel` Android-host and
  `iosSimulatorArm64` tests; platform currency tests on both targets; feature lint, detekt and
  Kover; architecture and contract checks; the complete repository command from `AGENTS.md`; and
  `git diff --check`.
- [x] Human review gates identified before work — gated paths `docs/SPECIFICATION.md`,
  `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md` and `docs/adr/**`; D-77 and the public validator
  contract require owner review before merge. E1-04 itself is not a gated story.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Added the canonical `MoneyInput`, create/update command and `FuelEntryRepository` contracts to
  `:feature:fuel`.
- Added D-77 pure create/update validators that consume immutable pre-write facts and return only
  normalized canonical persistence values.
- Implemented all three R-2 derivations through the exact `:core:model` integer formulas and
  validated supplied operands before arithmetic.
- Implemented R-1 hard bounds plus the idempotent warning/confirmation protocol for both create
  and update.
- Added common, Android/JVM and iOS/Foundation tests for domain behavior, currencies, persistence
  shape and the floating-point ban.

## Acceptance Evidence

- `MoneyInput` has exactly `LitersAndPrice`, `LitersAndTotal` and `PriceAndTotal`; the canonical
  commands contain no owner, timestamp or supplied-pair persistence metadata.
- Golden tests prove `45_123 + 1_789 -> 8_073`, `40_000 + 6_000 -> 1_500`, and
  `1_500 + 6_000 -> 40_000`. The largest case produces `49_999_950` from `500_000`, `999_999`
  and factor `100`, exercising the `49_999_950_000_000` `Long` intermediate.
- Tests validate both sides of every date, odometer, liters, price, total and notes interval.
  Supplied `Long.MIN_VALUE` and `Long.MAX_VALUE` values return typed range errors before arithmetic;
  every derived value is range-checked before success.
- An unconfirmed inconsistent odometer returns the exact idempotent
  `ValidationWarning.OdometerInconsistent` from a pure validator. Reissuing the identical command
  with `Confirmation.OdometerInconsistent` succeeds; unrelated confirmation and hard-error cases
  do not.
- Create and update execute the same validation path, with explicit tests for date, monetary and
  warning parity.
- Every `SUPPORTED_CURRENCY_CODES` entry resolves to factor `100`; unsupported and lowercase
  explicit codes return `ValidationError.InvalidUnit`. JVM `Currency` and Foundation
  `NSNumberFormatter` verify the two-minor-digit inventory with EUR fallback semantics.
- Android-host source guards prove no Fuel Entry domain source uses floating-point types or
  conversions, no local/remote/outbox source persists a supplied-pair marker, and the SQLDelight
  schema stores the canonical triple as non-null `INTEGER` columns.

## Out of Scope / Not Done

- E1-05 consumption calculation and E1-06 Fuel Entry persistence.
- The exact representation of `vehicle.createdAt - 20 years` remains an owner decision before
  E1-06. E1-04 applies the already-resolved `earliestAllowedDate` fact and embeds no calendar
  approximation.

## Files Changed

- `feature/fuel/src/commonMain/**` — commands, repository contract, D-77 validation context,
  canonical validated values and create/update validators.
- `feature/fuel/src/commonTest/**` — common command, money, date, odometer, currency and text tests.
- `feature/fuel/src/androidHostTest/**` — JVM currency, persistence-shape and floating-point guards.
- `feature/fuel/src/iosSimulatorArm64Test/**` — Foundation currency inventory test.
- D-77 records — `docs/adr/0078-*` and the four decision mirrors.
- `docs/CONTRACTS.md §5`, `§13`, `§20.5` — D-77 functional-core contract and canonical types.
- Story records and current state — this handoff, `docs/PROJECT_LOG.md`, `AGENTS.md`, `README.md`,
  `docs/DEFINITION.md`, `docs/BACKLOG.md` and `docs/TECHNICAL_PLAN.md`.

## Decisions Made

- D-77 selects pure Fuel Entry validators with immutable pre-write facts and canonical output.
- The owner requested one commit after each RED, GREEN and REFACTOR phase, followed by one push
  and pull-request creation. Technical decisions that require owner input are grouped for review
  after all independent work is complete.
- `ValidatedFuelEntryValues` deliberately omits the update target ID, owner, timestamps,
  synchronization metadata, database-owned `odometerInconsistent` and any supplied-pair marker.
- Unsupported explicit currencies fail validation; locale-suggested fallback remains platform
  input behavior and does not silently change an explicit command.
- The total-cost upper bound is enforced as an individual closed bound. A supplied value at that
  boundary may still fail because the derived liters or price would exceed its own independent
  bound; no field bound suppresses another canonical-triple constraint.
- No TDD order exemption was used. RED, GREEN and REFACTOR are separate commits, with one push
  after all three as requested.

## Verification Run

- `./gradlew :feature:fuel:testAndroidHostTest` during RED — failed as required: 40 tests executed,
  33 failed on the deliberately unimplemented validators and 7 contract/platform guards passed.
- `./gradlew :feature:fuel:iosSimulatorArm64Test` during RED — failed as required: 37 tests
  executed, the same 33 validators failed and 4 common/platform shape tests passed.
- The same two commands after GREEN — successful; all 40 Android-host and all 37 iOS tests passed
  unchanged.
- `./gradlew :feature:fuel:ktlintCheck :feature:fuel:detekt :feature:fuel:koverVerify
  :feature:fuel:testAndroidHostTest :feature:fuel:iosSimulatorArm64Test architectureCheck
  contractCheck` after REFACTOR — successful; tests, feature quality, the 85% feature coverage
  gate, 16 architecture rules and all 78 decision/ADR mirrors passed.
- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
  koverVerify :androidApp:assembleDebug testAndroidHostTest iosSimulatorArm64Test -x
  :integration:firebase-auth:iosSimulatorArm64Test -x
  :integration:firebase-firestore:iosSimulatorArm64Test -x
  :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test` — successful;
  all 600 actionable tasks completed, including lint, detekt, coverage, Android assembly,
  Android-host tests and required iOS simulator tests.
- `git diff --check` — successful.

## Contract Impact

- Updated `docs/CONTRACTS.md §5`, `§13` and `§20.5` for D-77.

## Decision Board Impact

- Added D-77 and ADR-0078.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [x] Entry appended.

## Risks or Follow-ups

- E1-06 must calculate `FuelEntryValidationContext.earliestAllowedDate` after the owner selects the
  exact representation of `vehicle.createdAt - 20 years`.
- E1-06 must exclude the target row when preparing update context and prove fact loading,
  validation and mutation share one local transaction.
- E1-05 owns the pure consumption calculation and remains the next human-gated story.

## Human Review Gate

- Applies: gated contract, specification, decision-board and ADR paths. Owner review is required
  before merge.
