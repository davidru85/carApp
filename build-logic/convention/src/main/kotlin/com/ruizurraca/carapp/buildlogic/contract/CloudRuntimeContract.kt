package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

internal data class CloudRuntimeContractInputs(
    val firebaseConfig: String,
    val functionsPackage: String,
    val runtimeScript: String,
    val versionMatrix: String,
    val workflow: String,
)

internal class CloudRuntimeContract private constructor(
    private val inputs: CloudRuntimeContractInputs,
) {
    constructor(repoRoot: File) : this(
        CloudRuntimeContractInputs(
            firebaseConfig = repoRoot.resolve("firebase.json").readText(),
            functionsPackage = repoRoot.resolve("functions/package.json").readText(),
            runtimeScript = repoRoot.resolve("scripts/verify-cloud-runtime.sh").readText(),
            versionMatrix = repoRoot.resolve("docs/versions-matrix.md").readText(),
            workflow = repoRoot.resolve(".github/workflows/ci.yml").readText(),
        ),
    )

    constructor(inputs: CloudRuntimeContractInputs, fixture: Boolean = true) : this(inputs) {
        check(fixture) { "The fixture marker prevents constructor signature ambiguity" }
    }

    fun validate(): List<AssertionResult> = listOf(runtimeResult(), actionResult())

    private fun runtimeResult(): AssertionResult {
        val expected = matrixValue("Cloud Functions runtime")
        val packageRuntime = Regex("\"node\"\\s*:\\s*\"(\\d+)\"")
            .find(inputs.functionsPackage)?.groupValues?.get(1)
        val firebaseRuntime = Regex("\"runtime\"\\s*:\\s*\"nodejs(\\d+)\"")
            .find(inputs.firebaseConfig)?.groupValues?.get(1)
        val workflowUsesScript = inputs.workflow.contains("./scripts/verify-cloud-runtime.sh")
        val scriptReadsNormativeSources =
            listOf(
                "docs/versions-matrix.md",
                "functions/package.json",
                "firebase.json",
                "buildConfig.runtime",
                "gcloud functions describe",
            ).all(inputs.runtimeScript::contains)
        val hardcodedCiRuntime = Regex("nodejs\\d+").containsMatchIn(inputs.workflow + inputs.runtimeScript)
        val mismatches =
            listOfNotNull(
                "version matrix".takeIf { expected == null },
                "functions/package.json=$packageRuntime".takeIf { packageRuntime != expected },
                "firebase.json=$firebaseRuntime".takeIf { firebaseRuntime != expected },
                "workflow runtime script".takeUnless { workflowUsesScript },
                "runtime script normative inputs".takeUnless { scriptReadsNormativeSources },
                "hardcoded CI runtime".takeIf { hardcodedCiRuntime },
            )

        return result(
            id = 19,
            name = "Cloud Functions runtime sources match the normative version matrix",
            mismatches = mismatches,
        )
    }

    private fun actionResult(): AssertionResult {
        val expectedAuthSha = matrixSha("Google GitHub Actions auth")
        val expectedSetupSha = matrixSha("Google GitHub Actions setup-gcloud")
        val mismatches =
            listOfNotNull(
                "auth SHA in version matrix".takeIf { expectedAuthSha == null },
                "setup-gcloud SHA in version matrix".takeIf { expectedSetupSha == null },
                "google-github-actions/auth@$expectedAuthSha".takeUnless {
                    expectedAuthSha != null &&
                        inputs.workflow.contains("google-github-actions/auth@$expectedAuthSha")
                },
                "google-github-actions/setup-gcloud@$expectedSetupSha".takeUnless {
                    expectedSetupSha != null &&
                        inputs.workflow.contains("google-github-actions/setup-gcloud@$expectedSetupSha")
                },
            )

        return result(
            id = 20,
            name = "Google GitHub Actions match the immutable version-matrix SHAs",
            mismatches = mismatches,
        )
    }

    private fun matrixValue(artifact: String): String? =
        matrixCells(artifact)?.getOrNull(2)?.substringBefore(' ')?.trim()

    private fun matrixSha(artifact: String): String? =
        matrixCells(artifact)?.getOrNull(2)
            ?.let { Regex("[0-9a-f]{40}").find(it)?.value }

    private fun matrixCells(artifact: String): List<String>? =
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
}
