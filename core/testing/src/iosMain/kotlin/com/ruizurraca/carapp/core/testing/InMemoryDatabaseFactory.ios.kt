package com.ruizurraca.carapp.core.testing

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseFactory

actual class InMemoryDatabaseFactory actual constructor() : DatabaseFactory {
    private val databases = TrackedInMemoryDatabases()

    actual override fun create(): AppDatabase =
        databases.create(
            AndroidxSqliteDriver(
                driver = BundledSQLiteDriver(),
                databaseType = AndroidxSqliteDatabaseType.Memory,
                schema = AppDatabase.Schema,
            ),
        )

    actual fun close() = databases.close()
}
