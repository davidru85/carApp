package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WorkflowTimeoutContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val workflow = repositoryRoot.resolve(".github/workflows/ci.yml").readText()

    @Test
    fun repositoryWorkflowPasses() {
        assertEquals(
            List(4) { AssertionResult.Status.PASS },
            WorkflowTimeoutContract(workflow).validate().map(AssertionResult::status),
            WorkflowTimeoutContract(workflow).validate().joinToString { "${it.id}: ${it.detail}" },
        )
    }

    @Test
    fun missingJobTimeoutFails() {
        val mutated = workflow.replaceFirst("    timeout-minutes: 20\n    steps:", "    steps:")
        assertEquals(AssertionResult.Status.FAIL, WorkflowTimeoutContract(mutated).validate().single { it.id == 27 }.status)
    }

    @Test
    fun excessiveJobTimeoutFails() {
        val mutated = workflow.replaceFirst("    timeout-minutes: 20", "    timeout-minutes: 21")
        assertEquals(AssertionResult.Status.FAIL, WorkflowTimeoutContract(mutated).validate().single { it.id == 28 }.status)
    }

    @Test
    fun missingPlatformStepTimeoutFails() {
        val mutated = workflow.replaceFirst(
            Regex("(      - name: Run Kotlin/Native simulator tests\\n        timeout-minutes: )\\d+\\n"),
            "$1",
        )
        assertNotEquals(workflow, mutated, "The fixture mutation must remove the named step timeout")
        assertEquals(AssertionResult.Status.FAIL, WorkflowTimeoutContract(mutated).validate().single { it.id == 29 }.status)
    }

    @Test
    fun missingProtectedCheckFails() {
        val mutated = workflow.replace("    name: provider-decoupling\n", "    name: provider-decoupling-renamed\n")
        assertEquals(AssertionResult.Status.FAIL, WorkflowTimeoutContract(mutated).validate().single { it.id == 30 }.status)
    }
}
