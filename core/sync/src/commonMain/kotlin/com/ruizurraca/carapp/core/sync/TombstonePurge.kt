package com.ruizurraca.carapp.core.sync

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.database.SyncDatabaseAccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The local 90-day tombstone purge of `docs/CONTRACTS.md §8`, owned by `:core:sync`.
 *
 * The contract states three things this class is responsible for. The purge condition itself is the
 * statement's job, in `:core:database` where every synchronized-entity write lives (`D-38`); what is
 * left here is the *policy*: the cutoff is derived from the injected [AppClock] rather than from a
 * system clock, and the purge runs **at most once per app start**.
 *
 * The once-per-start rule is enforced with a latch rather than left to the caller, because "once per
 * app start" is an invariant of the process, not a discipline of one call site. It is guarded by a
 * mutex so two concurrent callers cannot both observe an unlatched state, and the latch is set only
 * after the transaction succeeded: a purge that failed deleted nothing, so the next app start's
 * attempt - or a later call in this one - is still the first successful purge rather than a re-run.
 *
 * Remote tombstones are never purged in the MVP, which needs no code here: the statement only reads
 * the two local entity tables.
 */
class TombstonePurge(
    private val databaseAccess: SyncDatabaseAccess,
    private val clock: AppClock,
) {
    private val oncePerAppStart = Mutex()

    private var purged = false

    /**
     * Purges every locally purgeable tombstone, once per app start.
     *
     * A failure propagates rather than being swallowed: the caller is the app graph's startup path,
     * which decides how to report it. No outcome type is returned because there is no partial result
     * to describe - the transaction either deleted the purgable rows or it did not.
     */
    suspend fun purgeConfirmedTombstones() {
        oncePerAppStart.withLock {
            if (purged) return
            databaseAccess.purgeConfirmedTombstones(clock.now().toEpochMilliseconds() - TOMBSTONE_PURGE_AGE_MS)
            purged = true
        }
    }
}

/** `docs/CONTRACTS.md §8`: a confirmed tombstone is purgeable once it is older than 90 days. */
private const val TOMBSTONE_PURGE_AGE_MS = 90L * 24L * 60L * 60L * 1_000L
