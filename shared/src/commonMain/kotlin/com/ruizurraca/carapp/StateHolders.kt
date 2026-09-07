package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.feature.session.domain.AnonymousReminderRepository
import com.ruizurraca.carapp.feature.session.domain.dueAnonymousReminderIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SessionStateHolder internal constructor(
    private val scope: CoroutineScope? = null,
    private val authClient: AuthClient? = null,
    // Not exported: the constructor is internal, so this stays out of the Swift-facing surface.
    private val onLocalStartAccepted: () -> Unit = {},
    private val clock: AppClock? = null,
    private val anonymousReminders: AnonymousReminderRepository? = null,
) {
    private var closed = false
    private var operationJob: Job? = null
    private var reminderJob: Job? = null
    private var awaitingRestoredSession = false
    private var activePermanentProvider: AuthProvider? = null
    private val mutableState =
        MutableStateFlow(authClient?.authState?.value.toSessionUiState())
    val state: StateFlow<SessionUiState> = mutableState
    private val authStateJob =
        if (scope != null && authClient != null) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                authClient.authState.collect { authState ->
                    if (closed) return@collect
                    val next = authState.toSessionUiState()
                    // A session that is still the same anonymous one keeps the notice it is
                    // already showing; every other phase drops it.
                    mutableState.value =
                        if (next.phase == SessionPhase.ANONYMOUS) {
                            next.copy(anonymousReminderIndex = mutableState.value.anonymousReminderIndex)
                        } else {
                            next
                        }
                    // Permanent sign-in and successful linking end the schedule (§11.3). The
                    // in-flight evaluation is cancelled first, so it cannot write a position back
                    // after the clear.
                    if (authState is AuthState.SignedIn && !authState.session.isAnonymous) {
                        reminderJob?.cancel()
                        anonymousReminders?.clear()
                    }
                    completePendingEvaluation(authState)
                }
            }
        } else {
            null
        }

    fun startAnonymousSignIn() {
        if (closed) return
        val operationScope = scope ?: return
        val client = authClient ?: return
        operationJob?.cancel()
        activePermanentProvider = null
        mutableState.value = mutableState.value.copy(isBusy = true, message = null)
        operationJob =
            operationScope.launch {
                mutableState.value =
                    when (val result = client.signInAnonymously()) {
                        is Outcome.Ok -> {
                            result.value.toSessionUiState()
                        }

                        is Outcome.Err -> {
                            // The owner asked to continue without an account and got a local session.
                            // That choice is the signal returning connectivity retries on, and it is
                            // the only one a device with no rows yet can offer (`D-124`).
                            onLocalStartAccepted()
                            SessionUiState(
                                phase = SessionPhase.LOCAL,
                                providers = emptyList(),
                                isBusy = false,
                                message =
                                    UiMessage(
                                        id = LOCAL_AUTH_MESSAGE_ID,
                                        kind = UiMessageKind.WARNING,
                                        code = result.error.code,
                                        confirmation = null,
                                    ),
                                anonymousReminderIndex = null,
                            )
                        }
                    }
            }
    }

    fun startPermanentSignIn(provider: AuthProvider) {
        if (closed) return
        operationJob?.cancel()
        if (provider != AuthProvider.GOOGLE && provider != AuthProvider.APPLE) {
            activePermanentProvider = null
            publishError(AuthError.ProviderUnavailable)
            return
        }
        activePermanentProvider = provider
        mutableState.value = mutableState.value.copy(isBusy = true, message = null)
    }

    fun completeGoogleSignIn(
        idToken: String,
        accessToken: String?,
    ) {
        completePermanentSignIn(
            provider = AuthProvider.GOOGLE,
            credential = NativeAuthCredential.Google(idToken, accessToken),
        )
    }

    fun completeAppleSignIn(
        idToken: String,
        rawNonce: String,
    ) {
        completePermanentSignIn(
            provider = AuthProvider.APPLE,
            credential = NativeAuthCredential.Apple(idToken, rawNonce),
        )
    }

    fun failSignIn(reason: NativeSignInFailure) {
        if (closed) return
        operationJob?.cancel()
        operationJob = null
        activePermanentProvider = null
        mutableState.value =
            mutableState.value.copy(
                isBusy = false,
                message = reason.toReportableAuthError()?.toUiMessage(),
            )
    }

    /**
     * Evaluates the `D-62` reminder schedule on app launch and foreground return
     * (`docs/CONTRACTS.md §11.3`). It introduces no scheduler, alarm or operating-system
     * notification: the host calls it from its own foreground lifecycle.
     */
    fun evaluateAnonymousReminder() {
        if (closed || reminderJob?.isActive == true) return
        val operationScope = scope ?: return
        val session = anonymousSessionToEvaluate() ?: return
        reminderJob = operationScope.launch { publishDueReminder(session) }
    }

    /** Dismisses the reminder currently shown. Its index stays consumed. */
    fun dismissAnonymousReminder() {
        if (closed) return
        mutableState.value = mutableState.value.copy(anonymousReminderIndex = null)
    }

    fun startAccountConversion(provider: AuthProvider) = provider.let { Unit }

    fun confirmAccountConversion(confirmation: Confirmation) = confirmation.let { Unit }

    fun requestSignOut() = Unit

    fun confirmSignOut(confirmation: Confirmation) = confirmation.let { Unit }

    fun requestDeleteAccount() = Unit

    fun confirmDeleteAccount(confirmation: Confirmation) = confirmation.let { Unit }

    fun clearMessage() {
        if (closed) return
        mutableState.value = mutableState.value.copy(message = null)
    }

    fun close() {
        if (closed) return
        closed = true
        operationJob?.cancel()
        operationJob = null
        reminderJob?.cancel()
        reminderJob = null
        awaitingRestoredSession = false
        authStateJob?.cancel()
        activePermanentProvider = null
    }

    private fun completePermanentSignIn(
        provider: AuthProvider,
        credential: NativeAuthCredential,
    ) {
        if (closed) return
        if (activePermanentProvider != provider) {
            activePermanentProvider = null
            publishError(AuthError.ProviderUnavailable)
            return
        }
        activePermanentProvider = null
        val operationScope = scope
        val client = authClient
        if (operationScope == null || client == null) {
            publishError(AuthError.ProviderUnavailable)
            return
        }
        operationJob?.cancel()
        operationJob =
            operationScope.launch {
                mutableState.value =
                    when (val result = client.signInWithCredential(credential)) {
                        is Outcome.Ok -> {
                            result.value.toSessionUiState()
                        }

                        is Outcome.Err -> {
                            mutableState.value.copy(
                                isBusy = false,
                                message = result.error.toUiMessage(),
                            )
                        }
                    }
            }
    }

    /**
     * The anonymous session this evaluation applies to, or `null` when there is nothing to evaluate.
     *
     * An undetermined auth state is not "no session": on a cold start the provider may still be
     * restoring one (§11.1). The request is remembered and completed once by the auth-state
     * collector, so a launch evaluation is not lost until the next foreground return. That is the
     * same host-requested evaluation finishing late, not a second trigger: it is one-shot, and any
     * resolution other than an anonymous session consumes it without running it (`D-147`).
     */
    private fun anonymousSessionToEvaluate(): AuthSession? {
        val authState = authClient?.authState?.value ?: return null
        if (authState is AuthState.Unknown) {
            awaitingRestoredSession = true
            return null
        }
        // The schedule is disabled for the sentinel owner, signed-out and permanent sessions.
        return (authState as? AuthState.SignedIn)?.session?.takeIf { session -> session.isAnonymous }
    }

    private fun completePendingEvaluation(authState: AuthState) {
        if (!awaitingRestoredSession || authState is AuthState.Unknown) return
        awaitingRestoredSession = false
        if (authState is AuthState.SignedIn && authState.session.isAnonymous) {
            evaluateAnonymousReminder()
        }
    }

    /**
     * Persisting the index before publishing it is what consumes every lower pending reminder. A
     * notice shown before its index survived would come back on the next foreground return, and a
     * failure the owner cannot act on is not worth reporting for a non-blocking notice.
     */
    private suspend fun publishDueReminder(session: AuthSession) {
        val reminders = anonymousReminders ?: return
        val dueIndex = dueReminderIndexFor(session) ?: return
        if (reminders.recordShown(session.uid, dueIndex) is Outcome.Err) return
        // Re-read after the last suspension point. Cancellation is cooperative, so on a real
        // dispatcher the session can change while the position is being persisted, and an index
        // computed for an identity that is no longer current would be shown to the wrong owner.
        if (closed || !isCurrentSession(session.uid)) return
        mutableState.value = mutableState.value.copy(anonymousReminderIndex = dueIndex)
    }

    private fun isCurrentSession(anonymousUid: String): Boolean {
        val current = (authClient?.authState?.value as? AuthState.SignedIn)?.session ?: return false
        return current.isAnonymous && current.uid == anonymousUid
    }

    private suspend fun dueReminderIndexFor(session: AuthSession): Int? {
        val accountCreatedAt = session.createdAt
        val appClock = clock
        // An identity with no provider creation timestamp has no anchor to measure elapsed days
        // from, so it is left alone rather than measured from an invented origin.
        if (accountCreatedAt == null || appClock == null) return null
        val storedIndex = anonymousReminders?.lastShownIndex(session.uid)
        if (storedIndex !is Outcome.Ok) return null
        return dueAnonymousReminderIndex(
            accountCreatedAt = accountCreatedAt,
            now = appClock.now(),
            lastShownIndex = storedIndex.value,
        )
    }

    private fun publishError(error: AuthError) {
        mutableState.value =
            mutableState.value.copy(
                isBusy = false,
                message = error.toUiMessage(),
            )
    }
}

/**
 * A user-driven cancellation is a recoverable retry state, not a failure to report, so it maps to no
 * message. Every other closed native failure maps to the error the owner must see.
 */
private fun NativeSignInFailure.toReportableAuthError(): AuthError? =
    when (this) {
        NativeSignInFailure.CANCELLED -> null
        NativeSignInFailure.NETWORK -> AuthError.NetworkUnavailable
        NativeSignInFailure.CONFIGURATION -> AuthError.ProviderUnavailable
        NativeSignInFailure.NO_ACCOUNT_AVAILABLE -> AuthError.NoAccountAvailable
        NativeSignInFailure.UNKNOWN -> AuthError.Unknown
    }

private fun AuthError.toUiMessage(): UiMessage =
    UiMessage(
        id = AUTH_ERROR_MESSAGE_ID,
        kind = UiMessageKind.ERROR,
        code = code,
        confirmation = null,
    )

private fun AuthState?.toSessionUiState(): SessionUiState =
    when (this) {
        null,
        AuthState.Unknown,
        -> SessionUiState(SessionPhase.UNKNOWN, emptyList(), false, null, null)

        AuthState.SignedOut -> SessionUiState(SessionPhase.SIGNED_OUT, emptyList(), false, null, null)

        is AuthState.SignedIn -> session.toSessionUiState()
    }

private fun AuthSession.toSessionUiState(): SessionUiState =
    SessionUiState(
        phase = if (isAnonymous) SessionPhase.ANONYMOUS else SessionPhase.PERMANENT,
        providers = AuthProvider.entries.filter(providers::contains),
        isBusy = false,
        message = null,
        anonymousReminderIndex = null,
    )

private const val LOCAL_AUTH_MESSAGE_ID = 1L
private const val AUTH_ERROR_MESSAGE_ID = 2L

class SyncStateHolder internal constructor() {
    val state: StateFlow<SyncUiState> =
        MutableStateFlow(SyncUiState(SyncStatus.Idle, true, null))

    fun requestSync(reason: SyncTrigger) = reason.let { Unit }

    fun retryFailed() = Unit

    fun clearMessage() = Unit

    fun close() = Unit
}
