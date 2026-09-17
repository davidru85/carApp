package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.testing.TestDispatcherProvider
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

/**
 * Advances virtual time by a bounded amount and runs the work that becomes due.
 *
 * Use this instead of `advanceUntilIdle()` in every test that mounts a real `AppGraph`. The two are
 * not equivalent for a graph. `advanceUntilIdle()` runs until no scheduled task remains, so a
 * coroutine that re-arms itself in virtual time makes it non-terminating: the test task is then
 * killed with no test result, and because `advanceUntilIdle()` never yields back to the scheduler,
 * neither `runTest`'s own timeout nor a virtual `withTimeoutOrNull` around it can rescue the run.
 * `DefaultSyncController.scheduleAdoptionRetry` is exactly that shape - it delays
 * `retryDelayMillis(...)` and then requests another cycle - so the hazard is one failing adoption
 * away from being live.
 *
 * [span] is finite and generous: it covers every backoff this suite schedules while staying small
 * enough that a mis-scheduled test fails fast.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.advanceGraphWork(span: Duration = GRAPH_WORK_ADVANCE_SPAN) {
    advanceTimeBy(span)
    runCurrent()
}

/**
 * Virtual time a graph test advances in one [advanceGraphWork] call. It exceeds the largest backoff
 * and retry schedule the shared suite exercises, so legitimate scheduling still completes.
 */
internal val GRAPH_WORK_ADVANCE_SPAN = 60.seconds
