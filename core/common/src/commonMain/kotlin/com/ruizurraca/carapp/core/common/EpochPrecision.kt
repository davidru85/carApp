@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.core.common

import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

/*
 * Epoch-microsecond conversion for the pull ordering cursor (`D-174`).
 *
 * Firestore server timestamps carry microsecond precision. The database persists the cross-cycle
 * anchor as epoch milliseconds, while the in-cycle ordering cursor keeps the provider value as epoch
 * microseconds so a later-page `startAfter` boundary remains exclusive. These helpers are the
 * canonical conversion point for that in-cycle microsecond representation.
 *
 * Only the ordering cursor uses microseconds. The whole-document LWW comparison of `§9.6` and the
 * entity `serverUpdatedAt` column stay epoch milliseconds, matching the payload `updatedAt` field.
 */

private const val MICROS_PER_SECOND = 1_000_000L
private const val NANOS_PER_MICROSECOND = 1_000L

/**
 * This instant as epoch microseconds, truncating the sub-microsecond remainder.
 *
 * The current tree calls this direction directly only from tests. It deliberately remains public
 * beside [instantFromEpochMicroseconds] so integration modules can construct and inspect the
 * provider-precision cursor without duplicating epoch arithmetic or exposing platform timestamp
 * types.
 */
@HiddenFromObjC
fun Instant.toEpochMicroseconds(): Long = epochSeconds * MICROS_PER_SECOND + nanosecondsOfSecond / NANOS_PER_MICROSECOND

/** The instant `microseconds` after the epoch. */
@HiddenFromObjC
fun instantFromEpochMicroseconds(microseconds: Long): Instant =
    Instant.fromEpochSeconds(
        microseconds / MICROS_PER_SECOND,
        (microseconds % MICROS_PER_SECOND) * NANOS_PER_MICROSECOND,
    )
