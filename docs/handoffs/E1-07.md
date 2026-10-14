# Agent Handoff - E1-07

> **Owner code-review corrections — 2026-08-31:** D-84 through D-89 remain unchanged. The
> corrections add D-90 / ADR-0091 for keyed Swift holder release and creation completion, and D-91
> / ADR-0092 for explicit common-enum export names. The Objective-C golden changes intentionally:
> D-90 adds three release operations and `savedVehicleId`; D-91 pins the already generated
> Kotlin-matching enum names instead of leaving them dependent on framework export configuration.

> **Second-round review corrections — 2026-08-31:** N-1 republishes restored Android drafts into
> a fresh holder and preserves fields edited before the first edit-facts emission. N-2 exposes the
> existing initial-odometer range as a Kotlin-only domain declaration consumed by Android. These
> corrections add no decision, ADR or Swift-facing ABI change; the Objective-C golden is unchanged.

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
  graph separation, reactive edit facts and the pre-E3-03 sync-status exception. The owner-review
  corrections add accepted D-89 for graph-owned local database lifetime, D-90 for keyed Swift
  holder release and safe creation completion, and D-91 for exact common-enum export names. Every
  dependency is `Accepted`; no unresolved decision blocks the story.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §3`, `§5.1`, `§7 F-2`, `§8.3`, `§8.4`
  and `§11`; `docs/CONTRACTS.md §6`, `§12`, `§14`, `§15`, `§20.2`, `§20.4`, `§20.5` and
  `§20.10`; `docs/DECISION_BOARD.md` D-3, D-4, D-7, D-8, D-16, D-17, D-18, D-20, D-28, D-55 and
  D-76 and D-84 through D-91; `docs/TECHNICAL_PLAN.md §3`, `§4`, `§10` and `§12`; ADR-0017,
  ADR-0029 and ADR-0085 through ADR-0092; the E0-07,
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

- Added executable D-28 Konsist rules for feature `domain`, `data` and `presentation` packages,
  with an independently firing fixture for every rule.
- Added the reactive `VehicleEditFacts` repository projection and database read support used to
  keep editability authoritative while Fuel Entries change.
- Moved Vehicle presentation models and state holders into `:feature:vehicle`, moved shared UI
  primitives into `:core:common`, retained the declared Vehicle presentation export names and
  later made the three reviewed common-enum renames explicit under D-91.
- Separated Kotlin `AppGraph` construction from the composed `SwiftAppGraph` facade. Android now
  supplies `viewModelScope`; the Swift facade owns and closes its child scopes while preserving
  caching, idempotent close and post-close rejection.
- Implemented the Compose Vehicle list, create/edit form and detail shell with native Navigation,
  typed-error resource mapping, English and Spanish resources, logical deletion and the empty
  detail invitation.
- Added the protected API 36 emulator job and the instrumented creation test. The flow creates a
  Vehicle, never renders a Fuel Type input and routes to the empty detail shell.
- Corrected the owner-review resource leak by returning an owned, idempotently closeable
  `DatabaseHandle` from every production and test `DatabaseFactory`; direct `AppGraph.close()` and
  transitive `SwiftAppGraph.close()` now release the SQL driver exactly once.
- Corrected the cached creation-form corruption by preserving `vehicleId = null`, publishing the
  created identifier through `savedVehicleId`, resetting successful creation inputs and adding
  keyed release for every cached Swift state-holder family.
- Pinned `Confirmation`, `AuthProvider` and `SyncTrigger` to intentional Objective-C and Swift
  names with exact `@ObjCName` annotations and recorded the ABI change as D-91.
- Bound Android form-holder release to navigation exit rather than composition disposal. Saveable
  native draft text now survives configuration changes, while leaving the route releases the
  holder and a later route starts clean.
- Preserved raw initial-odometer text in the Android host. Empty, non-numeric, overflowing and
  out-of-range input remains visible, maps to a localized error and cannot be saved.
- Republished restored saveable text and valid odometer values into fresh holders so the displayed
  draft is the exact draft used by save. The first edit-facts emission now fills only untouched
  fields while continuing to update `canEditInitialOdometer` authoritatively.
- Replaced Android's duplicated initial-odometer range with the Kotlin-only
  `INITIAL_ODOMETER_RANGE_KM` domain source of truth.
- Recorded D-84 through D-88, regenerated the Objective-C golden header and updated the current
  repository, verification and contribution records.
- Recorded D-89 without reopening or amending the owner-ratified D-84 through D-88 records.

## Acceptance Evidence

- `VehicleListStateHolder` and `VehicleFormStateHolder` live in `:feature:vehicle` `commonMain`,
  take a caller-owned scope, expose `close()` and collect and publish on `dispatchers.main`.
- State-holder tests cover loading, empty, typed-error mapping, successful creation, reactive edit
  facts, authoritative initial-odometer rejection, `fuelType` round-trip and constant D-88 `Idle`.
- The production list test records the exact `observeVehicles(includeDeleted = false)` argument
  and proves a deleted row cannot enter the emitted list.
- Android maps stable message and validation codes to `values` and `values-es`; `UiState` contains
  codes and data only, and the Compose source contains no hardcoded user-facing copy.
- `VehicleCreationTest` runs on an API 36 emulator and proves creation, absence of
  `FUEL_TYPE_INPUT`, detail navigation and the first-Fuel-Entry invitation.
- `VehicleRepositoryEditFactsTest` observes the Vehicle and active-Fuel-Entry fact as one reactive
  projection; state-holder tests prove a stale editable flag cannot bypass repository validation.
- `AppGraphContractTest` and existing Swift graph tests prove Kotlin graph construction, Android
  scope ownership and the composed Swift facade's caching and close behavior.
- `AppGraphCloseTest` observes the owned handle directly on Android host and iOS, proving one
  release after repeated Kotlin-graph close calls and one release after repeated Swift-transitive
  close calls.
- The linked `Shared.framework` header exactly matches the committed golden. Compared with `main`,
  the E1-07 surface intentionally adds the D-90 holder-release and creation-completion API and
  uses the D-91 exact names `Confirmation`, `AuthProvider` and `SyncTrigger`; provider types remain
  absent.
- D-28's three real-tree rules and three violation fixtures pass on the Android host.
- `VehicleStateHoldersTest` proves that two saves through one creation holder issue two create
  commands, never an update, and that successful creation resets the public inputs.
- `SwiftAppGraphLifecycleTest` proves keyed holder caching, release, child-scope cancellation and
  fresh reconstruction for Vehicle forms, Fuel Entry lists and Fuel Entry forms.
- `VehicleCreationTest` proves draft retention across activity recreation, holder release after
  back-stack exit, raw empty and overflowing odometer retention and a visible localized error.
- `VehicleFormStateRestorationTest` destroys the original holder while retaining saveable
  composition state and proves the resulting `CreateVehicleCommand` carries the displayed name and
  odometer. `VehicleStateHoldersTest` proves pre-facts edits survive into `UpdateVehicleCommand`.
- The Android range test binds both accepted boundaries and their adjacent rejected values to the
  domain range and proves the host no longer declares its duplicate range field.

## Out of Scope / Not Done

- E1-08 and later Fuel Entry UI, E1-09 SwiftUI, complete synchronization, authentication and
  settings behavior.
- D-88 deliberately leaves Vehicle presentation on constant `SyncStatus.Idle` and direct D-55
  restoration until E3-03 supplies the final shared controller.
- `DefaultAppGraph.syncController()` remains a staged runtime error until E3-03.
- `VehicleFormStateHolder.loadedInitialOdometerKm` is not refreshed after a successful edit; no
  owning backlog story is currently assigned.
- `VehicleCreationTest` still uses the device database and accumulates rows across runs; no owning
  backlog story is currently assigned.
- `VehicleSliceRuntime.refresh()` still returns success when an individual remote apply fails;
  E3-03 owns replacement of the staged restoration path.
- D-28 package rules still inspect imports only, so fully qualified forbidden usages need a future
  architecture-check hardening story.
- Delete request/confirmation and restoration error branches still need dedicated presentation
  tests; no owning backlog story is currently assigned.
- N-3 remains out of scope: `ReleaseVehicleFormOnBackStackExit` retains its hand-written observer
  and deferred release until a future navigation-lifetime correction.
- N-4 remains out of scope: the existing invalid-odometer test still asserts literal English copy
  instead of resolving `R.string.error_out_of_range`.

## Files Changed

- Android host and UI test: `androidApp/build.gradle.kts`, `androidApp/src/main/**`,
  `androidApp/src/androidTest/**` and `.github/workflows/ci.yml`.
- Shared contracts and graph: `core/common`, `core/sync`, `shared`, `composition/ios` and the
  generated Objective-C header golden.
- Vehicle facts, presentation and tests: `core/database`, `feature/vehicle` and its Android-host
  architecture fixtures.
- Build and version pins: `gradle/libs.versions.toml`, `feature/vehicle/build.gradle.kts` and the
  build-logic contract tests.
- Decision and delivery records: the five normative decision mirrors, ADR-0085 through ADR-0089,
  `AGENTS.md`, `README.md`, `docs/BACKLOG.md`, `docs/CONTRIBUTING.md`, `docs/identifiers.md`, this
  handoff and `docs/PROJECT_LOG.md`.
- Owner-review correction: the `:core:database` lifetime contract and factories, `:core:testing`
  factory fakes, graph factory callers and tests, D-89 / ADR-0090 mirrors, this handoff and the
  project log.
- Owner code-review corrections: Vehicle and Swift graph presentation state, Android host and
  instrumented tests, explicit common enum annotations, the generated Objective-C golden, D-90 /
  ADR-0091 and D-91 / ADR-0092 mirrors, this handoff and the project log.
- Second-round corrections: Android saveable-form synchronization and tests, Vehicle form-holder
  edit tracking, the Kotlin-only Vehicle odometer range, this handoff and the project log.

## Decisions Made

- The owner explicitly replaced the default separate-push cadence with three ordered commits —
  RED, GREEN and REFACTOR — followed by one push and pull-request creation.
- The owner approved all five E1-07 choices. D-84 records Compose Navigation 2.9.8, BOM-managed UI
  tests, AndroidX Test runner/rules 1.7.0 and the SHA-pinned emulator job. D-85 places Vehicle
  presentation in `:feature:vehicle` and shared UI primitives in `:core:common`. D-86 separates
  the Kotlin `AppGraph` from its composed Swift facade. D-87 adds reactive Vehicle edit facts.
  D-88 keeps direct restoration and constant `Idle` until E3-03 instead of adding a provisional
  controller.
- The owner ratified D-84 through D-88 during human review and required that their ADRs and mirror
  rows remain unchanged. The review correction required a new public `DatabaseFactory` contract,
  so D-89 selects a `:core:database`-owned `DatabaseHandle`; the alternatives were a factory-level
  `close(database)` operation or leaking `SqlDriver` ownership into `:shared`.
- Native Compose host code uses the TDD-order exemption in
  `docs/SPECIFICATION.md §11`; it still requires executing UI tests.
- D-90 selects keyed release operations on `SwiftAppGraph` and a separate `savedVehicleId`
  completion signal. This retains caching without unbounded child scopes and keeps a creation
  holder permanently on the create path after a successful save.
- D-91 selects exact Kotlin-matching common-enum names because no current Swift source consumes
  the prior module-derived names. The change is intentional and owner-visible rather than an
  incidental consequence of framework exports.
- Android configuration retention and raw odometer parsing stay host-private and add no shared or
  Swift surface, so they do not require another gated decision.
- N-1 uses the requested host-side one-shot republication effects. Private per-field edit tracking
  is also required because the mandated `:feature:vehicle` common test does not execute Compose;
  the first facts emission therefore fills only untouched fields while D-87 editability remains
  facts-owned. N-2 reuses the existing range under `@HiddenFromObjC`. Neither correction requires
  a new decision or ADR.

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
- Vehicle behavior GREEN `./gradlew :feature:vehicle:testAndroidHostTest
  :shared:testAndroidHostTest` — successful with the reactive repository facts, shared Vehicle
  presentation and Kotlin/Swift graph separation in place.
- Android compilation `./gradlew :androidApp:compileDebugKotlin
  :androidApp:compileDebugAndroidTestKotlin` — successful.
- Instrumented GREEN `./gradlew :androidApp:connectedDebugAndroidTest` — successful on local AVD
  `E1_07_API_36` (Android API 36); one test passed. An initial run on the pre-existing API 37
  preview AVD failed in Espresso before application code because that platform removed the legacy
  `InputManager.getInstance` API, so D-84 fixes the supported CI target at API 36.
- Focused GREEN `./gradlew architectureCheck :build-logic:convention:test contractCheck
  :androidApp:assembleDebug` — successful: 16 module rules, their firing fixtures and 89
  decision/ADR mirrors passed with no pending contract assertion; Android assembled.
- REFACTOR `./gradlew ktlintFormat detekt` — successful after import ordering, line wrapping and an
  explicit coroutine-test opt-in; no behavior changed.
- Shared framework `./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64` followed by an
  exact generated-header/golden comparison — successful; the regenerated golden is byte-exact.
- The first complete repository run correctly failed contract assertion 7 after direct module
  export exposed `AppError`, `Outcome` and `VehicleRepository`. D-85's non-allowlisted common and
  Vehicle declarations are now Kotlin-only through `@HiddenFromObjC`; a regenerated header passes
  the allowlist and forbidden-symbol contract with no provider, repository, command or use-case
  declaration.
- The next complete runs exposed three stale cross-module assumptions: the `:core:sync` contract
  test lacked the moved `SyncStatus` import, `:core:common` presentation types had no direct
  coverage, and the Firebase provider test still expected `SwiftAppGraph`. Each check failed before
  correction; focused reruns then passed with the D-86 Kotlin `AppGraph` expectation and the 90%
  common coverage floor retained.
- Final repository command `./gradlew ktlintCheck detekt architectureCheck contractCheck
  :build-logic:convention:test koverVerify :androidApp:assembleDebug testAndroidHostTest
  iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x
  :integration:firebase-firestore:iosSimulatorArm64Test -x
  :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test` — successful in
  7 seconds with 607 actionable tasks. Lint, detekt, coverage, Android assembly, Android-host and
  required iOS simulator tests all passed; architecture reported 16 rules over 23 modules and
  contract check reported 89 mirrored decisions/ADRs with no pending assertion.
- Owner-review RED `./gradlew :shared:testAndroidHostTest --tests '*AppGraphCloseTest'` — failed as
  required: both release tests observed that direct and Swift-transitive graph close did not
  release the graph-created database connection.
- Owner-review GREEN `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test --tests
  '*AppGraphCloseTest'` — successful on both platforms with an observable recording handle; two
  close calls release the Kotlin graph's handle exactly once and the Swift facade does so
  transitively exactly once.
- Owner-review focused regression `./gradlew :core:database:testAndroidHostTest
  :core:database:iosSimulatorArm64Test :core:testing:testAndroidHostTest
  :core:testing:iosSimulatorArm64Test :feature:vehicle:testAndroidHostTest
  :feature:vehicle:iosSimulatorArm64Test :feature:fuel:testAndroidHostTest
  :feature:fuel:iosSimulatorArm64Test :shared:testAndroidHostTest
  :shared:iosSimulatorArm64Test architectureCheck :build-logic:convention:test contractCheck
  :composition:ios:linkDebugFrameworkIosSimulatorArm64` — successful across the affected database,
  fake, feature, graph, architecture and contract surfaces; contract check reports 90 accepted
  decision/ADR mirrors. At the D-89-only correction step, the generated Objective-C header
  remained byte-exact with the then-current golden.
- Owner-review REFACTOR `./gradlew ktlintFormat` — successful; normalized the GREEN import and
  expression formatting and ensured the graph-construction contract test closes its owned
  database handle even if its assertion fails.
- Owner-review final repository command `./gradlew ktlintCheck detekt architectureCheck
  contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug
  testAndroidHostTest iosSimulatorArm64Test -x
  :integration:firebase-auth:iosSimulatorArm64Test -x
  :integration:firebase-firestore:iosSimulatorArm64Test -x
  :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test` — successful in
  43 seconds with 607 actionable tasks. Lint, detekt, coverage, Android assembly, Android-host and
  required iOS simulator tests all passed; architecture reported 16 rules over 23 modules and
  contract check reported 90 accepted decision/ADR mirrors with no pending assertion.
- Owner-review framework check `./gradlew
  :composition:ios:linkDebugFrameworkIosSimulatorArm64`, exact generated-header/golden comparison
  and `git diff --check` — successful with 69 framework tasks; D-89 itself did not change the
  then-current committed golden.
- C-1 RED `./gradlew :feature:vehicle:testAndroidHostTest :shared:testAndroidHostTest --tests
  '*VehicleStateHoldersTest' --tests '*SwiftAppGraphLifecycleTest'` — failed before the correction
  because the cached creation holder became an editor and cached Swift holders had no keyed
  release.
- C-1 GREEN and REFACTOR focused runs — successful after separating `savedVehicleId`, resetting
  creation inputs and centralizing keyed close-and-release for all three Swift holder caches.
- C-2 RED common and framework contract tests failed before exact annotations because the three
  enum names were derived rather than declared. GREEN and REFACTOR runs passed after adding exact
  `@ObjCName` annotations and one shared export-name expectation table.
- C-3 RED `./gradlew :androidApp:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=com.ruizurraca.carapp.VehicleCreationTest` —
  failed because activity recreation disposed and removed the cached holder. GREEN and REFACTOR
  runs passed all creation and rotation tests on `E1_07_API_36`; Android lint and compilation also
  passed.
- C-5 RED isolated instrumented test failed with `EditableText = '0'` after clearing the field.
  GREEN and REFACTOR runs preserve empty and overflowing raw text, show the localized range error
  and pass all three `VehicleCreationTest` cases on `E1_07_API_36`.
- Final owner-code-review host tests `./gradlew :feature:vehicle:testAndroidHostTest
  :shared:testAndroidHostTest :core:common:testAndroidHostTest` — successful in 5 seconds with 81
  actionable tasks.
- Final Android compilation `./gradlew :androidApp:compileDebugKotlin
  :androidApp:compileDebugAndroidTestKotlin` — successful in 1 second with 113 actionable tasks.
- Final instrumented suite `./gradlew :androidApp:connectedDebugAndroidTest` — successful in 9
  seconds with 212 actionable tasks; all three tests passed on `E1_07_API_36`.
- Final Shared framework link `./gradlew
  :composition:ios:linkDebugFrameworkIosSimulatorArm64` and the exact generated-header/golden
  comparison — successful in 4 seconds with 69 actionable tasks; `diff -u` produced no output.
- The first final repository attempt exposed Android long-method limits and a stale host-contract
  source assertion; the second exposed formatting left in the C-1 GREEN source. Focused lint,
  detekt and build-logic reruns passed after behavior-neutral extraction and formatting.
- Final repository command `./gradlew ktlintCheck detekt architectureCheck contractCheck
  :build-logic:convention:test koverVerify :androidApp:assembleDebug testAndroidHostTest
  iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x
  :integration:firebase-firestore:iosSimulatorArm64Test -x
  :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test` — successful in
  2 seconds with 607 actionable tasks. Contract check passed 92 decision/ADR mirrors with no
  unresolved decision; architecture reported 16 rules over 23 modules.
- Final `git diff --check` — successful with no output.
- N-1 RED common test failed with `expected:<Edited roadster> but was:<Roadster>`; its restoration
  test failed with `expected:<Restored draft> but was:<>` in the captured create command.
- N-1 GREEN and REFACTOR targeted common and API 36 restoration tests passed after one-shot draft
  republication and centralized private edited-field tracking.
- N-2 RED failed with `NoSuchMethodException` for
  `VehicleValidationKt.getINITIAL_ODOMETER_RANGE_KM`. GREEN and REFACTOR API 36 boundary tests
  passed after Android consumed the hidden domain range directly.
- Second-round final verification completed successfully. The first aggregate invocation stopped
  on one overlong N-1 line; the continuation stopped on import ordering and a missing test blank
  line. Focused Vehicle Detekt and Android KtLint checks passed after those behavior-neutral
  formatting corrections, and the remaining 450 aggregate tasks then passed. Targeted host tests,
  Android compilation, all five instrumented tests, Shared framework linking, architecture,
  contracts, coverage, assembly and Android-host/iOS tests are green. The exact golden comparison
  and `git diff --check` produced no output.

## Contract Impact

- `buildAppGraph` returns Kotlin-facing `AppGraph`; `SwiftAppGraph` wraps it.
- Vehicle presentation ownership moves to `:feature:vehicle`; `UiMessage`, `UiMessageKind` and
  `SyncStatus` move to `:core:common`. Vehicle presentation names remain stable, while D-91 makes
  the reviewed common-enum rename intentional and exact.
- `VehicleRepository` gains reactive `observeVehicleEditFacts`; `VehicleEditFacts` is Kotlin-only.
- D-88 records the temporary constant-`Idle` exception to the final single-controller contract.
- `DatabaseFactory.create()` now returns a `DatabaseHandle` that owns its `AppDatabase` and driver;
  `AppGraph.close()` releases it idempotently and `SwiftAppGraph.close()` delegates transitively.
- `VehicleFormUiState.savedVehicleId` is the creation-completion signal; `vehicleId` remains the
  route identity. `SwiftAppGraph` adds keyed release for its three cached holder families.
- `Confirmation`, `AuthProvider` and `SyncTrigger` now have explicit Kotlin-matching Swift names
  and matching `Shared...` Objective-C names.
- `INITIAL_ODOMETER_RANGE_KM` is a Kotlin-only domain declaration used by shared validation and
  the Android adapter; it is hidden from Objective-C and does not alter the Swift-facing ABI.

## Decision Board Impact

- Added accepted D-84 through D-88 with ADR-0085 through ADR-0089 and identical mirrors.
- Added accepted D-89 with ADR-0090 and identical mirrors; D-84 through D-88 were not reopened or
  amended during owner review.
- Added accepted D-90 with ADR-0091 and D-91 with ADR-0092, including identical rows in every
  required mirror; D-84 through D-89 remain unchanged.
- The second-round corrections add no decision or ADR; D-84 through D-91 remain unchanged.

## Shared-Write Modules Touched

- `:core:database` read access gains a reactive active-Fuel-Entry count projection and its public
  factory now returns a closeable lifetime handle. No schema, query source, mutation path or
  migration changes.

## Project Log Entry

- [x] Entry appended.

## Risks or Follow-ups

- D-88 remains open by design after E1-07: Vehicle presentation reports constant `Idle` while the
  D-55 direct restoration adapter exists. E3-03 must wire every exposing holder to one
  `SyncController.status` and add the two-holder convergence test.
- E3-08 still owns completing the staged Fuel, Session and Sync factories on `AppGraph`.
- The owner-review findings about the staged sync exception test, D-28 fixture location, duplicated
  log text and emulator cleanup remain explicitly outside this correction and were not changed.
- The six 2026-08-31 code-review follow-ups listed under “Out of Scope / Not Done” remain
  deliberately unimplemented; E3-03 owns the staged sync and restoration items, while the other
  four require future backlog assignment.
- N-3 and N-4 from the second review remain deliberately unimplemented alongside those six
  pre-existing follow-ups.

## Human Review Gate

- Applies: module boundaries and dependency rules, the Swift-facing API surface and pinned
  versions are gated topics. The owner code-review correction intentionally changes the
  Objective-C golden under D-90 and records exact enum names under D-91. Final owner review is
  still required before merge. The second-round changes do not alter that golden or ABI, and the
  agent MUST NOT merge the pull request.
