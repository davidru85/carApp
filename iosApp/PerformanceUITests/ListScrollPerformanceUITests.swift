import XCTest

/// `E4-03` (`D-253`): hitches during a scripted fling across the 1,000-entry list.
///
/// At 60 Hz every hitch keeps the previous frame on screen for at least 33.3 ms, so a hitch count of zero
/// is exactly "0 frames over 32 ms". On iOS 27 the hitch metric stops recording after about 60 full-list
/// flings in one run, so a run measures one block of flings to the end, and the runbook repeats the run five
/// times (`D-266`). XCTest discards its first invocation, which never flings; the clock metric shows that the
/// measured block is the one that flings. The test measures online (`D-266`).
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
        options.iterationCount = PerformancePlan.flingBlocksPerRun
        options.invocationOptions = [.manuallyStop]
        var warmUp = true
        measure(metrics: [XCTHitchMetric(application: app), XCTClockMetric()], options: options) {
            if warmUp {
                warmUp = false
            } else {
                for _ in 0..<PerformancePlan.flingsPerIteration {
                    list.swipeUp(velocity: .fast)
                }
            }
            stopMeasuring()
        }
        app.terminate()
    }
}
