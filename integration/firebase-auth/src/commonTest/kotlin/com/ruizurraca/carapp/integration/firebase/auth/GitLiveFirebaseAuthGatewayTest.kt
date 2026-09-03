package com.ruizurraca.carapp.integration.firebase.auth

import dev.gitlive.firebase.FirebaseApiNotAvailableException
import dev.gitlive.firebase.FirebaseNetworkException
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthRecentLoginRequiredException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseAuthWebException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class GitLiveFirebaseAuthGatewayTest {
    @Test
    fun isCancellationRecognizesCancellationMessagesAndCodes() {
        assertTrue(isCancellation(code = "17058", message = null))
        assertTrue(isCancellation(code = "ERROR_WEB_CONTEXT_CANCELLED", message = null))
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
        val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)

        val claimsSeconds =
            mapOf<String, Any>(
                "iat" to 1_700_000_000L,
                "exp" to 1_700_003_600L,
            )
        val (iatSec, expSec) = parseTokenTimestamps(claimsSeconds, now)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), iatSec)
        assertEquals(Instant.fromEpochMilliseconds(1_700_003_600_000L), expSec)

        val claimsMillis =
            mapOf<String, Any>(
                "iat" to 1_700_000_000_000L,
                "exp" to 1_700_003_600_000L,
            )
        val (iatMs, expMs) = parseTokenTimestamps(claimsMillis, now)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), iatMs)
        assertEquals(Instant.fromEpochMilliseconds(1_700_003_600_000L), expMs)

        val claimsString =
            mapOf<String, Any>(
                "iat" to "1700000000",
                "exp" to "1700003600",
            )
        val (iatStr, expStr) = parseTokenTimestamps(claimsString, now)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), iatStr)
        assertEquals(Instant.fromEpochMilliseconds(1_700_003_600_000L), expStr)

        val (iatFallback, expFallback) = parseTokenTimestamps(emptyMap(), now)
        assertEquals(now, iatFallback)
        assertEquals(now + 1.hours, expFallback)
    }

    @Test
    fun mapAuthExceptionMapsSubclassesDirectly() {
        val collision = FirebaseAuthUserCollisionException("17025", "Collision")
        assertIs<FirebaseAuthGatewayException.UserCollision>(mapAuthException(collision))

        val recentLogin = FirebaseAuthRecentLoginRequiredException("17014", "Recent login")
        assertIs<FirebaseAuthGatewayException.RequiresRecentLogin>(mapAuthException(recentLogin))

        val network = FirebaseNetworkException("Network error")
        assertIs<FirebaseAuthGatewayException.Network>(mapAuthException(network))

        val apiUnavailable = FirebaseApiNotAvailableException("API unavailable")
        assertIs<FirebaseAuthGatewayException.Provider>(mapAuthException(apiUnavailable))
    }

    @Test
    fun mapWebExceptionAndInvalidCredentialsRecognizeCancellation() {
        val webCancelled = FirebaseAuthWebException("17058", "User dismissed")
        assertIs<FirebaseAuthGatewayException.Cancelled>(mapAuthException(webCancelled))

        val webOther = FirebaseAuthWebException("99999", "Internal browser crash")
        assertIs<FirebaseAuthGatewayException.Provider>(mapAuthException(webOther))

        val credCancelled = FirebaseAuthInvalidCredentialsException("17058", "User cancelled")
        assertIs<FirebaseAuthGatewayException.Cancelled>(mapAuthException(credCancelled))

        val credOther = FirebaseAuthInvalidCredentialsException("17009", "Bad credential")
        assertIs<FirebaseAuthGatewayException.Provider>(mapAuthException(credOther))
    }

    @Test
    fun mapFirebaseAuthExceptionMapsAllKnownCodes() {
        val collisionNumeric = FirebaseAuthException("17025", "User collision")
        assertIs<FirebaseAuthGatewayException.UserCollision>(mapFirebaseAuthException(collisionNumeric))

        val collisionString = FirebaseAuthException(ERROR_CREDENTIAL_ALREADY_IN_USE, "Collision")
        assertIs<FirebaseAuthGatewayException.UserCollision>(mapFirebaseAuthException(collisionString))

        val recentNumeric = FirebaseAuthException("17014", "Recent login required")
        assertIs<FirebaseAuthGatewayException.RequiresRecentLogin>(mapFirebaseAuthException(recentNumeric))

        val recentString = FirebaseAuthException(ERROR_REQUIRES_RECENT_LOGIN, "Recent login")
        assertIs<FirebaseAuthGatewayException.RequiresRecentLogin>(mapFirebaseAuthException(recentString))

        val networkNumeric = FirebaseAuthException("17020", "Network failed")
        assertIs<FirebaseAuthGatewayException.Network>(mapFirebaseAuthException(networkNumeric))

        val networkString = FirebaseAuthException(ERROR_NETWORK_REQUEST_FAILED, "Network error")
        assertIs<FirebaseAuthGatewayException.Network>(mapFirebaseAuthException(networkString))

        val webCancelNumeric = FirebaseAuthException("17058", "User cancelled")
        assertIs<FirebaseAuthGatewayException.Cancelled>(mapFirebaseAuthException(webCancelNumeric))

        val webCancelString = FirebaseAuthException(ERROR_WEB_CONTEXT_CANCELLED, "Cancelled")
        assertIs<FirebaseAuthGatewayException.Cancelled>(mapFirebaseAuthException(webCancelString))

        val permissionDeniedNumeric = FirebaseAuthException("17028", "Permission denied")
        assertIs<FirebaseAuthGatewayException.PermissionDenied>(mapFirebaseAuthException(permissionDeniedNumeric))

        val permissionDeniedString = FirebaseAuthException(ERROR_PERMISSION_DENIED, "Caller rejected")
        assertIs<FirebaseAuthGatewayException.PermissionDenied>(mapFirebaseAuthException(permissionDeniedString))

        val unknownCode = FirebaseAuthException("99999", "Unrecognized auth code")
        assertIs<FirebaseAuthGatewayException.Provider>(mapFirebaseAuthException(unknownCode))
    }
}
