package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.ruizurraca.carapp.core.common.CLIENT_MAX_SCHEMA_VERSION
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseMutations
import com.ruizurraca.carapp.core.database.VehicleDatabaseAccess
import com.ruizurraca.carapp.core.database.outboxPayloadByEntity
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemoteSnapshot
import com.ruizurraca.carapp.feature.vehicle.data.SqlDelightVehicleRepository
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleCommand
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * E0-07's internal remote adapter for the minimal Vehicle slice (`D-55`). Local operations delegate
 * to E1-03's complete repository; E3-02 and E3-03 replace the staged remote orchestration.
 */
internal class VehicleSliceRuntime(
    private val dependencies: AppGraphDependencies,
    private val database: AppDatabase,
) {
    private val mutations = DatabaseMutations(database)
    val repository =
        SqlDelightVehicleRepository(
            databaseAccess = VehicleDatabaseAccess(database),
            ownerContext = dependencies.ownerContext,
            clock = dependencies.clock,
            uuidGenerator = dependencies.uuidGenerator,
        )

    suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> {
        val result = repository.createVehicle(command)
        if (result is Outcome.Ok) pushVehicleIfEligible(result.value)
        return result
    }

    suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> {
        val result = repository.updateVehicle(command)
        if (result is Outcome.Ok) pushVehicleIfEligible(command.id)
        return result
    }

    private suspend fun pushVehicleIfEligible(entityId: EntityId) {
        val id = entityId.value
        val owner = dependencies.ownerContext.current
        if (owner != LOCAL_OWNER && dependencies.connectivityObserver.isOnline.value) {
            val vehicle = database.databaseQueries.selectVehicleById(id).awaitAsOneOrNull() ?: return
            val snapshot =
                database.databaseQueries
                    .outboxPayloadByEntity(entityType = "VEHICLE", entityId = id)
                    ?: return
            val result =
                dependencies.remoteSyncSource.pushSnapshot(
                    ownerId = owner,
                    snapshot =
                        EntitySnapshot(
                            entityType = EntityType.VEHICLE,
                            entityId = EntityId(id),
                            schemaVersion = CLIENT_MAX_SCHEMA_VERSION,
                            json = snapshot,
                        ),
                )
            if (result is Outcome.Ok) {
                mutations.confirmVehiclePush(
                    entityId = id,
                    pushedLocalRevision = vehicle.localRevision,
                    serverUpdatedAt = result.value.serverUpdatedAt.toEpochMilliseconds(),
                )
            }
        }
    }

    suspend fun refresh(): Outcome<Unit, AppError> {
        val owner = dependencies.ownerContext.current
        if (owner == LOCAL_OWNER || !dependencies.connectivityObserver.isOnline.value) return Outcome.Ok(Unit)

        val result =
            dependencies.remoteSyncSource.pullChanges(
                ownerId = owner,
                entityType = EntityType.VEHICLE,
                cursor = RemoteCursor.INITIAL,
                limit = REMOTE_PAGE_LIMIT,
            )
        val page =
            when (result) {
                is Outcome.Err -> return result
                is Outcome.Ok -> result.value
            }
        page.items.forEach { snapshot -> applyRemoteVehicle(owner.value, snapshot) }
        return Outcome.Ok(Unit)
    }

    private suspend fun applyRemoteVehicle(
        ownerId: String,
        snapshot: RemoteSnapshot,
    ) {
        check(snapshot.entityType == EntityType.VEHICLE)
        val payload = Json.decodeFromString<RemoteVehiclePayload>(snapshot.json)
        check(payload.id == snapshot.entityId.value)
        check(payload.ownerId == ownerId)
        check(payload.schemaVersion == snapshot.schemaVersion)
        check(payload.deleted == snapshot.deleted)
        check(payload.deleted == (payload.deletedAt != null))
        mutations.applyRemoteVehicle(
            id = payload.id,
            ownerId = payload.ownerId,
            name = payload.name,
            nameFold = payload.name.lowercase(),
            initialOdometerKm = payload.initialOdometerKm,
            brand = payload.brand,
            model = payload.model,
            fuelType = payload.fuelType,
            createdAt = payload.createdAt,
            updatedAt = payload.updatedAt,
            serverUpdatedAt = snapshot.serverUpdatedAt.toEpochMilliseconds(),
            deletedAt = payload.deletedAt,
            schemaVersion = payload.schemaVersion.toLong(),
        )
    }

    private companion object {
        const val REMOTE_PAGE_LIMIT = 50
    }
}

@Serializable
private data class RemoteVehiclePayload(
    val id: String,
    val ownerId: String,
    val name: String,
    val initialOdometerKm: Long,
    val brand: String?,
    val model: String?,
    val fuelType: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    val deletedAt: Long?,
    val schemaVersion: Int,
)
