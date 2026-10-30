package com.ruizurraca.carapp.buildlogic.contract

import com.ruizurraca.carapp.buildlogic.source.KotlinSourceText

/**
 * `E4-04`: a UI-test hook is a read of the process launch environment, and a Release build MUST NOT
 * contain one, because whoever launches the process could then steer a production build.
 *
 * The rule is a pure function over source text so it has failing fixtures (`D-16`). A read is
 * accepted only in the active branch of an `#if` whose condition is `DEBUG`, alone or conjoined with
 * further conditions. The `#else` or `#elseif` branch of such a block is the Release side, and a
 * disjunction such as `DEBUG || X` can be true in Release, so both are rejected.
 */
internal object SwiftDebugOnlyHookRule {
    /** Returns one `"<file>:<line> reads the launch environment outside #if DEBUG"` entry per read. */
    fun violations(sources: Map<String, String>): List<String> =
        sources.entries.flatMap { (path, text) -> violationsIn(path, text) }

    private fun violationsIn(
        path: String,
        text: String,
    ): List<String> {
        // `KotlinSourceText.code` blanks comments and string literals, offset-preserving, so a
        // commented-out read is not a call site and line numbers stay exact.
        val lines = KotlinSourceText.code(text).lines()
        val debugOnlyBranches = ArrayDeque<Boolean>()
        val found = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            val condition = IF_DIRECTIVE.matchEntire(trimmed)?.groupValues?.get(1)
            when {
                condition != null -> {
                    debugOnlyBranches.addLast(DEBUG_ONLY_CONDITION.matches(condition.trim()))
                }

                ELSE_DIRECTIVE.matches(trimmed) -> {
                    if (debugOnlyBranches.isNotEmpty()) {
                        debugOnlyBranches.removeLast()
                        debugOnlyBranches.addLast(false)
                    }
                }

                ENDIF_DIRECTIVE.matches(trimmed) -> {
                    debugOnlyBranches.removeLastOrNull()
                }

                trimmed.contains(ENVIRONMENT_READ) && debugOnlyBranches.none { it } -> {
                    found += "$path:${index + 1} reads the launch environment outside #if DEBUG"
                }
            }
        }
        return found
    }

    private const val ENVIRONMENT_READ = "processInfo.environment"
    private val IF_DIRECTIVE = Regex("""#if\s+(.+)""")
    private val ELSE_DIRECTIVE = Regex("""#(else|elseif\b.*)""")
    private val ENDIF_DIRECTIVE = Regex("""#endif\b.*""")
    private val DEBUG_ONLY_CONDITION = Regex("""DEBUG(\s*&&.*)?""")
}
