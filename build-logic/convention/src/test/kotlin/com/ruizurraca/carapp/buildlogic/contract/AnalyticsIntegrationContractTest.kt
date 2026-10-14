package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fixtures for the four `E3-09` assertions, each mutated so the check must fire.
 *
 * A rule with no failing fixture is a rule that passes everything, so the mutations here are the
 * proof that `AnalyticsIntegrationContract` is not vacuous.
 */
class AnalyticsIntegrationContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val realSources = readSources()

    @Test
    fun theRealRepositorySatisfiesEveryAssertion() {
        val results = AnalyticsIntegrationContract(repositoryRoot).validate()

        assertEquals(4, results.size)
        results.forEach { result -> assertEquals(AssertionResult.Status.PASS, result.status, result.detail) }
    }

    @Test
    fun aSecondConstructionSiteIsRejected() {
        val leakPath = "shared/src/commonMain/kotlin/com/ruizurraca/carapp/Leak.kt"
        val mutated = realSources + (leakPath to "val leaked = FirebaseAnalyticsTracker()")

        val result = AnalyticsIntegrationContract(realInputs(mutated)).validate().first { it.id == 37 }

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains("Leak.kt"), result.detail)
    }

    @Test
    fun aMissingConstructionInWiringIsRejected() {
        val mutated =
            realSources.map { (path, text) ->
                if (path.endsWith("FirebaseAppProviders.kt")) {
                    path to text.replace("FirebaseAnalyticsTracker()", "stagedTracker()")
                } else {
                    path to text
                }
            }

        val result = AnalyticsIntegrationContract(realInputs(mutated)).validate().first { it.id == 37 }

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains("does not construct"), result.detail)
    }

    @Test
    fun anEnabledByDefaultTrackerIsRejected() {
        val real = AnalyticsIntegrationContract.Inputs.from(repositoryRoot)
        val mutated =
            real.copy(
                trackerSource = real.trackerSource.replace("enabled: Boolean = false", "enabled: Boolean = true"),
            )

        val result = AnalyticsIntegrationContract(mutated).validate().first { it.id == 37 }

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains("disabled"), result.detail)
    }

    @Test
    fun aPublicConstructorThatEnablesCollectionIsRejected() {
        val real = AnalyticsIntegrationContract.Inputs.from(repositoryRoot)
        val mutated = real.copy(trackerSource = real.trackerSource.replace("enabled = false,", "enabled = true,"))

        val result = AnalyticsIntegrationContract(mutated).validate().first { it.id == 37 }

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains("public constructor"), result.detail)
    }

    @Test
    fun aMissingIosProductIsRejected() {
        val real = AnalyticsIntegrationContract.Inputs.from(repositoryRoot)
        val mutated =
            real.copy(
                projectSpec = real.projectSpec.replace("product: FirebaseAnalytics", "product: FirebaseCrashlytics"),
                xcodeProject = real.xcodeProject.replace("FirebaseAnalytics", "FirebaseCrashlytics"),
            )

        val result = AnalyticsIntegrationContract(mutated).validate().first { it.id == 38 }

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains("FirebaseAnalytics"), result.detail)
    }

    @Test
    fun anAndroidManifestThatLeavesCollectionOnIsRejected() {
        val real = AnalyticsIntegrationContract.Inputs.from(repositoryRoot)
        val mutated =
            real.copy(
                androidManifest =
                    real.androidManifest.replace(
                        "android:name=\"firebase_analytics_collection_enabled\"",
                        "android:name=\"firebase_analytics_collection_disabled\"",
                    ),
            )

        val result = AnalyticsIntegrationContract(mutated).validate().first { it.id == 39 }

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains("firebase_analytics_collection_enabled"), result.detail)
    }

    @Test
    fun anIosInfoPlistThatLeavesCollectionOnIsRejected() {
        val real = AnalyticsIntegrationContract.Inputs.from(repositoryRoot)
        val mutated =
            real.copy(
                iosInfoPlist =
                    real.iosInfoPlist.replace(
                        "<key>FIREBASE_ANALYTICS_COLLECTION_ENABLED</key>",
                        "<key>FIREBASE_ANALYTICS_COLLECTION_DISABLED</key>",
                    ),
            )

        val result = AnalyticsIntegrationContract(mutated).validate().first { it.id == 39 }

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains("FIREBASE_ANALYTICS_COLLECTION_ENABLED"), result.detail)
    }

    @Test
    fun aHostWithAllPrivacyControlsPasses() {
        val results = AnalyticsIntegrationContract(privacyInputs()).validate()

        assertEquals(4, results.size)
        results.forEach { result -> assertEquals(AssertionResult.Status.PASS, result.status, result.detail) }
    }

    @Test
    fun aDefaultAdvertisingIosProductIsRejected() {
        val real = privacyInputs()
        val mutations =
            listOf(
                real.copy(projectSpec = real.projectSpec.replace(NO_AD_PRODUCT, "FirebaseAnalytics")),
                real.copy(xcodeProject = real.xcodeProject.replace(NO_AD_PRODUCT, "FirebaseAnalytics")),
            )

        mutations.forEach { mutated ->
            val result = AnalyticsIntegrationContract(mutated).validate().first { it.id == 38 }
            assertEquals(AssertionResult.Status.FAIL, result.status, result.detail)
        }
    }

    @Test
    fun anIosProductWithAnAcceptedPrefixIsRejected() {
        val real = privacyInputs()
        val mutated =
            real.copy(
                projectSpec = real.projectSpec.replace(NO_AD_PRODUCT, "${NO_AD_PRODUCT}Invalid"),
                xcodeProject = real.xcodeProject.replace(NO_AD_PRODUCT, "${NO_AD_PRODUCT}Invalid"),
            )

        val result = AnalyticsIntegrationContract(mutated).validate().first { it.id == 38 }

        assertEquals(AssertionResult.Status.FAIL, result.status, result.detail)
    }

    @Test
    fun anAdditionalAdvertisingProductIsRejected() {
        val real = privacyInputs()
        val mutations =
            listOf(
                real.copy(projectSpec = real.projectSpec + "\n        product: FirebaseAnalytics\n"),
                real.copy(xcodeProject = real.xcodeProject + "\nproductName = FirebaseAnalytics;\n"),
            )

        mutations.forEach { mutated ->
            val result = AnalyticsIntegrationContract(mutated).validate().first { it.id == 38 }
            assertEquals(AssertionResult.Status.FAIL, result.status, result.detail)
        }
    }

    @Test
    fun everyAndroidPrivacySettingIsRequiredAndMustBeFalse() {
        val real = privacyInputs()
        androidPrivacyKeys.forEach { key ->
            val declaration = "<meta-data android:name=\"$key\" android:value=\"false\" />"
            val mutations =
                listOf(
                    real.androidManifest.replace(declaration, ""),
                    real.androidManifest.replace(declaration, declaration.replace("false", "true")),
                )
            mutations.forEach { manifest ->
                assertPrivacyRejected(real.copy(androidManifest = manifest), key)
            }
        }
    }

    @Test
    fun everyIosPrivacySettingIsRequiredAndMustBeFalse() {
        val real = privacyInputs()
        iosPrivacyKeys.forEach { key ->
            val declaration = "<key>$key</key><false/>"
            val mutations =
                listOf(
                    real.iosInfoPlist.replace(declaration, ""),
                    real.iosInfoPlist.replace(declaration, "<key>$key</key><true/>"),
                )
            mutations.forEach { plist ->
                assertPrivacyRejected(real.copy(iosInfoPlist = plist), key)
            }
        }
    }

    @Test
    fun everyAdvertisingPermissionMustBeRemoved() {
        val real = privacyInputs()
        advertisingPermissions.forEach { permission ->
            val declaration = "<uses-permission android:name=\"$permission\" tools:node=\"remove\" />"
            val mutations =
                listOf(
                    real.androidManifest.replace(declaration, ""),
                    real.androidManifest.replace(declaration, declaration.replace("remove", "merge")),
                )
            mutations.forEach { manifest ->
                assertPrivacyRejected(real.copy(androidManifest = manifest), permission)
            }
        }
    }

    @Test
    fun privacyDeclarationsOnlyInCommentsDoNotCount() {
        val real = privacyInputs()
        val mutations =
            listOf(
                real.copy(androidManifest = "<!-- ${real.androidManifest} -->"),
                real.copy(iosInfoPlist = "<!-- ${real.iosInfoPlist} -->"),
            )

        mutations.forEachIndexed { index, mutated ->
            val key = if (index == 0) androidPrivacyKeys.first() else iosPrivacyKeys.first()
            assertPrivacyRejected(mutated, key)
        }
    }

    private fun assertPrivacyRejected(
        inputs: AnalyticsIntegrationContract.Inputs,
        offendingKey: String,
    ) {
        val result = AnalyticsIntegrationContract(inputs).validate().firstOrNull { it.id == 40 }
        assertEquals(AssertionResult.Status.FAIL, result?.status, "privacy guard must reject $offendingKey")
        assertTrue(checkNotNull(result).detail.contains(offendingKey), result.detail)
    }

    private fun privacyInputs(): AnalyticsIntegrationContract.Inputs {
        val real = AnalyticsIntegrationContract.Inputs.from(repositoryRoot)
        val permissions =
            advertisingPermissions.joinToString("\n") { permission ->
                "<uses-permission android:name=\"$permission\" tools:node=\"remove\" />"
            }
        val androidKeys = androidPrivacyKeys + "firebase_analytics_collection_enabled"
        val androidControls =
            androidKeys.joinToString("\n") { key ->
                "<meta-data android:name=\"$key\" android:value=\"false\" />"
            }
        val iosKeys = iosPrivacyKeys + "FIREBASE_ANALYTICS_COLLECTION_ENABLED"
        val iosControls = iosKeys.joinToString("\n") { key -> "<key>$key</key><false/>" }
        return real.copy(
            projectSpec = "product: $NO_AD_PRODUCT\n",
            xcodeProject = "productName = $NO_AD_PRODUCT;\n",
            androidManifest =
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
                    "xmlns:tools=\"http://schemas.android.com/tools\">$permissions" +
                    "<application>$androidControls</application></manifest>",
            iosInfoPlist = "<plist><dict>$iosControls</dict></plist>",
        )
    }

    private val androidPrivacyKeys =
        listOf(
            "google_analytics_adid_collection_enabled",
            "google_analytics_default_allow_ad_personalization_signals",
            "google_analytics_automatic_screen_reporting_enabled",
        )
    private val iosPrivacyKeys =
        listOf(
            "GOOGLE_ANALYTICS_IDFV_COLLECTION_ENABLED",
            "GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS",
            "FirebaseAutomaticScreenReportingEnabled",
        )
    private val advertisingPermissions =
        listOf(
            "com.google.android.gms.permission.AD_ID",
            "android.permission.ACCESS_ADSERVICES_AD_ID",
            "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
            "android.permission.ACCESS_ADSERVICES_TOPICS",
        )

    private companion object {
        const val NO_AD_PRODUCT = "FirebaseAnalyticsWithoutAdIdSupport"
    }

    private fun realInputs(sources: List<Pair<String, String>>): AnalyticsIntegrationContract.Inputs =
        AnalyticsIntegrationContract.Inputs.from(repositoryRoot).copy(kotlinSources = sources)

    private fun readSources(): List<Pair<String, String>> =
        repositoryRoot
            .walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    "${File.separator}build${File.separator}" !in file.path
            }.map { file ->
                repositoryRoot.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/') to
                    file.readText()
            }.toList()
}
