package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class IosFileBackedSchemaTest {
    @Test
    fun schemaReopensWithExistingData() =
        runTest {
            val path = "${NSTemporaryDirectory()}carapp-e1-01-${Random.nextLong()}.db"

            try {
                assertDatabasePersistsAcrossReopen(AndroidxSqliteDatabaseType.File(path))
            } finally {
                listOf(path, "$path-shm", "$path-wal").forEach { candidate ->
                    if (NSFileManager.defaultManager.fileExistsAtPath(candidate)) {
                        NSFileManager.defaultManager.removeItemAtPath(candidate, error = null)
                    }
                }
            }
        }

    @Test
    fun persistentFactoryCreatesSchemaAtTheProvidedPath() =
        runTest {
            val path = "${NSTemporaryDirectory()}carapp-e0-07-${Random.nextLong()}.db"

            try {
                val database = createPersistentDatabaseFactory(path).create()

                assertEquals(emptyList(), database.databaseQueries.selectAllVehicles().awaitAsList())
            } finally {
                listOf(path, "$path-shm", "$path-wal").forEach { candidate ->
                    if (NSFileManager.defaultManager.fileExistsAtPath(candidate)) {
                        NSFileManager.defaultManager.removeItemAtPath(candidate, error = null)
                    }
                }
            }
        }
}
