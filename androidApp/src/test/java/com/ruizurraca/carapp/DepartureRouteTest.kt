package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D-214: a departure owns the root of the Android host while it runs, while it retains a step to
 * retry, during the hand-over between a remote step and the local clear, while a stale login waits
 * for re-authentication and while the host's re-authentication attempt is in flight, because the
 * `NavHost` is not the surface for any of those states. Every other state keeps the F-1 routing.
 */
class DepartureRouteTest {
    @Test
    fun theDeletingPhaseIsTheDepartureDestination() {
        assertEquals(
            OnboardingDestination.DEPARTURE,
            resolveOnboardingDestination(SessionPhase.DELETING, vehicleCount = 0),
        )
    }

    @Test
    fun aRunningDeletionRoutesToTheDepartureScreen() {
        assertEquals(
            OnboardingDestination.DEPARTURE,
            route(session(SessionPhase.DELETING, isBusy = true), vehicleCount = 2),
        )
    }

    @Test
    fun aRetainedDepartureRoutesToTheDepartureScreenInAnyPhase() {
        listOf(
            SessionPhase.PERMANENT to DepartureRetry.SESSION_CLEANUP,
            SessionPhase.UNKNOWN to DepartureRetry.LOCAL_CLEAR,
            SessionPhase.DELETING to DepartureRetry.LOCAL_CLEAR,
        ).forEach { (phase, retry) ->
            assertEquals(
                OnboardingDestination.DEPARTURE,
                route(session(phase, pendingDepartureRetry = retry), vehicleCount = 2),
                "$phase with $retry",
            )
        }
    }

    @Test
    fun theHandoverAfterARemoteStepRoutesToTheDepartureScreen() {
        assertEquals(
            OnboardingDestination.DEPARTURE,
            route(session(SessionPhase.UNKNOWN, isBusy = true), vehicleCount = 2),
        )
    }

    @Test
    fun anIdleUnknownSessionStillWaits() {
        assertEquals(
            OnboardingDestination.WAITING,
            route(session(SessionPhase.UNKNOWN), vehicleCount = 0),
        )
    }

    /** A permanent owner whose sign-out is still counting the outbox keeps the mounted `NavHost`. */
    @Test
    fun aSettledSessionKeepsItsOnboardingDestination() {
        assertEquals(
            OnboardingDestination.VEHICLE_LIST,
            route(session(SessionPhase.PERMANENT, isBusy = true), vehicleCount = 2),
        )
        assertEquals(
            OnboardingDestination.WELCOME,
            route(session(SessionPhase.SIGNED_OUT), vehicleCount = 0),
        )
        assertEquals(
            OnboardingDestination.FIRST_VEHICLE,
            route(session(SessionPhase.ANONYMOUS), vehicleCount = 0),
        )
    }

    /** `§20.10`: a stale login keeps the deletion, and the departure surface carries re-authentication. */
    @Test
    fun aStaleLoginRoutesAPermanentOwnerToTheDepartureSurface() {
        val staleLogin =
            UiMessage(id = 2L, kind = UiMessageKind.ERROR, code = "AUTH.REQUIRES_RECENT_LOGIN", confirmation = null)

        assertEquals(
            OnboardingDestination.DEPARTURE,
            route(session(SessionPhase.PERMANENT, message = staleLogin), vehicleCount = 2),
        )
    }

    /** The picker's result must arrive while the departure surface is still mounted. */
    @Test
    fun anInFlightReauthenticationKeepsTheDepartureSurface() {
        assertEquals(
            OnboardingDestination.DEPARTURE,
            resolveRootDestination(
                session(SessionPhase.PERMANENT, isBusy = true),
                vehicleCount = 2,
                isReauthenticating = true,
            ),
        )
    }

    @Test
    fun eachRetryStepExplainsWhatItRepeats() {
        assertEquals(
            R.string.departure_retry_session_cleanup,
            departureRetryExplanationResource(DepartureRetry.SESSION_CLEANUP),
        )
        assertEquals(
            R.string.departure_retry_local_clear,
            departureRetryExplanationResource(DepartureRetry.LOCAL_CLEAR),
        )
    }

    private fun route(
        session: SessionUiState,
        vehicleCount: Int,
    ): OnboardingDestination = resolveRootDestination(session, vehicleCount, isReauthenticating = false)

    private fun session(
        phase: SessionPhase,
        isBusy: Boolean = false,
        message: UiMessage? = null,
        pendingDepartureRetry: DepartureRetry? = null,
    ): SessionUiState =
        SessionUiState(
            phase = phase,
            providers = emptyList(),
            isBusy = isBusy,
            message = message,
            anonymousReminderIndex = null,
            pendingSyncCount = null,
            pendingDepartureRetry = pendingDepartureRetry,
        )
}
