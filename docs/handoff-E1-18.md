# Agent Handoff — E1-18 JVM Test Deadlock in `DatabaseHandle.close()` on the Test Scheduler Thread

## Story

`E1-18 - JVM Test Deadlock in DatabaseHandle.close() on the Test Scheduler Thread - S`
(`docs/BACKLOG.md`, follow-ups outside the phase milestones).

## Ready Check

- Backlog story: `E1-18 - JVM Test Deadlock in DatabaseHandle.close() on the Test Scheduler Thread - S`.
- Acceptance criteria reviewed: the four criteria of the backlog entry - (1) the deadlock is removed,
  not made rarer; (2) the fix is proved by repetition of the exact `provider-decoupling` Android-host
  command and of `:shared:testAndroidHostTest`; (3) `GraphTestDependenciesTest`'s scheduling contract
  is preserved or superseded by an explicit decision; (4) the ten required check names and the
  step-ceiling assertion are unchanged, or changed under their own decision.
- Dependencies checked: none. The story was diagnosed inside `E3-12` and delivered inside `E3-12`'s
  pull request #73 by owner decision on 2026-09-24.
- Decisions checked: `D-189` (temporary step-limit raise, `Superseded`) and `D-190` (the fix), both
  selected by the owner on 2026-09-24. No `Proposed` or `Pending` decision applies.
- Normative sections governing the work: `docs/SPECIFICATION.md §11` (TDD and its exemptions),
  `docs/CONTRACTS.md §18` (protected checks and step ceilings), `AGENTS.md` (Definition of Done).
- Expected verification: repeated runs of the two stalling commands, the complete non-instrumented
  `AGENTS.md` command, `contractCheck`, and CI on the pushed head.
- Human review gates identified before work: none of its own; it ships inside `E3-12`'s gated review.
- Rule 0 acknowledged: chat replies are in Spanish (es-ES) and every artifact is in technical English.
- This ready check is recorded retroactively; see Decisions Made.

## In-Progress Checkpoint

Update this section at every material state change and before yielding unfinished work (`D-105`).

- Date: 2026-09-24
- Branch and base: `story/E3-12-cross-device-recovery-proof`, based on `main` / `origin/main` at
  `65e7056`.
- Current phase and latest commit: implementation complete in `3940cbb` and `4d37935`, evidence in
  `95de2f1`; the scheduling guard was strengthened in `E3-12`'s fourth correction round.
- Push and pull-request status: pushed; delivered inside pull request #73, which the owner merges.
- Completed since the previous checkpoint: this record, created from
  `docs/templates/agent-handoff.md`.
- Verification evidence and known failures: see Verification Run. No known failure.
- Open decisions or blockers: none.
- Exact next step: the owner's gated review of pull request #73.

## Scope Completed

- `TestDispatcherProvider` takes a separate `ioDispatcher`, defaulting to the confined dispatcher, and
  `confinedGraphDependencies` passes `Dispatchers.Default`, so the driver's blocking close never runs
  on the test-scheduler thread.
- `DefaultAppGraph.graphScope` runs on `default` instead of `io`, and the vehicle list's local
  observation runs on `default`, so the sync engine's `delay()` timers stay on virtual time.
  Production wires `io` and `default` to `Dispatchers.Default`, so the change is invisible outside
  tests.
- `TrackedDatabaseHandles.close()` queues each release on a worker scope without awaiting it, and the
  three local-owner-adoption `tearDown`s stop closing the handle directly.
- `assertQueuedGraphWork` fails by name when `io` is a `TestDispatcher` bound to the test scheduler,
  under any instance, or `Dispatchers.Unconfined`.
- The `D-189` step-limit raise is reverted; the two stalling steps are back at 10 and 8 minutes.

## Acceptance Evidence

- **Criterion 1 — the deadlock is removed.** Both forms are removed at their cause: the
  `AppGraph.close()` form by taking `io` off the scheduler, and the direct-handle form by the
  non-blocking release in `TrackedDatabaseHandles`. The stacks that pinned them are in the historical
  checkpoint entries of `docs/handoff-E3-12.md` and in ADR-0191.
- **Criterion 2 — proved by repetition.** 12 consecutive runs of the exact `provider-decoupling`
  Android-host command with `--rerun-tasks`: 12 passes, 0 hangs, against 1 hang in 10 before (the
  first measurement, cited by ADR-0191, was 10 of 10). 16 consecutive runs of the exact
  `shared-tests` step command: 0 hangs. CI run `36032908302` on `4d37935`: all ten required checks
  green on attempt 1.
- **Criterion 3 — the scheduling contract is superseded explicitly.** `D-190` supersedes `E1-14`'s
  confinement: `main` and `default` stay confined and `io` is deliberately outside it.
  `GraphTestDependenciesTest` pins the new intent through `assertQueuedGraphWork`.
- **Criterion 4 — check names and step ceilings.** `.github/workflows/ci.yml` is unchanged relative
  to `main`: `D-189` raised the two step limits and `D-190` reverted them inside the same pull
  request.

## Out of Scope / Not Done

- Production dispatchers are unchanged.
- The window-closing re-read in `VehicleListStateHolder`, introduced in the same commit as the
  dispatcher split, is product behaviour owned by `E3-12` / `D-188`; its tests are in `E3-12`'s
  fourth correction round.

## Files Changed

- `core/testing/src/commonMain/kotlin/com/ruizurraca/carapp/core/testing/Fakes.kt` —
  `TestDispatcherProvider.ioDispatcher`.
- `core/testing/src/commonMain/kotlin/com/ruizurraca/carapp/core/testing/GraphDependencyFakes.kt` —
  the non-blocking `TrackedDatabaseHandles.close()`.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/GraphTestDependencies.kt` — the real `io` in
  `confinedGraphDependencies` and the `io` guard in `assertQueuedGraphWork`.
- `shared/src/commonTest/kotlin/com/ruizurraca/carapp/LocalOwnerAdoptionTest.kt`,
  `LocalOwnerAdoptionFailureTest.kt` and `LocalOwnerAdoptionTriggerTest.kt` — `tearDown` no longer
  closes the handle directly.
- `shared/src/commonMain/kotlin/com/ruizurraca/carapp/AppGraph.kt` — `graphScope` on `default`.
- `feature/vehicle/src/commonMain/kotlin/com/ruizurraca/carapp/feature/vehicle/presentation/VehicleStateHolders.kt`
  — the local observation on `default`.
- `docs/adr/0190-raise-the-stalling-ci-step-timeouts-as-a-temporary-measure.md`,
  `docs/adr/0191-remove-the-e1-18-deadlock-by-taking-io-off-the-test-scheduler.md`, the `D-189` and
  `D-190` rows, `docs/BACKLOG.md`, `AGENTS.md`, `docs/PROJECT_LOG.md` and a dated note in
  `docs/handoff-E1-14.md`.

## Decisions Made

Include any `SHOULD` you deviated from, and why.

- `D-189` — temporary raise of the two stalling step limits, selected by the owner on 2026-09-24 and
  superseded the same day. See ADR-0190.
- `D-190` — the fix, selected by the owner on 2026-09-24 as Option B and delivered inside `E3-12`'s
  pull request #73. See ADR-0191.
- **Process deviation, recorded for the owner.** `D-105` requires this record at intake; it was
  created after the implementation, and no separate ready check existed before the code changed. The
  diagnosis, the measurements and both decisions were recorded as they happened in
  `docs/handoff-E3-12.md`.
- The fixture, dispatcher and teardown changes are test infrastructure, driven by measured failures
  (the hang rate and the captured stacks) rather than by a written-first test. The one `commonMain`
  change, `graphScope` on `default`, is behaviour-preserving in production because `io` and `default`
  are both `Dispatchers.Default` there; the whole graph suite covers it.

## Verification Run

Exact commands, and their result.

- `./gradlew -Pcarapp.excludeFirebaseProviders=true :shared:testAndroidHostTest --rerun-tasks`, 12
  consecutive runs — 12 passes, 0 hangs.
- `./gradlew :androidApp:testDebugUnitTest testAndroidHostTest --rerun-tasks`, 16 consecutive runs —
  0 hangs.
- CI run `36032908302` on `4d37935` — all ten required checks green on attempt 1.
- `E3-12` fourth correction round: with `ioDispatcher = StandardTestDispatcher(testScheduler)` in
  `confinedGraphDependencies`, the new `assertQueuedGraphWork` guard fails both
  `GraphTestDependenciesTest` paths with `io must stay off the test scheduler (E1-18)`, where the old
  identity check passed.

## Contract Impact

- `docs/CONTRACTS.md §14` now states D-190's narrow exception: the recovery-sensitive
`VehicleListStateHolder` database observation runs on `dispatchers.default`; other database
flows retain the `dispatchers.io` rule.

## Decision Board Impact

- Added `D-189` with ADR-0190 (`Superseded`) and `D-190` with ADR-0191, with matching rows in
  `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`.

## Shared-Write Modules Touched

`:core:database` may be modified by only one story at a time.

- None.

## Project Log Entry

Appending an entry to `docs/PROJECT_LOG.md` is part of the Definition of Done.

- [x] Entry appended

## Risks or Follow-ups

- `TrackedDatabaseHandles.close()` does not await the release it queues. A release whose writer-lock
  holder is never resumed would park one `Dispatchers.Default` worker for the rest of the test JVM
  instead of hanging the test thread. No such park has been observed. If `shared-tests` or
  `provider-decoupling` stalls again, inspect the worker dump for a `DefaultDispatcher-worker` thread
  parked in `AndroidxDriverConnectionPool.close` first.
- A close that is awaited from the scheduler thread would restore the deadlock, so the release must
  stay non-blocking; the KDoc states that constraint at the call site.
- Giving `io` a real dispatcher has a second, subtler cost: every graph path that offloads work to
  it now takes wall-clock time, so a graph-backed wait that relies on virtual advancement can exceed
  a tight real-time bound on a slow runner. CI surfaced exactly that -
  `VehicleFormStateHolderTest.savePushesTheSnapshotOnlyAfterTheLocalTransactionCommits` timed out
  after 5 s waiting for the push cycle while the same test passed 10 of 10 locally. The shared
  real-time expectation budget was widened to 30 s with that rationale recorded in
  `FlowExpectation.kt`. Any future graph wait that measures a longer real-time window inherits the
  same reasoning.

## Human Review Gate

Applies through `E3-12`: `E1-18` is not a gated story, but it ships inside `E3-12`'s pull request #73,
whose gated review applies, and its decision records live in the gated paths `docs/adr/**` and
`docs/DECISION_BOARD.md`.
