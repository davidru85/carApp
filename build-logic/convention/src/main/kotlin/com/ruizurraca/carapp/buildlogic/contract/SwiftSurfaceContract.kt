package com.ruizurraca.carapp.buildlogic.contract

import com.ruizurraca.carapp.buildlogic.source.KotlinSourceText
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
        listOf(
            exportedFactoriesAreScopeFree(),
            appGraphMembersMatch(),
            swiftAppGraphMembersMatch(),
            hiddenHolderMembersAreDeclared(),
        )

    /**
     * `§18` assertion 36, the `§11.6` rule: a public member of an exported state-holder class that is
     * `@HiddenFromObjC` is still declared in `§20.10`, carrying that annotation.
     *
     * The generated Objective-C header cannot see a hidden member at all, and assertions 34 and 35
     * compare only the two `AppGraph` blocks, so no other assertion can observe this drift. `D-191`
     * places the check here because this is the only site that already reads both the `§20.10` blocks
     * and the Kotlin holder sources.
     *
     * Both directions are compared, plus the two fail-open shapes: a holder that declares hidden
     * members with no block to declare them in, and a contract with no holder block at all, which the
     * comparison could not run on. A code-side defect names its file; a contract-side one does not,
     * because the file is not what is wrong.
     */
    private fun hiddenHolderMembersAreDeclared(): AssertionResult {
        val contractBlocks =
            HOLDER_SOURCES.flatMap { path ->
                stateHolderClasses(inputs.sources[path].orEmpty()).mapNotNull { declaration ->
                    contractHolderBlock(declaration)?.let { declaration to it }
                }
            }
        if (contractBlocks.isEmpty()) {
            return result(
                ASSERTION_HIDDEN_HOLDER_MEMBERS,
                ASSERTION_36,
                listOf("§20.10 declares no state-holder class block, so §11.6 cannot be checked"),
            )
        }
        val blocks = contractBlocks.toMap()

        val problems = mutableListOf<String>()
        HOLDER_SOURCES.forEach { path ->
            val source = inputs.sources[path].orEmpty()
            stateHolderClasses(source).forEach { declaration ->
                // `stateHolderClasses` returns `class Name`; the problem text names the class once.
                val name = declaration.removePrefix("class ")
                val hidden = hiddenMembers(source, declaration)
                val block = blocks[declaration]
                if (block == null) {
                    // Reported rather than skipped: a holder whose hidden members cannot be declared
                    // anywhere is exactly the divergence the rule exists to catch.
                    if (hidden.isNotEmpty()) {
                        problems +=
                            "§20.10 declares no $declaration block, so its @HiddenFromObjC members " +
                            "cannot be declared"
                    }
                    return@forEach
                }
                val contractHidden = hiddenMembers(block, declaration)
                val contractAll = members(block, declaration).map { it.signature }.toSet()

                hidden.forEach { member ->
                    if (contractHidden.none { it.signature == member.signature }) {
                        problems +=
                            if (member.signature in contractAll) {
                                "$path: class $name.${member.hiddenLabel} is @HiddenFromObjC but " +
                                    "§20.10 declares it without the annotation"
                            } else {
                                "$path: class $name.${member.hiddenLabel} is @HiddenFromObjC but " +
                                    "absent from §20.10"
                            }
                    }
                }
                contractHidden.forEach { member ->
                    if (hidden.none { it.signature == member.signature }) {
                        problems +=
                            "class $name.${member.hiddenLabel} is declared in §20.10 with " +
                            "@HiddenFromObjC but is not such a member of the class"
                    }
                }
            }
        }

        return result(ASSERTION_HIDDEN_HOLDER_MEMBERS, ASSERTION_36, problems)
    }

    /** The `class <Name>StateHolder { … }` block of `docs/CONTRACTS.md §20.10`, or `null`. */
    private fun contractHolderBlock(declaration: String): String? =
        if (declarationIndex(inputs.contract, declaration) < 0) null else contractBlock(declaration)

    /**
     * The public `@HiddenFromObjC` members of one declaration, in declaration order.
     *
     * The annotation sits in the gap between the previous member and this one, so it is read from
     * there rather than from the declaration text: `members` sees an annotation-masked view, which is
     * what lets a comment or a string literal naming the annotation be ignored. The gap is cut at its
     * last brace or semicolon so the previous member's body cannot carry this member's annotation.
     * `internal` and `private` members are excluded: they never reach Swift, so hiding them changes
     * nothing the contract describes.
     */
    private fun hiddenMembers(source: String, declaration: String): List<Member> {
        val body = bodyOf(source, declaration)
        val all = members(source, declaration)
        return all.mapIndexedNotNull { index, member ->
            val from = (all.getOrNull(index - 1)?.sourceOffset ?: 0).coerceIn(0, member.sourceOffset)
            val gap = body.substring(from, member.sourceOffset.coerceAtMost(body.length))
            val header = gap.substring(gap.lastIndexOfAny(charArrayOf('{', '}', ';')) + 1)
            member.takeIf { member.isExported && HIDDEN_FROM_OBJC.containsMatchIn(KotlinSourceText.code(header)) }
        }
    }

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
        swiftFactories.filter { it.isExported }.forEach { function ->
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
                holderMembers.filter { it.isExported }.forEach { function ->
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
        if (declarationIndex(inputs.contract, KOTLIN_APP_GRAPH) < 0) {
            return result(
                ASSERTION_APP_GRAPH_MEMBERS,
                ASSERTION_34,
                listOf("§20.10 declares no $KOTLIN_APP_GRAPH block"),
            )
        }
        val contract = contractBlock() ?: return result(
            ASSERTION_APP_GRAPH_MEMBERS,
            ASSERTION_34,
            listOf("Unbalanced braces in the $KOTLIN_APP_GRAPH block of §20.10"),
        )
        val contractMembers = members(contract, KOTLIN_APP_GRAPH).map { it.signature }
        val declaredMembers = members(inputs.sources.getValue(APP_GRAPH), KOTLIN_APP_GRAPH).map { it.signature }

        val problems = mutableListOf<String>()
        when {
            contractMembers.isEmpty() && declaredMembers.isEmpty() ->
                problems += "the AppGraph members could not be parsed on both sides"
            contractMembers.isEmpty() -> problems += "no AppGraph members were parsed from §20.10"
            declaredMembers.isEmpty() -> problems += "no AppGraph members were parsed from the interface"
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
        if (declarationIndex(inputs.contract, SWIFT_APP_GRAPH_DECLARATION) < 0) {
            return result(
                ASSERTION_SWIFT_APP_GRAPH_MEMBERS,
                ASSERTION_35,
                listOf("§20.10 declares no $SWIFT_APP_GRAPH_DECLARATION block"),
            )
        }
        val contract = contractBlock(SWIFT_APP_GRAPH_DECLARATION) ?: return result(
            ASSERTION_SWIFT_APP_GRAPH_MEMBERS,
            ASSERTION_35,
            listOf("Unbalanced braces in the $SWIFT_APP_GRAPH_DECLARATION block of §20.10"),
        )
        val contractMembers = members(contract, SWIFT_APP_GRAPH_DECLARATION).map { it.signature }
        val declaredMembers =
            members(inputs.sources.getValue(SWIFT_APP_GRAPH), SWIFT_APP_GRAPH_DECLARATION)
                .filter { it.isExported }
                .map { it.signature }

        val problems = mutableListOf<String>()
        when {
            contractMembers.isEmpty() && declaredMembers.isEmpty() ->
                problems += "the SwiftAppGraph members could not be parsed on both sides"
            contractMembers.isEmpty() -> problems += "no SwiftAppGraph members were parsed from §20.10"
            declaredMembers.isEmpty() -> problems += "no SwiftAppGraph members were parsed from the class"
        }
        (declaredMembers - contractMembers.toSet()).forEach { problems += "$it is declared but absent from §20.10" }
        (contractMembers - declaredMembers.toSet()).forEach { problems += "$it is declared in §20.10 but absent from the class" }
        if (problems.isEmpty() && contractMembers != declaredMembers) {
            problems += "§20.10 declares $contractMembers, the class declares $declaredMembers"
        }

        return result(ASSERTION_SWIFT_APP_GRAPH_MEMBERS, ASSERTION_35, problems)
    }

    /** The `<declaration> { … }` block of `docs/CONTRACTS.md §20.10`, braces included. */
    private fun contractBlock(declaration: String = KOTLIN_APP_GRAPH): String? {
        val start = declarationIndex(inputs.contract, declaration)
        if (start < 0) return null
        val source = inputs.contract.substring(start)
        val opening = bodyBrace(source, 0)
        val closing = matchingBrace(source, opening)
        if (opening < 0 || closing <= opening) return null
        return source.substring(0, closing + 1)
    }

    /**
     * The members of one declaration, in declaration order.
     *
     * Functions and properties are parsed by two scanners, so the combined list is sorted by source
     * offset: `docs/CONTRACTS.md` and `D-180` make the order part of the surface definition, and
     * concatenating one kind before the other normalized an interleaved contract and a grouped
     * implementation to the same list.
     */
    private fun members(source: String, declaration: String): List<Member> {
        val body = bodyOf(source, declaration)
        return (functionMembers(body) + propertyMembers(body)).sortedBy(Member::sourceOffset)
    }

    /**
     * The `fun` declarations of one body. The modifiers are read from the declaration header, the
     * parameters from the balanced parenthesis pair, and the return type from the `:` that follows
     * it — which is what `§20.10` compares, because a factory returning another holder is a
     * different surface even when its name and parameters are unchanged.
     */
    private fun functionMembers(body: String): List<Member> {
        val code = KotlinSourceText.declarations(body)
        val depths = KotlinSourceText.braceDepths(code)
        return FUN.findAll(code)
            .filter { depths[it.range.first] == 0 }
            .mapNotNull { match ->
                val parameters = balancedParameters(body, match.range.last) ?: return@mapNotNull null
                // The header carries every modifier of this declaration and nothing else: it is the
                // text after the last `{` and the last newline before the `fun` keyword, on
                // annotation-masked code, so an annotation argument spelling `suspend` cannot
                // classify a blocking member.
                val header = headerBefore(code, match.range.first)
                Member(
                    kind = MemberKind.FUNCTION,
                    name = match.groupValues[1].removeSurrounding("`"),
                    parameters = splitTopLevel(parameters.text).mapNotNull(::parameter),
                    returnType = returnTypeAfter(body, parameters.closingIndex),
                    visibility = visibilityOf(header),
                    isSuspend = SUSPEND.containsMatchIn(header),
                    sourceOffset = match.range.first,
                )
            }.toList()
    }

    /**
     * The `val`/`var` declarations that are members of the body rather than local variables. A
     * member sits at brace depth zero inside [body]; a local variable inside a function or a lambda
     * is nested and is skipped. A property without an explicit declared type is not part of the
     * compared surface: `§20.10` declares the type, and inferring one textually could disagree.
     */
    private fun propertyMembers(body: String): List<Member> {
        val result = mutableListOf<Member>()
        var braceDepth = 0
        // Parentheses matter as well as braces: the constructor parameters of a nested class sit at
        // brace depth zero, so their `val`s were collected as members of the enclosing declaration.
        var parenthesisDepth = 0
        // The absolute offset of the current line inside `body`. A line index alone cannot order a
        // property against a function: both producers must report a position in the same space.
        var lineOffset = 0
        KotlinSourceText.declarations(body).lineSequence().forEach { line ->
            val declaration = if (braceDepth == 0 && parenthesisDepth == 0) PROPERTY.find(line) else null
            if (declaration != null) {
                // The keyword offset, not the match start: `PROPERTY` begins with `\s*`, so the
                // match starts at column zero and the modifiers sit between it and the keyword.
                val keywordOffset = declaration.groups[1]?.range?.first ?: 0
                result += Member(
                    kind = MemberKind.PROPERTY,
                    name = declaration.groupValues[2].removeSurrounding("`"),
                    parameters = emptyList(),
                    returnType = declaredPropertyType(declaration.groupValues[3]),
                    visibility = visibilityOf(line.substring(0, keywordOffset)),
                    // `val` and `var` are different members: a `var` is write access, which `§11.6`
                    // does not expose, so the keyword is part of the signature.
                    keyword = declaration.groupValues[1],
                    sourceOffset = lineOffset + keywordOffset,
                )
            }
            braceDepth += line.count { it == '{' } - line.count { it == '}' }
            parenthesisDepth += line.count { it == '(' } - line.count { it == ')' }
            lineOffset += line.length + 1
        }
        return result
    }

    /**
     * The declared type of a property, with an accessor or a delegate written on the declaration
     * line removed. `val isClosed: Boolean get() = closed` produced the type `Boolean get()`, which
     * no `§20.10` spelling can equal, so the property could never be declared in the contract.
     */
    private fun declaredPropertyType(captured: String): String =
        captured.trim()
            .substringBefore(" get(")
            .substringBefore(" set(")
            .substringBefore(" by ")
            .trim()

    /** The header text that carries a declaration's modifiers, up to the declaration itself. */
    private fun headerBefore(body: String, offset: Int): String =
        body.substring(0, offset).substringAfterLast('{').substringAfterLast('\n')

    /**
     * The declared return type of the `fun` whose parameter list closes at [closingIndex], or `null`
     * when the declaration carries none.
     *
     * The search is bounded to this declaration's own tail. An unbounded `indexOf(':')` runs past
     * `fun close()` into the next member and reports its colon as this one's return type, which
     * produced signs like `close(): String` and hid every real drift behind it.
     */
    private fun returnTypeAfter(body: String, closingIndex: Int): String? {
        val rest = body.substring(closingIndex + 1)
        val bound = listOf(rest.indexOf('{'), rest.indexOf('='), rest.indexOf('\n'), nextFunctionOffset(rest))
            .filter { it >= 0 }
            .minOrNull() ?: rest.length
        val window = rest.substring(0, bound)
        if (!window.trimStart().startsWith(':')) return null
        return window.trimStart().removePrefix(":").trim().replace(WHITESPACE, " ").ifEmpty { null }
    }

    /** The offset of the next `fun` declaration inside [rest], or `-1` when there is none. */
    private fun nextFunctionOffset(rest: String): Int = FUN.find(rest)?.range?.first ?: -1

    /** The visibility a declaration's own header carries, defaulting to `public`. */
    private fun visibilityOf(header: String): MemberVisibility =
        when {
            PRIVATE.containsMatchIn(header) -> MemberVisibility.PRIVATE
            INTERNAL.containsMatchIn(header) -> MemberVisibility.INTERNAL
            PROTECTED.containsMatchIn(header) -> MemberVisibility.PROTECTED
            else -> MemberVisibility.PUBLIC
        }

    /**
     * The parameter list that opens at or after [from], with nested parentheses balanced. The
     * closing index is returned so the return type can be read from the same scan.
     *
     * A plain `[^)]*` capture would stop inside a function-typed parameter such as
     * `callback: (Int) -> Unit`, which would then hide any default that followed it.
     */
    private fun balancedParameters(source: String, from: Int): ParsedParameters? {
        val code = KotlinSourceText.code(source)
        val opening = code.indexOf('(', from)
        if (opening < 0) return null
        var depth = 0
        for (index in opening until source.length) {
            when (code[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return ParsedParameters(source.substring(opening + 1, index), index)
                }
            }
        }
        return null
    }

    /**
     * The index at which [declaration] appears in [text] as a whole declaration. `indexOf` matched a
     * name prefix, so `class SessionStateHolderShim` shadowed `class SessionStateHolder` and the
     * wrong body was parsed.
     */
    private fun declarationIndex(text: String, declaration: String): Int =
        Regex(Regex.escape(declaration) + """\b""").find(text)?.range?.first ?: -1

    /** The braced body of one declaration, with the declaration header and its KDoc excluded. */
    private fun bodyOf(source: String, declaration: String): String {
        val start = declarationIndex(KotlinSourceText.code(source), declaration)
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
        val code = KotlinSourceText.code(source)
        var parenthesisDepth = 0
        for (index in from until source.length) {
            when (code[index]) {
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
        val code = KotlinSourceText.code(source)
        var depth = 0
        for (index in opening until source.length) {
            when (code[index]) {
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
        KotlinSourceText.code(parameters).forEachIndexed { index, character ->
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
        STATE_HOLDER.findAll(KotlinSourceText.declarations(source)).map { "class ${it.groupValues[1]}" }.toList()

    private fun result(id: Int, name: String, problems: List<String>): AssertionResult =
        if (problems.isEmpty()) {
            AssertionResult(id, name, AssertionResult.Status.PASS)
        } else {
            AssertionResult(id, name, AssertionResult.Status.FAIL, problems.joinToString("; "))
        }

    /** Whether a member is a function or a property. `§20.10` declares both. */
    private enum class MemberKind {
        FUNCTION,
        PROPERTY,
    }

    /** The visibility keyword a declaration carries, defaulting to `public` when it carries none. */
    private enum class MemberVisibility {
        PUBLIC,
        INTERNAL,
        PROTECTED,
        PRIVATE,
    }

    /** The parameter source text and the index of the `)` that closes it. */
    private data class ParsedParameters(
        val text: String,
        val closingIndex: Int,
    )

    /**
     * One member of a declaration.
     *
     * The signature carries the kind, the name, the parameter shapes and the declared type, because
     * that is what `§20.10` defines. A changed return type or property type is a different surface
     * even when the name and the parameters are unchanged, and a `var` is not a `val`.
     *
     * The parameters keep their inline default value in [Parameter.shape], because that shape is
     * what `§20.10` and the interface are compared on, and a default is a real divergence.
     */
    private data class Member(
        val kind: MemberKind,
        val name: String,
        val parameters: List<Parameter>,
        val returnType: String?,
        val visibility: MemberVisibility,
        /**
         * `suspend` is part of the member's call contract on both surfaces and is invisible to the
         * generated Objective-C header on the `@HiddenFromObjC` Kotlin-facing one, so it is
         * compared. Properties never carry it and keep the default.
         */
        val isSuspend: Boolean = false,
        val keyword: String? = null,
        /**
         * Where the declaration starts inside the parsed body. Used only to preserve source order
         * when the two producers are combined, so it is deliberately excluded from [signature]: the
         * comparison stays on kind, `suspend`, name, parameters, declared type and defaults.
         * Visibility is a filter rather than part of the signature — an unexported member never
         * reaches the comparison on the Swift-facing surface.
         */
        val sourceOffset: Int = 0,
    ) {
        /**
         * `§11.6` exports the public surface only. An `internal` or `protected` member never
         * reaches Swift, so comparing it would report a divergence the header cannot show either.
         */
        val isExported: Boolean get() = visibility == MemberVisibility.PUBLIC

        /** `name(a: A, b: B? = default)`, `name(): Return`, or `val name: Type`. */
        val signature: String
            get() =
                when (kind) {
                    MemberKind.FUNCTION ->
                        buildString {
                            if (isSuspend) append("suspend ")
                            append(name)
                            append("(")
                            append(parameters.joinToString { it.shape })
                            append(")")
                            returnType?.let {
                                append(": ")
                                append(it)
                            }
                        }
                    MemberKind.PROPERTY -> "$keyword $name: $returnType"
                }

        /** `§11.6` constrains the declared type, not the parameter name. */
        val scopeParameter: String? get() = parameters.firstOrNull { it.isCoroutineScope }?.shape

        /**
         * How a problem names this member. The full [signature] is used when it reads unambiguously,
         * and a property is named by its bare name because `§20.10` writes only the name in the
         * problem text a reader has to act on.
         */
        val hiddenLabel: String get() = if (kind == MemberKind.PROPERTY) name else signature

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

        /**
         * The declared type is `CoroutineScope`, nullable or not. Matching the identifier `scope`
         * instead let `syncStateHolder(coroutineScope: CoroutineScope)` pass assertion 14 while
         * `§11.6` forbids a `CoroutineScope` on the Swift-facing surface by type.
         */
        val isCoroutineScope: Boolean get() = type.removeSuffix("?").trim() == COROUTINE_SCOPE
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
        const val COROUTINE_SCOPE = "CoroutineScope"

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
        const val ASSERTION_HIDDEN_HOLDER_MEMBERS = 36
        const val ASSERTION_14 =
            "Kotlin-facing factories take a scope, Swift-facing ones do not, and no exported " +
                "state-holder function has a Kotlin default argument"
        const val ASSERTION_34 =
            "the Kotlin-facing AppGraph of §20.10 declares exactly the members of the real interface"
        const val ASSERTION_35 =
            "the Swift-facing SwiftAppGraph of §20.10 declares exactly the exported members of the real class"
        const val ASSERTION_36 =
            "every public @HiddenFromObjC member of an exported state-holder class is declared in §20.10 " +
                "carrying the annotation"
        /**
         * The `AppGraph` members that are deliberately neither a state-holder factory nor a scope
         * owner. `awaitClosed` joins them because the hidden Kotlin-facing interface declares it and
         * `§20.10` documents it; every other member is still rejected when it is neither.
         */
        val NON_HOLDER_MEMBERS = setOf("close", "syncController", "awaitClosed")
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
        val FUN = Regex("""\bfun\s*(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?(`[^`\r\n]+`|\w+)\s*\(""")
        val SUSPEND = Regex("""\bsuspend\b""")
        val PRIVATE = Regex("""\bprivate\b""")
        val INTERNAL = Regex("""\binternal\b""")
        val PROTECTED = Regex("""\bprotected\b""")

        /**
         * A `val`/`var` member: group 1 is the declaration keyword, group 2 the name and group 3
         * the declared type. The leading `\s*` is required — a class member is indented, and an
         * anchored pattern without it silently matched nothing. A property with no explicit type is
         * not matched at all, because `§20.10` declares the type and inferring one could disagree.
         */
        val PROPERTY = Regex("""^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|internal|private|protected|override|open|final|abstract|lateinit|const|expect|actual|external|inline)\s+)*(val|var)\s+(`[^`\r\n]+`|\w+)\s*:\s*([^=]+)""")
        val WHITESPACE = Regex("""\s+""")
        val SYNC_CONTROLLER = Regex("""\bSyncController\b""")

        /**
         * The hiding annotation `§11.6` names. It is matched on the annotation-masked code view, so
         * a comment or a string literal spelling it is not read as a declaration.
         */
        val HIDDEN_FROM_OBJC = Regex("""\bHiddenFromObjC\b""")

        /**
         * A `class <Name>StateHolder` declaration anywhere in the lexically masked source. The
         * modifiers are deliberately not enumerated: the previous pattern listed seven of them and
         * dropped a holder carrying any other word, and the per-source no-parsed-class guard could
         * not bound that, because it fires only when a source yields no holder at all and each of
         * the three `HOLDER_SOURCES` declares two. Comments and string literals are already masked
         * by `KotlinSourceText.declarations`, so prose naming a holder is not matched.
         */
        val STATE_HOLDER = Regex("""\bclass\s+(\w+StateHolder)\b""")
    }
}
