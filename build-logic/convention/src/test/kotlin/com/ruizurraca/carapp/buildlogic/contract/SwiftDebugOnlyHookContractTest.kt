package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `E4-04`: a launch-environment read in the application target is a test hook, and a Release build
 * MUST NOT contain one. `CARAPP_UI_TEST_FORCE_FIRST_VEHICLE` was always compiled only under
 * `#if DEBUG`; `CARAPP_UI_TEST_FORCE_WELCOME` shipped in Release until `E4-04`.
 *
 * The scanned surface is the top level of `iosApp/`, which is the application target's source set;
 * `iosApp/Tests` and `iosApp/UITests` are test targets and may set or read the environment.
 */
class SwiftDebugOnlyHookContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun theApplicationTargetReadsTheLaunchEnvironmentOnlyInsideDebug() {
        assertEquals(emptyList(), SwiftDebugOnlyHookRule.violations(applicationSources()))
    }

    @Test
    fun aLaunchEnvironmentReadOutsideDebugIsRejected() {
        val source =
            """
            if ProcessInfo.processInfo.environment["CARAPP_UI_TEST_FORCE_WELCOME"] == "1" {
                return .welcome
            }
            """.trimIndent()

        assertEquals(
            listOf("$FIXTURE_PATH:1 reads the launch environment outside #if DEBUG"),
            SwiftDebugOnlyHookRule.violations(mapOf(FIXTURE_PATH to source)),
        )
    }

    @Test
    fun aLaunchEnvironmentReadInsideDebugIsAccepted() {
        val source =
            """
            #if DEBUG
            if ProcessInfo.processInfo.environment["CARAPP_UI_TEST_FORCE_WELCOME"] == "1" {
                return .welcome
            }
            #endif
            """.trimIndent()

        assertEquals(emptyList(), SwiftDebugOnlyHookRule.violations(mapOf(FIXTURE_PATH to source)))
    }

    @Test
    fun aLaunchEnvironmentReadInTheElseBranchOfDebugIsRejected() {
        val source =
            """
            #if DEBUG
            let forced = false
            #else
            let forced = ProcessInfo.processInfo.environment["HOOK"] == "1"
            #endif
            """.trimIndent()

        assertEquals(
            listOf("$FIXTURE_PATH:4 reads the launch environment outside #if DEBUG"),
            SwiftDebugOnlyHookRule.violations(mapOf(FIXTURE_PATH to source)),
        )
    }

    @Test
    fun aDisjunctionWithDebugDoesNotGuardARead() {
        val source =
            """
            #if DEBUG || targetEnvironment(simulator)
            let forced = ProcessInfo.processInfo.environment["HOOK"] == "1"
            #endif
            """.trimIndent()

        assertEquals(
            listOf("$FIXTURE_PATH:2 reads the launch environment outside #if DEBUG"),
            SwiftDebugOnlyHookRule.violations(mapOf(FIXTURE_PATH to source)),
        )
    }

    @Test
    fun aConjunctionWithDebugGuardsARead() {
        val source =
            """
            #if DEBUG && targetEnvironment(simulator)
            let forced = ProcessInfo.processInfo.environment["HOOK"] == "1"
            #endif
            """.trimIndent()

        assertEquals(emptyList(), SwiftDebugOnlyHookRule.violations(mapOf(FIXTURE_PATH to source)))
    }

    @Test
    fun aCommentedOutReadIsNotACallSite() {
        val source = """// let forced = ProcessInfo.processInfo.environment["HOOK"] == "1""""

        assertEquals(emptyList(), SwiftDebugOnlyHookRule.violations(mapOf(FIXTURE_PATH to source)))
    }

    @Test
fun aLaunchArgumentReadOutsideAnyGuardIsRejected() {
    val source =
        """
        let seeding = ProcessInfo.processInfo.arguments.contains("-SEED")
        """.trimIndent()

    assertEquals(
        listOf("$FIXTURE_PATH:1 reads the launch arguments outside #if DEBUG or #if CARAPP_BENCHMARK"),
        SwiftDebugOnlyHookRule.violations(mapOf(FIXTURE_PATH to source)),
    )
}

@Test
fun aLaunchArgumentReadInsideBenchmarkIsAccepted() {
    val source =
        """
        #if CARAPP_BENCHMARK
        let seeding = ProcessInfo.processInfo.arguments.contains("-SEED")
        #endif
        """.trimIndent()

    assertEquals(emptyList(), SwiftDebugOnlyHookRule.violations(mapOf(FIXTURE_PATH to source)))
}

@Test
fun aLaunchArgumentReadInsideDebugIsAccepted() {
    val source =
        """
        #if DEBUG
        let seeding = ProcessInfo.processInfo.arguments.contains("-SEED")
        #endif
        """.trimIndent()

    assertEquals(emptyList(), SwiftDebugOnlyHookRule.violations(mapOf(FIXTURE_PATH to source)))
}

@Test
fun aLaunchArgumentReadInTheElseBranchOfBenchmarkIsRejected() {
    val source =
        """
        #if CARAPP_BENCHMARK
        let seeding = false
        #else
        let seeding = ProcessInfo.processInfo.arguments.contains("-SEED")
        #endif
        """.trimIndent()

    assertEquals(
        listOf("$FIXTURE_PATH:4 reads the launch arguments outside #if DEBUG or #if CARAPP_BENCHMARK"),
        SwiftDebugOnlyHookRule.violations(mapOf(FIXTURE_PATH to source)),
    )
}

@Test
fun aDisjunctionWithBenchmarkDoesNotGuardALaunchArgumentRead() {
    val source =
        """
        #if CARAPP_BENCHMARK || targetEnvironment(simulator)
        let seeding = ProcessInfo.processInfo.arguments.contains("-SEED")
        #endif
        """.trimIndent()

    assertEquals(
        listOf("$FIXTURE_PATH:2 reads the launch arguments outside #if DEBUG or #if CARAPP_BENCHMARK"),
        SwiftDebugOnlyHookRule.violations(mapOf(FIXTURE_PATH to source)),
    )
}

@Test
fun theBenchmarkConditionDoesNotGuardALaunchEnvironmentRead() {
    val source =
        """
        #if CARAPP_BENCHMARK
        let forced = ProcessInfo.processInfo.environment["HOOK"] == "1"
        #endif
        """.trimIndent()

    assertEquals(
        listOf("$FIXTURE_PATH:2 reads the launch environment outside #if DEBUG"),
        SwiftDebugOnlyHookRule.violations(mapOf(FIXTURE_PATH to source)),
    )
}

private fun applicationSources(): Map<String, String> =
        repositoryRoot
            .resolve("iosApp")
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "swift" }
            .associate { it.relativeTo(repositoryRoot).invariantSeparatorsPath to it.readText() }

    private companion object {
        const val FIXTURE_PATH = "iosApp/Fixture.swift"
    }
}
