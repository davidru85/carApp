import Combine
import Shared

@MainActor
final class WalkingSkeletonModel: ObservableObject {
    @Published private(set) var sessionState: SessionUiState
    @Published private(set) var vehicleFormState: VehicleFormUiState
    @Published private(set) var vehicleListState: VehicleListUiState
    @Published private(set) var syncDebugLines: [String]
    let signInCoordinator: NativeSignInCoordinator

    private let sessionStateHolder: SessionStateHolder
    private let vehicleFormStateHolder: VehicleFormStateHolder
    private let vehicleListStateHolder: VehicleListStateHolder
    private let syncStateHolder: SyncStateHolder
    private var observationTasks: [Task<Void, Never>] = []

    init(graph: SwiftAppGraph) {
        let sessionStateHolder = graph.sessionStateHolder()
        self.sessionStateHolder = sessionStateHolder
        signInCoordinator = NativeSignInCoordinator(sessionStateHolder: sessionStateHolder)
        vehicleFormStateHolder = graph.vehicleFormStateHolder(vehicleId: nil)
        vehicleListStateHolder = graph.vehicleListStateHolder()
        syncStateHolder = graph.syncStateHolder()
        sessionState = sessionStateHolder.state.value
        vehicleFormState = vehicleFormStateHolder.state.value
        vehicleListState = vehicleListStateHolder.state.value
        syncDebugLines = syncStateHolder.debugLines.value

        observationTasks = [
            Task { [weak self, sessionStateHolder] in
                for await state in sessionStateHolder.state {
                    self?.sessionState = state
                }
            },
            Task { [weak self, vehicleFormStateHolder] in
                for await state in vehicleFormStateHolder.state {
                    self?.vehicleFormState = state
                }
            },
            Task { [weak self, vehicleListStateHolder] in
                for await state in vehicleListStateHolder.state {
                    self?.vehicleListState = state
                }
            },
            Task { [weak self, syncStateHolder] in
                for await lines in syncStateHolder.debugLines {
                    self?.syncDebugLines = lines
                }
            },
        ]
    }

    var canStartAnonymousSession: Bool {
        sessionState.phase == .signedOut || sessionState.phase == .local
    }

    var sessionLabel: String {
        switch sessionState.phase {
        case .unknown:
            return String(localized: "session_unknown")
        case .local:
            return String(localized: "session_local")
        case .anonymous:
            return String(localized: "session_anonymous")
        case .permanent:
            return String(localized: "session_permanent")
        case .signedOut:
            return String(localized: "session_signed_out")
        case .deleting:
            return String(localized: "session_deleting")
        }
    }

    func startAnonymousSession() {
        sessionStateHolder.startAnonymousSignIn()
    }

    /// Launch and foreground return are the only evaluation moments of the `D-62` schedule
    /// (`docs/CONTRACTS.md §11.3`). No scheduler, alarm or user notification is involved.
    func evaluateAnonymousReminder() {
        sessionStateHolder.evaluateAnonymousReminder()
    }

    func dismissAnonymousReminder() {
        sessionStateHolder.dismissAnonymousReminder()
    }

    func setVehicleName(_ value: String) {
        vehicleFormStateHolder.setName(value: value)
    }

    func saveVehicle() {
        vehicleFormStateHolder.save()
    }

    func restoreBackup() {
        vehicleListStateHolder.refresh()
    }

    func refreshSyncDiagnostics() {
        syncStateHolder.refreshDebug()
    }

    deinit {
        observationTasks.forEach { $0.cancel() }
        vehicleFormStateHolder.close()
    }
}
