import Combine
import Shared

@MainActor
final class WalkingSkeletonModel: ObservableObject {
    @Published private(set) var sessionState: SessionUiState
    @Published private(set) var vehicleFormState: VehicleFormUiState
    @Published private(set) var vehicleListState: VehicleListUiState

    private let sessionStateHolder: SessionStateHolder
    private let vehicleFormStateHolder: VehicleFormStateHolder
    private let vehicleListStateHolder: VehicleListStateHolder
    private var observationTasks: [Task<Void, Never>] = []

    init(graph: SwiftAppGraph) {
        sessionStateHolder = graph.sessionStateHolder()
        vehicleFormStateHolder = graph.vehicleFormStateHolder(vehicleId: nil)
        vehicleListStateHolder = graph.vehicleListStateHolder()
        sessionState = sessionStateHolder.state.value
        vehicleFormState = vehicleFormStateHolder.state.value
        vehicleListState = vehicleListStateHolder.state.value

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

    func setVehicleName(_ value: String) {
        vehicleFormStateHolder.setName(value: value)
    }

    func saveVehicle() {
        vehicleFormStateHolder.save()
    }

    func restoreBackup() {
        vehicleListStateHolder.refresh()
    }

    deinit {
        observationTasks.forEach { $0.cancel() }
        vehicleFormStateHolder.close()
    }
}
