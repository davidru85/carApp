import SwiftUI
import UIKit

/// Colours that keep the WCAG AA 4.5:1 text contrast in light and dark appearance (`E4-02`,
/// `D-223`). The system `.red` measures 3.55:1 on white, so error copy uses `errorText` instead.
enum AccessibleColors {
    /// #B3261E in light appearance (6.54:1 on white, 5.86:1 on the grouped background) and #FF6961
    /// in dark appearance (7.45:1 on black, 6.03:1 on the grouped cell).
    /// #0040DD, the accent the audit measures at 7.56:1 on white, used on the controls whose system
    /// accent (system blue, 4.02:1) fails the audit.
    static let controlAccent = Color(red: 0.0, green: 64.0 / 255.0, blue: 221.0 / 255.0)

    static let errorText = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 1.0, green: 105.0 / 255.0, blue: 97.0 / 255.0, alpha: 1.0)
            : UIColor(red: 179.0 / 255.0, green: 38.0 / 255.0, blue: 30.0 / 255.0, alpha: 1.0)
    })
}

/// Posts a VoiceOver announcement when an error appears and whenever its text changes, so a failure
/// rendered away from the focused element is still heard (`E4-02`). Without VoiceOver it does nothing.
struct AnnouncedOnAppearance: ViewModifier {
    let text: String

    func body(content: Content) -> some View {
        content
            .onAppear { announce(text) }
            .onChange(of: text) { newText in announce(newText) }
    }

    private func announce(_ text: String) {
        UIAccessibility.post(notification: .announcement, argument: text)
    }
}

extension View {
    /// Announces `text` with VoiceOver when this view appears and whenever `text` changes.
    func announcedOnAppearance(_ text: String) -> some View {
        modifier(AnnouncedOnAppearance(text: text))
    }
}
