package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import com.ruizurraca.carapp.shared.testing.testAppProviders
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppGraphTestHarnessTest {
    @Test
    fun closeCancelsCollectorsBeforeClosingTheGraph() =
        runTest {
            val events = mutableListOf<String>()
            val owningFactory = InMemoryDatabaseFactory()
            val dependencies =
                testAppGraphDependencies(
                    databaseFactory =
                        CloseRecordingDatabaseFactory(owningFactory) {
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
                harness.collect(
                    flow<Unit> {
                        try {
                            awaitCancellation()
                        } finally {
                            events += "collectors-cancelled"
                        }
                    },
                )

                harness.close()

                assertEquals(
                    listOf("collectors-cancelled", "graph-closed"),
                    events,
                )
            } finally {
                owningFactory.close()
            }
        }
}

private class CloseRecordingDatabaseFactory(
    private val delegate: DatabaseFactory,
    private val onClose: () -> Unit,
) : DatabaseFactory {
    override fun create(): DatabaseHandle {
        val handle = delegate.create()
        return object : DatabaseHandle {
            override val database = handle.database

            override fun close() {
                onClose()
                handle.close()
            }
        }
    }
}
