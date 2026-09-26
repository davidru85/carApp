package com.ruizurraca.carapp.core.sync

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.database.SyncDatabaseAccess
import com.ruizurraca.carapp.core.database.createStagedDatabaseFactory
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * `E3-07` criteria 2 and 4.
 *
 * Criterion 2 is "purge runs at most once per app start". The entry point is the `:core:sync` surface
 * the graph calls once, and it must itself be idempotent under a second call, because the invariant is
 * about the app start rather than about one caller's discipline. The clock is injected and read when
 * the purge runs, so the cutoff is `now - 90 days` and never a system call.
 *
 * Criterion 4 is the fresh-device half: a pull page carrying a tombstone for an entity this device has
 * never seen must insert a tombstone instead of failing, which is what makes the purge safe on a
 * device that has just recovered a deleted row.
 */
class TombstonePurgeTest {
    private var handle: DatabaseHandle? = null

    @AfterTest
    fun tearDown() {
        handle?.close()
    }

    @Test
    fun purgeConfirmedTombstonesPurgesAnOldConfirmedTombstoneAndRunsOnlyOncePerAppStart() =
        runTest {
            val database = openDatabase()
            database.seedVehicleTombstone(VEHICLE_ID, serverUpdatedAt = NOW_MILLIS - NINETY_ONE_DAYS)
            val purge = purger()

            purge.purgeConfirmedTombstones()
            assertNull(database.vehicleRow(VEHICLE_ID), "the first call purges the confirmed tombstone")

            // The second call models a second invocation inside the same app start, which MUST be a
            // no-op rather than a re-run: the invariant is once per start, not once per caller.
            database.seedVehicleTombstone(SECOND_ID, serverUpdatedAt = NOW_MILLIS - NINETY_ONE_DAYS)
            purge.purgeConfirmedTombstones()
            assertNotNull(
                database.vehicleRow(SECOND_ID),
                "the purge runs at most once per app start, so the second call deletes nothing",
            )
        }

    @Test
    fun purgeConfirmedTombstonesDerivesTheCutoffFromTheInjectedClock() =
        runTest {
            val database = openDatabase()
            // 91 days before NOW_MILLIS. A clock 30 days behind places the cutoff at `NOW - 120 days`,
            // which this row is newer than, so the injected instant alone decides its fate.
            database.seedVehicleTombstone(VEHICLE_ID, serverUpdatedAt = NOW_MILLIS - NINETY_ONE_DAYS)

            purger(now = NOW_MILLIS - THIRTY_DAYS).purgeConfirmedTombstones()
            assertNotNull(
                database.vehicleRow(VEHICLE_ID),
                "the cutoff is the injected clock minus 90 days, never the system clock",
            )

            // A second instance is a second app start, so the same row is now older than its cutoff.
            purger(now = NOW_MILLIS).purgeConfirmedTombstones()
            assertNull(
                database.vehicleRow(VEHICLE_ID),
                "the same row becomes purgable only because the injected clock moved forward",
            )
        }

    @Test
    fun aPulledTombstoneForAnUnknownVehicleBecomesALocalTombstone() =
        runTest {
            val database = openDatabase()
            val controller = controller(TombstoneRemoteSource(vehicleTombstoneDocument(TOMBSTONE_ID)))

            val result = controller.sync(SyncTrigger.PullToRefresh)

            assertIs<Outcome.Ok<Unit>>(result, "a tombstone for an unseen entity is applied, not failed")
            val applied = assertNotNull(database.vehicleRow(TOMBSTONE_ID), "the row was inserted locally")
            assertEquals(1L, applied.deleted, "a pulled tombstone is inserted as a tombstone")
            assertEquals(TOMBSTONE_MILLIS, applied.deletedAt)
            assertEquals("SYNCED", applied.syncState, "a remote-applied row is SYNCED")
            assertFalse(
                TOMBSTONE_ID in database.visibleVehicleIds(),
                "the tombstone is inserted but excluded from the UI read model",
            )
        }

    @Test
    fun aPulledTombstoneForAnUnknownFuelEntryBecomesALocalTombstone() =
        runTest {
            val database = openDatabase()
            val controller = controller(TombstoneRemoteSource(fuelEntryTombstoneDocument(ENTRY_ID)))

            val result = controller.sync(SyncTrigger.PullToRefresh)

            assertIs<Outcome.Ok<Unit>>(result, "a fuel-entry tombstone for an unseen entity is applied too")
            val applied = assertNotNull(database.fuelEntryRow(ENTRY_ID), "the row was inserted locally")
            assertEquals(1L, applied.deleted, "a pulled tombstone is inserted as a tombstone")
            assertEquals("SYNCED", applied.syncState, "a remote-applied row is SYNCED")
        }

    private fun openDatabase(): AppDatabase {
        val created = createStagedDatabaseFactory().create()
        handle = created
        return created.database
    }

    private fun purger(now: Long = NOW_MILLIS): TombstonePurge =
        TombstonePurge(
            databaseAccess = SyncDatabaseAccess(requireNotNull(handle).database),
            clock = PurgeClock(Instant.fromEpochMilliseconds(now)),
        )

    private fun TestScope.controller(remote: RemoteSyncSource): SyncController =
        createSyncController(
            scope = backgroundScope,
            databaseAccess = SyncDatabaseAccess(requireNotNull(handle).database),
            ownerContext = PurgeOwnerContext(OwnerId(OWNER_ID)),
            connectivity = PurgeConnectivity(initial = true),
            remote = remote,
            clock = PurgeClock(Instant.fromEpochMilliseconds(NOW_MILLIS)),
            uuidGenerator = PurgeUuidGenerator,
            adoption = { Outcome.Ok(Unit) },
            onPoisoned = { _, _ -> },
            onQuarantined = {},
            isDebugBuild = false,
        )

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val SECOND_ID = "vehicle-2"
        const val ENTRY_ID = "entry-1"
        const val TOMBSTONE_ID = "vehicle-tomb"
        const val OWNER_ID = "owner-1"
        const val NOW_MILLIS = 4_070_908_800_000L
        const val NINETY_ONE_DAYS = 91L * 86_400_000L
        const val THIRTY_DAYS = 30L * 86_400_000L
    }
}

private const val TOMBSTONE_MILLIS = 4_000_000_000_000L

private class PurgeClock(
    private val value: Instant,
) : AppClock {
    override fun now(): Instant = value
}

private class PurgeOwnerContext(
    private val owner: OwnerId,
) : OwnerContext {
    override val current: OwnerId get() = owner

    override fun observe(): Flow<OwnerId> = MutableStateFlow(owner)
}

private class PurgeConnectivity(
    initial: Boolean,
) : ConnectivityObserver {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(initial)
}

private object PurgeUuidGenerator : UuidGenerator {
    override fun newId(): String = "cycle-1"
}

/** Serves exactly one tombstone document for its own entity type, then empty pages. */
private class TombstoneRemoteSource(
    private val document: RemoteDocument,
) : RemoteSyncSource {
    private var served = false

    override suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError> = Outcome.Err(RemoteError.Unavailable)

    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> {
        if (served || entityType != document.entityType) {
            return Outcome.Ok(RemotePage(emptyList(), cursor, false))
        }
        served = true
        return Outcome.Ok(
            RemotePage(listOf(document), RemoteCursor(document.serverUpdatedAt, document.documentId), false),
        )
    }
}

private fun vehicleTombstoneDocument(id: String) =
    RemoteDocument(
        entityType = EntityType.VEHICLE,
        documentId = EntityId(id),
        serverUpdatedAt = Instant.fromEpochMilliseconds(TOMBSTONE_MILLIS),
        rawJson =
            """
            {
              "id":"$id",
              "ownerId":"owner-1",
              "name":"Roadster",
              "initialOdometerKm":0,
              "brand":null,
              "model":null,
              "fuelType":"GASOLINE",
              "createdAt":1,
              "updatedAt":$TOMBSTONE_MILLIS,
              "deleted":true,
              "deletedAt":$TOMBSTONE_MILLIS,
              "schemaVersion":1
            }
            """.trimIndent(),
    )

private fun fuelEntryTombstoneDocument(id: String) =
    RemoteDocument(
        entityType = EntityType.FUEL_ENTRY,
        documentId = EntityId(id),
        serverUpdatedAt = Instant.fromEpochMilliseconds(TOMBSTONE_MILLIS),
        rawJson =
            """
            {
              "id":"$id",
              "ownerId":"owner-1",
              "vehicleId":"vehicle-1",
              "date":1,
              "odometerKm":1,
              "litersScaled":1000,
              "pricePerLiterScaled":1000,
              "totalCostMinor":100,
              "currency":"EUR",
              "isFullTank":true,
              "hasMissedEntries":false,
              "odometerInconsistent":false,
              "notes":null,
              "createdAt":1,
              "updatedAt":$TOMBSTONE_MILLIS,
              "deleted":true,
              "deletedAt":$TOMBSTONE_MILLIS,
              "schemaVersion":1
            }
            """.trimIndent(),
    )

private suspend fun AppDatabase.seedVehicleTombstone(
    id: String,
    serverUpdatedAt: Long,
) {
    databaseQueries.insertVehicleRow(
        id = id,
        ownerId = "owner-1",
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
        syncState = "SYNCED",
        localRevision = 1,
        localMutationSeq = 1,
        schemaVersion = 1,
    )
}

private suspend fun AppDatabase.vehicleRow(id: String) =
    databaseQueries.selectVehicleById(id).awaitAsOneOrNull()

private suspend fun AppDatabase.fuelEntryRow(id: String) =
    databaseQueries.selectFuelEntryById(id).awaitAsOneOrNull()

private suspend fun AppDatabase.visibleVehicleIds(): List<String> =
    databaseQueries.selectAllVehicles().awaitAsList().map { it.id }
