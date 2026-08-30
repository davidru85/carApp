package com.ruizurraca.carapp.core.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver

/**
 * E0-07 internal adapter replaced by the persistent platform factory before the story closes.
 * It exercises the accepted bundled SQLite stack without leaking database types into wiring.
 */
fun createStagedDatabaseFactory(): DatabaseFactory =
    object : DatabaseFactory {
        override fun create(): DatabaseHandle =
            SqlDriverDatabaseHandle(
                AndroidxSqliteDriver(
                    driver = BundledSQLiteDriver(),
                    databaseType = AndroidxSqliteDatabaseType.Memory,
                    schema = AppDatabase.Schema,
                ),
            )
    }
