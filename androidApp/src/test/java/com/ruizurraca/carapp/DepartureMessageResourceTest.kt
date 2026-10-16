package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import kotlin.test.Test
import kotlin.test.assertEquals

class DepartureMessageResourceTest {
    /**
     * The one generic mapping cannot format `pendingSyncCount`, so it maps the warning to the
     * count-free title; the prompt itself formats the plurals resource with the typed count.
     */
    @Test
    fun thePendingSyncWarningMapsToItsCountFreeTitle() {
        val warning =
            UiMessage(
                id = 4L,
                kind = UiMessageKind.WARNING,
                code = "WARNING.PENDING_SYNC",
                confirmation = Confirmation.DiscardPendingChanges,
            )

        assertEquals(R.string.pending_sync_title, warning.stringResource())
    }
}
