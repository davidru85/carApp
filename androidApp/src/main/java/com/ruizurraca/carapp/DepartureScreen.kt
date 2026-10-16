package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AuthProvider

/**
 * The root destination of the Android host (B1-D4).
 *
 * A departure owns the window while it runs (`DELETING`), while it retains a step to retry
 * (`pendingDepartureRetry != null`, in whatever phase the failure left), and during the hand-over
 * between a successful remote step and the local clear, which `AccountDepartureFlow` publishes as
 * `UNKNOWN` with `isBusy`. The authenticated `NavHost` is not mounted in any of those states, so
 * routing them anywhere else would leave the typed failure and `retryDeparture()` unreachable.
 *
 * It also carries `§20.10` re-authentication: a permanent owner whose deletion was refused with
 * `AuthError.RequiresRecentLogin` is routed here, and so is the host's own re-authentication attempt
 * ([isReauthenticating]) while the native picker is up, so its result arrives on a mounted surface.
 * `UNKNOWN` without `isBusy` is the cold start and keeps waiting; every other state keeps F-1 routing.
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
 * Whether the host's re-authentication attempt is still running after a session emission. The attempt
 * starts busy (`startReauthentication` sets `isBusy` before the picker opens) and ends the moment the
 * session is no longer busy: cancelled, failed, or the resumed deletion has settled. `SessionUiState`
 * has no "awaiting re-authentication" value, so the host tracks this itself.
 */
internal fun isReauthenticationStillInFlight(
    isReauthenticating: Boolean,
    session: SessionUiState,
): Boolean = isReauthenticating && session.isBusy

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
