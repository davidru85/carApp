package com.ruizurraca.carapp.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            listOf(repositoryRoot.resolve("androidApp/build.gradle.kts").canonicalFile),
            buildScripts
                .filter { it.readText().contains("com.google.gms.google-services") }
                .map(File::getCanonicalFile)
                .sortedBy(File::getPath),
        )
    }

    @Test
    fun firebaseAppleSdkPinIsConsumedByTheDirectIosIntegration() {
        val catalog = repositoryRoot.resolve("gradle/libs.versions.toml").readText()
        val projectSpec = repositoryRoot.resolve("iosApp/project.yml").readText()
        val projectGenerator = repositoryRoot.resolve("iosApp/generate-project.sh").readText()
        val xcodeProject = repositoryRoot.resolve("iosApp/carApp.xcodeproj/project.pbxproj").readText()
        val resolvedPackages =
            repositoryRoot
                .resolve("iosApp/carApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved")
                .readText()

        assertTrue(catalog.contains("firebaseApple = \"11.8.0\""))
        assertTrue(projectSpec.contains("exactVersion: ${'$'}{FIREBASE_APPLE_VERSION}"))
        assertFalse(projectSpec.contains("11.8.0"))
        assertTrue(projectGenerator.contains("^firebaseApple = "))
        assertTrue(projectGenerator.contains("FIREBASE_APPLE_VERSION"))
        assertTrue(projectGenerator.contains("xcodegen generate"))
        assertTrue(xcodeProject.contains("firebase-ios-sdk"))
        assertTrue(xcodeProject.contains("version = 11.8.0"))
        assertTrue(xcodeProject.contains("FirebaseAuth"))
        assertTrue(xcodeProject.contains("FirebaseCore"))
        assertTrue(xcodeProject.contains("FirebaseFirestore"))
        assertTrue(resolvedPackages.contains("\"identity\" : \"firebase-ios-sdk\""))
        assertTrue(resolvedPackages.contains("\"version\" : \"11.8.0\""))
    }
}
