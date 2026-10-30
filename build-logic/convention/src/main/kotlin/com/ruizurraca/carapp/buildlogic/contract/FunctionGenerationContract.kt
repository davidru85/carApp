package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

internal data class FunctionGenerationContractInputs(
    val functionSources: Map<String, String>,
    val devBillingSources: Map<String, String>,
    val firebaseConfig: String,
)

/**
 * Guards the TD-01 sole-1st-gen exception and the `D-{D12}` deployment boundary: only
 * `functions/src/auth/onAnonymousUserDeleted.ts` may depend on `firebase-functions/v1`; the product
 * `functions/src/index.ts` export set must match the TD-01 product surface exactly; the
 * development-only `functions-dev-billing/src/index.ts` must export exactly `stopBilling` and use no
 * 1st gen API; and `firebase.json` must declare exactly the `default` and `dev-billing` codebases on
 * Node.js 22 (`docs/TECHNICAL_PLAN.md §13`, `D-136`, `D-{D12}`).
 */
internal class FunctionGenerationContract private constructor(
    private val inputs: FunctionGenerationContractInputs,
) {
    constructor(repoRoot: File) : this(
        FunctionGenerationContractInputs(
            functionSources = sourcesUnder(repoRoot.resolve("functions/src")),
            devBillingSources = sourcesUnder(repoRoot.resolve("functions-dev-billing/src")),
            firebaseConfig = repoRoot.resolve("firebase.json").readText(),
        ),
    )

    constructor(inputs: FunctionGenerationContractInputs, fixture: Boolean = true) : this(inputs) {
        check(fixture) { "The fixture marker prevents constructor signature ambiguity" }
    }

    fun validate(): AssertionResult {
        val firstGenModules = inputs.functionSources.filterValues { it.contains(FIRST_GEN_IMPORT) }.keys.sorted()
        val devBillingFirstGenModules =
            inputs.devBillingSources.filterValues { it.contains(FIRST_GEN_IMPORT) }.keys.sorted()
        val exportSet = exportedFunctionNames(inputs.functionSources[INDEX].orEmpty())
        val devBillingExportSet = exportedFunctionNames(inputs.devBillingSources[INDEX].orEmpty())
        val codebases =
            CODEBASE_ENTRY.findAll(inputs.firebaseConfig).map { it.groupValues[1] to it.groupValues[2] }.toList()
        val codebaseKeys = CODEBASE_KEY.findAll(inputs.firebaseConfig).count()
        val runtimes = RUNTIME_ENTRY.findAll(inputs.firebaseConfig).map { it.groupValues[1] }.toList()

        val mismatches =
            listOfNotNull(
                "1st gen modules=$firstGenModules".takeIf {
                    firstGenModules != listOf("auth/onAnonymousUserDeleted.ts")
                },
                "dev-billing 1st gen modules=$devBillingFirstGenModules".takeIf {
                    devBillingFirstGenModules.isNotEmpty()
                },
                "index export set=$exportSet".takeIf { exportSet != TD01_EXPORT_SURFACE },
                "dev-billing export set=$devBillingExportSet".takeIf {
                    devBillingExportSet != DEV_BILLING_EXPORT_SURFACE
                },
                "firebase.json codebases=$codebases".takeIf {
                    codebases != EXPECTED_CODEBASES || codebaseKeys != EXPECTED_CODEBASES.size
                },
                "firebase.json runtimes=$runtimes".takeIf {
                    runtimes != List(EXPECTED_CODEBASES.size) { NODE_RUNTIME }
                },
            )

        return if (mismatches.isEmpty()) {
            AssertionResult(
                ID,
                NAME,
                AssertionResult.Status.PASS,
                "1st gen allowlist: onAnonymousUserDeleted only; ${exportSet.size} product exports; " +
                    "stopBilling only in dev-billing",
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
        private val CODEBASE_ENTRY = Regex(""""source"\s*:\s*"([^"]+)"\s*,\s*"codebase"\s*:\s*"([^"]+)"""")
        private val CODEBASE_KEY = Regex(""""codebase"""")
        private val RUNTIME_ENTRY = Regex(""""runtime"\s*:\s*"([^"]+)"""")
        private const val FIRST_GEN_IMPORT = "firebase-functions/v1"
        private const val INDEX = "index.ts"
        private const val NODE_RUNTIME = "nodejs22"
        const val ID = 22
        const val NAME =
            "only onAnonymousUserDeleted uses Cloud Functions 1st gen " +
                "and stopBilling ships only from dev-billing"
        val TD01_EXPORT_SURFACE =
            listOf(
                "deleteAccount",
                "deleteOrphanedAnonymousAccount",
                "issueOrphanCleanupTicket",
                "onAnonymousUserDeleted",
            )
        val DEV_BILLING_EXPORT_SURFACE = listOf("stopBilling")
        val EXPECTED_CODEBASES = listOf("functions" to "default", "functions-dev-billing" to "dev-billing")

        private fun sourcesUnder(root: File): Map<String, String> =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "ts" }
                .associate { file -> root.toPath().relativize(file.toPath()).toString() to file.readText() }
    }
}
