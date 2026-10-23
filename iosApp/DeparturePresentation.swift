import Foundation
import Shared

/// The code of `AuthError.RequiresRecentLogin` (`docs/CONTRACTS.md §6`, `§20.2`). A permanent deletion
/// refused with it waits for re-authentication instead of failing (`§11.5` step 1).
let requiresRecentLoginCode = "AUTH.REQUIRES_RECENT_LOGIN"

/// What discarding the pending changes of a `WARNING.PENDING_SYNC` prompt continues into.
///
/// `docs/CONTRACTS.md §20.10` publishes the same warning for two requests and gives each its own
/// answer: `confirmSignOut(DiscardPendingChanges)` for a sign-out, which only a permanent owner can
/// request, and `confirmDeleteAccount(DiscardPendingChanges)` for a local-data deletion, which only a
/// local or anonymous owner can request; a permanent account deletion never counts the outbox. The
/// phase that published the warning therefore names the request without the host remembering it.
enum DepartureDiscardIntent: Equatable {
    case signOut
    case deleteLocalData
}

/// One departure prompt drawn over the signed-in surface by `DeparturePromptPresenter`.
enum DeparturePrompt: Equatable {
    /// `WARNING.PENDING_SYNC` with its exact count: wait, cancel or discard (F-5, `D-213`).
    case pendingSync(count: Int, intent: DepartureDiscardIntent)
    /// `CONFIRMATION.DeleteAccount`, the destructive step of a permanent deletion.
    case confirmDeleteAccount
    /// `CONFIRMATION.DeleteLocalData`, the destructive step of a local or anonymous deletion (`D-158`).
    case confirmDeleteLocalData
    /// A typed error the session holder published while the signed-in surface is shown.
    case failure(code: String)
}

/// What the `D-214` departure screen draws.
enum DepartureStage: Equatable {
    case inProgress
    /// A step failed. `retry` is the published `pendingDepartureRetry`, or `nil` when no retry is
    /// callable and the failure can only be acknowledged.
    case failed(code: String?, retry: DepartureRetry?)
    /// A permanent deletion waits for a fresh credential (`§11.5` step 1). `failureCode` is the error of
    /// the last re-authentication attempt, or `nil` before the first one.
    case reauthenticationRequired(failureCode: String?)
}

func departureDiscardIntent(for phase: SessionPhase) -> DepartureDiscardIntent? {
    switch phase {
    case .permanent:
        return .signOut
    case .local, .anonymous:
        return .deleteLocalData
    case .unknown, .signedOut, .deleting:
        return nil
    }
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

/// The prompt a published session message becomes on the signed-in surface, or `nil` when it is not
/// one.
///
/// A departure confirmation is identified by its typed `Confirmation`, never by parsing a code. An
/// error is drawn as a failure only when it is an `ERROR`: on the signed-in surface the session holder
/// publishes an error only for a host-initiated departure, re-authentication or conversion, while the
/// `LOCAL` fallback of a failed anonymous sign-in is a `WARNING` that never interrupts. A stale login
/// is drawn by the departure screen, and the F-4 collision by the `E4-05` surface.
func departurePrompt(
    phase: SessionPhase,
    messageCode: String?,
    messageKind: UiMessageKind?,
    confirmation: Confirmation?,
    pendingSyncCount: Int?
) -> DeparturePrompt? {
    guard let messageCode, let messageKind else {
        return nil
    }
    switch phase {
    case .local, .anonymous, .permanent:
        break
    case .unknown, .signedOut, .deleting:
        return nil
    }
    if let confirmation {
        switch confirmation {
        case .discardPendingChanges:
            guard let intent = departureDiscardIntent(for: phase) else {
                return nil
            }
            return .pendingSync(count: pendingSyncCount ?? 0, intent: intent)
        case .deleteAccount:
            return .confirmDeleteAccount
        case .deleteLocalData:
            return .confirmDeleteLocalData
        case .odometerInconsistent, .adoptExistingAccount:
            return nil
        }
    }
    if messageCode == requiresRecentLoginCode {
        return nil
    }
    switch messageKind {
    case .error:
        return .failure(code: messageCode)
    case .info, .warning:
        return nil
    }
}

/// Whether the root shows the departure screen instead of the onboarding destination (`D-214`).
///
/// A running deletion is `DELETING`, and the hand-over that follows a successful remote step is
/// `UNKNOWN` with `isBusy` (`D-234`). A failed step keeps whichever phase it failed in and publishes
/// what a retry would repeat, so a published retry routes here in every phase. A permanent deletion
/// refused for a stale login stays `PERMANENT` while it waits for a fresh credential, and the screen
/// keeps the attempt the owner started there mounted until it ends.
func requiresDepartureScreen(
    phase: SessionPhase,
    messageCode: String?,
    hasPendingDepartureRetry: Bool,
    isReauthenticating: Bool,
    isBusy: Bool = false
) -> Bool {
    if hasPendingDepartureRetry || phase == .deleting {
        return true
    }
    // `D-234`: the hand-over between a successful remote step and the local clear is published as
    // `UNKNOWN` with `isBusy`; an idle `UNKNOWN` session is the cold start and keeps waiting.
    if phase == .unknown {
        return isBusy
    }
    guard phase == .permanent else {
        return false
    }
    return isReauthenticating || messageCode == requiresRecentLoginCode
}

/// What the departure screen draws for the published state.
///
/// The re-authentication stage keeps its provider buttons mounted while an attempt is in flight, so
/// the native Apple button can deliver its completion.
func departureStage(
    phase: SessionPhase,
    isBusy: Bool,
    messageCode: String?,
    pendingDepartureRetry: DepartureRetry?,
    isReauthenticating: Bool
) -> DepartureStage {
    let awaitsReauthentication = isReauthenticating || messageCode == requiresRecentLoginCode
    if phase == .permanent, pendingDepartureRetry == nil, awaitsReauthentication {
        return .reauthenticationRequired(
            failureCode: messageCode == requiresRecentLoginCode ? nil : messageCode
        )
    }
    if isBusy {
        return .inProgress
    }
    if let pendingDepartureRetry {
        return .failed(code: messageCode, retry: pendingDepartureRetry)
    }
    if let messageCode {
        return .failed(code: messageCode, retry: nil)
    }
    return .inProgress
}

func departureRetryLabelKey(_ retry: DepartureRetry) -> String {
    switch retry {
    case .sessionCleanup:
        return "departure_retry_session_cleanup"
    case .localClear:
        return "departure_retry_local_clear"
    }
}

func departureDiscardLabelKey(_ intent: DepartureDiscardIntent) -> String {
    switch intent {
    case .signOut:
        return "pending_sync_discard_sign_out"
    case .deleteLocalData:
        return "pending_sync_discard_delete"
    }
}

func departurePromptTitleKey(_ prompt: DeparturePrompt) -> String {
    switch prompt {
    case .pendingSync:
        return "pending_sync_title"
    case .confirmDeleteAccount:
        return "confirm_delete_account_title"
    case .confirmDeleteLocalData:
        return "confirm_delete_local_data_title"
    case .failure:
        return "departure_failure_title"
    }
}

/// The prompt body. The pending count is formatted here, because `UiMessage` transports only a code
/// and the count arrives as the typed `SessionUiState.pendingSyncCount` (`D-157`).
func departurePromptMessage(_ prompt: DeparturePrompt) -> String {
    switch prompt {
    case let .pendingSync(count, _):
        return String(format: String(localized: "pending_sync_body"), count)
    case .confirmDeleteAccount:
        return localizedUiMessage(for: "CONFIRMATION.DeleteAccount")
    case .confirmDeleteLocalData:
        return localizedUiMessage(for: "CONFIRMATION.DeleteLocalData")
    case let .failure(code):
        return localizedSessionMessage(forCode: code)
    }
}

/// The native providers a re-authentication offers: those the account is linked to, Apple first as on
/// the welcome screen. An account that reports neither is offered both, and Firebase rejects a
/// credential of the wrong provider with a typed error the departure screen shows.
func reauthenticationProviders(_ linked: [AuthProvider]) -> [AuthProvider] {
    let offered = [AuthProvider.apple, .google].filter { linked.contains($0) }
    return offered.isEmpty ? [.apple, .google] : offered
}
