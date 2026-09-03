package com.ruizurraca.carapp.integration.firebase.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class GitLiveFirebaseAuthGatewayTest {
    @Test
    fun isCancellationRecognizesCancellationMessagesAndCodes() {
        assertTrue(isCancellation(code = "17058", message = null))
        assertTrue(isCancellation(code = ERROR_WEB_CONTEXT_CANCELLED, message = null))
        assertTrue(isCancellation(code = null, message = "The user cancelled the sign-in flow."))
        assertTrue(isCancellation(code = null, message = "The dialog was dismissed."))
        assertTrue(isCancellation(code = null, message = "Error 17058 occurred during operation"))
    }

    @Test
    fun isCancellationDoesNotClassifyNonCancellationErrorsAsCancelled() {
        assertFalse(isCancellation(code = "17020", message = "Network request failed"))
        assertFalse(isCancellation(code = "17014", message = "Recent login required"))
        assertFalse(isCancellation(code = "17025", message = "Credential already in use"))
        assertFalse(isCancellation(code = null, message = "Invalid email or password"))
        assertFalse(isCancellation(code = null, message = "Internal server error"))
        assertFalse(isCancellation(code = null, message = null))
    }

    @Test
    fun parseTokenTimestampsHandlesSecondsAndMillisecondsAndFallbacks() {
        val claimsSeconds =
            mapOf<String, Any>(
                "iat" to 1_700_000_000L,
                "exp" to 1_700_003_600L,
            )
        val (iatSec, expSec) = parseTokenTimestamps(claimsSeconds)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), iatSec)
        assertEquals(Instant.fromEpochMilliseconds(1_700_003_600_000L), expSec)

        val claimsMillis =
            mapOf<String, Any>(
                "iat" to 1_700_000_000_000L,
                "exp" to 1_700_003_600_000L,
            )
        val (iatMs, expMs) = parseTokenTimestamps(claimsMillis)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), iatMs)
        assertEquals(Instant.fromEpochMilliseconds(1_700_003_600_000L), expMs)

        val claimsString =
            mapOf<String, Any>(
                "iat" to "1700000000",
                "exp" to "1700003600",
            )
        val (iatStr, expStr) = parseTokenTimestamps(claimsString)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), iatStr)
        assertEquals(Instant.fromEpochMilliseconds(1_700_003_600_000L), expStr)

        // Missing exp falls back to iat + 1.hours
        val claimsOnlyIat = mapOf<String, Any>("iat" to 1_700_000_000L)
        val (iatOnly, expFallback) = parseTokenTimestamps(claimsOnlyIat)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), iatOnly)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L) + 1.hours, expFallback)
    }

    @Test
    fun parseTokenTimestampsThrowsWhenIatIsMissing() {
        assertFailsWith<FirebaseAuthGatewayException.Provider> {
            parseTokenTimestamps(emptyMap())
        }
    }

    @Test
    fun parseTokenTimestampsThrowsWhenIatIsMalformed() {
        assertFailsWith<FirebaseAuthGatewayException.Provider> {
            parseTokenTimestamps(mapOf("iat" to "not-a-number"))
        }
    }

    @Test
    fun classifyAuthFailureMapsCollisionCodes() {
        assertIs<FirebaseAuthGatewayException.UserCollision>(
            classifyAuthFailure(code = "17025", message = "Collision", kind = AuthFailureKind.Auth),
        )
        assertIs<FirebaseAuthGatewayException.UserCollision>(
            classifyAuthFailure(
                code = ERROR_CREDENTIAL_ALREADY_IN_USE,
                message = "Collision",
                kind = AuthFailureKind.Auth,
            ),
        )
    }

    @Test
    fun classifyAuthFailureMapsRecentLoginCodes() {
        assertIs<FirebaseAuthGatewayException.RequiresRecentLogin>(
            classifyAuthFailure(code = "17014", message = "Recent login", kind = AuthFailureKind.Auth),
        )
        assertIs<FirebaseAuthGatewayException.RequiresRecentLogin>(
            classifyAuthFailure(
                code = ERROR_REQUIRES_RECENT_LOGIN,
                message = "Recent login",
                kind = AuthFailureKind.Auth,
            ),
        )
    }

    @Test
    fun classifyAuthFailureMapsNetworkCodes() {
        assertIs<FirebaseAuthGatewayException.Network>(
            classifyAuthFailure(code = "17020", message = "Network error", kind = AuthFailureKind.Auth),
        )
        assertIs<FirebaseAuthGatewayException.Network>(
            classifyAuthFailure(
                code = ERROR_NETWORK_REQUEST_FAILED,
                message = "Network error",
                kind = AuthFailureKind.Auth,
            ),
        )
    }

    @Test
    fun classifyAuthFailureMapsAppNotAuthorizedToProvider() {
        // 17028 is FIRAuthErrorCodeAppNotAuthorized (configuration failure), NOT PermissionDenied
        assertIs<FirebaseAuthGatewayException.Provider>(
            classifyAuthFailure(code = "17028", message = "App not authorized", kind = AuthFailureKind.Auth),
        )
    }

    @Test
    fun classifyAuthFailureMapsWebAndInvalidCredentialsToProviderOrCancelled() {
        assertIs<FirebaseAuthGatewayException.Cancelled>(
            classifyAuthFailure(code = "17058", message = "User dismissed", kind = AuthFailureKind.Web),
        )
        assertIs<FirebaseAuthGatewayException.Provider>(
            classifyAuthFailure(code = "99999", message = "Browser crash", kind = AuthFailureKind.Web),
        )
        assertIs<FirebaseAuthGatewayException.Cancelled>(
            classifyAuthFailure(code = "17058", message = "Cancelled", kind = AuthFailureKind.InvalidCredentials),
        )
        assertIs<FirebaseAuthGatewayException.Provider>(
            classifyAuthFailure(code = "17009", message = "Bad credential", kind = AuthFailureKind.InvalidCredentials),
        )
    }

    @Test
    fun classifyAuthFailureMapsGenericFirebaseException() {
        assertIs<FirebaseAuthGatewayException.Cancelled>(
            classifyAuthFailure(code = null, message = "Dialog was cancelled", kind = AuthFailureKind.Generic),
        )
        assertIs<FirebaseAuthGatewayException.Unknown>(
            classifyAuthFailure(code = null, message = "Some unknown internal error", kind = AuthFailureKind.Generic),
        )
    }

    @Test
    fun accountDeletionInvokerPermissionDeniedPropagatesThroughGateway() =
        runTest {
            val invoker =
                AccountDeletionInvoker {
                    throw FirebaseAuthGatewayException.PermissionDenied(IllegalStateException("Caller UID mismatch"))
                }
            val gateway =
                GitLiveFirebaseAuthGateway(
                    deletionInvoker = invoker,
                )
            val thrown =
                assertFailsWith<FirebaseAuthGatewayException.PermissionDenied> {
                    gateway.executeServerAccountDeletion("token-123")
                }
            assertEquals("Caller UID mismatch", thrown.cause?.message)
        }
}
