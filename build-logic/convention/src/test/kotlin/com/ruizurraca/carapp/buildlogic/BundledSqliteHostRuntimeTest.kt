package com.ruizurraca.carapp.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BundledSqliteHostRuntimeTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun everyKmpHostTestConsumerUsesTheJvmBundledSqliteArtifact() {
        val pluginDirectory =
            repositoryRoot.resolve(
                "build-logic/convention/src/main/kotlin/com/ruizurraca/carapp/buildlogic",
            )
        val kmpPlugin = pluginDirectory.resolve("KmpLibraryConventionPlugin.kt").readText()
        val databasePlugin = pluginDirectory.resolve("SqlDelightConventionPlugin.kt").readText()

        assertTrue(kmpPlugin.contains("androidHostTestRuntimeClasspath"))
        assertTrue(kmpPlugin.contains("androidx.sqlite:sqlite-bundled-jvm"))
        assertFalse(databasePlugin.contains("androidHostTestRuntimeClasspath"))
    }
}
