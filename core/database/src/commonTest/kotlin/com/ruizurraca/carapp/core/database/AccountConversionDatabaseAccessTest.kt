package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AccountConversionDatabaseAccessTest {
    @Test
    fun theSchemaVersionCarriesTheDurableConversionMarker() {
        assertEquals(4L, AppDatabase.Schema.version)
    }

    @Test
    fun capturePersistsEveryAnonymousRowIncludingTombstonesAndExcludesOtherOwners() =
        runTest {
            val testDatabase = TestDatabase.create()
            try {
                val queries = testDatabase.database.databaseQueries
                queries.insertVehicleForConversion("anonymous-active", ANONYMOUS_UID, deletedAt = null)
                queries.insertVehicleForConversion("anonymous-deleted", ANONYMOUS_UID, deletedAt = 20L)
                queries.insertVehicleForConversion("other-owner", "other-uid", deletedAt = null)
                queries.insertFuelEntryForConversion("anonymous-entry", ANONYMOUS_UID, "anonymous-active", null)
                val access = AccountConversionDatabaseAccess(testDatabase.database)

                val operation =
                    access.captureIfAbsent(
                        anonymousUid = ANONYMOUS_UID,
                        vehiclePayload = { row -> "vehicle:${row.id}" },
                        fuelEntryPayload = { row -> "fuel:${row.id}" },
                    )

                assertEquals(ANONYMOUS_UID, operation.anonymousUid)
                assertEquals(AccountConversionPhase.SNAPSHOT_CAPTURED, operation.phase)
                assertEquals(
                    listOf("anonymous-active", "anonymous-deleted", "anonymous-entry"),
                    operation.snapshots.map(AccountConversionSnapshotRow::entityId),
                )
                assertEquals(listOf("VEHICLE", "VEHICLE", "FUEL_ENTRY"), operation.snapshots.map { it.entityType })
            } finally {
                testDatabase.close()
            }
        }

    @Test
    fun cleanupTicketAndSnapshotRemainDurableUntilTheOperationIsCleared() =
        runTest {
            val testDatabase = TestDatabase.create()
            try {
                val queries = testDatabase.database.databaseQueries
                queries.insertVehicleForConversion("anonymous-active", ANONYMOUS_UID, deletedAt = null)
                val access = AccountConversionDatabaseAccess(testDatabase.database)
                access.captureIfAbsent(ANONYMOUS_UID, { "vehicle:${it.id}" }, { "fuel:${it.id}" })

                access.saveCleanupTicket(RAW_TICKET)

                val reloaded = assertNotNull(AccountConversionDatabaseAccess(testDatabase.database).load())
                assertEquals(RAW_TICKET, reloaded.cleanupTicket)
                assertEquals("anonymous-active", reloaded.snapshots.single().entityId)

                access.clear()
                assertNull(access.load())
            } finally {
                testDatabase.close()
            }
        }

    @Test
    fun localReplacementAtomicallyInstallsOnlyTheCapturedPermanentSnapshot() =
        runTest {
            val testDatabase = TestDatabase.create()
            try {
                val queries = testDatabase.database.databaseQueries
                queries.insertVehicleForConversion("anonymous-active", ANONYMOUS_UID, deletedAt = null)
                queries.insertVehicleForConversion("stale-permanent", PERMANENT_UID, deletedAt = null)
                val access = AccountConversionDatabaseAccess(testDatabase.database)
                access.captureIfAbsent(ANONYMOUS_UID, { "vehicle:${it.id}" }, { "fuel:${it.id}" })
                access.saveCleanupTicket(RAW_TICKET)
                access.savePermanentUid(PERMANENT_UID)
                access.markRemoteReplaced()

                access.replaceLocalSnapshot(
                    permanentUid = PERMANENT_UID,
                    vehicles = listOf(vehicleRow("anonymous-active", PERMANENT_UID, serverUpdatedAt = 99L)),
                    fuelEntries = emptyList(),
                )

                val rows = queries.selectVehiclesByOwner(PERMANENT_UID, includeDeleted = 1L).awaitAsList()
                assertEquals(listOf("anonymous-active"), rows.map { it.id })
                assertEquals("SYNCED", rows.single().syncState)
                assertEquals(99L, rows.single().serverUpdatedAt)
                assertEquals(AccountConversionPhase.LOCAL_REPLACED, access.load()?.phase)
                assertEquals(0L, queries.countRowsOwnedBy(ANONYMOUS_UID).awaitAsOne())
            } finally {
                testDatabase.close()
            }
        }

    private companion object {
        const val ANONYMOUS_UID = "anonymous-owner"
        const val PERMANENT_UID = "permanent-owner"
        const val RAW_TICKET = "0123456789012345678901234567890123456789012"
    }
}

private suspend fun DatabaseQueries.insertVehicleForConversion(
    id: String,
    ownerId: String,
    deletedAt: Long?,
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
        updatedAt = deletedAt ?: 1,
        serverUpdatedAt = null,
        deleted = if (deletedAt == null) 0 else 1,
        deletedAt = deletedAt,
        syncState = "PENDING",
        localRevision = 1,
        localMutationSeq = 1,
        schemaVersion = 1,
    )
}

private suspend fun DatabaseQueries.insertFuelEntryForConversion(
    id: String,
    ownerId: String,
    vehicleId: String,
    deletedAt: Long?,
) {
    insertFuelEntryRow(
        id = id,
        ownerId = ownerId,
        vehicleId = vehicleId,
        date = 1,
        odometerKm = 10,
        litersScaled = 1_000,
        pricePerLiterScaled = 1_000,
        totalCostMinor = 100,
        currency = "EUR",
        isFullTank = 1,
        hasMissedEntries = 0,
        odometerInconsistent = 0,
        notes = null,
        createdAt = 1,
        updatedAt = deletedAt ?: 1,
        serverUpdatedAt = null,
        deleted = if (deletedAt == null) 0 else 1,
        deletedAt = deletedAt,
        syncState = "PENDING",
        localRevision = 1,
        localMutationSeq = 2,
        schemaVersion = 1,
    )
}

private fun vehicleRow(
    id: String,
    ownerId: String,
    serverUpdatedAt: Long,
): VehicleDatabaseRow =
    VehicleDatabaseRow(
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
        serverUpdatedAt = serverUpdatedAt,
        deletedAt = null,
        syncState = "SYNCED",
        localRevision = 1,
        localMutationSeq = 1,
        schemaVersion = 1,
    )
