package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.database.OwnerActiveRowCounts
import com.ruizurraca.carapp.core.model.UserSettings
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.RecordingAnalyticsTracker
import com.ruizurraca.carapp.core.testing.RecordingCrashReporter
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
 * `E4-04` (`D-241`, `docs/CONTRACTS.md §16.1`): crash collection follows the Settings analytics
 * opt-in, at startup and on every change, through the one opt-in gate of `AnalyticsEmissions`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedCrashConsentTest {
    @Test
    fun aFreshInstallCommandsCrashCollectionOff() =
        runTest {
            withGate { crashReporter, settings ->
                // A fresh install's settings bootstrap publishes analyticsEnabled = false.
                settings.value = Outcome.Ok(userSettings(analyticsEnabled = false))
                advanceUntilIdle()

                assertEquals(listOf(false), crashReporter.enableCommands, "startup commands the stored choice")
            }
        }

    @Test
    fun optingInEnablesCrashCollection() =
        runTest {
            withGate { crashReporter, settings ->
                settings.value = Outcome.Ok(userSettings(analyticsEnabled = true))
                advanceUntilIdle()

                assertEquals(listOf(true), crashReporter.enableCommands)
            }
        }

    @Test
    fun optingOutDisablesCrashCollection() =
        runTest {
            withGate { crashReporter, settings ->
                settings.value = Outcome.Ok(userSettings(analyticsEnabled = true))
                advanceUntilIdle()
                crashReporter.clear()

                settings.value = Outcome.Ok(userSettings(analyticsEnabled = false))
                advanceUntilIdle()

                assertEquals(listOf(false), crashReporter.enableCommands)
            }
        }

    private suspend fun TestScope.withGate(
        block: suspend (RecordingCrashReporter, MutableStateFlow<Outcome<UserSettings, AppError>>) -> Unit,
    ) {
        val crashReporter = RecordingCrashReporter()
        // No settings row has been read yet: the gate commands nothing until the first Ok emission.
        val settings =
            MutableStateFlow<Outcome<UserSettings, AppError>>(
                Outcome.Err(ValidationError.InvalidUnit(detail = "none")),
            )
        val emissions =
            AnalyticsEmissions(
                tracker = RecordingAnalyticsTracker(initiallyEnabled = false),
                crashReporter = crashReporter,
                ownerContext = FakeOwnerContext(),
                activeRowCounts = { OwnerActiveRowCounts(vehicleCount = 0, entryCount = 0) },
            )
        // `backgroundScope`: the collectors never complete. An unconfined dispatcher makes each
        // emission observable as it happens, exactly as in SharedAnalyticsCadenceTest.
        val collectorScope =
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        emissions.launchIn(
            scope = collectorScope,
            settings = { settings },
            status = MutableStateFlow<SyncStatus>(SyncStatus.Idle),
        )
        advanceUntilIdle()
        block(crashReporter, settings)
    }
}
