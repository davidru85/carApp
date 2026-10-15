package com.ruizurraca.carapp.core.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `E3-20` / `D-208`: `:core:sync` recognises the marker the pull boundary writes in place of a provider
 * value with no JSON form, wherever it sits, so a marked document is quarantined whatever shape a future
 * schema gives its fields.
 */
class UnsupportedProviderValueTest {
    @Test
    fun aMarkerAtTheTopLevelIsDetected() {
        val document = buildJsonObject { put("name", marker()) }

        assertTrue(document.containsUnsupportedProviderValue())
    }

    @Test
    fun aMarkerNestedInAnObjectOrAnArrayIsDetected() {
        val document =
            buildJsonObject {
                put(
                    "brand",
                    buildJsonObject {
                        put(
                            "tags",
                            buildJsonArray {
                                add(JsonPrimitive("a"))
                                add(marker())
                            },
                        )
                    },
                )
            }

        assertTrue(document.containsUnsupportedProviderValue())
    }

    @Test
    fun aDocumentWithoutTheMarkerIsNotDetected() {
        // The reserved key is a marker only as a key; the same text as a value is ordinary data.
        val document =
            buildJsonObject {
                put("name", UNSUPPORTED_PROVIDER_VALUE_KEY)
                put("brand", buildJsonObject { put("model", "x") })
                put("tags", buildJsonArray { add(JsonPrimitive(1)) })
            }

        assertFalse(document.containsUnsupportedProviderValue())
    }

    private fun marker(): JsonObject = buildJsonObject { put(UNSUPPORTED_PROVIDER_VALUE_KEY, "GeoPoint") }
}
