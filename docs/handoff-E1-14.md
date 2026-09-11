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
- Current phase and latest commit: review items 1 and 2 complete; item 3 GREEN verified with RED
  `e6bc4fa` and GREEN `c50d427`; item 3 refactor documentation is uncommitted; latest published
  `c50d427`.
- Push and pull-request status: PR #66 remains open; follow-up changes are not pushed yet.
- Completed since the previous checkpoint: committed the ordered-cleanup limitation, added the
  cross-thread last-emission holder and host metadata guard, and verified the off-caller diagnostic.
  Items continue in the requested order.
- Verification evidence and known failures: previous 157/165 counts and 30-run evidence below
  describe the pre-review version. The owner independently reproduced them and neutralized the
  three existing regressions. New/modified regressions will receive fresh RED proof.
- Open decisions or blockers: none. Test-only scope, no production/schema/contract/architecture or
  decision changes; no merge. Retain ordered collector teardown and explicitly document its residual
  unbounded join, as permitted by review item 2.
- RED evidence: `./gradlew :shared:testAndroidHostTest --tests
  'com.ruizurraca.carapp.GraphTestDependenciesTest'` compiled and ran two tests; both failed on
  expected queued execution versus immediate `[main, io, default]`. Log:
  `/tmp/e1-14-review-fixtures-red.log`. In the RED commit, `confinedGraphDependencies` returned its input unchanged.
  The uncommitted GREEN implementation now installs the caller-scheduler dispatcher.
- GREEN evidence: both shared targets and shared ktlint/detekt passed: 159 Android-host and
  167 Native-simulator tests, zero failures/skips. Log: `/tmp/e1-14-review-fixtures-green.log`.
- Non-vacuity proof: neutralizing the shared dispatcher copy produced three intended failures:
  both GraphTestDependenciesTest cases and the fuel factory scheduling guard. The source was
  restored automatically, then all three regressions and shared lint passed.
  Logs: `/tmp/e1-14-review-fixtures-neutralized.log`, `/tmp/e1-14-review-fixtures-refactor.log`.
- Item 3 RED evidence: the host metadata guard failed because `LastEmission.value` was not volatile;
  the off-caller diagnostic test was added in the same RED commit.
- Item 3 GREEN evidence: host `FlowExpectationTest` off-caller diagnostic and
  `LastEmissionVisibilityTest` passed after `@kotlin.concurrent.Volatile` and holder integration.
  Neutralizing the assignment made the off-caller diagnostic test fail, then the source was restored.
- Exact next step: commit the item 3 refactor documentation, then begin item 4 RED for independent
  upstream `CancellationException` handling. Items 4–6 and final repeated/full verification remain.


## Owner Review Follow-up — 2026-09-11 (Active)

The owner independently verified the initial 157/165 tests, 37 migrated waits and non-vacuity of
three regressions. These follow-ups are non-blocking correctness improvements, explicitly ordered
1 through 6. The owner also explicitly requested continuously recoverable repository documentation.
This section and the checkpoint are authoritative for continuation; earlier acceptance counts are
historical until the final follow-up verification finishes.

1. **Shared graph confinement — RED/GREEN/REFACTOR complete.** New files:
   `GraphTestDependencies.kt` and `GraphTestDependenciesTest.kt` in shared commonTest. The two
   tests cover default dependencies and customized doubles; both assert no main/io/default work
   runs before `runCurrent()`, then all queued work runs. The helper now copies a StandardTestDispatcher(testScheduler) into supplied dependencies.
   Its six editor/adoption consumer files have been migrated. The first full GREEN attempt
   exposed five VehicleFormStateHolderTest assertions that read the initial idle state before save
   actually completed (null row or empty push list). Save expectations now also require a non-null
   savedVehicleId; vehicle recovery now requires a nonempty list. These changes passed 159/167 tests plus lint and are included in this GREEN commit.
   Extend this single confined fixture to `VehicleFormStateHolderTest`,
   `VehicleListStateHolderTest`, `SwiftAppGraphLifecycleTest`, graph construction in
   `LocalOwnerAdoptionTest`, and the graph-backed fuel-write case of
   `LocalOwnerAdoptionTriggerTest`. Reuse it from `FuelEntryStateHolderTest` as well. Preserve
   customized database/owner/auth doubles by wrapping existing `testAppGraphDependencies(...)`
   in `confinedGraphDependencies(...)`. Helpers such as `LocalOwnerAdoptionTest.graphOver` will
   need a `TestScope` receiver. Keep deliberately ordered, directly constructed adoption
   coordinator fixtures separate and explain their lack of editable graph state. Audit every
   remaining graph-mounting file and record a concrete reason if it remains unconfined.
   Watch immediate `.state.value` assertions and initial `!isSaving` predicates after switching
   scheduling; wait for actual save completion rather than an initial idle state if necessary.
2. **Unbounded cleanup join — documentation option applied.** Retain structured
   cancellation and join before graph/database teardown. A bounded join alone cannot bound an
   enclosing coroutineScope, which still waits for its children; detaching a stuck collector could
   restore the E1-12 database-close race. State explicitly in helper KDoc and Acceptance Evidence
   that extreme CPU starvation or non-cooperative collector cleanup can delay the diagnostic past
   runTest's timeout. The existing starvation test proves cooperative suspended-collector teardown,
   not an absolute wall-clock bound on all possible cleanup. The owner explicitly permits this
   choice; it does not require a new owner decision or changes outside test code/docs.
3. **Publish last emission safely — RED/GREEN complete; REFACTOR in progress.** Replaced the captured mutable String with a
   small common-code holder whose property is `@kotlin.concurrent.Volatile`. Add a diagnostic
   test with emissions originating off the caller thread. Prove its assertions non-vacuous;
   do not claim a timing-based test can deterministically establish the absence of a JVM/native
   memory-visibility race. A deterministic JVM volatile-field metadata regression is an option
   if needed to prove removing the publication guarantee is RED. The deterministic Android-host
   volatile-field metadata test is the publication guarantee; the common off-caller flow test
   exercises the diagnostic path. The assignment-neutralized run failed that common test, and the
   correct source is restored. Commit the KDoc refactor before item 4.
4. **Cancellation handling — RED/GREEN complete; REFACTOR in progress.** Replaced `runCatching` in async with explicit catches:
   rethrow CancellationException unchanged and capture only other Throwables in Result.failure.
   Add a case for a source that throws CancellationException independently of caller cancellation.
   Preserve the existing cancellation/collector-stop test. Check the observable upstream-cancel
   result carefully: blindly forwarding it from await can still cancel the caller silently;
   do not report that as corrected without an executable assertion. Keep external cancellation
   distinct from upstream cancellation and retain the original cause in any diagnostic. The RED
   test failed when an independent source `CancellationException` escaped as caller cancellation;
   GREEN now reports an `AssertionError` with the original cause while caller cancellation still
   reaches the existing propagation test unchanged. Commit the refactor comments before item 5.
5. **Readability — not started.** Join short predicates in FuelEntryStateHolderTest (odometer,
   total cost, message, deleted entry) and short awaitState calls for confirmed fuel entry,
   saved fuel entry and saved vehicle; VehicleListStateHolderTest saved/recovered vehicle calls;
   LocalOwnerAdoptionFailureTest recovered-after-retry call. Preserve the genuinely long wrapped
   SwiftAppGraphLifecycleTest predicates. The owner measured the requested joined forms below
   120 columns. Do not reintroduce gratuitous breaks through indiscriminate formatting.
6. **Small cleanup — not started.** Move GRAPH_STATE_EXPECTATION_TIMEOUT above awaitState; reuse
   fuelGraphDependencies in graphBootstrapCreatesSettingsWithoutAConsumer; repair the AGENTS.md
   ragged "Until that work / merges" wrap.

After all items: run the full AGENTS.md non-instrumented command plus shared ktlint/detekt;
repeat both shared targets with --rerun-tasks at least 30 times and report actual counts, including
failed attempts. Update Acceptance Evidence, this checkpoint, Files Changed, and append a new
PROJECT_LOG entry without editing the prior story entry. Push existing branch, refresh PR #66,
leave it open and do not merge. Maintain separate RED/GREEN/REFACTOR commits and do not add
production changes, schemas, contracts, architecture rules, decision IDs, libraries or versions.

Resume by checking `git status --short --branch`, `git log -10 --oneline`, this checkpoint, and
these two new test files. No repeat-run process is currently running. RED passed its intended failure proof; the first
full GREEN attempt failed on five premature Vehicle save assertions. The stronger completion
predicates are pending re-verification. Logs are `/tmp/e1-14-review-fixtures-red.log` and
`/tmp/e1-14-review-fixtures-green.log`. Previous repetition evidence belongs to initial delivery.

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

### Review item 1: confinement coverage (current follow-up)

The shared `confinedGraphDependencies` helper preserves customized doubles and replaces only the
injected dispatchers. GraphTestDependenciesTest guards both its default and customized call paths;
the existing fuel-factory scheduling test also exercises its actual delegating factory. The affected
call sites all use one of these two guarded paths, including the direct graph-backed fuel-write case
outside FuelEntryStateHolderTest. Explicit bootstrap duplication is retained only until review item 6.

| File | Confinement disposition and concrete exposure analysis |
|---|---|
| `FuelEntryStateHolderTest.kt` | Its factory delegates to the shared confined helper. The bootstrap case already uses StandardTestDispatcher and will reuse that factory in item 6. SQLite-backed currency/odometer initialization can otherwise compete with form edits. |
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


- Both forced-starvation fixtures compiled and failed against the raw `first(predicate)` RED helper.
  GREEN uses a 20 ms expectation deadline and produces its diagnostic assertion before the independent two-second
  outer bound. One fixture also proves the collector has stopped before the assertion is returned.
- Helper tests cover first matching value, matching null, a virtual one-day delayed emission,
  cancellation propagation/collector completion, and preservation of the original upstream cause.
- Final stability: 30/30 forced complete runs per target on macOS 26.6.2 aarch64 (Apple Silicon),
  zero failures and zero skips. Each run executed 157 Android-host tests and 165 Native-simulator
  tests, including the eight new regressions. This exceeds the historical approximately 1-in-17
  occurrence rate; it is observed stability, not a claim that arbitrary machine starvation is impossible.
- Final shared GREEN reports: Android host 157 tests; iOS simulator 165 tests; 0 failures and 0 skipped.
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

- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/FlowExpectation.kt` and `FlowExpectationTest.kt`.
- The seven migrated files listed in the audit above.
- `AGENTS.md`, `docs/BACKLOG.md`, `docs/PROJECT_LOG.md` and `docs/handoff-E1-14.md`.

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
- Second GREEN: `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test
  :shared:ktlintCheck :shared:detekt` — passed, 157 Android-host / 165 Native tests.
- Final REFACTOR stability: the following command was executed 30 consecutive times, stopping on
  any nonzero exit; every invocation returned zero. Both target reports were inspected after each
  successful invocation: 157 / 165 tests, no failures or skips, on every run.

```bash
./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test --rerun-tasks --quiet
```

- Full non-instrumented repository verification — BUILD SUCCESSFUL in 6s, 636 actionable tasks
  (41 executed, 595 up-to-date). Covers lint, coverage, architecture and its failing fixtures,
  contracts, Android assembly/unit tests, and the shared host/Native graph with the exact D-75
  exclusions. No PENDING contract assertions.

```bash
./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test
```

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
