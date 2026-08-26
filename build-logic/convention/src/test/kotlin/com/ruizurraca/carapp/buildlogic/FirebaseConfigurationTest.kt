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

    @Test
    fun appCheckProvidersAreRestrictedToTheirIntendedNativeBuilds() {
        val catalog = repositoryRoot.resolve("gradle/libs.versions.toml").readText()
        val androidBuild = repositoryRoot.resolve("androidApp/build.gradle.kts").readText()
        val androidManifest = repositoryRoot.resolve("androidApp/src/main/AndroidManifest.xml").readText()
        val androidApplication =
            repositoryRoot
                .resolve("androidApp/src/main/java/com/ruizurraca/carapp/CarAppApplication.kt")
                .readText()
        val androidDebugProvider =
            repositoryRoot
                .resolve("androidApp/src/debug/java/com/ruizurraca/carapp/AppCheckProvider.kt")
                .readText()
        val androidReleaseProvider =
            repositoryRoot
                .resolve("androidApp/src/release/java/com/ruizurraca/carapp/AppCheckProvider.kt")
                .readText()
        val iosProject = repositoryRoot.resolve("iosApp/project.yml").readText()
        val iosApplication = repositoryRoot.resolve("iosApp/carAppApp.swift").readText()
        val iosProvider = repositoryRoot.resolve("iosApp/AppCheckProviderFactory.swift").readText()
        val iosEntitlements = repositoryRoot.resolve("iosApp/carApp.entitlements").readText()

        assertTrue(catalog.contains("firebase-appcheck-playintegrity"))
        assertTrue(catalog.contains("firebase-appcheck-debug"))
        assertTrue(androidBuild.contains("implementation(platform(libs.firebase.bom))"))
        assertTrue(androidBuild.contains("implementation(libs.firebase.appcheck.playintegrity)"))
        assertTrue(androidBuild.contains("debugImplementation(libs.firebase.appcheck.debug)"))
        assertFalse(androidBuild.contains("implementation(libs.firebase.appcheck.debug)"))
        assertTrue(androidManifest.contains("android:name=\".CarAppApplication\""))
        assertTrue(androidApplication.contains("FirebaseApp.initializeApp(this)"))
        assertTrue(androidApplication.contains("installAppCheckProviderFactory(appCheckProviderFactory())"))
        assertFalse(androidApplication.contains("DebugAppCheckProviderFactory"))
        assertTrue(androidDebugProvider.contains("DebugAppCheckProviderFactory.getInstance()"))
        assertTrue(androidDebugProvider.contains("PlayIntegrityAppCheckProviderFactory.getInstance()"))
        assertTrue(androidDebugProvider.contains("isProbablyEmulator()"))
        assertFalse(androidReleaseProvider.contains("DebugAppCheckProviderFactory"))
        assertTrue(androidReleaseProvider.contains("PlayIntegrityAppCheckProviderFactory.getInstance()"))

        assertTrue(iosProject.contains("product: FirebaseAppCheck"))
        assertTrue(iosProject.contains("CODE_SIGN_ENTITLEMENTS: carApp.entitlements"))
        assertTrue(iosApplication.contains("import FirebaseAppCheck"))
        assertTrue(iosApplication.indexOf("configureAppCheck()") < iosApplication.indexOf("FirebaseApp.configure()"))
        assertTrue(iosProvider.contains("#if DEBUG && targetEnvironment(simulator)"))
        assertTrue(iosProvider.contains("AppCheckDebugProviderFactory"))
        assertTrue(iosProvider.contains("AppAttestProvider"))
        assertTrue(iosEntitlements.contains("com.apple.developer.devicecheck.appattest-environment"))
        assertTrue(iosEntitlements.contains("<string>production</string>"))
    }
}
