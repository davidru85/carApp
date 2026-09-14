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

- Date: 2026-09-13.
- Branch and base: `story/E3-03-core-sync-engine` from `main` at `fbc6d64`.
- Current phase and latest commit: REFACTOR complete and verified; the third TDD commit is
  `refactor(E3-03): finalize sync engine`. The story then received three owner-review correction
  rounds on the open pull request. RED is `a66c612`; GREEN is `bfced6b`.
- Push and pull-request status: pushed on `story/E3-03-core-sync-engine`; gated pull request opened
  against `main` after this commit. It is not merged and MUST NOT be merged on agent judgement.
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
- Known failures: none.
- Open decisions or blockers: none.
- Exact next step: none for the agent. The pull request awaits the mandatory owner review and the
  ten required checks.

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
- Platform background scheduling and repository post-write trigger wiring remain owned by E3-04.
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

## Human Review Gate

- Applies: E3-03 is a gated story and changes gated sync/database/normative paths and topics.
