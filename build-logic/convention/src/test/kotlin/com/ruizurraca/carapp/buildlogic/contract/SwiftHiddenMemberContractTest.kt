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
                holders = withHiddenMember(
                    "val cached: StateFlow<Boolean> = MutableStateFlow(false)",
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
                holders = withHiddenMember("fun observeRetries(): Flow<Unit> = emptyFlow()"),
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
                    withHiddenMember(
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
            "class FuelEntryFormStateHolder.isLoading is declared in §20.10 with @HiddenFromObjC " +
                "but is not such a member of the class",
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
            results(contract = "class NothingStateHolder {\n}\n"),
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
        assertPasses(results(holders = withHiddenMember("internal fun observeInternal(): Flow<Unit> = emptyFlow()")))
    }

    /** A `private` helper is not part of the surface either, annotated or not. */
    @Test
    fun aPrivateHiddenMemberIsOutsideTheRule() {
        assertPasses(results(holders = withHiddenMember("private fun observePrivate(): Flow<Unit> = emptyFlow()")))
    }

    /** Comments and string literals naming the annotation must not be read as declarations. */
    @Test
    fun theAnnotationInsideACommentOrStringIsNotADeclaration() {
        assertPasses(
            results(
                holders =
                    withHiddenMember(
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

    /** The real fuel holder source with one more hidden member added to the first holder class. */
    private fun withHiddenMember(vararg declarations: String): Map<String, String> {
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
