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
    fun registryOf(board: String): String = board.substringBefore(AWAITING_HEADING)

    /** The awaiting-confirmation summary, the other board table whose rows start with an ID. */
    fun awaitingOf(board: String): String = board.substringAfter(AWAITING_HEADING, "")

    /** Only the `ID` and `Status` columns are compared, per assertion 10. */
    fun decisionsWithStatus(
        markdown: String,
        statusColumn: Int,
    ): Map<String, String> =
        tableBlocks(markdown)
            .flatten()
            .mapNotNull { cells ->
                val id = cells.firstOrNull()?.takeIf { it.startsWith("D-") } ?: return@mapNotNull null
                val status = cells.getOrNull(statusColumn - 1)?.replace(Regex("""\(.*\)"""), "")?.trim()
                    ?: return@mapNotNull null
                id to status
            }.toMap()

    /**
     * Decision IDs that appear on more than one row of the same table in [markdown], in first-seen
     * order across the document.
     *
     * [decisionsWithStatus] terminates in `toMap()`, so a repeated ID silently overwrites the
     * earlier row and a duplicated decision reads as one correct row. This detector makes the
     * repetition visible. Counting is scoped per table, not per file: an ID legitimately appears
     * once in the registry and once in the awaiting summary of `docs/DECISION_BOARD.md`, so a
     * file-wide count would flag a required shape. A table is a contiguous block of `| D-` rows.
     */
    fun duplicatedIds(markdown: String): List<String> =
        tableBlocks(markdown)
            .flatMap { block ->
                block
                    .mapNotNull { cells -> cells.firstOrNull()?.takeIf { it.startsWith("D-") } }
                    .groupingBy { it }
                    .eachCount()
                    .filterValues { it > 1 }
                    .keys
            }

    private fun tableBlocks(markdown: String): List<List<List<String>>> {
        val blocks = mutableListOf<List<List<String>>>()
        var current = mutableListOf<List<String>>()
        markdown.lines().forEach { line ->
            if (line.trimStart().startsWith("| D-")) {
                current += line.trim().trim('|').split('|').map { it.trim() }
            } else if (current.isNotEmpty()) {
                blocks += current
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) blocks += current
        return blocks
    }
}
