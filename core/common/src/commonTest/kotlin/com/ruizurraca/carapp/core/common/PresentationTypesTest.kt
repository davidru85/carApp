package com.ruizurraca.carapp.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PresentationTypesTest {
    @Test
    fun syncStatusExposesEveryClosedState() {
        assertSame(SyncStatus.Idle, SyncStatus.Idle)
        assertSame(SyncStatus.Syncing, SyncStatus.Syncing)
        assertEquals(SyncStatus.Pending(count = 3), SyncStatus.Pending(count = 3))
        assertEquals(
            SyncStatus.Failed(retryableCount = 2, poisonedCount = 1),
            SyncStatus.Failed(retryableCount = 2, poisonedCount = 1),
        )
    }

    @Test
    fun uiMessagePreservesItsStablePayload() {
        val message =
            UiMessage(
                id = 7L,
                kind = UiMessageKind.WARNING,
                code = "WARNING.ODOMETER_INCONSISTENT",
                confirmation = Confirmation.OdometerInconsistent,
            )

        assertEquals(7L, message.id)
        assertEquals(UiMessageKind.WARNING, message.kind)
        assertEquals("WARNING.ODOMETER_INCONSISTENT", message.code)
        assertEquals(Confirmation.OdometerInconsistent, message.confirmation)
        assertEquals(
            listOf(UiMessageKind.INFO, UiMessageKind.WARNING, UiMessageKind.ERROR),
            UiMessageKind.entries,
        )
    }
}
