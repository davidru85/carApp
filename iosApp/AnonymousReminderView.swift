import SwiftUI

/// Maps a `D-62` reminder index to the notice body shown on iOS. Every body states the benefit of
/// permanent sign-in and the device-bound 30-day cleanup risk of `docs/CONTRACTS.md §11.2`; the
/// later ones state how little time is left. iOS offers Google and Apple
/// (`docs/SPECIFICATION.md §7 F-1`), so the copy names both and no other provider.
func anonymousReminderBodyKey(for index: Int32) -> String {
    switch index {
    case ..<1:
        return "anonymous_reminder_body_1"
    case 1:
        return "anonymous_reminder_body_2"
    case 2:
        return "anonymous_reminder_body_3"
    default:
        return "anonymous_reminder_body_4"
    }
}

/// The foreground retention notice. It is dismissible, it renders above the authenticated content
/// without covering it, and it offers no action of its own, so it can never gate a feature.
struct AnonymousReminderView: View {
    let index: Int32
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(LocalizedStringKey("anonymous_reminder_title"))
                .font(.subheadline.weight(.semibold))
            Text(LocalizedStringKey(anonymousReminderBodyKey(for: index)))
                .font(.footnote)
            HStack {
                Spacer()
                Button(LocalizedStringKey("anonymous_reminder_dismiss"), action: onDismiss)
                    .accessibilityIdentifier("anonymous_reminder_dismiss")
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.accentColor.opacity(0.12))
        .accessibilityIdentifier("anonymous_reminder_banner")
    }
}
