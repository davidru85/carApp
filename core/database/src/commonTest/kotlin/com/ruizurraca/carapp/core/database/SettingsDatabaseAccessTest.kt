package com.ruizurraca.carapp.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsDatabaseAccessTest {
    private lateinit var testDatabase: TestDatabase

    @AfterTest
    fun tearDown() {
        if (::testDatabase.isInitialized) testDatabase.close()
    }

    @Test
    fun upsertMaintainsExactlyOneFixedMetricSettingsRow() =
        runTest {
            val access = access()

            access.upsertSettings(settings(currency = "EUR", analyticsEnabled = false))
            access.upsertSettings(settings(currency = "USD", analyticsEnabled = true))

            assertEquals(1L, testDatabase.driver.nullableLong("SELECT COUNT(*) FROM user_settings"))
            assertEquals(
                SettingsDatabaseRow("USD", "KM", "LITER", analyticsEnabled = true),
                access.observeSettings().first(),
            )
        }

    @Test
    fun settingsWritesDoNotTouchFuelEntriesOrOutbox() =
        runTest {
            val access = access()
            testDatabase.driver.insertFuelEntry(deleted = 0, deletedAt = null)

            access.upsertSettings(settings(currency = "USD", analyticsEnabled = false))
            access.upsertSettings(settings(currency = "GBP", analyticsEnabled = true))

            assertEquals(
                "EUR",
                testDatabase.driver.nullableString("SELECT currency FROM fuel_entry WHERE id = 'entry-1'"),
            )
            assertEquals(0L, testDatabase.driver.nullableLong("SELECT COUNT(*) FROM outbox"))
        }

    @Test
    fun deleteRemovesTheDeviceLocalRow() =
        runTest {
            val access = access()
            access.upsertSettings(settings(currency = "EUR", analyticsEnabled = false))

            access.deleteSettings()

            assertNull(access.observeSettings().first())
        }

    private fun access(): SettingsDatabaseAccess {
        testDatabase = TestDatabase.create()
        return SettingsDatabaseAccess(testDatabase.database)
    }
}

private fun settings(
    currency: String,
    analyticsEnabled: Boolean,
) = SettingsDatabaseRow(
    currency = currency,
    distanceUnit = "KM",
    volumeUnit = "LITER",
    analyticsEnabled = analyticsEnabled,
)
