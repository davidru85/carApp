import Foundation

enum ScaledFormat {
    static let minorUnitScale = 2
    static let litersScale = 3
    static let pricePerLiterScale = 3
    static let consumptionScale = 2
    static let maxOdometerDigits = 10

    static func powerOfTen(_ scale: Int) -> Int64? {
        switch scale {
        case 2:
            return 100
        case 3:
            return 1_000
        default:
            return nil
        }
    }
}

func formatScaled(_ value: Int64, scale: Int) -> String {
    guard let factor = ScaledFormat.powerOfTen(scale) else { return "" }
    let isNegative = value < 0
    let absValue = abs(value)
    let whole = absValue / factor
    let remainder = absValue % factor
    let fraction = String(format: "%0*d", scale, remainder)
    let sign = isNegative ? "-" : ""
    return "\(sign)\(whole).\(fraction)"
}

/// A scaled quantity for display, grouped and with the locale's decimal separator: `7,24` in `es_ES`,
/// `7.24` in `en_US` (`E4-02`, `D-222`). The value and its scale are the `docs/CONTRACTS.md §2`
/// representation: an exact `NSDecimalNumber` with exactly `scale` fraction digits is printed with
/// exactly `scale` fraction digits, so nothing is rounded; only separators and grouping follow the
/// locale.
func formatScaledDecimal(_ value: Int64, scale: Int, locale: Locale) -> String {
    let formatter = exactFormatter(style: .decimal, scale: scale, locale: locale)
    return formatter.string(from: exactDecimal(value, scale: scale)) ?? formatScaled(value, scale: scale)
}

/// A scaled quantity for an editable field. It is never grouped and it uses a comma only where the
/// locale's decimal separator is a comma, because `parseScaled` reads exactly `.` and `,`.
func formatScaledInput(_ value: Int64, scale: Int, locale: Locale) -> String {
    let canonical = formatScaled(value, scale: scale)
    return locale.decimalSeparator == "," ? canonical.replacingOccurrences(of: ".", with: ",") : canonical
}

/// A scaled amount of `currencyCode` with the locale's currency placement: `12.345,67 €` in `es_ES`,
/// `€12,345.67` in `en_US`.
func formatCurrencyAmount(_ value: Int64, scale: Int, currencyCode: String, locale: Locale) -> String {
    let formatter = exactFormatter(style: .currency, scale: scale, locale: locale)
    formatter.currencyCode = currencyCode
    // Setting the currency may reset the fraction digits, so they are applied again afterwards.
    formatter.minimumFractionDigits = scale
    formatter.maximumFractionDigits = scale
    let fallback = formatScaledDecimal(value, scale: scale, locale: locale) + " " + currencyCode
    return formatter.string(from: exactDecimal(value, scale: scale)) ?? fallback
}

/// A whole number grouped for the locale, such as an odometer: `142.500` in `es_ES`.
func formatInteger(_ value: Int64, locale: Locale) -> String {
    let formatter = NumberFormatter()
    formatter.locale = locale
    formatter.numberStyle = .decimal
    formatter.usesGroupingSeparator = true
    formatter.maximumFractionDigits = 0
    return formatter.string(from: NSNumber(value: value)) ?? String(value)
}

private func exactFormatter(style: NumberFormatter.Style, scale: Int, locale: Locale) -> NumberFormatter {
    let formatter = NumberFormatter()
    formatter.locale = locale
    formatter.numberStyle = style
    formatter.usesGroupingSeparator = true
    formatter.minimumFractionDigits = scale
    formatter.maximumFractionDigits = scale
    formatter.roundingMode = .halfUp
    return formatter
}

private func exactDecimal(_ value: Int64, scale: Int) -> NSDecimalNumber {
    NSDecimalNumber(mantissa: value.magnitude, exponent: Int16(-scale), isNegative: value < 0)
}

func parseScaled(_ input: String, scale: Int) -> Int64? {
    let normalised = input.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")
    let parts = normalised.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
    guard isValidScaledInput(parts, scale: scale) else { return nil }
    guard let whole = Int64(parts[0]), let factor = ScaledFormat.powerOfTen(scale) else { return nil }
    let fractionText = parts.count > 1 ? parts[1] : ""
    let paddedFractionText = fractionText.padding(toLength: scale, withPad: "0", startingAt: 0)
    guard let fraction = Int64(paddedFractionText) else { return nil }

    let (multiplied, overflow1) = whole.multipliedReportingOverflow(by: factor)
    if overflow1 { return nil }
    let (added, overflow2) = multiplied.addingReportingOverflow(fraction)
    if overflow2 { return nil }
    return added
}

func acceptScaledInput(previous: String, candidate: String, scale: Int) -> String {
    if isValidScaledEditingText(candidate, scale: scale) {
        return candidate
    } else {
        return previous
    }
}

func isValidScaledEditingText(_ input: String, scale: Int) -> Bool {
    if input.isEmpty { return true }
    let separators: [Character] = [".", ","]
    guard let firstSeparatorIndex = input.firstIndex(where: { separators.contains($0) }) else {
        return input.allSatisfy(\.isNumber)
    }
    let afterFirst = input.index(after: firstSeparatorIndex)
    if input[afterFirst...].contains(where: { separators.contains($0) }) {
        return false
    }
    let whole = input[..<firstSeparatorIndex]
    let fraction = input[afterFirst...]
    return !whole.isEmpty && whole.allSatisfy(\.isNumber) && fraction.allSatisfy(\.isNumber) && fraction.count <= scale
}

private func isValidScaledInput(_ parts: [String], scale: Int) -> Bool {
    guard parts.count <= 2, !parts.isEmpty, !parts[0].isEmpty else { return false }
    guard parts.allSatisfy({ $0.allSatisfy(\.isNumber) }) else { return false }
    let fraction = parts.count > 1 ? parts[1] : ""
    return fraction.count <= scale
}

func isValidOdometerText(_ input: String) -> Bool {
    input.isEmpty || (input.allSatisfy(\.isNumber) && input.count <= ScaledFormat.maxOdometerDigits)
}
