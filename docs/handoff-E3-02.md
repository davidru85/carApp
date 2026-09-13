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

- Date: 2026-09-13
- Branch and base: `story/E3-02-firestore-remote-sync-source` from `origin/main` at `12c12c7`.
- Current phase and latest commit: second owner-review round applied on top of the first-review head
  `a530a7b`; the original RED `0dfa7a2`, GREEN `4feda04` and REFACTOR `6dd9b30` remain the
  implementation cycle beneath the review commits.
- Push and pull-request status: the second-round commits are ready to push to
  `origin/story/E3-02-firestore-remote-sync-source`; pull request #68 is open.
- Completed since the previous checkpoint: applied the owner's second review round. Finding 1 deleted
  the duplicated `D-169` awaiting row and closed the check gap with a new `contractCheck` assertion 23
  (`DecisionRegistry.duplicatedIds`) that fails on a repeated ID inside one table. Finding 2 escalated
  the pre-existing malformed-payload defect as `D-170` / ADR-0171, proposal-only, and qualified the
  overstated error-mapping claims in `docs/handoff-E3-02.md` and ADR-0169. Finding 3 restored
  compile-time exhaustiveness to the provider error mapping and deleted the redundant string mapping
  and its host-isolated test. Finding 4 moved `auth.currentUser` inside a protected
  `runProviderRefresh` region with a total `Throwable` catch and added mutation-proven coverage.
- Verification evidence and known failures:
  The second-round focused command passes Android quality gates, 19 focused source tests plus 5
  boundary tests, iOS simulator compilation, the `:build-logic:convention` tests and all 171
  mirrored decisions. `contractCheck` reports assertion 23 PASS and 171 decisions, up from 170
  because `D-170` was added. The emulator suite passes 156 assertions. Mutation probes: (1) removing
  the `catch (Throwable)` in `runProviderRefresh` makes exactly
  `aNonProviderThrowableFromHandleAcquisitionBecomesUnknownInsteadOfEscaping` fail; (2) the new
  duplicate detector failed `contractCheck` on the real board before Finding 1a was applied, naming
  `D-169 in docs/DECISION_BOARD.md awaiting summary`. The complete non-instrumented repository
  command passes 638 tasks (550 executed). No failure is outstanding.
- Open decisions or blockers: `D-169` and `D-170` are `Proposed` and block `E3-03`, not E3-02.
  `D-168` is `Accepted`. Owner review remains required before merge because Firestore and normative
  decision paths are gated.
- Exact next step: push the second-round commits, update the pull request body, and wait for owner
  review and all ten required checks; merge only after both gates pass.

## Review Round 2

### Finding 1 - duplicated `D-169` row and the check that missed it (fixed)

`docs/DECISION_BOARD.md` held two near-identical `D-169` awaiting rows; the less precise one was
deleted, keeping the row that names both `§9.4` and `§20.7` as the sections option A would amend.
`grep -c '^| D-169' docs/DECISION_BOARD.md` now returns `2` (one registry row, one awaiting row).

The gap was in `DecisionRegistry.decisionsWithStatus`, whose terminal `toMap()` collapsed a repeated
ID silently. `DecisionRegistry.duplicatedIds` now reports an ID repeated **within one table**, scoped
per table so the required registry-plus-awaiting pairing is not flagged. `contractCheck` exposes it
as assertion 23, which names the duplicated ID and the document. This was developed red-first against
a fixture board, and the new assertion was run against the real board before Finding 1a and failed
with `duplicated: D-169 in docs/DECISION_BOARD.md awaiting summary`.

### Finding 2 - malformed remote document fails the whole pull page (escalated, no fix)

Escalated as `D-170` / ADR-0171, `Proposed`, `Needed by` `E3-03`, mirrored across
`docs/DECISION_BOARD.md` (registry and awaiting), `docs/SPECIFICATION.md §12`,
`docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`. The ADR records the three concrete failure
paths, states that `UnsupportedSchemaVersion` is reachable and that only `MalformedPayload` is
unimplementable, presents options A/B/C with costs, recommends A, and names the verification test.
`RemotePage`, `RemoteSnapshot` and every `:core:sync` DTO are untouched by this pull request, per the
owner's decision. The overstated "exact closed error mapping" claim was qualified in
`docs/handoff-E3-02.md` and ADR-0169 to the provider **error-code** taxonomy with a `D-170`
cross-reference.

### Finding 3 - exhaustive provider error mapping (fixed)

`FirestoreExceptionCode.toGatewayFailure()` is now an exhaustive `when (this)` naming each mapped
constant, so a GitLive rename fails the build instead of silently degrading to
`FirestoreGatewayFailure.UNKNOWN`. The redundant `String.toFirestoreGatewayFailure()` mapping and its
`providerFailureNamesMapWithoutLoadingProviderEnumConstants` test were deleted rather than kept as a
second mapping that can drift. The `when` lives in its own file, `FirestoreExceptionCodeMapping.kt`,
because keeping it beside the `FirestoreGatewayFailure` switches initialised both generated
enum-mapping tables together and broke the Android host tests where `FirestoreExceptionCode` is a
Google SDK typealias; the host-isolation constraint that motivated the string indirection applies to
the file, not the guarantee.

### Finding 4 - escape from the closed error type in `refreshAuthToken` (fixed)

`auth.currentUser` and the forced `getIdToken` now run inside `runProviderRefresh`, which rethrows
`CancellationException`, rethrows `FirestoreGatewayException`, maps `FirebaseException` to
`UNAUTHENTICATED` and maps any other `Throwable` to `UNKNOWN`. This closes the unchecked escape from
`pushSnapshot` / `pullChanges`. The payload conversion path is deliberately untouched; it is
Finding 2's subject. Coverage:
`aNonProviderThrowableFromHandleAcquisitionBecomesUnknownInsteadOfEscaping` and
`cancellationDuringRefreshPropagatesInsteadOfBecomingARemoteError`. The first was proven non-vacuous
by removing the `catch (Throwable)` branch, which failed exactly that test.

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
- `firestoreFailuresMapToTheExactRemoteErrorLeaves` proves the closed provider **error-code**
  translation from `FirestoreGatewayFailure` to the exact `RemoteError` leaves. It does not cover
  payload deserialization failures, which escape the closed `Outcome` API; that pre-existing gap is
  `D-170` / ADR-0171.
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
  retry protocol, pagination, timestamp conversion and closed provider error-code mapping. Payload
  deserialization failures are not in that closed mapping; that pre-existing gap is `D-170`.
- `integration/firebase-firestore/.../FirebaseRemoteSyncSourceTest.kt` — focused source tests.
- `integration/firebase-firestore/.../FirestoreExceptionCodeMapping.kt` — exhaustive
  `FirestoreExceptionCode` mapping in its own file, so its generated enum-mapping table does not
  initialise beside the `FirestoreGatewayFailure` switches on the Android host.
- `build-logic/convention/.../DecisionRegistry.kt` — `duplicatedIds` detector and `awaitingOf`; the
  per-table duplicate rule behind `contractCheck` assertion 23.
- `build-logic/convention/.../ContractCheck.kt` — assertion 23, the duplicated-decision-ID guard.
- `build-logic/convention/.../DecisionRegistryTest.kt` — red-first tests for the duplicate detector
  and for the required registry-plus-awaiting pairing.
- `firestore/tests/firestore.rules.test.mjs` — resumed-cycle emulator test.
- `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md` and
  `docs/adr/README.md` — mirrored D-168, D-169 and D-170 decision records.
- `docs/adr/0169-keep-firestore-authentication-retry-inside-the-integration.md` — decision context,
  three options, consequences and verification.
- `docs/adr/0170-bound-the-pull-cursor-guarantee-to-millisecond-distinguishable-clusters.md` —
  Finding 2 analysis, no-data-loss proof, options and recommendation; D-169 is `Proposed`.
- `docs/adr/0171-quarantine-malformed-remote-documents-through-the-sync-source.md` — second-round
  Finding 2 analysis, three failure paths, options and recommendation; D-170 is `Proposed`.
- `docs/CONTRACTS.md §10` — per-attempt side-effect budget with the forced-refresh retry exemption.
- `docs/handoff-E3-02.md` and `docs/PROJECT_LOG.md` — story evidence and continuity records.

## Decisions Made

- D-168: perform the forced token refresh through the same module's GitLive Firebase Auth client.
  This keeps the complete retry protocol inside `:integration:firebase-firestore` without widening
  provider-free contracts or moving retry behavior into wiring. The two rejected alternatives and
  their trade-offs are recorded in ADR-0169.
- D-170: escalate the pre-existing malformed-remote-document defect as a decision, not a fix, per the
  owner's second review round. `RemotePage`, `RemoteSnapshot` and every `:core:sync` DTO are
  unchanged; `D-170` / ADR-0171 is `Proposed` and `Needed by` `E3-03`.
- `docs/CONTRACTS.md §9.4` is NOT amended in this pull request. The owner accepts that `main`
  temporarily carries the overstated later-page guarantee until `D-169` is resolved, and option A or
  option B of ADR-0170 MUST NOT be implemented here.
- No `SHOULD` was deviated from in either review round.

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
  --stacktrace` — first review round passed 18 focused tests (13 source plus 5 boundary), Android
  quality gates, iOS compilation and all 170 mirrored-decision checks.
- `npm run test:firestore-rules` — first review round passed all 156 emulator assertions.
- Complete non-instrumented command from `AGENTS.md` — first review round passed 638 tasks.
- `./gradlew :build-logic:convention:test --tests '*DecisionRegistryTest*' --rerun-tasks
  --stacktrace` — RED, `duplicatedIds` did not exist; then GREEN after implementation.
- `./gradlew contractCheck --rerun-tasks --stacktrace` — second review round, run before Finding 1a:
  `[FAIL] 23. no decision ID is duplicated inside a single table — duplicated: D-169 in
  docs/DECISION_BOARD.md awaiting summary`, proving the new assertion catches the real defect.
- `./gradlew :integration:firebase-firestore:testAndroidHostTest --rerun-tasks --stacktrace` — second
  review round: 19 focused source tests plus 5 boundary tests pass. Removing the `catch (Throwable)`
  branch in `runProviderRefresh` failed exactly
  `aNonProviderThrowableFromHandleAcquisitionBecomesUnknownInsteadOfEscaping`
  (`19 tests completed, 1 failed`), proving the test non-vacuous; the mutation was reverted.
- `./gradlew :integration:firebase-firestore:ktlintCheck :integration:firebase-firestore:detekt
  :integration:firebase-firestore:testAndroidHostTest
  :integration:firebase-firestore:compileKotlinIosSimulatorArm64 :build-logic:convention:test
  contractCheck --rerun-tasks --stacktrace` — second review round passed Android quality gates, the
  focused and convention tests, iOS compilation and all 171 mirrored decisions.
- `npm run test:firestore-rules` — second review round passed all 156 emulator assertions.
- Complete non-instrumented command from `AGENTS.md` — second review round passed 638 tasks
  (550 executed, 20 from cache, 68 up-to-date).

## Contract Impact

- `docs/CONTRACTS.md §10` is amended: the `pushSnapshot` side-effect budget is now stated per attempt
  and explicitly exempts the one forced-refresh retry mandated by the same section's token-refresh
  bullet. No other norm repeats the write/read budget, so no further document needed correction.
- The `docs/CONTRACTS.md §9.4` later-page cursor guarantee is potentially overstated for
  sub-millisecond timestamp clusters; correcting it is D-169 and awaits the owner. The owner accepted
  that `main` carries it temporarily, and it is not changed by either review round.
- `docs/CONTRACTS.md` line 403 and `§9.5` cannot be satisfied through the current `RemotePage` output
  for `MalformedPayload`; correcting it is D-170 and awaits the owner. No `:core:sync` DTO is changed
  by this pull request.

## Decision Board Impact

- D-168 is `Accepted` and mirrored across all four decision tables with ADR-0169.
- D-169 is `Proposed` and mirrored across all four decision tables with ADR-0170. It does not block
  E3-02.
- D-170 is `Proposed` and mirrored across all four decision tables with ADR-0171. It does not block
  E3-02 but blocks `E3-03`. The registry/awaiting tables now contain no duplicated ID.
- `contractCheck` assertion 23 is new: a decision ID repeated inside one table fails the check.

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
- D-170 (`Proposed`, ADR-0171) makes `§9.5` `MalformedPayload` quarantine unimplementable through the
  current `RemotePage` shape. E3-03 MUST NOT start until the owner selects an option; a malformed
  document can currently fail a whole pull page or escape the closed `Outcome` API. The defect is
  pre-existing from `E0-07`, not a regression from E3-02.
- The pull request must pass all ten required checks and receive owner review before merge; this
  branch is implemented, not yet complete.

## Human Review Gate

- Applies: `firestore/**` is a gated path and remote-backend changes are a gated topic. Any new
  decision record additionally touches gated normative and ADR paths.
