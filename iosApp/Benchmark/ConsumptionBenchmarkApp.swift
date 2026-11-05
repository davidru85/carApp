import ConsumptionBenchmark
import SwiftUI

/// `E4-03` (`D-255`, ADR-0256): the non-shipping `D-80` runner. It runs the consumption benchmark once at
/// launch, off the main thread, and shows the median in nanoseconds; the play button runs it again.
@main
struct ConsumptionBenchmarkApp: App {
    var body: some Scene {
        WindowGroup {
            ConsumptionBenchmarkView()
        }
    }
}

struct ConsumptionBenchmarkView: View {
    @State private var medianNanos: Int64?
    @State private var isRunning = false

    var body: some View {
        VStack(spacing: 24) {
            if let medianNanos {
                Text(String(medianNanos))
                    .font(.system(.largeTitle, design: .monospaced))
                    .accessibilityIdentifier("consumption_benchmark_median_nanos")
            } else {
                ProgressView()
            }
            Button(action: run) {
                Image(systemName: "play.fill")
            }
            .disabled(isRunning)
            .accessibilityIdentifier("consumption_benchmark_run")
        }
        .padding(24)
        .task { run() }
    }

    private func run() {
        guard !isRunning else { return }
        isRunning = true
        medianNanos = nil
        Task.detached(priority: .userInitiated) {
            let median = ConsumptionBenchmarkEntryKt.runConsumptionBenchmark()
            await MainActor.run {
                medianNanos = median
                isRunning = false
            }
        }
    }
}
