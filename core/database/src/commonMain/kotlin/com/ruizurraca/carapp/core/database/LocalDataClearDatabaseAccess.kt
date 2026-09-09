package com.ruizurraca.carapp.core.database

/**
 * The destructive local-data clear of `docs/CONTRACTS.md §11.5` and `docs/SPECIFICATION.md §7 F-5`.
 *
 * Behavior-free seam: it declares the surface the RED specification exercises, so those tests
 * compile and fail for the missing behavior rather than for a missing type.
 */
class LocalDataClearDatabaseAccess(
    private val database: AppDatabase,
) {
    suspend fun pendingOutboxCount(): Long = 0L

    suspend fun localSequenceNext(): Long = 0L

    suspend fun clearAllLocalData() = Unit
}
