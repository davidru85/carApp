package com.ruizurraca.carapp.core.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `E3-07` write-lock regression, iOS half.
 *
 * This is the platform where the hazard actually surfaced. The driver opens every
 * `database.transaction { }` with `BEGIN IMMEDIATE`, taking the file's write lock as the transaction
 * starts, and the bundled SQLite stack sets no `busy_timeout`; a `DELETE` matching zero rows takes the
 * same lock as one that deletes rows. An unconditional purge therefore made every app start a writing
 * transaction over the persistent `carapp.db`, and `ViewModelLifecycleTests` - which mounts two graphs
 * on that one file - had its second graph fail with `SQLITE_BUSY` instead of waiting.
 *
 * The assertions mirror `AndroidTombstonePurgeWriteLockTest`. The duplication is deliberate: this file
 * must exercise the real iOS file and the real iOS driver, and the failure being guarded against is a
 * platform-timing property that a shared helper would obscure rather than share.
 */
@OptIn(ExperimentalForeignApi::class)
class IosTombstonePurgeWriteLockTest {
    @Test
    fun anEmptyPurgeSucceedsWhileAnotherConnectionHoldsTheWriteLock() =
        runTest {
            withIosFileBackedDatabase { path, handle ->
                assertEquals(0L, handle.purgeableCount(CUTOFF), "this database has nothing to purge")

                val writer = IosSecondConnection(path)
                try {
                    // Held across the purge: with an unconditional writing transaction the purge would
                    // raise `SQLITE_BUSY` here, because `BEGIN IMMEDIATE` cannot wait for this writer.
                    writer.holdWriteLock()
                    handle.purge(CUTOFF)
                    assertEquals(
                        0L,
                        handle.purgeableCount(CUTOFF),
                        "an empty purge only read, so the held write lock did not block it",
                    )
                } finally {
                    writer.releaseWriteLock()
                    writer.close()
                }
            }
        }

    @Test
    fun aPurgeWithWorkToDoStillDeletesTheTombstone() =
        runTest {
            withIosFileBackedDatabase { _, handle ->
                handle.seedVehicleTombstone(VEHICLE_ID, serverUpdatedAt = CUTOFF - 1)
                assertEquals(1L, handle.purgeableCount(CUTOFF), "the seeded tombstone is purgeable")

                handle.purge(CUTOFF)

                assertEquals(0L, handle.purgeableCount(CUTOFF), "the purge deleted the confirmed tombstone")
                assertNull(
                    handle.database.databaseQueries
                        .selectVehicleById(VEHICLE_ID)
                        .awaitAsOneOrNull(),
                    "the row is gone, not merely no longer counted",
                )
            }
        }

    @Test
    fun aTombstoneYoungerThanTheCutoffIsNotPurged() =
        runTest {
            withIosFileBackedDatabase { _, handle ->
                handle.seedVehicleTombstone(VEHICLE_ID, serverUpdatedAt = CUTOFF + 1)

                assertEquals(0L, handle.purgeableCount(CUTOFF), "a tombstone younger than the cutoff is not purgeable")

                handle.purge(CUTOFF)

                assertNotNull(
                    handle.database.databaseQueries
                        .selectVehicleById(VEHICLE_ID)
                        .awaitAsOneOrNull(),
                    "the age guard is unchanged by the write-lock guard",
                )
            }
        }

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val CUTOFF = 4_063_132_800_000L
    }
}

/** Creates an iOS file-backed database at a fresh temporary path and removes its files afterwards. */
@OptIn(ExperimentalForeignApi::class)
private suspend fun withIosFileBackedDatabase(block: suspend (path: String, handle: TestDatabase) -> Unit) {
    val path = "${NSTemporaryDirectory()}carapp-e3-07-${Random.nextLong()}.db"
    val handle = TestDatabase.create(AndroidxSqliteDatabaseType.File(path))
    try {
        block(path, handle)
    } finally {
        handle.close()
        listOf(path, "$path-shm", "$path-wal").forEach { candidate ->
            if (NSFileManager.defaultManager.fileExistsAtPath(candidate)) {
                NSFileManager.defaultManager.removeItemAtPath(candidate, error = null)
            }
        }
    }
}

private suspend fun TestDatabase.purge(cutoff: Long) {
    SyncDatabaseAccess(database).purgeConfirmedTombstones(cutoff)
}

private suspend fun TestDatabase.purgeableCount(cutoff: Long): Long =
    database.databaseQueries.countPurgeableVehicleTombstones(cutoff).awaitAsOne() +
        database.databaseQueries.countPurgeableFuelEntryTombstones(cutoff).awaitAsOne()

private suspend fun TestDatabase.seedVehicleTombstone(
    id: String,
    serverUpdatedAt: Long,
) {
    database.databaseQueries.insertVehicleRow(
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

/** A second, independent connection onto the same file - what a concurrent writer in the app is. */
@OptIn(ExperimentalForeignApi::class)
private class IosSecondConnection(
    path: String,
) {
    private val driver =
        AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.File(path),
            schema = AppDatabase.Schema,
        )

    /** Acquires the file's write lock and keeps it until [releaseWriteLock]. */
    suspend fun holdWriteLock() {
        driver.execute(identifier = null, sql = "BEGIN IMMEDIATE", parameters = 0).await()
    }

    suspend fun releaseWriteLock() {
        driver.execute(identifier = null, sql = "ROLLBACK", parameters = 0).await()
    }

    fun close() {
        driver.close()
    }
}
