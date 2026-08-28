package com.ruizurraca.carapp.feature.vehicle.domain

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelType
import kotlin.test.fail

internal fun createCommand(
    name: String = "Family car",
    initialOdometerKm: Long = 10_000,
    brand: String? = "Toyota",
    model: String? = "Corolla",
    fuelType: FuelType = FuelType.GASOLINE,
): CreateVehicleCommand =
    CreateVehicleCommand(
        name = name,
        initialOdometerKm = initialOdometerKm,
        brand = brand,
        model = model,
        fuelType = fuelType,
        confirmations = emptySet(),
    )

internal fun updateCommand(
    id: EntityId = EntityId("00000000-0000-4000-8000-000000000001"),
    name: String = "Family car",
    initialOdometerKm: Long? = null,
    brand: String? = "Toyota",
    model: String? = "Corolla",
    fuelType: FuelType = FuelType.GASOLINE,
): UpdateVehicleCommand =
    UpdateVehicleCommand(
        id = id,
        name = name,
        initialOdometerKm = initialOdometerKm,
        brand = brand,
        model = model,
        fuelType = fuelType,
        confirmations = emptySet(),
    )

internal fun candidate(
    id: String = "00000000-0000-4000-8000-000000000002",
    name: String,
): VehicleNameCandidate = VehicleNameCandidate(EntityId(id), name)

internal fun errorFrom(result: Outcome<*, AppError>): AppError =
    when (result) {
        is Outcome.Err -> result.error
        is Outcome.Ok -> fail("Expected an error but received ${result.value}")
    }
