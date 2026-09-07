package com.ruizurraca.carapp.buildlogic.contract

/**
 * Parsing of the `docs/DECISION_BOARD.md` decision rows.
 *
 * The board holds two tables whose rows both start with a decision ID: the registry itself, and the
 * "Decisions Awaiting Owner Confirmation" summary, whose columns are `ID`, `Area`,
 * `Recommendation`, `Needed by` and `Consequence if unresolved`. Only the first is the registry, so
 * the status column of the second means something else entirely and MUST NOT be read as a status.
 */
internal object DecisionRegistry {
    private const val AWAITING_HEADING = "## Decisions Awaiting Owner Confirmation"

    /** The part of the board that is the decision registry, excluding the awaiting summary. */
    fun registryOf(board: String): String = board

    /** Only the `ID` and `Status` columns are compared, per assertion 10. */
    fun decisionsWithStatus(
        markdown: String,
        statusColumn: Int,
    ): Map<String, String> =
        markdown.lines()
            .filter { it.trimStart().startsWith("| D-") }
            .mapNotNull { line ->
                val cells = line.trim().trim('|').split('|').map { it.trim() }
                val id = cells.firstOrNull()?.takeIf { it.startsWith("D-") } ?: return@mapNotNull null
                val status = cells.getOrNull(statusColumn - 1)?.replace(Regex("""\(.*\)"""), "")?.trim()
                    ?: return@mapNotNull null
                id to status
            }.toMap()
}
