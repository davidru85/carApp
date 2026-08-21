package com.ruizurraca.carapp.buildlogic.contract

import java.io.File

/** One assertion outcome. [Status.PENDING] means the assertion cannot run yet and says why. */
data class AssertionResult(val id: Int, val name: String, val status: Status, val detail: String = "") {
    enum class Status { PASS, FAIL, PENDING }
}

/**
 * The `contract-check` assertions of `docs/CONTRACTS.md §18`.
 *
 * An assertion whose inputs do not exist yet reports `PENDING` with the story that creates them,
 * rather than passing. A check that silently skips is worse than no check: it reads as coverage
 * that is not there.
 */
class ContractCheck(private val repoRoot: File) {

    private fun read(path: String): String = File(repoRoot, path).readText()
    private fun exists(path: String): Boolean = File(repoRoot, path).exists()

    fun runAll(): List<AssertionResult> = listOf(
        assertion1DeclaredTypes(),
        assertion2DecisionParity(),
        assertion3AdrStatuses(),
        assertion4AwaitingSection(),
        assertion5InterfacesHaveStories(),
        assertion6PrTemplateSuperset(),
        assertion7ObjcHeaderGolden(),
        assertion8ModuleInventoryMirrored(),
        assertion11SchemaVersionMatchesRules(),
        assertion12PoisonRuleReferenced(),
        assertion13TestAppGraphParity(),
        assertion15NoTbdInVersionMatrix(),
        assertion18AuthProviderDeclaredFirst(),
        reportDeclarationsOutsideSection20(),
        reportProseInCodeFences(),
    )

    /**
     * Informational: a `kotlin` fence that holds prose cannot be compiled, copied or parsed, and
     * it makes assertion 1 read English words as type names. Recorded as `DEC-4`.
     */
    private fun reportProseInCodeFences(): AssertionResult {
        val offenders = CODE_BLOCK.findAll(read("docs/CONTRACTS.md"))
            .flatMap { it.groupValues[1].lines() }
            .filter { isProse(it) }
            .map { it.trim().take(60) + "…" }
            .toList()

        return if (offenders.isEmpty()) {
            AssertionResult(102, "contract kotlin fences contain only code", AssertionResult.Status.PASS)
        } else {
            AssertionResult(
                102,
                "contract kotlin fences contain only code",
                AssertionResult.Status.PENDING,
                "${offenders.size} prose line(s) inside kotlin fences (DEC-4): ${offenders.joinToString(" | ")}",
            )
        }
    }

    /** Informational: see [declaredOutsideSection20]. */
    private fun reportDeclarationsOutsideSection20(): AssertionResult {
        val outside = declaredOutsideSection20(read("docs/CONTRACTS.md")).toSortedSet()
        return if (outside.isEmpty()) {
            AssertionResult(101, "all contract declarations live in §20", AssertionResult.Status.PASS)
        } else {
            AssertionResult(
                101,
                "all contract declarations live in §20",
                AssertionResult.Status.PENDING,
                "declared outside §20 (DEC-4): ${outside.joinToString(", ")}",
            )
        }
    }

    // 1 --------------------------------------------------------------------------------------
    /**
     * Every project-owned type named in a code block of `docs/CONTRACTS.md` is declared in §20.
     *
     * The allowlist is the one written in `§18`: primitives, standard containers, coroutine types,
     * the pinned datetime type, Room-generated types owned by `:core:database`, and annotations
     * used only to hide declarations from Objective-C export.
     */
    private fun assertion1DeclaredTypes(): AssertionResult {
        val contracts = read("docs/CONTRACTS.md")
        val declared = declaredTypes(contracts) + enumMembers(contracts)

        val undeclared = sortedSetOf<String>()
        CODE_BLOCK.findAll(contracts).forEach { block ->
            val body = block.groupValues[1]
                .lines()
                .filterNot { isProse(it) }
                .joinToString("\n")
                .replace(Regex("""//.*"""), " ")
                .replace(Regex(""""[^"]*""""), " ")
            IDENTIFIER.findAll(body).map { it.value }.forEach { identifier ->
                if (identifier !in ALLOWLIST && identifier !in declared) undeclared += identifier
            }
        }

        return if (undeclared.isEmpty()) {
            AssertionResult(1, "every project-owned type in a code block is declared in the contract", AssertionResult.Status.PASS)
        } else {
            AssertionResult(
                1,
                "every project-owned type in a code block is declared in the contract",
                AssertionResult.Status.FAIL,
                "undeclared: ${undeclared.joinToString(", ")}",
            )
        }
    }

    /**
     * `§18` assertion 1 says declarations live in §20. Several do not: `Logger` is declared in
     * §17, `AnalyticsTracker` in §16.1, `RemoteSyncSource` in §10, `AppGraphDependencies` in
     * §11.6, the repositories in §12 and the use cases in §13, even though §20 opens with "Every
     * type referenced by a signature in this document is declared here".
     *
     * Reported rather than failed: the types *are* declared, so nothing is ambiguous for an
     * implementer, and moving six sections' worth of declarations is a contract change the owner
     * decides. Recorded as `DEC-4`.
     */
    private fun declaredOutsideSection20(contracts: String): Set<String> {
        val section20 = contracts.substring(contracts.indexOf("## 20. Canonical Type Definitions"))
        return declaredTypes(contracts) - declaredTypes(section20)
    }

    private fun declaredTypes(text: String): Set<String> =
        DECLARATION.findAll(text).map { it.groupValues[2] }.toSet() +
            VAL_DECLARATION.findAll(text).map { it.groupValues[1] }.toSet()

    /**
     * Some `kotlin` fences in the contract contain explanatory paragraphs rather than code — §20.9
     * is the clearest case. Their words would otherwise be read as undeclared types. They are
     * excluded here and reported separately by [reportProseInCodeFences].
     */
    private fun isProse(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.first().isUpperCase()) return false
        // Keyword presence is not a usable signal: the §20.9 paragraphs contain "when" and "val"
        // as ordinary English. Sentence shape is — a long line that starts a sentence and ends
        // one. Kotlin declarations start lowercase (`data`, `val`, `fun`, `enum`) or are short.
        return trimmed.split(" ").size >= 10 && SENTENCE_END.containsMatchIn(trimmed)
    }

    /** Enum entries are values, not types, so they are not subject to assertion 1. */
    private fun enumMembers(text: String): Set<String> =
        Regex("""enum class\s+[A-Za-z0-9]+\s*(?:\([^)]*\))?\s*\{([^}]*)\}""").findAll(text)
            .flatMap { match -> match.groupValues[1].split(',', '\n') }
            .map { it.trim().substringBefore('(').trim() }
            .filter { it.isNotEmpty() && it.first().isUpperCase() }
            .toSet()

    // 2, 10 ----------------------------------------------------------------------------------
    /** The decision ID set and status are identical across the four mirroring documents. */
    private fun assertion2DecisionParity(): AssertionResult {
        val sources = mapOf(
            "docs/DECISION_BOARD.md" to decisionsWithStatus(read("docs/DECISION_BOARD.md"), statusColumn = 5),
            "docs/SPECIFICATION.md §12" to decisionsWithStatus(read("docs/SPECIFICATION.md"), statusColumn = 4),
            "docs/TECHNICAL_PLAN.md §2" to decisionsWithStatus(read("docs/TECHNICAL_PLAN.md"), statusColumn = 4),
            "docs/adr/README.md" to decisionsWithStatus(read("docs/adr/README.md"), statusColumn = 4),
        )
        val baseline = sources.values.first()
        val problems = sources.filterValues { it != baseline }.keys

        return if (problems.isEmpty()) {
            AssertionResult(2, "decision IDs and statuses identical across the four documents", AssertionResult.Status.PASS, "${baseline.size} decisions")
        } else {
            AssertionResult(
                2,
                "decision IDs and statuses identical across the four documents",
                AssertionResult.Status.FAIL,
                problems.joinToString(", ") { name -> "$name differs: ${diff(baseline, sources.getValue(name))}" },
            )
        }
    }

    private fun diff(a: Map<String, String>, b: Map<String, String>): String {
        val missing = a.keys - b.keys
        val extra = b.keys - a.keys
        val changed = a.keys.intersect(b.keys).filter { a[it] != b[it] }
        return listOfNotNull(
            missing.takeIf { it.isNotEmpty() }?.let { "missing $it" },
            extra.takeIf { it.isNotEmpty() }?.let { "extra $it" },
            changed.takeIf { it.isNotEmpty() }?.let { keys -> "status differs for $keys" },
        ).joinToString("; ")
    }

    /** Only the `ID` and `Status` columns are compared, per assertion 10. */
    private fun decisionsWithStatus(markdown: String, statusColumn: Int): Map<String, String> =
        markdown.lines()
            .filter { it.trimStart().startsWith("| D-") }
            .mapNotNull { line ->
                val cells = line.trim().trim('|').split('|').map { it.trim() }
                val id = cells.firstOrNull()?.takeIf { it.startsWith("D-") } ?: return@mapNotNull null
                val status = cells.getOrNull(statusColumn - 1)?.replace(Regex("""\(.*\)"""), "")?.trim()
                    ?: return@mapNotNull null
                id to status
            }
            .toMap()

    // 3 --------------------------------------------------------------------------------------
    private fun assertion3AdrStatuses(): AssertionResult {
        val index = read("docs/adr/README.md")
        val boardStatuses = decisionsWithStatus(index, statusColumn = 4)
        val mismatches = mutableListOf<String>()

        Regex("""\|\s*(D-\d+)\s*\|\s*\[ADR-\d+]\(([^)]+)\)""").findAll(index).forEach { match ->
            val (id, file) = match.destructured
            val adr = File(repoRoot, "docs/adr/$file")
            if (!adr.isFile) {
                mismatches += "$id -> $file is missing"
                return@forEach
            }
            val status = Regex("""## Status\s*\n\s*\n\s*(\w+)""").find(adr.readText())?.groupValues?.get(1)
            if (status != boardStatuses[id]) {
                mismatches += "$id: ADR says '$status', index says '${boardStatuses[id]}'"
            }
        }

        return if (mismatches.isEmpty()) {
            AssertionResult(3, "each ADR Status matches its decision status", AssertionResult.Status.PASS, "${boardStatuses.size} ADRs")
        } else {
            AssertionResult(3, "each ADR Status matches its decision status", AssertionResult.Status.FAIL, mismatches.joinToString("; "))
        }
    }

    // 4 --------------------------------------------------------------------------------------
    private fun assertion4AwaitingSection(): AssertionResult {
        val board = read("docs/DECISION_BOARD.md")
        val unresolved = decisionsWithStatus(board, statusColumn = 5)
            .filterValues { it == "Proposed" || it == "Pending" }
        val section = board.substringAfter("## Decisions Awaiting Owner Confirmation")

        return when {
            unresolved.isEmpty() && section.contains("No `Proposed` or `Pending` decisions") ->
                AssertionResult(4, "unresolved decisions are listed with a Needed by story", AssertionResult.Status.PASS, "none unresolved")
            unresolved.isEmpty() ->
                AssertionResult(4, "unresolved decisions are listed with a Needed by story", AssertionResult.Status.FAIL, "no unresolved decisions, but the section does not say so")
            unresolved.keys.all { section.contains(it) } ->
                AssertionResult(4, "unresolved decisions are listed with a Needed by story", AssertionResult.Status.PASS, "${unresolved.size} listed")
            else ->
                AssertionResult(4, "unresolved decisions are listed with a Needed by story", AssertionResult.Status.FAIL, "not listed: ${unresolved.keys.filterNot { section.contains(it) }}")
        }
    }

    // 5 --------------------------------------------------------------------------------------
    private fun assertion5InterfacesHaveStories(): AssertionResult {
        val contracts = read("docs/CONTRACTS.md")
        val backlog = read("docs/BACKLOG.md")
        val interfaces = Regex("""(?:^|\n)\s*(?:fun\s+)?(?:sealed\s+)?interface\s+([A-Z][A-Za-z0-9]*)""")
            .findAll(contracts).map { it.groupValues[1] }.toSortedSet()
        val missing = interfaces.filterNot { backlog.contains(it) }

        return if (missing.isEmpty()) {
            AssertionResult(5, "every interface in the contract appears in a backlog story", AssertionResult.Status.PASS, "${interfaces.size} interfaces")
        } else {
            AssertionResult(5, "every interface in the contract appears in a backlog story", AssertionResult.Status.FAIL, "absent from docs/BACKLOG.md: $missing")
        }
    }

    // 6 --------------------------------------------------------------------------------------
    private fun assertion6PrTemplateSuperset(): AssertionResult {
        fun headings(path: String) = read(path).lines().filter { it.startsWith("## ") }.map { it.removePrefix("## ").trim() }.toSet()
        val handoff = headings("docs/templates/agent-handoff.md")
        val pr = headings(".github/pull_request_template.md")
        val missing = handoff - pr

        return if (missing.isEmpty()) {
            AssertionResult(6, "the PR template is a superset of the handoff template", AssertionResult.Status.PASS)
        } else {
            AssertionResult(6, "the PR template is a superset of the handoff template", AssertionResult.Status.FAIL, "PR template is missing: $missing")
        }
    }

    // 7 --------------------------------------------------------------------------------------
    private fun assertion7ObjcHeaderGolden(): AssertionResult =
        if (exists("shared/build/generated/objc-header/Shared.h.golden")) {
            AssertionResult(7, "the committed Objective-C golden header is unchanged", AssertionResult.Status.PASS)
        } else {
            AssertionResult(7, "the committed Objective-C golden header is unchanged", AssertionResult.Status.PENDING, "the golden header is produced by E0-07")
        }

    // 8 --------------------------------------------------------------------------------------
    private fun assertion8ModuleInventoryMirrored(): AssertionResult {
        val inventory = Regex("""^:[a-z:\-]+""", RegexOption.MULTILINE)
            .findAll(read("docs/CONTRACTS.md").substringAfter("### 1.1 Canonical module inventory").substringBefore("## 2."))
            .map { it.value }.toSortedSet()
        val plan = read("docs/TECHNICAL_PLAN.md")
        val missing = inventory.filterNot { plan.contains(it) }

        return if (inventory.isEmpty()) {
            AssertionResult(8, "the module inventory is mirrored in TECHNICAL_PLAN §3", AssertionResult.Status.FAIL, "no modules parsed from CONTRACTS §1.1")
        } else if (missing.isEmpty()) {
            AssertionResult(8, "the module inventory is mirrored in TECHNICAL_PLAN §3", AssertionResult.Status.PASS, "${inventory.size} modules")
        } else {
            AssertionResult(8, "the module inventory is mirrored in TECHNICAL_PLAN §3", AssertionResult.Status.FAIL, "absent from the plan: $missing")
        }
    }

    // 11 -------------------------------------------------------------------------------------
    private fun assertion11SchemaVersionMatchesRules(): AssertionResult =
        if (!exists("firestore/rules/main.rules")) {
            AssertionResult(11, "CLIENT_MAX_SCHEMA_VERSION matches the Firestore rules", AssertionResult.Status.PENDING, "firestore/rules/main.rules is created by E3-01")
        } else {
            val declared = Regex("""CLIENT_MAX_SCHEMA_VERSION:\s*Int\s*=\s*(\d+)""").find(read("docs/CONTRACTS.md"))?.groupValues?.get(1)
            val highest = Regex("""schemaVersion\s*[=<>]+\s*(\d+)""").findAll(read("firestore/rules/main.rules"))
                .map { it.groupValues[1].toInt() }.maxOrNull()?.toString()
            if (declared != null && declared == highest) {
                AssertionResult(11, "CLIENT_MAX_SCHEMA_VERSION matches the Firestore rules", AssertionResult.Status.PASS)
            } else {
                AssertionResult(11, "CLIENT_MAX_SCHEMA_VERSION matches the Firestore rules", AssertionResult.Status.FAIL, "contract says $declared, rules say $highest")
            }
        }

    // 12 -------------------------------------------------------------------------------------
    private fun assertion12PoisonRuleReferenced(): AssertionResult {
        val contracts = read("docs/CONTRACTS.md")
        val section7 = contracts.substringAfter("## 7. Sync State Machine").substringBefore("## 8. Outbox Contract")
        return if (section7.contains("§9.7")) {
            AssertionResult(12, "§7 references the qualified poison rule of §9.7", AssertionResult.Status.PASS)
        } else {
            AssertionResult(12, "§7 references the qualified poison rule of §9.7", AssertionResult.Status.FAIL, "§7 does not mention §9.7")
        }
    }

    // 13 -------------------------------------------------------------------------------------
    private fun assertion13TestAppGraphParity(): AssertionResult =
        if (repoRoot.resolve("core/testing/src/commonMain").walkTopDown()
                .any { it.isFile && it.readText().contains("fun testAppGraphDependencies") }
        ) {
            AssertionResult(13, "testAppGraphDependencies matches AppGraphDependencies", AssertionResult.Status.PASS)
        } else {
            AssertionResult(
                13,
                "testAppGraphDependencies matches AppGraphDependencies",
                AssertionResult.Status.PENDING,
                "the factory does not exist: four of the 15 AppGraphDependencies members are owned by " +
                    ":core:database, :core:auth and :core:sync, which Phase 0 forbids creating (DEC-2)",
            )
        }

    // 15 -------------------------------------------------------------------------------------
    private fun assertion15NoTbdInVersionMatrix(): AssertionResult {
        val offenders = read("docs/versions-matrix.md").lines().withIndex()
            .filter { (_, line) -> line.contains("TBD") && !line.contains("no `TBD` cells") }
        return if (offenders.isEmpty()) {
            AssertionResult(15, "no TBD remains in the version matrix after E0-06", AssertionResult.Status.PASS)
        } else {
            AssertionResult(15, "no TBD remains in the version matrix after E0-06", AssertionResult.Status.FAIL, "lines ${offenders.map { it.index + 1 }}")
        }
    }

    // 18 -------------------------------------------------------------------------------------
    private fun assertion18AuthProviderDeclaredFirst(): AssertionResult {
        val contracts = read("docs/CONTRACTS.md")
        val declaration = contracts.indexOf("enum class AuthProvider")
        val analytics = contracts.indexOf("### 20.9 Analytics types")
        return if (declaration in 1 until analytics) {
            AssertionResult(18, "AuthProvider is declared in §20.3 before §20.8 and §20.9 use it", AssertionResult.Status.PASS)
        } else {
            AssertionResult(18, "AuthProvider is declared in §20.3 before §20.8 and §20.9 use it", AssertionResult.Status.FAIL, "declared at $declaration, §20.9 at $analytics")
        }
    }

    private companion object {
        val CODE_BLOCK = Regex("""```kotlin\n(.*?)```""", RegexOption.DOT_MATCHES_ALL)
        val DECLARATION = Regex("""\b(data class|value class|sealed interface|sealed class|enum class|class|interface|object|fun interface)\s+([A-Z][A-Za-z0-9]*)""")
        val VAL_DECLARATION = Regex("""\b(?:val|const val)\s+([A-Z][A-Z0-9_]*)""")
        val IDENTIFIER = Regex("""\b[A-Z][A-Za-z0-9]{2,}\b""")
        /**
         * A sentence ends with a period followed by whitespace or end of line. Neither punctuation
         * nor keyword presence works as a signal here: the §20.9 paragraphs quote identifiers in
         * backticks, use `->`, and contain "when" and "val" as ordinary English words.
         */
        val SENTENCE_END = Regex("""\.(\s|$)""")

        /** The non-project identifiers listed in `docs/CONTRACTS.md §18`. */
        val ALLOWLIST = setOf(
            "String", "Long", "Int", "Boolean", "Unit", "Double", "Float", "Byte", "Short", "Char",
            "List", "Set", "Map", "MutableMap", "MutableList", "Pair", "Nothing", "Any", "Array",
            "Throwable", "Exception", "Result", "Comparable", "Iterable", "Collection",
            "Flow", "StateFlow", "SharedFlow", "CoroutineScope", "CoroutineDispatcher", "Job",
            "Instant", "Duration", "LocalDate", "LocalDateTime", "TimeZone",
            "AppDatabase", "Dao", "Entity", "PrimaryKey", "ColumnInfo", "Query", "Insert",
            "Upsert", "Delete", "Transaction", "Database", "TypeConverter", "Migration", "Index",
            "JvmInline", "HiddenFromObjC", "ObjCName", "Suppress", "OptIn", "Serializable",
            "Deprecated", "Throws", "JvmStatic", "JvmName",
            "TODO", "MUST", "NOT", "AND", "OR", "TRUE", "FALSE", "NULL", "UTC",
        )
    }
}
