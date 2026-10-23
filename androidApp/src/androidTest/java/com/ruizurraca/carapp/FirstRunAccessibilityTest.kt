package com.ruizurraca.carapp

import android.os.Build
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** `E4-02` (`D-216`): the F-1 first-run screens of a fresh install pass ATF. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class FirstRunAccessibilityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun firstRunScreensPassAccessibilityChecks() {
        // Same reset as FirstVehicleOnboardingTest: close the process graph, then remove its file.
        runBlocking { AndroidAppGraph.resetForTests() }
        composeRule.enableAccessibilityChecks(carAppAccessibilityValidator())

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitUntil(timeoutMillis = 30_000) {
                composeRule.hasNodeWithTag(OnboardingTestTags.GUEST) || composeRule.hasNodeWithTag(VehicleTestTags.NAME)
            }
            composeRule.checkAccessibilityOfEveryWindow()
            if (composeRule.hasNodeWithTag(OnboardingTestTags.GUEST)) {
                composeRule.onNodeWithTag(OnboardingTestTags.GUEST).performClick()
            }
            composeRule.waitUntil(timeoutMillis = 30_000) { composeRule.hasNodeWithTag(VehicleTestTags.NAME) }
            composeRule.checkAccessibilityOfEveryWindow()
        }
    }
}
