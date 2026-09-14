@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.core.common

import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

/*
 * Epoch-microsecond conversion for the pull ordering cursor (`D-174`).
 *
 * Firestore server timestamps carry microsecond precision. The pull cursor is persisted as an epoch
 * Long, so storing it in milliseconds truncates the sub-millisecond component and makes a later-page
 * `startAfter` boundary non-exclusive. These helpers are the single conversion point between the
 * microsecond Long the database stores and the `Instant` the sync contracts use.
 *
 * Only the ordering cursor uses microseconds. The whole-document LWW comparison of `§9.6` and the
 * entity `serverUpdatedAt` column stay epoch milliseconds, matching the payload `updatedAt` field.
 */

private const val MICROS_PER_SECOND = 1_000_000L
private const val NANOS_PER_MICROSECOND = 1_000L

/** This instant as epoch microseconds, truncating the sub-microsecond remainder. */
@HiddenFromObjC
fun Instant.toEpochMicroseconds(): Long = epochSeconds * MICROS_PER_SECOND + nanosecondsOfSecond / NANOS_PER_MICROSECOND

/** The instant `microseconds` after the epoch. */
@HiddenFromObjC
fun instantFromEpochMicroseconds(microseconds: Long): Instant =
    Instant.fromEpochSeconds(
        microseconds / MICROS_PER_SECOND,
        (microseconds % MICROS_PER_SECOND) * NANOS_PER_MICROSECOND,
    )
