package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.SyncStatus
import kotlin.test.assertTrue

/**
 * Keeps graph teardown behind the complete sync boundary used by shared integration tests.
 * Remote completion and terminal status are reevaluated independently: StateFlow can conflate
 * Idle -> Syncing -> Idle into no new emission (`D-204`). The real scheduling pulse of
 * [awaitGraphEffect] suspends outside the virtual scheduler, so runCurrent remains finite while a
 * fixture intentionally blocks a cycle. Persistence is checked after the terminal status because the
 * controller publishes it only after all cycle-owned database work finishes.
 */
internal suspend fun AppGraph.awaitSyncCycleSettled(
    expectation: String,
    expectedRemoteEffect: () -> Boolean,
    expectedPersistedState: suspend () -> Boolean = { true },
) {
    awaitGraphEffect(expectation) {
        expectedRemoteEffect() && syncController().status.value !is SyncStatus.Syncing
    }
    assertTrue(expectedPersistedState(), "Expected persisted state was not observed after $expectation")
}
