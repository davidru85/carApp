# Agent Handoff — E2-04 Anonymous Account Conversion F-4

## Summary

- In progress: implementing anonymous account linking and credential-collision replacement through the durable E3-11 orphan-cleanup protocol.

## Story

`E2-04 - Anonymous Account Conversion F-4 - M`

## Ready Check

- [x] Backlog story is explicit: `docs/BACKLOG.md` E2-04.
- [x] Acceptance criteria reviewed: UID-preserving linking; `SessionStateHolder` conversion intents; explicit `AdoptExistingAccount` confirmation; cancellation safety; durable snapshot and cleanup ticket before session switch; resumable idempotent remote and local replacement; E3-11 orphan deletion; interruption coverage; no automatic merge.
- [x] Dependencies checked: E2-02 and E3-11 are merged on `main`.
- [x] Required decisions are not `Proposed` or `Pending`: D-60, D-61, D-63, D-64, D-111 and D-132 through D-143 are `Accepted`. E3-15/D-149 and E3-16/D-150 are tracked follow-ups and are not declared E2-04 dependencies.
- [x] Normative sections reviewed: `docs/SPECIFICATION.md` §§3, 7 F-4, 11 and 12; `docs/CONTRACTS.md` §§8, 11.1, 11.3, 11.5, 16, 17, 18, 20.2, 20.8 and 20.10; `docs/DECISION_BOARD.md`; `docs/TECHNICAL_PLAN.md` §§2, 4 and 6.
- [x] Expected verification identified: focused RED/GREEN tests; the complete non-instrumented CI command; relevant Android instrumentation and iOS build/test commands if host surfaces change.
- [x] Human review gates identified before work: gated paths `core/auth/**`, `core/database/**`, the normative contract/decision paths if changed, and gated topics authentication, remote backend and Swift-facing API.
- [x] Rule 0 acknowledged: owner conversation is Spanish (es-ES); repository artifacts are technical English.

## In-Progress Checkpoint

- Date: 2026-09-09
- Branch and base: `story/E2-04-anonymous-account-conversion` from `origin/main` at `50eec16`
- Current phase and latest commit: RED complete and ready to commit; latest commit is still base `50eec16`
- Push and pull-request status: not pushed; no pull request
- Completed since the previous checkpoint: established the focused green baseline, added specific RED tests and behavior-free seams for UID-preserving linking, typed collision confirmation, cancellation, durable snapshot storage, replacement without merging, remote Fuel Entry decoding and convergence after every post-confirmation interruption boundary.
- Verification evidence and known failures: the focused baseline passed for `:shared`, `:core:database`, `:integration:firebase-auth` and `:integration:firebase-firestore`. The RED command compiles and executes, with the expected four database and six shared failures caused by the unimplemented E2-04 behavior; unrelated focused tests remain green. Focused ktlint passes. E1-14 and E1-17 remain the documented pre-existing sources of ambiguity for red `shared-tests` and `ios-simulator-build` jobs.
- Open decisions or blockers: none. The owner explicitly requested one push after the RED, GREEN and REFACTOR commits instead of the default per-phase pushes required by `docs/SPECIFICATION.md` §11; this story-specific workflow exemption will be retained under Decisions Made.
- Exact next step: commit the verified RED phase, then implement the minimum durable conversion behavior required to make the new tests green.

## Scope Completed

- Ready Check, branch creation and RED phase.

## Acceptance Evidence

- Pending.

## Out of Scope / Not Done

- Automatic merging of anonymous and permanent account data.
- E3-15 and E3-16 follow-up hardening.
- E2-05 sign-out and account deletion UI.

## Files Changed

- `docs/handoff-E2-04.md` — in-progress story record and Ready Check.
- `core/auth/**/OrphanCleanupContracts.kt` — behavior-free E3-11 client port used by RED tests.
- `core/database/**/AccountConversionDatabaseAccess*` — behavior-free durable-store seam and RED tests.
- `shared/**/AccountConversion*` and `SessionStateHolderTest.kt` — behavior-free coordinator seam and RED behavior coverage.
- `integration/firebase-firestore/**/FirebaseRemoteSyncSourceTest.kt` — closed Fuel Entry snapshot coverage needed by replacement.

## Decisions Made

- Story-specific owner exemption: retain separate RED, GREEN and REFACTOR commits but push them together once after REFACTOR, as explicitly requested for E2-04. This differs from the default per-phase push cadence in `docs/SPECIFICATION.md` §11.

## Verification Run

- [ ] Relevant tests pass
- [ ] Lint passes (ktlint, detekt)
- [ ] Coverage thresholds hold
- [ ] Architecture checks pass
- [ ] Contract check passes
- [ ] Relevant builds pass (Android, iOS simulator, `Shared` framework from `:composition:ios`)
- [ ] Documentation updated if behaviour, decisions or models changed

Commands or checks run:

```text
./gradlew :shared:testAndroidHostTest :core:database:testAndroidHostTest :integration:firebase-auth:testAndroidHostTest :integration:firebase-firestore:testAndroidHostTest
  PASS before RED changes.

./gradlew :core:database:testAndroidHostTest --tests com.ruizurraca.carapp.core.database.AccountConversionDatabaseAccessTest :shared:testAndroidHostTest --tests com.ruizurraca.carapp.AccountConversionCoordinatorTest --tests com.ruizurraca.carapp.SessionStateHolderTest :integration:firebase-firestore:testAndroidHostTest --tests com.ruizurraca.carapp.integration.firebase.firestore.FirebaseRemoteSyncSourceTest
  EXPECTED RED: 4 database tests and 6 shared tests fail for missing E2-04 behavior; sources compile and unrelated focused tests pass.

./gradlew :shared:ktlintCheck :core:auth:ktlintCheck :core:database:ktlintCheck :integration:firebase-firestore:ktlintCheck
  PASS.
```

## Contract Impact

- [x] No contract changes currently identified; implementation will follow the existing E2-04 contract.
- [ ] Updated `docs/CONTRACTS.md` §:

## Decision Board Impact

- [x] No decision changes currently identified.
- [ ] Updated `docs/DECISION_BOARD.md` (`D-n`) and the related ADR:

## Shared-Write Modules Touched

- [ ] None
- [x] `core/database` — expected for the durable replacement marker; confirm no other story is modifying it. The working tree was clean and no concurrent agent is active for this task.

## Project Log Entry

- [ ] Entry appended to `docs/PROJECT_LOG.md`

## Human Review Gate

Gates are defined canonically in `AGENTS.md`.

- [ ] Not applicable
- [ ] E0-00 owner decision closure
- [ ] Phase 0 closure
- [ ] E0-07 walking skeleton
- [ ] E1-05 consumption calculation
- [ ] E2-06 local owner adoption
- [ ] E3-01 Firestore security rules
- [ ] E3-03 synchronization engine
- [x] Gated path (`core/auth/**`, `core/database/**`, and any normative or ADR path changed)
- [x] Gated topic (authentication, remote backend, and possibly Swift-facing API surface)

## Risks or Follow-ups

- E3-15/D-149 and E3-16/D-150 remain separate known follow-ups to the E3-11 authorization flow; E2-04 must not silently absorb their unresolved owner choices.
