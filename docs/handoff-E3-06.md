# Agent Handoff - E3-06

## Story

`E3-06 - Provider Decoupling Proof - S` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — `E3-06`, pulled forward before `E3-01` by the owner so the
  required provider-decoupling check is executable before the first Firebase integration module.
- [x] Acceptance criteria reviewed — excluding `:integration:*` and `:wiring:firebase` from
  Gradle settings leaves `:core:*`, `:feature:*` and `:shared` compiling and testing with local
  fakes; CI runs the proof under the existing required name `provider-decoupling`.
- [x] Dependencies checked — Phase 0 and `E1-01` are merged; the check can be implemented before
  provider modules exist because the canonical provider registry includes only directories that
  exist, and functional fixtures create representative provider directories.
- [x] Decisions checked — `D-0`, `D-3`, `D-5`, `D-6`, `D-14`, `D-16`, `D-31` and `D-34` are
  `Accepted`; on 2026-08-24 the owner also accepted the E0-07 slice, prerequisite ordering,
  development certificate, Gradle-property exclusion, explicit provider registry and macOS
  JVM/Kotlin-Native verification decisions recorded by this pull request as `D-39` through
  `D-45`. No `Proposed` or `Pending` decision blocks the story.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §8.5`, `docs/CONTRACTS.md §11.6`,
  `§15.2` and `§18`, `docs/TECHNICAL_PLAN.md §4`, `§5` and `§12`, `docs/BACKLOG.md E3-06`,
  `docs/CONTRIBUTING.md`, and `AGENTS.md`.
- [x] Expected verification identified — focused build-logic functional tests; excluded provider
  graph compilation and tests on Android host and `iosSimulatorArm64`; architecture and contract
  checks; the complete local CI command; GitHub's nine required checks.
- [x] Human review gates identified before work — gated decision and ADR paths, module-boundary
  policy, CI provider-decoupling proof, and the E3-06 sequencing decision. Owner review is
  required before merge.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Pending implementation.

## Acceptance Evidence

- Pending implementation.

## Out of Scope / Not Done

- `E3-01` Firestore rules and `E0-07` walking skeleton remain separate later pull requests.

## Files Changed

- Pending implementation.

## Decisions Made

- Pending decision records `D-39` through `D-45`.

## Verification Run

- Pending implementation.

## Contract Impact

- Pending implementation.

## Decision Board Impact

- Pending decision records `D-39` through `D-45`.

## Shared-Write Modules Touched

- None. `:core:database` is compiled and tested but not modified.

## Project Log Entry

- [ ] Entry appended.

## Risks or Follow-ups

- `E3-01` follows this story, then `E0-07`, per the owner-approved prerequisite order.

## Human Review Gate

- Applies: `docs/SPECIFICATION.md`, `docs/DECISION_BOARD.md`, `docs/adr/**`, module-boundary
  policy and the provider-decoupling CI proof. The owner must review and merge this pull request.
