package com.ruizurraca.carapp.core.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver

/** Owns one local database and the SQL driver that must be released with it (`D-89`). */
interface DatabaseHandle {
    val database: AppDatabase

    fun close()
}

/** Creates an owned local database handle (`docs/CONTRACTS.md §20.3.2`). */
interface DatabaseFactory {
    fun create(): DatabaseHandle
}

/** Creates the production bundled-SQLite database at a host-owned sandbox path. */
fun createPersistentDatabaseFactory(databaseFilePath: String): DatabaseFactory =
    object : DatabaseFactory {
        override fun create(): DatabaseHandle =
            SqlDriverDatabaseHandle(
                AndroidxSqliteDriver(
                    driver = BundledSQLiteDriver(),
                    databaseType = AndroidxSqliteDatabaseType.File(databaseFilePath),
                    schema = AppDatabase.Schema,
                ),
            )
    }

internal class SqlDriverDatabaseHandle(
    private val driver: SqlDriver,
) : DatabaseHandle {
    override val database: AppDatabase = AppDatabase(driver)
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        driver.close()
    }
}
