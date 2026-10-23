import Foundation
import Shared

/// One account row of the Settings screen (`docs/SPECIFICATION.md §3.1`, `D-215`).
enum SettingsSessionRow: Hashable {
    case signOut
    case deleteAccount
    case deleteLocalData
}

/// The account rows a session phase is offered (`D-215`).
///
/// A permanent owner can sign out and delete the account. A local or anonymous identity exists only
/// on this device (`D-156`), so its one row deletes the local data through `requestDeleteAccount()`
/// and it has no sign-out (`docs/SPECIFICATION.md §7 F-5`). Without an active session there is
/// nothing to leave. The anonymous-only "link account" row belongs to `E4-05`.
func settingsSessionRows(for phase: SessionPhase) -> [SettingsSessionRow] {
    switch phase {
    case .permanent:
        return [.signOut, .deleteAccount]
    case .local, .anonymous:
        return [.deleteLocalData]
    case .unknown, .signedOut, .deleting:
        return []
    }
}

/// The catalogue key naming a distance unit. Units are read-only in the MVP (`§3.1`).
func distanceUnitLabelKey(_ unit: ModelDistanceUnit) -> String {
    switch unit {
    case .km:
        return "unit_distance_km"
    case .miles:
        return "unit_distance_miles"
    }
}

/// The catalogue key naming a volume unit. Units are read-only in the MVP (`§3.1`).
func volumeUnitLabelKey(_ unit: ModelVolumeUnit) -> String {
    switch unit {
    case .liter:
        return "unit_volume_liter"
    case .gallon:
        return "unit_volume_gallon"
    }
}

/// A currency option: the platform's display name for the code in `locale`, followed by the code.
/// `SettingsUiState` carries only the code (`docs/CONTRACTS.md §14`, `§15.3`), so the name comes from
/// the platform. A code the platform cannot name is shown as the code alone.
func currencyOptionLabel(code: String, locale: Locale) -> String {
    guard let name = locale.localizedString(forCurrencyCode: code), name != code else {
        return code
    }
    return String(format: String(localized: "settings_currency_option"), name, code)
}
