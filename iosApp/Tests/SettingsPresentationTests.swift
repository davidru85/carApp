import Foundation
import Shared
import XCTest
@testable import carApp

/// The host decisions behind the Settings screen of `docs/SPECIFICATION.md §3.1`.
///
/// The screen draws what `SettingsStateHolder` and `SessionStateHolder` publish; these tests pin which
/// rows a phase is offered and how the typed values are named, in both shipped languages.
final class SettingsPresentationTests: XCTestCase {
    func testAPermanentOwnerIsOfferedSignOutAndDeleteAccount() {
        XCTAssertEqual(settingsSessionRows(for: .permanent), [.signOut, .deleteAccount])
    }

    /// `D-215` and `D-156`: a local or anonymous identity exists only on this device, so its one
    /// account row deletes the local data and there is no sign-out.
    func testALocalOrAnonymousOwnerIsOfferedOnlyDeleteLocalData() {
        XCTAssertEqual(settingsSessionRows(for: .local), [.deleteLocalData])
        XCTAssertEqual(settingsSessionRows(for: .anonymous), [.deleteLocalData])
    }

    func testNoAccountRowIsOfferedWithoutAnActiveSession() {
        for phase in [SessionPhase.unknown, .signedOut, .deleting] {
            XCTAssertEqual(settingsSessionRows(for: phase), [], "\(phase) must offer no account row")
        }
    }

    func testEachDistanceUnitHasItsOwnLabel() {
        let keys = [ModelDistanceUnit.km, .miles].map(distanceUnitLabelKey)

        XCTAssertEqual(Set(keys).count, 2, "Two units drawn with one label cannot be told apart")
        XCTAssertFalse(keys.contains(""), "Every unit needs a catalogue key")
    }

    func testEachVolumeUnitHasItsOwnLabel() {
        let keys = [ModelVolumeUnit.liter, .gallon].map(volumeUnitLabelKey)

        XCTAssertEqual(Set(keys).count, 2, "Two units drawn with one label cannot be told apart")
        XCTAssertFalse(keys.contains(""), "Every unit needs a catalogue key")
    }

    func testEveryUnitLabelHasCopyInBothLanguages() throws {
        let keys = [ModelDistanceUnit.km, .miles].map(distanceUnitLabelKey) +
            [ModelVolumeUnit.liter, .gallon].map(volumeUnitLabelKey)

        try assertCopyExists(for: keys)
    }

    /// The option names the currency through the platform, so `SettingsUiState` keeps carrying the code
    /// only (`docs/CONTRACTS.md §14`). The expected name is read from the same platform API, which keeps
    /// the test independent of the ICU version on the simulator.
    func testACurrencyOptionJoinsThePlatformNameAndTheCode() throws {
        let locale = Locale(identifier: "es_ES")
        let name = try XCTUnwrap(locale.localizedString(forCurrencyCode: "EUR"))

        XCTAssertEqual(currencyOptionLabel(code: "EUR", locale: locale), "\(name) (EUR)")
    }

    func testEverySettingsLabelHasCopyInBothLanguages() throws {
        try assertCopyExists(for: [
            "settings_title",
            "settings_section_units",
            "settings_currency",
            "settings_currency_option",
            "settings_distance_unit",
            "settings_volume_unit",
            "settings_section_backup",
            "settings_section_privacy",
            "settings_analytics",
            "settings_analytics_description",
            "settings_section_account",
            "settings_sign_out",
            "settings_delete_account",
            "settings_delete_local_data",
            "settings_section_about",
            "settings_version",
        ])
    }

    /// `D-203` and `docs/CONTRACTS.md §16.1`: the SDK app instance ID remains, so the data is
    /// pseudonymous, not anonymous. Design 06 says "telemetría anónima", which the copy MUST NOT repeat.
    func testTheAnalyticsDescriptionCallsTheDataPseudonymous() throws {
        let expectations = [
            ("en", "pseudonymous", "anonymous"),
            ("es", "seudonimizad", "anónim"),
        ]

        for (language, required, forbidden) in expectations {
            let copy = try localization(language)
                .localizedString(forKey: "settings_analytics_description", value: nil, table: nil)
            XCTAssertTrue(copy.contains(required), "The \(language) copy must say \(required)")
            XCTAssertFalse(copy.lowercased().contains(forbidden), "The \(language) copy must not say \(forbidden)")
        }
    }

    private func assertCopyExists(
        for keys: [String],
        file: StaticString = #filePath,
        line: UInt = #line
    ) throws {
        for language in ["en", "es"] {
            let catalogue = try localization(language)
            for key in keys {
                let copy = catalogue.localizedString(forKey: key, value: nil, table: nil)
                XCTAssertNotEqual(copy, key, "\(language) is missing \(key).", file: file, line: line)
                XCTAssertFalse(copy.isEmpty, "\(language) has an empty value for \(key).", file: file, line: line)
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
