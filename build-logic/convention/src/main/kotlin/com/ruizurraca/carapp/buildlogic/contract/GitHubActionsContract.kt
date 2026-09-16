package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/** Contract for the foundational GitHub Actions pins used by every CI job. */
internal class GitHubActionsContract private constructor(
    private val inputs: Inputs,
) {
    constructor(repoRoot: File) : this(
        Inputs(
            workflow = repoRoot.resolve(".github/workflows/ci.yml").readText(),
            versionMatrix = repoRoot.resolve("docs/versions-matrix.md").readText(),
        ),
    )

    constructor(inputs: Inputs, fixture: Boolean = true) : this(inputs) {
        check(fixture) { "The fixture marker prevents constructor signature ambiguity" }
    }

    fun validate(): List<AssertionResult> = listOf(immutablePinsResult(), runtimeGenerationResult())

    private fun immutablePinsResult(): AssertionResult {
        val mismatches = mutableListOf<String>()
        specs.forEach { spec ->
            val expectedSha = matrixSha(spec.matrixRow)
            val references = USES.findAll(inputs.workflow)
                .filter { it.groupValues[1] == spec.artifact }
                .map { it.groupValues[2] }
                .toList()
            if (expectedSha == null) mismatches += "${spec.artifact} is missing a recorded SHA"
            if (references.isEmpty()) mismatches += "${spec.artifact} is not used in the workflow"
            references.forEach { reference ->
                if (!SHA.matches(reference)) mismatches += "${spec.artifact} uses floating ref $reference"
                if (expectedSha != null && reference != expectedSha) {
                    mismatches += "${spec.artifact} uses $reference, expected $expectedSha"
                }
            }
        }
        return result(
            id = 25,
            name = "foundational GitHub Actions use immutable version-matrix SHAs",
            mismatches = mismatches,
        )
    }

    private fun runtimeGenerationResult(): AssertionResult {
        val mismatches = mutableListOf<String>()
        specs.forEach { spec ->
            val row = matrixRow(spec.matrixRow)
            if (row == null) {
                mismatches += "${spec.artifact} is missing from the version matrix"
            } else {
                if (!row.any { it.contains("Node.js 24 runtime") }) {
                    mismatches += "${spec.artifact} is not recorded as Node.js 24"
                }
                val version = row.getOrNull(2)?.substringBefore('(')?.trim().orEmpty()
                if (!version.startsWith("5.")) {
                    mismatches += "${spec.artifact} records unsupported generation $version"
                }
            }
        }
        return result(
            id = 26,
            name = "foundational GitHub Actions are recorded as Node.js 24 generations",
            mismatches = mismatches,
        )
    }

    private fun matrixSha(artifact: String): String? =
        matrixRow(artifact)?.getOrNull(2)?.let { SHA.find(it)?.value }

    private fun matrixRow(artifact: String): List<String>? =
        inputs.versionMatrix.lineSequence()
            .firstOrNull { it.startsWith("| $artifact |") }
            ?.trim('|')
            ?.split('|')
            ?.map(String::trim)

    private fun result(
        id: Int,
        name: String,
        mismatches: List<String>,
    ): AssertionResult =
        if (mismatches.isEmpty()) {
            AssertionResult(id, name, AssertionResult.Status.PASS)
        } else {
            AssertionResult(id, name, AssertionResult.Status.FAIL, mismatches.joinToString())
        }

    internal data class Inputs(
        val workflow: String,
        val versionMatrix: String,
    )

    private data class Spec(
        val artifact: String,
        val matrixRow: String,
    )

    private companion object {
        val SHA = Regex("[0-9a-f]{40}")
        val USES = Regex("(?m)^\\s*- uses: ([A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)+)@([^\\s#]+)")
        val specs = listOf(
            Spec("actions/checkout", "GitHub Actions checkout"),
            Spec("actions/setup-java", "GitHub Actions Java setup"),
            Spec("actions/setup-node", "GitHub Actions Node setup"),
            Spec("gradle/actions/setup-gradle", "GitHub Actions Gradle setup"),
        )
    }
}
