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
- Branch and base: `story/E3-11-anonymous-cleanup-entry-points`, created from `main` at
  `6c74b5e` in the isolated worktree `/private/tmp/carapp-worktrees/E3-11` (required after the
  2026-09-06 shared-clone incident). No local or remote commits yet.
- Current phase and latest commit: RED phase complete locally; `npm test` fails exactly as
  intended — 26/30 pass; the four failing suites are the two new entry-point suites
  (`anonymousCleanup.test.mjs`, `orphanedAnonymousAccount.test.mjs`), the generation-policy
  suite (`functionGenerationPolicy.test.mjs`, missing `src/auth/onAnonymousUserDeleted.ts`) and
  the updated export-set assertion in `dependencyReachability.test.mjs`. RED commit pending.
- Push and pull-request status: nothing pushed; no pull request open.
- Completed since the previous checkpoint: wrote three new failing test suites and updated the
  reachable export set to require `deleteOrphanedAnonymousAccount` and `onAnonymousUserDeleted`;
  ran the RED suite and observed the expected module-not-found and export-set failures.
- Verification evidence and known failures: RED run `npm test` — 30 tests, 26 pass, 4 fail; no
  production source exists yet for any new behavior.
- Open decisions or blockers: the E3-11 decision group awaits owner selection at the end of the
  process; no code decision that requires the owner will be resolved before that.
- Exact next step: write the RED tests for `onAnonymousUserDeleted` eligibility and
  `deleteOrphanedAnonymousAccount` verification behavior plus the TD-01 generation-policy
  contract coverage; run `npm test` to observe the failures.
