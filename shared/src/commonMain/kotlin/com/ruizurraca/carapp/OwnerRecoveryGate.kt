package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.sync.SyncController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks whether the current owner's recovery cycle is still outstanding.
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
 * Three cases deliberately keep the previous behaviour:
 *
 * - the `LOCAL_OWNER` sentinel, which has nothing remote to fetch and which `§11.2` requires to work
 *   offline on first launch — it never raises the gate, because the owner observer skips it;
 * - a device that is offline, where the refused cycle settles immediately and the gate lowers at
 *   once, so an offline first run still reaches first-vehicle creation (`SPECIFICATION.md` P2);
 * - any owner whose local data is non-empty, because a non-empty list is known regardless of
 *   whether a recovery is pending.
 */
/** Owned by `:shared`; the vehicle feature reads only its [pending] flow, which keeps the feature
 * package free of any `:shared` edge (`docs/TECHNICAL_PLAN.md §4`). */
internal class OwnerRecoveryGate(
    private val syncController: SyncController,
) {
    private val mutablePending = MutableStateFlow(false)

    /** True while the current owner's recovery cycle has not completed. */
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
        mutablePending.value = true
        try {
            syncController.sync(SyncTrigger.OwnerChanged)
        } finally {
            // Also on failure and on cancellation. A failed recovery is a condition the owner can see
            // and retry through the existing unreadable-list path, and a graph being closed must not
            // leave a half-raised window behind it.
            mutablePending.value = false
        }
    }

    /**
     * Whether an empty list must stay unresolved because the owner's recovery is outstanding.
     * A non-empty list is known regardless, so it is never held back.
     */
    fun holdsEmptyListUnresolved(vehicleCount: Int): Boolean = mutablePending.value && vehicleCount == 0
}
