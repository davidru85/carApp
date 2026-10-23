package com.ruizurraca.carapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ruizurraca.carapp.core.common.AuthProvider

/**
 * The departure surface of D-214. While the shared holder is busy (a departure, or the host's
 * re-authentication attempt) it shows progress. A stale login (`AuthError.RequiresRecentLogin`), or a
 * host stage the shared provider already refused, shows the `§20.10` re-authentication choice: one
 * action per provider the host can open natively ([reauthenticationProviders]) and a cancel. Any other
 * settled state shows the typed failure through the one shared mapping and, only when
 * `pendingDepartureRetry` names a retained step, the `retryDeparture()` action. It decides nothing:
 * every value it draws is published by `SessionStateHolder` (`docs/CONTRACTS.md §20.10`).
 *
 * The two re-authentication parameters give this screen parity with the iOS `DepartureView`
 * (`D-214`, ADR-0215); no existing parameter was renamed. [isReauthenticating] is the host stage: once
 * it is set, a provider rejection does not end it, so the retained deletion keeps its Google and Cancel
 * actions instead of falling back to a generic dismissal.
 */
@Composable
internal fun DepartureScreen(
    session: SessionUiState,
    isReauthenticating: Boolean,
    onRetry: () -> Unit,
    onDismissMessage: () -> Unit,
    onReauthenticate: (AuthProvider) -> Unit,
    onCancelReauthentication: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize().testTag(DepartureTestTags.SCREEN)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    session.isBusy -> {
                        DepartureProgress()
                    }

                    isReauthenticating || sessionPrompt(session.message) == SessionPrompt.REAUTHENTICATE -> {
                        DepartureReauthentication(
                            session = session,
                            onReauthenticate = onReauthenticate,
                            onCancelReauthentication = onCancelReauthentication,
                        )
                    }

                    else -> {
                        DepartureFailure(session = session, onRetry = onRetry, onDismissMessage = onDismissMessage)
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureProgress() {
    LabelledProgressIndicator(
        modifier = Modifier.testTag(DepartureTestTags.PROGRESS),
        labelResource = R.string.departure_progress,
    )
    Text(
        text = stringResource(R.string.departure_progress),
        textAlign = TextAlign.Center,
    )
}

/**
 * `§20.10`: the deletion waits for a fresh credential. Android opens the Google picker only (F-1), so
 * every offered provider carries the Google action; an account with no such provider gets only Cancel.
 */
@Composable
private fun DepartureReauthentication(
    session: SessionUiState,
    onReauthenticate: (AuthProvider) -> Unit,
    onCancelReauthentication: () -> Unit,
) {
    Text(
        text = stringResource(R.string.reauthenticate_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(DepartureTestTags.REAUTHENTICATION_TITLE),
    )
    // The reason is the `§20.10` recent-login requirement itself, so it stays on screen when the shared
    // provider rejects the credential and the session message becomes a different typed failure.
    Text(
        text = stringResource(R.string.error_auth_requires_recent_login),
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(DepartureTestTags.REAUTHENTICATION_REASON),
    )
    if (sessionPrompt(session.message) != SessionPrompt.REAUTHENTICATE) {
        SessionErrorText(session.message, testTag = DepartureTestTags.ERROR)
    }
    reauthenticationProviders(session.providers).forEach { provider ->
        Button(
            onClick = { onReauthenticate(provider) },
            modifier = Modifier.fillMaxWidth().testTag(DepartureTestTags.reauthenticate(provider)),
        ) {
            Text(stringResource(R.string.continue_with_google))
        }
    }
    TextButton(
        onClick = onCancelReauthentication,
        modifier = Modifier.testTag(DepartureTestTags.CANCEL_REAUTHENTICATION),
    ) {
        Text(stringResource(R.string.cancel))
    }
}

@Composable
private fun DepartureFailure(
    session: SessionUiState,
    onRetry: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    Text(
        text = stringResource(R.string.departure_failed_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(DepartureTestTags.FAILURE_TITLE),
    )
    SessionErrorText(session.message, testTag = DepartureTestTags.ERROR)
    session.pendingDepartureRetry?.let { retry ->
        Text(
            text = stringResource(departureRetryExplanationResource(retry)),
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(DepartureTestTags.RETRY_EXPLANATION),
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().testTag(DepartureTestTags.RETRY),
        ) {
            Text(stringResource(R.string.retry))
        }
    }
    if (session.message != null) {
        TextButton(
            onClick = onDismissMessage,
            modifier = Modifier.testTag(DepartureTestTags.DISMISS),
        ) {
            Text(stringResource(R.string.ok))
        }
    }
}

/** Test tags of the departure screen. Every value starts with `departure_`. */
internal object DepartureTestTags {
    const val SCREEN = "departure_screen"
    const val PROGRESS = "departure_progress"
    const val FAILURE_TITLE = "departure_failure_title"
    const val ERROR = "departure_error"
    const val RETRY_EXPLANATION = "departure_retry_explanation"
    const val RETRY = "departure_retry"
    const val DISMISS = "departure_dismiss"
    const val REAUTHENTICATION_TITLE = "departure_reauthentication_title"
    const val REAUTHENTICATION_REASON = "departure_reauthentication_reason"
    const val CANCEL_REAUTHENTICATION = "departure_cancel_reauthentication"

    fun reauthenticate(provider: AuthProvider): String = "departure_reauthenticate_${provider.name.lowercase()}"
}

/**
 * Binds [DepartureScreen] to the session holder at the root (D-214). Cancelling re-authentication is
 * the contract's "cancelled re-authentication" (`docs/CONTRACTS.md §20.10`): it opens the attempt and
 * reports it cancelled, which abandons the deletion without touching local data — the sequence of the
 * shared test `cancellingReauthenticationAbandonsTheDeletionWithoutClearingLocalData`. `clearMessage()`
 * alone would leave the deletion pending and every later departure request silently refused.
 */
@Composable
internal fun DepartureRoute(
    session: SessionUiState,
    isReauthenticating: Boolean,
    sessionStateHolder: SessionStateHolder,
    onReauthenticateWithGoogle: () -> Unit,
    onReauthenticationAbandoned: () -> Unit,
) {
    DepartureScreen(
        session = session,
        isReauthenticating = isReauthenticating,
        onRetry = sessionStateHolder::retryDeparture,
        onDismissMessage = sessionStateHolder::clearMessage,
        // Android offers Google only (F-1), so every offered provider opens the Google picker.
        onReauthenticate = { _ -> onReauthenticateWithGoogle() },
        onCancelReauthentication = {
            onReauthenticationAbandoned()
            sessionStateHolder.startReauthentication(AuthProvider.GOOGLE)
            sessionStateHolder.failSignIn(NativeSignInFailure.CANCELLED)
        },
    )
}

/**
 * The host's `isReauthenticating` flag. `SessionUiState` has no "awaiting re-authentication" value, so
 * the host remembers that it started an attempt and keeps the departure surface mounted for as long as
 * the suspended deletion exists ([isReauthenticationStillInFlight]). It reads the holder's current
 * value, not the collected one, because the collected state can lag one frame behind the synchronous
 * `startReauthentication` call that made the session busy.
 */
@Composable
internal fun rememberReauthenticationInFlight(
    sessionStateHolder: SessionStateHolder,
    phase: SessionPhase,
): MutableState<Boolean> {
    val inFlight = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(inFlight.value, phase) {
        inFlight.value =
            isReauthenticationStillInFlight(inFlight.value, sessionStateHolder.state.value)
    }
    return inFlight
}

/**
 * The root destination of the Android host (D-214).
 *
 * A departure owns the window while it runs (`DELETING`), while it retains a step to retry
 * (`pendingDepartureRetry != null`, in whatever phase the failure left), and during the hand-over
 * between a successful remote step and the local clear, which `AccountDepartureFlow` publishes as
 * `UNKNOWN` with `isBusy` (`D-234`). The authenticated `NavHost` is not mounted in any of those states, so
 * routing them anywhere else would leave the typed failure and `retryDeparture()` unreachable.
 *
 * It also carries `§20.10` re-authentication: a permanent owner whose deletion was refused with
 * `AuthError.RequiresRecentLogin` is routed here, and so is the host's own re-authentication stage
 * ([isReauthenticating]) from the moment the owner asks for it until one of the transitions below ends
 * it, so its result arrives on a mounted surface. `UNKNOWN` without `isBusy` is the cold start and keeps
 * waiting; every other state keeps F-1 routing.
 *
 * The stage's transitions are the contract's, not an observation of the session state:
 *
 * | Input event | Host stage after the event | Root destination and visible result |
 * |---|---|---|
 * | Initial `AUTH.REQUIRES_RECENT_LOGIN` in `PERMANENT` | May still be false |
 *   Departure, reason plus provider controls |
 * | Tap Google | true | Departure progress while busy |
 * | Native picker returns a credential; the shared provider rejects it | true | Departure,
 *   permanent reason, typed failure, Google and Cancel |
 * | Native picker is cancelled or fails before a credential | false | Authenticated route;
 *   shared `failSignIn` abandons the deletion |
 * | Configuration change interrupts native acquisition | false via the abandonment callback |
 *   Authenticated route; the session is not stranded busy |
 * | Tap explicit Cancel on the stage | false | Shared start-then-cancel abandons the deletion |
 * | Shared re-authentication succeeds; the phase leaves `PERMANENT` | false once the phase
 *   observer runs | Departure progress, then success or the existing typed retry |
 * | A session emission occurs with no host stage active | false | No stage is inferred from a
 *   generic error |
 */
internal fun resolveRootDestination(
    session: SessionUiState,
    vehicleCount: Int,
    isReauthenticating: Boolean,
): OnboardingDestination =
    if (isDepartureSurfaceActive(session, isReauthenticating)) {
        OnboardingDestination.DEPARTURE
    } else {
        resolveOnboardingDestination(session.phase, vehicleCount)
    }

private fun isDepartureSurfaceActive(
    session: SessionUiState,
    isReauthenticating: Boolean,
): Boolean =
    when (session.phase) {
        SessionPhase.DELETING -> {
            true
        }

        SessionPhase.UNKNOWN -> {
            session.isBusy || session.pendingDepartureRetry != null
        }

        SessionPhase.PERMANENT -> {
            isReauthenticating ||
                sessionPrompt(session.message) == SessionPrompt.REAUTHENTICATE ||
                session.pendingDepartureRetry != null
        }

        SessionPhase.LOCAL,
        SessionPhase.ANONYMOUS,
        SessionPhase.SIGNED_OUT,
        -> {
            session.pendingDepartureRetry != null
        }
    }

/**
 * A shared provider rejection keeps the permanent owner's suspended deletion. Only an explicit
 * native abandonment, an explicit Cancel, or leaving PERMANENT ends the host stage.
 */
internal fun isReauthenticationStillInFlight(
    isReauthenticating: Boolean,
    session: SessionUiState,
): Boolean = isReauthenticating && session.phase == SessionPhase.PERMANENT

/**
 * The providers the departure surface offers for re-authentication: the session's own providers that
 * this host can open natively. Android has the Google picker only (`docs/SPECIFICATION.md §7 F-1`).
 */
internal fun reauthenticationProviders(providers: List<AuthProvider>): List<AuthProvider> =
    providers.filter { provider -> provider == AuthProvider.GOOGLE }

/** What a retry would repeat, in the owner's words. Exhaustive over `DepartureRetry`. */
internal fun departureRetryExplanationResource(retry: DepartureRetry): Int =
    when (retry) {
        DepartureRetry.SESSION_CLEANUP -> R.string.departure_retry_session_cleanup
        DepartureRetry.LOCAL_CLEAR -> R.string.departure_retry_local_clear
    }
