package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsTracker
import com.ruizurraca.carapp.core.analytics.ConversionFailureReason
import com.ruizurraca.carapp.core.analytics.toConversionFailureReason
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.auth.NativeAuthCredential
import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
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
    private val accountConversion: AccountConversionHandler? = null,
    private val analyticsTracker: AnalyticsTracker? = null,
) {
    private var closed = false
    private var operationJob: Job? = null
    private var reminderJob: Job? = null
    private var awaitingRestoredSession = false
    private var activePermanentProvider: AuthProvider? = null
    private var activeSignInKind: SignInKind? = null
    private var pendingCollision: PendingCollision? = null

    // The anonymous UID that produced the published reminder index, so the collector can tell a
    // re-emission of the same identity from a switch to a different one (§11.3).
    private var publishedReminderUid: String? = null
    private val mutableState =
        MutableStateFlow(authClient?.authState?.value.toSessionUiState())
    val state: StateFlow<SessionUiState> = mutableState
    private val authStateJob =
        if (scope != null && authClient != null) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                authClient.authState.collect { authState ->
                    if (closed) return@collect
                    val next = authState.toSessionUiState()
                    // A published index belongs to the anonymous UID that produced it. It survives
                    // a re-emission of that same identity and is dropped by every other transition,
                    // including a switch to a different anonymous identity (§11.3).
                    val incomingAnonymousUid =
                        (authState as? AuthState.SignedIn)
                            ?.session
                            ?.takeIf { session -> session.isAnonymous }
                            ?.uid
                    val carriesPublishedReminder = publishedReminderUid == incomingAnonymousUid
                    mutableState.value =
                        if (carriesPublishedReminder) {
                            next.copy(anonymousReminderIndex = mutableState.value.anonymousReminderIndex)
                        } else {
                            publishedReminderUid = null
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
        activeSignInKind = null
        clearPendingCollision()
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
        if (!provider.isNativePermanentProvider()) {
            activePermanentProvider = null
            activeSignInKind = null
            publishError(AuthError.ProviderUnavailable)
            return
        }
        clearPendingCollision()
        activePermanentProvider = provider
        activeSignInKind = SignInKind.PERMANENT
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
        val cancelledConversion = activeSignInKind == SignInKind.CONVERSION
        operationJob?.cancel()
        operationJob = null
        activePermanentProvider = null
        activeSignInKind = null
        clearPendingCollision()
        mutableState.value =
            mutableState.value.copy(
                isBusy = false,
                message = reason.toReportableAuthError()?.toUiMessage(),
            )
        if (cancelledConversion) {
            analyticsTracker?.track(
                AnalyticsEvent.AccountConversionFailed(
                    reason.toReportableAuthError()?.toConversionFailureReason() ?: ConversionFailureReason.CANCELLED,
                ),
            )
        }
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
        publishedReminderUid = null
        mutableState.value = mutableState.value.copy(anonymousReminderIndex = null)
    }

    fun startAccountConversion(provider: AuthProvider) {
        if (closed) return
        operationJob?.cancel()
        val session = (authClient?.authState?.value as? AuthState.SignedIn)?.session
        if (!provider.isNativePermanentProvider() || session?.isAnonymous != true) {
            activePermanentProvider = null
            activeSignInKind = null
            publishError(AuthError.ProviderUnavailable)
            return
        }
        clearPendingCollision()
        activePermanentProvider = provider
        activeSignInKind = SignInKind.CONVERSION
        mutableState.value = mutableState.value.copy(isBusy = true, message = null)
        analyticsTracker?.track(AnalyticsEvent.AccountConversionStarted)
    }

    fun confirmAccountConversion(confirmation: Confirmation) {
        if (closed || confirmation != Confirmation.AdoptExistingAccount) return
        val pending = pendingCollision ?: return
        val operationScope = scope ?: return
        val conversion = accountConversion ?: return
        clearPendingCollision()
        mutableState.value = mutableState.value.copy(isBusy = true, message = null)
        operationJob?.cancel()
        operationJob =
            operationScope.launch {
                mutableState.value =
                    confirmedConversionState(conversion.confirm(pending.anonymousUid, pending.credential))
            }
    }

    private fun confirmedConversionState(result: Outcome<AuthSession, AppError>): SessionUiState =
        when (result) {
            is Outcome.Ok -> {
                analyticsTracker?.track(AnalyticsEvent.AccountConversionCompleted)
                result.value.toSessionUiState()
            }

            is Outcome.Err -> {
                analyticsTracker?.track(
                    AnalyticsEvent.AccountConversionFailed(
                        (result.error as? AuthError)?.toConversionFailureReason()
                            ?: ConversionFailureReason.UNKNOWN,
                    ),
                )
                mutableState.value.copy(isBusy = false, message = result.error.toUiMessage())
            }
        }

    fun requestSignOut() = Unit

    fun confirmSignOut(confirmation: Confirmation) = confirmation.let { Unit }

    fun requestDeleteAccount() = Unit

    fun confirmDeleteAccount(confirmation: Confirmation) = confirmation.let { Unit }

    fun clearMessage() {
        if (closed) return
        if (mutableState.value.message?.confirmation == Confirmation.AdoptExistingAccount) {
            clearPendingCollision()
            analyticsTracker?.track(
                AnalyticsEvent.AccountConversionFailed(ConversionFailureReason.CANCELLED),
            )
        }
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
        activeSignInKind = null
        clearPendingCollision()
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
        val signInKind = activeSignInKind
        activePermanentProvider = null
        activeSignInKind = null
        val operationScope = scope
        val client = authClient
        if (operationScope == null || client == null) {
            publishError(AuthError.ProviderUnavailable)
            return
        }
        operationJob?.cancel()
        val converting = signInKind == SignInKind.CONVERSION
        operationJob =
            operationScope.launch {
                val result =
                    if (converting) {
                        client.linkCredential(credential)
                    } else {
                        client.signInWithCredential(credential)
                    }
                mutableState.value = permanentSignInState(result, converting, credential, client)
            }
    }

    private fun permanentSignInState(
        result: Outcome<AuthSession, AuthError>,
        converting: Boolean,
        credential: NativeAuthCredential,
        client: AuthClient,
    ): SessionUiState =
        when (result) {
            is Outcome.Ok -> {
                if (converting) {
                    analyticsTracker?.track(AnalyticsEvent.AccountConversionCompleted)
                }
                result.value.toSessionUiState()
            }

            is Outcome.Err -> {
                if (converting && result.error == AuthError.CredentialAlreadyInUse) {
                    pendingCollision = client.anonymousCollision(credential)
                    mutableState.value.copy(isBusy = false, message = destructiveConversionConfirmation())
                } else {
                    if (converting) {
                        analyticsTracker?.track(
                            AnalyticsEvent.AccountConversionFailed(result.error.toConversionFailureReason()),
                        )
                    }
                    mutableState.value.copy(isBusy = false, message = result.error.toUiMessage())
                }
            }
        }

    private fun clearPendingCollision() {
        pendingCollision = null
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
        publishedReminderUid = session.uid
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

private enum class SignInKind {
    PERMANENT,
    CONVERSION,
}

/** The anonymous identity and credential a confirmed destructive replacement will act on. */
private class PendingCollision(
    val anonymousUid: String,
    val credential: NativeAuthCredential,
)

/**
 * The collision is actionable only while the anonymous identity that produced it is still the
 * current session, so a session that is absent or already permanent yields nothing to confirm.
 */
private fun AuthClient.anonymousCollision(credential: NativeAuthCredential): PendingCollision? =
    (authState.value as? AuthState.SignedIn)
        ?.session
        ?.takeIf { it.isAnonymous }
        ?.let { PendingCollision(it.uid, credential) }

private fun AuthProvider.isNativePermanentProvider(): Boolean =
    this == AuthProvider.GOOGLE || this == AuthProvider.APPLE

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

private fun AppError.toUiMessage(): UiMessage =
    UiMessage(
        id = AUTH_ERROR_MESSAGE_ID,
        kind = UiMessageKind.ERROR,
        code = code,
        confirmation = null,
    )

private fun destructiveConversionConfirmation(): UiMessage =
    UiMessage(
        id = ACCOUNT_CONVERSION_CONFIRMATION_MESSAGE_ID,
        kind = UiMessageKind.WARNING,
        code = "CONFIRMATION.AdoptExistingAccount",
        confirmation = Confirmation.AdoptExistingAccount,
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
private const val ACCOUNT_CONVERSION_CONFIRMATION_MESSAGE_ID = 3L

class SyncStateHolder internal constructor() {
    val state: StateFlow<SyncUiState> =
        MutableStateFlow(SyncUiState(SyncStatus.Idle, true, null))

    fun requestSync(reason: SyncTrigger) = reason.let { Unit }

    fun retryFailed() = Unit

    fun clearMessage() = Unit

    fun close() = Unit
}
