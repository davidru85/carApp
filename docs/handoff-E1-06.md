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

- Pending RED/GREEN/REFACTOR evidence.

## Acceptance Evidence

- Pending RED/GREEN/REFACTOR evidence.

## Out of Scope / Not Done

- E1-07 and later presentation, authentication, adoption and synchronization stories.

## Files Changed

- Pending final inventory.

## Decisions Made

- D-81 records the already-approved UTC calendar-year calculation that was lost after E1-04.
- The owner explicitly replaced the default separate-push cadence with one push after the RED,
  GREEN and REFACTOR commits. Commit phases remain separate and ordered.
- SQLDelight query definitions use the `docs/SPECIFICATION.md §11` schema exemption; every
  observable repository behavior remains covered by executing tests.

## Verification Run

- Baseline `./gradlew architectureCheck contractCheck` — successful before RED changes.

## Contract Impact

- Updated `docs/CONTRACTS.md §2` and §5 for the bounded D-81 calendar exception.

## Decision Board Impact

- Added accepted D-81 and ADR-0082.

## Shared-Write Modules Touched

- `:core:database` — E1-06 is the only story modifying it during this work.

## Project Log Entry

- [ ] Entry appended.

## Risks or Follow-ups

- Pending final verification.

## Human Review Gate

- Applies: `core/database/**`, `docs/CONTRACTS.md` and `docs/adr/**`. Owner review is required;
  the pull request must not be merged by this agent.
