package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.sync.SyncController
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Tracks whether an owner's recovery cycle is still outstanding.
 *
 * An owner transition is not the same event as a known-empty list. When the owner resolves to an
 * identity the local database has never held, the local read succeeds with zero rows and
 * `VehicleListUiState.isLoading` would go `false`, which `docs/SPECIFICATION.md` F-1 reads as a
 * confirmed empty list and answers with mandatory first-vehicle creation. On a clean device that
 * happens while the owner's real data is still in Firestore.
 *
 * This gate keeps that interval explicit: it is raised when the owner changes and lowered once the
 * cycle the `§9.8` `OwnerChanged` trigger admitted has completed. The list holder consults it before
 * publishing `isLoading`, so an empty list that is empty *because nothing has been fetched yet* is
 * never presented as a list known to be empty.
 *
 * Recoveries are counted rather than flagged. `DefaultAppGraph` launches one `awaitRecovery` per
 * transition on `dispatchers.io`, so a transition that arrives while an earlier cycle is still
 * running leaves two in flight; a single boolean would let the earlier cycle's completion resolve
 * the newest owner's list, which is the defect this gate exists to prevent. Only the last recovery
 * to complete lowers the gate, and the counter and the value derived from it are written under one
 * lock so no thread can publish a state the counter contradicts.
 *
 * Three cases deliberately keep the previous behaviour:
 *
 * - the `LOCAL_OWNER` sentinel, which has nothing remote to fetch and which `§11.2` requires to work
 *   offline on first launch — it never raises the gate, because the owner observer skips it;
 * - a device that is offline, where the refused cycle settles immediately and the gate lowers at
 *   once, so an offline first run still reaches first-vehicle creation (`SPECIFICATION.md` P2);
 * - any owner whose local data is non-empty, because a non-empty list is known regardless of
 *   whether a recovery is pending.
 */
internal class OwnerRecoveryGate(
    private val syncController: SyncController,
) {
    private val mutablePending = MutableStateFlow(false)

    // Guards the counter and the published value together, because both are written from graph
    // coroutines that run on a multi-threaded dispatcher.
    private val stateLock = Mutex()
    private var outstanding = 0

    /** True while at least one owner's recovery cycle has not completed. */
    val pending: StateFlow<Boolean> = mutablePending

    /**
     * Raises the gate and requests the cycle that will lower it.
     *
     * `sync` is used rather than `requestSync` because this is the one caller that needs the cycle's
     * completion: the gate exists to describe an interval, so a fire-and-forget request would leave
     * it raised with nothing to lower it. It still enters the single controller and obeys every
     * `§9.1` and `§9.8` admission rule, including the offline and `LOCAL_OWNER` refusals, which is
     * what keeps a refused cycle from stranding an offline device behind the gate.
     */
    suspend fun awaitRecovery() {
        stateLock.withLock {
            outstanding += 1
            mutablePending.value = true
        }
        try {
            syncController.sync(SyncTrigger.OwnerChanged)
        } finally {
            // Also on failure and on cancellation, and under `NonCancellable` so a graph closed
            // mid-recovery still lowers what it raised. A failed recovery is a condition the owner
            // can see and retry through the existing unreadable-list path.
            withContext(NonCancellable) {
                stateLock.withLock {
                    outstanding -= 1
                    if (outstanding == 0) mutablePending.value = false
                }
            }
        }
    }
}
