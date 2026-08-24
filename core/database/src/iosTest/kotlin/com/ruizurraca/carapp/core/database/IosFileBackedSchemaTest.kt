package com.ruizurraca.carapp.core.database

import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test

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
}
