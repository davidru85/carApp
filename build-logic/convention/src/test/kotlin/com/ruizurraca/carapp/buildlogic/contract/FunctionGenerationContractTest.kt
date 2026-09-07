package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class FunctionGenerationContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun repositoryFunctionsSurfaceMatchesTheTd01Exception() {
        assertEquals(
            AssertionResult.Status.PASS,
            FunctionGenerationContract(repositoryRoot).validate().status,
        )
    }

    @Test
    fun everyAllowlistInputIsLoadBearing() {
        val inputs = repositoryInputs()
        val extraV1Module = inputs.functionSources +
            ("auth/onUserCreated.ts" to "import {auth} from \"firebase-functions/v1\";")

        val fixtures = listOf(
            inputs.copy(functionSources = extraV1Module),
            inputs.copy(
                functionSources = inputs.functionSources -
                    "auth/onAnonymousUserDeleted.ts",
            ),
            inputs.copy(
                functionSources = inputs.functionSources +
                    ("index.ts" to inputs.functionSources.getValue("index.ts") +
                        "\nexport const onUserCreated = 1;\n"),
            ),
            inputs.withAdditionalIndexExport(
                "export {hiddenGrouped, secondHidden} from \"./hidden.js\";",
            ),
            inputs.withAdditionalIndexExport(
                "export { paddedHidden } from \"./hidden.js\";",
            ),
            inputs.withAdditionalIndexExport(
                "export {sourceHidden as aliasedHidden} from \"./hidden.js\";",
            ),
            inputs.copy(
                firebaseConfig = inputs.firebaseConfig.replace("nodejs22", "nodejs24"),
            ),
            inputs.copy(
                firebaseConfig = inputs.firebaseConfig.replace(
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

    private fun FunctionGenerationContractInputs.withAdditionalIndexExport(
        exportStatement: String,
    ) = copy(
        functionSources = functionSources +
            ("index.ts" to "${functionSources.getValue("index.ts")}\n$exportStatement\n"),
    )

    private fun repositoryInputs() = FunctionGenerationContractInputs(
        functionSources = repositoryRoot.resolve("functions/src").walkTopDown()
            .filter { it.isFile && it.extension == "ts" }
            .associate { file ->
                repositoryRoot.resolve("functions/src").toPath().relativize(file.toPath()).toString() to
                    file.readText()
            },
        firebaseConfig = repositoryRoot.resolve("firebase.json").readText(),
    )
}
