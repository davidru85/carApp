import Foundation
import Shared
import SwiftUI

extension View {
    /// `E4-03` (`D-254`, ADR-0255): runs the benchmark seeding when the `Benchmark` configuration was
    /// launched with the seeding argument (`BenchmarkSeeding.launchArgument`). In every other
    /// configuration it returns the view unchanged, and only the benchmark configuration compiles the
    /// seeding code.
    @ViewBuilder
    func benchmarkSeedingIfRequested(graph: SwiftAppGraph) -> some View {
        #if CARAPP_BENCHMARK
        modifier(BenchmarkSeedingModifier(graph: graph))
        #else
        self
        #endif
    }
}

#if CARAPP_BENCHMARK
/// The deterministic benchmark history. Mirrors `shared/.../BenchmarkSeeding.kt`;
/// `PerformanceHarnessContractTest.seedingPlanIsIdenticalOnBothHosts` pins the two together.
enum BenchmarkPlan {
    static let fuelEntryCount = 1_000
    static let vehicleName = "Benchmark vehicle"
    static let vehicleInitialOdometerKm: Int64 = 10_000
    static let firstEntryEpochMillis: Int64 = 1_577_836_800_000
    static let entrySpacingMillis: Int64 = 86_400_000
    static let odometerStepKm: Int64 = 500
    static let litersText = "40.000"
    static let pricePerLiterText = "1.500"
    static let partialFillPeriod = 10
    static let maxPolls = 500
    static let pollNanoseconds: UInt64 = 10_000_000

    static func date(at index: Int) -> Date {
        let millis = firstEntryEpochMillis + Int64(index) * entrySpacingMillis
        return Date(timeIntervalSince1970: TimeInterval(millis / 1_000))
    }

    static func odometerKm(at index: Int) -> Int64 {
        vehicleInitialOdometerKm + Int64(index + 1) * odometerStepKm
    }

    static func isFullTank(at index: Int) -> Bool {
        index % partialFillPeriod != partialFillPeriod - 1
    }
}

/// What the seeding reports through an accessibility element the seeding UI test waits for.
struct BenchmarkSeedReport {
    let identifier: String
    let value: String
}

/// Seeds through the existing view models, which reach the shared repositories: the Swift ABI is
/// closed, so no Kotlin seeding function is exported to Swift (`D-254`).
@MainActor
final class BenchmarkSeeding: ObservableObject {
    static let launchArgument = "-CARAPP_BENCHMARK_SEED"

    @Published private(set) var report: BenchmarkSeedReport?

    /// Kept alive on purpose: its `deinit` would close the creation holder the app's model shares.
    private var vehicleViewModel: VehicleFormViewModel?

    static func isRequested(arguments: [String] = ProcessInfo.processInfo.arguments) -> Bool {
        arguments.contains(launchArgument)
    }

    func run(graph: SwiftAppGraph) async {
        guard report == nil else { return }
        if let refusal = await refusalReason(graph: graph) {
            report = BenchmarkSeedReport(identifier: "benchmark_seed_refused", value: refusal)
            return
        }
        guard let vehicleId = await createVehicle(graph: graph) else {
            report = BenchmarkSeedReport(identifier: "benchmark_seed_failed", value: "vehicle")
            return
        }
        for index in 0..<BenchmarkPlan.fuelEntryCount {
            let saved = await createFuelEntry(graph: graph, vehicleId: vehicleId, index: index)
            if !saved {
                report = BenchmarkSeedReport(identifier: "benchmark_seed_failed", value: "entry \(index)")
                return
            }
        }
        report = BenchmarkSeedReport(
            identifier: "benchmark_seed_done",
            value: String(BenchmarkPlan.fuelEntryCount)
        )
    }

    /// Refuses unless the device is offline and the session is signed out or local (`LOCAL_OWNER`).
    private func refusalReason(graph: SwiftAppGraph) async -> String? {
        let session = graph.sessionStateHolder()
        let sync = graph.syncStateHolder()
        var polls = 0
        // The provider resolves the session, and the path monitor corrects its optimistic "online"
        // value, asynchronously after launch.
        while polls < BenchmarkPlan.maxPolls,
              session.state.value.phase == .unknown || sync.state.value.isOnline {
            polls += 1
            try? await Task.sleep(nanoseconds: BenchmarkPlan.pollNanoseconds)
        }
        if sync.state.value.isOnline { return "ONLINE" }
        let phase = session.state.value.phase
        if phase != .signedOut && phase != .local { return "NOT_LOCAL_OWNER" }
        return nil
    }

    private func createVehicle(graph: SwiftAppGraph) async -> String? {
        let viewModel = VehicleFormViewModel(graph: graph, vehicleId: nil)
        vehicleViewModel = viewModel
        viewModel.setName(BenchmarkPlan.vehicleName)
        viewModel.setOdometerText(String(BenchmarkPlan.vehicleInitialOdometerKm))
        var createdVehicleId: String?
        viewModel.save { outcome in createdVehicleId = outcome.createdVehicleId }
        var polls = 0
        while polls < BenchmarkPlan.maxPolls, createdVehicleId == nil, viewModel.state.message == nil {
            polls += 1
            try? await Task.sleep(nanoseconds: BenchmarkPlan.pollNanoseconds)
        }
        return createdVehicleId
    }

    private func createFuelEntry(graph: SwiftAppGraph, vehicleId: String, index: Int) async -> Bool {
        let viewModel = FuelEntryFormViewModel(graph: graph, vehicleId: vehicleId, entryId: nil)
        viewModel.setDate(BenchmarkPlan.date(at: index))
        viewModel.setOdometerText(String(BenchmarkPlan.odometerKm(at: index)))
        viewModel.setLitersText(BenchmarkPlan.litersText)
        viewModel.setPricePerLiterText(BenchmarkPlan.pricePerLiterText)
        viewModel.setFullTank(BenchmarkPlan.isFullTank(at: index))
        var saved = false
        viewModel.save { saved = true }
        var polls = 0
        while polls < BenchmarkPlan.maxPolls, !saved, viewModel.state.message == nil {
            polls += 1
            try? await Task.sleep(nanoseconds: BenchmarkPlan.pollNanoseconds)
        }
        return saved
    }
}

/// Hosts the seeding and publishes its report as a one-point accessibility element.
struct BenchmarkSeedingModifier: ViewModifier {
    let graph: SwiftAppGraph
    @StateObject private var seeding = BenchmarkSeeding()

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .topLeading) {
                if let report = seeding.report {
                    Color.clear
                        .frame(width: 1, height: 1)
                        .accessibilityElement()
                        .accessibilityIdentifier(report.identifier)
                        .accessibilityValue(report.value)
                }
            }
            .task {
                guard BenchmarkSeeding.isRequested() else { return }
                await seeding.run(graph: graph)
            }
    }
}
#endif
