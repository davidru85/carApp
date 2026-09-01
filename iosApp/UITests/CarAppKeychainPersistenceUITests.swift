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

        let diagnosticsButton = app.buttons["diagnostics_button"]
        XCTAssertTrue(diagnosticsButton.waitForExistence(timeout: timeout))
        diagnosticsButton.tap()

        let anonymousButton = app.buttons["Continue without an account"]
        XCTAssertTrue(anonymousButton.waitForExistence(timeout: timeout))
        anonymousButton.tap()
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
}
