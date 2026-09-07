package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ContractAssertionIdTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun everyContractAssertionIdIsUnique() {
        val duplicateIds = ContractCheck(repositoryRoot, emptyMap())
            .runAll()
            .groupBy(AssertionResult::id)
            .filterValues { results -> results.size > 1 }
            .mapValues { (_, results) -> results.map(AssertionResult::name) }

        assertEquals(emptyMap(), duplicateIds)
    }
}
