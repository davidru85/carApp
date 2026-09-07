@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.feature.session.domain

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

/**
 * The fixed elapsed-day thresholds of the `D-62` anonymous sign-in benefit reminder schedule
 * (`docs/CONTRACTS.md §11.3`). The list is the single configuration constant of the schedule and a
 * threshold's position in it is the zero-based reminder index.
 */
val ANONYMOUS_REMINDER_ELAPSED_DAYS: List<Int> = listOf(1, 3, 8, 18)

/**
 * The highest due reminder index that has not been shown yet, or `null` when this evaluation emits
 * nothing.
 *
 * [accountCreatedAt] is the Firebase anonymous user-creation timestamp, which anchors the schedule.
 * [lastShownIndex] is the persisted zero-based index of the last reminder shown on this device, or
 * `null` when none has been shown.
 *
 * Only the highest due index is returned, so a device that returns after an inactive period shows
 * one notice instead of replaying the backlog it missed. Persisting that index consumes every lower
 * pending reminder.
 */
@HiddenFromObjC
fun dueAnonymousReminderIndex(
    accountCreatedAt: Instant,
    now: Instant,
    lastShownIndex: Int?,
): Int? {
    val elapsedDays = (now - accountCreatedAt).inWholeDays
    val highestDueIndex =
        ANONYMOUS_REMINDER_ELAPSED_DAYS.indexOfLast { threshold -> elapsedDays >= threshold }
    if (highestDueIndex < 0) return null
    if (lastShownIndex != null && highestDueIndex <= lastShownIndex) return null
    return highestDueIndex
}

/**
 * Device-local persistence of the anonymous reminder schedule position.
 *
 * The state is bound to the anonymous UID that produced it, so a different anonymous identity
 * starts the schedule again, and it is cleared once the owner signs in permanently or links a
 * permanent credential.
 */
@HiddenFromObjC
interface AnonymousReminderRepository {
    suspend fun lastShownIndex(anonymousUid: String): Outcome<Int?, AppError>

    suspend fun recordShown(
        anonymousUid: String,
        index: Int,
    ): Outcome<Unit, AppError>

    suspend fun clear(): Outcome<Unit, AppError>
}
