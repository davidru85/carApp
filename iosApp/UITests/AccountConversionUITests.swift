import XCTest

/// `E4-05`: an anonymous session sees the anonymous-only link-account row with Apple and Google.
///
/// The guest path reaches an anonymous session only when App Check admits the simulator through a
/// registered debug token; without it the session falls back to `LOCAL`, where `D-224` hides the
/// row. The test therefore skips without the token, exactly like
/// `CarAppKeychainPersistenceUITests`, and the owner runs it with the token.
final class AccountConversionUITests: XCTestCase {
    private let timeout: TimeInterval = 30

    /// The vehicle-list control that presents `SettingsView`, as `E4-01` identified it.
    private let settingsEntryIdentifier = "settings_open"

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testAnAnonymousSessionSeesTheLinkAccountRowWithAppleAndGoogle() throws {
        guard let debugToken = ProcessInfo.processInfo.environment["CARAPP_IOS_APP_CHECK_DEBUG_TOKEN"],
              !debugToken.isEmpty else {
            throw XCTSkip("A registered simulator-only App Check debug token is required for an anonymous session.")
        }
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launchEnvironment["AppCheckDebugToken"] = debugToken
        app.launch()
        addTeardownBlock { app.terminate() }

        reachVehicleList(in: app)
        app.buttons[settingsEntryIdentifier].tap()

        let row = app.descendants(matching: .any)["settings_link_account_row"]
        let apple = app.buttons["settings_link_account_apple"]
        let google = app.buttons["settings_link_account_google"]
        XCTAssertTrue(
            scrollUntilExists(row, in: app),
            "An anonymous session must see the link-account row; a LOCAL fallback means App Check rejected the debug token"
        )
        XCTAssertTrue(scrollUntilExists(apple, in: app), "iOS offers Apple whenever it offers Google (F-1)")
        XCTAssertTrue(scrollUntilExists(google, in: app))
        XCTAssertEqual(
            app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'settings_link_account_'")).count,
            2
        )
    }

    /// Reaches the vehicle list through the guest path. A fresh simulator first shows mandatory
    /// first-vehicle creation, which routes to the new vehicle's detail (F-2); the test then returns
    /// to the list, where the Settings entry lives.
    private func reachVehicleList(in app: XCUIApplication) {
        let settingsEntry = app.buttons[settingsEntryIdentifier]
        let firstFuelInvitation = app.staticTexts["first_fuel_invitation"]
        let vehicleNameField = app.textFields["vehicle_name"]
        let odometerField = app.textFields["vehicle_odometer"]
        let saveVehicleButton = app.buttons["save_vehicle"]
        var createdFirstVehicle = false

        _ = waitForOnboarding(
            in: app,
            destination: "the vehicle list or the first vehicle's detail",
            isComplete: { settingsEntry.exists || firstFuelInvitation.exists },
            handleVehicleForm: {
                guard !createdFirstVehicle, vehicleNameField.exists else {
                    return false
                }
                createdFirstVehicle = true
                vehicleNameField.tap()
                vehicleNameField.typeText("Conversion surface vehicle")
                odometerField.tap()
                odometerField.typeText("410")
                XCTAssertTrue(saveVehicleButton.isEnabled)
                saveVehicleButton.tap()
                return true
            }
        )
        if !settingsEntry.exists {
            app.navigationBars.buttons.element(boundBy: 0).tap()
        }
        XCTAssertTrue(settingsEntry.waitForExistence(timeout: timeout))
    }

    /// A SwiftUI list creates rows lazily, so a row below the fold exists only after scrolling.
    private func scrollUntilExists(_ element: XCUIElement, in app: XCUIApplication) -> Bool {
        for _ in 0..<8 {
            if element.waitForExistence(timeout: 2) {
                return true
            }
            app.swipeUp()
        }
        return element.waitForExistence(timeout: 2)
    }
}
