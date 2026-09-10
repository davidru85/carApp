package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsTracker
import com.ruizurraca.carapp.core.analytics.toDeletionFailureReason
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class DepartureKind {
    SIGN_OUT,
    DELETE_LOCAL,
    DELETE_ANONYMOUS,
    DELETE_PERMANENT,
}

/**
 * One F-5 departure request. It authorises the confirmation that follows it, remembers whether a
 * genuine pending-sync warning preceded a discard, and records which steps already succeeded so a
 * retry after a failed local clear never repeats the remote one.
 */
internal class PendingDeparture(
    val kind: DepartureKind,
    val ownerUid: String?,
    var warned: Boolean = false,
    var remoteDone: Boolean = false,
    var localDone: Boolean = false,
    var awaitingReauthentication: Boolean = false,
)

/**
 * The F-5 sign-out and account-deletion flows of `docs/SPECIFICATION.md §7 F-5` and
 * `docs/CONTRACTS.md §11.5`, kept out of [SessionStateHolder] because they are a small state
 * machine of their own.
 *
 * Three properties drive the design. A confirmation authorises exactly the request that preceded
 * it, for the same owner and session. The interval between a successful remote step and the local
 * clear is not cancellable, so the device is never left signed out of a deleted account whose local
 * data survives. And a failed local clear keeps the request, so a retry redoes the clear alone.
 */
internal class AccountDepartureFlow(
    private val scope: CoroutineScope?,
    private val authClient: AuthClient?,
    private val departure: AccountDepartureHandler?,
    private val analyticsTracker: AnalyticsTracker?,
    private val state: MutableStateFlow<SessionUiState>,
    private val cancelOtherWork: () -> Unit,
) {
    private var pending: PendingDeparture? = null
    private var job: Job? = null
    private var running = false

    val isRunning: Boolean get() = running

    val awaitingReauthentication: Boolean get() = pending?.awaitingReauthentication == true

    /**
     * The published state belongs to a departure while one is running, while a remote step has
     * succeeded and its local clear is still owed, and while the owner is re-authenticating. The
     * auth-state collector must not overwrite it: deleting the account signs the provider out, and
     * an unguarded collector would publish `SIGNED_OUT` while the local clear is still pending.
     */
    fun ownsState(): Boolean = running || pending?.remoteDone == true || pending?.awaitingReauthentication == true

    fun cancel() {
        job?.cancel()
        job = null
    }

    /** Withdraws the request without touching local data, for a cancelled re-authentication. */
    fun abandon() {
        pending = null
    }

    fun forget() {
        pending = null
        running = false
    }

    // ------------------------------------------------------------------ intents

    fun requestSignOut() {
        if (running) return
        val departureHandler = departure ?: return
        val session = currentSession()
        // F-5: sign-out is offered only to a permanently authenticated user. An anonymous session
        // has no sign-out, and a local owner has no account to sign out of.
        if (session == null || session.isAnonymous) {
            publish(AuthError.ProviderUnavailable)
            return
        }
        val operationScope = scope ?: return
        begin(PendingDeparture(DepartureKind.SIGN_OUT, session.uid), phase = null)
        job = operationScope.launch { evaluatePendingSync(departureHandler) }
    }

    fun confirmSignOut() {
        if (running) return
        val accepted = acceptedDeparture { it.kind == DepartureKind.SIGN_OUT && it.warned } ?: return
        resume(accepted, phase = null)
    }

    fun requestDeleteAccount() {
        if (running) return
        val requested = currentDeletionRequest()
        if (requested == null) {
            publish(AuthError.ProviderUnavailable)
            return
        }
        pending = requested
        state.value = state.value.copy(isBusy = false, message = deleteAccountConfirmation(), pendingSyncCount = null)
    }

    fun confirmDeleteAccount() {
        if (running) return
        // Re-authentication is a separate, host-driven step; the confirmation cannot skip it.
        val accepted =
            acceptedDeparture { it.kind != DepartureKind.SIGN_OUT && !it.awaitingReauthentication } ?: return
        resume(accepted, phase = SessionPhase.DELETING)
    }

    fun retryDeparture() = Unit

    fun resumeAfterReauthentication() {
        val accepted = pending ?: return
        accepted.awaitingReauthentication = false
        resume(accepted, phase = SessionPhase.DELETING)
    }

    // --------------------------------------------------------------- eligibility

    private fun currentSession(): AuthSession? = (authClient?.authState?.value as? AuthState.SignedIn)?.session

    private fun currentDeletionRequest(): PendingDeparture? {
        val session = currentSession()
        if (session != null) {
            val kind = if (session.isAnonymous) DepartureKind.DELETE_ANONYMOUS else DepartureKind.DELETE_PERMANENT
            return PendingDeparture(kind, session.uid)
        }
        // `SessionPhase.LOCAL` exists only in the published state: a local owner has no Firebase
        // session at all, so there is nothing remote to delete.
        if (state.value.phase != SessionPhase.LOCAL) return null
        return PendingDeparture(DepartureKind.DELETE_LOCAL, ownerUid = null)
    }

    private fun acceptedDeparture(predicate: (PendingDeparture) -> Boolean): PendingDeparture? {
        val candidate = pending ?: return null
        if (!predicate(candidate)) return null
        if (ownerStillMatches(candidate)) return candidate
        discard()
        return null
    }

    /**
     * A confirmation authorises exactly the owner and session its request was raised for. Once the
     * remote step has succeeded the original session is legitimately gone, so a retry that still
     * owes a local clear is not re-checked against it.
     */
    private fun ownerStillMatches(candidate: PendingDeparture): Boolean {
        if (candidate.remoteDone) return true
        val session = currentSession()
        if (candidate.kind == DepartureKind.DELETE_LOCAL) {
            return session == null && state.value.phase == SessionPhase.LOCAL
        }
        if (session == null || session.uid != candidate.ownerUid) return false
        return session.isAnonymous == (candidate.kind == DepartureKind.DELETE_ANONYMOUS)
    }

    // ------------------------------------------------------------------ running

    private fun discard() {
        pending = null
        running = false
        state.value = state.value.copy(message = null, pendingSyncCount = null)
    }

    private fun begin(
        candidate: PendingDeparture,
        phase: SessionPhase?,
    ) {
        pending = candidate
        running = true
        cancelOtherWork()
        val current = state.value
        state.value =
            current.copy(
                phase = phase ?: current.phase,
                isBusy = true,
                message = null,
                pendingSyncCount = null,
            )
    }

    private fun resume(
        candidate: PendingDeparture,
        phase: SessionPhase?,
    ) {
        val operationScope = scope ?: return
        begin(candidate, phase)
        job = operationScope.launch { run(candidate) }
    }

    private suspend fun evaluatePendingSync(departureHandler: AccountDepartureHandler) {
        val candidate = pending ?: return
        when (val counted = departureHandler.pendingOutboxCount()) {
            is Outcome.Err -> {
                fail(counted.error)
            }

            is Outcome.Ok -> {
                if (counted.value > 0) {
                    warn(candidate, counted.value)
                } else {
                    run(candidate)
                }
            }
        }
    }

    /**
     * The exact count reaches the host as a typed value, so the
     * `ValidationWarning.PendingSyncBeforeSignOut(pendingCount)` payload is not lost on the way
     * through the single message channel, which transports only a code.
     */
    private fun warn(
        candidate: PendingDeparture,
        pendingCount: Int,
    ) {
        candidate.warned = true
        running = false
        state.value =
            state.value.copy(isBusy = false, message = pendingSyncWarning(), pendingSyncCount = pendingCount)
    }

    private suspend fun run(candidate: PendingDeparture) {
        val departureHandler = departure
        if (departureHandler == null) {
            fail(AuthError.ProviderUnavailable)
            return
        }
        when (candidate.kind) {
            DepartureKind.DELETE_LOCAL -> {
                settle(candidate, departureHandler)
            }

            // The anonymous identity is unrecoverable and has no `D-23` server deletion. Its local
            // data goes first; the provider session is ended afterwards so a recreated holder
            // cannot route straight back to `ANONYMOUS` on the same UID.
            DepartureKind.DELETE_ANONYMOUS -> {
                if (clearLocalData(candidate, departureHandler) && endProviderSession(candidate)) {
                    complete(candidate)
                }
            }

            DepartureKind.SIGN_OUT -> {
                if (candidate.remoteDone || endProviderSession(candidate)) {
                    settle(candidate, departureHandler)
                }
            }

            DepartureKind.DELETE_PERMANENT -> {
                if (candidate.remoteDone || deleteRemoteAccount(candidate)) {
                    settle(candidate, departureHandler)
                }
            }
        }
    }

    /** Hands over from the completed remote step to the local clear, then settles the departure. */
    private suspend fun settle(
        candidate: PendingDeparture,
        departureHandler: AccountDepartureHandler,
    ) {
        publishRemoteHandover()
        if (clearLocalData(candidate, departureHandler)) complete(candidate)
    }

    /**
     * The interval between a successful remote step and the local clear is not cancellable. A
     * reentrant intent, a `close()` or a cancelled scope must never leave the device signed out of a
     * deleted account while that account's local data survives.
     */
    private suspend fun clearLocalData(
        candidate: PendingDeparture,
        departureHandler: AccountDepartureHandler,
    ): Boolean {
        if (candidate.localDone) return true
        return when (val cleared = withContext(NonCancellable) { departureHandler.clearLocalData() }) {
            // The remote side may already be done, so the request is retained: a retry redoes the
            // clear alone. Neither SIGNED_OUT nor the completion event may be published here.
            is Outcome.Err -> {
                fail(cleared.error)
                false
            }

            is Outcome.Ok -> {
                candidate.localDone = true
                true
            }
        }
    }

    private suspend fun endProviderSession(candidate: PendingDeparture): Boolean {
        val client = authClient
        if (client == null) {
            fail(AuthError.ProviderUnavailable)
            return false
        }
        return when (val result = client.signOut()) {
            is Outcome.Err -> {
                fail(result.error)
                false
            }

            is Outcome.Ok -> {
                candidate.remoteDone = true
                true
            }
        }
    }

    private suspend fun deleteRemoteAccount(candidate: PendingDeparture): Boolean {
        val client = authClient
        if (client == null) {
            fail(AuthError.ProviderUnavailable)
            return false
        }
        return when (val result = client.deleteAccount()) {
            is Outcome.Err -> {
                failDeletion(candidate, result.error)
                false
            }

            is Outcome.Ok -> {
                candidate.remoteDone = true
                true
            }
        }
    }

    // ----------------------------------------------------------------- outcomes

    /**
     * `§20.10` orders the phases `DELETING -> UNKNOWN`, and only then `UNKNOWN -> SIGNED_OUT` once
     * the local clear completes. The intermediate phase is what a failed clear is left holding.
     */
    private fun publishRemoteHandover() {
        state.value =
            state.value.copy(
                phase = SessionPhase.UNKNOWN,
                providers = emptyList(),
                isBusy = true,
                message = null,
                pendingSyncCount = null,
            )
    }

    private fun complete(candidate: PendingDeparture) {
        if (candidate.kind != DepartureKind.SIGN_OUT) {
            analyticsTracker?.track(AnalyticsEvent.AccountDeletionCompleted)
        }
        pending = null
        running = false
        state.value = signedOutSessionState()
    }

    private fun publish(error: AppError) {
        state.value = state.value.copy(isBusy = false, message = error.toSessionUiMessage(), pendingSyncCount = null)
    }

    private fun fail(error: AppError) {
        running = false
        publish(error)
    }

    /**
     * A stale login is recoverable, so the request is kept and the host is told to re-authenticate.
     * Every other failure preserves local data and reports the typed error unchanged.
     */
    private fun failDeletion(
        candidate: PendingDeparture,
        error: AuthError,
    ) {
        analyticsTracker?.track(AnalyticsEvent.AccountDeletionFailed(error.toDeletionFailureReason()))
        if (error == AuthError.RequiresRecentLogin) {
            candidate.awaitingReauthentication = true
        } else {
            pending = null
        }
        running = false
        state.value =
            state.value.copy(
                phase =
                    authClient
                        ?.authState
                        ?.value
                        .toSessionUiState()
                        .phase,
                isBusy = false,
                message = error.toSessionUiMessage(),
                pendingSyncCount = null,
            )
    }
}
