package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.CLIENT_MAX_SCHEMA_VERSION
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseMutations
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
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
    database: AppDatabase,
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
            outboxPayload = snapshot,
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
}

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
