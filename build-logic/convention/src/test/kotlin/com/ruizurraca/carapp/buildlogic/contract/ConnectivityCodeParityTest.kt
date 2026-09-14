package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `CONNECTIVITY_ERROR_CODES` is defined once in `:core:common` and is also repeated as SQL string
 * literals in `database.sq`, because a SQLDelight `IN` list cannot bind a Kotlin set from the
 * generated API without changing the query shape. This guard is the executable link between the two:
 * adding a code to the constant without adding it to the SQL, or the reverse, fails here rather than
 * silently diverging in production.
 *
 * `build-logic` cannot depend on `:core:common`, so the constant is parsed from its declaration
 * instead of imported.
 */
class ConnectivityCodeParityTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun sqlConnectivityLiteralsMatchTheKotlinConstant() {
        val constantCodes = connectivityConstantCodes()
        val sqlCodes = connectivitySqlLiterals()

        assertTrue(
            constantCodes.isNotEmpty(),
            "Could not parse CONNECTIVITY_ERROR_CODES from Constants.kt; the guard would pass vacuously.",
        )
        assertEquals(
            constantCodes,
            sqlCodes,
            "The connectivity-code literals in database.sq diverged from CONNECTIVITY_ERROR_CODES. " +
                "Update the SQL $CONNECTIVITY_STATEMENTS to match the constant.",
        )
    }

    private fun connectivityConstantCodes(): Set<String> {
        val constants = File(repositoryRoot, CONSTANTS_PATH).readText()
        val declaration =
            Regex("""CONNECTIVITY_ERROR_CODES:[\s\S]*?=\s*setOf\(([\s\S]*?)\)""")
                .find(constants)
                ?.groupValues
                ?.get(1)
                ?: return emptySet()
        return Regex("""\"([^\"]+)\"""").findAll(declaration).map { it.groupValues[1] }.toSet()
    }

    /** Collects the codes from the three statements that read `lastErrorCode` as a connectivity set. */
    private fun connectivitySqlLiterals(): Set<String> {
        val database = File(repositoryRoot, DATABASE_SQL_PATH).readText()
        val codes = mutableSetOf<String>()
        CONNECTIVITY_STATEMENTS.forEach { statement ->
            val body =
                Regex("""$statement:([\s\S]*?);""")
                    .find(database)
                    ?.groupValues
                    ?.get(1)
                    ?: error("Could not find the '$statement' statement in database.sq")
            codes += Regex("""'(REMOTE\.[A-Z_]+)'""").findAll(body).map { it.groupValues[1] }
        }
        return codes
    }

    private companion object {
        const val CONSTANTS_PATH =
            "core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/Constants.kt"
        const val DATABASE_SQL_PATH =
            "core/database/src/commonMain/sqldelight/com/ruizurraca/carapp/core/database/database.sq"
        val CONNECTIVITY_STATEMENTS =
            setOf("markConnectivityFailuresDue", "countPendingSyncRows", "countRetryableSyncRows")
    }
}
