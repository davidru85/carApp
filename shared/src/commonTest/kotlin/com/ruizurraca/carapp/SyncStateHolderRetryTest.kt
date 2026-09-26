package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.sync.SyncController
import com.ruizurraca.carapp.core.testing.FakeConnectivityObserver
import com.ruizurraca.carapp.core.testing.TestDispatcherProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
                    initialStatus = SyncStatus.Failed(retryableCount = 1, poisonedCount = 0),
                )
            val holder = holder(controller)

            holder.retryFailed()
            advanceUntilIdle()

            assertEquals(1, controller.retryCalls)
            assertEquals(
                PersistenceError.TransactionFailed.code,
                holder.state.value.message
                    ?.code,
            )
            holder.close()
        }

    @Test
    fun successfulRetryClearsPreviousFailureMessage() =
        runTest {
            val controller =
                RetryResultSyncController(
                    retryResult = Outcome.Err(PersistenceError.TransactionFailed),
                    initialStatus = SyncStatus.Failed(retryableCount = 1, poisonedCount = 0),
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

    /**
     * A retry failure reports failed rows that could not be reset. When the aggregate leaves `Failed`
     * those rows no longer exist, so the message MUST be withdrawn: a host that kept drawing it would
     * show an error beside `Pending`, which `§9.9` and the second `E3-05` criterion forbid.
     */
    @Test
    fun retryFailureIsWithdrawnWhenTheStatusLeavesFailed() =
        runTest {
            val controller =
                RetryResultSyncController(
                    retryResult = Outcome.Err(PersistenceError.TransactionFailed),
                    initialStatus = SyncStatus.Failed(retryableCount = 1, poisonedCount = 0),
                )
            val holder = holder(controller)
            advanceUntilIdle()

            holder.retryFailed()
            advanceUntilIdle()
            assertEquals(
                PersistenceError.TransactionFailed.code,
                holder.state.value.message
                    ?.code,
            )

            controller.mutableStatus.value = SyncStatus.Pending(count = 1)
            advanceUntilIdle()

            assertEquals(SyncStatus.Pending(count = 1), holder.state.value.status)
            assertNull(holder.state.value.message)
            holder.close()
        }

    /**
     * The withdrawal is tied to leaving `Failed`, not to any status change: a `Failed` whose counts
     * move is still the condition the message describes, so the message stays.
     */
    @Test
    fun retryFailureSurvivesAChangeBetweenFailedAggregates() =
        runTest {
            val controller =
                RetryResultSyncController(
                    retryResult = Outcome.Err(PersistenceError.TransactionFailed),
                    initialStatus = SyncStatus.Failed(retryableCount = 1, poisonedCount = 0),
                )
            val holder = holder(controller)
            advanceUntilIdle()

            holder.retryFailed()
            advanceUntilIdle()
            controller.mutableStatus.value = SyncStatus.Failed(retryableCount = 2, poisonedCount = 0)
            advanceUntilIdle()

            assertEquals(
                PersistenceError.TransactionFailed.code,
                holder.state.value.message
                    ?.code,
            )
            holder.close()
        }

    /**
     * A retry can be suspended on the controller for longer than the cycle that clears the failure.
     * Publishing the pre-suspension snapshot would resurrect the error beside a status that has
     * already moved on, which `docs/CONTRACTS.md §14` forbids.
     */
    @Test
    fun retryFailureThatCompletesAfterStatusLeavesFailedIsNotPublished() =
        runTest {
            val controller = SequencedRetrySyncController()
            val retry = controller.enqueueRetry()
            val holder = holder(controller)
            advanceUntilIdle()

            holder.retryFailed()
            runCurrent()
            controller.mutableStatus.value = SyncStatus.Pending(count = 1)
            runCurrent()
            retry.complete(Outcome.Err(PersistenceError.TransactionFailed))
            advanceUntilIdle()

            assertEquals(SyncStatus.Pending(count = 1), holder.state.value.status)
            assertNull(holder.state.value.message)
            holder.close()
        }

    /**
     * The field carries the latest manual-retry outcome, so an older attempt that finishes later
     * MUST NOT overwrite the outcome the newest attempt published.
     */
    @Test
    fun anOlderFailureCannotOverwriteANewerSuccessfulRetry() =
        runTest {
            val controller = SequencedRetrySyncController()
            val olderRetry = controller.enqueueRetry()
            val newerRetry = controller.enqueueRetry()
            val holder = holder(controller)
            advanceUntilIdle()

            holder.retryFailed()
            runCurrent()
            holder.retryFailed()
            runCurrent()
            newerRetry.complete(Outcome.Ok(Unit))
            runCurrent()
            olderRetry.complete(Outcome.Err(PersistenceError.TransactionFailed))
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
    initialStatus: SyncStatus = SyncStatus.Idle,
) : SyncController {
    var retryCalls = 0

    /** The published aggregate, writable so a test can move it the way a finished cycle would. */
    val mutableStatus = MutableStateFlow(initialStatus)

    override val status: StateFlow<SyncStatus> = mutableStatus

    override fun requestSync(reason: SyncTrigger) = Unit

    override suspend fun sync(reason: SyncTrigger): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override suspend fun retryFailed(): Outcome<Unit, AppError> {
        retryCalls += 1
        return retryResult
    }

    override fun shutdown() = Unit
}

/**
 * A controller whose retries resolve only when the test completes them, so a retry can be held
 * suspended across an aggregate change or across a newer attempt.
 */
private class SequencedRetrySyncController : SyncController {
    val mutableStatus =
        MutableStateFlow<SyncStatus>(
            SyncStatus.Failed(retryableCount = 1, poisonedCount = 0),
        )
    private val retries = mutableListOf<CompletableDeferred<Outcome<Unit, AppError>>>()
    var retryCalls = 0
        private set

    override val status: StateFlow<SyncStatus> = mutableStatus

    fun enqueueRetry(): CompletableDeferred<Outcome<Unit, AppError>> =
        CompletableDeferred<Outcome<Unit, AppError>>().also(retries::add)

    override fun requestSync(reason: SyncTrigger) = Unit

    override suspend fun sync(reason: SyncTrigger): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override suspend fun retryFailed(): Outcome<Unit, AppError> = retries[retryCalls++].await()

    override fun shutdown() = Unit
}
