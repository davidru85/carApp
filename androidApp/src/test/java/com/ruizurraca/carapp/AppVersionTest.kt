package com.ruizurraca.carapp

import kotlin.test.Test
import kotlin.test.assertEquals

/** The Settings version label (D-212): the package `versionName` verbatim, and never an empty row. */
class AppVersionTest {
    @Test
    fun aDeclaredVersionNameIsShownVerbatim() {
        assertEquals("0.0.1", appVersionLabel(versionName = "0.0.1", versionCode = 1L))
    }

    @Test
    fun aMissingVersionNameFallsBackToTheVersionCode() {
        assertEquals("1", appVersionLabel(versionName = null, versionCode = 1L))
    }

    @Test
    fun aBlankVersionNameFallsBackToTheVersionCode() {
        assertEquals("7", appVersionLabel(versionName = " ", versionCode = 7L))
    }
}
