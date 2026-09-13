# Agent Handoff

## Story

`E3-03 - :core:sync Engine`

## Ready Check

- Backlog story: `E3-03 - :core:sync Engine`.
- Acceptance criteria reviewed: all criteria in `docs/BACKLOG.md`, including the 18 backup and
  recovery tests, deterministic simulation, connectivity admission, single-cycle concurrency,
  push/pull ordering, retry and poison behavior, quarantine, aggregate status, adoption retry,
  debug support and state-holder convergence.
- Dependencies checked: `E1-01`, `E1-11`, `E2-06`, `E3-01` and `E3-02` are merged on `main`; PR #68
  merged as `fbc6d64` on 2026-09-13.
- Decisions checked: all decisions needed by E3-03 are accepted. The owner selected D-169 option A
  and D-170 option A on 2026-09-13 before implementation started. D-149 and D-150 remain pending
  only for E3-15 and E3-16.
- Normative sections reviewed: `docs/SPECIFICATION.md` §§2, 8-9, 11-12;
  `docs/CONTRACTS.md` §§3.1, 6-10, 14, 17, 18, 20.0.1, 20.3, 20.7, 20.10;
  `docs/DECISION_BOARD.md`; `docs/TECHNICAL_PLAN.md` §§4, 6, 8-9; ADR-0170; ADR-0171;
  `docs/handoff-E3-02.md`; and the three most recent `docs/PROJECT_LOG.md` entries.
- Expected verification: focused `:core:sync`, `:core:database`, Firebase Firestore and `:shared`
  Android-host tests; matching iOS simulator compilation/tests; `ktlintCheck`, `detekt`,
  `architectureCheck`, `contractCheck`, convention tests, Kover, Android assembly and host tests;
  the complete non-instrumented repository command; Firestore emulator tests if integration
  behavior changes; and the existing protected Android instrumented suite when available.
- Human review gates identified before work: the gated E3-03 story; gated `core/sync/**`,
  `core/database/**`, `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md` and `docs/adr/**` paths; and the
  sync algorithm, state machine, remote-backend boundary, error-taxonomy, logging/privacy and
  Swift-facing API topics. Owner review is mandatory before merge.
- Rule 0 acknowledged: owner conversation is Spanish (es-ES); every repository artifact is
  technical English.

## In-Progress Checkpoint

- Date: 2026-09-13.
- Branch and base: `story/E3-03-core-sync-engine` from `main` at `fbc6d64`.
- Current phase and latest commit: REFACTOR complete and verified, committed as the third TDD commit
  `refactor(E3-03): finalize sync engine`. RED is `a66c612`; GREEN is `bfced6b`. The three-commit
  series is the story's complete TDD history.
- Push and pull-request status: pushed on `story/E3-03-core-sync-engine`; gated pull request opened
  against `main` after this commit. It is not merged and MUST NOT be merged on agent judgement.
- Completed since the previous checkpoint: committed GREEN with the singleton sync controller,
  SQLDelight persistence, raw-document validation and quarantine, deterministic push/pull order,
  cursor progress guard, retry/poison behavior, automatic adoption retry, aggregate status,
  debug diagnostics, graph ownership and shared state-holder convergence. REFACTOR then formatted
  the implementation, removed direct `AppDatabase` exposure from `:core:sync` by composing a
  database-owned `SyncDatabaseAccess` in `:shared`, removed the premature E3-07 tombstone-purge
  implementation, split complex methods, named validation and backoff constants, corrected
  `Unauthenticated` failures to consume the non-connectivity poison budget, and strengthened tests
  for cold-start ordering, exact state transitions, crash-reporting policy, connectivity recovery,
  reset semantics, debug-only diagnostics and malformed raw Firestore transport.
- Quarantine deduplication completed: `SyncPersistence.applyPullPage` now returns newly persisted
  quarantine records; `SyncDatabaseAccess.applyPullPage` detects an existing quarantine row inside
  the pull transaction and returns only the rows it inserted; `SqlDelightSyncPersistence` maps the
  returned keys back to their `QuarantineRecord`s. The malformed-payload controller test repeats the
  overlapping cycle and asserts the document is reported exactly once. A new
  `SqlDelightSyncPersistenceTest` covers the persistence mapper, the production controller factory
  and the once-only overlap delivery, restoring `:core:sync` coverage above the `D-18` threshold.
- Verification evidence: the intentional RED command ran 21 tests with the expected 20 failures.
  The focused REFACTOR command (`ktlintFormat`, `:core:sync`, `:core:database`,
  `:integration:firebase-firestore` and `:shared` Android-host tests, `:core:sync:detekt`,
  `:core:database:detekt`, `architectureCheck`) passes. `:core:sync` Kover reports 96.01% line
  coverage, up from 72.65%, against the 80% threshold. The complete non-instrumented command passes
  638 tasks; `contractCheck` reports 19 `[PASS]` assertions and zero `PENDING`. The Android
  instrumented suite runs 17 tests on the D-84 API 36 emulator with zero failures.
- Known failures: none.
- Open decisions or blockers: none.
- Exact next step: none for the agent. The pull request awaits the mandatory owner review and the
  ten required checks.

## Scope Completed

- GREEN implements E3-03's sync engine and integration path: serialized cycles with one pending
  follow-up, offline/local-owner admission, cold-start pull-first selection, dependency-ordered
  push, idempotent acknowledgement, server-timestamp LWW pull, one-cycle overlap, deterministic
  pagination, retry/backoff/poison rules, quarantine, adoption retry and aggregate status.
- The app graph owns one controller. Vehicle and Fuel Entry holders observe its single status flow,
  and the Swift facade exposes a `SyncStateHolder` rather than the controller.
- Debug builds expose redacted outbox, cursor, quarantine and row-state lines on Android and iOS;
  release controllers return no diagnostic lines.
- D-170 is integrated as raw JSON transport with stable provider ordering metadata; product
  validation and quarantine classification remain in `:core:sync`.
- REFACTOR is complete: the implementation, its tests, the repository-wide verification, the iOS
  framework link and the host-app build all pass on the committed worktree.

## Acceptance Evidence

- `DefaultSyncControllerTest` covers the 18 canonical backup/recovery scenarios plus cursor
  fail-closed behavior, concurrent-trigger coalescing, initial-cursor materialization, local retry
  failure propagation, authentication poison budgeting, crash-reporting policy, debug gating and
  fixed-seed simulation.
- `SyncDatabaseAccessTest` covers due selection and acknowledgement, failure correlation, full
  retry reset, connectivity recovery without attempt reset, atomic quarantine/cursor persistence,
  redacted debug projections and remote Vehicle/Fuel Entry application with derived odometer
  recomputation.
- `FirebaseRemoteSyncSourceTest` covers raw Vehicle and Fuel Entry documents and now includes a
  malformed product-field result that must remain a successful raw pull item.
- `AppGraphContractTest` proves Vehicle and Fuel Entry holders converge on the singleton controller
  status flow.
- `SqlDelightSyncPersistenceTest` covers the production persistence mapper, the production
  controller factory and the once-only overlap quarantine delivery, which restores the `:core:sync`
  coverage threshold.
- Evidence is final for the committed REFACTOR: every required non-instrumented check, the iOS
  framework link and the host-app build pass.

## Out of Scope / Not Done

- Platform background scheduling and repository post-write trigger wiring remain owned by E3-04.
- Local tombstone purge remains owned by E3-07. A premature GREEN implementation was deliberately
  removed in REFACTOR and MUST NOT be restored in E3-03.
- The protected Android instrumented suite was run locally against the D-84 API 36 emulator as
  supporting evidence; CI remains the authoritative run.

## Files Changed

- RED and GREEN span `:core:common`, `:core:database`, `:core:sync`, the Firebase Firestore
  integration, the shared graph/state holders, Vehicle and Fuel presentation status wiring,
  Android/iOS debug surfaces and their localized resources, plus the D-169/D-170 normative records.
- REFACTOR changes: sync and database production/tests, the new `SqlDelightSyncPersistenceTest`,
  `core/sync/build.gradle.kts` test dependencies, the Firestore integration test,
  `shared/AppGraph.kt`, three shared tests formatted by ktlint, this handoff, the backlog status and
  the project log.

## Decisions Made

- D-169: owner selected option A, accepting the millisecond cursor and a fail-closed bound for an
  oversized same-millisecond timestamp cluster.
- D-170: owner selected option A, returning raw per-document results so `:core:sync` owns product
  validation and quarantine classification.

## Verification Run

- RED: `./gradlew :core:sync:testAndroidHostTest --tests '*DefaultSyncControllerTest'` — 21 tests,
  20 expected failures against the intentional stubs.
- GREEN: focused sync/database/Firestore/shared tests — pass.
- GREEN: `./gradlew testAndroidHostTest :androidApp:testDebugUnitTest
  :androidApp:assembleDebug` — pass, 279 tasks.
- REFACTOR quality checkpoint: `./gradlew ktlintFormat :core:sync:detekt
  :core:database:detekt architectureCheck` — pass after fixing all reported findings.
- REFACTOR focused checkpoint: `./gradlew :core:sync:testAndroidHostTest
  :core:database:testAndroidHostTest :integration:firebase-firestore:testAndroidHostTest
  :shared:testAndroidHostTest` — pass before the latest quarantine-return API edits.
- REFACTOR final: focused command pass; `:core:sync` Kover 96.01% line coverage; complete
  non-instrumented command pass with 638 tasks; `contractCheck` 19 `[PASS]`, zero `PENDING`;
  `:composition:ios:linkDebugFrameworkIosSimulatorArm64` pass (the framework is produced by
  `:composition:ios`, not `:shared`); host-app `xcodebuild` `** BUILD SUCCEEDED **`; protected
  Android instrumented suite 17 tests, zero failures on the API 36 emulator.
- Provider decoupling re-run locally after the golden update: `./gradlew
  -Pcarapp.excludeFirebaseProviders=true testAndroidHostTest iosSimulatorArm64Test` pass, 234 tasks.
  `contractCheck --rerun-tasks` reports assertion 7 `PASS` and zero `PENDING`, and the regenerated
  header is byte-identical to the golden.

## Contract Impact

- Updated `docs/CONTRACTS.md` §§9.4, 9.5 and 20.7 for D-169 and D-170.

## Decision Board Impact

- Updated `docs/DECISION_BOARD.md` D-169 and D-170 and ADR-0170 / ADR-0171 from Proposed to Accepted.

## Shared-Write Modules Touched

- `:core:database`.

## Project Log Entry

- [x] Entry appended on 2026-09-13.

## Risks or Follow-ups

- The quarantine deduplication now compiles and is exercised end to end; the once-only overlap
  delivery is asserted in `DefaultSyncControllerTest` and `SqlDelightSyncPersistenceTest`.
- `contractCheck` was inspected and reports zero `PENDING` assertions.
- No additional owner decision arose during E3-03. D-169 option A and D-170 option A are Accepted;
  D-149 and D-150 remain unrelated blockers for E3-15/E3-16 only.
- The first CI run failed only `objc-header-golden-check`: the new Swift-facing
  `SyncStateHolder.debugLines` and `refreshDebug` members of the debug diagnostics surface changed
  the generated header. The golden was regenerated, `docs/CONTRACTS.md §20.10` was corrected to
  document those members, and both `contractCheck` and the header diff now pass.
- The pull request awaits the mandatory owner review and the ten required checks; E3-03 is
  implemented, not complete, until it merges.

## Human Review Gate

- Applies: E3-03 is a gated story and changes gated sync/database/normative paths and topics.
