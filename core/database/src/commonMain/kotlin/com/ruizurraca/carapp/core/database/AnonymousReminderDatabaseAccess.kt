package com.ruizurraca.carapp.core.database

/** The device-local anonymous reminder schedule position of `docs/CONTRACTS.md §11.3`. */
data class AnonymousReminderRow(
    val anonymousUid: String,
    val lastShownIndex: Int,
)

/** Keeps SQLDelight-generated anonymous reminder types inside `:core:database`. */
@Suppress("UnusedPrivateProperty", "FunctionOnlyReturningConstant", "UnusedParameter")
class AnonymousReminderDatabaseAccess(
    private val database: AppDatabase,
) {
    suspend fun selectReminder(): AnonymousReminderRow? {
        // RED: declared without behaviour so the reminder persistence tests compile and execute.
        return null
    }

    suspend fun upsertReminder(row: AnonymousReminderRow) {
        // RED: declared without behaviour so the reminder persistence tests compile and execute.
    }

    suspend fun deleteReminder() {
        // RED: declared without behaviour so the reminder persistence tests compile and execute.
    }
}
