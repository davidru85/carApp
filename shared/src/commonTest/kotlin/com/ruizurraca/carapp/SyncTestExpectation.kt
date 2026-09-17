package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.SyncStatus
import kotlin.test.assertTrue

/**
 * Keeps graph teardown behind the complete sync boundary used by shared integration tests.
 * Persistence is checked after the terminal status because the controller publishes it only after
 * all cycle-owned database work finishes.
 */
internal suspend fun AppGraph.awaitSyncCycleSettled(
    expectation: String,
    expectedRemoteEffect: () -> Boolean,
    expectedPersistedState: suspend () -> Boolean = { true },
) {
    syncController().status.awaitState(expectation) { status ->
        expectedRemoteEffect() && status !is SyncStatus.Syncing
    }
    assertTrue(expectedPersistedState(), "Expected persisted state was not observed after $expectation")
}
