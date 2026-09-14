package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncDatabaseAccessTest {
    @Test
    fun dueOutboxAndSuccessfulAckUseOneDatabaseOwnedTransition() =
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
                val access = SyncDatabaseAccess(testDatabase.database)

                val row = access.dueOutbox(now = 100, limit = 50).single()
                assertNull(
                    testDatabase.driver.nullableString("SELECT cycleId FROM outbox WHERE entityId = 'vehicle-1'"),
                )
                access.markSyncing(row.entityType, row.entityId)
                access.confirmPush(row.entityType, row.entityId, row.localRevision, serverUpdatedAt = 200)

                val vehicle =
                    testDatabase.database.databaseQueries
                        .selectVehicleById("vehicle-1")
                        .awaitAsOneOrNull()
                assertEquals("SYNCED", vehicle?.syncState)
                assertEquals(200, vehicle?.serverUpdatedAt)
                assertNull(
                    testDatabase.database.databaseQueries
                        .selectOutboxByEntity(
                            "VEHICLE",
                            "vehicle-1",
                        ).awaitAsOneOrNull(),
                )
            } finally {
                testDatabase.close()
            }
        }

    @Test
    fun failedPushStoresCycleCorrelationAndRetryContext() =
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
                val access = SyncDatabaseAccess(testDatabase.database)

                access.failPush(
                    entityType = "VEHICLE",
                    entityId = "vehicle-1",
                    pushedLocalRevision = 1,
                    attemptCount = 3,
                    nextAttemptAt = 900,
                    errorCode = "REMOTE.UNKNOWN",
                    poisoned = false,
                    cycleId = "cycle-17",
                )

                assertEquals(
                    "cycle-17",
                    testDatabase.driver.nullableString("SELECT cycleId FROM outbox WHERE entityId = 'vehicle-1'"),
                )
                assertEquals(
                    "REMOTE.UNKNOWN",
                    testDatabase.driver.nullableString("SELECT lastErrorCode FROM outbox WHERE entityId = 'vehicle-1'"),
                )
                assertEquals(
                    3,
                    testDatabase.driver.nullableLong("SELECT attemptCount FROM outbox WHERE entityId = 'vehicle-1'"),
                )
                assertEquals(
                    "FAILED_RETRYABLE",
                    testDatabase.driver.nullableString("SELECT syncState FROM vehicle WHERE id = 'vehicle-1'"),
                )
                val debugLines = access.debugLines()
                assertTrue(debugLines.any { it.startsWith("outbox ") && "cycle=cycle-17" in it })
                assertTrue(debugLines.any { it == "row type=VEHICLE id=vehicle-1 state=FAILED_RETRYABLE" })

                access.resetFailed(now = 1_000)
                assertNull(
                    testDatabase.driver.nullableString("SELECT cycleId FROM outbox WHERE entityId = 'vehicle-1'"),
                )
                assertNull(
                    testDatabase.driver.nullableString("SELECT lastErrorCode FROM outbox WHERE entityId = 'vehicle-1'"),
                )
                assertEquals(
                    0,
                    testDatabase.driver.nullableLong("SELECT attemptCount FROM outbox WHERE entityId = 'vehicle-1'"),
                )
                assertEquals(
                    "PENDING",
                    testDatabase.driver.nullableString("SELECT syncState FROM vehicle WHERE id = 'vehicle-1'"),
                )
            } finally {
                testDatabase.close()
            }
        }

    @Test
    fun failureForAnOlderRevisionDoesNotStampTheNewerRow() =
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
                val access = SyncDatabaseAccess(testDatabase.database)

                // A local edit bumps the row and outbox revisions while the in-flight push targets 1.
                testDatabase.driver
                    .execute(
                        identifier = null,
                        sql = "UPDATE vehicle SET localRevision = 2, syncState = 'PENDING' WHERE id = 'vehicle-1'",
                        parameters = 0,
                    ).await()
                testDatabase.database.databaseQueries.coalesceOutbox(
                    entityType = "VEHICLE",
                    entityId = "vehicle-1",
                    payload = "{\"deleted\":false}",
                    localRevision = 2,
                )
                // Seed the newer row with unrelated retry context that the stale failure must not touch.
                testDatabase.driver
                    .execute(
                        identifier = null,
                        sql =
                            "UPDATE outbox SET attemptCount = 5, lastErrorCode = 'REMOTE.OLDER' " +
                                "WHERE entityId = 'vehicle-1'",
                        parameters = 0,
                    ).await()

                access.failPush(
                    entityType = "VEHICLE",
                    entityId = "vehicle-1",
                    pushedLocalRevision = 1,
                    attemptCount = 3,
                    nextAttemptAt = 900,
                    errorCode = "REMOTE.UNKNOWN",
                    poisoned = false,
                    cycleId = "cycle-old",
                )

                assertEquals(
                    5,
                    testDatabase.driver.nullableLong("SELECT attemptCount FROM outbox WHERE entityId = 'vehicle-1'"),
                )
                assertEquals(
                    "REMOTE.OLDER",
                    testDatabase.driver.nullableString("SELECT lastErrorCode FROM outbox WHERE entityId = 'vehicle-1'"),
                )
                assertEquals(
                    "PENDING",
                    testDatabase.driver.nullableString("SELECT syncState FROM vehicle WHERE id = 'vehicle-1'"),
                )
            } finally {
                testDatabase.close()
            }
        }

    @Test
    fun poisonedRowsAreExcludedFromDueOutboxUntilResetFailed() =
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
                val access = SyncDatabaseAccess(testDatabase.database)
                access.failPush(
                    entityType = "VEHICLE",
                    entityId = "vehicle-1",
                    pushedLocalRevision = 1,
                    attemptCount = 8,
                    nextAttemptAt = 50,
                    errorCode = "REMOTE.INVALID_ARGUMENT",
                    poisoned = true,
                    cycleId = "cycle-poison",
                )

                // `nextAttemptAt` is in the past, yet a poisoned row MUST NOT be selected again; it is
                // never retried automatically (`§7`).
                assertEquals(emptyList(), access.dueOutbox(now = 100, limit = 50))
                assertEquals(1, access.counts().poisoned)

                // `markSyncing` MUST fail closed: it MUST NOT move a FAILED_POISONED row to SYNCING,
                // which would drop it from `counts().poisoned` and from the aggregate status.
                access.markSyncing("VEHICLE", "vehicle-1")
                assertEquals(
                    "FAILED_POISONED",
                    testDatabase.driver.nullableString("SELECT syncState FROM vehicle WHERE id = 'vehicle-1'"),
                )
                assertEquals(1, access.counts().poisoned)

                access.resetFailed(now = 100)

                assertEquals(listOf("vehicle-1"), access.dueOutbox(now = 100, limit = 50).map { it.entityId })
            } finally {
                testDatabase.close()
            }
        }

    @Test
    fun connectivityRecoveryMakesFailuresDueWithoutResettingAttempts() =
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
                val access = SyncDatabaseAccess(testDatabase.database)
                access.failPush("VEHICLE", "vehicle-1", 1, 7, 900, "REMOTE.UNAVAILABLE", false, "cycle-18")

                access.markConnectivityFailuresDue(now = 100)

                val due = access.dueOutbox(now = 100, limit = 50).single()
                assertEquals(7, due.attemptCount)
                assertEquals(100, due.nextAttemptAt)
            } finally {
                testDatabase.close()
            }
        }

    @Test
    fun pullPagePersistsQuarantineAndCursorAtomically() =
        runTest {
            val testDatabase = TestDatabase.create()
            try {
                val access = SyncDatabaseAccess(testDatabase.database)
                access.applyPullPage(
                    entityType = "VEHICLE",
                    vehicles = emptyList(),
                    fuelEntries = emptyList(),
                    quarantines =
                        listOf(
                            QuarantineDatabaseWrite(
                                entityType = "VEHICLE",
                                entityId = "vehicle-bad",
                                reason = "MalformedPayload",
                                schemaVersion = 0,
                                serverUpdatedAt = 300,
                                rawJson = "{broken",
                                createdAt = 400,
                            ),
                        ),
                    cursor = SyncCursorDatabaseRow(300, "vehicle-bad"),
                )

                assertEquals(
                    "MalformedPayload",
                    testDatabase.driver.nullableString(
                        "SELECT reason FROM quarantine WHERE entityType = 'VEHICLE' AND entityId = 'vehicle-bad'",
                    ),
                )
                assertEquals("vehicle-bad", access.cursor("VEHICLE")?.lastDocumentId)
                val debugLines = access.debugLines()
                assertTrue(debugLines.any { it.startsWith("cursor type=VEHICLE ") })
                assertTrue(debugLines.any { it.startsWith("quarantine type=VEHICLE id=vehicle-bad ") })
                assertFalse(debugLines.joinToString().contains("{broken"))
            } finally {
                testDatabase.close()
            }
        }

    @Test
    fun remoteVehicleAndFuelEntryBecomeSyncedLocalRows() =
        runTest {
            val testDatabase = TestDatabase.create()
            try {
                val access = SyncDatabaseAccess(testDatabase.database)
                access.applyPullPage(
                    entityType = "VEHICLE",
                    vehicles = listOf(remoteVehicleWrite()),
                    fuelEntries = emptyList(),
                    quarantines = emptyList(),
                    cursor = SyncCursorDatabaseRow(200, "vehicle-1"),
                )
                access.applyPullPage(
                    entityType = "FUEL_ENTRY",
                    vehicles = emptyList(),
                    fuelEntries = listOf(remoteFuelEntryWrite()),
                    quarantines = emptyList(),
                    cursor = SyncCursorDatabaseRow(250, "entry-1"),
                )

                assertEquals(
                    "SYNCED",
                    testDatabase.database.databaseQueries
                        .selectVehicleById("vehicle-1")
                        .awaitAsOneOrNull()
                        ?.syncState,
                )
                assertEquals(
                    "SYNCED",
                    testDatabase.database.databaseQueries
                        .selectFuelEntryById("entry-1")
                        .awaitAsOneOrNull()
                        ?.syncState,
                )
                assertEquals(
                    20,
                    testDatabase.database.databaseQueries
                        .selectVehicleById(
                            "vehicle-1",
                        ).awaitAsOneOrNull()
                        ?.currentOdometerKm,
                )
                assertTrue(access.debugLines().any { it == "row type=FUEL_ENTRY id=entry-1 state=SYNCED" })
            } finally {
                testDatabase.close()
            }
        }

    private fun remoteVehicleWrite(): RemoteVehicleDatabaseWrite =
        RemoteVehicleDatabaseWrite(
            id = "vehicle-1",
            ownerId = "owner-1",
            name = "Roadster",
            nameFold = "roadster",
            initialOdometerKm = 10,
            brand = null,
            model = null,
            fuelType = "GASOLINE",
            createdAt = 100,
            updatedAt = 200,
            serverUpdatedAt = 200,
            deletedAt = null,
            schemaVersion = 1,
        )

    private fun remoteFuelEntryWrite(): RemoteFuelEntryDatabaseWrite =
        RemoteFuelEntryDatabaseWrite(
            id = "entry-1",
            ownerId = "owner-1",
            vehicleId = "vehicle-1",
            date = 150,
            odometerKm = 20,
            litersScaled = 10_000,
            pricePerLiterScaled = 1_500,
            totalCostMinor = 1_500,
            currency = "EUR",
            isFullTank = true,
            hasMissedEntries = false,
            notes = null,
            createdAt = 150,
            updatedAt = 250,
            serverUpdatedAt = 250,
            deletedAt = null,
            schemaVersion = 1,
        )
}
