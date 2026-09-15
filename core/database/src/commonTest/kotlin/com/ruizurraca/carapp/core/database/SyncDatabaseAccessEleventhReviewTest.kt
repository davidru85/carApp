package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.SuspendingTransacter
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncDatabaseAccessEleventhReviewTest {
    @Test
    fun markSyncingWithAStaleRevisionKeepsTheEditedVehicleAndFuelEntryPending() =
        runTest {
            val testDatabase = TestDatabase.create()
            try {
                testDatabase.insertVehicleForMutationTest()
                testDatabase.insertFuelEntryForMutationTest(
                    id = "entry-1",
                    date = 1,
                    createdAt = 1,
                    odometerKm = 100,
                )
                val queries = testDatabase.database.databaseQueries
                queries.coalesceOutbox("VEHICLE", "vehicle-1", "{\"deleted\":false}", 1)
                queries.coalesceOutbox("FUEL_ENTRY", "entry-1", "{\"deleted\":false}", 1)
                val access = SyncDatabaseAccess(testDatabase.database)
                val selected = access.dueOutbox(now = 100, limit = 50)

                testDatabase.driver
                    .execute(
                        identifier = null,
                        sql = "UPDATE vehicle SET localRevision = 2, syncState = 'PENDING' WHERE id = 'vehicle-1'",
                        parameters = 0,
                    ).await()
                testDatabase.driver
                    .execute(
                        identifier = null,
                        sql = "UPDATE fuel_entry SET localRevision = 2, syncState = 'PENDING' WHERE id = 'entry-1'",
                        parameters = 0,
                    ).await()

                selected.forEach { row -> access.markSyncing(row.entityType, row.entityId, row.localRevision) }

                assertEquals(
                    "PENDING",
                    testDatabase.driver.nullableString("SELECT syncState FROM vehicle WHERE id = 'vehicle-1'"),
                )
                assertEquals(
                    "PENDING",
                    testDatabase.driver.nullableString("SELECT syncState FROM fuel_entry WHERE id = 'entry-1'"),
                )
            } finally {
                testDatabase.close()
            }
        }

    @Test
    fun aggregateCountsReadAllThreeBucketsInsideOneTransaction() =
        runTest {
            val testDatabase = TestDatabase.create()
            try {
                testDatabase.insertVehicleForMutationTest()
                testDatabase.database.databaseQueries.coalesceOutbox(
                    entityType = "VEHICLE",
                    entityId = "vehicle-1",
                    payload = "{\"deleted\":false}",
                    localRevision = 1,
                )
                val transactionCountingDriver = TransactionCountingSqlDriver(testDatabase.driver)
                val access = SyncDatabaseAccess(AppDatabase(transactionCountingDriver))

                assertEquals(SyncDatabaseCounts(pending = 1, retryable = 0, poisoned = 0), access.counts())
                assertEquals(
                    1,
                    transactionCountingDriver.newTransactionCalls,
                    "the three aggregate reads must share one database snapshot",
                )
            } finally {
                testDatabase.close()
            }
        }

    @Test
    fun repeatedQuarantineDeliveryPreservesTheOriginalCreatedAt() =
        runTest {
            val testDatabase = TestDatabase.create()
            try {
                val access = SyncDatabaseAccess(testDatabase.database)

                fun quarantine(createdAt: Long) =
                    QuarantineDatabaseWrite(
                        entityType = "VEHICLE",
                        entityId = "vehicle-bad",
                        reason = "MalformedPayload",
                        schemaVersion = 1,
                        serverUpdatedAt = 300,
                        rawJson = "{broken",
                        createdAt = createdAt,
                    )

                access.applyPullPage(
                    entityType = "VEHICLE",
                    vehicles = emptyList(),
                    fuelEntries = emptyList(),
                    quarantines = listOf(quarantine(createdAt = 400)),
                    cursor = SyncCursorDatabaseRow(300, "vehicle-bad"),
                )
                access.applyPullPage(
                    entityType = "VEHICLE",
                    vehicles = emptyList(),
                    fuelEntries = emptyList(),
                    quarantines = listOf(quarantine(createdAt = 900)),
                    cursor = SyncCursorDatabaseRow(300, "vehicle-bad"),
                )

                assertEquals(
                    400,
                    testDatabase.driver.nullableLong(
                        "SELECT createdAt FROM quarantine WHERE entityType = 'VEHICLE' AND entityId = 'vehicle-bad'",
                    ),
                )
            } finally {
                testDatabase.close()
            }
        }
}

private class TransactionCountingSqlDriver(
    private val delegate: SqlDriver,
) : SqlDriver by delegate,
    SuspendingTransacter.TransactionDispatcher {
    var newTransactionCalls: Int = 0
        private set

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        newTransactionCalls += 1
        return delegate.newTransaction()
    }

    override suspend fun <R> dispatch(transaction: suspend () -> R): R =
        (delegate as SuspendingTransacter.TransactionDispatcher).dispatch(transaction)
}
