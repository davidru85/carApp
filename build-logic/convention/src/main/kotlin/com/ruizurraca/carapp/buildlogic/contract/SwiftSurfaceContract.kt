package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/**
 * Contract for the two `AppGraph` surfaces of `docs/CONTRACTS.md §20.10` (assertions 14, 34 and 35).
 *
 * Both guard a surface the Objective-C golden header cannot see:
 *
 * - Kotlin default arguments do not appear in the generated header at all, so a default added to an
 *   exported member would change no diff and still break the Swift call site. Assertion 14 is
 *   declared by `§18` and was never implemented, so nothing enforced this shape.
 * - The Kotlin-facing `AppGraph` is hidden from Objective-C export, so a member present in code but
 *   absent from the contract changes no header either. That divergence was live when `E3-08`
 *   started: `syncStateHolder(scope)` existed in the interface and not in `§20.10`. Assertion 34 is
 *   the guard that keeps the two from drifting again, and it compares parameter shapes *including*
 *   inline default values, which is the only place a default on the hidden interface is visible.
 *
 * The parser is textual, and its limits are enumerated in ADR-0181.
 */
internal class SwiftSurfaceContract(
    val inputs: Inputs,
) {
    constructor(repoRoot: File) : this(
        Inputs(
            contract = repoRoot.resolve(CONTRACTS).readText(),
            sources = SOURCES.associateWith { repoRoot.resolve(it).readText() },
        ),
    )

    fun validate(): List<AssertionResult> =
        listOf(exportedFactoriesAreScopeFree(), appGraphMembersMatch(), swiftAppGraphMembersMatch())

    /**
     * `§18` assertion 14: Kotlin-facing `AppGraph` factories take `scope: CoroutineScope`,
     * Swift-facing `SwiftAppGraph` factories do not, and no exported state-holder function has a
     * Kotlin default argument.
     *
     * The exported factories are the methods of the state-holder classes. The `createXStateHolder`
     * helpers beside them are `@HiddenFromObjC` and are therefore Kotlin-side implementation
     * details, which is why they may keep their default arguments.
     *
     * Only exported members are constrained. A `private` member of `SwiftAppGraph` never reaches
     * Swift, so reporting one would be a false positive on a legitimate helper. The private
     * filtering is the same for the state-holder classes.
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
                function.scopeParameter == null ->
                    problems += "AppGraph.${function.name} does not take a scope"
            }
            // Reported here as well as by assertion 34: when the same default is added to `§20.10`
            // the two sides agree, so the member comparison cannot see it and this is the only
            // check that still does.
            function.defaultsProblem()?.let { problems += "AppGraph.${function.name} $it" }
        }

        val swiftFactories = members(inputs.sources.getValue(SWIFT_APP_GRAPH), SWIFT_APP_GRAPH_DECLARATION)
        if (swiftFactories.isEmpty()) {
            problems += "no members were parsed from the Swift-facing SwiftAppGraph"
        }
        swiftFactories.filterNot { it.isPrivate }.forEach { function ->
            function.scopeParameter?.let { problems += "SwiftAppGraph.${function.name} takes $it" }
            function.defaultsProblem()?.let { problems += "SwiftAppGraph.${function.name} $it" }
        }
        // `§11.6`: the Swift-facing graph exposes a sync state holder instead of `SyncController`.
        // The generated header also forbids it, but this catches the drift at the source, before a
        // header regeneration would have to be committed to observe it.
        if (SYNC_CONTROLLER.containsMatchIn(bodyOf(inputs.sources.getValue(SWIFT_APP_GRAPH), SWIFT_APP_GRAPH_DECLARATION))) {
            problems += "SwiftAppGraph references SyncController"
        }

        HOLDER_SOURCES.forEach { path ->
            val source = inputs.sources[path].orEmpty()
            val declarations = stateHolderClasses(source)
            // A holder source that yields no class drops out of this assertion silently. That is
            // how a state holder added to a new module would escape it, so it is reported.
            if (declarations.isEmpty()) {
                problems += "$path declares no <Name>StateHolder class"
            }
            declarations.forEach { declaration ->
                val holderMembers = members(source, declaration)
                // A class whose body cannot be parsed yields no member and would pass silently. The
                // per-source guard above cannot see it, because the source does declare classes.
                if (holderMembers.isEmpty()) {
                    problems += "$path: $declaration declares no parsed member"
                }
                holderMembers.filterNot { it.isPrivate }.forEach { function ->
                    function.defaultsProblem()?.let { problems += "$path: $declaration.${function.name} $it" }
                }
            }
        }

        return result(ASSERTION_KOTLIN_FACTORIES_TAKE_SCOPE, ASSERTION_14, problems)
    }

    /**
     * `§18` assertion 34: the Kotlin-facing `AppGraph` block of `§20.10` and the real interface
     * declare exactly the same members, in the same order, with the same parameter shapes. Order is
     * part of the comparison because a reviewer reads the contract as the surface definition.
     *
     * The shape includes an inline default value. `§20.10` declares none, so a default added to the
     * interface is a divergence, and this is the only check that can see one: `AppGraph` is hidden
     * from Objective-C export and defaults never reach the generated header.
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

    /**
     * `§18` assertion 35: the Swift-facing `SwiftAppGraph` block of `§20.10` and the real class
     * declare the same exported members, in the same order, with the same parameter shapes.
     *
     * The generated Objective-C header guards the class against an unintended change, but it is
     * regenerated and committed together with the change that alters it, so it can never report
     * that `§20.10` has gone stale. That is the same drift assertion 34 closes on the Kotlin-facing
     * side. `private` members are excluded: they never reach Swift and `§20.10` does not declare
     * them.
     */
    private fun swiftAppGraphMembersMatch(): AssertionResult {
        if (inputs.contract.indexOf(SWIFT_APP_GRAPH_DECLARATION) < 0) {
            return result(
                ASSERTION_SWIFT_APP_GRAPH_MEMBERS,
                ASSERTION_35,
                listOf("§20.10 declares no $SWIFT_APP_GRAPH_DECLARATION block"),
            )
        }
        val contractMembers =
            members(contractBlock(SWIFT_APP_GRAPH_DECLARATION), SWIFT_APP_GRAPH_DECLARATION).map { it.signature }
        val declaredMembers =
            members(inputs.sources.getValue(SWIFT_APP_GRAPH), SWIFT_APP_GRAPH_DECLARATION)
                .filterNot { it.isPrivate }
                .map { it.signature }

        val problems = mutableListOf<String>()
        if (contractMembers.isEmpty() || declaredMembers.isEmpty()) {
            problems += "the SwiftAppGraph members could not be parsed on both sides"
        }
        (declaredMembers - contractMembers.toSet()).forEach { problems += "$it is declared but absent from §20.10" }
        (contractMembers - declaredMembers.toSet()).forEach { problems += "$it is declared in §20.10 but absent from the class" }
        if (problems.isEmpty() && contractMembers != declaredMembers) {
            problems += "§20.10 declares $contractMembers, the class declares $declaredMembers"
        }

        return result(ASSERTION_SWIFT_APP_GRAPH_MEMBERS, ASSERTION_35, problems)
    }

    /** The `<declaration> { … }` block of `docs/CONTRACTS.md §20.10`, braces included. */
    private fun contractBlock(declaration: String = KOTLIN_APP_GRAPH): String {
        val start = inputs.contract.indexOf(declaration)
        check(start >= 0) { "Could not find '$declaration' in docs/CONTRACTS.md" }
        val opening = bodyBrace(inputs.contract, start)
        val closing = matchingBrace(inputs.contract, opening)
        check(closing > opening) { "Unbalanced braces in the $declaration block of docs/CONTRACTS.md" }
        return inputs.contract.substring(start, closing + 1)
    }

    /** The members of one declaration, with their parameter shapes and their visibility. */
    private fun members(source: String, declaration: String): List<Member> {
        val body = bodyOf(source, declaration)
        return FUN.findAll(body).mapNotNull { match ->
            val parameters = balancedParameters(body, match.range.last) ?: return@mapNotNull null
            val modifiers = body.substring(0, match.range.first).substringAfterLast('\n')
            Member(
                name = match.groupValues[1],
                parameters = splitTopLevel(parameters).mapNotNull(::parameter),
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
        return braceBody(source, bodyBrace(source, start)).orEmpty()
    }

    /**
     * The index of the brace that opens the body of the declaration starting at [from], skipping a
     * primary-constructor parameter list.
     *
     * `class SessionStateHolder internal constructor(… onLocalStartAccepted: () -> Unit = {}, …)`
     * carries a lambda default, so the first `{` after the declaration sits inside the parameter
     * list. [matchingBrace] closes that lambda on the next character, the body parses as empty, and
     * every member of the class leaves assertion 14's coverage with nothing reporting it. Only a
     * brace at parenthesis depth zero opens a declaration body.
     */
    private fun bodyBrace(source: String, from: Int): Int {
        var parenthesisDepth = 0
        for (index in from until source.length) {
            when (source[index]) {
                '(' -> parenthesisDepth += 1
                ')' -> parenthesisDepth -= 1
                '{' -> if (parenthesisDepth == 0) return index
            }
        }
        return -1
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
        var previous = ' '
        parameters.forEachIndexed { index, character ->
            when {
                character == '(' || character == '[' || character == '{' || character == '<' -> depth += 1
                // The `>` of an arrow closes nothing. Decrementing on it drives the depth negative
                // inside `callback: (Int) -> Unit` and hides every comma after it.
                character == '>' && previous == '-' -> Unit
                character == ')' || character == ']' || character == '}' || character == '>' -> depth -= 1
                character == ',' && depth == 0 -> {
                    result += parameters.substring(start, index)
                    start = index + 1
                }
            }
            previous = character
        }
        result += parameters.substring(start)
        return result.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Every `<Name>StateHolder` class declaration of one source file, normalised to `class Name`. */
    private fun stateHolderClasses(source: String): List<String> =
        STATE_HOLDER.findAll(source).map { "class ${it.groupValues[1]}" }.toList()

    private fun result(id: Int, name: String, problems: List<String>): AssertionResult =
        if (problems.isEmpty()) {
            AssertionResult(id, name, AssertionResult.Status.PASS)
        } else {
            AssertionResult(id, name, AssertionResult.Status.FAIL, problems.joinToString("; "))
        }

    /**
     * One member of a declaration.
     *
     * The parameters keep their inline default value in [Parameter.shape], because that shape is
     * what `§20.10` and the interface are compared on, and a default is a real divergence.
     */
    private data class Member(
        val name: String,
        val parameters: List<Parameter>,
        val isPrivate: Boolean,
    ) {
        /** `name(a: A, b: B? = default)`. */
        val signature: String get() = "$name(${parameters.joinToString { it.shape }})"

        val scopeParameter: String? get() = parameters.firstOrNull { it.name == SCOPE }?.shape

        /** `defaults b: B? = default`, or `null` when no parameter carries one. */
        fun defaultsProblem(): String? =
            parameters.filter { it.hasDefault }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = "defaults ", separator = ", ") { it.shape }
    }

    /** One parameter, normalised so a renamed parameter or a changed type is a difference. */
    private data class Parameter(val name: String, val type: String, val default: String?) {
        val shape: String get() = if (default == null) "$name: $type" else "$name: $type = $default"

        val hasDefault: Boolean get() = default != null
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
        const val SCOPE = "scope"

        /** `scope: CoroutineScope = …` for one parameter, or `null` when it is not a parameter. */
        private fun parameter(raw: String): Parameter? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val name = trimmed.substringBefore(':').trim().removePrefix("val ").removePrefix("var ")
            val afterColon = trimmed.substringAfter(':', "")
            val type = afterColon.substringBefore(DEFAULT_SEPARATOR).trim()
            val default = afterColon.substringAfter(DEFAULT_SEPARATOR, "").trim()
            return if (type.isEmpty()) null else Parameter(name, type, default.ifEmpty { null })
        }

        const val DEFAULT_SEPARATOR = "="
        const val ASSERTION_KOTLIN_FACTORIES_TAKE_SCOPE = 14
        const val ASSERTION_APP_GRAPH_MEMBERS = 34
        const val ASSERTION_SWIFT_APP_GRAPH_MEMBERS = 35
        const val ASSERTION_14 =
            "Kotlin-facing factories take a scope, Swift-facing ones do not, and no exported " +
                "state-holder function has a Kotlin default argument"
        const val ASSERTION_34 =
            "the Kotlin-facing AppGraph of §20.10 declares exactly the members of the real interface"
        const val ASSERTION_35 =
            "the Swift-facing SwiftAppGraph of §20.10 declares exactly the exported members of the real class"
        val NON_HOLDER_MEMBERS = setOf("close", "syncController")
        val HOLDER_SOURCES = listOf(
            "shared/src/commonMain/kotlin/com/ruizurraca/carapp/StateHolders.kt",
            "feature/vehicle/src/commonMain/kotlin/com/ruizurraca/carapp/feature/vehicle/presentation/VehicleStateHolders.kt",
            "feature/fuel/src/commonMain/kotlin/com/ruizurraca/carapp/feature/fuel/presentation/FuelEntryStateHolders.kt",
        )
        val SOURCES = listOf(APP_GRAPH, SWIFT_APP_GRAPH) + HOLDER_SOURCES

        /**
         * A function declaration, tolerating leading modifiers, a generic parameter list and an
         * extension receiver: `fun name(`, `fun <T> name(` and `fun Foo.name(` all match. Widened
         * after review found the narrower shape skipped generic and extension declarations and so
         * silently removed them from assertion 14's coverage.
         */
        val FUN = Regex("""\bfun\s*(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?(\w+)\s*\(""")
        val PRIVATE = Regex("""\bprivate\b""")
        val SYNC_CONTROLLER = Regex("""\bSyncController\b""")

        /**
         * A `<Name>StateHolder` class at the start of a line, tolerating the `public`/`internal`/
         * `private`/`abstract`/`open`/`sealed`/`data` modifiers and an annotation on the same line.
         * A declaration whose annotation sits on its own line is still matched, because the regex
         * anchors to the line carrying `class`. Recorded in ADR-0181 under Negative.
         */
        val STATE_HOLDER = Regex(
            """^(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|internal|private|abstract|open|sealed|data)\s+)*class\s+(\w+StateHolder)\b""",
            RegexOption.MULTILINE,
        )
    }
}
