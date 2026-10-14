package com.ruizurraca.carapp.core.sync

import com.ruizurraca.carapp.core.common.EntityType
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityTypeCollectionTest {
    @Test
    fun entityTypesUseTheClosedFirestoreCollectionNames() {
        assertEquals("vehicles", EntityType.VEHICLE.collection)
        assertEquals("fuelEntries", EntityType.FUEL_ENTRY.collection)
    }
}
