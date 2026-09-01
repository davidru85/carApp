package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.LocaleInfo
import com.ruizurraca.carapp.core.common.LocaleProvider
import com.ruizurraca.carapp.core.common.resolveLocaleCurrency
import com.ruizurraca.carapp.core.model.CurrencyCode
import java.util.Currency
import java.util.Locale

internal class AndroidLocaleProvider(
    private val currentLocale: () -> Locale = Locale::getDefault,
) : LocaleProvider {
    override fun current(): LocaleInfo {
        val locale = currentLocale()
        val platformCurrency = runCatching { Currency.getInstance(locale) }.getOrNull()
        return LocaleInfo(
            languageTag = locale.toLanguageTag(),
            region = locale.country.ifBlank { null },
            suggestedCurrency =
                resolveLocaleCurrency(
                    suggestedCurrency = platformCurrency?.currencyCode?.let(::CurrencyCode),
                    runtimeMinorUnitFactor =
                        platformCurrency
                            ?.defaultFractionDigits
                            ?.takeIf { it == TWO_MINOR_DIGITS }
                            ?.let { TWO_DECIMAL_MINOR_UNIT_FACTOR },
                ),
        )
    }

    private companion object {
        const val TWO_MINOR_DIGITS = 2
        const val TWO_DECIMAL_MINOR_UNIT_FACTOR = 100
    }
}
