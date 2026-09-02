package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppGraphTestHarnessTest {
    @Test
    fun closeCancelsCollectorsBeforeClosingTheGraph() =
        runTest {
            val events = mutableListOf<String>()
            val owningFactory = InMemoryDatabaseFactory()
            val dependencies =
                testAppGraphDependencies(
                    databaseFactory =
                        RecordingDatabaseFactory(owningFactory) {
                            events += "graph-closed"
                        },
                )
            val harness =
                AppGraphTestHarness(
                    graph =
                        buildAppGraph(
                            isDebugBuild = true,
                            providers = testAppProviders(dependencies),
                        ),
                    parentScope = backgroundScope,
                )

            try {
                try {
                    harness.collect(
                        flow<Unit> {
                            try {
                                awaitCancellation()
                            } finally {
                                events += "collectors-cancelled"
                            }
                        },
                    )
                } finally {
                    harness.close()
                }

                assertEquals(
                    listOf("collectors-cancelled", "graph-closed"),
                    events,
                )
            } finally {
                owningFactory.close()
            }
        }

    @Test
    fun constructorThrowsWhenParentScopeHasNoTestCoroutineScheduler() {
        val parentJob = Job()
        val nonTestScope = CoroutineScope(parentJob)
        val owningFactory = InMemoryDatabaseFactory()
        val dependencies = testAppGraphDependencies(databaseFactory = owningFactory)
        val graph =
            buildAppGraph(
                isDebugBuild = true,
                providers = testAppProviders(dependencies),
            )

        try {
            assertFailsWith<IllegalArgumentException> {
                AppGraphTestHarness(graph, nonTestScope)
            }
            assertEquals(
                emptyList(),
                parentJob.children.toList(),
                "a failed harness construction must not leave an orphaned child job on the parent",
            )
        } finally {
            parentJob.cancel()
            graph.close()
            owningFactory.close()
        }
    }

    @Test
    fun collectorsRunEagerlyOnUnconfinedTestDispatcher() =
        runTest {
            val owningFactory = InMemoryDatabaseFactory()
            val dependencies = testAppGraphDependencies(databaseFactory = owningFactory)
            val harness =
                AppGraphTestHarness(
                    graph =
                        buildAppGraph(
                            isDebugBuild = true,
                            providers = testAppProviders(dependencies),
                        ),
                    parentScope = backgroundScope,
                )

            try {
                val flow = MutableSharedFlow<Int>(extraBufferCapacity = 1)
                var received = 0
                harness.collect(flow) { received = it }

                flow.tryEmit(42)
                assertEquals(42, received)
            } finally {
                harness.close()
                owningFactory.close()
            }
        }
}
