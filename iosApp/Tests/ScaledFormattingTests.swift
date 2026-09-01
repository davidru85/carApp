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
}
