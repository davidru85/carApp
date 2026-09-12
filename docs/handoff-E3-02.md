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
- Current phase and latest commit: GREEN complete and not yet committed; latest commit is RED
  `0dfa7a2`.
- Push and pull-request status: RED is pushed to
  `origin/story/E3-02-firestore-remote-sync-source`; no pull request exists.
- Completed since the previous checkpoint: committed and pushed RED; added one forced Firebase Auth
  token refresh after an unauthenticated Firestore operation; retried the same operation exactly
  once; converted provider failures to an internal closed failure vocabulary; mapped that vocabulary
  to the exact `RemoteError` leaves; and added the already accepted GitLive Firebase Auth artifact to
  the Firestore integration module so the refresh remains inside the module.
- Verification evidence and known failures:
  The RED run compiled and executed 15 tests with the intended four failures. The first GREEN run
  exposed an Android-host SDK initialization failure caused by a direct enum `when`; no behavior
  assertion failed. Mapping the provider enum through its stable `name` avoided loading Android's
  unmocked `SparseArray`, and the repeated focused suite passed all 15 tests. The emulator suite
  remains green at 156 tests.
- Open decisions or blockers: none. The direct same-module GitLive Auth refresh is decision D-168
  to record in the REFACTOR/documentation phase with its alternatives and consequences.
- Exact next step: run focused lint and static analysis, commit and push GREEN, then refactor the
  result mapping/classification and complete the decision and story records.

## Scope Completed

- Implemented the missing `Unauthenticated` recovery path with one forced token refresh and exactly
  one operation retry.
- Mapped the closed Firestore failure vocabulary to the exact `RemoteError` leaves.

## Acceptance Evidence

- In progress.

## Out of Scope / Not Done

- The `:core:sync` engine, outbox scheduling, local conflict application and aggregate sync status
  remain owned by E3-03.

## Files Changed

- `docs/handoff-E3-02.md` — ready check and live continuity record.
- `integration/firebase-firestore/.../FirebaseRemoteSyncSource.kt` — minimal RED test seam only.
- `integration/firebase-firestore/.../FirebaseRemoteSyncSourceTest.kt` — focused source tests.
- `firestore/tests/firestore.rules.test.mjs` — resumed-cycle emulator test.

## Decisions Made

- D-168 (to be recorded): perform the forced token refresh through the same module's GitLive
  Firebase Auth client. This keeps the complete retry protocol inside `:integration:firebase-firestore`
  without widening the provider-free contracts or moving retry behavior into wiring.

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

## Contract Impact

- No contract changes planned; implementation targets the existing `docs/CONTRACTS.md` §6, §9.4,
  §10, §16 and §17 contracts.

## Decision Board Impact

- D-168 must be mirrored with its ADR during REFACTOR before the story is complete.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [ ] Entry appended.

## Risks or Follow-ups

- E3-03 must consume this integration without moving Firebase or GitLive types into `:core:sync`.

## Human Review Gate

- Applies: `firestore/**` is a gated path and remote-backend changes are a gated topic. Any new
  decision record additionally touches gated normative and ADR paths.
