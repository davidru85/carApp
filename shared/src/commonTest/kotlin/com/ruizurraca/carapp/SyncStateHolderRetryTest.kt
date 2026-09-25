package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.sync.SyncController
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.core.testing.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The manual backup retry of `docs/SPECIFICATION.md §3.1` and the typed channel its failure uses.
 *
 * `docs/adr/0193` decides that a failed manual retry surfaces through the existing typed
 * `UiMessage`, so the host has one error path and no channel of its own. That only holds if the
 * holder publishes the failure, and if it clears the previous one before the next attempt: without
 * the clear, a stale error outlives the attempt that succeeded and stays on screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncStateHolderRetryTest {
    @Test
    fun retryFailurePublishesTypedMessage() =
        runTest {
            val controller =
                RetryResultSyncController(
                    retryResult = Outcome.Err(PersistenceError.TransactionFailed),
                )
            val holder = holder(controller)

            holder.retryFailed()
            advanceUntilIdle()

            assertEquals(1, controller.retryCalls)
            assertEquals(
                PersistenceError.TransactionFailed.code,
                holder.state.value.message?.code,
            )
            holder.close()
        }

    @Test
    fun successfulRetryClearsPreviousFailureMessage() =
        runTest {
            val controller =
                RetryResultSyncController(
                    retryResult = Outcome.Err(PersistenceError.TransactionFailed),
                )
            val holder = holder(controller)

            holder.retryFailed()
            advanceUntilIdle()
            controller.retryResult = Outcome.Ok(Unit)
            holder.retryFailed()
            advanceUntilIdle()

            assertEquals(2, controller.retryCalls)
            assertNull(holder.state.value.message)
            holder.close()
        }

    private fun TestScope.holder(controller: SyncController): SyncStateHolder =
        SyncStateHolder(
            scope = this,
            controller = controller,
            connectivity = FakeConnectivityObserver(),
            dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        )
}

private class RetryResultSyncController(
    var retryResult: Outcome<Unit, AppError>,
) : SyncController {
    var retryCalls = 0

    override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Idle)

    override fun requestSync(reason: SyncTrigger) = Unit

    override suspend fun sync(reason: SyncTrigger): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override suspend fun retryFailed(): Outcome<Unit, AppError> {
        retryCalls += 1
        return retryResult
    }

    override fun shutdown() = Unit
}
