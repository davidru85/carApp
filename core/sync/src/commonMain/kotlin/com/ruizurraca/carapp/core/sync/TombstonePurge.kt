package com.ruizurraca.carapp.core.sync

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.UnexpectedError
import com.ruizurraca.carapp.core.database.SyncDatabaseAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The local 90-day tombstone purge of `docs/CONTRACTS.md §8`, owned by `:core:sync` (`D-194`).
 *
 * The purge condition itself is the statement's job, in `:core:database` where every
 * synchronized-entity write lives (`D-38`). This class owns the policy: the cutoff is derived from the
 * injected [AppClock] rather than from a system clock, and the purge is attempted **at most once per
 * app start**.
 *
 * The latch is set under a mutex **before** the attempt, so a second call in the same app start is a
 * no-op whether the first attempt succeeded or failed. A failed attempt deleted nothing, because both
 * statements share one transaction, and the next app start builds a new instance that tries again.
 *
 * A failure never propagates into the caller's scope. It is converted here into
 * `UnexpectedError(":core:sync", <class name>)` and handed to [onFailure], which the app graph binds to
 * `CrashReporter.recordNonFatal` (`docs/CONTRACTS.md §6`, `§20.3.1`). Cancellation is rethrown unchanged.
 */
class TombstonePurge(
    private val databaseAccess: SyncDatabaseAccess,
    private val clock: AppClock,
    private val onFailure: (AppError, Map<String, String>) -> Unit,
) {
    private val oncePerAppStart = Mutex()

    private var attempted = false

    /** Attempts the purge once per app start; every later call in the same app start returns immediately. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun purgeConfirmedTombstones() {
        oncePerAppStart.withLock {
            if (attempted) return
            attempted = true
            try {
                databaseAccess.purgeConfirmedTombstones(clock.now().toEpochMilliseconds() - TOMBSTONE_PURGE_AGE_MS)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                val error = UnexpectedError(":core:sync", failure::class.simpleName ?: "Throwable")
                onFailure(error, mapOf("code" to error.code))
            }
        }
    }
}

/** `docs/CONTRACTS.md §8`: a confirmed tombstone is purgeable once it is older than 90 days. */
private const val TOMBSTONE_PURGE_AGE_MS = 90L * 24L * 60L * 60L * 1_000L
