package com.ruizurraca.carapp.core.analytics

/**
 * The analytics abstraction of `docs/CONTRACTS.md §16.1` (`D-10`).
 *
 * **Collection is disabled by default** and is enabled only after the user opts in from Settings.
 * While disabled, [track] and [setUserProperties] are no-ops and nothing is buffered — not
 * queued, not held for a later flush.
 *
 * [setUserProperties] is called once on analytics opt-in and thereafter on every successful
 * vehicle or fuel-entry create or delete, from the presentation layer. It MUST NOT be called from
 * domain or data.
 *
 * Analytics calls are FORBIDDEN in domain logic and data persistence logic. Shared presentation or
 * application-level orchestration may track product events after a use case returns `Ok` or `Err`,
 * provided the payload carries no user data — which the closed [AnalyticsEvent] hierarchy already
 * guarantees.
 *
 * This module contains no Firebase, GitLive or Android type. The Firebase implementation lives in
 * `:integration:firebase-analytics` (`E3-09`) and is bound by `:wiring:firebase`.
 */
interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)

    fun setUserProperties(properties: AnalyticsUserProperties)

    fun setEnabled(enabled: Boolean)
}
