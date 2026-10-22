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
        val mutated = workflow.replaceFirst("    timeout-minutes: 40\n    steps:", "    steps:")
        assertEquals(AssertionResult.Status.FAIL, WorkflowTimeoutContract(mutated).validate().single { it.id == 27 }.status)
    }

    @Test
    fun excessiveJobTimeoutFails() {
        val mutated = workflow.replaceFirst("    timeout-minutes: 40", "    timeout-minutes: 41")
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

    /**
     * `D-230` retired the 60-minute `ios-ui-tests` exception of `D-229`: each UI test shard fits the
     * default, so a shard that takes the old ceiling back MUST fail instead of borrowing it.
     */
    @Test
    fun aUiTestShardCannotKeepTheRetiredSixtyMinuteCeiling() {
        val mutated = workflow.replaceFirst(
            "  ios-ui-tests-audit-1:\n    name: ios-ui-tests-audit-1\n    runs-on: macos-latest\n    timeout-minutes: 40",
            "  ios-ui-tests-audit-1:\n    name: ios-ui-tests-audit-1\n    runs-on: macos-latest\n    timeout-minutes: 60",
        )
        assertNotEquals(workflow, mutated, "The fixture mutation must raise the audit-1 shard to 60 minutes")
        assertEquals(
            AssertionResult.Status.FAIL,
            WorkflowTimeoutContract(mutated).validate().single { it.id == 28 }.status,
        )
    }

    /**
     * A raised default would defeat the guard for every other job, so the higher ceiling MUST NOT be
     * reachable by a job that does not name one: `ktlint` at 60 minutes fails assertion 28.
     */
    @Test
    fun aJobWithoutItsOwnCeilingCannotBorrowATallerOne() {
        val mutated = workflow.replaceFirst("  ktlint:\n    name: ktlint\n    runs-on: ubuntu-latest\n    timeout-minutes: 40", "  ktlint:\n    name: ktlint\n    runs-on: ubuntu-latest\n    timeout-minutes: 60")
        assertNotEquals(workflow, mutated, "The fixture mutation must raise ktlint past the default ceiling")
        assertEquals(
            AssertionResult.Status.FAIL,
            WorkflowTimeoutContract(mutated).validate().single { it.id == 28 }.status,
        )
    }

    /** A UI shard check that stops being reported under its protected name silently stops gating merges. */
    @Test
    fun renamingAnyUiTestShardCheckFails() {
        listOf("audit-1", "audit-2", "ax3-1", "ax3-2", "remainder").forEach { shard ->
            val mutated = workflow.replace("    name: ios-ui-tests-$shard\n", "    name: ios-ui-tests-$shard-renamed\n")
            assertNotEquals(workflow, mutated, "The fixture mutation must rename the ios-ui-tests-$shard check")
            assertEquals(
                AssertionResult.Status.FAIL,
                WorkflowTimeoutContract(mutated).validate().single { it.id == 30 }.status,
                "Renaming ios-ui-tests-$shard must fail assertion 30",
            )
        }
    }
}
