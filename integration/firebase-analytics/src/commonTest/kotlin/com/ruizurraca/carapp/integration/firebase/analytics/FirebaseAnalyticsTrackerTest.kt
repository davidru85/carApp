package com.ruizurraca.carapp.integration.firebase.analytics

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsTracker
import com.ruizurraca.carapp.core.analytics.AnalyticsUserProperties
import com.ruizurraca.carapp.core.analytics.ConversionFailureReason
import com.ruizurraca.carapp.core.analytics.CountBucket
import com.ruizurraca.carapp.core.analytics.DeletionFailureReason
import com.ruizurraca.carapp.core.analytics.SyncStatusCategory
import com.ruizurraca.carapp.core.common.AuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `E3-09` acceptance criteria 1, 2 and 3 for `docs/CONTRACTS.md §16.1`.
 *
 * The tracker is exercised over [RecordingGateway], an in-test stand-in for the Firebase SDK, so
 * these tests describe the contract the Firebase implementation enforces **before** any provider
 * call is made: the closed event mapping, the opt-in gate and the forbidden-payload rule.
 */
class FirebaseAnalyticsTrackerTest {
    @Test
    fun everyEventLeafMapsToAnEventNameAndParameterSet() {
        val expectation = expectedSurface()

        assertEquals(13, expectation.size, "§20.9 declares exactly thirteen leaves")

        expectation.forEach { (event, expected) ->
            val gateway = RecordingGateway()
            val tracker = FirebaseAnalyticsTracker(enabled = true, gateway = gateway)

            tracker.track(event)

            assertEquals(
                listOf(expected.name),
                gateway.loggedEvents.map { it.name },
                "every leaf maps to exactly one event name",
            )
            assertEquals(
                listOf(expected.parameters),
                gateway.loggedEvents.map { it.parameters },
                "every leaf maps to exactly one parameter set",
            )
        }
    }

    // The expected surface, written as an exhaustive `when` with no `else`: adding, renaming or
    // removing an `AnalyticsEvent` leaf stops this compile, which is the point. Every parameter is an
    // enum name or a boolean, and no leaf contributes a free-text value.
    //
    // `LongMethod` and `CyclomaticComplexMethod` are suppressed because this is a flat data table
    // with one row per contract leaf, not branching logic: splitting it would put half the expected
    // surface in a second place, which is exactly the drift the table exists to prevent.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun expectedSurface(): Map<AnalyticsEvent, EventExpectation> =
        analyticsLeaves().associateWith { event ->
            when (event) {
                AnalyticsEvent.OnboardingStarted -> {
                    EventExpectation("onboarding_started", emptyMap())
                }

                AnalyticsEvent.OnboardingCompleted -> {
                    EventExpectation("onboarding_completed", emptyMap())
                }

                AnalyticsEvent.AnonymousSignInSelected -> {
                    EventExpectation("anonymous_sign_in_selected", emptyMap())
                }

                is AnalyticsEvent.PermanentSignInSelected -> {
                    EventExpectation(
                        "permanent_sign_in_selected",
                        mapOf("provider" to event.provider.name),
                    )
                }

                AnalyticsEvent.VehicleCreated -> {
                    EventExpectation("vehicle_created", emptyMap())
                }

                is AnalyticsEvent.FuelEntryCreated -> {
                    EventExpectation(
                        "fuel_entry_created",
                        mapOf(
                            "is_full_tank" to event.isFullTank,
                            "had_notes" to event.hadNotes,
                        ),
                    )
                }

                is AnalyticsEvent.SyncStatusChanged -> {
                    EventExpectation(
                        "sync_status_changed",
                        mapOf("status" to event.status.name),
                    )
                }

                AnalyticsEvent.AccountConversionStarted -> {
                    EventExpectation("account_conversion_started", emptyMap())
                }

                AnalyticsEvent.AccountConversionCompleted -> {
                    EventExpectation("account_conversion_completed", emptyMap())
                }

                is AnalyticsEvent.AccountConversionFailed -> {
                    EventExpectation(
                        "account_conversion_failed",
                        mapOf("reason" to event.reason.name),
                    )
                }

                AnalyticsEvent.AccountDeletionStarted -> {
                    EventExpectation("account_deletion_started", emptyMap())
                }

                AnalyticsEvent.AccountDeletionCompleted -> {
                    EventExpectation("account_deletion_completed", emptyMap())
                }

                is AnalyticsEvent.AccountDeletionFailed -> {
                    EventExpectation(
                        "account_deletion_failed",
                        mapOf("reason" to event.reason.name),
                    )
                }
            }
        }

    @Test
    fun noParameterValueIsDerivedFromForbiddenPayload() {
        // The §16.1 allowlist, stated as the complete set of parameter keys this module may emit.
        // An entity id, an odometer, a volume, a cost, a note, a UID or a token would have to arrive
        // as one of these keys or as a new value type; the key set is closed and every value type is
        // an enum name, a boolean or a bucketed integer.
        val allowedKeys = setOf("provider", "is_full_tank", "had_notes", "status", "reason")
        val allowedValueTypes = setOf(String::class, Boolean::class, Int::class, Long::class)

        analyticsLeaves().forEach { event ->
            val gateway = RecordingGateway()
            FirebaseAnalyticsTracker(enabled = true, gateway = gateway).track(event)

            val logged = gateway.loggedEvents.single()
            assertTrue(
                logged.parameters.keys.all { key -> key in allowedKeys },
                "$event emitted an undeclared parameter key: ${logged.parameters.keys - allowedKeys}",
            )
            logged.parameters.forEach { (key, value) ->
                assertTrue(
                    allowedValueTypes.any { type -> type.isInstance(value) },
                    "$event parameter '$key' has a type outside the closed allowlist: ${value::class}",
                )
            }
        }

        // A `FuelEntryCreated` carrying notes still cannot leak them: the leaf carries booleans only,
        // so the emitted value is a bucket-level flag and never the text.
        val gateway = RecordingGateway()
        FirebaseAnalyticsTracker(enabled = true, gateway = gateway)
            .track(AnalyticsEvent.FuelEntryCreated(isFullTank = true, hadNotes = true))
        assertEquals(
            mapOf<String, Any>("is_full_tank" to true, "had_notes" to true),
            gateway.loggedEvents.single().parameters,
        )
    }

    private data class EventExpectation(
        val name: String,
        val parameters: Map<String, Any>,
    )

    private fun analyticsLeaves(): List<AnalyticsEvent> =
        listOf(
            AnalyticsEvent.OnboardingStarted,
            AnalyticsEvent.OnboardingCompleted,
            AnalyticsEvent.AnonymousSignInSelected,
            AnalyticsEvent.PermanentSignInSelected(AuthProvider.GOOGLE),
            AnalyticsEvent.VehicleCreated,
            AnalyticsEvent.FuelEntryCreated(isFullTank = true, hadNotes = false),
            AnalyticsEvent.SyncStatusChanged(SyncStatusCategory.PENDING),
            AnalyticsEvent.AccountConversionStarted,
            AnalyticsEvent.AccountConversionCompleted,
            AnalyticsEvent.AccountConversionFailed(ConversionFailureReason.NETWORK),
            AnalyticsEvent.AccountDeletionStarted,
            AnalyticsEvent.AccountDeletionCompleted,
            AnalyticsEvent.AccountDeletionFailed(DeletionFailureReason.REMOTE_FAILED),
        )
}
