import Combine
import Shared

@MainActor
final class WalkingSkeletonModel: ObservableObject {
    @Published private(set) var sessionState: SessionUiState
    @Published private(set) var vehicleFormState: VehicleFormUiState
    @Published private(set) var vehicleListState: VehicleListUiState
    @Published private(set) var syncState: SyncUiState
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
        syncState = syncStateHolder.state.value
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
            // The indicator renders this value and nothing else. `docs/CONTRACTS.md §14` requires the
            // holder to relay the single `SyncController.status`, and the host MUST NOT compute a
            // second status, so this observation is the whole of the iOS-side sync policy.
            Task { [weak self, syncStateHolder] in
                for await state in syncStateHolder.state {
                    self?.syncState = state
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

    /// The two things a foreground entry does: the `D-146` reminder evaluation of
    /// `docs/CONTRACTS.md §11.3` and the `§9.8` `AppForeground` trigger.
    ///
    /// `backgroundMillis` is how long the scene spent out of `.active`, or `nil` for a cold start,
    /// which has no measurable background stay and is a trigger in its own right. The threshold is
    /// applied inside `SyncStateHolder` (`D-183`), so this host reports only the fact it observed.
    /// No scheduler, alarm or user notification is involved in either call.
    ///
    /// Both calls belong to one entry point because `§9.8` and `§11.3` name the same moment, and
    /// splitting them would let one of the two be forgotten at one of the call sites.
    func onSceneActivated(backgroundMillis: Int64?) {
        sessionStateHolder.evaluateAnonymousReminder()
        syncStateHolder.onForegroundReturn(backgroundMillis: backgroundMillis.map { KotlinLong(value: $0) })
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

    /// The manual backup retry `docs/SPECIFICATION.md §3.1` requires, forwarded to the shared holder so
    /// the host owns no retry policy of its own. A failure surfaces through `syncState.message`, whose
    /// `code` the host maps like any other `UiMessage`.
    func retryBackup() {
        syncStateHolder.retryFailed()
    }

    deinit {
        observationTasks.forEach { $0.cancel() }
        vehicleFormStateHolder.close()
    }
}
