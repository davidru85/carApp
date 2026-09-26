package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `E3-05` / `docs/CONTRACTS.md §18` assertion 36: the `§11.6` rule that a public member of an
 * exported state-holder class which is `@HiddenFromObjC` is still declared in `§20.10`, carrying that
 * annotation.
 *
 * The generated Objective-C header cannot see a hidden member at all, and assertions 34 and 35
 * compare only the two `AppGraph` blocks, so nothing else in `contractCheck` can observe this drift.
 * `E3-08` declared the two members that exist today by hand and recorded the gap as a deferral;
 * `D-191` places the executable check beside assertions 34 and 35, the only site that already reads
 * both `§20.10` and the Kotlin holder sources.
 *
 * `D-16` requires a failing fixture per rejected shape. Every branch that reports a problem has one
 * below, each asserting the **exact** problem text so a fixture cannot pass by matching a different
 * failure, and the first test runs the real `contractCheck` so the fixtures cannot drift away from
 * the repository they guard.
 */
class SwiftHiddenMemberContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val real = SwiftSurfaceContract(repositoryRoot).inputs

    @Test
    fun contractCheckGuardsTheHiddenHolderSurface() {
        val results = ContractCheck(repositoryRoot, emptyMap()).runAll()

        val result = results.singleOrNull { it.id == HIDDEN_MEMBERS }
        assertNotNull(result, "contract-check assertion $HIDDEN_MEMBERS is not implemented")
        assertEquals(AssertionResult.Status.PASS, result.status, result.detail)
    }

    // --- The code declares a hidden member the contract does not --------------------------------

    /**
     * The defect this assertion exists for. The fixture mutates the **real** source, so it fails if
     * the real holder stops declaring a hidden member the assertion is supposed to see.
     */
    @Test
    fun aHiddenPropertyAbsentFromTheContractIsRejected() {
        assertFails(
            "$FUEL_HOLDERS: class FuelEntryFormStateHolder.cached is @HiddenFromObjC but absent from §20.10",
            results(
                holders = withExtraMembers(
                    "@HiddenFromObjC val cached: StateFlow<Boolean> = MutableStateFlow(false)",
                ),
            ),
        )
    }

    @Test
    fun aHiddenFunctionAbsentFromTheContractIsRejected() {
        assertFails(
            "$FUEL_HOLDERS: class FuelEntryFormStateHolder.observeRetries(): Flow<Unit> is " +
                "@HiddenFromObjC but absent from §20.10",
            results(
                holders = withExtraMembers("@HiddenFromObjC fun observeRetries(): Flow<Unit> = emptyFlow()"),
            ),
        )
    }

    /** The annotation does not have to be the first modifier to hide the member. */
    @Test
    fun aHiddenMemberWithOtherModifiersIsRecognised() {
        assertFails(
            "$FUEL_HOLDERS: class FuelEntryFormStateHolder.cached is @HiddenFromObjC but absent from §20.10",
            results(
                holders =
                    withExtraMembers(
                        "public @HiddenFromObjC val cached: StateFlow<Boolean> = MutableStateFlow(false)",
                    ),
            ),
        )
    }

    // --- The contract declares a hidden member the code does not --------------------------------

    /**
     * `§11.6` says the declaration carries the annotation, so a `§20.10` block that lists a member
     * without it describes a member the code does not have: the member would be exported, and the
     * contract would be wrong about the surface rather than merely silent.
     */
    @Test
    fun aContractMemberMissingTheAnnotationIsRejected() {
        assertFails(
            "$FUEL_HOLDERS: class FuelEntryFormStateHolder.isLoading is @HiddenFromObjC but " +
                "§20.10 declares it without the annotation",
            results(
                contract =
                    real.contract.replace(
                        "    @HiddenFromObjC val isLoading: StateFlow<Boolean>",
                        "    val isLoading: StateFlow<Boolean>",
                    ),
            ),
        )
    }

    /** A contract-declared hidden member the class does not implement at all. */
    @Test
    fun aContractHiddenMemberAbsentFromTheClassIsRejected() {
        assertFails(
            "class FuelEntryFormStateHolder.observeSaveCompletions(): Flow<Unit> is declared in §20.10 " +
                "with @HiddenFromObjC but is not such a member of the class",
            results(
                holders =
                    mapOf(
                        FUEL_HOLDERS to
                            real.sources
                                .getValue(FUEL_HOLDERS)
                                .replace(
                                    "@HiddenFromObjC\n    fun observeSaveCompletions(): Flow<Unit> = " +
                                        "saveCompletions.receiveAsFlow()",
                                    "fun observeSaveCompletions(): Flow<Unit> = saveCompletions.receiveAsFlow()",
                                ),
                    ),
            ),
        )
    }

    /**
     * A contract-only holder class: a block whose class exists nowhere in production. Its hidden
     * members can never be a real hidden member of the class, so assertion 36 MUST reject them. The
     * fixture also pins that contract-side class discovery does not depend on production class names.
     */
    @Test
    fun aContractHiddenMemberInAContractOnlyHolderClassIsRejected() {
        val contractOnlyHolder =
            """
            class GhostStateHolder {
                @HiddenFromObjC fun observeGhost(): Flow<Unit>
            }

            """.trimIndent()

        assertFails(
            "class GhostStateHolder.observeGhost(): Flow<Unit> is declared in §20.10 " +
                "with @HiddenFromObjC but is not such a member of the class",
            results(
                contract =
                    real.contract.replace(
                        "class SyncStateHolder {",
                        contractOnlyHolder + "\n\nclass SyncStateHolder {",
                    ),
            ),
        )
    }

    // --- Fail-open guards -----------------------------------------------------------------------

    /**
     * A holder whose hidden members exist in code but whose `§20.10` block is missing entirely would
     * otherwise drop out of the comparison silently, which is the fail-open shape the `E3-08` review
     * rounds kept finding. It is reported instead.
     */
    @Test
    fun aHolderWithHiddenMembersAndNoContractBlockIsReported() {
        assertFails(
            "§20.10 declares no class FuelEntryFormStateHolder block, so its @HiddenFromObjC members " +
                "cannot be declared",
            results(
                contract =
                    real.contract.replace(
                        "class FuelEntryFormStateHolder {",
                        "class FuelEntryFormStateHolderWasRemoved {",
                    ),
            ),
        )
    }

    /** A contract that declares no holder block at all is a contract the comparison cannot run on. */
    @Test
    fun aContractWithNoHolderBlocksAtAllIsReported() {
        assertFails(
            "§20.10 declares no state-holder class block, so §11.6 cannot be checked",
            results(contract = "class Nothing {\n}\n"),
        )
    }

    // --- Members outside the rule ---------------------------------------------------------------

    /**
     * `§11.6` constrains a member of an exported state-holder **class**. The `@HiddenFromObjC`
     * `createXStateHolder` factories beside the classes are top-level declarations, not members, and
     * the real repository has four of them.
     */
    @Test
    fun theRealTopLevelHiddenFactoriesAreOutsideTheRule() {
        assertPasses(results())
    }

    /** An `internal` helper never reaches Swift, so hiding it changes nothing the contract describes. */
    @Test
    fun anInternalHiddenMemberIsOutsideTheRule() {
        assertPasses(results(holders = withExtraMembers("@HiddenFromObjC internal fun observeInternal(): Flow<Unit> = emptyFlow()")))
    }

    /** A `private` helper is not part of the surface either, annotated or not. */
    @Test
    fun aPrivateHiddenMemberIsOutsideTheRule() {
        assertPasses(results(holders = withExtraMembers("@HiddenFromObjC private fun observePrivate(): Flow<Unit> = emptyFlow()")))
    }

    /** Comments and string literals naming the annotation must not be read as declarations. */
    @Test
    fun theAnnotationInsideACommentOrStringIsNotADeclaration() {
        assertPasses(
            results(
                holders =
                    withExtraMembers(
                        "// @HiddenFromObjC val ghost: StateFlow<Boolean> = MutableStateFlow(false)",
                        "val description: String = \"@HiddenFromObjC fun ghost(): Flow<Unit>\"",
                    ),
            ),
        )
    }

    /** The other two holder sources are covered by the same rule, not only the fuel one. */
    @Test
    fun aHiddenMemberOfAnyHolderSourceIsCovered() {
        assertFails(
            "$SHARED_HOLDERS: class SyncStateHolder.observeInternalStatus(): Flow<Unit> is " +
                "@HiddenFromObjC but absent from §20.10",
            results(
                holders =
                    mapOf(
                        SHARED_HOLDERS to
                            real.sources
                                .getValue(SHARED_HOLDERS)
                                .replace(
                                    "    fun clearMessage() {\n        if (!closed) mutableState.value = " +
                                        "mutableState.value.copy(message = null)",
                                    "    @HiddenFromObjC fun observeInternalStatus(): Flow<Unit> = emptyFlow()\n\n" +
                                        "    fun clearMessage() {\n        if (!closed) mutableState.value = " +
                                        "mutableState.value.copy(message = null)",
                                ),
                    ),
            ),
        )
    }

    // --- The header cut runs on masked code ------------------------------------------------------

    /**
     * The annotation is read from the text between the previous member and this one, cut at its last
     * brace or semicolon. A `}` inside the previous member's string literal once cut that text
     * mid-literal; the fragment then re-lexed as an unterminated string, masked the annotation, and
     * an undeclared hidden member passed.
     */
    @Test
    fun aBraceInsideThePreviousMembersStringDoesNotHideTheAnnotation() {
        assertFails(
            "$FUEL_HOLDERS: class FuelEntryFormStateHolder.cached is @HiddenFromObjC but absent from §20.10",
            results(
                holders =
                    withExtraMembers(
                        "val label: String = \"{}\"",
                        "@HiddenFromObjC val cached: StateFlow<Boolean> = MutableStateFlow(false)",
                    ),
            ),
        )
    }

    /** The same cut on a semicolon inside a string literal. */
    @Test
    fun aSemicolonInsideThePreviousMembersStringDoesNotHideTheAnnotation() {
        assertFails(
            "$FUEL_HOLDERS: class FuelEntryFormStateHolder.cached is @HiddenFromObjC but absent from §20.10",
            results(
                holders =
                    withExtraMembers(
                        "val separator: String = \";\"",
                        "@HiddenFromObjC val cached: StateFlow<Boolean> = MutableStateFlow(false)",
                    ),
            ),
        )
    }

    /** A string template closes with a brace too, and it is the likeliest of the three in a holder. */
    @Test
    fun aStringTemplateInThePreviousMemberDoesNotHideTheAnnotation() {
        assertFails(
            "$FUEL_HOLDERS: class FuelEntryFormStateHolder.observeRetries(): Flow<Unit> is " +
                "@HiddenFromObjC but absent from §20.10",
            results(
                holders =
                    withExtraMembers(
                        "val summary: String get() = \"\${1}\"",
                        "@HiddenFromObjC fun observeRetries(): Flow<Unit> = emptyFlow()",
                    ),
            ),
        )
    }

    // --- A hidden property the member parser does not model ------------------------------------

    /**
     * `§20.10` declares a property with its type, and the member parser does not infer one, so a
     * public hidden property with no explicit type can be neither declared nor compared. It MUST be
     * reported rather than skipped: skipping it let an undeclared hidden member pass assertion 36.
     */
    @Test
    fun anUntypedHiddenPropertyIsRejected() {
        assertFails(
            "$FUEL_HOLDERS: class FuelEntryFormStateHolder.cachedUntyped is @HiddenFromObjC but declares " +
                "no explicit type, so §20.10 cannot declare it",
            results(holders = withExtraMembers("@HiddenFromObjC val cachedUntyped = MutableStateFlow(false)")),
        )
    }

    /** A delegated property with no explicit type is the same shape, and its lambda holds braces. */
    @Test
    fun aDelegatedUntypedHiddenPropertyIsRejected() {
        assertFails(
            "$FUEL_HOLDERS: class FuelEntryFormStateHolder.cachedLazy is @HiddenFromObjC but declares " +
                "no explicit type, so §20.10 cannot declare it",
            results(holders = withExtraMembers("@HiddenFromObjC val cachedLazy by lazy { MutableStateFlow(false) }")),
        )
    }

    /**
     * The annotation belongs to the declaration that follows it. Attributing it to the next parsed
     * member instead reported `clearMessage()` — a member that is not hidden — as the defect.
     */
    @Test
    fun anUntypedHiddenPropertyDoesNotLendItsAnnotationToTheNextMember() {
        assertFails(
            "$SHARED_HOLDERS: class SyncStateHolder.cachedUntyped is @HiddenFromObjC but declares no " +
                "explicit type, so §20.10 cannot declare it",
            results(
                holders =
                    mapOf(
                        SHARED_HOLDERS to
                            real.sources
                                .getValue(SHARED_HOLDERS)
                                .replace(
                                    "    fun clearMessage() {\n        if (!closed) mutableState.value = " +
                                        "mutableState.value.copy(message = null)",
                                    "    @HiddenFromObjC val cachedUntyped = MutableStateFlow(false)\n\n" +
                                        "    fun clearMessage() {\n        if (!closed) mutableState.value = " +
                                        "mutableState.value.copy(message = null)",
                                ),
                    ),
            ),
        )
    }

    /** A `private` untyped hidden property never reaches Swift, so it stays outside the rule. */
    @Test
    fun aPrivateUntypedHiddenPropertyIsOutsideTheRule() {
        assertPasses(results(holders = withExtraMembers("@HiddenFromObjC private val cachedPrivate = MutableStateFlow(false)")))
    }

    private fun assertFails(expected: String, results: List<AssertionResult>) {
        val result =
            assertNotNull(
                results.singleOrNull { it.id == HIDDEN_MEMBERS },
                "assertion $HIDDEN_MEMBERS is missing",
            )
        assertEquals(AssertionResult.Status.FAIL, result.status, "expected a failure, got: ${result.detail}")
        assertEquals(expected, result.detail)
    }

    private fun assertPasses(results: List<AssertionResult>) {
        val result =
            assertNotNull(
                results.singleOrNull { it.id == HIDDEN_MEMBERS },
                "assertion $HIDDEN_MEMBERS is missing",
            )
        assertEquals(AssertionResult.Status.PASS, result.status, result.detail)
    }

    /**
     * Fabricated inputs derived from the real ones, so every untouched path stays the repository's and
     * the fixtures cannot drift from the surface they guard.
     */
    private fun results(
        contract: String = real.contract,
        holders: Map<String, String> = emptyMap(),
    ): List<AssertionResult> =
        SwiftSurfaceContract(
            real.copy(
                contract = contract,
                sources = real.sources + holders,
            ),
        ).validate()

    /** The real fuel holder source with one more verbatim declaration added to its form holder. */
    private fun withExtraMembers(vararg declarations: String): Map<String, String> {
        val source = real.sources.getValue(FUEL_HOLDERS)
        val anchor = "    @HiddenFromObjC\n    fun observeSaveCompletions(): Flow<Unit> = saveCompletions.receiveAsFlow()"
        assertTrue(source.contains(anchor), "the fixture anchor left $FUEL_FORM_HOLDER")
        return mapOf(FUEL_HOLDERS to source.replace(anchor, declarations.joinToString("\n") { "    $it" } + "\n\n" + anchor))
    }

    private companion object {
        const val HIDDEN_MEMBERS = 36
        const val FUEL_FORM_HOLDER = "FuelEntryFormStateHolder"
        const val FUEL_HOLDERS =
            "feature/fuel/src/commonMain/kotlin/com/ruizurraca/carapp/feature/fuel/presentation/FuelEntryStateHolders.kt"
        const val SHARED_HOLDERS = "shared/src/commonMain/kotlin/com/ruizurraca/carapp/StateHolders.kt"
    }
}
