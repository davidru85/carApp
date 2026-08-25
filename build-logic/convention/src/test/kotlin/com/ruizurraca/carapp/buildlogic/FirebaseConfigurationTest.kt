package com.ruizurraca.carapp.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirebaseConfigurationTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun googleServicesPluginIsPinnedAndAppliedOnlyToAndroidApp() {
        val catalog = repositoryRoot.resolve("gradle/libs.versions.toml").readText()
        val rootBuild = repositoryRoot.resolve("build.gradle.kts").readText()
        val androidBuild = repositoryRoot.resolve("androidApp/build.gradle.kts").readText()
        val buildScripts =
            repositoryRoot
                .walkTopDown()
                .filter { file ->
                    file.isFile &&
                        file.name == "build.gradle.kts" &&
                        "${File.separator}build${File.separator}" !in file.path
                }.toList()

        assertTrue(catalog.contains("googleServices = \"4.5.0\""))
        assertTrue(
            catalog.contains(
                "google-services = { id = \"com.google.gms.google-services\", " +
                    "version.ref = \"googleServices\" }",
            ),
        )
        assertTrue(rootBuild.contains("alias(libs.plugins.google.services) apply false"))
        assertTrue(androidBuild.contains("id(\"com.google.gms.google-services\")"))
        assertEquals(
            listOf(
                repositoryRoot.resolve("androidApp/build.gradle.kts").canonicalFile,
                repositoryRoot.resolve("build.gradle.kts").canonicalFile,
            ),
            buildScripts
                .filter { it.readText().contains("com.google.gms.google-services") }
                .map(File::getCanonicalFile)
                .sortedBy(File::getPath),
        )
    }
}
