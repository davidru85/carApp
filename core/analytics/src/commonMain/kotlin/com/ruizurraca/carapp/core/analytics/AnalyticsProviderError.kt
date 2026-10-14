package com.ruizurraca.carapp.core.analytics

/**
 * The closed taxonomy of analytics provider failures (`docs/CONTRACTS.md §16.1`, `E3-09`).
 *
 * A provider failure is never propagated — the three `AnalyticsTracker` entry points have no error
 * channel — so it is classified into a stable code here and reported through the tracker's
 * injected sink. Classification is what keeps the report privacy-safe: the provider's own exception
 * text never leaves `:integration:firebase-analytics`, only a code and a tag do, exactly as
 * `docs/CONTRACTS.md §17` requires of anything that reaches the log.
 */
enum class AnalyticsProviderError(
    val code: String,
) {
    /**
     * The provider rejected or could not perform the call. `D-10` makes metrics best-effort, so the
     * product path continues; the code exists so the drop is observable instead of silent.
     */
    PROVIDER_FAILED("ANALYTICS.PROVIDER_FAILED"),
}
