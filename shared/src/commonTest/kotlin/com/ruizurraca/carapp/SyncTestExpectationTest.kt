package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.sync.SyncController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SyncTestExpectationTest {
    @Test
    fun aConflatedCycleStillWaitsForTheRemoteEffectAndTerminalStatus() =
        runTest {
            val controller = ExpectationSyncController()
            val graph = SyncOnlyGraph(controller)
            var remoteEffect = false
            var observations = 0
            var persistenceChecked = false
            val settled =
                async(start = CoroutineStart.UNDISPATCHED) {
                    graph.awaitSyncCycleSettled(
                        expectation = "a conflated sync cycle",
                        expectedRemoteEffect = {
                            observations += 1
                            remoteEffect
                        },
                        expectedPersistedState = {
                            persistenceChecked = true
                            true
                        },
                    )
                }
            try {
                assertEquals(1, observations, "the waiter observes the initial Idle before the effect")
                assertFalse(settled.isCompleted)
                controller.status.value = SyncStatus.Syncing
                remoteEffect = true
                controller.status.value = SyncStatus.Idle
                // Syncing -> Idle is conflated before the collector can run. The final Idle equals
                // its first observed value, so a status-only collector receives no new emission.
                runCurrent()

                // Completion may suspend on a real scheduling pulse, but requires no extra status.
                withContext(Dispatchers.Default) { withTimeout(1.seconds) { settled.await() } }
                assertTrue(persistenceChecked)
            } finally {
                settled.cancelAndJoin()
            }
        }

    @Test
    fun aRemoteEffectAloneDoesNotPermitTeardownWhileSyncing() =
        runTest {
            val controller = ExpectationSyncController()
            controller.status.value = SyncStatus.Syncing
            val graph = SyncOnlyGraph(controller)
            val settled =
                async(start = CoroutineStart.UNDISPATCHED) {
                    graph.awaitSyncCycleSettled("a still-running cycle", expectedRemoteEffect = { true })
                }
            try {
                assertFalse(settled.isCompleted)
                controller.status.value = SyncStatus.Idle
                settled.await()
            } finally {
                settled.cancelAndJoin()
            }
        }

    @Test
    fun aMissingPersistedEffectIsStillRejected() =
        runTest {
            val graph = SyncOnlyGraph(ExpectationSyncController())
            assertFailsWith<AssertionError> {
                graph.awaitSyncCycleSettled(
                    expectation = "a missing persisted effect",
                    expectedRemoteEffect = { true },
                    expectedPersistedState = { false },
                )
            }
        }
}

private class ExpectationSyncController : SyncController {
    override val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)

    override fun requestSync(reason: SyncTrigger): Unit = error("No sync execution in the expectation fixture")

    override suspend fun sync(reason: SyncTrigger): Nothing = error("No sync execution in the expectation fixture")

    override suspend fun retryFailed(): Nothing = error("No sync execution in the expectation fixture")

    override fun shutdown(): Unit = error("No sync execution in the expectation fixture")
}

private class SyncOnlyGraph(
    private val controller: SyncController,
) : AppGraph {
    override fun syncController(): SyncController = controller

    override fun vehicleListStateHolder(scope: CoroutineScope): Nothing = error("No holders in the fixture")

    override fun vehicleFormStateHolder(
        scope: CoroutineScope,
        vehicleId: String?,
    ): Nothing = error("No holders in the fixture")

    override fun fuelEntryListStateHolder(
        scope: CoroutineScope,
        vehicleId: String,
    ): Nothing = error("No holders in the fixture")

    override fun fuelEntryFormStateHolder(
        scope: CoroutineScope,
        vehicleId: String,
        entryId: String?,
    ): Nothing = error("No holders in the fixture")

    override fun sessionStateHolder(scope: CoroutineScope): Nothing = error("No holders in the fixture")

    override fun settingsStateHolder(scope: CoroutineScope): Nothing = error("No holders in the fixture")

    override fun syncStateHolder(scope: CoroutineScope): Nothing = error("No holders in the fixture")

    override fun close(): Unit = error("No database in the expectation fixture")

    override suspend fun awaitClosed(): Unit = error("No database in the expectation fixture")
}
