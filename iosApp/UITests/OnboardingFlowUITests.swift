import XCTest

final class OnboardingFlowUITests: XCTestCase {
    private let timeout: TimeInterval = 30

    func testIOSWelcomeOffersExactlyAppleGoogleAndGuest() {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launchEnvironment["CARAPP_UI_TEST_FORCE_WELCOME"] = "1"
        app.launch()
        addTeardownBlock { app.terminate() }

        XCTAssertTrue(app.buttons["welcome_apple"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["welcome_google"].exists)
        XCTAssertTrue(app.buttons["welcome_guest"].exists)
        XCTAssertEqual(
            app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'welcome_'")).count,
            3
        )
    }

    /// F-1 first-vehicle creation is mandatory: it exposes no cancellation control and the
    /// interactive dismissal gesture must not escape it either.
    ///
    /// Precondition: no vehicle exists yet. This class runs before `VehicleAndFuelFlowUITests`
    /// creates one, so it holds on the fresh simulator that CI and a local erased device provide.
    func testFirstRunVehicleFormResistsInteractiveDismissal() {
        let app = launchOnFirstVehicleCreation()

        let vehicleNameField = app.textFields["vehicle_name"]
        XCTAssertFalse(app.buttons["cancel_vehicle"].exists, "First-run creation offers no cancellation control")

        app.swipeDown(velocity: .fast)
        app.swipeDown(velocity: .fast)

        XCTAssertTrue(
            vehicleNameField.waitForExistence(timeout: 5),
            "The mandatory first-run form must stay presented after a swipe-to-dismiss gesture"
        )
        XCTAssertFalse(app.buttons["add_vehicle"].isHittable, "The vehicle list must stay covered by the form")
    }

    func testSavingTheFirstVehicleOpensItsDetail() {
        let app = launchOnFirstVehicleCreation()
        let vehicleName = "AAA-First-\(Int(Date().timeIntervalSince1970))"

        let vehicleNameField = app.textFields["vehicle_name"]
        vehicleNameField.tap()
        vehicleNameField.typeText(vehicleName)

        let odometerField = app.textFields["vehicle_odometer"]
        odometerField.tap()
        odometerField.typeText("410")

        app.buttons["save_vehicle"].tap()

        XCTAssertTrue(
            app.navigationBars[vehicleName].waitForExistence(timeout: timeout),
            "Saving the first vehicle must open its detail titled with that vehicle, per SPECIFICATION.md F-2"
        )
        XCTAssertTrue(
            app.staticTexts["first_fuel_invitation"].waitForExistence(timeout: timeout),
            "The first vehicle detail must invite the first fuel entry"
        )
    }

    /// The first-run state cannot be reproduced from data alone: UI tests cannot clear the
    /// application container and the unit-test target writes into the same one. The Debug-only
    /// `CARAPP_UI_TEST_FORCE_FIRST_VEHICLE` seam presents mandatory creation deterministically.
    private func launchOnFirstVehicleCreation() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launchEnvironment["CARAPP_UI_TEST_FORCE_FIRST_VEHICLE"] = "1"
        app.launch()
        addTeardownBlock { app.terminate() }

        let guestButton = app.buttons["welcome_guest"]
        if guestButton.waitForExistence(timeout: 10), guestButton.isHittable {
            guestButton.tap()
        }

        XCTAssertTrue(
            app.textFields["vehicle_name"].waitForExistence(timeout: timeout),
            "Mandatory first-vehicle creation must be presented"
        )
        return app
    }
}
