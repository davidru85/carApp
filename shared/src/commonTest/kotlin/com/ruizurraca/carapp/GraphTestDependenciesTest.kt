package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
}
