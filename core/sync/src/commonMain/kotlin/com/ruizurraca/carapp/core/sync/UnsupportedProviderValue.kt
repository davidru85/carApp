package com.ruizurraca.carapp.core.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Whether this raw JSON carries the marker the pull boundary writes in place of a provider value with
 * no JSON form (`docs/CONTRACTS.md §9.5`, `D-208`), at any depth. Only the reserved key counts; the same
 * text as a value is ordinary data. `:core:sync` quarantines a marked document as `MalformedPayload`
 * explicitly, so the guarantee does not depend on today's schema being made of primitives.
 */
internal fun JsonElement.containsUnsupportedProviderValue(): Boolean =
    when (this) {
        is JsonObject -> {
            containsKey(UNSUPPORTED_PROVIDER_VALUE_KEY) ||
                values.any { it.containsUnsupportedProviderValue() }
        }

        is JsonArray -> {
            any { it.containsUnsupportedProviderValue() }
        }

        else -> {
            false
        }
    }
