package com.ruizurraca.carapp.core.testing

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.ConnectivityObserver
import com.ruizurraca.carapp.core.common.DispatcherProvider
import com.ruizurraca.carapp.core.common.LocaleInfo
import com.ruizurraca.carapp.core.common.LocaleProvider
import com.ruizurraca.carapp.core.common.LogLevel
import com.ruizurraca.carapp.core.common.Logger
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.common.SyncTriggerAdapter
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Deterministic fakes for the platform abstractions of `docs/CONTRACTS.md §20.3`.
 *
 * `:core:testing` is the only `:core:*` module allowed to depend on every other `:core:*` module
 * (`docs/TECHNICAL_PLAN.md §4`). Its `commonMain` public surface MUST stay Kotlin-pure: no
 * platform API may appear here, and platform APIs are permitted only inside `expect`/`actual` test
 * doubles.
 */

/** A clock that never moves unless [advanceBy] or [set] is called. */
class FakeAppClock(initial: Instant = DEFAULT_NOW) : AppClock {
    private var current: Instant = initial

    override fun now(): Instant = current

    fun set(instant: Instant) {
        current = instant
    }

    fun advanceBy(millis: Long) {
        current = Instant.fromEpochMilliseconds(current.toEpochMilliseconds() + millis)
    }

    companion object {
        /** 2026-01-01T00:00:00Z, a fixed point so test expectations never depend on the wall clock. */
        val DEFAULT_NOW: Instant = Instant.fromEpochMilliseconds(1_767_225_600_000L)
    }
}

/** Emits `00000000-0000-4000-8000-<counter>`, so generated IDs are stable and ordered. */
class FakeUuidGenerator : UuidGenerator {
    private var counter = 0L

    override fun newId(): String {
        counter += 1
        return "00000000-0000-4000-8000-" + counter.toString().padStart(12, '0')
    }
}

/** Every dispatcher is [Dispatchers.Unconfined], so fake-driven tests run without a scheduler. */
class TestDispatcherProvider(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
}

/** Captures log lines so a test can assert on levels, codes and fields without a sink. */
class RecordingLogger : Logger {
    data class Entry(
        val level: LogLevel,
        val tag: String,
        val message: String,
        val fields: Map<String, String>,
        val throwable: Throwable?,
    )

    private val recorded = mutableListOf<Entry>()

    val entries: List<Entry> get() = recorded.toList()

    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        fields: Map<String, String>,
        throwable: Throwable?,
    ) {
        recorded += Entry(level, tag, message, fields, throwable)
    }

    fun clear() = recorded.clear()
}

/** Reports a fixed locale; defaults to Spain and EUR, the MVP's initial market. */
class FakeLocaleProvider(
    private var info: LocaleInfo = LocaleInfo("es-ES", "ES", CurrencyCode("EUR")),
) : LocaleProvider {
    override fun current(): LocaleInfo = info

    fun set(value: LocaleInfo) {
        info = value
    }
}

/** Connectivity that a test drives explicitly; online by default. */
class FakeConnectivityObserver(initiallyOnline: Boolean = true) : ConnectivityObserver {
    private val state = MutableStateFlow(initiallyOnline)

    override val isOnline: StateFlow<Boolean> = state

    fun set(online: Boolean) {
        state.value = online
    }
}

/** Owner context starting at the `LOCAL_OWNER` sentinel of `§11.4`. */
class FakeOwnerContext(initial: OwnerId = LOCAL_OWNER) : OwnerContext {
    private val state = MutableStateFlow(initial)

    override val current: OwnerId get() = state.value

    override fun observe(): Flow<OwnerId> = state

    fun set(owner: OwnerId) {
        state.value = owner
    }
}

/** Records scheduling requests instead of touching a platform scheduler. */
class RecordingSyncTriggerAdapter : SyncTriggerAdapter {
    private val recorded = mutableListOf<SyncTrigger>()

    val scheduled: List<SyncTrigger> get() = recorded.toList()

    override fun schedule(reason: SyncTrigger) {
        recorded += reason
    }

    fun clear() = recorded.clear()
}
