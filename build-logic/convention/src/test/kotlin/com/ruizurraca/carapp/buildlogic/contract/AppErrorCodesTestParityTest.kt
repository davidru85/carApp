package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `AppErrorCodesTest` pins every `AppError` code leaf by leaf (`docs/CONTRACTS.md §20.2`), but it is a
 * hand-written list, and `E4-02` found it one leaf behind: `AuthError.NoAccountAvailable` was declared
 * and never pinned. The KMP test itself asserts that `allErrors.map { it.code }` equals its literal
 * list, so pinning that list against the parsed declaration makes both impossible to fall behind.
 */
class AppErrorCodesTestParityTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun theCodesTestPinsEveryLeafInDeclarationOrder() {
        val leaves = UiMessageCodeInventory.appErrorCodes(read(UiMessageCodeInventory.APP_ERROR_PATH))
        assertTrue(leaves.isNotEmpty(), "Could not parse AppError.kt; the guard would pass vacuously.")

        val pinned = pinnedCodes(read(CODES_TEST_PATH))

        assertEquals(
            leaves,
            pinned,
            "AppErrorCodesTest does not pin ${leaves - pinned.toSet()}; " +
                "it pins ${pinned - leaves.toSet()} that no leaf declares.",
        )
    }

    @Test
    fun aCodesTestThatDropsALeafIsRejected() {
        val original = read(CODES_TEST_PATH)
        val mutated = original.replaceFirst("\"VALIDATION.REQUIRED_FIELD\",", "")
        assertNotEquals(original, mutated, "The fixture anchor is gone; the guard would be tested vacuously.")

        val leaves = UiMessageCodeInventory.appErrorCodes(read(UiMessageCodeInventory.APP_ERROR_PATH))

        assertNotEquals(leaves, pinnedCodes(mutated))
    }

    /** The string literals of the `listOf(…)` inside `everyLeafCarriesItsDocumentedCode()`. */
    private fun pinnedCodes(source: String): List<String> {
        val start = source.indexOf(PINNING_TEST)
        check(start >= 0) { "Could not find $PINNING_TEST in $CODES_TEST_PATH" }
        val opening = source.indexOf(LIST_OPENING, start)
        check(opening >= 0) { "Could not find the pinned list in $PINNING_TEST" }
        var depth = 0
        var closing = -1
        for (index in opening + LIST_OPENING.length - 1 until source.length) {
            when (source[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) {
                        closing = index
                        break
                    }
                }
            }
        }
        check(closing > opening) { "Unbalanced pinned list in $PINNING_TEST" }
        return CODE_LITERAL.findAll(source.substring(opening, closing)).map { it.groupValues[1] }.toList()
    }

    private fun read(path: String): String = repositoryRoot.resolve(path).readText()

    private companion object {
        const val CODES_TEST_PATH =
            "core/common/src/commonTest/kotlin/com/ruizurraca/carapp/core/common/AppErrorCodesTest.kt"
        const val PINNING_TEST = "fun everyLeafCarriesItsDocumentedCode()"
        const val LIST_OPENING = "listOf("
        val CODE_LITERAL = Regex("""\"([A-Z][A-Z0-9_]*(?:\.[A-Z][A-Z0-9_]*)?)\"""")
    }
}
