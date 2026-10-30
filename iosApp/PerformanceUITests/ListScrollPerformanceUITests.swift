import XCTest

/// `E4-03` (`D-253`): hitches during a scripted fling across the 1,000-entry list.
///
/// At 60 Hz every hitch keeps the previous frame on screen for at least 33.3 ms, so a hitch count of zero
/// is exactly "0 frames over 32 ms". Each iteration flings to the end and back; only the downward
/// flings are measured.
final class ListScrollPerformanceUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testFlingAcrossOneThousandFuelEntries() throws {
        guard #available(iOS 26.0, *) else {
            throw XCTSkip("XCTHitchMetric needs iOS 26; use the Instruments procedure of the runbook")
        }
        let app = XCUIApplication()
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
            for _ in 0..<PerformancePlan.flingsPerIteration {
                list.swipeDown(velocity: .fast)
            }
        }
        app.terminate()
    }
}
