package com.ruizurraca.carapp

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NativeSignInFailureMappingTest {
    @Test
    fun aDeviceWithoutAnAvailableGoogleAccountReportsItsOwnFailureCase() {
        val failure = NoCredentialException().toNativeSignInFailure()

        assertEquals(
            "NO_ACCOUNT_AVAILABLE",
            failure.name,
            "A device with no account to offer is a distinct outcome, not an unclassified failure.",
        )
    }

    @Test
    fun aDeviceWithoutAnAvailableGoogleAccountIsNotReportedAsAnUnconfiguredProvider() {
        assertNotEquals(
            NativeSignInFailure.CONFIGURATION,
            NoCredentialException().toNativeSignInFailure(),
            "Having no account available on the device says nothing about the provider configuration.",
        )
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
