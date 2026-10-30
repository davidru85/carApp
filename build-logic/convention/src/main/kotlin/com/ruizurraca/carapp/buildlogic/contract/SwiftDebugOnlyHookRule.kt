package com.ruizurraca.carapp.buildlogic.contract

import com.ruizurraca.carapp.buildlogic.source.KotlinSourceText

/**
 * `E4-04`: a UI-test hook is a read of the process launch environment, and a Release build MUST NOT
 * contain one, because whoever launches the process could then steer a production build.
 *
 * `E4-03` (`D-254`): a read of the launch arguments is a hook too. It is accepted in a `#if DEBUG` branch
 * or in a `#if CARAPP_BENCHMARK` branch, the condition that only the non-shipping `Benchmark`
 * configuration defines; a launch-environment read stays Debug-only.
 *
 * The rule is a pure function over source text so it has failing fixtures (`D-16`). A read is
 * accepted only in the active branch of an `#if` whose condition is the one its kind requires, alone or
 * conjoined with further conditions. The `#else` or `#elseif` branch of such a block is the Release
 * side, and a disjunction such as `DEBUG || X` can be true in Release, so both are rejected.
 */
internal object SwiftDebugOnlyHookRule {
    /** Returns one entry per launch read outside the branch its kind requires. */
    fun violations(sources: Map<String, String>): List<String> =
        sources.entries.flatMap { (path, text) -> violationsIn(path, text) }

    private fun violationsIn(
        path: String,
        text: String,
    ): List<String> {
        // `KotlinSourceText.code` blanks comments and string literals, offset-preserving, so a
        // commented-out read is not a call site and line numbers stay exact.
        val lines = KotlinSourceText.code(text).lines()
        val branches = ArrayDeque<BranchGuard>()
        val found = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            val condition = IF_DIRECTIVE.matchEntire(trimmed)?.groupValues?.get(1)
            when {
                condition != null -> {
                    branches.addLast(guardOf(condition.trim()))
                }

                ELSE_DIRECTIVE.matches(trimmed) -> {
                    if (branches.isNotEmpty()) {
                        branches.removeLast()
                        branches.addLast(BranchGuard.NONE)
                    }
                }

                ENDIF_DIRECTIVE.matches(trimmed) -> {
                    branches.removeLastOrNull()
                }

                trimmed.contains(ENVIRONMENT_READ) && BranchGuard.DEBUG !in branches -> {
                    found += "$path:${index + 1} reads the launch environment outside #if DEBUG"
                }

                trimmed.contains(ARGUMENTS_READ) && branches.all { it == BranchGuard.NONE } -> {
                    found += "$path:${index + 1} reads the launch arguments outside #if DEBUG or #if CARAPP_BENCHMARK"
                }
            }
        }
        return found
    }

    private fun guardOf(condition: String): BranchGuard =
        when {
            DEBUG_ONLY_CONDITION.matches(condition) -> BranchGuard.DEBUG
            BENCHMARK_ONLY_CONDITION.matches(condition) -> BranchGuard.BENCHMARK
            else -> BranchGuard.NONE
        }

    /** What the active branch of an enclosing `#if` guarantees about the configuration. */
    private enum class BranchGuard { NONE, DEBUG, BENCHMARK }

    private const val ENVIRONMENT_READ = "processInfo.environment"
    private const val ARGUMENTS_READ = "processInfo.arguments"
    private val IF_DIRECTIVE = Regex("""#if\s+(.+)""")
    private val ELSE_DIRECTIVE = Regex("""#(else|elseif\b.*)""")
    private val ENDIF_DIRECTIVE = Regex("""#endif\b.*""")
    private val DEBUG_ONLY_CONDITION = Regex("""DEBUG(\s*&&.*)?""")
    private val BENCHMARK_ONLY_CONDITION = Regex("""CARAPP_BENCHMARK(\s*&&.*)?""")
}
