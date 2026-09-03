package com.ruizurraca.carapp.integration.firebase.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours

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

        // Seconds (< 1e10)
        val claimsSeconds = mapOf<String, Any>(
            "iat" to 1_700_000_000L,
            "exp" to 1_700_003_600L,
        )
        val (iatSec, expSec) = parseTokenTimestamps(claimsSeconds, now)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), iatSec)
        assertEquals(Instant.fromEpochMilliseconds(1_700_003_600_000L), expSec)

        // Milliseconds (>= 1e10)
        val claimsMillis = mapOf<String, Any>(
            "iat" to 1_700_000_000_000L,
            "exp" to 1_700_003_600_000L,
        )
        val (iatMs, expMs) = parseTokenTimestamps(claimsMillis, now)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), iatMs)
        assertEquals(Instant.fromEpochMilliseconds(1_700_003_600_000L), expMs)

        // String representations
        val claimsString = mapOf<String, Any>(
            "iat" to "1700000000",
            "exp" to "1700003600",
        )
        val (iatStr, expStr) = parseTokenTimestamps(claimsString, now)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), iatStr)
        assertEquals(Instant.fromEpochMilliseconds(1_700_003_600_000L), expStr)

        // Missing claims fall back to now and now + 1.hours
        val (iatFallback, expFallback) = parseTokenTimestamps(emptyMap(), now)
        assertEquals(now, iatFallback)
        assertEquals(now + 1.hours, expFallback)
    }

    @Test
    fun mapAuthExceptionMapsSubclassesDirectly() {
        val collision = dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException("17025", "Collision")
        kotlin.test.assertIs<FirebaseAuthGatewayException.UserCollision>(mapAuthException(collision))

        val recentLogin = dev.gitlive.firebase.auth.FirebaseAuthRecentLoginRequiredException("17014", "Recent login")
        kotlin.test.assertIs<FirebaseAuthGatewayException.RequiresRecentLogin>(mapAuthException(recentLogin))

        val network = dev.gitlive.firebase.FirebaseNetworkException("Network error")
        kotlin.test.assertIs<FirebaseAuthGatewayException.Network>(mapAuthException(network))

        val apiUnavailable = dev.gitlive.firebase.FirebaseApiNotAvailableException("API unavailable")
        kotlin.test.assertIs<FirebaseAuthGatewayException.Provider>(mapAuthException(apiUnavailable))
    }

    @Test
    fun mapWebExceptionAndInvalidCredentialsRecognizeCancellation() {
        val webCancelled = dev.gitlive.firebase.auth.FirebaseAuthWebException("17058", "User dismissed")
        kotlin.test.assertIs<FirebaseAuthGatewayException.Cancelled>(mapAuthException(webCancelled))

        val webOther = dev.gitlive.firebase.auth.FirebaseAuthWebException("99999", "Internal browser crash")
        kotlin.test.assertIs<FirebaseAuthGatewayException.Provider>(mapAuthException(webOther))

        val credCancelled = dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException("17058", "User cancelled")
        kotlin.test.assertIs<FirebaseAuthGatewayException.Cancelled>(mapAuthException(credCancelled))

        val credOther = dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException("17009", "Bad credential")
        kotlin.test.assertIs<FirebaseAuthGatewayException.Provider>(mapAuthException(credOther))
    }

    @Test
    fun mapFirebaseAuthExceptionMapsAllKnownCodes() {
        val collisionNumeric = dev.gitlive.firebase.auth.FirebaseAuthException("17025", "User collision")
        kotlin.test.assertIs<FirebaseAuthGatewayException.UserCollision>(mapFirebaseAuthException(collisionNumeric))

        val collisionString = dev.gitlive.firebase.auth.FirebaseAuthException("ERROR_CREDENTIAL_ALREADY_IN_USE", "Collision")
        kotlin.test.assertIs<FirebaseAuthGatewayException.UserCollision>(mapFirebaseAuthException(collisionString))

        val recentNumeric = dev.gitlive.firebase.auth.FirebaseAuthException("17014", "Recent login required")
        kotlin.test.assertIs<FirebaseAuthGatewayException.RequiresRecentLogin>(mapFirebaseAuthException(recentNumeric))

        val recentString = dev.gitlive.firebase.auth.FirebaseAuthException("ERROR_REQUIRES_RECENT_LOGIN", "Recent login")
        kotlin.test.assertIs<FirebaseAuthGatewayException.RequiresRecentLogin>(mapFirebaseAuthException(recentString))

        val networkNumeric = dev.gitlive.firebase.auth.FirebaseAuthException("17020", "Network failed")
        kotlin.test.assertIs<FirebaseAuthGatewayException.Network>(mapFirebaseAuthException(networkNumeric))

        val networkString = dev.gitlive.firebase.auth.FirebaseAuthException("ERROR_NETWORK_REQUEST_FAILED", "Network error")
        kotlin.test.assertIs<FirebaseAuthGatewayException.Network>(mapFirebaseAuthException(networkString))

        val webCancelNumeric = dev.gitlive.firebase.auth.FirebaseAuthException("17058", "User cancelled")
        kotlin.test.assertIs<FirebaseAuthGatewayException.Cancelled>(mapFirebaseAuthException(webCancelNumeric))

        val webCancelString = dev.gitlive.firebase.auth.FirebaseAuthException("ERROR_WEB_CONTEXT_CANCELLED", "Cancelled")
        kotlin.test.assertIs<FirebaseAuthGatewayException.Cancelled>(mapFirebaseAuthException(webCancelString))

        val permissionDeniedNumeric = dev.gitlive.firebase.auth.FirebaseAuthException("17028", "Permission denied")
        kotlin.test.assertIs<FirebaseAuthGatewayException.PermissionDenied>(mapFirebaseAuthException(permissionDeniedNumeric))

        val permissionDeniedString = dev.gitlive.firebase.auth.FirebaseAuthException("ERROR_PERMISSION_DENIED", "Caller rejected")
        kotlin.test.assertIs<FirebaseAuthGatewayException.PermissionDenied>(mapFirebaseAuthException(permissionDeniedString))

        val unknownCode = dev.gitlive.firebase.auth.FirebaseAuthException("99999", "Unrecognized auth code")
        kotlin.test.assertIs<FirebaseAuthGatewayException.Provider>(mapFirebaseAuthException(unknownCode))
    }
}
