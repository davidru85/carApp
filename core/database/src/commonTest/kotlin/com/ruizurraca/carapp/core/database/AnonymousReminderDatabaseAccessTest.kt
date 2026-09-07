package com.ruizurraca.carapp.core.database

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnonymousReminderDatabaseAccessTest {
    private lateinit var testDatabase: TestDatabase

    @AfterTest
    fun tearDown() {
        if (::testDatabase.isInitialized) testDatabase.close()
    }

    @Test
    fun upsertMaintainsExactlyOneDeviceLocalReminderRow() =
        runTest {
            val access = access()

            access.upsertReminder(AnonymousReminderRow("anonymous-uid", lastShownIndex = 0))
            access.upsertReminder(AnonymousReminderRow("anonymous-uid", lastShownIndex = 2))

            assertEquals(1L, testDatabase.driver.nullableLong("SELECT COUNT(*) FROM anonymous_reminder"))
            assertEquals(AnonymousReminderRow("anonymous-uid", 2), access.selectReminder())
        }

    @Test
    fun anUnwrittenReminderRowReadsAsAbsent() =
        runTest {
            assertNull(access().selectReminder())
        }

    @Test
    fun deleteRemovesTheDeviceLocalReminderRow() =
        runTest {
            val access = access()
            access.upsertReminder(AnonymousReminderRow("anonymous-uid", lastShownIndex = 3))

            access.deleteReminder()

            assertNull(access.selectReminder())
        }

    @Test
    fun reminderWritesDoNotTouchSynchronizedEntitiesOrTheOutbox() =
        runTest {
            val access = access()
            testDatabase.driver.insertFuelEntry(deleted = 0, deletedAt = null)

            access.upsertReminder(AnonymousReminderRow("anonymous-uid", lastShownIndex = 1))

            assertEquals(
                1L,
                testDatabase.driver.nullableLong("SELECT COUNT(*) FROM fuel_entry"),
            )
            assertEquals(0L, testDatabase.driver.nullableLong("SELECT COUNT(*) FROM outbox"))
        }

    private fun access(): AnonymousReminderDatabaseAccess {
        testDatabase = TestDatabase.create()
        return AnonymousReminderDatabaseAccess(testDatabase.database)
    }
}
