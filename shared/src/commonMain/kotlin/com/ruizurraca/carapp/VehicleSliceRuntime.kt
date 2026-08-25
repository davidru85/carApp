package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.CLIENT_MAX_SCHEMA_VERSION
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseMutations

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
        mutations.insertVehicle(
            id = dependencies.uuidGenerator.newId(),
            ownerId = dependencies.ownerContext.current.value,
            name = name,
            nameFold = name.lowercase(),
            initialOdometerKm = form.initialOdometerKm,
            brand = form.brand,
            model = form.model,
            fuelType = form.fuelType.name,
            createdAt = now,
            updatedAt = now,
            schemaVersion = CLIENT_MAX_SCHEMA_VERSION.toLong(),
        )
    }
}

private fun canonicalWalkingSkeletonName(value: String): String = value.trim().split(Regex("\\s+")).joinToString(" ")
