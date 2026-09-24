package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.SyncController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The owner value every owner-scoped component observes, with the recovery interval it causes
 * already accounted for.
 *
 * An owner transition is not the same event as a known-empty list. When the owner resolves to an
 * identity the local database has never held, the local read succeeds with zero rows and
 * `VehicleListUiState.isLoading` would go `false`, which `docs/SPECIFICATION.md` F-1 reads as a
 * confirmed empty list and answers with mandatory first-vehicle creation. On a clean device that
 * happens while the owner's real data is still in Firestore.
 *
 * This class therefore does two jobs in one place, because doing them in two places is the defect:
 * it publishes the coordinated owner downstream, and it raises the outstanding-recovery count
 * **before** that publication. Downstream observers - the vehicle list, the sync controller's owner
 * reads, the fuel repository - can then never see a new owner whose recovery is not yet counted. The
 * previous shape had the gate raised on one dispatcher and the owner published on another, so a
 * resolved empty list could escape in the interval and merely be reopened afterwards.
 *
 * The count is one `MutableStateFlow<Int>` rather than a boolean plus a counter, because two separate
 * writes can contradict each other under concurrent completion; a reader must never observe a
 * resolved state that the count disagrees with. Overlapping recoveries stay counted: a transition
 * arriving while an earlier cycle still runs is exactly what a permanent sign-in after an anonymous
 * session produces, and only the last recovery to settle lowers the count.
 *
 * Baseline semantics: the owner already resolved when this object is constructed is published as-is
 * and causes no cycle. The check is a **value comparison** rather than `drop(1)`, so a transition
 * that lands between construction and subscription is still detected instead of being discarded as if
 * it were the baseline.
 *
 * Three cases deliberately keep the previous behaviour:
 *
 * - the `LOCAL_OWNER` sentinel, which has nothing remote to fetch and which `§11.2` requires to work
 *   offline on first launch - it is published downstream but never counted, so it never requests a
 *   cycle;
 * - a device that is offline, where the refused cycle settles immediately and the count returns to
 *   zero, so an offline first run still reaches first-vehicle creation (`SPECIFICATION.md` P2);
 * - any owner whose local data is non-empty, because a non-empty list is known regardless of whether
 *   a recovery is outstanding.
 */
internal class OwnerRecoveryGate(
    private val delegate: OwnerContext,
) : OwnerContext {
    private val mutableOwner = MutableStateFlow(delegate.current)
    private val mutableOutstanding = MutableStateFlow(0)

    override val current: OwnerId get() = mutableOwner.value

    override fun observe(): Flow<OwnerId> = mutableOwner

    /** The number of owner recoveries that have not settled yet. */
    val outstanding: StateFlow<Int> = mutableOutstanding

    /**
     * Starts observing the delegate and requests one recovery per non-sentinel transition.
     *
     * Collected `Unconfined` and `UNDISPATCHED` for the same reason the state holders observe the
     * owner that way (`D-120`): the baseline must be read inside the call stack that starts this, and
     * a later transition must reach downstream observers without a dispatch in between, so no
     * resolved state can be published against a count that has not been raised yet.
     */
    fun launchIn(
        scope: CoroutineScope,
        syncController: SyncController,
    ): Job =
        scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            delegate.observe().collect { owner ->
                if (owner == mutableOwner.value) return@collect

                if (owner == LOCAL_OWNER) {
                    mutableOwner.value = owner
                    return@collect
                }

                // Counted before publication: this order is the guarantee. Any downstream observer
                // resumed by the assignment below already sees the recovery as outstanding.
                mutableOutstanding.update { count -> count + 1 }
                mutableOwner.value = owner

                scope.launch {
                    try {
                        // `sync` rather than `requestSync`, because this is the one caller that needs
                        // the cycle's completion: the count describes an interval, so a
                        // fire-and-forget request would leave it raised with nothing to lower it. It
                        // still enters the single controller and obeys every `§9.1` and `§9.8`
                        // admission rule, including the offline and `LOCAL_OWNER` refusals, which is
                        // what keeps a refused cycle from stranding an offline device.
                        syncController.sync(SyncTrigger.OwnerChanged)
                    } finally {
                        // Also on failure and on cancellation, and under `NonCancellable` so a graph
                        // closed mid-recovery still lowers what it raised. A failed recovery is a
                        // condition the owner can see and retry through the existing
                        // unreadable-list path.
                        withContext(NonCancellable) {
                            mutableOutstanding.update { count ->
                                check(count > 0) { "Owner recovery count underflow" }
                                count - 1
                            }
                        }
                    }
                }
            }
        }
}
