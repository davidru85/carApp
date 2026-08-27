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

    @Test
    fun everyRuntimeSourceIsLoadBearing() {
        val inputs = repositoryInputs()

        for (fixture in listOf(
            inputs.copy(functionsPackage = inputs.functionsPackage.replace("\"node\": \"22\"", "\"node\": \"24\"")),
            inputs.copy(firebaseConfig = inputs.firebaseConfig.replace("\"runtime\": \"nodejs22\"", "\"runtime\": \"nodejs24\"")),
            inputs.copy(workflow = inputs.workflow.replace("./scripts/verify-cloud-runtime.sh", "true")),
            inputs.copy(runtimeScript = inputs.runtimeScript.replace("buildConfig.runtime", "serviceConfig.timeoutSeconds")),
        )) {
            assertEquals(
                AssertionResult.Status.FAIL,
                CloudRuntimeContract(fixture).validate().single { it.id == 19 }.status,
            )
        }
    }

    @Test
    fun eachGoogleActionShaIsLoadBearing() {
        val inputs = repositoryInputs()

        for (action in listOf("google-github-actions/auth@", "google-github-actions/setup-gcloud@")) {
            val mutatedWorkflow = inputs.workflow.replace(
                Regex("${Regex.escape(action)}[0-9a-f]{40}"),
                "${action}${"0".repeat(40)}",
            )
            assertEquals(
                AssertionResult.Status.FAIL,
                CloudRuntimeContract(inputs.copy(workflow = mutatedWorkflow)).validate()
                    .single { it.id == 20 }.status,
            )
        }
    }

    private fun repositoryInputs() =
        CloudRuntimeContractInputs(
            firebaseConfig = repositoryRoot.resolve("firebase.json").readText(),
            functionsPackage = repositoryRoot.resolve("functions/package.json").readText(),
            runtimeScript = repositoryRoot.resolve("scripts/verify-cloud-runtime.sh").readText(),
            versionMatrix = repositoryRoot.resolve("docs/versions-matrix.md").readText(),
            workflow = repositoryRoot.resolve(".github/workflows/ci.yml").readText(),
        )
}
