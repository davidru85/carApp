package com.ruizurraca.carapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AuthMessageResourceTest {
    @Test
    fun anUnclassifiedAuthFailureDoesNotClaimTheProviderIsUnconfigured() {
        val resource = authStringResource("AUTH.UNKNOWN")

        assertNotEquals(R.string.error_auth_provider, resource)
        assertEquals(R.string.error_unexpected, resource)
    }

    @Test
    fun anUnavailableProviderKeepsItsOwnMessage() {
        assertEquals(R.string.error_auth_provider, authStringResource("AUTH.PROVIDER_UNAVAILABLE"))
    }

    @Test
    fun aDeviceWithoutAnAvailableAccountGetsItsOwnMessageInsteadOfTheGenericOne() {
        val resource = authStringResource("AUTH.NO_ACCOUNT_AVAILABLE")

        assertEquals(
            R.string.error_auth_no_account,
            resource,
            "Telling the owner that something went wrong hides the one action that resolves it.",
        )
    }
}
