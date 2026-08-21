package com.ruizurraca.carapp.core.analytics

import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.AuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsContractTest {
    /**
     * The `when` below has no `else`. If a leaf is added, renamed or removed, this stops
     * compiling, which is how `docs/CONTRACTS.md §20.9` "the hierarchy is closed and the leaves
     * are fixed" is enforced rather than reviewed.
     */
    private fun AnalyticsEvent.discriminator(): String =
        when (this) {
            AnalyticsEvent.OnboardingStarted -> "OnboardingStarted"
            AnalyticsEvent.OnboardingCompleted -> "OnboardingCompleted"
            AnalyticsEvent.AnonymousSignInSelected -> "AnonymousSignInSelected"
            is AnalyticsEvent.PermanentSignInSelected -> "PermanentSignInSelected"
            AnalyticsEvent.VehicleCreated -> "VehicleCreated"
            is AnalyticsEvent.FuelEntryCreated -> "FuelEntryCreated"
            is AnalyticsEvent.SyncStatusChanged -> "SyncStatusChanged"
            AnalyticsEvent.AccountConversionStarted -> "AccountConversionStarted"
            AnalyticsEvent.AccountConversionCompleted -> "AccountConversionCompleted"
            is AnalyticsEvent.AccountConversionFailed -> "AccountConversionFailed"
            AnalyticsEvent.AccountDeletionStarted -> "AccountDeletionStarted"
            AnalyticsEvent.AccountDeletionCompleted -> "AccountDeletionCompleted"
            is AnalyticsEvent.AccountDeletionFailed -> "AccountDeletionFailed"
        }

    @Test
    fun theHierarchyIsExactlyTheThirteenDocumentedLeaves() {
        val allLeaves =
            listOf(
                AnalyticsEvent.OnboardingStarted,
                AnalyticsEvent.OnboardingCompleted,
                AnalyticsEvent.AnonymousSignInSelected,
                AnalyticsEvent.PermanentSignInSelected(AuthProvider.GOOGLE),
                AnalyticsEvent.VehicleCreated,
                AnalyticsEvent.FuelEntryCreated(isFullTank = true, hadNotes = false),
                AnalyticsEvent.SyncStatusChanged(SyncStatusCategory.IDLE),
                AnalyticsEvent.AccountConversionStarted,
                AnalyticsEvent.AccountConversionCompleted,
                AnalyticsEvent.AccountConversionFailed(ConversionFailureReason.CANCELLED),
                AnalyticsEvent.AccountDeletionStarted,
                AnalyticsEvent.AccountDeletionCompleted,
                AnalyticsEvent.AccountDeletionFailed(DeletionFailureReason.NETWORK),
            )

        assertEquals(13, allLeaves.size)
        assertEquals(allLeaves.size, allLeaves.map { it.discriminator() }.toSet().size)
    }

    @Test
    fun theAuthErrorToConversionMappingIsExhaustiveAndNormative() {
        val expected =
            mapOf(
                AuthError.Cancelled to ConversionFailureReason.CANCELLED,
                AuthError.CredentialAlreadyInUse to ConversionFailureReason.CREDENTIAL_IN_USE,
                AuthError.NetworkUnavailable to ConversionFailureReason.NETWORK,
                AuthError.UidWouldChange to ConversionFailureReason.UID_WOULD_CHANGE,
                AuthError.AccountDeletionRemoteFailed to ConversionFailureReason.UNKNOWN,
                AuthError.PermissionDenied to ConversionFailureReason.UNKNOWN,
                AuthError.ProviderUnavailable to ConversionFailureReason.UNKNOWN,
                AuthError.RequiresRecentLogin to ConversionFailureReason.UNKNOWN,
                AuthError.TokenExpired to ConversionFailureReason.UNKNOWN,
                AuthError.Unknown to ConversionFailureReason.UNKNOWN,
            )

        assertEquals(10, expected.size, "AuthError has ten leaves; all must be mapped")
        expected.forEach { (error, reason) -> assertEquals(reason, error.toConversionFailureReason()) }
    }

    @Test
    fun theAuthErrorToDeletionMappingIsExhaustiveAndNormative() {
        val expected =
            mapOf(
                AuthError.RequiresRecentLogin to DeletionFailureReason.REQUIRES_RECENT_LOGIN,
                AuthError.AccountDeletionRemoteFailed to DeletionFailureReason.REMOTE_FAILED,
                AuthError.NetworkUnavailable to DeletionFailureReason.NETWORK,
                AuthError.Cancelled to DeletionFailureReason.UNKNOWN,
                AuthError.CredentialAlreadyInUse to DeletionFailureReason.UNKNOWN,
                AuthError.PermissionDenied to DeletionFailureReason.UNKNOWN,
                AuthError.ProviderUnavailable to DeletionFailureReason.UNKNOWN,
                AuthError.TokenExpired to DeletionFailureReason.UNKNOWN,
                AuthError.UidWouldChange to DeletionFailureReason.UNKNOWN,
                AuthError.Unknown to DeletionFailureReason.UNKNOWN,
            )

        assertEquals(10, expected.size, "AuthError has ten leaves; all must be mapped")
        expected.forEach { (error, reason) -> assertEquals(reason, error.toDeletionFailureReason()) }
    }

    @Test
    fun countBucketBoundsAreExact() {
        assertEquals(CountBucket.ZERO, CountBucket.ofCount(0))
        assertEquals(CountBucket.ONE, CountBucket.ofCount(1))
        listOf(2, 3, 5).forEach { assertEquals(CountBucket.TWO_TO_FIVE, CountBucket.ofCount(it)) }
        listOf(6, 13, 20).forEach { assertEquals(CountBucket.SIX_TO_TWENTY, CountBucket.ofCount(it)) }
        listOf(21, 500).forEach { assertEquals(CountBucket.MORE_THAN_TWENTY, CountBucket.ofCount(it)) }
    }

    @Test
    fun syncStatusCategoryIsTheFourDocumentedValues() {
        assertEquals(
            listOf(
                SyncStatusCategory.IDLE,
                SyncStatusCategory.SYNCING,
                SyncStatusCategory.PENDING,
                SyncStatusCategory.FAILED,
            ),
            SyncStatusCategory.entries.toList(),
        )
    }
}
