package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AuthProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidGoogleSignInCoordinatorTest {
    @Test
    fun successfulAcquisitionUsesTheNamedGoogleIntents() =
        runBlocking {
            val calls = mutableListOf<String>()
            val coordinator =
                AndroidGoogleSignInCoordinator(
                    acquireCredential = {
                        GoogleCredentialAcquisition.Success(idToken = "id-token", accessToken = null)
                    },
                    startPermanentSignIn = { provider -> calls += "start:$provider" },
                    completeGoogleSignIn = { idToken, accessToken -> calls += "complete:$idToken:$accessToken" },
                    failSignIn = { failure -> calls += "fail:$failure" },
                )

            coordinator.signIn()

            assertEquals(
                listOf("start:${AuthProvider.GOOGLE}", "complete:id-token:null"),
                calls,
            )
        }

    @Test
    fun failedAcquisitionUsesOnlyTheClosedFailureIntent() =
        runBlocking {
            val calls = mutableListOf<String>()
            val coordinator =
                AndroidGoogleSignInCoordinator(
                    acquireCredential = {
                        GoogleCredentialAcquisition.Failure(NativeSignInFailure.NETWORK)
                    },
                    startPermanentSignIn = { provider -> calls += "start:$provider" },
                    completeGoogleSignIn = { _, _ -> calls += "complete" },
                    failSignIn = { failure -> calls += "fail:$failure" },
                )

            coordinator.signIn()

            assertEquals(
                listOf("start:${AuthProvider.GOOGLE}", "fail:${NativeSignInFailure.NETWORK}"),
                calls,
            )
        }
}
