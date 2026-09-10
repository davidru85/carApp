package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.testing.RecordingAnalyticsTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDepartureTest {
    // ---------------------------------------------------------------- sign-out

    @Test
    fun signOutWithEmptyOutboxSignsOutAndClearsLocalData() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()

            assertEquals(1, authClient.signOutCalls)
            assertEquals(1, departure.clearCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            assertFalse(holder.state.value.isBusy)
            holder.close()
        }

    @Test
    fun signOutIsRejectedForAnAnonymousSession() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(anonymousSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()

            assertEquals(0, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(0, departure.countCalls)
            assertEquals(
                AuthError.ProviderUnavailable.code,
                holder.state.value.message
                    ?.code,
            )
            assertEquals(SessionPhase.ANONYMOUS, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun signOutIsRejectedForALocalOwner() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedOut)
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = localOwnerHolder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()

            assertEquals(0, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(
                AuthError.ProviderUnavailable.code,
                holder.state.value.message
                    ?.code,
            )
            holder.close()
        }

    @Test
    fun signOutWithPendingOutboxPublishesTheExactCountWithoutSigningOut() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 7)
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()

            val state = holder.state.value
            assertEquals(0, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(Confirmation.DiscardPendingChanges, state.message?.confirmation)
            assertEquals("WARNING.PENDING_SYNC", state.message?.code)
            assertEquals(7, state.pendingSyncCount)
            assertEquals(SessionPhase.PERMANENT, state.phase)
            holder.close()
        }

    @Test
    fun confirmingDiscardSignsOutAndClearsLocalData() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 2)
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()
            holder.confirmSignOut(Confirmation.DiscardPendingChanges)
            advanceUntilIdle()

            assertEquals(1, authClient.signOutCalls)
            assertEquals(1, departure.clearCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            assertNull(holder.state.value.pendingSyncCount)
            holder.close()
        }

    @Test
    fun discardIsIgnoredWithoutAPrecedingPendingSyncWarning() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.confirmSignOut(Confirmation.DiscardPendingChanges)
            advanceUntilIdle()

            assertEquals(0, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(SessionPhase.PERMANENT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun aCountingFailurePreservesThePersistenceErrorAndDoesNotSignOut() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure =
                RecordingDeparture(
                    pendingCount = 0,
                    countResult = Outcome.Err(PersistenceError.DatabaseUnavailable),
                )
            val holder = holder(authClient, departure)

            holder.requestSignOut()
            advanceUntilIdle()

            assertEquals(0, authClient.signOutCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(
                PersistenceError.DatabaseUnavailable.code,
                holder.state.value.message
                    ?.code,
            )
            holder.close()
        }

    // ----------------------------------------------------------- deletion paths

    @Test
    fun localOwnerDeletionClearsLocalDataOnly() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedOut)
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = localOwnerHolder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(1, departure.clearCalls)
            assertEquals(0, authClient.deleteAccountCalls)
            assertEquals(0, authClient.signOutCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun anonymousDeletionClearsLocalDataAndEndsTheSessionWithoutTheServerOperation() =
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

            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(1, departure.clearCalls)
            assertEquals(0, authClient.deleteAccountCalls)
            // The provider session is ended so the deletion survives a recreated holder.
            assertEquals(1, authClient.signOutCalls)
            assertEquals(AuthState.SignedOut, authClient.authState.value)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun anonymousDeletionSurvivesRecreatingTheStateHolder() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(anonymousSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()
            holder.close()

            val recreated = holder(authClient, RecordingDeparture(pendingCount = 0))
            advanceUntilIdle()

            assertEquals(SessionPhase.SIGNED_OUT, recreated.state.value.phase)
            recreated.close()
        }

    @Test
    fun permanentDeletionCallsTheServerBeforeClearingLocalData() =
        runTest {
            val log = mutableListOf<String>()
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()), log = log)
            val departure = RecordingDeparture(pendingCount = 0, log = log)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            // The durable marker writes share this log; this assertion is about the destructive
            // steps and their order. `AccountDepartureRecoveryTest` pins the marker writes.
            val destructive = log.filter { it == "deleteAccount" || it == "signOut" || it == "clearLocalData" }
            // `D-160` inserts the provider-session cleanup between the two.
            assertEquals(listOf("deleteAccount", "signOut", "clearLocalData"), destructive)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun deletionEntersDeletingAndRefusesReentrantIntents() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val gate = CompletableDeferred<Unit>()
            authClient.deleteGate = gate
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(SessionPhase.DELETING, holder.state.value.phase)
            assertTrue(holder.state.value.isBusy)

            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            holder.requestSignOut()
            holder.requestDeleteAccount()
            advanceUntilIdle()
            assertEquals(1, authClient.deleteAccountCalls)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, authClient.deleteAccountCalls)
            assertEquals(1, departure.clearCalls)
            assertEquals(SessionPhase.SIGNED_OUT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun confirmationWithoutAPendingRequestDoesNothing() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(0, authClient.deleteAccountCalls)
            assertEquals(0, departure.clearCalls)
            assertEquals(SessionPhase.PERMANENT, holder.state.value.phase)
            holder.close()
        }

    @Test
    fun aSessionChangeBetweenRequestAndConfirmationDiscardsTheRequest() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            authClient.emit(AuthState.SignedIn(permanentSession(uid = "another-owner")))
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(0, authClient.deleteAccountCalls)
            assertEquals(0, departure.clearCalls)
            holder.close()
        }

    @Test
    fun anOwnerKindChangeBetweenRequestAndConfirmationDiscardsTheRequest() =
        runTest {
            val authClient = DepartureAuthClient(AuthState.SignedIn(anonymousSession()))
            val departure = RecordingDeparture(pendingCount = 0)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            // The anonymous identity was converted before the owner confirmed.
            authClient.emit(AuthState.SignedIn(permanentSession(uid = "anonymous-owner")))
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(0, departure.clearCalls)
            assertEquals(0, authClient.deleteAccountCalls)
            holder.close()
        }
}
