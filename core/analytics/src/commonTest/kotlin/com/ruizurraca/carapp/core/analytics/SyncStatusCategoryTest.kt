package com.ruizurraca.carapp.core.analytics

import com.ruizurraca.carapp.core.common.CONNECTIVITY_ERROR_CODES
import com.ruizurraca.carapp.core.common.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `SyncStatus -> SyncStatusCategory` collapse of `docs/CONTRACTS.md §20.9`.
 *
 * `§20.9` defines it as the one-to-one rename **qualified by the connectivity rule of `§9.9`**:
 * `Failed -> FAILED` only when at least one counted row has a `lastErrorCode` outside
 * `CONNECTIVITY_ERROR_CODES`; otherwise `Failed -> PENDING`.
 *
 * That qualification is applied where the aggregate is derived, once, by `:core:sync`
 * (`§9.9` "cycle-level failures" and `SyncEngine.refreshStatus`): the retryable and poisoned counts
 * the engine publishes already exclude connectivity-class rows, and a connectivity-only cycle
 * failure falls through to the row-derived buckets rather than publishing `Failed`. A published
 * `SyncStatus.Failed` therefore *entails* a non-connectivity condition, which is why the collapse
 * here is total over `SyncStatus` and has no connectivity parameter: re-deriving it from a second
 * observation would be the second implementation of the aggregate that `§14`, `D-192` and `D-193`
 * forbid.
 *
 * The entailment itself is pinned at the layer that produces it:
 * `DefaultSyncControllerTest.connectivityFailureKeepsRowStateAndAggregateInAgreement` asserts that an
 * offline failure publishes `Pending`, never `Failed`.
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
    fun aFailedAggregateIsNeverRenderedFromAConnectivityOnlyCondition() {
        // The `§9.9` rule the collapse depends on, stated executably: the categories that may be
        // produced from connectivity-only outstanding work are `PENDING` or `IDLE`, and the only
        // category that reports an error is reserved for a non-connectivity failure. This pins that
        // the category vocabulary itself cannot represent the forbidden rendering.
        val reportable =
            listOf(SyncStatus.Idle, SyncStatus.Pending(1), SyncStatus.Failed(1, 0), SyncStatus.Syncing)
                .map { status -> status.toSyncStatusCategory() }
                .toSet()

        assertEquals(
            setOf(
                SyncStatusCategory.IDLE,
                SyncStatusCategory.PENDING,
                SyncStatusCategory.FAILED,
                SyncStatusCategory.SYNCING,
            ),
            reportable,
        )
        // The rule is expressed on the codes the engines agree on, so the two cannot drift.
        assertEquals(setOf("REMOTE.UNAVAILABLE", "REMOTE.DEADLINE_EXCEEDED"), CONNECTIVITY_ERROR_CODES)
    }
}
