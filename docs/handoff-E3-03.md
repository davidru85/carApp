# Agent Handoff

## Story

`E3-03 - :core:sync Engine`

## Ready Check

- Backlog story: `E3-03 - :core:sync Engine`.
- Acceptance criteria reviewed: all criteria in `docs/BACKLOG.md`, including the 18 backup and
  recovery tests, deterministic simulation, connectivity admission, single-cycle concurrency,
  push/pull ordering, retry and poison behavior, quarantine, aggregate status, adoption retry,
  debug support and state-holder convergence.
- Dependencies checked: `E1-01`, `E1-11`, `E2-06`, `E3-01` and `E3-02` are merged on `main`; PR #68
  merged as `fbc6d64` on 2026-09-13.
- Decisions checked: all decisions needed by E3-03 are accepted. The owner selected D-169 option A
  and D-170 option A on 2026-09-13 before implementation started. D-149 and D-150 remain pending
  only for E3-15 and E3-16.
- Normative sections reviewed: `docs/SPECIFICATION.md` §§2, 8-9, 11-12;
  `docs/CONTRACTS.md` §§3.1, 6-10, 14, 17, 18, 20.0.1, 20.3, 20.7, 20.10;
  `docs/DECISION_BOARD.md`; `docs/TECHNICAL_PLAN.md` §§4, 6, 8-9; ADR-0170; ADR-0171;
  `docs/handoff-E3-02.md`; and the three most recent `docs/PROJECT_LOG.md` entries.
- Expected verification: focused `:core:sync`, `:core:database`, Firebase Firestore and `:shared`
  Android-host tests; matching iOS simulator compilation/tests; `ktlintCheck`, `detekt`,
  `architectureCheck`, `contractCheck`, convention tests, Kover, Android assembly and host tests;
  the complete non-instrumented repository command; Firestore emulator tests if integration
  behavior changes; and the existing protected Android instrumented suite when available.
- Human review gates identified before work: the gated E3-03 story; gated `core/sync/**`,
  `core/database/**`, `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md` and `docs/adr/**` paths; and the
  sync algorithm, state machine, remote-backend boundary, error-taxonomy, logging/privacy and
  Swift-facing API topics. Owner review is mandatory before merge.
- Rule 0 acknowledged: owner conversation is Spanish (es-ES); every repository artifact is
  technical English.

## In-Progress Checkpoint

- Date: 2026-09-15.
- Branch and base: `story/E3-03-core-sync-engine` from `main` at `fbc6d64`.
- Current phase and latest commit: the eleventh owner-review correction round is REFACTOR complete;
  RED `9177c70` and GREEN `65a6297` are committed and pushed. The complete non-instrumented check
  passes on the final worktree. Earlier story RED is `a66c612`; earlier story GREEN is `bfced6b`.
- Push and pull-request status: RED `9177c70` and GREEN `65a6297` are pushed on
  `story/E3-03-core-sync-engine`; the REFACTOR commit and push are the next step. Gated pull request
  #69 remains open against `main`. It is not merged and MUST NOT be merged on agent judgement.
- Completed since the previous checkpoint: committed GREEN with the singleton sync controller,
  SQLDelight persistence, raw-document validation and quarantine, deterministic push/pull order,
  cursor progress guard, retry/poison behavior, automatic adoption retry, aggregate status,
  debug diagnostics, graph ownership and shared state-holder convergence. REFACTOR then formatted
  the implementation, removed direct `AppDatabase` exposure from `:core:sync` by composing a
  database-owned `SyncDatabaseAccess` in `:shared`, removed the premature E3-07 tombstone-purge
  implementation, split complex methods, named validation and backoff constants, corrected
  `Unauthenticated` failures to consume the non-connectivity poison budget, and strengthened tests
  for cold-start ordering, exact state transitions, crash-reporting policy, connectivity recovery,
  reset semantics, debug-only diagnostics and malformed raw Firestore transport.

  > **Correction 2026-09-13 (ninth owner-review round):** the sentence above is wrong. The REFACTOR
  > round did make `Unauthenticated` consume the poison budget, but `docs/CONTRACTS.md §6` is
  > normative and states the opposite ("retry after a valid auth session, `attemptCount` unchanged").
  > The ninth round restored the `§6` rule: `Unauthenticated` no longer increments `attemptCount` and
  > never poisons. The original sentence is kept above as the historical record of what REFACTOR did.
- Quarantine deduplication completed: `SyncPersistence.applyPullPage` now returns newly persisted
  quarantine records; `SyncDatabaseAccess.applyPullPage` detects an existing quarantine row inside
  the pull transaction and returns only the rows it inserted; `SqlDelightSyncPersistence` maps the
  returned keys back to their `QuarantineRecord`s. The malformed-payload controller test repeats the
  overlapping cycle and asserts the document is reported exactly once. A new
  `SqlDelightSyncPersistenceTest` covers the persistence mapper, the production controller factory
  and the once-only overlap delivery, restoring `:core:sync` coverage above the `D-18` threshold.
- Verification evidence: the intentional RED command ran 21 tests with the expected 20 failures.
  The focused REFACTOR command (`ktlintFormat`, `:core:sync`, `:core:database`,
  `:integration:firebase-firestore` and `:shared` Android-host tests, `:core:sync:detekt`,
  `:core:database:detekt`, `architectureCheck`) passes. `:core:sync` Kover reports 96.01% line
  coverage, up from 72.65%, against the 80% threshold. The complete non-instrumented command passes
  638 tasks; `contractCheck` reports 19 `[PASS]` assertions and zero `PENDING`. The Android
  instrumented suite runs 17 tests on the D-84 API 36 emulator with zero failures.
- Known failures: none. The four intended RED failures are resolved; quality, architecture,
  contracts, coverage and the complete non-instrumented command pass.
- Open decisions or blockers: none.
- Exact next step: commit and push REFACTOR, then leave pull request #69 for the mandatory owner
  review and required checks without merging it.

## Scope Completed

- GREEN implements E3-03's sync engine and integration path: serialized cycles with one pending
  follow-up, offline/local-owner admission, cold-start pull-first selection, dependency-ordered
  push, idempotent acknowledgement, server-timestamp LWW pull, one-cycle overlap, deterministic
  pagination, retry/backoff/poison rules, quarantine, adoption retry and aggregate status.
- The app graph owns one controller. Vehicle and Fuel Entry holders observe its single status flow,
  and the Swift facade exposes a `SyncStateHolder` rather than the controller.
- Debug builds expose redacted outbox, cursor, quarantine and row-state lines on Android and iOS;
  release controllers return no diagnostic lines.
- D-170 is integrated as raw JSON transport with stable provider ordering metadata; product
  validation and quarantine classification remain in `:core:sync`.
- REFACTOR is complete: the implementation, its tests, the repository-wide verification, the iOS
  framework link and the host-app build all pass on the committed worktree.

## Acceptance Evidence

- `DefaultSyncControllerTest` covers the 18 canonical backup/recovery scenarios plus cursor
  fail-closed behavior, concurrent-trigger coalescing, initial-cursor materialization, local retry
  failure propagation, authentication poison budgeting, crash-reporting policy, debug gating and
  fixed-seed simulation.
- `SyncDatabaseAccessTest` covers due selection and acknowledgement, failure correlation, full
  retry reset, connectivity recovery without attempt reset, atomic quarantine/cursor persistence,
  redacted debug projections and remote Vehicle/Fuel Entry application with derived odometer
  recomputation.
- `FirebaseRemoteSyncSourceTest` covers raw Vehicle and Fuel Entry documents and now includes a
  malformed product-field result that must remain a successful raw pull item.
- `AppGraphContractTest` proves Vehicle and Fuel Entry holders converge on the singleton controller
  status flow.
- `SqlDelightSyncPersistenceTest` covers the production persistence mapper, the production
  controller factory and the once-only overlap quarantine delivery, which restores the `:core:sync`
  coverage threshold.
- Evidence is final for the committed REFACTOR: every required non-instrumented check, the iOS
  framework link and the host-app build pass.

## Out of Scope / Not Done

- R1 is recorded only: `E3-17` / `D-172` / ADR-0173 own making `AppGraph.close()` safe against an
  in-flight sync cycle. `AppGraph.close()`, `DatabaseFactory`, `DatabaseHandle` and `core/database/**`
  were deliberately not changed for it.
- `E3-03` **did** wire the post-write trigger: `VehicleSliceRuntime.createVehicle`/`updateVehicle`
  call `syncController.requestSync(SyncTrigger.PostWriteDebounce)` after a successful local write, and
  `VehicleSliceRuntime.refresh()` delegates to `sync(PullToRefresh)`. What remains owned by `E3-04` is
  the platform background scheduling and the *enforcement* of `docs/CONTRACTS.md §9.8`:
  `SYNC_POST_WRITE_DEBOUNCE_MS` (2 s) and `SYNC_MIN_AUTOMATIC_INTERVAL_MS` (30 s) are currently
  declarative only — no code consumes them and `requestSync` starts a cycle immediately.
- Local tombstone purge remains owned by E3-07. A premature GREEN implementation was deliberately
  removed in REFACTOR and MUST NOT be restored in E3-03.
- The protected Android instrumented suite was run locally against the D-84 API 36 emulator as
  supporting evidence; CI remains the authoritative run.

## Files Changed

- RED and GREEN span `:core:common`, `:core:database`, `:core:sync`, the Firebase Firestore
  integration, the shared graph/state holders, Vehicle and Fuel presentation status wiring,
  Android/iOS debug surfaces and their localized resources, plus the D-169/D-170 normative records.
- REFACTOR changes: sync and database production/tests, the new `SqlDelightSyncPersistenceTest`,
  `core/sync/build.gradle.kts` test dependencies, the Firestore integration test,
  `shared/AppGraph.kt`, three shared tests formatted by ktlint, this handoff, the backlog status and
  the project log.

## Decisions Made

- D-169: owner selected option A, accepting the millisecond cursor and a fail-closed bound for an
  oversized same-millisecond timestamp cluster.
- D-170: owner selected option A, returning raw per-document results so `:core:sync` owns product
  validation and quarantine classification.
- D-171 (second review round): owner selected option B, adding the awaitable
  `SyncController.sync(reason)` so a user-initiated refresh observes the cycle outcome.
- D-172 (second review round): raised as `Proposed` by the E3-03 review, recommending an awaitable
  drain plus a bounded join before the `DatabaseHandle` closes. `E3-17` owns the fix; no production
  change was made here.
- No new decision arose in the eleventh review round. D-169 was reconciled with the already-accepted
  D-174: D-169 governs the persisted millisecond anchor and fail-closed progress invariant, while
  D-174 governs the provider-precision in-cycle boundary.
- Re-quarantine preserves the original `createdAt`. This enforces the existing `§9.5` field meaning
  without a schema rename or migration; later deliveries update only the diagnostic fields.
- `Instant.toEpochMicroseconds()` remains public but hidden from Objective-C under D-174. Tests are
  its current callers, and cross-module integration tests need the canonical forward conversion to
  construct and inspect provider-precision boundaries without duplicating arithmetic or exposing
  provider timestamp types.

## Verification Run

- RED: `./gradlew :core:sync:testAndroidHostTest --tests '*DefaultSyncControllerTest'` — 21 tests,
  20 expected failures against the intentional stubs.
- GREEN: focused sync/database/Firestore/shared tests — pass.
- GREEN: `./gradlew testAndroidHostTest :androidApp:testDebugUnitTest
  :androidApp:assembleDebug` — pass, 279 tasks.
- REFACTOR quality checkpoint: `./gradlew ktlintFormat :core:sync:detekt
  :core:database:detekt architectureCheck` — pass after fixing all reported findings.
- REFACTOR focused checkpoint: `./gradlew :core:sync:testAndroidHostTest
  :core:database:testAndroidHostTest :integration:firebase-firestore:testAndroidHostTest
  :shared:testAndroidHostTest` — pass before the latest quarantine-return API edits.
- REFACTOR final: focused command pass; `:core:sync` Kover 96.01% line coverage; complete
  non-instrumented command pass with 638 tasks; `contractCheck` 19 `[PASS]`, zero `PENDING`;
  `:composition:ios:linkDebugFrameworkIosSimulatorArm64` pass (the framework is produced by
  `:composition:ios`, not `:shared`); host-app `xcodebuild` `** BUILD SUCCEEDED **`; protected
  Android instrumented suite 17 tests, zero failures on the API 36 emulator.
- Provider decoupling re-run locally after the golden update: `./gradlew
  -Pcarapp.excludeFirebaseProviders=true testAndroidHostTest iosSimulatorArm64Test` pass, 234 tasks.
  `contractCheck --rerun-tasks` reports assertion 7 `PASS` and zero `PENDING`, and the regenerated
  header is byte-identical to the golden.

## Contract Impact

- Updated `docs/CONTRACTS.md` §§9.4, 9.5 and 20.7 for D-169 and D-170.

## Decision Board Impact

- Updated `docs/DECISION_BOARD.md` D-169 and D-170 and ADR-0170 / ADR-0171 from Proposed to Accepted.

## Shared-Write Modules Touched

- `:core:database`.

## Project Log Entry

- [x] Entry appended on 2026-09-13.

## Risks or Follow-ups

- The quarantine deduplication now compiles and is exercised end to end; the once-only overlap
  delivery is asserted in `DefaultSyncControllerTest` and `SqlDelightSyncPersistenceTest`.
- `contractCheck` was inspected and reports zero `PENDING` assertions.
- No additional owner decision arose during E3-03. D-169 option A and D-170 option A are Accepted;
  D-149 and D-150 remain unrelated blockers for E3-15/E3-16 only.
- The first CI run failed only `objc-header-golden-check`: the new Swift-facing
  `SyncStateHolder.debugLines` and `refreshDebug` members of the debug diagnostics surface changed
  the generated header. The golden was regenerated, `docs/CONTRACTS.md §20.10` was corrected to
  document those members, and both `contractCheck` and the header diff now pass.
- The second CI run exposed two test-level races introduced by the E3-03 post-write trigger, not by
  product code. `VehicleFormStateHolderTest` asserted a locally `PENDING` row while the detached
  sync cycle could fail it against the default offline fake; those two tests now inject
  `FakeConnectivityObserver(initiallyOnline = false)`, which matches their stated local-persistence
  intent. `VehicleListStateHolderTest` closed the graph while the `refresh()`-initiated cycle was
  still calling SQLite, producing an intermittent Kotlin/Native `SIGSEGV` (30% locally; zero on
  `fbc6d64`); it now waits for the cycle to settle before teardown, the same mitigation E1-12 used
  for issue #42. The `:shared` Android-host suite passed 25/25 and the full iOS simulator suite
  25/25 after the change.
- The pull request awaits the mandatory owner review and the ten required checks; E3-03 is
  implemented, not complete, until it merges.

## Second Owner-Review Correction Round (2026-09-13)

Seven findings on pull request #69, with D-169 option A and D-170 option A already held as settled.

- **R1 — E3-03 invalidates the E1-12 deferral (recorded, not fixed).** `DefaultAppGraph.close()`
  calls `graphScope.cancel()` and then `databaseHandle.close()`; `cancel()` does not join, so a
  coroutine suspended inside an asynchronous SQLite call is not finished when the driver closes.
  Before E3-03 `graphScope` hosted only short bootstrap jobs, which is exactly why `E1-12`
  (`docs/BACKLOG.md`, GitHub issue #42) deferred the production fix as "a test-infrastructure
  defect, not a production defect". E3-03 puts long-running detached sync cycles on that scope,
  started by `VehicleSliceRuntime.createVehicle`/`updateVehicle` (`PostWriteDebounce`), by
  `VehicleSliceRuntime.refresh` (`PullToRefresh`) and by `scheduleAdoptionRetry`, so both
  `MainActivity.onCleared()` and `SwiftAppGraph.close()` can now run with a cycle in flight.
  `SyncStateHolder.close()` does not mitigate it: it cancels the holder's collectors, not the
  controller's cycle on `graphScope`. This is a **reachable hazard, not an observed production
  crash**, and it is **not fixed here**. Added `E3-17` (`docs/BACKLOG.md`, Human review required)
  and `D-172` / ADR-0173 (`Proposed`, recommendation option B, Needed by `E3-17`). No change was made
  to `AppGraph.close()`, `DatabaseFactory`, `DatabaseHandle` or `core/database/**` for this item.
- **R2 — Owner acceptance of D-169 and D-170 recorded.** Added a dedicated `decision` entry to
  `docs/PROJECT_LOG.md` dated 2026-09-13, naming ADR-0170 and ADR-0171 and the four mirrored
  documents. The E3-03 story entry's claim about new decisions arising during the story is
  unchanged; the acceptance is a separate event.
- **R3 — `refresh()` error contract restored via an awaitable cycle (owner option B).**
  `SyncController` gained `suspend fun sync(reason): Outcome<Unit, AppError>`. `DefaultSyncController`
  reserves either a new active cycle or a join on the single pending follow-up through a
  completion handle; `requestSync` is unchanged for every other caller and no caller busy-waits on
  `status`. Offline and `LOCAL_OWNER` return `Ok(Unit)`; a failed pull returns `Err` carrying the
  failure, which required retaining the underlying error in `failPullCycle`. `VehicleSliceRuntime.refresh()`
  delegates to `sync` and returns its result unchanged; `createVehicle`/`updateVehicle` stay on
  `requestSync(PostWriteDebounce)`. Recorded as `D-171` / ADR-0172 (`Accepted`, owner-selected option
  B, status-flow alternative rejected) and mirrored into `docs/DECISION_BOARD.md`,
  `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/adr/README.md` and the project log.
- **R4 — Aggregate counts preserved on unexpected and adoption failure.** `refreshStatus` reports
  the real `persistence.counts()` in the `unexpectedFailure`, `adoptionFailure` and `cycleFailure`
  branches; `retryable` takes `max(real, 1)` so a failure with zero outbox rows stays
  representable, and `poisoned` is never reduced. Tests assert an adoption failure and an unexpected
  failure each preserve a non-zero poisoned count.
- **R5 — Connectivity row state and aggregate now agree.** `SyncDatabaseAccess.failPush` leaves the
  entity `PENDING` when `lastErrorCode` is in `CONNECTIVITY_ERROR_CODES` (retry context stays in the
  outbox), so the row and the `SyncStatus.Pending` aggregate say the same thing; `FAILED_RETRYABLE`
  is reserved for non-connectivity retryable failures. `docs/CONTRACTS.md §7` and `§9.9` state the
  resolution. A test pushes with `RemoteError.Unavailable` and asserts row state and aggregate
  together.
- **R6 — Failure path guarded by `localRevision`.** `recordOutboxFailure`, `markVehiclePushFailed`
  and `markFuelEntryPushFailed` now carry the `pushedLocalRevision` guard, matching the confirm path,
  so a local edit during an in-flight push does not inherit the older attempt's retry context. A
  database test seeds a newer revision with unrelated context and asserts the stale failure leaves it
  untouched; a controller test asserts the newer revision keeps `attemptCount = 0` and a clear error.
- **R7 — Dead `NotFound` poison arm removed.** The `RemoteError.NotFound -> false` arm was
  unreachable behind the early `confirmPush(row, null)` return and is gone, with a comment on why
  `NotFound` is treated as success and why `serverUpdatedAt` is cleared to NULL for the `§9.6` LWW
  comparison. `docs/CONTRACTS.md §6` states it, and a test pins the behaviour.

Verification for this round:

- `./gradlew ktlintCheck detekt architectureCheck contractCheck koverVerify` pass; `contractCheck`
  reports 173 decisions and zero `PENDING`.
- `./gradlew :core:sync:testAndroidHostTest :core:database:testAndroidHostTest
  :shared:testAndroidHostTest` pass.
- `:shared:testAndroidHostTest --rerun-tasks` passed **10/10** consecutive runs, so the E3-03 test
  determinism property did not regress.
- The complete non-instrumented command passes 638 tasks; `:shared:iosSimulatorArm64Test` passed
  **12/12** consecutive runs; `:composition:ios:linkDebugFrameworkIosSimulatorArm64` passes and the
  regenerated header is byte-identical to the golden; the host-app `xcodebuild` is
  `** BUILD SUCCEEDED **`; the protected Android instrumented suite passes 17 tests on the D-84 API
  36 emulator.

## Third Owner-Review Correction Round (2026-09-13)

Four defects, TDD: each new test failed against the unfixed code before the fix.

- **BLOCKER 1 — total pull classification.** `RemoteDocument.toPullRecord` read the top-level keys
  `id`, `ownerId`, `updatedAt`, `deleted`, `deletedAt` and `schemaVersion` through
  `JsonObject.getValue`, and the diagnostic `schemaVersion` through `getValue`, so a document missing
  a key threw `NoSuchElementException`. That is neither `IllegalArgumentException` nor
  `IllegalStateException`, so it escaped `toPullRecord`, `pullEntity` and `runCycle` and was swallowed
  by the generic cycle catch: no quarantine, `Failed(1, 0)`, an `UnexpectedError` report and a cursor
  that never advanced. Every field read on the validation path is now reached through
  `get(name) + require`, and `schemaVersionOrNull` reads non-throwing, so the classification is total
  and a missing key becomes a `MalformedPayload` quarantine record with the cursor advancing.
  `docs/CONTRACTS.md §9.5` states the totality requirement. Tests iterate each of the five keys and
  assert the quarantine, the advanced cursor, no `UnexpectedError` and a non-`Failed` status; they
  fail on the previous `getValue` readers.
- **BLOCKER 2 — poisoned rows were retried automatically.** `selectDueOutbox` had no sync-state
  filter, so a `FAILED_POISONED` row was selected again once its capped backoff elapsed, producing a
  `FAILED_POISONED -> SYNCING` transition the `§7` table does not allow and repeating
  `onPoisoned`/`recordNonFatal` per cycle. The query now excludes an outbox row whose entity
  `syncState` is `FAILED_POISONED`, keeping `idx_outbox_due` effective; only `retryFailed()` or a
  local edit makes such a row due again. `docs/CONTRACTS.md §7` states the rule. The controller fake
  encoded the exclusion the production query lacked; the fake and the production path now agree.
  Tests: a poisoned row with a past `nextAttemptAt` is excluded until `resetFailed` (`SyncDatabaseAccessTest`),
  and after a `PermissionDenied` poison a later cycle past the backoff performs no second push and
  reports once (`DefaultSyncControllerTest`). Both fail on the unfixed query.
- **DEFECT 3 — the integration still decoded product fields.** `DocumentSnapshot.toFirestoreDocument`
  read every product field with a typed non-null `get<T>()`, so a missing or mistyped field threw
  inside `queryDocuments` and never reached `:core:sync` as raw JSON, leaving ADR-0171 failure path 2
  open. It now reads the provider's untyped field map (`untypedFields`, per-platform) and only
  strongly reads the ordering `updatedAt` transport timestamp; every product field is carried into
  `rawJson` verbatim. A document whose ordering timestamp is unusable fails the page as
  `RemoteError.InvalidArgument`. `docs/CONTRACTS.md §9.5` and the ADR-0171 reachability section are
  updated. Tests: a snapshot with one missing and one mistyped product field yields a successful
  `RemotePage` carrying the raw values. Correcting the integration exposed BLOCKER 1, which is fixed
  in the same round.
- **DEFECT 4 — `SyncError.ConflictUnresolved` was never surfaced.** `failPullCycle` set only a
  boolean, so a non-advancing cursor produced `Failed(1, 0)` with no code and no report, while
  `§9.4`/ADR-0170 require the fail-closed error. `pullEntity` now distinguishes the progress-invariant
  failure and reports `SyncError.ConflictUnresolved` through `onPoisoned` (a stranded cursor is not a
  connectivity-only failure, `§17`), returns it as the cycle outcome and leaves the stored cursor
  unchanged. Test: a page whose `nextCursor` does not strictly advance fails with
  `SyncError.ConflictUnresolved`, does not loop and preserves the stored cursor.
- **MINOR — `strictlyAfter` null document id.** `RemoteCursor.strictlyAfter` returned early on a
  later timestamp before its null check, so a `nextCursor` with a later timestamp and a null
  `lastDocumentId` reached `SqlDelightSyncPersistence.applyPullPage`'s `requireNotNull` and threw out
  of the persistence layer. It now rejects a null `lastDocumentId` regardless of the timestamp
  comparison, so a misbehaving source fails the pull cycle closed. A test pins it.
- **MINOR — dead `NotFound` poison arm.** The `RemoteError.NotFound` arm inside the `poisoned`
  `when` was already unreachable behind the early `confirmPush(row, null)` return and had been
  removed in the second round; the second-round restructure is confirmed to keep the `when` closed.

Verification for this round:

- `./gradlew ktlintCheck detekt architectureCheck contractCheck koverVerify` pass; `contractCheck`
  reports 173 decisions and zero `PENDING`.
- The focused `:core:sync`, `:core:database`, `:integration:firebase-firestore` and `:shared`
  Android-host tests pass.
- `:shared:testAndroidHostTest --rerun-tasks` passed **10/10** consecutive runs; the full
  `:shared:iosSimulatorArm64Test` passed **12/12** consecutive runs.
- The complete non-instrumented command passes 638 tasks; `:composition:ios:linkDebugFrameworkIosSimulatorArm64`
  passes and the header is byte-identical to the golden; the host-app `xcodebuild` is
  `** BUILD SUCCEEDED **`; the protected Android instrumented suite passes 17 tests on the D-84 API
  36 emulator.

## Fourth Owner-Review Correction Round (2026-09-13)

The review was written against `dbadbf7`, but the branch head when it arrived was already `6ceac01`
(the third round), which had closed part of both blocking items. The remaining gaps were fixed
TDD-first; every new test was shown failing on the pre-fix code before it passed.

- **BLOCKING 1 — `markSyncing` had no state guard.** `selectDueOutbox` already excluded
  `FAILED_POISONED` rows (third round), but `markVehicleSyncing` / `markFuelEntrySyncing` still ran
  `UPDATE ... SET syncState = 'SYNCING'` unconditionally. A poisoned row pushed again — even
  defensively — would leave `FAILED_POISONED`, drop out of `countPoisonedSyncRows`, and vanish from
  `SyncStatus.Failed`. Both statements now guard on `syncState != 'FAILED_POISONED'`, so the
  transition fails closed. `SyncDatabaseAccessTest` asserts the state stays `FAILED_POISONED` and
  `counts().poisoned` stays 1 after `markSyncing`; the controller test now asserts
  `SyncStatus.Failed.poisonedCount` survives a later cycle. The fake mirrors the guard. RED proven by
  reverting the guard.
- **BLOCKING 2 — the pull-validation helpers are total and the transport boundary is closed.**
  `string`/`long`/`boolean`/`nullableLong`/`nullableString` already read through `get(name)` and raise
  `IllegalArgumentException` on absence (third round), and `toRemoteDocument` already reads the
  ordering timestamp with a safe cast, so `NoSuchElementException`/`ClassCastException` no longer
  escape the closed `Outcome`. This round adds the missing coverage the review asked for: absent
  `ownerId`/`updatedAt`/`deleted`/`deletedAt` for **FUEL_ENTRY** as well as VEHICLE, and two
  `FirebaseRemoteSyncSourceTest` cases — a document missing `updatedAt` and one where `updatedAt` is
  not a timestamp — asserting `RemoteError.InvalidArgument` rather than a thrown exception. RED
  proven by reverting both the helpers and the transport cast.
- **MINOR 3 — connectivity codes duplicated as SQL literals.** The first attempt bound
  `CONNECTIVITY_ERROR_CODES` as a SQL parameter. That was measured to destabilise the
  `:shared:iosSimulatorArm64Test` graph-close path (bisected: parameter binding 11/15 passes vs 15/15
  for the reverted query, in the same worktree), so the accepted alternative was taken instead: the
  literals stay, and a new `:build-logic:convention` guard (`ConnectivityCodeParityTest`) parses both
  the constant and the three statements and fails the build when they diverge. The guard was proven
  to fire by mutating the SQL. `docs/CONTRACTS.md §7` records the guard.
- **MINOR 4 — `strictlyAfter` null document id.** Already fixed in the third round:
  `strictlyAfter` rejects a null `lastDocumentId` regardless of the timestamp comparison, so the
  persistence layer is never reached with a null id; `nullDocumentIdCursorFailsClosedRegardlessOfTimestamp`
  pins it. No further change was needed.

Verification for this round:

- `./gradlew ktlintCheck detekt architectureCheck contractCheck koverVerify` pass; `contractCheck`
  reports 173 decisions and zero `PENDING`.
- The focused `:core:sync`, `:core:database`, `:integration:firebase-firestore`, `:shared` and
  `:build-logic:convention` tests pass.
- `:shared:testAndroidHostTest --rerun-tasks` passed **10/10**; the full `:shared:iosSimulatorArm64Test`
  passed **15/15** consecutive runs on the final tree.
- The complete non-instrumented command passes; `:composition:ios:linkDebugFrameworkIosSimulatorArm64`
  passes and the header is byte-identical to the golden; the host-app `xcodebuild` is
  `** BUILD SUCCEEDED **`; the protected Android instrumented suite passes 17 tests on the D-84 API
  36 emulator.
- No new decision was opened: both blocking items and both minor items are corrections inside the
  accepted D-169 / D-170 / D-171 scope and the existing `§7` / `§9.5` / `§9.9` contract text.

## Fifth Owner-Review Correction Round (2026-09-13)

One blocking finding, fixed TDD-first.

- **BLOCKING 1 — a coalesced `ConnectivityRecovered` trigger lost its recovery step.**
  `registerTrigger` joined every trigger arriving during an active cycle to the single follow-up and
  discarded the trigger's own `SyncTrigger`, while `drainCycles` hardcoded the follow-up reason to
  `PostWriteDebounce`. `runCycle` ran `persistence.markConnectivityFailuresDue(clock.now())` only for
  `ConnectivityRecovered`, so when connectivity returned mid-cycle the follow-up never marked
  connectivity-only failures due: rows that failed with `REMOTE.UNAVAILABLE` /
  `REMOTE.DEADLINE_EXCEEDED` stayed behind their backoff, up to `MAX_BACKOFF_MS` (900_000 ms), instead
  of becoming due immediately. The `E3-03` acceptance criterion requires that guarantee whether the
  trigger starts its own cycle or is coalesced.

  Fix: `registerTrigger(reason)` accumulates each joined trigger's reason in a `pendingReasons` set,
  `drainCycles` carries the accumulated reasons into the follow-up cycle, and `runCycle(reasons)`
  runs every reason-dependent step any joined trigger requires — currently
  `SyncTrigger.ConnectivityRecovered in reasons`. No call site special-cases a reason, so a future
  reason-dependent step cannot regress the same way, and the `§9.1` serialization rules are
  unchanged: exactly one active cycle and at most one pending follow-up.

  Tests (all fail on the pre-fix code, RED proven by stashing the production change):
  1. `coalescedConnectivityRecoveredStillMarksFailuresDueInTheFollowUp` — with the cycle suspended in
     `onPushSuspend`, a joined `ConnectivityRecovered` records a `markConnectivityFailuresDue` call.
     The fake now records those calls.
  2. `coalescedConnectivityRecoveredMakesTheFailedRowDueAndPreservesAttempts` — end to end: a row
     failed with `REMOTE.UNAVAILABLE` behind a far-future `nextAttemptAt` becomes due and is pushed
     after the follow-up, with `attemptCount` unchanged at 4.
  3. `coalescedNonConnectivityTriggerDoesNotMarkFailuresDue` — the over-correction guard: a joined
     `Periodic` records no connectivity call.
  `concurrentTriggersProduceOneActiveAndOneFollowUpCycle` and
  `awaitableSyncResolvesAgainstTheFollowUpCycleWithoutStartingASecond` still pass unchanged, so the
  single-follow-up rule is preserved.

  No contract clarification was needed: the fix implements the `§9.7` / `§9.8` guarantee that already
  required `ConnectivityRecovered` to move connectivity-only failures due while preserving
  `attemptCount`. No new decision was opened.

Verification for this round:

- `./gradlew ktlintCheck detekt architectureCheck contractCheck koverVerify` pass; `contractCheck`
  reports 173 decisions and zero `PENDING`.
- The focused `:core:sync`, `:core:database`, `:integration:firebase-firestore`, `:shared` and
  `:build-logic:convention` tests pass.
- `:shared:testAndroidHostTest --rerun-tasks` passed **10/10**; the full `:shared:iosSimulatorArm64Test`
  passed **12/12** consecutive runs.
- The complete non-instrumented command passes; `:composition:ios:linkDebugFrameworkIosSimulatorArm64`
  passes and the header is byte-identical to the golden; the host-app `xcodebuild` is
  `** BUILD SUCCEEDED **`; the protected Android instrumented suite passes 17 tests on the D-84 API
  36 emulator.

## Sixth Owner-Review Correction Round (2026-09-13)

Branch head reviewed: `2123a139`. Two blocking items, three minors, two deferred follow-ups and one
question.

- **BLOCKING 1 — `§9.7` contradicted `§7` and `§9.9`.** The R5 round changed the connectivity row
  state to `PENDING` and updated `§7` and `§9.9`, but `§9.7` still described the superseded
  `FAILED_RETRYABLE` behaviour, and `§9.9` still described a `FAILED_RETRYABLE` row with a
  connectivity code, which is no longer reachable. `§9.7` is rewritten to state the implemented
  behaviour exactly: a connectivity-only failure leaves the entity `PENDING`, keeps `attemptCount`
  and `nextAttemptAt` in the outbox, never poisons, and is made due by `markConnectivityFailuresDue`,
  which selects on `lastErrorCode` alone and is therefore independent of the entity `syncState`. The
  poison-rule formula and the ~17-minutes-offline rationale are unchanged. `§9.9` is corrected. The
  `§7` / `§9.7` / `§9.9` triple now states one consistent rule.
- **BLOCKING 2 — manual-retry gap recorded as `D-173` / ADR-0174 (`Proposed`).** After R5 a
  connectivity row is `PENDING`, so `resetFailedOutbox` (which selects `FAILED_RETRYABLE` /
  `FAILED_POISONED` entities) does not clear its retry context and `retryFailed()` has no effect on
  it. A server-side `REMOTE.UNAVAILABLE`/`REMOTE.DEADLINE_EXCEEDED` while online fires no
  `ConnectivityRecovered`, so the row waits out its backoff up to `MAX_BACKOFF_MS`. Option B (extend
  the reset selection to connectivity codes regardless of entity state) is recommended; A (accept and
  bound in `§9.7`) and C (`PullToRefresh` calls `markConnectivityFailuresDue`) are the alternatives.
  Added the row to `docs/DECISION_BOARD.md` (registry and awaiting section),
  `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`; the story is
  `E3-18`, marked Human review required and `Needed by` the decision. No production change.
- **MINOR 3 — `§9.8` constants are declarative only.** Corrected the "Out of Scope / Not Done"
  sentence to state what `E3-03` actually wired — the post-write `requestSync(PostWriteDebounce)` call
  sites in `VehicleSliceRuntime` and the `refresh()` delegation — and added an explicit line to both
  the handoff and the `E3-04` backlog entry that enforcement of `SYNC_POST_WRITE_DEBOUNCE_MS` (2 s)
  and `SYNC_MIN_AUTOMATIC_INTERVAL_MS` (30 s) is `E3-04`'s, so the constants are currently
  declarative only.
- **MINOR 4 — dead `running` parameter and unreachable branch removed.** `refreshStatus(running:
  Boolean)` was always called with `false` and the `running -> SyncStatus.Syncing` branch was
  unreachable because `runCycle` assigns `Syncing` directly. Both are removed; the `Failed > Pending >
  Idle` precedence is unchanged. Existing controller tests pass unchanged, and `:core:sync` Kover
  line coverage stayed above the `D-18` threshold.
- **MINOR 5 — `drainCycles` could wedge silently.** The pending flag and its completion handle are now
  one value, `PendingFollowUp`, so a follow-up cannot be observed without a handle to complete; the
  `next == null` path also resets `cycleRunning`. Added
  `concurrentJoinersAllCompleteAgainstOneFollowUpWithoutWedging`, which fires five concurrent `sync()`
  callers during an active cycle and asserts all five complete and exactly one follow-up runs. The
  invariant already held through `registerTrigger`'s single-lock update, so this test is a guard
  rather than a RED-provable regression; the production change makes the inconsistent state
  unrepresentable. Recorded honestly here rather than claiming a RED.
- **FOLLOW-UPS 6 — two totality gaps deferred, not fixed.** `E3-19` (push-boundary
  `toFirestoreWrite` reads `getValue`, so a payload missing `id`/`ownerId`/`schemaVersion` escapes as
  `UnexpectedError` and stays `SYNCING`; deferred because it predates E3-03 — present on `main` — and
  the payload is written locally under `§8`, so reachability is low) and `E3-20`
  (`untypedFields()` throws for an unsupported provider value, failing the whole page and stalling the
  cursor instead of quarantining; deferred because the closed `§16` schema and `validPayload()` make
  it near-unreachable). Both are backlog items marked Human review required.
- **QUESTION 7 — answered in ADR-0173, scope section extended.** Option B as originally written did
  **not** cover completing or failing an in-flight `sync()` awaiter: if `graphScope` is cancelled
  between `registerTrigger` and the launched `drainCycles`, or while a cycle runs, the
  `CompletableDeferred` is never completed and `VehicleSliceRuntime.refresh()` suspends forever,
  because the awaiting caller lives outside `graphScope`. The ADR now states that option B includes
  the obligation to complete or fail every in-flight `sync()` awaiter (active and pending follow-up)
  with a closed `Outcome` on shutdown, and requires a test that closes the graph with a suspended
  `sync()` and asserts the caller returns rather than hanging. No code change in this round.

Verification for this round:

- `./gradlew ktlintCheck detekt architectureCheck contractCheck koverVerify` pass; `contractCheck`
  reports 174 decisions and zero `PENDING`.
- The focused `:core:sync`, `:core:database`, `:integration:firebase-firestore`, `:shared` and
  `:build-logic:convention` tests pass.
- `:shared:testAndroidHostTest --rerun-tasks` passed **10/10**; the full `:shared:iosSimulatorArm64Test`
  passed **10/10** consecutive runs.
- The complete non-instrumented command passes.

## Seventh Owner-Review Correction Round (2026-09-13)

Branch head reviewed: `00fc587`. Two blocking items and five minors.

- **BLOCKING 1 — the `§7` `SYNCING -> SYNCING` criterion was proven only against the fake.**
  **Reconciliation decision: `§9.3` and the `§7` "editor sets `PENDING` in the same transaction"
  invariant are normative, and the production SQL already implements them.** `updateVehicleRow` /
  `updateFuelEntryRow` set `PENDING` on a local edit, and `confirmVehiclePush` /
  `confirmFuelEntryPush` keep the entity state unchanged on a stale revision, so the reachable
  sequence is `SYNCING -> PENDING`, and the `§7` row `SYNCING -> SYNCING` was unreachable and
  incorrect. That row is removed and the `SYNCING -> PENDING` row now states the local-edit cause
  explicitly; `§9.3` states that an ack never downgrades an edited row; `§7`'s stale prose about a
  row remaining `SYNCING` is corrected to say `SYNCING -> SYNCING` is not reachable. `docs/BACKLOG.md`
  `E3-03` was updated in the same change: its acceptance criterion now requires the `SYNCING ->
  PENDING` sequence rather than the unreachable one, so no criterion the code does not meet is left
  standing. `docs/TECHNICAL_PLAN.md` test 6 was corrected to the same rule. `FakeSyncPersistence.edit()`
  now transitions to `PENDING` and `confirmPush` no longer invents a `PENDING` transition on a
  mismatch, mirroring the SQL; `localEditDuringInflightPushRemainsPending` re-derives its expected
  history as `["SYNCING", "PENDING"]` (RED proven by reverting the fake). Added
  `SyncDatabaseAccessTest.confirmPushWithAStaleRevisionKeepsTheEditedRowPendingAndTheOutbox`, which
  pushes a row, edits the entity to `localRevision = 2` in flight, confirms the stale revision and
  asserts the entity stays `PENDING`, `serverUpdatedAt` is stamped and the outbox keeps revision 2.
  Also corrected `localEditDuringFailingPushKeepsTheNewRevisionUnstamped` to the reconciled state.
- **BLOCKING 2 — a full push batch never scheduled a follow-up.** `push()` now drains every due
  batch in one cycle: it repeats while a batch is full and stops when a full batch contains no
  entity not already attempted in that push, so each row is attempted at most once per cycle and the
  loop cannot spin even when a slow cycle outlasts a failed row's backoff. The `§9.1` single-active /
  single-follow-up rules are unchanged and the pull step still runs. Tests:
  `fullPushBatchContinuesUntilEveryDueRowIsPushedWithoutAnExternalTrigger` pushes `PUSH_BATCH_LIMIT * 2 + 20`
  rows in one cycle and asserts all are pushed with an empty outbox, and
  `fullFailingPushBatchTerminatesTheContinuation` asserts a full batch that keeps failing attempts
  each row exactly once and stops. `PUSH_BATCH_LIMIT` became `internal` so tests size against the
  contract value. Both were RED before the change. This removes the owner-visible bound, so no new
  decision was needed.
- **MINOR 3 — `retryFailed()` clobbered `Syncing`.** It now refreshes the status only when no cycle
  is active; an active cycle keeps publishing `Syncing` and publishes the aggregate when it finishes.
  `manualRetryDuringAnActiveCycleKeepsPublishingSyncing` was RED before the change.
- **MINOR 4 — the connectivity parity guard could pass with one divergent statement.**
  `ConnectivityCodeParityTest` now asserts parity per statement rather than over their union, and
  `aSingleDivergentStatementIsRejected` is the failing fixture. Verified non-vacuous by mutating one
  statement and observing the real guard fail.
- **MINOR 5 — `:core:database` was an `implementation` dependency of a public signature.**
  `core/sync/build.gradle.kts` promotes it to `commonMainApi`, since `createSyncController(...)`
  exposes `SyncDatabaseAccess`. `architectureCheck` and the provider-decoupling run still pass.
- **MINOR 6 — `docs/TECHNICAL_PLAN.md` test 18 described the superseded rule.** Rewritten to state
  that a connectivity-failed row stays `PENDING` and that `FAILED_RETRYABLE` is not the connectivity
  outcome; `longOfflinePeriodNeverPoisonsAndBacksUpOnRecovery` was seeded with the corrected
  `PENDING` state so it asserts the `§7` / `§9.7` / `§9.9` rule.
- **MINOR 7 — `coalesceOutbox` left a stale `cycleId`.** The statement now clears `cycleId` with the
  other retry context, and `docs/CONTRACTS.md §8` states it.
  `coalescingALocalEditClearsTheStaleCycleCorrelation` was RED before the change.

Verification for this round:

- `./gradlew ktlintCheck detekt architectureCheck contractCheck koverVerify` pass; `contractCheck`
  reports 174 decisions and zero `PENDING`.
- The focused `:core:sync`, `:core:database`, `:integration:firebase-firestore`, `:shared` and
  `:build-logic:convention` tests pass; `:core:sync` Kover line coverage is 97.78%.
- `:shared:testAndroidHostTest --rerun-tasks` passed 25 of 25 runs after one native `SIGSEGV` inside
  the bundled SQLite library (`sqlite3DbMallocRawNN` while preparing an `IN` query) on a run with
  system load above 16. That crash is a native library fault under host contention, not a test
  assertion and not a regression: the same tree passed 25/25, including 15/15 at a comparable load,
  and the failing run reported no assertion failure. The full `:shared:iosSimulatorArm64Test` passed
  **10/10** consecutive runs.
- The complete non-instrumented command passes; `:composition:ios:linkDebugFrameworkIosSimulatorArm64`
  passes and the header is byte-identical to the golden; the host-app `xcodebuild` is
  `** BUILD SUCCEEDED **`; the protected Android instrumented suite passes 17 tests on the D-84 API
  36 emulator.

## Eighth Owner-Review Correction Round (2026-09-13)

Branch head reviewed: `16ffd34`. One blocking defect and three minors.

Owner decision: for BLOCKING 1 the owner selected **option A with an in-memory scope** (see `D-174` /
ADR-0175) after the agent reported that carrying microseconds into the persisted `sync_cursor` needs a
schema-tooling change, because this repository's SQLDelight configuration does not let an `.sqm`
reference a `schema.sq` table.

- **BLOCKING 1 — the millisecond cursor made `startAfter` non-exclusive.**
  `EntitySnapshot.toFirestoreWrite` maps `updatedAt` to `FirestoreServerTimestamp`, so Firestore stores
  a microsecond-precision server timestamp, but the integration truncated it with
  `Instant.fromEpochMilliseconds(timestamp.toMilliseconds())`. The later-page boundary
  `Timestamp.fromMilliseconds(truncatedMs)` then sorted before the previous page's last document, which
  was re-delivered; when the remaining change set was an exact page multiple, page N+1 returned only
  that repeated document, `nextCursor` equalled the request cursor, and the engine raised a false
  `SyncError.ConflictUnresolved` with nothing stranded. The suite stayed green because the controller
  fake modelled `startAfter` as index-exclusive on the document id alone.
  - Requirements 1–2: `FakeRemoteSyncSource` now keeps the stored microsecond ordering key separate
    from the delivered `serverUpdatedAt` and models `startAfter` as a strict total-order comparison.
    A `deliverUpdatedAtTruncatedToMillis` switch models the pre-fix integration. With it on,
    `moreThanOnePageSharingATimestampCompletes`, `aChangeSetOfExactlyOnePageCompletesWithoutAFalseProgressFailure`
    and `twoSameMillisecondDocumentsStraddlingAPageBoundaryDoNotFailTheCycle` all fail; with it off
    they pass. The new tests assert `Ok`, no `onPoisoned`, a non-`Failed` status, an advanced cursor
    and full completion.
  - Requirement 4: `FirebaseRemoteSyncSourceTest` asserts the full-precision cursor reaches
    `FirestoreQuery` and that the later-page boundary keeps the microsecond value
    (`aSubMillisecondCursorBecomesAFullPrecisionLaterPageBoundary`). `toProviderTimestamp` now uses
    `Timestamp(epochSeconds, nanosecondsOfSecond)` instead of `fromMilliseconds`, and
    `orderingUpdatedAtMicros()` reads the provider timestamp per platform.
  - Requirement 5: `§9.4` now states that the in-cycle cursor carries the provider's full precision
    and that the persisted `sync_cursor` stays an epoch-millisecond anchor the 30-second overlap
    re-includes; the stale "millisecond-distinguishable" claim is removed. `D-174` / ADR-0175 record
    the decision and its in-memory scope; no data migration and no `core/database/**` tooling change.
- **MINOR 3 — the pull cursor could regress inside the overlap window.** `upsertSyncCursor` now keeps
  the greater stored timestamp and, on a tie, the greater `lastDocumentId`, so a failed later page
  cannot move the anchor behind its pre-cycle position. `SyncDatabaseAccessTest.theStoredCursorNeverMovesBackwardsWithinACycle`
  was RED before the change; `§9.4` states the monotonic rule.
- **MINOR 4 — `canonicalName()` was duplicated.** Moved to `:core:model` as
  `canonicalVehicleName`; `:feature:vehicle` and `:core:sync` now call the single function, so
  `§3` duplicate-name detection cannot diverge between a local write and a remotely applied row. The
  feature test imports the new location.
- **MINOR 2 — push dependency order was not preserved across batches.** `selectDueOutbox` now joins
  the entity table, derives each row's `§8` dependency group from `deleted`, and orders by group then
  `seq` **before** the `LIMIT`, so a low-`seq` vehicle tombstone cannot be pushed in an earlier batch
  ahead of the fuel-entry tombstones it deletes. `§9.3` states the global-order rule. The controller
  fake mirrors the selection order, and
  `SyncDatabaseAccessTest.dueOutboxSelectsInGlobalDependencyOrderBeforeTheBatchLimit` was RED before
  the query change and proven non-vacuous by reverting it. `FakeSyncPersistence` gained a faithful
  `dependencyGroupForTest`.

New tests and how each RED was produced:

- `moreThanOnePageSharingATimestampCompletes` (updated): RED by modelling the pre-fix integration
  (`deliverUpdatedAtTruncatedToMillis = true`).
- `aChangeSetOfExactlyOnePageCompletesWithoutAFalseProgressFailure`: same RED mechanism.
- `twoSameMillisecondDocumentsStraddlingAPageBoundaryDoNotFailTheCycle`: same RED mechanism.
- `aSubMillisecondCursorBecomesAFullPrecisionLaterPageBoundary`: RED by reverting
  `toProviderTimestamp` to `fromMilliseconds` (the earlier `getValue`/`Timestamp` path).
- `theStoredCursorNeverMovesBackwardsWithinACycle`: RED by writing a regressing cursor with the old
  unconditional upsert.
- `dueOutboxSelectsInGlobalDependencyOrderBeforeTheBatchLimit`: RED by reverting `selectDueOutbox` to
  `ORDER BY seq` only.
- `pushDependencyOrderHoldsAcrossBatchBoundaries`: RED against the pre-fix selection order.

Verification for this round:

- `./gradlew ktlintCheck detekt architectureCheck contractCheck koverVerify` pass; `contractCheck`
  reports 175 decisions and zero `PENDING`.
- The focused `:core:sync`, `:core:database`, `:integration:firebase-firestore`, `:shared`,
  `:feature:vehicle` and `:build-logic:convention` tests pass.
- `:shared:testAndroidHostTest --rerun-tasks` passed **10/10**; the full `:shared:iosSimulatorArm64Test`
  passed **10/10** consecutive runs.
- The complete non-instrumented command passes; `:composition:ios:linkDebugFrameworkIosSimulatorArm64`
  passes and the header is byte-identical to the golden; the host-app `xcodebuild` is
  `** BUILD SUCCEEDED **`; the protected Android instrumented suite passes 17 tests on the D-84 API
  36 emulator.

## Ninth Owner-Review Correction Round (2026-09-13)

Branch head reviewed: `ecb4487`. One blocking defect and three minors.

- **BLOCKING 1 — `Unauthenticated` contradicted the normative `§6`.** `handlePushFailure` incremented
  `attemptCount` for every non-`NotFound` error, poisoned `Unauthenticated` at the ceiling and mapped
  it to `SyncError.PayloadPoisoned`. `§6` (normative, "decides retry versus poison") says
  `Unauthenticated` retries "after a valid auth session, `attemptCount` unchanged". **Owner decision:
  option A — the implementation follows `§6`; the contract is not changed.** The fix separates
  `incrementsAttempt` (only `Unauthenticated` is excluded), removes `Unauthenticated` from the poison
  arm, and leaves the mapping without a dead `AuthExpired` arm. **Row state:** the documents converge
  on `FAILED_RETRYABLE` — `§7` reserves `PENDING` for the `CONNECTIVITY_ERROR_CODES` case, `§9.7`'s
  `PENDING` rule is explicitly connectivity-only, and `§9.9` renders a non-connectivity retryable
  failure as `Failed` — so the aggregate reports `Failed(retryable=1, poisoned=0)`. `SyncError.AuthExpired`
  is therefore not reached on this path; it remains a declared `§20` taxonomy leaf whose `§6` mapping
  target is real, and the handoff records that it is not produced by the sync engine rather than
  leaving an unproduced leaf unstated.
  - Test 1: `repeatedAuthenticationFailuresNeverPoisonAndKeepTheAttemptCount` inverts the old
    `exhaustedAuthenticationFailuresPoisonTheRow`. RED proven by reverting the production change.
  - Test 2: `anAuthenticationFailureIsFollowedByASuccessfulPushWithoutARaisedCount`. RED with the
    same revert.
  - Test 3: `authenticationRetryDoesNotWeakenUnknownOrValidationPoisoning` pins `Unknown` still
    reaching `FAILED_POISONED` at the ceiling and `PermissionDenied`/`InvalidArgument` still poisoning
    on the first attempt with their mapped `SyncError`. It passes before and after (over-correction
    guard).
  - Requirement 4 (a `PENDING`-specific `SyncDatabaseAccessTest`) does not apply because the chosen
    state is `FAILED_RETRYABLE`.
  - Documents: `§9.7` and `§9.9` gained the `Unauthenticated` rule so the `§6`/`§7`/`§9.7`/`§9.9` set
    states one rule. The historical claim in this handoff and in `docs/PROJECT_LOG.md` is corrected by
    a dated note rather than by rewriting the original text.
- **MINOR 2 — a finishing cycle could publish a stale status.** `drainCycles` released `cycleMutex`
  and then published the terminal status, so a trigger that started a new cycle in that window saw its
  `Syncing` overwritten. A monotonic `cycleGeneration` is captured under the same lock that clears the
  active-cycle reservation, and the terminal publish skips when a newer cycle has started. RED proven
  by removing the generation guard; `aFinishingCycleNeverOverwritesTheSyncingStatusOfANewerCycle`
  pins it.
- **MINOR 3 — inconsistent initial sync status.** `FuelEntryListStateHolder.state` seeded `Idle`;
  it now seeds `syncStatus.value`, matching `VehicleListStateHolder`.
  `listSeedsItsInitialSyncStatusFromTheInjectedFlow` was RED before the change.
- **MINOR 4 — `SYNCING` has no explicit recovery path after a process death.** No recovery statement
  was added. `§7` now records that the `SYNCING` row is a push transient, that the surviving outbox
  row is the recovery mechanism, and that the aggregate is outbox-derived and therefore correct while
  the entity row is stale. Deferred hardening is tracked as `E3-21` (Human review required).

Verification for this round:

- `./gradlew ktlintCheck detekt architectureCheck contractCheck koverVerify` pass; `contractCheck`
  reports 175 decisions and zero `PENDING`.
- The focused `:core:sync`, `:core:database`, `:integration:firebase-firestore`, `:shared`,
  `:feature:fuel` and `:build-logic:convention` tests pass.
- `:shared:testAndroidHostTest --rerun-tasks` passed **10/10**; the full `:shared:iosSimulatorArm64Test`
  passed **10/10** consecutive runs.
- The complete non-instrumented command passes; `:composition:ios:linkDebugFrameworkIosSimulatorArm64`
  passes and the header is byte-identical to the golden; the host-app `xcodebuild` is
  `** BUILD SUCCEEDED **`; the protected Android instrumented suite passes 17 tests on the D-84 API
  36 emulator.

## Tenth Owner-Review Correction Round (Owner report labelled round 9; 2026-09-15)

The report reviewed branch head `ecb4487`, but the branch was already at `b8de833` when this work
started. That intervening commit is the ninth correction round recorded above. This section therefore
records the incoming report as the tenth correction round without reverting the accepted ninth-round
fixes. The report contained two blockers and five minors.

- **BLOCKING 1 — the documented entity state machine contradicted production.** The owner selected
  option A: production was correct and the documents were stale. `docs/CONTRACTS.md §7` now makes
  automatic due retry `FAILED_RETRYABLE -> SYNCING` directly, reserves
  `FAILED_RETRYABLE -> PENDING` for manual retry or a local edit, removes
  `FAILED_RETRYABLE -> FAILED_POISONED`, assigns qualifying retry-ceiling poison to
  `SYNCING -> FAILED_POISONED`, and limits `SYNCING -> FAILED_RETRYABLE` to non-connectivity
  retryable remote push failures. `docs/TECHNICAL_PLAN.md §§6, 9` and the E3-03 backlog entry mirror
  that rule. The allowed-subset guard, controller tests for automatic retry and ceiling poison, and
  SQLDelight vehicle/fuel path tests pin the corrected model.
- **BLOCKING 2 — the backlog's connectivity criterion required the wrong state.** The E3-03
  criterion now resets every outbox row whose `lastErrorCode` belongs to
  `CONNECTIVITY_ERROR_CODES`, independently of entity state, while preserving `attemptCount`. A
  sweep of the rest of E3-03, `docs/TECHNICAL_PLAN.md §9`, `docs/SPECIFICATION.md` and this handoff
  found no second stale connectivity/state rule.
- **MINOR 3 — graph convergence did not assert the fuel holder's initial value.** The production
  seed was already corrected by `b8de833`. The graph-level convergence test now constructs both list
  holders while the graph-owned controller is `Syncing` and asserts that both initial states are
  `Syncing` before observing their common terminal state.
- **MINOR 4 — the controller fake under-modelled local-edit reset semantics.**
  `FakeSyncPersistence.edit` now resets `attemptCount`, `nextAttemptAt`, `lastErrorCode`, stored
  error details and cycle attribution, matching the SQL path. The in-flight local-edit regression
  test seeds stale retry context and asserts the full reset.
- **MINOR 5 — aggregate classification could omit a stranded outbox row.** The implementation choice
  was to widen `countPendingSyncRows`: every outbox row not classified as retryable or poisoned
  failed work is pending work, including a stranded `SYNCING` row with a non-connectivity error.
  The three aggregate buckets are therefore exhaustive for outbox-backed work. A SQLDelight test
  pins the stranded-`SYNCING` case.
- **MINOR 6 — `§9.5` described the ordering timestamp as milliseconds.** It now names the provider
  microsecond value returned by `orderingUpdatedAtMicros()` and keeps the fail-closed
  `InvalidArgument` behavior for missing or unusable ordering metadata.
- **MINOR 7 — `toEpochMicroseconds` appeared production-public only for tests.** The function remains
  public as the symmetric counterpart of the provider-facing inverse conversion; its KDoc now
  records that integration boundaries and deterministic cross-module test construction share the
  canonical arithmetic without exposing provider types or duplicating conversion logic.

TDD evidence for this round:

- RED command:
  `./gradlew :core:sync:testAndroidHostTest --tests '*DefaultSyncControllerTest'
  :core:database:testAndroidHostTest --tests '*SyncDatabaseAccessTest'
  :shared:testAndroidHostTest --tests '*AppGraphContractTest'`. It failed in the three intended
  places: local edit retained `attemptCount = 4`, the observed transitions lacked
  `FAILED_RETRYABLE -> SYNCING`, and the stranded `SYNCING` row produced zero pending work. After
  production changes, the exact same command was GREEN: `BUILD SUCCESSFUL in 5s`, 82 tasks.
- Because `b8de833` already contained the fuel initial-seed production fix, its new convergence
  assertion was demonstrated RED by temporarily restoring the old `Idle` seed, then restoring the
  branch implementation. The exact command was
  `./gradlew :shared:testAndroidHostTest --tests
  '*AppGraphContractTest.vehicleAndFuelListsObserveTheSameGraphOwnedSyncStatus'`: RED reported
  expected `Syncing` but was `Idle`; the restored implementation was GREEN with
  `BUILD SUCCESSFUL in 2s`. No temporary production edit remains.

Verification for this round:

- `./gradlew ktlintCheck detekt architectureCheck contractCheck koverVerify` —
  `BUILD SUCCESSFUL in 3s`, 394 tasks; `contractCheck` reported 175 decisions, 19 passing assertions
  and zero `PENDING`.
- `./gradlew :core:sync:testAndroidHostTest :core:database:testAndroidHostTest
  :integration:firebase-firestore:testAndroidHostTest :shared:testAndroidHostTest
  :feature:vehicle:testAndroidHostTest :feature:fuel:testAndroidHostTest
  :build-logic:convention:test --rerun-tasks` — `BUILD SUCCESSFUL in 16s`, 102 tasks.
- `./gradlew :shared:testAndroidHostTest --rerun-tasks --quiet` — **10/10** consecutive runs passed.
- `./gradlew :shared:iosSimulatorArm64Test --rerun-tasks --quiet` — **10/10** consecutive runs passed.
- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
  koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest
  iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test
  -x :integration:firebase-firestore:iosSimulatorArm64Test
  -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test` —
  final rerun `BUILD SUCCESSFUL in 16s`, 642 tasks.
- `./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64 --stacktrace` —
  `BUILD SUCCESSFUL in 6s`, 70 tasks. `diff -u
  shared/build/generated/objc-header/Shared.h.golden
  composition/ios/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Headers/Shared.h` —
  exit 0 with no output; the headers are byte-identical.
- From `iosApp/`: `xcodebuild -project carApp.xcodeproj -scheme carApp -sdk iphonesimulator
  -configuration Debug ARCHS=arm64 ONLY_ACTIVE_ARCH=NO build` — `** BUILD SUCCEEDED **`. The first
  sandboxed attempt failed with exit 74 because Xcode could not write its cache or reach
  CoreSimulator; the unrestricted rerun is the platform result.
- `env ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest --stacktrace` —
  `BUILD SUCCESSFUL in 55s`, all 17 tests passed on the D-84 API 36 emulator.
- No pull-request merge was performed; PR #69 still requires the owner's gated review and all ten
  required checks.

## Eleventh Owner-Review Correction Round (2026-09-15)

Five findings were closed without changing behavior outside their stated scope.

- **Finding 1 — stale batch revision at `markSyncing`.** `markVehicleSyncing` and
  `markFuelEntrySyncing` now require the selected `localRevision` as well as the existing
  `FAILED_POISONED` guard. `SyncDatabaseAccess` and `SqlDelightSyncPersistence` propagate the
  revision, and `FakeSyncPersistence` mirrors both guards. A local edit after batch selection but
  before the later row reaches `markSyncing` therefore remains `PENDING`; its stale remote attempt
  cannot stamp the edited row.
- **Finding 2 — D-169/D-174 reconciliation.** The D-169 rows in `docs/DECISION_BOARD.md`,
  `docs/SPECIFICATION.md §12` and `docs/TECHNICAL_PLAN.md §2`, plus the `§8` recovery guarantee and
  ADR-0170, now state that D-169 governs the persisted epoch-millisecond anchor and the fail-closed
  rule at the active cursor's precision. D-174 governs the in-cycle provider-microsecond boundary.
  ADR-0170 is marked superseded in part by ADR-0175, and ADR-0175 has the reciprocal reference. No
  new decision ID was introduced.
- **Finding 3 — epoch precision documentation and API visibility.** `EpochPrecision.kt` now states
  the actual split: epoch milliseconds in persisted `sync_cursor`, provider microseconds in the
  in-cycle ordering cursor. `toEpochMicroseconds()` remains public and hidden from Objective-C under
  D-174 because cross-module integration tests need the canonical forward conversion; this avoids
  duplicated arithmetic and provider-type leakage even though production currently calls only the
  inverse direction.
- **Finding 4 — aggregate snapshot consistency.** `SyncDatabaseAccess.counts()` reads pending,
  retryable and poisoned buckets inside one SQLDelight transaction. The test driver asserts exactly
  one transaction encloses the three reads, preventing concurrent mutation from mixing snapshots.
- **Finding 5 — quarantine timestamp semantics.** Re-quarantine updates diagnostic fields while
  preserving the original `createdAt`. This was chosen because `§9.5` already names a creation time;
  preserving it keeps that meaning without a column rename, schema migration or representational
  change. The SQL test delivers the same quarantined entity twice and pins the first timestamp.
- **E3-21 re-check.** Its justification remains correct after Finding 1. The revision guard closes
  the local-edit window between batch selection and `markSyncing`; the remaining reachable stale
  `SYNCING` state still requires process death after a successful mark and before that push resolves.

TDD evidence for this round:

- RED `9177c70`: the focused command failed in four intended places. The controller and real SQL
  tests each expected `PENDING` but observed `SYNCING`; the aggregate test expected one transaction
  but observed zero; the quarantine test expected `createdAt = 400` but observed `900`.
- GREEN `65a6297`: the same four focused cases pass, followed by the complete
  `:core:sync:testAndroidHostTest` and `:core:database:testAndroidHostTest` suites with
  `--rerun-tasks`.
- REFACTOR: the new cases were moved into bounded review-specific test classes after detekt correctly
  reported both original classes over `LargeClass`; no behavior changed.

Verification for this round:

- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
  koverVerify` — `BUILD SUCCESSFUL`, 397 tasks. `contractCheck` reports 175 decisions, all 19
  assertions passing and zero `PENDING`.
- The complete non-instrumented repository command from `AGENTS.md` — initial `BUILD SUCCESSFUL in
  19s`; final documented-tree rerun `BUILD SUCCESSFUL in 5s`, both 642 tasks, including Android
  assembly/unit tests and Android-host/iOS-simulator shared suites.
- No pull-request merge was performed; PR #69 remains subject to mandatory owner review and its ten
  required checks.

## Human Review Gate

- Applies: E3-03 is a gated story and changes gated sync/database/normative paths and topics.
