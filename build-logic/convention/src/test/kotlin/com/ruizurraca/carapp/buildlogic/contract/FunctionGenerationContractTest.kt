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

        for (fixture in listOf(
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
            inputs.copy(
                firebaseConfig = inputs.firebaseConfig.replace("nodejs22", "nodejs24"),
            ),
            inputs.copy(
                firebaseConfig = inputs.firebaseConfig.replace(
                    "\"codebase\": \"default\"",
                    "\"codebase\": \"default\", \"codebase\": \"second\"",
                ),
            ),
        )) {
            assertEquals(
                AssertionResult.Status.FAIL,
                FunctionGenerationContract(fixture).validate().status,
                "fixture should fail: $fixture",
            )
        }
    }

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
