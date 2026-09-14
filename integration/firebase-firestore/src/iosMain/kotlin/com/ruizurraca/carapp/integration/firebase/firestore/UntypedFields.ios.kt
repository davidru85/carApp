@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ruizurraca.carapp.integration.firebase.firestore

import cocoapods.FirebaseCore.FIRTimestamp
import dev.gitlive.firebase.firestore.DocumentSnapshot
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
private const val NANOS_PER_MILLISECOND = 1_000_000L

internal actual fun DocumentSnapshot.untypedFields(): Map<String, Any?> =
    (ios.data() as? Map<*, *>)?.entries?.associate { (key, value) ->
        (key as? String ?: key?.toString().orEmpty()) to value.toJsonFriendly()
    } ?: emptyMap()

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
