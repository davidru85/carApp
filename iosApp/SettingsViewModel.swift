import Foundation
import Shared

/// Observes `SettingsStateHolder` for the Settings screen (`docs/SPECIFICATION.md §3.1`).
///
/// The holder is cached by `SwiftAppGraph` for the graph's lifetime and closed by
/// `SwiftAppGraph.close()`, so this view model only stops observing it: closing it here would end the
/// holder for every later Settings visit. The session and sync holders are not reached from here; the
/// screen uses the process model, which already owns them.
@MainActor
final class SettingsViewModel: ObservableObject {
    @Published private(set) var state: SettingsUiState

    private let stateHolder: SettingsStateHolder
    private var observationTask: Task<Void, Never>?

    init(graph: SwiftAppGraph) {
        let holder = graph.settingsStateHolder()
        self.stateHolder = holder
        self.state = holder.state.value

        self.observationTask = Task { [weak self, holder] in
            for await s in holder.state {
                self?.state = s
            }
        }
    }

    func selectCurrency(_ code: String) {
        stateHolder.selectCurrency(code: code)
    }

    /// Applied at once (`§3.1`); the shared gate turns collection on or off (`docs/CONTRACTS.md §16.1`).
    func setAnalyticsEnabled(_ enabled: Bool) {
        stateHolder.setAnalyticsEnabled(enabled: enabled)
    }

    func clearMessage() {
        stateHolder.clearMessage()
    }

    deinit {
        observationTask?.cancel()
    }
}
