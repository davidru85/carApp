package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.testing.TestDispatcherProvider
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.assertEquals

/**
 * Shared fixture for graph-backed editor tests. All injected work uses the caller's scheduler;
 * customized database, owner and provider doubles are preserved. Default and customized calls
 * share the scheduling regressions in GraphTestDependenciesTest.
 */
internal fun TestScope.confinedGraphDependencies(
    dependencies: AppGraphDependencies = testAppGraphDependencies(),
): AppGraphDependencies = dependencies.copy(dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)))

/**
 * Asserts that every dispatcher in [dependencies] queues its work until the caller advances the
 * test scheduler, then runs all of it. Shared by the direct fixture guards and the fuel wrapper
 * regression so both exercise the same scheduling contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.assertQueuedGraphWork(dependencies: AppGraphDependencies) {
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
