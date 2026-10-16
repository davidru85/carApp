package com.ruizurraca.carapp.buildlogic.contract

import com.ruizurraca.carapp.buildlogic.source.KotlinSourceText
import java.io.File

/**
 * `docs/CONTRACTS.md §18` assertion 43 (`{D-B1-D11}`): a `UiState` carries no user-facing text.
 *
 * `§14` forbids display copy in `UiState`, but a `String` field is the one place copy could enter
 * shared presentation state unnoticed: an identifier, a currency code and a vehicle name are all
 * `String` too. The allowlist is the `§14.2` table itself, so adding a text field to a `UiState` is a
 * contract change under the gated path, reviewed with its kind, never a silent code edit.
 */
internal class UiStateTextContract internal constructor(
    private val inputs: Inputs,
) {
    constructor(repoRoot: File) : this(Inputs.from(repoRoot))

    internal data class Inputs(
        val contract: String,
        val sources: Map<String, String>,
    ) {
        companion object {
            fun from(repoRoot: File): Inputs =
                Inputs(
                    contract = repoRoot.resolve(HostLocalizationInputs.CONTRACTS).readText(),
                    sources = UiMessageCodeInventory.producerSources(repoRoot),
                )
        }
    }

    fun validate(): AssertionResult {
        val problems = mutableListOf<String>()
        val allowlist = allowlist()
        if (allowlist.isEmpty()) problems += "docs/CONTRACTS.md has no '$ALLOWLIST_HEADING' table"
        allowlist.filterValues { kind -> kind !in KINDS }.toSortedMap().forEach { (name, kind) ->
            problems += "§14.2 classifies $name as '$kind', which is not one of $KINDS"
        }
        val classes = inputs.sources.values.sumOf { text -> UI_STATE_CLASS.findAll(KotlinSourceText.code(text)).count() }
        if (classes == 0) problems += "no UiState class parsed from the production Kotlin sources"
        val fields = inputs.sources.flatMap { (path, text) -> textFields(path, text) }
        fields.filterNot { field -> field.name in allowlist }.forEach { field ->
            problems += "${field.path}: ${field.name}: ${field.type} carries text but is not in the §14.2 allowlist"
        }
        val declared = fields.map(TextField::name).toSet()
        allowlist.keys.filterNot(declared::contains).sorted().forEach { name ->
            problems += "§14.2 allowlists $name, which is not a text field of a UiState class"
        }
        return if (problems.isEmpty()) {
            AssertionResult(ID, NAME, AssertionResult.Status.PASS, "${fields.size} text fields allowlisted")
        } else {
            AssertionResult(ID, NAME, AssertionResult.Status.FAIL, problems.joinToString("; "))
        }
    }

    /** `Owner.field -> kind` rows of the `§14.2` table; the section ends at the next heading. */
    private fun allowlist(): Map<String, String> {
        val section =
            inputs.contract.substringAfter(ALLOWLIST_HEADING, "").substringBefore("\n### ").substringBefore("\n## ")
        return ALLOWLIST_ROW.findAll(section).associate { it.groupValues[1] to it.groupValues[2].trim() }
    }

    /**
     * The text-typed constructor properties of every `UiState` class of one source. Comments and string
     * literals are masked first, so KDoc that mentions `String` and a default value are not read as types.
     */
    private fun textFields(path: String, text: String): List<TextField> {
        val masked = KotlinSourceText.code(text)
        return UI_STATE_CLASS.findAll(masked)
            .flatMap { match ->
                val owner = match.groupValues[1]
                val opening = match.range.last
                val closing = closingParenthesis(masked, opening)
                if (closing < 0) {
                    emptyList()
                } else {
                    splitTopLevel(masked.substring(opening + 1, closing)).mapNotNull { parameter ->
                        val property = PROPERTY.matchEntire(parameter.replace(WHITESPACE, " ").trim()) ?: return@mapNotNull null
                        val type = property.groupValues[2].trim()
                        if (TEXT_TYPE.containsMatchIn(type)) TextField(path, "$owner.${property.groupValues[1]}", type) else null
                    }
                }
            }.toList()
    }

    private fun closingParenthesis(masked: String, opening: Int): Int {
        var depth = 0
        for (index in opening until masked.length) {
            when (masked[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    /** Splits a parameter list on top-level commas; the `>` of a `->` arrow is not a closing bracket. */
    private fun splitTopLevel(parameters: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        parameters.forEachIndexed { index, character ->
            when {
                character in "([{<" -> depth += 1
                character in ")]}" -> depth -= 1
                character == '>' && parameters.getOrNull(index - 1) != '-' -> depth -= 1
                character == ',' && depth == 0 -> {
                    result += parameters.substring(start, index)
                    start = index + 1
                }
            }
        }
        parameters.substring(start).takeIf(String::isNotBlank)?.let(result::add)
        return result
    }

    private data class TextField(val path: String, val name: String, val type: String)

    companion object {
        const val ID = 43
        const val NAME = "UiState text fields are limited to the §14.2 allowlist"
        const val ALLOWLIST_HEADING = "### 14.2 UiState text-field allowlist"
        val KINDS = listOf("Identifier", "Currency code", "User-entered", "Programmatic code")
        private val ALLOWLIST_ROW = Regex("""(?m)^\|\s*`(\w+\.\w+)`\s*\|\s*([^|\n]+?)\s*\|""")
        private val UI_STATE_CLASS = Regex("""\bdata\s+class\s+(\w+(?:UiState|Ui)|UiMessage)\s*\(""")
        private val PROPERTY =
            Regex(
                """(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|internal|private|protected|override|open)\s+)*(?:val|var)\s+(\w+)\s*:\s*([^=]+?)(?:\s*=.*)?""",
            )
        private val TEXT_TYPE = Regex("""\b(?:String|CharSequence)\b""")
        private val WHITESPACE = Regex("""\s+""")
    }
}
