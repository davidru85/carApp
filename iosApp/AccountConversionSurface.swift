import AuthenticationServices
import Shared
import SwiftUI

/// `E4-05`: the F-4 entry point is offered only to an anonymous session. `startAccountConversion`
/// refuses every other session (`docs/CONTRACTS.md §20.10`), and `D-224` keeps the row hidden
/// while the session is still `LOCAL`, until adoption makes it anonymous.
func showsLinkAccountRow(phase: SessionPhase) -> Bool {
    switch phase {
    case .anonymous:
        return true
    case .unknown, .local, .permanent, .signedOut, .deleting:
        return false
    }
}

/// The one session confirmation the conversion surface answers itself (`docs/CONTRACTS.md §11.3`).
func isAccountConversionConfirmation(_ confirmation: Confirmation?) -> Bool {
    confirmation == .adoptExistingAccount
}

/// The session-channel key for `code`, or `nil` to keep the shared mapping. On that channel a
/// `REMOTE.*` code is published only by a confirmed account replacement that failed after the session
/// switch, which the app resumes on its own; departures publish only `AUTH.*` and `PERSISTENCE.*`.
func sessionMessageKey(for code: String) -> String? {
    code.hasPrefix("REMOTE.") ? "error_conversion_replacement_pending" : nil
}

/// Copy for a message of the session channel.
func localizedSessionMessage(_ message: UiMessage) -> String {
    guard let key = sessionMessageKey(for: message.code) else {
        return message.localizedText
    }
    return String(localized: String.LocalizationValue(key))
}
