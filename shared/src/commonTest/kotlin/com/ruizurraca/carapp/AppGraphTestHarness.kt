package com.ruizurraca.carapp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class AppGraphTestHarness(
    val graph: AppGraph,
    parentScope: CoroutineScope,
) {
    private val scopeJob = SupervisorJob(parentScope.coroutineContext[Job])

    val scope = CoroutineScope(parentScope.coroutineContext + scopeJob)

    fun collect(flow: Flow<*>): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collect()
        }

    fun <T> collect(
        flow: Flow<T>,
        collector: suspend (T) -> Unit,
    ): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collect(collector)
        }

    suspend fun close() {
        graph.close()
    }
}
