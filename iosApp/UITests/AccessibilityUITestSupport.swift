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
    /// Some elements carry no identifier of their own, so an allowance matches them by their label.
    var elementLabel: String = ""
    /// Some elements embed the entity id of the row under test, so an allowance matches a prefix.
    var matchesByIdentifierPrefix: Bool = false
    /// Some labels embed a value the test computes, so an allowance matches a label prefix.
    var elementLabelPrefix: String = ""
    let reason: String

    func matches(_ issue: XCUIAccessibilityAuditIssue, screen: String) -> Bool {
        let identifier = issue.element?.identifier ?? ""
        let identifierMatches =
            matchesByIdentifierPrefix ? identifier.hasPrefix(elementIdentifier) : identifier == elementIdentifier
        let label = issue.element?.label ?? ""
        let labelMatches =
            elementLabelPrefix.isEmpty
            ? (elementLabel.isEmpty || label == elementLabel)
            : label.hasPrefix(elementLabelPrefix)
        return self.screen == screen
            && issue.auditType.contains(auditType)
            && identifierMatches
            && labelMatches
    }
}

/// `D-217`: the reviewed list of audit issues the app cannot fix itself. The owner approved these
/// documented exceptions on 2026-10-17 (the `E402H-31` S-13 decision); every entry names the concrete
/// cause, and the whole list is recorded in `docs/handoffs/E4-02.md`. An issue that matches no entry
/// fails the test.
@available(iOS 17.0, *)
enum AccessibilityAuditAllowlist {
    static let allowances: [AccessibilityAuditAllowance] = [
        // Pre-approved by the `E402H-28` spec: the AuthenticationServices system button.
        AccessibilityAuditAllowance(
            screen: "welcome",
            auditType: .dynamicType,
            elementIdentifier: "welcome_apple",
            reason: "SignInWithAppleButton is rendered by AuthenticationServices; its title does not follow Dynamic Type and Apple requires the system button."
        ),
        AccessibilityAuditAllowance(
            screen: "welcome",
            auditType: .textClipped,
            elementIdentifier: "welcome_apple",
            reason: "SignInWithAppleButton is rendered by AuthenticationServices at the fixed 50-point height the guidelines recommend."
        ),
        // `E402H-31`, owner decision 2026-10-17.
        AccessibilityAuditAllowance(
            screen: "vehicle-form",
            auditType: .dynamicType,
            elementIdentifier: "save_vehicle",
            reason: "`save_vehicle` is the SwiftUI navigation-bar button of the vehicle form; the system renders its label and the app cannot change a bar button's type style."
        ),
        AccessibilityAuditAllowance(
            screen: "vehicle-form",
            auditType: .textClipped,
            elementIdentifier: "vehicle_odometer",
            reason: "`vehicle_odometer` is a SwiftUI `Form` `TextField`; the system renders and clips the field's label at accessibility text sizes, and the app owns only its value."
        ),
        AccessibilityAuditAllowance(
            screen: "vehicle-form",
            auditType: .textClipped,
            elementIdentifier: "vehicle_brand",
            reason: "`vehicle_brand` is a SwiftUI `Form` `TextField`; the system renders and clips the field's label at accessibility text sizes, and the app owns only its value."
        ),
        AccessibilityAuditAllowance(
            screen: "vehicle-form",
            auditType: .textClipped,
            elementIdentifier: "vehicle_model",
            reason: "`vehicle_model` is a SwiftUI `Form` `TextField`; the system renders and clips the field's label at accessibility text sizes, and the app owns only its value."
        ),
        AccessibilityAuditAllowance(
            screen: "fuel-form",
            auditType: .dynamicType,
            elementIdentifier: "",
            elementLabel: "Calculated total",
            reason: "`Calculated total` is a SwiftUI `Form` row label; the system renders it and the app cannot change a `Form` row label's type style."
        ),
        AccessibilityAuditAllowance(
            screen: "fuel-form",
            auditType: .dynamicType,
            elementIdentifier: "close_fuel_entry",
            reason: "`close_fuel_entry` is the SwiftUI navigation-bar button of the fuel form; the system renders its label and the app cannot change a bar button's type style."
        ),
        AccessibilityAuditAllowance(
            screen: "fuel-form",
            auditType: .dynamicType,
            elementIdentifier: "fuel_derived_value",
            reason: "`fuel_derived_value` is the value `Text` of a SwiftUI `Form` row; the row is system-rendered and applies its own type scale to the value."
        ),
        AccessibilityAuditAllowance(
            screen: "fuel-form",
            auditType: .dynamicType,
            elementIdentifier: "",
            elementLabel: "Missed refuels",
            reason: "`Missed refuels` is a SwiftUI `Form` `Toggle` label; the system renders the label and the app cannot change a `Form` row label's type style."
        ),
        AccessibilityAuditAllowance(
            screen: "fuel-form",
            auditType: .dynamicType,
            elementIdentifier: "save_fuel_entry",
            reason: "`save_fuel_entry` is the SwiftUI navigation-bar button of the fuel form; the system renders its label and the app cannot change a bar button's type style."
        ),
        AccessibilityAuditAllowance(
            screen: "fuel-form",
            auditType: .dynamicType,
            elementIdentifier: "",
            elementLabel: "Use this when an earlier refuel was not recorded.",
            reason: "The `Missed refuels` help text is the footer of a SwiftUI `Form` row; the system renders it and the app cannot change a `Form` footer's type style."
        ),
        AccessibilityAuditAllowance(
            screen: "fuel-form",
            auditType: .textClipped,
            elementIdentifier: "fuel_odometer",
            reason: "`fuel_odometer` is a SwiftUI `Form` `TextField`; the system renders and clips the field's label at accessibility text sizes, and the app owns only its value."
        ),
        AccessibilityAuditAllowance(
            screen: "fuel-row",
            auditType: .hitRegion,
            elementIdentifier: "fuel_explanation_",
            matchesByIdentifierPrefix: true,
            reason: "The fuel row is one accessibility container (`.accessibilityElement(children: .contain)`), so the explanation is an inner `Text` node and not a target; there is no control of the app's own whose hit area could be enlarged."
        ),
        AccessibilityAuditAllowance(
            screen: "fuel-row",
            auditType: .hitRegion,
            elementIdentifier: "",
            elementLabelPrefix: "500 km",
            reason: "The fuel row is one accessibility container (`.accessibilityElement(children: .contain)`), so the facts string is an inner `Text` node and not a target; there is no control of the app's own whose hit area could be enlarged."
        ),
        AccessibilityAuditAllowance(
            screen: "fuel-row",
            auditType: .hitRegion,
            elementIdentifier: "fuel_indicator_",
            matchesByIdentifierPrefix: true,
            reason: "The fuel row is one accessibility container (`.accessibilityElement(children: .contain)`), so the badge is an inner `Text` node and not a target; there is no control of the app's own whose hit area could be enlarged."
        ),
        AccessibilityAuditAllowance(
            screen: "fuel-row",
            auditType: .contrast,
            elementIdentifier: "",
            elementLabel: "New refuel",
            reason: "The `New refuel` toolbar control is a SwiftUI navigation-bar item; the system draws its label with the accent colour on the bar, which the app cannot set."
        ),
        AccessibilityAuditAllowance(
            screen: "fuel-row",
            auditType: .dynamicType,
            elementIdentifier: "fuel_indicator_",
            matchesByIdentifierPrefix: true,
            reason: "A fuel-row indicator badge is a `Text` inside a SwiftUI `List` row; the row is system-rendered and applies its own type scale to the badge."
        ),
        AccessibilityAuditAllowance(
            screen: "odometer-warning",
            auditType: .elementDetection,
            elementIdentifier: "",
            reason: "The odometer warning is a SwiftUI `alert`; the system renders an unlabeled container element the app neither creates nor can label."
        ),
        AccessibilityAuditAllowance(
            screen: "odometer-warning",
            auditType: .dynamicType,
            elementIdentifier: "",
            elementLabel: "This value is not greater than the previous odometer. Save it only if it is correct.",
            reason: "The odometer warning is a SwiftUI `alert`; the system renders its body and the app cannot change an alert's type style."
        ),
        AccessibilityAuditAllowance(
            screen: "odometer-warning",
            auditType: .dynamicType,
            elementIdentifier: "",
            elementLabel: "Odometer looks inconsistent",
            reason: "The odometer warning is a SwiftUI `alert`; the system renders its title and the app cannot change an alert's type style."
        ),
        AccessibilityAuditAllowance(
            screen: "odometer-warning",
            auditType: .dynamicType,
            elementIdentifier: "",
            elementLabel: "",
            reason: "The odometer warning is a SwiftUI `alert`; the system renders its buttons and the app cannot change an alert button's type style."
        ),
        AccessibilityAuditAllowance(
            screen: "settings",
            auditType: .dynamicType,
            elementIdentifier: "settings_delete_local_data",
            reason: "`settings_delete_local_data` is a SwiftUI `Button` inside a `Form`; the system renders its label and the app cannot change a `Form` button label's type style."
        ),
        AccessibilityAuditAllowance(
            screen: "settings",
            auditType: .contrast,
            elementIdentifier: "settings_delete_local_data",
            reason: "`settings_delete_local_data` is a destructive SwiftUI `Button` inside a `Form`; the system draws its red label over the grouped background and the app cannot set that label's colour."
        ),
        AccessibilityAuditAllowance(
            screen: "departure",
            auditType: .contrast,
            elementIdentifier: "departure_retry",
            reason: "In dark appearance the system draws the prominent button's own fill and label over the screen background, and the app cannot set both sides of that pair."
        ),
        AccessibilityAuditAllowance(
            screen: "odometer-warning",
            auditType: .contrast,
            elementIdentifier: "",
            elementLabel: "Odometer looks inconsistent",
            reason: "In dark appearance the SwiftUI `alert` draws its own title colour over its own material background, and the app cannot change either."
        ),
        AccessibilityAuditAllowance(
            screen: "vehicle-detail",
            auditType: .contrast,
            elementIdentifier: "",
            elementLabel: "New refuel",
            reason: "The `New refuel` toolbar control is a SwiftUI navigation-bar item; the system draws its label with the accent colour on the bar, which the app cannot set."
        ),
    ]

    static func shouldIgnore(_ issue: XCUIAccessibilityAuditIssue, screen: String) -> Bool {
        let identifier = issue.element?.identifier ?? ""
        let ignored = allowances.contains { allowance in
            allowance.matches(issue, screen: screen)
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
