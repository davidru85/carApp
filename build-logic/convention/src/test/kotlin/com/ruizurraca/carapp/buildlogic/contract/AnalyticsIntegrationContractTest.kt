package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fixtures for the two `E3-09` assertions, each mutated so the check must fire.
 *
 * A rule with no failing fixture is a rule that passes everything, so the mutations here are the
 * proof that `AnalyticsIntegrationContract` is not vacuous.
 */
class AnalyticsIntegrationContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val realSources = readSources()

    @Test
    fun theRealRepositorySatisfiesBothAssertions() {
        val results = AnalyticsIntegrationContract(repositoryRoot).validate()

        assertEquals(2, results.size)
        results.forEach { result -> assertEquals(AssertionResult.Status.PASS, result.status, result.detail) }
    }

    @Test
    fun aSecondConstructionSiteIsRejected() {
        val mutated =
            realSources.map { (path, text) ->
                if (path == "shared/src/commonMain/kotlin/com/ruizurraca/carapp/Leak.kt") {
                    path to text
                } else {
                    path to text
                }
            } + ("shared/src/commonMain/kotlin/com/ruizurraca/carapp/Leak.kt" to "val leaked = FirebaseAnalyticsTracker()")

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
