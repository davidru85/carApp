# Agent Handoff - E3-11

## Story

`E3-11 - Anonymous Identity Cleanup Entry Points - M`

## Ready Check

- Backlog story: `E3-11 - Anonymous Identity Cleanup Entry Points - M`
  (`docs/BACKLOG.md` line 775).
- Acceptance criteria reviewed:
  1. `onAnonymousUserDeleted` is the only Cloud Functions 1st gen function in the project. The
     application relies on it only for Firebase native automatic anonymous cleanup, and it
     delegates eligible deleted anonymous UIDs to the E3-10 `deleteUserData` service; delivery
     from another deletion path is harmless overlap.
  2. `deleteOrphanedAnonymousAccount` is a 2nd gen callable. It verifies the captured anonymous
     ID token and the permanent caller context, rejects deletion of the current permanent UID,
     deletes the orphaned anonymous Auth account through the Admin SDK, then invokes
     `deleteUserData` directly.
  3. The callable never relies on `onAnonymousUserDeleted` firing. An integration test deletes
     through the Admin SDK path with trigger delivery suppressed or disregarded and still proves
     `users/{uid}` is removed.
  4. Both paths are idempotent and concurrent or delayed overlap is harmless.
  5. Logs contain no UID, token, raw payload or other forbidden value.
  6. The functions, exports and deployment configuration match the exact `TD-01` migration
     surface; a contract check rejects any additional 1st gen function.
- Dependencies checked: `E3-10` merged on 2026-09-07 through pull request #58 and provides
  `deleteUserData`, the data-location registry and the Firebase Admin gateways. The existing
  `functions/` package, Node.js 22 runtime and firebase-functions 7.3.2 are in place.
- Decision rows checked: D-23, D-61, D-62, D-63, D-66, D-67, D-68, D-128, D-129, D-130 and
  D-131 are all `Accepted` in `docs/DECISION_BOARD.md`. Per `docs/handoff-E3-10.md`, E3-11 owns
  the shared Cloud Functions App Check decision; any new decision will be raised with the owner
  before merging and never implemented without owner selection.
- Normative sections reviewed: `docs/SPECIFICATION.md` §12 (D-61, D-62, D-63);
  `docs/CONTRACTS.md` §11.3 (collision flow step 5), §11.5 (deletion contract, two anonymous
  entry points, redacted logging), §17 (logging/privacy); `docs/TECHNICAL_PLAN.md` §13 (TD-01
  exact migration surface);
  ADR-0064/D-63, ADR-0132/D-131; `AGENTS.md` (repository state, human review gates, TDD and
  npm policies); `docs/handoff-E3-10.md`.
- Expected verification commands: `cd functions && npm ci && npm test`;
  `npm run test:firestore-rules`; `npm run audit`; the complete non-instrumented Gradle command;
  `./gradlew contractCheck`; `git diff --check`.
- Human review gates that apply: E3-11 is a backlog story that requires human review
  (`Human review required.`). Gated topics touched: remote backend, authentication, logging and
  privacy. The agent will not merge the pull request.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every repository
  artifact it produces is in technical English.

## In-Progress Checkpoint

- Date: 2026-09-07.
- Branch and base: `story/E3-11-anonymous-cleanup-entry-points`, from `main` at `6c74b5e`.
- Current phase: Review Round 1 findings resolved.
  - Finding 1 resolved: `deleteOrphanedAnonymousAccount` reads `verified.firebase?.sign_in_provider === "anonymous"` using `Pick<DecodedIdToken, "uid" | "firebase">`; tests assert real Admin SDK decoded token shape and reject legacy flat format.
  - Finding 2 resolved: D-137 / ADR-0138 pins `onAnonymousUserDeleted` explicitly to `europe-west1` via `.region("europe-west1")`.
  - Finding 3 resolved: D-138 / ADR-0139 bounds `onAnonymousUserDeleted` to 2 instances, 256MB memory, 60s timeout, and sets `failurePolicy: true` (`eventTrigger.retry = true`) to enable platform retries on transient Firestore errors.
  - Finding 4 resolved: `functions/test/dependencyReachability.test.mjs` pins endpoint metadata for both `deleteOrphanedAnonymousAccount` and `onAnonymousUserDeleted` (platform, region, memory, timeout, instances, retry).
  - Finding 5 resolved: ADR-0134 and `docs/CONTRACTS.md §11.5` explicitly document that captured anonymous ID token verification relies on standard Firebase 1-hour expiry without `auth_time` freshness or `checkRevoked`.
- Push and pull-request status: PR #60 open at `https://github.com/davidru85/carApp/pull/60`; the branch will be pushed with the review fixes; the agent will not merge it.
- Verification evidence: `npm test` 49/49 passed; `npm run audit` exit 0 (7 D-68 moderates only); Firestore rules 154/154 passed; complete non-instrumented Gradle command 636 actionable tasks BUILD SUCCESSFUL; `git diff --check` clean.
- Open decisions or blockers: none. Awaiting owner review round 2.

## Scope Completed

- Added the sole permitted Cloud Functions 1st gen trigger `onAnonymousUserDeleted`
  (`functions/src/auth/onAnonymousUserDeleted.ts`): an `auth.user().onDelete` handler that
  skips records without a UID (`MISSING_UID`) or with any provider entry (`NOT_ANONYMOUS`,
  covering linked and phone-only users) and delegates eligible anonymous UIDs to the E3-10
  `deleteUserData` service with redacted logs. The trigger is pinned to `europe-west1` (`D-137`),
  bounded to two 256 MiB instances, 60-second timeout and configured with `failurePolicy: true`
  (`eventTrigger.retry = true`) so transient Firestore errors trigger Cloud Functions retries
  (`D-138`). Redelivery, retry-after-failure and concurrent trigger/callable overlap are provably
  harmless and idempotent.
- Added the 2nd gen callable `deleteOrphanedAnonymousAccount`
  (`functions/src/callable/deleteOrphanedAnonymousAccount.ts`): verifies the captured
  `anonymousIdToken` (checking `firebase.sign_in_provider == "anonymous"` on the Admin SDK
  `DecodedIdToken` representation), rejects targeting the current permanent UID, deletes the
  orphaned Auth user through the Admin SDK and only then invokes `deleteUserData`, never relying
  on trigger delivery. Token verification relies on standard 1-hour expiry without extra freshness
  or revocation checks. The wire contract is the D-133 one; runtime bounds are the D-135 ones.
- Extended `FirebaseAdminAuthDeletionGateway` with `verifyIdToken` so the callable verifies
  captured tokens through the same injection seam as deletion.
- Added the TD-01 generation-policy suite (`functionGenerationPolicy.test.mjs`) and, per D-136,
  mirrored the sole-1st-gen allowlist into `contractCheck` as assertion 21 with a failing
  fixture in `:build-logic:convention:test`.
- Updated the reachable export set and pinned endpoint metadata in
  `dependencyReachability.test.mjs` for all deployed functions.

## Acceptance Evidence

- `onAnonymousUserDeleted` is the only 1st gen function: the source-level allowlist in
  `functionGenerationPolicy.test.mjs`, the new `contractCheck` assertion 21 with its failing
  fixture, and the pinned `firebase.json` deployment configuration all prove it. The trigger
  delegates only eligible anonymous UIDs to `deleteUserData` and treats other deliveries as
  harmless overlap — `anonymousCleanup.test.mjs` covers eligibility, redelivery, retry,
  concurrent overlap and log redaction. `dependencyReachability.test.mjs` pins its `europe-west1`
  region, 256 MiB memory, 2 max instances, 60s timeout, and `retry === true`.
- `deleteOrphanedAnonymousAccount` verifies the captured anonymous token and permanent caller
  context, rejects the current permanent UID (`failed-precondition`), deletes through the Admin
  SDK and then invokes `deleteUserData` directly — `orphanedAnonymousAccount.test.mjs` covers
  the real Admin SDK token shape, rejection of legacy flat tokens, the closed error-code set,
  the Admin-then-data ordering with no trigger involvement, the Auth-missing retry path, one-shot
  failure recovery, typed failures and redacted logs with no UID, token, payload or raw provider
  value. `dependencyReachability.test.mjs` pins its `gcfv2` platform, `europe-west1` region,
  256 MiB memory, 2 max instances and 60s timeout.
- Both paths are idempotent and overlap is harmless: redelivery and concurrent-overlap tests in
  both suites converge on exactly the registered collections under `users/{uid}`.
- Logs contain no forbidden value: both suites serialize the emitted logs and assert the
  absence of UIDs, tokens, payloads and raw failures.
- The functions, exports and deployment configuration match the exact TD-01 migration surface:
  assertion 21 in `contractCheck` fails on any additional v1 module, any export-set change or a
  hidden second codebase.

## Out of Scope / Not Done

- E2-04 owns the client collision flow that calls `deleteOrphanedAnonymousAccount`.
- E2-05 owns the client account-deletion presentation flow.
- A dedicated least-privilege service account for Cloud Functions remains deferred (D-131).
- Cloud Functions App Check enforcement is excluded by D-132 and stays scoped to Authentication
  and Firestore per D-67.

## Files Changed

- Functions implementation: `functions/src/auth/onAnonymousUserDeleted.ts`,
  `functions/src/callable/deleteOrphanedAnonymousAccount.ts`,
  `functions/src/deletion/firebaseAdminDeletionGateways.ts`, `functions/src/index.ts`.
- Functions tests: `functions/test/anonymousCleanup.test.mjs`,
  `functions/test/orphanedAnonymousAccount.test.mjs`,
  `functions/test/functionGenerationPolicy.test.mjs`,
  `functions/test/dependencyReachability.test.mjs`.
- Build-logic contract check: `build-logic/convention/.../contract/FunctionGenerationContract.kt`,
  `FunctionGenerationContractTest.kt`, `ContractCheck.kt`.
- Normative documentation: `docs/CONTRACTS.md §11.5`, `docs/DECISION_BOARD.md`,
  `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2, §13`, `docs/adr/README.md`,
  ADR-0133 through ADR-0139.
- Story records: this handoff and `docs/PROJECT_LOG.md`.

## Decisions Made

- The owner selected the five grouped options presented at the end of the TDD cycle:
  D-132 option B (no Functions App Check enforcement), D-133 option A (implemented wire
  contract), D-134 option A (empty `providerData` eligibility), D-135 option B (fitted runtime
  bounds) and D-136 option B (allowlist mirrored into `contractCheck`).
- Review round 1 introduced D-137 (pinning `onAnonymousUserDeleted` to `europe-west1`) and
  D-138 (bounding `onAnonymousUserDeleted` runtime to 256 MiB / 60s / 2 instances and enabling
  platform execution retries via `failurePolicy: true`), with captured anonymous token lifetime
  explicitly documented under ADR-0134.
- The owner explicitly confirmed the RED/GREEN/REFACTOR commit sequence with a single push at
  the end, the same exception granted to E3-10.
- No `SHOULD` rule was intentionally deviated from.

## Verification Run

- RED phase: `npm test` — 30 tests, 26 passed, 4 suites failed as intended (missing
  entry-point modules, missing policy source and the outdated export-set assertion).
- GREEN phase: `npm test` — 47/47 passed.
- REFACTOR phase (trigger failure propagation passes the original error through; the bespoke
  wrapper was dropped): `npm test` — 47/47 passed.
- Review Round 1 RED/GREEN:
  - Added tests for real `DecodedIdToken` shape and rejection of legacy flat token; verified RED then GREEN.
  - Added endpoint metadata assertions in `dependencyReachability.test.mjs`; verified RED failure on unconfigured trigger region/bounds/retry, then GREEN upon configuring `region("europe-west1").runWith(...)`.
- After review round 1:
  - `cd functions && npm test` — 49/49 passed.
  - `npm run audit` (functions) — exit 0; only the seven D-68 moderate `uuid` entries.
  - `npm run test:firestore-rules` — 154/154 emulator tests passed.
  - `./gradlew contractCheck` — passed; assertion 2 reports 139 decisions with ADR parity,
    assertion 21 (TD-01 allowlist) passes on the real repository and its five mutated fixtures
    fail in `:build-logic:convention:test`.
  - Complete non-instrumented Gradle command — passed 636 actionable tasks.
  - `git diff --check` — clean.

## Contract Impact

- Updated `docs/CONTRACTS.md §11.5` with the executable D-134 eligibility predicate, the D-133
  wire contract, token verification shape and expiry posture, the runtime bounds for
  `deleteOrphanedAnonymousAccount` (D-135), the D-137 region and D-138 runtime bounds and retry
  policy for `onAnonymousUserDeleted`, and the D-132 App Check posture for Cloud Functions. No
  Kotlin or Swift contract changed.

## Decision Board Impact

- Added D-132 through D-138 with ADR-0133 through ADR-0139 and matching rows in the four
  mirroring documents, validated by `contractCheck` assertions 2 and 3.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [x] Story and decision entries appended (see `docs/PROJECT_LOG.md`, 2026-09-07).

## Risks or Follow-ups

- TD-01 closure now has two guards that must move together: `functionGenerationPolicy.test.mjs`
  and `contractCheck` assertion 21 (D-136). The migration must carry forward `europe-west1` (D-137)
  and the runtime bounds and retry configuration (D-138).
- A registry growth (Storage prefixes or new collections) must re-evaluate the D-135 timeout
  before merging.
- The seven D-68 moderate advisories remain under the 2026-12-01 TD-01 review.
- CI on the draft pull request is pending when this handoff is committed; results are recorded
  in the pull request.

## Human Review Gate

- Applies: E3-11 is a backlog story with `Human review required`, and the change touches the
  gated remote-backend, authentication and logging/privacy topics plus the gated paths
  `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md`, `docs/adr/**` and
  (through CI) `firestore/**` behavior. The agent will not merge the pull request.
