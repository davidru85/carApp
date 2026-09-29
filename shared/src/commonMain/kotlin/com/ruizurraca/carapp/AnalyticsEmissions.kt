package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsTracker
import com.ruizurraca.carapp.core.analytics.AnalyticsUserProperties
import com.ruizurraca.carapp.core.analytics.CountBucket
import com.ruizurraca.carapp.core.analytics.SyncStatusCategory
import com.ruizurraca.carapp.core.analytics.toSyncStatusCategory
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.database.OwnerActiveRowCountDatabaseAccess
import com.ruizurraca.carapp.core.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * The `docs/CONTRACTS.md §16.1` emission orchestration of `E3-09` (`D-196`, ADR-0196).
 *
 * It owns the two rules that describe *when* the tracker is told something, as opposed to what it
 * sends:
 *
 * - the opt-in gate: collection is enabled at the first `analyticsEnabled = true`, which is also the
 *   one moment `setUserProperties` is called before any write; disabled at every `false`, with no
 *   user properties; a repeated `true` repeats nothing;
 * - the sync-status edge: `SyncStatusChanged` is emitted when, and only when, the resolved category
 *   changes, so a status re-emitting the same value is not a new product event.
 *
 * The two write events ([AnalyticsEvent.VehicleCreated], [AnalyticsEvent.FuelEntryCreated]) and the
 * post-write `setUserProperties` cadence live in [AnalyticsNotifyingVehicleRepository] and
 * [AnalyticsNotifyingFuelEntryRepository], because they belong to the write that produced them.
 *
 * Everything here is `:shared` orchestration. `docs/TECHNICAL_PLAN.md §4` forbids a feature
 * `presentation` package from reaching `:core:analytics` and `§16.1` forbids analytics in domain and
 * data logic, so no other module could hold these call sites.
 *
 * [enabled] is a local mirror of the opt-in, used only to avoid a pointless count query on the write
 * path while collection is off. It is not the gate: the tracker drops everything while disabled, so
 * a stale mirror can waste a query but cannot leak an event.
 */
internal class AnalyticsEmissions(
    private val tracker: AnalyticsTracker,
    private val ownerContext: OwnerContext,
    private val counts: OwnerActiveRowCountDatabaseAccess,
) {
    @Volatile
    private var enabled = false

    /**
     * Wires the opt-in gate and the sync-status edge onto the graph's scope.
     *
     * [settings] is the repository flow the graph already bootstraps, and [status] is the single
     * `SyncController.status` the whole app observes (`§14`), so neither rule can be re-derived by a
     * second observer.
     */
    fun launchIn(
        scope: CoroutineScope,
        settings: Flow<Outcome<UserSettings, AppError>>,
        status: Flow<SyncStatus>,
    ) {
        scope.launch {
            settings
                .mapNotNull { result -> (result as? Outcome.Ok)?.value }
                .map { settings -> settings.analyticsEnabled }
                // A settings re-emission repeating the value is not a new opt-in transition.
                .distinctUntilChanged()
                .collect { enabled -> applyOptIn(enabled) }
        }
        scope.launch {
            var previous: SyncStatusCategory? = null
            status.collect { value ->
                val category = value.toSyncStatusCategory()
                // The first resolved category establishes the baseline and is itself an event, which
                // is what makes the series complete rather than starting at the second value.
                if (category != previous) {
                    previous = category
                    tracker.track(AnalyticsEvent.SyncStatusChanged(category))
                }
            }
        }
    }

    /** Refreshes the user-property buckets after a successful write, per the `§16.1` cadence. */
    suspend fun refreshUserProperties() {
        if (!enabled) return
        tracker.setUserProperties(currentBuckets())
    }

    /** Emits one write event and then refreshes the buckets, in that order. */
    suspend fun trackAndRefresh(event: AnalyticsEvent) {
        tracker.track(event)
        refreshUserProperties()
    }

    private suspend fun applyOptIn(enabled: Boolean) {
        this.enabled = enabled
        tracker.setEnabled(enabled)
        // The cadence's opt-in half: the one `setUserProperties` call that belongs to the transition
        // itself. The disabled half deliberately does not call it.
        if (enabled) tracker.setUserProperties(currentBuckets())
    }

    private suspend fun currentBuckets(): AnalyticsUserProperties {
        val owned = counts.activeRowCounts(ownerContext.current.value)
        return AnalyticsUserProperties(
            vehicleCountBucket = CountBucket.ofCount(owned.vehicleCount.toInt()),
            entryCountBucket = CountBucket.ofCount(owned.entryCount.toInt()),
        )
    }
}
