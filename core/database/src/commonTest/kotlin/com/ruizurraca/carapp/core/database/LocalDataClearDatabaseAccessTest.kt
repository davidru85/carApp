package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class LocalDataClearDatabaseAccessTest {
    private lateinit var testDatabase: TestDatabase

    @AfterTest
    fun tearDown() {
        if (::testDatabase.isInitialized) testDatabase.close()
    }

    @Test
    fun clearAllLocalDataEmptiesEveryLocalTableInOneTransaction() =
        runTest {
            val database = createDatabase()
            val queries = database.database.databaseQueries
            queries.insertVehicleForClear("vehicle-1", "owner-1")
            queries.insertFuelEntryForClear("entry-1", "owner-1", "vehicle-1")
            queries.coalesceOutbox("VEHICLE", "vehicle-1", "{}", 1)
            queries.upsertSettings("EUR", "KM", "LITER", 0L)
            queries.upsertAnonymousReminder("owner-1", 0L)
            queries.insertAccountConversionOperation("owner-1")
            queries.insertAccountConversionSnapshot("VEHICLE", "vehicle-1", "{}", 1, 1)
            database.driver.seedSyncCursor()
            database.driver.seedQuarantine()
            queries.nextLocalMutationSequence().awaitAsOne()
            val access = LocalDataClearDatabaseAccess(database.database)

            access.clearAllLocalData()

            assertEquals(0L, database.driver.nullableLong("SELECT COUNT(*) FROM vehicle"))
            assertEquals(0L, database.driver.nullableLong("SELECT COUNT(*) FROM fuel_entry"))
            assertEquals(0L, database.driver.nullableLong("SELECT COUNT(*) FROM outbox"))
            assertEquals(0L, database.driver.nullableLong("SELECT COUNT(*) FROM sync_cursor"))
            assertEquals(0L, database.driver.nullableLong("SELECT COUNT(*) FROM quarantine"))
            assertEquals(0L, database.driver.nullableLong("SELECT COUNT(*) FROM user_settings"))
            assertEquals(0L, database.driver.nullableLong("SELECT COUNT(*) FROM anonymous_reminder"))
            assertEquals(0L, database.driver.nullableLong("SELECT COUNT(*) FROM account_conversion_operation"))
            assertEquals(0L, database.driver.nullableLong("SELECT COUNT(*) FROM account_conversion_snapshot"))
            // `local_sequence` is a control row, so it is reset to its canonical initial state
            // rather than deleted: a missing row could not assign `localMutationSeq` at all.
            assertEquals(1L, database.driver.nullableLong("SELECT COUNT(*) FROM local_sequence"))
            assertEquals(1L, access.localSequenceNext())
        }

    @Test
    fun clearAllLocalDataResetsAnAdvancedLocalSequence() =
        runTest {
            val database = createDatabase()
            val queries = database.database.databaseQueries
            repeat(5) { queries.nextLocalMutationSequence().awaitAsOne() }
            val access = LocalDataClearDatabaseAccess(database.database)
            assertEquals(6L, access.localSequenceNext())

            access.clearAllLocalData()

            assertEquals(1L, access.localSequenceNext())
            // The next assignment starts the sequence again from its initial state.
            assertEquals(2L, queries.nextLocalMutationSequence().awaitAsOne())
        }

    @Test
    fun aFailedClearRollsBackEveryDeletion() =
        runTest {
            val database = createDatabase()
            val queries = database.database.databaseQueries
            queries.insertVehicleForClear("vehicle-1", "owner-1")
            queries.insertFuelEntryForClear("entry-1", "owner-1", "vehicle-1")
            queries.coalesceOutbox("VEHICLE", "vehicle-1", "{}", 1)
            queries.upsertSettings("EUR", "KM", "LITER", 0L)
            database.driver.seedQuarantine()
            repeat(3) { queries.nextLocalMutationSequence().awaitAsOne() }
            // The clear deletes `quarantine` after the vehicle, fuel entry and outbox rows, so a
            // failure there proves the earlier deletions are inside the same transaction.
            database.driver.failOnQuarantineDelete()
            val access = LocalDataClearDatabaseAccess(database.database)

            assertFails { access.clearAllLocalData() }

            assertEquals(1L, database.driver.nullableLong("SELECT COUNT(*) FROM vehicle"))
            assertEquals(1L, database.driver.nullableLong("SELECT COUNT(*) FROM fuel_entry"))
            assertEquals(1L, database.driver.nullableLong("SELECT COUNT(*) FROM outbox"))
            assertEquals(1L, database.driver.nullableLong("SELECT COUNT(*) FROM user_settings"))
            assertEquals(1L, database.driver.nullableLong("SELECT COUNT(*) FROM quarantine"))
            assertEquals(4L, access.localSequenceNext())
        }

    @Test
    fun pendingOutboxCountCountsEveryOutboxRow() =
        runTest {
            val database = createDatabase()
            val queries = database.database.databaseQueries
            queries.coalesceOutbox("VEHICLE", "vehicle-1", "{}", 1)
            queries.coalesceOutbox("FUEL_ENTRY", "entry-1", "{}", 1)
            val access = LocalDataClearDatabaseAccess(database.database)

            assertEquals(2L, access.pendingOutboxCount())
        }

    @Test
    fun pendingOutboxCountIsZeroWhenTheOutboxIsEmpty() =
        runTest {
            val database = createDatabase()
            val access = LocalDataClearDatabaseAccess(database.database)

            assertEquals(0L, access.pendingOutboxCount())
        }

    private fun createDatabase(): TestDatabase = TestDatabase.create().also { testDatabase = it }
}

private suspend fun DatabaseQueries.insertVehicleForClear(
    id: String,
    ownerId: String,
) {
    insertVehicleRow(
        id = id,
        ownerId = ownerId,
        name = id,
        nameFold = id,
        initialOdometerKm = 0,
        currentOdometerKm = 0,
        brand = null,
        model = null,
        fuelType = "GASOLINE",
        createdAt = 1,
        updatedAt = 1,
        serverUpdatedAt = null,
        deleted = 0,
        deletedAt = null,
        syncState = "PENDING",
        localRevision = 1,
        localMutationSeq = 1,
        schemaVersion = 1,
    )
}

private suspend fun DatabaseQueries.insertFuelEntryForClear(
    id: String,
    ownerId: String,
    vehicleId: String,
) {
    insertFuelEntryRow(
        id = id,
        ownerId = ownerId,
        vehicleId = vehicleId,
        date = 1,
        odometerKm = 1,
        litersScaled = 1,
        pricePerLiterScaled = 1,
        totalCostMinor = 1,
        currency = "EUR",
        isFullTank = 1,
        hasMissedEntries = 0,
        odometerInconsistent = 0,
        notes = null,
        createdAt = 1,
        updatedAt = 1,
        serverUpdatedAt = null,
        deleted = 0,
        deletedAt = null,
        syncState = "PENDING",
        localRevision = 1,
        localMutationSeq = 1,
        schemaVersion = 1,
    )
}

private suspend fun SqlDriver.exec(sql: String) {
    execute(identifier = null, sql = sql, parameters = 0).await()
}

private suspend fun SqlDriver.seedSyncCursor() {
    exec(
        """
        INSERT INTO sync_cursor(entityType, lastServerUpdatedAt, lastDocumentId)
        VALUES ('VEHICLE', 1, 'vehicle-1')
        """.trimIndent(),
    )
}

private suspend fun SqlDriver.seedQuarantine() {
    exec(
        """
        INSERT INTO quarantine(
          entityType, entityId, reason, schemaVersion, serverUpdatedAt, rawJson, createdAt
        )
        VALUES ('VEHICLE', 'vehicle-9', 'MalformedPayload', 1, 1, '{}', 1)
        """.trimIndent(),
    )
}

private suspend fun SqlDriver.failOnQuarantineDelete() {
    exec(
        """
        CREATE TRIGGER fail_quarantine_delete BEFORE DELETE ON quarantine
        BEGIN SELECT RAISE(ABORT, 'local clear failed'); END
        """.trimIndent(),
    )
}
