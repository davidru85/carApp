package com.ruizurraca.carapp.core.common

import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.OwnerId
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The platform abstractions of `docs/CONTRACTS.md §20.3`.
 *
 * These exist so that domain, data and presentation code never touches a platform API directly.
 * The architecture check of `E0-04` fails the build on a direct reference.
 */

/**
 * The single source of time. Direct use of a system clock is FORBIDDEN outside `:wiring:*` and
 * `:core:testing` (`§2`).
 *
 * The `Instant` package is pinned in `docs/versions-matrix.md` and is `kotlin.time.Instant`.
 */
fun interface AppClock {
    fun now(): Instant
}

/** Produces lowercase canonical UUID v4 strings. */
fun interface UuidGenerator {
    fun newId(): String
}

interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LocaleInfo(
    val languageTag: String,
    val region: String?,
    val suggestedCurrency: CurrencyCode,
)

fun interface LocaleProvider {
    fun current(): LocaleInfo
}

interface ConnectivityObserver {
    val isOnline: StateFlow<Boolean>
}

enum class SyncTrigger {
    AppForeground,
    ConnectivityRecovered,
    PostWriteDebounce,
    PullToRefresh,
    Periodic,
}

fun interface SyncTriggerAdapter {
    fun schedule(reason: SyncTrigger)
}

/**
 * Shared by `:core:analytics` (Phase 0) and `:core:auth` (Phase 2). It lives here so that neither
 * module has to depend on the other, which is what lets a Phase 0 module compile without a Phase 2
 * module (`docs/CONTRACTS.md §18`, assertion 18).
 *
 * The provider set is closed: anonymous, Google and Apple, and nothing else
 * (`docs/CONTRACTS.md §20.3`, `docs/SPECIFICATION.md §7 F-1`).
 */
enum class AuthProvider { ANONYMOUS, GOOGLE, APPLE }

/**
 * The current owner, as seen by repositories. Feature `data` cannot depend on `:core:auth`
 * (`docs/TECHNICAL_PLAN.md §4`), so it reaches the owner through this abstraction, which
 * `:core:auth` implements and wiring binds.
 */
interface OwnerContext {
    val current: OwnerId

    fun observe(): Flow<OwnerId>
}
