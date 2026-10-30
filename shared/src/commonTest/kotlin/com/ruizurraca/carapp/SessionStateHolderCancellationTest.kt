package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.testing.FakeAuthClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `E4-03` (`D-256`), `docs/CONTRACTS.md §14`: `close()` cancels the work the session holder owns.
 *
 * The holder owns no child scope - it launches its auth collector and its operations in the caller's
 * scope - so "owned work" is every coroutine it started there. Android closes it while
 * `viewModelScope` lives on, which is why the caller's job must stay active.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionStateHolderCancellationTest {
    @Test
    fun closeCancelsTheAuthCollectorAndAnInFlightSignIn() =
        runTest {
            val callerJob = Job()
            val holder =
                SessionStateHolder(
                    scope = CoroutineScope(callerJob + UnconfinedTestDispatcher(testScheduler)),
                    authClient = SuspendedAnonymousSignInAuthClient(),
                )
            holder.startAnonymousSignIn()
            advanceUntilIdle()
            assertTrue(
                callerJob.children.count { child -> child.isActive } >= 2,
                "precondition: the auth collector and the sign-in are both running",
            )

            holder.close()
            advanceUntilIdle()

            assertTrue(
                callerJob.children.none { child -> child.isActive },
                "close() must cancel every coroutine the session holder started",
            )
            callerJob.cancel()
        }

    @Test
    fun closeLeavesTheCallerScopeActive() =
        runTest {
            val callerJob = Job()
            val holder =
                SessionStateHolder(
                    scope = CoroutineScope(callerJob + UnconfinedTestDispatcher(testScheduler)),
                    authClient = SuspendedAnonymousSignInAuthClient(),
                )
            holder.startAnonymousSignIn()

            holder.close()

            assertTrue(callerJob.isActive, "close() releases the holder, never the scope its caller owns")
            callerJob.cancel()
        }
}

/** Signed out, with an anonymous sign-in that never answers, so it is in flight when `close()` runs. */
private class SuspendedAnonymousSignInAuthClient(
    private val delegate: FakeAuthClient = FakeAuthClient(),
) : AuthClient by delegate {
    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> = awaitCancellation()
}
