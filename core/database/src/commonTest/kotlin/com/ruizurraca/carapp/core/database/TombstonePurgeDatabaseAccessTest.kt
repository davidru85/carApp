package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `E3-07` criteria 1, 2 and 3, at the layer that owns the statements.
 *
 * `docs/CONTRACTS.md §8` makes a tombstone purgeable locally only when `syncState == SYNCED`, when
 * `serverUpdatedAt` is older than 90 days and when no outbox row exists for it, and requires the purge
 * to run in one transaction. The age is expressed here as a cutoff instant, so this test never reads a
 * clock: it seeds `serverUpdatedAt` relative to [CUTOFF] and asserts the strict comparison the contract
 * states ("older than"), which is why [CUTOFF] itself must survive.
 *
 * The purge reads a count first and opens its transaction only when that count is non-zero (`D-194`).
 * A test that seeds only unpurgeable rows would therefore never reach the `DELETE` statements, so every
 * "kept" test proves two things separately, on both entity tables: [assertNothingPurgeable] pins the
 * count predicate with the unpurgeable rows alone, and [purgeWithAnchor] then adds one purgeable
 * [ANCHOR_ID] tombstone, so the transaction really runs both `DELETE` statements, which must remove the
 * anchor and keep every unpurgeable row.
 *
 * The transaction is asserted the only way it can be observed from outside: a `BEFORE DELETE` trigger
 * aborts the second statement, and every deletion the first statement performed must be gone with it.
 */
class TombstonePurgeDatabaseAccessTest {
    @Test
    fun anOldConfirmedVehicleTombstoneIsPurged() =
        runTest {
            val database = TestDatabase.create()
            try {
                database.seedVehicleTombstone(VEHICLE_ID, serverUpdatedAt = CUTOFF - 1)

                database.purge()

                assertNull(database.vehicleRow(VEHICLE_ID), "a confirmed tombstone older than the cutoff is purged")
            } finally {
                database.close()
            }
        }

    @Test
    fun anOldConfirmedFuelEntryTombstoneIsPurged() =
        runTest {
            val database = TestDatabase.create()
            try {
                database.seedFuelEntryTombstone(ENTRY_ID, serverUpdatedAt = CUTOFF - 1)

                database.purge()

                assertNull(database.fuelEntryRow(ENTRY_ID), "the fuel entry table is purged too")
            } finally {
                database.close()
            }
        }

    @Test
    fun aTombstoneYoungerThanTheCutoffIsKept() =
        runTest {
            val database = TestDatabase.create()
            try {
                database.seedVehicleTombstone(VEHICLE_ID, serverUpdatedAt = CUTOFF + 1)
                database.seedFuelEntryTombstone(ENTRY_ID, serverUpdatedAt = CUTOFF + 1)
                database.assertNothingPurgeable()

                database.purgeWithAnchor()

                assertNotNull(database.vehicleRow(VEHICLE_ID), "a vehicle tombstone younger than 90 days is kept")
                assertNotNull(database.fuelEntryRow(ENTRY_ID), "a fuel-entry tombstone younger than 90 days is kept")
            } finally {
                database.close()
            }
        }

    @Test
    fun aTombstoneExactlyAtTheCutoffIsKeptBecauseItIsNotOlder() =
        runTest {
            val database = TestDatabase.create()
            try {
                database.seedVehicleTombstone(VEHICLE_ID, serverUpdatedAt = CUTOFF)
                database.seedFuelEntryTombstone(ENTRY_ID, serverUpdatedAt = CUTOFF)
                database.assertNothingPurgeable()

                database.purgeWithAnchor()

                assertNotNull(
                    database.vehicleRow(VEHICLE_ID),
                    "\"older than 90 days\" is a strict comparison: exactly 90 days is not older, so it is kept",
                )
                assertNotNull(
                    database.fuelEntryRow(ENTRY_ID),
                    "the strict comparison holds for the fuel-entry statement too",
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun aConfirmedTombstoneWithoutAServerTimestampIsKept() =
        runTest {
            val database = TestDatabase.create()
            try {
                database.seedVehicleTombstone(VEHICLE_ID, serverUpdatedAt = null)
                database.seedFuelEntryTombstone(ENTRY_ID, serverUpdatedAt = null)
                database.assertNothingPurgeable()

                database.purgeWithAnchor()

                assertNotNull(
                    database.vehicleRow(VEHICLE_ID),
                    "age cannot be established from a missing serverUpdatedAt, so the row is not purgeable",
                )
                assertNotNull(
                    database.fuelEntryRow(ENTRY_ID),
                    "the missing-timestamp guard holds for the fuel-entry statement too",
                )
            } finally {
                database.close()
            }
        }

    /**
     * Criterion 3. Every non-`SYNCED` state is seeded on both tables with no outbox row, so this test
     * fails if the `syncState == SYNCED` guard is removed from either count or either `DELETE`.
     */
    @Test
    fun aPendingTombstoneIsNeverPurged() =
        runTest {
            val database = TestDatabase.create()
            try {
                UNCONFIRMED_STATES.forEachIndexed { index, state ->
                    database.seedVehicleTombstone("pending-vehicle-$index", CUTOFF - 1, syncState = state)
                    database.seedFuelEntryTombstone("pending-entry-$index", CUTOFF - 1, syncState = state)
                }
                database.assertNothingPurgeable()

                database.purgeWithAnchor()

                UNCONFIRMED_STATES.forEachIndexed { index, state ->
                    assertNotNull(
                        database.vehicleRow("pending-vehicle-$index"),
                        "an unconfirmed vehicle tombstone in $state must never be purged",
                    )
                    assertNotNull(
                        database.fuelEntryRow("pending-entry-$index"),
                        "an unconfirmed fuel-entry tombstone in $state must never be purged",
                    )
                }
            } finally {
                database.close()
            }
        }

    @Test
    fun anOldConfirmedTombstoneWithAnOutboxRowIsKept() =
        runTest {
            val database = TestDatabase.create()
            try {
                database.seedVehicleTombstone(VEHICLE_ID, serverUpdatedAt = CUTOFF - 1, outbox = true)
                database.seedFuelEntryTombstone(ENTRY_ID, serverUpdatedAt = CUTOFF - 1, outbox = true)
                database.assertNothingPurgeable()

                database.purgeWithAnchor()

                assertNotNull(database.vehicleRow(VEHICLE_ID), "a row still waiting in the outbox is not purged")
                assertNotNull(database.fuelEntryRow(ENTRY_ID), "a row still waiting in the outbox is not purged")
            } finally {
                database.close()
            }
        }

    @Test
    fun anActiveRowIsNeverPurgedEvenWhenItIsOldAndSynced() =
        runTest {
            val database = TestDatabase.create()
            try {
                database.seedActiveVehicle(VEHICLE_ID, serverUpdatedAt = CUTOFF - 1)
                database.seedActiveFuelEntry(ENTRY_ID, serverUpdatedAt = CUTOFF - 1)
                database.assertNothingPurgeable()

                database.purgeWithAnchor()

                assertNotNull(database.vehicleRow(VEHICLE_ID), "the purge removes tombstones, never live vehicles")
                assertNotNull(database.fuelEntryRow(ENTRY_ID), "the purge removes tombstones, never live fuel entries")
            } finally {
                database.close()
            }
        }

    @Test
    fun aFailedPurgeRollsBackEveryDeletion() =
        runTest {
            val database = TestDatabase.create()
            try {
                database.seedVehicleTombstone(VEHICLE_ID, serverUpdatedAt = CUTOFF - 1)
                database.seedFuelEntryTombstone(ENTRY_ID, serverUpdatedAt = CUTOFF - 1)
                // The abort is on the second statement, so a vehicle deletion that survived the
                // failure would prove the two statements were not in one transaction.
                database.failOnFuelEntryDelete()

                assertFails { database.purge() }

                assertNotNull(database.vehicleRow(VEHICLE_ID), "the vehicle deletion was rolled back with the failure")
                assertNotNull(database.fuelEntryRow(ENTRY_ID), "the failed statement deleted nothing")
            } finally {
                database.close()
            }
        }

    private suspend fun TestDatabase.purge() {
        SyncDatabaseAccess(database).purgeConfirmedTombstones(CUTOFF)
    }

    /** Pins both count predicates: the rows seeded so far must not be counted as purgeable. */
    private suspend fun TestDatabase.assertNothingPurgeable() {
        val counted =
            database.databaseQueries.countPurgeableVehicleTombstones(CUTOFF).awaitAsOne() +
                database.databaseQueries.countPurgeableFuelEntryTombstones(CUTOFF).awaitAsOne()
        assertEquals(0L, counted, "an unpurgeable row must not be counted, or the purge takes the writer for nothing")
    }

    /**
     * Adds one purgeable vehicle tombstone and purges, so the transaction opens and both `DELETE`
     * statements run against every row seeded before it.
     */
    private suspend fun TestDatabase.purgeWithAnchor() {
        seedVehicleTombstone(ANCHOR_ID, serverUpdatedAt = CUTOFF - 1, syncState = "SYNCED")
        purge()
        assertNull(vehicleRow(ANCHOR_ID), "the anchor is purged, which proves both DELETE statements ran")
    }

    private suspend fun TestDatabase.seedVehicleTombstone(
        id: String,
        serverUpdatedAt: Long?,
        syncState: String = "SYNCED",
        outbox: Boolean = false,
    ) {
        database.databaseQueries.insertVehicleRow(
            id = id,
            ownerId = OWNER_ID,
            name = "Roadster",
            nameFold = "roadster",
            initialOdometerKm = 0,
            currentOdometerKm = 0,
            brand = null,
            model = null,
            fuelType = "GASOLINE",
            createdAt = 1,
            updatedAt = 1,
            serverUpdatedAt = serverUpdatedAt,
            deleted = 1,
            deletedAt = 1,
            syncState = syncState,
            localRevision = 1,
            localMutationSeq = 1,
            schemaVersion = 1,
        )
        if (outbox) seedOutbox("VEHICLE", id)
    }

    private suspend fun TestDatabase.seedActiveVehicle(
        id: String,
        serverUpdatedAt: Long?,
    ) {
        database.databaseQueries.insertVehicleRow(
            id = id,
            ownerId = OWNER_ID,
            name = "Roadster",
            nameFold = "roadster",
            initialOdometerKm = 0,
            currentOdometerKm = 0,
            brand = null,
            model = null,
            fuelType = "GASOLINE",
            createdAt = 1,
            updatedAt = 1,
            serverUpdatedAt = serverUpdatedAt,
            deleted = 0,
            deletedAt = null,
            syncState = "SYNCED",
            localRevision = 1,
            localMutationSeq = 1,
            schemaVersion = 1,
        )
    }

    private suspend fun TestDatabase.seedFuelEntryTombstone(
        id: String,
        serverUpdatedAt: Long?,
        syncState: String = "SYNCED",
        outbox: Boolean = false,
    ) {
        seedFuelEntryRow(id, serverUpdatedAt, deleted = true, syncState = syncState)
        if (outbox) seedOutbox("FUEL_ENTRY", id)
    }

    private suspend fun TestDatabase.seedActiveFuelEntry(
        id: String,
        serverUpdatedAt: Long?,
    ) {
        seedFuelEntryRow(id, serverUpdatedAt, deleted = false, syncState = "SYNCED")
    }

    private suspend fun TestDatabase.seedFuelEntryRow(
        id: String,
        serverUpdatedAt: Long?,
        deleted: Boolean,
        syncState: String,
    ) {
        database.databaseQueries.insertFuelEntryRow(
            id = id,
            ownerId = OWNER_ID,
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
            serverUpdatedAt = serverUpdatedAt,
            deleted = if (deleted) 1 else 0,
            deletedAt = if (deleted) 1 else null,
            syncState = syncState,
            localRevision = 1,
            localMutationSeq = 1,
            schemaVersion = 1,
        )
    }

    private suspend fun TestDatabase.seedOutbox(
        entityType: String,
        entityId: String,
    ) {
        database.databaseQueries.coalesceOutbox(
            entityType = entityType,
            entityId = entityId,
            payload = "{\"deleted\":true}",
            localRevision = 1,
        )
    }

    private suspend fun TestDatabase.vehicleRow(id: String) =
        database.databaseQueries.selectVehicleById(id).awaitAsOneOrNull()

    private suspend fun TestDatabase.fuelEntryRow(id: String) =
        database.databaseQueries.selectFuelEntryById(id).awaitAsOneOrNull()

    private suspend fun TestDatabase.failOnFuelEntryDelete() {
        driver.exec(
            """
            CREATE TRIGGER fail_fuel_entry_purge BEFORE DELETE ON fuel_entry
            BEGIN SELECT RAISE(ABORT, 'purge failed'); END
            """.trimIndent(),
        )
    }

    private suspend fun SqlDriver.exec(sql: String) {
        execute(identifier = null, sql = sql, parameters = 0).await()
    }

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val ENTRY_ID = "entry-1"
        const val ANCHOR_ID = "vehicle-anchor"
        const val OWNER_ID = "owner-1"

        /** An arbitrary instant; every seeded `serverUpdatedAt` is placed relative to it. */
        const val CUTOFF = 4_063_132_800_000L

        /** Every `syncState` other than `SYNCED`, none of which may ever be purged. */
        val UNCONFIRMED_STATES = listOf("PENDING", "SYNCING", "FAILED_RETRYABLE", "FAILED_POISONED")
    }
}
