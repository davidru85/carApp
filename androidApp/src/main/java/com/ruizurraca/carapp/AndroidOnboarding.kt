package com.ruizurraca.carapp

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.ruizurraca.carapp.core.common.AuthProvider

internal enum class OnboardingDestination { WAITING, WELCOME, FIRST_VEHICLE, VEHICLE_LIST }

internal fun resolveOnboardingDestination(
    sessionPhase: SessionPhase,
    vehicleCount: Int,
): OnboardingDestination =
    when (sessionPhase) {
        SessionPhase.UNKNOWN,
        SessionPhase.DELETING,
        -> {
            OnboardingDestination.WAITING
        }

        SessionPhase.SIGNED_OUT -> {
            OnboardingDestination.WELCOME
        }

        SessionPhase.LOCAL,
        SessionPhase.ANONYMOUS,
        SessionPhase.PERMANENT,
        -> {
            if (vehicleCount == 0) OnboardingDestination.FIRST_VEHICLE else OnboardingDestination.VEHICLE_LIST
        }
    }

internal sealed interface GoogleCredentialAcquisition {
    data class Success(
        val idToken: String,
        val accessToken: String?,
    ) : GoogleCredentialAcquisition

    data class Failure(
        val reason: NativeSignInFailure,
    ) : GoogleCredentialAcquisition
}

internal class AndroidGoogleSignInCoordinator(
    private val acquireCredential: suspend () -> GoogleCredentialAcquisition,
    private val startPermanentSignIn: (AuthProvider) -> Unit,
    private val completeGoogleSignIn: (String, String?) -> Unit,
    private val failSignIn: (NativeSignInFailure) -> Unit,
) {
    suspend fun signIn() {
        startPermanentSignIn(AuthProvider.GOOGLE)
        when (val acquisition = acquireCredential()) {
            is GoogleCredentialAcquisition.Success -> {
                completeGoogleSignIn(acquisition.idToken, acquisition.accessToken)
            }

            is GoogleCredentialAcquisition.Failure -> {
                failSignIn(acquisition.reason)
            }
        }
    }
}

internal class AndroidGoogleCredentialSource(
    private val activity: Activity,
) {
    private val credentialManager = CredentialManager.create(activity)

    suspend fun acquire(): GoogleCredentialAcquisition {
        val serverClientId =
            activity.googleServerClientId()
                ?: return GoogleCredentialAcquisition.Failure(NativeSignInFailure.CONFIGURATION)
        val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (
                credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleCredentialAcquisition.Success(
                    idToken = googleCredential.idToken,
                    accessToken = null,
                )
            } else {
                GoogleCredentialAcquisition.Failure(NativeSignInFailure.CONFIGURATION)
            }
        } catch (error: GetCredentialException) {
            GoogleCredentialAcquisition.Failure(error.toNativeSignInFailure())
        } catch (error: GoogleIdTokenParsingException) {
            GoogleCredentialAcquisition.Failure(error.toNativeSignInFailure())
        }
    }

    private fun Activity.googleServerClientId(): String? {
        val resourceId = resources.getIdentifier("default_web_client_id", "string", packageName)
        return resourceId.takeIf { it != 0 }?.let(::getString)?.takeIf(String::isNotBlank)
    }
}

private fun Throwable.toNativeSignInFailure(): NativeSignInFailure =
    when (this) {
        is GetCredentialCancellationException -> {
            NativeSignInFailure.CANCELLED
        }

        is NoCredentialException,
        is GetCredentialProviderConfigurationException,
        is GoogleIdTokenParsingException,
        -> {
            NativeSignInFailure.CONFIGURATION
        }

        is GetCredentialCustomException -> {
            if (type.contains("network", ignoreCase = true)) {
                NativeSignInFailure.NETWORK
            } else {
                NativeSignInFailure.UNKNOWN
            }
        }

        else -> {
            NativeSignInFailure.UNKNOWN
        }
    }

@Composable
internal fun WelcomeScreen(
    state: SessionUiState,
    onGoogle: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(stringResource(R.string.welcome_body))
            OnboardingAction {
                Button(
                    onClick = onGoogle,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.GOOGLE),
                ) {
                    Text(stringResource(R.string.continue_with_google))
                }
            }
            OnboardingAction {
                OutlinedButton(
                    onClick = onContinueWithoutAccount,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.GUEST),
                ) {
                    Text(stringResource(R.string.continue_without_account))
                }
            }
            if (state.isBusy) CircularProgressIndicator()
            state.message?.let { ErrorText(it) }
        }
    }
}

@Composable
private fun OnboardingAction(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.ACTION)) {
        content()
    }
}

internal object OnboardingTestTags {
    const val ACTION = "onboarding_action"
    const val GOOGLE = "onboarding_google"
    const val APPLE = "onboarding_apple"
    const val GUEST = "onboarding_guest"
}
