package com.ruizurraca.carapp

import android.view.KeyEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.ruizurraca.carapp.core.common.AuthProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `R2`: the credential collision belongs to the authenticated root, not to the Settings destination.
 *
 * The collision arrives asynchronously, after the owner may have left Settings, and the root presenter is
 * the only surface that survives that navigation. This harness mounts exactly the two layers `VehicleApp`
 * mounts in its authenticated branch: the Settings child while it is visible, and the prompt presenter
 * beside it regardless.
 */
class AccountConversionRootPromptTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aCollisionDeliveredAfterLeavingSettingsIsConfirmedByTheRoot() {
        val calls = mutableListOf<String>()
        val harness = showHarness(calls)

        linkThenLeaveSettings(harness)
        publishCollision(harness)

        composeRule.onNodeWithTag(SettingsTestTags.CONVERSION_DIALOG).assertIsDisplayed()
        composeRule
            .onAllNodesWithText(conversionString(R.string.confirm_replace_existing_account))
            .assertCountEquals(1)
        composeRule.onNodeWithTag(SettingsTestTags.CONVERSION_CONFIRM).performClick()

        assertEquals(listOf("confirmAccountConversion"), calls)
    }

    @Test
    fun aCollisionDeliveredAfterLeavingSettingsIsCancelledByTheRoot() {
        val calls = mutableListOf<String>()
        val harness = showHarness(calls)

        linkThenLeaveSettings(harness)
        publishCollision(harness)

        composeRule.onNodeWithTag(SettingsTestTags.CONVERSION_CANCEL).assertIsDisplayed().performClick()

        assertEquals(listOf("cancelAccountConversion"), calls)
    }

    /**
     * A dismissed destructive confirmation is never consent: the platform Back action cancels it. The
     * harness publishes the session state itself, so nothing clears the message here and the alert stays
     * on screen; exactly one cancel callback is the evidence that Back dismissed it and that no
     * confirmation ran.
     */
    @Test
    fun dismissingTheRootCollisionCancelsIt() {
        val calls = mutableListOf<String>()
        val harness = showHarness(calls)

        linkThenLeaveSettings(harness)
        publishCollision(harness)

        composeRule.onNodeWithTag(SettingsTestTags.CONVERSION_DIALOG).assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()

        assertEquals(listOf("cancelAccountConversion"), calls)
    }

    private fun showHarness(calls: MutableList<String>): Harness {
        val harness =
            Harness(
                session = mutableStateOf(conversionSession(SessionPhase.ANONYMOUS)),
                settingsVisible = mutableStateOf(true),
            )
        composeRule.setContent {
            val session by harness.session
            val settingsVisible by harness.settingsVisible
            CarAppTestTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (settingsVisible) {
                        TestSettingsScreen(
                            session = session,
                            callbacks =
                                recordingSettingsCallbacks(mutableListOf()).copy(
                                    onBack = { harness.settingsVisible.value = false },
                                    onLinkAccount = { provider ->
                                        if (provider == AuthProvider.GOOGLE) {
                                            harness.session.value = session.copy(isBusy = true)
                                        }
                                    },
                                ),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.vehicle_list_title),
                            modifier = Modifier.testTag(ROOT_LIST_TAG),
                        )
                    }
                    // Mounted beside the child, exactly as the authenticated root mounts it.
                    SettingsSessionPrompts(
                        session = session,
                        callbacks = recordingSessionPromptCallbacks(calls),
                    )
                }
            }
        }
        return harness
    }

    private fun linkThenLeaveSettings(harness: Harness) {
        composeRule
            .onAllNodes(hasScrollAction())
            .onFirst()
            .performScrollToNode(hasTestTag(SettingsTestTags.LINK_ACCOUNT_GOOGLE))
        composeRule.onNodeWithTag(SettingsTestTags.LINK_ACCOUNT_GOOGLE).assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue("the link must publish a busy session", harness.session.value.isBusy)

        composeRule.onNodeWithTag(SettingsTestTags.BACK).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SettingsTestTags.SCREEN).assertDoesNotExist()
        composeRule.onNodeWithTag(ROOT_LIST_TAG).assertIsDisplayed()
    }

    /** The delayed credential result: the collision is published only after Settings is gone. */
    private fun publishCollision(harness: Harness) {
        composeRule.runOnIdle {
            harness.session.value = conversionSession(SessionPhase.ANONYMOUS, message = collisionConfirmation())
        }
    }

    private class Harness(
        val session: MutableState<SessionUiState>,
        val settingsVisible: MutableState<Boolean>,
    )

    private companion object {
        /** The list placeholder the harness renders once Settings is left. */
        const val ROOT_LIST_TAG = "conversion_root_list"
    }
}
