package com.ruizurraca.carapp

import kotlin.test.assertTrue

internal suspend fun AppGraph.awaitSyncCycleSettled(
    expectation: String,
    expectedRemoteEffect: () -> Boolean,
    expectedPersistedState: suspend () -> Boolean = { true },
) {
    syncController().status.awaitState(expectation) {
        expectedRemoteEffect()
    }
    assertTrue(expectedPersistedState(), "Expected persisted state was not observed after $expectation")
}
