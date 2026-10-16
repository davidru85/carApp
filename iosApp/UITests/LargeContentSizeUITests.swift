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

    @MainActor
    func testSettingsRowsAreReachableAtAx3() throws {
        let app = launchCarApp(arguments: largeEnglish)
        createVehicleAndOpenDetail(in: app, odometer: "1000")
        app.navigationBars.buttons.element(boundBy: 0).tap()
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
}
