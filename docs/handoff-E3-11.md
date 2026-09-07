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
- Current phase and latest commit: REFACTOR phase verification complete locally; the only
  refactor change relinquishes the bespoke `internal` rethrow in the trigger in favor of the
  original failure, with the test asserting rejection instead of a bespoke code. `npm test`
  still passes 47/47.
- Push and pull-request status: branch pushed through `29e54ca`; draft pull request #60 open at
  `https://github.com/davidru85/carApp/pull/60`. The PR stays draft until the owner selects the
  grouped E3-11 decisions and the corresponding ADRs, contract text and project-log entries land.
- Completed since the previous checkpoint: pushed the three TDD commits and opened the draft PR
  with the full ready-check, evidence and pending-decision group recorded.
- Verification evidence and known failures: no known failures; all ten required-check inputs
  verified locally. CI results pending on the draft PR.
- Open decisions or blockers: five owner decisions pending — (1) shared Cloud Functions App Check
  scope; (2) the `deleteOrphanedAnonymousAccount` wire contract D-id; (3) the 1st gen trigger
  eligibility semantics D-id; (4) the callable runtime bounds D-id; (5) the placement of the
  generation-policy contract check. The PR must not merge before they are recorded.
- Exact next step: present the grouped decisions to the owner with three options each.
