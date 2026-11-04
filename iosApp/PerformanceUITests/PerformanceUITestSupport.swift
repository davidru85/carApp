import Network
import XCTest

/// `E4-03` (`D-253`): the constants of the on-device performance tests.
enum PerformancePlan {
    /// Mirrors `BENCHMARK_VEHICLE_NAME` in `:shared` and `BenchmarkPlan.vehicleName`.
    static let vehicleName = "Benchmark vehicle"
    static let seedArgument = "-CARAPP_BENCHMARK_SEED"
    static let seedTimeout: TimeInterval = 900
    static let contentTimeout: TimeInterval = 30
    static let coldStartIterations = 10
    static let scrollIterations = 5
    static let flingsPerIteration = 30
    /// `D-264`: how long a test waits for the owner to switch the device offline.
    static let offlineTimeout: TimeInterval = 300
    /// `D-264`: the idle period of `docs/runbooks/performance-measurement.md` §1, before a measured block.
    static let idleBeforeMeasuring: TimeInterval = 120
}

extension XCUIApplication {
    /// The open vehicle's fuel entry list: the collection view that contains fuel rows.
    var fuelEntryList: XCUIElement {
        collectionViews.containing(NSPredicate(format: "identifier BEGINSWITH 'fuel_row_'")).firstMatch
    }

    /// `D-264`: iOS 27 verifies every developer-signed install online at its first launch, and
    /// `xcodebuild test` reinstalls this app at every invocation. Launches the app once while the device is
    /// online, so that iOS verifies the install, then waits until the owner switches the device offline
    /// and, when `idle` is positive, for that many seconds.
    func verifyOnlineThenWaitForOffline(idle: TimeInterval) {
        XCTAssertTrue(
            DeviceNetwork.wait(online: true, timeout: PerformancePlan.contentTimeout),
            "Start the step online: iOS verifies the new install at its first launch (D-264)"
        )
        launch()
        terminate()
        print("PERF: turn airplane mode on and Wi-Fi off now")
        XCTAssertTrue(
            DeviceNetwork.wait(online: false, timeout: PerformancePlan.offlineTimeout),
            "The device never went offline (D-264)"
        )
        if idle > 0 {
            let idlePeriod = XCTestExpectation(description: "idle before measuring")
            idlePeriod.isInverted = true
            _ = XCTWaiter().wait(for: [idlePeriod], timeout: idle)
        }
    }
}

/// `D-264`: the criterion of `IosConnectivityObserver`. A default `NWPathMonitor` path is online when its
/// status is `.satisfied`, and offline otherwise.
private enum DeviceNetwork {
    static func wait(online: Bool, timeout: TimeInterval) -> Bool {
        let reached = XCTestExpectation(description: online ? "the device is online" : "the device is offline")
        reached.assertForOverFulfill = false
        let monitor = NWPathMonitor()
        monitor.pathUpdateHandler = { path in
            if (path.status == .satisfied) == online { reached.fulfill() }
        }
        monitor.start(queue: DispatchQueue(label: "com.ruizurraca.carapp.perfuitests.network"))
        defer { monitor.cancel() }
        return XCTWaiter().wait(for: [reached], timeout: timeout) == .completed
    }
}
