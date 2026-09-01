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
