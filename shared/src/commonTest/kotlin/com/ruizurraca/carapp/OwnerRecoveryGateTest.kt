package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.sync.SyncController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `D-188`: the gate describes the interval during which the current owner's recovery is outstanding.
 *
 * `DefaultAppGraph.observeOwnerChanges` launches one `awaitRecovery` per non-sentinel owner
 * transition on `dispatchers.io`, so two recoveries can be in flight at once: a transition that
 * arrives while an earlier cycle is still running is exactly the case a permanent sign-in after an
 * anonymous session produces. The gate must stay raised until the last of them completes, because
 * the window it protects belongs to the newest owner, and `SPECIFICATION.md` F-1 answers a resolved
 * empty list with non-dismissible first-vehicle creation.
 */
class OwnerRecoveryGateTest {
    @Test
    fun theGateStaysRaisedWhileALaterRecoveryIsStillRunning() =
        runTest {
            val controller = SuspendingSyncController()
            val gate = OwnerRecoveryGate(controller)

            val firstRecovery = launch { gate.awaitRecovery() }
            runCurrent()
            val secondRecovery = launch { gate.awaitRecovery() }
            runCurrent()

            assertTrue(gate.pending.value, "two owner transitions leave two recoveries outstanding")

            controller.completeOldestCycle()
            runCurrent()

            assertTrue(
                gate.pending.value,
                "an earlier cycle completing must not resolve the list of the owner still recovering",
            )

            controller.completeOldestCycle()
            runCurrent()

            assertFalse(gate.pending.value, "the last recovery to complete lowers the gate")

            firstRecovery.join()
            secondRecovery.join()
        }

    @Test
    fun theGateLowersWhenItsOnlyRecoveryCompletes() =
        runTest {
            val controller = SuspendingSyncController()
            val gate = OwnerRecoveryGate(controller)

            val recovery = launch { gate.awaitRecovery() }
            runCurrent()

            assertTrue(gate.pending.value, "the transition raises the gate")

            controller.completeOldestCycle()
            recovery.join()

            assertFalse(gate.pending.value, "the completed cycle lowers it")
            assertEquals(
                listOf(SyncTrigger.OwnerChanged),
                controller.requestedReasons,
                "the gate awaits the §9.8 owner-change trigger and nothing else",
            )
        }
}

/** A controller whose cycle completes only when the test says so, one deferred per `sync` call. */
private class SuspendingSyncController : SyncController {
    private val cycles = ArrayDeque<CompletableDeferred<Unit>>()

    val requestedReasons = mutableListOf<SyncTrigger>()

    override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Idle)

    override fun requestSync(reason: SyncTrigger) = Unit

    override suspend fun sync(reason: SyncTrigger): Outcome<Unit, AppError> {
        requestedReasons += reason
        val cycle = CompletableDeferred<Unit>()
        cycles += cycle
        cycle.await()
        return Outcome.Ok(Unit)
    }

    override suspend fun retryFailed(): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override fun shutdown() = Unit

    fun completeOldestCycle() {
        cycles.removeFirst().complete(Unit)
    }
}
