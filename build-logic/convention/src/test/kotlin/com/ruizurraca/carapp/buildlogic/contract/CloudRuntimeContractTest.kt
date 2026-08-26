package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class CloudRuntimeContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun repositoryRuntimeAndActionsMatchTheNormativeVersionMatrix() {
        val results = CloudRuntimeContract(repositoryRoot).validate()

        assertEquals(
            listOf(AssertionResult.Status.PASS, AssertionResult.Status.PASS),
            results.map(AssertionResult::status),
            results.joinToString { "${it.id}: ${it.detail}" },
        )
    }
}
