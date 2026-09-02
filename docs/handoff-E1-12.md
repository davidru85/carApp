# Agent Handoff - E1-12

## Story

`E1-12 - FuelEntryStateHolderTest Kotlin/Native SIGSEGV on Graph Close - S`

## Ready Check

- Backlog story: explicit and open in `docs/BACKLOG.md`; GitHub issue #42 contains the diagnosed
  failure sequence and reproduction evidence.
- Acceptance criteria reviewed: make `:shared:iosSimulatorArm64Test` deterministic by moving every
  `:shared` test-owned state-holder scope and collector under a reusable child scope that is
  cancelled before its `AppGraph` closes; audit every `:shared` test that mounts an `AppGraph`;
  keep the fix in test code; record the repeated-run count.
- Dependencies checked: E1-08 introduced the affected tests and is complete. E1-12 has no
  dependency on E1-11; E1-11 is now merged into the `main` base at `64c91d6`. No other story is in
  flight in this working tree.
- Decisions checked: D-75, D-86, D-89 and D-106 are `Accepted` and govern the Native test route,
  graph separation, database-handle lifetime and graph bootstrap cancellation. The decision board
  contains no `Proposed` or `Pending` rows. The story explicitly requires no new decision.
- Normative sections reviewed: `docs/SPECIFICATION.md` §8.4 and §11;
  `docs/CONTRACTS.md` §11.6, §14 and §20.10; `docs/TECHNICAL_PLAN.md` §4 and §12; ADR-0090 / D-89;
  `AGENTS.md` Definition of Ready, Continuous Progress Documentation, Definition of Done and Human
  Review Gates.
- Expected verification: a focused RED test that deterministically proves cancellation must
  precede graph close; focused `:shared:testAndroidHostTest` and `:shared:iosSimulatorArm64Test`;
  repeated Apple-silicon `:shared:iosSimulatorArm64Test` runs; the complete non-instrumented command
  from `AGENTS.md`; `git diff --check`; and all required pull-request checks.
- Human review gates identified before work: applies because the E1-12 backlog story explicitly
  requires human review. The change must remain test-only and the agent MUST NOT merge the pull
  request.
- Rule 0 acknowledged: owner conversation is Spanish (Spain); every repository artifact, branch,
  commit and pull-request field is technical English.
- TDD workflow: the owner's requested `NETWORK -> GREEN -> REFACTOR -> one push -> PR` sequence is
  interpreted as `RED -> GREEN -> REFACTOR -> one push -> PR`, because E1-12 introduces no network
  behavior and the remaining terms exactly match the repository TDD workflow. The requested single
  push after the three local commits explicitly supersedes the default push-after-each-phase rule
  for this story.

## In-Progress Checkpoint

- Date: 2026-09-02.
- Branch and base: `story/E1-12-shared-test-graph-close-race`, based on `main` / `origin/main` at
  `64c91d6` (merged PR #48).
- Current phase and latest commit: RED `ed698e0`, GREEN `55f075c`, REFACTOR `a7fb619`, the
  pull-request continuity checkpoints `b23b4d2` and `a4009df`, the review-fix dispatcher
  correction `27030d6`, the shared-factory hardening `fa69083`, the review-findings record
  `eb8755f`, and the second review round RED `5b9cb46`, GREEN `92bc607` and REFACTOR
  `4d86f82` are complete. All phases are finished; only the final documentation checkpoint
  remains in flight.
- Push and pull-request status: all commits through the final documentation checkpoint are
  pushed; PR #49 is open at `https://github.com/davidru85/carApp/pull/49` and MUST NOT be
  merged by the agent.
- Completed since the previous checkpoint: resolved the second review round of PR #49.
  Extended `constructorThrowsWhenParentScopeHasNoTestCoroutineScheduler` to retain the parent
  `Job` explicitly and assert that a failed construction leaves no orphaned child attached
  (confirmed RED: `SupervisorJobImpl{Active}` remained attached). Moved the scheduler
  resolution and validation in `AppGraphTestHarness` before the `scopeJob` creation so a
  missing `TestCoroutineScheduler` fails before any `Job` is created. Made every teardown in the
  harness tests exception-safe with nested `try/finally` so `owningFactory.close()` always
  runs, and removed the obsolete `DatabaseFactory` and `DatabaseHandle` imports from
  `AppGraphCloseTest.kt`. Valid harness behavior is unchanged: collectors remain children of
  `scopeJob`, run eagerly through `UnconfinedTestDispatcher` and are cancelled and joined
  before `graph.close()`.
- Verification evidence and known failures: the extended constructor test failed as expected
  before the fix (BUILD FAILED; expected `[]` children but observed
  `[SupervisorJobImpl{Active}]`). After the fix, focused `:shared:testAndroidHostTest`
  harness tests passed (3/3), and `./gradlew :shared:testAndroidHostTest
  :shared:iosSimulatorArm64Test :shared:ktlintCheck :shared:detekt --rerun-tasks` passed with
  32 tests and 0 failures/skips on each target. The complete non-instrumented repository
  command passed with 627 actionable tasks; `contractCheck` reported no unresolved decisions
  and no `PENDING` assertions; `git diff --check` is clean; the source audit still finds no
  direct `backgroundScope.launch` state-holder collector and no state-holder factory receiving
  `backgroundScope` under `shared/src/commonTest`. Historical GREEN/REFACTOR evidence (30
  tests per target, 10/10 forced Native runs, CI run `33625103198` with 3/3 successful
  macOS shared-test executions) predates the second review round and is superseded by the
  final post-review verification above.
- Open decisions or blockers: no technical decision is open. Only the mandatory human review
  and the final CI run on the pushed HEAD remain. Production hardening of `AppGraph.close()`
  remains explicitly deferred outside E1-12 because it would change D-89 and touch gated
  `core/database/**`.
- Exact next step: commit this final CI-evidence checkpoint, push it, refresh the PR #49
  description to match this handoff, and leave PR #49 for owner review and merge.
- Final CI evidence: GitHub Actions run
  `https://github.com/davidru85/carApp/actions/runs/33667021311` on the final HEAD `9258821`
  passed all ten required jobs: `ktlint`, `detekt`, `architecture-check`, `contract-check`,
  `android-assemble`, `android-instrumented-tests`, `shared-tests`, `ios-simulator-build`,
  `objc-header-golden-check` and `provider-decoupling`. The macOS `shared-tests` job
  `100371177265` passed in 2m26s with no process signal.

## Scope Completed

- Added one reusable test harness that owns a child scope, launches state-holder collectors and
  cancels and joins that scope before closing its graph.
- Migrated every Kotlin caller-owned `AppGraph` state-holder scope in `:shared` tests to the
  harness.
- Removed all direct state-holder collector launches on `runTest.backgroundScope`.
- Audited every `:shared` test class that mounts an `AppGraph`.

## Acceptance Evidence

- `AppGraphTestHarnessTest.closeCancelsCollectorsBeforeClosingTheGraph` deterministically observes
  `collectors-cancelled` before `graph-closed` on Android host and Kotlin/Native.
- `FuelEntryStateHolderTest` uses `AppGraphTestHarness.scope` for every state holder and
  `AppGraphTestHarness.collect(...)` for every long-lived state or save-completion collector; its
  `finally` blocks call only `harness.close()`.
- `AppGraphContractTest`, `VehicleFormStateHolderTest` and `VehicleListStateHolderTest` use the
  harness for every Kotlin caller-owned state-holder scope and graph teardown.
- Source audit finds no direct `backgroundScope.launch` and no state-holder factory receiving
  `backgroundScope` anywhere under `shared/src/commonTest`.
- `constructorThrowsWhenParentScopeHasNoTestCoroutineScheduler` proves a failed harness
  construction leaves no orphaned child `Job` on the parent, and its teardown cancels the parent
  `Job` even if an assertion fails.
- Final post-review counts (generated reports): 32 tests, 0 failures, 0 skipped on
  `:shared:testAndroidHostTest`; 32 tests, 0 failures, 0 skipped on
  `:shared:iosSimulatorArm64Test`. Historical evidence below records the pre-review 30-test
  baseline and the pre-review CI executions.
- Historical (pre-review): `:shared:iosSimulatorArm64Test` passed 10 consecutive forced runs on
  the local Apple-silicon host, and GitHub Actions run `33625103198` passed all 10 required jobs
  with 3/3 successful macOS `shared-tests` executions and no SIGSEGV.
- Graph-mounting audit:
  - `AppGraphCloseTest.kt`: no caller-owned state holder or external collector; directly verifies
    idempotent direct and Swift-transitive graph close plus bootstrap cancellation.
  - `AppGraphContractTest.kt`: Kotlin caller-owned holder scopes migrated to the harness.
  - `AppGraphTestHarnessTest.kt`: uses the harness and observes its cancellation-before-close
    contract.
  - `BuildAppGraphTest.kt`: no state holder or external collector; only inspects dependency mapping
    before direct graph close.
  - `FuelEntryStateHolderTest.kt`: every holder scope and collector migrated to the harness.
  - `SessionStateHolderTest.kt`: uses `SwiftAppGraph`, which owns and closes its holder child scope
    before closing the wrapped graph under the existing D-89 contract.
  - `SwiftAppGraphLifecycleTest.kt`: uses the same Swift-owned lifecycle and explicitly verifies a
    released holder scope is inactive before final graph close.
  - `VehicleFormStateHolderTest.kt`: every Kotlin caller-owned holder scope migrated to the harness.
  - `VehicleListStateHolderTest.kt`: every Kotlin caller-owned holder scope migrated to the
    harness.

## Out of Scope / Not Done

- Production hardening of `AppGraph.close()`, state-holder production lifecycle changes and any
  `DatabaseFactory` change are explicitly deferred outside E1-12.

## Files Changed

- `AGENTS.md`.
- `README.md`.
- `docs/BACKLOG.md`.
- `docs/PROJECT_LOG.md`.
- `docs/handoff-E1-12.md` (new; live continuity record).
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/AppGraphCloseTest.kt`.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/AppGraphContractTest.kt`.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/AppGraphTestHarness.kt` (new; reusable child-
  scope and ordered-teardown helper).
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/AppGraphTestHarnessTest.kt` (new; deterministic
  teardown-order regression test).
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/FuelEntryStateHolderTest.kt`.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/RecordingDatabaseFactory.kt` (new; shared
  recording test double for graph-close assertions).
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/VehicleFormStateHolderTest.kt`.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/VehicleListStateHolderTest.kt`.

## Decisions Made

- The owner's `NETWORK` phase label is treated as `RED`; no network phase exists for this test-only
  story. This interpretation changes no technical contract or decision.
- The owner explicitly requested one push after the RED, GREEN and REFACTOR commits. This
  story-specific instruction replaces the default push-after-each-phase cadence while preserving
  the required commit order.
- No technical decision was introduced. The helper and test migrations implement the solution
  already fixed by the E1-12 acceptance criteria while preserving D-89 unchanged.

## Verification Run

Historical RED/GREEN/REFACTOR evidence (first implementation round, pre-review; retained for the
TDD record and superseded by the final post-review verification below):

- RED: `./gradlew :shared:testAndroidHostTest --tests
  "com.ruizurraca.carapp.AppGraphTestHarnessTest.closeCancelsCollectorsBeforeClosingTheGraph"
  --rerun-tasks` — expected BUILD FAILED; the test compiled and executed, expecting
  `[collectors-cancelled, graph-closed]` but observing `[graph-closed]`.
- GREEN: `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test --rerun-tasks` — BUILD
  SUCCESSFUL; 30 tests passed on each target, including 10 `FuelEntryStateHolderTest` cases and the
  deterministic harness regression test.
- GREEN: `./gradlew :shared:ktlintCheck :shared:detekt` — BUILD SUCCESSFUL.
- REFACTOR stability: `for run_index in {1..10}; do ./gradlew
  :shared:iosSimulatorArm64Test --rerun-tasks --quiet || exit 1; done` — 10/10 consecutive BUILD
  SUCCESSFUL runs on the local Apple-silicon host; no signal or assertion failure.
- REFACTOR full repository verification: `./gradlew ktlintCheck detekt architectureCheck
  contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug
  :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test -x
  :integration:firebase-auth:iosSimulatorArm64Test -x
  :integration:firebase-firestore:iosSimulatorArm64Test -x
  :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test` — BUILD
  SUCCESSFUL in 6s with 627 actionable tasks (41 executed, 586 up-to-date); `contractCheck`
  output inspected: every assertion PASS, 111 decisions, 111 ADRs, no unresolved decisions and no
  `PENDING` assertions.
- Historical CI: `https://github.com/davidru85/carApp/actions/runs/33625103198` — attempt 1 passed
  all 10 required jobs; the macOS `shared-tests` job passed in the original run and two isolated
  reruns (3/3 successful executions, no SIGSEGV).

Final post-review verification (second review round, current HEAD):

- RED (orphaned child job): `./gradlew :shared:testAndroidHostTest --tests
  "com.ruizurraca.carapp.AppGraphTestHarnessTest.constructorThrowsWhenParentScopeHasNoTestCoroutineScheduler"
  --rerun-tasks` — expected BUILD FAILED before the harness fix: the test observed
  `[SupervisorJobImpl{Active}]` attached to the retained parent `Job` instead of `[]`, proving the
  constructor orphaned `scopeJob` when scheduler validation failed.
- GREEN: `./gradlew :shared:testAndroidHostTest --tests
  "com.ruizurraca.carapp.AppGraphTestHarnessTest" --rerun-tasks` — BUILD SUCCESSFUL; 3/3 harness
  tests passed after moving scheduler validation before `scopeJob` creation.
- REFACTOR (exception-safe teardown): `./gradlew :shared:testAndroidHostTest
  :shared:iosSimulatorArm64Test :shared:ktlintCheck :shared:detekt --rerun-tasks` — BUILD
  SUCCESSFUL in 16s with all 143 actionable tasks executed; 32 tests, 0 failures, 0 skipped on
  each target (generated-report counts).
- Full repository verification: the complete non-instrumented command from `AGENTS.md` — BUILD
  SUCCESSFUL in 1s with 627 actionable tasks (38 executed, 589 up-to-date).
- `./gradlew contractCheck` output inspected: no unresolved decisions, no `PENDING` assertions.
- `git diff --check` — clean.
- Source audit repeated: no `backgroundScope.launch` state-holder collector and no state-holder
  factory call receiving `backgroundScope` under `shared/src/commonTest`; `backgroundScope` appears
  only as the harness `parentScope` argument.

## Review Findings Resolution (PR #49)

### Blocking Finding: Collector Dispatcher Regression
- Root cause: `AppGraphTestHarness.collect()` launched directly into `scope`, which inherited
  `StandardTestDispatcher` from the enclosing `runTest` scope. Tests migrated to the harness
  previously collected on `UnconfinedTestDispatcher(testScheduler)`. `CoroutineStart.UNDISPATCHED`
  made only the initial flow collection synchronous; subsequent emissions queued on the scheduler.
- Fix: `AppGraphTestHarness` now resolves `TestCoroutineScheduler` from
  `parentScope.coroutineContext` via `requireNotNull(...)` (failing loudly with
  `IllegalArgumentException` if missing), instantiates `UnconfinedTestDispatcher(scheduler)` and
  passes `context = collectorDispatcher` to `scope.launch(..., start = CoroutineStart.UNDISPATCHED)`.
  The collector jobs remain children of `scopeJob`, preserving `cancelAndJoin()` guarantees.
- Verification:
  - Step 1 (before fix): temporary assertion placed immediately after `form.confirmSave(...)`
    read `saveCompletionCount` as `0`.
  - Step 2 (after fix): eager collector execution verified; emission observed eagerly as `1`
    (and reinforced by unit test `collectorsRunEagerlyOnUnconfinedTestDispatcher`).
  - Step 3 (cleanup): temporary instrumentation removed; working tree clean.

### Non-blocking Cleanups
- Deduplicated test doubles: collapsed `CloseRecordingDatabaseFactory` in
  `AppGraphTestHarnessTest.kt` and `RecordingDatabaseFactory` in `AppGraphCloseTest.kt` into a single
  shared `RecordingDatabaseFactory.kt` with an optional `onClose: () -> Unit = {}` callback.
- Hardened teardown: wrapped `harness.close()` in a nested `finally` block in
  `AppGraphTestHarnessTest.kt` so the graph is closed even if assertions fail.
- Added explicit unit tests in `AppGraphTestHarnessTest.kt`:
  - `constructorThrowsWhenParentScopeHasNoTestCoroutineScheduler`: proves missing scheduler throws.
  - `collectorsRunEagerlyOnUnconfinedTestDispatcher`: proves emissions are observed eagerly.

### Second Review Round (PR #49, current)

- Finding 1 (orphaned child `Job` on failed construction): confirmed RED by extending
  `constructorThrowsWhenParentScopeHasNoTestCoroutineScheduler` to retain the parent `Job`
  explicitly and assert no children remain attached after the failed constructor; the assertion
  observed `[SupervisorJobImpl{Active}]` against the then-current implementation. Fixed by moving
  the `TestCoroutineScheduler` resolution and validation in `AppGraphTestHarness` before the
  `SupervisorJob` creation, so a missing scheduler fails before any `Job` is constructed. The
  test teardown now cancels the parent `Job` in its `finally` even if an assertion fails. Valid
  harness behavior is unchanged: collectors remain children of `scopeJob`, run eagerly through
  `UnconfinedTestDispatcher` and are cancelled and joined before `graph.close()`.
- Finding 2 (exception-safe teardown): every teardown in `AppGraphTestHarnessTest.kt` now uses
  nested `try/finally` blocks so `owningFactory.close()` still runs when `harness.close()` or
  `graph.close()` throws, and the obsolete `DatabaseFactory` and `DatabaseHandle` imports were
  removed from `AppGraphCloseTest.kt`.
- Finding 3 (continuity record): this handoff's `In-Progress Checkpoint`, `Files Changed`,
  acceptance evidence and verification sections were refreshed to the final HEAD, with final
  report-derived test counts and historical evidence clearly separated from final post-review
  verification. A correction entry was appended to `docs/PROJECT_LOG.md` rather than rewriting
  the original story entry.
- Finding 4 (PR description): the PR #49 description will be refreshed after the final push to
  agree with this handoff; the human-review gate remains checked and the PR is not merged.

## Contract Impact

- No contract changes planned. E1-12 preserves D-89 and makes test teardown respect its existing
  ownership contract.

## Decision Board Impact

- No decision changes planned.

## Shared-Write Modules Touched

- None. `:core:database` is explicitly out of scope.

## Project Log Entry

- [x] Entry appended

## Risks or Follow-ups

- The production question of making `AppGraph.close()` safe against live external subscribers is
  deferred to a separate explicitly scoped and human-gated story if pursued.

## Human Review Gate

- Applies: E1-12 is explicitly human-review-required in `docs/BACKLOG.md`. The agent MUST NOT merge
  its pull request.
