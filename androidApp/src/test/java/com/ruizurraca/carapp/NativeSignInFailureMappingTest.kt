package com.ruizurraca.carapp

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NativeSignInFailureMappingTest {
    @Test
    fun aDeviceWithoutAnAvailableGoogleAccountIsNotReportedAsAnUnconfiguredProvider() {
        val failure = NoCredentialException().toNativeSignInFailure()

        assertNotEquals(
            NativeSignInFailure.CONFIGURATION,
            failure,
            "Having no account available on the device says nothing about the provider configuration.",
        )
        assertEquals(NativeSignInFailure.UNKNOWN, failure)
    }

    @Test
    fun anActualProviderConfigurationProblemStaysAConfigurationFailure() {
        assertEquals(
            NativeSignInFailure.CONFIGURATION,
            GetCredentialProviderConfigurationException().toNativeSignInFailure(),
        )
    }

    @Test
    fun aCancelledCredentialRequestStaysCancelled() {
        assertEquals(
            NativeSignInFailure.CANCELLED,
            GetCredentialCancellationException().toNativeSignInFailure(),
        )
    }
}
