package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

internal class CloudRuntimeContract(
    private val repoRoot: File,
) {
    fun validate(): List<AssertionResult> =
        listOf(
            AssertionResult(
                19,
                "Cloud Functions runtime sources match the normative version matrix",
                AssertionResult.Status.FAIL,
                "not implemented",
            ),
            AssertionResult(
                20,
                "Google GitHub Actions match the immutable version-matrix SHAs",
                AssertionResult.Status.FAIL,
                "not implemented",
            ),
        )
}
