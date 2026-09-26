package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The host-side classification of the aggregate `docs/CONTRACTS.md §9.9` publishes.
 *
 * The precedence and the connectivity rule belong to `:core:sync`, not to the host: the shared layer
 * publishes one resolved `SyncStatus` and `§14` forbids a second computation. What the host owns is
 * which of those four published values is drawn as an error and offers the manual retry, so these
 * tests pin that classification and nothing about how the aggregate was derived.
 */
class SyncStatusVisualTest {
    @Test
    fun everyPublishedStatusMapsToItsOwnVisual() {
        assertEquals(SyncStatusVisual.IDLE, syncStatusVisual(SyncStatus.Idle))
        assertEquals(SyncStatusVisual.SYNCING, syncStatusVisual(SyncStatus.Syncing))
        assertEquals(SyncStatusVisual.PENDING, syncStatusVisual(SyncStatus.Pending(count = 3)))
        assertEquals(
            SyncStatusVisual.FAILED,
            syncStatusVisual(SyncStatus.Failed(retryableCount = 1, poisonedCount = 2)),
        )
    }

    /**
     * The counts inside a status are detail, not classification. A `Failed` of retryable rows and one
     * of poisoned rows are the same visual, because `§9.9` already placed them in the same bucket and
     * the host is in no position to re-open that decision.
     */
    @Test
    fun theCountsInsideAStatusDoNotChangeItsVisual() {
        assertEquals(
            SyncStatusVisual.FAILED,
            syncStatusVisual(SyncStatus.Failed(retryableCount = 0, poisonedCount = 1)),
        )
        assertEquals(
            SyncStatusVisual.FAILED,
            syncStatusVisual(SyncStatus.Failed(retryableCount = 9, poisonedCount = 0)),
        )
        assertEquals(SyncStatusVisual.PENDING, syncStatusVisual(SyncStatus.Pending(count = 1)))
        assertEquals(SyncStatusVisual.PENDING, syncStatusVisual(SyncStatus.Pending(count = 250)))
    }

    /**
     * `§9.9` reserves error presentation for `Failed`. An offline owner with outstanding work reaches
     * the host as `Pending`, so classifying it as `Failed` would draw an error for a condition that is
     * not one and would contradict the shared value it came from.
     */
    @Test
    fun aPendingStatusIsNeverClassifiedAsFailed() {
        val visual = syncStatusVisual(SyncStatus.Pending(count = 12))

        assertEquals(SyncStatusVisual.PENDING, visual)
        assertNotEquals(SyncStatusVisual.FAILED, visual)
    }

    /** `Syncing` outranks `Pending` in `§9.9`; the host draws the two differently and must not merge them. */
    @Test
    fun theSyncingVisualIsDistinctFromPending() {
        assertNotEquals(syncStatusVisual(SyncStatus.Syncing), syncStatusVisual(SyncStatus.Pending(count = 1)))
    }

    @Test
    fun everyVisualHasItsOwnLabel() {
        val resources = SyncStatusVisual.entries.map(::syncStatusLabelResource)

        assertEquals(
            resources.size,
            resources.distinct().size,
            "Four states drawn with repeated labels cannot be told apart by the owner.",
        )
    }
}
