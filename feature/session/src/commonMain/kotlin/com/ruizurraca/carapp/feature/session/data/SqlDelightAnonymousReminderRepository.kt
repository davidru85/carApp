@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.feature.session.data

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.AnonymousReminderDatabaseAccess
import com.ruizurraca.carapp.feature.session.domain.AnonymousReminderRepository
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Suppress("UnusedPrivateProperty", "UnusedParameter")
class SqlDelightAnonymousReminderRepository(
    private val databaseAccess: AnonymousReminderDatabaseAccess,
) : AnonymousReminderRepository {
    override suspend fun lastShownIndex(anonymousUid: String): Outcome<Int?, AppError> {
        // RED: declared without behaviour so the reminder persistence tests compile and execute.
        return Outcome.Ok(null)
    }

    override suspend fun recordShown(
        anonymousUid: String,
        index: Int,
    ): Outcome<Unit, AppError> {
        // RED: declared without behaviour so the reminder persistence tests compile and execute.
        return Outcome.Ok(Unit)
    }

    override suspend fun clear(): Outcome<Unit, AppError> {
        // RED: declared without behaviour so the reminder persistence tests compile and execute.
        return Outcome.Ok(Unit)
    }
}
