package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsOne
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

    @Suppress("LongParameterList")
    suspend fun updateFuelEntry(
        existingId: String,
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
            val before = queries.selectFuelEntryById(existingId).awaitAsOne()
            val preUpdateSuccessor = before.activeSuccessor()

            queries.updateFuelEntryRow(
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
                odometerInconsistent = before.odometerInconsistent,
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
                existingId = existingId,
            )

            val after = queries.selectFuelEntryById(id).awaitAsOne()
            if (before.recomputeKeysDifferFrom(after) && after.deleted == 0L) {
                val postUpdateSuccessor = after.activeSuccessor()
                val recomputeIds =
                    buildSet {
                        add(after.id)
                        preUpdateSuccessor?.let { add(it.id) }
                        postUpdateSuccessor?.let { add(it.id) }
                    }
                for (recomputeId in recomputeIds) {
                    queries.recomputeFuelEntryOdometerInconsistent(recomputeId)
                }
            }

            val vehicleIds =
                buildSet {
                    add(before.vehicleId)
                    add(after.vehicleId)
                }
            for (affectedVehicleId in vehicleIds) {
                queries.recomputeVehicleCurrentOdometer(affectedVehicleId)
            }
        }
    }

    suspend fun tombstoneFuelEntry(
        id: String,
        deletedAt: Long,
        updatedAt: Long,
        syncState: String,
        localRevision: Long,
        localMutationSeq: Long,
    ) {
        database.transaction {
            val before = queries.selectFuelEntryById(id).awaitAsOne()
            val successor = before.activeSuccessor()

            queries.tombstoneFuelEntryRow(
                updatedAt = updatedAt,
                deletedAt = deletedAt,
                syncState = syncState,
                localRevision = localRevision,
                localMutationSeq = localMutationSeq,
                id = id,
            )

            successor?.let { queries.recomputeFuelEntryOdometerInconsistent(it.id) }
            queries.recomputeVehicleCurrentOdometer(before.vehicleId)
        }
    }

    private suspend fun Fuel_entry.activeSuccessor(): Fuel_entry? =
        if (deleted == 0L) {
            queries.selectNextActiveFuelEntry(vehicleId, date, createdAt, id).awaitAsOneOrNull()
        } else {
            null
        }

    private fun Fuel_entry.recomputeKeysDifferFrom(other: Fuel_entry): Boolean =
        id != other.id ||
            vehicleId != other.vehicleId ||
            date != other.date ||
            createdAt != other.createdAt ||
            odometerKm != other.odometerKm
}
