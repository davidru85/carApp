import XCTest

final class OnboardingFlowUITests: XCTestCase {
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
}
