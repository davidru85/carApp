# Agent Handoff

Fill in every section. This template is the canonical field list; `AGENTS.md` links here rather than restating it, and `.github/pull_request_template.md` is a superset of it.

## Story

`E3-07 - Tombstone Purge - S`

## Ready Check

- Backlog story: `E3-07 - Tombstone Purge - S` (`docs/BACKLOG.md` §`### E3-07`).
- Acceptance criteria reviewed: the four criteria of the backlog entry, verbatim: (1) a tombstone is
  purged only when `SYNCED`, older than 90 days by `serverUpdatedAt`, and with no outbox row; (2) purge
  runs at most once per app start, in one transaction; (3) a test proves a pending tombstone is never
  purged; (4) a fresh device pulling a tombstone for an entity it has never seen inserts it as a
  tombstone instead of failing.
- Dependencies checked: **none**. The story lists no `Depends on:` row in `docs/BACKLOG.md`, and the
  purge rule is already normative in `docs/CONTRACTS.md §8`.
- Decisions checked: **no open decision applies**. `D-149`, `D-150` and `D-173` gate `E3-15`, `E3-16`
  and `E3-18` only. The purge policy of `§8` is normative, so this story needs no new `D-` id and no
  ADR, and none is created.
- Normative sections reviewed: `docs/CONTRACTS.md §8` (tombstone purge, outbox, push dependency
  order), `§9.4` (tombstones arrive like any document), `§9.3` (ack deletes the outbox row and stamps
  `serverUpdatedAt`), `§7` (sync-state machine), `§3.1` (database-owned read-model invariants),
  `§20.0.1` (named constants), `§20.7` (`SyncController` surface), `§18` (contract-check assertions),
  `docs/SPECIFICATION.md §9` and `§3.2` (out-of-scope list), `docs/TECHNICAL_PLAN.md §3`, `§4` and
  `§9` (module rules, `:core:database` shared-write rule, required `:core:sync` tests).
- Expected verification: the complete non-instrumented command of `AGENTS.md` §Build and verify
  (`ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify
  :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest
  iosSimulatorArm64Test` with the four D-75 exclusions), `contractCheck --rerun-tasks` and
  `git diff --check`. No emulator or simulator is launched: the story is pure shared Kotlin.
- Human review gates identified before work: the story is not on the gated-story list, but
  `core/sync/**` and `core/database/**` are **gated paths** (`CODEOWNERS`), so the pull request
  requires the owner's review. The change touches no gated topic beyond those paths: no scope change,
  no stack or version change, no backend, no auth, no sync-algorithm or state-machine change, no money
  representation, no error-taxonomy, logging or Firestore-rule change, and no Swift-facing surface
  change.
- Rule 0 is acknowledged: chat replies for this story are in Spanish (es-ES) and every artifact it
  produces is in technical English.

## In-Progress Checkpoint

Update this section at every material state change and before yielding unfinished work (`D-105`).

- Date: 2026-09-26
- Branch and base: `story/E3-07-tombstone-purge`, branched from `origin/main` at `ea48ecc5`
  ("Merge pull request #77"). Work happens in a `git worktree` (`../carApp-e3-07`).
- Current phase and latest commit: complete and verified at `7fa3f019`. RED `07d6d1bc`, GREEN
  `21465a14`, `CONTRIBUTING.md` `68fc7f4a`, REFACTOR `e53ba49b`, documentation `7fa3f019`.
- Push and pull-request status: the branch is pushed to `origin` and pull request #78 is open against
  `main`, awaiting the ten required checks and the owner's gated review.
- Completed since the previous checkpoint: the ready check; the RED tests for the four criteria; the
  GREEN implementation in `:core:database`, `:core:sync` and `AppGraph.init`; the bounded
  `docs/CONTRIBUTING.md` branch-rewrite instruction; the `docs/BACKLOG.md`, `AGENTS.md` and
  `docs/PROJECT_LOG.md` updates; the complete verification command, `contractCheck --rerun-tasks` and
  `git diff --check` all green.
- Verification evidence and known failures: see "Verification Run". No known failure is outstanding.
- Open decisions or blockers: none. No new `D-` id and no ADR were needed.
- Exact next step: await the ten required checks on pull request #78 and the owner's review on the
  `core/sync/**` and `core/database/**` gated paths. Do not merge.

## Scope Completed

- The local 90-day tombstone purge of `docs/CONTRACTS.md §8`, all four acceptance criteria.
- The `docs/CONTRIBUTING.md` §Commit identity correction the story requires as step 4 of its plan.

## Acceptance Evidence

1. **Purged only when `SYNCED`, older than 90 days by `serverUpdatedAt`, and with no outbox row.**
   `TombstonePurgeDatabaseAccessTest`:
   `anOldConfirmedVehicleTombstoneIsPurged` and `anOldConfirmedFuelEntryTombstoneIsPurged` (purged);
   `aTombstoneYoungerThanTheCutoffIsKept` (age); `anOldConfirmedTombstoneWithAnOutboxRowIsKept`
   (outbox); `anActiveRowIsNeverPurgedEvenWhenItIsOldAndSynced` (`deleted = 1`).
   The boundary is pinned by `aTombstoneExactlyAtTheCutoffIsKeptBecauseItIsNotOlder`, which fixes the
   contract's **strict** reading: "older than 90 days" means `serverUpdatedAt < cutoff`, so exactly 90
   days is kept. `aConfirmedTombstoneWithoutAServerTimestampIsKept` pins the `IS NOT NULL` guard,
   because age cannot be established from a missing timestamp.
2. **At most once per app start, in one transaction.**
   `TombstonePurgeTest.purgeConfirmedTombstonesPurgesAnOldConfirmedTombstoneAndRunsOnlyOncePerAppStart`
   (the second call in one app start deletes nothing) and
   `TombstonePurgeTest.purgeConfirmedTombstonesDerivesTheCutoffFromTheInjectedClock` (the cutoff is
   the injected clock minus 90 days). `TombstonePurgeAppGraphTest` proves the same through the product
   surface: `theGraphPurgesAConfirmedTombstoneAtStartup` and
   `theGraphDoesNotPurgeATombstoneThatBecomesPurgableLaterInTheSameAppStart`.
   The transaction is `TombstonePurgeDatabaseAccessTest.aFailedPurgeRollsBackEveryDeletion`: a
   `BEFORE DELETE` trigger aborts the fuel-entry statement, and the vehicle deletion performed by the
   first statement is rolled back with it.
3. **A pending tombstone is never purged.** `TombstonePurgeDatabaseAccessTest.aPendingTombstoneIsNeverPurged`
   seeds all four non-`SYNCED` states (`PENDING`, `SYNCING`, `FAILED_RETRYABLE`, `FAILED_POISONED`)
   with no outbox row, so the guard it exercises is the `syncState` one rather than the outbox one.
4. **A fresh device pulling a tombstone for an unseen entity inserts a tombstone.** `TombstonePurgeTest`
   `aPulledTombstoneForAnUnknownVehicleBecomesALocalTombstone` and
   `aPulledTombstoneForAnUnknownFuelEntryBecomesALocalTombstone`: the pull cycle returns `Ok`, the row
   is inserted with `deleted = 1`, `deletedAt` set and `syncState = 'SYNCED'`, and the vehicle is
   absent from the UI read model.

Criterion 3's guard proven rather than asserted: removing the `AND syncState = 'SYNCED'` clause from
both purge statements failed exactly `aPendingTombstoneIsNeverPurged` (1 of 9 tests) and nothing else;
the clause was restored and the suite is green again.

## Out of Scope / Not Done

- No schema change, therefore no `.sqm` migration and no `AppGraph.Schema` version bump. Purging is a
  `DELETE`, and the required lookup is served by the existing primary keys and the `UNIQUE(entityType,
  entityId)` index on `outbox`; no index was added because adding one would have forced a version bump,
  a migration and a populated previous-version migration test for no measured benefit at MVP scale.
- No remote purge. `docs/CONTRACTS.md §8` states remote tombstones are never purged in the MVP.
- `E3-07` remains in the `AGENTS.md` "Remaining Phase 3" list rather than being removed from it. See
  "Decisions Made".

## Files Changed

- `core/database/src/commonMain/sqldelight/.../database.sq` — `purgeConfirmedVehicleTombstones` and
  `purgeConfirmedFuelEntryTombstones`.
- `core/database/src/commonMain/.../SyncDatabaseAccess.kt` — `purgeConfirmedTombstones(cutoff)`, one
  transaction over both statements.
- `core/database/src/commonTest/.../TombstonePurgeDatabaseAccessTest.kt` — criteria 1 and 2 at the
  statement layer.
- `core/sync/src/commonMain/.../TombstonePurge.kt` — the cutoff from the injected `AppClock` and the
  once-per-app-start latch.
- `core/sync/src/commonTest/.../TombstonePurgeTest.kt` — criterion 2 at the policy layer and criterion
  4 through the pull cycle.
- `shared/src/commonMain/.../AppGraph.kt` — the `TombstonePurge` instance and its one `init`
  invocation.
- `shared/src/commonTest/.../TombstonePurgeAppGraphTest.kt` — criterion 2 through the graph.
- `docs/CONTRIBUTING.md` — the bounded branch-rewrite instruction.
- `docs/BACKLOG.md`, `AGENTS.md`, `docs/PROJECT_LOG.md`, `docs/handoff-E3-07.md` — story record.

## Decisions Made

- **No new decision ID and no ADR.** `docs/CONTRACTS.md §8` already states the purge condition, its
  frequency and its module; the story implements it, so there is no option the owner must choose
  between (`AGENTS.md` §Owner Decisions).
- **The once-per-app-start latch lives in `:core:sync`.** The contract names `:core:sync` as the
  owner, and the invariant is a property of the app start rather than of one call site, so the guard
  is not left to the caller. It is a `Mutex` plus a `Boolean`, and the flag is set only after the
  transaction succeeded: a purge that failed deleted nothing, so it must not consume the app start's
  one attempt.
- **The boundary reading of "older than 90 days" is strict.** `serverUpdatedAt < cutoff`, so a
  tombstone exactly 90 days old is kept. Pinned by a test and stated here because `§8` does not spell
  out the comparison.
- **A purge failure is reported as a non-fatal crash and does not abort start-up.** Reclaiming rows is
  not worth taking the rest of `init` down for, and the unset latch means the next start retries.
- **`AGENTS.md` §Repository State: `E3-07` was kept in the "Remaining Phase 3" list, with its status
  recorded beside it, instead of being removed from the list.** The task prompt said to remove it; the
  repository's own precedent disagrees. `git log -p --follow -- AGENTS.md` shows the E3-05 commit
  `9ff87096` ("close the record now that the pull request merged") *removed* E3-05 from that list, and
  the earlier commit `f336b54d`, made while E3-05's pull request was still open, left it in the list.
  `AGENTS.md` §Repository State is explicit that it "describes what exists right now" and is "updated
  by the story that changes it", so a merged state is what removes a story from "Remaining". E3-07 is
  not merged, so removing it would have made a false claim about the repository. This is an explicit
  deviation from the prompt, not from the repository's rules, and it is recorded here as required.
  `docs/BACKLOG.md` uses the merged-pending wording "implemented ... with its pull request open,
  awaiting the owner's gated review", matching the `E3-05` precedent while it was in flight.
- **TDD order held with no exemption.** RED `07d6d1bc`, GREEN `21465a14`, separate commits and pushes;
  no refactoring phase was needed, so none was created.

## Verification Run

- **RED** (`07d6d1bc`), tests compiled and failed behaviourally, never at the compiler:
  - `:core:database:testAndroidHostTest --tests '*TombstonePurgeDatabaseAccessTest*'` — 9 tests, 3
    failed: `anOldConfirmedVehicleTombstoneIsPurged` (`expected null, but was:<Vehicle(id=vehicle-1…)`),
    `anOldConfirmedFuelEntryTombstoneIsPurged` (`the fuel entry table is purged too expected null, but
    was:<Fuel_entry(id=entry-1…)`), `aFailedPurgeRollsBackEveryDeletion` (`Expected an exception to be
    thrown, but was completed successfully.`).
  - `:core:sync:testAndroidHostTest --tests '*TombstonePurgeTest*'` — 4 tests, 2 failed:
    `purgeConfirmedTombstonesPurgesAnOldConfirmedTombstoneAndRunsOnlyOncePerAppStart` (`the first call
    purges the confirmed tombstone expected null, but was:<Vehicle(id=vehicle-1…)`) and
    `purgeConfirmedTombstonesDerivesTheCutoffFromTheInjectedClock` (`the same row becomes purgable only
    because the injected clock moved forward expected null, but was:<Vehicle(id=vehicle-1…)`). The two
    criterion-4 pull tests passed at RED: `applyRemoteVehicle` already derived `deleted` from
    `deletedAt`, which is exactly the "prove it with a test, fix only if it fails" outcome the story
    anticipated.
  - `:shared:testAndroidHostTest --tests '*TombstonePurgeAppGraphTest*'` — 2 tests, 2 failed:
    `Timed out after 30s waiting for the graph's startup purge to delete the confirmed tombstone` and
    `Timed out after 30s waiting for the startup purge to delete the confirmed tombstone`.
- **GREEN** (`21465a14`): all 15 tests above pass. Removing the `syncState = 'SYNCED'` clause locally
  failed only `aPendingTombstoneIsNeverPurged` and nothing else; restored, the suite is green.
- **Affected module suites, forced re-run**: `./gradlew :shared:testAndroidHostTest
  :core:database:testAndroidHostTest :core:sync:testAndroidHostTest --rerun-tasks` →
  `BUILD SUCCESSFUL`; 414 tests, 0 failures, 0 errors across the three modules' result XML.
- **Complete non-instrumented command** (`AGENTS.md` §Build and verify, with the four D-75 `-x`
  exclusions): `BUILD SUCCESSFUL in 38s`, 642 actionable tasks.
- **`contractCheck --rerun-tasks`**: every assertion `PASS` — no `FAIL`, no `PENDING`. The live output
  includes assertion 7 (`Swift allowlist complete; forbidden Kotlin construction types absent`) and
  assertion 36, both unchanged by this story.
- **`git diff --check`**: exits 0.
- No emulator and no simulator was launched: the story is pure shared Kotlin, so
  `adb devices` and `xcrun simctl list devices booted` were not touched and there is nothing to clean
  up. The Gradle `iosSimulatorArm64Test` task is a host task and boots no device.

## Contract Impact

- No contract changes. `docs/CONTRACTS.md §8` already carries the purge rule; the story implements it
  and adds no public repository or use case contract.

## Decision Board Impact

- No decision changes. No `D-` id was introduced, no ADR was added, and `D-191`/`D-192`/`D-193` are
  untouched.

## Shared-Write Modules Touched

`:core:database` may be modified by only one story at a time.

- `:core:database` — modified. `core/database/.story-lock` does not exist, so no other in-flight story
  held the module. The change is additive at the statement layer and no schema version changed.

## Project Log Entry

Appending an entry to `docs/PROJECT_LOG.md` is part of the Definition of Done.

- [x] Entry appended

## Risks or Follow-ups

- The purge has no index of its own. At MVP scale the scan is over `vehicle` and `fuel_entry` by
  primary key with a correlated `NOT EXISTS` on the `outbox` unique index, and the statement runs once
  per app start. If the row counts in `docs/SPECIFICATION.md §11` ever grow by orders of magnitude,
  the follow-up is an index plus the migration and previous-version test a schema change requires.
- `TOMBSTONE_PURGE_AGE_MS` is private to `:core:sync` rather than a `§20.0.1` named constant, because
  nothing outside the purge policy reads it. If a second consumer appears, it moves to `:core:common`
  so the value has one source.
- No emulator or simulator was left running.

## Human Review Gate

Applies. The story is not gated by name, but `core/sync/**` and `core/database/**` are **gated paths**
in `AGENTS.md` §Human Review Gates, enforced by `CODEOWNERS` plus required review, so the pull request
MUST NOT be merged on agent judgement alone. The change touches no gated topic.
