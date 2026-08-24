package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class SchemaV1Test {
    private lateinit var testDatabase: TestDatabase

    @AfterTest
    fun tearDown() {
        if (::testDatabase.isInitialized) testDatabase.close()
    }

    @Test
    fun schemaCreatesAllRequiredTables() =
        runTest {
            val driver = createDatabase().driver

            assertEquals(
                listOf(
                    "fuel_entry",
                    "local_sequence",
                    "outbox",
                    "quarantine",
                    "sync_cursor",
                    "user_settings",
                    "vehicle",
                ),
                driver.stringList(
                    "SELECT name FROM sqlite_master " +
                        "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
                ),
            )
        }

    @Test
    fun entityDeletedConstraintsRejectValuesOutsideBooleanDomain() =
        runTest {
            val driver = createDatabase().driver

            listOf(-1L, 2L).forEach { invalidDeleted ->
                assertFails { driver.insertVehicle(deleted = invalidDeleted, deletedAt = null) }
                assertFails { driver.insertFuelEntry(deleted = invalidDeleted, deletedAt = null) }
            }
        }

    @Test
    fun entityTombstoneConstraintRejectsMismatchedDeletedAt() =
        runTest {
            val driver = createDatabase().driver

            assertFails { driver.insertVehicle(deleted = 0, deletedAt = 10) }
            assertFails { driver.insertVehicle(deleted = 1, deletedAt = null) }
            assertFails { driver.insertFuelEntry(deleted = 0, deletedAt = 10) }
            assertFails { driver.insertFuelEntry(deleted = 1, deletedAt = null) }
        }

    @Test
    fun nullableServerUpdatedAtRoundTrips() =
        runTest {
            val driver = createDatabase().driver
            driver.insertVehicle(deleted = 0, deletedAt = null)
            driver.insertFuelEntry(deleted = 0, deletedAt = null)

            assertNull(driver.nullableLong("SELECT serverUpdatedAt FROM vehicle WHERE id = 'vehicle-1'"))
            assertNull(driver.nullableLong("SELECT serverUpdatedAt FROM fuel_entry WHERE id = 'entry-1'"))
        }

    @Test
    fun syncCursorRejectsUnknownEntityType() =
        runTest {
            val driver = createDatabase().driver

            assertFails {
                driver
                    .execute(
                        identifier = null,
                        sql =
                            "INSERT INTO sync_cursor(entityType, lastServerUpdatedAt, lastDocumentId) " +
                                "VALUES ('UNKNOWN', 0, '')",
                        parameters = 0,
                    ).await()
            }
        }

    @Test
    fun fuelEntryHasNoVehicleForeignKey() =
        runTest {
            val driver = createDatabase().driver

            assertEquals(emptyList(), driver.stringList("PRAGMA foreign_key_list('fuel_entry')"))
        }

    @Test
    fun vehicleNameHasNoUniqueIndex() =
        runTest {
            val driver = createDatabase().driver

            assertEquals(
                emptyList(),
                driver.stringList(
                    "SELECT name FROM pragma_index_list('vehicle') WHERE \"unique\" = 1 " +
                        "AND origin != 'pk'",
                ),
            )
        }

    @Test
    fun outboxDueIndexMatchesCommittedColumns() =
        runTest {
            val driver = createDatabase().driver

            assertEquals(
                listOf("nextAttemptAt", "seq"),
                driver.stringList("SELECT name FROM pragma_index_info('idx_outbox_due') ORDER BY seqno"),
            )
        }

    private fun createDatabase(): TestDatabase = TestDatabase.create().also { testDatabase = it }
}

internal suspend fun SqlDriver.insertVehicle(
    deleted: Long,
    deletedAt: Long?,
) {
    execute(
        identifier = null,
        sql =
            """
            INSERT INTO vehicle(
              id, ownerId, name, nameFold, initialOdometerKm, currentOdometerKm,
              fuelType, createdAt, updatedAt, serverUpdatedAt, deleted, deletedAt,
              syncState, localRevision, localMutationSeq, schemaVersion
            ) VALUES ('vehicle-1', 'LOCAL_OWNER', 'Car', 'car', 0, 0,
              'GASOLINE', 1, 1, NULL, ?, ?, 'PENDING', 1, 1, 1)
            """.trimIndent(),
        parameters = 2,
    ) {
        bindLong(0, deleted)
        bindLong(1, deletedAt)
    }.await()
}

internal suspend fun SqlDriver.insertFuelEntry(
    deleted: Long,
    deletedAt: Long?,
) {
    execute(
        identifier = null,
        sql =
            """
            INSERT INTO fuel_entry(
              id, ownerId, vehicleId, date, odometerKm, litersScaled, pricePerLiterScaled,
              totalCostMinor, currency, isFullTank, hasMissedEntries, odometerInconsistent,
              createdAt, updatedAt, serverUpdatedAt, deleted, deletedAt, syncState,
              localRevision, localMutationSeq, schemaVersion
            ) VALUES ('entry-1', 'LOCAL_OWNER', 'missing-vehicle', 1, 1, 1, 1,
              1, 'EUR', 1, 0, 0, 1, 1, NULL, ?, ?, 'PENDING', 1, 1, 1)
            """.trimIndent(),
        parameters = 2,
    ) {
        bindLong(0, deleted)
        bindLong(1, deletedAt)
    }.await()
}

private suspend fun SqlDriver.stringList(sql: String): List<String> =
    executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            app.cash.sqldelight.db.QueryResult.AsyncValue {
                val values = mutableListOf<String>()
                while (cursor.next().await()) values += requireNotNull(cursor.getString(0))
                values
            }
        },
        parameters = 0,
    ).await()

internal suspend fun SqlDriver.nullableLong(sql: String): Long? =
    executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            app.cash.sqldelight.db.QueryResult.AsyncValue {
                cursor.next().await()
                cursor.getLong(0)
            }
        },
        parameters = 0,
    ).await()

internal suspend fun SqlDriver.nullableString(sql: String): String? =
    executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            app.cash.sqldelight.db.QueryResult.AsyncValue {
                cursor.next().await()
                cursor.getString(0)
            }
        },
        parameters = 0,
    ).await()
