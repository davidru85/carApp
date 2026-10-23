package com.ruizurraca.carapp.buildlogic.contract

import com.ruizurraca.carapp.buildlogic.source.KotlinSourceText
import java.io.File

/**
 * `docs/CONTRACTS.md §18` assertion 42 (`D-221`): every string key a host references exists in
 * both catalogues of that host, and the two catalogues of each host declare the same keys.
 *
 * A missing key fails neither build: Android falls back to the default locale and iOS renders the key
 * itself, so the gap stays invisible until someone runs the app in the other language. A key that
 * carries a format argument and is rendered without one shows the raw placeholder (`Date: %@`), the
 * defect `E4-02` found in `FuelEntryFormView.swift`; this assertion rejects that shape as well.
 *
 * The scan is textual. On iOS only key-shaped literals are references; a literal of any other shape is
 * literal text, which assertion 44 reports. A key computed at run time is outside a textual check; the
 * two mapping functions of assertion 41 cover the computed keys that carry messages.
 */
internal class LocalizationKeyContract internal constructor(
    private val inputs: HostLocalizationInputs,
) {
    constructor(repoRoot: File) : this(HostLocalizationInputs.from(repoRoot))

    fun validate(): AssertionResult {
        val problems = androidProblems() + iosProblems()
        return if (problems.isEmpty()) {
            AssertionResult(ID, NAME, AssertionResult.Status.PASS)
        } else {
            AssertionResult(ID, NAME, AssertionResult.Status.FAIL, problems.joinToString("; "))
        }
    }

    private fun androidProblems(): List<String> {
        val english = LocalizationCatalogues.androidStrings(inputs.androidStringsEn)
        val spanish = LocalizationCatalogues.androidStrings(inputs.androidStringsEs)
        val untranslatable = LocalizationCatalogues.androidUntranslatable(inputs.androidStringsEn)
        val englishPlurals = LocalizationCatalogues.androidPlurals(inputs.androidStringsEn)
        val spanishPlurals = LocalizationCatalogues.androidPlurals(inputs.androidStringsEs)
        val problems = mutableListOf<String>()
        problems += lacks(HostLocalizationInputs.ANDROID_STRINGS_ES, english.keys - untranslatable - spanish.keys, "")
        problems += lacks(HostLocalizationInputs.ANDROID_STRINGS_EN, spanish.keys - english.keys, "")
        problems += lacks(HostLocalizationInputs.ANDROID_STRINGS_ES, englishPlurals - spanishPlurals, "plurals ")
        problems += lacks(HostLocalizationInputs.ANDROID_STRINGS_EN, spanishPlurals - englishPlurals, "plurals ")

        val stringCatalogues =
            listOf(
                HostLocalizationInputs.ANDROID_STRINGS_EN to english.keys,
                HostLocalizationInputs.ANDROID_STRINGS_ES to (spanish.keys + untranslatable),
            )
        val pluralCatalogues =
            listOf(
                HostLocalizationInputs.ANDROID_STRINGS_EN to englishPlurals,
                HostLocalizationInputs.ANDROID_STRINGS_ES to spanishPlurals,
            )
        inputs.androidMainSources.forEach { (path, text) ->
            val masked = KotlinSourceText.code(text)
            liveMatches(HostSourceSyntax.R_STRING, text, masked).forEach { match ->
                problems += missing(path, text, match, "R.string", stringCatalogues)
            }
            liveMatches(HostSourceSyntax.R_PLURALS, text, masked).forEach { match ->
                problems += missing(path, text, match, "R.plurals", pluralCatalogues)
            }
            liveMatches(HostSourceSyntax.STRING_WITHOUT_ARGUMENTS, text, masked).forEach { match ->
                val key = match.groupValues[1]
                val line = HostSourceSyntax.lineOf(text, match.range.first)
                listOf(HostLocalizationInputs.ANDROID_STRINGS_EN to english, HostLocalizationInputs.ANDROID_STRINGS_ES to spanish)
                    .filter { (_, entries) -> entries[key]?.let(LocalizationCatalogues::hasFormatArgument) == true }
                    .forEach { (catalogue, _) ->
                        problems += "$path:$line renders R.string.$key without arguments but $catalogue gives it a format argument"
                    }
            }
        }
        inputs.androidResourceXml
            .filterKeys { path -> path != HostLocalizationInputs.ANDROID_STRINGS_EN && path != HostLocalizationInputs.ANDROID_STRINGS_ES }
            .forEach { (path, text) ->
                HostSourceSyntax.XML_STRING_REFERENCE.findAll(LocalizationCatalogues.withoutXmlComments(text)).forEach { match ->
                    val key = match.groupValues[1]
                    stringCatalogues.filterNot { (_, keys) -> key in keys }.forEach { (catalogue, _) ->
                        problems += "$path references @string/$key, missing from $catalogue"
                    }
                }
            }
        return problems
    }

    private fun iosProblems(): List<String> {
        val english = LocalizationCatalogues.iosStrings(inputs.iosStringsEn)
        val spanish = LocalizationCatalogues.iosStrings(inputs.iosStringsEs)
        val problems = mutableListOf<String>()
        problems += lacks(HostLocalizationInputs.IOS_STRINGS_ES, english.keys - spanish.keys, "")
        problems += lacks(HostLocalizationInputs.IOS_STRINGS_EN, spanish.keys - english.keys, "")
        val catalogues = listOf(HostLocalizationInputs.IOS_STRINGS_EN to english, HostLocalizationInputs.IOS_STRINGS_ES to spanish)
        inputs.iosAppSources.forEach { (path, text) ->
            HostSourceSyntax.iosLiterals(text).filter { literal -> literal.isKey }.forEach { reference ->
                catalogues.forEach { (catalogue, entries) ->
                    val value = entries[reference.literal]
                    if (value == null) {
                        problems += "$path:${reference.line} references '${reference.literal}', missing from $catalogue"
                    } else if (reference.rendersWithoutArguments && LocalizationCatalogues.hasFormatArgument(value)) {
                        problems +=
                            "$path:${reference.line} renders '${reference.literal}' without arguments but $catalogue gives it a format argument"
                    }
                }
            }
        }
        return problems
    }

    private fun liveMatches(pattern: Regex, text: String, masked: String): List<MatchResult> =
        pattern.findAll(text).filter { match -> HostSourceSyntax.isCode(masked, match.range.first) }.toList()

    private fun missing(
        path: String,
        text: String,
        match: MatchResult,
        prefix: String,
        catalogues: List<Pair<String, Set<String>>>,
    ): List<String> {
        val key = match.groupValues[1]
        val line = HostSourceSyntax.lineOf(text, match.range.first)
        return catalogues.filterNot { (_, keys) -> key in keys }.map { (catalogue, _) -> "$path:$line references $prefix.$key, missing from $catalogue" }
    }

    private fun lacks(catalogue: String, keys: Set<String>, kind: String): List<String> =
        if (keys.isEmpty()) emptyList() else listOf("$catalogue lacks $kind${keys.sorted()}")

    companion object {
        const val ID = 42
        const val NAME = "every referenced localization key exists in both catalogues of its host"
    }
}
