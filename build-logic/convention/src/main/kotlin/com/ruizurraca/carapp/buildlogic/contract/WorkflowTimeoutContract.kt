package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/** Contract for CI job safety limits and protected check names. */
internal class WorkflowTimeoutContract private constructor(
    private val workflow: String,
) {
    constructor(repoRoot: File) : this(repoRoot.resolve(".github/workflows/ci.yml").readText())

    constructor(workflow: String, fixture: Boolean = true) : this(workflow) {
        check(fixture) { "The fixture marker prevents constructor signature ambiguity" }
    }

    fun validate(): List<AssertionResult> = listOf(
        requiredJobsHaveTimeouts(),
        jobsDoNotExceedSafetyLimit(),
        sharedTestsHavePlatformStepLimits(),
        protectedCheckNamesRemainPresent(),
    )

    private fun requiredJobsHaveTimeouts(): AssertionResult {
        val missing = jobs.filter { it.timeoutMinutes == null }.map { it.id }
        return result(27, "every protected CI job declares a timeout", missing.map { "$it has no timeout-minutes" })
    }

    private fun jobsDoNotExceedSafetyLimit(): AssertionResult {
        val excessive = jobs
            .filter { (it.timeoutMinutes ?: 0) > ceilingFor(it.id) }
            .map { "${it.id} declares ${it.timeoutMinutes} minutes, its ceiling is ${ceilingFor(it.id)}" }
        val ceilings = JOB_CEILING_MINUTES.entries.joinToString { "${it.key} ${it.value}" }.ifEmpty { "no per-job exceptions" }
        return result(
            28,
            "no CI job exceeds its own safety limit (default $DEFAULT_JOB_CEILING_MINUTES minutes; $ceilings)",
            excessive,
        )
    }

    private fun sharedTestsHavePlatformStepLimits(): AssertionResult {
        val shared = jobs.singleOrNull { it.id == "shared-tests" }
        val required = listOf("Run Android application and KMP host tests", "Run Kotlin/Native simulator tests")
        val missing = if (shared == null) {
            listOf("shared-tests job is missing")
        } else {
            required.filterNot { name ->
                shared.steps.any { step -> step.name == name && step.timeoutMinutes != null && step.timeoutMinutes < ceilingFor(shared.id) }
            }.map { "$it lacks a platform-specific step timeout" }
        }
        return result(29, "shared-tests has stricter platform-specific step limits", missing)
    }

    /** The ceiling that applies to [jobId]: its own named ceiling, or the default for every other job. */
    private fun ceilingFor(jobId: String): Int = JOB_CEILING_MINUTES[jobId] ?: DEFAULT_JOB_CEILING_MINUTES

    private fun protectedCheckNamesRemainPresent(): AssertionResult {
        val present = jobs.mapNotNull { it.name }.toSet()
        val missing = PROTECTED_CHECK_NAMES - present
        return result(30, "all protected CI check names remain present", missing.map { "$it is missing" })
    }

    private fun result(id: Int, name: String, mismatches: List<String>): AssertionResult =
        if (mismatches.isEmpty()) AssertionResult(id, name, AssertionResult.Status.PASS)
        else AssertionResult(id, name, AssertionResult.Status.FAIL, mismatches.joinToString())

    private val jobs: List<Job>
        get() {
            val lines = workflow.substringAfter("\njobs:\n").lines()
            val starts = lines.mapIndexedNotNull { index, line -> JOB_START.matchEntire(line)?.let { index to it.groupValues[1] } }
            return starts.mapIndexed { position, (start, id) ->
                val end = starts.getOrNull(position + 1)?.first ?: lines.size
                parseJob(id, lines.subList(start, end))
            }
        }

    private fun parseJob(id: String, lines: List<String>): Job {
        val name = lines.firstNotNullOfOrNull { NAME.find(it)?.groupValues?.get(1)?.trim() }
        val timeout = lines.firstNotNullOfOrNull { TIMEOUT.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val steps = lines.mapIndexedNotNull { index, line ->
            STEP_NAME.find(line)?.groupValues?.get(1)?.trim()?.let { stepName ->
                val nextStep = lines.drop(index + 1).indexOfFirst { it.startsWith("      - name:") }
                val candidates = if (nextStep < 0) lines.drop(index + 1) else lines.drop(index + 1).take(nextStep)
                Step(stepName, candidates.firstNotNullOfOrNull { STEP_TIMEOUT.find(it)?.groupValues?.get(1)?.toIntOrNull() })
            }
        }
        return Job(id, name, timeout, steps)
    }

    private data class Job(val id: String, val name: String?, val timeoutMinutes: Int?, val steps: List<Step>)

    private data class Step(val name: String, val timeoutMinutes: Int?)

    private companion object {
        /**
         * The ceiling for a job's `timeout-minutes` unless the job names its own. It exists to kill a
         * hung job, not to bound a healthy one, so it MUST retain headroom over the measured
         * distribution: over 58 sampled `ios-simulator-build` runs, the 44 successes ranged 12.1 to
         * 23.9 minutes with a p90 of 18.3, so a ceiling must sit well above the worst observed
         * success rather than near it (`D-176`).
         */
        const val DEFAULT_JOB_CEILING_MINUTES = 40

        /**
         * Jobs whose own measured duration legitimately exceeds [DEFAULT_JOB_CEILING_MINUTES]. Each
         * entry needs a decision, because raising the default instead would widen the guard for
         * every job (`D-229`, ADR-0230). The map is empty: its only entry, `ios-ui-tests` at 60
         * minutes, was retired by `D-230` (ADR-0231) when the UI suite became five shards that each
         * fit the default, after run 39087272433 cancelled the single job at 60 minutes.
         */
        val JOB_CEILING_MINUTES: Map<String, Int> = emptyMap()
        val JOB_START = Regex("^  ([A-Za-z0-9_-]+):$")
        val NAME = Regex("^\\s{4}name: (.+)$")
        val STEP_NAME = Regex("^\\s{6}- name: (.+)$")
        val TIMEOUT = Regex("^\\s{4}timeout-minutes: (\\d+)$")
        val STEP_TIMEOUT = Regex("^\\s{8}timeout-minutes: (\\d+)$")
        val PROTECTED_CHECK_NAMES = setOf(
            "ktlint",
            "detekt",
            "architecture-check",
            "contract-check",
            "android-assemble",
            "android-instrumented-tests",
            "shared-tests",
            "ios-simulator-build",
            "ios-ui-tests-audit-1",
            "ios-ui-tests-audit-2",
            "ios-ui-tests-ax3-1",
            "ios-ui-tests-ax3-2",
            "ios-ui-tests-remainder",
            "objc-header-golden-check",
            "provider-decoupling",
        )
    }
}
