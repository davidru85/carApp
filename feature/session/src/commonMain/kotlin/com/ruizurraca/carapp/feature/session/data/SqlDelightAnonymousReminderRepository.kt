@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.feature.session.data

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.database.AnonymousReminderDatabaseAccess
import com.ruizurraca.carapp.core.database.AnonymousReminderRow
import com.ruizurraca.carapp.feature.session.domain.AnonymousReminderRepository
import kotlinx.coroutines.CancellationException
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
class SqlDelightAnonymousReminderRepository(
    private val databaseAccess: AnonymousReminderDatabaseAccess,
) : AnonymousReminderRepository {
    /**
     * A stored position that belongs to another anonymous identity is not this identity's position.
     * Reporting it as absent restarts the schedule, which is the behaviour a new anonymous UID
     * requires.
     */
    override suspend fun lastShownIndex(anonymousUid: String): Outcome<Int?, AppError> =
        persistence {
            databaseAccess.selectReminder()?.takeIf { row -> row.anonymousUid == anonymousUid }?.lastShownIndex
        }

    override suspend fun recordShown(
        anonymousUid: String,
        index: Int,
    ): Outcome<Unit, AppError> =
        persistence {
            databaseAccess.upsertReminder(AnonymousReminderRow(anonymousUid, index))
        }

    override suspend fun clear(): Outcome<Unit, AppError> = persistence { databaseAccess.deleteReminder() }
}

private inline fun <T> persistence(block: () -> T): Outcome<T, AppError> =
    try {
        Outcome.Ok(block())
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Throwable) {
        Outcome.Err(PersistenceError.TransactionFailed)
    }
