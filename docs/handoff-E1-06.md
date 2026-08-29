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
- [x] Decisions checked — D-1, D-17, D-18, D-19, D-28, D-36, D-38, D-55 and D-76 through D-81 are
  `Accepted`. D-81 records the owner's formal UTC calendar-year approval, and no `Proposed` or
  `Pending` decision blocks the story.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §3`, `§5.2`, `§6` R-1 through R-3,
  `§7` F-3, `§8.3`, `§9`, `§11`; `docs/CONTRACTS.md §2` through `§8`, `§12`, `§13`, `§15`,
  `§20.0`, `§20.0.1`, `§20.2`, `§20.3`, `§20.4`, `§20.5`, `§20.6`; ADR-0078 through ADR-0082;
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

## Out of Scope / Not Done

- E1-07 and later presentation, authentication, adoption and synchronization stories.
- No schema or migration changed: E1-06 uses the existing schema-v1 tables and adds queries and
  mutation-facade behavior only.

## Files Changed

- `core/common/**` — D-81 helper, accepted datetime dependency and cross-platform boundary tests.
- `core/database/**` — list/consumption/fact queries, neutral access types and transactional local
  Fuel Entry mutation operations.
- `feature/fuel/**` — local source, mappers, projections, repository implementation and behavior
  tests.
- `build-logic/convention/**` — executable D-81 calendar-type boundary, fixture and relative source
  paths for real-tree enforcement.
- D-81 and ADR-0082 in all four decision mirrors, plus the resolved contract and current-state
  documents.
- `docs/handoff-E1-06.md` and `docs/PROJECT_LOG.md` — acceptance and completion records.

## Decisions Made

- D-81 records the already-approved UTC calendar-year calculation that was lost after E1-04.
- The owner explicitly replaced the default separate-push cadence with one push after the RED,
  GREEN and REFACTOR commits. Commit phases remain separate and ordered.
- SQLDelight query definitions use the `docs/SPECIFICATION.md §11` schema exemption; every
  observable repository behavior remains covered by executing tests.
- The shared sequence assertions follow the pre-existing `LocalSequenceTest`: a fresh database
  first returns `2`, so a locally seeded Vehicle receives `2`, its first Fuel Entry receives `3`
  and the next Fuel Entry mutation receives `4`.
- No additional technical choice required a decision record; the database-owned mutation boundary,
  query orderings, projections and outbox rules directly implement D-38 and the existing contracts.

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

## Contract Impact

- Updated `docs/CONTRACTS.md §2` and §5 for the bounded D-81 calendar exception.

## Decision Board Impact

- Added accepted D-81 and ADR-0082.

## Shared-Write Modules Touched

- `:core:database` — E1-06 is the only story modifying it during this work.

## Project Log Entry

- [x] Entry appended.

## Risks or Follow-ups

- E1-07 is next and owns the Android Vehicle UI plus the D-28 feature package-boundary checks.
- The D-80 optimized real-iPhone consumption measurement remains explicitly owned by E4-03.
- No residual E1-06 implementation risk is known after the focused and complete verification
  suites; owner review remains mandatory for the gated paths below.

## Human Review Gate

- Applies: `core/database/**`, `docs/CONTRACTS.md` and `docs/adr/**`. Owner review is required;
  the pull request must not be merged by this agent.
