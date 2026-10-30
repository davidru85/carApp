import Foundation
import MetricKit

/// `E4-03` (`D-251`, ADR-0252): the iOS end of "cold start to first content".
///
/// One MetricKit extended launch task starts in `carAppApp.init()` and finishes when the first content
/// appears, so `XCTApplicationLaunchMetric(waitUntilResponsive: false)` and MetricKit's launch metrics
/// end at the same frame as Android's `ReportDrawn()`.
@MainActor
final class LaunchMeasurement {
    static let firstContentTaskId = "com.ruizurraca.carapp.first-content"

    static let shared = LaunchMeasurement(
        start: { taskId in try MXMetricManager.extendLaunchMeasurement(forTaskID: MXLaunchTaskID(rawValue: taskId)) },
        finish: { taskId in try MXMetricManager.finishExtendedLaunchMeasurement(forTaskID: MXLaunchTaskID(rawValue: taskId)) }
    )

    private enum Phase {
        case idle
        case measuring
        case finished
    }

    private let start: (String) throws -> Void
    private let finish: (String) throws -> Void
    private var phase: Phase = .idle

    init(start: @escaping (String) throws -> Void, finish: @escaping (String) throws -> Void) {
        self.start = start
        self.finish = finish
    }

    func begin() {
        guard phase == .idle else { return }
        do {
            try start(Self.firstContentTaskId)
            phase = .measuring
        } catch {
            phase = .finished
        }
    }

    /// Ends the task at the first content the user sees. Later calls, and calls before `begin()`, do nothing.
    func finishAtFirstContent() {
        guard phase == .measuring else { return }
        phase = .finished
        try? finish(Self.firstContentTaskId)
    }
}
