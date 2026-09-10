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
import com.ruizurraca.carapp.core.database.DepartureOperationKind
import com.ruizurraca.carapp.core.database.DepartureOperationStep
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

/**
 * Shared doubles for the F-5 departure tests. `DepartureAuthClient` is deliberately faithful to
 * `FirebaseAuthClient`: `deleteAccount()` runs the D-23 operation and returns Ok without signing
 * out, so a test cannot accidentally assume a cleanup the production client never performs.
 */
internal fun anonymousSession(uid: String = "anonymous-owner"): AuthSession =
    AuthSession(uid = uid, isAnonymous = true, providers = setOf(AuthProvider.ANONYMOUS))

internal fun permanentSession(uid: String = "permanent-owner"): AuthSession =
    AuthSession(uid = uid, isAnonymous = false, providers = setOf(AuthProvider.GOOGLE))

internal fun kotlinx.coroutines.CoroutineScope.holder(
    authClient: DepartureAuthClient,
    departure: RecordingDeparture,
    analyticsTracker: RecordingAnalyticsTracker? = null,
): SessionStateHolder =
    SessionStateHolder(
        scope = this,
        authClient = authClient,
        accountDeparture = departure,
        analyticsTracker = analyticsTracker,
    )

/**
 * A local owner is the published `SessionPhase.LOCAL`, which only a refused anonymous sign-in can
 * produce: there is no Firebase session behind it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun kotlinx.coroutines.test.TestScope.localOwnerHolder(
    authClient: DepartureAuthClient,
    departure: RecordingDeparture,
    analyticsTracker: RecordingAnalyticsTracker? = null,
): SessionStateHolder {
    val holder =
        SessionStateHolder(
            scope = this,
            authClient = authClient,
            accountDeparture = departure,
            analyticsTracker = analyticsTracker,
        )
    holder.startAnonymousSignIn()
    advanceUntilIdle()
    check(holder.state.value.phase == SessionPhase.LOCAL) { "expected a local owner" }
    return holder
}

internal class DepartureAuthClient(
    initialState: AuthState,
    var deleteResult: Outcome<Unit, AuthError> = Outcome.Ok(Unit),
    var reauthenticateResult: Outcome<AuthSession, AuthError> = Outcome.Ok(permanentSession()),
    var signOutResult: Outcome<Unit, AuthError> = Outcome.Ok(Unit),
    private val log: MutableList<String> = mutableListOf(),
) : AuthClient {
    private val mutableAuthState = MutableStateFlow(initialState)
    override val authState: StateFlow<AuthState> = mutableAuthState

    var signOutCalls = 0
    var deleteAccountCalls = 0
    var reauthenticateCalls = 0

    /** Holds `deleteAccount()` open so a test can observe or interrupt the step that follows it. */
    var deleteGate: CompletableDeferred<Unit>? = null

    /** Holds `signOut()` open after it has been entered, for the cancellation tests of `D-160`. */
    var signOutGate: CompletableDeferred<Unit>? = null

    fun emit(state: AuthState) {
        mutableAuthState.value = state
    }

    override suspend fun signInAnonymously(): Outcome<AuthSession, AuthError> =
        Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun signInWithCredential(
        credential: NativeAuthCredential,
        allowUidChange: Boolean,
    ): Outcome<AuthSession, AuthError> = Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> =
        Outcome.Err(AuthError.ProviderUnavailable)

    override suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError> {
        reauthenticateCalls += 1
        return reauthenticateResult
    }

    override suspend fun signOut(): Outcome<Unit, AuthError> {
        signOutCalls += 1
        log += "signOut"
        signOutGate?.await()
        val result = signOutResult
        if (result is Outcome.Ok) mutableAuthState.value = AuthState.SignedOut
        return result
    }

    // Production-faithful: FirebaseAuthClient.deleteAccount() calls the D-23 Admin operation and
    // returns Ok. It does NOT sign out and does NOT publish SignedOut, so the persisted client
    // session outlives it. Emitting SignedOut here would hide the cleanup the flow owes.
    override suspend fun deleteAccount(): Outcome<Unit, AuthError> {
        deleteAccountCalls += 1
        log += "deleteAccount"
        deleteGate?.await()
        return deleteResult
    }
}

internal class RecordingDeparture(
    private val pendingCount: Int,
    var countResult: Outcome<Int, AppError>? = null,
    var clearResult: Outcome<Unit, AppError> = Outcome.Ok(Unit),
    private val log: MutableList<String> = mutableListOf(),
) : AccountDepartureHandler {
    var clearCalls = 0
    var countCalls = 0
    val startedDepartures = mutableListOf<DepartureOperationKind>()
    val markedSteps = mutableListOf<DepartureOperationStep>()
    var clearedDepartures = 0

    /** Holds the outbox count open so a test can change the session while it is in flight. */
    var countGate: CompletableDeferred<Unit>? = null

    override suspend fun pendingOutboxCount(): Outcome<Int, AppError> {
        countCalls += 1
        countGate?.await()
        return countResult ?: Outcome.Ok(pendingCount)
    }

    override suspend fun clearLocalData(): Outcome<Unit, AppError> {
        clearCalls += 1
        log += "clearLocalData"
        return clearResult
    }

    override suspend fun startPersistedDeparture(
        kind: DepartureOperationKind,
        ownerUid: String?,
    ): Outcome<Unit, AppError> {
        startedDepartures += kind
        log += "startPersistedDeparture"
        return Outcome.Ok(Unit)
    }

    override suspend fun markDepartureStep(step: DepartureOperationStep): Outcome<Unit, AppError> {
        markedSteps += step
        log += "markDepartureStep:$step"
        return Outcome.Ok(Unit)
    }

    override suspend fun clearPersistedDeparture(): Outcome<Unit, AppError> {
        clearedDepartures += 1
        log += "clearPersistedDeparture"
        return Outcome.Ok(Unit)
    }
}
