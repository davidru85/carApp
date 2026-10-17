import AuthenticationServices
import Combine
import Shared

@MainActor
final class WalkingSkeletonModel: ObservableObject {
    @Published private(set) var sessionState: SessionUiState
    @Published private(set) var vehicleFormState: VehicleFormUiState
    @Published private(set) var vehicleListState: VehicleListUiState
    @Published private(set) var syncState: SyncUiState
    @Published private(set) var syncDebugLines: [String]
    /// Whether the owner started a re-authentication on the departure screen that has not ended yet.
    /// `SessionUiState` publishes no "awaiting re-authentication" value, so the host records the attempt
    /// it started; the departure itself stays in the shared holder.
    @Published private(set) var isReauthenticating = false
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
                    self?.receiveSessionState(state)
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
        // A native picker that ends without a credential abandons the suspended deletion, so the
        // departure screen leaves its re-authentication stage.
        signInCoordinator.onReauthenticationAbandoned = { [weak self] in
            self?.isReauthenticating = false
        }
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

    /// Delegates manual recovery to the shared holder. The vehicle-list surface renders any typed
    /// retry failure from `syncState.message` through the existing `UiMessage.localizedText` mapping.
    func retryBackup() {
        syncStateHolder.retryFailed()
    }

    /// `E4-05`: answers the credential collision of `docs/CONTRACTS.md §11.3` with its one accepted
    /// confirmation.
    func confirmAccountConversion() {
        sessionStateHolder.confirmAccountConversion(confirmation: .adoptExistingAccount)
    }

    /// `E4-05`: cancels the credential collision. The shared holder implements the cancellation as
    /// `clearMessage()` while the `AdoptExistingAccount` confirmation is shown.
    func cancelAccountConversion() {
        sessionStateHolder.clearMessage()
    }

    // MARK: - F-5 departure (`E4-01`)
    //
    // Each member calls one `SessionStateHolder` intent; the departure rules stay in
    // `AccountDepartureFlow` (`docs/CONTRACTS.md §11.5`, `§20.10`). The prompts that answer them are
    // drawn by `DeparturePromptPresenter` and the outcomes by `DepartureView`.

    func requestSignOut() {
        sessionStateHolder.requestSignOut()
    }

    /// The account deletion of a permanent owner and the local-data deletion of a local or anonymous
    /// owner are the same intent (`B1-D5`); the holder decides which operation it is.
    func requestDeleteAccount() {
        sessionStateHolder.requestDeleteAccount()
    }

    func confirmDeleteAccount() {
        sessionStateHolder.confirmDeleteAccount(confirmation: .deleteAccount)
    }

    func confirmDeleteLocalData() {
        sessionStateHolder.confirmDeleteAccount(confirmation: .deleteLocalData)
    }

    /// Discards the pending changes for the request the warning answers (`departureDiscardIntent`).
    func discardPendingChanges(for intent: DepartureDiscardIntent) {
        switch intent {
        case .signOut:
            sessionStateHolder.confirmSignOut(confirmation: .discardPendingChanges)
        case .deleteLocalData:
            sessionStateHolder.confirmDeleteAccount(confirmation: .discardPendingChanges)
        }
    }

    /// Withdraws the prompt shown. Dismissing a departure confirmation withdraws its request.
    func dismissSessionMessage() {
        sessionStateHolder.clearMessage()
    }

    func retryDeparture() {
        sessionStateHolder.retryDeparture()
    }

    func startGoogleReauthentication() {
        isReauthenticating = true
        signInCoordinator.startGoogleReauthentication()
    }

    func prepareAppleReauthentication(_ request: ASAuthorizationAppleIDRequest) {
        isReauthenticating = true
        signInCoordinator.prepareAppleReauthenticationRequest(request)
    }

    func completeAppleAuthorization(_ result: Result<ASAuthorization, Error>) {
        signInCoordinator.completeAppleAuthorization(result)
    }

    func cancelReauthentication() {
        isReauthenticating = false
        signInCoordinator.cancelReauthentication()
    }

    /// A re-authentication belongs to a permanent owner's suspended deletion. Once the phase leaves
    /// `PERMANENT` (the deletion resumed into `DELETING`, or the session ended), the attempt is over.
    private func receiveSessionState(_ state: SessionUiState) {
        sessionState = state
        if state.phase != .permanent {
            isReauthenticating = false
        }
    }

    /// F-5 "wait for sync" (`B1-D3`): withdraw the prompt and request one user-initiated cycle. The
    /// owner asks again later, and the prompt reappears while rows remain. Only `PullToRefresh` may be
    /// requested from Swift (`docs/CONTRACTS.md §20.10`, `D-185`).
    func waitForPendingSync() {
        sessionStateHolder.clearMessage()
        syncStateHolder.requestSync(reason: .pullToRefresh)
    }

    deinit {
        observationTasks.forEach { $0.cancel() }
        vehicleFormStateHolder.close()
    }
}
