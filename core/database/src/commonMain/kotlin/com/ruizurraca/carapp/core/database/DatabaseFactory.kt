package com.ruizurraca.carapp.core.database

/** Creates the local database owned by this module (`docs/CONTRACTS.md §20.3.2`). */
interface DatabaseFactory {
    fun create(): AppDatabase
}
