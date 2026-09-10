package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.database.AccountDepartureDatabaseAccess
import com.ruizurraca.carapp.core.database.DepartureOperationKind
import com.ruizurraca.carapp.core.database.DepartureOperationStep
import com.ruizurraca.carapp.core.database.LocalDataClearDatabaseAccess
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `E2-09`: an F-5 departure is persisted before its first destructive step and finished at the next
 * launch, so local data never survives for an account that no longer exists remotely.
 *
 * A relaunch is simulated the only way that is honest here: a brand new coordinator is built over
 * the same database, exactly as the app graph does at launch, with no in-memory state carried over.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountDepartureRecoveryTest {
    @Test
    fun aDepartureIsPersistedBeforeItsFirstDestructiveStepAndClearedOnSuccess() =
        runTest {
            val log = mutableListOf<String>()
            val authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession()), log = log)
            val departure = RecordingDeparture(pendingCount = 0, log = log)
            val holder = holder(authClient, departure)

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            assertEquals(listOf(DepartureOperationKind.DELETE_PERMANENT), departure.startedDepartures)
            // The marker exists before the D-23 call, and every step is recorded as it succeeds.
            assertEquals(
                listOf(
                    "startPersistedDeparture",
                    "deleteAccount",
                    "markDepartureStep:REMOTE_DELETION",
                    "signOut",
                    "markDepartureStep:SESSION_CLEANUP",
                    "clearLocalData",
                    "markDepartureStep:LOCAL_CLEAR",
                    "clearPersistedDeparture",
                ),
                log,
            )
            holder.close()
        }

    @Test
    fun aRelaunchFinishesAnInterruptedPermanentDeletionWithoutRepeatingTheServerCall() =
        runTest {
            val fixture = RecoveryFixture()
            // The state a process death left behind: the D-23 call succeeded and nothing else did.
            fixture.departureAccess.start(DepartureOperationKind.DELETE_PERMANENT, "permanent-owner")
            fixture.departureAccess.markStep(DepartureOperationStep.REMOTE_DELETION)
            fixture.seedOutboxRow()

            fixture.relaunch().resumePending()

            assertEquals(0, fixture.authClient.deleteAccountCalls)
            assertEquals(1, fixture.authClient.signOutCalls)
            assertEquals(0L, fixture.pendingOutboxCount())
            assertNull(fixture.departureAccess.load())
        }

    @Test
    fun aRelaunchFinishesAnInterruptedAnonymousDeletionByEndingTheSession() =
        runTest {
            val fixture = RecoveryFixture(AuthState.SignedIn(anonymousSession()))
            // An anonymous deletion clears first, so the session cleanup is what was interrupted.
            fixture.departureAccess.start(DepartureOperationKind.DELETE_ANONYMOUS, "anonymous-owner")
            fixture.departureAccess.markStep(DepartureOperationStep.LOCAL_CLEAR)

            fixture.relaunch().resumePending()

            assertEquals(1, fixture.authClient.signOutCalls)
            assertEquals(0, fixture.authClient.deleteAccountCalls)
            assertNull(fixture.departureAccess.load())
        }

    @Test
    fun aRelaunchFinishesAnInterruptedSignOutByClearingLocalData() =
        runTest {
            val fixture = RecoveryFixture()
            fixture.departureAccess.start(DepartureOperationKind.SIGN_OUT, "permanent-owner")
            fixture.departureAccess.markStep(DepartureOperationStep.SESSION_CLEANUP)
            fixture.seedOutboxRow()

            fixture.relaunch().resumePending()

            // The provider sign-out already succeeded and MUST NOT run again.
            assertEquals(0, fixture.authClient.signOutCalls)
            assertEquals(0L, fixture.pendingOutboxCount())
            assertNull(fixture.departureAccess.load())
        }

    @Test
    fun aRelaunchFinishesAnInterruptedLocalOwnerDeletion() =
        runTest {
            val fixture = RecoveryFixture(AuthState.SignedOut)
            fixture.departureAccess.start(DepartureOperationKind.DELETE_LOCAL, ownerUid = null)
            fixture.seedOutboxRow()

            fixture.relaunch().resumePending()

            assertEquals(0, fixture.authClient.signOutCalls)
            assertEquals(0L, fixture.pendingOutboxCount())
            assertNull(fixture.departureAccess.load())
        }

    /**
     * A permanent deletion whose `D-23` call never recorded success has nothing destructive to
     * finish, and the server operation MUST NOT be repeated or started without a confirmation. The
     * marker is dropped and the owner keeps their data and their account.
     */
    @Test
    fun aRelaunchDropsAPermanentDeletionWhoseServerCallNeverSucceeded() =
        runTest {
            val fixture = RecoveryFixture()
            fixture.departureAccess.start(DepartureOperationKind.DELETE_PERMANENT, "permanent-owner")
            fixture.seedOutboxRow()

            fixture.relaunch().resumePending()

            assertEquals(0, fixture.authClient.deleteAccountCalls)
            assertEquals(0, fixture.authClient.signOutCalls)
            assertEquals(1L, fixture.pendingOutboxCount())
            assertNull(fixture.departureAccess.load())
        }

    @Test
    fun aRelaunchWithNoInterruptedDepartureDoesNothing() =
        runTest {
            val fixture = RecoveryFixture()
            fixture.seedOutboxRow()

            fixture.relaunch().resumePending()

            assertEquals(0, fixture.authClient.signOutCalls)
            assertEquals(0, fixture.authClient.deleteAccountCalls)
            assertEquals(1L, fixture.pendingOutboxCount())
        }
}

/** One database shared across simulated launches, with a fresh coordinator per launch. */
private class RecoveryFixture(
    initialState: AuthState = AuthState.SignedIn(permanentSession()),
) {
    private val handle = InMemoryDatabaseFactory().create()
    private val clearAccess = LocalDataClearDatabaseAccess(handle.database)

    val departureAccess = AccountDepartureDatabaseAccess(handle.database)
    val authClient = DepartureAuthClient(initialState)

    suspend fun seedOutboxRow() {
        handle.database.databaseQueries.coalesceOutbox("VEHICLE", "vehicle-1", "{}", 1)
    }

    suspend fun pendingOutboxCount(): Long = clearAccess.pendingOutboxCount()

    fun relaunch(): AccountDepartureCoordinator =
        AccountDepartureCoordinator(
            databaseAccess = clearAccess,
            departureAccess = departureAccess,
            authClient = authClient,
        )
}
