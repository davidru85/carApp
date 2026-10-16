package com.ruizurraca.carapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AuthMessageResourceTest {
    @Test
    fun anUnclassifiedAuthFailureDoesNotClaimTheProviderIsUnconfigured() {
        val resource = uiMessageStringResource("AUTH.UNKNOWN")

        assertNotEquals(R.string.error_auth_provider, resource)
        assertEquals(R.string.error_unexpected, resource)
    }

    @Test
    fun anUnavailableProviderKeepsItsOwnMessage() {
        assertEquals(R.string.error_auth_provider, uiMessageStringResource("AUTH.PROVIDER_UNAVAILABLE"))
    }

    @Test
    fun aDeviceWithoutAnAvailableAccountGetsItsOwnMessageInsteadOfTheGenericOne() {
        val resource = uiMessageStringResource("AUTH.NO_ACCOUNT_AVAILABLE")

        assertEquals(
            R.string.error_auth_no_account,
            resource,
            "Telling the owner that something went wrong hides the one action that resolves it.",
        )
    }

    @Test
    fun aStaleLoginGetsTheReauthenticationMessage() {
        assertEquals(
            R.string.error_auth_requires_recent_login,
            uiMessageStringResource("AUTH.REQUIRES_RECENT_LOGIN"),
        )
    }

    @Test
    fun aRemoteAccountDeletionFailureGetsItsOwnMessage() {
        assertEquals(
            R.string.error_account_deletion_remote_failed,
            uiMessageStringResource("AUTH.ACCOUNT_DELETION_REMOTE_FAILED"),
        )
    }
}
