import XCTest

/// `E4-02`, `D-217`: every screen of F-1 to F-3, Settings, the departure screen and the conversion
/// surface passes `performAccessibilityAudit()`. Issues are recorded, not stopped at, so one run lists
/// every finding of a screen.
final class AccessibilityAuditUITests: XCTestCase {
    private let english = AccessibilityUITestSupport.englishArguments

    override func setUpWithError() throws {
        continueAfterFailure = true
    }

    @MainActor
    func testWelcomePassesTheAccessibilityAudit() throws {
        let app = launchCarApp(arguments: english, environment: AccessibilityUITestSupport.forceWelcome)
        XCTAssertTrue(app.buttons["welcome_guest"].waitForExistence(timeout: 10))
        attachAccessibilityEvidence(of: app, named: "audit-welcome")
        try auditAccessibility(of: app, screen: "welcome")
    }

    @MainActor
    func testFirstVehicleFormPassesTheAccessibilityAudit() throws {
        let app = launchCarApp(arguments: english, environment: AccessibilityUITestSupport.forceFirstVehicle)
        openVehicleCreationForAccessibility(in: app)
        attachAccessibilityEvidence(of: app, named: "audit-first-vehicle-form")
        try auditAccessibility(of: app, screen: "vehicle-form")
    }

    @MainActor
    func testVehicleDetailPassesTheAccessibilityAudit() throws {
        let app = launchCarApp(arguments: english)
        createVehicleAndOpenDetail(in: app, odometer: "142500")
        attachAccessibilityEvidence(of: app, named: "audit-vehicle-detail")
        try auditAccessibility(of: app, screen: "vehicle-detail")
    }

    @MainActor
    func testVehicleListPassesTheAccessibilityAudit() throws {
        let app = launchCarApp(arguments: english)
        createVehicleAndOpenDetail(in: app, odometer: "1000")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.buttons["add_vehicle"].waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        attachAccessibilityEvidence(of: app, named: "audit-vehicle-list")
        try auditAccessibility(of: app, screen: "vehicle-list")
    }

    @MainActor
    func testFuelEntryFormPassesTheAccessibilityAudit() throws {
        let app = launchCarApp(arguments: english)
        createVehicleAndOpenDetail(in: app, odometer: "1000")
        openFuelEntryForm(in: app)
        attachAccessibilityEvidence(of: app, named: "audit-fuel-form")
        try auditAccessibility(of: app, screen: "fuel-form")
    }

    @MainActor
    func testOdometerWarningPassesTheAccessibilityAudit() throws {
        let app = launchCarApp(arguments: english)
        createVehicleAndOpenDetail(in: app, odometer: "1000")
        openFuelEntryForm(in: app)
        typeFuelEntry(in: app, odometer: "500", liters: "40", price: "2")
        app.buttons["save_fuel_entry"].tap()
        XCTAssertTrue(app.alerts.firstMatch.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        attachAccessibilityEvidence(of: app, named: "audit-odometer-warning")
        try auditAccessibility(of: app, screen: "odometer-warning")
    }

    @MainActor
    func testFuelEntryRowWithBadgesPassesTheAccessibilityAudit() throws {
        let app = launchCarApp(arguments: english)
        createVehicleAndOpenDetail(in: app, odometer: "1000")
        openFuelEntryForm(in: app)
        typeFuelEntry(in: app, odometer: "500", liters: "40", price: "2")
        // The switch is flipped through a coordinate tap on its trailing edge, which is the only
        // gesture that reliably toggles a SwiftUI `Form` switch here, and the test waits for the new
        // value so it does not miss the `partial tank` badge it audits.
        // The same two-step toggle `VehicleAndFuelFlowUITests` uses: the element tap, then the
        // trailing-edge coordinate tap only if the switch is still on.
        let fullTank = app.switches["fuel_full_tank"]
        XCTAssertTrue(fullTank.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        if (fullTank.value as? String) == "1" {
            fullTank.tap()
        }
        if (fullTank.value as? String) == "1" {
            fullTank.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)).tap()
        }
        app.buttons["save_fuel_entry"].tap()
        let confirm = app.alerts.buttons["confirm_fuel_odometer_warning"].firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        confirm.tap()
        let badge = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'fuel_indicator_' AND identifier ENDSWITH '_partial'"))
            .firstMatch
        XCTAssertTrue(badge.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        attachAccessibilityEvidence(of: app, named: "audit-fuel-row-badges")
        try auditAccessibility(of: app, screen: "fuel-row")
    }

    @MainActor
    func testSettingsPassesTheAccessibilityAudit() throws {
        let app = launchCarApp(arguments: english)
        createVehicleAndOpenDetail(in: app, odometer: "1000")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        openSettings(in: app)
        attachAccessibilityEvidence(of: app, named: "audit-settings")
        try auditAccessibility(of: app, screen: "settings")
    }

    @MainActor
    func testConversionRowPassesTheAccessibilityAudit() throws {
        let app = launchCarApp(arguments: english)
        createVehicleAndOpenDetail(in: app, odometer: "1000")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        openSettings(in: app)
        let row = app.descendants(matching: .any)["settings_link_account_row"]
        // `D-224`: the row exists only for an anonymous session; a run without the App Check debug
        // token stays LOCAL and records the skip as evidence.
        try XCTSkipUnless(row.waitForExistence(timeout: 10), "The session is not anonymous, so the conversion row is hidden.")
        attachAccessibilityEvidence(of: app, named: "audit-conversion-row")
        try auditAccessibility(of: app, screen: "conversion")
    }

    @MainActor
    func testDepartureFailurePassesTheAccessibilityAudit() throws {
        let app = launchCarApp(arguments: english, environment: AccessibilityUITestSupport.forceDepartureFailure)
        let retry = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'departure_'"))
            .firstMatch
        XCTAssertTrue(retry.waitForExistence(timeout: 10))
        attachAccessibilityEvidence(of: app, named: "audit-departure-failure")
        try auditAccessibility(of: app, screen: "departure")
    }
}
