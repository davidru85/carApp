@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ruizurraca.carapp.integration.firebase.firestore

import cocoapods.FirebaseCore.FIRTimestamp
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.ios
import kotlinx.cinterop.get
import platform.Foundation.NSNull
import platform.Foundation.NSNumber
import platform.Foundation.NSString

/**
 * The iOS provider's untyped field map (`D-170`). GitLive's `DocumentSnapshot.ios` exposes the
 * native `FIRDocumentSnapshot`, whose `data()` bridges to a Kotlin `Map<Any?, Any?>` with provider
 * values still represented natively. Values are normalized to JSON-friendly Kotlin types and
 * timestamps to epoch milliseconds here; no product field is decoded or asserted, so `§9.5`
 * classification stays in `:core:sync`.
 */
private const val MILLIS_PER_SECOND = 1_000L
private const val MICROS_PER_SECOND = 1_000_000L
private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val NANOS_PER_MICROSECOND = 1_000L
private const val UPDATED_AT_FIELD = "updatedAt"

internal actual fun DocumentSnapshot.untypedFields(): Map<String, Any?> =
    (ios.data() as? Map<*, *>)?.entries?.associate { (key, value) ->
        (key as? String ?: key?.toString().orEmpty()) to value.toJsonFriendly()
    } ?: emptyMap()

/**
 * The full-precision `updatedAt` ordering timestamp as epoch microseconds (`D-174`). GitLive's
 * typed read keeps the native `FIRTimestamp` seconds and nanoseconds, so no provider precision is
 * lost here. A missing or mistyped field returns `null`, which the caller turns into a closed page
 * failure.
 */
internal actual fun DocumentSnapshot.orderingUpdatedAtMicros(): Long? =
    runCatching { get<Timestamp>(UPDATED_AT_FIELD) }
        .getOrNull()
        ?.let { it.seconds * MICROS_PER_SECOND + it.nanoseconds / NANOS_PER_MICROSECOND }

private fun Any?.toJsonFriendly(): Any? =
    when (this) {
        null, is NSNull -> {
            null
        }

        is FIRTimestamp -> {
            seconds * MILLIS_PER_SECOND + nanoseconds / NANOS_PER_MILLISECOND
        }

        is NSString -> {
            toString()
        }

        is String -> {
            this
        }

        is NSNumber -> {
            toJsonNumber()
        }

        is Map<*, *> -> {
            entries.associate { (key, value) ->
                (key as? String ?: key?.toString().orEmpty()) to value.toJsonFriendly()
            }
        }

        is List<*> -> {
            map { it.toJsonFriendly() }
        }

        is Boolean, is Long, is Int, is Double -> {
            this
        }

        else -> {
            throw IllegalArgumentException("Unsupported remote field type: ${this::class.simpleName}")
        }
    }

/**
 * Distinguishes an `NSNumber` boolean from its integer and floating-point forms using the Objective-C
 * type encoding, which is the only reliable discriminator once the value has crossed into Native.
 */
private fun NSNumber.toJsonNumber(): Any =
    when (objCType?.get(0)) {
        'c'.code.toByte(), 'B'.code.toByte() -> boolValue
        'f'.code.toByte(), 'd'.code.toByte() -> doubleValue
        else -> longLongValue
    }
