package com.ruizurraca.carapp.buildlogic.contract

import com.ruizurraca.carapp.buildlogic.source.KotlinSourceText

/**
 * Source shapes shared by the localization assertions 42 and 44 (`E4-02`).
 *
 * Every pattern runs on the raw text and keeps a match only when its first character is code in the
 * offset-preserving `KotlinSourceText.code` view, which blanks comments and string literals in Kotlin
 * and in Swift (`SwiftTriggerSurfaceRule` uses it on Swift for the same reason). A commented-out call
 * is therefore never read as a reference or as literal text.
 */
internal object HostSourceSyntax {
    /** The callee name a literal yielded by a `…Key` function reports. */
    private const val KEY_FUNCTION_CALLEE = "return"

    /** One string literal on one line. In the patterns below its body is group 2, escapes kept. */
    private const val LITERAL = """\"((?:[^\"\\\n]|\\.)*)\""""

    /** The SwiftUI initializers and modifiers whose first unlabeled `String` literal is a `LocalizedStringKey`. */
    val SWIFTUI_KEY_CALLEES: List<String> =
        listOf(
            "Text", "Button", "Label", "Toggle", "Picker", "Section", "TextField", "SecureField", "Link", "Menu",
            "Stepper", "DatePicker", "ProgressView", "LocalizedStringKey", "navigationTitle", "accessibilityLabel",
            "accessibilityHint", "accessibilityValue", "alert", "confirmationDialog", "help",
        )

    /** A catalogue key: lowercase snake_case, no interpolation, no spaces. */
    val KEY_SHAPE = Regex("""[a-z][a-z0-9_]*""")

    private val SWIFTUI_KEY_LITERAL = Regex("""\b(${SWIFTUI_KEY_CALLEES.joinToString("|")})\(\s*$LITERAL""")
    private val LOCALIZED_LITERAL = Regex("""\b(String)\(\s*localized:\s*$LITERAL""")
    private val LOCALIZATION_VALUE_LITERAL = Regex("""\b(LocalizationValue)\(\s*$LITERAL""")
    private val NS_LOCALIZED_LITERAL = Regex("""\b(NSLocalizedString)\(\s*$LITERAL""")

    /** A literal passed to an app view or helper parameter that the view renders as a `LocalizedStringKey`. */
    private val LABELLED_KEY_LITERAL = Regex("""\b(key|labelKey|valueKey|titleKey)\s*:\s*$LITERAL""")

    /** A snake_case literal a `…Key` function yields, after `return` or as a ternary branch (`? "a" : "b"`). */
    private val KEY_RETURN = Regex("""(?:\b(return)\s+|[?:]\s*)\"([a-z][a-z0-9]*(?:_[a-z0-9]+)+)\"""")
    private val KEY_FUNCTION = Regex("""\bfunc\s+\w*Key\s*\(""")

    val VERBATIM_TEXT = Regex("""\bText\(\s*verbatim\s*:""")
    val R_STRING = Regex("""\bR\.string\.([A-Za-z0-9_]+)""")
    val R_PLURALS = Regex("""\bR\.plurals\.([A-Za-z0-9_]+)""")
    val XML_STRING_REFERENCE = Regex("""@string/([A-Za-z0-9_]+)""")
    val STRING_WITHOUT_ARGUMENTS = Regex("""\b(?:stringResource|getString)\(\s*R\.string\.([A-Za-z0-9_]+)\s*\)""")
    val COMPOSE_LITERAL =
        Regex(
            """\b(Text)\(\s*(?:text\s*=\s*)?\"|\b(contentDescription|stateDescription|onClickLabel|onLongClickLabel|paneTitle)\s*=\s*\"|\b(showSnackbar)\(\s*(?:message\s*=\s*)?\"""",
        )

    /** Whether [offset] is code — not inside a comment or a string literal — in [masked]. */
    fun isCode(masked: String, offset: Int): Boolean = offset in masked.indices && !masked[offset].isWhitespace()

    fun lineOf(text: String, offset: Int): Int = text.substring(0, offset).count { it == '\n' } + 1

    /**
     * One string literal an iOS source hands to the localization system: the callee that receives it,
     * its body, its line, and whether the callee renders it without format arguments (a SwiftUI
     * initializer or modifier does; `String(localized:)` may feed `String(format:)`).
     */
    internal data class IosLiteral(
        val callee: String,
        val literal: String,
        val line: Int,
        val rendersWithoutArguments: Boolean,
    ) {
        val isKey: Boolean get() = KEY_SHAPE.matches(literal)
    }

    /**
     * Every literal of [text] passed to a SwiftUI key initializer or modifier, to `String(localized:)`,
     * `String.LocalizationValue` or `NSLocalizedString`, or to a `key:`, `labelKey:`, `valueKey:` or
     * `titleKey:` argument, plus every snake_case literal a function whose name ends in `Key` yields after
     * `return` or as a ternary branch, in line order.
     */
    fun iosLiterals(text: String): List<IosLiteral> {
        val masked = KotlinSourceText.code(text)
        val keyFunctions = keyFunctionBodies(masked)
        val shapes =
            listOf(
                SWIFTUI_KEY_LITERAL to true,
                LOCALIZED_LITERAL to false,
                LOCALIZATION_VALUE_LITERAL to false,
                NS_LOCALIZED_LITERAL to false,
                LABELLED_KEY_LITERAL to true,
            )
        val calls =
            shapes.flatMap { (pattern, withoutArguments) ->
                pattern.findAll(text)
                    .filter { match -> isCode(masked, match.range.first) }
                    .map { match ->
                        IosLiteral(match.groupValues[1], match.groupValues[2], lineOf(text, match.range.first), withoutArguments)
                    }.toList()
            }
        val returns =
            KEY_RETURN.findAll(text)
                .filter { match -> isCode(masked, match.range.first) && keyFunctions.any { body -> match.range.first in body } }
                .map { match -> IosLiteral(KEY_FUNCTION_CALLEE, match.groupValues[2], lineOf(text, match.range.first), false) }
                .toList()
        return (calls + returns).sortedBy(IosLiteral::line)
    }

    private fun keyFunctionBodies(masked: String): List<IntRange> =
        KEY_FUNCTION.findAll(masked)
            .mapNotNull { match ->
                val opening = masked.indexOf('{', match.range.last)
                val closing = if (opening < 0) -1 else matchingBrace(masked, opening)
                if (closing < 0) null else opening..closing
            }.toList()

    private fun matchingBrace(masked: String, opening: Int): Int {
        var depth = 0
        for (index in opening until masked.length) {
            when (masked[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }
}
