package com.ruizurraca.carapp.core.database

import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test

class AndroidFileBackedSchemaTest {
    @Test
    fun schemaReopensWithExistingData() =
        runTest {
            val directory = Files.createTempDirectory("carapp-e1-01-").toFile()

            try {
                assertDatabasePersistsAcrossReopen(
                    AndroidxSqliteDatabaseType.File(directory.resolve("carapp.db").absolutePath),
                )
            } finally {
                directory.deleteRecursively()
            }
        }
}
