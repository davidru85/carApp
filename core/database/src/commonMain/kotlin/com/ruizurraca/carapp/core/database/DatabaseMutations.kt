package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull

/** The transaction boundary for synchronized entity writes selected by `D-38`. */
class DatabaseMutations(
    private val database: AppDatabase,
) {
    private val queries: DatabaseQueries
        get() = database.databaseQueries

    @Suppress("LongParameterList")
    suspend fun insertFuelEntry(
        id: String,
        ownerId: String,
        vehicleId: String,
        date: Long,
        odometerKm: Long,
        litersScaled: Long,
        pricePerLiterScaled: Long,
        totalCostMinor: Long,
        currency: String,
        isFullTank: Long,
        hasMissedEntries: Long,
        notes: String?,
        createdAt: Long,
        updatedAt: Long,
        serverUpdatedAt: Long?,
        deletedAt: Long?,
        syncState: String,
        localRevision: Long,
        localMutationSeq: Long,
        schemaVersion: Long,
    ) {
        database.transaction {
            queries.insertFuelEntryRow(
                id = id,
                ownerId = ownerId,
                vehicleId = vehicleId,
                date = date,
                odometerKm = odometerKm,
                litersScaled = litersScaled,
                pricePerLiterScaled = pricePerLiterScaled,
                totalCostMinor = totalCostMinor,
                currency = currency,
                isFullTank = isFullTank,
                hasMissedEntries = hasMissedEntries,
                odometerInconsistent = 0,
                notes = notes,
                createdAt = createdAt,
                updatedAt = updatedAt,
                serverUpdatedAt = serverUpdatedAt,
                deleted = if (deletedAt == null) 0 else 1,
                deletedAt = deletedAt,
                syncState = syncState,
                localRevision = localRevision,
                localMutationSeq = localMutationSeq,
                schemaVersion = schemaVersion,
            )

            if (deletedAt == null) {
                val successor =
                    queries
                        .selectNextActiveFuelEntry(vehicleId, date, createdAt, id)
                        .awaitAsOneOrNull()
                queries.recomputeFuelEntryOdometerInconsistent(id)
                successor?.let { queries.recomputeFuelEntryOdometerInconsistent(it.id) }
            }
            queries.recomputeVehicleCurrentOdometer(vehicleId)
        }
    }
}
