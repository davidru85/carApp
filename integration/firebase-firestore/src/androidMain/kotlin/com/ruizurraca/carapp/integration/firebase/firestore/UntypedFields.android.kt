package com.ruizurraca.carapp.integration.firebase.firestore

import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.android

/**
 * The Android provider's untyped field map (`D-170`). GitLive's `DocumentSnapshot.android` exposes
 * the native `com.google.firebase.firestore.DocumentSnapshot`, whose `data` is the product-neutral
 * field map. Timestamps are normalized to epoch milliseconds and no product field is decoded or
 * asserted, so `§9.5` classification stays in `:core:sync`. A value with no JSON form (`GeoPoint`,
 * `Blob`, `DocumentReference`, a vector) or a map with a non-string key becomes the `D-208` marker in
 * its own position instead of failing the page.
 */
private const val MILLIS_PER_SECOND = 1_000L
private const val MICROS_PER_SECOND = 1_000_000L
private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val NANOS_PER_MICROSECOND = 1_000L
private const val UPDATED_AT_FIELD = "updatedAt"

internal actual fun DocumentSnapshot.untypedFields(): Map<String, Any?> =
    android.data?.mapValues { (_, value) -> value.toJsonFriendly() } ?: emptyMap()

/**
 * The full-precision `updatedAt` ordering timestamp as epoch microseconds (`D-174`). GitLive's
 * typed read keeps the native `Timestamp` seconds and nanoseconds, so no provider precision is lost
 * here. A missing or mistyped field returns `null`, which the caller turns into a closed page
 * failure.
 */
internal actual fun DocumentSnapshot.orderingUpdatedAtMicros(): Long? =
    runCatching { get<Timestamp>(UPDATED_AT_FIELD) }
        .getOrNull()
        ?.let { it.seconds * MICROS_PER_SECOND + it.nanoseconds / NANOS_PER_MICROSECOND }

private fun Any?.toJsonFriendly(): Any? =
    when (this) {
        null -> {
            null
        }

        is com.google.firebase.Timestamp -> {
            seconds * MILLIS_PER_SECOND + nanoseconds / NANOS_PER_MILLISECOND
        }

        is java.util.Date -> {
            time
        }

        is Map<*, *> -> {
            if (keys.all { it is String }) {
                entries.associate { (key, value) -> key as String to value.toJsonFriendly() }
            } else {
                unsupportedProviderValue(NON_STRING_KEY_MAP)
            }
        }

        is List<*> -> {
            map { it.toJsonFriendly() }
        }

        is String, is Boolean, is Long, is Int, is Double -> {
            this
        }

        is Number -> {
            toLong()
        }

        else -> {
            unsupportedProviderValue(this::class.simpleName ?: UNKNOWN_PROVIDER_TYPE)
        }
    }
