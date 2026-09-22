package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.testing.RecordingSyncTriggerAdapter
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `docs/CONTRACTS.md §20.10`: `SyncTriggerAdapter` is the route by which platform-owned triggers reach
 * the single in-process `SyncController`. A graph that ignores the adapter it was given leaves
 * `Periodic` unreachable on both hosts, which is exactly the dead wiring this asserts against.
 */
class AppGraphTriggerAdapterTest {
    @Test
    fun theGraphAsksTheAdapterToArrangeThePeriodicTrigger() =
        runTest {
            val adapter = RecordingSyncTriggerAdapter()
            val dependencies = testAppGraphDependencies(syncTriggerAdapter = adapter)

            val graph = buildAppGraph(true, testAppProviders(dependencies))

            try {
                assertEquals(
                    listOf(SyncTrigger.Periodic),
                    adapter.scheduled,
                    "the graph MUST hand the periodic trigger to the platform scheduler, not ignore it",
                )
            } finally {
                graph.close()
            }
        }

    @Test
    fun theAdapterIsAskedOnlyOncePerGraph() =
        runTest {
            val adapter = RecordingSyncTriggerAdapter()
            val dependencies = testAppGraphDependencies(syncTriggerAdapter = adapter)

            val graph = buildAppGraph(true, testAppProviders(dependencies))

            try {
                // One request per process (§9.1): re-submitting the same periodic work on every
                // trigger would keep resetting the platform's schedule.
                assertTrue(adapter.scheduled.size <= 1, "the periodic arrangement MUST be requested once")
            } finally {
                graph.close()
            }
        }
}
