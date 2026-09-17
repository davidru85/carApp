package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class GitHubActionsContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun repositoryUsesRecordedNode24ActionPins() {
        val results = GitHubActionsContract(repositoryInputs()).validate()

        assertEquals(
            listOf(AssertionResult.Status.PASS, AssertionResult.Status.PASS),
            results.map(AssertionResult::status),
            results.joinToString { "${it.id}: ${it.detail}" },
        )
    }

    @Test
    fun floatingTagFails() {
        val inputs = repositoryInputs()
        val mutated = inputs.copy(
            workflow = inputs.workflow.replace(
                "actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09",
                "actions/checkout@v5",
            ),
        )

        assertEquals(
            AssertionResult.Status.FAIL,
            GitHubActionsContract(mutated).validate().single { it.id == 25 }.status,
        )
    }

    @Test
    fun unrecordedShaFails() {
        val inputs = repositoryInputs()
        val mutated = inputs.copy(
            workflow = inputs.workflow.replace(
                "actions/setup-node@a0853c24544627f65ddf259abe73b1d18a591444",
                "actions/setup-node@${"0".repeat(40)}",
            ),
        )

        assertEquals(
            AssertionResult.Status.FAIL,
            GitHubActionsContract(mutated).validate().single { it.id == 25 }.status,
        )
    }

    @Test
    fun unsupportedRuntimeGenerationFails() {
        val inputs = repositoryInputs()
        val mutated = inputs.copy(
            versionMatrix = inputs.versionMatrix.replace(
                "| GitHub Actions checkout | `actions/checkout` | 5.1.0",
                "| GitHub Actions checkout | `actions/checkout` | 4.4.0",
            ).replace("Node.js 24 runtime; immutable commit pin verified against the official `v5.1.0` tag.", "Node.js 20 runtime; immutable commit pin verified against the official `v4.4.0` tag."),
        )

        assertEquals(
            AssertionResult.Status.FAIL,
            GitHubActionsContract(mutated).validate().single { it.id == 26 }.status,
        )
    }

    @Test
    fun missingMatrixShaFails() {
        val inputs = repositoryInputs()
        val mutated = inputs.copy(
            versionMatrix = inputs.versionMatrix.replace(
                "| GitHub Actions Node setup | `actions/setup-node` | 5.0.0 (`a0853c24544627f65ddf259abe73b1d18a591444`) | `E3-03` | Node.js 24 runtime; immutable commit pin verified against the official `v5.0.0` tag. |\n",
                "",
            ),
        )

        assertEquals(
            AssertionResult.Status.FAIL,
            GitHubActionsContract(mutated).validate().single { it.id == 25 }.status,
        )
    }

    private fun repositoryInputs() =
        GitHubActionsContract.Inputs(
            workflow = repositoryRoot.resolve(".github/workflows/ci.yml").readText(),
            versionMatrix = repositoryRoot.resolve("docs/versions-matrix.md").readText(),
        )
}
