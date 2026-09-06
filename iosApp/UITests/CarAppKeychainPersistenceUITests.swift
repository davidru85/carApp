import XCTest

final class CarAppKeychainPersistenceUITests: XCTestCase {
    private let timeout: TimeInterval = 30

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testAnonymousSessionSurvivesARealProcessRestart() throws {
        guard let debugToken = ProcessInfo.processInfo.environment["CARAPP_IOS_APP_CHECK_DEBUG_TOKEN"],
              !debugToken.isEmpty else {
            throw XCTSkip("A registered simulator-only App Check debug token is required.")
        }
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launchEnvironment["AppCheckDebugToken"] = debugToken

        app.launch()
        addTeardownBlock { app.terminate() }

        reachAnonymousMainShell(in: app)
        let diagnosticsButton = app.buttons["diagnostics_button"]
        XCTAssertTrue(diagnosticsButton.waitForExistence(timeout: timeout))
        diagnosticsButton.tap()

        XCTAssertTrue(
            app.staticTexts["Session: Anonymous backup active"]
                .waitForExistence(timeout: timeout)
        )

        app.terminate()
        XCTAssertEqual(app.state, .notRunning)
        app.launch()

        let diagnosticsButtonAfterRestart = app.buttons["diagnostics_button"]
        XCTAssertTrue(diagnosticsButtonAfterRestart.waitForExistence(timeout: timeout))
        diagnosticsButtonAfterRestart.tap()

        XCTAssertTrue(
            app.staticTexts["Session: Anonymous backup active"]
                .waitForExistence(timeout: timeout)
        )
        XCTAssertFalse(app.buttons["Continue without an account"].exists)
    }

    private func reachAnonymousMainShell(in app: XCUIApplication) {
        let guestButton = app.buttons["welcome_guest"]
        let diagnosticsButton = app.buttons["diagnostics_button"]
        let vehicleNameField = app.textFields["vehicle_name"]
        let saveVehicleButton = app.buttons["save_vehicle"]
        let deadline = Date().addingTimeInterval(timeout)
        var startedGuestSession = false
        var createdFirstVehicle = false

        while Date() < deadline {
            if diagnosticsButton.exists {
                return
            }
            if !startedGuestSession, guestButton.exists, guestButton.isHittable {
                guestButton.tap()
                startedGuestSession = true
            } else if !createdFirstVehicle, vehicleNameField.exists {
                vehicleNameField.tap()
                vehicleNameField.typeText("Keychain persistence vehicle")
                XCTAssertTrue(saveVehicleButton.isEnabled)
                saveVehicleButton.tap()
                createdFirstVehicle = true
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        }
        XCTFail("Onboarding did not reach the authenticated main shell before the timeout")
    }
}
