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
        val excessive = jobs.filter { (it.timeoutMinutes ?: 0) > MAX_JOB_MINUTES }
            .map { "${it.id} declares ${it.timeoutMinutes} minutes" }
        return result(28, "CI jobs do not exceed the $MAX_JOB_MINUTES-minute safety limit", excessive)
    }

    private fun sharedTestsHavePlatformStepLimits(): AssertionResult {
        val shared = jobs.singleOrNull { it.id == "shared-tests" }
        val required = listOf("Run Android application and KMP host tests", "Run Kotlin/Native simulator tests")
        val missing = if (shared == null) {
            listOf("shared-tests job is missing")
        } else {
            required.filterNot { name ->
                shared.steps.any { step -> step.name == name && step.timeoutMinutes != null && step.timeoutMinutes < MAX_JOB_MINUTES }
            }.map { "$it lacks a platform-specific step timeout" }
        }
        return result(29, "shared-tests has stricter platform-specific step limits", missing)
    }

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
         * The ceiling for a job's `timeout-minutes`. It exists to kill a hung job, not to bound a
         * healthy one, so it MUST retain headroom over the measured distribution: over 58 sampled
         * `ios-simulator-build` runs, the 44 successes ranged 12.1 to 23.9 minutes with a p90 of
         * 18.3, so a ceiling must sit well above the worst observed success rather than near it
         * (`D-176`).
         */
        const val MAX_JOB_MINUTES = 40
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
            "objc-header-golden-check",
            "provider-decoupling",
        )
    }
}
