package com.ruizurraca.carapp.core.sync

import kotlinx.serialization.json.JsonElement

/** `D-208`: whether this raw JSON carries the unsupported-provider-value marker. Not implemented yet. */
internal fun JsonElement.containsUnsupportedProviderValue(): Boolean = false
