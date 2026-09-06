# Agent Handoff - E2-06

## Story

`E2-06 - Local Owner Adoption - M`

## Ready Check

- Backlog story: `E2-06 - Local Owner Adoption - M` is explicit in `docs/BACKLOG.md` and is the next
  open Phase 2 story. The owner selected it ahead of `E2-04`, which is also the backlog order.
- Acceptance criteria reviewed: one transaction rewrites every `LOCAL_OWNER` row to the new UID and
  bumps `localRevision`; existing `localMutationSeq` values are preserved; outbox rows are inserted
  in the push dependency order of `docs/CONTRACTS.md §8` and then by `localMutationSeq ASC, id ASC`;
  every non-`SYNCED` row, `FAILED_POISONED` included, is reset to `PENDING` with its error context
  cleared and a snapshot enqueued; adoption `seq` values are monotonic and the first one is
  `(max pre-existing seq) + 1`; the operation is idempotent; a test starts from a populated
  `LOCAL_OWNER` database with vehicles, fuel entries and interleaved edits and asserts that nothing
  is lost and that the outbox order is deterministic; and adoption is triggered automatically when
  connectivity returns rather than only from a UI action.
- Dependencies checked: `E1-11` fixed the Vehicle outbox payload `entityType` and MUST precede this
  story; it is merged. `E2-01` supplied `OwnerContext` and `AuthOwnerContext`; `E2-02` supplied the
  Firebase Auth credential exchange; `E2-03` supplied `SessionStateHolder` and F-1 routing and
  merged on 2026-09-06 as `fe9ed55`, which is this branch's base. `E2-06` blocks `E3-04`.
- Decisions checked: `docs/DECISION_BOARD.md` reports no `Proposed` or `Pending` decision awaiting
  owner confirmation, so no decision blocks the story. The decisions that govern the work are
  `D-38` (`DatabaseMutations` is the sole transaction boundary for synchronized entity writes),
  `D-110` (explicit outbox entity-type tokens in production code), `D-88` (`SyncController` stays
  staged until `E3-03`) and `D-105` (continuous progress documentation).
- Normative sections reviewed: `docs/CONTRACTS.md` §7 (`syncState` machine and its invariants), §8
  (outbox contract, coalescing, `localMutationSeq` and the four-group push dependency order), §11.2
  and §11.4 (local owner adoption), and §20.0 (`OwnerId` and `LOCAL_OWNER`);
  `docs/TECHNICAL_PLAN.md` §4 (dependency rule table), §5 (local data model and outbox DDL) and the
  required sync test 14; `docs/SPECIFICATION.md` §11 (TDD and the red/green/refactor workflow);
  and `AGENTS.md` Definition of Ready, Story Intake, Continuous Progress Documentation, Definition
  of Done and Human Review Gates.
- Expected verification: new `:core:database` common tests for the adoption transaction, ordering,
  idempotency and the populated-database scenario; new `:shared` common tests for the automatic
  trigger; the complete non-instrumented repository command of `AGENTS.md` §`Build and verify`; and
  `git diff --check`.
- Human review gates identified before work: **`E2-06` is a gated story** in the canonical gate list
  of `AGENTS.md`, and `core/database/**` is a gated path. The agent does not merge the pull request.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES); every repository
  artifact, branch, commit and pull-request field is technical English.
- TDD workflow: the owner requested local RED, GREEN and REFACTOR commits, then one push and one
  pull request. This replaces the default push-after-each-phase cadence for `E2-06` while preserving
  failing-test-first order and separate phase commits.

## In-Progress Checkpoint

### Owner review round 1 applied on pull request #55 (2026-09-06)

- Date: 2026-09-06. Branch and base: `story/E2-06-local-owner-adoption`, based on `main` at
  `fe9ed55`. Pull request #55 is open.
- Current phase and latest commit: REFACTOR, in the commit that contains this text. The round's
  phase commits are `8492dbb` (RED), `9a2c227` (GREEN) and this one.
- Push and pull-request status: the first round's ten checks ran and **`objc-header-golden-check`
  failed**; every other check passed. This round fixes that defect and applies the owner's three
  decisions. All three commits are pushed, and **all ten required checks pass on this round**,
  including the `objc-header-golden-check` that failed before and the `shared-tests` job whose
  `E1-14` flake did not fire. `mergeable` is clean; the gate that remains is the policy one. The
  agent does not merge.
- Completed since the previous checkpoint:
  1. **Defect found by CI, not by me.** Both `toAdoptionOutboxPayload` mappers were public in
     modules `:composition:ios` exports, so they reached the committed Objective-C golden header.
     The first round's handoff and pull request claimed the Swift-facing surface was unchanged;
     that claim was wrong. Both are now `@HiddenFromObjC` and the regenerated header matches the
     committed golden byte for byte.
  2. **`D-124` revised, not superseded.** The owner rejected gating the retry on `LOCAL_OWNER` rows
     alone: rows are not the decision, and the gate stranded a device that was online when it
     started locally, whose only connectivity emission fires before any row exists. The retry now
     admits either the explicit choice, remembered for the life of the process, or the rows a
     restart leaves behind; a write committing under the sentinel re-evaluates it; and concurrent
     triggers produce one attempt. The decision record was revised in place because `D-124` has
     never been merged.
  3. **`D-125` extended.** The gate stands, but a failed adoption is no longer a silent wait. It is
     `PersistenceError.TransactionFailed` on read and write paths, never thrown across the gate and
     never a cancellation of the caller; the list stays unknown with a message and `refresh()`
     genuinely re-runs the gate; and the two automatic triggers are siblings under a supervisor so
     neither can disable the other. The deferred half — automatic retry with backoff and aggregate
     status — is now an acceptance criterion of `E3-03`, not a handoff note.
- Verification evidence and known failures: see `Verification Run`, which is rewritten for this
  round. No known failure.
- Open decisions or blockers: none. `D-123` was approved unchanged.
- Exact next step: wait for the owner's review of the applied round. Nothing is in flight, and no
  work is left that this story owns.

### Story complete, pull request #55 open and awaiting owner review (2026-09-06)

- Date: 2026-09-06. Branch and base: `story/E2-06-local-owner-adoption`, based on `main` at
  `fe9ed55` (the `E2-03` merge commit).
- Current phase and latest commit: REFACTOR, in the commit that contains this text. The three phase
  commits are `53491bd` (intake and status realignment), `2104ce1` (RED) and `4d44074` (GREEN).
- Push and pull-request status: the branch is pushed and **pull request #55 is open** on `main`,
  created after the single push the owner asked for. The agent does **not** merge: `E2-06` is a
  gated story and `core/database/**`,
  `docs/CONTRACTS.md`, `docs/SPECIFICATION.md`, `docs/DECISION_BOARD.md` and `docs/adr/**` are gated
  paths.
- Completed since the previous checkpoint:
  1. RED: eleven failing tests, seven failing on the assertion they exist for and four the
     complementary halves of those pairs.
  2. GREEN: `DatabaseMutations.adoptLocalOwner`, the two feature payload mappers,
     `LocalOwnerAdoption` and `AdoptionGatedVehicleRepository`, wired in `DefaultAppGraph` and
     `VehicleSliceRuntime`.
  3. REFACTOR: the enqueue partition extracted to two named group functions; `D-123`, `D-124` and
     `D-125` recorded with `ADR-0124`, `ADR-0125` and `ADR-0126` and mirrored in all four documents;
     `docs/CONTRACTS.md` §11.2 and §11.4 updated with the retry gate and the read gate.
- Verification evidence and known failures: see `Verification Run`. The complete `AGENTS.md`
  non-instrumented command passed 636 actionable tasks and forced provider decoupling passed 234.
  No known failure. The `D-84` API 36 instrumented suite passed 14 of 14.
- Open decisions or blockers: none blocking. Three decisions were taken by the agent and are
  recorded as `Accepted` with their ADRs; they are the ones to review first, and each ADR carries
  the alternatives that were rejected.
- Exact next step: wait for the ten required checks on pull request #55, then for the owner's
  review. Three agent-taken decisions (`D-123`, `D-124`, `D-125`) are presented for that review, and
  `D-125` is the one to look at first. Nothing else is in flight.

### Intake and status realignment checkpoint (2026-09-06)

- Date: 2026-09-06. Branch and base: `story/E2-06-local-owner-adoption`, based on `main` at
  `fe9ed55`.
- Current phase and latest commit: intake, `53491bd`. No product code had changed.
- Push and pull-request status: not pushed; no pull request.
- Completed since the previous checkpoint: the ready check above; the realignment of the six
  documents that still described `E2-03` as awaiting review after pull request #54 merged
  (`AGENTS.md`, `README.md`, `docs/DEFINITION.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md` and a
  closure update in `docs/handoff-E2-03.md`), plus the project log entry recording the merge.
- Verification evidence and known failures: `./gradlew contractCheck architectureCheck` passed.
- Open decisions or blockers: none.
- Exact next step: write the failing RED tests.

## Scope Completed

- The `docs/CONTRACTS.md §11.4` adoption transaction, in `:core:database`, behind
  `DatabaseMutations.adoptLocalOwner`.
- The outbox payload for an adopted row, supplied by each feature over its own database row type.
- The automatic triggers of `§11.2`: anonymous UID acquisition when connectivity returns, and
  adoption when the owner changes. Neither is reachable from the UI.
- The read gate that keeps a newly authenticated owner's vehicle list unknown until adoption has
  committed.
- The status realignment `AGENTS.md` requires of the story that follows a merge.

## Acceptance Evidence

Every criterion of `docs/BACKLOG.md` `E2-06`, in order:

1. *One transaction rewrites every `LOCAL_OWNER` row, bumps `localRevision`, enqueues a snapshot per
   non-synced row.* `adoptLocalOwner` runs entirely inside `database.transaction`.
   `adoptionRewritesEveryLocalOwnerRowToTheNewUidAndBumpsLocalRevision`.
2. *Adoption preserves `localMutationSeq` and inserts in the `§8` push dependency order, then
   `localMutationSeq ASC, id ASC`.*
   `adoptionPreservesLocalMutationSeqAndDoesNotConsumeTheMutationCounter` and
   `adoptionEnqueuesTheOutboxInPushDependencyOrderThenByMutationSeqAndId`, whose fixture is seeded in
   an order that is deliberately not the expected push order.
3. *Adoption resets non-`SYNCED` rows including `FAILED_POISONED`, clears error context, enqueues.*
   `adoptionResetsEveryNonSyncedRowIncludingPoisonedAndLeavesSyncedRowsUnenqueued`.
4. *`seq` values are monotonic and the first adoption row receives `(max pre-existing seq) + 1`.*
   `adoptionSeqValuesAreMonotonicAndStartAtOneAfterTheHighestPreExistingSeq`.
5. *The operation is idempotent: running it twice enqueues each row once.*
   `runningAdoptionTwiceEnqueuesEachRowExactlyOnce`, which also asserts that the second run does not
   bump `localRevision` again.
6. *A test starts from a populated `LOCAL_OWNER` database with vehicles and fuel entries, including
   interleaved edits, and asserts nothing is lost and the outbox order is deterministic.*
   `adoptionOfAPopulatedLocalOwnerDatabaseWithInterleavedEditsLosesNothing`. It is the required sync
   test 14 of `docs/TECHNICAL_PLAN.md`. "Everything syncs" is asserted as far as this story reaches:
   every row is enqueued exactly once, in the contract order, with the adopting UID in its payload.
   The push itself belongs to `E3-03`, which owns the sync engine.
7. *Adoption is triggered automatically when connectivity returns, not only from a UI action.*
   Seven tests after the owner's first review round:
   `connectivityReturningAcquiresAnAnonymousUidWhenLocalOwnerDataIsWaiting`,
   `connectivityReturningCreatesNoAccountWithoutAnExplicitLocalStartAndWithoutRows`,
   `anExplicitLocalStartRetriesAcquisitionOnceWhenConnectivityReturnsBeforeAnyRowExists`,
   `localOwnerRowsAreDurableEvidenceOfALocalSessionAfterARestartLosesTheSignal`,
   `anAlreadyAuthenticatedSessionNeverRepeatsTheAnonymousAcquisition`,
   `concurrentTriggersDoNotCreateDuplicateAcquisitionAttempts`, and
   `theFirstLocalOwnerWriteWhileOnlineTriggersAcquisitionAfterAMissedConnectivityEmission`. End to
   end through `DefaultAppGraph`: `authenticationAdoptsTheWaitingRowsAndTheListNeverResolvesEmpty`
   and `anExplicitLocalStartIsRememberedSoReturningConnectivityRetriesWithNoRows`, where the only
   actions taken are the authentication and the "continue without an account" choice itself.

Beyond the story's own criteria, the owner's review added the `D-125` failure semantics, covered by
`LocalOwnerAdoptionFailureTest`: a failed adoption is a typed
`PersistenceError.TransactionFailed` on the read path and the write path, the caller is not
cancelled, the list stays unknown with a message rather than resolving empty, `refresh()` recovers
it by running the gate again, and a failing owner observer leaves the connectivity trigger working.

All twenty-three tests run on both the JVM and `iosSimulatorArm64`: seven in `:core:database`
`LocalOwnerAdoptionTest`, eleven in `:shared` `LocalOwnerAdoptionTest` and five in `:shared`
`LocalOwnerAdoptionFailureTest`.

## Out of Scope / Not Done

- **Pushing the enqueued snapshots.** Adoption fills the outbox; `E3-03` owns the sync cycle that
  drains it and `syncController()` is still staged by `D-88`.
- **Gating the Fuel Entry repository.** `D-125` gates the Vehicle side only, because a fuel-entry
  read is reached through a vehicle and therefore cannot precede a resolved vehicle list.
- **Automatic retry of a repeatedly failing adoption.** The failure is now typed, visible and
  retryable by the owner, but nothing retries it on a schedule and it is not part of `SyncStatus`.
  That half is an explicit acceptance criterion of `E3-03` in `docs/BACKLOG.md`, not a handoff note.
- **`E1-14` and `E1-15`**, the two Phase 1 defects filed on 2026-09-06. Neither blocks this story.
- **Manual acceptance on a real device.** Not required by this story's criteria; the behaviour is
  covered by executable tests on both targets.

## Files Changed

Product code:

- `core/database/src/commonMain/sqldelight/.../database.sq` — `selectVehiclesForAdoption`,
  `selectFuelEntriesForAdoption`, `adoptVehicleRow`, `adoptFuelEntryRow`, `countRowsOwnedBy`.
- `core/database/src/commonMain/kotlin/.../DatabaseMutations.kt` — `adoptLocalOwner` and its two
  private enqueue helpers.
- `feature/vehicle/src/commonMain/kotlin/.../data/VehicleOutboxMapper.kt` and
  `feature/fuel/src/commonMain/kotlin/.../data/FuelEntryOutboxMapper.kt` — one public
  `toAdoptionOutboxPayload()` each.
- `shared/src/commonMain/kotlin/.../LocalOwnerAdoption.kt` (new) — the gate, the two signals and the
  triggers.
- `shared/src/commonMain/kotlin/.../AdoptionGatedVehicleRepository.kt` (new) — the `D-125` decorator
  and its typed-error paths.
- `shared/src/commonMain/kotlin/.../StateHolders.kt` — `SessionStateHolder` records the explicit
  local start on its internal constructor callback, which stays out of the exported surface.
- `shared/src/commonMain/kotlin/.../AppGraph.kt` and `.../VehicleSliceRuntime.kt` — composition.

Tests:

- `core/database/src/commonTest/kotlin/.../LocalOwnerAdoptionTest.kt` (new, seven tests).
- `shared/src/commonTest/kotlin/.../LocalOwnerAdoptionTest.kt` (new, eleven tests).
- `shared/src/commonTest/kotlin/.../LocalOwnerAdoptionFailureTest.kt` (new, five tests) — the
  `D-125` failure semantics.
- `core/database/src/commonTest/kotlin/.../SchemaV1Test.kt` — `stringList` widened to `internal` so
  the new test file can reuse it.

Documentation:

- `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`,
  `docs/adr/README.md` — `D-123`, `D-124`, `D-125`.
- `docs/adr/0124-*.md`, `docs/adr/0125-*.md`, `docs/adr/0126-*.md` (new; `0125` and `0126` revised
  in the owner's first review round).
- `docs/CONTRACTS.md` §11.2 and §11.4.
- `docs/BACKLOG.md` `E3-03` — the deferred automatic retry is an acceptance criterion there.
- `AGENTS.md`, `README.md`, `docs/DEFINITION.md`, `docs/BACKLOG.md`, `docs/handoff-E2-03.md` — the
  `E2-03` merge realignment.
- `docs/PROJECT_LOG.md`, `docs/handoff-E2-06.md`.

## Decisions Made

- **`D-123` — Local owner adoption transaction ownership.** The whole `§11.4` operation is one
  `DatabaseMutations` transaction, and the outbox payload is injected as a pure function over the
  module's own row types. `ADR-0124`.
- **`D-124` — Automatic anonymous retry gate.** The `§11.2` retry runs for the sentinel owner, a
  `SignedOut` auth state, no acquisition in flight, and either the explicit "continue without an
  account" choice remembered for the life of the process or rows still owned by the sentinel. It is
  triggered by returning connectivity and by a write committing under the sentinel. `ADR-0125`.
- **`D-125` — Adoption read gate.** Vehicle reads and writes wait until adoption has run for the
  current owner, and a failed adoption is a typed error rather than a silent wait. `ADR-0126`.

None of the three is an owner decision reserved by `AGENTS.md` §`Owner Decisions`. All three were
reviewed by the owner on pull request #55 on 2026-09-06: `D-123` was approved unchanged; `D-124` was
rejected in its first form and revised in place, because it has never been merged and
`AGENTS.md` requires a superseding decision only for one that has; `D-125` was approved with the
failure semantics added.

Deviations from a `SHOULD`, and other things worth stating:

- **Two RED tests were restated during GREEN.** The RED phase expressed the read gate as an
  `isSettled: StateFlow<Boolean>`. That shape cannot close the gate synchronously with the
  authentication change, which is the exact race the gate exists to prevent, so it was replaced by
  the suspending `awaitAdoption()` and the two tests were restated against it. The behaviour they
  pin is unchanged and is now also covered end to end through `DefaultAppGraph`. This is recorded
  here rather than left in the commit history because it is the one place where the RED artifact
  does not match the shipped API.
- **Four of the eleven RED tests passed against the empty stub.** Each is the complementary half of
  a pair — "does not create an account when there is nothing to adopt" against "creates one when
  there is" — and no constant implementation satisfies both halves. The pairs are deliberate; the
  individual trivial pass is not evidence on its own.
- **One test was added after GREEN, not before it.** The owner's list required that a failure in one
  automatic observer not disable the other "and vice versa". The first round of tests covered a
  failing owner observer leaving the connectivity trigger working; the reverse direction was added
  once the supervisor made both directions symmetric by construction, so it passed on the first run.
  It is coverage of an implemented property rather than a specification of a missing one, and it is
  recorded here rather than presented as test-first.
- **The `E1-14` flake was not observed** on any run of this story, but it is in
  `FuelEntryStateHolderTest`, which this branch does not touch. A red `shared-tests` job on CI
  should be checked against `E1-14` before being treated as a regression.
- **The first round claimed the Swift-facing surface was unchanged, and that was wrong.** Both
  `toAdoptionOutboxPayload` mappers were public in modules `:composition:ios` exports, so they
  entered the committed Objective-C golden header. `objc-header-golden-check` caught it on the first
  push; `contractCheck` assertion 7 did not, because it checks the committed golden rather than
  regenerating the header. Both mappers are now `@HiddenFromObjC`. The lesson worth keeping is that
  a claim about the exported surface has to be verified by regenerating the header locally, which is
  now part of `Verification Run`.

## Verification Run

- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
  koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest
  iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test
  -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test
  -x :composition:ios:iosSimulatorArm64Test` — **BUILD SUCCESSFUL, 636 actionable tasks.** This is
  the exact command in `AGENTS.md` §`Build and verify`.
- `./gradlew -Pcarapp.excludeFirebaseProviders=true testAndroidHostTest iosSimulatorArm64Test` —
  **BUILD SUCCESSFUL, 234 actionable tasks.** The `D-45` forced provider decoupling route.
- `ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:connectedDebugAndroidTest` on the `D-84`
  `E1_07_API_36` AVD, booted with `-wipe-data` and API level confirmed as 36 — **14 of 14 tests
  passed**, `BUILD SUCCESSFUL`, 214 actionable tasks with 38 executed, so the suite ran rather than
  reporting `UP-TO-DATE`. Re-run unchanged after the owner review round, with the Compose Vehicle
  and Fuel Entry flows now reading through the `D-125` gate and its typed-error paths.
- `./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64` followed by the CI job's own
  `diff -u` between `shared/build/generated/objc-header/Shared.h.golden` and the regenerated
  `Shared.h` — **identical.** This step exists because the first round's claim about the exported
  surface was made without it and was wrong.
- `contractCheck` reports 17 assertions passing and no `PENDING`, over 126 decisions and 126 ADRs.
- New tests, confirmed executed on both targets from the JUnit XML rather than from the task result:
  `core:database` `LocalOwnerAdoptionTest` 7 tests, `shared` `LocalOwnerAdoptionTest` 11 tests and
  `shared` `LocalOwnerAdoptionFailureTest` 5 tests, 0 failures each on `iosSimulatorArm64Test`. Same
  counts on the JVM.
- RED evidence, first round: with the behaviour stubbed out, `:core:database` reported `7 tests
  completed, 7 failed` and `:shared` reported four failures.
- RED evidence, owner review round: `56 tests completed, 8 failed` in `:shared`, the eight being
  exactly the new behaviours — the explicit local start and its retry, the durable-rows case, the
  first-write trigger, the concurrency guard, the typed error on the read and write paths, the
  unreadable-and-retryable list, and observer independence. Every failure was on compiled, executing
  code, and the run completed in 51 seconds because the awaiting tests carry a bounded timeout.

## Contract Impact

Updated `docs/CONTRACTS.md`:

- **§11.2** — the background anonymous retry states which device it runs for: the explicit
  "continue without an account" choice while the process lives, or rows under the sentinel after a
  restart; a device with neither MUST NOT be given an account. It also states the second trigger, a
  write committing under the sentinel, and that concurrent triggers produce at most one attempt
  (`D-124`).
- **§11.4** — local reads for a newly authenticated owner MUST NOT resolve until adoption has run,
  and the gate is a no-op for the sentinel. A failed adoption MUST be a typed `AppError`, never
  thrown across the gate and never an indefinite wait; cancellation MUST stay distinguishable from
  failure; and the automatic triggers MUST NOT be able to cancel one another (`D-125`).

No type, signature or schema in `§20` changed. The exported Swift-facing surface is unchanged, and
that is now verified by regenerating the Objective-C header and diffing it against the committed
golden, not by assertion 7 of `contractCheck` alone.

## Decision Board Impact

Updated `docs/DECISION_BOARD.md` with `D-123`, `D-124` and `D-125`, each mirrored in
`docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`, and each with its
own ADR: `0124-own-the-adoption-transaction-in-core-database.md`,
`0125-gate-anonymous-retry-on-adoptable-data.md`,
`0126-hold-vehicle-reads-until-adoption-has-run.md`.

## Shared-Write Modules Touched

`:core:database` may be modified by only one story at a time.

- `:core:database`. This story declares it. No other story is in flight.

## Project Log Entry

Appending an entry to `docs/PROJECT_LOG.md` is part of the Definition of Done.

- [x] Entry appended

## Risks or Follow-ups

- **A repeatedly failing adoption is visible and retryable, but not retried automatically.** `D-125`
  chose an unreadable list over an empty one, which would open mandatory first-run creation, and the
  owner can retry it. Nothing retries it on a schedule and it is not part of `SyncStatus`; that is
  now an acceptance criterion of `E3-03` rather than a note here.
- **The `(max pre-existing seq) + 1` criterion relies on `AUTOINCREMENT`.** It holds while no outbox
  row is ever deleted before adoption. Under the contract the outbox is empty at adoption time, so
  the premise holds today, but a future flow that deletes outbox rows and then adopts would see the
  first adoption row land above `max + 1`. Worth revisiting when `E2-04` adds the conversion flow.
- **`E1-14`** makes a red `shared-tests` job ambiguous until it is fixed. **`E1-15`** is unblocked
  now that `E2-03` has merged.
- **The gate adds one indexed count per Vehicle observation.** Measured cost is a single-row
  aggregate over two indexed columns; if it ever shows up, the natural fix is to cache the
  "nothing waiting" answer per owner rather than to remove the gate.

## Human Review Gate

Applies, on three counts:

- `E2-06` is a gated story in the `AGENTS.md` canonical gate list.
- Gated paths touched: `core/database/**`, `docs/CONTRACTS.md`, `docs/SPECIFICATION.md`,
  `docs/DECISION_BOARD.md` and `docs/adr/**`.
- Gated topic: the synchronization algorithm's outbox ordering and the local-to-remote data
  boundary.

The agent does not merge the pull request.
