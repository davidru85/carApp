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
 * Assertion 35 applies the same member comparison to the Swift-facing `class SwiftAppGraph` block of
 * `§20.10`, which the generated header cannot keep honest because it is regenerated with the change
 * that alters the class.
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

        listOf(KOTLIN_FACTORIES_TAKE_SCOPE, APP_GRAPH_MEMBERS, SWIFT_APP_GRAPH_MEMBERS).forEach { id ->
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

    /**
     * `§11.6` constrains the declared *type*: the Swift-facing graph exposes state-holder factories
     * without `CoroutineScope`. Matching the identifier `scope` let a renamed parameter pass
     * assertions 14, 34 and 35 together once `§20.10` was edited in the same change.
     */
    @Test
    fun aSwiftFacingMemberTakingAScopeUnderAnotherNameIsRejected() {
        assertFails(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            "SwiftAppGraph.reviewProbe takes coroutineScope: CoroutineScope",
            results(
                swiftAppGraph = swiftSource(
                    "fun vehicleListStateHolder(): VehicleListStateHolder",
                    "fun reviewProbe(coroutineScope: CoroutineScope): Int = 0",
                ),
            ),
        )
    }

    /** A parameter named `scope` that is not a `CoroutineScope` is not the scope `§20.10` requires. */
    @Test
    fun aKotlinFacingFactoryWhoseScopeParameterIsNotACoroutineScopeIsRejected() {
        assertFails(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            "AppGraph.vehicleListStateHolder does not take a scope",
            results(
                contractMembers = listOf("vehicleListStateHolder(scope: Any): VehicleListStateHolder", "close()"),
                interfaceMembers = listOf("vehicleListStateHolder(scope: Any): VehicleListStateHolder", "close()"),
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
     * A function-typed parameter must not swallow the parameters after it. The `>` of `->` closes
     * nothing, and treating it as a closing delimiter merged the whole list into one parameter and
     * reported the default under the wrong name.
     */
    @Test
    fun aDefaultAfterAFunctionTypedParameterIsReportedUnderItsOwnName() {
        assertFails(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            "$SESSION_HOLDERS: class SessionStateHolder.observe defaults retries: Int = 1",
            results(
                holders = validHolders() + (
                    SESSION_HOLDERS to
                        "class SessionStateHolder {\n" +
                        "    fun observe(callback: (Int) -> Unit, retries: Int = 1) {}\n" +
                        "}\n"
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

    /**
     * A primary-constructor parameter with a lambda default puts a `{` before the class body. Taking
     * the first brace closed that lambda instead, parsed an empty body, and removed every member of
     * the real `SessionStateHolder` from assertion 14 with nothing reporting it.
     */
    @Test
    fun aHolderWithALambdaDefaultInItsConstructorStillHasItsMembersChecked() {
        assertFails(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            "$SESSION_HOLDERS: class SessionStateHolder.observe defaults retries: Int = 1",
            results(
                holders = validHolders() + (
                    SESSION_HOLDERS to
                        "class SessionStateHolder internal constructor(\n" +
                        "    private val onLocalStartAccepted: () -> Unit = {},\n" +
                        ") {\n" +
                        "    fun observe(retries: Int = 1) {}\n" +
                        "}\n"
                    ),
            ),
        )
    }

    /**
     * A recognised holder class whose body yields no member is the second silent path out of
     * assertion 14, and the per-source guard cannot see it because the source does declare classes.
     * A body whose only declaration is an untyped property yields no member: the property parser
     * requires an explicit type, so the class is reported rather than passing with zero coverage.
     */
    @Test
    fun aHolderClassWithNoParsedMemberIsReported() {
        assertFails(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            "$SESSION_HOLDERS: class SessionStateHolder declares no parsed member",
            results(
                holders = validHolders() + (
                    SESSION_HOLDERS to "class SessionStateHolder {\n    val state = 0\n}\n"
                    ),
            ),
        )
    }

    @Test
    fun anUnparsedKotlinFacingInterfaceIsReportedByAssertion14() {
        assertFails(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            "no members were parsed from the Kotlin-facing AppGraph",
            results(interfaceMembers = emptyList()),
        )
    }

    @Test
    fun anUnparsedSwiftFacingClassIsReportedByAssertion14() {
        assertFails(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            "no members were parsed from the Swift-facing SwiftAppGraph",
            results(swiftAppGraph = swiftSource()),
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
            "sessionStateHolder(scope: CoroutineScope = CoroutineScope(Job())): SessionStateHolder is declared but " +
                "absent from §20.10; sessionStateHolder(scope: CoroutineScope): SessionStateHolder is declared in " +
                "§20.10 but absent from the interface",
            SwiftSurfaceContract(real.withSource(APP_GRAPH, mutated)).validate(),
        )
    }

    @Test
    fun aMemberDeclaredInTheContractButAbsentFromTheInterfaceIsRejected() {
        assertFails(
            APP_GRAPH_MEMBERS,
            "syncStateHolder(scope: CoroutineScope): SyncStateHolder is declared in §20.10 but absent from the interface",
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
            "syncStateHolder(scope: CoroutineScope): SyncStateHolder is declared but absent from §20.10",
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
            "§20.10 declares [close(), vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder], " +
                "the interface declares [vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder, close()]",
            results(contractMembers = listOf("close()", "vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder")),
        )
    }

    /**
     * Declaration order is part of the surface definition and it spans both member kinds. The
     * comparison concatenated every function before every property, so a contract that interleaves
     * them and an implementation that groups them were normalized to the same list and the
     * assertion passed without seeing the ordering violation.
     */
    @Test
    fun anInterleavedFunctionAndPropertyOrderIsRejectedOnTheKotlinFacingSurface() {
        assertFails(
            APP_GRAPH_MEMBERS,
            "§20.10 declares [vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder, " +
                "val extra: String, close()], the interface declares " +
                "[vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder, close(), val extra: String]",
            SwiftSurfaceContract(
                real.withSource(
                    APP_GRAPH,
                    rawBlock(
                        "interface AppGraph",
                        listOf(
                            "fun vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder",
                            "fun close()",
                            "val extra: String",
                        ),
                    ),
                ).copy(
                    contract = rawBlock(
                        "interface AppGraph",
                        listOf(
                            "fun vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder",
                            "val extra: String",
                            "fun close()",
                        ),
                    ),
                ),
            ).validate(),
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

    @Test
    fun aContractWithNoKotlinFacingBlockIsReported() {
        assertFails(
            APP_GRAPH_MEMBERS,
            "§20.10 declares no interface AppGraph block",
            SwiftSurfaceContract(
                real.copy(contract = block(SWIFT_APP_GRAPH_DECLARATION, DEFAULT_SWIFT_MEMBERS)),
            ).validate(),
        )
    }

    @Test
    fun aContractSideThatParsesToNothingIsNamedRatherThanBlamedOnBothSides() {
        assertFails(
            APP_GRAPH_MEMBERS,
            "no AppGraph members were parsed from §20.10; " +
                "vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder is declared but absent from §20.10; " +
                "close() is declared but absent from §20.10",
            results(contractMembers = emptyList()),
        )
    }

    @Test
    fun anInterfaceSideThatParsesToNothingIsNamedRatherThanBlamedOnBothSides() {
        assertFails(
            APP_GRAPH_MEMBERS,
            "no AppGraph members were parsed from the interface; " +
                "vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder is declared in §20.10 but absent from the interface; " +
                "close() is declared in §20.10 but absent from the interface",
            results(interfaceMembers = emptyList()),
        )
    }

    // --- Assertion 35 ------------------------------------------------------------------------

    @Test
    fun aSwiftFacingMemberAbsentFromTheContractIsRejected() {
        assertFails(
            SWIFT_APP_GRAPH_MEMBERS,
            "vehicleListStateHolder(): VehicleListStateHolder is declared but absent from §20.10",
            swiftResults(contractMembers = listOf("close()")),
        )
    }

    @Test
    fun aSwiftFacingMemberDeclaredOnlyInTheContractIsRejected() {
        assertFails(
            SWIFT_APP_GRAPH_MEMBERS,
            "vehicleListStateHolder(): VehicleListStateHolder is declared in §20.10 but absent from the class",
            swiftResults(classMembers = listOf("close()")),
        )
    }

    @Test
    fun anOutOfOrderSwiftFacingMemberListIsRejected() {
        assertFails(
            SWIFT_APP_GRAPH_MEMBERS,
            "§20.10 declares [close(), vehicleListStateHolder(): VehicleListStateHolder], " +
                "the class declares [vehicleListStateHolder(): VehicleListStateHolder, close()]",
            swiftResults(contractMembers = listOf("close()", "vehicleListStateHolder(): VehicleListStateHolder")),
        )
    }

    /** The same interleaved-ordering gap on the Swift-facing surface. */
    @Test
    fun anInterleavedFunctionAndPropertyOrderIsRejectedOnTheSwiftFacingSurface() {
        assertFails(
            SWIFT_APP_GRAPH_MEMBERS,
            "§20.10 declares [vehicleListStateHolder(): VehicleListStateHolder, val extra: String, close()], " +
                "the class declares [vehicleListStateHolder(): VehicleListStateHolder, close(), val extra: String]",
            swiftResults(
                contractMembers = listOf(
                    "vehicleListStateHolder(): VehicleListStateHolder",
                    "val extra: String",
                    "close()",
                ),
                classSource = rawBlock(
                    SWIFT_APP_GRAPH_DECLARATION,
                    listOf(
                        "fun vehicleListStateHolder(): VehicleListStateHolder",
                        "fun close()",
                        "val extra: String",
                    ),
                ),
            ),
        )
    }

    @Test
    fun bothSwiftFacingSidesFailingToParseIsReported() {
        assertFails(
            SWIFT_APP_GRAPH_MEMBERS,
            "the SwiftAppGraph members could not be parsed on both sides",
            swiftResults(contractMembers = emptyList(), classMembers = emptyList()),
        )
    }

    @Test
    fun aSwiftFacingContractSideThatParsesToNothingIsNamedRatherThanBlamedOnBothSides() {
        assertFails(
            SWIFT_APP_GRAPH_MEMBERS,
            "no SwiftAppGraph members were parsed from §20.10; " +
                "vehicleListStateHolder(): VehicleListStateHolder is declared but absent from §20.10; " +
                "close() is declared but absent from §20.10",
            swiftResults(contractMembers = emptyList()),
        )
    }

    @Test
    fun aSwiftFacingClassSideThatParsesToNothingIsNamedRatherThanBlamedOnBothSides() {
        assertFails(
            SWIFT_APP_GRAPH_MEMBERS,
            "no SwiftAppGraph members were parsed from the class; " +
                "vehicleListStateHolder(): VehicleListStateHolder is declared in §20.10 but absent from the class; " +
                "close() is declared in §20.10 but absent from the class",
            swiftResults(classMembers = emptyList()),
        )
    }

    @Test
    fun aContractWithNoSwiftFacingBlockIsReported() {
        assertFails(
            SWIFT_APP_GRAPH_MEMBERS,
            "§20.10 declares no class SwiftAppGraph block",
            results(),
        )
    }

    /** A `private` helper of the facade never reaches Swift, so `§20.10` MUST NOT declare it. */
    @Test
    fun aPrivateSwiftFacingHelperIsNotComparedAgainstTheContract() {
        assertPasses(
            SWIFT_APP_GRAPH_MEMBERS,
            swiftResults(
                classSource = swiftSource(
                    "fun vehicleListStateHolder(): VehicleListStateHolder",
                    "fun close()",
                    "private fun reviewProbe(scope: CoroutineScope): Int = 0",
                ),
            ),
        )
    }

    // --- Return type, property and visibility drift ------------------------------------------

    /**
     * The member comparison compared `name(parameters)` and nothing else, so a changed return type
     * left both assertions at `PASS`. `§11.6` and `§20.10` define the surface by its full
     * signatures: a factory that returns another holder is a different surface.
     */
    @Test
    fun aKotlinFacingReturnTypeChangeIsRejected() {
        val declared = real.sources.getValue(APP_GRAPH)
        val mutated = declared.replace(
            "fun vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder",
            "fun vehicleListStateHolder(scope: CoroutineScope): SessionStateHolder",
        )
        assertTrue(mutated != declared, "the fixture did not mutate the real AppGraph source")

        assertFails(
            APP_GRAPH_MEMBERS,
            "vehicleListStateHolder(scope: CoroutineScope): SessionStateHolder is declared but absent from §20.10; " +
                "vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder is declared in §20.10 but absent from the interface",
            SwiftSurfaceContract(real.withSource(APP_GRAPH, mutated)).validate(),
        )
    }

    @Test
    fun aSwiftFacingReturnTypeChangeIsRejected() {
        val declared = real.sources.getValue(SWIFT_APP_GRAPH)
        val mutated = declared.replace(
            "fun vehicleListStateHolder(): VehicleListStateHolder",
            "fun vehicleListStateHolder(): SessionStateHolder",
        )
        assertTrue(mutated != declared, "the fixture did not mutate the real SwiftAppGraph source")

        assertFails(
            SWIFT_APP_GRAPH_MEMBERS,
            "vehicleListStateHolder(): SessionStateHolder is declared but absent from §20.10; " +
                "vehicleListStateHolder(): VehicleListStateHolder is declared in §20.10 but absent from the class",
            SwiftSurfaceContract(real.withSource(SWIFT_APP_GRAPH, mutated)).validate(),
        )
    }

    /** A public property is part of the exported surface, so `§20.10` must declare it. */
    @Test
    fun aKotlinFacingPropertyOnlyInTheContractIsRejected() {
        assertFails(
            APP_GRAPH_MEMBERS,
            "val extra: String is declared in §20.10 but absent from the interface",
            SwiftSurfaceContract(
                real.withSource(APP_GRAPH, rawBlock("interface AppGraph", CORE_MEMBERS)).copy(
                    contract = rawBlock("interface AppGraph", CORE_MEMBERS + "val extra: String"),
                ),
            ).validate(),
        )
    }

    @Test
    fun aKotlinFacingPropertyOnlyInTheInterfaceIsRejected() {
        assertFails(
            APP_GRAPH_MEMBERS,
            "val extra: String is declared but absent from §20.10",
            SwiftSurfaceContract(
                real.withSource(APP_GRAPH, rawBlock("interface AppGraph", CORE_MEMBERS + "val extra: String")).copy(
                    contract = rawBlock("interface AppGraph", CORE_MEMBERS),
                ),
            ).validate(),
        )
    }

    @Test
    fun aSwiftFacingPropertyOnlyInTheContractIsRejected() {
        assertFails(
            SWIFT_APP_GRAPH_MEMBERS,
            "val extra: String is declared in §20.10 but absent from the class",
            SwiftSurfaceContract(
                real.withSource(
                    SWIFT_APP_GRAPH,
                    rawBlock(SWIFT_APP_GRAPH_DECLARATION, CORE_SWIFT_MEMBERS),
                ).copy(
                    contract = rawBlock(SWIFT_APP_GRAPH_DECLARATION, CORE_SWIFT_MEMBERS + "val extra: String"),
                ),
            ).validate(),
        )
    }

    @Test
    fun aSwiftFacingPropertyOnlyInTheClassIsRejected() {
        assertFails(
            SWIFT_APP_GRAPH_MEMBERS,
            "val extra: String is declared but absent from §20.10",
            SwiftSurfaceContract(
                real.withSource(
                    SWIFT_APP_GRAPH,
                    rawBlock(SWIFT_APP_GRAPH_DECLARATION, CORE_SWIFT_MEMBERS + "val extra: String"),
                ).copy(
                    contract = rawBlock(SWIFT_APP_GRAPH_DECLARATION, CORE_SWIFT_MEMBERS),
                ),
            ).validate(),
        )
    }

    /**
     * A `var` is a different member from a `val` of the same name and type: it is write access, and
     * `§11.6` exposes no mutable state holder property.
     */
    @Test
    fun aPropertyKindChangeIsRejected() {
        assertFails(
            APP_GRAPH_MEMBERS,
            "var extra: String is declared but absent from §20.10; " +
                "val extra: String is declared in §20.10 but absent from the interface",
            SwiftSurfaceContract(
                real.withSource(APP_GRAPH, rawBlock("interface AppGraph", CORE_MEMBERS + "var extra: String")).copy(
                    contract = rawBlock("interface AppGraph", CORE_MEMBERS + "val extra: String"),
                ),
            ).validate(),
        )
    }

    /** `internal` never reaches Swift, so it is not part of the compared surface. */
    @Test
    fun anInternalSwiftFacingHelperIsNotComparedAgainstTheContract() {
        assertPasses(
            SWIFT_APP_GRAPH_MEMBERS,
            swiftResults(
                classSource = swiftSource(
                    "fun vehicleListStateHolder(): VehicleListStateHolder",
                    "fun close()",
                    "internal fun reviewProbe(scope: CoroutineScope): Int = 0",
                ),
            ),
        )
    }

    @Test
    fun aProtectedSwiftFacingHelperIsNotComparedAgainstTheContract() {
        assertPasses(
            SWIFT_APP_GRAPH_MEMBERS,
            swiftResults(
                classSource = swiftSource(
                    "fun vehicleListStateHolder(): VehicleListStateHolder",
                    "fun close()",
                    "protected fun reviewProbe(scope: CoroutineScope): Int = 0",
                ),
            ),
        )
    }

    /** Neither does an `internal` or `protected` helper carry a checked default argument. */
    @Test
    fun anInternalSwiftFacingHelperWithAScopeAndADefaultIsAccepted() {
        assertPasses(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            results(
                swiftAppGraph = swiftSource(
                    "fun vehicleListStateHolder(): VehicleListStateHolder",
                    "internal fun reviewProbe(scope: CoroutineScope, retries: Int = 1): Int = retries",
                ),
            ),
        )
    }

    @Test
    fun aProtectedSwiftFacingHelperWithAScopeAndADefaultIsAccepted() {
        assertPasses(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            results(
                swiftAppGraph = swiftSource(
                    "fun vehicleListStateHolder(): VehicleListStateHolder",
                    "protected fun reviewProbe(scope: CoroutineScope, retries: Int = 1): Int = retries",
                ),
            ),
        )
    }

    /** An exported state-holder member that is `internal` is not part of the Swift surface. */
    @Test
    fun anInternalStateHolderFunctionWithADefaultIsAccepted() {
        assertPasses(
            KOTLIN_FACTORIES_TAKE_SCOPE,
            results(
                holders = validHolders() + (
                    SESSION_HOLDERS to
                        "class SessionStateHolder {\n" +
                        "    fun observe() {}\n" +
                        "    internal fun reviewProbe(retries: Int = 1) {}\n" +
                        "}\n"
                    ),
            ),
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

    /**
     * Runs the assertions over a fabricated `§20.10` Swift block and a fabricated class, derived
     * from the real inputs so the untouched paths stay the repository's.
     *
     * The fabricated contract keeps the Kotlin-facing block, so assertion 34 reports a real member
     * comparison beside 35 instead of the missing-block result.
     */
    private fun swiftResults(
        contractMembers: List<String> = DEFAULT_SWIFT_MEMBERS,
        classMembers: List<String> = DEFAULT_SWIFT_MEMBERS,
        classSource: String = block(SWIFT_APP_GRAPH_DECLARATION, classMembers),
    ): List<AssertionResult> = SwiftSurfaceContract(
        real.copy(
            contract = block(SWIFT_APP_GRAPH_DECLARATION, contractMembers) + block("interface AppGraph", DEFAULT_MEMBERS),
            sources = real.sources + (SWIFT_APP_GRAPH to classSource),
        ),
    ).validate()

    private fun block(declaration: String, members: List<String>): String =
        (listOf("$declaration {") + members.map { "    fun $it" } + listOf("}"))
            .joinToString("\n", postfix = "\n")

    /**
     * A fabricated `§20.10` block whose members are written verbatim, so a fixture can express a
     * property, a `var` or a visibility modifier that `block(...)` always writes as a `fun`.
     */
    private fun rawBlock(declaration: String, members: List<String>): String =
        (listOf("$declaration {") + members.map { "    $it" } + listOf("}"))
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
        const val SWIFT_APP_GRAPH_MEMBERS = 35

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
        val DEFAULT_SWIFT_MEMBERS = listOf(
            "vehicleListStateHolder(): VehicleListStateHolder",
            "close()",
        )

        /**
         * [DEFAULT_MEMBERS] written the way a raw block needs them. Derived rather than repeated,
         * so a property fixture cannot drift from the surface declaration it sits beside.
         */
        val CORE_MEMBERS = DEFAULT_MEMBERS.map { "fun $it" }

        /** [DEFAULT_SWIFT_MEMBERS] written the way a raw block needs them. */
        val CORE_SWIFT_MEMBERS = DEFAULT_SWIFT_MEMBERS.map { "fun $it" }
        const val SWIFT_APP_GRAPH_DECLARATION = "class SwiftAppGraph"
    }
}
