package com.ruizurraca.carapp

import kotlin.test.Test
import kotlin.test.assertEquals

class VehicleFormStateHolderTest {
    @Test
    fun nameIntentUpdatesFormState() {
        val holder = VehicleFormStateHolder(vehicleId = null)

        holder.setName("Roadster")

        assertEquals("Roadster", holder.state.value.name)
    }
}
