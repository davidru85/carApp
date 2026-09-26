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
        // `E3-05` added assertion 36 to the same validation, so the id list carries it too; the point
        // of this test is that the unbalanced block degrades to a result rather than throwing.
        assertEquals(listOf(14, 34, 35, 36), results.map { it.id })
        assertResult(results, 34, AssertionResult.Status.FAIL, "Unbalanced braces in the interface AppGraph block of §20.10")
    }

    @Test
    fun anUnbalancedSwiftContractBlockReturnsAResultInsteadOfThrowing() {
        val results = SwiftSurfaceContract(real.copy(contract = "class SwiftAppGraph {\n    fun close()\n")).validate()
        // See the Kotlin-facing counterpart: `E3-05` added assertion 36 to the same validation.
        assertEquals(listOf(14, 34, 35, 36), results.map { it.id })
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

    /**
     * Round 8, finding B. `STATE_HOLDER` enumerated its modifiers, so `expect class …StateHolder`
     * dropped out of assertion 14. The per-source guard cannot see it: `VehicleStateHolders.kt`
     * still yields `VehicleListStateHolder`, so the source is not empty and nothing is reported.
     */
    @Test
    fun anUnrecognisedClassModifierDoesNotHideAStateHolderFromAssertion14() {
        val mutated = real.sources.getValue(VEHICLE)
            .replace("fun setName(value: String)", "fun setName(value: String = \"\")")
            .replace(
                "class VehicleFormStateHolder internal constructor(",
                "expect class VehicleFormStateHolder internal constructor(",
            )
        val results = SwiftSurfaceContract(real.copy(sources = real.sources + (VEHICLE to mutated))).validate()
        val detail = results.single { it.id == 14 }.detail
        assertEquals(AssertionResult.Status.FAIL, results.single { it.id == 14 }.status, "assertion 14 passed: $detail")
        assertTrue(
            detail!!.contains("class VehicleFormStateHolder.setName defaults value: String = \"\""),
            "Expected the hidden holder's default to be reported, got: $detail",
        )
    }

    /**
     * Round 8, finding A. `PROPERTY` enumerated its modifiers, so a public property carrying
     * `abstract`, `inline`, `expect`, `actual` or `external` never reached assertions 34 and 35.
     */
    @Test
    fun anUnrecognisedPropertyModifierDoesNotHideAnExportedMember() {
        listOf(
            "abstract val extra: String",
            "inline val extra: String get() = \"\"",
            "actual val extra: String = \"\"",
            "external val extra: String",
        ).forEach { declaration ->
            val results = mutate(SWIFT, "    fun close() {", "    $declaration\n\n    fun close() {")
            val detail = results.single { it.id == 35 }.detail
            assertEquals(
                AssertionResult.Status.FAIL,
                results.single { it.id == 35 }.status,
                "assertion 35 passed for `$declaration`: $detail",
            )
            assertTrue(
                detail!!.contains("val extra: String is declared but absent from §20.10"),
                "Expected `$declaration` to be reported, got: $detail",
            )
        }
    }

    /**
     * Round 8, finding C. `bodyOf` located a declaration with `indexOf`, which matches a name
     * prefix, so a class whose name extends a holder's name shadowed the real holder's body.
     */
    @Test
    fun aClassWhoseNameExtendsAHolderNameDoesNotShadowTheRealHolder() {
        val mutated = real.sources.getValue(SESSION)
            .replace("fun dismissAnonymousReminder()", "fun dismissAnonymousReminder(force: Boolean = false)")
            .replace(
                "class SessionStateHolder internal constructor(",
                "class SessionStateHolderShim {\n    fun clean() = Unit\n}\n\nclass SessionStateHolder internal constructor(",
            )
        val results = SwiftSurfaceContract(real.copy(sources = real.sources + (SESSION to mutated))).validate()
        val detail = results.single { it.id == 14 }.detail
        assertEquals(AssertionResult.Status.FAIL, results.single { it.id == 14 }.status, "assertion 14 passed: $detail")
        assertTrue(
            detail!!.contains("class SessionStateHolder.dismissAnonymousReminder defaults force: Boolean = false"),
            "Expected the shadowed holder's default to be reported, got: $detail",
        )
    }

    /**
     * Round 8, finding D. A property whose accessor sits on the declaration line produced the
     * signature `val isClosed: Boolean get()`, which no `§20.10` spelling can ever equal. The
     * member is inserted at the same position on both sides, because order is compared too.
     */
    @Test
    fun anAccessorOnTheDeclarationLineIsNotPartOfThePropertyType() {
        val source = real.sources.getValue(SWIFT)
            .replace("    fun close() {", "    val isClosed: Boolean get() = closed\n\n    fun close() {")
        val contract = real.contract.replace(
            "    fun syncStateHolder(): SyncStateHolder\n    fun close()",
            "    fun syncStateHolder(): SyncStateHolder\n    val isClosed: Boolean\n    fun close()",
        )
        val results = SwiftSurfaceContract(
            real.copy(sources = real.sources + (SWIFT to source), contract = contract),
        ).validate()
        assertAllPass(results)
    }

    /**
     * Round 8, finding E. `propertyMembers` tracked brace depth only, so the constructor `val`
     * parameters of a nested class were collected as public members of the enclosing declaration.
     */
    @Test
    fun constructorParametersOfANestedClassAreNotMembersOfTheEnclosingDeclaration() {
        assertAllPass(
            mutate(
                SWIFT,
                "    fun close() {",
                "    private data class Key(\n        val vehicleId: String,\n        val entryId: String?,\n    )\n\n    fun close() {",
            ),
        )
    }

    /**
     * Round 8, finding F. A context-parameter clause put a `(` before the keyword, and
     * `TOP_LEVEL_DECLARATION` cannot cross one, so the declaration was skipped with no report.
     */
    @Test
    fun aContextParameterClauseDoesNotHideAWiringDeclaration() {
        listOf(
            "context(scope: CoroutineScope) internal class StrayMapper",
            "context(CoroutineScope) object StrayCache",
        ).forEach(::assertWiringRejected)
        assertWiringAccepted("context(scope: CoroutineScope) private fun stagedLogger(): Logger = noop()")
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
        const val VEHICLE =
            "feature/vehicle/src/commonMain/kotlin/com/ruizurraca/carapp/feature/vehicle/presentation/VehicleStateHolders.kt"
    }
}
