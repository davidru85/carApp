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

### REFACTOR complete, verification green, pull request pending (2026-09-06)

- Date: 2026-09-06. Branch and base: `story/E2-06-local-owner-adoption`, based on `main` at
  `fe9ed55` (the `E2-03` merge commit).
- Current phase and latest commit: REFACTOR, in the commit that contains this text. The three phase
  commits are `53491bd` (intake and status realignment), `2104ce1` (RED) and `4d44074` (GREEN).
- Push and pull-request status: **not pushed yet; no pull request exists.** The next step is one
  push of the whole branch followed by pull request creation, which is the cadence the owner asked
  for. The agent does **not** merge: `E2-06` is a gated story and `core/database/**`,
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
  No known failure. The `D-84` API 36 instrumented suite is the one check still to run locally.
- Open decisions or blockers: none blocking. Three decisions were taken by the agent and are
  recorded as `Accepted` with their ADRs; they are the ones to review first, and each ADR carries
  the alternatives that were rejected.
- Exact next step: run `:androidApp:connectedDebugAndroidTest` on the `E1_07_API_36` emulator, then
  push the branch and open the pull request against `main`.

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
   `connectivityReturningAcquiresAnAnonymousUidWhenLocalOwnerDataIsWaiting`,
   `connectivityReturningDoesNotCreateAnAccountWhenThereIsNothingToAdopt`,
   `anAlreadyAuthenticatedSessionNeverRepeatsTheAnonymousAcquisition`, and end to end through
   `DefaultAppGraph` in `authenticationAdoptsTheWaitingRowsAndTheListNeverResolvesEmpty`, where the
   only action taken is the authentication itself.

All thirteen tests run on both the JVM and `iosSimulatorArm64`.

## Out of Scope / Not Done

- **Pushing the enqueued snapshots.** Adoption fills the outbox; `E3-03` owns the sync cycle that
  drains it and `syncController()` is still staged by `D-88`.
- **Gating the Fuel Entry repository.** `D-125` gates the Vehicle side only, because a fuel-entry
  read is reached through a vehicle and therefore cannot precede a resolved vehicle list.
- **Surfacing a repeatedly failing adoption.** The gate leaves the list unknown rather than empty,
  which is the safer failure, but there is no user-facing recovery for it yet. Recorded under
  `Risks or Follow-ups` and belonging to `E3-03`, which owns sync failure surfacing.
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
- `shared/src/commonMain/kotlin/.../LocalOwnerAdoption.kt` (new) — the gate and the two triggers.
- `shared/src/commonMain/kotlin/.../AdoptionGatedVehicleRepository.kt` (new) — the `D-125` decorator.
- `shared/src/commonMain/kotlin/.../AppGraph.kt` and `.../VehicleSliceRuntime.kt` — composition.

Tests:

- `core/database/src/commonTest/kotlin/.../LocalOwnerAdoptionTest.kt` (new, seven tests).
- `shared/src/commonTest/kotlin/.../LocalOwnerAdoptionTest.kt` (new, six tests).
- `core/database/src/commonTest/kotlin/.../SchemaV1Test.kt` — `stringList` widened to `internal` so
  the new test file can reuse it.

Documentation:

- `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`,
  `docs/adr/README.md` — `D-123`, `D-124`, `D-125`.
- `docs/adr/0124-*.md`, `docs/adr/0125-*.md`, `docs/adr/0126-*.md` (new).
- `docs/CONTRACTS.md` §11.2 and §11.4.
- `AGENTS.md`, `README.md`, `docs/DEFINITION.md`, `docs/BACKLOG.md`, `docs/handoff-E2-03.md` — the
  `E2-03` merge realignment.
- `docs/PROJECT_LOG.md`, `docs/handoff-E2-06.md`.

## Decisions Made

- **`D-123` — Local owner adoption transaction ownership.** The whole `§11.4` operation is one
  `DatabaseMutations` transaction, and the outbox payload is injected as a pure function over the
  module's own row types. `ADR-0124`.
- **`D-124` — Automatic anonymous retry gate.** The `§11.2` retry runs only for the sentinel owner,
  a `SignedOut` auth state and at least one row still owned by the sentinel. `ADR-0125`.
- **`D-125` — Adoption read gate.** Vehicle reads and writes wait until adoption has run for the
  current owner. `ADR-0126`.

None of the three is an owner decision reserved by `AGENTS.md` §`Owner Decisions`. Each is recorded
as `Accepted` with the rejected alternatives in its ADR, so the owner can reverse any of them in
review, which is how `D-115` to `D-122` were settled.

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
- **The `E1-14` flake was not observed** on any run of this story, but it is in
  `FuelEntryStateHolderTest`, which this branch does not touch. A red `shared-tests` job on CI
  should be checked against `E1-14` before being treated as a regression.

## Verification Run

- `./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test
  koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest
  iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test
  -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test
  -x :composition:ios:iosSimulatorArm64Test` — **BUILD SUCCESSFUL, 636 actionable tasks.** This is
  the exact command in `AGENTS.md` §`Build and verify`.
- `./gradlew -Pcarapp.excludeFirebaseProviders=true testAndroidHostTest iosSimulatorArm64Test` —
  **BUILD SUCCESSFUL, 234 actionable tasks.** The `D-45` forced provider decoupling route.
- `contractCheck` reports 17 assertions passing and no `PENDING`, over 126 decisions and 126 ADRs.
- New tests, confirmed executed on both targets from the JUnit XML: `core:database`
  `LocalOwnerAdoptionTest` 7 tests, 0 failures on `iosSimulatorArm64Test`; `shared`
  `LocalOwnerAdoptionTest` 6 tests, 0 failures on `iosSimulatorArm64Test`. Same counts on the JVM.
- RED evidence: with the behaviour stubbed out, `:core:database` reported `7 tests completed, 7
  failed` and `:shared` reported the four failures described under `Decisions Made`. Every failure
  was an assertion failure on compiled, executing code, not a compilation or setup error.

## Contract Impact

Updated `docs/CONTRACTS.md`:

- **§11.2** — the background anonymous retry now states its three conditions (`D-124`).
- **§11.4** — local reads for a newly authenticated owner MUST NOT resolve until adoption has run,
  and the gate is a no-op for the sentinel (`D-125`).

No type, signature or schema in `§20` changed, and the exported Swift-facing surface is unchanged:
`objc-header-golden-check` passes against the unmodified committed golden.

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

- **A repeatedly failing adoption leaves the vehicle list unknown.** `D-125` chose that over an
  empty list, which would open mandatory first-run creation. There is no user-facing recovery for
  the stuck case yet. Owner: `E3-03`, which owns sync failure surfacing.
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
