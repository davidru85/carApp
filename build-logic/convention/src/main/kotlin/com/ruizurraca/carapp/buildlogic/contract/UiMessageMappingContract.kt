package com.ruizurraca.carapp.buildlogic.contract

import com.ruizurraca.carapp.buildlogic.source.KotlinSourceText
import java.io.File

/**
 * `docs/CONTRACTS.md §18` assertion 41 (`D-220`): every `UiMessage.code` the shared layer can
 * publish has an explicit, single-code mapping on both hosts and a row in the `§14.1` table, and the
 * three agree on its key.
 *
 * `§6` forbids an `else` branch over `AppError`, but a host receives `UiMessage.code`, a `String`, so no
 * compiler can prove its `when` or `switch` exhaustive and each host keeps a safety net. This assertion
 * is what makes that safety net unreachable for every known code: the code set is parsed from the
 * sources that define it ([UiMessageCodeInventory]) and each mapping must name every code explicitly. A
 * mapped key must exist in both catalogues of its host and carry no format argument, because both hosts
 * render it without arguments; the fallback must be the generic copy; and both host test lists must
 * name every code, so the tests cannot fall behind the table either.
 */
internal class UiMessageMappingContract internal constructor(
    private val inputs: HostLocalizationInputs,
) {
    constructor(repoRoot: File) : this(HostLocalizationInputs.from(repoRoot))

    fun validate(): AssertionResult {
        val problems = mutableListOf<String>()
        val appErrorCodes = UiMessageCodeInventory.appErrorCodes(inputs.appErrorSource)
        val confirmationCodes = UiMessageCodeInventory.confirmationCodes(inputs.appErrorSource)
        if (appErrorCodes.isEmpty()) problems += "no AppError leaf code parsed from ${UiMessageCodeInventory.APP_ERROR_PATH}"
        if (confirmationCodes.isEmpty()) {
            problems += "no Confirmation entry parsed from ${UiMessageCodeInventory.APP_ERROR_PATH}"
        }
        val codes = (appErrorCodes + confirmationCodes + UiMessageCodeInventory.infoCodes(inputs.producerSources)).distinct()

        val android =
            mapping(
                inputs.androidMainSources[HostLocalizationInputs.ANDROID_MAPPING].orEmpty(),
                ANDROID_FUNCTION,
                ANDROID_ENTRY,
                ANDROID_FALLBACK,
                ANDROID_ANCHOR,
            )
        val ios =
            mapping(
                inputs.iosAppSources[HostLocalizationInputs.IOS_MAPPING].orEmpty(),
                IOS_FUNCTION,
                IOS_ENTRY,
                IOS_FALLBACK,
                IOS_ANCHOR,
            )
        if (TABLE_HEADING !in inputs.contract) problems += "docs/CONTRACTS.md has no '$TABLE_HEADING' section"
        val table = contractTable()

        problems += coverage(HostLocalizationInputs.ANDROID_MAPPING, android.entries, codes)
        problems += coverage(HostLocalizationInputs.IOS_MAPPING, ios.entries, codes)
        problems += coverage(CONTRACT_TABLE, table, codes)
        problems += fallback(HostLocalizationInputs.ANDROID_MAPPING, android.fallback)
        problems += fallback(HostLocalizationInputs.IOS_MAPPING, ios.fallback)
        problems += agreement(codes, android.entries.toMap(), ios.entries.toMap(), table.toMap())
        problems +=
            catalogue(
                HostLocalizationInputs.ANDROID_MAPPING,
                android.keys(),
                mapOf(
                    HostLocalizationInputs.ANDROID_STRINGS_EN to LocalizationCatalogues.androidStrings(inputs.androidStringsEn),
                    HostLocalizationInputs.ANDROID_STRINGS_ES to LocalizationCatalogues.androidStrings(inputs.androidStringsEs),
                ),
            )
        problems +=
            catalogue(
                HostLocalizationInputs.IOS_MAPPING,
                ios.keys(),
                mapOf(
                    HostLocalizationInputs.IOS_STRINGS_EN to LocalizationCatalogues.iosStrings(inputs.iosStringsEn),
                    HostLocalizationInputs.IOS_STRINGS_ES to LocalizationCatalogues.iosStrings(inputs.iosStringsEs),
                ),
            )
        problems += testMirror(HostLocalizationInputs.ANDROID_MAPPING_TEST, inputs.androidMappingTest, codes)
        problems += testMirror(HostLocalizationInputs.IOS_MAPPING_TEST, inputs.iosMappingTest, codes)

        return if (problems.isEmpty()) {
            AssertionResult(ID, NAME, AssertionResult.Status.PASS, "${codes.size} codes")
        } else {
            AssertionResult(ID, NAME, AssertionResult.Status.FAIL, problems.joinToString("; "))
        }
    }

    /**
     * The explicit branches of one host mapping function. A branch counts only when its anchor token
     * (`R.string` or `return`) is code, so a commented-out branch is not a mapping, and only a branch
     * that names exactly one code matches the entry pattern.
     */
    private fun mapping(
        source: String,
        function: String,
        entry: Regex,
        fallback: Regex,
        anchor: String,
    ): Mapping {
        val body = functionBody(source, function) ?: return Mapping(emptyList(), null)
        val masked = KotlinSourceText.code(body)
        val entries =
            entry.findAll(body)
                .filter { match -> isLive(masked, match, anchor) }
                .map { match -> match.groupValues[1] to match.groupValues[2] }
                .toList()
        val fallbackKey =
            fallback.findAll(body)
                .filter { match -> isLive(masked, match, anchor) }
                .map { match -> match.groupValues[1] }
                .firstOrNull()
        return Mapping(entries, fallbackKey)
    }

    private fun isLive(masked: String, match: MatchResult, anchor: String): Boolean {
        val offset = match.range.first + match.value.indexOf(anchor)
        return masked.startsWith(anchor, offset)
    }

    /** The text between the braces of the first block after [signature], or `null` when either is missing. */
    private fun functionBody(source: String, signature: String): String? {
        val masked = KotlinSourceText.code(source)
        val start = masked.indexOf(signature)
        if (start < 0) return null
        val opening = masked.indexOf('{', start + signature.length)
        if (opening < 0) return null
        var depth = 0
        for (index in opening until masked.length) {
            when (masked[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return source.substring(opening + 1, index)
                }
            }
        }
        return null
    }

    /** `code -> key` rows of the `§14.1` table; the section ends at the next heading. */
    private fun contractTable(): List<Pair<String, String>> {
        val section = inputs.contract.substringAfter(TABLE_HEADING, "").substringBefore("\n### ").substringBefore("\n## ")
        return TABLE_ROW.findAll(section).map { it.groupValues[1] to it.groupValues[2] }.toList()
    }

    private fun coverage(source: String, entries: List<Pair<String, String>>, codes: List<String>): List<String> {
        val mapped = entries.map { (code, _) -> code }
        val problems = mutableListOf<String>()
        val missing = codes.filterNot(mapped::contains)
        if (missing.isNotEmpty()) problems += "$source has no explicit entry for $missing"
        val stale = mapped.distinct().filterNot(codes::contains)
        if (stale.isNotEmpty()) problems += "$source maps codes no source defines: $stale"
        mapped.groupingBy { it }.eachCount().filterValues { count -> count > 1 }.keys.sorted().forEach { code ->
            problems += "$source maps $code more than once"
        }
        return problems
    }

    private fun fallback(source: String, key: String?): List<String> =
        if (key == GENERIC_KEY) emptyList() else listOf("$source falls back to '${key ?: "nothing"}' instead of '$GENERIC_KEY'")

    private fun agreement(
        codes: List<String>,
        android: Map<String, String>,
        ios: Map<String, String>,
        table: Map<String, String>,
    ): List<String> =
        codes.mapNotNull { code ->
            val keys = listOf(android[code], ios[code], table[code])
            if (keys.any { it == null } || keys.distinct().size == 1) {
                null
            } else {
                "$code: Android maps to '${android[code]}', iOS to '${ios[code]}', $CONTRACT_TABLE to '${table[code]}'"
            }
        }

    private fun catalogue(source: String, keys: Set<String>, catalogues: Map<String, Map<String, String>>): List<String> =
        catalogues.flatMap { (catalogue, entries) ->
            keys.sorted().mapNotNull { key ->
                val value = entries[key]
                when {
                    value == null -> "$catalogue has no key '$key' mapped by $source"
                    LocalizationCatalogues.hasFormatArgument(value) -> "$catalogue key '$key' mapped by $source carries a format argument"
                    else -> null
                }
            }
        }

    private fun testMirror(path: String, text: String, codes: List<String>): List<String> {
        val missing = codes.filterNot { code -> "\"$code\"" in text }
        return if (missing.isEmpty()) emptyList() else listOf("$path does not name $missing")
    }

    private data class Mapping(val entries: List<Pair<String, String>>, val fallback: String?) {
        fun keys(): Set<String> = entries.map { (_, key) -> key }.toSet() + listOfNotNull(fallback)
    }

    companion object {
        const val ID = 41
        const val NAME = "every UiMessage code has an explicit mapping on both hosts and in §14.1"
        const val CONTRACT_TABLE = "docs/CONTRACTS.md §14.1"
        const val TABLE_HEADING = "### 14.1 UiMessage code-to-copy table"
        const val GENERIC_KEY = "error_unexpected"
        private const val ANDROID_FUNCTION = "fun uiMessageStringResource(code: String): Int"
        private const val IOS_FUNCTION = "func uiMessageKey(for code: String) -> String"
        private const val ANDROID_ANCHOR = "R.string"
        private const val IOS_ANCHOR = "return"
        private val ANDROID_ENTRY = Regex("""\"([^\"\\\n]+)\"\s*->\s*R\.string\.([A-Za-z0-9_]+)""")
        private val ANDROID_FALLBACK = Regex("""\belse\s*->\s*R\.string\.([A-Za-z0-9_]+)""")
        private val IOS_ENTRY = Regex("""\bcase\s+\"([^\"\\\n]+)\"\s*:\s*return\s+\"([a-z][a-z0-9_]*)\"""")
        private val IOS_FALLBACK = Regex("""\bdefault\s*:\s*return\s+\"([a-z][a-z0-9_]*)\"""")
        private val TABLE_ROW = Regex("""(?m)^\|\s*`([^`|]+)`\s*\|[^|\n]*\|\s*`([a-z][a-z0-9_]*)`\s*\|""")
    }
}
