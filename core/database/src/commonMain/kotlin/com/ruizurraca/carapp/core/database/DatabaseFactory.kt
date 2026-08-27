package com.ruizurraca.carapp.core.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver

/** Creates the local database owned by this module (`docs/CONTRACTS.md §20.3.2`). */
interface DatabaseFactory {
    fun create(): AppDatabase
}

/** Creates the production bundled-SQLite database at a host-owned sandbox path. */
fun createPersistentDatabaseFactory(databaseFilePath: String): DatabaseFactory =
    object : DatabaseFactory {
        override fun create(): AppDatabase =
            AppDatabase(
                AndroidxSqliteDriver(
                    driver = BundledSQLiteDriver(),
                    databaseType = AndroidxSqliteDatabaseType.File(databaseFilePath),
                    schema = AppDatabase.Schema,
                ),
            )
    }
