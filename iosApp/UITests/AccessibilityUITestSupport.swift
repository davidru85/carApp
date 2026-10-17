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
    /// Leaves the vehicle detail and returns to the vehicle list. A navigation bar can hold more than
    /// one button, so the back affordance is resolved by identifier rather than by position.
    func returnToTheListFromTheDetail(in app: XCUIApplication) -> Bool {
        // A navigation bar can hold more than one button and the Debug diagnostics item comes first, so
        // the back affordance is resolved by what it is not: a button that is not one of the controls
        // this app gives an identifier to, sitting in the leading half of the bar.
        let bar = app.navigationBars.element(boundBy: 0)
        XCTAssertTrue(bar.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        // The system calls the back affordance `BackButton` on this runtime; a button with no
        // identifier of the app's own is the fallback, and the app's own controls are excluded.
        let knownIdentifiers = ["diagnostics_button", "vehicle_more_actions", "add_vehicle", "settings_open"]
        let candidates =
            [bar.buttons["BackButton"].firstMatch, bar.buttons["Back"].firstMatch]
            + bar.buttons.allElementsBoundByIndex.filter { button in
                !knownIdentifiers.contains(button.identifier) && button.identifier.isEmpty
            }
        for back in candidates where back.exists {
            if back.isHittable || scrollUntilHittable(back, in: app) {
                back.tap()
                return true
            }
        }
        return false
    }
}

/// A vehicle name that is unique across a whole suite run: two tests can run inside the same second,
/// and a repeated name is refused by the duplicate-name validation, which would leave the form open.
func uniqueVehicleName() -> String { "AAA-A11y-\(UUID().uuidString.prefix(8))" }

extension XCTestCase {
    /// Taps a point away from the keyboard so a covered action becomes hittable again. At AX3 the
    /// vehicle form is taller than the screen and the numeric keyboard covers its save button.
    func tapOutsideTheKeyboard(_ app: XCUIApplication) -> Bool {
        let field = app.textFields["vehicle_name"]
        guard field.exists, app.keyboards.firstMatch.exists else { return true }
        let start = field.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        let above = start.withOffset(CGVector(dx: 0, dy: -120))
        above.tap()
        return !app.keyboards.firstMatch.exists
    }
}

extension XCUIElement {
    /// Taps when the element exists; reports whether the tap happened, so a caller can branch on it.
    @discardableResult
    func tapIfExists() -> Bool {
        guard exists else { return false }
        tap()
        return true
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

    /// Reaches the vehicle list from wherever onboarding stands, creating the first vehicle when F-1
    /// asks for it. The list may already hold vehicles left by an earlier test, in which case nothing
    /// is created.
    func reachTheVehicleListForAccessibility(in app: XCUIApplication) {
        let vehicleNameField = app.textFields["vehicle_name"]
        let addVehicle = app.buttons["add_vehicle"]
        var createdTheFirstVehicle = false
        waitForOnboarding(
            in: app,
            destination: "the vehicle list",
            isComplete: { addVehicle.exists && !vehicleNameField.exists },
            handleVehicleForm: {
                guard !createdTheFirstVehicle, vehicleNameField.exists else { return false }
                createdTheFirstVehicle = true
                self.fillTheVehicleForm(name: uniqueVehicleName(), odometer: "1000", in: app)
                // A first vehicle lands on its detail; the list is reached by the back affordance.
                _ = self.returnToTheListFromTheDetail(in: app)
                return true
            }
        )
        XCTAssertTrue(addVehicle.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
    }

    func openSettings(in app: XCUIApplication) {
        let settingsButton = app.buttons[AccessibilityUITestSupport.settingsEntryIdentifier]
        XCTAssertTrue(settingsButton.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        XCTAssertTrue(scrollUntilHittable(settingsButton, in: app), "the Settings entry is unreachable")
        settingsButton.tap()
        // The screen is proved by the back affordance of its navigation bar rather than by its title,
        // which is localized ("Settings" or "Ajustes"), or by the version row, which sits below the fold
        // on a shorter device. The bar's back button exists on both hosts' layouts.
        let backButton = app.navigationBars.buttons.element(boundBy: 0)
        let settingsIsUp =
            backButton.waitForExistence(timeout: AccessibilityUITestSupport.timeout)
            && app.buttons["settings_currency_picker"].waitForExistence(timeout: AccessibilityUITestSupport.timeout)
        XCTAssertTrue(settingsIsUp, "Settings did not open")
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

    /// Creates a vehicle named uniquely for this run and lands on its detail. The list may already
    /// hold vehicles left by an earlier test in the same suite and F-1 may route an empty list to the
    /// creation form, so both routes are handled and the vehicle is always the one this call created.
    @discardableResult
    func createVehicleAndOpenDetail(in app: XCUIApplication, odometer: String) -> String {
        let name = uniqueVehicleName()
        let vehicleNameField = app.textFields["vehicle_name"]
        let odometerField = app.textFields["vehicle_odometer"]
        let saveVehicleButton = app.buttons["save_vehicle"]
        var createdTheFirstVehicle = false

        // A first vehicle lands on its detail and a later one on the list, so the waiter waits for the
        // form to be gone; the destination is resolved afterwards.
        waitForOnboarding(
            in: app,
            destination: "the vehicle list",
            isComplete: { !vehicleNameField.exists && (app.buttons["add_fuel_entry"].exists || app.buttons["add_vehicle"].exists) },
            handleVehicleForm: {
                guard !createdTheFirstVehicle, vehicleNameField.exists else { return false }
                createdTheFirstVehicle = true
                self.fillTheVehicleForm(name: name, odometer: odometer, in: app)
                return true
            }
        )
        if app.buttons["add_fuel_entry"].exists {
            return name
        }

        // This run's own vehicle is created when the list did not already carry it, which keeps the
        // test independent of whatever an earlier test left behind.
        // The list row is matched by the name in its own label, never by position, so an earlier
        // test's vehicles cannot be mistaken for this one.
        let rowMatchingThisVehicle = NSPredicate(
            format: "identifier BEGINSWITH 'vehicle_row_' AND label CONTAINS %@",
            name
        )
        let ownRow = app.descendants(matching: .any).matching(rowMatchingThisVehicle).firstMatch
        if !ownRow.waitForExistence(timeout: 3) {
            _ = self.returnToTheListFromTheDetail(in: app)
            let addVehicle = app.buttons["add_vehicle"]
            XCTAssertTrue(addVehicle.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
            XCTAssertTrue(self.scrollUntilHittable(addVehicle, in: app), "add_vehicle is unreachable")
            addVehicle.tap()
            XCTAssertTrue(vehicleNameField.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
            vehicleNameField.tap()
            vehicleNameField.typeText(name)
            odometerField.tap()
            odometerField.typeText(odometer)
            app.navigationBars.element(boundBy: 0).tap()
            _ = app.keyboards.firstMatch.waitForNonExistence(timeout: 3)
            XCTAssertTrue(self.scrollUntilHittable(saveVehicleButton, in: app), "save_vehicle is unreachable")
            saveVehicleButton.tap()
            XCTAssertTrue(saveVehicleButton.waitForNonExistence(timeout: AccessibilityUITestSupport.timeout))
            _ = self.returnToTheListFromTheDetail(in: app)
        }

        // The list may be scrolled away from this row after the creation flow, in either direction:
        // earlier tests in the same run leave their own vehicles behind, and at AX3 only a few rows
        // fit on screen. Both directions are swept, bounded, before the row is asserted.
        if !ownRow.exists {
            for _ in 0..<4 where !ownRow.exists { app.swipeDown() }
            for _ in 0..<8 where !ownRow.exists { app.swipeUp() }
        }
        XCTAssertTrue(ownRow.waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        XCTAssertTrue(self.scrollUntilHittable(ownRow, in: app), "the vehicle row is not hittable")
        ownRow.tap()
        XCTAssertTrue(app.buttons["add_fuel_entry"].waitForExistence(timeout: AccessibilityUITestSupport.timeout))
        return name
    }

    /// Fills the open vehicle form up to (and including) the save tap. The keypad has no Done key, so
    /// the keyboard is dismissed by tapping the navigation bar, which lies outside it.
    func fillTheVehicleForm(name: String, odometer: String, in app: XCUIApplication) {
        let vehicleNameField = app.textFields["vehicle_name"]
        let odometerField = app.textFields["vehicle_odometer"]
        let saveVehicleButton = app.buttons["save_vehicle"]
        vehicleNameField.tap()
        vehicleNameField.typeText(name)
        odometerField.tap()
        odometerField.typeText(odometer)
        app.navigationBars.element(boundBy: 0).tap()
        _ = app.keyboards.firstMatch.waitForNonExistence(timeout: 3)
        XCTAssertTrue(scrollUntilHittable(saveVehicleButton, in: app), "save_vehicle is unreachable")
        saveVehicleButton.tap()
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
