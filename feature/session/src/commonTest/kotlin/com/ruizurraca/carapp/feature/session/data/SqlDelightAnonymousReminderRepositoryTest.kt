package com.ruizurraca.carapp.feature.session.data

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.AnonymousReminderDatabaseAccess
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The last-shown index of `docs/CONTRACTS.md §11.3` is device-local state that MUST survive a
 * process restart and MUST be bound to the anonymous UID that produced it.
 */
class SqlDelightAnonymousReminderRepositoryTest {
    @Test
    fun theRecordedIndexIsReadBackForTheSameAnonymousUid() =
        runTest {
            withDatabase { handle ->
                val repository = repository(handle)

                repository.recordShown(anonymousUid = "anonymous-uid", index = 2)

                assertEquals(2, lastShownIndex(repository, "anonymous-uid"))
            }
        }

    @Test
    fun theRecordedIndexSurvivesANewRepositoryOverTheSameDatabase() =
        runTest {
            withDatabase { handle ->
                repository(handle).recordShown(anonymousUid = "anonymous-uid", index = 1)

                assertEquals(
                    1,
                    lastShownIndex(repository(handle), "anonymous-uid"),
                    "The schedule position must outlive the process that recorded it.",
                )
            }
        }

    @Test
    fun aDifferentAnonymousIdentityStartsTheScheduleAgain() =
        runTest {
            withDatabase { handle ->
                val repository = repository(handle)
                repository.recordShown(anonymousUid = "first-anonymous-uid", index = 3)

                assertNull(lastShownIndex(repository, "second-anonymous-uid"))
            }
        }

    @Test
    fun clearingRemovesThePersistedSchedulePosition() =
        runTest {
            withDatabase { handle ->
                val repository = repository(handle)
                repository.recordShown(anonymousUid = "anonymous-uid", index = 2)

                repository.clear()

                assertNull(lastShownIndex(repository, "anonymous-uid"))
            }
        }

    private suspend fun lastShownIndex(
        repository: SqlDelightAnonymousReminderRepository,
        anonymousUid: String,
    ): Int? = assertIs<Outcome.Ok<Int?>>(repository.lastShownIndex(anonymousUid)).value

    private fun repository(handle: DatabaseHandle) =
        SqlDelightAnonymousReminderRepository(AnonymousReminderDatabaseAccess(handle.database))

    private suspend fun withDatabase(block: suspend (DatabaseHandle) -> Unit) {
        val handle = InMemoryDatabaseFactory().create()
        try {
            block(handle)
        } finally {
            handle.close()
        }
    }
}
