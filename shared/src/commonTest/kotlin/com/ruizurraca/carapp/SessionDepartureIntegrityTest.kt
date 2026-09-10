package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.DeletionFailureReason
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.testing.RecordingAnalyticsTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The third-round review of the F-5 departure: an unconfirmed pending-sync warning is not retained
 * destructive work, the tail after a destructive step is not cancellable, retained work cannot be
 * replaced, `DepartureRetry` is truthful for every kind, a dismissed local-data confirmation is
 * withdrawn, and the account-deletion analytics identity survives re-authentication.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionDepartureIntegrityTest {
    // ------------------- an unconfirmed warning is not retained destructive work

    @Test
    fun anUnconfirmedPendingSyncWarningOffersNoRetry() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 3)
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()

            assertNull(holder.state.value.pendingDepartureRetry)

            holder.retryDeparture()
            advanceUntilIdle()

            // Nothing destructive was ever authorised, so there is nothing to retry.
            assertEquals(0, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)
            holder.close()
        }

    @Test
    fun anUnconfirmedPendingSyncWarningDoesNotSuppressAuthStateChanges() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 3)
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()
            authClient.emit(AuthState.SignedOut)
            advanceUntilIdle()

            // Only a departure that has begun owns the published state.
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun aSessionChangeAfterAPendingSyncWarningRefusesTheDiscard() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 3)
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()
            authClient.emit(AuthState.SignedIn(permanentSession(uid = "another-owner")))
            advanceUntilIdle()
            holder.confirmSignOut(Confirmation.DiscardPendingChanges)
            advanceUntilIdle()

            // A request raised for owner A can never sign out owner B.
            assertEquals(0, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)
            holder.close()
        }

    @Test
    fun aSessionChangeWhileCountingAnEmptyOutboxAbortsTheSignOut() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val gate = CompletableDeferred<Unit>()
            departure.countGate = gate
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()
            assertEquals(1, departure.countCalls)

            authClient.emit(AuthState.SignedIn(permanentSession(uid = "another-owner")))
            gate.complete(Unit)
            advanceUntilIdle()

            // The owner is re-checked after the asynchronous count, before anything destructive.
            assertEquals(0, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)
            holder.close()
        }

    // ------------------------------------------- the post-destructive tail holds

    @Test
    fun cancellationAfterRemoteDeletionStillEndsTheSessionAndClears() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val gate = CompletableDeferred<Unit>()
            authClient.signOutGate = gate
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(1, authClient.deleteAccountCalls)
            assertEquals(1, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)

            holder.close()
            gate.complete(Unit)
            advanceUntilIdle()

            // The account is already gone remotely; the tail MUST finish anyway.
            assertEquals(AuthState.SignedOut, authClient.authState.value)
            assertEquals(1, departure.clearCalls)
            assertEquals(1, authClient.deleteAccountCalls)
        }

    @Test
    fun cancellationAfterAnAnonymousClearStillEndsTheSession() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(anonymousSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val gate = CompletableDeferred<Unit>()
            authClient.signOutGate = gate
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(1, departure.clearCalls)
            assertEquals(1, authClient.signOutCalls)

            holder.close()
            gate.complete(Unit)
            advanceUntilIdle()

            // The local data is already gone; leaving the anonymous session alive would make the
            // deletion look undone on the next launch.
            assertEquals(AuthState.SignedOut, authClient.authState.value)
            assertEquals(1, departure.clearCalls)
        }

    // ------------------------------------------------ retained work is protected

    @Test
    fun aNewRequestCannotReplaceRetainedWorkOrRepeatTheServerDeletion() =
        runTest {
            val authClient =
                DepartureAuthClient(
                    AuthState.SignedIn(permanentSession()),
                    signOutResult = Outcome.Err(AuthError.NetworkUnavailable),
                )
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(1, authClient.deleteAccountCalls)
            assertEquals(DepartureRetry.SESSION_CLEANUP, holder.state.value.pendingDepartureRetry)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()
            holder.requestSignOut()
            advanceUntilIdle()
            holder.confirmSignOut(Confirmation.DiscardPendingChanges)
            advanceUntilIdle()

            assertEquals(1, authClient.deleteAccountCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(DepartureRetry.SESSION_CLEANUP, holder.state.value.pendingDepartureRetry)
            holder.close()
        }

    // --------------------------------------- DepartureRetry is truthful per kind

    @Test
    fun aPermanentDeletionWhoseSessionCleanupFailsRetriesOnlyThatStep() =
        runTest {
            val authClient =
                DepartureAuthClient(
                    AuthState.SignedIn(permanentSession()),
                    signOutResult = Outcome.Err(AuthError.NetworkUnavailable),
                )
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(DepartureRetry.SESSION_CLEANUP, holder.state.value.pendingDepartureRetry)

            authClient.signOutResult = Outcome.Ok(Unit)
            holder.retryDeparture()
            advanceUntilIdle()

            assertEquals(1, authClient.deleteAccountCalls)
            assertEquals(2, authClient.signOutCalls)
            assertEquals(1, departure.clearCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            assertNull(holder.state.value.pendingDepartureRetry)
            holder.close()
        }

    @Test
    fun aSignOutWhoseSessionCleanupFailsOffersASessionCleanupRetry() =
        runTest {
            val authClient =
                DepartureAuthClient(
                    AuthState.SignedIn(permanentSession()),
                    signOutResult = Outcome.Err(AuthError.NetworkUnavailable),
                )
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()

            assertEquals(1, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(DepartureRetry.SESSION_CLEANUP, holder.state.value.pendingDepartureRetry)

            authClient.signOutResult = Outcome.Ok(Unit)
            holder.retryDeparture()
            advanceUntilIdle()

            assertEquals(2, authClient.signOutCalls)
            assertEquals(1, departure.clearCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            assertNull(holder.state.value.pendingDepartureRetry)
            holder.close()
        }

    @Test
    fun anAnonymousDeletionWhoseSessionCleanupFailsRetriesOnlyTheSessionCleanup() =
        runTest {
            val authClient =
                DepartureAuthClient(
                    AuthState.SignedIn(anonymousSession()),
                    signOutResult = Outcome.Err(AuthError.NetworkUnavailable),
                )
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(1, departure.clearCalls)
            assertEquals(DepartureRetry.SESSION_CLEANUP, holder.state.value.pendingDepartureRetry)

            authClient.signOutResult = Outcome.Ok(Unit)
            holder.retryDeparture()
            advanceUntilIdle()

            // The local clear already succeeded and MUST NOT run a second time.
            assertEquals(1, departure.clearCalls)
            assertEquals(2, authClient.signOutCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            assertNull(holder.state.value.pendingDepartureRetry)
            holder.close()
        }

    @Test
    fun aLocalOwnerClearFailureOffersALocalClearRetry() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedOut)
            val departure =
                RecordingDeparture(
                    pendingCount = 0,
                    clearResult = Outcome.Err(PersistenceError.TransactionFailed),
                )
            val holder = localOwnerHolder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(DepartureRetry.LOCAL_CLEAR, holder.state.value.pendingDepartureRetry)
            holder.close()
        }

    // ------------------------------------- a dismissed confirmation is withdrawn

    @Test
    fun dismissingTheLocalDataConfirmationWithdrawsALocalOwnerRequest() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedOut)
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = localOwnerHolder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            assertEquals(
                Confirmation.DeleteLocalData,
                holder.state.value.message
                    ?.confirmation,
            )

            holder.clearMessage()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(0, departure.clearCalls)
            assertEquals(0, authClient.signOutCalls)
            holder.close()
        }

    @Test
    fun dismissingTheLocalDataConfirmationWithdrawsAnAnonymousRequest() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(anonymousSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            assertEquals(
                Confirmation.DeleteLocalData,
                holder.state.value.message
                    ?.confirmation,
            )

            holder.clearMessage()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(0, departure.clearCalls)
            assertEquals(0, authClient.signOutCalls)
            holder.close()
        }

    // ------------------------------------------------ the analytics identity holds

    @Test
    fun aDeletionResumedAfterReauthenticationEmitsANewStarted() =
        runTest {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = true)
            val authClient =
                DepartureAuthClient(
                    AuthState.SignedIn(permanentSession()),
                    deleteResult = Outcome.Err(AuthError.RequiresRecentLogin),
                )
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure, tracker)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            authClient.deleteResult = Outcome.Ok(Unit)
            holder.startReauthentication(AuthProvider.GOOGLE)
            holder.completeGoogleSignIn("fresh-id-token", null)
            advanceUntilIdle()

            // ADR-0162: started attempts equal completed plus failed attempts, so the resumed D-23
            // attempt is a new start rather than a continuation of the failed one.
            assertEquals(
                listOf(
                    AnalyticsEvent.AccountDeletionStarted,
                    AnalyticsEvent.AccountDeletionFailed(DeletionFailureReason.REQUIRES_RECENT_LOGIN),
                    AnalyticsEvent.AccountDeletionStarted,
                    AnalyticsEvent.AccountDeletionCompleted,
                ),
                tracker.events,
            )
            holder.close()
        }
}
