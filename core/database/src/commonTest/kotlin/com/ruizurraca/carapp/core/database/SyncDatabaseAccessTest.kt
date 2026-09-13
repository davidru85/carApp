package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
                assertNull(testDatabase.driver.nullableString("SELECT cycleId FROM outbox WHERE entityId = 'vehicle-1'"))
                access.markSyncing(row.entityType, row.entityId)
                access.confirmPush(row.entityType, row.entityId, row.localRevision, serverUpdatedAt = 200)

                val vehicle = testDatabase.database.databaseQueries.selectVehicleById("vehicle-1").awaitAsOneOrNull()
                assertEquals("SYNCED", vehicle?.syncState)
                assertEquals(200, vehicle?.serverUpdatedAt)
                assertNull(testDatabase.database.databaseQueries.selectOutboxByEntity("VEHICLE", "vehicle-1").awaitAsOneOrNull())
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
                    attemptCount = 3,
                    nextAttemptAt = 900,
                    errorCode = "REMOTE.UNKNOWN",
                    poisoned = false,
                    cycleId = "cycle-17",
                )

                assertEquals("cycle-17", testDatabase.driver.nullableString("SELECT cycleId FROM outbox WHERE entityId = 'vehicle-1'"))
                assertEquals("REMOTE.UNKNOWN", testDatabase.driver.nullableString("SELECT lastErrorCode FROM outbox WHERE entityId = 'vehicle-1'"))
                assertEquals(3, testDatabase.driver.nullableLong("SELECT attemptCount FROM outbox WHERE entityId = 'vehicle-1'"))
                assertEquals("FAILED_RETRYABLE", testDatabase.driver.nullableString("SELECT syncState FROM vehicle WHERE id = 'vehicle-1'"))
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
                    vehicles =
                        listOf(
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
                            ),
                        ),
                    fuelEntries = emptyList(),
                    quarantines = emptyList(),
                    cursor = SyncCursorDatabaseRow(200, "vehicle-1"),
                )
                access.applyPullPage(
                    entityType = "FUEL_ENTRY",
                    vehicles = emptyList(),
                    fuelEntries =
                        listOf(
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
                            ),
                        ),
                    quarantines = emptyList(),
                    cursor = SyncCursorDatabaseRow(250, "entry-1"),
                )

                assertEquals("SYNCED", testDatabase.database.databaseQueries.selectVehicleById("vehicle-1").awaitAsOneOrNull()?.syncState)
                assertEquals("SYNCED", testDatabase.database.databaseQueries.selectFuelEntryById("entry-1").awaitAsOneOrNull()?.syncState)
                assertEquals(20, testDatabase.database.databaseQueries.selectVehicleById("vehicle-1").awaitAsOneOrNull()?.currentOdometerKm)
            } finally {
                testDatabase.close()
            }
        }
}
