package com.ruizurraca.carapp

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * A scaled quantity for display, grouped and with the locale's decimal separator: `7,24` in `es-ES`,
 * `7.24` in `en-US` (`E4-02`, `D-222`).
 *
 * The value and its scale are the `docs/CONTRACTS.md §2` representation and are never changed: an
 * exact `BigDecimal` carrying exactly [scale] fraction digits is printed with exactly [scale] fraction
 * digits, and `RoundingMode.UNNECESSARY` turns any attempt to round into an exception instead of a
 * silently different number. Only separators and grouping follow the locale (`§14`).
 */
internal fun formatScaledDecimal(
    value: Long,
    scale: Int,
    locale: Locale,
): String = exactly(NumberFormat.getNumberInstance(locale), scale).format(BigDecimal.valueOf(value, scale))

/**
 * A scaled quantity for an editable field. It is never grouped and it uses a comma only where the
 * locale's decimal separator is a comma, because [parseScaled] reads exactly `.` and `,`.
 */
internal fun formatScaledInput(
    value: Long,
    scale: Int,
    locale: Locale,
): String {
    val canonical = formatScaled(value, scale)
    return if (DecimalFormatSymbols.getInstance(locale).decimalSeparator == COMMA) {
        canonical.replace(POINT, COMMA)
    } else {
        canonical
    }
}

/**
 * A scaled amount of [currencyCode] with the locale's currency placement: `12.345,67 €` in `es-ES`,
 * `€12,345.67` in `en-US`. A code the platform does not know follows the plain amount, so a corrupt
 * code can never crash a screen.
 */
internal fun formatCurrencyAmount(
    value: Long,
    scale: Int,
    currencyCode: String,
    locale: Locale,
): String {
    val currency =
        runCatching { Currency.getInstance(currencyCode) }.getOrNull()
            ?: return "${formatScaledDecimal(value, scale, locale)} $currencyCode"
    val format = NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }
    return exactly(format, scale).format(BigDecimal.valueOf(value, scale))
}

/** A whole number grouped for the locale, such as an odometer: `142.500` in `es-ES`. */
internal fun formatInteger(
    value: Long,
    locale: Locale,
): String = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = true }.format(value)

/** The fraction digits are set after the currency, because setting a currency may reset them. */
private fun exactly(
    format: NumberFormat,
    scale: Int,
): NumberFormat =
    format.apply {
        isGroupingUsed = true
        minimumFractionDigits = scale
        maximumFractionDigits = scale
        roundingMode = RoundingMode.UNNECESSARY
    }

private const val COMMA = ','
private const val POINT = '.'
