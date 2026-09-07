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
  2. `issueOrphanCleanupTicket` is a 2nd gen callable that binds an opaque ticket to the verified
     anonymous caller without accepting a UID, while `deleteOrphanedAnonymousAccount` requires
     that ticket and a permanent caller, deletes only the server-bound anonymous identity and
     marks authorization complete after `deleteUserData` succeeds.
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

- Date: 2026-09-07 (D-141 security remediation implemented and locally verified).
- Branch and base: `story/E3-11-anonymous-cleanup-entry-points`, from `main` at `6c74b5e`.
- Current phase: D-141 implementation, decision documentation and local verification complete;
  commit, single push and PR update next.
  - An AI-assisted security review of commits `519d0b4` and `0aef797` found that the expired-token
    verifier accepts tokens from other Firebase projects because it does not validate `aud` or
    `iss`, and it prefers an attacker-controlled top-level `uid` custom claim over the authentic
    `sub`. In combination, an authenticated permanent caller can target an arbitrary account for
    Auth and Firestore deletion. Those commits MUST NOT merge without the remediation now present
    later in the branch.
  - The same review found two robustness defects: malformed certificate `max-age` values can
    disable cache hits, and the certificate fetch has no timeout within the function's 60-second
    runtime bound.
  - The owner selected Option B. D-141 and ADR-0142 now supersede D-133 and D-140 with a
    server-issued cleanup authorization ticket created while the anonymous session is live.
- Push and pull-request status: PR #60 is open at `https://github.com/davidru85/carApp/pull/60`.
  The vulnerable commits are pushed. Nine required checks are green and `ios-simulator-build` was
  still running when the security finding was recorded; green CI would not make the vulnerable
  implementation mergeable. The agent will not merge it.
- Verification evidence: the review-round-4 implementation previously passed `npm test` (63 passed,
  1 skipped), the live Firestore emulator test, the accepted D-68 audit, Firestore rules 154/154,
  `contractCheck`, the complete non-instrumented Gradle command and `git diff --check`. These results
  do not cover foreign-project `aud`, incorrect `iss`, or `uid`/`sub` disagreement and therefore do
  not validate the expired-token authorization path. Remediation RED: `cd functions && npm test`
  executed 65 tests, with the focused issuance test failing because
  `createOrphanCleanupTicketHandler` did not exist. GREEN: the same command passes 64 tests with
  the emulator test skipped after adding the minimum handler; the raw ticket is returned only to
  the caller and its SHA-256 digest is bound to the verified anonymous caller UID for 30 days.
  Second RED: `cd functions && npm test` executed 66 tests; the focused deletion test failed with
  `invalid-argument` because the existing handler still requires `anonymousIdToken`; 64 passed and
  the emulator test was skipped. Second GREEN: the command passes 56 tests with the emulator test
  skipped after replacing the token path with ticket resolution, deleting Auth then registered
  data, and marking the authorization completed last. The expired-token verifier, certificate
  fetcher and `uid`/`sub` parsing are removed. Focused coverage preserves authentication guards,
  expiry, missing/completed ticket behavior, failure mapping, retries and log redaction. Retention
  RED: `cd functions && npm test` executed 59 tests; the focused TTL policy test failed because
  `firestore.indexes.json` still had no field override; 57 passed and the emulator test was skipped.
  Retention GREEN: `cd functions && npm test` passes 58 tests with the emulator test skipped;
  `npm run test:emulator` passes the real Admin Firestore ticket lifecycle; and root
  `npm run test:firestore-rules` passes 155/155, including denial of every mobile read, write and
  delete against `orphanCleanupTickets`.
- Open decisions or blockers: none. Exact next step: commit the D-141 documentation closure, push
  once under the owner-granted exception, update PR #60 and wait for its required checks. The
  agent will not merge it.

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
- Added the 2nd gen callable `issueOrphanCleanupTicket`
  (`functions/src/callable/deleteOrphanedAnonymousAccount.ts`): requires the verified anonymous
  caller, accepts no client-selected UID, generates a 256-bit opaque ticket, returns its canonical
  base64url encoding only to the caller and persists only its SHA-256 digest bound to the caller
  UID in a 30-day `PENDING` authorization record.
- Reworked the 2nd gen callable `deleteOrphanedAnonymousAccount` to require the ticket and a
  verified permanent caller. It hashes the ticket, resolves the server-bound UID, rejects missing,
  expired and current-caller authorizations, deletes Auth then registered remote data, and marks
  the authorization `COMPLETED` last. Missing Auth users and completed tickets converge
  idempotently. There is no ID-token, JWT, certificate-fetch or client-selected UID path.
- Added `FirebaseAdminOrphanCleanupAuthorizationGateway` for the default-denied
  `orphanCleanupTickets` collection and configured an unindexed `expiresAt` TTL field override.
- Added the TD-01 generation-policy suite (`functionGenerationPolicy.test.mjs`) and, per D-136,
  mirrored the sole-1st-gen allowlist into `contractCheck` as assertion 21 with a failing
  fixture in `:build-logic:convention:test`.
- Added true Firestore emulator integration test (`orphanedAnonymousAccountEmulator.test.mjs`)
  exercising the Admin SDK path against a live Firestore emulator instance with trigger delivery
  suppressed, proving deletion of registered collections under `users/{orphanUid}` while other UIDs
  remain untouched.
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
- `orphanedAnonymousAccount.test.mjs` covers issuer authentication and server-derived UID binding,
  ticket entropy/shape, digest-only persistence, permanent caller enforcement, malformed and
  legacy payload rejection, unknown/expired/current-UID tickets, completed-ticket response loss,
  Auth-missing retry, failure after each stage, completion-last ordering and redacted logs.
  `dependencyReachability.test.mjs` pins both ticket callables to `gcfv2`, `europe-west1`,
  256 MiB memory, 2 max instances and 60s timeout.
- True Firestore emulator integration test (`orphanedAnonymousAccountEmulator.test.mjs`) proves
  that the real Admin gateway issues only a hashed `PENDING` authorization, removes `fuelEntries`
  and `vehicles` under its bound UID while leaving another UID untouched, and records `COMPLETED`,
  with trigger delivery suppressed.
- `orphanCleanupTicketPolicy.test.mjs` proves the superseded verifier and payload cannot return and
  pins the exact TTL field override. Firestore rules tests prove mobile clients cannot read,
  create, update or delete the internal authorization collection.
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
  `functions/src/deletion/firebaseAdminDeletionGateways.ts`, `functions/src/index.ts`,
  `functions/package.json`.
- Functions tests: `functions/test/anonymousCleanup.test.mjs`,
  `functions/test/orphanedAnonymousAccount.test.mjs`,
  `functions/test/orphanedAnonymousAccountEmulator.test.mjs`,
  `functions/test/orphanCleanupTicketPolicy.test.mjs`,
  `functions/test/functionGenerationPolicy.test.mjs`,
  `functions/test/dependencyReachability.test.mjs`.
- Firestore policy: `firestore/firestore.indexes.json`, `firestore/tests/firestore.rules.test.mjs`.
- Build-logic contract check: `build-logic/convention/.../contract/FunctionGenerationContract.kt`,
  `FunctionGenerationContractTest.kt`, `ContractCheck.kt`.
- CI workflow: `.github/workflows/ci.yml`.
- Normative documentation: `docs/CONTRACTS.md §11.5`, `docs/DECISION_BOARD.md`,
  `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2, §13`, `docs/adr/README.md`,
  ADR-0062, ADR-0064, ADR-0133, ADR-0134, ADR-0141 and ADR-0142.
- Story records: this handoff, `AGENTS.md` and `docs/PROJECT_LOG.md`.

## Decisions Made

- The owner selected the five grouped options presented at the end of the TDD cycle:
  D-132 option B (no Functions App Check enforcement), D-133 option A (implemented wire
  contract), D-134 option A (empty `providerData` eligibility), D-135 option B (fitted runtime
  bounds) and D-136 option B (allowlist mirrored into `contractCheck`).
- Review round 1 introduced D-137 (pinning `onAnonymousUserDeleted` to `europe-west1`) and
  D-138 (bounding `onAnonymousUserDeleted` runtime to 256 MiB / 60s / 2 instances and enabling
  platform execution retries via `failurePolicy: true`), with captured anonymous token lifetime
  explicitly documented under ADR-0134.
- Review round 2 resolved Findings A, B and C:
  - Validated real CLI deployment feasibility and retry policy recognition via
    `npx firebase deploy --only functions --dry-run --force --project davidruiz-carapp-dev`, confirming
    that Firebase CLI cleanly validates `onAnonymousUserDeleted(europe-west1)` and recognizes
    `failurePolicy: true` with automatic retry semantics.
  - Recorded in ADR-0134 and here that `VerifiedIdentityToken` derives `uid: string` directly from
    `Pick<DecodedIdToken, "uid" | "firebase">`, so the previous runtime guard `verified.uid === undefined`
    was dropped as redundant under the type system. If a verifier test double returned a missing `uid`,
    subsequent Admin SDK deletion fails and maps to `internal` rather than `failed-precondition`.
- Review round 3 resolved Findings 1, 2, 3, 4 and 5:
  - Finding 1: Enforced permanent caller precondition (`failed-precondition`) via caller token's
    nested claim (`request.auth.token.firebase.sign_in_provider !== "anonymous"`), with RED test.
  - Finding 2: Recorded D-139 (ADR-0140) permitting well-formed expired anonymous tokens when Auth user was already deleted.
  - Finding 3: Distinguished client token errors (`invalid-argument`) from Admin SDK transient/infrastructure
    failures (`internal` logging stage `"AUTH_USER"` in redacted form) in `resolveCapturedIdentity`.
  - Finding 4: Added actual Firestore emulator integration test (`orphanedAnonymousAccountEmulator.test.mjs`)
    verifying collection deletion under `users/{orphanUid}` while other UIDs remain untouched, with trigger
    delivery suppressed; integrated `npm run test:emulator` into CI.
  - Finding 5: Updated `AGENTS.md` decision range to D-132..D-139, refreshed handoff, added append-only
    project log entries, and updated PR #60 body.
- Review round 4 resolved the convergence defect and review findings:
  - Reproduced non-convergence defect on 1-hour token expiry with an active Auth user via RED test.
  - Owner selected Option A; recorded D-140 (ADR-0141) superseding D-139; implemented cryptographic RS256
    signature verification against Google public certificates (with 30-day `iat` bound) in `deleteOrphanedAnonymousAccount`.
  - Made `OrphanCleanupAuthGateway.getUser` mandatory, eliminating unexercised seams.
  - Added tests for invalid cryptographic signatures, unknown `kid`, expired `iat` bounds (> 30 days), and certificate fetch failures.
  - Updated `docs/handoff-E3-11.md` and PR #60 body removing stale draft/push statements.
- A subsequent AI-assisted security review of the review-round-4 commits found that the D-140
  implementation did not bind expired tokens to this Firebase project and trusted a top-level
  custom `uid` claim ahead of `sub`. The finding was critical and blocked PR #60 pending
  remediation.
- The owner selected Option B. D-141 (ADR-0142) supersedes D-133 and D-140: the active anonymous
  caller receives a 256-bit server-issued cleanup ticket, only its SHA-256 digest and server-bound
  UID are stored in a default-denied 30-day TTL record, the permanent caller presents the ticket,
  and completion is recorded only after Auth and registered data deletion converge. The unsafe
  verifier, certificate fetching and legacy `anonymousIdToken` payload were removed completely.
- The TDD order exemption for Firebase Admin provider integration and Firestore security rules was
  used exactly as allowed by `docs/SPECIFICATION.md §11`: the real gateway and existing default-deny
  rule were verified after the focused handler cycles by the Admin emulator lifecycle and a mobile
  denial test. The ticket handler behavior itself followed focused RED/GREEN cycles.
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
- Review Round 2 Validation:
  - `npx firebase deploy --only functions --dry-run --force --project davidruiz-carapp-dev` — exit code 0;
    dry run complete, explicitly recognizing `onAnonymousUserDeleted(europe-west1)` and its retry policy:
    `⚠ functions: The following functions will newly be retried in case of failure: onAnonymousUserDeleted(europe-west1)... ✔ Dry run complete!`.
- D-141 remediation RED/GREEN:
  - Ticket issuance RED failed because `createOrphanCleanupTicketHandler` did not exist; GREEN
    bound a digest-only ticket to verified anonymous callable context.
  - Ticket deletion RED failed because the callable still required `anonymousIdToken`; GREEN
    resolved the server-bound UID and preserved Auth/data/completion ordering and retries.
  - Retention RED failed because `firestore.indexes.json` had no TTL override; GREEN pinned the
    unindexed `expiresAt` TTL field, default-denied rules and real emulator lifecycle.
  - `cd functions && npm test` — 59 tests (58 passed, 1 emulator test skipped).
  - `npm run test:emulator` (functions) — passed against live Firestore emulator.
  - `npm run test:firestore-rules` — 155/155 emulator tests passed.
- Full suite verification after review round 4:
  - `cd functions && npm test` — 64 tests (63 unit tests passed, 1 emulator test skipped when run without emulator).
  - `npm run test:emulator` (functions) — passed against live Firestore emulator (Admin recursive deletion verified under `users/{orphanUid}`).
  - `npm run audit` (functions) — exit 0; only the seven D-68 moderate `uuid` entries.
  - `npm run test:firestore-rules` — 154/154 emulator tests passed.
  - `./gradlew contractCheck` — passed; assertion 2 reports 141 decisions with ADR parity,
    assertion 21 (TD-01 allowlist) passes on the real repository and its five mutated fixtures
    fail in `:build-logic:convention:test`.
  - Complete non-instrumented Gradle command — passed 636 actionable tasks.
  - `git diff --check` — clean.
- Full suite verification after D-141 remediation:
  - `cd functions && npm test` — 59 tests: 58 passed and the emulator-only test skipped.
  - `cd functions && npm run test:emulator` — 1/1 passed against the real Firestore emulator,
    including the ticket authorization lifecycle and direct registered-data deletion.
  - `npm run test:firestore-rules` — 155/155 passed, including complete mobile denial for
    `orphanCleanupTickets`.
  - `cd functions && npm run audit` — exit 0; only the seven D-68 moderate `uuid` entries.
  - `./gradlew contractCheck :build-logic:convention:test` — passed; 142 decisions and 142 ADRs,
    with assertion 21 accepting exactly five exports and only `onAnonymousUserDeleted` as 1st gen.
  - Complete non-instrumented Gradle command — passed 636 actionable tasks, including the iOS
    simulator test route.
  - `npx firebase deploy --only functions,firestore:indexes --dry-run --force --project
    davidruiz-carapp-dev` — exit 0; Functions source analysis, Firestore rules compilation, index
    file reading and the complete deployment dry run succeeded without deploying.
  - `git diff --check` — clean.

## Contract Impact

- Updated `docs/CONTRACTS.md §11.3`, §11.5 and §16 with the D-141 issuance step, exact ticket wire
  contract, digest-only authorization schema, 30-day TTL, completion-last retry semantics,
  default-denied storage and the closed error/logging contract. D-133 and D-140 are superseded;
  no expired-token fallback remains. No Kotlin or Swift contract changed because E2-04 owns the
  client implementation.

## Decision Board Impact

- Added D-141 and ADR-0142, superseded D-133 and D-140, corrected ADR-0141's false security
  assurances and updated all normative mirrors. The E3-11 range is now D-132 through D-141.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [x] Story and decision entries appended (see `docs/PROJECT_LOG.md`, 2026-09-07).

## Risks or Follow-ups

- The critical flaw in commits `519d0b4` and `0aef797` is remediated by later D-141 commits that
  remove the verifier rather than patching it. Future token-verification work must retain the
  lesson that signature authenticity alone does not prove audience, issuer, subject selection or
  destructive-operation authorization.
- TD-01 closure now has two guards that must move together: `functionGenerationPolicy.test.mjs`
  and `contractCheck` assertion 21 (D-136). The migration must carry forward `europe-west1` (D-137)
  and the runtime bounds and retry configuration (D-138).
- A registry growth (Storage prefixes or new collections) must re-evaluate the D-135 timeout
  before merging.
- Post-deployment verification: once `onAnonymousUserDeleted`, `issueOrphanCleanupTicket` and
  `deleteOrphanedAnonymousAccount` are deployed to `davidruiz-carapp-dev`, extend
  `scripts/verify-cloud-runtime.sh` to assert their region (`europe-west1`) and runtime generation
  (`gcfv1`, `gcfv2` and `gcfv2` respectively), mirroring the check currently performed for
  `stopBilling`.
- The seven D-68 moderate advisories remain under the 2026-12-01 TD-01 review.
- CI on pull request #60 is pending when this handoff is committed; results are recorded
  in the pull request.

## Human Review Gate

- Applies: E3-11 is a backlog story with `Human review required`, and the change touches the
  gated remote-backend, authentication and logging/privacy topics plus the gated paths
  `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md`, `docs/adr/**` and
  (through CI) `firestore/**` behavior. The agent will not merge the pull request.
