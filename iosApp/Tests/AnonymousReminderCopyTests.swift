import Foundation
import XCTest
@testable import carApp

final class AnonymousReminderCopyTests: XCTestCase {
    func testEveryScheduledReminderHasItsOwnBody() {
        let keys = (0..<4).map { index in anonymousReminderBodyKey(for: Int32(index)) }

        XCTAssertEqual(
            keys.count,
            Set(keys).count,
            "Repeating the same sentence four times reads as a bug, not as an escalating warning."
        )
    }

    func testAnIndexOutsideTheScheduleFallsBackWithoutCrashing() {
        XCTAssertEqual(anonymousReminderBodyKey(for: 9), anonymousReminderBodyKey(for: 3))
        XCTAssertEqual(anonymousReminderBodyKey(for: -1), anonymousReminderBodyKey(for: 0))
    }

    /// Both catalogues are asserted explicitly. The notice is worthless in the language it fails to
    /// state the deadline in, and the language of the test device must not decide what is checked.
    func testEveryReminderBodyStatesTheRecoveryBenefitAndTheThirtyDayRisk() throws {
        let expectations = [
            ("en", "30 days", "Sign in with"),
            ("es", "30 días", "Inicia sesión con"),
        ]

        for (language, deadline, benefit) in expectations {
            let catalogue = try localization(language)
            for index in Int32(0)..<Int32(4) {
                let key = anonymousReminderBodyKey(for: index)
                let body = catalogue.localizedString(forKey: key, value: nil, table: nil)

                XCTAssertNotEqual(body, key, "\(language) is missing \(key).")
                XCTAssertTrue(
                    body.contains(deadline),
                    "Reminder \(index) hides the cleanup deadline in \(language)."
                )
                XCTAssertTrue(
                    body.contains(benefit),
                    "Reminder \(index) hides the recovery benefit in \(language)."
                )
            }
        }
    }

    /// The catalogues live in the application bundle, not in the test bundle, so the lookup is
    /// anchored to a class the application target owns.
    private func localization(_ language: String) throws -> Bundle {
        let applicationBundle = Bundle(for: WalkingSkeletonModel.self)
        let path = try XCTUnwrap(
            applicationBundle.path(forResource: language, ofType: "lproj"),
            "The application bundle carries no \(language) localization."
        )
        return try XCTUnwrap(Bundle(path: path))
    }
}
