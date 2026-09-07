package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

internal data class FunctionGenerationContractInputs(
    val functionSources: Map<String, String>,
    val firebaseConfig: String,
)

/**
 * Guards the TD-01 sole-1st-gen exception: only `functions/src/auth/onAnonymousUserDeleted.ts`
 * may depend on `firebase-functions/v1`, the `functions/src/index.ts` export set must match the
 * TD-01 surface exactly, and the deployment configuration must keep a single Node.js 22 codebase
 * (`docs/TECHNICAL_PLAN.md §13`, `D-136`).
 */
internal class FunctionGenerationContract private constructor(
    private val inputs: FunctionGenerationContractInputs,
) {
    constructor(repoRoot: File) : this(
        FunctionGenerationContractInputs(
            functionSources = repoRoot.resolve("functions/src").walkTopDown()
                .filter { it.isFile && it.extension == "ts" }
                .associate { file ->
                    repoRoot.resolve("functions/src").toPath().relativize(file.toPath()).toString() to
                        file.readText()
                },
            firebaseConfig = repoRoot.resolve("firebase.json").readText(),
        ),
    )

    constructor(inputs: FunctionGenerationContractInputs, fixture: Boolean = true) : this(inputs) {
        check(fixture) { "The fixture marker prevents constructor signature ambiguity" }
    }

    fun validate(): AssertionResult {
        val firstGenModules = inputs.functionSources
            .filterValues { it.contains("firebase-functions/v1") }
            .keys
            .sorted()
        val exportSet = exportedFunctionNames(
            inputs.functionSources.getValue("index.ts"),
        )

        val mismatches = listOfNotNull(
            "1st gen modules=$firstGenModules".takeIf {
                firstGenModules != listOf("auth/onAnonymousUserDeleted.ts")
            },
            "index export set=$exportSet".takeIf { exportSet != TD01_EXPORT_SURFACE },
            "multiple firebase.json codebases".takeIf {
                Regex("\"codebase\"").findAll(inputs.firebaseConfig).count() != 1
            },
            "firebase.json runtime".takeIf {
                !inputs.firebaseConfig.contains("\"runtime\": \"nodejs22\"")
            },
        )

        return if (mismatches.isEmpty()) {
            AssertionResult(
                ID,
                NAME,
                AssertionResult.Status.PASS,
                "1st gen allowlist: onAnonymousUserDeleted only; ${exportSet.size} exports",
            )
        } else {
            AssertionResult(ID, NAME, AssertionResult.Status.FAIL, mismatches.joinToString("; "))
        }
    }

    private fun exportedFunctionNames(indexSource: String): List<String> {
        val names = mutableListOf<String>()
        EXPORT_CLAUSE.findAll(indexSource).forEach { match ->
            match.groupValues[1]
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { declaration -> declaration.split(ALIAS_SEPARATOR).last().trim() }
                .filter(TYPESCRIPT_IDENTIFIER::matches)
                .forEach(names::add)
        }
        EXPORTED_CONST.findAll(indexSource).forEach { names += it.groupValues[1] }
        return names.sorted()
    }

    internal companion object {
        private val EXPORT_CLAUSE = Regex("""export\s*\{([^}]*)}""")
        private val ALIAS_SEPARATOR = Regex("""\s+as\s+""")
        private val TYPESCRIPT_IDENTIFIER = Regex("""[A-Za-z_$][A-Za-z0-9_$]*""")
        private val EXPORTED_CONST = Regex("""export\s+const\s+([A-Za-z_$][A-Za-z0-9_$]*)""")
        const val ID = 22
        const val NAME = "only onAnonymousUserDeleted uses Cloud Functions 1st gen (TD-01)"
        val TD01_EXPORT_SURFACE = listOf(
            "deleteAccount",
            "deleteOrphanedAnonymousAccount",
            "issueOrphanCleanupTicket",
            "onAnonymousUserDeleted",
            "stopBilling",
        )
    }
}
