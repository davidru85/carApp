package com.ruizurraca.carapp.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class EntityTypeCollectionTest {
    @Test
    fun entityTypesUseTheClosedFirestoreCollectionNames() {
        assertEquals("vehicles", EntityType.VEHICLE.collection)
        assertEquals("fuelEntries", EntityType.FUEL_ENTRY.collection)
    }
}
