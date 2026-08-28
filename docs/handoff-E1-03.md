# Agent Handoff - E1-03

## Story

`E1-03 - Vehicle Data, Local Only - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — implement the local Vehicle data source, mappers and complete
  `VehicleRepository` implementation in `:feature:vehicle`.
- [x] Acceptance criteria reviewed — mapper round trips; `OwnerContext` stamping without
  `AuthClient`; `PENDING` create, update and tombstone writes; fresh shared
  `localMutationSeq` values; no `LOCAL_OWNER` outbox rows; transactional Vehicle and FuelEntry
  tombstones; and no Firebase or GitLive types.
- [x] Dependencies checked — E1-01, E0-07 and E1-02 are complete. E1-01 provides SQLDelight schema
  v1 and the D-38 mutation facade; E0-07 provides the removable runtime and final module topology;
  E1-02 provides the canonical repository contract and D-76 pure validators.
- [x] Decisions checked — D-1 selects SQLDelight; D-17 selects Turbine; D-18 requires 85% feature
  coverage; D-19 fixes `Outcome`; D-28 defers feature package-rule enforcement to E1-07; D-36
  fixes the database driver; D-38 fixes `DatabaseMutations` as the synchronized-entity write
  boundary; D-55 fixes staged ownership; and D-76 requires transactional fact loading, validation
  and mutation. Every applicable decision is `Accepted`, and no `Proposed` or `Pending` row blocks
  the story.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §3`, `§5.1`, `§6 R-4`, `§8.3`, `§9`,
  `§11`; `docs/CONTRACTS.md §3`, `§5`, `§6`, `§8`, `§12`, `§13`, `§20.2`, `§20.3.2`, `§20.4`,
  `§20.5`; `docs/DECISION_BOARD.md` D-1, D-17, D-18, D-19, D-28, D-36, D-38, D-55 and D-76;
  `docs/TECHNICAL_PLAN.md §4`, `§6`, `§10`, `§12`; and ADR-0077.
- [x] Expected verification identified — focused `:feature:vehicle` and `:core:database`
  Android-host tests; `:feature:vehicle` `iosSimulatorArm64` tests, lint, detekt and Kover;
  architecture fixtures and contract checks; the complete repository command from `AGENTS.md`;
  and `git diff --check`.
- [x] Human review gates identified before work — `core/database/**` is a gated path and requires
  owner review before merge. No gated normative-document change is expected.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Pending implementation.

## Acceptance Evidence

- RED phase: 57 Android-host tests compiled and executed; 26 focused E1-03 tests failed for absent
  mapper, observation, create, update, delete, error-boundary and transaction behavior, while all
  31 pre-existing tests passed.

## Out of Scope / Not Done

- E1-04 FuelEntry domain behavior and E1-06 FuelEntry persistence beyond the deletion cascade
  required by E1-03.
- E1-07 presentation and executable feature package-layer rules.
- E2-06 local-owner adoption and E3-03 outbox processing or remote synchronization.

## Files Changed

- Pending implementation.

## Decisions Made

- No implementation decision or TDD-order exemption recorded at intake.

## Verification Run

- `./gradlew :feature:vehicle:testAndroidHostTest` during RED — failed as required: 57 tests
  executed, 26 failed for deliberately absent E1-03 behavior and 31 pre-existing tests passed.

## Contract Impact

- No contract changes expected.

## Decision Board Impact

- No decision changes expected.

## Shared-Write Modules Touched

- `:core:database` — E1-03 is the only story modifying it during this work.

## Project Log Entry

- [ ] Entry appended.

## Risks or Follow-ups

- D-76 requires an executable test proving fact loading, validation and mutation share one local
  transaction.
- E1-03 must preserve the E0-07 walking-skeleton behavior until later sync stories replace its
  remote orchestration.

## Human Review Gate

- Applies: `core/database/**` is a gated path. Owner review is required before merge.
