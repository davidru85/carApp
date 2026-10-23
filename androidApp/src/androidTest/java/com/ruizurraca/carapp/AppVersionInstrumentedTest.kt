package com.ruizurraca.carapp

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** D-212: the Android host reads its version natively from the installed package. */
class AppVersionInstrumentedTest {
    @Test
    fun theContextVersionIsThePackageVersionName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        @Suppress("DEPRECATION")
        val declared = context.packageManager.getPackageInfo(context.packageName, 0).versionName

        assertEquals(declared, context.appVersionName())
    }
}
