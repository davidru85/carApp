import Foundation

enum ScaledFormat {
    static let minorUnitScale = 2
    static let litersScale = 3
    static let pricePerLiterScale = 3
    static let consumptionScale = 2
    static let maxOdometerDigits = 10
}

func formatScaled(_ value: Int64, scale: Int) -> String {
    return ""
}

func parseScaled(_ input: String, scale: Int) -> Int64? {
    return nil
}

func acceptScaledInput(previous: String, candidate: String, scale: Int) -> String {
    return previous
}

func isValidScaledEditingText(_ input: String, scale: Int) -> Bool {
    return false
}

func isValidOdometerText(_ input: String) -> Bool {
    return false
}
