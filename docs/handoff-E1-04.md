# Agent Handoff - E1-04

## Story

`E1-04 - Fuel Entry Domain - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — implement the `:feature:fuel` domain package, canonical command
  and repository contracts, pure create/update validators and rules R-1 and R-2.
- [x] Acceptance criteria reviewed — `MoneyInput` is the sole supplied-pair representation; all
  three derivations and the largest `Long` intermediate; canonical-triple-only persistence;
  warning then confirmation with no first-attempt mutation; create/update R-1 parity; every closed
  §5 bound; unsupported and supported currencies; and Android/JVM plus iOS/native currency facts.
- [x] Dependencies checked — E1-01, E0-07, E1-02 and E1-03 are complete. `:core:model` already
  owns the canonical money types and exact arithmetic; `:core:common` owns `Outcome`, validation
  errors, confirmations, currency support and minor-unit factors; E1-06 remains the data shell.
- [x] Decisions checked — D-18 requires 85% feature coverage; D-19 fixes `Outcome`; D-26 fixes the
  monetary golden values; D-28 defers package-layer enforcement to E1-07; D-38 keeps database-owned
  derived writes behind `DatabaseMutations`; D-55 fixes staged module ownership; D-76 establishes
  the functional-core precedent; and D-77 applies it to Fuel Entry validation. Every applicable
  decision is `Accepted`, and no `Proposed` or `Pending` row blocks E1-04.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §3`, `§5.2`, `§6` R-1 and R-2, `§7`
  F-3, `§8.3`, `§11`; `docs/CONTRACTS.md §2`, `§3`, `§4`, `§5`, `§6`, `§12`, `§13`, `§20.0`,
  `§20.0.1`, `§20.2`, `§20.4`, `§20.5`; `docs/TECHNICAL_PLAN.md §4`, `§10`, `§12`; and
  ADR-0078.
- [x] Expected verification identified — focused `:feature:fuel` Android-host and
  `iosSimulatorArm64` tests; platform currency tests on both targets; feature lint, detekt and
  Kover; architecture and contract checks; the complete repository command from `AGENTS.md`; and
  `git diff --check`.
- [x] Human review gates identified before work — gated paths `docs/SPECIFICATION.md`,
  `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md` and `docs/adr/**`; D-77 and the public validator
  contract require owner review before merge. E1-04 itself is not a gated story.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Pending implementation.

## Acceptance Evidence

- Pending implementation and verification.

## Out of Scope / Not Done

- E1-05 consumption calculation and E1-06 Fuel Entry persistence.

## Files Changed

- Pending final inventory.

## Decisions Made

- D-77 selects pure Fuel Entry validators with immutable pre-write facts and canonical output.
- The owner requested one commit after each RED, GREEN and REFACTOR phase, followed by one push
  and pull-request creation. Technical decisions that require owner input are grouped for review
  after all independent work is complete.

## Verification Run

- Pending RED, GREEN, REFACTOR and final verification evidence.

## Contract Impact

- Updated `docs/CONTRACTS.md §5`, `§13` and `§20.5` for D-77.

## Decision Board Impact

- Added D-77 and ADR-0078.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [ ] Entry appended.

## Risks or Follow-ups

- E1-06 must calculate `FuelEntryValidationContext.earliestAllowedDate` after the owner selects the
  exact representation of `vehicle.createdAt - 20 years`.

## Human Review Gate

- Applies: gated contract, specification, decision-board and ADR paths. Owner review is required
  before merge.
