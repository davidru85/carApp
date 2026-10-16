import XCTest

/// Launch arguments and navigation steps shared by the `E4-02` accessibility, large-content and
/// Spanish UI tests. Navigation reuses the onboarding waiter of `OnboardingWait.swift`.
enum AccessibilityUITestSupport {
    static let timeout: TimeInterval = 30
    static let englishArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
    static let spanishArguments = ["-AppleLanguages", "(es)", "-AppleLocale", "es_ES"]
    /// `D-218`: AX3, the smallest category at or above 200 % of Body.
    static let ax3Arguments = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXL"]
    static let forceWelcome = ["CARAPP_UI_TEST_FORCE_WELCOME": "1"]
    static let forceFirstVehicle = ["CARAPP_UI_TEST_FORCE_FIRST_VEHICLE": "1"]
    static let forceDepartureFailure = ["CARAPP_UI_TEST_FORCE_DEPARTURE_FAILURE": "1"]
    /// The `E4-01` identifier of the vehicle-list control that opens Settings.
    static let settingsEntryIdentifier = "settings_open"
}

/// `D-217`: one reviewed exception to the accessibility audit.
@available(iOS 17.0, *)
struct AccessibilityAuditAllowance {
    let screen: String
    let auditType: XCUIAccessibilityAuditType
    let elementIdentifier: String
    let reason: String
}

/// `D-217`: the minimal list of audit issues the app cannot fix itself. Every entry is a reviewed
/// change recorded in `docs/handoffs/E4-02.md`; an issue that matches no entry fails the test.
@available(iOS 17.0, *)
enum AccessibilityAuditAllowlist {
    static let allowances: [AccessibilityAuditAllowance] = []

    static func shouldIgnore(_ issue: XCUIAccessibilityAuditIssue, screen: String) -> Bool {
        let identifier = issue.element?.identifier ?? ""
        let ignored = allowances.contains { allowance in
            allowance.screen == screen
                && issue.auditType.contains(allowance.auditType)
                && allowance.elementIdentifier == identifier
        }
        print(
            "[a11y-audit] screen=\(screen) type=\(issue.auditType.rawValue) id=\(identifier) "
                + "label=\(issue.element?.label ?? "") ignored=\(ignored) issue=\(issue.compactDescription)"
        )
        return ignored
    }
}

extension XCTestCase {
    func launchCarApp(arguments: [String], environment: [String: String] = [:]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += arguments
        for (key, value) in environment {
            app.launchEnvironment[key] = value
        }
        app.launch()
        addTeardownBlock { app.terminate() }
        return app
    }

    /// Runs the `D-217` audit of every type, or skips explicitly on a pre-iOS 17 runtime.
    @MainActor
    func auditAccessibility(of app: XCUIApplication, screen: String) throws {
        if #available(iOS 17.0, *) {
            try app.performAccessibilityAudit { issue in
                AccessibilityAuditAllowlist.shouldIgnore(issue, screen: screen)
            }
        } else {
            throw XCTSkip("performAccessibilityAudit() needs an iOS 17 runtime; the deployment target is iOS 16.")
        }
    }

    /// The large-content audit of `D-218`: Dynamic Type support and clipped text only.
    @MainActor
    func auditLargeContent(of app: XCUIApplication, screen: String) throws {
        if #available(iOS 17.0, *) {
            try app.performAccessibilityAudit(for: [.dynamicType, .textClipped]) { issue in
                AccessibilityAuditAllowlist.shouldIgnore(issue, screen: screen)
            }
        } else {
            throw XCTSkip("performAccessibilityAudit() needs an iOS 17 runtime; the deployment target is iOS 16.")
        }
    }

    /// Keeps a screenshot and the accessibility hierarchy in the result bundle as handoff evidence.
    func attachAccessibilityEvidence(of app: XCUIApplication, named name: String) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "\(name)-screenshot"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        let hierarchy = XCTAttachment(string: app.debugDescription)
        hierarchy.name = "\(name)-hierarchy"
        hierarchy.lifetime = .keepAlways
        add(hierarchy)
    }

    func openVehicleCreationForAccessibility(in app: XCUIApplication) {
        let vehicleNameField = app.textFields["vehicle_name"]
        waitForOnboarding(in: app, destination: "vehicle creation", isComplete: { vehicleNameField.exists })
    }

    /// Creates a vehicle and lands on its detail, whether this is the first-run form (which routes to
    /// the detail itself) or a later creation (which returns to the list).
    @discardableResult
    func createVehicleAndOpenDetail(in app: XCUIApplication, odometer: String) -> String {
        openVehicleCreationForAccessibility(in: app)
        let name = "AAA-A11y-\(Int(Date().timeIntervalSince1970))"
        let nameField = app.textFields["vehicle_name"]
        nameField.tap()
        nameField.typeText(name)
        let odometerField = app.textFields["vehicle_odometer"]
        odometerField.tap()
        odometerField.typeText(odometer)
        let save = app.buttons["save_vehicle"]
        save.tap()
        XCTAssertTrue(save.waitForNonExistence(timeout: AccessibilityUITestSupport.timeout))

        let addFuelEntry = app.buttons["add_fuel_entry"]
        if !addFuelEntry.waitForExistence(timeout: 3) {
            let row = app.staticTexts[name]
            if !row.waitForExistence(timeout: 2) {
                app.swipeDown()
            }
            XCTAssertTrue(row.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
            row.tap()
        }
        XCTAssertTrue(addFuelEntry.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        return name
    }

    func openFuelEntryForm(in app: XCUIApplication) {
        app.buttons["add_fuel_entry"].tap()
        XCTAssertTrue(app.textFields["fuel_odometer"].waitForExistence(timeout: AccessibilityUITestSupport.timeout))
    }

    func typeFuelEntry(in app: XCUIApplication, odometer: String, liters: String, price: String) {
        let odometerField = app.textFields["fuel_odometer"]
        odometerField.tap()
        if let current = odometerField.value as? String, !current.isEmpty {
            odometerField.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
        }
        odometerField.typeText(odometer)
        let litersField = app.textFields["fuel_liters"]
        litersField.tap()
        litersField.typeText(liters)
        let priceField = app.textFields["fuel_price_per_liter"]
        priceField.tap()
        priceField.typeText(price)
    }

    func openSettings(in app: XCUIApplication) {
        let entry = app.buttons[AccessibilityUITestSupport.settingsEntryIdentifier]
        XCTAssertTrue(entry.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        entry.tap()
    }

    @discardableResult
    func scrollUntilHittable(_ element: XCUIElement, in app: XCUIApplication, maxSwipes: Int = 8) -> Bool {
        var swipes = 0
        while !(element.exists && element.isHittable) && swipes < maxSwipes {
            app.swipeUp()
            swipes += 1
        }
        return element.exists && element.isHittable
    }
}
