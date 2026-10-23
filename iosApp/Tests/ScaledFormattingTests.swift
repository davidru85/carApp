import XCTest
@testable import carApp

final class ScaledFormattingTests: XCTestCase {
    func testFormatScaledLitersAndPrice() {
        XCTAssertEqual(formatScaled(45200, scale: ScaledFormat.litersScale), "45.200")
        XCTAssertEqual(formatScaled(1629, scale: ScaledFormat.pricePerLiterScale), "1.629")
        XCTAssertEqual(formatScaled(0, scale: ScaledFormat.litersScale), "0.000")
    }

    func testFormatScaledCurrencyAndConsumption() {
        XCTAssertEqual(formatScaled(7363, scale: ScaledFormat.minorUnitScale), "73.63")
        XCTAssertEqual(formatScaled(5, scale: ScaledFormat.minorUnitScale), "0.05")
        XCTAssertEqual(formatScaled(724, scale: ScaledFormat.consumptionScale), "7.24")
    }

    func testParseScaledValidInput() {
        XCTAssertEqual(parseScaled("45.2", scale: ScaledFormat.litersScale), 45200)
        XCTAssertEqual(parseScaled("45,200", scale: ScaledFormat.litersScale), 45200)
        XCTAssertEqual(parseScaled("1.629", scale: ScaledFormat.pricePerLiterScale), 1629)
        XCTAssertEqual(parseScaled("73.63", scale: ScaledFormat.minorUnitScale), 7363)
        XCTAssertEqual(parseScaled("73,63", scale: ScaledFormat.minorUnitScale), 7363)
        XCTAssertEqual(parseScaled("100", scale: ScaledFormat.minorUnitScale), 10000)
    }

    func testParseScaledInvalidInput() {
        XCTAssertNil(parseScaled("", scale: ScaledFormat.litersScale))
        XCTAssertNil(parseScaled("abc", scale: ScaledFormat.litersScale))
        XCTAssertNil(parseScaled("45.2001", scale: ScaledFormat.litersScale))
        XCTAssertNil(parseScaled("1.2.3", scale: ScaledFormat.litersScale))
        XCTAssertNil(parseScaled("-5.0", scale: ScaledFormat.litersScale))
    }

    func testAcceptScaledInput() {
        XCTAssertEqual(acceptScaledInput(previous: "", candidate: "12", scale: 3), "12")
        XCTAssertEqual(acceptScaledInput(previous: "12", candidate: "12.", scale: 3), "12.")
        XCTAssertEqual(acceptScaledInput(previous: "12", candidate: "12,", scale: 3), "12,")
        XCTAssertEqual(acceptScaledInput(previous: "12.", candidate: "12.3", scale: 3), "12.3")
        XCTAssertEqual(acceptScaledInput(previous: "12.3", candidate: "12.345", scale: 3), "12.345")
        // Exceeding 3 decimals should be rejected, returning previous
        XCTAssertEqual(acceptScaledInput(previous: "12.345", candidate: "12.3456", scale: 3), "12.345")
        // Multiple separators should be rejected
        XCTAssertEqual(acceptScaledInput(previous: "12.", candidate: "12..", scale: 3), "12.")
        XCTAssertEqual(acceptScaledInput(previous: "12.", candidate: "12.,", scale: 3), "12.")
        // Non-digits should be rejected
        XCTAssertEqual(acceptScaledInput(previous: "12", candidate: "12a", scale: 3), "12")
    }

    func testFormatScaledNegativeValue() {
        XCTAssertEqual(formatScaled(-7363, scale: ScaledFormat.minorUnitScale), "-73.63")
        XCTAssertEqual(formatScaled(-1, scale: ScaledFormat.minorUnitScale), "-0.01")
        XCTAssertEqual(formatScaled(-45200, scale: ScaledFormat.litersScale), "-45.200")
    }

    func testIsValidOdometerText() {
        XCTAssertTrue(isValidOdometerText(""))
        XCTAssertTrue(isValidOdometerText("0"))
        XCTAssertTrue(isValidOdometerText("142500"))
        XCTAssertTrue(isValidOdometerText("1234567890"))
        // Exceeding 10 digits
        XCTAssertFalse(isValidOdometerText("12345678901"))
        // Negative or letters
        XCTAssertFalse(isValidOdometerText("-10"))
        XCTAssertFalse(isValidOdometerText("12a3"))
    }

    // MARK: - E4-02 locale-aware display (D-222)
    //
    // Grouped expectations use five or more integer digits: Spanish CLDR data leaves a four-digit
    // integer ungrouped on ICU-based runtimes (`1234,56 €`), so a four-digit case would pin a CLDR
    // detail instead of the behaviour.

    private let spanish = Locale(identifier: "es_ES")
    private let english = Locale(identifier: "en_US")
    private let arabicEgypt = Locale(identifier: "ar_EG")

    func testSpanishDecimalUsesTheCommaSeparator() {
        XCTAssertEqual(formatScaledDecimal(724, scale: ScaledFormat.consumptionScale, locale: spanish), "7,24")
    }

    func testEnglishDecimalUsesThePointSeparator() {
        XCTAssertEqual(formatScaledDecimal(724, scale: ScaledFormat.consumptionScale, locale: english), "7.24")
    }

    func testSpanishDecimalGroupsThousandsWithPoints() {
        XCTAssertEqual(formatScaledDecimal(12_345_678, scale: ScaledFormat.litersScale, locale: spanish), "12.345,678")
    }

    func testEnglishDecimalGroupsThousandsWithCommas() {
        XCTAssertEqual(formatScaledDecimal(12_345_678, scale: ScaledFormat.litersScale, locale: english), "12,345.678")
    }

    func testDecimalShowsEveryFractionDigitOfTheScale() {
        XCTAssertEqual(formatScaledDecimal(45_200, scale: ScaledFormat.litersScale, locale: english), "45.200")
    }

    func testSpanishInputTextUsesTheCommaWithoutGrouping() {
        XCTAssertEqual(formatScaledInput(1_234_567, scale: ScaledFormat.litersScale, locale: spanish), "1234,567")
    }

    func testEnglishInputTextUsesThePointWithoutGrouping() {
        XCTAssertEqual(formatScaledInput(1_234_567, scale: ScaledFormat.litersScale, locale: english), "1234.567")
    }

    func testInputTextRoundTripsThroughTheParser() {
        let text = formatScaledInput(1_549, scale: ScaledFormat.pricePerLiterScale, locale: spanish)
        XCTAssertEqual(parseScaled(text, scale: ScaledFormat.pricePerLiterScale), 1_549)
    }

    func testInputTextKeepsThePointWhenTheLocaleSeparatorIsNeitherPointNorComma() {
        XCTAssertEqual(formatScaledInput(1_549, scale: ScaledFormat.pricePerLiterScale, locale: arabicEgypt), "1.549")
    }

    func testSpanishMoneyPlacesTheEuroAfterTheAmount() {
        XCTAssertEqual(
            formatCurrencyAmount(1_234_567, scale: ScaledFormat.minorUnitScale, currencyCode: "EUR", locale: spanish),
            "12.345,67\u{00A0}\u{20AC}"
        )
    }

    func testEnglishMoneyPlacesTheEuroBeforeTheAmount() {
        XCTAssertEqual(
            formatCurrencyAmount(1_234_567, scale: ScaledFormat.minorUnitScale, currencyCode: "EUR", locale: english),
            "\u{20AC}12,345.67"
        )
    }

    func testEnglishDollarAmountUsesTheDollarSign() {
        XCTAssertEqual(
            formatCurrencyAmount(1_234_567, scale: ScaledFormat.minorUnitScale, currencyCode: "USD", locale: english),
            "$12,345.67"
        )
    }

    func testPricePerLitreKeepsThreeFractionDigits() {
        XCTAssertEqual(
            formatCurrencyAmount(1_549, scale: ScaledFormat.pricePerLiterScale, currencyCode: "EUR", locale: spanish),
            "1,549\u{00A0}\u{20AC}"
        )
    }

    func testSpanishIntegerGroupsThousandsWithPoints() {
        XCTAssertEqual(formatInteger(142_500, locale: spanish), "142.500")
    }

    func testEnglishIntegerGroupsThousandsWithCommas() {
        XCTAssertEqual(formatInteger(142_500, locale: english), "142,500")
    }
}
