package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
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

    private fun TestScope.assertQueuedGraphWork(dependencies: AppGraphDependencies) {
        val completed = mutableListOf<String>()
        val dispatchers = dependencies.dispatchers
        listOf("main" to dispatchers.main, "io" to dispatchers.io, "default" to dispatchers.default)
            .forEach { (name, dispatcher) ->
                backgroundScope.launch(dispatcher) { completed += name }
            }

        assertEquals(emptyList(), completed, "graph work must wait for the caller scheduler")
        runCurrent()
        assertEquals(setOf("main", "io", "default"), completed.toSet())
    }
}
