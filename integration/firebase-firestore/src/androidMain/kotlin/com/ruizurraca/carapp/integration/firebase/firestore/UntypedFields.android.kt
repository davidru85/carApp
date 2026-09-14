package com.ruizurraca.carapp.integration.firebase.firestore

import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.android

/**
 * The Android provider's untyped field map (`D-170`). GitLive's `DocumentSnapshot.android` exposes
 * the native `com.google.firebase.firestore.DocumentSnapshot`, whose `data` is the product-neutral
 * field map. Timestamps are normalized to epoch milliseconds and no product field is decoded or
 * asserted, so `§9.5` classification stays in `:core:sync`.
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
            entries.associate { (key, value) ->
                requireNotNull(key as? String) { "Non-string map key in remote document" } to value.toJsonFriendly()
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
            throw IllegalArgumentException("Unsupported remote field type: ${this::class.simpleName}")
        }
    }
