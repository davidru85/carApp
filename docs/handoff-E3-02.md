# Agent Handoff - E3-02

## Story

`E3-02 - Firestore RemoteSyncSource - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — complete the Firebase-backed `RemoteSyncSource` without
  implementing the `:core:sync` engine owned by E3-03.
- [x] Acceptance criteria reviewed — client-ID full-document writes with a server timestamp;
  first-page and later-page cursor semantics; resumed-cycle emulator evidence; Firestore timestamp
  conversion; exact `RemoteError` mapping; one forced token refresh and one retry after
  `Unauthenticated`; empty and non-empty page cursor semantics; no provider types across the module
  boundary.
- [x] Dependencies checked — E3-06, E3-01 and E0-07 are merged; the Firestore rules, emulator
  harness, GitLive client, real provider wiring and memory-only client cache already exist.
- [x] Decisions checked — D-0, D-5, D-9, D-13, D-14, D-15, D-40, D-42, D-45, D-48, D-50,
  D-55, D-65 and D-67 are `Accepted`. D-149 and D-150 are `Pending` only for E3-15 and E3-16 and
  do not block E3-02. No unresolved owner decision blocks this story.
- [x] Normative sections reviewed — `docs/SPECIFICATION.md` §8.3, §8.5, §9, §10 and §11;
  `docs/CONTRACTS.md` §6, §9.4, §10, §16, §17, §18 and §20.7;
  `docs/TECHNICAL_PLAN.md` §4, §5, §7, §8 and §12; `docs/BACKLOG.md` E3-02;
  `docs/DECISION_BOARD.md`; `docs/CONTRIBUTING.md`; `docs/handoff-E3-01.md`;
  `docs/handoff-E0-07.md`; the latest `docs/PROJECT_LOG.md` entries; and `AGENTS.md`.
- [x] Expected verification identified — focused Android-host tests for
  `:integration:firebase-firestore`; the Firestore emulator suite; module lint and static analysis;
  architecture and contract checks; the complete non-instrumented command from `AGENTS.md`; and
  the ten required pull-request checks.
- [x] Human review gates identified before work — `firestore/**` is a gated path and the remote
  backend is a gated topic. Any decision record also touches gated normative and ADR paths. Owner
  review is required before merge.
- [x] Rule 0 acknowledged — owner conversation is Spanish (Spain); every repository artifact,
  branch, commit and pull-request field is technical English.

## In-Progress Checkpoint

- Date: 2026-09-12
- Branch and base: `story/E3-02-firestore-remote-sync-source` from `origin/main` at `12c12c7`.
- Current phase and latest commit: first owner-review round applied and committed at the head of
  `story/E3-02-firestore-remote-sync-source`; the original RED `0dfa7a2`, GREEN `4feda04` and
  REFACTOR `6dd9b30` remain the implementation cycle beneath the review commits.
- Push and pull-request status: the review-round commits are pushed to
  `origin/story/E3-02-firestore-remote-sync-source`; pull request #68 is open for owner review.
- Completed since the previous checkpoint: applied the owner's first review round. Finding 1 amended
  the `docs/CONTRACTS.md §10` side-effects budget to be per attempt and to exempt the single
  forced-refresh retry. Finding 3 made `FirestoreGateway.refreshAuthToken()` abstract. Finding 4
  injected the GitLive `FirebaseAuth` handle into `GitLiveFirestoreGateway` alongside the existing
  `FirebaseFirestore` handle. Finding 5 added failed-refresh coverage for both `pushSnapshot` and
  `pullChanges` and proved it non-vacuous by mutation probe. Finding 2 was an analysis-and-escalation
  task: no data loss is possible, but the millisecond-truncated cursor cannot advance when a full
  page falls inside one millisecond, so D-169 / ADR-0170 records the options and recommendation and
  waits for the owner.
- Verification evidence and known failures:
  The original RED run compiled and executed 15 tests with the intended four failures; GREEN and
  REFACTOR pass the focused suite. The review round adds two failed-refresh tests, bringing the
  focused source suite to 13 tests plus 5 boundary tests (18 total). The emulator suite remains green
  at 156 assertions. The iOS simulator target compiles, `contractCheck` passes all 170 mirrored
  decisions, and the complete non-instrumented repository command passes 638 tasks. The failed-refresh
  tests first passed against the pre-existing retry code, so a mutation probe was used to prove they
  are sensitive: swallowing a refresh failure in `refreshAndRetry` makes exactly those two tests
  fail.
- Open decisions or blockers: D-169 is `Proposed` and blocks `E3-03`, not E3-02. D-168 is `Accepted`.
  Owner review remains required before merge because Firestore and normative decision paths are gated.
- Exact next step: wait for owner review and all ten required checks; merge only after both gates pass.

## Scope Completed

- Implemented the missing `Unauthenticated` recovery path with one forced token refresh and exactly
  one operation retry.
- Mapped the closed Firestore failure vocabulary to the exact `RemoteError` leaves.
- Preserved client-generated document IDs and server timestamps for writes.
- Implemented deterministic first-page `startAt` and later-page two-field `startAfter` queries.
- Converted provider timestamps to epoch milliseconds before returning remote snapshots.
- Preserved the input cursor for empty pages and advanced non-empty pages to the last returned item.
- Kept every Firebase and GitLive type behind the integration boundary.

## Acceptance Evidence

- `vehiclePushUsesTheOwnerPathAndReturnsTheServerTimestamp` proves the owner path, client document ID
  and server-timestamp acknowledgement.
- `vehiclePullReturnsOrderedRemoteSnapshotsWithoutProviderTypes` and
  `fuelEntryPullReturnsTheCompleteClosedRemoteSnapshot` prove boundary conversion and the closed
  remote shapes.
- The push and pull authentication tests prove one forced refresh, one retry and the retry ceiling.
- `failedRefreshOnPushReturnsUnauthenticatedWithoutRetryingTheWrite` and
  `failedRefreshOnPullReturnsUnauthenticatedWithoutRetryingTheQuery` prove the failed-refresh branch:
  one refresh attempt, no operation retry after it, and the exact `RemoteError.Unauthenticated` leaf.
- `firestoreFailuresMapToTheExactRemoteErrorLeaves` and
  `providerFailureNamesMapWithoutLoadingProviderEnumConstants` prove the closed error translation.
- The empty-page and shared-timestamp tests prove both cursor edge cases.
- `a resumed cycle applies startAt to the overlap after a non-empty pull` passes against the
  Firestore emulator and proves the resumed-cycle overlap behavior.

## Out of Scope / Not Done

- The `:core:sync` engine, outbox scheduling, local conflict application and aggregate sync status
  remain owned by E3-03.

## Files Changed

- `integration/firebase-firestore/build.gradle.kts` — accepted GitLive Auth artifact used for the
  same-module forced refresh.
- `integration/firebase-firestore/.../FirebaseRemoteSyncSource.kt` — complete provider adapter,
  retry protocol, pagination, timestamp conversion and closed error mapping.
- `integration/firebase-firestore/.../FirebaseRemoteSyncSourceTest.kt` — focused source tests.
- `firestore/tests/firestore.rules.test.mjs` — resumed-cycle emulator test.
- `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md` and
  `docs/adr/README.md` — mirrored D-168 and D-169 decision records.
- `docs/adr/0169-keep-firestore-authentication-retry-inside-the-integration.md` — decision context,
  three options, consequences and verification.
- `docs/adr/0170-bound-the-pull-cursor-guarantee-to-millisecond-distinguishable-clusters.md` —
  Finding 2 analysis, no-data-loss proof, options and recommendation; D-169 is `Proposed`.
- `docs/CONTRACTS.md §10` — per-attempt side-effect budget with the forced-refresh retry exemption.
- `docs/handoff-E3-02.md` and `docs/PROJECT_LOG.md` — story evidence and continuity records.

## Decisions Made

- D-168: perform the forced token refresh through the same module's GitLive Firebase Auth client.
  This keeps the complete retry protocol inside `:integration:firebase-firestore` without widening
  provider-free contracts or moving retry behavior into wiring. The two rejected alternatives and
  their trade-offs are recorded in ADR-0169.
- No `SHOULD` was deviated from in the first review round.

## Verification Run

- `./gradlew :integration:firebase-firestore:testAndroidHostTest --stacktrace` — passed as the
  pre-change baseline.
- `./gradlew :integration:firebase-firestore:testAndroidHostTest --rerun-tasks --stacktrace` — RED,
  15 tests executed and four failed for the intended missing behaviors.
- `npm run test:firestore-rules` — passed 156 tests, including the new resumed-cycle test.
- `./gradlew :integration:firebase-firestore:testAndroidHostTest --rerun-tasks --stacktrace` — GREEN,
  all 15 tests passed after the provider-enum host-isolation correction.
- `./gradlew :integration:firebase-firestore:ktlintCheck :integration:firebase-firestore:detekt
  :integration:firebase-firestore:testAndroidHostTest --rerun-tasks --stacktrace` — GREEN after the
  implementation-format correction; all focused quality and behavior checks passed.
- `./gradlew :integration:firebase-firestore:ktlintCheck :integration:firebase-firestore:detekt
  :integration:firebase-firestore:testAndroidHostTest
  :integration:firebase-firestore:compileKotlinIosSimulatorArm64 contractCheck --rerun-tasks
  --stacktrace` — passed 16 focused tests, Android quality gates, iOS compilation and all 169
  mirrored-decision checks.
- `./gradlew :integration:firebase-firestore:testAndroidHostTest --rerun-tasks --stacktrace` — first
  owner-review round: the two new failed-refresh tests passed against the existing retry code, so a
  mutation probe swallowed the refresh failure inside `refreshAndRetry` and exactly those two tests
  failed (`18 tests completed, 2 failed`), proving the tests are non-vacuous. The mutation was then
  reverted.
- `./gradlew :integration:firebase-firestore:ktlintCheck :integration:firebase-firestore:detekt
  :integration:firebase-firestore:testAndroidHostTest
  :integration:firebase-firestore:compileKotlinIosSimulatorArm64 contractCheck --rerun-tasks
  --stacktrace` — review round passed 18 focused tests (13 source plus 5 boundary), Android quality
  gates, iOS compilation and all 170 mirrored-decision checks.
- `npm run test:firestore-rules` — review round passed all 156 emulator assertions.
- Complete non-instrumented command from `AGENTS.md` — review round passed 638 tasks (48 executed,
  590 up-to-date), including lint, static analysis, architecture, contracts, convention tests,
  coverage, Android assembly and unit tests, Android-host shared tests and eligible iOS simulator
  tests.

## Contract Impact

- `docs/CONTRACTS.md §10` is amended: the `pushSnapshot` side-effect budget is now stated per attempt
  and explicitly exempts the one forced-refresh retry mandated by the same section's token-refresh
  bullet. No other norm repeats the write/read budget, so no further document needed correction.
- The `docs/CONTRACTS.md §9.4` later-page cursor guarantee is potentially overstated for
  sub-millisecond timestamp clusters; correcting it is D-169 and awaits the owner. It is not changed
  by this review round.

## Decision Board Impact

- D-168 is `Accepted` and mirrored across all four decision tables with ADR-0169.
- D-169 is `Proposed` and mirrored across all four decision tables with ADR-0170. It does not block
  E3-02.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [x] Entry appended.

## Risks or Follow-ups

- E3-03 must consume this integration without moving Firebase or GitLive types into `:core:sync`.
- D-169 (`Proposed`, ADR-0170) bounds the `§9.4` later-page progress guarantee. E3-03 MUST NOT start
  until the owner resolves it. There is no data loss: the truncated boundary is a downward lower
  bound on an `>=` filter. The unresolved behavior is a non-advancing page cursor when a full page
  falls inside one millisecond.
- The pull request must pass all ten required checks and receive owner review before merge; this
  branch is implemented, not yet complete.

## Human Review Gate

- Applies: `firestore/**` is a gated path and remote-backend changes are a gated topic. Any new
  decision record additionally touches gated normative and ADR paths.
