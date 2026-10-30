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
}

extension XCUIApplication {
    /// The open vehicle's fuel entry list: the collection view that contains fuel rows.
    var fuelEntryList: XCUIElement {
        collectionViews.containing(NSPredicate(format: "identifier BEGINSWITH 'fuel_row_'")).firstMatch
    }
}
