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
 * The only gate is the tracker (`§16.1`: while disabled, `track` and `setUserProperties` are
 * no-ops). [enabled] is **not** a second gate: it exists so that a write path with analytics off
 * performs no count query at all, which is what keeps the off state free rather than merely silent.
 * If it ever disagreed with the tracker, the tracker would still drop the call, so the flag can only
 * cost or save a read — it can never let a payload through.
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
     * A non-authoritative projection of the last opt-in the tracker was commanded, used only to skip
     * the bucket count while collection is off. See the class KDoc.
     */
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
        settings: () -> Flow<Outcome<UserSettings, AppError>>,
        status: Flow<SyncStatus>,
    ) {
        // A dispatched launch, unlike the connectivity observer: the settings flow emits its current
        // value on subscription, so nothing is missed by starting one dispatch later, and a fixture
        // that must install its own source has a moment to do it before the collector starts.
        scope.launch {
            settings()
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
        // A no-op while collection is off, so an opted-out device pays nothing for a write it makes.
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
        val owned = activeRowCounts(ownerContext.current.value)
        return AnalyticsUserProperties(
            vehicleCountBucket = CountBucket.ofCount(owned.vehicleCount.toInt()),
            entryCountBucket = CountBucket.ofCount(owned.entryCount.toInt()),
        )
    }
}
