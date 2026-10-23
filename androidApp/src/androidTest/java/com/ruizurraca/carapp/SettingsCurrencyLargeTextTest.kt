package com.ruizurraca.carapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `R4`: the Settings currency row must keep its label readable at 200% text in Spanish with a
 * three-letter currency name. The regression measures the label's own text layout rather than a
 * container tag: a row that exists but collapses its label into four lines is not usable.
 *
 * The 320 dp case stands in for a narrow device; the unconstrained case is the reference device.
 * Longer currency display names may wrap below the label, so only the short "Moneda" label is asserted
 * to stay on one line.
 */
class SettingsCurrencyLargeTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theSpanishUsdCurrencyLabelStaysOnOneLineOnTheReferenceDevice() {
        assertSpanishUsdLabel(constrainWidth = false)
    }

    @Test
    fun theSpanishUsdCurrencyLabelStaysOnOneLineAtNarrowWidth() {
        assertSpanishUsdLabel(constrainWidth = true)
    }

    private fun assertSpanishUsdLabel(constrainWidth: Boolean) {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(2f),
            ) {
                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.Locales(LocaleList("es-ES")),
                ) {
                    CarAppTestTheme {
                        // The currency picker opens a dialog, which brings its own window; the list is
                        // wrapped only when the test asks for the narrower reference width.
                        if (constrainWidth) {
                            Box(Modifier.width(320.dp)) { UsdSettingsScreen() }
                        } else {
                            UsdSettingsScreen()
                        }
                    }
                }
            }
        }

        scrollToCurrencyRow()
        composeRule.onNodeWithTag(SettingsTestTags.CURRENCY_ROW).assertIsDisplayed()

        val layouts = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText("Moneda", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
        // `R4`: the label must occupy one line. Against the reviewed implementation it occupied four,
        // because the unweighted currency value starved it. `hasVisualOverflow` is deliberately not
        // asserted: Compose reports it for an unclipped single line as well, so it cannot distinguish the
        // defect from a rounding artifact (measured in the review probe).
        assertEquals("layouts: ${layouts.size}", 1, layouts.size)
        val layout = layouts.single()
        assertEquals("lines=${layout.lineCount} size=${layout.size}", 1, layout.lineCount)
        assertTrue(
            "the label overflows its available width: ${layout.size.width} > " +
                "${layout.layoutInput.constraints.maxWidth}",
            layout.size.width <= layout.layoutInput.constraints.maxWidth,
        )

        // The complete currency name and its ISO code are retained: the label does not truncate them away.
        val value =
            localizedString(
                "es-ES",
                R.string.settings_currency_value,
                currencyDisplayName("USD", SPANISH),
                "USD",
            )
        composeRule.onNodeWithText(value, useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag(SettingsTestTags.CURRENCY_ROW).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(SettingsTestTags.CURRENCY_PICKER).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.CURRENCY_PICKER_CANCEL).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.CURRENCY_PICKER).assertDoesNotExist()
    }

    @Composable
    private fun UsdSettingsScreen() {
        TestSettingsScreen(settings = settingsState(currencyCode = "USD"))
    }

    /** The currency row sits at the top of the list, but a taller font can still push it down. */
    private fun scrollToCurrencyRow() {
        val scrollables = composeRule.onAllNodes(hasScrollAction())
        if (scrollables.fetchSemanticsNodes().isNotEmpty()) {
            scrollables.onFirst().performScrollToNode(hasTestTag(SettingsTestTags.CURRENCY_ROW))
        }
    }
}
