# Agent Handoff - E3-01

## Story

`E3-01 - Firestore Structure and Security Rules - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — E3-01 creates Firestore rules, empty indexes and emulator tests;
  it does not create an application provider module under D-48.
- [x] Acceptance criteria reviewed — operation-specific authorization; exact closed Vehicle and
  FuelEntry payload validation; schema version, timestamp, tombstone and orphan behavior; every
  required emulator case; delta-pull query/index proof; protected CI execution.
- [x] Dependencies checked — Phase 0, E1-01 and E3-06 are merged; the development Firestore
  database exists in `europe-west1`; CI remains emulator-only and needs no Firebase credentials.
- [x] Decisions checked — D-0, D-5, D-9, D-13, D-14, D-23, D-31, D-34, D-40 and D-42 are
  `Accepted`; on 2026-08-24 the owner also accepted the exact official emulator stack, existing
  protected-job placement, client-configuration ownership and exact MVP schema version as D-46
  through D-49, then accepted the timestamp-only first-page cursor as D-50 after the pinned SDK
  rejected the former empty document-ID anchor, then accepted the repository-wide install-script
  block and the moderate Firebase CLI audit residual as D-51 and D-52. No `Proposed` or `Pending`
  decision blocks the story.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md §9`, `§10` and `§11`;
  `docs/CONTRACTS.md §9.5`, `§16`, `§18` and `§20.0.1`; `docs/TECHNICAL_PLAN.md §7` and `§12`;
  `docs/BACKLOG.md E3-01`; `docs/SECURITY.md`; `docs/CONTRIBUTING.md`; and `AGENTS.md`.
- [x] Expected verification identified — focused RED/GREEN `node:test` cases through the
  Firestore emulator; `npm ci`; the complete rules suite; pull-query/index proof; Gradle contract,
  architecture, lint, coverage and multiplatform commands; GitHub's nine protected checks.
- [x] Human review gates identified before work — E3-01 is a gated story; `firestore/**`,
  `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `AGENTS.md`,
  `docs/adr/**` and `docs/versions-matrix.md` are gated paths; Firestore rule and remote-backend
  topics are gated. Owner review is required before merge.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## Scope Completed

- Pending implementation.

## Acceptance Evidence

- Pending implementation.

## Out of Scope / Not Done

- E0-07 owns real Firestore client configuration and disabled-persistence evidence under D-48.
- E3-02 owns the complete `RemoteSyncSource`; E3-10 owns Admin account deletion hard deletes.
- No rule is deployed to the development project by this pull request.

## Files Changed

- Pending implementation.

## Decisions Made

- D-46 through D-49 were accepted by the owner before implementation. D-50 was accepted after the
  official Firebase SDK rejected the former empty document-ID cursor. D-51 and D-52 were accepted
  after a clean npm install exposed version-dependent install-script behavior and moderate
  development-tool advisories. All are recorded in this pull request.

## Verification Run

- Pending implementation.

## Contract Impact

- Corrected the conflicting schema-version sentence in `docs/CONTRACTS.md §9.5` under D-49 and
  replaced the SDK-invalid empty document-ID anchor with the D-50 timestamp-only first-page
  boundary; Firestore rules implement the resulting §16 contract.

## Decision Board Impact

- Added accepted decisions D-46 through D-52 and ADR-0047 through ADR-0053.

## Shared-Write Modules Touched

- None. `:core:database` is compiled and tested but not modified.

## Project Log Entry

- [ ] Entry appended.

## Risks or Follow-ups

- E0-07 follows this story and must configure the first real Firestore client with an in-memory
  cache on Android and iOS.
- Firebase CLI 15.28.1 retains the moderate development-tool-only audit residual accepted by D-52;
  re-evaluate it on the next CLI update or any high/critical advisory.

## Human Review Gate

- Applies: E3-01, every `firestore/**` change, gated normative/decision paths and the Firestore
  rule/backend topics. The owner must review and merge this pull request.
