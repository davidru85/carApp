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
- Current phase and latest commit: D-131 documentation is ready to commit after final verification;
  RED commit `e6de1ca` and GREEN commit `a2daf0f` are complete. No REFACTOR commit is needed because
  the production change is already the exact declarative endpoint configuration.
- Push and pull-request status: branch pushed through `da02763`; pull request #58 is open at
  `https://github.com/davidru85/carApp/pull/58`, and its preceding review checkpoint passed all ten
  required checks. D-131 work is not pushed yet.
- Completed since the previous checkpoint: committed the endpoint-metadata RED test, then added
  exactly the four owner-selected options to the `deleteAccount` `onCall` declaration without
  changing its region or handler behavior.
- Verification evidence and known failures: the D-131 RED run passed 25/26 tests and showed all
  four emitted fields as Firebase Functions `ResetValue` sentinels. Clean-install Functions passes
  26/26, Firestore Rules passes 154/154, `contractCheck` validates all 132 decisions and ADRs, and
  the complete non-instrumented Gradle command passes 636 actionable tasks. Direct inspection of
  the built endpoint reports `availableMemoryMb: 256`, `concurrency: 1`, `maxInstances: 3` and
  `timeoutSeconds: 300`. No known failure.
- Open decisions or blockers: none. D-131 explicitly defers Cloud Functions App Check enforcement
  and a dedicated minimum-privilege service account; neither is authorized in this change.
- Exact next step: commit the D-131 and ADR-0132 records, push the three D-131 commits, update pull
  request #58 and confirm its required checks.

## Scope Completed

- Added the Cloud Functions 2nd gen `deleteAccount` callable in `europe-west1` with authenticated
  caller/target equality and a closed success/error wire contract.
- Bounded that callable to three single-concurrency 256 MiB instances with a 300-second timeout.
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
  gen function in `europe-west1` with the D-131 instance, concurrency, memory and timeout bounds,
  while the existing D-68 affected modules remain unreachable from `stopBilling`.
- The unchanged 154-test Firestore emulator suite proves client hard deletes remain denied.

## Out of Scope / Not Done

- E3-11 owns the anonymous-deletion trigger and orphan-cleanup callable.
- E2-05 owns the client account-deletion presentation flow and local-data clearing.
- E2-04 owns anonymous account conversion and collision recovery.
- E3-11 owns the shared Cloud Functions App Check decision when it introduces the second callable.
- Provisioning and selecting a dedicated least-privilege service account remains deferred until the
  function is deployed.

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
- Decision and story records: `docs/adr/README.md`, ADR-0129 through ADR-0132, this handoff and
  `docs/PROJECT_LOG.md`.

## Decisions Made

- The owner selected the prerequisite sequence `E3-10 -> E3-11 -> E2-04`.
- The owner explicitly requested RED, GREEN and REFACTOR commits followed by one push. This is the
  story-specific exception to the default per-phase push cadence in `docs/SPECIFICATION.md`.
- The first owner review prescribed the registry-block parser and expanded redaction coverage, so
  that review introduced no new technical decision. The owner subsequently selected D-131's exact
  callable runtime bounds.
- D-128 fixes the `deleteAccount` callable name, request, success and failure wire contract.
- D-129 selects sequential Firestore Admin `recursiveDelete` calls over the D-63 registry.
- D-130 resolves the newly reported `qs` advisories at 6.16.0 inside existing parent ranges.
- D-131 bounds the account-deletion callable runtime and explicitly defers Functions App Check and
  a dedicated least-privilege service account.
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
- D-131 RED phase: `npm test` — failed only the endpoint-metadata assertion; 25/26 tests passed and
  the four runtime fields were Firebase Functions `ResetValue` sentinels instead of `256`, `1`, `3`
  and `300`.
- D-131 GREEN phase: `npm test` — passed all 26 tests; direct built-endpoint inspection reported
  `availableMemoryMb: 256`, `concurrency: 1`, `maxInstances: 3` and `timeoutSeconds: 300`.
- D-131 final verification:
  - `cd functions && npm ci && npm test` — passed all 26 tests with lifecycle scripts remaining
    blocked by repository policy.
  - `npm run test:firestore-rules` — passed all 154 emulator tests.
  - `./gradlew contractCheck` — passed, including identical status for all 132 decisions and ADRs.
  - Complete non-instrumented Gradle command — passed 636 actionable tasks.
  - `git diff --check` — passed.

## Contract Impact

- Updated `docs/CONTRACTS.md §11.5` with the callable wire contract and Admin deletion primitive;
  no Kotlin or Swift contract changed.

## Decision Board Impact

- Added D-128 through D-131 with ADR-0129 through ADR-0132. The implementation also executes
  accepted D-23 and D-63.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [x] Story and D-128 through D-131 entries appended.

## Risks or Follow-ups

- E3-11 remains required before E2-04 is Ready.
- Cloud Functions App Check remains deliberately deferred to E3-11, when both callable entry points
  can be governed by one decision.
- A dedicated least-privilege service account remains deferred until GCP provisioning and
  deployment are in scope.
- The seven moderate production dependency entries accepted under D-68 remain and keep their
  2026-12-01 TD-01 review.

## Human Review Gate

- Applies: gated story E3-10 and gated remote-backend, authentication, logging/privacy and account
  deletion topics. The agent will not merge the pull request.
