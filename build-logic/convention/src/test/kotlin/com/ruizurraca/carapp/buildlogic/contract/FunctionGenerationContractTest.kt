package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class FunctionGenerationContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun repositoryFunctionsSurfaceMatchesTheTd01ExceptionAndTheCodebaseSplit() {
        assertEquals(
            AssertionResult.Status.PASS,
            FunctionGenerationContract(repositoryRoot).validate().status,
        )
    }

    @Test
    fun everyAllowlistInputIsLoadBearing() {
        val inputs = repositoryInputs()
        val extraV1Module = inputs.functionSources + ("auth/onUserCreated.ts" to "import {auth} from \"firebase-functions/v1\";")

        val fixtures =
            listOf(
                inputs.copy(functionSources = extraV1Module),
                inputs.copy(functionSources = inputs.functionSources - "auth/onAnonymousUserDeleted.ts"),
                inputs.withAdditionalIndexExport("export const onUserCreated = 1;"),
                inputs.withAdditionalIndexExport("export {hiddenGrouped, secondHidden} from \"./hidden.js\";"),
                inputs.withAdditionalIndexExport("export { paddedHidden } from \"./hidden.js\";"),
                inputs.withAdditionalIndexExport("export {sourceHidden as aliasedHidden} from \"./hidden.js\";"),
                inputs.copy(firebaseConfig = inputs.firebaseConfig.replace("nodejs22", "nodejs24")),
                inputs.copy(
                    firebaseConfig =
                        inputs.firebaseConfig.replace(
                            "\"codebase\": \"default\"",
                            "\"codebase\": \"default\", \"codebase\": \"second\"",
                        ),
                ),
            )

        assertEquals(
            List(fixtures.size) { AssertionResult.Status.FAIL },
            fixtures.map { FunctionGenerationContract(it).validate().status },
        )
    }

    @Test
    fun theProductCodebaseRejectsTheBillingCutoff() {
        val inputs = repositoryInputs()

        assertEquals(
            AssertionResult.Status.FAIL,
            FunctionGenerationContract(
                inputs.withAdditionalIndexExport("export {stopBilling} from \"./billing/stopBilling.js\";"),
            ).validate().status,
        )
    }

    @Test
    fun theDevelopmentCodebaseExportsOnlyTheBillingCutoff() {
        val inputs = repositoryInputs()
        val extraExport =
            inputs.devBillingSources +
                ("index.ts" to "${inputs.devBillingSources.getValue("index.ts")}\nexport const deleteAccount = 1;\n")

        assertEquals(
            AssertionResult.Status.FAIL,
            FunctionGenerationContract(inputs.copy(devBillingSources = extraExport)).validate().status,
        )
    }

    @Test
    fun theDevelopmentCodebaseUsesNoFirstGenApi() {
        val inputs = repositoryInputs()
        val v1Source = inputs.devBillingSources + ("billing/legacy.ts" to "import {pubsub} from \"firebase-functions/v1\";")

        assertEquals(
            AssertionResult.Status.FAIL,
            FunctionGenerationContract(inputs.copy(devBillingSources = v1Source)).validate().status,
        )
    }

    @Test
    fun swappingTheCodebaseSourcesIsRejected() {
        val inputs = repositoryInputs()
        val swapped =
            inputs.firebaseConfig
                .replace("\"source\": \"functions-dev-billing\"", "\"source\": \"PLACEHOLDER\"")
                .replace("\"source\": \"functions\"", "\"source\": \"functions-dev-billing\"")
                .replace("\"source\": \"PLACEHOLDER\"", "\"source\": \"functions\"")

        assertEquals(
            AssertionResult.Status.FAIL,
            FunctionGenerationContract(inputs.copy(firebaseConfig = swapped)).validate().status,
        )
    }

    private fun FunctionGenerationContractInputs.withAdditionalIndexExport(exportStatement: String) =
        copy(functionSources = functionSources + ("index.ts" to "${functionSources.getValue("index.ts")}\n$exportStatement\n"))

    private fun repositoryInputs() =
        FunctionGenerationContractInputs(
            functionSources = sourcesUnder("functions/src"),
            devBillingSources = sourcesUnder("functions-dev-billing/src"),
            firebaseConfig = repositoryRoot.resolve("firebase.json").readText(),
        )

    private fun sourcesUnder(path: String): Map<String, String> {
        val root = repositoryRoot.resolve(path)
        return root
            .walkTopDown()
            .filter { it.isFile && it.extension == "ts" }
            .associate { file -> root.toPath().relativize(file.toPath()).toString() to file.readText() }
    }
}
