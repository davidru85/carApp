package com.ruizurraca.carapp.buildlogic.contract

import com.ruizurraca.carapp.buildlogic.architecture.ArchitectureChecker
import com.ruizurraca.carapp.buildlogic.architecture.ModuleUnderCheck
import com.ruizurraca.carapp.buildlogic.architecture.SourceLine
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Pr71ReviewRegressionTest {
    private val real = SwiftSurfaceContract(File(checkNotNull(System.getProperty("carapp.repoRoot")))).inputs

    @Test
    fun annotationMessagesDoNotSetTheVisibilityOfAnExportedFunction() {
        listOf("private", "internal", "protected").forEach { message ->
            val results = mutate(
                SESSION,
                "fun dismissAnonymousReminder()",
                "@Deprecated(\"$message\") fun dismissAnonymousReminder(force: Boolean = false)",
            )
            assertResult(
                results,
                14,
                AssertionResult.Status.FAIL,
                "$SESSION: class SessionStateHolder.dismissAnonymousReminder defaults force: Boolean = false",
            )
        }
    }

    @Test
    fun multipleModifiersAndQualifiedAnnotationsDoNotHidePublicProperties() {
        listOf(
            "public final val extra: String = \"\"",
            "@kotlin.Deprecated(\"temporary\") val extra: String = \"\"",
        ).forEach { declaration ->
            assertResult(
                mutate(SWIFT, "    fun close() {", "    $declaration\n\n    fun close() {"),
                35,
                AssertionResult.Status.FAIL,
                "val extra: String is declared but absent from §20.10",
            )
        }
    }

    @Test
    fun localFunctionsDoNotBecomeExportedGraphMembers() {
        assertAllPass(
            mutate(
                SWIFT,
                "    fun close() {",
                "    fun close() {\n        fun localProbe(retries: Int = 1) = retries\n        localProbe()",
            ),
        )
    }

    @Test
    fun commentsAndStringLiteralsDoNotBecomeGraphMembers() {
        listOf(
            "// fun ghost(retries: Int = 1)",
            "/* fun ghost(retries: Int = 1) */",
            "private val example = \"fun ghost(retries: Int = 1) {\"",
        ).forEach { text ->
            assertAllPass(mutate(SWIFT, "    fun close() {", "    $text\n    fun close() {"))
        }
    }

    @Test
    fun aBacktickNamedExportedFunctionStillReportsItsDefaultArgument() {
        assertResult(
            mutate(
                SESSION,
                "fun dismissAnonymousReminder()",
                "fun `dismissAnonymousReminder`(force: Boolean = false)",
            ),
            14,
            AssertionResult.Status.FAIL,
            "$SESSION: class SessionStateHolder.dismissAnonymousReminder defaults force: Boolean = false",
        )
    }

    @Test
    fun anUnbalancedKotlinContractBlockReturnsAResultInsteadOfThrowing() {
        val results = SwiftSurfaceContract(real.copy(contract = "interface AppGraph {\n    fun close()\n")).validate()
        assertEquals(listOf(14, 34, 35), results.map { it.id })
        assertResult(results, 34, AssertionResult.Status.FAIL, "Unbalanced braces in the interface AppGraph block of §20.10")
    }

    @Test
    fun anUnbalancedSwiftContractBlockReturnsAResultInsteadOfThrowing() {
        val results = SwiftSurfaceContract(real.copy(contract = "class SwiftAppGraph {\n    fun close()\n")).validate()
        assertEquals(listOf(14, 34, 35), results.map { it.id })
        assertResult(results, 35, AssertionResult.Status.FAIL, "Unbalanced braces in the class SwiftAppGraph block of §20.10")
    }

    @Test
    fun koinBindingsCannotExemptExpectOrActualDeclarations() {
        listOf(
            "expect val bindings: Module",
            "actual val bindings: Module = module { }",
        ).forEach(::assertWiringRejected)
        assertWiringAccepted("val bindings: Module = module { }")
        assertWiringAccepted("private val data = 1")
    }

    @Test
    fun qualifiedAnnotationsAndLiteralParenthesesCannotHideWiringDeclarations() {
        listOf(
            "@kotlin.Deprecated(\"legacy\") internal class StrayMapper",
            "@Deprecated(\"(\") internal class StrayMapper",
            "@Deprecated(\")\") internal class StrayMapper",
        ).forEach(::assertWiringRejected)
        assertWiringAccepted("@kotlin.Deprecated(\"(\") val bindings: Module = module { }")
    }

    @Test
    fun anInnerAssignmentOrALiteralIsNotThePropertysKoinInitialiser() {
        listOf(
            "internal val leaked = run { val bindings = module { }; 1 }",
            "internal val leaked = \"= module {\"",
        ).forEach(::assertWiringRejected)
        assertWiringAccepted("val bindings = module { }")
    }

    private fun mutate(path: String, from: String, to: String): List<AssertionResult> {
        val source = real.sources.getValue(path)
        assertTrue(source.contains(from), "Missing mutation anchor: $from")
        return SwiftSurfaceContract(real.copy(sources = real.sources + (path to source.replace(from, to)))).validate()
    }

    private fun assertResult(results: List<AssertionResult>, id: Int, status: AssertionResult.Status, detail: String) {
        val result = results.single { it.id == id }
        assertEquals(status, result.status, result.detail)
        assertEquals(detail, result.detail)
    }

    private fun assertAllPass(results: List<AssertionResult>) {
        results.forEach { assertEquals(AssertionResult.Status.PASS, it.status, it.detail) }
    }

    private fun wiringViolations(source: String) = ArchitectureChecker.check(
        ModuleUnderCheck(
            path = ":wiring:firebase",
            sourceLines = listOf(SourceLine("Probe.kt", 1, source)),
        ),
        emptyList(),
    ).filter { it.rule == "wiring-product-logic" }

    private fun assertWiringRejected(source: String) {
        val violations = wiringViolations(source)
        assertEquals(1, violations.size, "Expected wiring-product-logic for $source; got $violations")
    }

    private fun assertWiringAccepted(source: String) {
        assertTrue(wiringViolations(source).isEmpty(), source)
    }

    private companion object {
        const val SWIFT = "shared/src/commonMain/kotlin/com/ruizurraca/carapp/SwiftAppGraph.kt"
        const val SESSION = "shared/src/commonMain/kotlin/com/ruizurraca/carapp/StateHolders.kt"
    }
}
