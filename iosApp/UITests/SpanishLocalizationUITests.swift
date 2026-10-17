import XCTest

/// `E4-02`: the app runs in Spanish with Spanish number formatting under `-AppleLanguages (es)
/// -AppleLocale es_ES`. The expected copy is the `es.lproj` value of the named key: a UI-test process
/// cannot read the app bundle's catalogue, so each literal names its key.
final class SpanishLocalizationUITests: XCTestCase {
    private let spanish = AccessibilityUITestSupport.spanishArguments

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testWelcomeActionsUseSpanishCopy() {
        let app = launchCarApp(arguments: spanish, environment: AccessibilityUITestSupport.forceWelcome)
        let guest = app.buttons["welcome_guest"]
        XCTAssertTrue(guest.waitForExistence(timeout: 10))
        XCTAssertEqual(guest.label, "Continuar sin cuenta") // es.lproj continue_without_account
        XCTAssertEqual(app.buttons["welcome_google"].label, "Continuar con Google") // es.lproj continue_with_google
        attachAccessibilityEvidence(of: app, named: "es-welcome")
    }

    func testVehicleOdometerUsesSpanishGrouping() {
        let app = launchCarApp(arguments: spanish)
        createVehicleAndOpenDetail(in: app, odometer: "142500")
        // The odometer is read on the vehicle list, so the row is opened and left again: the detail is
        // where a first vehicle lands (F-2), and its back affordance returns to the list.
        XCTAssertTrue(returnToTheListFromTheDetail(in: app), "the back affordance is not hittable")
        let odometer = app.staticTexts.matching(NSPredicate(format: "label == %@", "142.500 km")).firstMatch
        XCTAssertTrue(odometer.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        attachAccessibilityEvidence(of: app, named: "es-vehicle-list")
    }

    func testFuelDerivedTotalUsesTheSpanishDecimalComma() {
        let app = launchCarApp(arguments: spanish)
        createVehicleAndOpenDetail(in: app, odometer: "1000")
        openFuelEntryForm(in: app)
        typeFuelEntry(in: app, odometer: "1100", liters: "40", price: "2")
        let derived = app.staticTexts["fuel_derived_value"]
        XCTAssertTrue(derived.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        XCTAssertTrue(derived.label.contains("80,00"), "Saw \(derived.label)")
        attachAccessibilityEvidence(of: app, named: "es-fuel-form")
    }

    func testSettingsUseSpanishCopy() {
        let app = launchCarApp(arguments: spanish)
        // Settings lives on the vehicle list, so the list is reached directly.
        reachTheVehicleListForAccessibility(in: app)
        openSettings(in: app)
        // es.lproj backup_status_idle, read through the backup_status_description label.
        let status = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "Sincronizado localmente"))
            .firstMatch
        XCTAssertTrue(status.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        // The backup row sits below the fold on a shorter device; scroll to it before reading its copy.
        XCTAssertTrue(scrollUntilHittable(status, in: app), "the backup status row must be reachable")
        attachAccessibilityEvidence(of: app, named: "es-settings")
    }

    func testDepartureFailureUsesSpanishCopy() {
        let app = launchCarApp(arguments: spanish, environment: AccessibilityUITestSupport.forceDepartureFailure)
        // es.lproj error_persistence
        let error = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "No se ha podido guardar el cambio"))
            .firstMatch
        XCTAssertTrue(error.waitForExistence(timeout: 10))
        attachAccessibilityEvidence(of: app, named: "es-departure")
    }
}
