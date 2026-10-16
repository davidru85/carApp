import Foundation
import Shared

/// The code of `AuthError.RequiresRecentLogin` (`docs/CONTRACTS.md §6`, `§20.2`). A permanent deletion
/// refused with it waits for re-authentication instead of failing (`§11.5` step 1).
let requiresRecentLoginCode = "AUTH.REQUIRES_RECENT_LOGIN"

/// What discarding the pending changes of a `WARNING.PENDING_SYNC` prompt continues into.
enum DepartureDiscardIntent: Equatable {
    case signOut
    case deleteLocalData
}

/// One departure prompt drawn over the signed-in surface.
enum DeparturePrompt: Equatable {
    case pendingSync(count: Int, intent: DepartureDiscardIntent)
    case confirmDeleteAccount
    case confirmDeleteLocalData
    case failure(code: String)
}

/// What the `B1-D4` departure screen draws.
enum DepartureStage: Equatable {
    case inProgress
    case failed(code: String?, retry: DepartureRetry?)
    case reauthenticationRequired(failureCode: String?)
}

func departureDiscardIntent(for phase: SessionPhase) -> DepartureDiscardIntent? {
    // RED stub: departureDiscardIntent
    return nil
}

func departurePrompt(for state: SessionUiState) -> DeparturePrompt? {
    departurePrompt(
        phase: state.phase,
        messageCode: state.message?.code,
        messageKind: state.message?.kind,
        confirmation: state.message?.confirmation,
        pendingSyncCount: state.pendingSyncCount.map { Int($0.int32Value) }
    )
}

func departurePrompt(
    phase: SessionPhase,
    messageCode: String?,
    messageKind: UiMessageKind?,
    confirmation: Confirmation?,
    pendingSyncCount: Int?
) -> DeparturePrompt? {
    // RED stub: departurePrompt
    return nil
}

func requiresDepartureScreen(
    phase: SessionPhase,
    messageCode: String?,
    hasPendingDepartureRetry: Bool,
    isReauthenticating: Bool
) -> Bool {
    // RED stub: requiresDepartureScreen
    return false
}

func departureStage(
    phase: SessionPhase,
    isBusy: Bool,
    messageCode: String?,
    pendingDepartureRetry: DepartureRetry?,
    isReauthenticating: Bool
) -> DepartureStage {
    // RED stub: departureStage
    return .inProgress
}

func departureRetryLabelKey(_ retry: DepartureRetry) -> String {
    // RED stub: departureRetryLabelKey
    return ""
}

func departureDiscardLabelKey(_ intent: DepartureDiscardIntent) -> String {
    // RED stub: departureDiscardLabelKey
    return ""
}

func departurePromptTitleKey(_ prompt: DeparturePrompt) -> String {
    // RED stub: departurePromptTitleKey
    return ""
}

func departurePromptMessage(_ prompt: DeparturePrompt) -> String {
    // RED stub: departurePromptMessage
    return ""
}

func reauthenticationProviders(_ linked: [AuthProvider]) -> [AuthProvider] {
    // RED stub: reauthenticationProviders
    return []
}
