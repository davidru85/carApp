package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.CLIENT_MAX_SCHEMA_VERSION
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseMutations
import com.ruizurraca.carapp.core.database.observeVehicles
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemoteSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * E0-07's removable internal adapter for the minimal Vehicle slice (`D-55`). E1-02 and E1-03
 * replace this orchestration with the complete validated use case and repository in place.
 */
internal class VehicleSliceRuntime(
    private val dependencies: AppGraphDependencies,
    private val database: AppDatabase,
) {
    private val mutations = DatabaseMutations(database)

    suspend fun save(form: VehicleFormUiState) {
        check(form.vehicleId == null) { "E0-07 only creates the minimal Vehicle slice" }
        val name = canonicalWalkingSkeletonName(form.name)
        val now = dependencies.clock.now().toEpochMilliseconds()
        val id = dependencies.uuidGenerator.newId()
        val schemaVersion = CLIENT_MAX_SCHEMA_VERSION.toLong()
        val owner = dependencies.ownerContext.current
        val snapshot =
            buildVehicleSnapshot(
                id = id,
                ownerId = owner.value,
                name = name,
                form = form,
                now = now,
                schemaVersion = schemaVersion,
            )
        mutations.insertVehicle(
            id = id,
            ownerId = owner.value,
            name = name,
            nameFold = name.lowercase(),
            initialOdometerKm = form.initialOdometerKm,
            brand = form.brand,
            model = form.model,
            fuelType = form.fuelType.name,
            createdAt = now,
            updatedAt = now,
            schemaVersion = schemaVersion,
            outboxPayload = snapshot.takeUnless { owner == LOCAL_OWNER },
        )
        if (owner != LOCAL_OWNER && dependencies.connectivityObserver.isOnline.value) {
            val result =
                dependencies.remoteSyncSource.pushSnapshot(
                    ownerId = owner,
                    snapshot =
                        EntitySnapshot(
                            entityType = EntityType.VEHICLE,
                            entityId = EntityId(id),
                            schemaVersion = schemaVersion.toInt(),
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
        database.databaseQueries.observeVehicles().map { rows ->
            rows.map { row ->
                VehicleListItemUi(
                    id = row.id,
                    name = row.name,
                    currentOdometerKm = row.currentOdometerKm,
                    fuelType = FuelType.valueOf(row.fuelType),
                    deleted = row.deleted != 0L,
                )
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

private fun canonicalWalkingSkeletonName(value: String): String = value.trim().split(Regex("\\s+")).joinToString(" ")

@Suppress("LongParameterList")
private fun buildVehicleSnapshot(
    id: String,
    ownerId: String,
    name: String,
    form: VehicleFormUiState,
    now: Long,
    schemaVersion: Long,
): String =
    buildJsonObject {
        put("id", id)
        put("ownerId", ownerId)
        put("name", name)
        put("initialOdometerKm", form.initialOdometerKm)
        put("brand", form.brand?.let(::JsonPrimitive) ?: JsonNull)
        put("model", form.model?.let(::JsonPrimitive) ?: JsonNull)
        put("fuelType", form.fuelType.name)
        put("createdAt", now)
        put("updatedAt", now)
        put("deleted", false)
        put("deletedAt", JsonNull)
        put("schemaVersion", schemaVersion)
    }.toString()
