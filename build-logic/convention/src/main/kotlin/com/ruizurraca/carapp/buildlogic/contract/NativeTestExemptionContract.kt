package com.ruizurraca.carapp.buildlogic.contract

/** D-75 exact-set guard for standalone Kotlin/Native Firebase test exemptions. */
internal object NativeTestExemptionContract {
    val expectedModules = sortedSetOf(
        ":integration:firebase-auth",
        ":integration:firebase-firestore",
    )

    fun validate(workflow: String): AssertionResult {
        val sharedTestsJob = workflow
            .substringAfter(SHARED_TESTS_JOB, missingDelimiterValue = "")
            .substringBefore(NEXT_JOB)
        if (sharedTestsJob.isBlank()) {
            return failure("the shared-tests job could not be parsed")
        }

        val actualModules = NATIVE_TEST_EXCLUSION.findAll(sharedTestsJob)
            .map { it.groupValues[1] }
            .toSortedSet()
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
