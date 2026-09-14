package com.ruizurraca.carapp.integration.firebase.firestore

import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.android

/**
 * The Android provider's untyped field map (`D-170`). GitLive's `DocumentSnapshot.android` exposes
 * the native `com.google.firebase.firestore.DocumentSnapshot`, whose `data` is the product-neutral
 * field map. Timestamps are normalized to epoch milliseconds and no product field is decoded or
 * asserted, so `§9.5` classification stays in `:core:sync`.
 */
private const val MILLIS_PER_SECOND = 1_000L
private const val NANOS_PER_MILLISECOND = 1_000_000L

internal actual fun DocumentSnapshot.untypedFields(): Map<String, Any?> =
    android.data?.mapValues { (_, value) -> value.toJsonFriendly() } ?: emptyMap()

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
