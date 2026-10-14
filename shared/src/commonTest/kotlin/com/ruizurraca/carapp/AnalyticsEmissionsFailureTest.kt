package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.database.OwnerActiveRowCounts
import com.ruizurraca.carapp.core.model.UserSettings
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.RecordingAnalyticsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `E3-09`: metrics are best-effort (`D-10`, ADR-0198). A bucket count that cannot be read drops that
 * one `setUserProperties` call; the failure never reaches the writer that committed a row, and it
 * never ends the opt-in gate, so a later opt-out still reaches the tracker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsEmissionsFailureTest {
    private var countFails = false
    private val tracker = RecordingAnalyticsTracker(initiallyEnabled = false)
    private val settings =
        MutableStateFlow<Outcome<UserSettings, AppError>>(Outcome.Ok(userSettings(analyticsEnabled = false)))
    private val emissions =
        AnalyticsEmissions(
            tracker = tracker,
            ownerContext = FakeOwnerContext(),
            activeRowCounts = { _ ->
                check(!countFails) { "count unavailable" }
                OwnerActiveRowCounts(vehicleCount = 1, entryCount = 0)
            },
        )

    @Test
    fun aFailingCountAtOptInStillEnablesCollectionAndKeepsTheGateAlive() =
        runTest {
            launchEmissions()
            tracker.clear()
            countFails = true

            settings.value = Outcome.Ok(userSettings(analyticsEnabled = true))
            advanceUntilIdle()
            settings.value = Outcome.Ok(userSettings(analyticsEnabled = false))
            advanceUntilIdle()

            assertEquals(
                listOf(true, false),
                tracker.enableCommands,
                "a failed count at opt-in must not end the gate: the later opt-out must still be applied",
            )
            assertEquals(emptyList(), tracker.userProperties, "a failed count drops only its own refresh")
        }

    @Test
    fun aFailingCountAfterAWriteDoesNotReachTheWriter() =
        runTest {
            launchEmissions()
            settings.value = Outcome.Ok(userSettings(analyticsEnabled = true))
            advanceUntilIdle()
            tracker.clear()
            countFails = true

            emissions.trackAndRefresh(AnalyticsEvent.VehicleCreated)
            emissions.refreshUserProperties()

            assertEquals(
                listOf<AnalyticsEvent>(AnalyticsEvent.VehicleCreated),
                tracker.events,
                "the write event is still recorded when the bucket count fails",
            )
            assertEquals(emptyList(), tracker.userProperties, "a failed count drops only its own refresh")
        }

    private fun TestScope.launchEmissions() {
        // `backgroundScope`: the two collectors never complete. An unconfined test dispatcher makes
        // each settings emission observable as it happens, as in `SharedAnalyticsCadenceTest`.
        emissions.launchIn(
            scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            settings = { settings },
            status = MutableStateFlow<SyncStatus>(SyncStatus.Idle),
        )
        advanceUntilIdle()
    }
}
