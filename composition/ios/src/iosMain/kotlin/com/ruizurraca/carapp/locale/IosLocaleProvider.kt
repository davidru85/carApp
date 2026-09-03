package com.ruizurraca.carapp.locale

import com.ruizurraca.carapp.core.common.LocaleInfo
import com.ruizurraca.carapp.core.common.LocaleProvider
import com.ruizurraca.carapp.core.common.resolveLocaleCurrency
import com.ruizurraca.carapp.core.model.CurrencyCode
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleCurrencyCode
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

internal class IosLocaleProvider(
    private val currentLocale: () -> NSLocale = { NSLocale.currentLocale },
) : LocaleProvider {
    override fun current(): LocaleInfo = currentLocale().toLocaleInfo()
}

private fun NSLocale.toLocaleInfo(): LocaleInfo {
    val currencyCode = objectForKey(NSLocaleCurrencyCode) as? String
    val fractionDigits =
        currencyCode?.let { code ->
            NSNumberFormatter()
                .apply {
                    numberStyle = NSNumberFormatterCurrencyStyle
                    this.currencyCode = code
                }.maximumFractionDigits
                .toInt()
        }
    return LocaleInfo(
        languageTag = localeIdentifier.replace('_', '-'),
        region = objectForKey(NSLocaleCountryCode) as? String,
        suggestedCurrency =
            resolveLocaleCurrency(
                suggestedCurrency = currencyCode?.let(::CurrencyCode),
                runtimeMinorUnitFactor =
                    fractionDigits
                        ?.takeIf { it == TWO_MINOR_DIGITS }
                        ?.let { TWO_DECIMAL_MINOR_UNIT_FACTOR },
            ),
    )
}

private const val TWO_MINOR_DIGITS = 2
private const val TWO_DECIMAL_MINOR_UNIT_FACTOR = 100
