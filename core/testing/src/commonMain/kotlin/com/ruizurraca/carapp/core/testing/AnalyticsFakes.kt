package com.ruizurraca.carapp.core.testing

import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsTracker
import com.ruizurraca.carapp.core.analytics.AnalyticsUserProperties

/**
 * The default tracker for tests and for any build without a provider. It records nothing,
 * buffers nothing and reaches no SDK (`docs/CONTRACTS.md §16.1`).
 */
object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) = Unit

    override fun setUserProperties(properties: AnalyticsUserProperties) = Unit

    override fun setEnabled(enabled: Boolean) = Unit
}

/**
 * A tracker that honours the opt-in semantics of `§16.1` so a test can assert them.
 *
 * Collection is **disabled by default**. While disabled, `track` and `setUserProperties` are
 * no-ops that buffer nothing: the events are dropped, not queued for a later flush. Enabling
 * afterwards therefore MUST NOT replay anything that happened while disabled — which is the part
 * an implementation is most likely to get wrong, and the reason this fake exists rather than a
 * plain recording list.
 */
class RecordingAnalyticsTracker(initiallyEnabled: Boolean = false) : AnalyticsTracker {
    private var enabled = initiallyEnabled
    private val recordedEvents = mutableListOf<AnalyticsEvent>()
    private val recordedProperties = mutableListOf<AnalyticsUserProperties>()

    val events: List<AnalyticsEvent> get() = recordedEvents.toList()
    val userProperties: List<AnalyticsUserProperties> get() = recordedProperties.toList()
    val isEnabled: Boolean get() = enabled

    override fun track(event: AnalyticsEvent) {
        if (enabled) recordedEvents += event
    }

    override fun setUserProperties(properties: AnalyticsUserProperties) {
        if (enabled) recordedProperties += properties
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun clear() {
        recordedEvents.clear()
        recordedProperties.clear()
    }
}
