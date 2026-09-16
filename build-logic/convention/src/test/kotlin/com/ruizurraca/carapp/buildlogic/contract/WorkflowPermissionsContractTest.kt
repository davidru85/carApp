package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkflowPermissionsContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val workflow = repositoryRoot.resolve(".github/workflows/ci.yml").readText()

    @Test
    fun repositoryWorkflowPasses() {
        assertEquals(
            listOf(AssertionResult.Status.PASS, AssertionResult.Status.PASS),
            WorkflowPermissionsContract(workflow).validate().map(AssertionResult::status),
            WorkflowPermissionsContract(workflow).validate().joinToString { "${it.id}: ${it.detail}" },
        )
    }

    @Test
    fun broaderWorkflowPermissionFails() {
        val mutated = workflow.replace("permissions:\n  contents: read", "permissions:\n  contents: write")
        assertEquals(AssertionResult.Status.FAIL, WorkflowPermissionsContract(mutated).validate().single { it.id == 31 }.status)
    }

    @Test
    fun unnecessaryJobPermissionFails() {
        val mutated = workflow.replace("  android-assemble:\n", "  android-assemble:\n    permissions:\n      contents: write\n")
        assertEquals(AssertionResult.Status.FAIL, WorkflowPermissionsContract(mutated).validate().single { it.id == 32 }.status)
    }

    @Test
    fun contractCheckCannotDropOidcPermission() {
        val mutated = workflow.replace("      id-token: write\n", "")
        assertEquals(AssertionResult.Status.FAIL, WorkflowPermissionsContract(mutated).validate().single { it.id == 32 }.status)
    }
}
