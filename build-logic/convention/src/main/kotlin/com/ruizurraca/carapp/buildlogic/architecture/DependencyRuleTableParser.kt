package com.ruizurraca.carapp.buildlogic.architecture

/**
 * Builds the rule set from the `docs/TECHNICAL_PLAN.md §4` table.
 *
 * `E0-04` requires that "the check configuration is generated from this table so the two cannot
 * drift". Parsing the document is what makes that literally true: editing the table changes the
 * check, and a row the parser cannot understand fails rather than being skipped.
 */
object DependencyRuleTableParser {
    private val ROW = Regex("""^\|\s*(.+?)\s*\|\s*(.*?)\s*\|\s*(.*?)\s*\|\s*$""")
    private val CODE_SPAN = Regex("""`([^`]+)`""")

    /**
     * @param markdown the full text of `docs/TECHNICAL_PLAN.md`.
     * @throws IllegalStateException when the section or its table cannot be found, so a renamed
     *         heading breaks the build instead of quietly disabling every rule.
     */
    fun parse(markdown: String): List<ModuleRule> {
        val section = sectionOf(markdown, "## 4. Dependency Rules", "## 4.1")
        val rows = section.lines()
            .mapNotNull { ROW.matchEntire(it) }
            .filterNot { it.groupValues[1].startsWith("---") || it.groupValues[1] == "Area" }
            .filterNot { it.groupValues[2].startsWith("---") }

        check(rows.isNotEmpty()) {
            "No dependency rows found in docs/TECHNICAL_PLAN.md §4. The architecture check is " +
                "generated from that table; an empty parse would silently disable every rule."
        }

        return rows.map { row ->
            val area = normaliseArea(row.groupValues[1])
            val allowed = row.groupValues[2]
            val forbidden = row.groupValues[3]
            ModuleRule(
                area = area,
                allowedModules = modulesIn(allowed),
                forbiddenModules = modulesIn(forbidden),
                allowedCapabilities = capabilitiesIn(allowed),
                forbiddenCapabilities = capabilitiesIn(forbidden),
                forbidsOtherFeatures = forbidden.contains("other features") ||
                    forbidden.contains("features"),
            )
        }
    }

    private fun sectionOf(markdown: String, startsWith: String, endsWith: String): String {
        val start = markdown.indexOf(startsWith)
        check(start >= 0) { "Could not find '$startsWith' in docs/TECHNICAL_PLAN.md" }
        val end = markdown.indexOf(endsWith, start + startsWith.length)
        check(end > start) { "Could not find the end of '$startsWith' in docs/TECHNICAL_PLAN.md" }
        return markdown.substring(start, end)
    }

    /** `feature `domain`` and `` `:core:model` `` both reduce to a stable key. */
    private fun normaliseArea(raw: String): String =
        raw.replace("`", "").replace("**", "").trim()

    private fun modulesIn(cell: String): Set<String> =
        CODE_SPAN.findAll(cell)
            .map { it.groupValues[1] }
            .filter { it.startsWith(":") }
            .toSet()

    private fun capabilitiesIn(cell: String): Set<Capability> {
        val fromCodeSpans = CODE_SPAN.findAll(cell).mapNotNull { Capability.fromToken(it.groupValues[1]) }.toSet()
        return fromCodeSpans + Capability.allIn(cell)
    }
}
