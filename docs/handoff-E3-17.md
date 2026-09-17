# Handoff - E3-17 Make `AppGraph.close()` Safe Against an In-Flight Sync Cycle

## Story

`E3-17`. `DefaultAppGraph.close()` cancelled `graphScope` and then closed the `DatabaseHandle`
immediately. `cancel()` does not join, and `E3-03` put long-running detached sync cycles on that same
scope, so the driver could be released while a cycle was still reading or writing through it. Both
production close paths — `MainActivity.onCleared()` and `SwiftAppGraph.close()` — could hit it.

## Ready Check

- Backlog story: `E3-17 - Make AppGraph.close() Safe Against an In-Flight Sync Cycle`.
- Acceptance criteria satisfied: the accepted option's obligations, the `D-89` ownership rules, both
  host close paths, the preserved `E1-12` mitigation, and the recorded decision.
- Dependency and decision rows checked: `D-172` (raised from `Proposed` to `Accepted` here, mechanism
  option D with a 5-second grace), `E1-12` (context), `E3-03` (the change that introduced the hazard).
- Normative sections: `docs/CONTRACTS.md §6`, `§9.1`, `§11`, `§18`, `§20.3.2`, `§20.7`; `docs/adr/0173`;
  `docs/adr/0090` (`D-89`); `docs/SECURITY.md`.
- Expected verification: both shared suites, `:core:sync` suites, the quality and contract gates, and
  the complete non-instrumented command.
- Human review gates: **this story is gated** (production close-path change on the `E3-03` review
  finding) and MUST NOT merge on agent judgement.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES); every artifact is in
  technical English.

## In-Progress Checkpoint

- Date: 2026-09-17.
- Branch and base: `story/E3-17-appgraph-close-safety`, stacked on `story/E3-03-core-sync-engine`
  (`b517dba`), because the hazard exists only once `E3-03` puts cycles on `graphScope`.
- Current phase and latest commit: complete at `069f36d`, which merges the `E3-03` tip `714aa5a`
  into this branch so that pull request #70 stays a superset of pull request #69. Pushed; pull request
  [#70](https://github.com/davidru85/carApp/pull/70) is open against `main` and awaiting the owner's
  gated review.
- Reconciliation since the previous checkpoint: `AGENTS.md` and `docs/BACKLOG.md` no longer describe
  `E2-05`, `E3-14`, `E1-17` or `E3-02` as awaiting review, and an "Outstanding Owner Decisions"
  section in `docs/BACKLOG.md` lists `D-149` / `E3-15`, `D-150` / `E3-16` and `D-173` / `E3-18`
  together with the statement that none of them blocks this pull request. `docs/BACKLOG.md` also
  called `D-173` `Pending` while the board and its four mirrors say `Proposed`, and its `E2-07` and
  `E2-01` rows still read as unmerged although pull requests #61 and #52 had merged; all three were
  corrected.
- Completed: `D-172` accepted as option D with a 5-second grace; `SyncController.shutdown()` added and
  implemented; `AppGraph.close()` restructured; RED and GREEN for the close path; the residual window
  recorded in `docs/SECURITY.md`.
- Verification evidence: see **Verification Run**.
- Known failures: none on this story's own checks. The `shared-tests` step stall described under
  **Verification Run** is a pre-existing CI-environment failure that also hits `E3-03` and
  documentation-only commits; it is not a failure of this story's behaviour, and the tip has been
  green on it.
- Open decisions or blockers: the owner review gate. Nothing else blocks.
- Exact next step: the owner reviews pull request #70. Nothing else is outstanding.

## Scope Completed

- `SyncController.shutdown()`: refuses every later trigger and completes every in-flight `sync()`
  awaiter with `PersistenceError.DatabaseUnavailable`, idempotently and without waiting for a cycle.
- `AppGraph.close()`: calls `shutdown()`, closes the auth client, cancels `graphScope`, and releases
  the `DatabaseHandle` from a single bounded waiter that joins the cancelled scope or gives up at the
  5-second deadline.
- `PersistenceError.DatabaseUnavailable` is reused as the closed outcome. No new error leaf was added,
  because `§6` gives that leaf the exact meaning required and a new one would need a gate of its own.
- Tests: `AppGraphCloseSafetyTest` (four cases), `DefaultSyncControllerShutdownTest` (three cases),
  and the two existing close-order tests adapted to await the ordered release instead of assuming it
  is inline.

## Acceptance Evidence

- **The handle is not released while a non-cancellable cycle is still running.**
  `AppGraphCloseSafetyTest.theHandleIsNotReleasedWhileANonCancellableCycleIsStillRunning` and
  `theSwiftClosePathIsSafeToo` park a cycle in a `NonCancellable` remote call, close the graph, and
  assert the recorded order is exactly `[cycle-finished, handle-closed]`. RED before the fix:
  `events=[handle-closed]`.
- **Every in-flight `sync()` awaiter returns.**
  `AppGraphCloseSafetyTest.anInFlightSyncAwaiterReturnsInsteadOfHangingWhenTheGraphCloses` asserts the
  caller receives `Err(PersistenceError.DatabaseUnavailable)` rather than suspending. RED before the
  fix: "Timed out after 5s waiting for the sync() caller to return".
  `DefaultSyncControllerShutdownTest.shutdownCompletesAnInFlightSyncAwaiterWithAClosedOutcome` is the
  same contract at the controller level.
- **Later triggers are refused.** `DefaultSyncControllerShutdownTest.shutdownRefusesEveryLaterTrigger`
  proves no cycle reaches the remote after shutdown, and `shutdownIsIdempotent` proves a second call
  is harmless.
- **The deadline really fires.** `theHandleIsStillReleasedWhenACycleNeverObservesCancellation` asserts
  the release happens at roughly the 5-second window while the cycle is still parked. Verified both
  ways: measuring 5 011 ms with the bound, and failing when the bound is removed.
- **The `E1-12` mitigation survives.** `AppGraphTestHarnessTest.closeCancelsCollectorsBeforeClosingTheGraph`
  still asserts collectors are cancelled before the handle is released.
- **`D-89` is intact.** `AppGraphCloseTest` idempotency cases pass, and the handle is released by
  exactly one path.

## Out of Scope / Not Done

- **A cycle parked inside a local SQLite statement** is not covered by a dedicated test. The ordering
  guarantee covers it — nothing reaches the driver before the scope finishes — but holding a real
  SQLite call open requires either a production seam or a driver proxy, and both were rejected as
  scope creep for this story. This is the one acceptance criterion that is satisfied by construction
  rather than by a dedicated fixture, and it is stated here rather than implied.
- **Bounding `scheduleAdoptionRetry` in production** is not done here. It is `§9` behaviour; `D-177`
  bounds its observation in tests only.
- `docs/BACKLOG.md` `E3-19`, `E3-20` and the other Phase 3 stories are untouched.

## Files Changed

- `core/sync/src/commonMain/.../SyncContracts.kt` — `shutdown()` on the interface, with its contract.
- `core/sync/src/commonMain/.../SyncEngine.kt` — shutdown state, trigger refusal, awaiter completion.
- `shared/src/commonMain/.../AppGraph.kt` — the close path and the bounded release waiter.
- `shared/src/commonTest/.../AppGraphCloseSafetyTest.kt` — new; the four D-172 cases.
- `core/sync/src/commonTest/.../DefaultSyncControllerTest.kt` — the shutdown test class.
- `shared/src/commonTest/.../AppGraphCloseTest.kt`, `AppGraphTestHarnessTest.kt` — await the ordered
  release.
- `docs/CONTRACTS.md §20.7`, `docs/adr/0173-*.md`, `docs/adr/README.md`, `docs/DECISION_BOARD.md`,
  `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/SECURITY.md`, `docs/BACKLOG.md`,
  `docs/PROJECT_LOG.md`.

## Decisions Made

- `D-172` accepted as **option D** with a **5-second** grace. Both were the owner's explicit choice on
  2026-09-17. Option D is recorded in the ADR alongside A, B and C, with the reason option B was not
  taken: it makes the release asynchronous and breaks the synchronous close observation that
  `AppGraphCloseTest` pins.
- `PersistenceError.DatabaseUnavailable` is the closed outcome for an abandoned `sync()`. It was chosen
  over a new `SyncError` leaf because `§6` already gives it this meaning and a new leaf would widen the
  error taxonomy.
- The release is deferred rather than inline whenever graph work is live. That is inherent to option D
  and it changed two existing tests from asserting synchronous release to asserting ordered release.
  Their property (the order) is preserved; only the timing assumption changed.
- Rule 0 held throughout: chat replies were in Spanish, every artifact is in technical English.

## Verification Run

- RED, before the fix: all three original `AppGraphCloseSafetyTest` cases failed with
  `events=[handle-closed]` and with the hung awaiter, which is the defect itself.
- GREEN: `AppGraphCloseSafetyTest` 4/4, `DefaultSyncControllerShutdownTest` 3/3,
  `AppGraphCloseTest` and `AppGraphTestHarnessTest` pass.
- `:shared:testAndroidHostTest`, `:shared:iosSimulatorArm64Test` and `:core:sync` suites pass on both
  targets.
- `ktlintCheck detekt architectureCheck contractCheck koverVerify :build-logic:convention:test` pass;
  `contractCheck` reports 178 decisions, 178 ADRs and zero `PENDING`.
- The complete non-instrumented command of `AGENTS.md` passes.
- CI `35201565587` on `7ebd3e6` is **fully green**: all ten required checks pass, including
  `shared-tests` and `ios-simulator-build`. Pull request #70 reports `CLEAN`.
- The documentation reconciliation commit `0e5c0c1` changed only `AGENTS.md`, `docs/BACKLOG.md`,
  `docs/PROJECT_LOG.md` and `docs/handoff-E3-03.md`. `contractCheck` reports the same 178 decisions,
  178 ADRs and zero `PENDING`; `ktlintCheck` passes.
- CI on the last tip that is fully green, `9d4ba49`: run `35226021529` passes all ten required
  checks after one `shared-tests` re-run.
- **The two commits after it are documentation-only and their runs have not gone green once.** They
  are `5d185cc` and `804157b`, and `git diff --stat 9d4ba49 804157b` touches no `.kt`, `.swift`,
  `.sq`, `.kts` or `.yml` file, so the product code under test is byte-identical to `9d4ba49`. Runs
  `35229329896` and its re-run each had `shared-tests` killed at a step's 10-minute limit - once the
  Android/KMP host step, once the Kotlin/Native step - with no failing test named in either. The
  step takes 3 to 5 minutes when it passes, on this branch and on `main` alike.
- **This stall is not attributable to this story.** It hit the same job on `E3-03`'s tip `0f88552`,
  which does not contain this story's `AppGraph.close()` change at all, and on documentation-only
  commits of both branches. Local runs pass every time, including a `--rerun-tasks` sweep of both
  targets on this tip (`BUILD SUCCESSFUL in 44s`, 280 tasks executed) and 20 consecutive runs of
  `:wiring:firebase:testAndroidHostTest` under `--max-workers=3` at about 5 seconds each. `D-175`
  already records an open, unbounded `shared-tests` stall mechanism, and `docs/BACKLOG.md` already
  states that `shared-tests` can go red without a regression until `E1-14` and `E1-17` are fixed.
- **Consequence for this handoff, stated rather than hidden:** the acceptance evidence for this story
  is the green run on `9d4ba49` plus the local sweeps, not a green run on the final documentation
  commit. The pull request is not mergeable on this evidence until a run on its actual tip is green.
- One earlier `shared-tests` failure on `ecbaccc` was an assertion, not a stall: the Android/KMP host
  step was killed at its 10-minute limit with the last task reported as
  `:wiring:firebase:testAndroidHostTest`, the module whose test closes a real `AppGraph`. The same
  product code had passed `shared-tests` on `ef3dbef`, on `d5b3706` and on `7ebd3e6`, so that one is
  also recorded as intermittent rather than as a close-path defect.
- `ios-simulator-build` failed once on run `35212737926` while `E3-03`'s run `35212706941` was green
  on the same product content: `OnboardingFlowUITests.testFirstRunVehicleFormResistsInteractiveDismissal`
  reported "Guest session did not reach the vehicle list before the timeout within the 180.000-second
  absolute cap". This is the known `E1-17` iOS UI flake, not a behaviour change of this story, which
  touches `AppGraph.close()` and the sync controller and no onboarding UI code. The failed job was
  re-run.
- The `E3-03` base branch is also green: CI `35194821814` on `b517dba` passes all ten after one
  re-run of `ios-simulator-build`, which failed once in `VehicleAndFuelFlowUITests` on a keyboard-focus
  step. That is the known `E1-17` UI flake and is unrelated to this story; it passed on re-run.

## Contract Impact

- `docs/CONTRACTS.md §20.7` now declares `SyncController.shutdown()` and states its obligations. No
  existing rule changed; the interface gained the entry point the close path needs.
- `docs/SECURITY.md` gained the accepted residual window of `D-172`.

## Decision Board Impact

- `D-172`: `Proposed` → `Accepted`, mechanism option D, grace 5 seconds. ADR-0173 status updated, the
  option added to its table, and removed from "Decisions Awaiting Owner Confirmation". The four mirror
  rows (`DECISION_BOARD`, `SPECIFICATION §12`, `TECHNICAL_PLAN §2`, `adr/README`) are consistent.

## Shared-Write Modules Touched

- `core/sync/**` and `shared/**`. `docs/CONTRIBUTING.md` names `:core:database` as the shared-write
  module, and it is **not** modified by this story, so the single-writer rule is not engaged.

## Project Log Entry

Appended: "E3-17 and D-172: `AppGraph.close()` no longer races an in-flight sync cycle".

## Risks or Follow-ups

- The residual window is real and recorded in `docs/SECURITY.md`: work that ignores cancellation can
  keep the driver alive for up to 5 seconds after `close()` returns. The reverse — releasing the driver
  underneath live work — is what this story removes.
- The close path now releases the handle from a coroutine on the injected IO dispatcher. A caller that
  needs to observe the release synchronously must await it, as the adapted tests do.
- `D-177` bounds `advanceUntilIdle()` usage in tests so this story's parked-cycle fixtures cannot make
  the suite non-terminating.

## Human Review Gate

**Required.** This is a gated story: it changes a production close path found during the `E3-03`
review, on `:core:sync` and `:shared`. It MUST NOT be merged on agent judgement.
