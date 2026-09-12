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
- Current phase and latest commit: REFACTOR complete and committed at `6dd9b30`.
- Push and pull-request status: RED, GREEN and REFACTOR are pushed to
  `origin/story/E3-02-firestore-remote-sync-source`; pull request #68 is open for owner review.
- Completed since the previous checkpoint: committed and pushed GREEN and REFACTOR; isolated provider code-name
  translation behind a host-safe internal function; added direct coverage for every mapped provider
  code and the unknown fallback; recorded D-168 and ADR-0169 with all three alternatives; and
  completed the focused, emulator and repository-wide verification.
- Verification evidence and known failures:
  The RED run compiled and executed 15 tests with the intended four failures. The first GREEN run
  exposed an Android-host SDK initialization failure caused by a direct enum `when`; no behavior
  assertion failed. Mapping the provider enum through its stable `name` avoided loading Android's
  unmocked `SparseArray`, and the repeated focused suite passed all 15 tests. The emulator suite
  remains green at 156 tests. The final focused suite passes 16 tests, the iOS simulator target
  compiles, `contractCheck` passes all 169 mirrored decisions, and the complete non-instrumented
  repository command passes 638 tasks.
- Open decisions or blockers: none. D-168 is recorded as `Accepted`; owner review remains required
  before merge because Firestore and normative decision paths are gated.
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
  `docs/adr/README.md` — mirrored D-168 decision record.
- `docs/adr/0169-keep-firestore-authentication-retry-inside-the-integration.md` — decision context,
  three options, consequences and verification.
- `docs/handoff-E3-02.md` and `docs/PROJECT_LOG.md` — story evidence and continuity records.

## Decisions Made

- D-168: perform the forced token refresh through the same module's GitLive Firebase Auth client.
  This keeps the complete retry protocol inside `:integration:firebase-firestore` without widening
  provider-free contracts or moving retry behavior into wiring. The two rejected alternatives and
  their trade-offs are recorded in ADR-0169.

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
- `npm run test:firestore-rules` — final run passed all 156 emulator assertions.
- Complete non-instrumented command from `AGENTS.md` — passed 638 tasks (48 executed, 590
  up-to-date), including lint, static analysis, architecture, contracts, convention tests, coverage,
  Android assembly and unit tests, Android-host shared tests and eligible iOS simulator tests.

## Contract Impact

- No representation contract changed; implementation satisfies the existing
  `docs/CONTRACTS.md` §6, §9.4, §10, §16 and §17 contracts.

## Decision Board Impact

- D-168 is `Accepted` and mirrored across all four decision tables with ADR-0169.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [x] Entry appended.

## Risks or Follow-ups

- E3-03 must consume this integration without moving Firebase or GitLive types into `:core:sync`.
- The pull request must pass all ten required checks and receive owner review before merge; this
  branch is implemented, not yet complete.

## Human Review Gate

- Applies: `firestore/**` is a gated path and remote-backend changes are a gated topic. Any new
  decision record additionally touches gated normative and ADR paths.
