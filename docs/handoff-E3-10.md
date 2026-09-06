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
- Current phase and latest commit: REFACTOR phase complete; RED commit `1e64ab8`, GREEN commit
  `60fb15b`; REFACTOR commit pending.
- Push and pull-request status: not pushed; no pull request.
- Completed since the previous checkpoint: committed GREEN; added exact Admin path and 2nd gen
  endpoint coverage; resolved two new `qs` advisories inside the existing dependency ranges;
  recorded D-128 through D-130; completed repository state, backlog and project-log documentation.
- Verification evidence and known failures: 22 Cloud Functions tests, 154 Firestore emulator tests,
  the 636-task full non-instrumented command, the 234-task provider-free command and Objective-C
  header parity pass. The production audit reports only the seven moderate entries already
  accepted under D-68. No known failure.
- Open decisions or blockers: none. The owner explicitly requested one push after the REFACTOR
  commit instead of one push per TDD phase.
- Exact next step: rerun contract-sensitive verification over the final documentation, commit
  REFACTOR, push once as requested and create the gated pull request.

## Scope Completed

- Added the Cloud Functions 2nd gen `deleteAccount` callable in `europe-west1` with authenticated
  caller/target equality and a closed success/error wire contract.
- Added the reusable `deleteUserData` service and explicit data-location registry containing
  `fuelEntries`, `vehicles` and an empty Storage-prefix list.
- Added Firebase Admin Auth and Firestore gateways; collection deletion uses awaited sequential
  `recursiveDelete` calls rooted exactly at `users/{uid}`.
- Added typed failure handling and redacted logs that contain stage/status only.
- Resolved the two newly published `qs@6.15.3` advisories by selecting patched 6.16.0 inside the
  already accepted transitive ranges.

## Acceptance Evidence

- `accountDeletion.test.mjs` proves unauthenticated and malformed calls do nothing; a caller cannot
  delete another UID; remote collections are deleted before Auth; a missing Auth user succeeds;
  failures are typed; partial progress retries safely; and logs omit UID, token, payload and raw
  failure values.
- The same suite proves `deleteUserData` order and idempotency and proves the Firebase Admin gateway
  constructs only `users/{uid}/{registeredCollection}`.
- `dataLocationRegistry.test.mjs` compares the executable registry with every declared remote path
  in `docs/CONTRACTS.md`, fixes order to `fuelEntries`, then `vehicles`, and asserts the Storage list
  is empty.
- `dependencyReachability.test.mjs` fixes the public export set and proves `deleteAccount` is a 2nd
  gen function in `europe-west1` while the existing D-68 affected modules remain unreachable from
  `stopBilling`.
- The unchanged 154-test Firestore emulator suite proves client hard deletes remain denied.

## Out of Scope / Not Done

- E3-11 owns the anonymous-deletion trigger and orphan-cleanup callable.
- E2-05 owns the client account-deletion presentation flow and local-data clearing.
- E2-04 owns anonymous account conversion and collision recovery.

## Files Changed

- Functions implementation: `functions/src/callable/deleteAccount.ts`,
  `functions/src/deletion/dataLocationRegistry.ts`,
  `functions/src/deletion/firebaseAdminDeletionGateways.ts`,
  `functions/src/deletion/userDeletionService.ts`, `functions/src/index.ts`.
- Tests and dependency lock: `functions/test/accountDeletion.test.mjs`,
  `functions/test/dataLocationRegistry.test.mjs`,
  `functions/test/dependencyReachability.test.mjs`, `functions/package-lock.json`.
- Normative and derived documentation: `AGENTS.md`, `docs/BACKLOG.md`, `docs/CONTRACTS.md`,
  `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md`,
  `docs/versions-matrix.md`.
- Decision and story records: `docs/adr/README.md`, ADR-0129 through ADR-0131, this handoff and
  `docs/PROJECT_LOG.md`.

## Decisions Made

- The owner selected the prerequisite sequence `E3-10 -> E3-11 -> E2-04`.
- The owner explicitly requested RED, GREEN and REFACTOR commits followed by one push. This is the
  story-specific exception to the default per-phase push cadence in `docs/SPECIFICATION.md`.
- D-128 fixes the `deleteAccount` callable name, request, success and failure wire contract.
- D-129 selects sequential Firestore Admin `recursiveDelete` calls over the D-63 registry.
- D-130 resolves the newly reported `qs` advisories at 6.16.0 inside existing parent ranges.
- No `SHOULD` rule was intentionally deviated from.

## Verification Run

- RED phase: `npm test` — failed as intended because the account-deletion callable and deletion
  registry/service modules do not exist; all ten pre-existing tests passed.
- GREEN phase: `npm test` — passed all 21 tests.
- REFACTOR phase:
  - `npm ci` — passed with lifecycle scripts blocked by the repository policy.
  - `npm ls qs --all` — both parents resolve `qs@6.16.0`.
  - `npm test` — passed all 22 tests.
  - `npm run audit` — passed the high/critical gate; seven D-68 moderate entries remain.
  - `npm run test:firestore-rules` — passed all 154 emulator tests, including client hard-delete
    rejection.
  - Complete non-instrumented Gradle command — passed 636 actionable tasks.
  - Provider-decoupling Gradle command — passed 234 actionable tasks.
  - `:composition:ios:linkDebugFrameworkIosSimulatorArm64` — passed 70 actionable tasks; generated
    header matches `Shared.h.golden` byte for byte.

## Contract Impact

- Updated `docs/CONTRACTS.md §11.5` with the callable wire contract and Admin deletion primitive;
  no Kotlin or Swift contract changed.

## Decision Board Impact

- Added D-128, D-129 and D-130 with ADR-0129, ADR-0130 and ADR-0131. The implementation also
  executes accepted D-23 and D-63.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [x] Story and D-128 through D-130 entries appended.

## Risks or Follow-ups

- E3-11 remains required before E2-04 is Ready.
- The seven moderate production dependency entries accepted under D-68 remain and keep their
  2026-12-01 TD-01 review.

## Human Review Gate

- Applies: gated story E3-10 and gated remote-backend, authentication, logging/privacy and account
  deletion topics. The agent will not merge the pull request.
