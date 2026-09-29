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
import com.ruizurraca.carapp.core.database.OwnerActiveRowCounts
import com.ruizurraca.carapp.core.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

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
 * There is deliberately **no local mirror of the opt-in state**. The tracker is the single gate
 * (`§16.1`: while disabled, `track` and `setUserProperties` are no-ops), and a second copy here
 * would be a second source of truth that a write path could disagree with. A refresh issued while
 * disabled therefore costs one count query and records nothing, which is the honest trade.
 */
internal class AnalyticsEmissions(
    private val tracker: AnalyticsTracker,
    private val ownerContext: OwnerContext,
    /**
     * A port rather than the accessor itself, so the two rules this class owns are testable without a
     * database: a test supplies a pure function and observes the cadence synchronously, while
     * production binds `OwnerActiveRowCountDatabaseAccess::activeRowCounts`. The accessor's own SQL is
     * verified by its database test.
     */
    private val activeRowCounts: suspend (String) -> OwnerActiveRowCounts,
) {
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
        // `UNDISPATCHED`, like the graph's own connectivity observer: subscribing must happen
        // inside the wiring call, so a baseline the source already holds cannot be mistaken for a
        // later change, and the opt-in that is already persisted reaches the provider before the
        // first write rather than one dispatch later.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settings
                .mapNotNull { result -> (result as? Outcome.Ok)?.value }
                .map { settings -> settings.analyticsEnabled }
                // A settings re-emission repeating the value is not a new opt-in transition.
                .distinctUntilChanged()
                .collect { enabled -> applyOptIn(enabled) }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
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
        tracker.setUserProperties(currentBuckets())
    }

    /** Emits one write event and then refreshes the buckets, in that order. */
    suspend fun trackAndRefresh(event: AnalyticsEvent) {
        tracker.track(event)
        refreshUserProperties()
    }

    private suspend fun applyOptIn(enabled: Boolean) {
        tracker.setEnabled(enabled)
        // The cadence's opt-in half: the one `setUserProperties` call that belongs to the transition
        // itself. The disabled half deliberately does not call it.
        if (enabled) tracker.setUserProperties(currentBuckets())
    }

    private suspend fun currentBuckets(): AnalyticsUserProperties {
        val owned = activeRowCounts(ownerContext.current.value)
        return AnalyticsUserProperties(
            vehicleCountBucket = CountBucket.ofCount(owned.vehicleCount.toInt()),
            entryCountBucket = CountBucket.ofCount(owned.entryCount.toInt()),
        )
    }
}
