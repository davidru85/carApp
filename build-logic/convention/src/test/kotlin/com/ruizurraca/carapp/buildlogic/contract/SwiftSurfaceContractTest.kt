package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `E3-08`: the two `AppGraph` surfaces of `docs/CONTRACTS.md §20.10` MUST be guarded.
 *
 * Assertion 14 is declared by `docs/CONTRACTS.md §18` and has never been implemented, so nothing
 * enforced the shape of either surface. The generated Objective-C header cannot substitute for it:
 * Kotlin default arguments do not appear in the generated header at all, so a default added to an
 * exported member would change no diff and still break the Swift call site.
 *
 * Assertion 34 covers the one divergence the header cannot see either, because the Kotlin-facing
 * `AppGraph` is hidden from Objective-C export: the interface of `§20.10` and the real interface
 * MUST declare the same members, with the same parameter shapes.
 *
 * `D-16` requires a failing fixture per rejected shape, and the parser behind these assertions is
 * hand-written, so every branch that reports a problem has one below. The fabricated
 * [SwiftSurfaceContract.Inputs] keeps them runnable without mutating five real files. Each fixture
 * asserts the **exact** problem text the branch produces, so a fixture cannot pass by matching a
 * different failure. The first test runs the real `contractCheck` so the fixtures cannot drift away
 * from the repository they guard.
 */
class SwiftSurfaceContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val real = SwiftSurfaceContract(repositoryRoot).inputs

    @Test
    fun contractCheckGuardsTheSwiftFacingSurface() {
        val results = ContractCheck(repositoryRoot, emptyMap()).runAll()

        listOf(KOTLIN_FACTORIES_TAKE_SCOPE, APP_GRAPH_MEMBERS).forEach { id ->
            val result = results.singleOrNull { it.id == id }
            assertNotNull(result, "contract-check assertion $id is not implemented")
            assertEquals(AssertionResult.Status.PASS, result.status, result.detail)
        }
    }

    // --- Assertion 14 ------------------------------------------------------------------------

    @Test
    fun aKotlinFacingFactoryWithoutAScopeIsRejected() {
        assertFails(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            "AppGraph.vehicleListStateHolder does not take a scope",
            results(interfaceMembers = listOf("vehicleListStateHolder(): VehicleListStateHolder", "close()")),
        )
    }

    @Test
    fun aKotlinFacingMemberThatIsNeitherAStateHolderFactoryNorAKnownMemberIsRejected() {
        assertFails(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            "AppGraph.currentVehicleId is neither a state-holder factory nor a non-holder member",
            results(
                contractMembers = listOf("currentVehicleId(scope: CoroutineScope): String", "close()"),
                interfaceMembers = listOf("currentVehicleId(scope: CoroutineScope): String", "close()"),
            ),
        )
    }

    @Test
    fun aSwiftFacingMemberTakingAScopeIsRejected() {
        assertFails(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            "SwiftAppGraph.reviewProbe takes scope: CoroutineScope",
            results(
                swiftAppGraph = swiftSource(
                    "fun vehicleListStateHolder(): VehicleListStateHolder",
                    "fun reviewProbe(scope: CoroutineScope): Int = 0",
                ),
            ),
        )
    }

    @Test
    fun aSwiftFacingMemberWithADefaultArgumentIsRejected() {
        assertFails(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            "SwiftAppGraph.reviewProbe defaults retries: Int = 1",
            results(
                swiftAppGraph = swiftSource(
                    "fun vehicleListStateHolder(): VehicleListStateHolder",
                    "fun reviewProbe(retries: Int = 1): Int = retries",
                ),
            ),
        )
    }

    @Test
    fun aSwiftFacingSyncControllerReferenceIsRejected() {
        assertFails(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            "SwiftAppGraph references SyncController",
            results(
                swiftAppGraph = swiftSource(
                    "fun vehicleListStateHolder(): VehicleListStateHolder",
                    "val controller: SyncController? = null",
                ),
            ),
        )
    }

    /**
     * A private member of `SwiftAppGraph` is not exported, and `§18` assertion 14 constrains
     * exported functions. The real facade's `newScopedHolder` passes today only because its
     * parameter happens to be named `factory`; a private helper taking a scope and a default is
     * legitimate and MUST NOT be reported.
     */
    @Test
    fun aPrivateSwiftFacingHelperTakingAScopeAndDefaultingIsAccepted() {
        assertPasses(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            results(
                swiftAppGraph = swiftSource(
                    "fun vehicleListStateHolder(): VehicleListStateHolder",
                    "private fun reviewProbe(scope: CoroutineScope, retries: Int = 1): Int = retries",
                ),
            ),
        )
    }

    @Test
    fun anExportedStateHolderMethodWithADefaultArgumentIsRejected() {
        assertFails(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            "$SESSION_HOLDERS: class SessionStateHolder.observe defaults retries: Int = 1",
            results(
                holders = validHolders() + (
                    SESSION_HOLDERS to "class SessionStateHolder {\n    fun observe(retries: Int = 1) {}\n}\n"
                    ),
            ),
        )
    }

    /**
     * A holder source that yields no parsed class silently drops out of assertion 14's coverage — a
     * new state holder in a new module, or one whose declaration the classifier cannot read, would
     * never be checked. That is reported rather than passing.
     */
    @Test
    fun aHolderSourceWithNoParsedClassIsReported() {
        assertFails(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            "$SESSION_HOLDERS declares no <Name>StateHolder class",
            results(holders = validHolders() - SESSION_HOLDERS),
        )
    }

    // --- Assertion 34 ------------------------------------------------------------------------

    /**
     * The defect this story was reviewed for: a Kotlin default argument on the Kotlin-facing
     * `AppGraph`. The interface is hidden from Objective-C export and Kotlin defaults never reach
     * the generated header, so the member comparison is the only place it is visible. The fixture
     * mutates the **real** source, so it fails if the real interface stops declaring the shape.
     */
    @Test
    fun aKotlinFacingDefaultArgumentIsRejected() {
        val declared = real.sources.getValue(APP_GRAPH)
        val mutated = declared.replace(
            "fun sessionStateHolder(scope: CoroutineScope): SessionStateHolder",
            "fun sessionStateHolder(scope: CoroutineScope = CoroutineScope(Job())): SessionStateHolder",
        )
        assertTrue(mutated != declared, "the fixture did not mutate the real AppGraph source")

        assertFails(
            APP_GRAPH_MEMBERS,
            "sessionStateHolder(scope: CoroutineScope = CoroutineScope(Job())) is declared but " +
                "absent from §20.10; sessionStateHolder(scope: CoroutineScope) is declared in " +
                "§20.10 but absent from the interface",
            SwiftSurfaceContract(real.withSource(APP_GRAPH, mutated)).validate(),
        )
    }

    @Test
    fun aMemberDeclaredInTheContractButAbsentFromTheInterfaceIsRejected() {
        assertFails(
            APP_GRAPH_MEMBERS,
            "syncStateHolder(scope: CoroutineScope) is declared in §20.10 but absent from the interface",
            results(
                contractMembers = listOf(
                    "vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder",
                    "syncStateHolder(scope: CoroutineScope): SyncStateHolder",
                    "close()",
                ),
            ),
        )
    }

    @Test
    fun aMemberDeclaredInTheInterfaceButAbsentFromTheContractIsRejected() {
        assertFails(
            APP_GRAPH_MEMBERS,
            "syncStateHolder(scope: CoroutineScope) is declared but absent from §20.10",
            results(
                interfaceMembers = listOf(
                    "vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder",
                    "syncStateHolder(scope: CoroutineScope): SyncStateHolder",
                    "close()",
                ),
            ),
        )
    }

    @Test
    fun anOutOfOrderMemberListIsRejected() {
        assertFails(
            APP_GRAPH_MEMBERS,
            "§20.10 declares [close(), vehicleListStateHolder(scope: CoroutineScope)], " +
                "the interface declares [vehicleListStateHolder(scope: CoroutineScope), close()]",
            results(contractMembers = listOf("close()", "vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder")),
        )
    }

    @Test
    fun bothSidesFailingToParseIsReported() {
        assertFails(
            APP_GRAPH_MEMBERS,
            "the AppGraph members could not be parsed on both sides",
            results(contractMembers = emptyList(), interfaceMembers = emptyList()),
        )
    }

    private fun assertFails(assertion: Int, expected: String, results: List<AssertionResult>) {
        val result = assertNotNull(results.singleOrNull { it.id == assertion }, "assertion $assertion is missing")
        assertEquals(AssertionResult.Status.FAIL, result.status, "expected a failure, got: ${result.detail}")
        assertEquals(expected, result.detail)
    }

    private fun assertPasses(assertion: Int, results: List<AssertionResult>) {
        val result = assertNotNull(results.singleOrNull { it.id == assertion }, "assertion $assertion is missing")
        assertEquals(AssertionResult.Status.PASS, result.status, result.detail)
    }

    /**
     * Runs both assertions over fabricated inputs derived from the real ones, so the covered paths
     * and the contract text are the repository's rather than a copy of it.
     */
    private fun results(
        contractMembers: List<String> = DEFAULT_MEMBERS,
        interfaceMembers: List<String> = DEFAULT_MEMBERS,
        swiftAppGraph: String = swiftSource("fun vehicleListStateHolder(): VehicleListStateHolder"),
        holders: Map<String, String> = validHolders(),
    ): List<AssertionResult> = SwiftSurfaceContract(
        real.copy(
            contract = block("interface AppGraph", contractMembers),
            sources = (real.sources - HOLDER_SOURCES.toSet()) + (
                APP_GRAPH to block("interface AppGraph", interfaceMembers)
                ) + (
                SWIFT_APP_GRAPH to swiftAppGraph
                ) + holders,
        ),
    ).validate()

    private fun block(declaration: String, members: List<String>): String =
        (listOf("$declaration {") + members.map { "    fun $it" } + listOf("}"))
            .joinToString("\n", postfix = "\n")

    private fun swiftSource(vararg members: String): String =
        (listOf("class SwiftAppGraph {") + members.map { "    $it" } + listOf("}"))
            .joinToString("\n", postfix = "\n")

    /** One valid `<Name>StateHolder` class per source that hosts exported holders. */
    private fun validHolders(): Map<String, String> = HOLDER_SOURCES.associateWith { path ->
        "class ${path.substringAfterLast('/').removeSuffix(".kt")}StateHolder { fun observe() {} }"
    }

    private fun SwiftSurfaceContract.Inputs.withSource(
        path: String,
        text: String,
    ): SwiftSurfaceContract.Inputs = copy(sources = sources + (path to text))

    private companion object {
        const val KOTLIN_FACTORIES_TAKE_SCOPE = 14
        const val APP_GRAPH_MEMBERS = 34

        const val APP_GRAPH = "shared/src/commonMain/kotlin/com/ruizurraca/carapp/AppGraph.kt"
        const val SWIFT_APP_GRAPH = "shared/src/commonMain/kotlin/com/ruizurraca/carapp/SwiftAppGraph.kt"
        const val SESSION_HOLDERS = "shared/src/commonMain/kotlin/com/ruizurraca/carapp/StateHolders.kt"
        val HOLDER_SOURCES = listOf(
            SESSION_HOLDERS,
            "feature/vehicle/src/commonMain/kotlin/com/ruizurraca/carapp/feature/vehicle/presentation/VehicleStateHolders.kt",
            "feature/fuel/src/commonMain/kotlin/com/ruizurraca/carapp/feature/fuel/presentation/FuelEntryStateHolders.kt",
        )
        val DEFAULT_MEMBERS = listOf(
            "vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder",
            "close()",
        )
    }
}
