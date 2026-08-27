package com.ruizurraca.carapp.buildlogic.architecture

/**
 * A capability a module may or may not reach for. The vocabulary is closed on purpose: the rule
 * table of `docs/TECHNICAL_PLAN.md §4` is prose, and a parser that accepted arbitrary words would
 * silently drop a rule it failed to understand instead of failing loudly.
 */
enum class Capability {
    PLATFORM_API,
    FIREBASE,
    GITLIVE,
    DATABASE,
    KOIN,
    KTOR,
    SERIALIZATION,
    DATETIME,
    COROUTINES,
    ;

    companion object {
        /**
         * The phrases the table uses for each capability.
         *
         * Detection is by containment rather than by exact match, because the table qualifies
         * some entries in prose — `:core:testing` forbids "platform APIs in `commonMain` public
         * API (platform APIs are permitted only in `expect`/`actual` test doubles)". An
         * exact-match parser silently dropped that row's platform rule, which is the failure mode
         * this design has to avoid: a rule that parses to nothing passes everything.
         */
        private val PHRASES: List<Pair<String, Capability>> = listOf(
            "platform api" to PLATFORM_API,
            "android" to PLATFORM_API,
            "ios" to PLATFORM_API,
            "firebase" to FIREBASE,
            "gitlive" to GITLIVE,
            "sqldelight" to DATABASE,
            "sqlite" to DATABASE,
            "room" to DATABASE,
            "koin" to KOIN,
            "ktor" to KTOR,
            "kotlinx.serialization" to SERIALIZATION,
            "kotlinx-datetime" to DATETIME,
            "coroutines" to COROUTINES,
        )

        /** Maps a single token, used for the backticked code spans of the table. */
        fun fromToken(token: String): Capability? {
            val normalised = token.lowercase().trim(' ', '.', ',', '*', '`')
            return PHRASES.firstOrNull { (phrase, _) -> normalised == phrase || normalised == phrase + "s" }?.second
        }

        /** Maps every capability mentioned anywhere in a table cell. */
        fun allIn(cell: String): Set<Capability> {
            val lowered = cell.lowercase()
            return PHRASES.filter { (phrase, _) -> lowered.contains(phrase) }.map { it.second }.toSet()
        }
    }
}

/**
 * One row of the `docs/TECHNICAL_PLAN.md §4` table.
 *
 * [area] is the row label as written in the table, for example `:core:model`, `:integration:*` or
 * `feature domain`. [allowedModules] and [forbiddenModules] hold module path patterns, where `*`
 * matches one or more path segments.
 */
data class ModuleRule(
    val area: String,
    val allowedModules: Set<String>,
    val forbiddenModules: Set<String>,
    val allowedCapabilities: Set<Capability>,
    val forbiddenCapabilities: Set<Capability>,
    val forbidsOtherFeatures: Boolean,
)

/** A single violation, carrying the rule that produced it so the message can name it. */
data class Violation(
    val modulePath: String,
    val rule: String,
    val detail: String,
) {
    override fun toString(): String = "$modulePath: $rule\n    $detail"
}

/** What the checker knows about a module. Kept as plain data so the rules stay unit-testable. */
data class ModuleUnderCheck(
    val path: String,
    val projectDependencies: Set<String> = emptySet(),
    val projectDependencyConfigurations: Map<String, Set<String>> = emptyMap(),
    val externalDependencies: Set<String> = emptySet(),
    /** Fully-qualified imports found in `commonMain`-visible sources, plus declaration keywords. */
    val imports: Set<String> = emptySet(),
    val sourceLines: List<SourceLine> = emptyList(),
    val appliedPluginIds: Set<String> = emptySet(),
)

data class SourceLine(val file: String, val number: Int, val text: String)
