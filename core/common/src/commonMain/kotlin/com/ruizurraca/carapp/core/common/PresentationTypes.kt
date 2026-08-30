@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package com.ruizurraca.carapp.core.common

import kotlin.native.ObjCName

@ObjCName(name = "SharedSyncSyncStatus", swiftName = "SyncSyncStatus", exact = true)
sealed class SyncStatus {
    data object Idle : SyncStatus()

    data object Syncing : SyncStatus()

    data class Pending(
        val count: Int,
    ) : SyncStatus()

    data class Failed(
        val retryableCount: Int,
        val poisonedCount: Int,
    ) : SyncStatus()
}

@ObjCName(name = "SharedUiMessageKind", swiftName = "UiMessageKind", exact = true)
enum class UiMessageKind { INFO, WARNING, ERROR }

@ObjCName(name = "SharedUiMessage", swiftName = "UiMessage", exact = true)
data class UiMessage(
    val id: Long,
    val kind: UiMessageKind,
    val code: String,
    val confirmation: Confirmation?,
)
