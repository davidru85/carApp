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
                    setAcquisitionInFlight = { inFlight -> calls += "inFlight:$inFlight" },
                )

            coordinator.signIn()

            assertEquals(
                listOf("start:${AuthProvider.GOOGLE}", "inFlight:true", "inFlight:false", "complete:id-token:null"),
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
                    setAcquisitionInFlight = { inFlight -> calls += "inFlight:$inFlight" },
                )

            coordinator.signIn()

            assertEquals(
                listOf(
                    "start:${AuthProvider.GOOGLE}",
                    "inFlight:true",
                    "inFlight:false",
                    "fail:${NativeSignInFailure.NETWORK}",
                ),
                calls,
            )
        }

    @Test
    fun abandoningAnInterruptedAcquisitionCancelsItAndClearsTheMarker() {
        val calls = mutableListOf<String>()
        val coordinator =
            AndroidGoogleSignInCoordinator(
                acquireCredential = {
                    calls += "acquire"
                    GoogleCredentialAcquisition.Success(idToken = "id-token", accessToken = null)
                },
                startPermanentSignIn = { provider -> calls += "start:$provider" },
                completeGoogleSignIn = { _, _ -> calls += "complete" },
                failSignIn = { failure -> calls += "fail:$failure" },
                setAcquisitionInFlight = { inFlight -> calls += "inFlight:$inFlight" },
            )

        coordinator.abandonInterruptedAcquisition()

        assertEquals(
            listOf("inFlight:false", "fail:${NativeSignInFailure.CANCELLED}"),
            calls,
        )
    }

    /**
     * `docs/CONTRACTS.md §20.10`: re-authentication opens its own attempt before the native picker and
     * completes through the same Google completion intent. It is the sign-in path with a different
     * start intent, not a second picker path.
     */
    @Test
    fun reauthenticationStartsItsOwnIntentBeforeTheSamePicker() =
        runBlocking {
            val calls = mutableListOf<String>()
            val coordinator =
                AndroidGoogleSignInCoordinator(
                    acquireCredential = {
                        GoogleCredentialAcquisition.Success(idToken = "fresh-id-token", accessToken = null)
                    },
                    startPermanentSignIn = { provider -> calls += "start:$provider" },
                    completeGoogleSignIn = { idToken, accessToken -> calls += "complete:$idToken:$accessToken" },
                    failSignIn = { failure -> calls += "fail:$failure" },
                    setAcquisitionInFlight = { inFlight -> calls += "inFlight:$inFlight" },
                )

            coordinator.acquireAndComplete { provider -> calls += "reauthenticate:$provider" }

            assertEquals(
                listOf(
                    "reauthenticate:${AuthProvider.GOOGLE}",
                    "inFlight:true",
                    "inFlight:false",
                    "complete:fresh-id-token:null",
                ),
                calls,
            )
        }

    @Test
    fun aCancelledReauthenticationPickerUsesTheClosedFailureIntent() =
        runBlocking {
            val calls = mutableListOf<String>()
            val coordinator =
                AndroidGoogleSignInCoordinator(
                    acquireCredential = {
                        GoogleCredentialAcquisition.Failure(NativeSignInFailure.CANCELLED)
                    },
                    startPermanentSignIn = { provider -> calls += "start:$provider" },
                    completeGoogleSignIn = { _, _ -> calls += "complete" },
                    failSignIn = { failure -> calls += "fail:$failure" },
                    setAcquisitionInFlight = { inFlight -> calls += "inFlight:$inFlight" },
                )

            coordinator.acquireAndComplete { provider -> calls += "reauthenticate:$provider" }

            assertEquals(
                listOf(
                    "reauthenticate:${AuthProvider.GOOGLE}",
                    "inFlight:true",
                    "inFlight:false",
                    "fail:${NativeSignInFailure.CANCELLED}",
                ),
                calls,
            )
        }

    /**
     * `E4-05`: the account-conversion entry point is the same native picker path behind the shared
     * `startAccountConversion` intent. It never starts the permanent sign-in flow and completes through
     * the same Google completion intent.
     */
    @Test
    fun conversionAcquisitionStartsTheConversionIntentAndCompletesWithGoogle() =
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
                    setAcquisitionInFlight = { inFlight -> calls += "inFlight:$inFlight" },
                )

            coordinator.acquireAndComplete { provider -> calls += "convert:$provider" }

            assertEquals(
                listOf("convert:${AuthProvider.GOOGLE}", "inFlight:true", "inFlight:false", "complete:id-token:null"),
                calls,
            )
        }

    @Test
    fun aCancelledConversionPickerReportsTheClosedCancellation() =
        runBlocking {
            val calls = mutableListOf<String>()
            val coordinator =
                AndroidGoogleSignInCoordinator(
                    acquireCredential = {
                        GoogleCredentialAcquisition.Failure(NativeSignInFailure.CANCELLED)
                    },
                    startPermanentSignIn = { provider -> calls += "start:$provider" },
                    completeGoogleSignIn = { _, _ -> calls += "complete" },
                    failSignIn = { failure -> calls += "fail:$failure" },
                    setAcquisitionInFlight = { inFlight -> calls += "inFlight:$inFlight" },
                )

            coordinator.acquireAndComplete { provider -> calls += "convert:$provider" }

            assertEquals(
                listOf(
                    "convert:${AuthProvider.GOOGLE}",
                    "inFlight:true",
                    "inFlight:false",
                    "fail:${NativeSignInFailure.CANCELLED}",
                ),
                calls,
            )
        }
}
