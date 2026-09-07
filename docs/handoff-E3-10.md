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

- Date: 2026-09-07.
- Branch and base: `story/E3-10-account-deletion-service`, synchronized with `main` at `c8cf2b8`
  through merge commit `12904be`; work continues in the isolated worktree required after pull
  request #59.
- Current phase and latest commit: first owner review complete; RED commit `cc2e6fd`, GREEN commit
  `2d7b6c2` and review-documentation commit `697b4d9`; this CI checkpoint is pending commit.
- Push and pull-request status: branch pushed through `697b4d9`; pull request #58 is open at
  `https://github.com/davidru85/carApp/pull/58`, its description includes the review round, and all
  ten required checks are green.
- Completed since the previous checkpoint: committed and pushed the review documentation, updated
  the pull-request description and monitored CI run `34069170619` through successful completion.
- Verification evidence and known failures: clean `npm ci && npm test` passes all 26 Functions
  tests; Firestore emulator tests pass 154/154; the complete non-instrumented Gradle command passes
  636 actionable tasks. The first Gradle attempt lacked the worktree-local Android SDK path, and the
  first Firestore attempt lacked root dependencies after sandboxed port access; adding ignored
  `local.properties`, running root `npm ci` and repeating the exact commands resolved both
  environment-only failures. CI run `34069170619` passes all ten required jobs, including
  `ios-simulator-build` without reproducing E1-17. No product or test failure remains.
- Open decisions or blockers: callable runtime options remain an owner decision and are explicitly
  out of scope for this review. No implementation blocker.
- Exact next step: commit and push this CI checkpoint, confirm the final SHA remains green, then
  return pull request #58 to the owner for its second review and merge.

## Scope Completed

- Added the Cloud Functions 2nd gen `deleteAccount` callable in `europe-west1` with authenticated
  caller/target equality and a closed success/error wire contract.
- Added the reusable `deleteUserData` service and explicit data-location registry containing
  `fuelEntries`, `vehicles` and an empty Storage-prefix list.
- Added Firebase Admin Auth and Firestore gateways; collection deletion uses awaited sequential
  `recursiveDelete` calls rooted exactly at `users/{uid}`.
- Added typed failure handling and redacted logs that contain stage/status only.
- Corrected registry/schema parity so the test derives complete Firestore and Storage locations
  from the fenced contract registry without hardcoded document-ID placeholder names.
- Extended log-redaction coverage to the Auth-deletion failure stage and successful completion.
- Resolved the two newly published `qs@6.15.3` advisories by selecting patched 6.16.0 inside the
  already accepted transitive ranges.

## Acceptance Evidence

- `accountDeletion.test.mjs` proves unauthenticated and malformed calls do nothing; a caller cannot
  delete another UID; remote collections are deleted before Auth; a missing Auth user succeeds;
  failures are typed; partial progress retries safely; and remote-data failure, Auth-user failure
  and success logs omit UID, token, payload and raw failure values.
- The same suite proves `deleteUserData` order and idempotency and proves the Firebase Admin gateway
  constructs only `users/{uid}/{registeredCollection}`.
- `dataLocationRegistry.test.mjs` parses only the fenced registry declaration in
  `docs/CONTRACTS.md`, compares complete Firestore entries and the declared Storage-prefix array in
  both directions, and proves omissions are detected for arbitrary document-ID placeholder names.
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
- The first owner review prescribed the registry-block parser and expanded redaction coverage, so
  this review introduced no new technical decision. Callable runtime options remain owner-owned and
  were not changed or recorded as a decision.
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
- First owner review RED phase:

  ```text
  # Subtest: the parity check detects any omitted owner collection placeholder
  not ok 24 - the parity check detects any omitted owner collection placeholder
    error: 'Missing expected exception.'
  # Subtest: the parity check detects an omitted Cloud Storage prefix
  not ok 25 - the parity check detects an omitted Cloud Storage prefix
    error: 'Missing expected exception.'
  1..26
  # tests 26
  # pass 24
  # fail 2
  ```

  The two additional log tests already passed against production and are therefore characterization
  tests. A deliberately contaminated test logger made the remote-data, `AUTH_USER` and success log
  assertions fail (10/13 passed); the mutation was removed before the RED commit, and no production
  source changed.
- First owner review GREEN phase: `npm test` — passed all 26 tests.
- First owner review final verification:
  - `cd functions && npm ci && npm test` — passed all 26 tests.
  - `npm run test:firestore-rules` — passed all 154 emulator tests.
  - Complete non-instrumented Gradle command — passed 636 actionable tasks.

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
- Callable `maxInstances`, memory, timeout and Cloud Functions App Check enforcement remain a
  separate owner decision and were not changed in this review.
- The seven moderate production dependency entries accepted under D-68 remain and keep their
  2026-12-01 TD-01 review.

## Human Review Gate

- Applies: gated story E3-10 and gated remote-backend, authentication, logging/privacy and account
  deletion topics. The agent will not merge the pull request.
