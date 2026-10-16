package com.ruizurraca.carapp.buildlogic.contract

/**
 * Textual readers for the two host string catalogues (`E4-02`).
 *
 * `build-logic` cannot run Android resource merging or Xcode, so the committed catalogues are parsed
 * directly. The readers accept the shapes this repository writes and nothing else: an entry they cannot
 * read is reported as a missing key, so an unparseable catalogue fails closed.
 */
internal object LocalizationCatalogues {
    private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    private val IOS_ENTRY = Regex("""(?m)^\s*\"((?:[^\"\\]|\\.)*)\"\s*=\s*\"((?:[^\"\\]|\\.)*)\"\s*;""")
    private val XML_COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val ANDROID_STRING =
        Regex("""<string\s+name=\"([A-Za-z0-9_]+)\"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val ANDROID_PLURALS = Regex("""<plurals\s+name=\"([A-Za-z0-9_]+)\"""")
    private val UNTRANSLATABLE = Regex("""\btranslatable\s*=\s*\"false\"""")
    private val FORMAT_ARGUMENT = Regex("""%(?:\d+\$)?[-#+0]*\d*(?:\.\d+)?[a-zA-Z@]""")

    /** `"key" = "value";` entries of a `Localizable.strings` file; comments are ignored. */
    fun iosStrings(text: String): Map<String, String> {
        val withoutComments =
            text.replace(BLOCK_COMMENT, "")
                .lines()
                .filterNot { line -> line.trimStart().startsWith("//") }
                .joinToString("\n")
        return IOS_ENTRY.findAll(withoutComments).associate { it.groupValues[1] to it.groupValues[2] }
    }

    /** `<string name="key">value</string>` entries of an Android `strings.xml`; comments are ignored. */
    fun androidStrings(text: String): Map<String, String> =
        ANDROID_STRING.findAll(withoutXmlComments(text)).associate { it.groupValues[1] to it.groupValues[3] }

    /** Names declared `translatable="false"`; they exist in the default locale only, by design. */
    fun androidUntranslatable(text: String): Set<String> =
        ANDROID_STRING.findAll(withoutXmlComments(text))
            .filter { match -> UNTRANSLATABLE.containsMatchIn(match.groupValues[2]) }
            .map { match -> match.groupValues[1] }
            .toSet()

    /** Names of the `<plurals>` resources. */
    fun androidPlurals(text: String): Set<String> =
        ANDROID_PLURALS.findAll(withoutXmlComments(text)).map { it.groupValues[1] }.toSet()

    fun withoutXmlComments(text: String): String = text.replace(XML_COMMENT, "")

    /** Whether a value carries a printf-style argument (`%@`, `%d`, `%1$s`, …); `%%` is a literal percent. */
    fun hasFormatArgument(value: String): Boolean = FORMAT_ARGUMENT.containsMatchIn(value.replace("%%", ""))
}
