import XCTest

/// `E4-02`, `D-218`: at AX3 (about 235 % of Body, the iOS reading of the 200 % of
/// `docs/SPECIFICATION.md §11`) every primary action can be scrolled to and hit, and no text is clipped
/// or excluded from Dynamic Type.
final class LargeContentSizeUITests: XCTestCase {
    private let english = AccessibilityUITestSupport.englishArguments
    private let largeEnglish = AccessibilityUITestSupport.englishArguments + AccessibilityUITestSupport.ax3Arguments

    override func setUpWithError() throws {
        continueAfterFailure = true
    }

    /// Proves the launch argument reaches the app: without it every other test here could pass at the
    /// default size.
    func testTheAx3LaunchArgumentEnlargesText() {
        let standard = launchCarApp(arguments: english, environment: AccessibilityUITestSupport.forceWelcome)
        let standardTitle = standard.staticTexts["welcome_title"]
        XCTAssertTrue(standardTitle.waitForExistence(timeout: 10))
        let standardHeight = standardTitle.frame.height
        standard.terminate()

        let large = launchCarApp(arguments: largeEnglish, environment: AccessibilityUITestSupport.forceWelcome)
        let largeTitle = large.staticTexts["welcome_title"]
        XCTAssertTrue(largeTitle.waitForExistence(timeout: 10))
        XCTAssertGreaterThan(
            largeTitle.frame.height,
            standardHeight * 1.5,
            "AX3 did not reach the app: \(largeTitle.frame.height) vs \(standardHeight) points."
        )
    }

    @MainActor
    func testWelcomeActionsAreReachableAtAx3() throws {
        let app = launchCarApp(arguments: largeEnglish, environment: AccessibilityUITestSupport.forceWelcome)
        XCTAssertTrue(app.staticTexts["welcome_title"].waitForExistence(timeout: 10))
        for identifier in ["welcome_apple", "welcome_google", "welcome_guest"] {
            XCTAssertTrue(scrollUntilHittable(app.buttons[identifier], in: app), "\(identifier) is unreachable at AX3")
        }
        attachAccessibilityEvidence(of: app, named: "ax3-welcome")
        try auditLargeContent(of: app, screen: "welcome")
    }

    @MainActor
    func testVehicleFormFieldsAndSaveAreReachableAtAx3() throws {
        let app = launchCarApp(arguments: largeEnglish, environment: AccessibilityUITestSupport.forceFirstVehicle)
        openVehicleCreationForAccessibility(in: app)
        XCTAssertTrue(app.buttons["save_vehicle"].isHittable, "save_vehicle is unreachable at AX3")
        for identifier in ["vehicle_name", "vehicle_odometer"] {
            XCTAssertTrue(scrollUntilHittable(app.textFields[identifier], in: app), "\(identifier) is unreachable at AX3")
        }
        XCTAssertTrue(scrollUntilHittable(app.buttons["fuel_type_picker"], in: app), "fuel_type_picker is unreachable at AX3")
        attachAccessibilityEvidence(of: app, named: "ax3-vehicle-form")
        try auditLargeContent(of: app, screen: "vehicle-form")
    }

    @MainActor
    func testFuelFormFieldsAndActionsAreReachableAtAx3() throws {
        let app = launchCarApp(arguments: largeEnglish)
        createVehicleAndOpenDetail(in: app, odometer: "1000")
        openFuelEntryForm(in: app)
        XCTAssertTrue(app.buttons["save_fuel_entry"].isHittable, "save_fuel_entry is unreachable at AX3")
        XCTAssertTrue(app.buttons["close_fuel_entry"].isHittable, "close_fuel_entry is unreachable at AX3")
        for identifier in ["fuel_odometer", "fuel_liters", "fuel_price_per_liter", "fuel_notes"] {
            XCTAssertTrue(scrollUntilHittable(app.textFields[identifier], in: app), "\(identifier) is unreachable at AX3")
        }
        XCTAssertTrue(scrollUntilHittable(app.switches["fuel_full_tank"], in: app), "fuel_full_tank is unreachable at AX3")
        attachAccessibilityEvidence(of: app, named: "ax3-fuel-form")
        try auditLargeContent(of: app, screen: "fuel-form")
    }

    /// `R5`: the vehicle list, its detail, the odometer warning and a fuel row are audited at AX3 by the
    /// same local flows the accessibility audit already uses, in their own methods so one failure cannot
    /// hide the rest.
    @MainActor
    func testVehicleListIsReachableAtAx3() throws {
        let app = launchCarApp(arguments: largeEnglish)
        let name = createVehicleAndOpenDetail(in: app, odometer: "142500")
        XCTAssertTrue(returnToTheListFromTheDetail(in: app), "the back affordance is not hittable")
        requireReviewControls(["settings_open", "add_vehicle"], in: app)
        let row = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'vehicle_row_' AND label CONTAINS %@", name))
            .firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: AccessibilityUITestSupport.timeout), "this run's vehicle row is missing")
        XCTAssertTrue(scrollUntilHittable(row, in: app), "this run's vehicle row is unreachable")
        attachAccessibilityEvidence(of: app, named: "ax3-vehicle-list")
        try auditLargeContent(of: app, screen: "vehicle-list")
    }

    @MainActor
    func testVehicleDetailIsReachableAtAx3() throws {
        let app = launchCarApp(arguments: largeEnglish)
        let name = createVehicleAndOpenDetail(in: app, odometer: "142500")
        requireReviewControls(["vehicle_more_actions", "add_fuel_entry"], in: app)
        XCTAssertTrue(
            app.navigationBars[name].exists,
            "the detail must carry this run's vehicle name, saw \(app.navigationBars.debugDescription)"
        )
        attachAccessibilityEvidence(of: app, named: "ax3-vehicle-detail")
        try auditLargeContent(of: app, screen: "vehicle-detail")
    }

    @MainActor
    func testOdometerWarningIsReachableAtAx3() throws {
        let app = launchCarApp(arguments: largeEnglish)
        createVehicleAndOpenDetail(in: app, odometer: "1000")
        openFuelEntryForm(in: app)
        typeFuelEntry(in: app, odometer: "500", liters: "40", price: "2")
        app.buttons["save_fuel_entry"].tap()
        let confirm = app.alerts.buttons["confirm_fuel_odometer_warning"].firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        let alert = app.alerts.firstMatch
        XCTAssertTrue(alert.staticTexts["Odometer looks inconsistent"].exists, "the warning title is missing")
        XCTAssertTrue(
            alert.descendants(matching: .any)
                .matching(NSPredicate(format: "label CONTAINS %@", "not greater than the previous odometer"))
                .firstMatch.exists,
            "the warning body is missing"
        )
        let cancel = alert.buttons["Cancel"].firstMatch
        XCTAssertTrue(cancel.exists, "the warning must offer its own Cancel")
        attachAccessibilityEvidence(of: app, named: "ax3-odometer-warning")
        try auditLargeContent(of: app, screen: "odometer-warning")
    }

    @MainActor
    func testFuelRowIsReachableAtAx3() throws {
        let app = launchCarApp(arguments: largeEnglish)
        createVehicleAndOpenDetail(in: app, odometer: "1000")
        openFuelEntryForm(in: app)
        typeFuelEntry(in: app, odometer: "500", liters: "40", price: "2")
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
        let partial = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'fuel_indicator_' AND identifier ENDSWITH '_partial'"))
            .firstMatch
        XCTAssertTrue(partial.waitForExistence(timeout: AccessibilityUITestSupport.timeout), "the partial badge is missing")
        let inconsistent = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'fuel_indicator_' AND identifier ENDSWITH '_odometer'"))
            .firstMatch
        XCTAssertTrue(inconsistent.exists, "the odometer-inconsistency badge is missing")
        let explanation = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'fuel_explanation_'"))
            .firstMatch
        XCTAssertTrue(scrollUntilHittable(explanation, in: app), "the consumption explanation is unreachable")
        attachAccessibilityEvidence(of: app, named: "ax3-fuel-row")
        try auditLargeContent(of: app, screen: "fuel-row")
    }

    @MainActor
    func testSettingsRowsAreReachableAtAx3() throws {
        let app = launchCarApp(arguments: largeEnglish)
        // Settings lives on the vehicle list, so the list is reached directly rather than by creating
        // a vehicle the test does not need.
        reachTheVehicleListForAccessibility(in: app)
        openSettings(in: app)
        let settingsElements = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'settings_'"))
        let containerTypes: Set<XCUIElement.ElementType> = [.collectionView, .table, .scrollView, .other]
        var reachable: [String: Bool] = [:]
        for _ in 0..<10 {
            for element in settingsElements.allElementsBoundByIndex where element.exists {
                guard !containerTypes.contains(element.elementType) else { continue }
                reachable[element.identifier] = (reachable[element.identifier] ?? false) || element.isHittable
            }
            app.swipeUp()
        }
        let unreachable = reachable.filter { !$0.value }.map(\.key).sorted()
        XCTAssertTrue(unreachable.isEmpty, "Unreachable at AX3: \(unreachable)")
        XCTAssertGreaterThanOrEqual(reachable.count, 4, "Too few settings elements were found: \(reachable.keys.sorted())")
        attachAccessibilityEvidence(of: app, named: "ax3-settings")
        try auditLargeContent(of: app, screen: "settings")
    }

    @MainActor
    func testReauthenticationIsReachableAtAx3() throws {
        let app = launchReviewSurface("reauthentication", arguments: largeEnglish)
        requireReviewControls(["departure_reauth_apple", "departure_reauth_google", "departure_reauth_cancel"], in: app)
        attachAccessibilityEvidence(of: app, named: "ax3-departure-reauthentication")
        try auditLargeContent(of: app, screen: "departure-reauthentication")
    }

    @MainActor
    func testReauthenticationFailureIsReachableAtAx3() throws {
        let app = launchReviewSurface("reauthenticationFailure", arguments: largeEnglish)
        requireReviewControls(["departure_reauth_apple", "departure_reauth_google", "departure_reauth_cancel", "departure_failure"], in: app)
        attachAccessibilityEvidence(of: app, named: "ax3-departure-reauthentication-failure")
        try auditLargeContent(of: app, screen: "departure-reauthentication-failure")
    }

    @MainActor
    func testDeleteAccountIsReachableAtAx3() throws {
        let app = launchReviewSurface("deleteAccount", arguments: largeEnglish)
        requireAlertControls(["Delete account", "Cancel"], in: app)
        attachAccessibilityEvidence(of: app, named: "ax3-departure-delete-account")
        try auditLargeContent(of: app, screen: "departure-delete-account")
    }

    @MainActor
    func testDeleteLocalDataIsReachableAtAx3() throws {
        let app = launchReviewSurface("deleteLocalData", arguments: largeEnglish)
        requireAlertControls(["Delete local data", "Cancel"], in: app)
        attachAccessibilityEvidence(of: app, named: "ax3-departure-delete-local-data")
        try auditLargeContent(of: app, screen: "departure-delete-local-data")
    }

    @MainActor
    func testPendingSyncIsReachableAtAx3() throws {
        let app = launchReviewSurface("pendingSync", arguments: largeEnglish)
        requireAlertControls(["Wait for backup", "Discard and sign out", "Cancel"], in: app)
        attachAccessibilityEvidence(of: app, named: "ax3-departure-pending-sync")
        try auditLargeContent(of: app, screen: "departure-pending-sync")
    }

    @MainActor
    func testConversionIsReachableAtAx3() throws {
        let app = launchReviewSurface("conversion", arguments: largeEnglish)
        requireReviewControls(["settings_link_account_apple", "settings_link_account_google"], in: app)
        attachAccessibilityEvidence(of: app, named: "ax3-conversion")
        try auditLargeContent(of: app, screen: "conversion")
    }

    @MainActor
    func testCollisionIsReachableAtAx3() throws {
        let app = launchReviewSurface("collision", arguments: largeEnglish)
        requireAlertControls(["Replace data", "Cancel"], in: app)
        attachAccessibilityEvidence(of: app, named: "ax3-conversion-collision")
        try auditLargeContent(of: app, screen: "conversion-collision")
    }

}
