package com.ruizurraca.carapp.core.sync

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.database.DatabaseMutations
import com.ruizurraca.carapp.core.database.SyncDatabaseAccess
import com.ruizurraca.carapp.core.database.createStagedDatabaseFactory
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SqlDelightSyncPersistenceTest {
    @Test
    fun emptyDatabaseIsAdmittedAsAnEmptyOwner() =
        withPersistence { persistence ->
            assertTrue(persistence.isOwnerDatabaseEmpty(ownerId))
        }

    @Test
    fun dueOutboxMapsTheStoredVehicleRowAndItsDeletedFlag() =
        withPersistence { persistence ->
            seedVehicleOutbox(persistence, payload = "{\"deleted\":false}")
            val row = persistence.dueOutbox(instant(0), limit = 10).single()

            assertEquals(EntityType.VEHICLE, row.entityType)
            assertEquals(EntityId(VEHICLE_ID), row.entityId)
            assertEquals(1, row.localRevision)
            assertEquals(0, row.attemptCount)
            assertNull(row.lastErrorCode)
            assertFalse(row.deleted)
        }

    @Test
    fun deletedPayloadIsRecognisedEvenWhenRawJsonIsMalformed() =
        withPersistence { persistence ->
            seedVehicleOutbox(persistence, payload = "{not-json")
            assertFalse(persistence.dueOutbox(instant(0), limit = 10).single().deleted)
        }

    @Test
    fun markSyncingThenConfirmPushTransitionsTheVehicleToSynced() =
        withPersistence { persistence ->
            seedVehicleOutbox(persistence, payload = "{\"deleted\":false}")
            val row = persistence.dueOutbox(instant(0), limit = 10).single()

            persistence.markSyncing(row)
            persistence.confirmPush(row, serverUpdatedAt = instant(500))

            assertEquals("SYNCED", vehicleState(persistence))
            assertTrue(persistence.dueOutbox(instant(0), limit = 10).isEmpty())
            assertEquals(SyncCounts(0, 0, 0), persistence.counts())
        }

    @Test
    fun confirmPushWithoutAServerTimestampLeavesTheSyncedRowInPlace() =
        withPersistence { persistence ->
            seedVehicleOutbox(persistence, payload = "{\"deleted\":false}")
            val row = persistence.dueOutbox(instant(0), limit = 10).single()

            persistence.markSyncing(row)
            persistence.confirmPush(row, serverUpdatedAt = null)

            assertEquals("SYNCED", vehicleState(persistence))
        }

    @Test
    fun failPushStoresRetryContextAndCountsItAsRetryable() =
        withPersistence { persistence ->
            seedVehicleOutbox(persistence, payload = "{\"deleted\":false}")
            val row = persistence.dueOutbox(instant(0), limit = 10).single()

            persistence.failPush(
                row = row,
                attemptCount = 2,
                nextAttemptAt = instant(1_000),
                errorCode = RemoteError.Unknown.code,
                poisoned = false,
                cycleId = CycleId("cycle-1"),
            )

            assertEquals("FAILED_RETRYABLE", vehicleState(persistence))
            assertEquals(SyncCounts(0, 1, 0), persistence.counts())
        }

    @Test
    fun failPushWithPoisonedFlagIsCountedAsPoisoned() =
        withPersistence { persistence ->
            seedVehicleOutbox(persistence, payload = "{\"deleted\":false}")
            val row = persistence.dueOutbox(instant(0), limit = 10).single()

            persistence.failPush(
                row = row,
                attemptCount = 8,
                nextAttemptAt = instant(1_000),
                errorCode = RemoteError.InvalidArgument.code,
                poisoned = true,
                cycleId = CycleId("cycle-1"),
            )

            assertEquals("FAILED_POISONED", vehicleState(persistence))
            assertEquals(SyncCounts(0, 0, 1), persistence.counts())
        }

    @Test
    fun connectivityCodesCountAsPending() =
        withPersistence { persistence ->
            seedVehicleOutbox(persistence, payload = "{\"deleted\":false}")
            val row = persistence.dueOutbox(instant(0), limit = 10).single()

            persistence.failPush(
                row = row,
                attemptCount = 1,
                nextAttemptAt = instant(1_000),
                errorCode = RemoteError.Unavailable.code,
                poisoned = false,
                cycleId = CycleId("cycle-1"),
            )

            assertEquals(SyncCounts(1, 0, 0), persistence.counts())
        }

    @Test
    fun markConnectivityFailuresDueMakesOnlyConnectivityRowsDue() =
        withPersistence { persistence ->
            seedVehicleOutbox(persistence, payload = "{\"deleted\":false}", id = VEHICLE_ID)
            seedVehicleOutbox(persistence, payload = "{\"deleted\":false}", id = SECOND_ID)
            val rows = persistence.dueOutbox(instant(0), limit = 10).associateBy { it.entityId.value }
            persistence.failPush(
                row = rows.getValue(VEHICLE_ID),
                attemptCount = 1,
                nextAttemptAt = instant(9_000),
                errorCode = RemoteError.Unavailable.code,
                poisoned = false,
                cycleId = CycleId("cycle-1"),
            )
            persistence.failPush(
                row = rows.getValue(SECOND_ID),
                attemptCount = 1,
                nextAttemptAt = instant(9_000),
                errorCode = RemoteError.Unknown.code,
                poisoned = false,
                cycleId = CycleId("cycle-1"),
            )

            persistence.markConnectivityFailuresDue(instant(100))

            val due = persistence.dueOutbox(instant(100), limit = 10)
            assertEquals(listOf(VEHICLE_ID), due.map { it.entityId.value })
            assertEquals(1, due.single().attemptCount)
        }

    @Test
    fun resetFailedClearsRetryContextAndReturnsTheRowToPending() =
        withPersistence { persistence ->
            seedVehicleOutbox(persistence, payload = "{\"deleted\":false}")
            val row = persistence.dueOutbox(instant(0), limit = 10).single()
            persistence.failPush(
                row = row,
                attemptCount = 4,
                nextAttemptAt = instant(9_000),
                errorCode = RemoteError.Unknown.code,
                poisoned = false,
                cycleId = CycleId("cycle-1"),
            )

            val result = persistence.resetFailed(instant(100))

            assertIs<Outcome.Ok<Unit>>(result)
            assertEquals("PENDING", vehicleState(persistence))
            assertTrue(persistence.dueOutbox(instant(100), limit = 10).single().attemptCount == 0)
        }

    @Test
    fun cursorReadsInitialThenPersistsTheAppliedPage() =
        withPersistence { persistence ->
            assertEquals(RemoteCursor.INITIAL, persistence.cursor(EntityType.VEHICLE))

            persistence.applyPullPage(
                ownerId = ownerId,
                entityType = EntityType.VEHICLE,
                records = emptyList(),
                cursor = RemoteCursor(instant(300), EntityId(VEHICLE_ID)),
            )

            assertEquals(
                RemoteCursor(instant(300), EntityId(VEHICLE_ID)),
                persistence.cursor(EntityType.VEHICLE),
            )
        }

    @Test
    fun applyPullPageMapsVehicleFuelEntryAndQuarantineRecords() =
        withPersistence { persistence ->
            val records =
                listOf(
                    vehicleRecord(serverUpdatedAt = 200),
                    fuelEntryRecord(serverUpdatedAt = 250),
                    PullRecord.Quarantined(quarantineRecord()),
                )

            val newQuarantines =
                persistence.applyPullPage(
                    ownerId = ownerId,
                    entityType = EntityType.VEHICLE,
                    records = records,
                    cursor = RemoteCursor(instant(300), EntityId(VEHICLE_ID)),
                )

            assertEquals(listOf(EntityId(VEHICLE_ID)), newQuarantines.map { it.entityId })
            val queries = persistence.handle.database.databaseQueries
            assertEquals("SYNCED", queries.selectVehicleById(VEHICLE_ID).awaitAsOneOrNull()?.syncState)
            assertEquals("SYNCED", queries.selectFuelEntryById(FUEL_ENTRY_ID).awaitAsOneOrNull()?.syncState)
        }

    @Test
    fun overlappingQuarantineDeliveryIsReturnedOnlyOnce() =
        withPersistence { persistence ->
            persistence.applyPullPage(
                ownerId = ownerId,
                entityType = EntityType.VEHICLE,
                records = listOf(PullRecord.Quarantined(quarantineRecord())),
                cursor = RemoteCursor(instant(300), EntityId(VEHICLE_ID)),
            )

            val repeated =
                persistence.applyPullPage(
                    ownerId = ownerId,
                    entityType = EntityType.VEHICLE,
                    records = listOf(PullRecord.Quarantined(quarantineRecord())),
                    cursor = RemoteCursor(instant(400), EntityId(VEHICLE_ID)),
                )

            assertTrue(repeated.isEmpty())
        }

    @Test
    fun debugLinesProjectRedactedOutboxCursorAndQuarantineState() =
        withPersistence { persistence ->
            seedVehicleOutbox(persistence, payload = "{\"deleted\":false}")
            persistence.applyPullPage(
                ownerId = ownerId,
                entityType = EntityType.VEHICLE,
                records = listOf(PullRecord.Quarantined(quarantineRecord())),
                cursor = RemoteCursor(instant(300), EntityId(VEHICLE_ID)),
            )

            val lines = persistence.debugLines()
            assertTrue(lines.any { it.startsWith("outbox ") })
            assertTrue(lines.any { it.startsWith("cursor type=VEHICLE ") })
            assertTrue(lines.any { it.startsWith("quarantine type=VEHICLE id=$VEHICLE_ID ") })
            assertTrue(lines.none { it.contains("rawJson") })
        }

    @Test
    fun createSyncControllerWiresProductionPersistenceAndDebugLoader() =
        runTest {
            val persistence = openPersistence()
            try {
                val controller =
                    createSyncController(
                        scope = this,
                        databaseAccess = SyncDatabaseAccess(persistence.handle.database),
                        ownerContext = StaticOwnerContext(ownerId),
                        connectivity = StaticConnectivity(true),
                        remote = NoopRemoteSource,
                        clock = StaticClock(instant(0)),
                        uuidGenerator = StaticUuidGenerator,
                        adoption = { Outcome.Ok(Unit) },
                        onPoisoned = { _, _ -> },
                        onQuarantined = {},
                        isDebugBuild = true,
                    )

                assertIs<SyncController>(controller)
            } finally {
                persistence.close()
            }
        }

    private fun withPersistence(block: suspend (TestPersistence) -> Unit) {
        runTest {
            val persistence = openPersistence()
            try {
                block(persistence)
            } finally {
                persistence.close()
            }
        }
    }

    private fun openPersistence(): TestPersistence = TestPersistence.create()

    private suspend fun seedVehicleOutbox(
        persistence: TestPersistence,
        payload: String,
        id: String = VEHICLE_ID,
    ) {
        val database = persistence.handle.database
        DatabaseMutations(database).insertVehicle(
            id = id,
            ownerId = ownerId.value,
            name = "Roadster",
            nameFold = "roadster",
            initialOdometerKm = 0,
            brand = null,
            model = null,
            fuelType = "GASOLINE",
            createdAt = 0,
            updatedAt = 0,
            schemaVersion = 1,
            outboxPayload = null,
        )
        database.databaseQueries.coalesceOutbox(
            entityType = EntityType.VEHICLE.name,
            entityId = id,
            payload = payload,
            localRevision = 1,
        )
    }

    private suspend fun vehicleState(persistence: TestPersistence): String? =
        persistence.handle.database.databaseQueries
            .selectVehicleById(VEHICLE_ID)
            .awaitAsOneOrNull()
            ?.syncState

    private fun vehicleRecord(serverUpdatedAt: Long): PullRecord.Vehicle =
        PullRecord.Vehicle(
            document =
                RemoteDocument(
                    EntityType.VEHICLE,
                    EntityId(VEHICLE_ID),
                    instant(serverUpdatedAt),
                    "{}",
                ),
            ownerId = ownerId,
            name = "Roadster",
            nameFold = "roadster",
            initialOdometerKm = 10,
            brand = null,
            model = null,
            fuelType = "GASOLINE",
            createdAt = instant(0),
            updatedAt = instant(serverUpdatedAt),
            deletedAt = null,
            schemaVersion = 1,
        )

    private fun fuelEntryRecord(serverUpdatedAt: Long): PullRecord.FuelEntry =
        PullRecord.FuelEntry(
            document =
                RemoteDocument(
                    EntityType.FUEL_ENTRY,
                    EntityId(FUEL_ENTRY_ID),
                    instant(serverUpdatedAt),
                    "{}",
                ),
            ownerId = ownerId,
            vehicleId = EntityId(VEHICLE_ID),
            date = instant(0),
            odometerKm = 20,
            litersScaled = 10_000,
            pricePerLiterScaled = 1_500,
            totalCostMinor = 1_500,
            currency = "EUR",
            isFullTank = true,
            hasMissedEntries = false,
            notes = null,
            createdAt = instant(0),
            updatedAt = instant(serverUpdatedAt),
            deletedAt = null,
            schemaVersion = 1,
        )

    private fun quarantineRecord(): QuarantineRecord =
        QuarantineRecord(
            entityType = EntityType.VEHICLE,
            entityId = EntityId(VEHICLE_ID),
            reason = QuarantineReason.MalformedPayload,
            schemaVersion = 1,
            serverUpdatedAt = instant(300),
            rawJson = "{broken",
            createdAt = instant(400),
        )

    private fun instant(epochMillis: Long): Instant = Instant.fromEpochMilliseconds(epochMillis)

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val SECOND_ID = "vehicle-2"
        const val FUEL_ENTRY_ID = "entry-1"
        val ownerId = OwnerId("owner-1")
    }
}

private class TestPersistence private constructor(
    val handle: DatabaseHandle,
    val closeHandle: () -> Unit,
) {
    private val delegate =
        SqlDelightSyncPersistence(
            SyncDatabaseAccess(handle.database),
        )

    fun close() = closeHandle()

    suspend fun isOwnerDatabaseEmpty(ownerId: OwnerId) = delegate.isOwnerDatabaseEmpty(ownerId)

    suspend fun dueOutbox(
        now: Instant,
        limit: Int,
    ) = delegate.dueOutbox(now, limit)

    suspend fun markSyncing(row: OutboxRecord) = delegate.markSyncing(row)

    suspend fun markConnectivityFailuresDue(now: Instant) = delegate.markConnectivityFailuresDue(now)

    suspend fun confirmPush(
        row: OutboxRecord,
        serverUpdatedAt: Instant?,
    ) = delegate.confirmPush(row, serverUpdatedAt)

    suspend fun failPush(
        row: OutboxRecord,
        attemptCount: Int,
        nextAttemptAt: Instant,
        errorCode: String,
        poisoned: Boolean,
        cycleId: CycleId,
    ) = delegate.failPush(row, attemptCount, nextAttemptAt, errorCode, poisoned, cycleId)

    suspend fun cursor(entityType: EntityType) = delegate.cursor(entityType)

    suspend fun applyPullPage(
        ownerId: OwnerId,
        entityType: EntityType,
        records: List<PullRecord>,
        cursor: RemoteCursor,
    ) = delegate.applyPullPage(ownerId, entityType, records, cursor)

    suspend fun resetFailed(now: Instant) = delegate.resetFailed(now)

    suspend fun counts() = delegate.counts()

    suspend fun debugLines() = delegate.debugLines()

    companion object {
        fun create(): TestPersistence {
            val factory = createStagedDatabaseFactory()
            val handle = factory.create()
            return TestPersistence(handle, handle::close)
        }
    }
}

private class StaticOwnerContext(
    private val owner: OwnerId,
) : OwnerContext {
    override val current: OwnerId get() = owner

    override fun observe(): Flow<OwnerId> = MutableStateFlow(owner)
}

private class StaticConnectivity(
    initial: Boolean,
) : ConnectivityObserver {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(initial)
}

private class StaticClock(
    private val value: Instant,
) : AppClock {
    override fun now(): Instant = value
}

private object StaticUuidGenerator : UuidGenerator {
    override fun newId(): String = "cycle-1"
}

private object NoopRemoteSource : RemoteSyncSource {
    override suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError> = Outcome.Err(RemoteError.Unavailable)

    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> = Outcome.Ok(RemotePage(emptyList(), cursor, false))
}
