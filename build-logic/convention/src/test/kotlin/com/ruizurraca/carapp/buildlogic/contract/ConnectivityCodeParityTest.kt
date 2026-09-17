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
    fun everyConnectivityStatementMatchesTheKotlinConstantIndividually() {
        val constantCodes = connectivityConstantCodes()
        assertTrue(
            constantCodes.isNotEmpty(),
            "Could not parse CONNECTIVITY_ERROR_CODES from Constants.kt; the guard would pass vacuously.",
        )

        // Parity is asserted per statement, not over their union: removing a code from exactly one
        // statement must fail. A union would keep matching the constant while that statement silently
        // changed behaviour.
        CONNECTIVITY_STATEMENTS.forEach { statement ->
            assertEquals(
                constantCodes,
                connectivitySqlLiterals(statement),
                "The '$statement' statement in database.sq diverged from CONNECTIVITY_ERROR_CODES.",
            )
        }
    }

    @Test
    fun aSingleDivergentStatementIsRejected() {
        // Failing fixture: the guard must fire when one statement loses a code while the others keep
        // it, which a union-based check could not detect. The mutation is fed through the same
        // per-statement comparison the real check uses.
        val bodies = connectivityStatementBodies()
        val target = CONNECTIVITY_STATEMENTS.first()
        val mutated = bodies + (target to mutate(bodies.getValue(target)))

        val divergent =
            CONNECTIVITY_STATEMENTS.filter { statement ->
                connectivityCodesIn(mutated.getValue(statement)) != REFERENCE_CODES
            }

        assertEquals(
            listOf(target),
            divergent,
            "The guard must reject exactly the single statement that omits a connectivity code.",
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

    private fun connectivitySqlLiterals(statement: String): Set<String> =
        connectivityCodesIn(connectivityStatementBodies().getValue(statement))

    private fun connectivityCodesIn(body: String): Set<String> =
        Regex("""'(REMOTE\.[A-Z_]+)'""").findAll(body).map { it.groupValues[1] }.toSet()

    private fun connectivityStatementBodies(): Map<String, String> {
        val database = File(repositoryRoot, DATABASE_SQL_PATH).readText()
        return CONNECTIVITY_STATEMENTS.associateWith { statement ->
            Regex("""$statement:([\s\S]*?);""")
                .find(database)
                ?.groupValues
                ?.get(1)
                ?: error("Could not find the '$statement' statement in database.sq")
        }
    }

    /** Drops one connectivity literal from a statement body, simulating a divergent statement. */
    private fun mutate(body: String): String = body.replaceFirst("""'$DROPPED_CODE'""", "''")

    private companion object {
        const val CONSTANTS_PATH =
            "core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/Constants.kt"
        const val DATABASE_SQL_PATH =
            "core/database/src/commonMain/sqldelight/com/ruizurraca/carapp/core/database/database.sq"
        const val DROPPED_CODE = "REMOTE.UNAVAILABLE"
        val REFERENCE_CODES = setOf("REMOTE.UNAVAILABLE", "REMOTE.DEADLINE_EXCEEDED")
        val CONNECTIVITY_STATEMENTS =
            setOf("markConnectivityFailuresDue", "countPendingSyncRows", "countRetryableSyncRows")
    }
}
