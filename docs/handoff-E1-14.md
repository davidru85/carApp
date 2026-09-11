# Agent Handoff — E1-14

## Story

`E1-14 - FuelEntryStateHolderTest Kotlin/Native Timeout Flake`

## Ready Check

- Backlog story: `docs/BACKLOG.md`, E1-14, size S; Ready.
- Acceptance criteria reviewed: bounded explicit graph-backed state expectations; reusable helper;
  forced-starvation regression fixture; more than 17 repeated Apple-silicon runs; audit every shared
  graph-mounting test; test-only implementation with no production, schema, contract, architecture
  or decision change.
- Dependencies checked: E1-12 and its handoff (ordered collector teardown), E2-03 and its handoff
  (original timeout evidence), and the Android recurrence recorded in the E1-14 backlog.
- Decisions checked: accepted D-56 (test factory), D-89 (database ownership), D-105 (continuity) and
  D-106 (bootstrap). Pending D-149/D-150 concern unrelated backend stories and do not block E1-14.
- Normative sections reviewed: AGENTS.md in full; SPECIFICATION §11 TDD; CONTRACTS §14 presentation,
  §18 checks and §20.10 holder lifecycle; DECISION_BOARD rows above; CONTRIBUTING conventions.
- Expected verification: focused helper RED on `:shared:testAndroidHostTest`; shared tests on Android
  host and iosSimulatorArm64; at least 30 forced repetitions on each target on this Apple-silicon
  host; shared lint; complete non-instrumented command from AGENTS.md; ten required CI jobs.
- Human review gates identified before work: no gated implementation path/topic; updating AGENTS.md
  repository status would require owner review. PR creation is authorised; merge is not requested.
- Rule 0 acknowledged: all owner conversation uses Spanish (es-ES); repository artifacts use
  technical English.

## In-Progress Checkpoint

- Date: 2026-09-11.
- Branch and base: `story/E1-14-bounded-state-expectations`, base `eb52daf`.
- Current phase and latest commit: the third review round is implemented on the working tree; the
  five misleading `awaitState` descriptions in `VehicleFormStateHolderTest.kt` are corrected, with
  the diagnostic proven correct by a temporary predicate negation. The second review pass is also
  present: dead imports removed, eleven completion predicates joined, shared scheduling assertion,
  explicit `currentCoroutineContext` import, rewritten follow-up section and corrected AGENTS.md
  fixture sentence. Shared verification passed with 162 Android-host / 169 Native tests.
- Push and pull-request status: the second-pass test commit `dc9b48b` and the documentation commits
  are pushed. PR #66 is open, `MERGEABLE`, and its body was refreshed to the final state. Every run
  whose code matched this branch passed all ten required checks, including run 34592642389
  (`edef077`), run 34594176104 (`0e20173`) and the final observed run 34599603577 (`50b7d21`). One
  intermediate documentation-only run, 34595706047, hit the pre-existing `E1-17` flake on its
  `ios-simulator-build` job rather than failing on this change: the failing tests were
  `OnboardingFlowUITests.testFirstRunVehicleFormResistsInteractiveDismissal` at
  `iosApp/UITests/OnboardingFlowUITests.swift:86` and `:36`, and on rerun
  `VehicleAndFuelFlowUITests.testVehicleSwipeDeleteShowsConfirmationDialog` at
  `iosApp/UITests/VehicleAndFuelFlowUITests.swift:214` with "Onboarding did not reach vehicle
  creation before the timeout". Both already exist on `main` (run 34221494080, 2026-09-08, same
  lines and messages), the E1-14 diff touches no file under `iosApp/`, and the flake did not recur
  on the next run. The third-round description correction is not yet committed or pushed.
- Completed since the previous checkpoint: corrected the five misleading expectation descriptions
  in `VehicleFormStateHolderTest.kt`; re-checked the whole file and confirmed every remaining
  description, including line 54, is accurate; ran shared ktlint/detekt, the forced re-run of both
  shared targets and the full non-instrumented repository command; and captured the corrected
  timeout diagnostic by temporarily negating the line-104 predicate.
- Verification evidence and known failures: full shared GREEN with the final code is 162
  Android-host and 169 Native-simulator tests, zero failures and zero skips. The full
  non-instrumented repository command passed. Only the pre-existing expect/actual Beta warnings
  remain.
- Open decisions or blockers: none. Test-only scope, no production/schema/contract/architecture or
  decision changes; no merge. Ordered collector teardown and its explicitly documented residual
  unbounded join are retained.
- Exact next step: commit the description correction, push the branch and report the required-check
  results. Owner reviews PR #66; no merge was requested.
- Publishing checkpoint: the follow-up branch is pushed, and `gh pr edit 66 --repo davidru85/carApp
  --body-file /tmp/e1-14-pr.md` refreshed the PR body. The live required-check status is:
  android-assemble, android-instrumented-tests, architecture-check, contract-check, detekt,
  ios-simulator-build, ktlint, objc-header-golden-check, provider-decoupling and shared-tests, all
  passing on the latest observed run.


## Owner Review Follow-up — 2026-09-11

The owner independently verified the initial 157/165 tests, the 37 migrated waits and the
non-vacuity of three regressions, and then ordered six non-blocking follow-ups. All six were
implemented and verified. The owner also required continuously recoverable repository
documentation, which is why this section and the checkpoint are the authoritative continuation
record while the earlier acceptance counts remain historical.

1. **Shared graph confinement — completed.** `GraphTestDependencies.kt` and
   `GraphTestDependenciesTest.kt` were added in shared commonTest. The two tests cover the default
   dependencies and the customized doubles; both assert that no main/io/default work runs before
   `runCurrent()` and that all queued work then runs. `confinedGraphDependencies` copies a
   `StandardTestDispatcher(testScheduler)` into the supplied dependencies, preserving the
   customized database, owner and auth doubles. Six editor/adoption consumer files were migrated:
   `VehicleFormStateHolderTest`, `VehicleListStateHolderTest`, `SwiftAppGraphLifecycleTest`, the
   graph construction in `LocalOwnerAdoptionTest`, the graph-backed fuel-write case of
   `LocalOwnerAdoptionTriggerTest`, and `FuelEntryStateHolderTest` (through its
   `fuelGraphDependencies` wrapper). The first full GREEN attempt exposed five
   `VehicleFormStateHolderTest` assertions that read the initial idle state before the save had
   completed (a null row or an empty push list); the completion predicates were strengthened so
   that saves also require a non-null `savedVehicleId` and vehicle recovery requires a nonempty
   list. No assertion was removed or weakened. The decision to confine was driven by the
   demonstrated race: an unconfined fixture resumes initialization after a SQLite suspension on a
   worker thread while test intents execute on the test thread, and both copy the mutable form
   input, so edits can be lost. The deliberately ordered, directly constructed adoption
   coordinator fixtures were left unconfined because they execute and await a single adoption
   operation and never construct an editable form holder whose initialization can overwrite test
   edits. The concrete criterion that keeps those remaining `testAppGraphDependencies(...)` call
   sites unconfined is that each one constructs `LocalOwnerAdoption(...)` directly, with no
   AppGraph and no editable form holder; this covers the direct-construction helpers in
   `LocalOwnerAdoptionTest`, `LocalOwnerAdoptionTriggerTest` and `LocalOwnerAdoptionFailureTest`.
   The other graph-mounting files that still call `testAppGraphDependencies(...)`
   (`BuildAppGraphTest`, `AppGraphContractTest`, `AppGraphTestHarnessTest`, `AppGraphCloseTest`,
   `SessionStateHolderTest` and `TestAppGraphDependenciesTest`) remain unconfined for their own
   per-file reasons recorded in the confinement table below; none of them creates an editable form
   holder that a database-backed initialization could overwrite.
2. **Unbounded cleanup join — documented, not changed.** Structured cancellation and joining
   before graph/database teardown were retained. The decision was that a bounded join alone cannot
   bound an enclosing `coroutineScope`, which still waits for its children, and that detaching a
   stuck collector could restore the E1-12 database-close race. The helper KDoc and Acceptance
   Evidence therefore state explicitly that extreme CPU starvation or non-cooperative collector
   cleanup can delay the diagnostic past `runTest`'s timeout. The starvation regression proves
   cooperative suspended-collector teardown, not an absolute wall-clock bound on all cleanup. The
   owner explicitly permitted this choice; it required no owner decision and no change outside
   test code and documentation.
3. **Last emission published safely — completed.** The captured mutable `String` was replaced by
   a small common-code holder whose property is `@kotlin.concurrent.Volatile`. A diagnostic test
   with emissions originating off the caller thread was added, and its assertions were proven
   non-vacuous by neutralizing the assignment. No claim was made that a timing-based test can
   deterministically establish the absence of a JVM/native memory-visibility race. The
   deterministic JVM volatile-field metadata regression is the executable publication guarantee;
   the common off-caller flow test exercises the diagnostic path. `LastEmissionVisibilityTest`
   lives in `androidHostTest` and proves the publication guarantee through JVM field metadata
   only, so the Kotlin/Native side rests on the compiler honouring `@Volatile` and is not covered
   by an executable guard.
4. **Cancellation handling — completed.** `runCatching` in the async collector was replaced by
   explicit catches that rethrow `CancellationException` unchanged and capture only other
   `Throwable`s in `Result.failure`. A case for a source that throws `CancellationException`
   independently of caller cancellation was added, and the existing cancellation/collector-stop
   test was preserved. The observable upstream-cancel result was checked carefully: blindly
   forwarding it from `await` can still cancel the caller silently, so the RED test asserted that
   an independent source `CancellationException` must not cancel the caller. GREEN now reports an
   `AssertionError` with the original cause, while external caller cancellation still reaches the
   existing propagation test unchanged.
5. **Readability — completed.** Short predicates in `FuelEntryStateHolderTest` (odometer, total
   cost, message, deleted entry) and short `awaitState` calls for confirmed fuel entry, saved
   fuel entry and saved vehicle were joined; the same was done for the `VehicleListStateHolderTest`
   saved/recovered vehicle calls and the `LocalOwnerAdoptionFailureTest` recovered-after-retry
   call. The genuinely long wrapped SwiftAppGraphLifecycleTest predicates were preserved at the
   time, and the later review confirmed that the eleven completion predicates that had been left
   wrapped fit within the 120-column limit and could be joined as well. The four-condition
   weighted-consumption predicate in `FuelEntryStateHolderTest` remains wrapped because it is
   genuinely long.
6. **Small cleanup — completed.** `GRAPH_STATE_EXPECTATION_TIMEOUT` was moved above `awaitState`;
   `fuelGraphDependencies` was reused in `graphBootstrapCreatesSettingsWithoutAConsumer`; and the
   `AGENTS.md` "Until that work merges" wrap was repaired.

After the six items, the full `AGENTS.md` non-instrumented command plus shared ktlint/detekt were
run, both shared targets were repeated with `--rerun-tasks` at least 30 times with actual counts
reported including failed attempts, Acceptance Evidence, this checkpoint, Files Changed and a new
PROJECT_LOG entry were updated without editing the prior story entry, and the existing branch was
pushed. Separate RED/GREEN/REFACTOR commits were maintained. No production change, schema,
contract, architecture rule, decision ID, library or version was added.

Final review evidence: the full command passed, and 30/30 fresh direct repetitions passed per
target, each executing 162 Android-host and 169 Native-simulator tests with zero failures and zero
skips. The first scripted attempt exited before tests because the wrapper lock was inaccessible;
it is retained as a non-executed infrastructure attempt. The earlier five premature-save failures
were resolved by stronger completion predicates, and the subsequent 162/169 suite passed. Logs for
the fixture phase remain `/tmp/e1-14-review-fixtures-red.log` and
`/tmp/e1-14-review-fixtures-green.log`.

## Expectation Description Correction — 2026-09-11

The owner's third review round found that five `awaitState` descriptions in
`VehicleFormStateHolderTest.kt` named behavior their enclosing test does not exercise. Because the
`expectation` argument is the only human-readable part of the timeout diagnostic
(`Timed out after $timeout waiting for $expectation. Last value: ...`), a timeout at those sites
would have pointed the reader at the wrong behavior, defeating the actionable-diagnostic purpose of
E1-14. The five strings had the shape of text copied from another suite and were corrected to name
the state each test actually waits for:

| Line | Test | Previous description | Corrected description |
|---|---|---|---|
| 104 | `saveEnqueuesTheClosedRemoteVehicleSnapshot` | `vehicle validation finished` | `vehicle outbox snapshot saved` |
| 175 | `savePushesTheSnapshotOnlyAfterTheLocalTransactionCommits` | `anonymous vehicle creation finished` | `vehicle local commit finished` |
| 218 | `vehicleOutboxPayloadWithEntityTypeReachesRemoteSyncSourceAsAValidSnapshot` | `backup failure save finished` | `vehicle outbox payload saved` |
| 257 | `successfulRemoteAckMarksTheVehicleSyncedAndClearsItsOutboxRow` | `vehicle edit finished` | `vehicle remote ack applied` |
| 307 | `localOwnerSavePersistsPendingVehicleWithoutOutboxOrRemotePush` | `invalid vehicle edit finished` | `local owner vehicle save finished` |

Only the five description strings changed. No predicate, timeout, assertion, fixture, import or
other file was touched, and the strengthened `savedVehicleId != null && !state.isSaving` predicates
were left exactly as they were. The descriptions use lowercase technical English consistent with
the 43 correct descriptions in the other six migrated files, and line 54
(`vehicle creation finished`, in `savePersistsACompletePendingVehicleForTheCurrentOwner`) was
re-checked and is already accurate.

Diagnostic evidence: the predicate at line 104 was temporarily negated to `{ false }`, the test was
run on the Android host target, and the captured assertion message was:

```text
java.lang.AssertionError: Timed out after 5s waiting for vehicle outbox snapshot saved. Last value: VehicleFormUiState(vehicleId=null, savedVehicleId=00000000-0000-4000-8000-000000000001, name=, initialOdometerKm=0, brand=null, model=null, fuelType=GASOLINE, canEditInitialOdometer=true, isSaving=false, message=null)
```

The message names the behavior the test exercises (`vehicle outbox snapshot saved`) and includes the
last observed state. The source was then restored and the test re-run green, with the final forced
re-run of both targets still reporting 162 Android-host and 169 Native tests, zero failures and zero
skips. This is a diagnostic-message correction with no behavioral change, so it required no RED
test and was isolated in a single `refactor(E1-14): correct expectation descriptions in the vehicle
form fixtures` commit, consistent with the earlier readability-only changes in this story.

## Scope Completed

- Added `Flow<T>.awaitState(expectation, timeout, predicate)` in shared commonTest, with a default
  five-second real-time deadline, a mandatory description and last-emission diagnostics.
- Kept collection on the caller context and cancellation structured; no production dispatcher changed.
- Confined the fuel graph fixtures to StandardTestDispatcher(testScheduler), including persisted
  settings setup. The graph-bootstrap test already used that dispatcher. A deterministic fixture
  test prevents replacing it with an eagerly executing unconfined dispatcher.
- Migrated all 37 raw predicate flow waits in seven files, including direct database settings and
  auth-flow waits adjacent to graph-backed holder tests.
- Added seven focused helper tests, including two independently bounded starvation fixtures,
  and one deterministic graph-fixture scheduling regression.
- Audited all 11 graph-mounting test files plus the directly constructed adoption-failure holder.

## Acceptance Evidence

### Review item 2: ordered cleanup and its explicit residual risk

The helper deliberately retains the unbounded cancelAndJoin and its structured child scope.
Returning after a secondary join timeout would either still wait at coroutineScope exit, or require
detaching a live collector and allowing graph/database close while it can still execute. The latter
would reintroduce the E1-12 resource-lifetime hazard. The ordered-teardown guarantee is preferred
for these database-backed fixtures; this is the documentation option explicitly allowed by the owner.

The five-second deadline bounds waiting for a matching emission, not total helper return time.
Extreme CPU starvation or non-cooperative collector cleanup can delay the diagnostic beyond
runTest's 60-second default and still yield UncompletedCoroutinesError. The existing
starvedEmissionFailsWithTheExpectationAndLastValueBeforeTheOuterDeadline test proves that a
cooperatively suspended collector is cancelled and stopped before its assertion is returned.
It does not prove a hard bound for arbitrary cleanup, and its assertions remain unchanged.
This limit is now stated in the helper KDoc as well as here.

### Review item 1: confinement coverage

The shared `confinedGraphDependencies` helper preserves customized doubles and replaces only the
injected dispatchers. GraphTestDependenciesTest guards both its default and customized call paths;
the existing fuel-factory scheduling test also exercises its actual delegating factory. The affected
call sites all use one of these two guarded paths, including the direct graph-backed fuel-write case
outside FuelEntryStateHolderTest. The bootstrap case now reuses the same fuel factory, so the
earlier explicit bootstrap duplication no longer exists.

The scheduling-contract assertion is shared: `GraphTestDependencies.kt` exposes
`TestScope.assertQueuedGraphWork(dependencies)`, which both `GraphTestDependenciesTest` and
`FuelEntryStateHolderTest.graphFixtureWorkWaitsForTheCallerTestScheduler` call. The fuel test is
therefore an assertion that its `fuelGraphDependencies()` wrapper delegates to the confined fixture
rather than a second copy of the scheduling proof.

| File | Confinement disposition and concrete exposure analysis |
|---|---|
| `FuelEntryStateHolderTest.kt` | Its factory delegates to the shared confined helper, and the bootstrap case now reuses that same factory. SQLite-backed currency/odometer initialization can otherwise compete with form edits. |
| `VehicleFormStateHolderTest.kt` | All six current creation tests use the confined helper. Their success waits require savedVehicleId as well as !isSaving, so initial idle state cannot masquerade as completion. The fixture also protects future edit/load interleavings. |
| `VehicleListStateHolderTest.kt` | Both graph fixtures use the helper. Creation waits for savedVehicleId; recovery waits for a nonempty loaded list before asserting database/remote evidence. |
| `SwiftAppGraphLifecycleTest.kt` | Customized database dependencies are confined before constructing the real graph and Swift wrapper; both Swift forms therefore use the same scheduler as test intents. |
| `LocalOwnerAdoptionTest.kt` | Both graph-construction paths use the helper, including the TestScope.graphOver factory. Direct coordinator-only tests retain their controlled unconfined doubles: they execute and await one adoption operation and never construct an editable holder whose initialization can overwrite test edits. |
| `LocalOwnerAdoptionTriggerTest.kt` | The graph-backed fuel-write test uses the helper. The separate direct-coordinator trigger fixtures intentionally model inline owner/auth/connectivity ordering, have no editable graph form, and are not converted to deferred dispatch. |
| `BuildAppGraphTest.kt` | Left unchanged: constructs a graph only to compare supplied and retained dependencies, invokes no holder/edit intent, then closes it. There is no shared mutable form input for initialization to overwrite. |
| `AppGraphContractTest.kt` | Left unchanged: only constructs a list holder and a new vehicle form to assert their types. It never invokes setters, save or an existing-vehicle load. No concurrent editor mutation exists. |
| `AppGraphTestHarnessTest.kt` | Left unchanged: checks cancellation order, collector eagerness and invalid parent-scheduler construction. It does not create editable holders or mutate form input; changing its deliberately non-test parent would invalidate its rejection fixture. |
| `AppGraphCloseTest.kt` | Left unchanged: directly checks close/bootstrap/auth-observer ownership without constructing a form or issuing edit/save intents. In particular its immediate-close/bootstrap test must preserve the lifecycle condition it was written to exercise. |
| `SessionStateHolderTest.kt` | Left unchanged: graph-backed cases exercise fake auth transitions and conversion analytics, not SQLite-backed editable form initialization. Auth replies are controlled/synchronous and session intents do not mutate a vehicle/fuel FormInputs snapshot that a database callback also copies. This is not a claim of blanket thread safety for arbitrary future session tests. |
| `LocalOwnerAdoptionFailureTest.kt` | No AppGraph: constructs a read-only vehicle list against a fault-injected adoption gate. Its retry toggles a fault and awaits error/recovery states, without an editable form or concurrent initialization/edit copies. |

The first confined-suite attempt exposed five premature-save assertions (null database rows or
empty push recordings). Those assertions were fixed to wait for actual successful completion;
none was removed. Subsequent shared tests and lint passed: 159 Android-host / 167 Native tests.

Review RED/GREEN evidence, retained from the follow-up checkpoint:

- Confinement RED: `./gradlew :shared:testAndroidHostTest --tests
  'com.ruizurraca.carapp.GraphTestDependenciesTest'` compiled and ran two tests; both failed on
  expected queued execution versus immediate `[main, io, default]`. In the RED commit,
  `confinedGraphDependencies` returned its input unchanged. Log:
  `/tmp/e1-14-review-fixtures-red.log`.
- Confinement GREEN: both shared targets and shared ktlint/detekt passed with 162 Android-host and
  169 Native tests. Log: `/tmp/e1-14-review-fixtures-green.log`.
- Non-vacuity: neutralizing the shared dispatcher copy produced three intended failures (both
  `GraphTestDependenciesTest` cases and the fuel factory scheduling guard); the source was restored
  and all three regressions plus shared lint passed. Logs:
  `/tmp/e1-14-review-fixtures-neutralized.log`, `/tmp/e1-14-review-fixtures-refactor.log`.
- Publication RED/GREEN: the host metadata guard failed while `LastEmission.value` was not volatile;
  the off-caller diagnostic test was added in the same RED commit. Both passed after
  `@kotlin.concurrent.Volatile` and holder integration, and neutralizing the assignment made the
  off-caller diagnostic test fail before the source was restored.
- Cancellation RED/GREEN: the independent upstream cancellation test failed before the explicit
  catch and passed after it; the existing caller-cancellation test still passes unchanged.


- Both forced-starvation fixtures compiled and failed against the raw `first(predicate)` RED helper.
  GREEN uses a 20 ms expectation deadline and produces its diagnostic assertion before the independent two-second
  outer bound. One fixture also proves the collector has stopped before the assertion is returned.
- Helper tests cover first matching value, matching null, a virtual one-day delayed emission,
  cancellation propagation/collector completion, and preservation of the original upstream cause.
- Final review stability: 30/30 forced complete runs per target on macOS 26.6.2 aarch64 (Apple
  Silicon), zero failures and zero skips. Each run executed 162 Android-host tests and 169
  Native-simulator tests, including the review regressions. This exceeds the historical approximately
  1-in-17 occurrence rate; it is observed stability, not a claim that arbitrary machine starvation
  is impossible. One preliminary wrapper-lock invocation exited before tests and is not counted.
- Final shared GREEN reports: Android host 162 tests; iOS simulator 169 tests; 0 failures and 0 skipped.
- `graphFixtureWorkWaitsForTheCallerTestScheduler` failed before the dispatcher change with
  `expected: [] but was: [main, io, default]`. After the fix, no graph work runs until `runCurrent()`,
  and then all three dispatchers complete their queued work.
- Historical stability attempts, deliberately retained rather than discarded:

| Version | Android host | Native simulator | Diagnostic |
|---|---|---|---|
| Bounded waits only | 24 pass, 1 fail | 24 pass; iteration 25 not executed | Empty fuel list after save; five-second assertion fired. |
| Uncommitted UnconfinedTestDispatcher experiment | 28 pass, 0 fail | 27 pass, 1 fail | Liters overwritten to null while price remained 1789; five-second assertion fired. |

Unconfined execution can resume initialization after SQLite suspension on a worker thread while
form intents execute on the test thread. Both initialization and edits copy mutable form input,
so the fixture permits lost updates that the product's confined main dispatcher prevents.
Merely adding a timeout or substituting UnconfinedTestDispatcher did not remove that test race.
The final fixture uses StandardTestDispatcher on the caller scheduler and leaves product code intact.
- Audit (paths below are relative to `shared/src/commonTest/kotlin/com/ruizurraca/carapp/`):

| Audited file | Finding and treatment |
|---|---|
| `FuelEntryStateHolderTest.kt` | 14 waits migrated, including graph bootstrap and shared save helpers; harness teardown retained. |
| `VehicleFormStateHolderTest.kt` | 6 save-completion state waits migrated. |
| `VehicleListStateHolderTest.kt` | 4 save/list/recovery waits migrated. |
| `SwiftAppGraphLifecycleTest.kt` | 2 saved-vehicle waits migrated; Swift-owned lifecycle retained. |
| `LocalOwnerAdoptionTest.kt` | 6 list/save/session/auth waits migrated. |
| `LocalOwnerAdoptionTriggerTest.kt` | 2 auth waits migrated, including the graph-backed fuel-write case. |
| `AppGraphTestHarnessTest.kt` | No state predicate wait; deliberate awaitCancellation collector is cancelled and joined by the harness. |
| `AppGraphCloseTest.kt` | No state predicate wait; lifecycle/bootstrap tests use explicit cancellation and immediate assertions. |
| `AppGraphContractTest.kt` | No wait; checks the returned holder types. |
| `BuildAppGraphTest.kt` | No wait; checks graph dependency mapping. |
| `SessionStateHolderTest.kt` | Graph-backed session state is inspected after scheduler advancement; no predicate wait. |
| `LocalOwnerAdoptionFailureTest.kt` | Additional audit of a database-backed holder constructed without an AppGraph: 3 error/retry/auth waits migrated. |

The remaining shared test files construct coordinators or holders with controlled doubles rather
than mounting a graph, or cover static contracts/platform adapters. None imports `flow.first`.
The only remaining predicate `first` in shared test sources is encapsulated in `FlowExpectation.kt`.
Finite `flowOf(...).toList()` in the odometer-suggestion test is not an unbounded state expectation.
The pre-existing non-flow adoption polling loops remain outside this state-emission fix.

## Out of Scope / Not Done

- Production timing, holders, graph, database and schema changes; E1-17 iOS host flakes.

## Files Changed

- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/FlowExpectation.kt`, `FlowExpectationTest.kt`,
  `LastEmission.kt`, `GraphTestDependencies.kt` and `GraphTestDependenciesTest.kt`.
- `shared/src/androidHostTest/kotlin/com/ruizurraca/carapp/LastEmissionVisibilityTest.kt`.
- The seven migrated files listed in the audit above.
- `AGENTS.md`, `docs/BACKLOG.md`, `docs/PROJECT_LOG.md` and `docs/handoff-E1-14.md`.
- Second review pass: dead imports removed from `FlowExpectation.kt` and
  `FuelEntryStateHolderTest.kt`; the shared `assertQueuedGraphWork` assertion moved into
  `GraphTestDependencies.kt`; eleven completion predicates joined across
  `VehicleFormStateHolderTest.kt`, `VehicleListStateHolderTest.kt`, `LocalOwnerAdoptionTest.kt` and
  `SwiftAppGraphLifecycleTest.kt`; explicit `currentCoroutineContext` import in
  `FlowExpectationTest.kt`. No new files.
- Third review round: five `awaitState` description strings corrected in
  `VehicleFormStateHolderTest.kt` only. No other file changed.

## Decisions Made

- The owner's explicit one-push-after-REFACTOR instruction supersedes the default phase-by-phase
  push cadence for this story. Separate RED, GREEN and REFACTOR commits are retained.
- No technical decision introduced: E1-14 already requires a reusable bounded expectation in tests.
  No new library, product behavior or normative policy is needed.
- Two TDD cycles were needed: the first makes unbounded waits diagnostic; repetition then exposed
  the independent test-fixture scheduling defect, which received its own deterministic RED test,
  GREEN implementation and REFACTOR verification. All cycle commits remain local until the final
  push requested by the owner. No TDD exemption or force push is used.

## Verification Run

- First RED: `./gradlew :shared:testAndroidHostTest --tests
  'com.ruizurraca.carapp.FlowExpectationTest'` — 7 compiled tests, 2 expected failures. The unbounded
  helper reached the independent two-second deadline instead of returning the required assertion.
- First GREEN: `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test` — passed
  (156 Android-host / 164 Native tests before the scheduling regression was added).
- First REFACTOR: `./gradlew :shared:testAndroidHostTest --tests
  'com.ruizurraca.carapp.FlowExpectationTest' :shared:ktlintCheck :shared:detekt` — passed. The
  separate fixture flake found during repetition is recorded above and was not waved through.
- Second RED: `./gradlew :shared:testAndroidHostTest --tests
  'com.ruizurraca.carapp.FuelEntryStateHolderTest.graphFixtureWorkWaitsForTheCallerTestScheduler'`
  — expected assertion failure, `expected: [] but was: [main, io, default]`.
- Review RED/GREEN: the volatile metadata guard and independent upstream-cancellation expectation
  each failed before their corresponding fixes; both now pass. Neutralizing the last-emission
  assignment also failed the off-caller diagnostic test, then the source was restored.
- Final shared verification: `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test
  :shared:ktlintCheck :shared:detekt` — passed, 162 Android-host / 169 Native tests.
- Final REFACTOR stability: the following command was executed 30 consecutive times, stopping on
  any nonzero exit; every invocation returned zero. Both target reports were inspected after each
  successful invocation: 162 / 169 tests, no failures or skips, on every run.

```bash
./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test --rerun-tasks --quiet
```

- Full non-instrumented repository verification — BUILD SUCCESSFUL in 6s, 638 actionable tasks
  (39 executed, 599 up-to-date). Covers lint, coverage, architecture and its failing fixtures,
  contracts, Android assembly/unit tests, and the shared host/Native graph with the exact D-75
  exclusions. No PENDING contract assertions.

```bash
./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test
```

- Second review pass: after removing the dead imports, joining the eleven predicates and sharing
  `assertQueuedGraphWork`, `./gradlew :shared:ktlintCheck :shared:detekt` and
  `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test --rerun-tasks` both passed.
  The forced re-run reported 162 Android-host and 169 Native tests, zero failures and zero skips.
  The full non-instrumented command above was re-run on the same final code and passed (638
  actionable tasks, 39 executed). The only remaining warnings are the pre-existing expect/actual
  Beta notices.

- Third review round: `./gradlew :shared:ktlintCheck :shared:detekt` passed;
  `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test --rerun-tasks` executed both
  tasks (not UP-TO-DATE) and reported 162 Android-host and 169 Native tests with zero failures and
  zero skips; the full non-instrumented command above passed on the same code. The corrected
  diagnostic was proven by temporarily negating the line-104 predicate and capturing
  `Timed out after 5s waiting for vehicle outbox snapshot saved. Last value: VehicleFormUiState(...)`,
  then restoring the source and re-running the test green.

- Source audit: `rg -n 'kotlinx.coroutines.flow.first|\.first\s*\{' shared/src/commonTest
  shared/src/iosTest` — only the encapsulated call/import in `FlowExpectation.kt` remains.
- `git diff --check` — clean.
- CI: PR #66 triggered all ten required jobs; their live results are tracked in the PR.
  This test-only change does not alter the iOS application or Shared public framework surface.

## Contract Impact

- No contract changes.

## Decision Board Impact

- No decision changes.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [x] Entry appended

## Risks or Follow-ups

- The five-second bound is a real-time test expectation, not a product deadline. Under extreme host
  starvation, it can deliberately fail with a diagnostic assertion; it does not silently retry.
- E1-17 iOS host UI flakes and the historical D-89 production graph-close follow-up remain separate.
- Ordinary runtime warnings about Gradle 10 deprecations and expect/actual beta classes are unchanged.

## Human Review Gate

- Applies: `AGENTS.md` is a gated path. The change only updates repository status; the owner
  reviews and merges the PR. No product contract or runtime path is changed.
