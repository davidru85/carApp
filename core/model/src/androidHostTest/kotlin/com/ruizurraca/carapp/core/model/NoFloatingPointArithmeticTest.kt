package com.ruizurraca.carapp.core.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * `docs/CONTRACTS.md §2`: `Float` and `Double` are FORBIDDEN for money, volume, price and
 * consumption calculations, in every layer.
 *
 * The ban cannot be expressed as a runtime assertion, because a floating-point implementation
 * would still return the right answer for most inputs and would only drift on the cases nobody
 * tests. So this test reads the module's own sources instead: it is a JVM host test precisely
 * because it needs a filesystem, which the Kotlin/Native targets do not offer.
 *
 * `E0-04` generalises this into an architecture rule covering `:core:*`, `:feature:*` and
 * `:shared`. This test is the module-local guard that exists from Phase 0 onwards.
 */
class NoFloatingPointArithmeticTest {
    @Test
    fun noSourceFileInCoreModelMentionsFloatOrDouble() {
        val sourceRoot = locateCommonMain()
        val sources = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

        assertTrue(sources.isNotEmpty(), "Found no Kotlin sources under $sourceRoot")

        val offenders =
            sources.mapNotNull { file ->
                val hits =
                    file
                        .readLines()
                        .withIndex()
                        .filter { (_, line) -> FORBIDDEN.containsMatchIn(stripComment(line)) }
                        .map { (index, line) -> "  ${file.name}:${index + 1}: ${line.trim()}" }
                if (hits.isEmpty()) null else hits.joinToString("\n")
            }

        if (offenders.isNotEmpty()) {
            fail(
                "Float and Double are forbidden in monetary and consumption paths " +
                    "(docs/CONTRACTS.md §2):\n" + offenders.joinToString("\n"),
            )
        }
    }

    /**
     * Walks up from the working directory to the module root, so the test does not depend on which
     * directory the test runner happens to start in.
     */
    private fun locateCommonMain(): File {
        var candidate: File? = File(".").absoluteFile
        while (candidate != null) {
            val source = File(candidate, "src/commonMain/kotlin")
            if (source.isDirectory) return source
            candidate = candidate.parentFile
        }
        fail("Could not locate src/commonMain/kotlin from ${File(".").absolutePath}")
    }

    /** Comments are stripped so that prose mentioning the ban does not trip it. */
    private fun stripComment(line: String): String {
        val trimmed = line.trim()
        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) return ""
        return line.substringBefore("//")
    }

    private companion object {
        val FORBIDDEN = Regex("""\b(Float|Double|toFloat|toDouble)\b""")
    }
}
