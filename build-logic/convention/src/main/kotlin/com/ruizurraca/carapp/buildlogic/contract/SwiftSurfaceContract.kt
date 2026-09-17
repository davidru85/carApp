package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/**
 * Contract for the two `AppGraph` surfaces of `docs/CONTRACTS.md §20.10` (assertions 14 and 34).
 *
 * Both guard a surface the Objective-C golden header cannot see:
 *
 * - Kotlin default arguments do not appear in the generated header at all, so a default added to an
 *   exported member would change no diff and still break the Swift call site. Assertion 14 is
 *   declared by `§18` and was never implemented, so nothing enforced this shape.
 * - The Kotlin-facing `AppGraph` is hidden from Objective-C export, so a member present in code but
 *   absent from the contract changes no header either. That divergence was live when `E3-08`
 *   started: `syncStateHolder(scope)` existed in the interface and not in `§20.10`. Assertion 34 is
 *   the guard that keeps the two from drifting again.
 */
internal class SwiftSurfaceContract private constructor(
    private val inputs: Inputs,
) {
    constructor(repoRoot: File) : this(
        Inputs(
            contract = repoRoot.resolve(CONTRACTS).readText(),
            sources = SOURCES.associateWith { repoRoot.resolve(it).readText() },
        ),
    )

    constructor(inputs: Inputs, fixture: Boolean = true) : this(inputs) {
        check(fixture) { "The fixture marker prevents constructor signature ambiguity" }
    }

    fun validate(): List<AssertionResult> = listOf(exportedFactoriesAreScopeFree(), appGraphMembersMatch())

    /**
     * `§18` assertion 14: Kotlin-facing `AppGraph` factories take `scope: CoroutineScope`,
     * Swift-facing `SwiftAppGraph` factories do not, and no exported state-holder function has a
     * Kotlin default argument.
     *
     * The exported factories are the methods of the state-holder classes. The `createXStateHolder`
     * helpers beside them are `@HiddenFromObjC` and are therefore Kotlin-side implementation
     * details, which is why they may keep their default arguments.
     */
    private fun exportedFactoriesAreScopeFree(): AssertionResult {
        val problems = mutableListOf<String>()

        val kotlinFactories = members(inputs.sources.getValue(APP_GRAPH), KOTLIN_APP_GRAPH)
        if (kotlinFactories.isEmpty()) {
            problems += "no members were parsed from the Kotlin-facing AppGraph"
        }
        kotlinFactories.forEach { function ->
            when {
                function.name in NON_HOLDER_MEMBERS -> Unit
                !function.name.endsWith(STATE_HOLDER_SUFFIX) ->
                    problems += "AppGraph.${function.name} is neither a state-holder factory nor a non-holder member"
                function.parameters.none { it.startsWith("scope:") } ->
                    problems += "AppGraph.${function.name} does not take a scope"
            }
        }

        val swiftFactories = members(inputs.sources.getValue(SWIFT_APP_GRAPH), SWIFT_APP_GRAPH_DECLARATION)
        if (swiftFactories.isEmpty()) {
            problems += "no members were parsed from the Swift-facing SwiftAppGraph"
        }
        swiftFactories.forEach { function ->
            function.parameters.firstOrNull { it.startsWith("scope") }?.let {
                problems += "SwiftAppGraph.${function.name} takes $it"
            }
            function.defaulted.takeIf { it.isNotEmpty() }?.let {
                problems += "SwiftAppGraph.${function.name} defaults ${it.joinToString()}"
            }
        }
        // `§11.6`: the Swift-facing graph exposes a sync state holder instead of `SyncController`.
        // The generated header also forbids it, but this catches the drift at the source, before a
        // header regeneration would have to be committed to observe it.
        if (SYNC_CONTROLLER.containsMatchIn(bodyOf(inputs.sources.getValue(SWIFT_APP_GRAPH), SWIFT_APP_GRAPH_DECLARATION))) {
            problems += "SwiftAppGraph references SyncController"
        }

        exportedStateHolderMembers().forEach { (path, declaration, function) ->
            if (function.defaulted.isNotEmpty()) {
                problems += "$path: $declaration.${function.name} defaults ${function.defaulted.joinToString()}"
            }
        }

        return result(ASSERTION_KOTLIN_FACTORIES_TAKE_SCOPE, ASSERTION_14, problems)
    }

    /**
     * `§18` assertion 34: the Kotlin-facing `AppGraph` block of `§20.10` and the real interface
     * declare exactly the same members, in the same order, with the same parameter shapes. Order is
     * part of the comparison because a reviewer reads the contract as the surface definition.
     */
    private fun appGraphMembersMatch(): AssertionResult {
        val contractMembers = members(contractBlock(), KOTLIN_APP_GRAPH).map { it.signature }
        val declaredMembers = members(inputs.sources.getValue(APP_GRAPH), KOTLIN_APP_GRAPH).map { it.signature }

        val problems = mutableListOf<String>()
        if (contractMembers.isEmpty() || declaredMembers.isEmpty()) {
            problems += "the AppGraph members could not be parsed on both sides"
        }
        (declaredMembers - contractMembers.toSet()).forEach { problems += "$it is declared but absent from §20.10" }
        (contractMembers - declaredMembers.toSet()).forEach { problems += "$it is declared in §20.10 but absent from the interface" }
        if (problems.isEmpty() && contractMembers != declaredMembers) {
            problems += "§20.10 declares $contractMembers, the interface declares $declaredMembers"
        }

        return result(ASSERTION_APP_GRAPH_MEMBERS, ASSERTION_34, problems)
    }

    /** The `interface AppGraph { … }` block of `docs/CONTRACTS.md §20.10`, braces included. */
    private fun contractBlock(): String {
        val start = inputs.contract.indexOf(KOTLIN_APP_GRAPH)
        check(start >= 0) { "Could not find '$KOTLIN_APP_GRAPH' in docs/CONTRACTS.md" }
        val opening = inputs.contract.indexOf('{', start)
        val closing = matchingBrace(inputs.contract, opening)
        check(closing > opening) { "Unbalanced braces in the $KOTLIN_APP_GRAPH block of docs/CONTRACTS.md" }
        return inputs.contract.substring(start, closing + 1)
    }

    /** The public members of one declaration, normalised to `name(parameter: Type)`. */
    private fun members(source: String, declaration: String): List<Member> {
        val body = bodyOf(source, declaration)
        return FUN.findAll(body).mapNotNull { match ->
            val parameters = balancedParameters(body, match.range.last) ?: return@mapNotNull null
            val modifiers = body.substring(0, match.range.first).substringAfterLast('\n')
            Member(
                name = match.groupValues[1],
                rawParameters = splitTopLevel(parameters).map { it.trim() }.filter { it.isNotEmpty() },
                isPrivate = PRIVATE.containsMatchIn(modifiers),
            )
        }.toList()
    }

    /**
     * The parameter list that opens at or after [from], with nested parentheses balanced.
     *
     * A plain `[^)]*` capture would stop inside a function-typed parameter such as
     * `callback: (Int) -> Unit`, which would then hide any default that followed it.
     */
    private fun balancedParameters(source: String, from: Int): String? {
        val opening = source.indexOf('(', from)
        if (opening < 0) return null
        var depth = 0
        for (index in opening until source.length) {
            when (source[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return source.substring(opening + 1, index)
                }
            }
        }
        return null
    }

    /** The braced body of one declaration, with the declaration header and its KDoc excluded. */
    private fun bodyOf(source: String, declaration: String): String {
        val start = source.indexOf(declaration)
        if (start < 0) return ""
        return braceBody(source, source.indexOf('{', start)).orEmpty()
    }

    private fun braceBody(source: String, opening: Int): String? {
        val closing = matchingBrace(source, opening)
        return if (closing < 0) null else source.substring(opening + 1, closing)
    }

    /** The index of the brace that closes the one at [opening], or `-1` when it never closes. */
    private fun matchingBrace(source: String, opening: Int): Int {
        if (opening < 0) return -1
        var depth = 0
        for (index in opening until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    private fun splitTopLevel(parameters: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var depth = 0
        parameters.forEachIndexed { index, character ->
            when (character) {
                '(', '[', '{', '<' -> depth += 1
                ')', ']', '}', '>' -> depth -= 1
                ',' -> if (depth == 0) {
                    result += parameters.substring(start, index)
                    start = index + 1
                }
            }
        }
        result += parameters.substring(start)
        return result
    }

    /** Every `<Name>StateHolder` class declared in the sources that host the exported holders. */
    private fun exportedStateHolderMembers(): List<Triple<String, String, Member>> =
        HOLDER_SOURCES.flatMap { path ->
            val source = inputs.sources[path].orEmpty()
            stateHolderClasses(source).flatMap { holder ->
                members(source, holder.declaration)
                    .filterNot { it.isPrivate }
                    .map { Triple(path, holder.declaration, it) }
            }
        }

    private fun result(id: Int, name: String, problems: List<String>): AssertionResult =
        if (problems.isEmpty()) {
            AssertionResult(id, name, AssertionResult.Status.PASS)
        } else {
            AssertionResult(id, name, AssertionResult.Status.FAIL, problems.joinToString("; "))
        }

    private data class StateHolderClass(val declaration: String)

    /**
     * One member of a declaration.
     *
     * [rawParameters] keeps the source text so a default argument remains visible; [parameters] is
     * the normalised `name: Type` form the surface comparison uses, because a changed parameter
     * name, type or order is a contract difference and a default value is not.
     */
    private data class Member(
        val name: String,
        val rawParameters: List<String>,
        val isPrivate: Boolean,
    ) {
        val parameters: List<String> = rawParameters.mapNotNull(::normaliseParameter)

        /** `name(a: A, b: B?)`. */
        val signature: String get() = "$name(${parameters.joinToString()})"

        /** The parameters carrying an inline default value, which only Kotlin-side members can. */
        val defaulted: List<String> get() = rawParameters.filter { it.contains(DEFAULT_PARAMETER) }
    }

    internal data class Inputs(
        val contract: String,
        val sources: Map<String, String>,
    )

    private companion object {
        const val CONTRACTS = "docs/CONTRACTS.md"
        const val APP_GRAPH = "shared/src/commonMain/kotlin/com/ruizurraca/carapp/AppGraph.kt"
        const val SWIFT_APP_GRAPH = "shared/src/commonMain/kotlin/com/ruizurraca/carapp/SwiftAppGraph.kt"
        const val KOTLIN_APP_GRAPH = "interface AppGraph"
        const val SWIFT_APP_GRAPH_DECLARATION = "class SwiftAppGraph"
        const val STATE_HOLDER_SUFFIX = "StateHolder"
        const val DEFAULT_ARGUMENT = "="

        /** `scope: CoroutineScope` for a declaration, or `null` for a parameter that is not one. */
        private fun normaliseParameter(parameter: String): String? {
            val trimmed = parameter.trim()
            if (trimmed.isEmpty()) return null
            val name = trimmed.substringBefore(':').trim().removePrefix("val ").removePrefix("var ")
            val type = trimmed.substringAfter(':', "").substringBefore(DEFAULT_ARGUMENT).trim()
            return if (type.isEmpty()) null else "$name: $type"
        }
        /** A parameter list entry carrying a default value, of any shape. */
        const val DEFAULT_PARAMETER = "="
        const val ASSERTION_KOTLIN_FACTORIES_TAKE_SCOPE = 14
        const val ASSERTION_APP_GRAPH_MEMBERS = 34
        const val ASSERTION_14 =
            "Kotlin-facing factories take a scope, Swift-facing ones do not, and no exported " +
                "state-holder function has a Kotlin default argument"
        const val ASSERTION_34 =
            "the Kotlin-facing AppGraph of §20.10 declares exactly the members of the real interface"
        val NON_HOLDER_MEMBERS = setOf("close", "syncController")
        val HOLDER_SOURCES = listOf(
            "shared/src/commonMain/kotlin/com/ruizurraca/carapp/StateHolders.kt",
            "feature/vehicle/src/commonMain/kotlin/com/ruizurraca/carapp/feature/vehicle/presentation/VehicleStateHolders.kt",
            "feature/fuel/src/commonMain/kotlin/com/ruizurraca/carapp/feature/fuel/presentation/FuelEntryStateHolders.kt",
        )
        val SOURCES = listOf(APP_GRAPH, SWIFT_APP_GRAPH) + HOLDER_SOURCES
        val FUN = Regex("""fun\s+(?:\w+\s+)*(\w+)\s*\(""")
        val PRIVATE = Regex("""\bprivate\b""")
        val SYNC_CONTROLLER = Regex("""\bSyncController\b""")
        val STATE_HOLDER = Regex("""^(?:internal )?class \w+StateHolder\b""", RegexOption.MULTILINE)
    }

    /** Every `<Name>StateHolder` class declaration of one source file. */
    private fun stateHolderClasses(source: String): List<StateHolderClass> =
        STATE_HOLDER.findAll(source)
            .map { match -> StateHolderClass(match.groupValues[0].trimEnd().removePrefix("internal ")) }
            .toList()
}
