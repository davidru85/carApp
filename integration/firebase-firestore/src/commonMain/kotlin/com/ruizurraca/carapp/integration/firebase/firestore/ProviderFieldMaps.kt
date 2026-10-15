package com.ruizurraca.carapp.integration.firebase.firestore

/**
 * Converts a provider map without inventing JSON field names (`D-208`, `CONTRACTS §9.5`).
 * A map with any non-string key is represented by one marker before any value is converted.
 */
internal fun Map<*, *>.toProviderFieldMap(convertValue: (Any?) -> Any?): Map<String, Any?> {
    if (keys.any { it !is String }) return unsupportedProviderValue(NON_STRING_KEY_MAP)
    return entries.associate { (key, value) -> key as String to convertValue(value) }
}
