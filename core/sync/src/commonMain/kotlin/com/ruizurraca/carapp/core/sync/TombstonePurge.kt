package com.ruizurraca.carapp.core.sync

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.SyncDatabaseAccess
import kotlinx.coroutines.CancellationException

/**
 * The local 90-day tombstone purge of `docs/CONTRACTS.md §8`, owned by `:core:sync` and invoked once
 * per app start from `AppGraph.init`.
 *
 * RED stub for `E3-07` criterion 2: the method returns without purging, so the tests that assert the
 * purge fail because the behaviour does not exist yet rather than at the compiler.
 */
class TombstonePurge(
    private val databaseAccess: SyncDatabaseAccess,
    private val clock: AppClock,
) {
    suspend fun purgeConfirmedTombstones(): Outcome<Unit, AppError> = Outcome.Ok(Unit)
}
