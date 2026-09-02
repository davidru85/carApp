package com.ruizurraca.carapp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
internal class AppGraphTestHarness(
    val graph: AppGraph,
    parentScope: CoroutineScope,
) {
    private val scopeJob = SupervisorJob(parentScope.coroutineContext[Job])

    private val scheduler =
        requireNotNull(parentScope.coroutineContext[TestCoroutineScheduler]) {
            "AppGraphTestHarness requires parentScope to provide a TestCoroutineScheduler"
        }

    private val collectorDispatcher = UnconfinedTestDispatcher(scheduler)

    val scope = CoroutineScope(parentScope.coroutineContext + scopeJob)

    fun <T> collect(
        flow: Flow<T>,
        collector: suspend (T) -> Unit = {},
    ): Job =
        scope.launch(
            context = collectorDispatcher,
            start = CoroutineStart.UNDISPATCHED,
        ) {
            flow.collect(collector)
        }

    suspend fun close() {
        try {
            scopeJob.cancelAndJoin()
        } finally {
            graph.close()
        }
    }
}
