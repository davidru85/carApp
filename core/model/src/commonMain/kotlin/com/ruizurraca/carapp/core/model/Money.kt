package com.ruizurraca.carapp.core.model

import kotlin.jvm.JvmInline

/**
 * Monetary amount in ISO-4217 minor units (`docs/CONTRACTS.md §2`, `§20.0`).
 *
 * `Money` values of different [currency] MUST NOT be added, subtracted or compared; any
 * aggregation across currencies is a `ValidationError.InvalidUnit`. That rule is enforced by the
 * use cases, not by this type, which stays construction-safe like the rest of `§20.0`.
 */
data class Money(val minorUnits: Long, val currency: CurrencyCode)

/** Litres × 1000. */
@JvmInline
value class FuelVolume(val scaled: Long)

/** Currency units × 1000. */
@JvmInline
value class PricePerLiter(val scaled: Long)

/** L/100 km × 100. A computed read model; never persisted. */
@JvmInline
value class ConsumptionL100Km(val scaled: Long)
