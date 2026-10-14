package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsOne

/**
 * The owner's active (non-deleted) row counts, for the analytics count buckets of `E3-09`.
 *
 * Both figures describe what the owner can see, which is why a tombstone is excluded: the buckets of
 * `docs/CONTRACTS.md §16.1` are computed from the list size, and a deleted row is not in the list
 * (`D-197`, ADR-0197).
 */
data class OwnerActiveRowCounts(
    val vehicleCount: Long,
    val entryCount: Long,
)

/**
 * A **read-only** count accessor.
 *
 * It deliberately opens no transaction and exposes no mutation: `D-194` records that an
 * unconditional write transaction takes SQLite's file lock even when it changes nothing, and a
 * count has no reason to hold one. The two queries it runs are `deleted = 0` filtered and
 * owner-scoped, so a bucket cannot include another owner's rows or a row the owner deleted.
 */
class OwnerActiveRowCountDatabaseAccess(
    private val database: AppDatabase,
) {
    suspend fun activeRowCounts(ownerId: String): OwnerActiveRowCounts =
        OwnerActiveRowCounts(
            vehicleCount = database.databaseQueries.countActiveVehiclesByOwner(ownerId).awaitAsOne(),
            entryCount = database.databaseQueries.countActiveFuelEntriesByOwner(ownerId).awaitAsOne(),
        )
}
