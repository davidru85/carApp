package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull

/** The transaction boundary for synchronized entity writes selected by `D-38`. */
class DatabaseMutations(
    private val database: AppDatabase,
) {
    private val queries: DatabaseQueries
        get() = database.databaseQueries

    @Suppress("LongParameterList")
    suspend fun insertVehicle(
        id: String,
        ownerId: String,
        name: String,
        nameFold: String,
        initialOdometerKm: Long,
        brand: String?,
        model: String?,
        fuelType: String,
        createdAt: Long,
        updatedAt: Long,
        schemaVersion: Long,
        outboxPayload: String,
    ) {
        database.transaction {
            val localMutationSeq = queries.nextLocalMutationSequence().awaitAsOne()
            queries.insertVehicleRow(
                id = id,
                ownerId = ownerId,
                name = name,
                nameFold = nameFold,
                initialOdometerKm = initialOdometerKm,
                currentOdometerKm = initialOdometerKm,
                brand = brand,
                model = model,
                fuelType = fuelType,
                createdAt = createdAt,
                updatedAt = updatedAt,
                serverUpdatedAt = null,
                deleted = 0,
                deletedAt = null,
                syncState = "PENDING",
                localRevision = 1,
                localMutationSeq = localMutationSeq,
                schemaVersion = schemaVersion,
            )
            queries.coalesceOutbox(
                entityType = "VEHICLE",
                entityId = id,
                payload = outboxPayload,
                localRevision = 1,
            )
        }
    }

    suspend fun confirmVehiclePush(
        entityId: String,
        pushedLocalRevision: Long,
        serverUpdatedAt: Long,
    ) {
        database.transaction {
            queries.confirmVehiclePush(
                serverUpdatedAt = serverUpdatedAt,
                pushedLocalRevision = pushedLocalRevision,
                entityId = entityId,
            )
            queries.deleteConfirmedVehicleOutbox(
                entityId = entityId,
                pushedLocalRevision = pushedLocalRevision,
            )
        }
    }

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

    suspend fun tombstoneFuelEntriesForVehicle(
        vehicleId: String,
        deletedAt: Long,
        updatedAt: Long,
        syncState: String,
    ) {
        database.transaction {
            val entries = queries.selectActiveFuelEntriesByVehicle(vehicleId).awaitAsList()
            val tombstonedIds = entries.mapTo(mutableSetOf()) { it.id }
            val recomputeIds =
                entries
                    .mapNotNull { it.activeSuccessor()?.id }
                    .filterNotTo(mutableSetOf()) { it in tombstonedIds }

            for (entry in entries) {
                queries.tombstoneFuelEntryRow(
                    updatedAt = updatedAt,
                    deletedAt = deletedAt,
                    syncState = syncState,
                    localRevision = entry.localRevision + 1,
                    localMutationSeq = queries.nextLocalMutationSequence().awaitAsOne(),
                    id = entry.id,
                )
            }

            for (recomputeId in recomputeIds) {
                queries.recomputeFuelEntryOdometerInconsistent(recomputeId)
            }
            queries.recomputeVehicleCurrentOdometer(vehicleId)
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
