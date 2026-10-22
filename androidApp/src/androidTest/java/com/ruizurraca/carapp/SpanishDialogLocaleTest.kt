package com.ruizurraca.carapp

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * `E4-02`: the Spanish copy of the destructive dialogs. A Material3 dialog opens its own window and
 * takes no locale from `DeviceConfigurationOverride.Locales`, so this class sets the app's own locale
 * through the shell before the compose rule launches its activity. Setting it after the launch would
 * recreate the running activity and discard the composition under test.
 */
@RunWith(AndroidJUnit4::class)
class SpanishDialogLocaleTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SpanishAppLocaleRule()).around(composeRule)

    @Test
    fun conversionConfirmationRendersSpanishCopy() {
        val session = conversionCollisionSession()
        composeRule.setContent {
            CarAppTestTheme {
                // The collision is presented by the authenticated root, beside Settings (`R2`).
                SettingsSessionPrompts(session = session, callbacks = a11yPromptCallbacks())
            }
        }
        val copy = localizedString(SPANISH_TAG, R.string.confirm_replace_existing_account)
        assertTrue(copy, copy.contains("sustituye permanentemente"))
        composeRule.scrollToAndAssertDisplayed(hasTextContaining(copy))
        listOf(SettingsTestTags.CONVERSION_CONFIRM, SettingsTestTags.CONVERSION_CANCEL)
            .forEach(composeRule::scrollToAndAssertDisplayed)
    }

    @Test
    fun deleteAccountConfirmationRendersSpanishCopy() {
        val session = deleteAccountSession()
        composeRule.setContent {
            CarAppTestTheme {
                SettingsSessionPrompts(session = session, callbacks = a11yPromptCallbacks())
            }
        }
        val body = localizedString(SPANISH_TAG, R.string.confirm_delete_account)
        assertTrue(body, body.contains("Esto elimina permanentemente tu cuenta"))
        composeRule.scrollToAndAssertDisplayed(hasTextContaining(body))
        composeRule.onNodeWithText(localizedString(SPANISH_TAG, R.string.settings_delete_account)).assertExists()
        composeRule.onNodeWithText(localizedString(SPANISH_TAG, R.string.cancel)).assertExists()
    }
}

/** Sets the app's own locale to Spanish before the activity launches and clears it afterwards. */
private class SpanishAppLocaleRule : ExternalResource() {
    override fun before() = setAppLocales(SPANISH_TAG)

    override fun after() = setAppLocales("\"\"")

    private fun setAppLocales(locales: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val command = "cmd locale set-app-locales ${instrumentation.targetContext.packageName} --locales $locales"
        ParcelFileDescriptor
            .AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { stream -> stream.readBytes() }
    }
}
