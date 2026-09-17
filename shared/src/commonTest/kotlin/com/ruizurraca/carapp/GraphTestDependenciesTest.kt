package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class GraphTestDependenciesTest {
    @Test
    fun defaultGraphWorkWaitsForTheCallerScheduler() =
        runTest {
            assertQueuedGraphWork(confinedGraphDependencies())
        }

    @Test
    fun customizedGraphWorkWaitsForTheCallerSchedulerWithoutReplacingItsDoubles() =
        runTest {
            val clock = FakeAppClock()
            val dependencies = confinedGraphDependencies(testAppGraphDependencies(clock = clock))

            assertQueuedGraphWork(dependencies)
            assertSame(clock, dependencies.clock)
        }

    /**
     * The reason [advanceGraphWork] exists. `advanceUntilIdle()` on work that re-arms itself in
     * virtual time never terminates, and because it never yields the thread back to the scheduler,
     * neither `runTest`'s own timeout nor a virtual `withTimeoutOrNull` around it can end the run -
     * the test task is killed with no result. This is the shape of
     * `DefaultSyncController.scheduleAdoptionRetry`, so the helper is not a style preference.
     */
    @Test
    fun boundedAdvanceReturnsWhileWorkKeepsRearmingItselfInVirtualTime() =
        runTest {
            backgroundScope.launch { while (true) delay(1_000) }

            advanceGraphWork()

            assertEquals(
                GRAPH_WORK_ADVANCE_SPAN.inWholeMilliseconds,
                testScheduler.currentTime,
                "the bounded advance stops at its span instead of chasing the re-arming work",
            )
        }
}
