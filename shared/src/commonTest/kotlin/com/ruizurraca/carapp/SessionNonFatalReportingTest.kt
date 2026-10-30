package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.UnexpectedError
import com.ruizurraca.carapp.core.testing.FakeAuthClient
import com.ruizurraca.carapp.core.testing.RecordingCrashReporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `E4-04` (`D-243`): the session chokepoint of `docs/CONTRACTS.md §20.3.1`. Each recorded case is a
 * site where an `AppError` reaches a `UiMessage`; each negative case is an expected leaf the policy
 * MUST NOT record, reached through the site where it really occurs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionNonFatalReportingTest {
    private val unexpected = UnexpectedError(origin = ":shared", throwableClassName = "IllegalStateException")
    private val recordedOnce = listOf<Pair<AppError, Map<String, String>>>(unexpected to mapOf("code" to "UNEXPECTED"))
    private val crashReporter = RecordingCrashReporter()
    private val nonFatals = NonFatalReporting(crashReporter)

    // ------------------------------------------------------------------------------- recorded

    @Test
    fun aDepartureCountFailureCarryingAnUnexpectedErrorIsRecordedOnce() =
        runTest {
            val holder =
                SessionStateHolder(
                    scope = this,
                    authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession())),
                    accountDeparture = RecordingDeparture(pendingCount = 0, countResult = Outcome.Err(unexpected)),
                    nonFatals = nonFatals,
                )

            holder.requestSignOut()
            advanceUntilIdle()

            assertEquals(recordedOnce, crashReporter.nonFatals)
            holder.close()
        }

    @Test
    fun aFailedLocalClearCarryingAnUnexpectedErrorIsRecordedOnce() =
        runTest {
            val holder =
                SessionStateHolder(
                    scope = this,
                    authClient = DepartureAuthClient(AuthState.SignedOut),
                    accountDeparture = RecordingDeparture(pendingCount = 0, clearResult = Outcome.Err(unexpected)),
                    nonFatals = nonFatals,
                )
            // A refused anonymous sign-in is the only way to a local owner (`D-124`).
            holder.startAnonymousSignIn()
            advanceUntilIdle()
            check(holder.state.value.phase == SessionPhase.LOCAL) { "the fixture must reach a local owner" }

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteLocalData)
            advanceUntilIdle()

            assertEquals(recordedOnce, crashReporter.nonFatals)
            holder.close()
        }

    @Test
    fun aConversionFailureCarryingAnUnexpectedErrorIsRecordedOnce() =
        runTest {
            val holder = collidingConversionHolder(confirmResult = Outcome.Err(unexpected))

            holder.confirmAccountConversion(Confirmation.AdoptExistingAccount)
            advanceUntilIdle()

            assertEquals(recordedOnce, crashReporter.nonFatals)
            holder.close()
        }

    // --------------------------------------------------------------------------- not recorded

    @Test
    fun aRequiresRecentLoginDeletionFailureIsNotRecorded() =
        runTest {
            val holder =
                SessionStateHolder(
                    scope = this,
                    authClient =
                        DepartureAuthClient(
                            AuthState.SignedIn(permanentSession()),
                            deleteResult = Outcome.Err(AuthError.RequiresRecentLogin),
                        ),
                    accountDeparture = RecordingDeparture(pendingCount = 0),
                    nonFatals = nonFatals,
                )

            holder.requestDeleteAccount()
            advanceUntilIdle()
            holder.confirmDeleteAccount(Confirmation.DeleteAccount)
            advanceUntilIdle()

            check(
                holder.state.value.message
                    ?.code == AuthError.RequiresRecentLogin.code,
            ) {
                "the fixture must publish the stale-login failure"
            }
            assertEquals(emptyList(), crashReporter.nonFatals)
            holder.close()
        }

    @Test
    fun aCancelledPermanentSignInIsNotRecorded() =
        runTest {
            failPermanentSignIn(AuthError.Cancelled)

            assertEquals(emptyList(), crashReporter.nonFatals)
        }

    @Test
    fun aCredentialAlreadyInUseSignInFailureIsNotRecorded() =
        runTest {
            failPermanentSignIn(AuthError.CredentialAlreadyInUse)

            assertEquals(emptyList(), crashReporter.nonFatals)
        }

    @Test
    fun aConnectivityOnlyConversionFailureIsNotRecorded() =
        runTest {
            val holder = collidingConversionHolder(confirmResult = Outcome.Err(RemoteError.Unavailable))

            holder.confirmAccountConversion(Confirmation.AdoptExistingAccount)
            advanceUntilIdle()

            assertEquals(emptyList(), crashReporter.nonFatals)
            holder.close()
        }

    @Test
    fun aTypedPersistenceFailureIsNotRecorded() =
        runTest {
            val holder =
                SessionStateHolder(
                    scope = this,
                    authClient = DepartureAuthClient(AuthState.SignedIn(permanentSession())),
                    accountDeparture =
                        RecordingDeparture(
                            pendingCount = 0,
                            countResult = Outcome.Err(PersistenceError.DatabaseUnavailable),
                        ),
                    nonFatals = nonFatals,
                )

            holder.requestSignOut()
            advanceUntilIdle()

            assertEquals(emptyList(), crashReporter.nonFatals)
            holder.close()
        }

    private suspend fun TestScope.failPermanentSignIn(error: AuthError) {
        val holder =
            SessionStateHolder(
                scope = this,
                authClient = FakeAuthClient(sessionResult = Outcome.Err(error)),
                nonFatals = nonFatals,
            )
        holder.startPermanentSignIn(AuthProvider.GOOGLE)
        holder.completeGoogleSignIn("id-token", null)
        advanceUntilIdle()
        check(
            holder.state.value.message
                ?.code == error.code,
        ) { "the fixture must publish the sign-in failure" }
        holder.close()
    }

    private suspend fun TestScope.collidingConversionHolder(
        confirmResult: Outcome<AuthSession, AppError>,
    ): SessionStateHolder {
        val holder =
            SessionStateHolder(
                scope = this,
                authClient =
                    FakeAuthClient(
                        initialState = AuthState.SignedIn(anonymousSession()),
                        sessionResult = Outcome.Err(AuthError.CredentialAlreadyInUse),
                    ),
                accountConversion =
                    object : AccountConversionHandler {
                        override suspend fun confirm(
                            anonymousUid: String,
                            credential: NativeAuthCredential,
                        ): Outcome<AuthSession, AppError> = confirmResult
                    },
                nonFatals = nonFatals,
            )
        holder.startAccountConversion(AuthProvider.GOOGLE)
        holder.completeGoogleSignIn("colliding-id-token", null)
        advanceUntilIdle()
        check(
            holder.state.value.message
                ?.confirmation == Confirmation.AdoptExistingAccount,
        ) {
            "the fixture must reach the collision confirmation"
        }
        return holder
    }
}
