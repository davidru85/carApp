package com.ruizurraca.carapp.buildlogic.contract

import com.ruizurraca.carapp.buildlogic.source.KotlinSourceText
import java.io.File

/**
 * The closed set of `UiMessage.code` values the shared layer can publish (`docs/CONTRACTS.md §20.10`):
 * every `AppError` leaf code, `CONFIRMATION.<entry>` for every `Confirmation` entry, and every
 * `INFO.*` string literal of the production Kotlin sources.
 *
 * `build-logic` cannot depend on `:core:common`, so the codes are parsed from their declarations, in
 * the same way `ConnectivityCodeParityTest` reads `CONNECTIVITY_ERROR_CODES`. Assertion 41 and
 * `AppErrorCodesTestParityTest` read this one inventory, so neither the host mappings nor the codes
 * test can fall behind a new leaf.
 */
internal object UiMessageCodeInventory {
    const val APP_ERROR_PATH = "core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/AppError.kt"
    const val CONFIRMATION_PREFIX = "CONFIRMATION."

    private const val OVERRIDE = "override"
    private val LEAF_CODE = Regex("""override\s+val\s+code\s*=\s*\"([A-Z][A-Z0-9_]*(?:\.[A-Z][A-Z0-9_]*)?)\"""")
    private val CONFIRMATION_ENUM = Regex("""\benum\s+class\s+Confirmation\s*\{([^}]*)\}""")
    private val INFO_LITERAL = Regex("""\"(INFO\.[A-Z][A-Z0-9_]*)\"""")

    /**
     * Every leaf code, in declaration order. A leaf counts only when its `override` keyword is code in
     * the `KotlinSourceText.code` view, so a commented-out leaf is not read.
     */
    fun appErrorCodes(source: String): List<String> {
        val code = KotlinSourceText.code(source)
        return LEAF_CODE.findAll(source)
            .filter { match -> code.startsWith(OVERRIDE, match.range.first) }
            .map { match -> match.groupValues[1] }
            .toList()
    }

    /** `CONFIRMATION.<entry>` for every entry of `enum class Confirmation`, in declaration order. */
    fun confirmationCodes(source: String): List<String> {
        val body = CONFIRMATION_ENUM.find(KotlinSourceText.code(source))?.groupValues?.get(1) ?: return emptyList()
        return body.substringBefore(';')
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { entry -> "$CONFIRMATION_PREFIX$entry" }
    }

    /**
     * Every `INFO.*` string literal, distinct and sorted. A literal inside a comment is read as well:
     * that fails closed, because it can only demand a mapping nobody needs, never hide one somebody
     * does.
     */
    fun infoCodes(sources: Map<String, String>): List<String> =
        sources.values
            .flatMap { text -> INFO_LITERAL.findAll(text).map { match -> match.groupValues[1] }.toList() }
            .distinct()
            .sorted()

    /**
     * `path -> text` of every production Kotlin source that can construct a `UiMessage` or declare a
     * `UiState`: `shared/src/commonMain` and the `src/commonMain` of every feature and core module.
     */
    fun producerSources(repoRoot: File): Map<String, String> {
        val roots =
            listOf(repoRoot.resolve("shared/src/commonMain")) +
                moduleRoots(repoRoot, "feature") +
                moduleRoots(repoRoot, "core")
        return roots
            .filter(File::isDirectory)
            .flatMap { root -> root.walkTopDown().filter { file -> file.isFile && file.extension == "kt" }.toList() }
            .map { file -> relativePath(repoRoot, file) to file.readText() }
            .sortedBy { (path, _) -> path }
            .toMap()
    }

    /** The repository-relative path with `/` separators, so a failure names the file a reviewer opens. */
    fun relativePath(repoRoot: File, file: File): String =
        repoRoot.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')

    private fun moduleRoots(repoRoot: File, group: String): List<File> =
        repoRoot.resolve(group)
            .listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .sortedBy(File::getName)
            .map { module -> module.resolve("src/commonMain") }
}
