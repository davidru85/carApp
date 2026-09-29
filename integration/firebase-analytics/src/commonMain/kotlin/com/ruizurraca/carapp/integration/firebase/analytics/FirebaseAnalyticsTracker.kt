package com.ruizurraca.carapp.integration.firebase.analytics

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsProviderError
import com.ruizurraca.carapp.core.analytics.AnalyticsTracker
import com.ruizurraca.carapp.core.analytics.AnalyticsUserProperties

/**
 * The Firebase-backed `AnalyticsTracker` of `docs/CONTRACTS.md §16.1` (`D-10`).
 *
 * This is the only place in the repository where an analytics provider is reached directly. The
 * provider is behind [AnalyticsGateway], so every rule this class enforces — the closed event
 * mapping, the opt-in gate, the forbidden-payload allowlist and the failure policy — is testable
 * without a Firebase runtime, exactly as `FirebaseAuthGateway` and `FirestoreGateway` isolate the
 * other two integrations.
 *
 * Three invariants come straight from the contract and are enforced here rather than trusted to
 * callers:
 *
 * 1. **Collection is off until told otherwise.** Construction commands the provider off, because the
 *    provider's own default is not this app's default: `§16.1` requires collection disabled at
 *    startup including on a fresh install, and an implementation that only forwarded later
 *    `setEnabled` calls would collect until the first Settings visit.
 * 2. **While disabled, nothing is buffered.** [track] and [setUserProperties] drop their argument
 *    instead of holding it, so enabling later replays nothing.
 * 3. **The payload is closed.** Every parameter is an enum name or a bucket-level boolean, and the
 *    event name comes from the exhaustive mapping below. There is no code path that can put an
 *    odometer, a volume, a cost, a note, an entity ID, a UID or a token into a payload, because no
 *    such value reaches this class: the closed [AnalyticsEvent] hierarchy cannot carry one.
 *
 * A provider failure never propagates. The contract declares three non-suspending `Unit` methods
 * with no error channel, so a throw would surface as an unhandled exception on a product path for a
 * feature that exists only to observe. The failure is classified into the closed
 * [AnalyticsProviderError] taxonomy and handed to [onProviderError].
 */
class FirebaseAnalyticsTracker internal constructor(
    enabled: Boolean = false,
    private val gateway: AnalyticsGateway,
    private val onProviderError: (AnalyticsProviderError) -> Unit = {},
) : AnalyticsTracker {
    constructor() : this(
        enabled = false,
        gateway = GitLiveAnalyticsGateway(),
        onProviderError = {},
    )

    private var enabled = enabled

    init {
        // An explicit off at construction, not an assumption. See invariant 1.
        commandCollection(enabled)
    }

    override fun track(event: AnalyticsEvent) {
        if (!enabled) return
        val mapped = event.toProviderEvent()
        safely {
            gateway.logEvent(mapped.name, mapped.parameters)
        }
    }

    override fun setUserProperties(properties: AnalyticsUserProperties) {
        if (!enabled) return
        safely {
            gateway.setUserProperty(VEHICLE_COUNT_BUCKET, properties.vehicleCountBucket.name)
            gateway.setUserProperty(ENTRY_COUNT_BUCKET, properties.entryCountBucket.name)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        commandCollection(enabled)
    }

    private fun commandCollection(enabled: Boolean) {
        safely { gateway.setCollectionEnabled(enabled) }
    }

    /**
     * Converts the closed [AnalyticsEvent] hierarchy into a provider name and parameter map.
     *
     * The `when` is exhaustive with **no `else`**, which is `E3-09`'s first acceptance criterion: a
     * new leaf, a renamed one or a split one stops this function compiling rather than silently
     * mapping to nothing. The map values are `String` (enum names) and `Boolean` (bucket-level
     * flags) only, so the §16.1 payload rule is structural, not a review convention.
     */
    private fun AnalyticsEvent.toProviderEvent(): ProviderEvent =
        when (this) {
            AnalyticsEvent.OnboardingStarted -> {
                ProviderEvent("onboarding_started", emptyMap())
            }

            AnalyticsEvent.OnboardingCompleted -> {
                ProviderEvent("onboarding_completed", emptyMap())
            }

            AnalyticsEvent.AnonymousSignInSelected -> {
                ProviderEvent("anonymous_sign_in_selected", emptyMap())
            }

            is AnalyticsEvent.PermanentSignInSelected -> {
                ProviderEvent(
                    "permanent_sign_in_selected",
                    mapOf(PROVIDER to provider.name),
                )
            }

            AnalyticsEvent.VehicleCreated -> {
                ProviderEvent("vehicle_created", emptyMap())
            }

            is AnalyticsEvent.FuelEntryCreated -> {
                ProviderEvent(
                    "fuel_entry_created",
                    mapOf(
                        IS_FULL_TANK to isFullTank,
                        HAD_NOTES to hadNotes,
                    ),
                )
            }

            is AnalyticsEvent.SyncStatusChanged -> {
                ProviderEvent(
                    "sync_status_changed",
                    mapOf(STATUS to status.name),
                )
            }

            AnalyticsEvent.AccountConversionStarted -> {
                ProviderEvent("account_conversion_started", emptyMap())
            }

            AnalyticsEvent.AccountConversionCompleted -> {
                ProviderEvent("account_conversion_completed", emptyMap())
            }

            is AnalyticsEvent.AccountConversionFailed -> {
                ProviderEvent(
                    "account_conversion_failed",
                    mapOf(REASON to reason.name),
                )
            }

            AnalyticsEvent.AccountDeletionStarted -> {
                ProviderEvent("account_deletion_started", emptyMap())
            }

            AnalyticsEvent.AccountDeletionCompleted -> {
                ProviderEvent("account_deletion_completed", emptyMap())
            }

            is AnalyticsEvent.AccountDeletionFailed -> {
                ProviderEvent(
                    "account_deletion_failed",
                    mapOf(REASON to reason.name),
                )
            }
        }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (failure: Throwable) {
            if (failure is kotlin.coroutines.cancellation.CancellationException) throw failure
            onProviderError(AnalyticsProviderError.PROVIDER_FAILED)
        }
    }

    private data class ProviderEvent(
        val name: String,
        val parameters: Map<String, Any>,
    )

    private companion object {
        // The §16.1 parameter keys, written once. A new key is a contract change.
        const val PROVIDER = "provider"
        const val IS_FULL_TANK = "is_full_tank"
        const val HAD_NOTES = "had_notes"
        const val STATUS = "status"
        const val REASON = "reason"
        const val VEHICLE_COUNT_BUCKET = "vehicle_count_bucket"
        const val ENTRY_COUNT_BUCKET = "entry_count_bucket"
    }
}
