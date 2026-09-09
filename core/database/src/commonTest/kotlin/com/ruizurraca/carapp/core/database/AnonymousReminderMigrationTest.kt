package com.ruizurraca.carapp.core.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `docs/TECHNICAL_PLAN.md §6`: every schema version bump ships a committed `.sqm` migration and a
 * test that migrates a populated previous-version database and asserts row preservation.
 * Destructive schema recreation is forbidden.
 */
class AnonymousReminderMigrationTest {
    @Test
    fun theSchemaVersionIsThree() {
        assertEquals(3L, AppDatabase.Schema.version)
    }

    @Test
    fun migratingAPopulatedVersionOneDatabasePreservesEveryRow() =
        runTest {
            val driver = versionOneDriver()
            try {
                driver.insertVehicle(deleted = 0, deletedAt = null)
                driver.insertFuelEntry(deleted = 0, deletedAt = null)
                driver
                    .execute(
                        identifier = null,
                        sql =
                            "INSERT INTO user_settings(id, currency, distanceUnit, volumeUnit, analyticsEnabled) " +
                                "VALUES (0, 'EUR', 'KM', 'LITER', 0)",
                        parameters = 0,
                    ).await()

                AppDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2).await()

                assertEquals(1L, driver.nullableLong("SELECT COUNT(*) FROM vehicle"))
                assertEquals(1L, driver.nullableLong("SELECT COUNT(*) FROM fuel_entry"))
                assertEquals(
                    "EUR",
                    driver.nullableString("SELECT currency FROM user_settings WHERE id = 0"),
                )
            } finally {
                driver.close()
            }
        }

    @Test
    fun migratingAPopulatedVersionOneDatabaseAddsTheEmptyReminderTable() =
        runTest {
            val driver = versionOneDriver()
            try {
                driver.insertVehicle(deleted = 0, deletedAt = null)

                AppDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2).await()

                assertEquals(0L, driver.nullableLong("SELECT COUNT(*) FROM anonymous_reminder"))

                val access = AnonymousReminderDatabaseAccess(AppDatabase(driver))
                access.upsertReminder(AnonymousReminderRow("anonymous-uid", 1))
                assertEquals(AnonymousReminderRow("anonymous-uid", 1), access.selectReminder())
            } finally {
                driver.close()
            }
        }

    @Test
    fun aFreshInstallCreatesAnEmptyReminderTable() =
        runTest {
            val testDatabase = TestDatabase.create()
            try {
                assertNull(AnonymousReminderDatabaseAccess(testDatabase.database).selectReminder())
            } finally {
                testDatabase.close()
            }
        }

    @Test
    fun migratingAPopulatedVersionTwoDatabasePreservesRowsAndAddsTheConversionStore() =
        runTest {
            val driver = versionOneDriver()
            try {
                driver.insertVehicle(deleted = 0, deletedAt = null)
                driver.insertFuelEntry(deleted = 0, deletedAt = null)
                AppDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2).await()
                driver
                    .execute(
                        identifier = null,
                        sql =
                            "INSERT INTO anonymous_reminder(id, anonymousUid, lastShownIndex) " +
                                "VALUES (0, 'anonymous-uid', 2)",
                        parameters = 0,
                    ).await()

                AppDatabase.Schema.migrate(driver, oldVersion = 2, newVersion = 3).await()

                assertEquals(1L, driver.nullableLong("SELECT COUNT(*) FROM vehicle"))
                assertEquals(1L, driver.nullableLong("SELECT COUNT(*) FROM fuel_entry"))
                assertEquals(2L, driver.nullableLong("SELECT lastShownIndex FROM anonymous_reminder WHERE id = 0"))
                assertEquals(0L, driver.nullableLong("SELECT COUNT(*) FROM account_conversion_operation"))
                assertEquals(0L, driver.nullableLong("SELECT COUNT(*) FROM account_conversion_snapshot"))
            } finally {
                driver.close()
            }
        }

    @Test
    fun migratingDirectlyFromVersionOneToVersionThreeAppliesEveryIntermediateMigration() =
        runTest {
            val driver = versionOneDriver()
            try {
                driver.insertVehicle(deleted = 0, deletedAt = null)
                driver.insertFuelEntry(deleted = 0, deletedAt = null)

                AppDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 3).await()

                assertEquals(1L, driver.nullableLong("SELECT COUNT(*) FROM vehicle"))
                assertEquals(1L, driver.nullableLong("SELECT COUNT(*) FROM fuel_entry"))
                assertEquals(0L, driver.nullableLong("SELECT COUNT(*) FROM anonymous_reminder"))
                assertEquals(0L, driver.nullableLong("SELECT COUNT(*) FROM account_conversion_operation"))
            } finally {
                driver.close()
            }
        }

    private fun versionOneDriver(): SqlDriver =
        AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.Memory,
            schema = SchemaVersionOne,
        )
}

/**
 * The schema exactly as version 1 shipped. It is duplicated here on purpose: a migration test that
 * built its starting point from the current `.sq` files would prove nothing about an installed
 * database.
 */
private object SchemaVersionOne : SqlSchema<QueryResult.AsyncValue<Unit>> {
    override val version: Long = 1

    override fun create(driver: SqlDriver): QueryResult.AsyncValue<Unit> =
        QueryResult.AsyncValue {
            VERSION_ONE_STATEMENTS.forEach { statement ->
                driver.execute(identifier = null, sql = statement, parameters = 0).await()
            }
        }

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: app.cash.sqldelight.db.AfterVersion,
    ): QueryResult.AsyncValue<Unit> = QueryResult.AsyncValue { }
}

private val VERSION_ONE_STATEMENTS =
    listOf(
        """
        CREATE TABLE vehicle (
          id TEXT NOT NULL PRIMARY KEY,
          ownerId TEXT NOT NULL,
          name TEXT NOT NULL,
          nameFold TEXT NOT NULL,
          initialOdometerKm INTEGER NOT NULL,
          currentOdometerKm INTEGER NOT NULL,
          brand TEXT,
          model TEXT,
          fuelType TEXT NOT NULL CHECK (fuelType IN ('GASOLINE', 'DIESEL', 'LPG', 'CNG', 'OTHER')),
          createdAt INTEGER NOT NULL,
          updatedAt INTEGER NOT NULL,
          serverUpdatedAt INTEGER,
          deleted INTEGER NOT NULL CHECK (deleted IN (0, 1)),
          deletedAt INTEGER,
          syncState TEXT NOT NULL CHECK (
            syncState IN ('PENDING', 'SYNCING', 'SYNCED', 'FAILED_RETRYABLE', 'FAILED_POISONED')
          ),
          localRevision INTEGER NOT NULL,
          localMutationSeq INTEGER NOT NULL,
          schemaVersion INTEGER NOT NULL,
          CHECK ((deleted = 0 AND deletedAt IS NULL) OR (deleted = 1 AND deletedAt IS NOT NULL))
        )
        """.trimIndent(),
        """
        CREATE TABLE fuel_entry (
          id TEXT NOT NULL PRIMARY KEY,
          ownerId TEXT NOT NULL,
          vehicleId TEXT NOT NULL,
          date INTEGER NOT NULL,
          odometerKm INTEGER NOT NULL,
          litersScaled INTEGER NOT NULL,
          pricePerLiterScaled INTEGER NOT NULL,
          totalCostMinor INTEGER NOT NULL,
          currency TEXT NOT NULL,
          isFullTank INTEGER NOT NULL CHECK (isFullTank IN (0, 1)),
          hasMissedEntries INTEGER NOT NULL CHECK (hasMissedEntries IN (0, 1)),
          odometerInconsistent INTEGER NOT NULL CHECK (odometerInconsistent IN (0, 1)),
          notes TEXT,
          createdAt INTEGER NOT NULL,
          updatedAt INTEGER NOT NULL,
          serverUpdatedAt INTEGER,
          deleted INTEGER NOT NULL CHECK (deleted IN (0, 1)),
          deletedAt INTEGER,
          syncState TEXT NOT NULL CHECK (
            syncState IN ('PENDING', 'SYNCING', 'SYNCED', 'FAILED_RETRYABLE', 'FAILED_POISONED')
          ),
          localRevision INTEGER NOT NULL,
          localMutationSeq INTEGER NOT NULL,
          schemaVersion INTEGER NOT NULL,
          CHECK ((deleted = 0 AND deletedAt IS NULL) OR (deleted = 1 AND deletedAt IS NOT NULL))
        )
        """.trimIndent(),
        """
        CREATE TABLE user_settings (
          id INTEGER NOT NULL PRIMARY KEY CHECK (id = 0),
          currency TEXT NOT NULL,
          distanceUnit TEXT NOT NULL CHECK (distanceUnit = 'KM'),
          volumeUnit TEXT NOT NULL CHECK (volumeUnit = 'LITER'),
          analyticsEnabled INTEGER NOT NULL CHECK (analyticsEnabled IN (0, 1))
        )
        """.trimIndent(),
        """
        CREATE TABLE local_sequence (
          id INTEGER PRIMARY KEY CHECK (id = 0),
          next INTEGER NOT NULL DEFAULT 1
        )
        """.trimIndent(),
        "INSERT INTO local_sequence(id, next) VALUES (0, 1)",
        """
        CREATE TABLE outbox (
          seq INTEGER PRIMARY KEY AUTOINCREMENT,
          entityType TEXT NOT NULL CHECK (entityType IN ('VEHICLE', 'FUEL_ENTRY')),
          entityId TEXT NOT NULL,
          payload TEXT NOT NULL,
          localRevision INTEGER NOT NULL,
          attemptCount INTEGER NOT NULL DEFAULT 0,
          nextAttemptAt INTEGER NOT NULL DEFAULT 0,
          lastError TEXT,
          lastErrorCode TEXT,
          cycleId TEXT,
          UNIQUE(entityType, entityId)
        )
        """.trimIndent(),
        "CREATE INDEX idx_outbox_due ON outbox(nextAttemptAt, seq)",
        """
        CREATE TABLE sync_cursor (
          entityType TEXT NOT NULL CHECK (entityType IN ('VEHICLE', 'FUEL_ENTRY')),
          lastServerUpdatedAt INTEGER NOT NULL,
          lastDocumentId TEXT NOT NULL,
          PRIMARY KEY (entityType)
        )
        """.trimIndent(),
        """
        CREATE TABLE quarantine (
          entityType TEXT NOT NULL CHECK (entityType IN ('VEHICLE', 'FUEL_ENTRY')),
          entityId TEXT NOT NULL,
          reason TEXT NOT NULL CHECK (reason IN ('UnsupportedSchemaVersion', 'MalformedPayload')),
          schemaVersion INTEGER NOT NULL,
          serverUpdatedAt INTEGER NOT NULL,
          rawJson TEXT NOT NULL,
          createdAt INTEGER NOT NULL,
          UNIQUE(entityType, entityId)
        )
        """.trimIndent(),
    )
