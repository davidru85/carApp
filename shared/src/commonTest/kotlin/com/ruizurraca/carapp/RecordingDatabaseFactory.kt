package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.database.DatabaseFactory
import com.ruizurraca.carapp.core.database.DatabaseHandle

internal class RecordingDatabaseFactory(
    private val delegate: DatabaseFactory,
    private val onClose: () -> Unit = {},
) : DatabaseFactory {
    var closeCalls: Int = 0
        private set

    override fun create(): DatabaseHandle {
        val handle = delegate.create()
        return object : DatabaseHandle {
            override val database = handle.database

            override fun close() {
                closeCalls += 1
                onClose()
                handle.close()
            }
        }
    }
}
