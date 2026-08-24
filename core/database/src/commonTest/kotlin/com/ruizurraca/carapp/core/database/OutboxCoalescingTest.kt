package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutboxCoalescingTest {
    private lateinit var testDatabase: TestDatabase

    @AfterTest
    fun tearDown() {
        if (::testDatabase.isInitialized) testDatabase.close()
    }

    @Test
    fun coalescingPreservesSequenceAndResetsRetryState() =
        runTest {
            val database = createDatabase()
            val queries = database.database.databaseQueries

            queries.coalesceOutbox(
                entityType = "VEHICLE",
                entityId = "vehicle-1",
                payload = "payload-v1",
                localRevision = 1,
            )
            val originalSequence = database.driver.outboxLong("seq")
            database.driver.markOutboxAttemptFailed()

            queries.coalesceOutbox(
                entityType = "VEHICLE",
                entityId = "vehicle-1",
                payload = "payload-v2",
                localRevision = 2,
            )

            assertEquals(originalSequence, database.driver.outboxLong("seq"))
            assertEquals("payload-v2", database.driver.outboxString("payload"))
            assertEquals(2L, database.driver.outboxLong("localRevision"))
            assertEquals(0L, database.driver.outboxLong("attemptCount"))
            assertEquals(0L, database.driver.outboxLong("nextAttemptAt"))
            assertNull(database.driver.outboxString("lastError"))
            assertNull(database.driver.outboxString("lastErrorCode"))
        }

    private fun createDatabase(): TestDatabase = TestDatabase.create().also { testDatabase = it }
}

private suspend fun app.cash.sqldelight.db.SqlDriver.markOutboxAttemptFailed() {
    execute(
        identifier = null,
        sql =
            "UPDATE outbox SET attemptCount = 4, nextAttemptAt = 123, " +
                "lastError = 'debug', lastErrorCode = 'REMOTE.UNKNOWN'",
        parameters = 0,
    ).await()
}

private suspend fun app.cash.sqldelight.db.SqlDriver.outboxLong(column: String): Long? =
    nullableLong("SELECT $column FROM outbox WHERE entityType = 'VEHICLE' AND entityId = 'vehicle-1'")

private suspend fun app.cash.sqldelight.db.SqlDriver.outboxString(column: String): String? =
    nullableString("SELECT $column FROM outbox WHERE entityType = 'VEHICLE' AND entityId = 'vehicle-1'")
