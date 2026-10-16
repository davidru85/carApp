package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The host side of `§20.10` re-authentication. `SessionUiState` has no "awaiting re-authentication"
 * value, so the host tracks its own attempt until the session is no longer busy, and it offers only
 * the account's providers it can open natively: Google on Android (`docs/SPECIFICATION.md §7 F-1`).
 */
class ReauthenticationTest {
    @Test
    fun aReauthenticationContinuesWhileTheSessionIsBusy() {
        assertTrue(isReauthenticationStillInFlight(isReauthenticating = true, session = session(isBusy = true)))
    }

    @Test
    fun aReauthenticationEndsOnceTheSessionIsNoLongerBusy() {
        assertFalse(isReauthenticationStillInFlight(isReauthenticating = true, session = session(isBusy = false)))
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
