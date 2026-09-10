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
- Current phase and latest commit: RED for the third owner review round, appended on the reviewed
  head `fe7d367`. Published history is not rewritten and nothing is force-pushed.
- Push and pull-request status: pull request #65 is open and under the owner's gated review.
- Completed since the previous checkpoint: executable RED coverage for the six third-round findings —
  an unconfirmed pending-sync warning being treated as retained destructive work, the cancellable
  tail after a destructive step, retained work being replaceable by a new request, `DepartureRetry`
  being untruthful for the sign-out and anonymous kinds, `clearMessage()` not withdrawing a
  `DeleteLocalData` request, and the broken account-deletion analytics identity after
  re-authentication. The test doubles gained controllable gates on `signOut()` and the outbox count.
- Verification evidence and known failures: EXPECTED RED. `SessionDepartureIntegrityTest` compiles
  and 12 of its 14 tests fail on assertions, one per finding. The clearest is
  `aNewRequestCannotReplaceRetainedWorkOrRepeatTheServerDeletion`, which observes two `deleteAccount`
  calls where the contract allows exactly one. The two passing tests are the cases that already
  behaved correctly, kept as regression cover.
- Open decisions or blockers: none. The round is a correction of the accepted semantics of `D-156`
  to `D-163`; no new owner decision is introduced.
- Exact next step: GREEN, then the documentation reconciliation of `D-159` / ADR-0160, ADR-0162 and
  `docs/CONTRACTS.md §20.10`.

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

Backlog criteria:

- Sign-out only for permanently authenticated users — `signOutIsRejectedForAnAnonymousSession` and
  `signOutIsRejectedForALocalOwner` assert the refusal reads nothing from the database;
  `signOutWithEmptyOutboxSignsOutAndClearsLocalData` covers the permitted path.
- Anonymous sessions get "delete local data" with two-step confirmation —
  `anonymousDeletionAsksForTheLocalDataConfirmation` and
  `anonymousDeletionClearsLocalDataAndEndsTheSessionWithoutTheServerOperation`;
  `anonymousDeletionSurvivesRecreatingTheStateHolder` proves an unchanged provider state does not
  undo it.
- `ValidationWarning.PendingSyncBeforeSignOut(pendingCount)` then `Confirmation.DiscardPendingChanges`
  — `signOutWithPendingOutboxPublishesTheExactCountWithoutSigningOut` asserts the exact count 7 on
  `pendingSyncCount`; `confirmingDiscardSignsOutAndClearsLocalData` and
  `discardIsIgnoredWithoutAPrecedingPendingSyncWarning` cover the two confirmation cases.
- Account deletion follows §11.5 — `permanentDeletionEndsTheProviderSessionBeforeClearingLocalData`
  asserts the observed order `deleteAccount`, `signOut`, `clearLocalData` from one shared call log.
- Outbox rows dropped only after the server operation succeeds — the clear is the last step of that
  ordering, and `serverDeletionFailurePreservesLocalDataAndReportsTheError` asserts no clear on
  failure.
- `AuthError.AccountDeletionRemoteFailed` preserves local data and does not report deletion — same
  test; the phase stays `PERMANENT` and no completion event is published.
- `user_settings` deleted and recreated from defaults —
  `clearAllLocalDataEmptiesEveryLocalTableInOneTransaction`, plus `SqlDelightSettingsRepository`
  recreating defaults on a missing row.
- Account deletion accessible from settings — **as the callable application contract only** (`D-162`).
  E2-05 delivers the `SessionStateHolder` intents and the typed state a Settings screen invokes;
  `E4-01` delivers that screen and carries the store-compliance obligation. This criterion is NOT
  claimed as satisfied by a host surface this story does not ship.
- Session state holders stay in `:shared` — `D-155` / ADR-0156.

Second review round, each finding with the test that closes it:

- Provider session ended after a D-23 deletion —
  `permanentDeletionEndsTheProviderSessionBeforeClearingLocalData` and
  `permanentDeletionSurvivesRecreatingTheStateHolder`, with
  `FirebaseAuthClientTest.deleteAccountLeavesTheClientSessionForTheCallerToEnd` pinning the adapter
  contract that made the cleanup necessary.
- Server operation never repeated —
  `aProviderSignOutFailureAfterRemoteDeletionNeverRepeatsTheServerOperation` and
  `retryDepartureNeverRepeatsAServerDeletionThatSucceeded` both assert exactly one `deleteAccount`
  call across a failure and a retry.
- Retryable local clear through the public contract — `aLocalClearFailureOffersATypedRetry`,
  `retryDepartureRepeatsOnlyTheLocalClearAfterASignOut` (the sign-out path that had no reachable
  retry at all), `retryDepartureRepeatsTheLocalClearForALocalOwner` (the `DELETE_LOCAL` path whose
  request was being discarded) and `retryDepartureDoesNothingWithoutRetainedWork`.
- Confirmation semantics — `localOwnerDeletionAsksForTheLocalDataConfirmation`,
  `anonymousDeletionAsksForTheLocalDataConfirmation`,
  `localDataDeletionWithPendingOutboxDiscardsFirstThenConfirmsDestructively`,
  `theDestructiveLocalDataConfirmationIsNotAcceptedBeforeTheDiscard`, and
  `UiMessageMappingTests.testDepartureConfirmationsHaveTheirOwnDestructiveCopy` for the host copy.
- Analytics scope — `permanentDeletionEmitsTheAccountDeletionLifecycle` asserts the ordered pair
  `AccountDeletionStarted`, `AccountDeletionCompleted`; `localOwnerDeletionEmitsNoAccountDeletionAnalytics`
  and `anonymousDeletionEmitsNoAccountDeletionAnalytics` assert neither event on the other two kinds.

## Out of Scope / Not Done

- The Settings UI surface (E4-01) that renders the sign-out and deletion controls.
- The native credential acquisition for re-authentication. The shared holder now owns the intent,
  the `AuthClient.reauthenticate()` call and the resumption; the platform picker itself is host UI
  owned by E4-01, exactly as for sign-in.
- The `WARNING.PENDING_SYNC` host copy. Its text needs the count formatted into it, which only the
  rendering surface can do, so it belongs to `E4-01` together with the Settings screen. The two
  argument-free departure confirmations are mapped in both hosts by this story.
- Durable recovery of an interrupted departure across a process death. `D-163` accepts that residual
  risk, `docs/SECURITY.md` records it and `E2-09` closes it.
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

First round:

- `D-155` (ADR-0156): keep `SessionStateHolder` and its `SessionUiState` / `SessionPhase` types in
  `:shared`, because the holder depends on `:core:auth` and `:core:analytics`, which
  `docs/TECHNICAL_PLAN.md §4` forbids feature `presentation` from reaching. ADR-0156 also claimed the
  Objective-C golden header was unchanged; that was false and is corrected in place.
- **TDD history reconstruction, approved by the owner.** The original RED commit `6e07e6a` did not
  compile, so it was not the executable behavioural RED `AGENTS.md` requires, and the original GREEN
  had to modify the test file to make it compile. With the owner's explicit approval the branch was
  rebuilt into a compiling RED, a GREEN and a REFACTOR commit and republished with
  `--force-with-lease`.

Second round, all seven presented to the owner and selected by the owner before any code changed:

- `D-156` (ADR-0157): anonymous "delete local data" clears local data and then ends the provider
  session, without any server operation. Ratifies behaviour that had been implemented without a
  decision record.
- `D-157` (ADR-0158): the exact outbox count travels on the typed `SessionUiState.pendingSyncCount`,
  not on `UiMessage`. Also a ratification.
- `D-158` (ADR-0159): add `Confirmation.DeleteLocalData` and order the local and anonymous paths —
  count the outbox, ask `DiscardPendingChanges` when rows are pending, then ask `DeleteLocalData`.
- `D-159` (ADR-0160): expose the retry as `SessionUiState.pendingDepartureRetry` plus
  `SessionStateHolder.retryDeparture()`.
- `D-160` (ADR-0161): end the provider session as its own step of the permanent deletion, with its
  own flag, so no later failure can repeat the server operation.
- `D-161` (ADR-0162): the account-deletion analytics trio reports the permanent path only, with
  `AccountDeletionStarted` emitted when that deletion is confirmed.
- `D-162` (ADR-0163): E2-05's settings criterion is the callable application contract; the surface
  and the store-compliance obligation belong to `E4-01`.
- `D-163` (ADR-0164): accept the process-death window, record it in `docs/SECURITY.md`, and close it
  in the new `E2-09`.

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
  EVIDENCE OF THE INVALID FIRST RED: compilation failed. LocalDataClearDatabaseAccessTest.kt:28,50,59
  "Unresolved reference 'LocalDataClearDatabaseAccess'"; SessionDepartureTest.kt:33,56,83,107,137,
  165,196 "No parameter with name 'accountDeparture'"; SessionDepartureTest.kt:266 "Unresolved
  reference 'AccountDepartureHandler'".

./gradlew :shared:testAndroidHostTest --tests ...SessionDepartureTest (on RED 1add7ea)
  EXPECTED RED for the second round: the sources compile and 16 of 38 tests fail for the missing
  behavior.

./gradlew :shared:testAndroidHostTest :core:database:testAndroidHostTest :integration:firebase-auth:testAndroidHostTest :androidApp:testDebugUnitTest
  PASS in GREEN: :shared 128, :core:database 51, :integration:firebase-auth 48, :androidApp 31
  tests, 0 failures.

./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest testAndroidHostTest iosSimulatorArm64Test -x :integration:firebase-auth:iosSimulatorArm64Test -x :integration:firebase-firestore:iosSimulatorArm64Test -x :wiring:firebase:iosSimulatorArm64Test -x :composition:ios:iosSimulatorArm64Test
  BUILD SUCCESSFUL. Two intermediate runs failed and were fixed rather than suppressed: four detekt
  findings (two CyclomaticComplexMethod, one LargeClass on the test class, one ReturnCount), and
  AppErrorCodesTest.confirmationCoversExactlyTheFourDocumentedFlows, which is the executable mirror
  of the §20.2 Confirmation table and now pins five values with the reason they are not
  interchangeable.

./gradlew :composition:ios:linkDebugFrameworkIosSimulatorArm64 && diff -u <golden> <generated>
  BUILD SUCCESSFUL and no diff. The golden was regenerated for exactly four additions:
  DepartureRetry, SessionUiState.pendingDepartureRetry, retryDeparture() and
  Confirmation.deletelocaldata. No existing declaration is renamed or relocated.

git diff --check
  Clean.

Known flake: FuelEntryStateHolderTest failed once on a runTest timeout and passed on a clean
re-run. That is the documented E1-14 flake, not a regression from this work.
```

## Contract Impact

- `docs/CONTRACTS.md §11.5`: the permanent deletion gains the provider-session cleanup as an explicit
  step 6, with the local clear renumbered to step 7; the no-repeat rule for the server operation and
  the retained-retry rule are stated; and the process-death limit is stated plainly instead of
  implying resumability.
- `docs/CONTRACTS.md §20.2`: `Confirmation` gains `DeleteLocalData`; the `DeleteAccount` row now says
  it never authorises the clearing of local data, and the `DiscardPendingChanges` row says the
  destructive step still follows for a local-data deletion.
- `docs/CONTRACTS.md §20.9`: the account-deletion trio is marked as reporting the permanent path only.
- `docs/CONTRACTS.md §20.10`: the public `SessionStateHolder` declaration gains `retryDeparture()`
  and `startReauthentication(provider)`; the canonical `SessionUiState` gains `pendingSyncCount` and
  `pendingDepartureRetry`, with `DepartureRetry` declared beside it; and the prose states the
  confirmation protocol, the analytics scope and the retry semantics.

## Decision Board Impact

- `docs/DECISION_BOARD.md` gains `D-156` through `D-163`, all `Accepted`, with ADR-0157 to ADR-0164,
  mirrored identically in `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and
  `docs/adr/README.md`. `contractCheck` asserts the four tables agree.
- ADR-0156 is corrected: it claimed the Objective-C golden header was unchanged, which was false.

## Shared-Write Modules Touched

- `:core:database` — expected for the local-data-clear operation; no concurrent agent is active.

## Project Log Entry

- [x] Entry appended to `docs/PROJECT_LOG.md`.

## Human Review Gate

- Applies: gated paths `core/database/**`, `core/auth/**`; gated topics authentication, module
  boundaries/dependency rules, Swift-facing API surface.

## Risks or Follow-ups

- **`E2-09` owns the process-death window.** A departure that dies between a successful remote step
  and a completed local clear leaves local data for an account that no longer exists remotely, and
  the retained retry dies with the process. `D-163` accepts this, `docs/SECURITY.md` records it, and
  no document may describe the departure as recoverable across process death until `E2-09` proves it
  with an executable recovery.
- **`E4-01` owns the Settings surface**, and therefore the store-compliance obligation that depends
  on it (`D-162`), plus the `WARNING.PENDING_SYNC` copy, whose text needs `pendingSyncCount`
  formatted into it.
- The remote replacement window of the departure is bounded by one local transaction, but a failed
  local clear still leaves the account deleted remotely while local data survives until the owner
  retries. The retry is now reachable and typed, which is the part this round fixed.
- `E1-14` and `E1-17` remain the documented pre-existing flakes that make a red `shared-tests` or
  `ios-simulator-build` job ambiguous. `E1-14` was observed once during this round.
