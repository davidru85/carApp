package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `D-206` (ADR-0207): `EntityType` in `:core:common` is the single source of the outbox `entityType`
 * wire value. SQLite cannot read a Kotlin constant, so `schema.sq`, the `.sqm` migrations and
 * `database.sq` keep their literals, and this guard is the executable link between them and the enum,
 * in the same way `ConnectivityCodeParityTest` links `CONNECTIVITY_ERROR_CODES` to its SQL.
 *
 * `build-logic` cannot depend on `:core:common`, so the enum entries are parsed from the declaration.
 */
class EntityTypeWireValueParityTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun entityTypeIsDeclaredOnlyInCoreCommon() {
        val declarations = productionKotlinSources().filter { (_, text) -> ENUM_DECLARATION.containsMatchIn(text) }

        assertEquals(
            listOf(ENTITY_TYPE_PATH),
            declarations.map { (path, _) -> path },
            "EntityType MUST be declared exactly once, in :core:common (D-206), so that :core:database can use it.",
        )
    }

    @Test
    fun theSchemaCheckListsEqualTheEnumEntries() {
        val entries = enumEntries()

        val lists = checkLists(read(SCHEMA_PATH))
        assertTrue(lists.isNotEmpty(), "Could not find an entityType CHECK list in schema.sq; the guard would pass vacuously.")
        lists.forEach { list ->
            assertEquals(entries, list, "A CHECK (entityType IN (...)) list in schema.sq diverged from EntityType.")
        }
    }

    @Test
    fun theMigrationCheckListsNameOnlyEnumEntries() {
        val entries = enumEntries()

        migrationPaths().forEach { path ->
            checkLists(read(path)).forEach { list ->
                assertTrue(
                    entries.containsAll(list),
                    "A CHECK (entityType IN (...)) list in $path names a value outside EntityType: ${list - entries}.",
                )
            }
        }
    }

    @Test
    fun everyQueryLiteralComparedWithEntityTypeIsAnEnumEntry() {
        val entries = enumEntries()

        val literals = queryLiterals(read(DATABASE_SQL_PATH))
        assertTrue(literals.isNotEmpty(), "Could not find an entityType literal in database.sq; the guard would pass vacuously.")
        assertEquals(emptySet(), literals - entries, "database.sq compares entityType with a value outside EntityType.")
    }

    @Test
    fun aSchemaCheckListThatDropsAnEntryIsRejected() {
        // Failing fixture: one list loses an entry while the others keep it.
        val mutated = read(SCHEMA_PATH).replaceFirst("""'FUEL_ENTRY'""", "''")

        val divergent = checkLists(mutated).filter { it != REFERENCE_ENTRIES }

        assertEquals(1, divergent.size, "The guard must reject exactly the one CHECK list that dropped an entry.")
    }

    @Test
    fun aQueryLiteralOutsideTheEnumIsRejected() {
        // Failing fixture: a statement compares entityType with a token the enum does not declare.
        val mutated = read(DATABASE_SQL_PATH) + "\nselectForeign:\nSELECT * FROM outbox WHERE entityType = 'TRIP';\n"

        assertEquals(setOf("TRIP"), queryLiterals(mutated) - REFERENCE_ENTRIES)
    }

    @Test
    fun noProductionKotlinSourceRepeatsAWireValueLiteral() {
        val entries = enumEntries()

        val offenders =
            productionKotlinSources()
                .filter { (path, _) -> path != ENTITY_TYPE_PATH }
                .flatMap { (path, text) -> wireValueLiteralLines(text, entries).map { line -> "$path:$line" } }

        assertEquals(
            emptyList(),
            offenders,
            "Production Kotlin MUST take the entityType wire value from EntityType (D-206), not from a string literal.",
        )
    }

    @Test
    fun aProductionWireValueLiteralIsRejected() {
        // Failing fixture: a payload writer spelling the token, beside the comment and enum forms the
        // guard must accept.
        val source =
            """
            |package fixture
            |
            |val payload = mapOf("entityType" to "FUEL_ENTRY")
            |// "VEHICLE" in a comment is not code
            |val name = EntityType.VEHICLE.name
            """.trimMargin()

        assertEquals(listOf(3), wireValueLiteralLines(source, REFERENCE_ENTRIES))
    }

    private fun wireValueLiteralLines(
        source: String,
        entries: Set<String>,
    ): List<Int> =
        source.lines().mapIndexedNotNull { index, line ->
            val trimmed = line.trim()
            val code = if (trimmed.startsWith("*") || trimmed.startsWith("/*")) "" else line.substringBefore("//")
            if (entries.any { entry -> "\"$entry\"" in code }) index + 1 else null
        }

    private fun read(path: String): String = File(repositoryRoot, path).readText()

    private fun enumEntries(): Set<String> {
        val declaration = File(repositoryRoot, ENTITY_TYPE_PATH)
        assertTrue(declaration.isFile, "EntityType MUST be declared in $ENTITY_TYPE_PATH (D-206).")
        val body =
            Regex("""enum class EntityType\b[^{]*\{([\s\S]*?)\}""")
                .find(declaration.readText())
                ?.groupValues
                ?.get(1)
                .orEmpty()
        val entries = ENUM_ENTRY.findAll(body).map { it.groupValues[1] }.toSet()
        assertTrue(entries.isNotEmpty(), "Could not parse the EntityType entries; the guard would pass vacuously.")
        return entries
    }

    private fun checkLists(sql: String): List<Set<String>> =
        CHECK_LIST.findAll(sql).map { match -> SQL_LITERAL.findAll(match.groupValues[1]).map { it.groupValues[1] }.toSet() }.toList()

    private fun queryLiterals(sql: String): Set<String> =
        (COMPARISON_LITERAL.findAll(sql).map { it.groupValues[1] } + CASE_LITERAL.findAll(sql).map { it.groupValues[1] }).toSet()

    private fun migrationPaths(): List<String> =
        File(repositoryRoot, SQLDELIGHT_DIRECTORY)
            .listFiles { file -> file.extension == "sqm" }
            .orEmpty()
            .map { "$SQLDELIGHT_DIRECTORY/${it.name}" }
            .sorted()

    private fun productionKotlinSources(): List<Pair<String, String>> =
        PRODUCT_ROOTS
            .map { File(repositoryRoot, it) }
            .filter { it.isDirectory }
            .flatMap { root ->
                root.walkTopDown()
                    .onEnter { it.name != "build" }
                    .filter { it.isFile && it.extension == "kt" && PRODUCTION_SOURCE_SET.containsMatchIn(it.invariantSeparatorsPath) }
                    .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath to it.readText() }
                    .toList()
            }
            .sortedBy { it.first }

    private companion object {
        const val ENTITY_TYPE_PATH =
            "core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/EntityType.kt"
        const val SQLDELIGHT_DIRECTORY =
            "core/database/src/commonMain/sqldelight/com/ruizurraca/carapp/core/database"
        const val SCHEMA_PATH = "$SQLDELIGHT_DIRECTORY/schema.sq"
        const val DATABASE_SQL_PATH = "$SQLDELIGHT_DIRECTORY/database.sq"
        val REFERENCE_ENTRIES = setOf("VEHICLE", "FUEL_ENTRY")
        val PRODUCT_ROOTS = listOf("core", "feature", "shared", "integration", "wiring", "composition", "androidApp")
        val PRODUCTION_SOURCE_SET = Regex("""/src/(commonMain|androidMain|iosMain|jvmMain|main)/""")
        val ENUM_DECLARATION = Regex("""\benum\s+class\s+EntityType\b""")
        val ENUM_ENTRY = Regex("""\b([A-Z][A-Z0-9_]*)\s*\(""")
        val CHECK_LIST = Regex("""CHECK\s*\(\s*entityType\s+IN\s*\(([^)]*)\)""")
        val SQL_LITERAL = Regex("""'([^']*)'""")
        val COMPARISON_LITERAL = Regex("""\bentityType\s*(?:=|!=|<>)\s*'([^']*)'""")
        val CASE_LITERAL = Regex("""\bCASE\s+(?:\w+\.)?entityType\s+WHEN\s+'([^']*)'""")
    }
}
