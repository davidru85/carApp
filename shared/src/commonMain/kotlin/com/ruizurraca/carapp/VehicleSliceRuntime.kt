package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.CLIENT_MAX_SCHEMA_VERSION
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    private val repository =
        SqlDelightVehicleRepository(
            databaseAccess = VehicleDatabaseAccess(database),
            ownerContext = dependencies.ownerContext,
            clock = dependencies.clock,
            uuidGenerator = dependencies.uuidGenerator,
        )

    suspend fun save(form: VehicleFormUiState) {
        check(form.vehicleId == null) { "E0-07 only creates the minimal Vehicle slice" }
        val createResult =
            repository.createVehicle(
                CreateVehicleCommand(
                    name = form.name,
                    initialOdometerKm = form.initialOdometerKm,
                    brand = form.brand,
                    model = form.model,
                    fuelType = form.fuelType,
                    confirmations = emptySet(),
                ),
            )
        if (createResult !is Outcome.Ok) return

        val id = createResult.value.value
        val owner = dependencies.ownerContext.current
        if (owner != LOCAL_OWNER && dependencies.connectivityObserver.isOnline.value) {
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
                    pushedLocalRevision = 1,
                    serverUpdatedAt = result.value.serverUpdatedAt.toEpochMilliseconds(),
                )
            }
        }
    }

    fun observeVehicles(): Flow<List<VehicleListItemUi>> =
        repository.observeVehicles(includeDeleted = false).map { result ->
            when (result) {
                is Outcome.Err -> {
                    emptyList()
                }

                is Outcome.Ok -> {
                    result.value.map { vehicle ->
                        VehicleListItemUi(
                            id = vehicle.id.value,
                            name = vehicle.name,
                            currentOdometerKm = vehicle.currentOdometerKm,
                            fuelType = vehicle.fuelType,
                            deleted = vehicle.deletedAt != null,
                        )
                    }
                }
            }
        }

    suspend fun refresh() {
        val owner = dependencies.ownerContext.current
        if (owner == LOCAL_OWNER || !dependencies.connectivityObserver.isOnline.value) return

        val result =
            dependencies.remoteSyncSource.pullChanges(
                ownerId = owner,
                entityType = EntityType.VEHICLE,
                cursor = RemoteCursor.INITIAL,
                limit = REMOTE_PAGE_LIMIT,
            )
        if (result !is Outcome.Ok) return

        result.value.items.forEach { snapshot -> applyRemoteVehicle(owner.value, snapshot) }
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
