package com.ruizurraca.carapp.core.analytics

import com.ruizurraca.carapp.core.common.AuthProvider

/**
 * The closed analytics event hierarchy of `docs/CONTRACTS.md §20.9` and `§16.1`.
 *
 * **The hierarchy is closed and the leaves are fixed.** No leaf may carry a free-text `String`;
 * every parameter is an enum, a boolean or a bucketed integer. That is what makes the
 * forbidden-payload rule enforceable by the type system rather than by review.
 *
 * Forbidden in any payload: exact odometer values, exact fuel volume, exact cost or price, notes,
 * raw entity IDs, the Firebase UID, auth tokens or credentials, raw sync payloads. Adding a leaf,
 * renaming one or splitting one requires a contract change.
 */
sealed interface AnalyticsEvent {
    data object OnboardingStarted : AnalyticsEvent

    data object OnboardingCompleted : AnalyticsEvent

    data object AnonymousSignInSelected : AnalyticsEvent

    data class PermanentSignInSelected(
        val provider: AuthProvider,
    ) : AnalyticsEvent

    data object VehicleCreated : AnalyticsEvent

    data class FuelEntryCreated(
        val isFullTank: Boolean,
        val hadNotes: Boolean,
    ) : AnalyticsEvent

    data class SyncStatusChanged(
        val status: SyncStatusCategory,
    ) : AnalyticsEvent

    data object AccountConversionStarted : AnalyticsEvent

    data object AccountConversionCompleted : AnalyticsEvent

    data class AccountConversionFailed(
        val reason: ConversionFailureReason,
    ) : AnalyticsEvent

    data object AccountDeletionStarted : AnalyticsEvent

    data object AccountDeletionCompleted : AnalyticsEvent

    data class AccountDeletionFailed(
        val reason: DeletionFailureReason,
    ) : AnalyticsEvent
}

enum class SyncStatusCategory { IDLE, SYNCING, PENDING, FAILED }

enum class ConversionFailureReason { CANCELLED, CREDENTIAL_IN_USE, NETWORK, UID_WOULD_CHANGE, UNKNOWN }

enum class DeletionFailureReason { REQUIRES_RECENT_LOGIN, REMOTE_FAILED, NETWORK, UNKNOWN }

data class AnalyticsUserProperties(
    val vehicleCountBucket: CountBucket,
    val entryCountBucket: CountBucket,
)

/**
 * Bucketed counts, so a user property never carries an exact figure.
 *
 * Bounds are exact (`§20.9`): `ZERO == 0`, `ONE == 1`, `TWO_TO_FIVE == 2..5`,
 * `SIX_TO_TWENTY == 6..20`, `MORE_THAN_TWENTY >= 21`.
 */
enum class CountBucket {
    ZERO,
    ONE,
    TWO_TO_FIVE,
    SIX_TO_TWENTY,
    MORE_THAN_TWENTY,
    ;

    companion object {
        /**
         * Buckets a list size. Callers pass the current list size from the presentation layer
         * (`§16.1`); this is the only place the bounds are written, so a caller cannot invent a
         * different bucketing.
         *
         * `MagicNumber` is suppressed because the bounds are stated as exact literals in `§20.9`.
         * Naming them would put a second spelling of the same rule in the codebase.
         */
        @Suppress("MagicNumber")
        fun ofCount(count: Int): CountBucket {
            require(count >= 0) { "count must not be negative, was $count" }
            return when (count) {
                0 -> ZERO
                1 -> ONE
                in 2..5 -> TWO_TO_FIVE
                in 6..20 -> SIX_TO_TWENTY
                else -> MORE_THAN_TWENTY
            }
        }
    }
}
