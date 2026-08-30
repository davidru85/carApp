package com.ruizurraca.carapp.core.testing

import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle
import com.ruizurraca.carapp.core.database.createStagedDatabaseFactory

actual class InMemoryDatabaseFactory actual constructor() : DatabaseFactory {
    private val delegate = createStagedDatabaseFactory()
    private val handles = TrackedDatabaseHandles()

    actual override fun create(): DatabaseHandle = handles.track(delegate.create())

    actual fun close() = handles.close()
}
