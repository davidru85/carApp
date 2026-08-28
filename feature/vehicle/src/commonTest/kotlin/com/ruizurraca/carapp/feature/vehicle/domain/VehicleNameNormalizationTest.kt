package com.ruizurraca.carapp.feature.vehicle.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class VehicleNameNormalizationTest {
    @Test
    fun canonicalNameTrimsAndCollapsesUnicodeWhitespace() {
        assertEquals("Family car", canonicalVehicleName("\u2003Family\t\ncar\u00A0"))
    }
}
