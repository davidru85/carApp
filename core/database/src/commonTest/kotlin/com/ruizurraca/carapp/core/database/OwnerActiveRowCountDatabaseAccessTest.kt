package com.ruizurraca.carapp.core.database

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `E3-09` (`D-197`, ADR-0197): the analytics count buckets come from the owner's **active** rows.
 *
 * The two new count queries are asserted at the layer that owns the statements, because the
 * `deleted = 0` predicate is the whole reason this accessor exists instead of reusing
 * `countRowsOwnedBy`: a tombstoned row is not part of the list the buckets describe, and a count that
 * included one would report an item the owner has deleted. Owner scoping is asserted too, so a count
 * cannot leak another owner's rows into a bucket.
 */
class OwnerActiveRowCountDatabaseAccessTest {
    @Test
    fun countsOnlyTheOwnersActiveRows() =
        runTest {
            val database = TestDatabase.create()
            try {
                database.seedVehicle(id = "vehicle-active", ownerId = OWNER_ID)
                database.seedVehicle(id = "vehicle-tombstone", ownerId = OWNER_ID, deleted = true)
                database.seedVehicle(id = "vehicle-other-owner", ownerId = OTHER_OWNER_ID)
                database.seedFuelEntry(id = "entry-active", ownerId = OWNER_ID)
                database.seedFuelEntry(id = "entry-tombstone", ownerId = OWNER_ID, deleted = true)

                assertEquals(
                    OwnerActiveRowCounts(vehicleCount = 1, entryCount = 1),
                    OwnerActiveRowCountDatabaseAccess(database.database).activeRowCounts(OWNER_ID),
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun anOwnerWithNoRowsCountsZero() =
        runTest {
            val database = TestDatabase.create()
            try {
                assertEquals(
                    OwnerActiveRowCounts(vehicleCount = 0, entryCount = 0),
                    OwnerActiveRowCountDatabaseAccess(database.database).activeRowCounts(OWNER_ID),
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun anOwnerWhoseEveryRowIsATombstoneCountsZero() =
        runTest {
            val database = TestDatabase.create()
            try {
                database.seedVehicle(id = "vehicle-tombstone", ownerId = OWNER_ID, deleted = true)
                database.seedFuelEntry(id = "entry-tombstone", ownerId = OWNER_ID, deleted = true)

                assertEquals(
                    OwnerActiveRowCounts(vehicleCount = 0, entryCount = 0),
                    OwnerActiveRowCountDatabaseAccess(database.database).activeRowCounts(OWNER_ID),
                )
            } finally {
                database.close()
            }
        }

    private suspend fun TestDatabase.seedVehicle(
        id: String,
        ownerId: String,
        deleted: Boolean = false,
    ) {
        database.databaseQueries.insertVehicleRow(
            id = id,
            ownerId = ownerId,
            name = "Roadster",
            nameFold = "roadster",
            initialOdometerKm = 0,
            currentOdometerKm = 0,
            brand = null,
            model = null,
            fuelType = "GASOLINE",
            createdAt = 1,
            updatedAt = 1,
            serverUpdatedAt = null,
            deleted = if (deleted) 1 else 0,
            deletedAt = if (deleted) 1 else null,
            syncState = "PENDING",
            localRevision = 1,
            localMutationSeq = 1,
            schemaVersion = 1,
        )
    }

    private suspend fun TestDatabase.seedFuelEntry(
        id: String,
        ownerId: String,
        deleted: Boolean = false,
    ) {
        database.databaseQueries.insertFuelEntryRow(
            id = id,
            ownerId = ownerId,
            vehicleId = VEHICLE_ID,
            date = 1,
            odometerKm = 100,
            litersScaled = 1_000,
            pricePerLiterScaled = 1_000,
            totalCostMinor = 100,
            currency = "EUR",
            isFullTank = 1,
            hasMissedEntries = 0,
            odometerInconsistent = 0,
            notes = null,
            createdAt = 1,
            updatedAt = 1,
            serverUpdatedAt = null,
            deleted = if (deleted) 1 else 0,
            deletedAt = if (deleted) 1 else null,
            syncState = "PENDING",
            localRevision = 1,
            localMutationSeq = 1,
            schemaVersion = 1,
        )
    }

    private companion object {
        const val OWNER_ID = "owner-1"
        const val OTHER_OWNER_ID = "owner-2"
        const val VEHICLE_ID = "vehicle-active"
    }
}
