@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.VolumeUnit
import kotlin.native.ObjCName

enum class SessionPhase { UNKNOWN, LOCAL, ANONYMOUS, PERMANENT, SIGNED_OUT, DELETING }

/**
 * The unfinished part of a departure that `SessionStateHolder.retryDeparture()` would repeat, or
 * `null` when there is nothing to retry (`docs/CONTRACTS.md §20.10`). A retry never repeats a
 * remote step that already succeeded.
 */
@ObjCName(name = "SharedDepartureRetry", swiftName = "DepartureRetry", exact = true)
enum class DepartureRetry { SESSION_CLEANUP, LOCAL_CLEAR }

@ObjCName(name = "SharedNativeSignInFailure", swiftName = "NativeSignInFailure", exact = true)
enum class NativeSignInFailure { CANCELLED, NETWORK, CONFIGURATION, NO_ACCOUNT_AVAILABLE, UNKNOWN }

data class SessionUiState(
    val phase: SessionPhase,
    val providers: List<AuthProvider>,
    val isBusy: Boolean,
    val message: UiMessage?,
    /**
     * The zero-based index of the `D-62` anonymous sign-in benefit reminder currently offered, or
     * `null` when no reminder is being shown (`docs/CONTRACTS.md §11.3`).
     */
    val anonymousReminderIndex: Int?,
    /**
     * The exact number of outbox rows still pending when a sign-out was refused with
     * `ValidationWarning.PendingSyncBeforeSignOut`, or `null` when no such warning is being
     * offered (`docs/CONTRACTS.md §20.10`).
     *
     * It is a typed value, not display copy: each host formats it into its own string resources
     * under §14. Carrying it here is what keeps the warning's `pendingCount` from being lost, since
     * `UiMessage` transports only a code.
     */
    val pendingSyncCount: Int?,
    /**
     * The unfinished departure work a `retryDeparture()` would repeat, or `null` when there is
     * none. It is typed state rather than something the host must remember for itself.
     */
    val pendingDepartureRetry: DepartureRetry?,
)

data class SyncUiState(
    val status: SyncStatus,
    val isOnline: Boolean,
    val message: UiMessage?,
)

/**
 * The state of the Settings screen (`docs/CONTRACTS.md §20.10`).
 *
 * [currencyCode] is `null` until the settings repository has emitted the settings once; until then
 * the other settings fields are placeholders that a host MUST NOT render as known values. Currency
 * codes cross as `String` and carry no display name: each host resolves the name through its
 * platform currency API, so this state holds no user-facing text (`docs/CONTRACTS.md §14`).
 */
data class SettingsUiState(
    val currencyCode: String?,
    val supportedCurrencyCodes: List<String>,
    val distanceUnit: DistanceUnit,
    val volumeUnit: VolumeUnit,
    val analyticsEnabled: Boolean,
    val isSaving: Boolean,
    val message: UiMessage?,
)
