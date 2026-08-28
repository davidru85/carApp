package com.ruizurraca.carapp.feature.vehicle.domain

import com.ruizurraca.carapp.core.model.FuelType
import kotlin.test.Test
import kotlin.test.assertEquals

class VehicleCommandsTest {
    @Test
    fun createCommandDefaultsFuelTypeToGasoline() {
        val command =
            CreateVehicleCommand(
                name = "Family car",
                initialOdometerKm = 0,
                brand = null,
                model = null,
                confirmations = emptySet(),
            )

        assertEquals(FuelType.GASOLINE, command.fuelType)
    }

    @Test
    fun fuelTypeContainsExactlyTheMvpValues() {
        assertEquals(
            listOf("GASOLINE", "DIESEL", "LPG", "CNG", "OTHER"),
            FuelType.entries.map(FuelType::name),
        )
    }
}
