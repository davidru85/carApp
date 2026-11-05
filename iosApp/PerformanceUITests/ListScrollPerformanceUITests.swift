import XCTest

/// `E4-03` (`D-253`): hitches during a scripted fling across the 1,000-entry list.
///
/// At 60 Hz every hitch keeps the previous frame on screen for at least 33.3 ms, so a hitch count of zero
/// is exactly "0 frames over 32 ms". Each iteration measures the flings to the end, then reopens the vehicle
/// to return to the top: on iOS 27 a block that also flings back (about 175 s) records no hitch value
/// (`D-266`). The test measures online (`D-266`).
final class ListScrollPerformanceUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testFlingAcrossOneThousandFuelEntries() throws {
        guard #available(iOS 26.0, *) else {
            throw XCTSkip("XCTHitchMetric needs iOS 26; use the Instruments procedure of the runbook")
        }
        let app = XCUIApplication()
        app.verifyOnlineThenIdle(idle: PerformancePlan.idleBeforeMeasuring)
        app.launch()
        let vehicle = app.staticTexts[PerformancePlan.vehicleName]
        XCTAssertTrue(vehicle.waitForExistence(timeout: PerformancePlan.contentTimeout), "Seed and adopt the history first")
        vehicle.tap()
        let list = app.fuelEntryList
        XCTAssertTrue(list.waitForExistence(timeout: PerformancePlan.contentTimeout), "The fuel entry list never appeared")

        let options = XCTMeasureOptions()
        options.iterationCount = PerformancePlan.scrollIterations
        options.invocationOptions = [.manuallyStop]
        measure(metrics: [XCTHitchMetric(application: app)], options: options) {
            for _ in 0..<PerformancePlan.flingsPerIteration {
                list.swipeUp(velocity: .fast)
            }
            stopMeasuring()
            app.navigationBars.buttons.element(boundBy: 0).tap()
            _ = app.buttons["add_vehicle"].waitForExistence(timeout: PerformancePlan.contentTimeout)
            vehicle.tap()
            _ = list.waitForExistence(timeout: PerformancePlan.contentTimeout)
        }
        app.terminate()
    }
}
