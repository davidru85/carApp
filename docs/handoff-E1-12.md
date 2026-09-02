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
- Current phase and latest commit: RED `ed698e0`, GREEN `55f075c` and REFACTOR `a7fb619` are
  complete.
- Push and pull-request status: all three TDD phase commits are pushed; PR #49 is open at
  `https://github.com/davidru85/carApp/pull/49` and MUST NOT be merged by the agent.
- Completed since the previous checkpoint: committed GREEN; retained its passing behavior while
  collapsing the duplicate collector-launch overloads into one typed helper with an optional
  collector callback. Earlier GREEN work made `AppGraphTestHarness.close()` cancel and join its
  child scope before closing the graph, moved Fuel Entry collectors and all Kotlin caller-owned
  graph holder scopes under the harness, removed every direct `backgroundScope.launch` state-
  holder collector from `:shared` tests and completed the graph-mounting test audit. Updated the
  repository state, README and backlog to mark E1-12 complete and E1-13 next. Committed REFACTOR,
  pushed the branch and created PR #49.
- Verification evidence and known failures: the focused Android-host RED test compiled, executed
  and failed for the intended missing behavior before GREEN: expected
  `[collectors-cancelled, graph-closed]` but observed `[graph-closed]`. In GREEN, all 30 `:shared`
  tests passed on both Android host and `iosSimulatorArm64`; focused ktlint and detekt passed. No
  direct `backgroundScope.launch` or state-holder factory call with `backgroundScope` remains under
  `shared/src/commonTest`. The historical intermittent signal 11 has not occurred in this GREEN
  run. REFACTOR repeated `:shared:iosSimulatorArm64Test` 10 consecutive times with forced task
  execution on the local Apple-silicon host; all 10 runs passed without a process signal. The
  complete non-instrumented repository command passed with 627 actionable tasks, and
  `contractCheck` reported 111 aligned decisions and ADRs with no unresolved or pending assertions.
- Open decisions or blockers: no technical decision is open. Repeated macOS CI evidence and the
  mandatory human review can begin only after the requested push and pull-request creation.
  Production hardening of `AppGraph.close()` remains explicitly deferred outside E1-12 because it
  would change D-89 and touch gated `core/database/**`.
- Exact next step: push this continuity checkpoint, monitor PR #49, repeat the macOS shared-test CI
  run to complete external acceptance evidence, record the results and leave the PR for owner
  review.

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
- `:shared:iosSimulatorArm64Test` passed 10 consecutive forced runs on the local Apple-silicon host;
  repeated CI evidence remains pending pull-request creation.
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
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/AppGraphTestHarness.kt` (new; reusable child-
  scope and ordered-teardown helper).
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/AppGraphTestHarnessTest.kt` (new; deterministic
  teardown-order regression test).
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/AppGraphContractTest.kt`.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/FuelEntryStateHolderTest.kt`.
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
  SUCCESSFUL in 4s with 627 actionable tasks; `contractCheck` output inspected: every assertion
  PASS, 111 decisions, 111 ADRs, no unresolved decisions and no `PENDING` assertions.

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
