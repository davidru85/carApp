# Agent Handoff - E3-10

## Story

`E3-10 - Account Deletion Server Operation - M`

## Ready Check

- Backlog story: `E3-10 - Account Deletion Server Operation - M`.
- Acceptance criteria reviewed: authenticated caller and matching target UID; `fuelEntries` before
  `vehicles`; reusable idempotent `deleteUserData`; explicit Firestore and Storage registry;
  registry/schema parity; Auth deletion only after remote deletion; typed failures; redacted logs;
  server-side happy-path, retry, authorization and failure-order coverage; unchanged client
  hard-delete rejection.
- Dependencies checked: the existing Firebase Functions package, accepted D-23 server operation,
  accepted D-63 cleanup ownership, E3-01 Firestore rules and the development Firebase project are
  present. This story is the prerequisite for E3-11.
- Decisions checked: D-23, D-63, D-66 and D-68 are `Accepted`; the Decision Board reports no
  `Proposed` or `Pending` decisions. No new library, service, identifier or MVP-scope decision is
  required.
- Normative sections reviewed: `docs/SPECIFICATION.md` F-5 and sections 3, 8 and 12;
  `docs/CONTRACTS.md` sections 11.1, 11.5, 16, 17 and 18; `docs/TECHNICAL_PLAN.md` sections 12 and
  13; `docs/SECURITY.md`; ADR-0024/D-23; ADR-0064/D-63; `docs/CONTRIBUTING.md`; and `AGENTS.md`.
- Expected verification: focused Cloud Functions RED/GREEN tests; production dependency audit;
  Firestore Rules emulator suite; the complete non-instrumented Gradle command; provider
  decoupling; Objective-C header parity; and `git diff --check`.
- Human review gates identified before work: E3-10 is a gated story. Remote backend,
  authentication, logging/privacy and account deletion are gated topics. The owner must review and
  merge the pull request.
- Rule 0 acknowledged: owner conversation is Spanish (Spain); every repository artifact, branch,
  commit and pull-request field is technical English.

## In-Progress Checkpoint

- Date: 2026-09-06.
- Branch and base: `story/E3-10-account-deletion-service`, based on synchronized `main` at
  `a8bbfcf`.
- Current phase and latest commit: GREEN phase complete; RED commit `1e64ab8`; GREEN commit pending.
- Push and pull-request status: not pushed; no pull request.
- Completed since the previous checkpoint: committed RED; added the explicit data-location
  registry, sequential reusable deletion service, Firebase Admin gateways, authenticated 2nd gen
  callable handler and public function export.
- Verification evidence and known failures: `npm test` passes all 21 Cloud Functions tests,
  including the 11 new E3-10 tests. No known focused-test failure.
- Open decisions or blockers: none. The owner explicitly requested one push after the REFACTOR
  commit instead of one push per TDD phase.
- Exact next step: commit GREEN, then refactor and run the complete repository verification.

## Scope Completed

- In progress.

## Acceptance Evidence

- In progress.

## Out of Scope / Not Done

- E3-11 owns the anonymous-deletion trigger and orphan-cleanup callable.
- E2-05 owns the client account-deletion presentation flow and local-data clearing.
- E2-04 owns anonymous account conversion and collision recovery.

## Files Changed

- In progress.

## Decisions Made

- The owner selected the prerequisite sequence `E3-10 -> E3-11 -> E2-04`.
- The owner explicitly requested RED, GREEN and REFACTOR commits followed by one push. This is the
  story-specific exception to the default per-phase push cadence in `docs/SPECIFICATION.md`.
- No `SHOULD` deviation or new project-level technical decision has been introduced.

## Verification Run

- RED phase: `npm test` — failed as intended because the account-deletion callable and deletion
  registry/service modules do not exist; all ten pre-existing tests passed.
- GREEN phase: `npm test` — passed all 21 tests.

## Contract Impact

- No contract changes planned; implementation follows `docs/CONTRACTS.md` sections 11.5 and 16.

## Decision Board Impact

- No decision changes planned; implementation executes accepted D-23 and D-63.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [ ] Entry appended

## Risks or Follow-ups

- E3-11 remains required before E2-04 is Ready.

## Human Review Gate

- Applies: gated story E3-10 and gated remote-backend, authentication, logging/privacy and account
  deletion topics. The agent will not merge the pull request.
