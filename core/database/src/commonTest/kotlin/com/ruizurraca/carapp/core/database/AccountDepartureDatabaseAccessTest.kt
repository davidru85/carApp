package com.ruizurraca.carapp.core.database

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountDepartureDatabaseAccessTest {
    private lateinit var testDatabase: TestDatabase

    @AfterTest
    fun tearDown() {
        if (::testDatabase.isInitialized) testDatabase.close()
    }

    @Test
    fun schemaVersionFourOwnsTheDurableDepartureMarker() {
        assertEquals(4L, AppDatabase.Schema.version)
    }

    @Test
    fun thereIsNoDepartureToResumeOnAFreshDatabase() =
        runTest {
            val access = AccountDepartureDatabaseAccess(createDatabase().database)

            assertNull(access.load())
        }

    @Test
    fun startingADepartureRecordsItsKindAndOwnerWithNoStepDone() =
        runTest {
            val access = AccountDepartureDatabaseAccess(createDatabase().database)

            access.start(DepartureOperationKind.DELETE_PERMANENT, "permanent-owner")

            assertEquals(
                DepartureOperationRow(
                    kind = DepartureOperationKind.DELETE_PERMANENT,
                    ownerUid = "permanent-owner",
                    remoteDone = false,
                    sessionEnded = false,
                    localDone = false,
                ),
                access.load(),
            )
        }

    @Test
    fun eachStepIsRecordedIndependently() =
        runTest {
            val access = AccountDepartureDatabaseAccess(createDatabase().database)
            access.start(DepartureOperationKind.DELETE_PERMANENT, "permanent-owner")

            access.markStep(DepartureOperationStep.REMOTE_DELETION)
            assertEquals(true to false, access.load()?.let { it.remoteDone to it.sessionEnded })

            access.markStep(DepartureOperationStep.SESSION_CLEANUP)
            assertEquals(true to true, access.load()?.let { it.remoteDone to it.sessionEnded })

            access.markStep(DepartureOperationStep.LOCAL_CLEAR)
            assertEquals(true, access.load()?.localDone)
        }

    @Test
    fun startingADepartureReplacesAnyStaleOne() =
        runTest {
            val access = AccountDepartureDatabaseAccess(createDatabase().database)
            access.start(DepartureOperationKind.DELETE_PERMANENT, "permanent-owner")
            access.markStep(DepartureOperationStep.REMOTE_DELETION)

            access.start(DepartureOperationKind.SIGN_OUT, "another-owner")

            assertEquals(
                DepartureOperationRow(DepartureOperationKind.SIGN_OUT, "another-owner", false, false, false),
                access.load(),
            )
        }

    @Test
    fun clearingRemovesTheMarker() =
        runTest {
            val access = AccountDepartureDatabaseAccess(createDatabase().database)
            access.start(DepartureOperationKind.DELETE_ANONYMOUS, "anonymous-owner")

            access.clear()

            assertNull(access.load())
        }

    @Test
    fun aLocalOwnerDepartureRecordsNoOwnerUid() =
        runTest {
            val access = AccountDepartureDatabaseAccess(createDatabase().database)

            access.start(DepartureOperationKind.DELETE_LOCAL, ownerUid = null)

            assertNull(access.load()?.ownerUid)
            assertEquals(DepartureOperationKind.DELETE_LOCAL, access.load()?.kind)
        }

    /**
     * An anonymous deletion clears local data and only then ends the provider session, so the marker
     * that records the second step MUST survive the first. Wiping it in the clear would make the
     * very operation performing the clear unrecoverable.
     */
    @Test
    fun theDepartureMarkerSurvivesTheLocalDataClear() =
        runTest {
            val database = createDatabase()
            val access = AccountDepartureDatabaseAccess(database.database)
            access.start(DepartureOperationKind.DELETE_ANONYMOUS, "anonymous-owner")
            access.markStep(DepartureOperationStep.LOCAL_CLEAR)

            LocalDataClearDatabaseAccess(database.database).clearAllLocalData()

            val survivor = access.load()
            assertEquals(DepartureOperationKind.DELETE_ANONYMOUS, survivor?.kind)
            assertTrue(survivor?.localDone == true)
        }

    private fun createDatabase(): TestDatabase = TestDatabase.create().also { testDatabase = it }
}
