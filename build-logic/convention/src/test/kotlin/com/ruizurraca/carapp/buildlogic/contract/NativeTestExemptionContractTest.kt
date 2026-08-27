package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeTestExemptionContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val workflow = repositoryRoot.resolve(".github/workflows/ci.yml").readText()

    @Test
    fun sharedTestsDeclareTheExactFirebaseNativeExemptionSet() {
        val result = NativeTestExemptionContract.validate(workflow)

        assertEquals(AssertionResult.Status.PASS, result.status, result.detail)
        assertEquals(
            setOf(":integration:firebase-auth", ":integration:firebase-firestore"),
            NativeTestExemptionContract.expectedModules,
        )
    }

    @Test
    fun removingAnExemptionIsRejected() {
        val mutated = workflow.replace(
            "-x :integration:firebase-auth:iosSimulatorArm64Test",
            "",
        )

        assertEquals(
            AssertionResult.Status.FAIL,
            NativeTestExemptionContract.validate(mutated).status,
        )
    }

    @Test
    fun addingAnExemptionIsRejected() {
        val mutated = workflow.replace(
            "-x :integration:firebase-firestore:iosSimulatorArm64Test",
            "-x :integration:firebase-firestore:iosSimulatorArm64Test " +
                "-x :integration:firebase-storage:iosSimulatorArm64Test",
        )

        assertEquals(
            AssertionResult.Status.FAIL,
            NativeTestExemptionContract.validate(mutated).status,
        )
    }
}
