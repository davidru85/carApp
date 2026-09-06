package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Local owner adoption, `docs/CONTRACTS.md §11.4`, and the required sync test 14 of
 * `docs/TECHNICAL_PLAN.md`. Every assertion here is an acceptance criterion of story `E2-06`.
 */
class LocalOwnerAdoptionTest {
    private lateinit var testDatabase: TestDatabase

    @AfterTest
    fun tearDown() {
        if (::testDatabase.isInitialized) testDatabase.close()
    }

    @Test
    fun adoptionRewritesEveryLocalOwnerRowToTheNewUidAndBumpsLocalRevision() =
        runTest {
            val database = createDatabase()
            database.seedLocalOwnerVehicle(id = "vehicle-1", localMutationSeq = 1, localRevision = 3)
            database.seedLocalOwnerFuelEntry(id = "entry-1", vehicleId = "vehicle-1", localMutationSeq = 2)

            database.mutations().adoptLocalOwner(NEW_UID, ::vehiclePayload, ::fuelEntryPayload)

            assertEquals(0L, database.count("SELECT COUNT(*) FROM vehicle WHERE ownerId = 'LOCAL_OWNER'"))
            assertEquals(0L, database.count("SELECT COUNT(*) FROM fuel_entry WHERE ownerId = 'LOCAL_OWNER'"))
            assertEquals(NEW_UID, database.vehicleString("ownerId", "vehicle-1"))
            assertEquals(NEW_UID, database.fuelEntryString("ownerId", "entry-1"))
            assertEquals(4L, database.vehicleLong("localRevision", "vehicle-1"))
            assertEquals(2L, database.fuelEntryLong("localRevision", "entry-1"))
        }

    @Test
    fun adoptionPreservesLocalMutationSeqAndDoesNotConsumeTheMutationCounter() =
        runTest {
            val database = createDatabase()
            database.seedLocalOwnerVehicle(id = "vehicle-1", localMutationSeq = 7)
            database.seedLocalOwnerFuelEntry(id = "entry-1", vehicleId = "vehicle-1", localMutationSeq = 9)
            val counterBefore = database.driver.nullableLong("SELECT next FROM local_sequence WHERE id = 0")

            database.mutations().adoptLocalOwner(NEW_UID, ::vehiclePayload, ::fuelEntryPayload)

            // The rewrite happened, and it left the mutation order of both rows untouched.
            assertEquals(NEW_UID, database.vehicleString("ownerId", "vehicle-1"))
            assertEquals(7L, database.vehicleLong("localMutationSeq", "vehicle-1"))
            assertEquals(9L, database.fuelEntryLong("localMutationSeq", "entry-1"))
            assertEquals(counterBefore, database.driver.nullableLong("SELECT next FROM local_sequence WHERE id = 0"))
        }

    @Test
    fun adoptionEnqueuesTheOutboxInPushDependencyOrderThenByMutationSeqAndId() =
        runTest {
            val database = createDatabase()
            // Seeded in an order that is deliberately not the expected push order, so a correct
            // result cannot come from insertion order alone.
            database.seedLocalOwnerVehicle(id = "vehicle-tombstone-b", localMutationSeq = 3, deletedAt = 40)
            database.seedLocalOwnerFuelEntry(
                id = "entry-tombstone",
                vehicleId = "vehicle-live-a",
                localMutationSeq = 8,
                deletedAt = 50,
            )
            database.seedLocalOwnerVehicle(id = "vehicle-live-b", localMutationSeq = 2)
            database.seedLocalOwnerFuelEntry(id = "entry-live-b", vehicleId = "vehicle-live-a", localMutationSeq = 6)
            database.seedLocalOwnerVehicle(id = "vehicle-live-a", localMutationSeq = 2)
            database.seedLocalOwnerFuelEntry(id = "entry-live-a", vehicleId = "vehicle-live-a", localMutationSeq = 6)
            database.seedLocalOwnerVehicle(id = "vehicle-tombstone-a", localMutationSeq = 1, deletedAt = 30)

            database.mutations().adoptLocalOwner(NEW_UID, ::vehiclePayload, ::fuelEntryPayload)

            assertEquals(
                listOf(
                    // 1. Vehicle upserts, by localMutationSeq then id.
                    "VEHICLE:vehicle-live-a",
                    "VEHICLE:vehicle-live-b",
                    // 2. Fuel entry upserts.
                    "FUEL_ENTRY:entry-live-a",
                    "FUEL_ENTRY:entry-live-b",
                    // 3. Fuel entry tombstones.
                    "FUEL_ENTRY:entry-tombstone",
                    // 4. Vehicle tombstones.
                    "VEHICLE:vehicle-tombstone-a",
                    "VEHICLE:vehicle-tombstone-b",
                ),
                database.outboxOrder(),
            )
        }

    @Test
    fun adoptionResetsEveryNonSyncedRowIncludingPoisonedAndLeavesSyncedRowsUnenqueued() =
        runTest {
            val database = createDatabase()
            database.seedLocalOwnerVehicle(id = "vehicle-pending", localMutationSeq = 1, syncState = "PENDING")
            database.seedLocalOwnerVehicle(id = "vehicle-retry", localMutationSeq = 2, syncState = "FAILED_RETRYABLE")
            database.seedLocalOwnerVehicle(id = "vehicle-poison", localMutationSeq = 3, syncState = "FAILED_POISONED")
            database.seedLocalOwnerVehicle(id = "vehicle-synced", localMutationSeq = 4, syncState = "SYNCED")

            database.mutations().adoptLocalOwner(NEW_UID, ::vehiclePayload, ::fuelEntryPayload)

            assertEquals(
                0L,
                database.count("SELECT COUNT(*) FROM vehicle WHERE syncState NOT IN ('PENDING', 'SYNCED')"),
            )
            assertEquals(
                listOf("VEHICLE:vehicle-pending", "VEHICLE:vehicle-retry", "VEHICLE:vehicle-poison"),
                database.outboxOrder(),
            )
            assertNull(database.outboxString("lastError", "vehicle-poison"))
            assertNull(database.outboxString("lastErrorCode", "vehicle-poison"))
            assertEquals(0L, database.outboxLong("attemptCount", "vehicle-poison"))
        }

    @Test
    fun adoptionSeqValuesAreMonotonicAndStartAtOneAfterTheHighestPreExistingSeq() =
        runTest {
            val database = createDatabase()
            database.seedAdoptedVehicleWithOutboxRow(id = "already-adopted", seqCount = 3)
            val highestBefore = database.driver.nullableLong("SELECT MAX(seq) FROM outbox")
            database.seedLocalOwnerVehicle(id = "vehicle-1", localMutationSeq = 1)
            database.seedLocalOwnerVehicle(id = "vehicle-2", localMutationSeq = 2)

            database.mutations().adoptLocalOwner(NEW_UID, ::vehiclePayload, ::fuelEntryPayload)

            assertEquals(
                requireNotNull(highestBefore) + 1,
                database.outboxLong("seq", "vehicle-1"),
            )
            assertEquals(
                requireNotNull(highestBefore) + 2,
                database.outboxLong("seq", "vehicle-2"),
            )
        }

    @Test
    fun runningAdoptionTwiceEnqueuesEachRowExactlyOnce() =
        runTest {
            val database = createDatabase()
            database.seedLocalOwnerVehicle(id = "vehicle-1", localMutationSeq = 1)
            database.seedLocalOwnerFuelEntry(id = "entry-1", vehicleId = "vehicle-1", localMutationSeq = 2)
            val mutations = database.mutations()

            mutations.adoptLocalOwner(NEW_UID, ::vehiclePayload, ::fuelEntryPayload)
            val afterFirstRun = database.outboxRows()
            mutations.adoptLocalOwner(NEW_UID, ::vehiclePayload, ::fuelEntryPayload)

            assertEquals(2L, database.count("SELECT COUNT(*) FROM outbox"))
            assertEquals(afterFirstRun, database.outboxRows())
            assertEquals(2L, database.vehicleLong("localRevision", "vehicle-1"))
        }

    @Test
    fun adoptionOfAPopulatedLocalOwnerDatabaseWithInterleavedEditsLosesNothing() =
        runTest {
            val database = createDatabase()
            val mutations = database.mutations()
            // A realistic offline session: two vehicles, entries created and then edited, and one
            // entry deleted, so the mutation counter interleaves the two entity types.
            database.seedLocalOwnerVehicle(id = "vehicle-a", localMutationSeq = 1)
            database.seedLocalOwnerFuelEntry(id = "entry-a1", vehicleId = "vehicle-a", localMutationSeq = 2)
            database.seedLocalOwnerVehicle(id = "vehicle-b", localMutationSeq = 3)
            database.seedLocalOwnerFuelEntry(id = "entry-b1", vehicleId = "vehicle-b", localMutationSeq = 4)
            // entry-a1 is edited after vehicle-b exists, so its mutation order overtakes both.
            database.seedLocalOwnerFuelEntry(id = "entry-a2", vehicleId = "vehicle-a", localMutationSeq = 5)
            database.seedLocalOwnerFuelEntry(
                id = "entry-b2",
                vehicleId = "vehicle-b",
                localMutationSeq = 6,
                deletedAt = 60,
            )

            val rowsBefore = database.productRowFingerprints()
            mutations.adoptLocalOwner(NEW_UID, ::vehiclePayload, ::fuelEntryPayload)

            assertEquals(rowsBefore.size, database.productRowFingerprints().size)
            assertEquals(6L, database.count("SELECT COUNT(*) FROM outbox"))
            assertEquals(
                listOf(
                    "VEHICLE:vehicle-a",
                    "VEHICLE:vehicle-b",
                    "FUEL_ENTRY:entry-a1",
                    "FUEL_ENTRY:entry-b1",
                    "FUEL_ENTRY:entry-a2",
                    "FUEL_ENTRY:entry-b2",
                ),
                database.outboxOrder(),
            )
            assertTrue(database.payloads().all { NEW_UID in it }, "every enqueued payload names the adopting UID")
        }

    private fun createDatabase(): TestDatabase = TestDatabase.create().also { testDatabase = it }

    private companion object {
        const val NEW_UID = "uid-adopting-owner"
    }
}

private fun TestDatabase.mutations(): DatabaseMutations = DatabaseMutations(database)

private fun vehiclePayload(row: VehicleDatabaseRow): String =
    """{"entityType":"VEHICLE","id":"${row.id}","ownerId":"${row.ownerId}"}"""

private fun fuelEntryPayload(row: FuelEntryDatabaseRow): String =
    """{"entityType":"FUEL_ENTRY","id":"${row.id}","ownerId":"${row.ownerId}"}"""

private suspend fun TestDatabase.count(sql: String): Long? = driver.nullableLong(sql)

private suspend fun TestDatabase.vehicleLong(
    column: String,
    id: String,
): Long? = driver.nullableLong("SELECT MAX($column) FROM vehicle WHERE id = '$id'")

private suspend fun TestDatabase.vehicleString(
    column: String,
    id: String,
): String? = driver.nullableString("SELECT MAX($column) FROM vehicle WHERE id = '$id'")

private suspend fun TestDatabase.fuelEntryLong(
    column: String,
    id: String,
): Long? = driver.nullableLong("SELECT MAX($column) FROM fuel_entry WHERE id = '$id'")

private suspend fun TestDatabase.fuelEntryString(
    column: String,
    id: String,
): String? = driver.nullableString("SELECT MAX($column) FROM fuel_entry WHERE id = '$id'")

private suspend fun TestDatabase.outboxLong(
    column: String,
    entityId: String,
): Long? = driver.nullableLong("SELECT MAX($column) FROM outbox WHERE entityId = '$entityId'")

private suspend fun TestDatabase.outboxString(
    column: String,
    entityId: String,
): String? = driver.nullableString("SELECT MAX($column) FROM outbox WHERE entityId = '$entityId'")

private suspend fun TestDatabase.outboxOrder(): List<String> =
    driver.stringList("SELECT entityType || ':' || entityId FROM outbox ORDER BY seq ASC")

private suspend fun TestDatabase.outboxRows(): List<String> =
    driver.stringList(
        "SELECT seq || '|' || entityType || '|' || entityId || '|' || localRevision FROM outbox ORDER BY seq ASC",
    )

private suspend fun TestDatabase.payloads(): List<String> =
    driver.stringList("SELECT payload FROM outbox ORDER BY seq ASC")

private suspend fun TestDatabase.productRowFingerprints(): List<String> =
    driver.stringList("SELECT 'V|' || id || '|' || localMutationSeq FROM vehicle ORDER BY id ASC") +
        driver.stringList("SELECT 'F|' || id || '|' || localMutationSeq FROM fuel_entry ORDER BY id ASC")

private suspend fun TestDatabase.seedLocalOwnerVehicle(
    id: String,
    localMutationSeq: Long,
    localRevision: Long = 1,
    syncState: String = "PENDING",
    deletedAt: Long? = null,
) {
    driver
        .execute(
            identifier = null,
            sql =
                """
                INSERT INTO vehicle(
                  id, ownerId, name, nameFold, initialOdometerKm, currentOdometerKm, fuelType,
                  createdAt, updatedAt, serverUpdatedAt, deleted, deletedAt, syncState,
                  localRevision, localMutationSeq, schemaVersion
                ) VALUES (?, 'LOCAL_OWNER', ?, ?, 0, 0, 'GASOLINE', 1, 1, NULL, ?, ?, ?, ?, ?, 1)
                """.trimIndent(),
            parameters = 8,
        ) {
            bindString(0, id)
            bindString(1, id)
            bindString(2, id)
            bindLong(3, if (deletedAt == null) 0L else 1L)
            bindLong(4, deletedAt)
            bindString(5, syncState)
            bindLong(6, localRevision)
            bindLong(7, localMutationSeq)
        }.await()
}

private suspend fun TestDatabase.seedLocalOwnerFuelEntry(
    id: String,
    vehicleId: String,
    localMutationSeq: Long,
    localRevision: Long = 1,
    syncState: String = "PENDING",
    deletedAt: Long? = null,
) {
    driver
        .execute(
            identifier = null,
            sql =
                """
                INSERT INTO fuel_entry(
                  id, ownerId, vehicleId, date, odometerKm, litersScaled, pricePerLiterScaled,
                  totalCostMinor, currency, isFullTank, hasMissedEntries, odometerInconsistent,
                  createdAt, updatedAt, serverUpdatedAt, deleted, deletedAt, syncState,
                  localRevision, localMutationSeq, schemaVersion
                ) VALUES (?, 'LOCAL_OWNER', ?, 1, 10, 1000, 1000, 100, 'EUR', 1, 0, 0,
                  1, 1, NULL, ?, ?, ?, ?, ?, 1)
                """.trimIndent(),
            parameters = 7,
        ) {
            bindString(0, id)
            bindString(1, vehicleId)
            bindLong(2, if (deletedAt == null) 0L else 1L)
            bindLong(3, deletedAt)
            bindString(4, syncState)
            bindLong(5, localRevision)
            bindLong(6, localMutationSeq)
        }.await()
}

/** A row that already belongs to a real UID, with the outbox rows that gave the table its high-water seq. */
private suspend fun TestDatabase.seedAdoptedVehicleWithOutboxRow(
    id: String,
    seqCount: Int,
) {
    repeat(seqCount) { index ->
        database.databaseQueries.coalesceOutbox(
            entityType = "VEHICLE",
            entityId = "$id-$index",
            payload = "pre-existing",
            localRevision = 1,
        )
    }
}
