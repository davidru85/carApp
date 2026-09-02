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
- Current phase and latest commit: RED complete and awaiting its commit; latest commit is the base
  `64c91d6`.
- Push and pull-request status: the remote branch exists at the same base commit; no E1-12 work has
  been pushed and no pull request has been created.
- Completed since the previous checkpoint: completed intake and the initial graph-mounting audit;
  added `AppGraphTestHarness` as deliberately incomplete RED scaffolding and a focused test that
  observes collector cancellation and database-handle close order.
- Verification evidence and known failures: the focused Android-host RED test compiled, executed
  and failed for the intended missing behavior: expected `[collectors-cancelled, graph-closed]` but
  observed `[graph-closed]`. Issue #42 records the intermittent Kotlin/Native signal 11; E1-11
  records a second graph-close failure signature where the AndroidX driver reported one checked-
  out reader during teardown. GitHub issue #42 is currently closed even though the backlog
  implementation story remains open.
- Open decisions or blockers: none. Production hardening of `AppGraph.close()` remains explicitly
  deferred outside E1-12 because it would change D-89 and touch gated `core/database/**`.
- Exact next step: create the RED commit, then implement cancellation-before-close and migrate all
  audited graph-mounting tests to the helper in GREEN.

## Scope Completed

- In progress.

## Acceptance Evidence

- Pending.

## Out of Scope / Not Done

- Production hardening of `AppGraph.close()`, state-holder production lifecycle changes and any
  `DatabaseFactory` change are explicitly deferred outside E1-12.

## Files Changed

- `docs/handoff-E1-12.md` (new; live continuity record).
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/AppGraphTestHarness.kt` (new; deliberately
  incomplete RED scaffolding).
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/AppGraphTestHarnessTest.kt` (new; deterministic
  teardown-order regression test).

## Decisions Made

- The owner's `NETWORK` phase label is treated as `RED`; no network phase exists for this test-only
  story. This interpretation changes no technical contract or decision.
- The owner explicitly requested one push after the RED, GREEN and REFACTOR commits. This
  story-specific instruction replaces the default push-after-each-phase cadence while preserving
  the required commit order.

## Verification Run

- RED: `./gradlew :shared:testAndroidHostTest --tests
  "com.ruizurraca.carapp.AppGraphTestHarnessTest.closeCancelsCollectorsBeforeClosingTheGraph"
  --rerun-tasks` — expected BUILD FAILED; the test compiled and executed, expecting
  `[collectors-cancelled, graph-closed]` but observing `[graph-closed]`.

## Contract Impact

- No contract changes planned. E1-12 preserves D-89 and makes test teardown respect its existing
  ownership contract.

## Decision Board Impact

- No decision changes planned.

## Shared-Write Modules Touched

- None. `:core:database` is explicitly out of scope.

## Project Log Entry

- [ ] Entry appended

## Risks or Follow-ups

- The production question of making `AppGraph.close()` safe against live external subscribers is
  deferred to a separate explicitly scoped and human-gated story if pursued.

## Human Review Gate

- Applies: E1-12 is explicitly human-review-required in `docs/BACKLOG.md`. The agent MUST NOT merge
  its pull request.
