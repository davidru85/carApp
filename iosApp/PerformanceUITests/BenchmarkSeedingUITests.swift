import XCTest

/// `E4-03` (`D-254`): runs the iOS seeding entry point. Owner-run on the device: the install is verified
/// online without the seed argument, then the seeding runs in airplane mode (`D-264`).
final class BenchmarkSeedingUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testSeedsTheBenchmarkFuelHistoryWhileOffline() throws {
        let app = XCUIApplication()
        app.verifyOnlineThenWaitForOffline(idle: 0)
        app.launchArguments += [PerformancePlan.seedArgument]
        app.launch()
        addTeardownBlock { app.terminate() }

        let outcome = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'benchmark_seed_'"))
            .firstMatch
        XCTAssertTrue(outcome.waitForExistence(timeout: PerformancePlan.seedTimeout), "The seeding reported nothing")
        XCTAssertEqual(
            outcome.identifier,
            "benchmark_seed_done",
            "Seeding outcome \(outcome.identifier): \(String(describing: outcome.value))"
        )
        XCTAssertEqual(outcome.value as? String, "1000")
    }
}
