package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull

/** The device-local anonymous reminder schedule position of `docs/CONTRACTS.md §11.3`. */
data class AnonymousReminderRow(
    val anonymousUid: String,
    val lastShownIndex: Int,
)

/** Keeps SQLDelight-generated anonymous reminder types inside `:core:database`. */
class AnonymousReminderDatabaseAccess(
    private val database: AppDatabase,
) {
    suspend fun selectReminder(): AnonymousReminderRow? =
        database.databaseQueries
            .selectAnonymousReminder()
            .awaitAsOneOrNull()
            ?.let { row -> AnonymousReminderRow(row.anonymousUid, row.lastShownIndex.toInt()) }

    suspend fun upsertReminder(row: AnonymousReminderRow) {
        database.databaseQueries.upsertAnonymousReminder(
            anonymousUid = row.anonymousUid,
            lastShownIndex = row.lastShownIndex.toLong(),
        )
    }

    suspend fun deleteReminder() {
        database.databaseQueries.deleteAnonymousReminder()
    }
}
