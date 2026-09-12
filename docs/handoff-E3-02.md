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
- Current phase and latest commit: RED complete and not yet committed; latest commit is base
  `12c12c7`.
- Push and pull-request status: branch is local only; no pull request exists.
- Completed since the previous checkpoint: added focused retry, retry-ceiling, exact error-mapping,
  empty-page and shared-timestamp cursor tests; added the minimal gateway failure/refresh test seam;
  and added the resumed-cycle Firestore emulator test.
- Verification evidence and known failures:
  `./gradlew :integration:firebase-firestore:testAndroidHostTest --rerun-tasks --stacktrace`
  compiled and executed 15 tests with the intended four failures: both refresh/retry behaviors and
  exact gateway-error mapping throw the new unhandled `FirestoreGatewayException`. The ten existing
  tests and the two new page-semantics tests pass. `npm run test:firestore-rules` passed 156 tests,
  including the new resumed-cycle overlap case. The first emulator attempt was blocked by sandbox
  port permissions before any test ran; the approved local-emulator rerun passed.
- Open decisions or blockers: none. The token-refresh composition mechanism is an implementation
  decision to record if the RED/GREEN evidence confirms the proposed boundary.
- Exact next step: commit and push RED, then implement the minimum gateway classification and
  single token-refresh retry needed to make the four intended failures pass.

## Scope Completed

- In progress.

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

- None yet.

## Verification Run

- `./gradlew :integration:firebase-firestore:testAndroidHostTest --stacktrace` — passed as the
  pre-change baseline.
- `./gradlew :integration:firebase-firestore:testAndroidHostTest --rerun-tasks --stacktrace` — RED,
  15 tests executed and four failed for the intended missing behaviors.
- `npm run test:firestore-rules` — passed 156 tests, including the new resumed-cycle test.

## Contract Impact

- No contract changes planned; implementation targets the existing `docs/CONTRACTS.md` §6, §9.4,
  §10, §16 and §17 contracts.

## Decision Board Impact

- No decision changes yet.

## Shared-Write Modules Touched

- None.

## Project Log Entry

- [ ] Entry appended.

## Risks or Follow-ups

- E3-03 must consume this integration without moving Firebase or GitLive types into `:core:sync`.

## Human Review Gate

- Applies: `firestore/**` is a gated path and remote-backend changes are a gated topic. Any new
  decision record additionally touches gated normative and ADR paths.
