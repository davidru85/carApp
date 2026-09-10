# Agent Handoff — E2-05 Sign-Out and Account Deletion F-5

## Summary

- Implemented on pull request #65 and awaiting the owner's gated review. Sign-out is restricted to a
  permanent session, the three `DELETING` operations are separated, the departure runs as a
  resumable state machine that never reports success while local data survives, and a stale login is
  recovered through a host-driven re-authentication intent.

## Story

`E2-05 - Sign-Out and Account Deletion F-5 - M`

## Ready Check

- [x] Backlog story is explicit: `docs/BACKLOG.md` E2-05.
- [x] Acceptance criteria reviewed: sign-out only for permanent users; anonymous "delete local data" with two-step confirmation; `ValidationWarning.PendingSyncBeforeSignOut(pendingCount)` + `Confirmation.DiscardPendingChanges`; account deletion order of `docs/CONTRACTS.md §11.5`; outbox dropped only after server success; `AuthError.AccountDeletionRemoteFailed` preserves local data; `user_settings` deleted and recreated from defaults; deletion reachable from settings; Session state holders stay in `:shared` (owner decision D-155).
- [x] Dependencies checked: `E2-02`, `E3-10` and `E3-11` are merged on `main`. `E2-04` merged through pull request #64.
- [x] Decisions checked: D-23, D-63, D-85/D-97, D-105, D-155. No `Proposed`/`Pending` decision blocks the work.
- [x] Normative sections reviewed: `docs/SPECIFICATION.md` §3.1, §7 F-5, §11, §12; `docs/CONTRACTS.md` §11.5, §20.2, §20.10; `docs/TECHNICAL_PLAN.md` §4, §6; `docs/DECISION_BOARD.md`; `AGENTS.md`.
- [x] Expected verification: focused RED/GREEN tests on `:shared` and `:core:database`; the complete non-instrumented CI command; Objective-C golden header parity.
- [x] Human review gates identified before work: gated paths `core/database/**`, `core/auth/**`; gated topics authentication, module boundaries/dependency rules, Swift-facing API surface.
- [x] Rule 0 acknowledged: owner conversation is Spanish (es-ES); repository artifacts are technical English.

## In-Progress Checkpoint

- Date: 2026-09-10
- Branch and base: `story/E2-05-sign-out-and-account-deletion` from `origin/main` at `b521bda`
- Current phase and latest commit: RED for the second owner review round, on top of the reviewed head
  `2230346`. Published history is not rewritten; this round appends commits.
- Push and pull-request status: pull request #65 is open and under the owner's gated review.
- Completed since the previous checkpoint: the owner resolved seven decisions (see "Decisions
  Made"). This commit adds the executable RED coverage for them: the provider session ended after a
  successful D-23 deletion, the server operation never repeated, a typed retry surface, the
  local-data confirmation protocol, and account-deletion analytics restricted to the permanent path.
- Verification evidence and known failures: EXPECTED RED. The sources compile and 16 of 38
  `SessionDepartureTest` tests fail for the missing behavior. `FirebaseAuthClientTest`'s new
  `deleteAccountLeavesTheClientSessionForTheCallerToEnd` passes by design: it is a regression pin on
  the existing production behavior that the old test double masked by publishing `SignedOut` from
  `deleteAccount()`.
- Open decisions or blockers: none; the seven owner decisions are taken and are registered during
  REFACTOR.
- Exact next step: GREEN, then REFACTOR with the decision records and the documentation
  reconciliation.

## Scope Completed

- Ready Check, branch creation and a reconstructed RED, GREEN and REFACTOR sequence.
- `LocalDataClearDatabaseAccess` in `:core:database`: `pendingOutboxCount()`, `localSequenceNext()`
  and a single-transaction `clearAllLocalData()` that empties every local table the application owns
  and restores `local_sequence` to its canonical `(id = 0, next = 1)` initial state.
- `AccountDepartureHandler` seam and its `AccountDepartureCoordinator` implementation, which reports
  typed `PersistenceError` values rather than an opaque failure.
- `AccountDepartureFlow`: the F-5 state machine covering eligibility, the pending-sync warning, the
  three `DELETING` operations, the non-cancellable remote-to-local interval, retry after a failed
  local clear, and recent-login recovery.
- `SessionStateHolder` intents delegating to that flow, plus the new `startReauthentication(provider)`
  host intent and the typed `SessionUiState.pendingSyncCount`.
- Account-deletion analytics events (`AccountDeletionFailed` / `AccountDeletionCompleted`).
- `D-155` / ADR-0156 recording the decision to keep the Session state holder in `:shared`.
- The regenerated Objective-C golden header for the two public-contract additions.

## Acceptance Evidence

Backlog criteria, each with the test that proves it:

- Sign-out only for permanently authenticated users — `signOutIsRejectedForAnAnonymousSession` and
  `signOutIsRejectedForALocalOwner` assert the refusal reads nothing from the database;
  `signOutWithEmptyOutboxSignsOutAndClearsLocalData` covers the permitted path.
- Anonymous sessions get "delete local data" with two-step confirmation —
  `anonymousDeletionClearsLocalDataAndEndsTheSessionWithoutTheServerOperation` asserts the typed
  confirmation, the absence of any server call, and that the session is ended;
  `anonymousDeletionSurvivesRecreatingTheStateHolder` proves the result is not undone by an
  unchanged provider state.
- `ValidationWarning.PendingSyncBeforeSignOut(pendingCount)` then `Confirmation.DiscardPendingChanges`
  — `signOutWithPendingOutboxPublishesTheExactCountWithoutSigningOut` asserts the code, the
  confirmation and the exact count of 7 on `SessionUiState.pendingSyncCount`;
  `confirmingDiscardSignsOutAndClearsLocalData` covers the discard, and
  `discardIsIgnoredWithoutAPrecedingPendingSyncWarning` covers the unsolicited case.
- Account deletion follows §11.5 — `permanentDeletionCallsTheServerBeforeClearingLocalData` asserts
  the observed order `deleteAccount`, then `clearLocalData`, from one shared call log.
- Outbox rows dropped only after the server operation succeeds — the clear is the step after the
  server call in that same ordering assertion, and `serverDeletionFailurePreservesLocalDataAndReportsTheError`
  asserts no clear happens when the server fails.
- `AuthError.AccountDeletionRemoteFailed` preserves local data and does not report deletion —
  same test; the phase stays `PERMANENT` and no completion event is published.
- `user_settings` deleted and recreated from defaults —
  `clearAllLocalDataEmptiesEveryLocalTableInOneTransaction` asserts `user_settings` is emptied, and
  `SqlDelightSettingsRepository` recreates defaults on a missing row.
- Deletion reachable from settings — the `SessionStateHolder` intents are the settings entry point;
  the Settings surface itself is owned by E4-01.
- Session state holders stay in `:shared` — `D-155` / ADR-0156.

Review findings, each with the test that closes it:

- Eligibility — the two sign-out refusals above, `localOwnerDeletionClearsLocalDataOnly` (no server
  call, no sign-out), the anonymous path (no server call), and the permanent path (server first).
- Confirmation without an active request — `confirmationWithoutAPendingRequestDoesNothing`.
- Owner or session change between request and confirmation —
  `aSessionChangeBetweenRequestAndConfirmationDiscardsTheRequest` and
  `anOwnerKindChangeBetweenRequestAndConfirmationDiscardsTheRequest`.
- `DELETING` and reentrancy — `deletionEntersDeletingAndRefusesReentrantIntents` holds the server
  call open on a gate, asserts `DELETING` with `isBusy`, fires three further intents and asserts the
  server was still called exactly once.
- Never `SIGNED_OUT` or `AccountDeletionCompleted` on a failed clear —
  `aLocalClearFailureAfterSignOutNeverPublishesSignedOut` and
  `aLocalClearFailureAfterRemoteDeletionReportsNoCompletion`.
- Retry without a second remote deletion — `retryingAfterRemoteSuccessRepeatsOnlyTheLocalClear`
  asserts one `deleteAccount` call and two `clearLocalData` calls.
- Recent-login recovery — `aStaleLoginKeepsTheRequestAndResumesAfterReauthentication`,
  `aFailedReauthenticationKeepsTheRequestAndPreservesLocalData`,
  `cancellingReauthenticationAbandonsTheDeletionWithoutClearingLocalData` and
  `reauthenticationIsRejectedWithoutAStaleDeletionRequest`.
- Typed persistence failures — `aCountingFailurePreservesThePersistenceErrorAndDoesNotSignOut`
  asserts `PERSISTENCE.DATABASE_UNAVAILABLE` reaches the owner, not `AUTH.UNKNOWN`.
- Complete and atomic local cleanup — `clearAllLocalDataEmptiesEveryLocalTableInOneTransaction` now
  seeds `sync_cursor`, `quarantine` and the conversion snapshot as well;
  `clearAllLocalDataResetsAnAdvancedLocalSequence` and `aFailedClearRollsBackEveryDeletion`, which
  installs a `BEFORE DELETE` trigger on `quarantine` and asserts every earlier deletion rolled back.

## Out of Scope / Not Done

- The Settings UI surface (E4-01) that renders the sign-out and deletion controls.
- The native credential acquisition for re-authentication. The shared holder now owns the intent,
  the `AuthClient.reauthenticate()` call and the resumption; the platform picker itself is host UI
  owned by E4-01, exactly as for sign-in.
- The D-85 / ADR-0086 move of Session presentation into `:feature:session` (superseded by D-155).

## Files Changed

- `core/database/**/LocalDataClearDatabaseAccess.kt` and `database.sq` — the outbox count, the
  complete single-transaction clear and the `local_sequence` reset.
- `core/database/**/LocalDataClearDatabaseAccessTest.kt` — complete-cleanup, sequence-reset and
  transaction-rollback coverage.
- `shared/**/AccountDepartureFlow.kt` — the F-5 state machine (new).
- `shared/**/AccountDepartureCoordinator.kt` — the typed local-data seam implementation.
- `shared/**/StateHolders.kt` — the departure intents, `startReauthentication` and the re-auth
  routing of the native credential callbacks.
- `shared/**/UiModels.kt` — `SessionUiState.pendingSyncCount`.
- `shared/**/AppGraph.kt` — wires the departure coordinator into the session holder.
- `shared/**/SessionDepartureTest.kt` — the session-holder tests.
- `androidApp/**/OnboardingFlowTest.kt` — the added `SessionUiState` member.
- `shared/build/generated/objc-header/Shared.h.golden` — regenerated for the two public additions.
- `docs/CONTRACTS.md`, `docs/BACKLOG.md`, `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md`,
  `docs/TECHNICAL_PLAN.md`, `docs/adr/README.md`, `docs/adr/0156-*.md`, `AGENTS.md`,
  `docs/PROJECT_LOG.md` and this handoff.

## Decisions Made

- `D-155` (ADR-0156): keep `SessionStateHolder` and its `SessionUiState` / `SessionPhase` types in
  `:shared`, because `SessionStateHolder` depends on `:core:auth` and `:core:analytics`, which
  `docs/TECHNICAL_PLAN.md §4` forbids feature `presentation` from reaching. This is the owner's
  decision (option A of the three presented).
- **TDD history reconstruction, approved by the owner.** The original RED commit `6e07e6a` did not
  compile: `LocalDataClearDatabaseAccessTest` could not resolve `LocalDataClearDatabaseAccess` and
  `SessionDepartureTest` could not resolve `accountDeparture` or `AccountDepartureHandler`. A commit
  that fails to compile is not the executable behavioural RED that `AGENTS.md` requires, and the
  original GREEN commit had to modify the test file to make it compile, which inverts the TDD order.
  The owner was shown that evidence and explicitly approved rebuilding the branch into a compiling
  RED, a GREEN and a REFACTOR commit, republished with `--force-with-lease`. The RED commit contains
  behavior-free production seams so the tests compile and fail for missing behavior.
- Anonymous "delete local data" ends the provider session after the local clear. F-5 says the
  anonymous identity is unrecoverable and that this action is not account deletion, so the `D-23`
  server operation is not called; but leaving the session behind made the deletion look undone,
  because a recreated holder routed straight back to `ANONYMOUS` on the same UID. Ending the session
  locally is the smallest in-scope way to satisfy both. It is flagged for the owner below.
- The exact pending-outbox count is carried on the new typed `SessionUiState.pendingSyncCount`
  rather than on `UiMessage`, following the `D-145` precedent for `anonymousReminderIndex`: a typed
  value each host formats itself, and a change scoped to the session surface rather than to the
  message channel every feature shares.
- Rule 0 held for the whole story: every owner-facing reply was in Spanish (es-ES) and every
  repository artifact is in technical English. No violation occurred.

## Verification Run

- [x] Relevant tests pass
- [x] Lint passes (ktlint, detekt)
- [x] Coverage thresholds hold
- [x] Architecture checks pass
- [x] Contract check passes
- [x] Relevant builds pass (Android, iOS simulator, `Shared` framework)
- [x] Documentation updated if behaviour, decisions or models changed

Commands or checks run:

```text
git worktree add --detach <tmp> 6e07e6a && ./gradlew :core:database:testAndroidHostTest :shared:testAndroidHostTest
  EVIDENCE OF THE INVALID RED: compilation failed. LocalDataClearDatabaseAccessTest.kt:28,50,59
  "Unresolved reference 'LocalDataClearDatabaseAccess'"; SessionDepartureTest.kt:33,56,83,107,137,
  165,196 "No parameter with name 'accountDeparture'"; SessionDepartureTest.kt:266 "Unresolved
  reference 'AccountDepartureHandler'". This is what the reconstruction fixes.

./gradlew :core:database:testAndroidHostTest --tests ...LocalDataClearDatabaseAccessTest :shared:testAndroidHostTest --tests ...SessionDepartureTest
  EXPECTED RED on the reconstructed RED commit: the sources compile and the tests fail for the
  missing E2-05 behavior. PASS after GREEN: 5 database tests and 23 session tests.

./gradlew :shared:testAndroidHostTest --tests com.ruizurraca.carapp.SessionDepartureTest
  PASS: 23 tests, 0 failures.

./gradlew :core:database:testAndroidHostTest --tests com.ruizurraca.carapp.core.database.LocalDataClearDatabaseAccessTest
  PASS: 5 tests, 0 failures.

./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test
  BUILD SUCCESSFUL. An intermediate run reported LargeClass on SessionStateHolder and two
  ReturnCount findings; they were resolved by extracting AccountDepartureFlow, not suppressed.

./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64 && diff -u <golden> <generated>
  BUILD SUCCESSFUL and no diff. The golden was regenerated for exactly two public additions:
  SessionUiState.pendingSyncCount and startReauthentication(provider:). Every D-85 / D-97 exact
  Objective-C name is unchanged.

git diff --check
  Clean.
```

## Contract Impact

- Updated `docs/CONTRACTS.md §11.5`: the anonymous clear does not call the `D-23` operation; the
  clear is complete and atomic; `local_sequence` is reset to its canonical initial state rather than
  emptied.
- Updated `docs/CONTRACTS.md §20.10`: the `DELETING` phase is separated into its three operations,
  resolving the contradiction with the earlier sentence that put the `D-23` server operation on the
  anonymous path, with `docs/SPECIFICATION.md §7 F-5` as the behavioural authority. The departure
  contract now also states sign-out eligibility, the typed `pendingSyncCount`, typed persistence
  failures, request/confirmation pairing, the non-cancellable remote-to-local interval, retry after a
  failed clear, and the re-authentication intent.

## Decision Board Impact

- Updated `docs/DECISION_BOARD.md` with `D-155` (`Accepted`) and ADR-0156, mirrored identically in
  `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`.

## Shared-Write Modules Touched

- `:core:database` — expected for the local-data-clear operation; no concurrent agent is active.

## Project Log Entry

- [x] Entry appended to `docs/PROJECT_LOG.md`.

## Human Review Gate

- Applies: gated paths `core/database/**`, `core/auth/**`; gated topics authentication, module
  boundaries/dependency rules, Swift-facing API surface.

## Risks or Follow-ups

- **For the owner to confirm:** anonymous "delete local data" now ends the provider session after
  clearing. F-5 says "for an anonymous session there is no sign-out", which is about the action
  offered to the owner rather than the internal provider call, and without it the deletion is not
  effective across a restart. If the owner reads that sentence as forbidding the internal call too,
  the alternative is to leave the anonymous session in place and accept that a recreated holder
  returns to `ANONYMOUS` with empty data. The abandoned identity becomes an orphan handled by the
  existing `E3-11` cleanup; no new server operation was introduced.
- A local clear that fails after a successful remote step leaves the account deleted remotely while
  local data survives. That state is observable as `UNKNOWN` with the persistence error, the request
  is retained, and a retry repeats only the clear; but nothing forces the owner to retry within the
  session, and a process death at that point loses the retained request. Local data would then
  remain for an account that no longer exists remotely.
- `E1-14` and `E1-17` remain the documented pre-existing flakes that make a red `shared-tests` or
  `ios-simulator-build` job ambiguous.
