import XCTest
@testable import carApp

/// `E4-03` (`D-251`): the first-content marker ends the extended launch task exactly once.
@MainActor
final class LaunchMeasurementTests: XCTestCase {
    func testFirstContentFinishesTheExtendedLaunchTaskExactlyOnce() {
        var finished: [String] = []
        let measurement = LaunchMeasurement(start: { _ in }, finish: { finished.append($0) })

        measurement.begin()
        measurement.finishAtFirstContent()
        measurement.finishAtFirstContent()

        XCTAssertEqual(finished, [LaunchMeasurement.firstContentTaskId])
    }

    func testFirstContentWithoutABegunMeasurementFinishesNothing() {
        var finished: [String] = []
        let measurement = LaunchMeasurement(start: { _ in }, finish: { finished.append($0) })

        measurement.finishAtFirstContent()

        XCTAssertEqual(finished, [])
    }

    func testAMeasurementThatFailedToStartIsNeverFinished() {
        var finished: [String] = []
        let measurement = LaunchMeasurement(
            start: { _ in throw LaunchMeasurementTestError.rejected },
            finish: { finished.append($0) }
        )

        measurement.begin()
        measurement.finishAtFirstContent()

        XCTAssertEqual(finished, [])
    }
}

private enum LaunchMeasurementTestError: Error {
    case rejected
}
