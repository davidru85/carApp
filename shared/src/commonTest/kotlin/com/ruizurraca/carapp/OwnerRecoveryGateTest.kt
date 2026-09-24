package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.SyncController
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `D-188`: the coordinator describes the interval during which the current owner's recovery is
 * outstanding, and it owns the owner value its subscribers observe.
 *
 * The ordering is the whole point. `DefaultAppGraph` runs every owner-scoped component off one
 * `OwnerContext`, so the owner must not become observable downstream until the recovery it causes is
 * already counted. Two independent collectors - one publishing the owner, a second raising the gate -
 * leave an interval in which the vehicle list reads a resolved empty list for the new owner while the
 * count still reads zero, and `SPECIFICATION.md` F-1 answers that state with non-dismissible
 * first-vehicle creation.
 *
 * Overlapping recoveries stay counted, because a transition that arrives while an earlier cycle is
 * still running is exactly what a permanent sign-in after an anonymous session produces. The count is
 * the single atomic value; a separate boolean could contradict it under concurrent completion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OwnerRecoveryGateTest {
    @Test
    fun theCountStaysAboveZeroWhileALaterRecoveryIsStillRunning() =
        runTest {
            val owners = FakeOwnerContext()
            val controller = SuspendingSyncController()
            val gate = OwnerRecoveryGate(owners)

            gate.launchIn(backgroundScope, controller)

            owners.set(OwnerId("owner-1"))
            runCurrent()
            owners.set(OwnerId("owner-2"))
            runCurrent()

            assertEquals(2, gate.outstanding.value, "two owner transitions leave two recoveries outstanding")

            controller.completeOldestCycle()
            runCurrent()

            assertEquals(
                1,
                gate.outstanding.value,
                "an earlier cycle completing must not resolve the list of the owner still recovering",
            )

            controller.completeOldestCycle()
            runCurrent()

            assertEquals(0, gate.outstanding.value, "the last recovery to complete lowers the count")
        }

    @Test
    fun theCountLowersWhenItsOnlyRecoveryCompletes() =
        runTest {
            val owners = FakeOwnerContext()
            val controller = SuspendingSyncController()
            val gate = OwnerRecoveryGate(owners)

            gate.launchIn(backgroundScope, controller)
            owners.set(OwnerId("owner-1"))
            runCurrent()

            assertEquals(1, gate.outstanding.value, "the transition raises the count")

            controller.completeOldestCycle()
            runCurrent()

            assertEquals(0, gate.outstanding.value, "the completed cycle lowers it")
            assertEquals(
                listOf(SyncTrigger.OwnerChanged),
                controller.requestedReasons,
                "the coordinator awaits the §9.8 owner-change trigger and nothing else",
            )
        }

    @Test
    fun theCountLowersWhenItsRecoveryCycleFails() =
        runTest {
            val owners = FakeOwnerContext()
            val controller = SuspendingSyncController()
            val gate = OwnerRecoveryGate(owners)

            gate.launchIn(backgroundScope, controller)
            owners.set(OwnerId("owner-1"))
            runCurrent()

            assertEquals(1, gate.outstanding.value, "the transition raises the count")

            controller.completeOldestCycle(Outcome.Err(RemoteError.Unavailable))
            runCurrent()

            assertEquals(
                0,
                gate.outstanding.value,
                "ADR-0189: a failed recovery lowers the count, so the owner is never stranded behind it",
            )
        }

    @Test
    fun theCountLowersWhenTheGraphIsClosedMidRecovery() =
        runTest {
            val owners = FakeOwnerContext()
            val controller = SuspendingSyncController()
            val gate = OwnerRecoveryGate(owners)
            val graphScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

            gate.launchIn(graphScope, controller)
            owners.set(OwnerId("owner-1"))
            runCurrent()

            assertEquals(1, gate.outstanding.value, "the transition raises the count")

            graphScope.cancel()
            runCurrent()

            assertEquals(
                0,
                gate.outstanding.value,
                "ADR-0189: a graph closed mid-recovery still lowers what it raised",
            )
        }

    @Test
    fun aNonSentinelTransitionCountsItsRecoveryBeforeAnythingCanObserveTheNewOwner() =
        runTest {
            val owners = FakeOwnerContext()
            val controller = SuspendingSyncController()
            val gate = OwnerRecoveryGate(owners)

            gate.launchIn(backgroundScope, controller)
            owners.set(OwnerId("owner-1"))
            runCurrent()

            // Non-vacuity boundary, stated rather than implied: `MutableStateFlow` conflates, and a
            // `StateFlow` observer is not resumed inline, so an increment placed on either side of the
            // owner assignment is *not* distinguishable from downstream. This assertion therefore
            // pins the observable consequence - a non-sentinel transition leaves a recovery counted
            // and awaiting the cycle - and not the instruction order. The order itself is guaranteed
            // by construction in `launchIn` and documented there; no test in this suite can falsify it.
            assertEquals(1, gate.outstanding.value, "a non-sentinel transition is counted immediately")
            assertEquals(
                OwnerId("owner-1"),
                gate.current,
                "the coordinated owner is the value downstream observes",
            )
            assertEquals(1, controller.requestedReasons.size, "and its cycle is already requested")
        }

    @Test
    fun anInitiallyPermanentOwnerIsABaselineAndRequestsNoCycle() =
        runTest {
            val owners = FakeOwnerContext(OwnerId("owner-1"))
            val controller = SuspendingSyncController()
            val gate = OwnerRecoveryGate(owners)

            gate.launchIn(backgroundScope, controller)
            runCurrent()

            assertEquals(
                OwnerId("owner-1"),
                gate.current,
                "an owner already resolved at construction is published as the baseline",
            )
            assertEquals(0, gate.outstanding.value, "a baseline raises no recovery")
            assertEquals(
                emptyList(),
                controller.requestedReasons,
                "an owner resolved at construction requests no OwnerChanged cycle",
            )
        }

    @Test
    fun aTransitionBetweenConstructionAndSubscriptionIsStillDetected() =
        runTest {
            val owners = FakeOwnerContext()
            val controller = SuspendingSyncController()
            val gate = OwnerRecoveryGate(owners)

            // The transition lands after construction and before the collector subscribes. The old
            // `drop(1)` shape discarded exactly this case, treating it as the construction baseline.
            owners.set(OwnerId("owner-1"))

            gate.launchIn(backgroundScope, controller)
            runCurrent()

            assertEquals(
                listOf(SyncTrigger.OwnerChanged),
                controller.requestedReasons,
                "a transition missed between construction and subscription MUST still recover",
            )
            assertEquals(1, gate.outstanding.value)
        }

    @Test
    fun theSentinelNeverRequestsARemoteCycle() =
        runTest {
            val owners = FakeOwnerContext(OwnerId("owner-1"))
            val controller = SuspendingSyncController()
            val gate = OwnerRecoveryGate(owners)

            gate.launchIn(backgroundScope, controller)
            runCurrent()
            owners.set(LOCAL_OWNER)
            runCurrent()

            assertEquals(
                emptyList(),
                controller.requestedReasons,
                "LOCAL_OWNER has nothing remote to fetch and must not raise a recovery",
            )
            assertEquals(0, gate.outstanding.value)
            assertEquals(LOCAL_OWNER, gate.current, "the sentinel is still published downstream")
        }
}

/** A controller whose cycle completes only when the test says so, one deferred per `sync` call. */
private class SuspendingSyncController : SyncController {
    private val cycles = ArrayDeque<CompletableDeferred<Outcome<Unit, AppError>>>()

    val requestedReasons = mutableListOf<SyncTrigger>()

    override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Idle)

    override fun requestSync(reason: SyncTrigger) = Unit

    override suspend fun sync(reason: SyncTrigger): Outcome<Unit, AppError> {
        requestedReasons += reason
        val cycle = CompletableDeferred<Outcome<Unit, AppError>>()
        cycles += cycle
        return cycle.await()
    }

    override suspend fun retryFailed(): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override fun shutdown() = Unit

    fun completeOldestCycle(outcome: Outcome<Unit, AppError> = Outcome.Ok(Unit)) {
        cycles.removeFirst().complete(outcome)
    }
}
