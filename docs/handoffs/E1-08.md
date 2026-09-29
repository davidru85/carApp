# Agent Handoff - E1-08

## Story

`E1-08 - Android UI: Fuel Entries - L` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — implement the Android Fuel Entry list, create/edit form,
  segment and average consumption presentation, two-step odometer confirmation and the production
  Fuel Entry presentation holders in `:feature:fuel`.
- [x] Acceptance criteria reviewed — F-3 defaults, live R-2 derivation through `MoneyInput`,
  warning then confirmation, accessible no-consumption reasons, independent missed-entry and
  inconsistent-odometer indicators on every row, the empty-consumption state and byte-exact Swift
  ABI preservation.
- [x] Dependencies checked — E1-04 through E1-06 provide Fuel Entry domain, consumption and local
  data; E1-07 provides Compose Navigation, instrumented Android UI tests, the Kotlin/Swift graph
  split and keyed holder release. E1-10 and E3-03 remain later stories and are not implemented or
  partially absorbed here.
- [x] Decisions checked — D-8, D-20, D-55, D-77 through D-88 and D-90 through D-99 apply and are
  `Accepted`. D-92 through D-99 record the E1-08 projection, money-resolution, defaults, staged
  sync, calendar-day, iOS export, save-completion and mode-switch choices. No `Proposed` or
  `Pending` decision blocks E1-08.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §3`, `§5.2`, `§6` R-1 through R-3,
  `§7` F-3, `§8.3`, `§8.4`, `§11`; `docs/CONTRACTS.md §2` through `§6`, `§12` through `§15`,
  `§20.4` through `§20.6` and `§20.10`; `docs/TECHNICAL_PLAN.md §3`, `§4`, `§10`, `§12`; the
  Android design references indexed by `docs/DESIGN.md §4`; and ADR-0093 through ADR-0100.
- [x] Expected verification identified — focused `:core:model`, `:feature:fuel` and `:shared`
  Android-host and iOS simulator tests; Android compilation and API 36 instrumented tests; feature
  lint, detekt and Kover; architecture fixtures and checks; contract checks; Shared framework
  linking with an ordering-only pre-update golden diff followed by an exact empty comparison; the complete repository command from
  `AGENTS.md`; and `git diff --check`.
- [x] Human review gates identified before work — gated `docs/SPECIFICATION.md`,
  `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/adr/**`, module boundaries and the
  Swift-facing ABI. Owner review is required before merge; the agent MUST NOT merge the PR.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Extended the canonical Fuel Entry list projection with independent missed-entry and
  inconsistent-odometer facts and preserved both through SQLDelight projection mapping.
- Moved Fuel Entry UI models and state holders into `:feature:fuel`, backed them with the local
  repository and consumption flows, and composed exact clock, Vehicle odometer and temporary
  locale-currency defaults in `AppGraph`.
- Reused the promoted pure `MoneyInput` resolver for live form derivation, kept live range errors
  silent and nullable, and retained save-time validation and two-step odometer confirmation.
- Implemented the Android Fuel Entry list, empty and summary states, accessible invalid-segment
  explanations, row indicators, create/edit form and local-calendar date picker.
- Exported refined Fuel Entry presentation declarations from the sole iOS framework, hid domain
  and data declarations, and regenerated the ordering-only Objective-C header golden.
- Added the D-95 staged `SyncStatus.Idle` record and the D-96 cross-platform calendar-day rule
  without implementing E1-10 or E3-03.
- Applied the first owner-review corrections: Android preserves raw money text while typing,
  invalid Vehicle odometer suggestions no longer replace the last valid suggestion, save
  completions survive a detached collector without queuing repeated navigation, and reactive plus
  instrumented tests wait for complete observable UI state rather than fixed delays or partial
  emissions.
- Recorded D-99 and aligned `setMoneyInputMode` with the existing holder behavior: available money
  values remain present and the value outside the selected mode is immediately re-derived through
  D-93, including the accepted visible one-scale-unit rounding drift.

## Acceptance Evidence

- `DomainModelsCoverageTest` and `FuelEntryProjectionMapperTest` prove that a partial refuel with
  `EndEntryNotFullTank` retains both independent row flags.
- `FuelEntryStateHoldersTest` proves exact-clock, Vehicle-odometer and supported-locale defaults,
  EUR fallback, live R-2 derivation, silent live range failure, two-step confirmation, both row
  indicators, weighted summary, staged `Idle`, successful-save signaling and the holder-level
  confirmed-deletion contract.
- `FuelEntryStateHoldersTest` also pins the D-99 round trip (`40001` liters-scaled and `1549`
  price-scaled derive `6196` total minor, then `PRICE_AND_TOTAL` re-derives liters as `40000`),
  proves no value is cleared when the participating pair is present and proves two successful
  saves without a collector leave only one pending navigation completion.
- `FuelEntryMoneyInputTest` and the API 36 incremental-input test prove Android retains raw money
  text, including a scale-overflowing digit, while publishing only parseable prefixes to the
  holder.
- `FuelEntryFlowTest` drives the API 36 Compose flow through vehicle creation, live money
  derivation, partial refuel, warning confirmation, accessible explanation and both visible row
  indicators. Its deterministic zone test proves Europe/Madrid local-day conversion across UTC
  and daylight-saving boundaries.
- The Android create and edit destinations use the same shared form holder; edit loads the selected
  local entry and persists through `UpdateFuelEntryCommand`.
- Framework linking plus an exact generated-versus-golden comparison proves that only reviewed
  declaration ordering moved. Exact Objective-C/Swift names and signatures are unchanged, the two
  existing `FuelEntryListItemUi` flags are unchanged, and all forbidden Fuel domain/data symbols
  remain absent.

## Out of Scope / Not Done

- E1-10 settings persistence remains unimplemented; the only related change is the required
  composition-point TODO for replacing the temporary locale-derived currency.
- E3-03 synchronization behavior remains unimplemented; the Fuel list publishes the recorded
  D-95 constant `SyncStatus.Idle` only.
- E1-09 owns the iOS screens and must apply D-96's identical device-local calendar-day rule.
- `FuelEntryListStateHolder` exposes the contract-mandated `requestDelete` and `confirmDelete`
  intents, but no Android screen consumes them and E1-08 delivers no Fuel Entry delete affordance.

## Files Changed

- `:core:model` and `:feature:fuel`: row projection facts, promoted pure money resolution,
  Objective-C refinement and production Fuel Entry presentation.
- `:shared`: graph composition and removal of the D-55 Fuel Entry presentation shells.
- `:androidApp`: Compose Fuel Entry list/form screens, navigation, localization and API 36 tests.
- `:composition:ios`, build contract checks and the Shared header golden: refined feature export
  with allowlist enforcement and stable ABI evidence.
- `AGENTS.md`, `README.md` and `docs/**`: D-92 through D-99, representation and behavior contracts,
  current state, acceptance evidence and delivery records.

## Decisions Made

- D-92 through D-97 record the six owner-approved E1-08 technical choices.
- D-98 records the Kotlin-only successful-save completion signal selected after the API 36 UI test
  proved that inferring navigation from conflated `isSaving` and `message` state is racy.
- D-99 records the owner-selected rule that money mode changes preserve all available values and
  re-derive the value outside the new mode through D-93. Its accepted trade-off is that alternating
  modes may visibly move a re-derived value by one scale unit because money field scales differ.
- The two C-2 RED characterization tests passed against the existing holder because its product
  behavior already matched D-99; the RED failure was the required normative assertion against the
  obsolete clearing rule in `CONTRACTS.md §20.10`. No deliberately false product failure was
  introduced merely to manufacture a behavioral RED.
- D-98 delivery is conflated: every accepted save still persists, but at most one completion is
  retained while navigation has no collector, preventing two queued successes from navigating
  twice when collection resumes.
- D-97's RED wording predicted an empty pre-update diff. The linked framework proved that
  Kotlin/Native relocates declarations by their new owning package. Its record was corrected to the
  owner's stated acceptance boundary: ordering-only relocation is recorded, while names and
  signatures remain byte-identical and any module-derived rename remains a failure.
- Native Compose host code uses the TDD-order exemption in `docs/SPECIFICATION.md §11`; it still
  requires instrumented UI tests. Shared presentation, domain and graph behavior remain test-first.
- The owner explicitly requires three ordered commits — RED, GREEN and REFACTOR — followed by one
  push and pull-request creation.

## Verification Run

- Baseline `./gradlew architectureCheck contractCheck` — successful before RED: 16 architecture
  rules and 92 decision/ADR mirrors passed with no unresolved decision.
- RED `./gradlew :feature:fuel:testAndroidHostTest :shared:testAndroidHostTest` — the test sources
  compiled and executed; the projection test failed because the row flags were dropped and six
  shared graph tests failed against the deterministic Fuel Entry shells. The one pre-existing
  fallback assertion passed. This is the expected behavioral RED state.
- RED `./gradlew ktlintCheck detekt architectureCheck contractCheck` — architecture and contracts
  passed with 98 decision/ADR mirrors; the first run identified only formatting in the new shared
  test, which was corrected before the RED commit.
- GREEN `./gradlew ktlintCheck detekt architectureCheck contractCheck
  :build-logic:convention:test :core:model:testAndroidHostTest :feature:fuel:testAndroidHostTest
  :shared:testAndroidHostTest :androidApp:assembleDebug` — successful with 99 accepted decision/ADR
  mirrors and no unresolved decisions.
- Focused `env ANDROID_SERIAL=emulator-5554 ./gradlew
  :androidApp:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=com.ruizurraca.carapp.FuelEntryFlowTest` —
  both E1-08 API 36 tests passed.
- `./gradlew :feature:fuel:koverXmlReport` — 907 covered lines and 8 missed lines (99.13%); the
  complete `koverVerify` threshold is green without exclusions or suppressions.
- Complete repository command from `AGENTS.md` — successful: 607 actionable tasks, including
  `ktlintCheck`, `detekt`, 16 architecture rules, 99 decision/ADR contract mirrors,
  `:build-logic:convention:test`, `koverVerify`, Android assembly, Android-host tests and required
  iOS simulator tests with the four D-75 exclusions.
- `env ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest` — successful:
  all 7 tests passed on the D-84 API 36 emulator with no physical-device target.
- `./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64` — successful with 69 actionable
  tasks; exact `diff -u` between generated `Shared.h` and its golden produced no output.
- `git diff --check` — successful before the REFACTOR commit.
- Review C-1 RED/GREEN — Android money-input unit tests and the API 36 incremental-input test prove
  raw scale overflow remains visible while only the last parseable prefix reaches the holder.
- Review C-3 RED/GREEN and C-4 RED/GREEN — delayed save-completion collection and invalid/missing
  Vehicle odometer suggestion tests pass on Android host and iOS simulator.
- Review C-5 and C-6 — both affected API 36 flows pass with observable-state waits, and the handoff
  now states that E1-08 exposes deletion intents but no Android delete affordance.
- C-2 RED `721db96` — the two D-99 characterization tests pass against the already-correct holder;
  the explicit normative probe fails because `CONTRACTS.md §20.10` still required clearing.
- C-2 GREEN `537b42d` — `:feature:fuel:testAndroidHostTest`,
  `:feature:fuel:iosSimulatorArm64Test`, `contractCheck` and `architectureCheck` pass with 100
  accepted decision/ADR mirrors and no unresolved decisions.
- C-2 REFACTOR focused verification — Android-host and iOS-simulator Fuel tests, feature ktlint,
  feature detekt and `contractCheck` pass; two queued successful saves retain one pending
  navigation completion.
- Final complete repository command from `AGENTS.md` — successful: 609 actionable tasks, including
  ktlint, detekt, 16 architecture rules, 100 decision/ADR contract mirrors, build-logic tests,
  Kover thresholds, Android assembly, Android-host tests and required iOS simulator tests with the
  four D-75 exclusions.
- Final `./gradlew :feature:fuel:koverXmlReport` — 908 covered lines and 8 missed lines (99.13%).
- Final `env ANDROID_SERIAL=emulator-5554 ./gradlew
  :androidApp:connectedDebugAndroidTest` — all 8 tests pass on the D-84 API 36 emulator; the
  emulator was closed immediately afterwards and the attached physical device was not targeted.
- Final framework link and exact header comparison — successful; generated `Shared.h` and its
  golden are byte-identical with SHA-256
  `b746a480958913a98ba5135a4e69711193e76a371e8f9b0d4346d8267a7a6074`.
- The first post-REFACTOR CI run exposed a shared-test race in
  `listPublishesWeightedSummaryAndReliabilityAfterThreeFullTanks`: it waited for three rows but
  asserted the independently emitted consumption summary immediately. The test-only correction
  now waits for the complete row-and-summary state.
- Exact local `provider-decoupling` command — successful with 222 actionable tasks on Android host
  and iOS simulator. The complete 609-task repository command and all 8 API 36 tests were then
  repeated successfully; the emulator was closed again.

## Contract Impact

- Updated `docs/CONTRACTS.md §11.6`, `§14`, `§15.3`, `§20.4` and `§20.10` for the Fuel export,
  staged sync status, projection flags, local calendar day, refined allowlist, conflated Kotlin-only
  save completion and D-99 money mode-switch behavior.

## Decision Board Impact

- Added accepted D-92 through D-99 with ADR-0093 through ADR-0100 and identical mirrors.

## Shared-Write Modules Touched

- None. `:core:database` is not modified by E1-08.

## Project Log Entry

- [x] Entry appended

## Risks or Follow-ups

- E1-10 must replace the locale-derived creation currency at the AppGraph composition point with
  the persisted settings source.
- E3-03 must replace D-95 constant `Idle` with the single final `SyncController.status` source.
- E1-09 must apply D-96's identical device-local calendar-day conversion on iOS.

## Human Review Gate

- Applies: gated normative documents, module boundaries and the Swift-facing ABI. The agent MUST
  NOT merge the pull request.
