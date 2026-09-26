package com.ruizurraca.carapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The rendered indicator: which of the four published statuses shows an error, what it offers, and
 * what a typed retry failure looks like once the holder publishes one.
 *
 * Each test composes once and varies the published status through a mutable state, because a Compose
 * test rule accepts a single `setContent` per test.
 */
class SyncStatusIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aFailedStatusOffersTheManualRetry() {
        var retries = 0
        composeRule.setContent {
            SyncStatusIndicator(
                status = SyncStatus.Failed(retryableCount = 1, poisonedCount = 0),
                message = null,
                onRetry = { retries += 1 },
            )
        }

        composeRule.onNodeWithTag(SyncStatusTestTags.INDICATOR).assertIsDisplayed()
        composeRule.onNodeWithTag(SyncStatusTestTags.RETRY).assertIsDisplayed().performClick()

        assertEquals(1, retries)
    }

    /**
     * `§9.9` reserves the error presentation for `Failed`. A `Pending` status reaches the host when the
     * owner is offline with outstanding work, so offering a retry there would report a condition the
     * aggregate says is not an error.
     */
    @Test
    fun everyNonFailedStatusRendersWithoutARetry() {
        var status: SyncStatus by mutableStateOf(SyncStatus.Idle)
        composeRule.setContent { SyncStatusIndicator(status = status, message = null, onRetry = {}) }

        listOf(
            SyncStatus.Idle,
            SyncStatus.Syncing,
            SyncStatus.Pending(count = 4),
        ).forEach { published ->
            status = published
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(SyncStatusTestTags.INDICATOR).assertIsDisplayed()
            composeRule.onNodeWithTag(SyncStatusTestTags.RETRY).assertDoesNotExist()
        }
    }

    @Test
    fun eachStatusUsesItsOwnLabel() {
        var status: SyncStatus by mutableStateOf(SyncStatus.Idle)
        composeRule.setContent { SyncStatusIndicator(status = status, message = null, onRetry = {}) }

        listOf(
            SyncStatus.Idle to SyncStatusVisual.IDLE,
            SyncStatus.Syncing to SyncStatusVisual.SYNCING,
            SyncStatus.Pending(count = 4) to SyncStatusVisual.PENDING,
            SyncStatus.Failed(retryableCount = 1, poisonedCount = 0) to SyncStatusVisual.FAILED,
        ).forEach { (published, visual) ->
            status = published
            composeRule.waitForIdle()

            composeRule.onNodeWithText(label(visual)).assertIsDisplayed()
        }
    }

    /**
     * The `Idle` wording is the design's own and states that nothing is outstanding rather than that a
     * remote copy exists: under `LOCAL_OWNER` nothing is ever enqueued (`§9.1`, `D-193`).
     */
    @Test
    fun theIdleLabelDoesNotClaimABackedUpCopy() {
        val idle = label(SyncStatusVisual.IDLE)

        assertTrue("Idle hides its local scope: $idle", idle.contains("local", ignoreCase = true))
        composeRule.setContent { SyncStatusIndicator(status = SyncStatus.Idle, message = null, onRetry = {}) }
        composeRule.onNodeWithText(idle).assertIsDisplayed()
    }

    /**
     * A failed manual retry reaches the host as a typed `UiMessage` whose `code` is the error code
     * (`docs/adr/0193`). The host MUST render it through the one existing localized mapping rather
     * than through a second table of its own, and the Retry action MUST stay separately actionable
     * beside it rather than being merged into the status text.
     */
    @Test
    fun retryFailureRendersMappedPersistenceMessage() {
        val message =
            UiMessage(
                id = 7L,
                kind = UiMessageKind.ERROR,
                code = "PERSISTENCE.TRANSACTION_FAILED",
                confirmation = null,
            )
        composeRule.setContent {
            SyncStatusIndicator(
                status = SyncStatus.Failed(retryableCount = 1, poisonedCount = 0),
                message = message,
                onRetry = {},
            )
        }

        val expected =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getString(R.string.error_persistence)
        composeRule.onNodeWithText(expected).assertIsDisplayed()
        composeRule.onNodeWithTag(SyncStatusTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(VehicleTestTags.ERROR).assertDoesNotExist()
        composeRule
            .onNodeWithTag(SyncStatusTestTags.RETRY)
            .assertIsEnabled()
            .assertHasClickAction()
    }

    /**
     * `§9.9` reserves the error presentation for `Failed`. A retry failure still published while the
     * status is already `Idle`, `Syncing` or `Pending` MUST NOT be drawn beside it.
     */
    @Test
    fun aRetryFailureIsNotRenderedBesideANonFailedStatus() {
        val message =
            UiMessage(
                id = 7L,
                kind = UiMessageKind.ERROR,
                code = "PERSISTENCE.TRANSACTION_FAILED",
                confirmation = null,
            )
        var status: SyncStatus by mutableStateOf(SyncStatus.Idle)
        composeRule.setContent { SyncStatusIndicator(status = status, message = message, onRetry = {}) }
        val error =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getString(R.string.error_persistence)

        listOf(
            SyncStatus.Idle,
            SyncStatus.Syncing,
            SyncStatus.Pending(count = 4),
        ).forEach { published ->
            status = published
            composeRule.waitForIdle()

            composeRule.onNodeWithText(error).assertDoesNotExist()
            composeRule.onNodeWithTag(SyncStatusTestTags.ERROR).assertDoesNotExist()
        }
    }

    private fun label(visual: SyncStatusVisual): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(syncStatusLabelResource(visual))
}
