package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.FOREGROUND_RESUME_THRESHOLD_MS
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.sync.SyncController
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.core.testing.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `docs/CONTRACTS.md §9.8` foreground trigger.
 *
 * The host observes the platform lifecycle and reports how long the app was in the background;
 * applying `FOREGROUND_RESUME_THRESHOLD_MS` is shared behaviour, so both hosts report the same
 * primitive and neither repeats the comparison. A return inside the threshold is deliberately not a
 * trigger: the work it would re-run is still fresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncStateHolderForegroundTest {
    @Test
    fun aBackgroundStayLongerThanTheThresholdTriggersAnAppForegroundCycle() =
        runTest {
            val controller = RecordingSyncController()
            val holder = holder(controller)

            holder.onForegroundReturn(FOREGROUND_RESUME_THRESHOLD_MS + 1)
            advanceUntilIdle()

            assertEquals(
                listOf(SyncTrigger.AppForeground),
                controller.requested,
                "a return after more than FOREGROUND_RESUME_THRESHOLD_MS MUST trigger AppForeground",
            )
            holder.close()
        }

    @Test
    fun aBackgroundStayInsideTheThresholdDoesNotTrigger() =
        runTest {
            val controller = RecordingSyncController()
            val holder = holder(controller)

            holder.onForegroundReturn(FOREGROUND_RESUME_THRESHOLD_MS - 1)
            advanceUntilIdle()

            assertEquals(
                emptyList(),
                controller.requested,
                "a return inside the threshold MUST NOT trigger a cycle",
            )
            holder.close()
        }

    @Test
    fun theThresholdBoundaryItselfDoesNotTrigger() =
        runTest {
            val controller = RecordingSyncController()
            val holder = holder(controller)

            // The rule is *more than* the threshold, so the exact value is not yet a trigger.
            holder.onForegroundReturn(FOREGROUND_RESUME_THRESHOLD_MS)
            advanceUntilIdle()

            assertEquals(emptyList(), controller.requested)
            holder.close()
        }

    @Test
    fun aColdStartAlwaysTriggersBecauseItHasNoBackgroundDuration() =
        runTest {
            val controller = RecordingSyncController()
            val holder = holder(controller)

            // `§9.8` names the cold start as its own trigger case. It has no measurable background
            // duration, so it is reported as `null` rather than being encoded as some duration.
            holder.onForegroundReturn(null)
            advanceUntilIdle()

            assertEquals(
                listOf(SyncTrigger.AppForeground),
                controller.requested,
                "a cold start MUST trigger, and MUST NOT be encoded as a background duration",
            )
            holder.close()
        }

    @Test
    fun aForegroundReturnAfterCloseTriggersNothing() =
        runTest {
            val controller = RecordingSyncController()
            val holder = holder(controller)
            holder.close()

            holder.onForegroundReturn(FOREGROUND_RESUME_THRESHOLD_MS * 2)
            advanceUntilIdle()

            assertEquals(emptyList(), controller.requested, "a closed holder MUST NOT trigger a cycle")
        }

    private fun kotlinx.coroutines.test.TestScope.holder(controller: SyncController): SyncStateHolder =
        SyncStateHolder(
            scope = this,
            controller = controller,
            connectivity = FakeConnectivityObserver(),
            dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        )
}

/** Records the triggers it is asked to request, which is the observable of the foreground rule. */
private class RecordingSyncController : SyncController {
    val requested = mutableListOf<SyncTrigger>()

    override val status: StateFlow<com.ruizurraca.carapp.core.common.SyncStatus> =
        MutableStateFlow(com.ruizurraca.carapp.core.common.SyncStatus.Idle)

    override fun requestSync(reason: SyncTrigger) {
        requested += reason
    }

    override suspend fun sync(reason: SyncTrigger): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override suspend fun retryFailed(): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override fun shutdown() = Unit
}
