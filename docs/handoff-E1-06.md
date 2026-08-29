# Agent Handoff - E1-06

## Story

`E1-06 - Fuel Entry Data, Local Only - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — implement the local Fuel Entry data source, mappers, projections
  and complete `FuelEntryRepository` implementation.
- [x] Acceptance criteria reviewed — both deterministic orderings; chronological orphan-free list
  projection; partial-row explanation with segment contribution; dedicated consumption projection;
  D-79 production filtering; `PENDING` local writes; no `LOCAL_OWNER` outbox; fresh shared mutation
  sequences; logical-delete recomputation; and mapper round trips.
- [x] Dependencies checked — E1-01, E1-03, E1-04 and E1-05 are complete. `:core:database` owns the
  schema and D-38 mutations; E1-04 owns the D-77 pure validators; E1-05 owns the pure calculator.
- [x] Decisions checked — D-1, D-17, D-18, D-19, D-28, D-36, D-38, D-55 and D-76 through D-83 are
  `Accepted`. D-81 records the owner's formal UTC calendar-year approval, and no `Proposed` or
  `Pending` decision blocks the story.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §3`, `§5.2`, `§6` R-1 through R-3,
  `§7` F-3, `§8.3`, `§9`, `§11`; `docs/CONTRACTS.md §2` through `§8`, `§12`, `§13`, `§15`,
  `§20.0`, `§20.0.1`, `§20.2`, `§20.3`, `§20.4`, `§20.5`, `§20.6`; ADR-0078 through ADR-0084;
  and `docs/TECHNICAL_PLAN.md §4`, `§6`, `§10`, `§12`.
- [x] Expected verification identified — focused `:core:common`, `:core:database` and
  `:feature:fuel` Android-host tests; `:core:common` and `:feature:fuel` iOS simulator tests;
  feature lint, detekt and Kover; architecture fixtures and checks; contract checks; the complete
  repository command from `AGENTS.md`; and `git diff --check`.
- [x] Human review gates identified before work — `core/database/**`, `docs/CONTRACTS.md` and
  `docs/adr/**` are gated paths. Owner review is required before merge; this agent MUST NOT merge.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Added owner-scoped SQLDelight queries for chronological list projection, calculation order,
  single-row reads and previous-active-entry validation facts. The list query excludes orphans
  through its Vehicle join and the calculation query is dedicated to consumption.
- Implemented the neutral `:core:database` Fuel Entry access boundary and D-38 local mutation
  operations, including shared sequencing, `PENDING` metadata, outbox coalescing and database-owned
  successor and Vehicle-odometer recomputation.
- Implemented bidirectional persistence mappers, canonical outbox snapshots, chronological list
  projections and the complete SQLDelight-backed `FuelEntryRepository`.
- Kept D-77 fact loading, pure validation and mutation in one transaction for create and update,
  including the D-81 UTC calendar-year fact and target exclusion from the update predecessor query.
- Added cross-platform repository, projection, filtering, mapping, transaction, error and D-81
  tests, plus the bounded calendar-type architecture rule and its failing fixture.
- Applied the second owner-review corrections: D-82 aligns persisted odometer inconsistency with
  both validation branches, D-83 retains the highest bounded projection windows, and list mapping
  indexes consumption segments once per emission instead of scanning every segment per row.

## Acceptance Evidence

- `SqlDelightFuelEntryLocalDataSourceTest` proves chronological `date, createdAt, id` ordering,
  calculation `odometerKm, date, id` ordering, orphan exclusion, deleted-row exclusion from the
  calculation projection and explicit list visibility for `includeDeleted`.
- `FuelEntryRepositoryObserveTest` proves chronological `FuelEntryListItem` projection. A partial
  row has null consumption and `EndEntryNotFullTank`, while its litres contribute to the next
  full-to-full segment through the canonical calculator.
- `FuelEntryRepositoryConsumptionFilterTest` proves the production repository calls the dedicated
  consumption source, never the UI-list source, and supplies `CalculateConsumption` only active
  rows for the requested Vehicle.
- Create, update and delete tests prove canonical persistence, `PENDING` state, monotonically fresh
  sequences shared after the seeded Vehicle, absence of a `LOCAL_OWNER` outbox and complete
  coalesced snapshots for permanent owners.
- Core database and repository delete tests prove the exact logical-delete recompute set: the
  pre-delete successor is recalculated and `vehicle.currentOdometerKm` is refreshed.
- Mapper tests prove database/local round trips and every domain field; the outbox test proves a
  complete canonical snapshot with no local sync metadata.
- Transaction-boundary tests prove Vehicle and predecessor facts are loaded before mutation in the
  same transaction and an unconfirmed R-1 warning mutates nothing.
- D-81 helper tests prove ordinary subtraction, time-of-day preservation, century leap-day
  clamping and Unix-epoch clamping on Android host and `iosSimulatorArm64`.
- D-82 tests prove a confirmed first entry below `initialOdometerKm` stores and enqueues
  `odometerInconsistent = true`; equality stays false; a missing Vehicle row stays orphan-safe; and
  all existing neighbour cases remain unchanged.
- D-83 tests inject a limit of three, prove both queries discard the lowest/oldest rows and prove
  the retained list and consumption windows still arrive in their canonical ascending orders. The
  internal data-source constructor defaults to `MAX_ENTRIES_IN_MEMORY`, and the production
  repository never supplies an override, so the production binding remains fixed at 5,000.
- Existing projection tests pass unchanged after replacing per-row segment scans with one
  `Map<EntityId, SegmentResult>` per list emission. The mapper still gives a partial row
  `EndEntryNotFullTank` precedence over an invalid segment, and a valid segment supplies
  consumption.

## Out of Scope / Not Done

- E1-07 and later presentation, authentication, adoption and synchronization stories.
- No schema or migration changed: E1-06 uses the existing schema-v1 tables and adds queries and
  mutation-facade behavior only.
- GitHub issue #36 records the pre-existing `VehicleOutboxMapper.toFuelEntryTombstonePayload`
  omission of the §8 `entityType` key. It is intentionally not fixed in this branch.

## Files Changed

- `core/common/**` — D-81 helper, accepted datetime dependency and cross-platform boundary tests.
- `core/database/**` — list/consumption/fact queries, neutral access types and transactional local
  Fuel Entry mutation operations.
- `feature/fuel/**` — local source, mappers, projections, repository implementation and behavior
  tests.
- `build-logic/convention/**` — executable D-81 calendar-type boundary, fixture and relative source
  paths for real-tree enforcement.
- D-81 through D-83 and ADR-0082 through ADR-0084 in all four decision mirrors, plus the resolved contract and current-state
  documents.
- `docs/handoff-E1-06.md` and `docs/PROJECT_LOG.md` — acceptance and completion records.

## Decisions Made

- D-81 records the already-approved UTC calendar-year calculation that was lost after E1-04.
- D-82 makes the database derivation match both validation branches. Restricting §5 to the
  neighbour branch was rejected because it would adapt the contract to the implementation and
  leave a confirmed warning with no persisted trace.
- D-83 keeps the highest complete canonical-order window under the existing cap and restores the
  ascending contract order outside the descending limited selection.
- The owner explicitly replaced the default separate-push cadence with one push after the RED,
  GREEN and REFACTOR commits. Commit phases remain separate and ordered.
- SQLDelight query definitions use the `docs/SPECIFICATION.md §11` schema exemption; every
  observable repository behavior remains covered by executing tests.
- The owner approved D-81 as implemented and made two implementation constraints intentional:
  `kotlinx-datetime` remains an implementation dependency of `:core:common`, and the architecture
  exception remains anchored to the helper's full package path rather than its file name alone.
- The owner-review extension of the calendar boundary to `:shared` found no production-source
  violations. The only existing `:shared` calendar references are in `commonTest`, which the
  architecture scanner deliberately excludes so fixtures may name forbidden types.
- The shared sequence assertions follow the pre-existing `LocalSequenceTest`: a fresh database
  first returns `2`, so a locally seeded Vehicle receives `2`, its first Fuel Entry receives `3`
  and the next Fuel Entry mutation receives `4`.
- The D-82 initial-odometer branch depends on a Vehicle column that no legal Fuel Entry write
  mutates. Because `initialOdometerKm` is editable only while no active Fuel Entry exists, the
  existing §3.1 recompute set needs no new trigger.
- The database-owned mutation boundary and outbox rules otherwise directly implement D-38 and the
  existing contracts.

## Verification Run

- Baseline `./gradlew architectureCheck contractCheck` — successful before RED changes.
- RED `./gradlew :core:common:testAndroidHostTest` and
  `./gradlew :core:common:iosSimulatorArm64Test` — failed as required on the three new D-81 helper
  tests on each target.
- RED `./gradlew :feature:fuel:testAndroidHostTest` — 29 new tests failed while all 61 prior tests
  passed; RED `./gradlew :feature:fuel:iosSimulatorArm64Test` — the same 29 new common tests failed
  while all 58 prior tests passed.
- RED `./gradlew :build-logic:convention:test` — the new calendar-boundary fixture failed before
  the rule existed.
- GREEN `./gradlew :core:common:testAndroidHostTest :build-logic:convention:test
  :core:database:testAndroidHostTest :feature:fuel:testAndroidHostTest` — successful.
- GREEN `./gradlew :core:common:iosSimulatorArm64Test :feature:fuel:iosSimulatorArm64Test` —
  successful with the RED behavior tests retained.
- REFACTOR focused lint, detekt, feature Kover, architecture fixtures, real-tree architecture and
  contract checks — successful; 16 architecture rules and 82 decision/ADR mirrors passed.
- Complete repository verification command — successful (`BUILD SUCCESSFUL` in 31 seconds; 602
  actionable tasks: 484 executed, 17 from cache and 101 up-to-date).
- `git diff --check` — passed with no whitespace errors.
- Owner-review RED fixture — failed because `:shared` was outside the calendar-boundary scope.
- Owner-review GREEN `:build-logic:convention:test` and real-tree `architectureCheck` — successful;
  extending the rule to `:shared` found no production-source violations.
- Owner-review complete repository verification command — successful (`BUILD SUCCESSFUL` in 38
  seconds; 602 actionable tasks: 540 executed, 19 from cache and 43 up-to-date).
- Second-review RED Android-host run — 95 Fuel Entry tests executed with exactly four expected
  failures: stored initial-odometer inconsistency, its outbox snapshot, the bounded list window and
  the bounded consumption window. The core-database orphan test passed.
- Second-review RED iOS run — 92 Fuel Entry tests executed with the same four expected failures;
  the core-database iOS suite passed.
- Second-review GREEN Android-host and iOS runs — successful with all new boundary, orphan,
  outbox, limited-window and existing neighbour tests retained. SQLDelight generation compiled the
  existing hand-written data-source call sites unchanged.
- Second-review focused quality run — successful; lint, detekt, all 16 architecture rules and all
  84 decision/ADR mirrors passed. `contractCheck` reported no pending assertions.
- Second-review refactor run — all 95 Android-host and 92 iOS Fuel Entry tests passed unchanged,
  together with feature lint and detekt.
- Second-review complete repository verification command — `BUILD SUCCESSFUL in 7s`; 602
  actionable tasks: 79 executed and 523 up-to-date.
- Second-review `:composition:ios:linkDebugFrameworkIosSimulatorArm64` — `BUILD SUCCESSFUL in 4s`;
  69 actionable tasks: 32 executed and 37 up-to-date.
- Second-review `git diff --check` — passed with no whitespace errors.

## Contract Impact

- Updated `docs/CONTRACTS.md §2` and §5 for the bounded D-81 calendar exception, §3.1 for the full
  D-82 derived predicate and orphan fallback, and §12 for the D-83 retained-window semantics.

## Decision Board Impact

- Added accepted D-81 through D-83 and ADR-0082 through ADR-0084.

## Shared-Write Modules Touched

- `:core:database` — E1-06 is the only story modifying it during this work.

## Project Log Entry

- [x] Entry appended.

## Risks or Follow-ups

- E1-07 is next and owns the Android Vehicle UI plus the D-28 feature package-boundary checks.
- The D-80 optimized real-iPhone consumption measurement remains explicitly owned by E4-03.
- GitHub issue #36 owns the pre-existing Vehicle cascade Fuel Entry tombstone payload omission.
- No residual E1-06 implementation risk is known after the focused and complete verification
  suites; owner review remains mandatory for the gated paths below.

## Human Review Gate

- Applies: `core/database/**`, `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md` and `docs/adr/**`. Owner review is required;
  the pull request must not be merged by this agent.
