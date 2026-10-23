import Shared
import SwiftUI

struct ContentView: View {
    @ObservedObject var model: WalkingSkeletonModel
    let graph: SwiftAppGraph

    private var destination: OnboardingDestination {
        if ProcessInfo.processInfo.environment["CARAPP_UI_TEST_FORCE_WELCOME"] == "1" {
            return .welcome
        }
        let session = model.sessionState
        return resolveOnboardingDestination(
            sessionPhase: session.phase,
            vehicleCount: model.vehicleListState.vehicles.count,
            showsDepartureScreen: requiresDepartureScreen(
                phase: session.phase,
                messageCode: session.message?.code,
                hasPendingDepartureRetry: session.pendingDepartureRetry != nil,
                isReauthenticating: model.isReauthenticating
            )
        )
    }

    var body: some View {
        #if DEBUG
        // `E4-02`: UI tests cannot reach every audited state, so this Debug-only seam renders one of a
        // closed set of review surfaces. It cannot exist in a Release build. (CARAPP_UI_TEST_FORCE_WELCOME,
        // which predates `E4-02`, is read in every build configuration.)
        if let surface = DebugReviewSurface.selected {
            DebugReviewSurfaceView(surface: surface)
        } else if ProcessInfo.processInfo.environment["CARAPP_UI_TEST_FORCE_DEPARTURE_FAILURE"] == "1" {
            DepartureView(
                state: forcedDepartureFailureState(),
                isReauthenticating: false,
                onRetry: {},
                onDismissMessage: {},
                onReauthenticateWithGoogle: {},
                onPrepareAppleReauthentication: { _ in },
                onCompleteAppleAuthorization: { _ in },
                onCancelReauthentication: {}
            )
        } else {
            routedContent
        }
        #else
        routedContent
        #endif
    }

    @ViewBuilder
    private var routedContent: some View {
switch destination {
        case .waiting:
            ProgressView()
        case .departure:
            // `D-214`: progress, the typed failure and its retry, and the re-authentication of a
            // stale-login deletion stay reachable while the signed-in surface is unmounted.
            DepartureView(
                state: model.sessionState,
                isReauthenticating: model.isReauthenticating,
                onRetry: model.retryDeparture,
                onDismissMessage: model.dismissSessionMessage,
                onReauthenticateWithGoogle: model.startGoogleReauthentication,
                onPrepareAppleReauthentication: model.prepareAppleReauthentication,
                onCompleteAppleAuthorization: model.completeAppleAuthorization,
                onCancelReauthentication: model.cancelReauthentication
            )
        case .welcome:
            WelcomeView(
                state: model.sessionState,
                signInCoordinator: model.signInCoordinator,
                onContinueWithoutAccount: model.startAnonymousSession
            )
        case .firstVehicle, .vehicleList:
            // The authenticated surface is mounted once, and it owns its own unresolved-list
            // indicator. Gating it from here would cover the list from a second observer of the
            // same state holder, which can lag behind the one that decides what to present.
            VStack(spacing: 0) {
                // The notice sits above the product surface instead of over it, so it never gates
                // a feature.
                if let reminderIndex = model.sessionState.anonymousReminderIndex {
                    AnonymousReminderView(
                        index: reminderIndex.int32Value,
                        onDismiss: model.dismissAnonymousReminder
                    )
                }
                VehicleListView(graph: graph, skeletonModel: model)
            }
            // F-5 prompts and departure outcomes are drawn above the navigation stack, so the pushed
            // Settings screen and the list share one presenter.
            .modifier(DeparturePromptPresenter(model: model))
            // `E4-05`: the credential collision is presented only here, beside the presenter. The
            // presenter ignores `AdoptExistingAccount`, and this alert ignores everything else.
            .conversionCollisionAlert(
                confirmation: model.sessionState.message?.confirmation,
                onConfirm: model.confirmAccountConversion,
                onCancel: model.cancelAccountConversion
            )
        }
    }
}

#if DEBUG
/// The failed local clear the departure seam renders (`E4-02`); no real departure produces it here.
private func forcedDepartureFailureState() -> SessionUiState {
    SessionUiState(
        phase: .deleting,
        providers: [],
        isBusy: false,
        message: UiMessage(id: 1, kind: .error, code: "PERSISTENCE.TRANSACTION_FAILED", confirmation: nil),
        anonymousReminderIndex: nil,
        pendingSyncCount: nil,
        pendingDepartureRetry: .localClear
    )
}
#endif

#if DEBUG
/// `E4-02`: the closed set of review surfaces a UI test may ask for. The value is validated against this
/// enum, so an unknown string falls through to normal routing instead of accepting arbitrary state.
private enum DebugReviewSurface: String {
    case settingsUnknown
    case settingsReadError
    case settingsOff
    case settingsOn
    case settingsSaving
    case settingsAccountPermanent
    case reauthentication
    case reauthenticationFailure
    case departureFailure
    case deleteAccount
    case deleteLocalData
    case pendingSync
    case conversion
    case collision

    static var selected: DebugReviewSurface? {
        guard let raw = ProcessInfo.processInfo.environment["CARAPP_UI_TEST_REVIEW_SURFACE"] else {
            return nil
        }
        return DebugReviewSurface(rawValue: raw)
    }
}

/// `E4-02`: renders one review surface over the production renderers with inert callbacks. It holds no
/// model, graph, provider or repository, so it performs no product write and starts no authentication.
private struct DebugReviewSurfaceView: View {
    let surface: DebugReviewSurface

    var body: some View {
        content
            // Production reaches an alert through a session emission after the root is mounted. Publishing
            // the fixture's prompt on the next main-actor hop reproduces that timing, so the alert presents
            // the same way it does in the app (an alert armed during the same first render of a freshly
            // installed window loses its buttons' identifiers on this runtime).
            .task { promptPublished = true }
    }

    @ViewBuilder
    private var content: some View {
        switch surface {
        case .settingsUnknown, .settingsReadError, .settingsOff, .settingsOn, .settingsSaving:
            List {
                Section(header: Text("settings_section_units").foregroundStyle(.primary)) {
                    SettingsUnitRows(state: settings)
                    if let message = settings.message {
                        SettingsMessageRow(message: message)
                    }
                }
                Section(header: Text("settings_section_privacy").foregroundStyle(.primary)) {
                    SettingsAnalyticsRow(state: settings, onChange: { _ in })
                }
            }
        case .settingsAccountPermanent:
            // The account rows of a permanent session, which CI cannot reach because its sessions stay
            // `LOCAL` without an App Check debug token.
            List {
                Section(header: Text("settings_section_account").foregroundStyle(.primary)) {
                    SettingsSessionRows(phase: .permanent, isBusy: false, onSignOut: {}, onDeleteAccount: {})
                }
            }
        case .reauthentication, .reauthenticationFailure, .departureFailure:
            DepartureView(
                state: session,
                isReauthenticating: surface != .departureFailure,
                onRetry: {},
                onDismissMessage: {},
                onReauthenticateWithGoogle: {},
                onPrepareAppleReauthentication: { _ in },
                onCompleteAppleAuthorization: { _ in },
                onCancelReauthentication: {}
            )
        case .deleteAccount:
            alertBackground.modifier(DeparturePromptAlert(
                current: fixturePrompt(.confirmDeleteAccount),
                callbacks: promptCallbacks
            ))
        case .deleteLocalData:
            alertBackground.modifier(DeparturePromptAlert(
                current: fixturePrompt(.confirmDeleteLocalData),
                callbacks: promptCallbacks
            ))
        case .pendingSync:
            alertBackground.modifier(DeparturePromptAlert(
                current: fixturePrompt(.pendingSync(count: 3, intent: .signOut)),
                callbacks: promptCallbacks
            ))
        case .conversion:
            List {
                LinkAccountRows(
                    state: session,
                    onPrepareApple: { _ in },
                    onCompleteApple: { _ in },
                    onGoogle: {}
                )
            }
        case .collision:
            alertBackground.conversionCollisionAlert(
                confirmation: promptPublished ? .adoptExistingAccount : nil,
                onConfirm: {},
                onCancel: {}
            )
        }
    }

    /// An inert, full-size backdrop so the alert has a presenter without any product surface. It is
    /// wrapped in a `NavigationStack`, as the production root is, so the alert presents from the same
    /// kind of container it does in the app.
    private var alertBackground: some View {
        NavigationStack {
            List { Text("settings_title") }
                .navigationTitle(Text("settings_title"))
        }
    }

    /// A prompt the alert observes as a later change rather than at first appearance. Production always
    /// reaches an alert through a session emission after the root is mounted, and an alert armed during
    /// the first `onAppear` of a freshly installed window loses its buttons' identifiers on this runtime;
    /// the fixture reproduces the production timing instead of the anomaly.
    @State private var promptPublished = false

    private func fixturePrompt(_ prompt: DeparturePrompt) -> DeparturePrompt? {
        promptPublished ? prompt : nil
    }

    private var promptCallbacks: DeparturePromptCallbacks {
        DeparturePromptCallbacks(
            onWaitForPendingSync: {},
            onDiscardPendingChanges: { _ in },
            onDismiss: {},
            onConfirmDeleteAccount: {},
            onConfirmDeleteLocalData: {}
        )
    }

    private var settings: SettingsUiState {
        let unknown = surface == .settingsUnknown || surface == .settingsReadError
        return SettingsUiState(
            currencyCode: unknown ? nil : "EUR",
            supportedCurrencyCodes: ["EUR", "USD"],
            distanceUnit: .km,
            volumeUnit: .liter,
            analyticsEnabled: surface == .settingsOn || surface == .settingsSaving,
            isSaving: surface == .settingsSaving,
            message: surface == .settingsReadError
                ? UiMessage(id: 1, kind: .error, code: "PERSISTENCE.TRANSACTION_FAILED", confirmation: nil)
                : nil
        )
    }

    private var session: SessionUiState {
        let departureFailed = surface == .departureFailure
        let anonymous = surface == .conversion
        let code = departureFailed
            ? "PERSISTENCE.TRANSACTION_FAILED"
            : (surface == .reauthenticationFailure ? "AUTH.NETWORK_UNAVAILABLE" : "AUTH.REQUIRES_RECENT_LOGIN")
        return SessionUiState(
            phase: departureFailed ? .deleting : (anonymous ? .anonymous : .permanent),
            providers: anonymous ? [.anonymous] : [.apple, .google],
            isBusy: false,
            message: anonymous ? nil : UiMessage(id: 1, kind: .error, code: code, confirmation: nil),
            anonymousReminderIndex: nil,
            pendingSyncCount: nil,
            pendingDepartureRetry: departureFailed ? .localClear : nil
        )
    }
}
#endif
