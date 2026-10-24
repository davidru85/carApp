package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fixtures for the three `E4-04` Crashlytics assertions, each mutated so the check must fire.
 *
 * A rule with no failing fixture is a rule that passes everything, so the mutations here are the
 * proof that `CrashlyticsIntegrationContract` is not vacuous. Modelled on
 * `AnalyticsIntegrationContractTest`.
 */
class CrashlyticsIntegrationContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun theRealRepositorySatisfiesEveryAssertion() {
        val results = CrashlyticsIntegrationContract(repositoryRoot).validate()

        assertEquals(listOf(46, 47, 48), results.map(AssertionResult::id))
        results.forEach { result -> assertEquals(AssertionResult.Status.PASS, result.status, result.detail) }
    }

    @Test
    fun aSecondConstructionSiteIsRejected() {
        val real = real()
        val leak = "shared/src/commonMain/kotlin/com/ruizurraca/carapp/Leak.kt" to "val leaked = FirebaseCrashReporter()"

        val result = validate(real.copy(kotlinSources = real.kotlinSources + leak), 46)

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains("Leak.kt"), result.detail)
    }

    @Test
    fun aMissingConstructionInWiringIsRejected() {
        val real = real()
        val mutated =
            real.kotlinSources.map { (path, text) ->
                if (path.endsWith("FirebaseAppProviders.kt")) {
                    path to text.replace("FirebaseCrashReporter()", "stagedReporter()")
                } else {
                    path to text
                }
            }

        val result = validate(real.copy(kotlinSources = mutated), 46)

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains("does not construct"), result.detail)
    }

    @Test
    fun anEnabledByDefaultReporterIsRejected() {
        val real = real()
        val mutated =
            real.copy(reporterSource = real.reporterSource.replace("enabled: Boolean = false", "enabled: Boolean = true"))

        val result = validate(mutated, 46)

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains("default"), result.detail)
    }

    @Test
    fun aPublicConstructorThatEnablesReportingIsRejected() {
        val real = real()
        val mutated = real.copy(reporterSource = real.reporterSource.replace("enabled = false,", "enabled = true,"))

        val result = validate(mutated, 46)

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains("public constructor"), result.detail)
    }

    @Test
    fun aUserIdentifierCallInTheIntegrationIsRejected() {
        val real = real()
        val leak =
            "integration/firebase-crashlytics/src/commonMain/kotlin/com/ruizurraca/carapp/integration/" +
                "firebase/crashlytics/Leak.kt" to "fun leak(uid: String) = Firebase.crashlytics.setUserId(uid)"

        val result = validate(real.copy(kotlinSources = real.kotlinSources + leak), 46)

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains("user identifier"), result.detail)
    }

    @Test
    fun aMissingIosProductIsRejected() {
        val real = real()
        val mutated =
            real.copy(
                projectSpec = real.projectSpec.replace("product: FirebaseCrashlytics", "product: FirebaseCrashlyticsMissing"),
                xcodeProject =
                    real.xcodeProject.replace(
                        "productName = FirebaseCrashlytics;",
                        "productName = FirebaseCrashlyticsMissing;",
                    ),
            )

        val result = validate(mutated, 47)

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains("FirebaseCrashlytics"), result.detail)
    }

    @Test
    fun anAndroidManifestThatLeavesCollectionOnIsRejected() {
        val real = real()
        val mutated =
            real.copy(
                androidManifest =
                    real.androidManifest.replace(
                        "android:name=\"firebase_crashlytics_collection_enabled\"",
                        "android:name=\"firebase_crashlytics_collection_disabled\"",
                    ),
            )

        val result = validate(mutated, 48)

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains(ANDROID_COLLECTION_KEY), result.detail)
    }

    @Test
    fun anIosInfoPlistThatLeavesCollectionOnIsRejected() {
        val real = real()
        val mutated =
            real.copy(
                iosInfoPlist =
                    real.iosInfoPlist.replace(
                        "<key>FirebaseCrashlyticsCollectionEnabled</key>",
                        "<key>FirebaseCrashlyticsCollectionDisabled</key>",
                    ),
            )

        val result = validate(mutated, 48)

        assertEquals(AssertionResult.Status.FAIL, result.status)
        assertTrue(result.detail.contains(IOS_COLLECTION_KEY), result.detail)
    }

    @Test
    fun collectionDeclarationsOnlyInCommentsDoNotCount() {
        // Synthetic hosts without inner comments, so wrapping the whole file comments every
        // declaration out (a real manifest's own comments would end the wrapper early).
        val hosts = collectionHosts()
        listOf(
            hosts.copy(androidManifest = "<!-- ${hosts.androidManifest} -->") to ANDROID_COLLECTION_KEY,
            hosts.copy(iosInfoPlist = "<!-- ${hosts.iosInfoPlist} -->") to IOS_COLLECTION_KEY,
        ).forEach { (mutated, key) ->
            val result = validate(mutated, 48)
            assertEquals(AssertionResult.Status.FAIL, result.status, "a commented-out $key must not count")
            assertTrue(result.detail.contains(key), result.detail)
        }
    }

    @Test
    fun aSecondContradictingCollectionDeclarationIsRejected() {
        val hosts = collectionHosts()
        listOf(
            hosts.copy(
                androidManifest =
                    hosts.androidManifest.replace(
                        ANDROID_DECLARATION,
                        ANDROID_DECLARATION + ANDROID_DECLARATION.replace("false", "true"),
                    ),
            ) to ANDROID_COLLECTION_KEY,
            hosts.copy(
                iosInfoPlist = hosts.iosInfoPlist.replace(IOS_DECLARATION, IOS_DECLARATION + IOS_DECLARATION.replace("false", "true")),
            ) to IOS_COLLECTION_KEY,
        ).forEach { (mutated, key) ->
            val result = validate(mutated, 48)
            assertEquals(AssertionResult.Status.FAIL, result.status, "a second $key must be rejected")
            assertTrue(result.detail.contains(key), result.detail)
        }
    }

    @Test
    fun synthesisedHostsWithOneDisabledDeclarationEachPass() {
        // Guards the two fixtures above: their unmutated inputs pass, so each failure is the mutation's.
        assertEquals(AssertionResult.Status.PASS, validate(collectionHosts(), 48).status)
    }

    private fun real(): CrashlyticsIntegrationContract.Inputs = CrashlyticsIntegrationContract.Inputs.from(repositoryRoot)

    private fun collectionHosts(): CrashlyticsIntegrationContract.Inputs =
        real().copy(
            androidManifest =
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">" +
                    "<application>$ANDROID_DECLARATION</application></manifest>",
            iosInfoPlist = "<plist><dict>$IOS_DECLARATION</dict></plist>",
        )

    private fun validate(
        inputs: CrashlyticsIntegrationContract.Inputs,
        id: Int,
    ): AssertionResult = CrashlyticsIntegrationContract(inputs).validate().first { it.id == id }

    private companion object {
        const val ANDROID_COLLECTION_KEY = "firebase_crashlytics_collection_enabled"
        const val IOS_COLLECTION_KEY = "FirebaseCrashlyticsCollectionEnabled"
        const val ANDROID_DECLARATION = "<meta-data android:name=\"$ANDROID_COLLECTION_KEY\" android:value=\"false\" />"
        const val IOS_DECLARATION = "<key>$IOS_COLLECTION_KEY</key><false/>"
    }
}
