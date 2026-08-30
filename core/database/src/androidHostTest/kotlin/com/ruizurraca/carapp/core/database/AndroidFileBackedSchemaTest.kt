package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun persistentFactoryCreatesSchemaAtTheProvidedPath() =
        runTest {
            val directory = Files.createTempDirectory("carapp-e0-07-").toFile()
            val databaseHandle =
                createPersistentDatabaseFactory(
                    directory.resolve("carapp.db").absolutePath,
                ).create()

            try {
                assertEquals(
                    emptyList(),
                    databaseHandle.database.databaseQueries.selectAllVehicles().awaitAsList(),
                )
            } finally {
                databaseHandle.close()
                directory.deleteRecursively()
            }
        }
}
