// MagicNumber is suppressed for this file on purpose. The literals below are not magic: they are
// the canonical formula of docs/CONTRACTS.md §2, which that section says MUST be implemented
// literally. Replacing 500_000 or 1_000_000 with a named constant would hide the one thing a
// reviewer has to check, which is that the expression matches the document character for character.
@file:Suppress("MagicNumber")

package com.ruizurraca.carapp.core.model

/*
 * The three canonical monetary formulas of `docs/CONTRACTS.md §2`, implemented literally as exact
 * integer arithmetic. A floating-point or naive integer-division implementation is a contract
 * violation, and `Float` and `Double` are forbidden here in every layer.
 *
 * Every intermediate expression is evaluated as `Long` and MUST NOT be narrowed to `Int` at any
 * step: the largest golden case produces the intermediate product `49_999_950_000_000`, which
 * overflows `Int` by six orders of magnitude.
 *
 * Rounding is HALF_UP on non-negative inputs, expressed as the `+ half` term inside each numerator
 * rather than by a rounding helper, so the result cannot drift from the contract.
 *
 * `minorUnitFactor` is supplied by the caller rather than resolved here, because it comes from
 * `MinorUnits.factorFor` in `:core:common` and `:core:model` MUST NOT depend on `:core:common`
 * (`docs/TECHNICAL_PLAN.md §4`). Validation resolves the factor and rejects an unsupported
 * currency before any of these functions is reached.
 */

/** `totalCostMinor = (litersScaled * pricePerLiterScaled * minorUnitFactor + 500_000) / 1_000_000` */
fun totalCostMinorOf(
    litersScaled: Long,
    pricePerLiterScaled: Long,
    minorUnitFactor: Int,
): Long {
    val factor = minorUnitFactor.toLong()
    return (litersScaled * pricePerLiterScaled * factor + 500_000L) / 1_000_000L
}

/**
 * `pricePerLiterScaled = (totalCostMinor * 1_000_000 + (litersScaled * minorUnitFactor) / 2)
 *                        / (litersScaled * minorUnitFactor)`
 */
fun pricePerLiterScaledOf(
    totalCostMinor: Long,
    litersScaled: Long,
    minorUnitFactor: Int,
): Long {
    val denominator = litersScaled * minorUnitFactor.toLong()
    require(denominator > 0L) { "litersScaled * minorUnitFactor must be positive, was $denominator" }
    return (totalCostMinor * 1_000_000L + denominator / 2L) / denominator
}

/**
 * `litersScaled = (totalCostMinor * 1_000_000 + (pricePerLiterScaled * minorUnitFactor) / 2)
 *                 / (pricePerLiterScaled * minorUnitFactor)`
 */
fun litersScaledOf(
    totalCostMinor: Long,
    pricePerLiterScaled: Long,
    minorUnitFactor: Int,
): Long {
    val denominator = pricePerLiterScaled * minorUnitFactor.toLong()
    require(denominator > 0L) { "pricePerLiterScaled * minorUnitFactor must be positive, was $denominator" }
    return (totalCostMinor * 1_000_000L + denominator / 2L) / denominator
}
