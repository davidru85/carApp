import XCTest

/// `E4-03` (`D-251`, `D-253`): cold start to first content on the device, 10 iterations.
///
/// `XCTApplicationLaunchMetric(waitUntilResponsive: false)` ends when the first frame is drawn and every
/// MetricKit extended launch task has finished; the app finishes its only task at first content. The
/// waits inside the blocks only keep each iteration alive until then.
final class LaunchPerformanceUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// The gate: a returning user's populated vehicle list (seeded and adopted history, offline).
    func testReturningUserLaunchToPopulatedVehicleList() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(
            app.staticTexts[PerformancePlan.vehicleName].waitForExistence(timeout: PerformancePlan.contentTimeout),
            "Run the seeding and adoption steps of docs/runbooks/performance-measurement.md first"
        )

        let options = XCTMeasureOptions()
        options.iterationCount = PerformancePlan.coldStartIterations
        measure(metrics: [XCTApplicationLaunchMetric(waitUntilResponsive: false)], options: options) {
            app.launch()
            _ = app.staticTexts[PerformancePlan.vehicleName].waitForExistence(timeout: PerformancePlan.contentTimeout)
        }
        app.terminate()
    }

    /// The second figure: the welcome screen of a signed-out install. Run it before seeding.
    func testSignedOutLaunchToWelcomeScreen() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(
            app.buttons["welcome_guest"].waitForExistence(timeout: PerformancePlan.contentTimeout),
            "Run this test on a fresh, signed-out install, before seeding"
        )

        let options = XCTMeasureOptions()
        options.iterationCount = PerformancePlan.coldStartIterations
        measure(metrics: [XCTApplicationLaunchMetric(waitUntilResponsive: false)], options: options) {
            app.launch()
            _ = app.buttons["welcome_guest"].waitForExistence(timeout: PerformancePlan.contentTimeout)
        }
        app.terminate()
    }
}
