package com.ruizurraca.carapp.core.analytics

import com.ruizurraca.carapp.core.common.CONNECTIVITY_ERROR_CODES
import com.ruizurraca.carapp.core.common.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The direct `SyncStatus -> SyncStatusCategory` mapping of `§20.9` (`D-202`).
 *
 * The engine alone classifies connectivity and cycle failures. Existing fixtures in
 * `DefaultSyncControllerThirteenthReviewTest` cover connectivity with an empty outbox (Idle),
 * connectivity with pending work (Pending), and non-connectivity pull failure with zero failed rows
 * (Failed with a representative retryable count of one). This consumer preserves the published
 * shape without reclassifying it from row errors or a second connectivity observation.
 */
class SyncStatusCategoryTest {
    @Test
    fun everySyncStatusShapeMapsToItsCategory() {
        // The exhaustive shape list of `§20.3`. A new `SyncStatus` shape stops this test compiling
        // rather than silently falling through a default.
        val shapes =
            listOf(
                SyncStatus.Idle,
                SyncStatus.Syncing,
                SyncStatus.Pending(count = 3),
                SyncStatus.Failed(retryableCount = 2, poisonedCount = 1),
            )
        val expected =
            listOf(
                SyncStatusCategory.IDLE,
                SyncStatusCategory.SYNCING,
                SyncStatusCategory.PENDING,
                SyncStatusCategory.FAILED,
            )

        assertEquals(expected, shapes.map { status -> status.toSyncStatusCategory() })
    }

    @Test
    fun theConnectivityCodesTheCollapseReliesOnArePinned() {
        // The collapse takes no connectivity parameter because `:core:sync` already keeps these codes
        // out of the `Failed` bucket (`§9.9`). Pinning the set here makes a change to it fail in the
        // module whose mapping depends on it, instead of silently changing what `FAILED` means.
        assertEquals(setOf("REMOTE.UNAVAILABLE", "REMOTE.DEADLINE_EXCEEDED"), CONNECTIVITY_ERROR_CODES)
    }
}
