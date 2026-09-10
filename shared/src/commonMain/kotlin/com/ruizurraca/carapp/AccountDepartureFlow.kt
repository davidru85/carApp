package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsTracker
import com.ruizurraca.carapp.core.analytics.toDeletionFailureReason
import com.ruizurraca.carapp.core.auth.AuthClient
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.database.DepartureOperationKind
import com.ruizurraca.carapp.core.database.DepartureOperationStep
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
    var readyToDelete: Boolean = false,
    /**
     * `true` once the owner has authorised a destructive step and it has begun. Counting the outbox
     * for a warning nobody has confirmed is NOT an authorised step: until this flips, the request is
     * still re-checked against the current owner and session, and nothing is retained.
     */
    var authorized: Boolean = false,
    var remoteDone: Boolean = false,
    var sessionEnded: Boolean = false,
    var localDone: Boolean = false,
    var awaitingReauthentication: Boolean = false,
    /** `true` once the durable marker exists, so a retry never resets the steps already recorded. */
    var persisted: Boolean = false,
) {
    val operationKind: DepartureOperationKind
        get() =
            when (kind) {
                DepartureKind.SIGN_OUT -> DepartureOperationKind.SIGN_OUT
                DepartureKind.DELETE_LOCAL -> DepartureOperationKind.DELETE_LOCAL
                DepartureKind.DELETE_ANONYMOUS -> DepartureOperationKind.DELETE_ANONYMOUS
                DepartureKind.DELETE_PERMANENT -> DepartureOperationKind.DELETE_PERMANENT
            }

    /** Only the permanent path deletes an account through the `D-23` server operation. */
    private val remoteOwed: Boolean get() = kind == DepartureKind.DELETE_PERMANENT && !remoteDone

    /** Every kind but a local owner's ends the provider session; a local owner has none. */
    private val sessionOwed: Boolean get() = kind != DepartureKind.DELETE_LOCAL && !sessionEnded

    private val localOwed: Boolean get() = !localDone

    /** Retained destructive work: an authorised step has begun and a required step is still owed. */
    val hasRetainedWork: Boolean get() = authorized && (remoteOwed || sessionOwed || localOwed)

    /**
     * The first required step still owed, in the order this kind performs them, or `null` when
     * there is nothing a retry could repeat. A `D-23` deletion is never among it: one that already
     * succeeded MUST NOT run again, and one that never ran leaves nothing destructive to resume —
     * that case is re-authentication's, not a retry's.
     */
    val retry: DepartureRetry?
        get() =
            when {
                !hasRetainedWork || remoteOwed -> {
                    null
                }

                // An anonymous deletion clears local data first, so its session cleanup is only
                // the next step once that clear has succeeded.
                sessionOwed && (kind != DepartureKind.DELETE_ANONYMOUS || localDone) -> {
                    DepartureRetry.SESSION_CLEANUP
                }

                localOwed -> {
                    DepartureRetry.LOCAL_CLEAR
                }

                else -> {
                    DepartureRetry.SESSION_CLEANUP
                }
            }
}

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
    fun ownsState(): Boolean = running || pending?.hasRetainedWork == true || pending?.awaitingReauthentication == true

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
        if (running || retainsWork()) return
        val departureHandler = departure ?: return
        val session = currentSession()
        // F-5: sign-out is offered only to a permanently authenticated user. An anonymous session
        // has no sign-out, and a local owner has no account to sign out of.
        if (session == null || session.isAnonymous) {
            publish(AuthError.ProviderUnavailable)
            return
        }
        val operationScope = scope ?: return
        beginEvaluation(PendingDeparture(DepartureKind.SIGN_OUT, session.uid))
        job = operationScope.launch { evaluatePendingSync(departureHandler) }
    }

    fun confirmSignOut() {
        if (running) return
        val accepted = acceptedDeparture { it.kind == DepartureKind.SIGN_OUT && it.warned } ?: return
        resume(accepted, phase = null)
    }

    fun requestDeleteAccount() {
        if (running || retainsWork()) return
        val requested = currentDeletionRequest()
        if (requested == null) {
            publish(AuthError.ProviderUnavailable)
            return
        }
        pending = requested
        if (requested.kind == DepartureKind.DELETE_PERMANENT) {
            requested.readyToDelete = true
            askFor(deleteAccountConfirmation())
        } else {
            countOutboxBeforeLocalDataDeletion(requested)
        }
    }

    /**
     * Local-data deletion inspects the outbox first: §20.2 assigns `DiscardPendingChanges` to
     * exactly that case, and the destructive step only follows once those rows are discarded.
     */
    private fun countOutboxBeforeLocalDataDeletion(requested: PendingDeparture) {
        val departureHandler = departure ?: return
        val operationScope = scope ?: return
        beginEvaluation(requested)
        job = operationScope.launch { offerLocalDataDeletion(requested, departureHandler) }
    }

    fun confirmDeleteAccount(confirmation: Confirmation) {
        if (running) return
        when (confirmation) {
            Confirmation.DiscardPendingChanges -> acceptDiscardBeforeLocalDataDeletion()
            Confirmation.DeleteLocalData -> startDeletion(permanent = false)
            Confirmation.DeleteAccount -> startDeletion(permanent = true)
            else -> Unit
        }
    }

    /**
     * Repeats the unfinished part of a departure and only that part. A D-23 deletion that already
     * succeeded is never repeated, and the owner and session are not re-checked: the work is
     * retained precisely because the original session may legitimately be gone by then.
     */
    fun retryDeparture() {
        if (running) return
        val candidate = pending ?: return
        // The intent follows the published value exactly: no value, no retry.
        if (candidate.retry == null) return
        resume(candidate, phase = SessionPhase.DELETING)
    }

    /**
     * A departure that still owes a required step keeps its request. A new one would restart the
     * step model, and for a permanent deletion that means calling the `D-23` operation a second
     * time for an account that is already gone. The retry is the only way forward.
     */
    private fun retainsWork(): Boolean = pending?.hasRetainedWork == true

    private fun askFor(message: UiMessage) {
        state.value = state.value.copy(isBusy = false, message = message, pendingSyncCount = null)
    }

    private suspend fun offerLocalDataDeletion(
        candidate: PendingDeparture,
        departureHandler: AccountDepartureHandler,
    ) {
        when (val counted = departureHandler.pendingOutboxCount()) {
            is Outcome.Err -> {
                fail(counted.error)
            }

            is Outcome.Ok -> {
                if (counted.value > 0) {
                    warn(candidate, counted.value)
                } else {
                    candidate.readyToDelete = true
                    running = false
                    askFor(deleteLocalDataConfirmation())
                }
            }
        }
    }

    private fun acceptDiscardBeforeLocalDataDeletion() {
        val accepted =
            acceptedDeparture {
                it.kind != DepartureKind.SIGN_OUT && it.warned && !it.readyToDelete
            } ?: return
        accepted.readyToDelete = true
        askFor(deleteLocalDataConfirmation())
    }

    /**
     * `DeleteAccount` answers only an account deletion and `DeleteLocalData` only a local-data one,
     * so a confirmation of the wrong kind acts on nothing. Re-authentication is a separate,
     * host-driven step that the confirmation cannot skip.
     */
    private fun startDeletion(permanent: Boolean) {
        val accepted =
            acceptedDeparture {
                it.kind != DepartureKind.SIGN_OUT &&
                    (it.kind == DepartureKind.DELETE_PERMANENT) == permanent &&
                    it.readyToDelete &&
                    !it.awaitingReauthentication
            } ?: return
        if (permanent) analyticsTracker?.track(AnalyticsEvent.AccountDeletionStarted)
        resume(accepted, phase = SessionPhase.DELETING)
    }

    fun resumeAfterReauthentication() {
        val accepted = pending ?: return
        accepted.awaitingReauthentication = false
        // `D-161` / ADR-0162: started attempts equal completed plus failed attempts. The stale-login
        // refusal already reported a failed attempt, so the resumed `D-23` call is a new attempt and
        // reports its own start.
        if (accepted.kind == DepartureKind.DELETE_PERMANENT && !accepted.remoteDone) {
            analyticsTracker?.track(AnalyticsEvent.AccountDeletionStarted)
        }
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
        if (candidate.hasRetainedWork) return true
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
        state.value = state.value.copy(message = null, pendingSyncCount = null, pendingDepartureRetry = null)
    }

    /** Starts the outbox count. Nothing is authorised yet, so nothing is retained. */
    private fun beginEvaluation(candidate: PendingDeparture) {
        pending = candidate
        running = true
        cancelOtherWork()
        state.value =
            state.value.copy(
                isBusy = true,
                message = null,
                pendingSyncCount = null,
                pendingDepartureRetry = null,
            )
    }

    private fun begin(
        candidate: PendingDeparture,
        phase: SessionPhase?,
    ) {
        pending = candidate
        running = true
        candidate.authorized = true
        cancelOtherWork()
        val current = state.value
        state.value =
            current.copy(
                phase = phase ?: current.phase,
                isBusy = true,
                message = null,
                pendingSyncCount = null,
                pendingDepartureRetry = null,
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
                    signOutAfterAnEmptyCount(candidate)
                }
            }
        }
    }

    /**
     * The count is asynchronous, so the provider may have switched session while it ran. A request
     * raised for owner A MUST NOT sign out owner B, so the owner is re-checked here, immediately
     * before the first authorised step.
     */
    private suspend fun signOutAfterAnEmptyCount(candidate: PendingDeparture) {
        if (!ownerStillMatches(candidate)) {
            discard()
            return
        }
        candidate.authorized = true
        run(candidate)
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
        // `D-166`: the marker exists before the first destructive step, so a process death from here
        // on leaves a record the next launch can finish.
        if (!candidate.persisted) {
            val started = departureHandler.startPersistedDeparture(candidate.operationKind, candidate.ownerUid)
            if (started is Outcome.Err) {
                failDeparture(candidate, started.error)
                return
            }
            candidate.persisted = true
        }
        when (candidate.kind) {
            DepartureKind.DELETE_LOCAL -> runLocalDeletion(candidate, departureHandler)
            DepartureKind.DELETE_ANONYMOUS -> runAnonymousDeletion(candidate, departureHandler)
            DepartureKind.SIGN_OUT -> runSignOut(candidate, departureHandler)
            DepartureKind.DELETE_PERMANENT -> runAccountDeletion(candidate, departureHandler)
        }
    }

    /** Nothing remote exists for a local owner, so there is no handover to publish. */
    private suspend fun runLocalDeletion(
        candidate: PendingDeparture,
        departureHandler: AccountDepartureHandler,
    ) {
        if (clearLocalData(candidate, departureHandler)) complete(candidate)
    }

    /**
     * The anonymous identity is unrecoverable and has no `D-23` server deletion. Its local data
     * goes first; the provider session is ended afterwards so a recreated holder cannot route
     * straight back to `ANONYMOUS` on the same UID (`D-156`).
     */
    private suspend fun runAnonymousDeletion(
        candidate: PendingDeparture,
        departureHandler: AccountDepartureHandler,
    ) {
        if (!clearLocalData(candidate, departureHandler)) return
        // The local data is already gone. Leaving the anonymous session alive would make the
        // deletion look undone on the next launch, so ordinary cancellation MUST NOT lose this.
        withContext(NonCancellable) {
            if (endProviderSession(candidate)) complete(candidate)
        }
    }

    private suspend fun runSignOut(
        candidate: PendingDeparture,
        departureHandler: AccountDepartureHandler,
    ) {
        if (!candidate.sessionEnded && !endProviderSession(candidate)) return
        settle(candidate, departureHandler)
    }

    /**
     * `D-160`: the D-23 operation removes the server-side account but leaves the persisted client
     * session alive, so ending it is an explicit step of its own. Each step carries its own flag,
     * which is what stops a later failure from repeating the server call.
     */
    private suspend fun runAccountDeletion(
        candidate: PendingDeparture,
        departureHandler: AccountDepartureHandler,
    ) {
        if (!candidate.remoteDone && !deleteRemoteAccount(candidate)) return
        // The account is gone remotely. Ending the session and clearing local data is one tail that
        // ordinary cancellation, including `SessionStateHolder.close()`, MUST NOT interrupt. This
        // says nothing about process death, which stays `E2-09`.
        withContext(NonCancellable) {
            if (!candidate.sessionEnded && !endProviderSession(candidate)) return@withContext
            settle(candidate, departureHandler)
        }
    }

    /** Hands over from the completed remote steps to the local clear, then settles the departure. */
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
                failDeparture(candidate, cleared.error)
                false
            }

            is Outcome.Ok -> {
                candidate.localDone = true
                departureHandler.markDepartureStep(DepartureOperationStep.LOCAL_CLEAR)
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
                failDeparture(candidate, result.error)
                false
            }

            is Outcome.Ok -> {
                candidate.sessionEnded = true
                departure?.markDepartureStep(DepartureOperationStep.SESSION_CLEANUP)
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
                departure?.markDepartureStep(DepartureOperationStep.REMOTE_DELETION)
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

    /**
     * Only the permanent path deletes an account, so only it reports the account-deletion
     * lifecycle. Clearing local data for a local or anonymous owner is not account deletion.
     */
    private suspend fun complete(candidate: PendingDeparture) {
        departure?.clearPersistedDeparture()
        if (candidate.kind == DepartureKind.DELETE_PERMANENT) {
            analyticsTracker?.track(AnalyticsEvent.AccountDeletionCompleted)
        }
        pending = null
        running = false
        state.value = signedOutSessionState()
    }

    private fun publish(error: AppError) {
        state.value =
            state.value.copy(
                isBusy = false,
                message = error.toSessionUiMessage(),
                pendingSyncCount = null,
                pendingDepartureRetry = null,
            )
    }

    private fun fail(error: AppError) {
        running = false
        pending = null
        publish(error)
    }

    /**
     * A departure that already attempted a step keeps its request and publishes what a retry would
     * repeat, so the host offers the retry from typed state instead of remembering it itself.
     */
    private fun failDeparture(
        candidate: PendingDeparture,
        error: AppError,
    ) {
        running = false
        state.value =
            state.value.copy(
                isBusy = false,
                message = error.toSessionUiMessage(),
                pendingSyncCount = null,
                pendingDepartureRetry = candidate.retry,
            )
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
