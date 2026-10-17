package com.ruizurraca.carapp.buildlogic.contract

import com.ruizurraca.carapp.buildlogic.source.KotlinSourceText
import java.io.File

/**
 * `docs/CONTRACTS.md §18` assertion 44 (`D-221`): no host UI source passes literal text to a
 * rendering API (`§14.3`).
 *
 * `§14` keeps display copy out of shared state, but a host can still hard-code it: a Compose
 * `Text("…")`, a `contentDescription = "…"`, or a SwiftUI `Text("\(value) L/100 km")` that no catalogue
 * backs and that therefore never reaches Spanish. Text computed at run time, such as a user-entered
 * vehicle name passed as a variable, is outside this textual check and stays under review.
 *
 * The Debug-only allowlist is explicit and provable: an allowlisted iOS source must exist, and its view
 * may be constructed elsewhere only inside an `#if DEBUG` branch, so the allowlist cannot be used to
 * exempt a production screen (`D-100`).
 */
internal class HostLiteralTextContract internal constructor(
    private val inputs: HostLocalizationInputs,
) {
    constructor(repoRoot: File) : this(HostLocalizationInputs.from(repoRoot))

    fun validate(): AssertionResult {
        val problems = androidProblems() + iosProblems() + allowlistProblems()
        return if (problems.isEmpty()) {
            AssertionResult(ID, NAME, AssertionResult.Status.PASS)
        } else {
            AssertionResult(ID, NAME, AssertionResult.Status.FAIL, problems.joinToString("; "))
        }
    }

    private fun androidProblems(): List<String> =
        inputs.androidMainSources
            .filterKeys { path -> path !in ANDROID_DEBUG_ONLY_SOURCES }
            .flatMap { (path, text) ->
                val masked = KotlinSourceText.code(text)
                HostSourceSyntax.COMPOSE_LITERAL.findAll(text)
                    .filter { match -> HostSourceSyntax.isCode(masked, match.range.first) }
                    .map { match ->
                        val name = match.groupValues.drop(1).first(String::isNotEmpty)
                        "$path:${HostSourceSyntax.lineOf(text, match.range.first)}: literal text passed to $name"
                    }.toList()
            }

    private fun iosProblems(): List<String> =
        inputs.iosAppSources
            .filterKeys { path -> path !in IOS_DEBUG_ONLY_SOURCES }
            .flatMap { (path, text) ->
                val masked = KotlinSourceText.code(text)
                val verbatim =
                    HostSourceSyntax.VERBATIM_TEXT.findAll(text)
                        .filter { match -> HostSourceSyntax.isCode(masked, match.range.first) }
                        .map { match ->
                            "$path:${HostSourceSyntax.lineOf(text, match.range.first)}: Text(verbatim:) renders text that no catalogue backs"
                        }.toList()
                val literals =
                    HostSourceSyntax.iosLiterals(text)
                        .filterNot { literal -> literal.isKey }
                        .map { literal -> "$path:${literal.line}: '${literal.literal}' passed to ${literal.callee} is not a catalogue key" }
                verbatim + literals
            }

    private fun allowlistProblems(): List<String> {
        val problems = mutableListOf<String>()
        (ANDROID_DEBUG_ONLY_SOURCES - inputs.androidMainSources.keys).sorted().forEach { path ->
            problems += "allowlisted Debug-only source $path does not exist"
        }
        IOS_DEBUG_ONLY_SOURCES.sorted().forEach { allowlisted ->
            if (allowlisted !in inputs.iosAppSources) {
                problems += "allowlisted Debug-only source $allowlisted does not exist"
                return@forEach
            }
            val view = allowlisted.substringAfterLast('/').substringBeforeLast('.')
            val use = Regex("""\b${Regex.escape(view)}\s*\(""")
            inputs.iosAppSources.filterKeys { path -> path != allowlisted }.forEach { (path, text) ->
                val masked = KotlinSourceText.code(text)
                val debugLines = debugOnlyLines(masked)
                use.findAll(text)
                    .filter { match -> HostSourceSyntax.isCode(masked, match.range.first) }
                    .forEach { match ->
                        val line = HostSourceSyntax.lineOf(text, match.range.first)
                        if (line !in debugLines) problems += "$path:$line uses Debug-only $view outside #if DEBUG"
                    }
            }
        }
        return problems
    }

    /**
     * The 1-based lines inside an `#if` branch whose condition requires `DEBUG`, nested blocks included.
     * `#if !DEBUG`, a condition joined by `||`, and every `#elseif` / `#else` branch are not Debug-only.
     */
    private fun debugOnlyLines(masked: String): Set<Int> {
        val branches = mutableListOf<Boolean>()
        val lines = mutableSetOf<Int>()
        masked.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            when {
                line.startsWith("#if ") ->
                    branches += DEBUG_CONDITION.containsMatchIn(line.removePrefix("#if ")) && "||" !in line
                line.startsWith("#elseif") || line == "#else" ->
                    if (branches.isNotEmpty()) branches[branches.lastIndex] = false
                line.startsWith("#endif") ->
                    if (branches.isNotEmpty()) branches.removeAt(branches.lastIndex)
                branches.any { it } -> lines += index + 1
            }
        }
        return lines
    }

    companion object {
        const val ID = 44
        const val NAME = "no host UI source renders literal text outside the Debug-only allowlist"

        /** `D-100` (ADR-0101): the walking-skeleton diagnostics screen, reachable only from `#if DEBUG`. */
        val IOS_DEBUG_ONLY_SOURCES: Set<String> = setOf("iosApp/DiagnosticsView.swift")

        /**
         * Android allowlists no source: its diagnostics screen is part of `MainActivity.kt`, which is not
         * Debug-only, and it renders `debugLines` values, never literals.
         */
        val ANDROID_DEBUG_ONLY_SOURCES: Set<String> = emptySet()

        private val DEBUG_CONDITION = Regex("""(?<![!\w])DEBUG\b""")
    }
}
