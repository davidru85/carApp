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
}
