package com.ruizurraca.carapp.buildlogic.contract

/** D-75 graph-derived exact-set guard for standalone Kotlin/Native Firebase test exemptions. */
internal object NativeTestExemptionContract {
    private val rootModules = setOf(
        ":integration:firebase-auth",
        ":integration:firebase-firestore",
    )

    fun deriveExpectedModules(
        dependencyGraph: Map<String, Set<String>>,
        nativeTestProjects: Set<String>,
    ): Set<String> {
        val reachesRoot = mutableMapOf<String, Boolean>()

        fun reachesFirebaseIntegration(
            module: String,
            visiting: Set<String>,
        ): Boolean {
            reachesRoot[module]?.let { return it }
            if (module in rootModules) return true.also { reachesRoot[module] = it }
            if (module in visiting) return false

            val result = dependencyGraph[module].orEmpty().any { dependency ->
                reachesFirebaseIntegration(dependency, visiting + module)
            }
            reachesRoot[module] = result
            return result
        }

        return nativeTestProjects
            .filter { module -> reachesFirebaseIntegration(module, emptySet()) }
            .toSortedSet()
    }

    fun validate(
        workflow: String,
        dependencyGraph: Map<String, Set<String>>,
        nativeTestProjects: Set<String>,
    ): AssertionResult {
        val sharedTestsJob = workflow
            .substringAfter(SHARED_TESTS_JOB, missingDelimiterValue = "")
            .substringBefore(NEXT_JOB)
        if (sharedTestsJob.isBlank()) {
            return failure("the shared-tests job could not be parsed")
        }

        val actualModules = NATIVE_TEST_EXCLUSION.findAll(sharedTestsJob)
            .map { it.groupValues[1] }
            .toSortedSet()
        val expectedModules = deriveExpectedModules(dependencyGraph, nativeTestProjects)
        val runsBothAggregates =
            sharedTestsJob.contains("testAndroidHostTest") &&
                sharedTestsJob.contains("iosSimulatorArm64Test")

        return if (runsBothAggregates && actualModules == expectedModules) {
            AssertionResult(
                21,
                "the standalone Native test exemption set is exact",
                AssertionResult.Status.PASS,
                expectedModules.joinToString(),
            )
        } else {
            failure(
                "expected=$expectedModules, actual=$actualModules, " +
                    "runs Android-host and Native aggregates=$runsBothAggregates",
            )
        }
    }

    private fun failure(detail: String) = AssertionResult(
        21,
        "the standalone Native test exemption set is exact",
        AssertionResult.Status.FAIL,
        detail,
    )

    private const val SHARED_TESTS_JOB = "  shared-tests:"
    private const val NEXT_JOB = "\n  ios-simulator-build:"
    private val NATIVE_TEST_EXCLUSION =
        Regex("""-x\s+(:[a-z0-9:-]+):iosSimulatorArm64Test""")
}
