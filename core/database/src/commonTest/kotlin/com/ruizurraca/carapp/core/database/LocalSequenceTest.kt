package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsOne
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalSequenceTest {
    private lateinit var testDatabase: TestDatabase

    @AfterTest
    fun tearDown() {
        if (::testDatabase.isInitialized) testDatabase.close()
    }

    @Test
    fun localMutationSequenceIsMonotonicAcrossEntityTypes() =
        runTest {
            val queries = createDatabase().database.databaseQueries

            val vehicleMutationSeq = queries.nextLocalMutationSequence().awaitAsOne()
            val fuelEntryMutationSeq = queries.nextLocalMutationSequence().awaitAsOne()

            assertEquals(2L, vehicleMutationSeq)
            assertEquals(3L, fuelEntryMutationSeq)
        }

    @Test
    fun writesWithExistingMutationSequencesDoNotConsumeTheCounter() =
        runTest {
            val database = createDatabase()
            database.driver.insertVehicle(deleted = 0, deletedAt = null)
            database.driver.insertFuelEntry(deleted = 0, deletedAt = null)

            assertEquals(
                2L,
                database.database.databaseQueries
                    .nextLocalMutationSequence()
                    .awaitAsOne(),
            )
        }

    private fun createDatabase(): TestDatabase = TestDatabase.create().also { testDatabase = it }
}
