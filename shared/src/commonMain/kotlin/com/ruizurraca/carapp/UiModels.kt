@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessage
import kotlin.native.ObjCName

enum class SessionPhase { UNKNOWN, LOCAL, ANONYMOUS, PERMANENT, SIGNED_OUT, DELETING }

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
)

data class SyncUiState(
    val status: SyncStatus,
    val isOnline: Boolean,
    val message: UiMessage?,
)
