package com.ruizurraca.carapp.core.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `E3-07` write-lock regression, Android host half.
 *
 * The hazard the story introduced, and CI caught, is a lock-contention one. The driver opens every
 * `database.transaction { }` with `BEGIN IMMEDIATE`, which acquires the file's write lock the instant
 * the transaction starts, and the bundled SQLite stack sets no `busy_timeout`. A `DELETE` that matches
 * zero rows takes that lock exactly like one that deletes rows, so the first implementation began a
 * writing transaction on **every** app start. On iOS the UI tests mount two graphs over one persistent
 * file, and the second graph's write then failed outright with `SQLITE_BUSY` instead of waiting.
 *
 * The purge condition is unchanged; only the decision to open a transaction is guarded, and the guard
 * is a read. These tests use a **file-backed** database plus an independent second connection, because
 * the contention is between connections and cannot exist on an in-memory database.
 *
 * Deliberately host tests rather than `commonTest`: `AndroidxSqliteDatabaseType.File` needs a real
 * path. `IosTombstonePurgeWriteLockTest` asserts the same three properties on the platform whose
 * persistent file is where the failure actually appeared.
 */
class AndroidTombstonePurgeWriteLockTest {
    @Test
    fun anEmptyPurgeSucceedsWhileAnotherConnectionHoldsTheWriteLock() =
        runTest {
            withFileBackedDatabase("carapp-e3-07-lock-") { path, handle ->
                assertEquals(0L, handle.purgeableCount(CUTOFF), "this database has nothing to purge")

                val writer = SecondConnection(path)
                try {
                    // The lock is held across the purge, which is the whole point: with an
                    // unconditional writing transaction the purge would raise `SQLITE_BUSY` here,
                    // because `BEGIN IMMEDIATE` cannot wait for this writer.
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
            withFileBackedDatabase("carapp-e3-07-purge-") { _, handle ->
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
    fun anOutboxRowMakesAnOldConfirmedTombstoneUnpurgeable() =
        runTest {
            withFileBackedDatabase("carapp-e3-07-outbox-") { _, handle ->
                handle.seedVehicleTombstone(VEHICLE_ID, serverUpdatedAt = CUTOFF - 1)
                handle.database.databaseQueries.coalesceOutbox(
                    entityType = "VEHICLE",
                    entityId = VEHICLE_ID,
                    payload = "{\"deleted\":true}",
                    localRevision = 1,
                )

                assertEquals(0L, handle.purgeableCount(CUTOFF), "an outbox row makes the tombstone unpurgeable")

                handle.purge(CUTOFF)

                assertNotNull(
                    handle.database.databaseQueries
                        .selectVehicleById(VEHICLE_ID)
                        .awaitAsOneOrNull(),
                    "the tombstone survived the purge because its outbox row still owns it",
                )
            }
        }

    private companion object {
        const val VEHICLE_ID = "vehicle-1"
        const val CUTOFF = 4_063_132_800_000L
    }
}

/** Creates a file-backed database under a fresh temporary directory and removes it afterwards. */
internal suspend fun withFileBackedDatabase(
    prefix: String,
    block: suspend (path: String, handle: TestDatabase) -> Unit,
) {
    val directory = Files.createTempDirectory(prefix).toFile()
    val path = directory.resolve("carapp.db").absolutePath
    val handle = TestDatabase.create(AndroidxSqliteDatabaseType.File(path))
    try {
        block(path, handle)
    } finally {
        handle.close()
        directory.deleteRecursively()
    }
}

internal suspend fun TestDatabase.purge(cutoff: Long) {
    SyncDatabaseAccess(database).purgeConfirmedTombstones(cutoff)
}

internal suspend fun TestDatabase.purgeableCount(cutoff: Long): Long =
    database.databaseQueries.countPurgeableVehicleTombstones(cutoff).awaitAsOne() +
        database.databaseQueries.countPurgeableFuelEntryTombstones(cutoff).awaitAsOne()

internal suspend fun TestDatabase.seedVehicleTombstone(
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
internal class SecondConnection(
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
