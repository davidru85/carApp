package com.ruizurraca.carapp.integration.firebase.firestore

/** Converts provider map values before raw JSON serialization. */
internal fun Map<*, *>.toProviderFieldMap(convertValue: (Any?) -> Any?): Map<String, Any?> =
    entries.associate { (key, value) ->
        (key as? String ?: key?.toString().orEmpty()) to convertValue(value)
    }
