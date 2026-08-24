package com.ruizurraca.carapp.core.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver

internal class TestDatabase private constructor(
    val driver: SqlDriver,
    val database: AppDatabase,
) {
    fun close() {
        driver.close()
    }

    companion object {
        fun create(databaseType: AndroidxSqliteDatabaseType = AndroidxSqliteDatabaseType.Memory): TestDatabase {
            val driver =
                AndroidxSqliteDriver(
                    driver = BundledSQLiteDriver(),
                    databaseType = databaseType,
                    schema = AppDatabase.Schema,
                )
            return TestDatabase(driver, AppDatabase(driver))
        }
    }
}

internal suspend fun assertDatabasePersistsAcrossReopen(databaseType: AndroidxSqliteDatabaseType) {
    val initialDatabase = TestDatabase.create(databaseType)
    try {
        initialDatabase.driver.insertVehicle(deleted = 0, deletedAt = null)
    } finally {
        initialDatabase.close()
    }

    val reopenedDatabase = TestDatabase.create(databaseType)
    try {
        kotlin.test.assertEquals(
            1L,
            reopenedDatabase.driver.nullableLong("SELECT COUNT(*) FROM vehicle"),
        )
    } finally {
        reopenedDatabase.close()
    }
}
