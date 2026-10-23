package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The host side of `§20.10` re-authentication. `SessionUiState` has no "awaiting re-authentication"
 * value, so the host tracks its own attempt across a shared provider failure, and it offers only the
 * account's providers it can open natively: Google on Android (`docs/SPECIFICATION.md §7 F-1`).
 */
class ReauthenticationTest {
    @Test
    fun aReauthenticationContinuesWhileTheSessionIsBusy() {
        assertTrue(isReauthenticationStillInFlight(isReauthenticating = true, session = session(isBusy = true)))
    }

    /**
     * `§20.10`: a provider that refuses the credential keeps the deletion suspended in shared code, so
     * the host stage must survive the emission and keep the departure surface reachable. Without it the
     * retained deletion can be neither re-authenticated nor cancelled.
     */
    @Test
    fun aProviderRejectionKeepsTheSuspendedDeletionReachable() {
        val failed =
            session(isBusy = false).copy(
                message =
                    UiMessage(
                        id = 2L,
                        kind = UiMessageKind.ERROR,
                        code = "AUTH.NETWORK_UNAVAILABLE",
                        confirmation = null,
                    ),
            )
        val retained = isReauthenticationStillInFlight(true, failed)
        assertTrue(retained)
        assertEquals(
            OnboardingDestination.DEPARTURE,
            resolveRootDestination(failed, vehicleCount = 1, isReauthenticating = retained),
        )
    }

    /**
     * A second provider rejection the deletion can end with. It must preserve the stage exactly like
     * the network failure: the routing rule is the retained deletion, not a list of error codes.
     */
    @Test
    fun aDifferentAccountCredentialKeepsTheSuspendedDeletionReachable() {
        val failed =
            session(isBusy = false).copy(
                message =
                    UiMessage(
                        id = 2L,
                        kind = UiMessageKind.ERROR,
                        code = "AUTH.UID_WOULD_CHANGE",
                        confirmation = null,
                    ),
            )
        val retained = isReauthenticationStillInFlight(true, failed)
        assertTrue(retained)
        assertEquals(
            OnboardingDestination.DEPARTURE,
            resolveRootDestination(failed, vehicleCount = 1, isReauthenticating = retained),
        )
    }

    /** The stage is host state, never re-inferred from an unrelated session emission. */
    @Test
    fun anInactiveReauthenticationStageNeverStartsFromASessionEmission() {
        assertFalse(isReauthenticationStillInFlight(false, session(isBusy = true)))
        assertFalse(isReauthenticationStillInFlight(false, session(isBusy = false)))
    }

    /** Leaving `PERMANENT` ends the stage: only that phase can hold a suspended deletion. */
    @Test
    fun leavingThePermanentPhaseEndsTheHostReauthenticationStage() {
        SessionPhase.entries.filter { it != SessionPhase.PERMANENT }.forEach { phase ->
            assertFalse(
                isReauthenticationStillInFlight(true, session(isBusy = false).copy(phase = phase)),
                "The stage survived $phase",
            )
        }
    }

    @Test
    fun onlyGoogleIsOfferedForReauthenticationOnAndroid() {
        assertEquals(
            listOf(AuthProvider.GOOGLE),
            reauthenticationProviders(listOf(AuthProvider.GOOGLE, AuthProvider.APPLE)),
        )
    }

    @Test
    fun anAccountWithoutAGoogleProviderIsOfferedNoProvider() {
        assertEquals(emptyList<AuthProvider>(), reauthenticationProviders(listOf(AuthProvider.APPLE)))
    }

    private fun session(isBusy: Boolean): SessionUiState =
        SessionUiState(
            phase = SessionPhase.PERMANENT,
            providers = listOf(AuthProvider.GOOGLE),
            isBusy = isBusy,
            message = null,
            anonymousReminderIndex = null,
            pendingSyncCount = null,
            pendingDepartureRetry = null,
        )
}
