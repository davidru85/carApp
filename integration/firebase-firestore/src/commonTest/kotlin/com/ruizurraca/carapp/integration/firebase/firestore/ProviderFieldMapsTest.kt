package com.ruizurraca.carapp.integration.firebase.firestore

import com.ruizurraca.carapp.core.sync.UNSUPPORTED_PROVIDER_VALUE_KEY
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderFieldMapsTest {
    @Test
    fun aMapWithANumericKeyBecomesOneMarkerBeforeConvertingValues() {
        var convertedValues = 0
        val fields = linkedMapOf<Any?, Any?>("kept" to "value", 1L to "unsupported")

        val result =
            fields.toProviderFieldMap {
                convertedValues += 1
                it
            }

        assertEquals(marker(), result)
        assertEquals(0, convertedValues)
    }

    @Test
    fun aMapWithANullKeyBecomesOneMarker() {
        val fields = mapOf<Any?, Any?>(null to "value")

        assertEquals(marker(), fields.toProviderFieldMap { it })
    }

    @Test
    fun keysThatWouldCollideAfterCoercionBecomeOneMarker() {
        val fields = linkedMapOf<Any?, Any?>(1L to "numeric", "1" to "text")

        assertEquals(marker(), fields.toProviderFieldMap { it })
    }

    @Test
    fun anUnsupportedKeyIsNeverConvertedToText() {
        val fields = mapOf<Any?, Any?>(KeyWhoseToStringThrows() to "value")

        assertEquals(marker(), fields.toProviderFieldMap { it })
    }

    @Test
    fun aStringKeyedMapPreservesNamesAndConvertsValues() {
        val fields = linkedMapOf<Any?, Any?>("first" to 1L, "second" to 2L)

        val result = fields.toProviderFieldMap { (it as Long) + 10L }

        assertEquals(mapOf("first" to 11L, "second" to 12L), result)
    }

    @Test
    fun anEmptyMapRemainsEmpty() {
        assertEquals(emptyMap(), emptyMap<Any?, Any?>().toProviderFieldMap { it })
    }

    private fun marker(): Map<String, Any?> = mapOf(UNSUPPORTED_PROVIDER_VALUE_KEY to NON_STRING_KEY_MAP)

    private class KeyWhoseToStringThrows {
        override fun toString(): String = error("An unsupported key must not be rendered")
    }
}
