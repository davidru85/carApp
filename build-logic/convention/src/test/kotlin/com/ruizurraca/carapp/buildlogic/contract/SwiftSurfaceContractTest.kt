package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * `E3-08`: the two `AppGraph` surfaces of `docs/CONTRACTS.md §20.10` MUST be guarded.
 *
 * Assertion 14 is declared by `docs/CONTRACTS.md §18` and has never been implemented, so nothing
 * enforced the shape of either surface. The generated Objective-C header cannot substitute for it:
 * Kotlin default arguments do not appear in the generated header at all, so a default added to an
 * exported member would change no diff and still break the Swift call site.
 *
 * Assertion 34 covers the one divergence the header cannot see either, because the Kotlin-facing
 * `AppGraph` is hidden from Objective-C export: the interface of `§20.10` and the real interface
 * MUST declare the same members.
 */
class SwiftSurfaceContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun contractCheckGuardsTheSwiftFacingSurface() {
        val results = ContractCheck(repositoryRoot, emptyMap()).runAll()

        listOf(ASSERTION_KOTLIN_FACTORIES_TAKE_SCOPE, ASSERTION_APP_GRAPH_MEMBERS).forEach { id ->
            val result = results.singleOrNull { it.id == id }
            assertNotNull(result, "contract-check assertion $id is not implemented")
            assertEquals(AssertionResult.Status.PASS, result.status, result.detail)
        }
    }

    private companion object {
        const val ASSERTION_KOTLIN_FACTORIES_TAKE_SCOPE = 14
        const val ASSERTION_APP_GRAPH_MEMBERS = 34
    }
}
