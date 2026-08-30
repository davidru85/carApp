@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.core.common

import kotlin.native.HiddenFromObjC

/**
 * The result channel of `docs/CONTRACTS.md §20.1` (`D-19`).
 *
 * `kotlin.Result` was rejected because it carries a single type parameter and cannot express the
 * typed error side; Arrow was rejected for MVP dependency surface.
 */
@HiddenFromObjC
sealed interface Outcome<out T, out E> {
    @HiddenFromObjC
    data class Ok<out T>(
        val value: T,
    ) : Outcome<T, Nothing>

    @HiddenFromObjC
    data class Err<out E>(
        val error: E,
    ) : Outcome<Nothing, E>
}

@HiddenFromObjC
inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> =
    when (this) {
        is Outcome.Ok -> Outcome.Ok(transform(value))
        is Outcome.Err -> this
    }

@HiddenFromObjC
inline fun <T, E, F> Outcome<T, E>.mapError(transform: (E) -> F): Outcome<T, F> =
    when (this) {
        is Outcome.Ok -> this
        is Outcome.Err -> Outcome.Err(transform(error))
    }

@HiddenFromObjC
inline fun <T, E, R> Outcome<T, E>.flatMap(transform: (T) -> Outcome<R, E>): Outcome<R, E> =
    when (this) {
        is Outcome.Ok -> transform(value)
        is Outcome.Err -> this
    }

@HiddenFromObjC
fun <T, E> Outcome<T, E>.getOrNull(): T? =
    when (this) {
        is Outcome.Ok -> value
        is Outcome.Err -> null
    }

@HiddenFromObjC
inline fun <T, E, R> Outcome<T, E>.fold(
    onOk: (T) -> R,
    onErr: (E) -> R,
): R =
    when (this) {
        is Outcome.Ok -> onOk(value)
        is Outcome.Err -> onErr(error)
    }
