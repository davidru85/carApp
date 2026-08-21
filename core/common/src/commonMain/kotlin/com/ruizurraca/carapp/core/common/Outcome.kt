package com.ruizurraca.carapp.core.common

/**
 * The result channel of `docs/CONTRACTS.md §20.1` (`D-19`).
 *
 * `kotlin.Result` was rejected because it carries a single type parameter and cannot express the
 * typed error side; Arrow was rejected for MVP dependency surface.
 */
sealed interface Outcome<out T, out E> {
    data class Ok<out T>(val value: T) : Outcome<T, Nothing>

    data class Err<out E>(val error: E) : Outcome<Nothing, E>
}

inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

inline fun <T, E, F> Outcome<T, E>.mapError(transform: (E) -> F): Outcome<T, F> = when (this) {
    is Outcome.Ok -> this
    is Outcome.Err -> Outcome.Err(transform(error))
}

inline fun <T, E, R> Outcome<T, E>.flatMap(transform: (T) -> Outcome<R, E>): Outcome<R, E> =
    when (this) {
        is Outcome.Ok -> transform(value)
        is Outcome.Err -> this
    }

fun <T, E> Outcome<T, E>.getOrNull(): T? = when (this) {
    is Outcome.Ok -> value
    is Outcome.Err -> null
}

inline fun <T, E, R> Outcome<T, E>.fold(onOk: (T) -> R, onErr: (E) -> R): R = when (this) {
    is Outcome.Ok -> onOk(value)
    is Outcome.Err -> onErr(error)
}
